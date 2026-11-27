# SmartConnect 经 NAN 上线可靠性与吞吐测试：HA2E6NPB ↔ iPhone 12

测试日期：2026-11-27，北京时间。正式测试 16:22:48—16:34 左右；准备、首次配对与测速预检不计入 10 次。

## 结果

- 在已有配对设备页面，Android/iPhone 交替各发起 5 次连接，10 次均完成 NAN 缓存配对验证、NDP、WSS 认证并在双端显示在线，10/10。各轮测速结束时 SC 的 aware_data0 TCP 仍为 ESTAB。
- TCP 4 并行流、每方向目标 10 秒。Android→iPhone 10/10 完成，接收吞吐平均 **22.67 Mbps**，范围 **18.67—28.42 Mbps**。
- iPhone→Android 仅第 6 次完成，接收吞吐 **4.40 Mbps**；其余 **9/10 返回 `idle timeout for receiving data`**。超时项不记为有效 0 Mbps，不把成功一轮的速率当作反向平均值。
- 所以，本批短时重连的“上线”通过，**上线后双向吞吐测试未通过**。不据这 10 次推断长期、冷启动、后台、删除配对或完整业务可靠性。

[逐次 CSV](results/sc-nan-ha2e6npb-iphone12-2026-11-27.csv)。完整产品日志含认证资料，只留本机，不上传。

## 设备、软件与无线条件

