# Android 配对身份查询：App 直接调用 Framework

2026-11-20。针对同一 iPhone 重配后留下多条 alias、不同 NPK，App 只有 alias 无法可靠识别同一身份的问题，提供自定义 ROM 接口。方案为 **App → WifiAwareManager → IWifiAwareManager → WifiAwareServiceImpl → WifiAwareStateManager → PairingConfigManager**。

不需要 Priv-App、中转 Service、平台签名、root、配对文件解析，也不修改 Vendor AIDL、Wi-Fi HAL 或固件。此前准备的 Priv-App 方案已移除。此接口是项目自定义的 ROM 扩展，不是官方 Android 17 SDK 现有接口。

## 接口与身份含义

`WifiAwareManager.getAwareBridgePairingIdentities()` 返回 `Bundle`：

- `status`：`ok`。
- `source`：`framework-memory`。
- `records`：`ArrayList<Bundle>`，每条只有 `alias`、`peerId` 两个 String。

`peerId = hex(SHA-256(UTF8("NAN-Peer-Identity/v1") || 0x00 || UTF8(packageName) || 0x00 || PeerNik))`。零字节用于分隔字段；见 [实际 Java 实现](../rom/peer-identity/PeerIdentityId.java)。输出为完整 64 位小写十六进制字符串。原始 NIK、Local NIK、NPK 不出 Framework，不进入应用日志或界面。

同一调用包、同一 Peer NIK 的多条 alias 得到相同 peerId；修改显示名称、PeerHandle、随机 MAC 不参与该计算。不同应用包的输出不同。对端重置 NIK 会改变标识，所以它是配对身份标识，不是永不改变的硬件设备号。它只解释已有配对缓存，不能在首次配对前识别陌生设备，也不能代替 Pair Verification 的密码学认证。

App 的“配对缓存管理 → 读取配对身份”显示 alias 和 peerId。当前功能只读，不改变新配对 alias 生成策略，不自动删除任何缓存。相同 peerId 的重复记录识别出来后，仍需另行实现并验证 Framework 的同身份凭据更新策略，才能解决旧 NPK 首条匹配问题。

## 具体修改位置

以下路径均相对于 **你的 Android 17 ROM 源码根目录**；无需操作 Vendor 的 `hardware/interfaces/wifi/aidl`：

| 文件 | 修改 |
| --- | --- |
| `packages/modules/Wifi/framework/java/android/net/wifi/aware/WifiAwareManager.java` | 增加普通 App 可访问的公开方法；将本应用包名、attribution tag/source 传到系统 |
| `packages/modules/Wifi/framework/java/android/net/wifi/aware/IWifiAwareManager.aidl` | 在接口末尾追加查询方法，保留既有 transaction 排序 |
| `packages/modules/Wifi/service/java/com/android/server/wifi/aware/WifiAwareServiceImpl.java` | 检查 Wi-Fi/附近设备权限、包名归属与 Android 用户；在 Aware Handler 执行查询 |
| `packages/modules/Wifi/service/java/com/android/server/wifi/aware/WifiAwareStateManager.java` | 将已授权查询交给 PairingConfigManager |
| `packages/modules/Wifi/service/java/com/android/server/wifi/aware/PairingConfigManager.java` | 从本应用 alias 集合与内存安全关联生成只读快照 |
| `packages/modules/Wifi/service/java/com/android/server/wifi/aware/PeerIdentityId.java` | 新增应用范围身份派生函数 |
| `packages/modules/Wifi/framework/api/current.txt` 等 API 签名文件 | 用 API 更新构建流程生成；不能只改实现却将方法留在隐藏 API 中 |

实现片段放在 [rom/peer-identity](../rom/peer-identity)。Binder 层通过 `checkPackage(uid, callingPackage)` 防止调用方传其他包名，检查 ACCESS_WIFI_STATE、NEARBY_WIFI_DEVICES，并沿用 ROM 的附近设备/attribution 校验。当前缓存按包名索引；为防跨用户误读，本扩展明确拒绝 Android 非 0 用户。工作资料/次用户需先完善底层缓存的用户隔离，再放开查询。

查询串行执行于 Aware Handler，最长等待 3 秒，最多 256 条；异常不返回部分数据，不输出密钥或异常原因链。App 在工作线程调用，10 秒超时、关闭窗口或刷新会取消本次 UI 结果。

