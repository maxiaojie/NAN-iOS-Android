# 验证记录

更新：2026-11-13。此记录区分构建检查与真实无线互通；完整双向互通尚未通过。

## 已通过

- `scripts/check.sh`：Android Debug 编译、Android lint、Swift JSON 协议检查、iOS 真机目标无签名编译。
- Swift 协议检查覆盖 Android 格式报文解码、中文文本、ACK 的 session / seq，以及不支持的版本、负序号拒绝。
- Android Debug APK 安装到 TB323FU（Android 17），成功 attach、发布服务并注册数据链路请求。
- iOS 签名构建安装到 iPhone 12（iOS 27.0）；本机借用已有、包含 Wi-Fi Aware 权限的示例 Bundle ID/profile。仓库保留独立 Bundle ID，签名资料未入库。
- USB 真实触控：点击 App 按钮后打开了系统 DevicePicker；不是仅截屏或启动 App。
- Android 停止/退出时关闭网络请求和 Aware 会话，未观察到本 App 遗留的活动 NDP。

## 尚未通过

11 月 12 日的测试中 DevicePicker 未显示 Android。11 月 13 日新增 Pixel 9 对照，已观察到新 App 的发现和 Android 配对成功回调，但仍**未验证新 App 的 NDP 建立、UDP 双向收发或两分钟持续心跳**。

排查使用了独立服务名与原示例服务名、Solicited/Unsolicited 发布、原 Android App 与原 Apple 示例 App 的对照，以及两端 Wi-Fi 开关重试。原版对照同样未发现设备。Android HAL 有 Publish Replied 事件，而采集窗口内 iOS `wifip2pd` 没有对应发现结果；这些证据不足以确认具体故障原因，也不能证明新代码已具备端到端互通能力。

最终代码恢复 Solicited 发布，服务名为 `_aware-bridge._udp`。为验证首次配对，测试中已在 iOS 删除 TB323FU 的旧配对；再次测试需要重新配对。11 月 13 日 iPhone USB 已恢复。

## 继续真机验证

1. 确保两端距离接近，Wi-Fi、Android 定位与所需权限开启，停止其他测试 App 的 Aware 会话。
2. Android 开始发布，iOS 添加设备，输入 Android 显示的六位 PIN；记录两端配对成功结果。
3. iOS 连接；检查 Android NDP/IP 与 iOS ready 状态。
4. 确认两端接收计数都持续增长，观察至少两分钟并核对 ACK；仅有 IP 或本机发送计数不算双向通信成功。
5. 断开、停止，检查 NDP 与网络请求释放；再次连接验证缓存配对。
6. 测试结束停止两端 App，并锁定 iPhone、确认屏幕关闭。

完成上述步骤后再记录实测结果并发布经过真机验证的版本。

## 2026-11-13 对照实验

- Ubuntu monitor 接口自动抓包已可用，分别采集信道 6、149；原始抓包和含密钥的系统日志未提交仓库。
- 当前同一台 iPhone 能发现 Pixel 9 的旧 App，也能发现新 AwareBridge App。此对照暂用原示例服务名 `_sat-simulation._udp`；正式代码已恢复独立服务名。
- Pixel 9 新 App 在系统配对流程后显示“配对成功”；连接时收到 NDP 请求，HAL 确认 response code 为 1，iOS 显示 Wi-Fi Aware 错误 -11987，应用收发计数仍为 0。配对成功不等于数据链路成功。
- TB323FU 在 09:28:38 曾被 iOS 系统发现并解析 TXT，之后在平板重启附近的测试窗口中失去发现。用户报告前一天原 App 已成功发现、配对和连接；不能据此将当前失败归结为设备不支持。
- 此后 TB323FU 的新 App、原 App，以及从 Pixel 提取的同一旧 APK 均未稳定出现在当前 DevicePicker。后一项信道 6/149 抓包未捕获 Publish，因此不能拿它作异常帧复现证据。
- 同一份抓包中，Pixel 和 TB323FU 的服务 ID、SSI 长度、SDEA 控制值相同。TB323FU 部分 Publish 帧尾多出 23 字节零数据，被 Wireshark 解析成异常属性；Pixel 对照帧没有此尾部。该差异尚未证明是 iOS 丢弃原因。
- 缩短 pairingName、关闭配对缓存与验证均未恢复平板发现。关闭缓存后 NIRA 属性消失，但上述零数据保留。临时诊断改动全部恢复。
- 已通过旧 App 身份删除 TB323FU 的测试配对别名 aware-100，并核对系统存储中别名和安全信息映射为空；未删除普通 Wi-Fi 配置。
- 最终代码再次通过 scripts/check.sh。由于无线互通验收未通过，未 push GitHub。

## 发现恢复（11 月 13 日追加）

用户断开平板普通 Wi-Fi 后恢复发现，并报告异常 23 字节尾部消失；USB 截图确认当前独立服务名的新 App 显示平板并进入 PIN 配对。随后配对成功，但 NDP 建立仍失败：iOS -11987，Android 驱动原因码 9010。参见 [ROM 问题记录](tb323fu-sta-nan-issue.md)。此前“尚未发现”的记录仅描述相应测试时段。用户现已明确要求上传问题文档，因此仓库可先推送当前工作；这不代表双向数据验收通过。
