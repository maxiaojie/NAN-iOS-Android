# 删除 setVendorData 的真机验证

日期：2026-11-13，Asia/Shanghai。测试代码基于 `8e76674`，唯一功能变化为删除 Android 发布阶段的 `setVendorData(OUI=0x506f9a, android=false)` 及不用的 OuiKeyedData import；SSI、正式服务名、Solicited、配对/缓存/验证、PASN 和 NDP/UDP 参数保持不变。iOS 继续使用已安装的恢复版本。

## 环境与结果

- TB323FU，Android 17；普通 Wi-Fi 未连接，Wi-Fi 开关开启。
- iPhone 12，iOS 27.0；两端 App 前台。
- Pixel 9 本轮停止发布，避免混入其他发布者；没有在 Pixel 上验证本次无 VendorData 版本。
- Android assembleDebug / lintDebug 通过，安装的是删除调用后重新编译的 APK。

| 步骤 | 实际结果 |
| --- | --- |
| 既有配对重连 | 11:17 成功建立 NDP，iOS 17/17，Android 13/13（截图非同一时刻） |
| 清除 Android 测试配对 | 以 io.awarebridge.android 自身 UID 调用系统 resetPairedDevices，仅清理当前 App；未使用 ADB root |
| 删除 iPhone 平板配对 | 设置 → 隐私与安全 → 配对设备 → 编辑 → 删除指定平板 → 确认取消配对；App 返回后没有已配对平板可选 |
| 重新发现 | DevicePicker 重新列出“联想平板 拯救者 Y700 五代”，不依赖 App 旧列表 |
| 新 PIN 配对 | Android 生成并显示 PIN，经 USB 在 iPhone 输入；Android 显示“配对成功，请在 iOS 上点击连接”，iPhone 重新出现已配对平板 |
| 系统确认 | Android 11:30:30.933 eventPairingConfirm，pairingInstanceId=26；对应 App 成功状态，非只看到发起请求 |
| 新配对后的 NDP / UDP | 点击连接后进入双向通信；11:32 iOS 15/15，11:33 增至 44/44；Android 同期 46/45，最近接收 0 秒，收到 iOS received heartbeat |

测试中第一次分步操作的配对尝试没有完成，Android 日志出现 HAL Pairing Confirm Timeout（20 秒）及 reject reason=1。该次不能计为通过，也不能仅凭超时认定 VendorData 必需。重新触发发现/配对、连续完成读取 PIN 与输入后，上表所述新配对成功。其失败的更细底层原因未单独定位。

## 结论及边界

当前 TB323FU / iPhone 12 / STA 断开条件下，不调用 setVendorData 仍能重新发现、全新 PIN 配对、缓存配对重连和交换应用数据，因此该调用不是当前已验证互通路径的必需项，正式代码删除该调用。

此结果不等于所有 ROM 通用，也不证明原先 bundle 在所有厂商固件中均被忽略；没有将删除调用当作修复 STA 并发额外 23 字节的证据。未修改其他服务/配对参数以“凑出”成功。构建暂时保留原编译 jar；是否彻底移除 jar 需另行用标准 SDK 构建验证。

本机证据文件位于 `/tmp/aware-reverse/`：`no-vendor-build.log`、`no-vendor-status.png`、`re-pair-app.png`、`re-pair-discovery.png`、`re-pair-final.xml`、`re-pair-final-logcat.txt`、`re-pair-data.png` / `.xml`。含 PIN/安全信息的原始文件不上传公开仓库。
