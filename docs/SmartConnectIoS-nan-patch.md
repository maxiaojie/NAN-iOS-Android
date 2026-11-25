# SmartConnect IoS NAN Patch

2026-11-25。产品仓库 `SmartConnectIoS`，基线 `092a1661e2e8e6842a7997ff2d257421467b1f1a`。这是 SmartConnect 产品补丁，不适用于本仓库 AwareBridge 源码。

SHA-256：`101ff6c497e3347d878faca51355c74ccdaea3b76db9b13cf16c458fc26803e2`。

在对应产品仓库根目录执行 `git apply --check /path/to/SmartConnectIoS-nan-integration.patch`，通过后执行 `git apply`。本地反向 `git apply --reverse --check` 已通过。详细代码、构建、依赖恢复、安装和问题经过见补丁内 `docs/NAN-integration.md`。

协议：`nan = ["1", "_smartconnect._tcp", "<临时邀请标识>", "<Aware TCP端口>"]`。先建立 Aware 数据链路，再保留原 WSS 证书指纹、Token 和反向验证；显式 NAN 邀请不与 STA / WFD / cloud 竞速。

验证设备：a0807d7 / TB323FU_PRC（Android SDK37）、iPhone12 / iPhone13,2（系统报告 iOS27.2.1）。Android 新 APK 已安装；iOS 使用已有 Wi-Fi Aware profile 构建独立主 App 验证包并安装，保留产品签名配置。验证包未嵌入 App 扩展，不用于验收 APNs、App Group 或 File Provider。

结果：Android 发布 / iOS 连接曾通过 WSS 并存活约七分钟；最新反方向 iOS 发布 / Android 订阅已通过反向验证和 connType10 的 WSS，持续至少3分41秒。二维码临时配对服务也有 serviceId19、channel4、connType10、result1 记录。已有配对后自动重连与手动邀请并发可抢占选择 UI，该复测分支未验收；文件传输、NFS 等其他 socket 业务也未完成 NAN 适配。不能将上述结果视为全部业务、后台和失败恢复矩阵通过。

不包含 APK、签名资料、私钥、配对凭据或完整设备日志；产品源码未提交公司 Gerrit。
