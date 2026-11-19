# 单端删除配对后的重新配对验证

日期：2026-11-19，时间为北京时间。

## 修改

按用户要求，Android 每次新建 Publish 时默认使能 PairSetup，清理上一轮在途请求/PIN 状态，从发布成功回调开始计时 60 秒。包括自动恢复时新建的 Publish；普通 updatePublish 不会强行重新开启。配对成功或窗口超时后仍关闭 PairSetup、更新发布配置和 UI。需要时可手动再次开启。该改动不删除系统配对缓存。

此前加入的防护继续保留：发布端只将本轮已经接受的 Setup 的成功回调作为新配对成功；Pair Verification 单独提示，不以缓存验证替代新 PIN 配对。UI 和 README 已同步默认窗口说明。

## 环境

| 设备 | 系统及路径 | 本轮用途 |
| --- | --- | --- |
| 联想 TB323FU / 303fdedc | Android 17 / API 37，Wi-Fi HAL V3 NDK 库 | Android 发布，iOS 单端删除后重新配对 |
| 联想 TB323FU / a0807d7 | Android 17 / API 37，此前部署的 Vendor V4 Bootstrap SSI 转换 | Android 单端删除后，订阅 iOS 并重新配对 |
| Apple iPhone 12 / iPhone13,2 | iOS 27.2.1 | 双向配对操作 |

双端靠近，App 前台。Android 临时断开 STA，避免已知 TB323FU STA 并发 Publish 异常干扰。未修改其他平板，未使用 Ubuntu 抓包，不是 STA 并发或吞吐验收。

## 场景一：只删除 iOS，Android 保留

先使用 303fdedc 发布并完成基线配对。18:44:00 Bootstrap Display 成功，18:44:05 收到新 Setup，18:44:06 成功回调记录 acceptedThisAttempt=true。

随后只在 iOS 设置 → 隐私与安全 → 配对设备中删除刚建立的 303fdedc 记录；另一台平板的记录保留。Android 不调用删除/重置缓存接口，保留原缓存。停止后重新发布，默认配对开关开启，再从 iOS 添加该 Android。

18:48:40.826 Bootstrap Display 成功；Android 显示新的 PIN；18:48:46.584 收到 Setup 请求；iOS 输入代码后，18:48:47.144 Android 收到 acceptedThisAttempt=true 的成功回调，iOS 配对页面退出。**该场景通过，不是只收到旧缓存验证成功。**

查询 Android 缓存由原来的两条增加为三条：旧记录没有自动被替换或清理。这说明支持重新配对，但目前不会自动合并旧别名。用户仍可通过配对缓存管理按需删除旧条目。

## 场景二：只删除 Android，iOS 保留

先在 303fdedc 仅删除刚新增的 Android 缓存，iOS 记录保留，切换为 iOS 发布、Android 订阅。18:54:45 App 调用 API 37 Bootstrap 并记录传入 TXT TLV 86 字节，但本次没有完成新 PIN 配对。303fdedc 仅有 V3 NDK 库；本轮没有取得该尝试的 iOS 接收元数据日志，**不能据此直接确认字段丢失位置，亦不能宣称 303fdedc 反向通过**。该限制与已知 V3 Bootstrap SSI 传递问题相关，但仍需设备日志核对。

改用具备此前已验证 V4 SSI 路径的 a0807d7：只在 Android 配对缓存管理中删除原有的一条记录，iOS 对应记录始终保留，不清 App 数据。iOS 打开允许 Android 配对，Android 订阅并发起配对。

19:11:39.419570 iOS wifip2pd 记录 Received Bootstrap Request，TXT Record size: 83 bytes；随后 Received Pairing SSI 非空，包含 pairingName（34 字节）、modelName（7 字节）、vendorName（6 字节）。Android 传入的 86 字节含三项 TXT 长度前缀，与 iOS 解析的 83 字节口径不同，并非丢失三字节。

iOS 显示新的 PIN，输入 Android 后，Android UI 显示“配对成功，选择设备后点击连接”，所选 iOS 设备显示“已配对”，iOS 本次 PIN 页面退出。**该场景在 a0807d7 上通过，iOS 保留旧配对也能接受重新配对。**

## 验证边界与失败记录

- 首轮基线、反向首次尝试有操作超过窗口而超时的情况；只计上述明确成功的轮次，超时不计通过。
- 两种单端删除场景分别在不同 Android 设备完成，不代表所有组合均通过。尤其 303fdedc 反向 V3 路径仍未通过。
- 本轮只验收重新配对及回调/UI，不重复验收 NDP、心跳或吞吐。
- 原先“不显示 PIN 却提示配对完成”的底层原因仍未证实；本轮成功说明默认新配对窗口及现有请求关联防护下可完成新 PIN 配对，不能推导 ROM 问题已彻底修复。
- 编译检查：Android assembleDebug、lintDebug 通过。PIN、密钥、完整原始日志及截图不进入仓库。

