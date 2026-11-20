# 配对缓存身份识别问题与后续 ROM 修改建议

记录日期：2026-11-20。**当前状态：问题保留，暂不实现；本轮 App 与 Framework 补丁已回退，后续在 ROM 中处理。**

## 已确认的问题

在 a0807d7（TB323FU、Android 17，设备名 baldur-1）与 iPhone 12 重配的案例中，Android 同一应用留下三条不同 alias，Local NIK / Peer NIK 相同，但 NPK 不同。设备 Framework 的 `PairingConfigManager.getPairedDeviceAlias()` 按身份返回首条匹配记录，并不优先最新凭据，存在命中旧安全关联的风险。

经用户授权只删除两条旧记录、保留新记录后，不重新输入 PIN 即恢复 Pair Verification、NDP 和双向心跳。未捕获失败轮次实际选中的 alias，因此不能将具体旧记录的选中过程当作已直接观测的事实。环境、时序和证据见 [原始诊断记录](baldur1-repair-cache-verification-2026-11-20.md)。

App 现有公开 API 可以获取、删除本应用的配对 alias，但 alias 本身无法说明多条缓存是否属于同一配对身份。显示名称可以修改、重名，PeerHandle 是会话相关编号，随机 MAC 也不适合作为稳定设备标识。

## 后续优先在 ROM 中处理

优先检查 `packages/modules/Wifi/service/java/com/android/server/wifi/aware/PairingConfigManager.java` 保存和选择安全关联的逻辑：新配对完成时，根据应用范围及已确认的 Peer NIK 更新同身份凭据，避免同一身份保留多个互相矛盾的 NPK；同时检查旧 alias 的引用、持久化、重启恢复以及发布/订阅角色互换。

这是后续建议，**当前未实现、未验证**。不能仅根据 alias 数字大小或集合遍历顺序推断配对新旧，也不能按显示名称自动删除记录。

如果 App 后续仍需识别重复身份，可在 Framework 增加按调用应用隔离的只读查询，返回 `alias + peerId`。peerId 可以由应用范围和 Peer NIK 派生，原始 NIK、NPK 留在系统内。既然要修改 Framework，App 可直接通过 WifiAwareManager/Binder 接口查询，无需 Priv-App 中转，也无需解析配对文件。

同一应用、同一 Peer NIK 的多条记录应得到相同 peerId；对端重置 NIK 后标识会变化。它表示配对身份，不是永久硬件编号，不能在陌生设备首次配对前保证识别设备，也不能代替 Pair Verification。接口应校验调用包归属、权限和用户隔离；是否需要对 App 暴露该能力，应在 ROM 修复方案确定后决定。

## 当前 App 使用 alias 的位置

- Android 发布并接受新配对：`AwareController.onPairingSetupRequestReceived()` 调用 `acceptPairingRequest(requestId, peer, "awarebridge-" + peer.hashCode(), CIPHER, PIN)`，指定本次保存安全关联的别名。
- Android 订阅并主动配对：`SubscriberController.submitPin()` 调用 `initiatePairingRequest(peer.handle, "awarebridge-ios-" + peer.handle.hashCode(), CIPHER, pin)`，指定本次保存别名。
- 发现回调：读取 `ServiceDiscoveryInfo.getPairedAlias()`；非空时只将该 peer 标为已配对，不按该值主动选取密钥。配对/验证成功回调虽然带 alias，当前代码不保存该参数用来控制后续缓存选择。
- 配对缓存管理：`getPairedDevices()` 获取 alias 列表，`removePairedDevice(alias)` 删除用户指定的一条；`resetPairedDevices()` 清除本应用全部记录。

当前 alias 后缀来自 PeerHandle 的 hashCode，属于会话相关编号，不是稳定设备标识。后续 Verification 的缓存选择由 Framework 完成，App 建立数据链路时也没有传入 alias 来指定使用某条 NPK。这些逻辑本轮保持不变。

## 本次回退范围

提交 `4040c2f` 曾加入 App“读取配对身份”入口、客户端适配、Framework 接口片段、补丁生成工具及相关测试。按用户要求，本轮全部撤回这些实现，App 回到该提交之前的代码，仅保留本问题记录和 ROM 修改建议。

此前仅完成 APK 构建/lint、合成身份测试及公开 AOSP 参考源码的补丁检查；未构建、部署修改后的 ROM，也未安装该轮 APK。因此本次无需在设备上卸载服务或恢复 Framework；未对设备、iPhone、Ubuntu 做操作。原有配对缓存管理功能保留。

## 后续 ROM 验证要求

需要覆盖同一设备重复配对、单端删除后重配、发布/订阅角色互换、多设备并存、应用/Android 用户隔离、持久化重启、Pair Verification、NDP 和持续心跳。若增加只读 peerId 查询，还需核对改名不改变同一身份、身份重置后标识变化、不同应用不可互查，以及未修改 ROM 时 App 的兼容行为。

当前问题仍未永久修复；前次定向删除旧缓存只是已验证的恢复手段，不代表自动重配已修复。
