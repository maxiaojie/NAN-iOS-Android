import Foundation
import Network

@MainActor final class IperfClient {
    private static var running = false
    private var closed = false
    private var ownsRun = false
    private var host: String?
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
        guard port == 5201, let address, IPv6Address(address)?.isLinkLocal == true else { return }
        host = address
    }
    func request(_ json: String) {
        guard let data = json.data(using: .utf8), let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let streams = object["streams"] as? Int, let proto = object["protocol"] as? String,
              let reverse = object["reverse"] as? Bool,
              let rate = object["rate"] as? Int else { return }
        start(proto: proto, streams: streams, reverse: reverse, rateMbps: rate)
    }
    func start(proto: String, streams: Int, reverse: Bool, rateMbps: Int) {
        guard !closed else { return }
        guard !Self.running else { publish("已有 iperf3 测试运行中"); return }
        guard (1...16).contains(streams), (0...100000).contains(rateMbps), ["TCP","UDP"].contains(proto) else { return }
        guard host != nil else { publish("等待 Android iperf3 服务地址"); return }
        Self.running = true
        ownsRun = true
        let direction = reverse ? "Android → iOS" : "iOS → Android"
        publish("iperf3 \(proto) · \(streams) 线程 · \(direction) · 测试中…")
        ab_set_temp_dir(NSTemporaryDirectory())
        ab_prepare(0)
        Task {
            do { relay = try await IperfRelay.open(from: connection) }
            catch {
                Self.running = false
                ownsRun = false
                guard !closed else { return }
                let text = "iperf3 适配层失败：\(error.localizedDescription)"
                publish(text); return
            }
            guard !closed else { relay?.close(); relay = nil; Self.running = false; ownsRun = false; return }
            let result = await Task.detached(priority: .userInitiated) {
                let output = ab_iperf_run(0, "::1", "", 5201, proto == "UDP" ? 1 : 0,
                                          reverse ? 1 : 0, Int32(streams), 10, Int32(rateMbps))
                let json = output.map { String(cString: $0) } ?? "{}"
                ab_free(output)
                return Self.summary(json, proto: proto, streams: streams, direction: direction)
            }.value
            relay?.close(); relay = nil
            Self.running = false
            ownsRun = false
            guard !closed else { return }
            publish(result)
        }
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
    func close() { closed = true; if ownsRun { ab_cancel() }; relay?.close(); relay = nil }
}
