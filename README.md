# NAN-iOS-Android

**Wi-Fi Aware / NAN，连接 Android 与 iOS，也支持同平台设备互联。** 两端应用名为 **AwareBridge**。

一个可阅读、可修改的跨端演示：支持 Android↔Android、iOS↔iOS、Android↔iOS；两端均可选择发布或订阅角色。系统 PIN 配对后建立 Wi-Fi Aware 数据链路。两端通过原始 UDP JSON 每 5 秒发送心跳、回复确认，界面显示收发计数和最近接收时间。

```
NAN-iOS-Android/
├── NAN-Android/      # Android Studio / Gradle / Java
├── NAN-iOS/          # Xcode / SwiftUI / WiFiAware / Network
├── docs/             # 协议、ROM 条件、测试记录
├── scripts/          # 构建和协议验证
└── tests/            # 跨语言报文样本
```

## 功能与范围

- 分区单页 UI：配对和连接、数据链路、性能测试；Android 另设配对缓存管理区域。
- 内嵌 libiperf3，支持 TCP/UDP、双向测试、可配置并行线程数（默认 4）；UDP 发送速率可设，0 表示不限速。见 [性能测试说明](docs/throughput-test.md)。
- Android 停止会话后可通过“管理配对缓存”查询、逐条删除或清空本应用的系统配对别名；iOS 端需在“设置 → 隐私与安全 → 配对设备”单独删除。
- 两端可切换发布/订阅角色，使用 `_aware-bridge._udp`；每次仅连接一个对端。
- UDP socket 绑定 Android 的 Aware `Network`；在 NDP 响应中通知监听端口和协议号 `17`。
- 双向心跳与 ACK；ACK 不再回复 ACK，避免循环。超过 20 秒无有效接收显示心跳超时。
- 停止或退到后台时释放 socket、计时任务、网络请求和 Aware 会话。
- 前台测试期间保持屏幕常亮，停止时撤销本 App 的常亮请求。

**这是前台互通演示。** 心跳能避免无数据导致的 inactivity teardown，但不能阻止系统后台挂起、Wi-Fi 关闭、超出距离或 ROM 主动终止链路。两端退到后台会主动断开，返回后需重新发布/连接。

## 环境

### Android

- Android 17 / API 37 或更高，硬件及 ROM 支持 Wi-Fi Aware **和 Aware 配对**。
- 开启 Wi-Fi 和系统定位，授予附近设备、定位权限。
- JDK **21**、NDK **28.0.12433566**、CMake **3.22.1**、Android SDK Platform **37.0**；Gradle wrapper **9.3.1**、AGP **9.1.1**。compileSdk / minSdk / targetSdk 均为 37；使用新增 Bootstrap message 接口携带配对设备信息，不再支持 Android 17 以下版本。
- 使用标准 Android SDK 编译，不需要 `setVendorData` 或 `framework-wifi.jar`，不覆盖 SDK 的 Wi-Fi 编译接口。不同 ROM 仍需验证 Aware 配对和数据链路能力，见 [Android ROM 条件](docs/android-rom.md)。

```sh
cd NAN-Android
# 本机配置 SDK；不要提交 local.properties。
printf 'sdk.dir=/path/to/Android/sdk\n' > local.properties
./gradlew :app:assembleDebug :app:lintDebug
adb -s YOUR_ANDROID_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

应用 ID：`io.awarebridge.android`，可与原 `com.mxj.wifiaware` 共存。测试前请停止原 App 的发布，避免争用 Aware 资源。

### iOS

- 支持 Wi-Fi Aware 的 iPhone / iPad，iOS / iPadOS 26+；真机验证需要设备与系统支持，模拟器编译不能代替无线测试。
- 使用支持 WiFiAware 和新版 Network API 的 Xcode，本项目在 Xcode 27.2 编译验证；内嵌 libiperf 当前仅构建 iphoneos arm64，暂不提供模拟器 slice。
- 性能测试的 Aware 新连接适配需要 iOS 26.4+；已验证 Android 发布 / iOS 订阅时的双向 TCP/UDP，结果包含 iOS Network framework 适配开销，见性能测试文档。
- 打开 `NAN-iOS/AwareBridge.xcodeproj`，选择 `AwareBridge` scheme。
- 配置自己的 Bundle Identifier / Development Team；签名 profile 必须包含 Wi-Fi Aware 的 `Subscribe` 和 `Publish` entitlement。
- 默认 Bundle Identifier：`io.awarebridge.ios`。仓库不包含个人 Team、profile、证书或私钥。

```sh
xcodebuild -project NAN-iOS/AwareBridge.xcodeproj -scheme AwareBridge \
  -sdk iphoneos -configuration Debug -derivedDataPath /tmp/nan-derived \
  CODE_SIGNING_ALLOWED=NO build
