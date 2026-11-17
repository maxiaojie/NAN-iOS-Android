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
3. Android 公共 initiateBootstrappingRequest(peerHandle, method) 没有 SSI 参数，不能直接从此调用补 TXT。把 TXT 加回普通 Subscribe，也没有证据证明它能转移到 Bootstrap Request，且该 ROM 已有 Invalid TLV type 的历史失败。
4. Android API 的 method 文档按对端能力描述，生产代码选择 Display 有其依据。方法 32 是本次针对实际行为的诊断对照，不代表所有 ROM 都应无条件改为 Keypad。没有把只绕过第一项检查的版本作为修复提交；需要核对设备实际 framework/HAL/firmware 的方法转换与引导 SSI 设置来源。
5. iOS 日志确认过滤分支，不能据此补写完整私有实现或确定所有必填 TXT 键。下一步应在 ROM 层使 Bootstrap 请求携带有效配对元数据，并验证完整首次 PIN 流程。

## 空口索引与证据边界

01～07 是 iPhone 对照阶段，08～11 和 series 是 Android 阶段。未取得成功轮次全部认证帧，首次配对成功以系统事件及 UI 双重确认，不能宣称所有结论都来自完整空口握手。

- 03-iphone12-publisher-iphone14-subscriber-ch6-bootstrap.pcap 第 77711 帧：iPhone Publish SDF，发送者 76:2c:9b:4a:17:b2，SDEA Service Info 长度 193，PIN Display 0x0002。文件名表示测试阶段，不表示抓全 Bootstrap。
- 04-iphone12-publisher-iphone14-subscriber-ch149-bootstrap-retry.pcap 第 146749 帧起：重开页面后 NMI 为 52:e1:e0:92:f1:13 的发布者。
- 05-iphone-iphone-ch149-pin-auth-timeout.pcap 第 74515 帧起：早期超时轮次的 Authentication Request，携带方法 0x0020；不是 Bootstrap Follow-up，不能用作其 SSI 样本。
- 08-android-bootstrap-method32-ch149.pcap：方法 32 所在时段，本次字段筛选未取得相应单播引导帧，不能只靠此包证明 SSI 为零。直接证据是上述 iPhone 接收日志。
- series-ch6-164431.pcap：Android 普通 Subscribe 声明 0x0020，iPhone Publish 声明 0x0002；不能将 Subscribe 的声明当成实际 Bootstrap Request。

原始包保留在 Ubuntu 供查看，不提交 Git；该日期目录 README.md 同步列出操作与证据。关键日志摘录去除 PIN、密钥和身份解析材料。

## 日志读取原理与收尾

本次 syslog live 实际使用 OsTraceService，并非名称相似的旧 com.apple.syslog_relay。主机经 usbmux 与设备 lockdownd 建立已信任会话，通过 StartService 启动 com.apple.os_trace_relay、连接返回端口，再发送 Plist：Request=StartActivity、MessageFilter=65535、Pid=-1、StreamFlags=60。设备返回 RequestSuccessful 后，主机按记录标记及长度读取二进制流，解析时间、PID、进程、日志等级和消息。底层属于 iOS 系统诊断转发，不要求 App 注入，也不直接读取系统日志文件；private 字段仍受系统隐藏规则约束。本机实现位于 pymobiledevice3/services/os_trace.py 的 OsTraceService.syslog。

测试收尾恢复 a0807d7 原版 APK，停止订阅，恢复 30 秒熄屏设置。iPhone 14 Pro 的自动锁定从临时“永不”恢复为原来的“3 分钟”，界面勾选已核对；两台 iPhone 执行锁屏后再次查询系统锁状态。AOSP 不恢复下载。Ubuntu 抓包保持 monitor 状态，无继续抓包任务。
