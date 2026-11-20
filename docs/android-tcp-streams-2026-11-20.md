# 双 Android TCP 2/4/8/16 线程复测（2026-11-20）

## 任务与环境

用户确认上次 Crash 收集后已重启成功，要求继续测试，仅 TCP，线程数 2/4/8/16。本轮按每个线程数两方向、目标每轮 10 秒，共 8 项；不测 UDP。设备为 a0807d7、303fdedc，两台联想 TB323FU / Android 17，家中实际环境。未测量环境干扰、设备距离或姿态，不能视为屏蔽箱性能上限。

沿用 [家庭原生对照](home-throughput-2026-11-20.md) 的方法：系统 shell 的 Aware 发布/订阅和 NCS-SK PSK 加密 NDP，a080 为 iperf server、303 为 client，reverse 切换方向。用同一仓库 libiperf3 3.22 和 ab_iperf.c 编译一次性 TCP 测试程序，新增线程数参数；不是重新安装 App。显式 android_setsocknetwork 绑定各自 Network handle，socket 使用 scoped aware_data0 地址。未修改 App、HAL、ROM 或固件，也未删除配对。

测试前临时限制两台 STA 自动选网并断开 STA，双方状态均确认未连接路由器。每轮结束间隔约 3 秒，复用同一 NDP；每轮创建新的 one-off iperf server/client。双端确认协商信道 5745 MHz（149）、80 MHz、2 空间流。线程数从客户端 JSON 的 test_start.num_streams 核对，下面速率均为 sum_received 的接收端汇总。

当前固件哈希：

- a0807d7：`23f5ed17af4d6ff8ddb5818e87a9cc2c5328a820beb976c27cf4a89bb15ea038`，实验 amss20 补丁。
- 303fdedc：`8d76f63e34584be27fc84e0ff526cb638380b5337395f51e97906a057c8b24d3`，与 a080 不同。

## 第一阶段结果

| 并行线程 | 303fdedc → a0807d7 接收 Mbps | a0807d7 → 303fdedc 接收 Mbps |
| --- | ---: | ---: |
| 2 | 595.18 | 671.43 |
| 4 | 515.77 | 666.85 |
| 8 | 604.01 | 未完成，firmware hang |
| 16 | 未执行 | 未执行 |

这五个有效样本均有双端完整 JSON。开始时间（北京时间）依次为 21:33:50、21:34:04、21:34:18、21:34:33、21:34:47；接收持续时间分别为 10.072038、10.001144、10.134606、10.000714、10.138314 秒。各配置只有一轮，不能从 4 线程低于 2 线程直接推出最佳线程数或稳定性能趋势。

仅完成 5/8，不能宣称 2/4/8/16 全部通过，也不能把这五轮速率当作长稳结果。此前原生基线高于 iOS 路径，但两组设备、固件、安全方式及转发路径不同，不足以单独定位 iOS 性能瓶颈。

## 8 线程反向时再次异常

第六项为 TCP / 8 流 / a080→303；303 客户端 JSON 报 `idle timeout for receiving data`，a080 服务端未返回 JSON，设备再次从 USB 消失。脚本遇到错误即停止，不进入 16 线程测试，没有自动重启设备。

本轮启动了双端内核及筛选 logcat 采集，a080 最后的内核证据包括：

```text
[508.442648] ... [13:35:10.327033] ... wlan_hdd_pld_uevent: Received firmware hang event
[508.443656] ... host pm_state:SYS ERROR Detect dev_state:SYS ERROR ee:RAMDUMP DOWNLOAD MODE
[508.456952] ... device ee: RAMDUMP DOWNLOAD MODE dev_state: SYS ERROR
[508.468284] ... BHI_ERRDBG2 val: 0x1325d2f
[508.478268] ... BHI_ERRDBG3 val: 0x25c
```

驱动事件内的 13:35:10 为 UTC，对应北京时间 21:35:10；方括号为开机后的内核时间。上述直接支持 WLAN 固件进入 SYS ERROR / RAMDUMP 状态，已超过上次仅凭 USB 失联和对端超时的证据。但 BHI 错误寄存器不能直接视为符号化调用栈，本轮日志未给出可确认的 assert 函数/源码行。

**仍不能确定具体固件函数、App/HAL/驱动的触发责任，或实验 amss20 补丁是否相关。** a080 运行实验补丁且两机固件不同，需结合此次高通 dump、准确 build 和符号/异常现场分析；本轮没有刷回原版作对照，不应宣称补丁无关。用户随后明确确认设备再次显示 CrashDump。暂停剩余测速，保留/收集 Crash 现场；未经恢复不再次压测。

## 收尾与原始证据

303fdedc 的临时选网限制已撤销、Aware 测试会话已退出、普通 Wi-Fi 已恢复 FTTR-5G。a080 的同类清理命令因设备消失失败，不能声称它已清理；待其按用户安排恢复后，再撤销临时选网限制、清理测试进程/会话。没有操作 Ubuntu 或 iPhone。

原始数据：`/Users/mxj/workspace/nan-iperf-validation/2026-11-20-home-tcp-streams/`。包含可调线程 TCP 测试程序源文件/二进制、Mac 驱动脚本、results.json、各轮双端 JSON/stderr、NDP 信道信息、每轮 uptime、固件校验以及双端私有内核/logcat。日志含底层字段，仅本机保存，权限 600，不提交仓库。
