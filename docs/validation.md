# 真机验证记录

### 第二台 TB323FU 的 STA 零尾问题复现（2026-11-19）

303fdedc + iPhone14 Pro 当前 App，保留原 HAL。同一 Publish/NMI 在 STA 连接前无零尾，连接5260MHz后多23字节，再次断开恢复；72/75帧直接对照，Setup变化单独计数。驱动STA切换日志没有新的Publish下发，尚未取得NAN WMI buffer或确定构帧责任层。详见 [证据与限制](303fdedc-sta-sdf-driver-analysis.md)。本轮未操作另一台平板及iPhone12。

### 自动重连双端验收通过（2026-11-19，10:18～10:21）

首版保留 Publish 的恢复实测失败，旧 NDP 终止状态阻挡新请求；仅重建 Publish 也失败。最终自动重建 attach/Publish，保留配对状态后，a0807d7 与 iPhone12 在 Setup=false 下完成主动断开、心跳超时、自动恢复、仅 iOS 点击重连、Pair Verify/NDP 和双向 13/13。编译/lint、安装通过。STA 并发及底层 teardown 未解决。详见 [完整测试](publisher-reconnect-recovery.md)。以下“待验收”保留为首版历史。

### 发布端自动恢复数据链路（2026-11-19）

代码已修改，Android assembleDebug/lintDebug 通过；APK 覆盖安装 a0807d7 成功、冷启动正常。断链后保留 Publish、重建 socket/网络请求。完整双端重连真机验证待完成，详见 [修改范围](publisher-reconnect-recovery.md)。

### Publish CSIA 修复通过（2026-11-19）

新高通库修正 Publish 入口，在 Setup=false 时仍下发 0x27=04；空口 CSIA=04 07 01。默认关闭时 iPhone 已配对设备发现、Pair Verify、NDP、双向通信通过；开启一分钟自动关闭后链路保持，关闭状态重新发布后再次连接通过。完整 [部署与验证记录](csia-publish-fix-validation-2026-11-19.md)。使用已有缓存、STA 断开；P07 和首次配对不在本次验收范围。前一库误改 Subscribe 的失败历史保留如下。

### 新高通 Publish 候选库部署（2026-11-18，18:34～18:46）

新 libwifi-hal.so（SHA1837883062e14520511f37cb9411c551421208539af4d20598b79450cc2a876c）已备份后替换、重启，文件 hash 与服务 maps 均确认。部署通过，P19 修复验证未通过：初始 false 和自动关闭后仍缺 0x27，空口 CSIA=00 07 01，iOS 明确报 missing CSIA GTKIGTKBIGTKSupported attribute；true 时才有 04 并建立双向通信。新 ELF 中仍存在 Setup 门限，待用户核对修改片段/构建输出。详细 [部署、帧号和失败证据](csia-candidate-deployment-2026-11-18.md)。Ubuntu 已自行恢复 Wi-Fi/外网200；平板恢复原网络和息屏，iPhone 执行锁屏。

### CSIA 修复产物再次核验（2026-11-18，18:30 左右）

用户要求部署下载目录的新 service 和 so。实际 SHA-256 与 a0807d7 已安装版本完全一致：service 为 b2af3812b34720cf85fe09853f4428fb73ba0211785b6968d9c8b929d96668b2，V4-ndk.so 为 26c5f7d42131d8a2eb0381e8b1e428f48e21b35edd2cad482351c8521c279bc7。当前 libwifi-hal.so 仍为旧 hash 0602a90c34734d335b00dd1d2ce7154e5b1e5b30b8ad47e8a5226ee7f9dc47ae，下载目录尚无新同名库。文件更新时间变化不能证明已包含修改；已通知用户核对真正的构建输出。没有重复覆盖相同文件、重启服务或把旧版本复测写成修复通过。

当前 service、V4 NDK 库、高通 libwifi-hal.so、VINTF XML 已备份到 Mac 分析目录 2026-11-18/vendor-before-csia-fix-1830-a0807d7，等待更新产物后继续部署。Ubuntu 未切换 monitor，iPhone 未解锁或操作。

### Publish CSIA 与 STA 尾部定位（2026-11-18）

