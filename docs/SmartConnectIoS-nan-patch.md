# SmartConnect iOS NAN Patch

2026-11-25。本补丁对应 SmartConnectIoS 基线 `092a1661e2e8e6842a7997ff2d257421467b1f1a`，不适用于本仓库 AwareBridge 工程。

SHA-256：`30f1499f67495757f07914bf6e04ec92d155e008066376a76c7deef5c6808704`。

在 SmartConnectIoS 根目录先运行 `git apply --check /path/to/SmartConnectIoS-nan-integration.patch`，通过后使用 `git apply`。新增文档 `docs/NAN-integration.md` 包含协议、构建步骤、依赖问题及验证边界。

内容：nan Policy、iOS Network framework Aware TCP、先建链后 WSS、反向验证、系统配对入口、权限与关闭资源。附带完整构建所需的原有 OpenSSL 4 路径/API 兼容修复。

验证：Go race 测试、SCAPI 真机/模拟器构建、Swift/Objective-C 编译以及完整 SmartConnect iOS Debug 真机目标未签名构建通过。**未完成真机端到端验证，Android 产品端接入另行进行。** 不包含 APK、签名资料、私钥或完整设备日志。
