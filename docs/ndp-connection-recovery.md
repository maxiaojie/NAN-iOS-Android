# NDP 建立失败 -11987 与应用恢复处理

日期：2026-11-13。TB323FU 已断开普通 Wi-Fi、保留 Wi-Fi 开关，iPhone 12 使用 AwareBridge。发现阶段的 STA/NAN 问题另见 [ROM 问题记录](tb323fu-sta-nan-issue.md)。

## 失败发生在哪一步

发现、系统 PIN 配对成功，不表示 NDP 已成功。首次失败时：

1. iOS 已解析独立服务 `_aware-bridge._udp`，能显示平板设备名。
2. Android 10:31:27 记录 Pairing Confirm / Security Association；后续连接执行 Pair Verify。
3. iOS 10:31:36.959 记录 Pair Verify authenticated，使用 datapathTransientKey，随后调用 `APPLE80211_IOC_NAN_DP_REQ`。
4. iOS 记录 NDP `early termination (User Requested)`，最终向 App 报 `-11987`。这里的 User Requested 是系统日志中的原因标签，不能据此断言用户手动点击了断开。
5. Android 10:31:37.459 记录 `Response code 1`、`Drv reason code 9010`、`Reason code 2`、`num_channels = 0`、`NDP App Info not present`；框架收到 `dataPathSetupSuccess=false`，对端 NDI 为全零。
6. Android 框架记录 `network request not found for ndpId=1`。该时段未看到正常的 Data Path Request → 主机接受响应流程，不能把这一句直接当成应用未注册网络请求的证据；dumpsys 中应用请求实际仍存在。
7. iOS 未 ready，两端应用收发计数均为 0；失败在 NDP 协商阶段，尚未进入 UDP socket 通信。

两端时间戳来自各自系统，未做精确时钟同步，不以它们的差值计算空口时延。日志中 Pair Verify 期间曾出现 `Invalid transaction sequence (0 != 2)`，但随后又记录成功存储 PTK，不能截取前一句认定配对验证最终失败。

[AOSP 的 NAN 状态表](https://android.googlesource.com/platform/hardware/interfaces/+/4e2420a/wifi/1.0/types.hal) 将 9010 定义为 `NDP_MGMT_FRAME_RESPONSE_FAILED`，属于管理帧协商失败。当前厂商日志的最终意义仍需结合该 ROM 驱动/固件确认，9010 本身不足以判断是密钥、字段、信道还是收发问题。

`NDP App Info not present` 是该失败确认中的字段状态；不能据此推出 App 没配置端口。Android 实际注册了加密 responder 网络请求、UDP 端口及 transportProtocol 17。

## 后续成功的对照

未更改服务名、配对配置、口令或 UDP 格式的情况下，再次连接后：

- iOS 日志 10:40:19 开始服务订阅，10:40:20 Pair Verify authenticated 并启动数据链路。
- iOS 10:41 截图发送/接收为 18/18，10:43 为 72/71；最近接收 0 秒，内容为 `Android heartbeat`。
- Android 对应截图从发送/接收 33/32 增长到 71/70，内容包含 `iOS heartbeat` 及 `iOS received heartbeat`。
- 对端为绑定 Aware 接口的 IPv6 link-local 地址；原始 JSON UDP、每 5 秒心跳及 ACK 实际双向通过，连续观察超过两分钟。

这排除了“这两份 App 始终无法建立 NDP / 始终不能交换 JSON”的判断，但没有证明第一次失败的底层原因已经消除。

## 应用改动

### iOS

原先 `NetworkConnection` 进入 `.waiting(.wifiAware(-11987))` 后，仅更新状态文字，send/receive 仍可能挂起，用户必须手动断开重连。

现在把该状态送入当前连接任务的错误流，取消并释放当前 send/receive，再重新浏览同一配对设备并建立一个新连接：

- 包含首次在内最多尝试 3 次，失败间隔 2 秒。
- 只对 -11987 且本次 NDP 尚未 ready 的情况重试。
- ready 后不自动更换 UDP 的对端/session，以免与 Android 已锁定的会话身份冲突。
- 用户点击断开或切后台会取消整个流程，包括重试间隔。
- 每次连接有独立标识，旧连接的迟到回调不会覆盖新尝试状态。
- 连续失败后退出 active 状态，提示在 Android 重新发布后再连接，避免无限等待。
- 记录 NDP waiting、重试次数和 ready 日志，不记录配对密钥。

### Android

实测 iOS 停止后，系统 `Active NDPs{}`、`NdpInfos[]` 已空，但应用未得到预期的 `NetworkCallback.onLost`，仍向旧端口发送心跳并保留旧 peer/session。

新增应用层兜底：已收到过对端数据后，若超过 20 秒没有合法数据/ACK，下一次 5 秒定时检查时关闭 socket、timer、网络请求及发布会话，提示重新发布并连接。实际检查延迟约为 20～25 秒。未收到过首个数据时不错误判定一个尚在等待首次连接的发布已超时。

这些是恢复与清理措施，不修改 ROM，不宣称已经修复驱动第一次 NDP 失败的根因。

## 验证边界

- 变更通过 Android 构建/lint、Swift JSON 检查、iOS 构建及签名安装。
- iOS 重试版本重新安装后，经缓存配对再次 ready；连续两分钟以上双向通信，10:47 截图为 7/7，10:50 为 59/59；Android 同期为 60/59。
- iOS 点击断开后，平板活动 NDP 和 NdpInfos 为空。
- 自动重试的 -11987 分支未在该次成功重连中再次触发，不把成功连接归因于“自动重试已修复固件”。

- 最终两端恢复版本再次连续通信约两分钟，iOS 47/47、Android 48/48。10:59:06 iOS 停止后，Android 实际进入心跳超时释放状态；dumpsys 的 mClients、Active NDPs、mUsedNdis、mNetworkRequestsCache 均为空，确认超时兜底不仅更新 UI，也释放系统资源。

全部问题及历史处理见 [问题台账](problem-log.md)。
