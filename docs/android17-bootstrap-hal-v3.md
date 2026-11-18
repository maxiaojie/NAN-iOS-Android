# Android 17 Bootstrap SSI 在 Wi-Fi HAL V3 边界丢失

日期：2026-11-18，Asia/Shanghai。问题索引 P24。此文补充并收敛此前“App 非空、iOS 接收为空，但尚未定位层级”的结论。

## 结论

a0807d7 的 App 和 Wi-Fi Java 模块已经支持 Bootstrap message，但 vendor Wi-Fi HAL 仍为 AIDL V3。Bootstrap 请求的 `serviceSpecificInfo` 是 V4 才加入的字段；设备实际 V3 NDK 库的反序列化只读取旧六个字段，随后跳过剩余 Parcel 数据，因此无法把新 SSI 交给后续原生 HAL 实现。

本轮同时取得运行时 HAL 版本、实际加载库、设备二进制读写逻辑、单播空口帧和 iPhone 接收日志。已经确认有一个足以导致此次元数据缺失的 HAL 接口版本断点，不再仅把范围泛称为 Framework/HAL/固件。尚未部署升级后的 vendor HAL，不能宣称首次配对修复，也不能证明 V4 升级后下游高通 HAL、驱动、固件一定支持此数据。

## 环境与源码

- Android：TB323FU，USB a0807d7，API 37；固件 `Lenovo/TB323FU_PRC/TB323FU:17/CQ2A.260712.001/maxj9_227.2611121127C:userdebug/test-keys`。
- iOS：USB iPhone 12，使用本项目发布角色的系统允许配对页面；未清除缓存，未操作另一台 Android 或 iPhone 14 Pro。
- App：最低 API 37，直接调用三参数 `initiateBootstrappingRequest`，方法 PIN Keypad（32）。设备信息通过 `WifiAwareManager.createTxtRecordTlvBuffer` 生成。
- Ubuntu：`/home/mxj/workspace/aosp-android17`，分支 android17-release。Wi-Fi 模块提交 `67beff9acf0e91046d2a9a36a68cb06ef263d1f1`，hardware/interfaces 提交 `0162af698935100a590b7359581ac8b1b80693e5`。
- AOSP 下载已完成才切换 monitor；抓包目录 `/home/mxj/NAN/2026-11-18/`。Mac 副本 `/Users/mxj/workspace/nan-capture-analysis/2026-11-18/`。原始包和完整日志不提交 Git。

## 空口：Bootstrap 是什么报文

主证据文件 `01-android-bootstrap-ch6.pcap`，信道 6。第 **81821** 帧，10:24:50.776913；第 **81824** 帧为同一 SN=1119 的 Retry，不能算两次独立请求。

| 字段 | 第 81821 帧 |
|---|---|
| 802.11 Type/Subtype | Management / Action，0x000d |
| Category / Public Action | Public Action 4 / Vendor Specific 9 |
| WFA OUI / subtype | 50:6f:9a / NAN 19 |
| 目的地址 | iPhone 的单播 NMI |
| SDA（属性 3） | 长度 9；Service Control=0x02，即 Follow up |
| SDA Service Info Present | false |
| NAN Pairing Bootstrapping（属性 44） | 长度 5；Type=Request（1）；Methods=0x0020，即 PIN Keypad |
| SDEA（属性 14） | **不存在** |
| 属性序列 | 3、44、15、42、18、18、43、11、221、221 |

这里不能只依据 SDA 的 Service Info Present=false 就判定 SDEA 数据缺失：完整属性序列也没有属性 14，才是本帧没有 SDEA 的证据。帧长 313 字节（含 radiotap）。没有发现可承载本次 86 字节 TXT 的 SDEA，不能解释为“iOS 收到 TXT 后仅因键值不符合而忽略”。

App 点击配对首先调用 Bootstrap API，对应 **NAN SDF Follow-up + Pairing Bootstrapping Request 属性**；它不是 Publish/Subscribe，也不是已经开始 PIN 认证。Bootstrap 成功、用户输入 PIN 后才调用 `initiatePairingRequest`，进入后续认证流程；本次止于 Bootstrap 元数据检查，没有产生成功的首次 PIN 认证。

