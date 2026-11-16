# 真机验证记录

2026-11-16 Android 16 Baldur 用户测试补充：安装 APK 后 iOS 无法发现，用户抓包发现设备信息位于 SDA。用户随后明确该设备只升级 Android 17、不再更新 Android 16，已取消强制使用 SDEA 的 Android 16 修改与验证；后续以 Android 17 互通结果为准。历史线索及取消决定见 [P23 专项记录](android16-baldur-ssi-placement.md)。本轮未独立抓包或修改 ROM；长度异常影响此前配对仍为假设，不将结束跟进记作修复或验收通过。

2026-11-16 HA2E6NPB 后续：用户解锁后 root/remount 成功，经关闭 verity、启用 overlayfs 并重启，已写入 Aware XML、重新安装 APK，再次重启后 feature 声明及 wifiaware 服务可见，应用冷启动正常。此前写入受阻已解除；未验收发布、配对或 NDP。详见 P22 后续记录。

2026-11-16 HA2E6NPB（TB323FU，Android 16/API 36）：已有 root，但 Bootloader 锁定导致 remount/disable-verity 被拒绝，vendor 手动重新挂载与 XML push 也失败，系统未声明 Aware。APK 安装与冷启动成功；未重启、未解锁/刷机，未验收 NAN 功能。详见问题台账 P22。

2026-11-16 b2302624（TB522FU，Android 16/API 36）：root/remount 成功，Aware feature XML 写入 vendor 并校验一致；重启后系统声明 `android.hardware.wifi.aware`、存在 wifiaware 服务，APK 安装与冷启动成功。未测试此设备的发布、发现、配对及 NDP。详见问题台账 P21。

2026-11-16 HA2DAQGD（TB323FU，Android 16/API 36）部署：当前源码 assembleDebug 成功，APK 安装 Success、冷启动 Status ok。请求添加的 Aware feature XML 未能写入只读 vendor；正式版 adbd 不支持 root，目标文件仍不存在，系统仍未声明 Aware。未验证此设备的发现、配对或 NDP。详见问题台账 P20。

2026-11-16 HAL 二进制诊断补充：a0807d7 的进程映射、ELF 符号/重定位及反汇编确认当前实现库为 `/vendor/lib64/libwifi-hal.so`，Publish 指针指向 `nan_publish_request`，继续调用 `NanCommand::putNanPublish`。详见 [分析记录](device-wifi-hal-library-analysis.md)。仅做只读诊断，没有变更应用或 ROM，也没有新增跨端功能验收；普通 adb shell 保持 uid=2000。

更新：2026-11-16。当前已在 **TB323FU 断开普通 Wi-Fi、保留 Wi-Fi 开关** 的条件下，完成 iPhone 12 ↔ TB323FU 的发现、系统配对、NDP、UDP 双向心跳/ACK验证。普通 Wi-Fi 并发发现异常和首次 NDP 失败仍是已知限制，不能宣称所有设备/网络状态均已通过。

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

完整问题索引与持续记录规则见 [问题台账](problem-log.md)。此前 USB 锁屏事件失败；2026-11-16 已通过短按 Consumer Power 完成并核验锁屏，见末尾补充。

## 不设置 VendorData 的补充验证

2026-11-13：删除该调用后重新编译安装，TB323FU（普通 Wi-Fi 断开）与 iPhone 12 通过缓存重连；清理两端测试配对后，重新发现、新 PIN 配对成功，并再次建立 NDP 和双向心跳/ACK。详见 [完整记录](vendor-data-removal-validation.md)。本次未验证 Pixel 的无 VendorData 版本。

## 移除 framework-wifi.jar 的补充验证

删除 compileOnly、bootstrapClasspath 覆盖及 jar 文件，标准 Android SDK 36 的 clean / assembleDebug / lintDebug 通过。APK 安装 TB323FU 后，与 iPhone 12 缓存配对重连、NDP 和 UDP 双向心跳/ACK 正常，11:38 两端均为 23/23。本项没有再次清除配对做 PIN 流程，Pixel 未重测。

## 第二台 TB323FU 安装

2026-11-13（Mac 测试日期）：最新标准 SDK 构建 APK 已安装到 USB 设备 a0807d7（TB323FU、Android 17），包名 io.awarebridge.android、版本 1.0。adb 安装返回 Success，冷启动 Status ok，唤醒后 UI 已确认显示 AwareBridge 与开始发布按钮。仅验证安装/启动，未对这台设备验证配对或数据链路。设备自身 lastUpdateTime 显示 11 月 12 日，与 Mac 日期不一致，未修改其系统时间。

