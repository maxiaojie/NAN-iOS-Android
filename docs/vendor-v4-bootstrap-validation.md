# Vendor V4 Bootstrap SSI 实机验证（2026-11-18）

## 环境与操作

Android 为 a0807d7 / TB323FU / Android 17，使用用户构建并部署的 Wi-Fi HAL 服务及 android.hardware.wifi-V4-ndk.so，运行时报告 4。接口为本轮定向实验版本，不宣称完整标准 V4 兼容。iPhone 12 运行本项目 AwareBridge（实际签名 Bundle ID com.slab.wifiawaredemo），发布服务 _aware-bridge._udp；Android 使用订阅角色。另一台 Android 303fdedc 与 iPhone 14 Pro 未操作。

iPhone 打开“允许 Android 配对”系统页面，Android 开始订阅并发现设备，点击配对。用户协助手动输入 iPhone 显示的 PIN；未删除配对缓存来重复这一轮。随后关闭 iOS 配对页面、点击开始发布，Android 通过缓存验证后点击连接。

## Bootstrap 已通过的证据

Android App 记录 Bootstrap request: API 37, TXT TLV bytes=86；Vendor HAL 新增日志记录 copied SSI to legacy request, input length=86, sdeaLength=86。说明本轮 AIDL→legacy 复制已执行，非仅确认版本号。

Ubuntu 抓包保存在 /home/mxj/NAN/2026-11-18/03-vendor-v4-bootstrap-ch6.pcap；Mac 副本为 /Users/mxj/workspace/nan-capture-analysis/2026-11-18/03-vendor-v4-bootstrap-ch6.pcap。信道 6，第 122062 帧，抓包时间 16:38:52.457015，411 字节（含 56 字节 radiotap），Android NMI 02:f4:e3:f9:31:b1 → iPhone NMI ce:fd:aa:ea:92:d0。帧类型为单播 NAN SDF Follow-up，SDA service control=2，instanceID=128、requestorInstanceID=1；NPBA 类型 Request，方法 0x0020（PIN Keypad）。

完整属性序列：3、14、44、15、42、18、18、43、11、221、221。SDEA（14）属性体长度 95；instanceID=128，control=0；Service Info 长度 90，其中 50:6f:9a OUI + Generic protocol 2 共 4 字节，后续 SSI 86 字节。SSI 头 04 53 00 共 3 字节，声明 TXT 长度 0x0053=83，后续确有 83 字节 DNS-SD TXT，完整解析为：

- modelName=TB323FU
- pairingName=联想平板 拯救者 Y700 五代
- vendorName=LENOVO

所有属性解析结束偏移=411，等于抓包帧长度，本代表帧没有未解析异常尾部。App/HAL 的 86 与 iOS 的 TXT 83 是不同层次的长度，不是丢失 3 字节。

iOS 接收时间 16:38:52.445307，日志为 TXT Record size: 83 bytes；Received Pairing SSI 为非空字典，三个字段值长度为 7、34、6 字节。随后接受 Bootstrap、显示 Android 设备名和 PIN。iOS 与 Ubuntu 时钟有约 12ms 差异，不用绝对时间先后推断协议因果。Android 自身日期仍为 11-12，按事件内容对应，不能直接与 Mac/iPhone 混用。

## 首次配对与缓存验证

16:39:27.202375 iOS 状态 authenticated，16:39:27.624798 成功保存 NIK；iOS 系统页出现已配对平板，Android UI 显示“配对成功，选择设备后点击连接”。PIN 由用户协助手动输入。原始日志中的 PIN/密钥不提交仓库。iOS 期间还记录 Missing pairingKeyStoreID or bundleID to notify pair-verify completion，但随后认证、缓存及两端 UI 均成功，保留为诊断现象，不用这一条孤立日志认定失败。

关闭系统配对页并重新发布后，Android 显示“已验证缓存配对，可点击连接”。因此本轮确认首次 PIN 及其后的缓存身份验证通过，之前 Vendor V3 跳过 Bootstrap SSI 的断点已被本轮 Vendor 实验修复。

## 首次数据连接发现的权限问题

16:43:42 iOS 记录 datapath established / Publisher Data Confirmed，说明 NDP 已建立；但 Android 首个 UDP 心跳 sendto 返回 EPERM，App 释放链路，iOS 后续显示 publisherTimeout（-11989）。AppOps 同期记录 ACCESS_LOCAL_NETWORK 拒绝，UID mode=ignore。原 APK 未声明此权限。尝试仅设置 appops allow 未改变有效 UID ignore，不以该尝试作为修复。

App 已统一只支持 API 37，故在 AndroidManifest.xml 添加 android.permission.ACCESS_LOCAL_NETWORK，并加入 MainActivity 的运行时权限申请列表。assembleDebug 与 lintDebug 通过，覆盖安装 a0807d7，使用 pm grant 对本轮真机授予新增权限，UID ignore 随之消失。此轮授予通过 ADB 完成，尚未单独验收全新安装的系统授权弹窗流程。

## 授权后的数据连接

重新 iOS 发布、Android 订阅并通过缓存验证后连接；Android UI 已显示“已连接 · 双向数据通信中”，最早采样发送 3 / 接收 2，最近接收 iOS heartbeat 为 0 秒前，对端 UDP 45454。16:50 左右开始，到 16:52:56 持续超过 2 分钟（约 170 秒），Android 发送 70 / 接收 69；iOS 16:52 截图发送 68 / 接收 69，最近接收 3 秒前。两端采样不同步，计数不要求逐项相等，均显示双向通信且没有自发断链。

抓包 04-vendor-v4-ndp-first-ch149.pcap 对应权限修复前首轮 NDP；05-vendor-v4-ndp-local-permission-ch149.pcap 对应修复后的测试，均保存在 Ubuntu 相同日期目录。首次 PIN Authentication 发生在第一份 120 秒抓包窗口结束后，本次不宣称该文件抓全了首次认证交换。Bootstrap 的直接空口证据为 03 文件第 122062 帧。

## 验证边界

本轮未修改高通隔离服务器源码；使用用户构建产物，无法据此列出其全部私有差异。不把本次成功推广为完整标准 V4 兼容，也未验证其他 ROM、其他 NAN 功能或 STA 并发异常问题。本轮未验证反方向首次配对回归。连续通信验收通过。收尾停止 iOS 发布、force-stop Android 并恢复 30 秒息屏，保留双方本轮成功配对缓存。收尾曾出现 iPhone USB 断开，用户重新连接并解锁后，发送短按锁屏事件；devicectl 确认 passcodeRequired=true，iPhone 12 已锁屏。


### 运行时权限补充检查

持续通信结束后，撤销 ACCESS_LOCAL_NETWORK 并清除该权限 user-set/user-fixed，再启动 App、选择订阅并点击开始；复查该权限 granted=true 且 App 已进入订阅，未再次执行 pm grant。说明当前已有附近设备权限的设备上，新增的运行时申请路径可重新取得授权。未采到独立授权弹窗，不宣称全新安装的弹窗文案/拒绝分支已验收。