其他抓包：`00-ch6-before-android-start.pcap` 是 Android 未正确唤醒时的前置窗口，不作为 Bootstrap 证据；`02-android-bootstrap-ch149.pcap` 未捕获对应请求（第二次点击发生在该窗口结束后），不能用它推翻信道 6 的结果。固定抓包包装器在超时结束时返回 137，但文件可正常用 tshark 读取。

## App 与 iOS 日志时序

Android 自身时间滞后，不能直接拿它与 Ubuntu/iOS 的墙上时间相减。

- Android 11-14 11:04:32.370：App 输出 `Bootstrap request: API 37, TXT TLV bytes=86`；NativeApi 同时记录 transactionId=783、method=32。
- Ubuntu 11-18 10:24:50.776913：第 81821 帧发出 Bootstrap Follow-up，无 SDEA。
- iPhone 11-18 10:24:50.798367：`TXT Record size: 0 bytes`；随后 `Received Pairing SSI: nil`、`Missing required pairing metadata in Bootstrap Request`。
- Android 11-14 11:04:52.385：`bootstrapping request reject, reason=1`。
- 第二次点击在 iPhone 10:27:12.996 再次出现 TXT 0 / SSI nil / Missing required pairing metadata，未作为新的空口样本。

## Java 与原生交界的证据

已经提取设备 `/apex/com.android.wifi/javalib/framework-wifi.jar`、`service-wifi.jar`，用 SDK dexdump 检查相关实现；没有替换设备系统文件。

1. `DiscoverySession.initiateBootstrappingRequest(PeerHandle,int,byte[])` 把 message 传到 WifiAwareManager，并经 IWifiAwareManager Binder 进入 WifiAwareServiceImpl。
2. ServiceImpl 将 SSI 交给 StateManager；后者放入 `MESSAGE_BUNDLE_KEY_SSI_DATA`，DiscoverySessionState / NativeApi 继续下传。
3. NativeApi 在 vendor Wi-Fi HAL 分支调用 WifiNanIface；WifiNanIfaceAidlImpl 的 `createNanBootstrappingRequest` 对非空 SSI 设置 `request.serviceSpecificInfo = joinByteArrays(sdeaHeader, ssi)`。
4. 设备 Java `NanBootstrappingRequest.writeToParcel` 在 discoverySessionId 后调用 writeByteArray，写入 serviceSpecificInfo。不存在 App 只调用了旧两参数重载的情况。
5. **vendor 接收端是 V3**，未读取该第七字段，详见下文。

不能把 SSI 的 App 长度 86 与 HAL 最终长度直接等同：Framework 可能按资源开关补 SDEA 头。本轮没有动态抓取 Binder payload，也没有注入进程；断点由设备读写实现和版本差异确认。

### 完整调用关系与源码位置

以下为本设备使用的 vendor Wi-Fi HAL 路径。Java 源码根分别为 `packages/modules/Wifi/framework/java/android/net/wifi/aware/`、`packages/modules/Wifi/service/java/com/android/server/wifi/`；C++ 源码根为 `hardware/interfaces/wifi/aidl/default/`。行号对应本文记录的 android17-release 提交。

