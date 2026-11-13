# Android ROM 与 AOSP 编译接口

此仓库沿用 `/wifi_aware` 原项目中已验证的 Android 发布/配对配置，目的是保留在当前测试设备上的互通路径，并未把它包装成通用 Android SDK 功能。

`NAN-Android/app/libs/framework-wifi.jar` 为原项目提取并调整 API 可见性的 AOSP Wi-Fi 编译 jar。它仅作为 `compileOnly` 和 javac bootstrap classpath 使用，**不会打包进 APK，也不会替换设备 ROM 的 Wi-Fi 模块**。

当前发布配置使用：

- solicited publish；服务 `_aware-bridge._udp`；
- SSI 为 `04 + TXT长度的 little-endian uint16 + TXT`，长度不包含 3 字节头；
- TXT 的 `modelName` / `pairingName` / `vendorName` 从 Android 设备读取，值按 UTF-8 字节长度截断；
- Aware Pairing PIN Display、配对缓存/验证、PASN-128；
- 原项目的 `setVendorData` 配置：OUI `0x506f9a`、`android=false`；
- publisher network request 宣告 UDP 端口和 transport protocol 17；
- 保留原实验的固定 passphrase `1234567890`。

普通量产 ROM 可能拒绝隐藏 API、权限或不支持这些参数，表现为运行时异常、发布失败、配对不可用或 HAL 拒绝 NDP。应用会展示错误，而不会因为复制编译 jar 就获得 ROM 权限。真正的产品适配需要逐 ROM 检查 API、系统权限和配对后密钥的数据路径使用方式。

固定 passphrase 仅为复现已验证实验配置，不是 PIN，不提供应用身份认证，也不适合直接作为公开产品的共享秘密。不要据此声称所有 Android 16/17 设备都支持 iOS 互通。

构建需要 JDK 21 读取该 jar 的 class file 65。Java 源码/目标设为 8，确保 AOSP Wi-Fi API 在 javac bootstrap classpath 中先于 Android SDK stub 被解析；这些是编译配置，与设备运行时权限相互独立。

`setVendorData` 目前仅是保留的已验证配置，不代表已经证明它是 iOS 互通必需项；删除后的独立对照尚未进行，见 [P15](problem-log.md#p15setvendordata-是否为互通必需项)。
