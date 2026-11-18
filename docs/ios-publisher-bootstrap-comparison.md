# iOS 发布端首次配对：iPhone 与 Android 引导请求对比

日期：2026-11-17。Ubuntu 抓包目录 `/home/mxj/NAN/2026-11-17/`；Mac 副本 `/Users/mxj/workspace/nan-capture-analysis/2026-11-17/live/`。完整系统日志仅保存在 Mac `/tmp/nan-pair-12-live.log`、`/tmp/nan-pair-14-live.log`、`/tmp/nan-android-bootstrap-live.log`，含配对材料，不提交 Git。

## 环境和操作

iPhone 12 为发布端，iPhone 14 Pro 为订阅端，均运行本项目 AwareBridge，服务 `_aware-bridge._udp`。系统首次配对页面分别由 DevicePairingView、DevicePicker 打开；后续 App 的开始发布/连接用于数据链路，两阶段不能混淆。Android 设备为 a0807d7。

Android 普通 Subscribe 不配置设备 TXT，本地配置支持 PIN Keypad，生产代码向 initiateBootstrappingRequest 传 peer PIN Display（2）。另制作临时 APK，仅将请求参数改为 PIN Keypad（32），隔离两项检查；之后恢复源代码及原 APK。

AOSP 下载由用户要求停止，保留 STOPPED 状态，源码未完整下载；随后恢复 Ubuntu monitor 接口，分别抓信道 6、149，有线 SSH 保留。单网卡分时捕获存在间隙，不能把未抓到解释为没有发送。

日志通过开源 pymobiledevice3 的 syslog live 获取：USB/usbmux → 已信任的 lockdown 会话 → com.apple.os_trace_relay → StartActivity → 二进制日志解析。不是 App 自行打印，也没有注入 wifip2pd。筛选在 Mac 端进行。iOS 时间为 2026-11-17；Android 自身日志仍显示 11-13，必须按事件对应，不能直接混用时钟。

## iPhone 对照结果

16:36:45，iPhone 12 收到 iPhone 14 Pro 的 Bootstrap Request，记录 TXT Record size: 122 bytes，请求 Methods 为 keypadPinCode。响应端接受并显示 PIN。快速输入后，16:36:48～49 订阅端依次发送 Authentication Request、Authentication Confirmation、Pairing Caching Follow-up，记录保存 NIK、认证成功；界面显示已配对 iPhone。

较早一轮 16:31:45 已显示 PIN，但直到 16:32:48 才提交认证请求，发布端已于 16:32:45 退出。该轮失败不能归因于协议不兼容，后续快速重试成功。

配对后，两端分别开始发布、连接，均进入 NDP ready。发送统计增长，接收统计为 0：当前 iOS 接收代码使用 validAndroidMessage，会过滤另一台 iOS 的消息。本轮确认首次配对和 NDP 建立，不宣称 iPhone 间应用层双向心跳通过。

## Android 方法 2：方法筛选失败

16:44:04.971，iPhone 12 的 wifip2pd 收到 Android Follow-up，并打印：

```text
pairingBootstrapping(Type: Request, Methods: [pinCode])
ServiceDescriptor.serviceInfo: nil
None/Multiple Pairing Methods Requested. NPBA: [], PBEA: []
```

Android NativeApi 记录 method=2，HAL 命令响应成功，约 20 秒后框架记录 bootstrapping request reject, reason=1。16:51:32、16:53:42 也复现同一 iOS 错误。

可以确定 iOS 收到了请求，并在方法筛选处停止。HAL 命令受理成功不等于对端接受。NPBA 的空列表也不等于原始报文完全没有 Pairing Bootstrapping 属性：接收日志已明确包含该属性，只是筛选结果为空。

## 临时改为方法 32：元数据检查失败

16:49:29.380～381，iPhone 12 收到测试 APK 的请求：

```text
Methods: [keypadPinCode]
NAN-Pairing: Received Bootstrap Request ... TXT Record size: 0 bytes
Received Pairing SSI: nil
NAN-Pairing: Missing required pairing metadata in Bootstrap Request ...
```

Android NativeApi 同步记录 method=32、命令受理成功，但最终仍约 20 秒超时。这项对照确认改变方法位能越过第一项检查，随后缺少首次配对元数据，仍不能成功。iPhone 订阅端相应请求带 122 字节 TXT，Android 为 0；这已是 iOS 接收端的明确检查结果。

## 结论、处理和限制