## 在隔离的 ROM 编译服务器上生成和应用补丁

将仓库中的整个 `rom/peer-identity` 文件夹拷贝到编译服务器，例如 `/tmp/nan-peer-identity`。假设你的 ROM 根目录是 `/work/android17`，执行下列命令；请将该路径换成真实源码根目录：

```bash
python3 /tmp/nan-peer-identity/prepare_patch.py \
  --wifi-root /work/android17/packages/modules/Wifi \
  --output /tmp/nan-peer-identity.patch

cd /work/android17/packages/modules/Wifi
git apply --check /tmp/nan-peer-identity.patch
cat /tmp/nan-peer-identity.patch
git apply /tmp/nan-peer-identity.patch
```

生成工具只输出补丁，不修改 ROM 目录；输出文件已存在会拒绝覆盖。缺少字段/方法、目录不匹配或已部分应用时会报错，需人工适配，不能跳过后强行套用。检查是基于符号和文件结构的适配保护，不等于 Android 17 完整编译检查。

## API 与编译

在 ROM 根目录加载环境并使用该产品原有 lunch 配置后执行：

```bash
cd /work/android17
source build/envsetup.sh
# 执行你平时构建这台平板所用的 lunch 命令。
m update-api
m framework-wifi service-wifi com.android.wifi
```

`m update-api` 会更新 API 文本；检查差异，只接受本次查询接口相关变化，不要提交其他模块的无关变化。此方法有意作为自定义 ROM 的公开 API，使用局部 `SuppressLint("UnflaggedApi")` 注解说明这一点；不是向 AOSP 上游新增未经 flag 管理的接口。你分支若有额外 API 审核规则，按该分支流程处理并记录，不要全局关闭 API 检查。

需要成套构建/部署匹配的 Framework 客户端与服务端；不能只换 `service-wifi.jar`、只改 Binder 一端，或只安装新版 App。设备当前 Wi-Fi 模块来自 `/apex/com.android.wifi`，APEX 是受签名/版本/挂载机制管理的，按该 ROM 产品正常的 Wi-Fi APEX/系统镜像打包、签名、刷机流程部署，不直接向只读 APEX 路径 push jar。本轮没有该隔离 ROM 的完整构建环境或签名产物，因此不提供未经验证的 APEX 热替换命令。

App 可继续用官方 SDK 37 构建：通过反射检测并调用 **自定义 ROM 已公开** 的 WifiAwareManager 方法。这不依赖隐藏 API 绕过；若仅新增 `@hide` 方法，没有将其纳入公开 API 及对应构建输出，普通 App 反射仍可能被拦截。发布到其他 ROM 时，按钮显示“当前 ROM 尚未提供配对身份接口”，既有获取/删除 alias 功能继续使用官方 API。

App 构建：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./NAN-Android/gradlew \
  -p NAN-Android :app:assembleDebug :app:lintDebug
```

产物：`NAN-Android/app/build/outputs/apk/debug/app-debug.apk`。不需要平台签名；用户需授予现有附近设备权限。

## 本轮验证和边界

- Android `assembleDebug`、`lintDebug` 通过。
- Java 合成数据测试通过：相同输入稳定、不同包隔离、身份重置改变 ID、输入不修改、非法 NIK 长度/命名空间拒绝。
- Python 补丁测试通过：可以完整应用、重复/部分应用拒绝、源结构不匹配不改文件。
- 补丁对公开 AOSP 提交 `c1981d605012aff5ed9729359d1ffaa0670576a6` 的五个实际文件生成并 `git apply --check` 通过。该参考源码不是本机 Android 17 ROM 的完整源码；字段/Handler 设计另与之前设备 `service-wifi.jar` 分析核对，不能据此宣称 ROM 编译通过。
- 未部署 Framework、未验证真实 peerId 返回、权限拒绝/跨用户分支、重启后的稳定性。本轮未安装 APK、未删除配对、未操作 iPhone 或 Ubuntu。

部署后应验证：同 Peer NIK 多 alias 得相同 ID；另一设备得到不同 ID；App A 无法指定 App B 查询；权限拒绝及次用户明确拒绝；普通 SDK/普通签名 App 能访问已公开接口；改名和重启保留身份时 ID 稳定；对端重置身份后 ID 变化；发布/订阅切换及现有配对、NDP、心跳不回归。原始凭据不要写入验收文档。
