# 高通 Publish CSIA 修复真机验证（2026-11-19）

## 范围与部署

对应 P19。设备 a0807d7 / TB323FU Android 17 与 iPhone 12 / iOS 27.2.1，现有 AwareBridge；保留已有配对缓存，不做首次 PIN 配对。主要对照在平板断开 STA 后进行，避免与 P07 的 STA 并发异常混淆。

用户确认 11 月 18 日候选库误改了 `nan_subscribe_request`，本轮提供的是修改 Publish 入口的新库。前一轮失败记录保留于 [候选库部署记录](csia-candidate-deployment-2026-11-18.md)。

- 输入：`/Users/mxj/Downloads/libwifi-hal.so`，704360 字节、ELF64 AArch64。
- SHA-256：`0cd80cb19390ebb38cf46f982359bf53545e8f2ed386a244550c782237c6a546`。
- 原库备份：`/Users/mxj/workspace/nan-capture-analysis/2026-11-19/before-publish-fix/libwifi-hal.so`，SHA-256 为 `1837883062e14520511f37cb9411c551421208539af4d20598b79450cc2a876c`。
- root/remount 后停止 `vendor.wifi_hal_legacy`，替换 `/vendor/lib64/libwifi-hal.so`，设置 0644、restorecon、sync、重启。service、AIDL V4 NDK 库和 VINTF 未重复替换。
- 重启完成后重新 root，核对设备文件 hash 一致，Wi-Fi 服务 PID 1292 的 maps 确认实际加载该路径。

只分析用户提供的 ELF，没有访问隔离服务器中的高通源码。Publish 路径 0x6759c～0x675ac 现在在 cipher_capabilities 为 0 时直接补 4，不再读取 Setup 作为补值门限。保留已有非零值；并非所有值一律覆盖为 4。

## 默认 Setup=false 已成功

09:47:39 初始 Publish，UI 配对开关为 false；HAL 记录 `putNanPublish: cipher capabilities :4`，347 字节请求含 `27 00 01 00 04`（小端类型 0x27、长度 1、值 4）。Pairing 配置 TLV 0x20 值为 `0a 00 00 00`，SDEA 配置为 `40 04 00 00`。完整 dump 含密钥，不提交原日志。

信道 6 抓到 56 个目标 Publish SDF：CSIA 均为 `04 07 01`，SDEA control 为 0x0440，属性结束后没有额外全零尾部。第一帧 45096，09:48:25.653817，抓包记录长度 527 字节。

注意：`04` 是 CSIA capability，`07 01` 为一个 cipher/service-instance 项；这里没有像 Apple 样本那样列出 `05 01`。尽管列表尚不与 Apple 完全相同，本次真实 iOS 已通过发现与连接，不能据此要求必须逐字节复制 Apple。

iOS 列表有两条同名平板记录。首次误选旧记录时，日志提示目标不在 allowed paired devices list，UI -11988；改选匹配记录后，09:51:29 Pair Verify authenticated、DataPath Started，09:51:31 Android 出现 Aware IPv6 地址。UI 双向计数由 5/5 增至 19/19。没有开启 Setup 来绕过此次首次连接。

## 更新开关与重连

09:51:54 updatePublish(true)，09:52:54 一分钟超时 updatePublish(false)。三份 Publish 请求均为 347 字节，各 TLV 顺序解析至恰好 347 字节，边界闭合：

| 状态 | 配对 TLV 0x20 | SDEA 配置 TLV 0x15 | capability TLV 0x27 |
| --- | --- | --- | --- |
| 初始 false | 0a 00 00 00 | 40 04 00 00 | 04 |
| update true | 0b 00 00 00 | 40 04 00 00 | 04 |
| 超时 update false | 0a 00 00 00 | 40 04 00 00 | 04 |

自动关闭后已有连接持续，09:53:04 iPhone 截图双向计数 38/38，随后断开前达到 48/48。Setup 关闭没有令 CSIA capability 再次消失。

09:53 主动从 iOS 断开后，Android UI 在心跳超时后显示“连接已释放；请重新发布并连接”，09:53:56 Framework 清理 attach/Publish；此时直接在 iOS 重连会等待，因为对端已经不发布。09:55:58 在 Android 重新开始发布（默认 false，HAL capability=4）后，iOS 当即 Pair Verify authenticated、DataPath Started，09:56:00 Android 获取新 Aware IPv6，iOS UI 再次显示双向通信。这轮等待不是 missing CSIA 过滤的复现。

本次已通过初始关闭状态的发现/缓存验证/NDP/双向通信，以及开关超时后链路保持、重新 Publish(false) 的重连。App 心跳超时会同时结束发布的恢复体验仍可改进；本轮不修改业务源码。

## 证据位置

Ubuntu：`/home/mxj/NAN/2026-11-19/`；Mac：`/Users/mxj/workspace/nan-capture-analysis/2026-11-19/`。抓包和完整 HAL/iOS 日志不入 Git。

- `01-publish-fix-initial-off-ch6.pcap`：默认关闭 Setup 的空口样本。
- `02-publish-fix-toggle-ch6.pcap`：初始连接及开关阶段；抓到 2 个目标 Publish，CSIA=04 07 01。没有在 true 和自动关闭后分别触发新的订阅，不能把此文件当作三个状态均有空口 Publish 的证据；各状态另有 HAL dump。
- `03-publish-fix-reconnect-ch149.pcap`：主动断开阶段，未抓到目标服务 Publish，不能用于证明字段正确。
- `03-publish-fix-reconnect-ch149.pcap`：本轮没有目标服务 Publish SDF，不能用作关闭后的字段证据。
- `04-publish-fix-republish-ch6.pcap`：重新发布及第二次连接阶段；帧 933，09:55:57.950046，CSIA=04 07 01、SDEA=0x0440、额外零尾=0。

第二次连接后又手动开启/关闭开关，HAL 两次仍记录 capability=4；关闭后 Android UI 双向计数为 32/32。未抓到这次手动关闭后的独立 Publish SDF，不将 HAL 证据写成空口证据。

## 收尾

Ubuntu monitor 接口已移除，wlp3s0 恢复 managed 并连接 lenovo-internet，默认路由走 wlp3s0；HTTPS 清华镜像返回 HTTP/2 200。第一次 restore 遇到 NetworkManager 接口尚不可用，第二次自行重试成功，没有再次要求用户输入 sudo。

平板已停止测试 App、恢复 30000 毫秒息屏及普通 Wi-Fi；iPhone 断开测试链路后执行电源锁屏，device-info 确认 backlightState=off。保留本次新 HAL 库及双方配对缓存。

## 验证限制

本轮使用已有配对凭据，不能替代清缓存后的首次配对验收。P07 的 STA 并发额外 23 字节问题独立，不能因为 CSIA 修复就宣称它一并解决。此 Vendor 默认值修补仅对当前硬件/ROM 的组合验证，不代表通用 capability 协商策略已完成。
