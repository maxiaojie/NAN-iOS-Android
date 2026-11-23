# 清除配对后反向 Bootstrap 超时（2026-11-20）

## 环境及复现

用户删除 iPhone12 和 a0807d7 配对记录，iPhone 发布 `_aware-bridge._udp`，Android 订阅后点击配对。App 提示“配对方式协商失败”。本轮只读取既有日志与截图，未删除缓存、重启服务、安装 App 或抓空口。Android 未连接 STA，App 最后安装时间 2026-11-19 19:21:05。iOS 27.2.1；日志发布会话 bundle 为 `com.slab.wifiawaredemo`。

## 确认证据

Android 14:27:57.991 下发 Bootstrap，method=32（PIN keypad），TXT TLV=60 字节；AIDL service 确认 sdeaLength=60，HAL 14:27:57.997 返回请求接受 status=0。14:28:18.000 Framework 的 HAL Pairing Confirm Timeout 定时器触发，message type=8（Bootstrap timeout）；随后 bootstrapping request reject reason=1，App 收到 onBootstrappingFailed。没有证据表明这是对端明确拒绝。

Ubuntu Android17 WifiAwareStateManager.java 的 MESSAGE_TYPE_BOOTSTRAPPING_TIMEOUT 分支自行生成 NAN_BOOTSTRAPPING_REJECT + INTERNAL_FAILURE。这解释了 reason=1；日志标签虽然叫 Pairing Confirm Timeout，实际处理的是 Bootstrap。

iOS 日志时间约比 Android 早 1.6 秒：14:27:56.414 收到 Android Follow-up，包含 SDEA 和 PairingBootstrapping Request，Token=6、Methods=[keypadPinCode]。14:27:56.420 deviceaccessd 成功解析 pairingName=baldur-1、vendorName=LENOVO、modelName=TB323FU；创建 Responder，会话配置 AskForConsent。14:27:56.438 出现 GetDevices failed / NSFileReadNoSuchFileError / ENOENT、No discovery for appID: com.slab.wifiawaredemo；随后上报 DADevice（state Invalid、flags AppSessionPaired），14:27:56.442 wifip2pd 成功 attach responder session。当前可用日志在该请求后没有显示完成 Bootstrap 响应。14:34 截图中系统发布页仍为空，提示附近设备发送配对请求后将在此显示。

## 判断与边界

请求确实到达 iOS，SSI/设备信息未丢失，PIN keypad 被识别。当前失败点收窄到 iOS 系统用户授权/配对响应推进路径；不能归因于 alias 选到旧 NPK，因为尚未进入后续 Pairing Setup 密钥协商。App 通用“协商失败”掩盖了 Framework 等待确认超时。

No discovery、ENOENT、AppSessionPaired 是排查线索，尚不足以证明其直接导致 UI 不显示或响应未发送；需重新创建 iOS 系统配对页，与现有页对照，抓取 deviceaccessd、系统 UI 及 wifip2pd 连续日志，必要时抓空口验证是否发送响应。不能仅据该错误宣布 iOS 配对数据库损坏。

原始日志和截图存于 `/Users/mxj/workspace/nan-capture-analysis/2026-11-20/bootstrap-failure/`，不提交包含潜在密钥的原始日志。Ubuntu 本轮未进入 monitor。未改变 iPhone 的锁屏状态，便于用户继续当前手动测试。

## 用户已验证的触发条件（2026-11-20 补充）

用户明确确认：两端配对成功后，仅 Android 删除配对缓存、iPhone 仍保留配对记录，再由 iPhone 发布、Android 订阅并重新发起配对，会出现本问题。这一触发条件已由用户验证，不再列为仅根据日志提出的猜测；本轮未由代理独立重复该对照。

结合既有日志，表现为新 Bootstrap 和设备信息到达 iOS，但 iOS 没有完成新配对响应，Android 20 秒超时。此处确认的是单侧删除造成配对状态不一致这一复现条件；iOS 内部具体状态判断，以及 ENOENT / No discovery / AppSessionPaired 各字段的直接因果，仍未确定。Android 删除本地缓存不会同步删除 iOS 配对记录；App 不应将该超时简单描述为 PIN 方式不兼容，也不能承诺自动删除对端系统记录。
