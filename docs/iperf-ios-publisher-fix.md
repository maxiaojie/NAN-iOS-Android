# iOS 发布、Android 订阅时 iperf3 无法启动（2026-11-20）

## 环境与现象

- Android：联想 Y700 / TB323FU，a0807d7，Android 17 / API 37；STA 保持连接。该机当前使用已部署的实验固件及 Wi-Fi HAL，本轮未修改 ROM、HAL 或固件。
- iOS：iPhone 12 / iPhone13,2，iOS 27.2.1，通过 USB 安装和操作。
- iOS 发布 `_aware-bridge._udp`，Android 订阅；沿用已有配对，NDP 和双向 5 秒心跳正常。
- Android 测速区显示 `启动失败：null`；iOS 自身发起时显示 `iperf3 适配层失败：No additional Aware endpoint discovered`。
- 原来的性能验收只覆盖 Android 发布 / iOS 订阅，不能用其结果认定反向角色可测速。

## 三处问题与处理

### Android 控制消息在主线程发送

`MainActivity` 按钮同步调用 `HeartbeatTransport.benchmark()`，原实现直接 `DatagramSocket.send()`。正常心跳在后台线程发送，两者线程不同，因此心跳正常不能证明按钮发送正常。Android 主线程网络操作会触发运行时检查；原 catch 只显示 `getMessage()`，丢失异常类型，可能只剩 `null`。

将控制消息投递到现有心跳工作线程，在锁内检查连接和服务是否仍有效，然后发送。请求日志记录协议、方向、线程数、时长和 UDP 总速率；失败日志保留异常栈，UI 显示异常类型和可用的原因。原版本的异常栈没有记录，不能把具体异常类型当成已抓到的旧日志；本轮真机确认修改后按钮请求送达，iOS 返回了开始和结束结果。

### iOS 错把订阅端当成发布端查找

`IperfRelay.open()` 原先先转换已有连接的 `remoteEndpoint.wifiAware(port: 5201)`，不成功便使用 `WASubscriberBrowser` 搜索 Android 的服务。这只适用于 Android 发布角色；Android 仅订阅时没有该 Publish，10 秒等待后失败，即使已存在正常 NDP。

本轮确认 iOS 接受的 UDP 连接给出带 `nan0` scope 的对端 IPv6 端点；对其直接调用 `wifiAware(port:)` 返回 nil。由 `WAPath.endpoint` 临时构造未启动连接再读取端点的实验同样返回 nil，已移除该尝试。

最终处理：确认现有连接具有 Wi-Fi Aware path、且属于发布服务路径后，使用公开的 `NWEndpoint(copying:newPort:)` 将已接受连接的远端端口改为 5201，保留 scoped IPv6/interface 信息。适配层以该端点创建 Network framework 的 TCP/UDP 连接，继续承载 libiperf 的控制连接和数据流；libiperf 自身仅连本机 loopback。仍保留原来另一发现角色的 WAEndpoint/浏览路径。

这里没有改成 iOS BSD socket 直连，没有新增 Android Publish，也没有重新配对。Android 始终是 iperf server，iOS 始终是 client，`reverse` 只切换数据发送方向。真机 iOS Network 日志确认新连接 `ready channel-flow`、interface 为 `nan0`；Android 的监听地址限定在 `aware_data0`。

官方接口：[NWEndpoint 复制并修改端口](https://developer.apple.com/documentation/network/nwendpoint/init(copying:newport:))、[Wi-Fi Aware 端点转换](https://developer.apple.com/documentation/network/nwendpoint/wifiaware(port:))。两种端点的类型和适用条件不同，不能把转换失败视为 NDP 本身失败。

### libiperf 服务端完成时重复关闭 socket

端点修正后，首次 TCP 4 流实际建立并运行到结束，但 Android 原生进程在收尾时崩溃：

```text
fdsan: attempted to close file descriptor ..., expected to be unowned,
actually owned by unique_fd ...
cleanup_server -> close
```

对应本轮旧版库 PC `0x2abfc`，反汇编确认处在 `cleanup_server()` 的 stream socket 关闭循环。`iperf_handle_message_server()` 的 `TEST_END` 和 `CLIENT_TERMINATE` 分支已关闭 `sp->socket`，却未置 -1；之后 cleanup 再关一次，其他 Android 线程复用 fd 后触发 fdsan，可能误伤其他资源。这属于原生 socket 生命周期错误，不能归因于 Wi-Fi Firmware。

在这两处关闭后设置 `sp->socket = -1`，由现有 cleanup 的有效性判断避免重复关闭。没有关闭 fdsan 检查。同步保留在上游目录的本地补丁说明。

## 验证

Android assembleDebug/lintDebug、iOS iphoneos 签名构建通过；新版双端覆盖安装 a0807d7 和 iPhone 12。具体测速结果和范围见 [吞吐测试记录](throughput-test.md)。

本轮为办公室存在无线干扰的实际环境，非屏蔽箱/干净信道下的性能上限测试；STA 保持连接。结果同时包含本机 loopback 转发、Network framework 和链路调度开销。未验收其他设备、iOS 26.4 的实际运行或长时稳定性，不能据此推广到所有版本。
