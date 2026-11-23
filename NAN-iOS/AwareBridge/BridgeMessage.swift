import Foundation

/// Protocol v2 separates discovery roles from the sender's operating system.
struct BridgeMessage: Codable, Sendable {
    let v: Int
    let type: String
    let platform: String
    let role: String
    let session: String
    let seq: Int64
    let timestampMs: Int64
    let text: String
    var ackSession: String?
    var iperfPort: Int?
    var iperfAddress: String?

    static func make(_ type: String, role: String, session: String, seq: Int64,
                     ackSession: String? = nil, text: String? = nil) -> BridgeMessage {
        BridgeMessage(v: 2, type: type, platform: "ios", role: role, session: session, seq: seq,
                      timestampMs: Int64(Date().timeIntervalSince1970 * 1000),
                      text: text ?? (type == "ack" ? "已确认收到心跳" : "iOS heartbeat"), ackSession: ackSession)
    }
    func validPeerMessage(localRole: String) -> Bool {
        v == 2 && ["android", "ios"].contains(platform) && !session.isEmpty && seq >= 0
        && ["publisher", "subscriber"].contains(localRole)
        && role == (localRole == "publisher" ? "subscriber" : "publisher")
        && ["hello", "heartbeat", "ack", "iperf_request", "iperf_result"].contains(type)
    }
}
