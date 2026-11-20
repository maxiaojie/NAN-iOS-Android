# baldur-1 重新配对成功但数据链路失败

日期：2026-11-20。设备：a0807d7 / 联想 TB323FU / Android 17（API 37），iPhone 12 / iPhone13,2 / iOS 27.2.1。使用已有 Vendor V4 Bootstrap 服务和此前修改的 libwifi-hal.so，运行接口查询为 4；没有修改 ROM 或 App 代码。

## 现象与时序

用户将 Android 系统设备名改为 baldur-1，在 iOS 删除原配对并重新 PIN 配对成功，随后连接失败，Android 提示“已有配对验证失败；如对端已删除配对，请打开配对开关后重新添加”。

Android 日志确认发布的设备 TXT 已为 modelName=TB323FU、pairingName=baldur-1、vendorName=LENOVO；SSI 总长 60 字节。10:25:06.726 Display Bootstrap 成功，10:25:12.988 新 Setup 请求，10:25:13.553 acceptedThisAttempt=true 的 Setup 成功。说明此次新 PIN 配对确实完成，不是 App 将 Verification 成功误报为新配对。

10:25:25.045 和 10:25:50.732 Framework 两次接受 requestType=1 的 Pair Verification 并调用 HAL；10:25:45.442 和 10:26:11.263 返回失败 reason=1，App 随后显示验证失败。HAL 有 Peer already paired / Peer NIK not available, Ignore NIRA validation 日志，但不单凭这些语句判断错误层次。

USB 接入 iPhone 12 后，在保留两端缓存的条件下复现连接失败。10:31:55 iOS 发 Authentication Request，10:31:56.397 收到 Android Authentication Response，10:31:56.399 发送 Authentication Rejection；错误为 `Pair Verify Authentication Failure: Failed to send authentication confirmation because <WiFiP2PError Invalid Security Parameters>`。之后还有重试超时。失败位于已有配对认证阶段，不是应用 TCP/UDP 心跳或端口收发阶段。

## 缓存证据与实际 ROM 实现

只读获取 `/data/misc_ce/0/apexdata/com.android.wifi/WifiConfigStoreAware.xml`，本应用有三个别名：

| 别名 | 来源 | 凭据比较 |
| --- | --- | --- |
| awarebridge-ios-138 | 此前 Android 订阅 iOS 的配对 | 与另外两条 LocalNik、PeerNik 相同，NPK 不同 |
| awarebridge-139 | 此前 Android 发布的配对 | 与另外两条 LocalNik、PeerNik 相同，NPK 不同 |
| awarebridge-141 | 今天重新 PIN 配对 | 同一身份，新 NPK |

原始密钥值、完整配置和完整日志只存本地临时诊断文件，不写入文档或仓库。比较使用实际字节而非设备名称；三条 LocalNik 均 16 字节、PeerNik 均 16 字节、NPK 均 32 字节。

本轮从该设备实际 `/apex/com.android.wifi/javalib/service-wifi.jar` 提取 classes.dex，用 Android SDK dexdump 核对，不依赖其他分支源码猜测：

- `PairingConfigManager.addPairedDeviceSecurityAssociation(packageName, alias, info)` 将 alias 加入本应用 Set，按 alias 保存 PeerNik 及安全关联；没有按相同 PeerNik 统一替换其他别名。
- `checkMatchAlias(alias, nonce, tag, mac)` 用 alias 对应的 PeerNik 执行 HMAC-SHA256，输入 `NIR || mac || nonce`，前 8 字节与 tag 比较。设备名不参与。
- `getPairedDeviceAlias(packageName, nonce, tag, mac)` 遍历应用别名集合，第一个匹配就返回，不根据配对时间挑最新 NPK，也没有在认证失败后遍历其他同身份凭据的此处逻辑。
- `WifiAwareStateManager.onPairingRequestReceivedLocal()` 对 Verification 取得上述 alias，再取其安全关联的 NPK/PeerNik 调用响应；新 Setup 则通知 App。

因此相同 PeerNik 的三条记录都具备相同身份匹配能力，但 NPK 不同。App 当前使用 PeerHandle 派生别名（发布 awarebridge-编号，订阅 awarebridge-ios-编号），重新配对及角色切换可积累多个别名。PeerHandle 是会话标识，不能当作稳定的设备身份；pairingName 也不是安全身份或缓存键。