1. 普通 Subscribe 不带设备 TXT 仍合理。本次缺失的是首次配对 Bootstrap Request 的 SSI，不能混为一谈。
2. 当前 ROM/调用组合有两项阻碍：方法 2 在接收端被解释为 Display，不满足 iOS 此请求分支；方法 32 越过方法检查后，引导配对元数据为空。
3. 本轮旧代码调用的两参数 initiateBootstrappingRequest(peerHandle, method) 没有 SSI 参数。此前据 SDK 36 将其推广为“公共接口不能传入元数据”不准确：API 37 已提供三参数重载，见下方修正。把 TXT 加回普通 Subscribe，也没有证据证明它能转移到 Bootstrap Request，且该 ROM 已有 Invalid TLV type 的历史失败。
4. Android API 的 method 文档按对端能力描述，生产代码选择 Display 有其依据。方法 32 是本次针对实际行为的诊断对照，不代表所有 ROM 都应无条件改为 Keypad。没有把只绕过第一项检查的版本作为修复提交；需要核对设备实际 framework/HAL/firmware 的方法转换与引导 SSI 设置来源。
5. iOS 日志确认过滤分支，不能据此补写完整私有实现或确定所有必填 TXT 键。应先使用 API 37 新增的 message 参数传入设备信息，再核对对端接收日志及完整首次 PIN 流程，不能预先认定必须修改 ROM。

## API 37 新接口修正与接入

