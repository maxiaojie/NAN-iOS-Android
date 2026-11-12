# 验证记录

日期：2026-11-12。此记录区分构建检查与真实无线互通，后者尚未通过。

## 已通过

- `scripts/check.sh`：Android Debug 编译、Android lint、Swift JSON 协议检查、iOS 真机目标无签名编译。
- Swift 协议检查覆盖 Android 格式报文解码、中文文本、ACK 的 session / seq，以及不支持的版本、负序号拒绝。
- Android Debug APK 安装到 TB323FU（Android 17），成功 attach、发布服务并注册数据链路请求。
- iOS 签名构建安装到 iPhone 12（iOS 27.0）；本机借用已有、包含 Wi-Fi Aware 权限的示例 Bundle ID/profile。仓库保留独立 Bundle ID，签名资料未入库。
- USB 真实触控：点击 App 按钮后打开了系统 DevicePicker；不是仅截屏或启动 App。
- Android 停止/退出时关闭网络请求和 Aware 会话，未观察到本 App 遗留的活动 NDP。

## 尚未通过

本轮 DevicePicker 未显示 Android。iOS 浏览连接也未到达可交换应用数据的状态，因而**未验证新 App 的配对成功、NDP 建立、UDP 双向收发或两分钟持续心跳**。

排查使用了独立服务名与原示例服务名、Solicited/Unsolicited 发布、原 Android App 与原 Apple 示例 App 的对照，以及两端 Wi-Fi 开关重试。原版对照同样未发现设备。Android HAL 有 Publish Replied 事件，而采集窗口内 iOS `wifip2pd` 没有对应发现结果；这些证据不足以确认具体故障原因，也不能证明新代码已具备端到端互通能力。

最终代码恢复 Solicited 发布，服务名为 `_aware-bridge._udp`。为验证首次配对，测试中已在 iOS 删除 TB323FU 的旧配对；再次测试需要重新配对。iPhone 重启后暂未恢复 USB 枚举，真机测试等待设备重新连接。

## 继续真机验证

1. 确保两端距离接近，Wi-Fi、Android 定位与所需权限开启，停止其他测试 App 的 Aware 会话。
2. Android 开始发布，iOS 添加设备，输入 Android 显示的六位 PIN；记录两端配对成功结果。
3. iOS 连接；检查 Android NDP/IP 与 iOS ready 状态。
4. 确认两端接收计数都持续增长，观察至少两分钟并核对 ACK；仅有 IP 或本机发送计数不算双向通信成功。
5. 断开、停止，检查 NDP 与网络请求释放；再次连接验证缓存配对。
6. 测试结束停止两端 App，并锁定 iPhone、确认屏幕关闭。

完成上述步骤后再记录实测结果并发布经过真机验证的版本。
