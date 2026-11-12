import Foundation

// Decode a Java/JSONObject-shaped packet, including Unicode and unknown-key tolerance.
let fixture = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
let incoming = try JSONDecoder().decode(BridgeMessage.self, from: fixture)
precondition(incoming.validAndroidMessage && incoming.seq == 42 && incoming.text.contains("中文"))
let reply = BridgeMessage.make("ack", session: "ios-test-session", seq: incoming.seq, ackSession: incoming.session)
let encoded = try JSONEncoder().encode(reply)
let object = try JSONSerialization.jsonObject(with: encoded) as! [String: Any]
precondition(object["ackSession"] as? String == "android-test-session")
precondition(object["platform"] as? String == "ios")
precondition((object["seq"] as? NSNumber)?.int64Value == 42)
for mutation in ["v": 2, "seq": -1] {
    var invalid = try JSONSerialization.jsonObject(with: fixture) as! [String: Any]
    invalid[mutation.key] = mutation.value
    let decoded = try JSONDecoder().decode(BridgeMessage.self, from: JSONSerialization.data(withJSONObject: invalid))
    precondition(!decoded.validAndroidMessage)
}
print("PASS: Android JSON → Swift decode, Unicode, ACK session/sequence, invalid version/sequence rejection")
