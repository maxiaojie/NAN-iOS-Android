# STA 并发下 iOS 发布、Android 订阅验证（2026-11-19）

## 结果

反向流程不是“连接 STA 必然失败”：5260 MHz STA 条件下两次 NDP reason=2；断开 STA 成功；重新连接同名网络但切换到另一 BSSID、5320 MHz 后，已有链路持续通信，重新建链也成功。不能把失败唯一归因于 STA 信道，因为操作顺序、会话状态也变化；没有完成固定 BSSID 的重复 A/B，也没有本轮空口证据来断言 Bootstrap/Subscribe/NDP 有 23 字节异常。

## 环境和范围

用户指定 a0807d7 / TB323FU Android 17 与 iPhone 12 / iOS 27.2.1，取代此前 303fdedc 排查计划，本轮未操作另一平板。APK 最后更新时间 10:22:34、min/target SDK 37、ACCESS_LOCAL_NETWORK 已授权。Android 订阅，iPhone 发布；使用已有配对缓存，不删除缓存、不测试首次 PIN。iPhone 12 USB ID 00008101-0016212414D0001E，用户解锁后运行。双 iPhone 在线，操作通道明确指定目标。

## 时序和证据

| 条件 | 结果与日志 |
| --- | --- |
| STA lenovo-internet，5260 MHz，BSSID 后缀 50:a7 | 10:48:06 发现 iPhone，SSI 6 字节，无设备名称，缓存验证成功；10:48:35 发起 NDP，10:48:38 accept=false/reason=2。同期一次 Pair Verify 响应发送失败，不能据此概括所有轮次。 |
| STA 保持上述连接，重新发布后及时连接 | 10:51:26 iOS Pair Verify authenticated；10:51:27 Android 发现新 peer=117；10:51:35 NDP accept=false/reason=2，iOS 记录 termination during response。该次不是发布超时后连接。 |
| 临时断开 STA，保留 Wi-Fi 开关 | 10:52:32 NDP accept=true/reason=0；IPv6、UDP 45454/protocol=17 可用；10:53:44 iPhone 29/29 双向数据。 |
| 先有 Aware 链路再连接 STA | 网络重新分配至 BSSID 后缀 60:67，5320 MHz；Android 心跳仍增长至 55/54、最近接收 0 秒。 |
| STA 5320 MHz 保持连接，停止旧连接并重新订阅/发布 | 10:55:53 发现 peer=122，10:55:56 NDP accept=true/reason=0、port=45454/protocol=17；Android 8/7，10:56:32 iPhone 15/15、最近接收 0 秒。 |

iOS 默认发布 120 秒超时确实发生于 10:50:05（Publisher Timeout），UI -11989。这解释随后无服务的部分等待，不能解释 10:51 的新发布有效期内 NDP 失败。两次失败都没有进入双向收发。

成功 NDP 回调列出的 channelInfos 是 Aware 调度信息，不等于 STA 频率；最终 STA 5320 MHz 来自 cmd wifi status 独立确认。STA IP 始终 10.114.249.95，不代表 BSSID/信道未变。

发现项无名称的原因有实测依据：iPhone listener 的 SSI 为 6 字节，没有 modelName/pairingName/vendorName TXT；另一个 86 字节、TB323FU 名称的发现项不能误当成 iPhone。连接验收依赖 iPhone UI 和双方 NDP/UDP 日志，不仅凭列表显示。

## 分析及后续

确认存在 STA 条件下的间歇反向建链失败，但当前日志不足以确定最终责任层。尤其第二次 Pair Verify 已成功、NDP 仍失败，应沿 NDP request/response/confirm 及并发调度检查，而不能只分析服务发现。下一步固定同一 BSSID/频率，重复 STA on/off，再将 NDP 空口与驱动日志对齐。未证明与 Android Publish 多 23 字节为同一根因。

完整日志、截图保存在 Mac /Users/mxj/workspace/nan-capture-analysis/2026-11-19/reverse-sta/，不上传含密钥的原始日志。本轮 Ubuntu 未进入 monitor，未修改 App/ROM。

## 收尾

Android 停止测试 App，STA 保留正常连接并恢复 30 秒息屏；iPhone 停止测试并执行电源锁屏，检查 backlightState=off。缓存未删除。
