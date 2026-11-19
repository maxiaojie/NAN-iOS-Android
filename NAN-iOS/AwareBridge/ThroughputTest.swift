import Foundation

/// ABT1 wire format matches Android: magic/type/ID/count/nanoseconds, big endian.
actor ThroughputTest {
    typealias Sender = @Sendable (Data) async throws -> Void
    private let send: Sender
    private let status: @Sendable (String) async -> Void
    private var task: Task<Void, Never>?
    private var closed = false
    private var sending: UInt64 = 0
    private var receiving: UInt64 = 0
    private var sent: UInt64 = 0
    private var receiveCompleted = false
    private var seen = Set<UInt64>()
    private var receiveStart = ContinuousClock.now

    init(send: @escaping Sender, status: @escaping @Sendable (String) async -> Void) {
        self.send = send; self.status = status
    }
    static func matches(_ data: Data) -> Bool {
        data.count >= 32 && Array(data.prefix(4)) == [0x41, 0x42, 0x54, 0x31]
    }
    private static func packet(_ type: UInt64, _ id: UInt64, _ count: UInt64 = 0, _ ns: UInt64 = 0) -> Data {
        var bytes: [UInt8] = [0x41,0x42,0x54,0x31,0,0,0,UInt8(type)]
        for value in [id,count,ns] {
            bytes += (0..<8).reversed().map { UInt8(truncatingIfNeeded: value >> ($0*8)) }
        }
        if type == 2 { bytes += Array(repeating: 0, count: 1168) }
        return Data(bytes)
    }
    private static func value(_ bytes: [UInt8], _ offset: Int) -> UInt64 {
        bytes[offset..<offset+8].reduce(0) { ($0 << 8) | UInt64($1) }
    }
    func start() async {
        guard !closed else { return }
        await expire()
        guard sending == 0 && receiving == 0 else { await status("已有吞吐测试进行中"); return }
        let id = UInt64.random(in: 1...UInt64(Int64.max))
        sending = id
        await status("发送测试中 · 10 秒 · 目标 50 Mbps")
        task = Task { await self.transmit(id) }
    }
    private func transmit(_ id: UInt64) async {
        do {
            for _ in 0..<3 { try await send(Self.packet(1,id)) }
            let start = ContinuousClock.now
            var next = start
            var count: UInt64 = 0
            while !Task.isCancelled && start.duration(to: .now) < .seconds(10) {
                for _ in 0..<16 {
                    try Task.checkCancellation()
                    try await send(Self.packet(2,id,count)); count += 1
                }
                next += .nanoseconds(3_072_000)
                if next > .now { try await ContinuousClock().sleep(until: next) }
                else { next = .now }
            }
            try Task.checkCancellation()
            let duration = start.duration(to: .now).components
            let ns = UInt64(duration.seconds)*1_000_000_000 + UInt64(duration.attoseconds/1_000_000_000)
            sent = count
            for _ in 0..<3 {
                try await send(Self.packet(3,id,count,ns))
                try await Task.sleep(for: .milliseconds(50))
            }
            try await Task.sleep(for: .seconds(3))
            if sending == id { sending = 0; await status("测试超时：未收到对端结果，请确认双端均已升级") }
        } catch {
            if sending == id {
                sending = 0
                if !closed { await status("吞吐测试失败：\(error.localizedDescription)") }
            }
        }
    }
    func receive(_ data: Data) async throws {
        guard !closed, Self.matches(data) else { return }
        await expire()
        let bytes = Array(data)
        let type = bytes[7], id = Self.value(bytes,8), count = Self.value(bytes,16), ns = Self.value(bytes,24)
        guard id > 0, id <= UInt64(Int64.max), count <= 1_000_000 else { return }
        if type == 1 && data.count == 32 {
            guard receiving != id, receiving == 0, sending == 0 else { return }
            receiving=id; receiveCompleted=false; seen.removeAll(keepingCapacity: true); receiveStart = .now
            await status("正在接收对端吞吐测试…")
        } else if type == 2 && data.count == 1200 && receiving == id {
            seen.insert(count)
        } else if type == 3 && data.count == 32 && receiving == id && ns > 0 {
            receiveCompleted=true
            let received = UInt64(seen.filter { $0 < count }.count)
            await status(Self.result("接收完成", received, count, ns))
            try await send(Self.packet(4,id,received,ns))
        } else if type == 4 && data.count == 32 && sending == id && ns > 0 && count <= sent {
            sending = 0
            await status(Self.result("对端实收", count, sent, ns))
        }
    }
    func tick() async { await expire() }
    private func expire() async {
        if receiving != 0 && receiveStart.duration(to: .now) > .seconds(14) {
            receiving=0; seen.removeAll()
            if !receiveCompleted { await status("接收测试超时：未收到结束报文") }
        }
    }
    private static func result(_ prefix: String, _ received: UInt64, _ total: UInt64, _ ns: UInt64) -> String {
        String(format: "%@：%.2f Mbps · %.2f MB · %.1f 秒 · 丢包 %.2f%%", prefix,
               Double(received)*1200*8000/Double(ns), Double(received)*1200/1e6, Double(ns)/1e9,
               total == 0 ? 0 : 100*Double(total-received)/Double(total))
    }
    func close() { closed=true; task?.cancel(); receiving=0; sending=0; seen.removeAll() }
}
