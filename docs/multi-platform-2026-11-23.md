# 三种设备组合：角色、心跳与 iperf3（2026-11-23）

## 目标和实现

Android↔Android、iOS↔iOS、Android↔iOS 使用同一服务与协议。双方均可选择发布或订阅；一端发布，另一端配对并连接。UI 使用“附近设备”“本机/对端”。发现、配对 API 保持各平台公开 API，未为本次改动替换 HAL 或固件。

原实现把平台固定为对端校验条件，Android 只运行 iperf server，iOS 只运行 client，同平台收到消息会被丢弃，也不能正确测速。现改为协议 v2 的独立 `platform` / `role` 字段，只要求角色相反，允许同平台。v1 不再接收，两端须一起更新。服务名保留，已有系统配对可复用。

发布端固定运行 iperf server，订阅端运行 client；两端按钮都支持任一数据方向。发布端发送控制请求，订阅端执行并回传汇总。iOS 新增 Network framework 的 Aware TCP/UDP 监听，将数据转入 loopback libiperf server；每条控制/并行流各自处理。native 任务串行并绑定自己的取消状态，避免切换链路时旧任务取消新任务。测速服务启动失败单独显示，不因此终止正常心跳；Android 丢弃不合法的测速参数并显示错误。后两项错误恢复路径本轮仅构建检查，没有注入故障实测。

Android 发布端原本显式配置实验 PSK，订阅端原为 iOS 发布服务设计，未设置口令。首次双 Android NDP 失败后，增加发布 TXT `ndpSecurity=demo-psk`，Android 订阅端据此配置同一实验口令；iOS 发布端无该字段，保持原路径。Bootstrap 设备 TXT 仍只有设备信息。口令仅为现有实验配置，不替代 PIN 或产品密钥管理。

## 设备和环境

- Android：a0807d7 / TB323FU PRC，Android 17 API 37；HA2E6NPB / TB323FU US，Android 17 API 37。
- iOS：iPhone 12、iPhone 14 Pro，USB 真机，本机有效 Wi-Fi Aware 签名安装。
- 本次保留两台 Android 原有 STA 连接（a0807d7：lenovo-internet；HA2E6NPB：Pixel_2564）。未改变 Ubuntu 网络，未进行空口抓包。
- 均为前台、TCP 4 并行流、10 秒；周围有无线干扰，未控制信道、距离、其他业务和射频环境。结果是功能验收样本，不作为芯片峰值指标，也未测试 UDP。

## 已观察结果

| 发布端 | 订阅端 | 配对/连接/心跳 | 发布→订阅 TCP | 订阅→发布 TCP |
| --- | --- | --- | --- | --- |
| a0807d7 | HA2E6NPB | 本轮 PIN 配对成功，缓存验证成功，双向心跳持续正常 | 197.63 Mbps / 247.07 MB / 10.0 秒 | 未通过：测试期间设备异常，见下文 |
| iPhone 14 Pro | iPhone 12 | 使用已有系统配对，建立 NDP，持续双向心跳；本轮未删除 iOS 系统缓存重新做首次 PIN | 153.49 Mbps / 191.89 MB / 10.0 秒 | 126.81 Mbps / 159.12 MB / 10.0 秒 |
| HA2E6NPB | iPhone 12 | 本轮首次 PIN 配对成功，NDP、双向心跳通过 | 119.16 Mbps / 149.03 MB / 10.0 秒 | 34.52 Mbps / 43.65 MB / 10.1 秒 |
| iPhone 12 | HA2E6NPB | 复用刚才跨平台配对、缓存验证成功，NDP、双向心跳通过 | 128.85 Mbps / 161.09 MB / 10.0 秒 | 145.30 Mbps / 198.44 MB / 10.9 秒 |

双 Android 成功一轮由发布端 UI 发起，请求订阅端 client 以 reverse=true 执行，结果返回发布端与订阅端两处显示。iPhone↔iPhone 与 Android 发布的两轮跨平台测试均由 iPhone 订阅端 UI 发起。

## 发现的问题与限制

### 双 Android 第二方向期间 CrashDump

约 17:03 在 HA2E6NPB 点击本机→对端后，17:04:11 HA2E6NPB 日志出现 WifiHAL fatal event、firmware alert 4099，结果发送报 ENETUNREACH；a0807d7 随后从 ADB 列表消失。用户明确确认 a0807d7 显示 CrashDump，要求保留。助手未对它重启、刷写、清除配对或强制恢复。

不能仅凭对端日志确定 a0807d7 的崩溃栈，也不能把该轮作为有效吞吐结果。需由用户收集高通 dump 后判断根因；不能声称双 Android 两方向长稳通过。a0807d7 测试前被临时设置充电常亮，设备不可用后无法撤销，恢复后需将 `settings put global stay_on_while_plugged_in 0` 执行一次。

### iOS 同名历史记录与发布窗口

iPhone 14 Pro 有两条名称相同的 iPhone 历史记录，选择其中记录会发现超时。测试没有擅自删除系统缓存；换向由 iPhone 12 选择 iPhone 14 Pro 后连接成功。iOS 无接入时的 Publish 仍可能由系统在约 120 秒后结束，即使请求 active=3600；本轮通过重新开始发布完成验证，没有宣称已修复自动重发。

### iOS 发布端发起测速的端口声明和一次接收超时

实测发现 iOS 发布端按钮发送 iperf_request 时未附带 iperfPort，Android 将其识别为尚未就绪。已在发送请求前检查本机 server ready，并在请求附带 5201。随后测试确实进入 native client，但第一轮返回 idle timeout for receiving data；双向 UDP 心跳未断，未观察到设备 CrashDump。改在 Android 订阅端点击同一数据方向后复测成功，128.85 Mbps；再次从 iPhone 发布端 UI 发起也成功，131.06 Mbps / 163.84 MB / 10.0 秒，验证端口修复有效；该次偶发超时的根因尚未确定，不将单次重试成功称为消除此问题。

### 设备名缺失

iOS 常规发布可能不附带设备 TXT。Android 订阅列表此时显示“附近设备（未提供名称） #句柄”，不把任意 SSI 解码成设备名，也不把会话 PeerHandle 当作永久设备身份。

## 构建与证据范围

Android assembleDebug、lintDebug，设备 TXT 解析测试、Swift 协议测试及 iOS iphoneos 签名构建均通过。协议测试覆盖两平台、两角色和心跳/ACK/测速消息、错误角色/版本/会话拒绝。

截图与完整调试日志保留在 Mac 的 `/Users/mxj/nan-test-results/2026-11-23-multi-platform/`，没有把可能含配对材料的系统日志提交到 Git。文档只收录状态、汇总和必要的无密钥错误。当前 UDP 与 2/8/16 流矩阵、本轮 iOS↔iOS 全新删配对验证未执行。

收尾：HA2E6NPB 已停止 App 并撤销充电常亮，STA 保持 Pixel_2564；a0807d7 按用户要求保留 CrashDump。两台 iPhone 已安装最终版。
