# libiperf3 双端性能测试

2026-11-19，替代初版单线程自定义 UDP 测试。直接集成 ESnet iperf3 3.22 的 C 库；不执行 iperf3 命令行、不依赖手机外部二进制。

## 界面与使用

Android 参照 iOS 分区：**配对和连接、数据链路、性能测试、配对缓存管理**，同页独立白色卡片；取消临时的连接/性能分页。心跳、对端和收发统计只放在数据链路区域。配对缓存管理在停止会话后可查看、逐条删除和清空。

先建立 Aware 链路并等待双向心跳和服务就绪，再选择 TCP/UDP、线程数、时长、方向。默认 **4 个并行流/工作线程**，Android 可选 1/2/4/8/16，iOS 可设 1～16。测试时长默认 **10 秒**，双端可输入 **1～3600 秒**，逐次测试两个方向。时长为 iperf 数据传输阶段的目标时长，连接建立和结果交换还会额外耗时。

TCP 不限发送速率。UDP 总发送速率可配置，默认 **0 表示不限速**；非零 Mbps 总额由并行流均分。固定 50 Mbps 是初版实现的测试负载，已取消，不是链路带宽上限。不限制 UDP 发包速率可能产生较高丢包，结果应同时看接收吞吐、丢包与抖动；不是 PHY 速率。

## 集成与链路绑定

Android 通过 JNI 加载 `libawareiperf.so`，其静态链接 libiperf；iOS 静态链接同一 libiperf，由 Swift 调用 C 包装。Android 始终作为 iperf server，iOS 是 client，两个方向使用 iperf reverse 切换，和发现角色无关。UI 两端都能发起。

原有 UDP 心跳负责控制和报告，不承载 iperf 数据。Android 在心跳中通告当前 Aware IPv6 和 TCP 控制端口 5201；Android 发起时发送 `iperf_request`，iOS 完成后发送 `iperf_result`。iperf 自己建立 TCP 控制及 TCP/UDP 数据 socket，复用已有 NDP，无需再发布 TCP 服务。