```text
App 进程 / framework-wifi.jar
DiscoverySession.initiateBootstrappingRequest(peerHandle, method, message)
  → WifiAwareManager.initiateBootStrappingSetupRequest(..., ssi)
  → IWifiAwareManager.Proxy.initiateBootStrappingSetupRequest(...)
    ══ Binder：App → system_server ══
WifiAwareServiceImpl.initiateBootStrappingSetupRequest(..., ssi)
  → WifiAwareStateManager.initiateBootStrappingSetupRequest(..., serviceSpecificInfo)
    → 状态机入队 COMMAND_TYPE_INITIATE_BOOTSTRAPPING_REQUEST
    → 状态机分发，取 MESSAGE_BUNDLE_KEY_SSI_DATA
  → WifiAwareStateManager.initiateBootstrappingRequestLocal(..., ssi)
  → WifiAwareDiscoverySessionState.initiateBootstrapping(..., ssi)
  → WifiAwareNativeApi.initiateBootstrapping(..., ssi)
  → WifiNanIface.initiateBootstrapping(..., ssi, sdeaHeader)
  → WifiNanIfaceAidlImpl.initiateNanBootstrappingRequest(...)
    → createNanBootstrappingRequest(...)
    → request.serviceSpecificInfo = joinByteArrays(sdeaHeader, ssi)
  → android.hardware.wifi.IWifiNanIface.Proxy.initiateBootstrappingRequest(cmdId, request)
    ══ Binder：system_server → vendor Wi-Fi HAL ══
    → NanBootstrappingRequest::readFromParcel（当前 V3 在这里跳过 SSI）
WifiNanIface::initiateBootstrappingRequest(...)
  → WifiNanIface::initiateBootstrappingRequestInternal(...)
    → convertAidlNanBootstrappingInitiatorRequestToLegacy(...)
    → legacy_msg.sdea_service_specific_info_len / sdea_service_specific_info
  → WifiLegacyHal::nanBootstrappingRequest(...)
  → global_func_table_.wifi_nan_bootstrapping_request(id, ifaceHandle, &msg_internal)
  → 厂商实际函数指针实现 → 驱动/固件 → 空口 NAN SDF Follow-up
```

| 层级 | 文件及行号 | 主要动作 |
|---|---|---|
| App SDK | framework/.../DiscoverySession.java:434 | 三参数入口，message 原样传下 |
| App SDK | framework/.../WifiAwareManager.java:821 | PeerHandle 转 peerId；调用 Binder |
| App ↔ system_server | framework/.../IWifiAwareManager.aidl:81 | Binder 方法的 SSI 参数 |
| Wi-Fi Service | service/.../aware/WifiAwareServiceImpl.java:777 | 权限、客户端、能力、SSI 长度检查；调用状态机 |
| 状态机入队 | service/.../aware/WifiAwareStateManager.java:1533 | SSI 放入 Bundle，发送命令 |
| 状态机分发 | 同文件:3294 | 从 Bundle 取 SSI，调用 Local 方法 |
| 状态机执行 | 同文件:4548 | 查找 client/session |
| 发现会话 | service/.../aware/WifiAwareDiscoverySessionState.java:619 | 应用 peerId 映射为底层 instanceId/MAC，加入 pubSubId |
| Java HAL 桥接 | service/.../aware/WifiAwareNativeApi.java:926 | 选择 supplicant 或 vendor Wi-Fi HAL；本链展开后者 |
| Java HAL 门面 | service/.../hal/WifiNanIface.java:523 | validateAndCall，调用 AIDL 实现 |
| Java HAL AIDL | service/.../hal/WifiNanIfaceAidlImpl.java:519 | 创建请求并调用 HAL Binder |
| 请求字段构造 | 同文件:606 | 将 SSI 与可选 SDEA header 拼接，写 serviceSpecificInfo |
| HAL AIDL | hardware/interfaces/wifi/aidl/android/hardware/wifi/IWifiNanIface.aidl:331 | 原生 HAL 的 Bootstrap 请求接口 |
| 原生 HAL 入口 | default/wifi_nan_iface.cpp:830 | validateAndCall，调用 Internal |
| 原生 HAL 实现 | 同文件:1012 | 转 legacy 请求并调用 WifiLegacyHal |
| 数据转换 | default/aidl_struct_util.cpp:3695 | AIDL SSI → legacy 长度与缓冲区；当前旧 V3 实现没有新增字段 |
| legacy HAL 适配 | default/wifi_legacy_hal.cpp:1733 | 调用厂商函数表的 wifi_nan_bootstrapping_request |

`WifiAwareNativeApi` 本身仍是 Java 类，此路径通过 AIDL Binder 进入原生服务，不是调用同名 JNI。`WifiAwareNativeApi` 还存在 supplicant NAN 分支（getSupplicantNanIface 非空时），不能把所有设备都画成 vendor Wi-Fi HAL 路径；本文针对已确认的 a0807d7 V3 HAL 断点。

### HAL 实际版本与二进制

