# iPhone Publish 的 SDEA 与 Android 17 发现回调（2026-11-25）

## 本轮范围与结论

使用 USB iPhone 12（iOS 27.2.1 / 24B156）发布 AwareBridge 服务、HA2E6NPB（TB323FU_US / Android 17）订阅。iPhone 14 Pro 本轮 USB 不可用，因此本轮空口结果不能直接当作该设备的同一轮证明。未删除配对、未修改 App/ROM/HAL/固件、未建立 NDP 或测速。

**iPhone Publish 确实包含 SDEA。普通发布的该 SDEA 没有设备名称 TXT；Android 回调明确选择了 extendedServiceSpecificInfo 分支并交给 App 6 字节，不是完全没有收到 SDEA。**

同时发现空口的 6 字节和 Framework 日志的 6 字节不一致，需保留为尚未解释的下层问题，不能据此宣称从空口到 HAL/Framework 的所有字节已验证一致。当前证据也不能证明修复此差异就能得到设备名，因为本轮空口并没有设备名 TXT。

## 空口证据

Ubuntu `/home/mxj/NAN/2026-11-25/`：

- `ios-publish-ch6.pcap`：6 信道，目标 iPhone Publish 40 帧；示例 **4645**。
- `ios-publish-ch149.pcap`：149 信道，目标 iPhone Publish 38 帧；示例 **1732**。

Mac 镜像：`/Users/mxj/workspace/nan-capture-analysis/2026-11-25/sdea/`。PCAP 未提交 Git；其中其他设备、管理属性可能包含身份/安全相关数据。

目标发送地址 `56:f8:44:71:55:d2`；服务 ID `9a:a7:d1:b3:28:3c`（本项目服务）。用 Wireshark 和独立的 PCAP 字节解析交叉检查：

| 属性 | 6 信道帧 4645 | 149 信道帧 1732 |
| --- | --- | --- |
| SDA ID / Length | 0x03 / 9 | 0x03 / 9 |
| SDA Service Control | 0x00：Publish，Service Info Present=0 | 同左 |
| SDEA ID / Length | 0x0e / 16 | 同左 |
| SDEA Instance ID | 1 | 1 |
| SDEA Control | 0x0644 | 同左 |
| Service Update Indicator | 0x75 | 0x76 |
| Service Info Length | 10 | 10 |
| Service Info 头 | `50 6f 9a 02`，WFA OUI / Generic | 同左 |
| 头后 Specific Info | `dd 03 00 00 17 f2`，6 字节 | 同左 |

SDEA 完整 payload：

```text
01 44 06 75 0a 00 50 6f 9a 02 dd 03 00 00 17 f2  # ch6
01 44 06 76 0a 00 50 6f 9a 02 dd 03 00 00 17 f2  # ch149
```

0x0644 涉及 Data Path Required、Security Required、Service Update Indicator 与 GTK 要求等控制位；“含 SDEA”并不代表“含名称”。此处没有三项设备 TXT，也不将未确认语义的 6 字节强行解释为设备名称。

## Android 实际观测与边界

09:52:11.503，`WifiNanIfaceCallbackAidlImpl.eventMatch`：

```text
addr=56F8447155D2
isExtendedServiceSpecificInfo=true
serviceSpecificInfo=[-72, -89, -77, 1, 0, 0]
ssi.size()=6
```

同一 peer 的 App 日志：`Peer metadata: peer=123, SSI bytes=6, fields=[]`，UI“附近设备（未提供名称）”。数组转无符号十六进制为 `b8 a7 b3 01 00 00`，与空口 `dd 03 00 00 17 f2` **不相同**。

高通 `getNanMatch` 同期日志可见 type=28 / length=6，但该日志只证明 TLV 类型/长度，没有输出本次 TLV payload，尚不能确定不同字节始于固件/驱动事件、高通解析、legacy 结构体布局或 AIDL 转换。上游转换为直接拷贝，不等于产品 vendor 二进制已被验证与上游完全一致。

因此当前可确认：SDEA 存在；SDEA 路径已被使用；普通 Publish 的名称 TXT 缺失；另有字节不一致待查。不能说“HAL 不支持 SDEA”或“Android 17 把名称丢掉了”，也不能直接以两处长度都是 6 证明内容一致。

## AOSP Android 17 的具体结构和路径

Ubuntu 源码根目录 `/home/mxj/workspace/aosp-android17`；本轮检查 `hardware/interfaces` 提交 `0162af69`、`packages/modules/Wifi` 提交 `67beff9`。这是下载源码，不作为设备 ROM 的实现证明。

### 1. legacy HAL 的发现事件

文件：`hardware/interfaces/wifi/legacy_headers/include/hardware_legacy/wifi_nan.h`，结构 **NanMatchInd**：