Android 每个原生 socket 以 `android_setsocknetwork` 绑定 Aware Network，监听 scoped link-local IPv6。iOS 直接用 BSD socket 连接 scoped Aware IPv6 在真机返回 No route to host；[Apple 官方确认](https://developer.apple.com/forums/thread/838513)需要使用 Network framework。现增加本地 loopback 适配层，libiperf 的 socket 仅连本机 TCP/UDP 监听，适配层使用 Network framework 的 Aware 端点承载空口流量。新增端点转换接口需要 iOS 26.4+。已有连接的 remoteEndpoint 不能转换时，通过公开的 WASubscriberBrowser/NWBrowser 发现结果取得可指定 5201 端口的端点；真机 TCP 4 流已成功。结果包含本机转发和 Network framework 的开销，不能当作裸 socket 基准。

工作线程运行 libiperf，UI 不逐包更新。每进程一次测试，停止连接时取消测试；服务完成一轮后重新监听。接收端汇总 Mbps/MB/耗时，UDP 追加丢包与抖动。

## 构建与来源

上游源码位于 `third_party/iperf3`，来源、校验及本地补丁见该目录 `README.awarebridge.md`。保留完整 LICENSE，两端安装包也包含许可文本。

Android：JDK21、SDK37、NDK28.0.12433566、CMake3.22.1；当前构建 arm64-v8a。Gradle 自动运行 `tools/build-libiperf.sh android` 再链接 JNI。
iOS：Xcode、iphoneos SDK、arm64 真机；构建阶段自动运行 `tools/build-libiperf.sh ios`。当前原生库不提供模拟器 slice，不能用模拟器目标构建来验收。

生成文件仅保存在忽略的 `.native-build/`、`.cxx/` 及常规 build 目录，不提交产物/本机签名资料。

## 验证状态

Android assembleDebug/lintDebug、iOS iphoneos 手动签名构建通过；新版覆盖安装 a0807d7 与 iPhone12。Android 四分区标题、默认 4、TCP 隐藏 UDP 速率栏已从真机层级核对。曾在更换界面前建立双向心跳，原生 server 在 aware_data0 scoped IPv6 的 5201 监听。

2026-11-19，使用以下设备；Android 发布、iOS 订阅，STA 临时断开以避开已知 TB323FU Publish 问题：

| 平台 | 测试型号 | 设备标识 | 系统版本 |
| --- | --- | --- | --- |
| Android | 联想拯救者 Y700 平板（TB323FU） | a0807d7 | Android 17 / API 37 |
| iOS | Apple iPhone 12（iPhone13,2） | iPhone 12 | iOS 27.2.1 |

下列结果均来自这组设备，不代表其他平板或 iPhone 型号。

**测试环境：办公室存在无线干扰的实际使用环境，未在干净、受控的无线环境下测试。** 下表仅记录该环境下的功能验证和吞吐样本，不代表设备或 Wi-Fi Aware 链路的性能上限；不能仅凭这些结果将吞吐或丢包归因于无线干扰。用户后续会选择更干净的无线环境复测，届时另行记录条件和结果，保留本轮记录供对照。

| 方向 | 协议/线程 | 接收汇总 | 时间 | 其他 |
| --- | --- | --- | --- | --- |
| Android → iOS | TCP / 4 | 64.69 Mbps / 80.87 MB | 10.0s | 两端结果一致 |
| iOS → Android | TCP / 4 | 50.20 Mbps / 63.18 MB | 10.1s | 1 控制 + 4 数据 TCP 连接 |
| iOS → Android | UDP / 4，不限速 | 54.39 Mbps / 68.44 MB | 10.1s | 丢包 93.57%，抖动 0.452ms |
| Android → iOS | UDP / 4，不限速 | 79.24 Mbps / 99.10 MB | 10.0s | 丢包 0.00%，抖动 0.620ms |
| Android → iOS | TCP / 2 | 80.51 Mbps / 100.66 MB | 10.0s | Android 线程设置生效 |
| iOS → Android | UDP / 2，总速率 100 Mbps | 70.32 Mbps / 88.54 MB | 10.1s | Android 发起，丢包 26.54%，抖动 0.300ms |
| Android → iOS | TCP / 4，中断后重连重测 | 58.27 Mbps / 72.88 MB | 10.0s | 未重启 App，前一轮 5 个活动 TCP 连接在断开后全部释放 |

UDP 不限速下发送超过接收能力，丢包包括本机适配层/套接字缓冲及链路，不能直接认定为射频丢包。上述各轮心跳持续。真实测试角色为 Android 发布 / iOS 订阅；反向发现角色下的新增测试连接尚未验收。旧测试类和对应协议测试已移除；其历史验证只属于旧提交 de0a1b4，不代表 libiperf 已验收。

### iOS 发布 / Android 订阅补测（2026-11-20）

此前反向角色未验收；本轮修复 Android 主线程发送测速控制消息、iOS 对订阅端重复浏览、libiperf 服务端结束时重复关闭 socket 三个问题，详见 [完整定位记录](iperf-ios-publisher-fix.md)。Android/iOS 新版均覆盖安装。设备为 a0807d7 / TB323FU / Android 17 与 iPhone 12 / iPhone13,2 / iOS 27.2.1；Android 保持 STA 连接，当前实验 Wi-Fi 固件和 HAL 未变。沿用配对，仅重新发现和建立 NDP。

仍是办公室存在无线干扰的实际环境，非干净环境性能上限。以下各轮依次复用同一 NDP，不重启 App，4 线程、目标 10 秒；UDP 总发送目标 100 Mbps。

| 发起界面 | 数据方向 | 协议 | 接收汇总 | 接收时间 | 丢包 / 抖动 |
| --- | --- | --- | --- | --- | --- |
| Android | Android → iOS | TCP | 98.12 Mbps / 122.68 MB | 10.0 s | — |
| Android | iOS → Android | TCP | 69.36 Mbps / 87.82 MB | 10.1 s | — |
| Android | Android → iOS | UDP | 94.30 Mbps / 117.93 MB | 10.0 s | 0.01% / 0.179 ms |
| Android | iOS → Android | UDP | 70.61 Mbps / 88.78 MB | 10.1 s | 24.62% / 0.554 ms |
| iOS | iOS → Android | TCP | 65.05 Mbps / 81.79 MB | 10.1 s | — |

五轮均返回双端 UI 结果、Android server 完成后继续监听，Android 进程 PID 1390 保持不变，双向心跳持续。iOS 日志确认采用已有连接的 scoped Aware 端点、Network framework 的 `nan0` channel-flow。UDP 丢包不能单独归因于射频；该值包含本机转发和链路处理开销，不用发送目标速率代替实测接收吞吐。未进行长稳、全部线程数/时长组合和其他机型验收；原生 CLIENT_TERMINATE 失败分支本轮未单独注入验证。

## 心跳频率核对

2026-11-19 16:47～16:48，a0807d7 aware_data0 抓包：Android heartbeat 在 16:47:50.091、55.096、16:48:00.104、05.107；iOS heartbeat 在 16:47:51.273、56.341、16:48:01.432、06.659。每端约 5 秒，两端相位错开约 1 秒，每条后均紧跟 ACK。UI 每秒更新最近接收时间，不代表每秒发送心跳。iOS 将心跳说明移至数据链路区域，底部仅提示保持前台。


### 可调时长补测（2026-11-19）

iOS 新版已签名并覆盖安装 iPhone 12，Android 同版另已安装 303fdedc（仅安装检查）。测速仍采用上表的 a0807d7 / TB323FU 与 iPhone 12，办公室干扰环境、Android STA 断开。Android UI 输入 15 秒，TCP 4 线程 Android→iOS，iOS 最终接收汇总 **63.45 Mbps / 119.01 MB / 15.0 秒**，证明配置的时长实际传入 libiperf3。此补测不覆盖 iOS 本地输入发起、UDP 非 10 秒和一小时长稳。
