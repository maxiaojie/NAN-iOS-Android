# AwareBridge UDP v1

服务：`_aware-bridge._udp`。Android 为 Publisher / UDP listener；iOS 为 Subscriber / UDP initiator。

NDP 响应通过 Android `WifiAwareNetworkSpecifier.Builder.setPort(port)` 和 `setTransportProtocol(17)` 告知 iOS 本次 UDP 服务的端口。iOS 使用 `NetworkConnection<UDP>`，不添加 Apple `Coder` 或 `TLV` framing。

每个 UDP datagram 是一个 UTF-8 JSON 对象，最大 4096 字节：

```json
{"v":1,"type":"heartbeat","platform":"ios","session":"uuid","seq":1,"timestampMs":1794470000000,"text":"iOS heartbeat"}
```

| 字段 | 含义 |
| --- | --- |
| v | 固定为 1 |
| type | `hello`、`heartbeat` 或 `ack` |
| platform | `ios` 或 `android` |
| session | 每次新连接生成的 UUID 字符串 |
| seq | 非负整数；心跳递增，ACK 使用原消息序号 |
| timestampMs | 发送端 Unix 毫秒时间，用于诊断，不假定两端时钟同步 |
| text | 界面展示的说明文字 |
| ackSession | 仅 ACK 必须填写，被确认消息的 session |

iOS 建立连接立即发送 `hello`，之后每 5 秒发送 `heartbeat`。Android 收到有效首包后固定本轮对端 IPv6、端口和 session，每 5 秒主动发送心跳。两端收到 `hello`/`heartbeat` 立即回复 `ack`，不对 `ack` 再响应。

计数包含心跳和 ACK。计数增加仅说明本机发送成功/收到有效消息；UDP 发送成功不代表对端已收到。两端都收到匹配 session 的 ACK，才构成双向收发证据。超过 20 秒未收到有效消息显示超时；不会用 ACK 触发无限重传。

Android 对 socket 调用 `Network.bindSocket()`，按 Aware 网络路由；不更改进程默认网络。所有关闭路径注销网络请求、关闭 socket，停止定时器，并释放最后的 Aware attach，避免残留 NDI 占用。

未来 TCP 扩展可增加端口协商消息。UDP 服务名不会把 IP 链路限制为 UDP；TCP 与 UDP 的端口、socket 和应用协议需要分别管理。
