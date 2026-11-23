/*
Derived from Apple's BuildingPeerToPeerApps discovery/connection approach.
See ../LICENSE.txt for the Apple sample license. Uses raw UDP, not Coder/TLV.
*/
import Foundation
import Observation
import Network
import WiFiAware
import OSLog

let bridgeServiceName = "_aware-bridge._udp"

extension WASubscribableService {
    static var bridge: WASubscribableService { allServices[bridgeServiceName]! }
}

extension WAPublishableService {
    static var bridge: WAPublishableService { allServices[bridgeServiceName]! }
}

enum BridgeRole: String, CaseIterable, Identifiable {
    case subscriber = "发现并连接"
    case publisher = "发布服务"
    var id: Self { self }
}

@MainActor @Observable
final class BridgeModel {
    var role: BridgeRole = .subscriber
    var status = "准备就绪"
    var devices: [WAPairedDevice] = []
    var selectedDeviceID: WAPairedDevice.ID?
    var active = false
    var tx = 0
    var rx = 0
    var peer = "—"
    var lastMessage = "每 5 秒双向心跳，收到后回复确认"
    var lastReceived: Date?
    var now = Date()
    var benchmarkStatus = "libiperf · 默认 10 秒 / 4 线程 · 参数可调整"
    var testProtocol = "TCP"
    var testStreams = 4
    var testDurationSeconds = 10
    var testRateMbps = 0
    @ObservationIgnored private var benchmark: IperfClient?
    @ObservationIgnored private var benchmarkSend: ((String) -> Void)?
    func startBenchmark(reverse: Bool) {
        guard let benchmark, let lastReceived, Date().timeIntervalSince(lastReceived) < 20 else {
            benchmarkStatus = "请先等待双向心跳建立"; return
        }
        if role == .publisher {
            let config: [String: Any] = ["protocol": testProtocol, "streams": testStreams,
                "reverse": !reverse, "duration": testDurationSeconds, "rate": testRateMbps]
            guard let data = try? JSONSerialization.data(withJSONObject: config),
                  let text = String(data: data, encoding: .utf8) else { return }
            benchmarkSend?(text)
            benchmarkStatus = "已请求对端启动 iperf3 测试"
        } else {
            benchmark.start(proto: testProtocol, streams: testStreams, reverse: reverse, rateMbps: testRateMbps, durationSeconds: testDurationSeconds)
        }
    }
    @ObservationIgnored private var connectionTask: Task<Void, Never>?
    @ObservationIgnored private var incomingTask: Task<Void, Never>?
    @ObservationIgnored private var generation = UUID()
    @ObservationIgnored private var attemptReachedReady = false
    @ObservationIgnored private var connectionAttempt = UUID()
    @ObservationIgnored private var incomingActive = false
    @ObservationIgnored private let logger = Logger(subsystem: "io.awarebridge.ios", category: "connection")

    func watchDevices() async {
        do {
            for try await update in WAPairedDevice.allDevices {
                devices = Array(update.values).sorted { displayName($0) < displayName($1) }
                if !devices.contains(where: { $0.id == selectedDeviceID }) {
                    selectedDeviceID = devices.first?.id
                }
            }
        } catch { status = "读取已配对设备失败：\(error.localizedDescription)" }
    }
    func displayName(_ device: WAPairedDevice) -> String {
        device.name ?? device.pairingInfo?.pairingName ?? "已配对设备"
    }
    func didPair(_ endpoint: WAEndpoint) {
        selectedDeviceID = endpoint.device.id
        status = "配对成功，点击连接"
    }
    func connect() {
        guard role == .subscriber, !active, let target = selectedDeviceID,
              let targetDevice = devices.first(where: { $0.id == target }) else { return }
        let token = UUID()
        generation = token
        active = true
        tx = 0; rx = 0; lastReceived = nil; peer = "—"
        status = "正在发现所选设备…"
        connectionTask = Task { [weak self] in
            do {
                for attempt in 1...3 {
                    guard let self, self.generation == token else { return }
                    self.attemptReachedReady = false
                    do {
                        let browser = NetworkBrowser(for: .wifiAware(.connecting(to: .selected([targetDevice]), from: .bridge)))
                        let endpoint = try await browser.run { endpoints in
                            if let endpoint = endpoints.first(where: { $0.device.id == target }) {
                                return .finish(endpoint)
                            }
                            return .continue
                        }
                        try Task.checkCancellation()
                        guard self.generation == token else { return }
                        self.peer = self.displayName(endpoint.device)
                        self.status = "正在建立 Wi-Fi Aware 数据链路…"
                        try await self.exchange(with: endpoint, token: token)
                        if self.generation == token { self.finish("连接已结束") }
                        return
                    } catch {
                        try Task.checkCancellation()
                        guard self.generation == token else { return }
                        // A failed NDP negotiation may leave NetworkConnection waiting
                        // indefinitely. Retry only before ready; an established UDP
                        // session must not silently switch its peer/session identity.
                        guard let networkError = error as? NWError,
                              case .wifiAware(-11987) = networkError,
                              !self.attemptReachedReady, attempt < 3 else { throw error }
                        self.logger.notice("NDP negotiation failed (-11987), retry \(attempt + 1)/3")
                        self.status = "链路协商失败（-11987），2 秒后重试（\(attempt + 1)/3）"
                        try await Task.sleep(for: .seconds(2))
                    }
                }
            } catch {
                guard let self, self.generation == token else { return }
                if let networkError = error as? NWError, case .wifiAware(-11987) = networkError {
                    self.finish("链路协商失败（-11987），请在对端重新发布后再连接")
                } else {
                    self.finish(error is CancellationError ? "已断开" : "连接失败：\(Self.errorDescription(error))")
                }
            }
        }
    }

