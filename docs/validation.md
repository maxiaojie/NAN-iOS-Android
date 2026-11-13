# 真机验证记录

更新：2026-11-13。当前已在 **TB323FU 断开普通 Wi-Fi、保留 Wi-Fi 开关** 的条件下，完成 iPhone 12 ↔ TB323FU 的发现、系统配对、NDP、UDP 双向心跳/ACK验证。普通 Wi-Fi 并发发现异常和首次 NDP 失败仍是已知限制，不能宣称所有设备/网络状态均已通过。

## 构建和安装

- `scripts/check.sh` 通过：Android Debug、Android lint、Swift JSON 协议检查、iOS 真机目标无签名编译。
- Swift 检查覆盖 Android 格式报文解码、中文文本、ACK 的 session / seq，以及不支持的版本、负序号拒绝。
- Android 安装到 TB323FU（Android 17）；Pixel 9 用于发现与配对对照。
- iOS 签名构建安装到 iPhone 12（iOS 27.0）。本机借用已有 Wi-Fi Aware 示例 Bundle ID/profile，仓库保留独立 Bundle ID；签名资料未入库。
- USB 触控已实际操作 DevicePicker、连接、断开。截屏或启动 App 本身不计作触控验证。

## 无线验证

| 项目 | 结果 / 条件 |
| --- | --- |
| TB323FU 服务发现 | 普通 Wi-Fi 断开后通过；iOS 显示从设备读取的中文系统名称 |
| 首次系统配对 | 已进入 PIN 流程并成功，Android 记录 Pairing Confirm / Security Association |
| 缓存配对重连 | Android 重新发布后可再次连接，无需重新输入 PIN |
| NDP / IPv6 | 通过；应用绑定 Aware Network，使用 IPv6 link-local 对端 |
| UDP 双向 / ACK | 通过；两端实际收到 hello、heartbeat 和 ACK，计数持续增长 |
| 持续通信 | 多次观察；10:41 到 10:43，iOS 18/18 增长到 72/71；重试版本 10:47 到 10:50，7/7 增长到 59/59 |
| 最终两端恢复版本 | 再次建立链路，截图 iOS 47/47、Android 48/48，最近接收 0 秒 |
| iOS 停止后的 NDP | 最终版本实际触发心跳超时，提示重新发布；mClients、Active NDPs、mUsedNdis、mNetworkRequestsCache 均为空，清理验证通过 |
| Pixel 9 | 连接普通 Wi-Fi 时旧/新 App 均可被发现；配对成功。其 NDP 曾失败，未完成 Pixel 双向数据验收 |
| TCP、后台持续连接 | 未测试；当前示例为前台 raw UDP，切后台停止 |

发送/接收截图不是同一瞬间，差 1 个报文不等同丢包；本次验证的是两端接收持续增长及 ACK 内容，未进行严格丢包率测量。

## 已知问题与处理

1. [TB323FU STA + NAN 并发异常](tb323fu-sta-nan-issue.md)：连接普通 Wi-Fi 时部分 Publish 在完整属性之后额外出现 23 个零字节，iOS 发现失败；断开后恢复。由平板 ROM 流程继续定位，Pixel 对照正常。
2. [NDP -11987 与恢复](ndp-connection-recovery.md)：首次配对后的链路协商曾失败，Android 记录原因码 9010；后续未修改网络参数重连成功。增加 iOS 有限重试与 Android 心跳超时清理；不宣称已修复固件根因。
3. 自动重试错误分支没有在后续成功重连中重新触发；不能以正常路径通过代替该失败分支已复现验证。

## 历史与诊断清理

11 月 12 日及 13 日早期原/新 App 都曾发现失败。尝试过服务名、发布模式、instant mode、短 pairingName、配对缓存配置等对照。它们不是最终代码改动，现均恢复正式独立服务名 `_aware-bridge._udp`、Solicited 发布及完整配对配置。

测试中通过旧 App 身份删除 TB323FU 的测试别名 aware-100，并核对系统配对映射为空；未删除普通 Wi-Fi 保存网络。此后新 App 的配对已重新建立。平板 ADB 临时 root 已恢复为 shell。完整含密钥日志、原始抓包、签名资料不提交仓库。

用户于 11 月 13 日明确要求先上传 ROM 问题文档，随后代码及后续验证记录继续推送。发布代码不表示上述已知 ROM 问题已经解决。

完整问题索引与持续记录规则见 [问题台账](problem-log.md)。USB 锁屏事件尚未成功，设备此前仍报告 activeOn；不能以 App 已停止代替完成锁屏。