a0807d7 + iPhone 12 保留配对缓存。信道 6 的 Setup 关闭/开启样本为 60/55 个，CSIA 分别 00 07 01 / 04 05 01 07 01。iOS 明确报 missing CSIA GTKIGTKBIGTKSupported attribute；HAL 关闭时省略 0x27、开启时携带 04，定位通过，单字段 ROM 修复未部署验收。

STA 连接 5260MHz 时 76 个 Publish 额外 23 字节；断开后同一 NMI 的代表 Publish 无额外尾部并完成 Pair Verify/NDP，iOS 双向 13/13。原始包 07/09/10 留在 Ubuntu /home/mxj/NAN/2026-11-18 和 Mac 分析目录，不入库。详见 [本轮专项记录](vendor-publish-csia-sta-analysis.md)。本轮没有 Pixel 新对照。

收尾已撤销平板临时自动连接限制、恢复原 STA 网络、停止 Android 并恢复 30 秒息屏；iPhone 执行短按电源锁屏，USB 截图为黑屏，CoreDevice lockState 查询返回 4016，未取得 passcodeRequired 二次确认。Ubuntu 一次性管理授权已安装，实际验证 prepare/capture/stop/restore；NetworkManager 恢复首次存在就绪延迟，助手免密重试后 wlp3s0 managed/connected、无 monitor、默认路由和 HTTPS 200 均确认。

2026-11-17 最终订阅修正：移除 Subscribe DeviceInfo 与降级重试。a0807d7 首次 native startSubscribe 返回 status=0 并发现 iOS 服务，未出现旧提示。首次 PIN 仍未通过；与 Bootstrap 信息承载分开跟踪。