| 项目 | 配置 |
| --- | --- |
| Android | HA2E6NPB，TB323FU_US / Lenovo Legion Tab (8.8",5)，Android 17 / SDK37 |
| ROM | TB323FU_ROW_OPEN_UD_Q00037.0_A17_ZUXOS_18.5.10.267_ST_261118，userdebug |
| Wi-Fi | 驱动 5.2.1.147N，固件 197458.2，已部署对应日志字典；本轮不刷 HAL / 固件 |
| Android SC | com.motorola.mobiledesktop，10.0.0.003.0 / 10000030，本轮覆盖安装最新本地 APK，保留数据 |
| iPhone | iPhone 12 / iPhone13,2，iOS 27.2.1，USB 连接 |
| iOS SC | SmartConnect 产品主 App 本地验证签名，bundle com.slab.wifiawaredemo；不是 AwareBridge 示例 App |
| 产品代码基线 | Android 1f030fcc641ed1eb869252ea36fcc53c43081daa；iOS 092a1661e2e8e6842a7997ff2d257421467b1f1a，均叠加既有本地 NAN 集成补丁 |
| STA | Android 全程连接 Pixel_2564，WPA3-SAE / 11ax，5805 MHz / 信道161；抽查 RSSI约 -53 dBm；未断开 STA 绕过测试 |
| NDP | dumpsys wifiaware 抽查 channelInfo：2437 MHz/BW0/NSS2 与 5745 MHz/BW2/NSS2；这不是 STA 链路速率 |
| 测速 | libiperf 3.22，TCP，4 并行流，每方向目标 10 s，先 Android→iPhone，再 iPhone→Android；顺序执行，不做双向同时压测，不测 UDP |
| 环境 | 现有现场无线环境，未控制其他无线干扰；两端接 USB，保持前台。并行采集双端日志，SC 自身业务重试未禁用 |

Android / iOS 的产品源码分别来自 `/Users/mxj/workspace/master_android/readyfor` 和 `/Users/mxj/workspace/SmartConnectIoS`。没有使用另一个 readyfor 目录，没有推送公司 Gerrit。本轮未修改上述产品正常源码。

## 操作与判定

1. HA2E6NPB 与当前 iPhone 原先没有 SC 对应关系，先完成一次正常首次关联与系统 PIN 配对。此阶段需要一次二维码连接信息作为邀请，不计入正式重连。
2. 正式轮次均从已有设备详情页操作，先在 Android 点击“断开连接”，等待旧 SC NAN TCP 消失，再交替从 Android 或 iPhone 点“连接”。不扫描二维码、不删除配对、不手动选择新的 NAN 设备、不在失败后补一轮覆盖原结果。
3. 每轮连接观察上限 75 秒。新日志必须有缓存配对验证成功、WSS `handleWsVerified status:0`；同时存在 `aware_data0` 的 SC TCP，Android 有“断开连接”，iOS 有“断开连接”或“已连接”。核对 iOS `result:1,connType:10`，避免把普通 STA/Wi-Fi 连接算成 NAN。
4. 10 轮均自动匹配已认证 alias，并完成缓存验证。**发起操作的两端交替，不等于 NAN 发布/订阅角色交替**：本批 10 轮实际均为 iOS 发布、Android 订阅（Android session `publisher=false`）。
5. 每次上线后，从本轮 SC TCP 获取对应 iPhone IPv6，绑定 `aware_data0` 执行两方向测速。端点地址逐轮重新获取，避免复用失效地址。测完检查 SC TCP 仍在。

下表“观察到上线耗时”含自动化工具、约 2 秒轮询与 UI dump 等开销，是从发起操作到工具确认在线的**上界**，不是精确协议握手时延。第 1 轮起点还包含一次控件定位，因此更不能作为底层 NAN 建链时延。未用这些数字计算精确协议平均延迟。

## 10 次原始结果

速率单位均为 Mbps，使用接收端汇总；“接收超时”表示没有有效完成的汇总。

| 次数 | 发起端 | 发起时间 | 双端在线 | 观察耗时上界(s) | iOS serviceId | Android→iPhone | iPhone→Android | 测速后 SC TCP |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Android | 16:22:48 | 通过 | 15.93 | 19 | 25.53 | 接收超时 | 保持 |
| 2 | iPhone | 16:24:11 | 通过 | 15.85 | 8 | 22.99 | 接收超时 | 保持 |
| 3 | Android | 16:26:15 | 通过 | 15.33 | 19 | 18.91 | 接收超时 | 保持 |
| 4 | iPhone | 16:27:12 | 通过 | 11.44 | 8 | 23.95 | 接收超时 | 保持 |
| 5 | Android | 16:28:13 | 通过 | 8.73 | 19 | 28.42 | 接收超时 | 保持 |
| 6 | iPhone | 16:29:01 | 通过 | 11.36 | 8 | 22.01 | 4.40 | 保持 |
| 7 | Android | 16:30:31 | 通过 | 10.86 | 19 | 21.79 | 接收超时 | 保持 |
| 8 | iPhone | 16:31:20 | 通过 | 11.26 | 8 | 20.16 | 接收超时 | 保持 |
| 9 | Android | 16:32:36 | 通过 | 9.03 | 19 | 18.67 | 接收超时 | 保持 |
| 10 | iPhone | 16:33:22 | 通过 | 13.76 | 8 | 24.32 | 接收超时 | 保持 |

## 为什么不能只按 serviceId=8 判断上线

本轮一度将 Android 发起时的 `serviceId=19` 当作临时配对服务，这是不准确的，已在继续统计前复核源码并更正。

当前 iOS 产品的 `SmartConnect/SCService/SCConnectService/SCGOConstants.h` 定义 `SERVICE_ID_CONNECT_SERVER=8`、`SERVICE_ID_CONNECT_CLIENT=19`；`SCConnectManager+Connect.m` 的 `triggerConnectToDevice` 根据连接角色选择它们。19 本身不能证明连接只停留在临时配对阶段。

本批奇数轮为19，偶数轮为8，均有 result1/connType10、认证后的真实 NAN TCP、双端在线和后续流量。这里依据组合证据判定上线。此前 2026-11-25 某轮 service19 同时出现 iOS 失败弹窗，仍不能作为完整通过；不能仅用本次更正抹去当时的实际失败现象。

## 性能测试如何接入、测到什么

SC 产品没有现成的 iperf 测试入口，本轮只在生成的 iOS 本地验证工程中增加测速通道，产品正常源码未改：

- 对既有 SC `NetworkConnection<TCP>` 读取 Wi-Fi Aware path 与配对设备，新增 `.addingConnections(from:.selected([device]))` 的 TCP 5201 listener，随 SC session 回收。收发仍走 Apple Network framework，测速子连接设置 `.bulk`；原 SC 控制连接配置不改。
- iOS libiperf 只监听 loopback `::1:5202`，由 Network framework 转发；不是使用 BSD socket 直接访问 NAN。Android 临时 CLI 将 libiperf socket 绑定 `aware_data0`，连接该 SC 对端的 5201，P=4/T=10，reverse=0/1 切换数据方向。
- Android 正常 SC App 未切换到 root 进程运行；仅临时 shell 测速命令需要 root 完成设备绑定。本轮没有通过 AwareBridge 另建一条 NDP 测速。
- 本结果是**经过 Network framework 与 loopback 转发的 SC 现有 NDP 上的 TCP 接收吞吐**，不是 SC WSS 的业务吞吐，也不是无线 PHY 速率、纯固件上限、文件传输或 NFS 成功率。
- 代理层可能有额外缓冲与 CPU 开销。以第 6 轮反向为例，发送端汇总23.97 Mbps，接收端仅4.40 Mbps，不能拿 loopback 发送端值冒充实际到达对端的速率。
- 目前共用 native wrapper 的 `rcv_timeout.secs=5`、`snd_timeout=5000000`，因此目标10秒不保证失败测试实际运行10秒：接收停顿达到5秒会提前失败。这是本批超时判定条件，不等于已证明无线链路断开。

本机验证源码、CLI、自动化、每轮 JSON 和完整日志位于：
`/Users/mxj/workspace/nan-capture-analysis/2026-11-27/sc10/`。
其中 `run-trials.py`、`record-results.py`、`SCNanTransportValidation.swift`、`iperf-cli.c` 保存了实际实现。生成工程的正常源码引用恢复后，测试副本仍在这个目录，可复查。本仓库仅上传无凭据的结果 CSV 和文档。

## 已发现问题、失败过程与限制

### 反向 TCP 接收经常超时：未解决

正式10次中9次 `idle timeout for receiving data`，只有第6次返回完整10秒接收汇总；预检也有两次相同反向超时，不计入正式10次。超时后同一SC TCP仍为 ESTAB，没有观察到本批设备 CrashDump。

确认的是“该测试通道的反向数据没有在接收等待条件内完成”，**尚未定位是 Network framework 转发调度、STA/NAN并发调度、缓冲/超时配置还是更下层问题**。本批不能据此归责固件。下次应先保存完整 iOS native server 汇总（OSLog 输出整份 JSON 会被截断），采集数据 socket/线程状态，并将测试等待超时与10秒采样窗口分离，再做原参数对照；保持原始9次失败，不用补测覆盖。

### 上线不代表 NFS 业务可用

正式各轮窗口合计观察到173条 `NfsContext mount returned null` 和73条 `Failed to start nfs server`。还看到 `failed to build TYPE_CLIENT clientIps`、携带对端 `%nan0` 作用域的地址在 Android 侧未正确转换等日志；不能仅据这些片段锁定唯一根因。

这是已存在的其他业务 socket 适配问题，NFS 未通过验收。业务持续重试及日志采集也可能影响本批速率，影响幅度没有量化。本轮未传文件，不把UI在线扩展为完整SC功能正常。

### 准备阶段的问题

- 第一次系统PIN输入因操作延迟超过窗口，另一次因键盘弹出后按钮坐标变化取消；改为读取当前可访问控件、立即输入当前PIN并定位当前按钮后完成。PIN不进入报告或公开仓库。此后正式10轮均用已有缓存，无PIN输入。
- Android 原屏幕超时短，准备时曾息屏；临时开启USB保持亮屏，测试结束恢复原值0。
- 初次编译临时Android CLI漏 `-landroid`，补齐后链接成功；未因此修改产品App。
- 正式前补齐本地验证 listener 对连接角色的接入，并增加 native结果日志。正式轮次1—10使用同一验证构建，不在中途换参数或换库。

## 收尾

已恢复生成工程对正常 `SCNanTransport.swift` 的引用，移除临时iperf C接入及链接，重新构建成功并覆盖安装到iPhone12；检查Mach-O中已无测速日志及SC_TEST_PAIRING标记。正常SC主App已重新启动。

iPhone12通过0.05秒短按锁屏，实际SpringBoard lockstate **0→1**。Android USB保持亮屏设置恢复原值0，临时日志标签清空，测速CLI移除，adbd核对为uid2000(shell)。本轮日志采集已停止，正常SC配对记录保留。

Ubuntu 本轮未使用、未切换 monitor、未修改网络。产品正常源码未包含临时测速入口；只在本地生成工程增加及撤销测试接入。