## 经授权的对照验证

用户明确授权仅删除 awarebridge-139、awarebridge-ios-138，保留 awarebridge-141；iOS 记录始终不动，不重新 PIN 配对，不修改名称、不重启 Wi-Fi HAL、不刷其他文件。

通过 App 配对缓存管理的单条删除调用标准 removePairedDevice。停止/重新发布服务，再由 iOS 用 baldur-1 原有配对直接连接。10:42:51.427 Android 记录 cached pairing verification succeeded；10:42:51.749 NDP Confirm accept=true、reason=0。双端 UI 均显示双向通信，Android 先为 17/17，后为 42/42，持续超过两分钟。iOS 截图采样为 16/16（采样时刻不同）。

配置异步写盘：删除后立即读取的 XML 曾仍含旧条目，不能据此认为删除失败；最终落盘确认只剩 awarebridge-141，新条目的安全字段与删除前完全相同。最初关于“只删 ios-138 即恢复”的中间判断已更正，最终成功轮次是在两条旧记录都已提交删除后，不能归因到其中某一个别名。

## 结论与修复方向

**本次失败高度明确指向重复配对缓存造成的旧 NPK 匹配风险：新 PIN 配对成功，但同一 PeerNik 对应多个不同 NPK；Framework 首条匹配策略可能使用旧凭据，iOS 拒绝认证。清除重复旧凭据、保留新凭据即可直接建链，且新凭据未变。** 不是简单改名导致协议不支持，也不是需要重复打开 PairSetup 才能连接。App 的失败提示是泛化排查建议，本次并不能理解成“iOS 还没重新配对”。

严格边界：现有日志没有输出当次 Framework 选中的 alias 或其密钥摘要，不能宣称已逐字节证明失败响应用了哪条旧 NPK；本次停止/重新发布也是对照操作的一部分，未做旧缓存回灌的反证。HAL 同时维护内部状态，但不必把本问题直接归到 Firmware。现在确认的是重复缓存、实际选择算法、iOS 认证拒绝及清理后的成功闭环。

ROM 修复建议：在 PairingConfigManager 保存新安全关联时，按应用范围和已确认的 PeerNik 识别旧安全关联，更新/清理同身份旧 NPK，并处理不同角色的别名引用；不能只按名称删除，更不能全量删除其他设备。需回归同端重配、单端删配对、角色互换、多设备、持久化重启。仅按遍历顺序选“最后一条”不可靠，当前数据并无可信的配对时间排序。

App 层可改善别名管理及失败说明，但公开 API 只提供别名列表，不直接提供 PeerNik，不能从 App 按同名设备或 PeerHandle 安全地断言哪些缓存应自动删除。本轮按用户确认执行定点清理，没有加入自动全量清缓存方案。

### alias 责任与长期方案

alias 由 App 提供，Framework 按 alias 保存记录；同 alias 更新，不同 alias 新增。当前 App 以临时 PeerHandle 生成别名是设计不足，不应把缓存累积完全归为底层责任。若能获得稳定、可认证的对端标识，App 应持久化“对端身份 → alias”，发布/订阅两种角色共用映射。系统设备名、随机发现 MAC 和 PeerHandle 都不能直接当作稳定身份。当前发布端 Bootstrap/Setup 回调并不向普通 App 提供 PeerNik，无法仅依靠公开别名列表可靠合并未知对端；可采用双方协商并验证的持久化 App 设备标识，或在 ROM 已掌握 PeerNik 的配对管理层去重。不能用全应用固定 alias，避免不同设备相互覆盖。

收尾：两条旧记录删除已最终落盘，保留新 awarebridge-141；停止 Android App 和日志，取消临时 STA 自动连接限制，主动恢复 lenovo-internet，网络 VALIDATED，USB 非常亮恢复。iPhone 12 的 devicectl lockState 确认 passcodeRequired=true（已锁定）；显示状态读取仍曾为 activeOn，因此不宣称已确认背光关闭。本轮没有使用 Ubuntu 抓包，也没有改动其他设备。