2026-11-17：Android 新增配对缓存查询/刷新、单条删除、清空全部和删除确认。assembleDebug/lintDebug 通过，已安装 a0807d7；空列表查询、刷新、清空禁用和订阅期间管理禁用实机通过。有记录删除 UI 尚未覆盖，详见 [P25](problem-log.md#p25app-缺少配对缓存管理入口)。已有缓存的反向通信再次演示成功，删除后新 PIN 仍未通过，不能混作完整配对验收。

2026-11-16 新增反向角色真机验证：使用用户指定的 a0807d7 与 iPhone 12。iOS 发布 → Android 订阅的服务发现、缓存配对 Verification、NDP、UDP 45454 双向心跳通过；iOS 120 秒发布到期后保留独立 connection，继续收发。**首次反向 PIN 未通过验收**（引导超时），订阅 SSI 被当前 ROM 拒绝，最终有一次空 SSI 降级。本轮原方向回归仍遇到 -11987 / NDP reason=2，未重新通过双向数据验收。详见 [P24 反向流程完整记录](reverse-flow-validation.md)。以下早期记录主要是原 Android 发布方向，不能混作反向首次配对证据。


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

### iPhone 对照与 Android 反向首次配对诊断（2026-11-17）

后续正式修正：Bootstrap 请求方法固定改为已实测的 Keypad（32）；该方法通过 iOS 方法筛选的证据来自同日临时 APK 对照。元数据缺失仍未解决，首次配对不验收通过。

iPhone 14 Pro 订阅 iPhone 12：首次 PIN、认证、缓存成功，后续双方 NDP ready；不验收应用层双向心跳，因为当前 iOS 只接受 Android 消息。Android a0807d7：原请求方法 2 被 iOS 方法筛选拒绝；临时方法 32 越过方法检查后，iOS 明确报告 Bootstrap 缺少必需元数据（TXT 0 字节），仍超时。生产源码和 APK 恢复，不作为互操作修复通过。详见 [实测证据](ios-publisher-bootstrap-comparison.md)。AOSP 下载保持用户要求的停止状态。

### Apple 原版 Demo 的 Publish 对照

2026-11-16：原版 iOS Demo 真机签名构建并运行，取得普通监听 / 允许新配对两组 Publish，CSIA 均保留 GTK、SDEA 均要求 GTK，DCEA Pairing Enable 随 UI 状态变化。详见 [证据和复验限制](apple-original-publish-comparison.md)。本轮不验收新 PIN 配对、NDP 或数据收发；Android 关闭 Setup 后重连仍未修复。两端测试 App 已恢复，订阅已停止，iPhone 已锁屏。

### API 37 引导消息与发现名称（2026-11-17）

Android compileSdk 37 / AGP 9.1.1 / Gradle 9.3.1，JDK 21 assembleDebug、lintDebug 通过；解析器独立检查通过，新 APK 安装 a0807d7。发现列表支持 pairingName、modelName、vendorName，并保留编号区分设备。实机当前 SSI 189 字节未解析到有效字段，因此仅验收“未提供名称”回退显示，不验收真实设备名展示。

API 37 三参数 Bootstrap 调用实机 App 记录非空 TXT TLV 86 字节，iPhone 12 接收日志仍为 TXT 0 / SSI nil，首次配对继续失败。未修改普通 Subscribe SSI，未中断 Ubuntu 下载。详见问题台账 P24/P26 及引导专项文档。

本轮收尾：Android 已停止订阅并恢复 30 秒息屏；关闭 iPhone 12 系统配对页面后执行锁屏，devicectl 确认 passcodeRequired=true。iPhone 14 Pro 未操作。未清除配对缓存。

### 最低 Android 版本调整（2026-11-17）

仅支持 Android 17 / API 37 及以上，compileSdk / minSdk / targetSdk 均为 37；移除 API 36 Bootstrap 兼容分支。assembleDebug / lintDebug 通过，最终 APK 元数据确认三个 SDK 值均为 37。本轮未重新安装或执行双端测试，不改变先前配对问题的验收状态。

### Bootstrap 空口与 vendor HAL 版本定位（2026-11-18）

当前 APK 安装 a0807d7，iPhone 12 系统配对页复现失败。App TXT TLV 86 字节；信道 6 第 81821/81824 帧为 Bootstrap Request 的单播 SDF Follow-up 原发/Retry，均没有 SDEA；iPhone 日志 TXT 0 / SSI nil。实际 Wi-Fi HAL 运行时 V3，二进制仅读取旧六个字段；新 SSI 为 V4 字段，丢失断点已定位。确认诊断，不验收首次配对修复，未改 ROM/HAL。详见 [代码、空口和 ROM 升级位置](android17-bootstrap-hal-v3.md)。结束后 Android 停止并恢复 30 秒息屏，iPhone 12 锁屏。


### 用户构建的 Vendor V4 产物部署（2026-11-18）

a0807d7 已替换 Wi-Fi HAL 服务、添加 V4 NDK 库、将 VINTF 声明改为 4 并重启。运行版本查询 4、服务 running、maps 加载 V4 库、SHA-256 匹配。仅部署与服务初始化检查通过，未验收 Bootstrap SSI、首次配对或 NDP；仍保留此前失败结论，待双端测试。详见 [部署记录](wifi-hal-aidl-v3-v4-comparison.md#vendor-私有-v4-产物部署2026-11-18)。


### Vendor V4 Bootstrap 与首次反向配对通过（2026-11-18）

a0807d7 订阅 iPhone 12，HAL 转换 SSI=86 字节；空口 03-vendor-v4-bootstrap-ch6.pcap 第 122062 帧包含 SDEA（14），三项设备 TXT=83 字节，iOS 非空解析并显示设备名/PIN。用户输入 PIN 后首次配对成功，缓存验证通过。首轮 NDP 建立但 UDP 因 ACCESS_LOCAL_NETWORK 缺失返回 EPERM；补齐 manifest/运行时申请，assembleDebug/lintDebug 通过，覆盖安装并授权后双向通信持续超过 2 分钟，Android 70/69、iOS 68/69（采样时间不同）。撤销权限后再走 App 申请可重新获准，独立弹窗/拒绝分支未验收。未宣称完整标准 V4 或其他 ROM 兼容。证据、原始包路径和长度解释见 [专项验收](vendor-v4-bootstrap-validation.md)。

本轮收尾：停止 iOS 发布与 Android 订阅，Android 恢复 30 秒息屏；用户恢复 USB 后 iPhone 12 锁屏，devicectl 确认 passcodeRequired=true。成功配对缓存保留，未操作另一台平板。

Ubuntu 网络收尾已通过：wlp3s0mon 删除、wlp3s0 managed 并连接 lenovo-internet，默认路由恢复，清华镜像 HTTPS 返回 200；有线 SSH 保留。后续测试必须恢复网络的要求已写入 AGENTS.md。


### 2026-11-19 反向 STA 并发验证

a0807d7 订阅 iPhone12：STA 5260 MHz 下两次 NDP reason=2（其中一次 Pair Verify 成功），STA 断开后双向 29/29；重新连到另一 BSSID、5320 MHz 后已有链路持续，重新建链也通过、iPhone 15/15。不能概括连接 STA 必然失败，亦未证明与 Publish 23 字节同根因。固定信道重复对照及空口待补；详见 [时序及限制](reverse-sta-validation-2026-11-19.md)。


### 2026-11-19 b2302624 STA 双接入点检查

TB522FU Android17启用Aware声明、安装当前App通过。未连接STA及连接mxj-5G/153、mxj-5G_3/48，空口6/149共246个目标Publish无额外23字节。仅验收当前环境未复现，不验收首次配对或NDP，也未修复TB323FU。详见 [完整记录](b2302624-sta-sdf-validation.md)。


b2302624 追加 lenovo-internet（5260MHz / DFS52）测试：信道6/149共114个目标Publish均无额外23字节；保留原厂HAL、Setup=false。结论仍限于本机当前版本，未验收配对/NDP。详见 [追加记录](b2302624-sta-sdf-validation.md)。


### 2026-11-19 吞吐功能

双端代码/构建通过，Android lint通过，Java/Swift测试包和报告互通、去重/重试及10秒本地发送循环通过。b2302624 APK覆盖安装、测试入口可见。iPhone14 unavailable，真机双向吞吐、拥塞下心跳和断开重测仍待验证。详见 [测试说明](throughput-test.md)。

### 2026-11-19 libiperf3 真机替代验证

测试型号：Android 为联想拯救者 Y700 平板 **TB323FU**（a0807d7，Android 17 / API 37）；iOS 为 **Apple iPhone 12**（iPhone13,2，iOS 27.2.1）。以下吞吐样本均对应此组合。

本轮结果测于办公室存在无线干扰的环境，未在干净、受控的无线环境下测试，不能作为设备或链路的性能上限。用户计划后续在更干净的无线环境复测；当前未量化干扰对结果的影响。

以上初版自定义 UDP 测试已被内嵌 libiperf3 3.22 替代。a0807d7 发布 / iPhone12（27.2.1）订阅，清理旧配对后重新 PIN 配对和双向心跳通过。Android 四区域为配对和连接、数据链路、性能测试、配对缓存管理，默认 4 线程。

真实 TCP 4 线程：Android→iOS 64.69 Mbps、iOS→Android 50.20 Mbps；UDP 4 线程不限速：79.24 / 54.39 Mbps。切换 TCP 2 线程下行 80.51 Mbps；Android 发起 UDP 2 线程/总 100 Mbps，上行接收 70.32 Mbps，参数传递通过。Android TCP 连接数确认 1 控制 + 4 数据。各轮心跳持续，结果在双端显示。UDP 丢包包含 iOS 本地适配层，不能直接当作射频丢包；详情见专项表格。

iOS 直接 BSD socket 无路由，现通过 loopback 和 Network framework 公开 Aware 端点转发；需要 iOS 26.4+，包括转发开销。反向发现角色的新增测试连接尚未验收。测速中主动断开 iOS 时确认 5 个活动 TCP 连接随后全部释放；未重启 App，重新连接后 TCP 4 线程下行再次完成，58.27 Mbps / 72.88 MB / 10.0 秒。最新双端构建及 Android lint 通过并已安装，签名资料和原始敏感日志不提交。

收尾：停止 a0807d7 App，恢复 30 秒息屏、取消临时 STA 自动连接限制，确认已连接 lenovo-internet（5260MHz，网络 VALIDATED）。iPhone12 执行锁屏并确认 backlightState=off。成功配对保留。本轮未操作 Ubuntu 网卡、未进入 monitor；心跳检查使用平板 aware_data0 抓包。


### 2026-11-19 测速时长设置

双端增加 1～3600 秒输入，默认 10 秒；Android 控制消息 → iOS → libiperf 的时长传递已贯通，原生上限同步调整。Android assembleDebug/lintDebug、iOS iphoneos 无签名编译通过，Android 新 APK 已安装 a0807d7。新增非 10 秒时长尚未双端真机验收，本轮未安装 iOS 新版本；原有吞吐表为此前 10 秒结果。实现与待验收项见 [问题记录](problem-log.md)。


### 2026-11-19 可调时长补充安装与真机验证

iOS 使用已有本机开发签名配置重建并成功覆盖安装 iPhone 12；Android 同版 APK 额外成功覆盖安装 303fdedc。303fdedc 本轮仅确认安装，不计入以下测速组合。

a0807d7（联想 TB323FU，Android 17 / API 37）发布，iPhone 12（iOS 27.2.1）订阅，复用已有配对建立双向心跳。Android UI 将时长改为 15 秒，发起 TCP / 4 线程 / Android→iOS；iOS 运行状态收到 15 秒，完整可访问性结果为 **63.45 Mbps / 119.01 MB / 15.0 秒**。确认 Android 配置经控制消息传至 iOS/libiperf 后实际生效。办公室干扰环境，临时断开 Android STA，包含 iOS 转发开销，不能视为性能上限。该结果只验收 15 秒 TCP 单方向；iOS 本地输入发起、UDP 非 10 秒及 3600 秒长稳尚未验收。


### 2026-11-19 单端删除配对后的状态防护

发布端新增本次 Setup 接受记录、成功回调关联及缓存验证独立提示，重新 Bootstrap 使用新 PIN，避免无本次请求的成功回调直接显示新配对完成。该改动为 App 状态防护，未确定用户所述行为的底层根因，也未完成只删 iOS 配对、Android 保留的真机验收；详见 [问题台账](problem-log.md)。


### 每次发布默认配对与单端删除后重新配对（2026-11-19）

每次新建 Android Publish 默认开启 60 秒 PairSetup（含自动重建），成功或超时后关闭；UI/README 已同步。iOS 仅删除 303fdedc 记录、Android 保留缓存后，新 PIN → Setup → 成功回调通过；Android 仅删除 a0807d7 缓存、iOS 保留记录后，反向 Bootstrap 设备信息非空、新 PIN 配对通过。两种场景分别在两台 TB323FU 上完成，不能说 303fdedc 的 V3 反向路径通过；该尝试尚未完成。旧别名可能累积，未自动清全量缓存。本轮不验收 NDP/吞吐，不认定原始误报根因已修复。Android assembleDebug/lintDebug 通过，完整环境、操作及时序见 [专项记录](single-sided-repair-validation-2026-11-19.md)。


### 303fdedc 替换 HAL 后反向配对通过（2026-11-19）

备份原文件后，部署与 a0807d7 校验一致的 V4 Wi-Fi 服务、V4 NDK 和 libwifi-hal.so，VINTF 改 4；重启运行版本 4、校验一致。HAL Bootstrap SDEA SSI=86，iPhone 12 收到 TXT=83 且三项设备信息非空。清理 Android 更早的两条旧缓存、保留 iOS 配对记录，重置 Wi-Fi 会话并断开 STA 后，20:06 新 PIN 配对成功。此前几轮 PIN 未显示，iOS 有属性更新 Invalid argument，恢复因素未隔离；不能宣称无干预重配全部修复。本轮不验收 NDP/吞吐，也未抓新空口包。完整部署、失败对照与成功时序见 [追加记录](single-sided-repair-validation-2026-11-19.md#303fdedc-补齐-hal-后的反向重新配对同日追加)。


### iPhone 14 Pro 最新 App 覆盖安装（2026-11-20）

按用户要求，从当前仓库源码重新签名构建 iOS App（构建成功），通过 devicectl 覆盖安装 USB 连接的 iPhone 14 Pro（iPhone15,2），返回 App installed，bundleID=com.slab.wifiawaredemo。本轮仅验收构建及安装，不重测配对、数据链路或吞吐，没有删除配对记录或操作 Ubuntu。


### baldur-1 重配后验证失败：重复身份缓存（2026-11-20）

a0807d7 改名、iOS 删配对后新 PIN Setup 成功，但 Pair Verification 被 iOS 以 Invalid Security Parameters 拒绝。Android 三条缓存 LocalNik/PeerNik 相同、NPK 不同；实际设备 service-wifi.jar 的 PairingConfigManager 按身份首条匹配而不优先最新凭据。经用户授权仅删除旧 awarebridge-139/awarebridge-ios-138，保留新 awarebridge-141，iOS 配对不动、不重输 PIN，重新发布后 Verification/NDP 成功，双向心跳持续超过两分钟至 42/42。最终只剩新缓存，安全字段未变；未捕获失败轮次实际选中别名，保留这一诊断边界。未改 App/ROM，不把单次清理说成自动重配永久修复。完整 [证据、ROM 方法与处理建议](baldur1-repair-cache-verification-2026-11-20.md)。


### 配对身份查询实现已撤回（2026-11-20）

按用户要求，回退提交 4040c2f 的 App 入口、客户端和 Framework 补丁/工具/测试，仅保留 [问题及后续 ROM 建议](paired-peer-identity-api.md)。App 源码恢复至 bbda302；未曾部署该查询接口的 ROM，也未安装该轮 APK，因此无设备侧回滚。身份查询和同身份凭据更新均未真机验收，后续在 ROM 中处理，不标记为已修复。

回退检查补充：Android 源码与 bbda302 的差异为空，回退后 assembleDebug/lintDebug 再次通过，APK 已由回退后源码重新生成；未安装到设备。


### P07 23 字节 Extended WLAN Infrastructure 假设复核（2026-11-20）

只读旧包确认：303 抓包 33004 Publish、141/33305 Subscribe 的合法属性后各有 23 个零；Subscribe 发送地址未核对物理设备，不算 Android 订阅 STA 切换实验。522 的合法 0x1e 属性头为 1e 14 00，总长确为 23；官方 Wireshark 源码格式一致。支持优先检查属性预估长度/实际填充不一致，未证明驱动或 Firmware 责任，未修复；本轮未操作 Ubuntu/设备。见 [专项记录](sta-zero-tail-extended-wlan-hypothesis.md)。


### P07 amss20 固件反汇编（2026-11-20）

完成 a0807d7 原文件导出、SHA-256、两层 ELF/加载地址识别及内层 Hexagon 静态解码。确认 Extended WLAN Infrastructure builder 的完整头部、23 字节返回与空指针查询路径，识别末尾 Vendor 后只计长的具体嫌疑点；未动态验证实际 Tx 路径、未修复、未运行/修改/部署固件。完整 [版本、地址与证据边界](amss20-nan-disassembly-2026-11-20.md)。


### P07 三个属性调用者用途确认（2026-11-20）

配套 Data20.msc 日志字典与镜像日志标识匹配：0x0184cb44 属于 NANBCN_PrepareBeaconFrameExtended，0x0184fd28 属于 NANMAC_BuildSDFFrame，0x01a5ded8 属于 NANMAC_BuildFollowupFrame；取得源码文件及附近日志行号。更正不能将 Follow-up 的查询/填充路径当作 Subscribe 对照。未动态证明异常 Publish/Subscribe 走哪个入口，未修改固件。见 [函数名、日志标识与参数语义](amss20-nan-disassembly-2026-11-20.md)。


### P07 实验固件补丁部署（2026-11-20）

a0807d7 原件备份并校验，六指令字补丁及段哈希更新通过限定修改范围/错版本拒绝检查；QEMU 解码和容量足够、不足、恰好边界、无属性、填充前状态变化、used=0 六个合成短片段用例通过。临时加载、持久文件替换、恢复只读后重新下载固件，Service Ready、STA 连接通过；App 发布产生有效 Publish 会话通过。未验证新空口 23 零尾是否消失、跨整机重启、重新配对/NDP/吞吐或长稳，不宣称 P07 已修复。原件与一键恢复脚本已保留，详见 [部署验收与回滚](amss20-ext-wlan-patch-deployment.md)。


### 303fdedc 高通 ST197458（2026-11-20）

原件备份校验、新固件持久替换、跨整机重启、运行版本与Wi-Fi连接通过；HAL/驱动未替换，固件分区ro。23零尾及iOS发现/配对/NDP待验证。[部署说明](qcom-197458-firmware-deployment-303fdedc.md)。


### P07 补丁后 Publish 空口通过（2026-11-20）

a0807d7 保持 STA，04-ch6-restart.pcap 第544/5131帧、05-ch149-restart.pcap 第18898帧均为目标baldur-1 Publish，实际0x1e payload=20、额外零尾0，tshark与独立边界扫描一致；iOS12设备选择页发现通过。样本为三次发布会话，不扩大到反向Subscribe/NDP/长稳。用户手动锁屏已确认；Ubuntu恢复联网后用户另行启用monitor，明确要求保留。完整 [空口验收记录](amss20-ext-wlan-air-validation.md)。

### iOS 发布角色 iperf3 修复验收（2026-11-20）

a0807d7 / TB323FU / Android 17、iPhone 12 / iPhone13,2 / iOS 27.2.1，STA 保持连接，办公室干扰环境。新版双端覆盖安装；Android assembleDebug/lintDebug、iOS 真机签名构建与 JSON 协议检查通过。同一 NDP 连续五轮 4 线程/10 秒，Android 按钮 TCP 双向 98.12/69.36 Mbps、UDP 双向 94.30/70.61 Mbps（总目标 100 Mbps；丢包 0.01%/24.62%），iOS 按钮 TCP iOS→Android 65.05 Mbps。双端显示结果，Android PID 1390 保持不变、每轮恢复 server 监听，最后心跳接收 0 秒前。

修复前捕获的原生崩溃来自 libiperf 重复关闭 fd；修复后五轮正常收尾。未单独注入 CLIENT_TERMINATE，也未做长稳/其他设备/所有参数组合，不扩展验收范围。见 [定位记录](iperf-ios-publisher-fix.md)、[完整性能表](throughput-test.md)。本轮未使用或修改 Ubuntu 抓包网络状态。

### 家庭性能样本（2026-11-20 晚）

iPhone12+iOS发布、a0807d7+Android17订阅，无Android STA，TCP双向各3轮平均328.24/359.84 Mbps，UDP100双向接收100.02/99.95 Mbps、显示丢包0.00%。UDP300 Android→iOS结束无汇总，不通过。303fdedc+a0807d7原生PSK-NDP首轮TCP双向560.46/621.74 Mbps，5745MHz/80MHz/2SS；两机固件不同，非单变量A/B。第三轮a080USB失联，重复/UDP未完成。保留 [原始条件及限制](home-throughput-2026-11-20.md)。本轮不修改App/ROM，不操作Ubuntu。

### 家庭双 Android Crash 验收状态更正（2026-11-20 晚）

用户确认a0807d7发生Crash且正在高通收集，双Android验收暂停，原两向TCP速率仅为崩溃前有效样本；第三轮和后续测试未通过。保留现场，助手不再操作该设备；303对端KEEPALIVE日志和Mac文件时间已保存，a080崩溃层级与原因待高通dump。详见 [更新记录](home-throughput-2026-11-20.md)。

### TCP并行流矩阵中断（2026-11-20）

a0807d7/303fdedc原生加密NDP，4种线程数两方向共8项，仅5项完成：2线程595.18/671.43 Mbps、4线程515.77/666.85、8线程303→a080为604.01。8线程反向触发WLAN firmware hang、SYS ERROR/RAMDUMP，客户端接收超时，无有效汇总；16线程未执行，不通过整组验收。保留双端采集日志，未自动重启/改固件；根因和补丁关联待分析。[专项记录](android-tcp-streams-2026-11-20.md)。


2026-11-20 第二版高通固件：303fdedc已更新至`.197458.2`，重启后WifiHealthMonitor确认实际运行版本；第一版与原厂备份均保留且哈希核对，固件分区ro，STA连接FTTR-5G。23字节空口异常仍待验收。[部署及回滚](qcom-197458-firmware-deployment-303fdedc.md)。

2026-11-20家庭抓包尝试：新固件303fdedc在触发iPhone订阅前后掉线，用户确认无法开机、正在充电排除电量问题。无Android Publish有效样本，23字节修复未验收；Ubuntu已退出monitor并HTTPS验证联网。详见[部署记录追加](qcom-197458-firmware-deployment-303fdedc.md)。


2026-11-21 P07高通第二版实测：a0807d7部署197458.2，整机重启/STA/Publish正常；STA5180MHz两轮Publish尾部均为完整0x1e/20字节属性，无23零尾；STA断开样本无0x1e且边界完整。每组仅1目标帧，未新PIN/NDP/长稳，不排除303fdedc故障关联。Ubuntu已恢复managed和外网，iPhone已锁屏。[完整证据](qcom197458-2-a0807d7-validation-2026-11-21.md)。

### Xcode签名配置验收（2026-11-23）

真机不签名构建通过；修正xcconfig优先级并配置本机Local.xcconfig后，不带DEVELOPMENT_TEAM/Bundle ID/profile等命令行覆盖的iphoneos签名构建BUILD SUCCEEDED，codesign确认签名与App ID有效。Local.xcconfig确认为Git忽略。没有操作真机、重新安装或无线验证；模拟器静态库平台不匹配仍属明确限制。[构建说明](ios-xcode-build.md)。


### HA2E6NPB Android17部署（2026-11-23）

原件双端备份后使能Aware、部署V4服务/NDK/CSIA修正库与高通197458.2、安装已有APK。重启、功能声明、Binder版本4、实际固件版本、进程库映射和App发布均通过；跨端配对/NDP/长稳待验收。[部署与恢复记录](ha2e6npb-android17-deployment-2026-11-23.md)。


### 三种平台组合验证（2026-11-23）

Android/Swift 协议和 TXT 测试、Android assemble/lint、iOS 真机签名构建通过。Android↔Android：a0807d7 发布、HA2E6NPB 订阅，本轮 PIN 配对及双向心跳通过，4 流 TCP 发布→订阅 197.63 Mbps；反方向遇 a0807d7 CrashDump，用户要求保留，未完成整组。iPhone 14 Pro 发布、iPhone 12 订阅：已有配对、双向心跳通过，TCP 153.49/126.81 Mbps。HA2E6NPB 发布、iPhone 12 订阅：新 PIN 配对、NDP/心跳通过，TCP 119.16/34.52 Mbps。换向复用同一跨平台配对，iPhone 12 发布：TCP 128.85/145.30 Mbps，另从发布端 UI 发起 131.06 Mbps。均 4 流/10 秒、干扰环境功能样本；一次 idle receive timeout 未定因，UDP 和 2/8/16 矩阵未测。本轮 iOS↔iOS 未删除系统配对重做 PIN。最终版安装 HA2E6NPB 和两台 iPhone；a0807d7 保留崩溃状态。[完整记录](multi-platform-2026-11-23.md)。


### iPhone 12 USB 锁屏（2026-11-24）

0.05 秒 Consumer/Power 短按真机通过，SpringBoard lockstate 0→1、截图黑屏；没有使用旧 0.5 秒长按。新增脚本在已锁屏时不再按键的分支通过，手机保持锁屏。passcodeRequired=false 与 lockstate=1 同时存在，后续不能用前者替代锁屏验收。详情见[锁屏修复](iphone-usb-lock.md)。


### 四台设备统一安装最新 App（2026-11-24）

按用户要求由当前源码重新构建，Android assembleDebug 与 iOS iphoneos 签名构建均通过。a0807d7、HA2E6NPB 的 adb install -r 均 Success，设备 base.apk SHA256 与本地最新 APK 完全一致（a5b7aa6da5e714e9c783fef29cf8b56b779b24d2927f61d190bb6f1c2af6bcfd）。iPhone 12 与 iPhone 14 Pro 安装同一最终版 AwareBridge.app，devicectl 均返回 App installed（com.slab.wifiawaredemo）。本轮仅安装及收尾锁屏，未启动连接或吞吐测试，不能作为 CrashDump 修复验收。


### iOS Publish SDEA 发现信息对照（2026-11-25）

iPhone12/HA2E6NPB只验证发布与订阅，未NDP/测速/删配对。ch6帧4645、ch149帧1732确认SDEA且无设备TXT；Framework走extended分支、App只收6字节。两端6字节内容不同，HAL原始TLV/转换前后仍待定位；不能宣称名称被Framework丢弃或HAL不支持SDEA。抓包保存在Ubuntu /home/mxj/NAN/2026-11-25；网络恢复managed、外网200，iPhone12锁屏确认。14Pro本轮未USB连接，不作为该机本轮证明。[完整分析](ios-publish-sdea-match-2026-11-25.md)。
