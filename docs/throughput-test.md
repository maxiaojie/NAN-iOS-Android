# 双端 UDP 吞吐测试

2026-11-19。Android、iOS 的发布/订阅角色均可使用，沿当前已建立的 Aware UDP socket 传输，不依赖 STA 路由，不另建数据链路。

## 使用

两端均需升级本次版本。先配对、连接，等待双向心跳，然后在发送端点击“测试本端 → 对端”。Android点击测试Android→iOS，iOS点击测试iOS→Android，逐个方向运行，不同时点击。发送10秒、目标50Mbps、1200字节UDP payload；接收端显示吞吐、接收MB、耗时、丢包率，并向发送端回报。每次接收测试保留约14秒窗口供结束报文重试，再接受下一轮；Android清理由5秒心跳定时器驱动，可能略晚。

这是固定负载UDP测试，不是TCP测试、无上限压测或Wi-Fi PHY速率测试。目标50Mbps不保证发送调度或实际链路达到50Mbps。MB=十进制1,000,000字节，Mbps=1,000,000bit/s；字节统计包含32字节测试头，不含UDP/IP/MAC开销。吞吐=接收端唯一包数×1200×8÷发送端单调时钟测得的发送窗口，避免两机墙钟不同造成偏差。接收端以首个有效END到达时的数据集统计，迟到数据可能被计为丢包；不是精确单向时延或射频丢包测量。

心跳照常，每5秒一次，吞吐包不计入心跳发送/接收计数，也不掩盖心跳超时。断开或退后台随传输对象关闭而取消测试。测试尚未收到双端心跳时拒绝启动；同时启动可能互相拒绝并最终报告无结果，需顺序重试。旧App只会忽略二进制包，新App会在缺少报告时明确超时，不凭本机send成功显示通过。

## 协议及处理

每个包使用big-endian：magic四字节ABT1，type四字节（1 START、2 DATA、3 END、4 REPORT），run ID八字节、count/sequence八字节、duration ns八字节。DATA共1200字节，其余包32字节。run ID为正Int64兼容范围；DATA序号从0起，END给总发送包数及发送窗口，REPORT给有效唯一接收包数及同一窗口。

START/END各重发三次，重复START不清零，重复END可再次回报。接收端仅接受当前run，序号计数上限1,000,000；BitSet/Set去重，END统计序号小于发送总数的包。结束无报告约3秒提示超时，接收无END在14秒后提示超时。两端沿既有心跳锁定的UDP对端接受测试包，未建立心跳会话时不接受。

Android使用后台executor发送，16包一批，目标间隔3.072ms；Swift使用actor和异步send/单调时钟节流。发生调度落后时不追赶突发。UI只接收状态和结果，不逐包刷新。结果不包含配对安全字段。

## 本轮验证

- Android JDK21 assembleDebug、lintDebug通过。
- iOS iphoneos无签名构建和本机现有profile手动签名构建均通过。签名配置/文件不入Git。
- `tools/throughput-tests/run.sh`（JDK21及swiftc）：重复DATA去重、重复START保留统计、其他run隔离、重复END回报、Java与Swift的1200字节DATA及32字节REPORT逐字节互通通过。Java发送循环运行10秒、模拟对端报告通过；约49.99Mbps是Mac内存回环结果，**不是Wi-Fi Aware真机性能**。
- APK已覆盖安装b2302624，UI显示测试入口。未连接点击保护的自动检查未取得有效结果（截图/层级出现息屏及USB设置页），因此该UI分支未验收；不将入口存在当作双端吞吐验收。
- iPhone14 Pro目前在devicectl中unavailable，双端真实Aware的两个方向尚未验收，未报告真实吞吐值。未操作其他平板/iPhone12；未进入Ubuntu抓包模式。

实现：Android `ThroughputTest.java`、`HeartbeatTransport.java`；iOS `ThroughputTest.swift`、`BridgeModel.swift`，两端主页面增加按钮和结果。后续应在双端升级后确认Android→iOS和iOS→Android结果、心跳、连接断开及重测。
