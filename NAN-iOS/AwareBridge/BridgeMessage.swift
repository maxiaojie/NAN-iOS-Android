import Foundation

/// One JSON object per UDP datagram; keys shared with NAN-Android.
struct BridgeMessage: Codable, Sendable {
    let v: Int
    let type: String
    let platform: String
    let session: String
    let seq: Int64
    let timestampMs: Int64
    let text: String
    var ackSession: String?

    static func make(_ type: String, session: String, seq: Int64, ackSession: String? = nil) -> BridgeMessage {
        BridgeMessage(v: 1, type: type, platform: "ios", session: session, seq: seq,
                      timestampMs: Int64(Date().timeIntervalSince1970 * 1000),
                      text: type == "ack" ? "iOS received heartbeat" : "iOS heartbeat", ackSession: ackSession)
    }
    var validAndroidMessage: Bool {
        v == 1 && platform == "android" && !session.isEmpty && seq >= 0
        && ["hello", "heartbeat", "ack"].contains(type)
    }
}
