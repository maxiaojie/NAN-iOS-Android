# SmartConnect Android NAN Patch

2026-11-25。产品仓库 `master_android/readyfor`，基线 `1f030fcc641ed1eb869252ea36fcc53c43081daa`。这是 SmartConnect 产品补丁，不适用于本仓库 AwareBridge 源码。

SHA-256：`ebc4fa9e7fbfd59303974c5c42afcb975707a148df633fedcac32e696046f68f`。

在对应产品仓库根目录执行 `git apply --check /path/to/SmartConnectAndroid-nan-integration.patch`，通过后执行 `git apply`。本地反向 `git apply --reverse --check` 已通过。详细代码、构建、依赖恢复、安装和问题经过见补丁内 `docs/NAN-integration.md`。

协议：`nan = ["1", "_smartconnect._tcp", "<临时邀请标识>", "<Aware TCP端口>"]`。先建立 Aware 数据链路，再保留原 WSS 证书指纹、Token 和反向验证；显式 NAN 邀请不与 STA / WFD / cloud 竞速。

验证设备：a0807d7 / TB323FU_PRC（Android SDK37）、iPhone12 / iPhone13,2（系统报告 iOS27.2.1）。Android 新 APK 已安装；iOS 使用已有 Wi-Fi Aware profile 构建独立主 App 验证包并安装，保留产品签名配置。验证包未嵌入 App 扩展，不用于验收 APNs、App Group 或 File Provider。

结果：Android 发布 / iOS 连接曾通过 WSS 并存活约七分钟；最新反方向 iOS 发布 / Android 订阅已通过反向验证和 connType10 的 WSS，持续至少3分41秒。二维码临时配对服务也有 serviceId19、channel4、connType10、result1 记录。已有配对后自动重连与手动邀请并发可抢占选择 UI，该复测分支未验收；文件传输、NFS 等其他 socket 业务也未完成 NAN 适配。不能将上述结果视为全部业务、后台和失败恢复矩阵通过。

不包含 APK、签名资料、私钥、配对凭据或完整设备日志；产品源码未提交公司 Gerrit。


2026-11-25 自动连接补充：ReadyFor 保存经 WSS 认证后的 realDeviceId→公开 pairedAlias 关联，后续自动匹配并等待 onPairingVerificationSucceed，再建立 NDP/WSS。首次未知设备仍需一次明确关联/系统配对，后台没有关联不弹选择框。相同目标共享有引用计数的 NDP、相同在建邀请去重、失败重连退避及验证失败暂停均已实现。完整 NAN 二维码邀请直接进入 WSS setup，不再先等 BLE 协商。

真机：15:53:19 自动连接 WSS 成功；强制停止 Android App 后，15:55:48 使用新二维码再次自动连接成功，均无选择界面。Android 六项身份/Policy 单测通过，最新 productDebug 已安装 a0807d7。iOS 同时修复后台邀请抢占 QR publisher，最新独立主 App 验证包已安装 iPhone12。失败轮次、缓存验证时序和 QR 失效端口证据在 Patch 中的 docs/NAN-integration.md 完整记录。

此结论针对 Android 客户端的已关联设备自动连接。iOS 客户端的系统 DevicePicker、首次未知设备、全部并发/取消/失败恢复矩阵及文件/NFS socket业务没有因此全部验收。
