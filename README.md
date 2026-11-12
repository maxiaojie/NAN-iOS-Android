# NAN-iOS-Android

**Wi-Fi Aware / NAN，连接 iOS 与 Android。** 两端应用名为 **AwareBridge**。

一个可阅读、可修改的跨端演示：Android 发布服务并显示配对码，iOS 使用系统配对界面添加设备并建立 Wi-Fi Aware 数据链路。两端通过原始 UDP JSON 每 5 秒发送心跳、回复确认，界面显示收发计数和最近接收时间。

```
NAN-iOS-Android/
├── NAN-Android/      # Android Studio / Gradle / Java
├── NAN-iOS/          # Xcode / SwiftUI / WiFiAware / Network
├── docs/             # 协议、ROM 条件、测试记录
├── scripts/          # 构建和协议验证
└── tests/            # 跨语言报文样本
```

## 功能与范围

- 简洁单页 UI：开始/停止、配对码、连接状态、发送/接收计数。
- Android 发布 `_aware-bridge._udp`，iOS 配对并主动连接；当前实现单对端，未实现角色互换。
- UDP socket 绑定 Android 的 Aware `Network`；在 NDP 响应中通知监听端口和协议号 `17`。
- 双向心跳与 ACK；ACK 不再回复 ACK，避免循环。超过 20 秒无有效接收显示心跳超时。
- 停止或退到后台时释放 socket、计时任务、网络请求和 Aware 会话。
- 前台测试期间保持屏幕常亮，停止时撤销本 App 的常亮请求。

**这是前台互通演示。** 心跳能避免无数据导致的 inactivity teardown，但不能阻止系统后台挂起、Wi-Fi 关闭、超出距离或 ROM 主动终止链路。两端退到后台会主动断开，返回后需重新发布/连接。

## 环境

### Android

- Android 16 / API 36 或更高，硬件及 ROM 支持 Wi-Fi Aware **和 Aware 配对**。
- 开启 Wi-Fi 和系统定位，授予附近设备、定位权限。
- JDK **21**、Android SDK 36；Gradle wrapper 8.6、AGP 8.4.0 沿用原 Android 项目。AGP 对 SDK 36 会给出兼容性提示。
- **当前依赖定制 ROM 的非公开接口**：`setVendorData` 及 AOSP Wi-Fi 编译接口来自原项目 `framework-wifi.jar`，仅用于编译。复制 jar 不会给普通 ROM 解锁这些接口。请先阅读 [Android ROM 条件](docs/android-rom.md)。

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
- 使用支持 WiFiAware 和新版 Network API 的 Xcode，本项目在 Xcode 27.2 编译验证。
- 打开 `NAN-iOS/AwareBridge.xcodeproj`，选择 `AwareBridge` scheme。
- 配置自己的 Bundle Identifier / Development Team；签名 profile 必须包含 Wi-Fi Aware 的 `Subscribe` entitlement。
- 默认 Bundle Identifier：`io.awarebridge.ios`。仓库不包含个人 Team、profile、证书或私钥。

```sh
xcodebuild -project NAN-iOS/AwareBridge.xcodeproj -scheme AwareBridge \
  -sdk iphoneos -configuration Debug -derivedDataPath /tmp/nan-derived \
  CODE_SIGNING_ALLOWED=NO build
```

以上命令仅验证编译，安装真机需要有效签名。可在未跟踪的 `NAN-iOS/Local.xcconfig` 中配置本机签名，模板见 `NAN-iOS/Local.xcconfig.example`。

## 使用

1. Android 点击「开始发布」。
2. iOS 点击「添加 Android 设备」，选择 Android 设备；将 Android 显示的 6 位代码输入 iOS 系统配对界面。
3. 配对成功后，在 iOS 选择该设备并点击「连接」。Android 无需再点连接。
4. 两端出现双向通信状态，发送/接收计数每 5 秒增长。持续观察至少 2 分钟，确认链路不会因无流量而断开。
5. iOS 点「断开」，Android 点「停止」；再次开始可复用系统已保存的配对。

首次 iOS 发包后，Android 学到该 UDP 会话的来源端口，再定时向它发送心跳。因此 Android 显示 NDP 已建立但 RX 为 0 时，应先检查 iOS 是否发送首包。

## 协议与扩展

参阅 [UDP 协议](docs/protocol.md)。服务名是发现与端口协商标识；Wi-Fi Aware 提供的是 IP 链路。后续可在同一链路扩展 TCP，只需另行监听并通知 TCP 端口，且 socket 必须走 Aware 网络。

当前演示保留原项目的固定测试 passphrase；**它不是配对 PIN，也不是适合产品发布的密钥管理方案**。当前设备 ROM 的已配对数据路径行为需单独验证，不据此推断所有 Android ROM 都能和 iOS 互通。

## 验证

- 构建与报文测试：`scripts/check.sh`。
- 真机步骤和实际结果：[测试记录](docs/validation.md)。
- iOS 端基于 Apple [Building peer-to-peer apps](https://developer.apple.com/documentation/wifiaware/building-peer-to-peer-apps) 的配对与 Network 连接方式，使用 **原始 UDP** 替代示例的 `Coder/TLV` 协议栈。
- 官方参考：[Apple Wi-Fi Aware](https://developer.apple.com/documentation/wifiaware/adopting-wi-fi-aware)、[Android Wi-Fi Aware](https://developer.android.com/develop/connectivity/wifi/wifi-aware)。

## 许可

本项目新增代码采用 MIT；Apple 示例派生部分保留 [Apple 示例许可](NAN-iOS/LICENSE.txt)。AOSP 编译 jar 的说明及许可见 [第三方说明](THIRD_PARTY_NOTICES.md)。
