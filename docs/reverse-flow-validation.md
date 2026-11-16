# iOS 发布 → Android 订阅、配对与数据连接

日期：2026-11-16，Mac 主机时间（Asia/Shanghai）。代码新增角色切换，保留原 Android 发布方向。本轮仅使用用户指定的 **TB323FU a0807d7 / Android 17** 与 **iPhone 12 / iOS 27**；未操作另一台 303fdedc。Android Wi-Fi 开启但没有 STA 连接。平板系统日期显示 11 月 12 日，与主机时间不同，不能直接按日志时间混合排序。

## 实现

- Android：独立 SubscriberController，Active Subscribe，同一正式服务名，PIN Keypad 引导、PIN 输入、Pairing Setup/Verification 回调和缓存标记；用户显式选设备、配对、连接。90 秒配对超时，停止清除 PIN；拒绝非主动配对请求。
- iOS：增加 Publish entitlement 与 Publishable 服务声明，系统 DevicePairingView 负责首次配对；NetworkListener 提供原始 UDP。配对页与应用数据监听分开使用，避免同时占用同服务发布资源。
- 反向 NDP 请求不设置原发布方向固定 PSK。配对密钥管理仍由系统/ROM负责，不在应用导出密钥；本轮未独立验证实际空口加密强度。
- iOS 固定监听 UDP 45454。Android 优先读框架端口，缺失时使用此应用约定端口；必须仍通过 Aware Network 绑定 socket。已配对并不代表端口已通知成功。
- Android 主动发送 hello，然后每 5 秒发送 heartbeat；双方回复 ACK。对端地址/端口、session 与 ackSession 均验证，不允许 ACK 循环。
- 已接入的 iOS UDP connection 由单独 Task 持有，停止时连同 listener Task 一起取消；发布会话到期不再连带取消数据连接。

## 排查过程（包括失败方案）

### 1. Android 带 SSI 的订阅失败

最初带 `DeviceInfo.build()` 的 SubscribeConfig，其他配对配置为 Setup/Verification/Cache=true、Keypad、PASN-128。Attach 成功，但 native startSubscribe 返回：

```
NAN_SUBSCRIBE
NanErrorTranslation: Status: 5 Error Info[value 28]: Invalid TLV type
notifyStartSubscribeResponse: status=5, subscribeId=0
onSessionConfigFailLocal: isPublish=false, reason=5
```

同设备、同服务、同配对参数，去掉 Subscribe SSI 后返回 `status=0 / NAN status success`，进入 onSubscribeStarted，之后收到 iOS 服务发现及缓存验证回调。确认 **当前带 SSI 请求被设备底层拒绝、空 SSI 对照可启动**；尚未定位具体 TLV 的构造与拒绝分支，不把 `value 28` 当作已确认的 TLV 编号。

最终代码先保留完整设备信息请求；若初始化配置失败，只重试一次空 SSI，并显示首次配对可能不可用的提示。这不是修复 ROM，也不代表任何配置失败都由 SSI 引起。无 SSI 的降级只确认已配对设备可用。

### 2. iOS 系统配对页发布冲突

iOS 同时已有旧 Demo `_sat-simulation._udp` 与应用 listener `_aware-bridge._udp` 发布。再打开系统配对页时，deviceaccessd 创建发布端返回 `handleError: error=5`，页面仍展示等待提示。停止应用 listener 后单独打开配对页，系统成功激活 Pairing Mode，Android 收到发现及缓存验证。

确认分开执行可用；**error=5 的具体资源/服务冲突原因没有独立证实**。最终 UI 要求先配对、关系统页、再开始数据发布；监听运行中禁用配对按钮。

### 3. PIN 引导尚未通过

在系统配对页成功发布、Android 显示已配对之后，点击 Android 配对按钮重试。iOS 日志确认收到来自 Android 的 Pairing Bootstrapping Request，Methods=`keypadPinCode`，建立 Responder pairing session。其设备信息为 `matchedPairingName=''`，Android 约 20 秒后报告引导失败，系统页没有出现可输入/读取 PIN 的步骤。

**推测：** 缺少订阅设备信息可能影响系统 UI/配对流程；另有缓存配对存在，不能认定为全新未配对场景。没有删除两端现有配对，也没有验证首次 PIN Setup 成功。因此本轮仅验收已有配对 Verification 和反向数据通信；新代码的首次反向 PIN 路径尚未验收。可先用原方向完成配对，再切换反向连接。

