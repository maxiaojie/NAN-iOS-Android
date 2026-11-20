# 家庭环境吞吐复测（2026-11-20）

## 条件和范围

用户将设备带回家后要求复测，并增加两个 Android 的对照。沿用提交 `8b560d1` 的 App，未修改或部署新的 App、ROM、HAL、固件。家中环境未做射频扫描或屏蔽，不能称为无干扰环境；设备距离和姿态未精确测量。所有有效结果采用接收端汇总，4 个并行流、目标 10 秒。

- a0807d7：联想 TB323FU / Y700，Android 17。当前 amss20 实验补丁哈希 `23f5ed17af4d6ff8ddb5818e87a9cc2c5328a820beb976c27cf4a89bb15ea038`，不是本轮新刷。
- iPhone 12：iPhone13,2，iOS 27.2.1。使用已有配对，iOS 发布、Android 订阅，心跳间隔仍为每端 5 秒。
- 303fdedc：联想 TB323FU / Y700，Android 17；当前固件哈希 `8d76f63e34584be27fc84e0ff526cb638380b5337395f51e97906a057c8b24d3`。两台平板固件不同，不能称为同固件 A/B。

开始时 a0807d7 Wi-Fi 开启但 STA 未连接；iPhone 截图中 Wi-Fi 已连接，未核对其 AP 信道和带宽。Android 双机阶段，303fdedc 原连接家中 FTTR-5G；临时限制自动选网并断开 STA，确认两台 Android 均无 STA。临时选网限制需要在收尾撤销。

## iOS 发布 / Android 订阅：有效结果

| 协议 | 方向 | 轮次/总目标 | 接收吞吐 Mbps | MB | 秒 | UDP 丢包/抖动 |
| --- | --- | --- | ---: | ---: | ---: | --- |
| TCP | Android → iOS | 1 | 361.36 | 451.94 | 10.0 | — |
| TCP | Android → iOS | 2 | 311.04 | 388.89 | 10.0 | — |
| TCP | Android → iOS | 3 | 312.32 | 390.59 | 10.0 | — |
| TCP | iOS → Android | 1 | 334.40 | 418.91 | 10.0 | — |
| TCP | iOS → Android | 2 | 364.82 | 457.57 | 10.0 | — |
| TCP | iOS → Android | 3 | 380.29 | 476.32 | 10.0 | — |
| UDP | Android → iOS | 总目标 100 Mbps | 100.02 | 125.05 | 10.0 | 0.00% / 0.306 ms |
| UDP | iOS → Android | 总目标 100 Mbps | 99.95 | 125.00 | 10.0 | 0.00% / 0.514 ms |

TCP 三轮平均：Android→iOS **328.24 Mbps**，iOS→Android **359.84 Mbps**。0.00% 是 UI 输出精度，不能替代原始计数证明绝对零丢包。

办公室同角色样本为 TCP 98.12 / 69.36 Mbps，但当时 a0807d7 连接 STA，本轮未连接 STA，同时环境也变化，所以不是只改变干扰强度的受控 A/B。提升不能全部归因于家庭更干净，也不能只凭不到 400 Mbps 判定为射频上限。

### UDP 300 Mbps 未完成

20:35:53 Android 发出 `protocol=UDP, streams=4, reverse=true, duration=10, rate=300`，iOS 返回测试中。超过预期结束时间没有收到汇总，Android TCP 控制连接仍 ESTAB，双向心跳正常；不能作为有效 300 Mbps 结果，也不能称该轮通过。随后结束 App 会话以开展双 Android 对照；iOS→Android 的 300 Mbps 未执行。本轮未定位具体卡在 iperf 结束协议还是本机转发队列，不将其归因于 Firmware。

## Android ↔ Android：原生 NDP 基线

当前 App 的测速协议固定为 iOS client / Android server，心跳接收也校验 iOS 平台。因此不能直接按两台 App 的测速按钮进行 Android 双机测试。

本轮使用设备自带 `cmd wifi aware-attach/publish/subscribe/request-network` 建立独立、预共享密码加密的 NDP；不删除已有 App 配对。随后用当前仓库同一 iperf3 3.22 静态库和 `ab_iperf.c` 包装编译一次性 arm64 测试程序。a0807d7 是 server，303fdedc 是 client；`reverse` 切换发送方向。每个 socket 均以实际 Network handle 绑定 Aware Network，监听 scoped `aware_data0` 地址；没有走路由器或 USB 转发。普通 shell socket 首次未绑定 Network 时返回 Network is unreachable，随后已修正绑定后重新测试。

双端 HAL 回调报告 `channelFreq=5745, channelBandwidth=2, numSpatialStreams=2`，即信道 149、80 MHz、2 空间流。这是协商信息，不等于每个数据包都能使用最高 PHY 速率。

| 方向 | 协议/线程 | 接收吞吐 Mbps | 接收秒数 | 状态 |
| --- | --- | ---: | ---: | --- |
| 303fdedc → a0807d7 | TCP / 4 | 560.46 | 10.12534 | 完整客户端及服务端 JSON |
| a0807d7 → 303fdedc | TCP / 4 | 621.74 | 10.001116 | 完整客户端及服务端 JSON |

第三轮准备时 a0807d7 USB 消失，客户端 JSON 为空，该轮无效；剩余重复轮次及 Android 双机 UDP 尚未完成。已询问用户重新接入设备，不能报告完整测试矩阵通过。

这是绕过 iOS loopback/Network framework 适配层的原生基线，但还同时改变了接收设备、协议栈、固件和安全配置：双 Android 使用 NCS-SK PSK，App 与 iOS 使用配对凭据。它能说明本组平板当前原生路径取得了更高吞吐，不能单凭这两轮量化 iOS 转发成本或证明 iOS 是唯一瓶颈。

## 原始证据

Mac：`/Users/mxj/workspace/nan-iperf-validation/2026-11-20-home/`：过滤后的 App 测速日志、双端截图、双 Android 完整 iperf JSON、一次性测试程序源文件/二进制及运行脚本。大文件和本地路径产物不上传仓库，不提交配对安全材料。App PID 13572 在 iOS 有效轮次保持不变，心跳持续。

iPhone USB 操作工具原先位于临时目录，重启后被清理。本轮重新安装 pymobiledevice3 11.55.1，按用户要求迁至 `/Users/mxj/nan-ios-tools`，修正入口脚本路径并通过实际截图/触控验证；依赖版本清单留在用户目录。工具不再放 /tmp 或 .local。
