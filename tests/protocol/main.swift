import Foundation

let fixture = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
let incoming = try JSONDecoder().decode(BridgeMessage.self, from: fixture)
precondition(incoming.validPeerMessage(localRole: "subscriber") && incoming.seq == 42 && incoming.text.contains("中文"))
let reply = BridgeMessage.make("ack", role: "subscriber", session: "ios-test-session", seq: incoming.seq, ackSession: incoming.session)
let encoded = try JSONEncoder().encode(reply)
let object = try JSONSerialization.jsonObject(with: encoded) as! [String: Any]
precondition(object["ackSession"] as? String == "android-test-session")
precondition(object["platform"] as? String == "ios")
precondition((object["seq"] as? NSNumber)?.int64Value == 42)
// Same-platform messages are valid; discovery roles, not OS, decide peers.
let original = try JSONSerialization.jsonObject(with: fixture) as! [String: Any]
for platform in ["android", "ios"] {
    for role in ["publisher", "subscriber"] {
        for type in ["hello", "heartbeat", "ack", "iperf_request", "iperf_result"] {
            var packet = original
            packet["platform"] = platform; packet["role"] = role; packet["type"] = type
            let decoded = try JSONDecoder().decode(BridgeMessage.self, from: JSONSerialization.data(withJSONObject: packet))
            precondition(decoded.validPeerMessage(localRole: role == "publisher" ? "subscriber" : "publisher"))
            precondition(!decoded.validPeerMessage(localRole: role))
        }
    }
}
for (key, value) in [("v", 1 as Any), ("seq", -1), ("platform", "unknown"), ("role", "unknown"), ("type", "unknown"), ("session", "")] {
    var invalid = original; invalid[key] = value
    let decoded = try JSONDecoder().decode(BridgeMessage.self, from: JSONSerialization.data(withJSONObject: invalid))
    precondition(!decoded.validPeerMessage(localRole: "subscriber"))
}
print("PASS: protocol v2, both platforms/roles, heartbeat and benchmark messages, Unicode, ACK identity, invalid/legacy rejection")
