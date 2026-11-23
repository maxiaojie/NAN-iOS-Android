import Foundation
import Network

@MainActor final class IperfClient {
    private static var running = false
    private var job: Task<Void, Never>?
    private var closed = false
    private var serverReady = false
    private let connection: NetworkConnection<UDP>
    private var relay: IperfRelay?
    private let report: @MainActor (String) -> Void
    private let sendResult: @Sendable (String) async -> Void
    init(connection: NetworkConnection<UDP>, report: @escaping @MainActor (String) -> Void,
         sendResult: @escaping @Sendable (String) async -> Void) {
        self.connection = connection
        self.report = report; self.sendResult = sendResult
    }
    func configure(_ address: String?, port: Int?) {
        serverReady = port == 5201 && (address == nil || IPv6Address(address!)?.isLinkLocal == true)
    }
    func request(_ json: String) {
        guard let data = json.data(using: .utf8), let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let streams = object["streams"] as? Int, let proto = object["protocol"] as? String,
              let reverse = object["reverse"] as? Bool,
              let rate = object["rate"] as? Int else { return }
        // Older peers omitted duration; preserve their ten-second default.
        let duration = object["duration"] as? Int ?? 10
        start(proto: proto, streams: streams, reverse: reverse, rateMbps: rate, durationSeconds: duration)
    }
    func start(proto: String, streams: Int, reverse: Bool, rateMbps: Int, durationSeconds: Int) {
        guard !closed else { return }
        guard !Self.running else { publish("已有 iperf3 测试运行中"); return }
        guard (1...16).contains(streams), (0...100000).contains(rateMbps), ["TCP","UDP"].contains(proto) else { return }
        guard (1...3600).contains(durationSeconds) else { publish("测试时长请输入 1～3600 秒"); return }
        guard serverReady else { publish("等待发布端 iperf3 服务就绪"); return }
        Self.running = true
        let direction = reverse ? "发布端 → 订阅端" : "订阅端 → 发布端"
        publish("iperf3 \(proto) · \(streams) 线程 · \(direction) · \(durationSeconds) 秒 · 测试中…")
        job = Task {
            do { relay = try await IperfRelay.open(from: connection) }
            catch {
                Self.running = false
                guard !closed else { return }
                let text = "iperf3 适配层失败：\(error.localizedDescription)"
                publish(text); return
            }
            guard !closed else { relay?.close(); relay = nil; Self.running = false; return }
            let json = await Self.runNative(server: false, address: "::1", port: 5201,
                udp: proto == "UDP", reverse: reverse, streams: streams, duration: durationSeconds, rate: rateMbps)
            let result = Self.summary(json, proto: proto, streams: streams, direction: direction)
            relay?.close(); relay = nil
            Self.running = false
            guard !closed else { return }
            publish(result)
        }
    }
    // Native state is process-wide. Serialize runs, including canceled old links.
    private static let nativeQueue = DispatchQueue(label: "awarebridge.libiperf")
    static func runNative(server: Bool, address: String, port: Int, udp: Bool, reverse: Bool,
                          streams: Int, duration: Int, rate: Int) async -> String {
        let temp = NSTemporaryDirectory()
        let cancellation = NativeIperfCancellation()
        return await withTaskCancellationHandler {
            await withCheckedContinuation { continuation in
                nativeQueue.async {
                    guard cancellation.begin() else {
                        continuation.resume(returning: "{\"error\":\"cancelled\"}"); return
                    }
                    ab_set_temp_dir(temp)
                    let output = ab_iperf_run(server ? 1 : 0, address, "", Int32(port), udp ? 1 : 0,
                        reverse ? 1 : 0, Int32(streams), Int32(duration), Int32(rate))
                    let result = output.map { String(cString: $0) } ?? "{}"
                    ab_free(output)
                    cancellation.finish()
                    continuation.resume(returning: result)
                }
            }
        } onCancel: { cancellation.cancel() }
    }
    private func publish(_ text: String) {
        guard !closed else { return }
        report(text)
        Task { await sendResult(text) }
    }
    nonisolated private static func summary(_ json: String, proto: String, streams: Int, direction: String) -> String {
        guard let data = json.data(using: .utf8), let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return "iperf3 返回无效结果" }
        if let error = root["error"] as? String { return "iperf3 失败：\(error)" }
        guard let end = root["end"] as? [String: Any],
              let sum = (end["sum_received"] ?? end["sum"]) as? [String: Any],
              let bps = sum["bits_per_second"] as? Double, let seconds = sum["seconds"] as? Double else { return "iperf3 未取得接收端汇总结果" }
        let bytes = (sum["bytes"] as? Double) ?? 0
        let test = (root["start"] as? [String: Any])?["test_start"] as? [String: Any]
        let actual = test?["num_streams"] as? Int ?? streams
        var text = String(format: "%@ · %d 线程 · %@\n接收汇总 %.2f Mbps · %.2f MB · %.1f 秒", proto, actual, direction, bps/1e6, bytes/1e6, seconds)
        if proto == "UDP" {
            let udp = (end["sum"] as? [String: Any]) ?? sum
            text += String(format: "\n丢包 %.2f%% · 抖动 %.3f ms", (udp["lost_percent"] as? Double) ?? 0, (udp["jitter_ms"] as? Double) ?? 0)
        }
        return text
    }
    func close() { closed = true; job?.cancel(); job = nil; relay?.close(); relay = nil }
}

/// Cancellation must not affect a later link which reuses process-wide libiperf.
private final class NativeIperfCancellation: @unchecked Sendable {
    private let lock = NSLock()
    private var cancelled = false
    private var started = false
    func begin() -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard !cancelled else { return false }
        ab_prepare(0); started = true; return true
    }
    func cancel() {
        lock.lock(); defer { lock.unlock() }
        cancelled = true
        if started { ab_cancel() }
    }
    func finish() {
        lock.lock(); defer { lock.unlock() }
        started = false
    }
}
