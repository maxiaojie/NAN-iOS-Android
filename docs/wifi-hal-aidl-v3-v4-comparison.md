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

## 高通是否已经提供匹配版本

2026-11-18 用户询问高通是否提供 V4 HAL。公开检索未取得可证明“本平板对应 Wi-Fi 芯片/BSP 已交付完整 V4 Bootstrap SSI 支持”的高通发布说明或提交；CodeLinaro 页面访问受 robots 限制。搜索不到不代表高通没有提供，不能据此判断其私有 BSP 交付状态。

已确定的只有 AOSP Android 17 提供 V4 Binder 接口/默认服务，以及当前平板实际运行 V3。android.hardware.wifi-V4-ndk 是 AOSP 接口版本，不是 libwifi-hal-qcom.so 的通用产品版本号。需要向对应 BSP/WLAN 维护方核对高通库、驱动、固件是否支持通过 wifi_nan_bootstrapping_request 在 PIN Keypad 引导请求的 SDEA 中携带 SSI，而不仅是存在该函数指针、支持 Service Managed 方法或已经具备基本 NAN 配对。尚未访问用户隔离服务器，未确认该私有源码是否已有补丁。

## 保持 V3 外部版本进行定向验证的可行性

2026-11-18 用户提出只修改 V3 Bootstrap 方法携带 deviceInfo。工程上可对 vendor 端定向回移植 SSI 来验证，但不能只改 initiateBootstrappingRequestInternal 的函数体：当前 V3 reader 已在进入方法前跳过新字段，必须同时让原生 NanBootstrappingRequest 的类型定义、Parcel reader/writer 和使用它的代码认识 SSI，再补齐 AIDL→legacy 转换及下游高通序列化。Framework 已写新字段，App 无需为此再改一套协议。

只在自有匹配构建中作诊断时，可以保持旧方法签名并验证新字段通路；应使用生成源码的维护流程，不把直接修改生成的 C++ 文件当作持久修复。类型内存布局变化后需重编译匹配的服务/库，不能只替换不匹配的单个 NDK so。旧请求没有尾部字段时仍须兼容读取。

正式产品不应修改冻结 aidl_api/3 内容或悄悄扩展 V3 却沿用原 hash/version：这已构成 parcelable 接口变更。AOSP 已有与 Framework 匹配的完整冻结 V4，优先在 vendor Wi-Fi 服务局部引入 V4，无需因此升级整个 Vendor；没有新增 Binder 业务方法，工作重点仍是字段转换和高通下游能力。保持 V3 的私有回移植可用于验证，但不作为标准 V3 兼容交付方案。当前未访问隔离 ROM 源码、未制作 ROM 补丁、未验证高通对 PIN Keypad SSI 的支持，不能预先承诺仅改 reader 即可配对成功。

### 用户核对高通 legacy 结构体后的范围收敛

2026-11-18 用户在隔离 ROM 代码中确认：putNanBootstrappingReq 使用的 const NanBootstrappingRequest 已有 service_specific_info_len 和 sdea_service_specific_info_len 字段。这是用户提供的代码核对结果，本助手未直接访问其私有实现。说明高通 legacy 请求类型已预留普通 SSI/SDEA SSI，不应预先要求升级高通全部 HAL、驱动或固件。

应区分同名的两个类型：AIDL NanBootstrappingRequest（vendor V3 reader 不认识新增 serviceSpecificInfo）与高通 legacy NanBootstrappingRequest（用户确认已包含长度字段）。优先补齐 vendor AIDL 类型/反序列化及 AIDL→legacy 的 SDEA SSI 拷贝；仅改转换函数仍无法恢复此前被 V3 reader 跳过的数据。沿用 Framework 已构造的数据原样透传到 sdea_service_specific_info[_len]，不误写为普通 service_specific_info，不额外重复套 TXT/SDEA 头。

下一项代码核对是 putNanBootstrappingReq 是否在长度非零时真正序列化 SDEA SSI，以及有无只对 Service Managed 方法生效的条件；字段存在本身不能证明 PIN Keypad 路径已使用它。若序列化已具备，最小修复很可能只需 AIDL 接收/适配层，不必修改高通底层。仍需以修复后 Follow-up 出现 SDEA、iOS 收到非空 TXT 和首次 PIN 成功验收。本轮未修改 ROM。

### 单字段自定义 V4 的技术边界

2026-11-18 用户提出在 Vendor 新增 V4，仅给 NanBootstrappingRequest 添加 SSI，并在 AIDL→高通 legacy 转换中赋值。从当前发送路径看，此方案可让双方这个请求对象的字段顺序对齐，具备解决已确认 Parcel reader 断点的条件；前提是目标服务确实改为编译/链接新生成的 NDK 类型，且高通 putNanBootstrappingReq 已序列化 SDEA SSI，不能只增加版本目录但继续链接 V3。

但标准 V4 还包含本文列出的其他六项类型变化，仅增加一个字段形成的是私有接口版本，其 API/hash 与 AOSP 标准 V4 不同。工程验证可明确按私有变体处理；正式使用建议引入完整标准 V4 接口快照/对应源定义，不照抄标准 V4 hash 来标记不同内容。完整接口定义的引入不等于升级整个 Vendor，也不意味着硬件必须支持所有新增可选能力；本次优先实现 Bootstrap SSI 的实际传递并按真实能力上报。用户隔离源码尚未修改，方案未构建或上机验收。

### HIDL 跨版本继承与 Stable AIDL 快照的区别

2026-11-18 用户询问以前接口是否可继承低版本。源码核对：hardware/interfaces/wifi/1.6/IWifiNanIface.hal:36 明确声明 `interface IWifiNanIface extends @1.5::IWifiNanIface`，1.5 同样 extends 1.4。这是 HIDL 显式跨版本接口继承。HAL 是功能层，HIDL/AIDL 是接口定义与通信技术，不应说成“HAL 能继承、AIDL 不能”。

当前 Stable AIDL 的 aidl_api/.../4 不是继承 3 的增量目录，而是完整冻结快照。用户可以只编辑当前输入 AIDL 中需要的字段，再通过 API 更新/冻结流程生成完整新版本，无需手工重新编写所有旧文件。旧版本可共存、请求新字段被旧 reader 忽略，属于协议兼容，不代表版本目录继承。完整标准 V4 定义的回移植仍需编译与运行回归，不能预先保证厂商分支没有适配问题；单字段私有版本的边界见前文。本次检查的 AOSP Wi-Fi HAL Java 代码存在按服务版本 gating 的逻辑，未搜索到 isServiceVersionAtLeast(4) 的调用；这不是对用户隔离 Framework 分支的保证。