## 安装与收尾

最终 APK 已覆盖安装 303fdedc、a0807d7；最终修改含 UI 文案，assembleDebug/lintDebug 均通过。停止两端测试日志、停止 Android App；两台平板取消临时 STA 自动连接限制，恢复 30 秒息屏与 USB 非常亮，均重新连接 lenovo-internet 且网络标记 VALIDATED。iPhone 12 执行锁屏，读取 backlightState=off。成功配对保留。本轮未切换 Ubuntu monitor。

## 303fdedc 补齐 HAL 后的反向重新配对（同日追加）

用户指出该机尚未替换 Bootstrap HAL 服务和 so，要求替换后再验证。部署前确实没有 V4 NDK 库。先将原服务、VINTF XML、libwifi-hal.so 备份至 Mac `/Users/mxj/workspace/nan-capture-analysis/2026-11-19/vendor-v4-backup-303fdedc/`，未覆盖其他设备备份。

本次将 Downloads 中与 a0807d7 实际运行文件 SHA-256 完全相同的三个产物部署到 303fdedc：

| 设备目标 | SHA-256 |
| --- | --- |
| /vendor/bin/hw/android.hardware.wifi-service | b2af3812b34720cf85fe09853f4428fb73ba0211785b6968d9c8b929d96668b2 |
| /vendor/lib64/android.hardware.wifi-V4-ndk.so | 26c5f7d42131d8a2eb0381e8b1e428f48e21b35edd2cad482351c8521c279bc7 |
| /vendor/lib64/libwifi-hal.so | 0cd80cb19390ebb38cf46f982359bf53545e8f2ed386a244550c782237c6a546 |

root/remount 成功，停止 vendor.wifi_hal_legacy 后替换文件，将该 Wi-Fi VINTF fragment 声明改为 4，恢复权限/SELinux 标签并重启。原 V3 NDK 库保留。重启后 boot_completed=1、服务 running，getInterfaceVersion 返回 4，三份文件校验与 Mac 一致。本次没有重新编译厂商源码，使用用户提供并已在另一台验证的产物；不是宣称实现完整标准 V4。

初次复测 19:42:01，App 输入 86 字节，HAL 新日志确认 input length=86、sdeaLength=86；iOS 已收到 TXT 83 字节并解析三项 SSI，证明原先元数据丢失已解除。但最初几轮没有显示 PIN、未完成配对，不计通过。Android 此时仍有两条更早的旧别名；随后通过 App 配对缓存管理清除这两条，iOS 两条已配对 Y700 记录均保留。整个过程没有删除 iOS 配对。

中间 iOS 日志记录 `Failed to update nan0 pairing custom attributes to [] ... Apple80211Error Invalid argument`，并观察到 Android 所选服务离开/peer 变化。曾重启 App、重开系统配对页及终止配对选择器 UI；这些尝试未通过。之后保留 iOS 配对记录，关闭再打开两端 Wi-Fi，重新开始会话。平板 Wi-Fi 重启后自动重新连接 STA，首次未发现服务；再次取消 STA 自动连接后完成下面成功轮次。**尚未隔离清缓存、Wi-Fi 重置和 STA 状态分别对 PIN 恢复的影响，不能确认该 Invalid argument 为根因，也不能把一次成功概括为所有旧状态都能无干预恢复。**

20:06:12.706 Android App 发起 Bootstrap、TXT TLV=86；20:06:12.708 HAL 转换 input length=86、sdeaLength=86。20:06:12.998536 iOS 记录 TXT Record size: 83 bytes；紧接着 SSI 非空，pairingName=34、modelName=7、vendorName=6 字节。iOS 显示新 PIN，工具读取后输入 Android，Android 随后显示“配对成功，选择设备后点击连接”。本轮实际走新 PIN，而非仅用旧缓存验证。

结论更新：此前“303fdedc 反向未通过”的结论仅适用于原 V3/此前失败轮次；**部署匹配 HAL 后，303fdedc 反向新 PIN 配对已通过**。本轮确认 SDEA 信息从 HAL 到 iOS 接收日志闭环，没有另行空口抓包，不据此声称抓到了新 SDF；没有进行 NDP/吞吐验收。配对成功不代表已消除上述会话恢复限制。

成功后停止订阅，通过 App 查询到一条新缓存 `awarebridge-ios-108`。HAL 进程 maps 确认实际加载 V4 NDK 和本次 libwifi-hal.so。收尾停止日志及 Android App，303fdedc 恢复 USB 非常亮、30 秒息屏、连接 lenovo-internet 且网络 VALIDATED；iPhone Wi-Fi 已重新开启，锁屏确认 backlightState=off。本轮没有操作 Ubuntu，没有删除 iOS 记录，也没有改动其他平板。