    func publish() {
        guard role == .publisher, !active else { return }
        let token = UUID()
        generation = token
        active = true
        incomingActive = false
        tx = 0
        rx = 0
        lastReceived = nil
        peer = "—"
        status = "正在发布服务…"
        connectionTask = Task { [weak self] in
            do {
                let listener = try NetworkListener(
                    for: .wifiAware(.connecting(to: .bridge, from: .allPairedDevices),
                                    active: .seconds(3600)),
                    using: .parameters { UDP() }
                        .localPort(45454)
                        .wifiAware { $0.performanceMode = .realtime }
                        .serviceClass(.bestEffort))
                listener.onStateUpdate { [weak self] _, state in
                    guard let self, self.generation == token else { return }
                    switch state {
                    case .ready:
                        if !self.incomingActive {
                            self.status = "服务已发布，等待已配对的设备连接"
                        }
                    case .waiting(let error):
                        self.status = "发布等待：\(Self.errorDescription(error))"
                    default: break
                    }
                }
                try await listener.run { [weak self] connection in
                    guard let self, self.generation == token, !self.incomingActive else { return }
                    self.incomingActive = true
                    self.tx = 0
                    self.rx = 0
                    self.lastReceived = nil
                    self.peer = "附近设备"
                    self.status = "已收到对端连接，等待双向心跳"
                    // The accepted UDP connection owns its lifetime independently of
                    // the discovery listener (which iOS may stop after 120 seconds).
                    self.incomingTask = Task { [weak self] in
                        guard let self else { return }
                        defer {
                            if self.generation == token { self.incomingActive = false }
                        }
                        do {
                            try await self.exchange(connection: connection, token: token)
                        } catch {
                            guard self.generation == token else { return }
                            self.stop()
                            self.status = "连接已结束，请重新发布：\(Self.errorDescription(error))"
                        }
                    }
                }
                if let self, self.generation == token { self.finish("发布已结束") }
            } catch {
                guard let self, self.generation == token else { return }
                if let networkError = error as? NWError,
                   case .publisherTimeout = networkError.wifiAware,
                   self.incomingActive {
                    // Publishing has expired; the accepted data connection remains
                    // owned by incomingTask until Stop or a transport failure.
                    self.logger.notice("Publisher expired; retaining the active UDP connection")
                    return
                }
                self.incomingTask?.cancel()
                self.finish(error is CancellationError ? "已停止发布" : "发布失败：\(Self.errorDescription(error))")
            }
        }
    }
    private static func errorDescription(_ error: Error) -> String {
        if let networkError = error as? NWError, let awareError = networkError.wifiAware {
            if case .subscriberTimeout = awareError {
                return "未发现所选设备的服务（发现超时，\(networkError)）"
            }
            return "\(awareError)（\(networkError)）"
        }
        return error.localizedDescription
    }
    private func exchange(with endpoint: WAEndpoint, token: UUID) async throws {
        let connection = NetworkConnection(to: endpoint, using: .parameters { UDP() }
            .wifiAware { $0.performanceMode = .realtime }
            .serviceClass(.bestEffort))
        try await exchange(connection: connection, token: token)
    }

