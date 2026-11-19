# AwareBridge 集成说明

上游 ESnet iperf3 3.22：https://downloads.es.net/pub/iperf/iperf-3.22.tar.gz

归档 SHA-256：`1c0d0fb02c52626111d6e132db80edfbf27bbaff8bd9245df2a371dcb0b35a92`。

保留原始 LICENSE。官方 API 说明：https://software.es.net/iperf/dev.html 。仅构建 src/libiperf.la，不构建/执行 main 命令行程序。

本地补丁：net.c/iperf_tcp.c/iperf_sctp.c 的 socket 创建经过 ab_socket，Android 用 Aware Network 绑定；net.c 的 listen 经过 ab_listen，供 App 判断服务就绪；iperf_client_api.c/iperf_server_api.c 控制循环检查取消标志，并将 select 最长等待限制为 1 秒。上游每流线程及数据/结果协议保留。

包装在 ../../native/iperf：不安装进程信号处理器，不调用命令行参数解析；one-off server 禁用 idle_timeout，避免上游超时 exit(0) 结束整个 App。每轮释放 test，再建立下一轮服务。临时文件使用 App cache/tmp，不使用手机不可写的 /tmp。

构建工具及说明见 ../../docs/throughput-test.md。仅提供 Android arm64 与 iOS iphoneos arm64。
