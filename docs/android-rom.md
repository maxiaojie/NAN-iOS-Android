# Android ROM 与 AOSP 编译接口

此仓库源自 `/wifi_aware` 的发布/配对实验。当前已经移除非公开的 setVendorData 调用，使用标准 Android SDK 36 编译；设备仍须支持 Wi-Fi Aware 及配对能力。

原项目曾使用提取的 `framework-wifi.jar` 及 bootstrap classpath 覆盖，让编译器看到非公开接口。删除 setVendorData 后，当前剩余调用均能由标准 SDK 36 编译。2026-11-13 已删除 jar 文件、compileOnly 依赖及 bootstrap classpath 配置，并通过 clean + assembleDebug + lintDebug；不再需要用该 jar 解锁编译接口。

当前发布配置使用：

- solicited publish；服务 `_aware-bridge._udp`；
- SSI 为 `04 + TXT长度的 little-endian uint16 + TXT`，长度不包含 3 字节头；
- TXT 的 `modelName` / `pairingName` / `vendorName` 从 Android 设备读取，值按 UTF-8 字节长度截断；
- Aware Pairing PIN Display、配对缓存/验证、PASN-128；
- 不再调用 `setVendorData`；原 OUI `0x506f9a`、`android=false` 配置已通过真机对照删除；
- publisher network request 宣告 UDP 端口和 transport protocol 17；
- 保留原实验的固定 passphrase `1234567890`。

不同 ROM 可能存在 API、权限或参数支持差异，表现为运行时异常、发布失败、配对不可用或 HAL 拒绝 NDP。应用会展示错误，而不会因为复制编译 jar 就获得 ROM 权限。真正的产品适配需要逐 ROM 检查 API、系统权限和配对后密钥的数据路径使用方式。

固定 passphrase 仅为复现已验证实验配置，不是 PIN，不提供应用身份认证，也不适合直接作为公开产品的共享秘密。不要据此声称所有 Android 16/17 设备都支持 iOS 互通。

本机使用 JDK 21 完成构建，Java source/target 暂沿用 8。删除 jar 后，已不存在读取其 class file 65 的要求；未另行验证其他 JDK 版本。

2026-11-13 已验证删除 `setVendorData` 后，TB323FU（STA 断开）↔ iPhone 12 仍能全新 PIN 配对和建立数据链路，详见 [删除验证](vendor-data-removal-validation.md)。后续又以标准 SDK 完成移除 jar 的全量编译验证，jar 现已删除。
