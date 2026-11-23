# Android 17 多设备配对缓存选择（2026-11-20）

## 源码基线

只读分析 Ubuntu `/home/mxj/workspace/aosp-android17/packages/modules/Wifi`，提交 `67beff9acf0e91046d2a9a36a68cb06ef263d1f1`（2026-04-15）。不是设备 ROM/vendor 的实现证明；本轮未进行多设备真机测试，也未切换抓包模式。

## 已确认的选择逻辑

所有行号相对于 `service/java/com/android/server/wifi/`。

- `aware/PairingConfigManager.java:173`：`getPairedDeviceAlias(packageName, nonce, tag, mac)` 先取得调用应用包名对应的 alias 集合，再逐条 `checkMatchAlias`，返回首个身份校验匹配的 alias；没有匹配则 null。
- `aware/PairingConfigManager.java:186`：使用该 alias 缓存的对端 NIK 作为 HMAC-SHA256 密钥，对 ASCII `NIR`、本次收到的对端 MAC、nonce 顺序计算，取前 8 字节与收到的 tag 比较。MAC 是计算输入，不是固定 MAC 地址查表。
- `aware/PairingConfigManager.java:211`：通过匹配 alias 查询安全关联，取 NPK、AKM、cipher suite、对端 NIK。
- `aware/WifiAwareStateManager.java:5736` 的 `onMatchLocal`：发现事件按上述方法获取 alias，传给应用发现回调；若本会话已确认该 peer 则不重复验证。允许 verification、有匹配缓存且无活动配对请求时，自动发起验证，传入匹配缓存的安全参数。
- `aware/WifiAwareStateManager.java:5906` 附近的 `onPairingRequestReceivedLocal`：收到 verification 请求同样使用包名、nonce、tag、对端 MAC 匹配缓存；存在缓存且 verification 开启才接受，否则拒绝。首次 setup 请求走应用处理路径。
- `aware/WifiAwareStateManager.java:5986` 附近 `onPairingSecurityAssociationReceivedLocal`：通过 pairingId 找本次请求的 client、session、alias，把收到的 NPKSA 存到该 alias 下。
- `hal/WifiNanIfaceCallbackAidlImpl.java:476` 和 `:659`：发现/配对请求事件的 nonce、tag 来自 HAL 的 peerNira。

计算关系：`expectedTag = HMAC-SHA256(peerNIK, "NIR" || peerMAC || nonce)[0:8]`。

## 多设备含义及限制

设备 A/B/C 各缓存自己的对端 NIK、安全关联；收到 B 的有效身份标记后命中 B 的 alias，使用 B 的 NPK 进行验证。设备名称、DeviceInfo TXT、最近配对顺序不参与该选择。发现时识别缓存不等于验证已成功；以 verification 成功回调为准。

alias 是应用设置的缓存标签，不是空口身份本身。该实现的安全信息 Map 按 alias 索引，重复 alias 写入会覆盖原条目，因此应给不同对端使用不同 alias。多个条目若都能匹配，代码返回集合遍历中首个匹配项，不应依赖遍历顺序。

NDP 另有独立请求/安全配置；不能将上述缓存识别机制直接理解成应用按 alias 显式选择数据链路密码。本结论仅说明 Framework 如何识别已配对设备、选择配对验证安全关联；固件在验证后如何安装/选择内部密钥需另外追踪。
