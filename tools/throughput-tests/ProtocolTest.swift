import Foundation
actor Replies {
    var values: [Data] = []
    func add(_ value: Data) { values.append(value) }
}
@main struct ProtocolTest {
    static func packet(_ type: UInt8, _ count: UInt64 = 0, _ ns: UInt64 = 0) -> Data {
        var b: [UInt8] = [0x41,0x42,0x54,0x31,0,0,0,type]
        for v in [UInt64(123),count,ns] {
            b += (0..<8).reversed().map { UInt8(truncatingIfNeeded: v >> ($0*8)) }
        }
        if type==2 { b += Array(repeating:0,count:1168) }
        return Data(b)
    }
    static func main() async throws {
        let replies=Replies()
        let engine=ThroughputTest(send: { await replies.add($0) }, status: { _ in })
        try await engine.receive(packet(1))
        try await engine.receive(packet(2,0))
        try await engine.receive(packet(1))
        try await engine.receive(packet(2,0))
        let javaData=try Data(contentsOf:URL(fileURLWithPath:CommandLine.arguments[2]))
        precondition(javaData==packet(2,2), "data wire formats differ")
        try await engine.receive(javaData)
        try await engine.receive(packet(3,3,10_000_000_000))
        try await engine.receive(packet(3,3,10_000_000_000))
        let expected=try Data(contentsOf:URL(fileURLWithPath:CommandLine.arguments[1]))
        let values=await replies.values
        precondition(values.count==2 && values.allSatisfy { $0==expected }, "receiver reports differ")
        await engine.close()
        print("Swift / Java interoperability checks passed")
    }
}