运行时执行只读版本查询：

```sh
adb -s a0807d7 shell service call android.hardware.wifi.IWifi/default 16777215
# Result: Parcel(00000000 00000003)
```

设备 `/vendor/etc/vintf/manifest/android.hardware.wifi-service.xml` 声明 version=3。实际 `/vendor/bin/hw/android.hardware.wifi-service` 的 ELF DT_NEEDED 包含 `android.hardware.wifi-V3-ndk.so`；运行进程 PID 1298 的 `/proc/1298/maps` 也确认加载 `/vendor/lib64/android.hardware.wifi-V3-ndk.so`，并非仅残留了一个未使用的旧库文件。

该库 `NanBootstrappingRequest::readFromParcel` 符号地址为 **0x70d70**，最后一个字段在 **0x70f10** 调用 AParcel_readByte 读取 discoverySessionId，随后 **0x70f34** 调用 AParcel_setDataPosition 跳到 parcelable 的 start+size；中间没有再读取 SSI byte array。这是 Stable AIDL 老接收端跳过新字段的兼容行为，所以调用本身仍可能成功。

| 证据文件 | SHA-256 |
|---|---|
| vendor android.hardware.wifi-V3-ndk.so | bfe4da24fcde4340a755e57ee53d8306eafdc48e7e0db01d460966e3fb38bac0 |
| vendor android.hardware.wifi-service | 3d11e0000a694180f05ff155e3a791a16ec6b7a67101c84780346ce330e7eac6 |
| framework-wifi.jar | 4ed8e23006e6d5e967765142cd357abc3b746e79226af767bdad21855801c9c8 |
| service-wifi.jar | f81ba956a329c339c6e598012e1cb6fad0593f6539ee936031acd5b86bbe6819 |

### V3 与 V4 的接口差异

源码目录 `hardware/interfaces/wifi/aidl/aidl_api/android.hardware.wifi/` 中：

```aidl
// V3 NanBootstrappingRequest 的全部字段
int peerId;
byte[6] peerDiscMacAddr;
NanBootstrappingMethod requestBootstrappingMethod;
byte[] cookie;
boolean isComeback;
byte discoverySessionId;

// V4 在末尾新增
@nullable byte[] serviceSpecificInfo;
```

因此设备显示 Android 17/API 37 及 Java API 存在，不代表 vendor AIDL HAL 已同步升级。当前缺失已经发生在 vendor Binder 读取边界，不需要先假定固件收到完整设备 TXT 后丢弃。

## ROM 升级 V4 的控制位置

以下是下载的 AOSP Android 17 默认实现；厂商 ROM 若覆盖了 service 模块，应修改实际参与编译的那份实现，而不是只改没有被选中的 AOSP 文件。

### 编译依赖

`hardware/interfaces/wifi/aidl/default/Android.bp` 的 `android.hardware.wifi-service-lib`、`android.hardware.wifi-service`、lazy 服务（若使用）及相关测试使用：

```bp
shared_libs: [
    // ...
    "android.hardware.wifi-V4-ndk",
],
```

这份 Android 17 源码已默认链接 V4；设备二进制仍链接 V3，因此应核对 ROM 的实际源码基线、厂商替代模块和是否使用了旧预编译 vendor 产物。接口定义由 `hardware/interfaces/wifi/aidl/Android.bp` 的 versions_with_info 定义，当前已有冻结的 V4；不要修改冻结 V3 快照来伪装成新版，也不需要重新发明 V4。

### VINTF 声明

`hardware/interfaces/wifi/aidl/default/android.hardware.wifi-service.xml`：

```xml
<hal format="aidl">
    <name>android.hardware.wifi</name>
    <version>4</version>
    <fqname>IWifi/default</fqname>
</hal>
```

Android.bp 将其作为 VINTF fragment 安装到 vendor。**单独修改设备 XML 不会改变 getInterfaceVersion 返回值或反序列化能力。** 必须部署匹配的服务、V4 NDK 库和配置，满足产品的 VINTF 兼容检查。

### SSI 实现不能只改库名

同步 V4 的接口和默认实现后，至少核对：