```c
// SDA Service Info（约 2142 行）
u16 service_specific_info_len;
u8 service_specific_info[NAN_MAX_SERVICE_SPECIFIC_INFO_LEN];

// SDEA Service Info（约 2242 行）
u16 sdea_service_specific_info_len;
u8 sdea_service_specific_info[NAN_MAX_SDEA_SERVICE_SPECIFIC_INFO_LEN];
```

注册 `NanCallbackHandler.EventMatch(NanMatchInd*)`；默认 HAL 经 `wifi_legacy_hal.cpp:onAsyncNanEventMatch` 转交 `wifi_nan_iface.cpp` 的 `callback_handlers.on_event_match`（约 361 行）。

### 2. legacy → AIDL 转换

文件：`hardware/interfaces/wifi/aidl/default/aidl_struct_util.cpp`，函数 **convertLegacyNanMatchIndToAidl**（2352 行）：

```cpp
aidl_ind->serviceSpecificInfo = std::vector<uint8_t>(
        legacy_ind.service_specific_info,
        legacy_ind.service_specific_info + legacy_ind.service_specific_info_len);
aidl_ind->extendedServiceSpecificInfo = std::vector<uint8_t>(
        legacy_ind.sdea_service_specific_info,
        legacy_ind.sdea_service_specific_info + legacy_ind.sdea_service_specific_info_len);
```

### 3. AIDL 结构和回调

文件：`hardware/interfaces/wifi/aidl/android/hardware/wifi/NanMatchInd.aidl`：

```java
byte[] serviceSpecificInfo;         // SDA / Service Info
byte[] extendedServiceSpecificInfo; // SDEA / Service Info
```

文件：同目录 `IWifiNanIfaceEventCallback.aidl:103`：

```java
void eventMatch(in NanMatchInd event);
```

**这两个字段在冻结的 V1、V3 和 V4 NanMatchInd 中均可找到。本问题不是 Bootstrap 的 V4 新增字段问题。** 不要为发现回调误改 NanBootstrappingRequest；后者用于发起 Bootstrap，不是发现事件。

### 4. Framework 的选择逻辑

文件：`packages/modules/Wifi/service/java/com/android/server/wifi/hal/WifiNanIfaceCallbackAidlImpl.java:424`：

```java
byte[] serviceSpecificInfo = event.serviceSpecificInfo;
boolean isExtendedServiceSpecificInfo = false;
if (serviceSpecificInfo == null || serviceSpecificInfo.length == 0) {
    serviceSpecificInfo = event.extendedServiceSpecificInfo;
    isExtendedServiceSpecificInfo = true;
}
```

随后只把选中的一个数组转发 Framework。**优先 SDA，SDA 为空才回退 SDEA，不拼接两者。** 若两者均非空，SDEA 名称可能被 SDA 覆盖，但本轮 `isExtendedServiceSpecificInfo=true`，没有命中这一优先级问题。

### 5. Framework → App

```text
IWifiNanIfaceEventCallback.eventMatch(NanMatchInd)
 → WifiNanIfaceCallbackAidlImpl.eventMatch
 → WifiAwareNativeCallback / WifiAwareStateManager.onMatchLocal（5736）
 → WifiAwareDiscoverySessionState.onMatch（803）
 → IWifiAwareDiscoverySessionCallback.onMatch
 → WifiAwareManager.WifiAwareDiscoverySessionCallbackProxy.onMatch（1125）
 → onServiceDiscovered(ServiceDiscoveryInfo)
 → ServiceDiscoveryInfo.getServiceSpecificInfo()
 → SubscriberController.Peer.updateInfo / PeerDeviceInfo.parse
```

Framework 不把 pairing alias 转换成设备名，也不会自动为普通 Publish 生成 `pairingName/modelName/vendorName`。App 只解析已有 TXT，优先 pairingName，再 modelName，否则显示未提供名称；非文本控制内容不能当作名称。

## 后续下层定位建议

在高通 `getNanMatch` 接收到 type=28 的分支中输出长度及非敏感 SSI payload；在 AIDL 服务调用 convertLegacyNanMatchIndToAidl 前后分别输出两种 SSI 的长度和内容，以同一 peer MAC、时间戳对照空口。先定位已观测的 6 字节内容差异，再决定是否需要修 HAL/结构体布局；不直接新增 AIDL 字段或无条件优先 extended 覆盖所有服务。

## 收尾

Ubuntu 初次 restore 遇到 NetworkManager 接口尚未就绪，第二次自行重试成功；wlp3s0 为 managed，lenovo-internet 已连接，有默认路由，HTTPS 清华镜像返回 HTTP 200。HA2E6NPB 停止 App、撤销充电常亮，iPhone 12 通过短按脚本确认 lockstate 从 0→1。没有操作 a0807d7 或重新配对。
