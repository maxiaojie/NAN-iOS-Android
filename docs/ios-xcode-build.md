# Xcode 直接打开时的构建与签名（2026-11-23）

## 模拟器链接错误

用户选择 iOS Simulator 构建时报告：

```text
Building for 'iOS-simulator', but linking in object file
libiperf.a[2](cjson.o) built for 'iOS'
```

当前 tools/build-libiperf.sh ios 固定使用 iphoneos SDK、arm64 真机目标；原生库不包含模拟器版本。即使 Apple Silicon 模拟器和真机均使用 arm64，两者平台也不同，不能混用。此前助手以 xcodebuild -sdk iphoneos 编译并安装真机，没有覆盖模拟器构建。没有通过修改架构排除项或最低系统版本解决此问题。

在 Xcode 顶部选择已连接的 iPhone，或 Any iOS Device (arm64)。本轮不新增模拟器 slice，真实 Wi-Fi Aware 互通仍需真机。

## 真机缺少 Development Team

用户改选真机后提示 Signing for AwareBridge requires a development team。此前安装命令显式传入 DEVELOPMENT_TEAM、PRODUCT_BUNDLE_IDENTIFIER、CODE_SIGN_STYLE=Manual、PROVISIONING_PROFILE_SPECIFIER 及 CODE_SIGN_IDENTITY；这些参数不会自动保存到 Xcode 工程，Git 中也不包含个人签名资料。

原项目虽然在 Configuration/SampleCode.xcconfig 中可选引入 ../Local.xcconfig，但 target 的 Build Settings 又固定 PRODUCT_BUNDLE_IDENTIFIER 和 CODE_SIGN_STYLE，优先级高于配置文件，导致本机 App ID/手动签名覆盖不完整。

修正为：将默认 PRODUCT_BUNDLE_IDENTIFIER=io.awarebridge.ios、CODE_SIGN_STYLE=Automatic 移入 SampleCode.xcconfig，放在 Local.xcconfig 引入之前；删除 Debug/Release target 中同名固定项。本机覆盖可以同时影响 Xcode GUI 和不带签名覆盖参数的 xcodebuild。没有把个人 Team、App ID、profile、证书信息写入 Git。

## 本机使用

1. 打开 NAN-iOS/AwareBridge.xcodeproj，选择 AwareBridge scheme 和 iPhone 真机目标。
2. 将 NAN-iOS/Local.xcconfig.example 复制为 NAN-iOS/Local.xcconfig，填写开发团队和已注册的 Bundle ID。
3. 自动签名使用 CODE_SIGN_STYLE=Automatic；已有合适的描述文件则使用 Manual，填写 PROVISIONING_PROFILE_SPECIFIER、CODE_SIGN_IDENTITY。
4. 描述文件应包含 com.apple.developer.wifi-aware 的 Publish 和 Subscribe 权限，并匹配 App ID、签名证书及目标设备。选择任意个人团队不代表具备该权限。
5. 保存后在 Xcode 构建；若界面仍保留旧诊断，重新打开工程再构建。

Local.xcconfig 已被 .gitignore 排除。本轮本机使用此前真机测试对应的已有有效证书和 Wi-Fi Aware profile，不申请新证书、不修改开发者门户。

命令行等价构建（仓库根目录）：

```sh
xcodebuild -project NAN-iOS/AwareBridge.xcodeproj -scheme AwareBridge \
  -configuration Debug -sdk iphoneos -destination 'generic/platform=iOS' build
```

只检查编译而不签名时加 CODE_SIGNING_ALLOWED=NO；该结果不能直接安装到 iPhone。本轮先验证不签名真机构建通过，修正本机配置后再以不带任何签名覆盖参数的命令验证签名构建。具体结果补充在验证台账中。