### 4. NDP 安全参数与端口缺失

沿用原方向 `setPskPassphrase` 的初次请求：NDP Confirm=false，reason=2。移除该设置后观察到 NDP Confirm=true、reason=0。后续仍出现过一次 reason=2；重新订阅、缓存验证后正常，不能归结为固定 PSK 的单一原因已修复。

成功的 NDP 中，Android 收到 responder indication `appInfo.size()=7`，Confirm 的 `appInfo.size()=0`；框架最后交给应用：

```
IPv6=/fe80::…%aware_data0
port=0, transportProtocol=-1
```

本轮未抓空口，无法确认 iOS 未发送、HAL 丢失，还是格式解析不兼容；不能仅凭长度断言消息内容。默认 iOS 动态 UDP 端口不能被应用取得，导致等待地址/端口超时。改用两端约定 45454 后，实际双向 JSON 心跳与 ACK 通过。

### 5. Android 主线程异常

反向初始化曾在主线程 `DatagramSocket.connect(remote)` 触发 NetworkOnMainThreadException。移除该调用，改为保存远端地址、由原接收线程和定时发送线程处理报文；仍检查报文源地址/端口。修复后反向双向通信成功，未再次出现该异常。

### 6. 发布超时与数据连接生命周期

旧实现 19:37:02 启动发布，19:39:02 系统在 120 秒后报 publisherTimeout（-11989）；收发约停在 29/29。虽然请求公开 `wifiAware(_:active:)` 的 3600 秒时长，日志仍为 timeoutAfter=120，不能声称时长请求生效。

修复为 accepted connection 独立 Task。19:47:02 发布、19:49:02 listener 到期，应用记录 `Publisher expired; retaining the active UDP connection`；19:49:28 iPhone 仍显示双向通信，46/45，最近接收 0 秒。19:51:56 再次取证 iPhone 104/103、Android 103/102，最近接收 0 秒，约四分钟持续通信。说明该设备的发布到期不必结束已持有的数据连接，前次中断与应用的 Task 生命周期处理有关。

发布到期后只保留这一条已接入连接；新连接须停止后重新发布。未承诺永久发布、后台通信或无限时长。

### 7. 原方向回归仍有 NDP 失败

切回 Android 发布 / iOS 订阅，打开一分钟配对窗口后能发现所选设备并进入 NDP，但多次 iOS 返回 -11987，Android NDP Confirm=false、reason=2；自动有限重试也未恢复。重新发布及切换 Android Wi-Fi 后仍复现。本轮**未完成原方向双向数据回归验收**，不能用此前历史成功记录替代。此现象与既有 P08/P09 链路协商失败相符，但尚不能证明与角色切换无关，也未定位根因。未删除已有配对。

## 验收边界

- Android / iOS 构建、Android lint、跨语言 JSON fixture 检查通过。
- 两端新 APK/App 实际安装，USB 操作角色切换、系统配对页、订阅和连接。
- 带 SSI 失败 → 自动降级空 SSI → 发现 → 缓存配对验证：实机通过。
- 反向 NDP、固定端口首包、双向 heartbeat/ACK：实机通过。
- listener 120 秒到期后继续持有连接：约四分钟持续收发，104/103 与 103/102；截图采样不同步，差一不等于丢包率结论。
- iOS 停止后 Android 收到 onLost，提示链路已断开；mClients、mUsedNdis、mNetworkRequestsCache 均清空。
- 首次反向 PIN、无缓存新配对、其他 Android ROM、TCP、后台、长时间压力：未通过或未测试，不以已有配对替代首次配对验收。

测试结束：停止双端应用，Android 的 Aware 客户端、NDI 占用与网络请求清空；恢复测试前 30 秒息屏设置。iPhone 使用 USB 短按 Power 锁屏，核验 `passcodeRequired=true` 且主屏 `backlightState=off`。未修改用户的自动锁定选项。

公开接口依据：[Apple wifiAware(_:active:)](https://developer.apple.com/documentation/network/listenerprovider/wifiaware(_:active:))、[Apple WAPublisherListener](https://developer.apple.com/documentation/wifiaware/wapublisherlistener)、[Android Aware pairing](https://developer.android.com/develop/connectivity/wifi/wifi-aware)。原始含安全信息系统日志、PIN 截图和签名资料不入库。