```

以上命令仅验证编译，安装真机需要有效签名。可在未跟踪的 `NAN-iOS/Local.xcconfig` 中配置本机签名，模板见 `NAN-iOS/Local.xcconfig.example`。

Xcode 顶部运行目标请选择已连接的 iPhone 或 `Any iOS Device (arm64)`；选择模拟器会因 libiperf 的平台不匹配而链接失败。若提示需要 Development Team，在 Target → Signing & Capabilities 选择有 Wi-Fi Aware 权限的团队，或复制本机签名模板并填好配置。项目默认签名和 App ID 可由 Local.xcconfig 覆盖，个人配置不入库。详细步骤见 [Xcode 构建与签名](docs/ios-xcode-build.md)。

## 使用

任意组合都选择一端“发布服务”、另一端“发现并连接”。配对后由订阅端点击连接；两侧都可发起任一方向的性能测试。发布端运行 iperf server，订阅端运行 client，不按系统区分。**协议已升级 v2，两端 App 必须同时更新**，已有系统配对可保留。实现与实测范围见 [三种组合验证](docs/multi-platform-2026-11-23.md)。

### Android ↔ Android / iOS ↔ iOS

Android 发布端开启一分钟配对窗口，Android 订阅端选择设备、输入发布端代码后连接。两台 iPhone 则在发布端打开“允许附近设备配对”，订阅端“添加附近设备”；完成后关闭系统配对页，开始发布，再由订阅端选择设备连接。

### Android 发布 → iOS 连接

1. Android 点击「开始发布」。
2. 每次开始发布（含自动重建 Publish），Android 默认开启「允许新设备配对（1 分钟）」；窗口超时后需要配对可手动重新打开。iOS 点击「添加附近设备」，选择 Android 设备；将 Android 显示的 6 位代码输入 iOS 系统配对界面。
3. 配对成功或开启满 1 分钟后，Android 自动关闭新配对，继续发布服务；设计上已配对设备连接无需打开此开关，当前实机限制见下方说明。配对成功后，在 iOS 选择该设备并点击「连接」。Android 无需再点连接。
4. 两端出现双向通信状态，发送/接收计数每 5 秒增长。持续观察至少 2 分钟，确认链路不会因无流量而断开。
5. iOS 点「断开」，Android 点「停止」；再次开始可复用系统已保存的配对。

> 原厂/未修复 HAL 的历史限制：TB323FU a0807d7 与 iPhone 12 配对开关关闭时，重新发现服务可超时（-11988 / subscriberTimeout）；暂时打开 1 分钟配对窗口后可连接，窗口关闭后已有链路能继续。此操作会允许新配对，仅为临时绕过，已通过高通 Publish CSIA 修正产物进行后续验证，原厂版本仍须核对，见 [问题 P19](docs/problem-log.md) 和 [空口属性对照](docs/pair-setup-discovery-analysis.md)。

首次 iOS 发包后，Android 学到该 UDP 会话的来源端口，再定时向它发送心跳。因此 Android 显示 NDP 已建立但 RX 为 0 时，应先检查 iOS 是否发送首包。

### iOS 发布 → Android 连接

1. iOS 选择「发布服务」。首次配对先打开「允许附近设备配对」，Android 选择订阅角色并开始订阅。
2. Android 选择发现的 iOS 服务，点击配对，将 iOS 系统页显示的 PIN 输入 Android。配对完成后关闭 iOS 系统配对页。
3. iOS 点击「开始发布」，Android 等待缓存配对验证后选择设备、点击连接。已配对设备直接从此步骤开始即可。
4. iOS 固定监听 UDP `45454`；Android 使用 Aware 网络中的对端 IPv6，并主动发出首包。两端每 5 秒心跳并回复 ACK。
5. 停止发布/订阅后可切换角色。iOS 仅保留一条已接入连接；发布到期后不主动重建发现会话，新的连接需要停止后重新发布。

**反向首次配对已在 2026-11-18 实机通过：** a0807d7 使用用户构建的 Vendor V4 实验产物后，Bootstrap Follow-up 的 SDEA 携带完整设备 TXT，iPhone 12 接受请求，输入 PIN 后双方配对成功。此前 Vendor V3 会跳过新增 SSI 字段，原厂未修复版本仍受限制。数据通信还需授予 Android 17 本地网络访问权限，App 已加入声明和运行时申请。详见 [Vendor V4 实机验证](docs/vendor-v4-bootstrap-validation.md)；不据此宣称私有 HAL 与完整标准 V4 兼容。

## 协议与扩展

参阅 [UDP 协议](docs/protocol.md)。服务名是发现与端口协商标识；Wi-Fi Aware 提供的是 IP 链路。后续可在同一链路扩展 TCP，只需另行监听并通知 TCP 端口，且 socket 必须走 Aware 网络。

当前演示保留原项目的固定测试 passphrase；**它不是配对 PIN，也不是适合产品发布的密钥管理方案**。当前设备 ROM 的已配对数据路径行为需单独验证，不据此推断所有 Android ROM 都能和 iOS 互通。

## 验证

- 所有问题、处理过程及剩余限制：[问题台账](docs/problem-log.md)，后续按 [开发记录要求](AGENTS.md) 持续更新。

- 构建与报文测试：`scripts/check.sh`。
- 真机步骤和实际结果：[测试记录](docs/validation.md)。
- iOS 端基于 Apple [Building peer-to-peer apps](https://developer.apple.com/documentation/wifiaware/building-peer-to-peer-apps) 的配对与 Network 连接方式，使用 **原始 UDP** 替代示例的 `Coder/TLV` 协议栈。
- 官方参考：[Apple Wi-Fi Aware](https://developer.apple.com/documentation/wifiaware/adopting-wi-fi-aware)、[Android Wi-Fi Aware](https://developer.android.com/develop/connectivity/wifi/wifi-aware)。

## 许可

本项目新增代码采用 MIT；Apple 示例派生部分保留 [Apple 示例许可](NAN-iOS/LICENSE.txt)。AOSP 编译 jar 的说明及许可见 [第三方说明](THIRD_PARTY_NOTICES.md)。

当前真机状态与已知问题见 [验证记录](docs/validation.md) 和 [TB323FU STA/NAN 并发问题](docs/tb323fu-sta-nan-issue.md)。TB323FU 断开普通 Wi-Fi 时已通过配对、NDP 和持续双向 UDP 心跳/ACK；STA 并发发现异常及首次 NDP 失败仍需处理。
