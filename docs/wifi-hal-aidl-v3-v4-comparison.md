# Wi-Fi AIDL HAL V3 与 V4 的冻结接口差异

日期：2026-11-18。按 Ubuntu 下载的 Android 17 android17-release 源码直接对比，不以 SDK/API 版本代替 vendor HAL 版本。

源码根目录 `/home/mxj/workspace/aosp-android17`；hardware/interfaces 提交 `0162af698935100a590b7359581ac8b1b80693e5`。比较目录：

```text
hardware/interfaces/wifi/aidl/aidl_api/android.hardware.wifi/3/
hardware/interfaces/wifi/aidl/aidl_api/android.hardware.wifi/4/
```

## 完整差异范围

递归比较全部 .aidl 文件：V3 为 140 个，V4 为 141 个；删除 0 个、修改 6 个、新增 1 个。差异全部属于 NAN 数据结构或枚举。

`IWifi`、`IWifiApIface`、`IWifiChip`、`IWifiChipEventCallback`、`IWifiEventCallback`、`IWifiNanIface`、`IWifiNanIfaceEventCallback`、`IWifiP2pIface`、`IWifiRttController`、`IWifiRttControllerEventCallback`、`IWifiStaIface`、`IWifiStaIfaceEventCallback` 的冻结 AIDL 文件逐字节相同。

因此，这次 V3→V4 **没有新增 Binder 业务方法**，无需为“新增 V4 方法”凭空添加接口函数；但已有方法参数/回调的 parcelable 新字段必须真正贯通，编译成功不等于字段实现完备。版本及接口哈希等生成元数据仍会变化。这个对比只说明冻结接口差异，不是两个 Android 版本全部 HAL 实现、驱动或固件变更的比较。

| 文件 | V4 相比 V3 的完整新增内容 | 意义 |
|---|---|---|
| NanBootstrappingRequest.aidl | `@nullable byte[] serviceSpecificInfo` | Framework 向 HAL 下发 Bootstrap 请求时携带 SSI，用于 SDEA Service Info |
| NanBootstrappingRequestInd.aidl | `@nullable byte[] serviceSpecificInfo` | HAL 向 Framework 上报收到的 Bootstrap 请求 SSI |
| NanBootstrappingResponse.aidl | `NanBootstrappingMethod responseBootstrappingMethod` | Framework 向 HAL 回复 Bootstrap 时明确携带响应方法 |
| NanBootstrappingMethod.aidl | `BOOTSTRAPPING_NONE_MASK = 0` | 显式定义零值，不代表原有 PIN/QR 方法位被重新编号 |
| NanCipherSuiteType.aidl | `GROUP_KEY_CCMP_128_MASK = 1 << 4`、`GROUP_KEY_GCMP_256_MASK = 1 << 5` | 增加两个组密钥 cipher 的 HAL 位掩码定义 |
| NanCapabilities.aidl | `int supportedPeriodicRangingIntervals` | 上报支持哪些周期测距间隔；只有 supportsPeriodicRanging=true 时有效 |
| NanPeriodicRangingInterval.aidl（新增文件） | 128、256、512、1024、2048、4096、8192 TU 的七个位掩码 | 为上述测距间隔能力 bitmap 定义取值 |

## 与当前 iOS 配对最直接的三项

### Bootstrap 请求发送 SSI

V3 请求仅有 peerId、peerDiscMacAddr、requestBootstrappingMethod、cookie、isComeback、discoverySessionId 六个字段；V4 在末尾增加 serviceSpecificInfo。

`IWifiNanIface.initiateBootstrappingRequest(cmdId, request)` 方法在 V3 就存在，本次不是新增此 HAL 方法，而是拓展它的 request 参数。App API 37 新重载使用这个新字段。

当前 a0807d7 是 Framework 写新字段、vendor V3 reader 跳过新字段，直接导致无法把设备 TXT 送给后续 HAL 实现。运行时与空口证据详见 [HAL V3 断点](android17-bootstrap-hal-v3.md)。

### Bootstrap 请求接收 SSI

`NanBootstrappingRequestInd` 是接收方事件数据，区别于发送请求对象。V4 增加 SSI 后，接收端才能通过这个 HAL 事件把对端的 Bootstrap SDEA 信息交给 Framework。

V3 缺少的是这个 Bootstrap 事件的 SSI 字段，不代表 V3 所有 NAN 发现事件都不能携带设备信息。当前 Android 发现 iPhone 时 189 字节不可解析的 SSI 属于发现回调，不能直接归因为本字段缺失。

### Bootstrap 响应选择方法

V4 的 `NanBootstrappingResponse` 增加 responseBootstrappingMethod，配合已有的 respondToBootstrappingIndicationRequest 方法使用。V3 只能从这个 parcelable 传 bootstrappingInstanceId、acceptRequest、discoverySessionId。

不要将新增响应字段与我们之前将请求方法由 Display=2 改为 Keypad=32 混为一项：V3 的 Request 本来就有 requestBootstrappingMethod，PIN Display=2 / Keypad=32 在 V4 中取值没有改变。

## 组密钥与周期测距的解释边界

V4 新增的组密钥 HAL mask 为 16、32；它们是能力位掩码，**不是空口 CSIA 的 cipher suite ID**，不能把 mask=16 当作 CSIA ID=16。

新增组密钥枚举与此前 GTK 问题属于同一功能领域，但不能因此断言升级 V4 就会修复 PairSetup=false 的 GTK 声明矛盾。V3 固件在开启 Setup 时已有支持 GTK 的样本；是否正确构造 CSIA/SDEA 还取决于能力转换和厂商实现，需要升级后的抓包验证。

周期测距本身及 supportsPeriodicRanging 在 V3 已存在；V4 新增的是可支持间隔集合的能力表达。NanPeriodicRangingInterval 使用从 bit 0 到 bit 6 的掩码，分别对应 128～8192 TU。1 TU=1024 微秒；这不是新的业务数据心跳参数。

## 对 ROM 回移植范围的影响

对当前只带 V1～V3 的 Vendor：需要导入匹配的 V4 冻结接口/生成库、更新 service 链接依赖和 VINTF 声明，并补齐上述新字段在现有实现中的转换。没有新的 IWifi* 方法，不代表只改依赖库名就能完成。

当前配对问题优先核对：

```text
发送：AIDL NanBootstrappingRequest.serviceSpecificInfo
   → legacy NanBootstrappingRequest.sdea_service_specific_info[_len]
   → 高通 wifi_nan_bootstrapping_request 的 TLV 处理

接收：legacy Bootstrap Request indication 的 SSI
   → AIDL NanBootstrappingRequestInd.serviceSpecificInfo
   → Framework 回调

响应：AIDL NanBootstrappingResponse.responseBootstrappingMethod
   → legacy response_bootstrapping_method
```

后两条为 V4 回移植的检查要求，不作为本轮已经抓到相应方向故障的结论。高通结构体定义、二进制 ABI 以及下层支持仍需配套核对。升级控制文件和编译/验收步骤见 [ROM 升级位置](android17-bootstrap-hal-v3.md#rom-升级-v4-的控制位置)。

本轮仅执行源码接口对比和文档更新，没有操作设备、修改 ROM 或重新验证配对。