2026-11-16：a0807d7 点击发布被 App 的系统定位总开关检查拦截；开启定位后发布成功（UDP 46260）。详见问题台账 P17；本轮未测 iOS 连接。

2026-11-16：定位错误弹窗版本 assembleDebug / lintDebug 通过，已覆盖安装到 a0807d7。定位关闭时弹窗正常，“打开定位设置”跳转正常；开启定位后返回 App 再次发布成功（UDP 58196）。测试结束定位开启、App 正在发布。未触发设置 Activity 缺失分支，未以本项测试宣称 iOS 连接通过。


## 显式配对窗口（2026-11-16）

Android assembleDebug / lintDebug 通过，已安装 a0807d7。默认发布 Setup=false；UI 开关手动开启/关闭 updatePublish 成功。真实配对 Confirm 后自动关闭 updatePublish 成功，维持原 sessionId=7 / pubSubId=1；另一次窗口开启约 60 秒后自动关闭，保留 Cache / Verification。关闭时已有链路继续双向收发，iPhone USB 截图确认 101/101，Android 后续 225/223。详见问题台账 P18 / P19。

用户曾报告配对后一直发现与 -11988，随后恢复通信；受控断开/重连结果见下文。未清空全部凭据重测，配置更新失败/无回调兜底尚未故障注入。不能以当前通信成功宣称 -11988 根因已解决。


### 受控重连结论修正

USB 控制 iPhone 断开并重新连接后，已复现 **Android Setup=false 时服务发现不通过**；同一发现过程中 updatePublish 开启 Setup=true 后恢复，iPhone 双向计数 6/6。Bootstrap=0、不带 SSI、Unsolicited 三项对照均未解决，均恢复。iOS 系统直接发现所选设备的版本仍超时，SDK 明确将 -11988 解码为 subscriberTimeout；已改为具体错误提示。故本版本的“默认关闭后跨端重连”尚未验收通过，不能以既有链路持续收发替代它。详见 P19，需补充空口与 Pixel 对照。


### 最终版本与测试收尾

2026-11-16：最终 Android assembleDebug / lintDebug、iOS 真机签名构建均通过，APK 已安装 a0807d7，iOS App 已安装 iPhone 12。保留精确设备选择后的正常路径复验：Android 自身时钟 12:41:38.094 开启 Setup，12:42:38.203 自动关闭；关闭后 Android 38/38（最近接收 0 秒）、iPhone 39/38（最近接收 2 秒）。这验证了最终版本建立链路后自动关窗仍能持续通信；Setup=false 时新建连接仍未通过，不将临时绕过当作修复。

测试结束由 USB 点击 iOS 断开、Android 停止发布。iPhone USB 短按 Consumer Power 后主 LCD 和全局 backlightState 均为 off，devicectl lockState 返回 passcodeRequired=true，已核验锁屏；未修改自动锁定设置。Ubuntu 抓包机仍待恢复可连接地址，尚无本轮空口证据。


### PairSetup 的空口复验

2026-11-16：Ubuntu monitor 恢复后，在信道 6、149 两次复现：Setup=false 时 iOS 一直发现，打开同会话窗口后连接；窗口自动关闭后两端 77/77，最近接收 0 秒。发现 SDEA 要求 GTK 与 CSIA 不支持组密钥的矛盾，开启后 CSIA 恢复组密钥支持与 ID 5；NIRA 两状态均存在。详见 [逐帧分析与限制](pair-setup-discovery-analysis.md)。这是新增诊断证据，未做单字段固件修复验证，不计作关闭状态重连通过。原始抓包不入库。两端已停止，iPhone 已锁屏。

### Apple 原版 Demo 的 Publish 对照

2026-11-16：原版 iOS Demo 真机签名构建并运行，取得普通监听 / 允许新配对两组 Publish，CSIA 均保留 GTK、SDEA 均要求 GTK，DCEA Pairing Enable 随 UI 状态变化。详见 [证据和复验限制](apple-original-publish-comparison.md)。本轮不验收新 PIN 配对、NDP 或数据收发；Android 关闭 Setup 后重连仍未修复。两端测试 App 已恢复，订阅已停止，iPhone 已锁屏。