用户指出 API 37 新增 initiateBootstrappingRequest(PeerHandle, int, byte[])。已核对 [官方 DiscoverySession 文档](https://developer.android.com/reference/android/net/wifi/aware/DiscoverySession)：message 会作为 SDEA Service Info 传输，推荐 createTxtRecordTlvBuffer(Map) 生成 DNS-SD TXT。两参数接口自 API 34 存在，三参数接口自 API 37 增加，不能混用版本结论。

在 a0807d7（Android 17 / API 37）执行只读运行时方法探测，确认实际 Framework 同时具有两种重载和 WifiAwareManager.createTxtRecordTlvBuffer(Map)。纯测试 map k=v 经官方助手得到 04 04 00 03 6b 3d 76，createTxtRecordMap 可还原，验证助手已包含 TXT TLV 头，不能再套一层现有 DeviceInfo.build 头。

应用接入：API 37 调用三参数重载，message 使用官方助手，从设备读取 modelName、pairingName、vendorName 编码；本地方法仍为 PIN Keypad。API 36 保留旧路径；普通 Subscribe 继续不携带 DeviceInfo。共享读取与 UTF-8 长度限制，Publish 原有编码保留。compileSdk 升级 37，按官方工具兼容表升级 AGP 9.1.1 / Gradle 9.3.1，minSdk、targetSdk 保持 36。

新 APK 已完成 assembleDebug / lintDebug 并安装 a0807d7。首轮 iPhone 12 在 17:39:59.909 仍报告 TXT 0 / SSI nil；增加安全诊断日志后再次测试，Android App 记录 `Bootstrap request: API 37, TXT TLV bytes=86`，Framework NativeApi 记录 method=32，iPhone 在 Mac 时间 17:49:44.883 仍记录 `TXT Record size: 0 bytes`、`Received Pairing SSI: nil`、`Missing required pairing metadata in Bootstrap Request`。因此参数在 App 侧非空，实际接收仍为空，首次配对未修复。尚不能确定丢失发生于设备 Framework、HAL、固件或编码交接环节，需检查实际实现。Ubuntu 仍在下载，按用户要求本轮没有切换 monitor 或进行空口抓包。该公共接口的存在修正此前“没有公开办法传参”的判断。

## 空口索引与证据边界

01～07 是 iPhone 对照阶段，08～11 和 series 是 Android 阶段。未取得成功轮次全部认证帧，首次配对成功以系统事件及 UI 双重确认，不能宣称所有结论都来自完整空口握手。

- 03-iphone12-publisher-iphone14-subscriber-ch6-bootstrap.pcap 第 77711 帧：iPhone Publish SDF，发送者 76:2c:9b:4a:17:b2，SDEA Service Info 长度 193，PIN Display 0x0002。文件名表示测试阶段，不表示抓全 Bootstrap。
- 04-iphone12-publisher-iphone14-subscriber-ch149-bootstrap-retry.pcap 第 146749 帧起：重开页面后 NMI 为 52:e1:e0:92:f1:13 的发布者。
- 05-iphone-iphone-ch149-pin-auth-timeout.pcap 第 74515 帧起：早期超时轮次的 Authentication Request，携带方法 0x0020；不是 Bootstrap Follow-up，不能用作其 SSI 样本。
- 08-android-bootstrap-method32-ch149.pcap：方法 32 所在时段，本次字段筛选未取得相应单播引导帧，不能只靠此包证明 SSI 为零。直接证据是上述 iPhone 接收日志。
- series-ch6-164431.pcap：Android 普通 Subscribe 声明 0x0020，iPhone Publish 声明 0x0002；不能将 Subscribe 的声明当成实际 Bootstrap Request。

原始包保留在 Ubuntu 供查看，不提交 Git；该日期目录 README.md 同步列出操作与证据。关键日志摘录去除 PIN、密钥和身份解析材料。

## 日志读取原理与收尾

### 后续应用修正：请求改为 Keypad

用户要求把已验证的方法位调整落实到正式代码。SubscriberController.pair 改为传 PAIRING_BOOTSTRAPPING_PIN_CODE_KEYPAD（32），与本地输入 PIN 的角色一致，替代此前 Display（2）。依据同日对照测试，32 可使 iOS 越过方法检查，进入配对元数据检查。此前“恢复原版”的叙述保留为诊断阶段历史；这项修改仍不构成首次配对成功，Bootstrap SSI 缺失未修复，也未验证其他 ROM 的兼容性。

最终版本使用 JDK 21 执行 assembleDebug、lintDebug 通过，并安装到 a0807d7。没有再次解锁已锁定的 iPhone；不把构建和安装作为新增首次配对通过证据。

本次 syslog live 实际使用 OsTraceService，并非名称相似的旧 com.apple.syslog_relay。主机经 usbmux 与设备 lockdownd 建立已信任会话，通过 StartService 启动 com.apple.os_trace_relay、连接返回端口，再发送 Plist：Request=StartActivity、MessageFilter=65535、Pid=-1、StreamFlags=60。设备返回 RequestSuccessful 后，主机按记录标记及长度读取二进制流，解析时间、PID、进程、日志等级和消息。底层属于 iOS 系统诊断转发，不要求 App 注入，也不直接读取系统日志文件；private 字段仍受系统隐藏规则约束。本机实现位于 pymobiledevice3/services/os_trace.py 的 OsTraceService.syslog。

测试收尾恢复 a0807d7 原版 APK，停止订阅，恢复 30 秒熄屏设置。iPhone 14 Pro 的自动锁定从临时“永不”恢复为原来的“3 分钟”，界面勾选已核对；两台 iPhone 执行锁屏后再次查询系统锁状态。AOSP 不恢复下载。Ubuntu 抓包保持 monitor 状态，无继续抓包任务。


2026-11-17 后续版本范围调整：按用户要求仅支持 Android 17 / API 37 及以上，minSdk 与 targetSdk 均升为 37，移除 API 36 两参数兼容分支，直接调用三参数 Bootstrap 接口。上文 min/target 36 是当时接入阶段的历史配置；本次版本调整不代表元数据传输问题已解决。

## 2026-11-18 后续定位

重新抓包并检查设备二进制后，已将此前未确定的丢失层级收敛到 vendor Binder 的 HAL V3 接口：Java 发送新增 SSI，设备 V3 NDK reader 不读取此 V4 字段，跳过剩余 Parcel；空口为没有 SDEA 的单播 NAN SDF Follow-up。具体运行时版本查询、帧号、源码差异与升级步骤见 [HAL V3 断点专项](android17-bootstrap-hal-v3.md)。本轮没有改 ROM，首次反向配对仍失败。


## 2026-11-18 Vendor 实验修复后验证

用户构建的 V4 服务与库部署后，App/HAL SSI=86 字节；信道 6 抓包 03-vendor-v4-bootstrap-ch6.pcap 第 122062 帧的 Bootstrap SDF Follow-up 包含 SDEA，TXT=83 字节且三项设备信息完整。iOS 接收 SSI 非空、显示平板名称及 PIN，用户协助输入 PIN 后双方首次配对成功；后续缓存验证通过。原始 V3 失败记录保留为历史，当前结果见 [V4 实机验证](vendor-v4-bootstrap-validation.md)。后续数据首包 EPERM 已定位到缺失的 Android 17 本地网络权限，声明并授予后双向心跳开始增长，持续验收结果见同文档。
