# 发布端断链后的自动恢复（2026-11-19）

## 原行为

iOS 主动断开后，Android 超过 20 秒未收到有效心跳，HeartbeatTransport 通知 onError；AwareController 调用 fail → close，同时停止 socket、网络请求、Publish 和 attach，需要手动重新发布。这是 App 的清理策略。

## 真机验证发现的第二个阻碍

首版修改（3547e28）仅重建数据链路，未完成双端测试就收尾。本次补测证实它不够：

- 10:11 首次双向心跳 7/7；10:12:04 从 iOS 主动断开。
- 10:12:26 心跳超时，App 保留 clientId=3/sessionId=3/Publish，关闭端口 52618 的请求，改为新端口 50380。
- Framework 旧请求停留 state=104（STATE_TERMINATING），新请求因 canonical descriptor 命中旧会话而 requested=false。
- iOS 再次 Pair Verify 成功，但 Android onDataPathRequest 报 can't find a request，NDP reason=2。不能把仅保留 Publish 记为重连通过。
- 第二个对照只重建 Publish、保留 attach，clientId=4 的 sessionId 从 4 变为 5；旧 NDP 仍残留，重连仍失败。不能认为换 discovery session 就足以清除这台 ROM 的数据路径状态。

这是 Framework 运行日志定位到的阻塞状态；没有据此确定驱动/Firmware 未完成 teardown 的最终根因，也不代表历史所有 -11987 都是同一原因。

## 最终 App 方案

文件：NAN-Android/app/src/main/java/io/awarebridge/android/AwareController.java。

心跳/UDP 错误、NetworkCallback.onLost/onUnavailable 转入自动恢复：释放旧 transport/网络请求，关闭旧 Publish 和 attach，一秒后重新 attach、发布及请求数据链路。用户不再需要点击 Android 的“开始发布”。系统配对缓存、App 配对开关及原有一分钟关窗任务保留；恢复不主动打开 Setup、不删除配对。服务会有短暂重建间隙，并非保留原 Publish 对象。重建间隙也保存关闭配对的请求，避免一分钟到期恰好落在该间隙时被忽略。

网络、Publish、attach 均有 generation 标识，忽略旧对象回调，避免主动关闭旧会话后的 onSessionTerminated 再关闭新会话；手动停止取消延迟恢复。用户停止、Aware 不可用、发现会话异常及重新 attach/Publish 失败仍完整关闭并显示错误。订阅端策略未改。

日志记录恢复原因及 restarting Aware and Publish, retaining pairing state。HeartbeatTransport 超时文案改为通用链路失去响应。

## 最终双端验收

设备：a0807d7 / TB323FU Android 17（11 月 19 日已部署 CSIA 修复库），iPhone 12 / iOS 27.2.1。平板 STA 断开，Wi-Fi 开关保留；双方使用已有配对缓存，Setup 全程 false。

1. 新 APK 编译/lint、安装通过，10:18:46 Android 获得 Aware IPv6，iOS 双向 9/9。
2. 10:19 在 iOS 点击断开，Android 未做手动停止或重新开始。
3. 10:19:41.449 心跳超时自动恢复。dumpsys 中 clientId=5/sessionId=9 自动变为 clientId=6/sessionId=10，仍为 Publish。
4. 等待恢复后只在 iOS 点击连接。10:20:24.891962 Pair Verify authenticated，10:20:24.904827 DataPath Started，10:20:26.561 Android 获得新的 Aware IPv6。
5. iOS 截图 11/11，随后 Android UI 13/13、最近接收 0 秒；配对开关仍为 false。没有清配对、没有输入 PIN、没有手动重新发布。

通过范围：已有缓存、Setup=false、iOS 主动断开触发心跳超时后的自动恢复与双向重连。Setup=true 恢复、手动停止恰好落在恢复延迟期间、系统权限失败恢复未逐项实机验收；不宣称 STA 并发或底层 NDP 根因修复。

日志、dumpsys 和截图存放 Mac `/Users/mxj/workspace/nan-capture-analysis/2026-11-19/reconnect-recovery/`，不提交完整日志/密钥。本轮无需空口抓包，Ubuntu 保持普通 Wi-Fi。测试结束停止 Android 并恢复普通 Wi-Fi和 30 秒息屏，iPhone 执行锁屏，device-info 确认 backlightState=off。
