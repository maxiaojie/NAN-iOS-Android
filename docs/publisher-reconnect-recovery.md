# 发布端断链后继续等待重连（2026-11-19）

## 原行为与原因

iOS 主动断开后，Android 超过 20 秒未收到有效心跳，HeartbeatTransport 通知 onError；AwareController 调用 fail → close，同时关闭 socket、网络请求、Publish 和 attach。用户需要手动重新发布。这是 App 生命周期策略，不是 Wi-Fi Aware 要求停止服务。

## 修改

文件：NAN-Android/app/src/main/java/io/awarebridge/android/AwareController.java。

- 心跳/UDP 错误、NetworkCallback.onLost/onUnavailable 转入 recoverNetwork。
- 仅释放旧 HeartbeatTransport 和 NetworkCallback，保留 Publish、attach、配对开关及原有一分钟关窗任务。
- 清空界面旧对端和收发计数，提示服务继续发布；一秒后建立新的 socket 和网络请求，用新端口等待 iOS 连接。
- 每代网络请求有 generation 标识；忽略旧网络回调和已排队的旧统计/错误，避免旧链路重复清理新请求。
- 用户停止、Aware 不可用/会话终止、Publish 失败仍完整关闭；停止会取消待执行的恢复任务。
- 记录恢复原因和保留 Publish 的日志。HeartbeatTransport 超时文案改为通用链路失去响应，避免错误地要求发布端重新发布。

订阅端的连接恢复策略没有改动。普通运行时错误/权限问题若在创建请求时抛异常，仍走完整失败路径，不承诺所有系统错误自动恢复。

## 验证范围

JDK 21 下 assembleDebug、lintDebug 通过。APK 已覆盖安装 a0807d7（Success），冷启动 Status=ok。双端断开 → 超时 → 保持同一 Publish → iOS 重连的完整真机流程尚未验收，不能把此前 HAL 修复的成功连接当作本修改的回归结果。此前锁屏的 iPhone 本轮未解锁或操作，Ubuntu 未切换 monitor。
