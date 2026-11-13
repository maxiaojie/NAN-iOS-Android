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

@MainActor @Observable
final class BridgeModel {
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
    @ObservationIgnored private var connectionTask: Task<Void, Never>?
    @ObservationIgnored private var generation = UUID()
    @ObservationIgnored private var attemptReachedReady = false
    @ObservationIgnored private var connectionAttempt = UUID()
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
        guard !active, let target = selectedDeviceID else { return }
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
                        let browser = NetworkBrowser(for: .wifiAware(.connecting(to: .allPairedDevices, from: .bridge)))
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
                    self.finish("链路协商失败（-11987），请在 Android 重新发布后再连接")
                } else {
                    self.finish(error is CancellationError ? "已断开" : "连接失败：\(error.localizedDescription)")
                }
            }
        }
    }
    private func exchange(with endpoint: WAEndpoint, token: UUID) async throws {
        let attemptToken = UUID()
        connectionAttempt = attemptToken
        let connection = NetworkConnection(to: endpoint, using: .parameters { UDP() }
            .wifiAware { $0.performanceMode = .realtime }
            .serviceClass(.bestEffort))
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
                self.status = "等待连接：\(error.localizedDescription)"
                if case .wifiAware(-11987) = error { failureContinuation.finish(throwing: error) }
            case .failed(let error): failureContinuation.finish(throwing: error)
            default: break
            }
        }
        let session = UUID().uuidString
        // Both tasks retain the connection only for this scope. Cancelling the receive
        // operation cancels the underlying connection, releasing the Aware data path.
        try await withThrowingTaskGroup(of: Void.self) { group in
            group.addTask {
                for try await _ in failures { }
            }
            group.addTask { [weak self] in
                var seq: Int64 = 0
                while !Task.isCancelled {
                    let message = BridgeMessage.make(seq == 0 ? "hello" : "heartbeat", session: session, seq: seq)
                    try await connection.send(JSONEncoder().encode(message))
                    await self?.didSend(token: token)
                    seq += 1
                    try await Task.sleep(for: .seconds(5))
                }
            }
            group.addTask { [weak self] in
                var remoteSession: String?
                while !Task.isCancelled {
                    let (data, _) = try await connection.receive()
                    guard data.count <= 4096,
                          let message = try? JSONDecoder().decode(BridgeMessage.self, from: data),
                          message.validAndroidMessage else { continue }
                    if let remoteSession, message.session != remoteSession { continue }
                    if message.type == "ack" && message.ackSession != session { continue }
                    remoteSession = message.session
                    await self?.didReceive(message, token: token)
                    if message.type != "ack" {
                        let ack = BridgeMessage.make("ack", session: session, seq: message.seq, ackSession: message.session)
                        try await connection.send(JSONEncoder().encode(ack))
                        await self?.didSend(token: token)
                    }
                }
            }
            defer { group.cancelAll() }
            try await group.next()
        }
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
            status = "心跳超时，请检查 Android 是否在前台"
        }
    }
    func stop() {
        generation = UUID()
        connectionTask?.cancel()
        connectionTask = nil
        finish("已断开")
    }
    private func finish(_ text: String) { active = false; status = text }
}