1. `wifi_nan_iface.cpp` 的 `initiateBootstrappingRequestInternal` 调用转换函数。
2. `aidl_struct_util.cpp` 的 `convertAidlNanBootstrappingInitiatorRequestToLegacy` 把 AIDL serviceSpecificInfo 拷到 legacy `sdea_service_specific_info`，设置对应长度并检查上限。
3. `hardware/interfaces/wifi/legacy_headers/include/hardware_legacy/wifi_nan.h` 中 NanBootstrappingRequest 包含 SSI 长度与缓冲区；适配层和高通库使用一致的结构体定义/ABI。
4. `wifi_legacy_hal.cpp` 的 `nanBootstrappingRequest` 调用 `global_func_table_.wifi_nan_bootstrapping_request(...)`。继续核对高通实际函数指针实现是否把该 SSI 序列化到请求 TLV 并由下层组进 Follow-up 的 SDEA；不能以函数指针非空或命令响应成功代替它。

可在隔离 ROM 仓库执行：

```sh
rg -n 'android\.hardware\.wifi-V3-ndk|name: "android.hardware.wifi-service"' hardware vendor device
rg -n 'convertAidlNanBootstrappingInitiatorRequestToLegacy|sdea_service_specific_info' hardware/interfaces/wifi
rg -n 'wifi_nan_bootstrapping_request|nan_bootstrapping_request' hardware vendor
```

在已选择正确产品 lunch 的环境中，可先构建 `m android.hardware.wifi-service android.hardware.wifi-V4-ndk` 检查依赖及编译；产品实际使用 lazy/厂商服务时改用对应目标。正式部署应使用匹配产品的 vendor 构建产物；仅从别的产品复制一个 V4 库，不能升级旧服务的内存布局及字段转换。

验收依次检查：运行时 getInterfaceVersion=4；服务 ELF 与进程 maps 为 V4；转换后 SSI 长度非零；空口 Bootstrap Follow-up 出现 SDEA 且包含目标 TXT；iPhone 不再打印 SSI nil，并出现首次 PIN，最后才验收配对和后续 NDP。

## 本轮操作及边界

最新 APK 安装并运行；仅重现和分析，没有修改 ROM/HAL，也没有清除配对缓存。抓到两份相同 SN 的 Follow-up（原发及 Retry）不代表首次配对成功；失败后约 20 秒超时仍存在。

结束时停止 Android App，恢复 30 秒息屏，关闭 iPhone 系统配对页并锁屏。Ubuntu 保持用户授权的 monitor 模式，未重启下载。原始抓包按日期保存，供用户本机查看。


2026-11-18 冻结接口完整对比补充：V3→V4 没有新增 IWifi* Binder 业务方法，主要是 6 个 NAN 类型的字段/枚举变化和 1 个新枚举文件。此前“包括新接口方法”的表述不准确，已修正；回移植仍需补齐新字段转换。详见 [完整 V3/V4 对比](wifi-hal-aidl-v3-v4-comparison.md)。

### 对端 V3 是否会让 Framework 裁剪发送字段

2026-11-18 再次直接核对设备 service-wifi.jar，而非仅按 AOSP 推测：IWifiNanIface$Stub$Proxy.initiateBootstrappingRequest（DEX 0x2bc798）直接 writeTypedObject(request) 后执行 Binder transact=21，没有 getInterfaceVersion 或按 V3 裁剪字段的分支。NanBootstrappingRequest.writeToParcel（DEX 0x2c00c0）无条件在 discoverySessionId 后调用 writeByteArray(serviceSpecificInfo)，也没有对端版本参数。WifiNanIfaceAidlImpl 的对应构造/发送路径同样没有按 V3 清空 SSI 的分支。因此，即使 Framework 已知 vendor 为 V3，本设备当前调用路径仍写出新增字段；Binder 传输不会自行按 AIDL 版本改写 payload。接收方 V3 reader 使用 parcelable size 跳过不认识的尾部，完成前向兼容。这是“新发送端写全部已知字段、旧接收端忽略未知字段”，不是协商生成 V3 格式。本轮只读设备已提取的二进制，没有新一轮真机配对操作。