    private func exchange(connection: NetworkConnection<UDP>, token: UUID) async throws {
        let attemptToken = UUID()
        connectionAttempt = attemptToken
        let (failures, failureContinuation) = AsyncThrowingStream<Void, Error>.makeStream()
        defer { failureContinuation.finish() }
        connection.onStateUpdate { [weak self] _, state in
            guard let self, self.generation == token, self.connectionAttempt == attemptToken else { return }
            switch state {
            case .ready:
                self.attemptReachedReady = true
                self.logger.info("NDP ready")
                self.status = "数据链路已建立，等待双向心跳"
            case .waiting(let error):
                self.logger.notice("NDP waiting: \(String(describing: error), privacy: .public)")
                self.status = "等待连接：\(Self.errorDescription(error))"
                if case .wifiAware(-11987) = error { failureContinuation.finish(throwing: error) }
            case .failed(let error): failureContinuation.finish(throwing: error)
            default: break
            }
        }
        let session = UUID().uuidString
        let localRole = role == .publisher ? "publisher" : "subscriber"
        let service = IperfService()
        @Sendable func makeMessage(_ type: String, seq: Int64, text: String? = nil,
                                  ackSession: String? = nil) -> BridgeMessage {
            BridgeMessage.make(type, role: localRole, session: session, seq: seq,
                               ackSession: ackSession, text: text)
        }
        let engine = IperfClient(connection: connection, report: { [weak self] text in
            self?.benchmarkUpdate(text, token: token, attempt: attemptToken)
        }, sendResult: { text in
            let message = makeMessage("iperf_result", seq: 0, text: text)
            try? await connection.send(JSONEncoder().encode(message))
        })
        benchmark = engine
        benchmarkSend = { text in
            Task {
                do {
                    guard await service.available else {
                        self.benchmarkUpdate("等待本机测速服务就绪", token: token, attempt: attemptToken)
                        return
                    }
                    var request = makeMessage("iperf_request", seq: 0, text: text)
                    request.iperfPort = 5201
                    try await connection.send(JSONEncoder().encode(request))
                }
                catch { self.benchmarkUpdate("测速请求发送失败：\(error.localizedDescription)", token: token, attempt: attemptToken) }
            }
        }
        defer {
            if connectionAttempt == attemptToken { benchmark = nil; benchmarkSend = nil }
            engine.close()
            service.close()
        }
        // Both tasks retain the connection only for this scope. Cancelling the receive
        // operation cancels the underlying connection, releasing the Aware data path.
        try await withThrowingTaskGroup(of: Void.self) { group in
            group.addTask {
                for try await _ in failures { }
            }
            group.addTask { [weak self] in
                var seq: Int64 = 0
                while !Task.isCancelled {
                    var message = makeMessage(seq == 0 ? "hello" : "heartbeat", seq: seq)
                    if localRole == "publisher", await service.available { message.iperfPort = 5201 }
                    try await connection.send(JSONEncoder().encode(message))
                    await self?.didSend(token: token)
                    seq += 1
                    try await Task.sleep(for: .seconds(5))
                }
            }
            group.addTask { [weak self] in
                var remoteSession: String?
                var benchmarkUnavailable = false
                while !Task.isCancelled {
                    let (data, _) = try await connection.receive()
                    guard data.count <= 4096,
                          let message = try? JSONDecoder().decode(BridgeMessage.self, from: data),
                          message.validPeerMessage(localRole: localRole) else { continue }
                    if let remoteSession, message.session != remoteSession { continue }
                    if message.type == "ack" && message.ackSession != session { continue }
                    remoteSession = message.session
                    if localRole == "publisher", !benchmarkUnavailable, !(await service.ready) {
                        do {
                            try await service.start(from: connection)
                        } catch is CancellationError {
                            throw CancellationError()
                        } catch {
                            benchmarkUnavailable = true
                            await service.close()
                            await self?.benchmarkUpdate("测速服务不可用：\(error.localizedDescription)", token: token, attempt: attemptToken)
                        }
                    }
                    await engine.configure(message.iperfAddress, port: message.iperfPort)
                    if message.type == "iperf_request" {
                        if localRole == "subscriber" { await engine.request(message.text) }
                        continue
                    }
                    if message.type == "iperf_result" {
                        await self?.benchmarkUpdate(message.text, token: token, attempt: attemptToken)
                        continue
                    }
                    await self?.didReceive(message, token: token)
                    if message.type != "ack" {
                        var ack = makeMessage("ack", seq: message.seq, ackSession: message.session)
                        if localRole == "publisher", await service.available { ack.iperfPort = 5201 }
                        try await connection.send(JSONEncoder().encode(ack))
                        await self?.didSend(token: token)
                    }
                }
            }
            defer { group.cancelAll() }
            try await group.next()
        }
    }
    private func benchmarkUpdate(_ text: String, token: UUID, attempt: UUID) {
        guard generation == token, connectionAttempt == attempt else { return }
        benchmarkStatus = text
    }
    private func didSend(token: UUID) {
        guard generation == token else { return }
        tx += 1
    }
    private func didReceive(_ message: BridgeMessage, token: UUID) {
        guard generation == token else { return }
        rx += 1; lastReceived = Date(); lastMessage = message.text
        status = "已连接 · 双向数据通信中"
    }
    func tick() {
        now = Date()
        if active, let lastReceived, now.timeIntervalSince(lastReceived) > 20 {
            status = "心跳超时，请检查对端是否在前台"
        }
    }
    func stop() {
        benchmark?.close()
        benchmark = nil
        benchmarkStatus = "libiperf · 默认 10 秒 / 4 线程 · 参数可调整"
        generation = UUID()
        connectionTask?.cancel()
        connectionTask = nil
        incomingTask?.cancel()
        incomingTask = nil
        incomingActive = false
        finish("已断开")
    }
    private func finish(_ text: String) { active = false; status = text }
}
