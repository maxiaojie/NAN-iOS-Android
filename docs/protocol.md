# AwareBridge UDP v2

服务为 `_aware-bridge._udp`。Android 与 iOS 均可选择发布或订阅，支持 Android↔Android、iOS↔iOS、Android↔iOS。每次一端发布，另一端订阅并主动连接；当前每个 App 只接入一个对端。

v2 将平台与角色分离：`platform` 表示操作系统，`role` 决定连接和测速分工。两端必须更新到 v2，旧 v1 报文会被拒绝；服务名不变，可复用已有系统配对记录。

## 数据链路和端口

Android 发布端通过 `WifiAwareNetworkSpecifier.Builder.setPort()` / `setTransportProtocol(17)` 在 NDP 响应中通知心跳 UDP 端口。iOS 发布端使用 `NetworkListener<UDP>` 监听 `45454`。Android 订阅端优先采用 `WifiAwareNetworkInfo` 的端口，未提供时仅为本项目服务回退到 `45454`；明确声明其他传输协议时拒绝连接。

Android 发布端沿用实验 PSK 配置，并在发现 SSI 的 TXT 中增加 `ndpSecurity=demo-psk`。Android 订阅端识别此字段后配置同一测试口令，避免两台 Android 的 NDP 配置不一致；没有此字段的 iOS 发布端沿用原有配对数据路径。固定口令不是 PIN，也不是产品级身份认证方案。Bootstrap 设备 TXT 不增加该字段。

iOS 的无线收发通过 Network framework；Android socket 绑定 Aware `Network`。均不依靠默认 STA 路由。

## 报文

每个 UDP datagram 是一个 UTF-8 JSON 对象，最大 4096 字节，不加 Apple Coder/TLV framing：

```json
{"v":2,"type":"heartbeat","platform":"ios","role":"subscriber","session":"uuid","seq":1,"timestampMs":1794470000000,"text":"iOS heartbeat"}
```

| 字段 | 含义 |
| --- | --- |
| v | 固定为 2 |
| type | `hello`、`heartbeat`、`ack`、`iperf_request`、`iperf_result` |
| platform | `ios` 或 `android`；允许同平台对端 |
| role | `publisher` 或 `subscriber`；必须与本机角色相反 |
| session | 每次连接生成的非空 UUID 字符串 |
| seq | 非负整数；心跳递增，ACK 使用原消息序号 |
| timestampMs | Unix 毫秒，仅诊断，不假定两端时钟同步 |
| text | 展示文字；测速请求时为参数 JSON 字符串 |
| ackSession | ACK 必须匹配本机 session |
| iperfPort | 发布端测速服务就绪时为 `5201` |
| iperfAddress | Android 发布端可附带链路本地 IPv6；iOS 可省略 |

订阅端建链后主动发送 hello，双方每 5 秒发送心跳，收到 hello/heartbeat 立即 ACK，不对 ACK 再响应。Android 发布端从有效首包固定来源 IPv6、端口与 session；订阅端预先固定目标地址，再从回包固定 session。无效版本、平台、角色、类型、会话和超长报文被丢弃。

收发计数包含心跳与 ACK；UDP send 成功本身不证明对端收到。双方持续收到匹配会话的 ACK 才是双向证据。Android 20 秒无有效接收释放链路并恢复发布/报告订阅失败；iOS 显示心跳超时。前台演示不保证后台、系统终止或固件异常后的保活。

## 测速分工

发布端始终运行 iperf3 server，订阅端运行 client，与操作系统无关。两侧 UI 均提供“本机 → 对端”和“对端 → 本机”。订阅端按钮直接运行 client；发布端按钮通过 `iperf_request` 请求订阅端执行。参数：`protocol`、`streams`（1～16，默认 4）、`duration`（1～3600 秒，默认 10）、`rate`（Mbps，UDP 0 表示不限速）、`reverse`。

`reverse=false` 表示订阅端→发布端，`true` 表示发布端→订阅端。结果通过 `iperf_result` 返回，两侧显示实际接收端汇总。

Android libiperf 直接绑定 Aware Network。iOS libiperf 仅使用 loopback，由 Network framework 转接 Aware TCP/UDP：订阅端本地 `::1:5201` 转至发布端 Aware 5201；发布端 Aware 5201 转至本地 `::1:5202`。每条控制/数据流独立转接，支持多并行流。进程内 native 调用串行，关闭连接时取消所属测试，防止旧链路任务取消新链路的 native 实例。
