# iPhone 12 USB 锁屏修复（2026-11-24）

## 已确认原因

本机 pymobiledevice3 的 `hid button lock` 将 Consumer/Power（usage page `0x0c`、usage `0x30`）按住 0.5 秒。用户观察到反复弹出 Siri。上游 issue #2210 与修复 PR #2212 已明确复现同一问题：长按会触发 Siri，应改为 0.05 秒短按。

- [上游问题 #2210](https://github.com/doronz88/pymobiledevice3/issues/2210)
- [上游修复 #2212](https://github.com/doronz88/pymobiledevice3/pull/2212)
- [Apple 侧键说明](https://support.apple.com/en-sg/105103)：长按侧键用于 Siri。

之前将命令成功或 `passcodeRequired` 当成锁屏完成证明不准确。该字段表示是否需要密码，不等于当前屏幕锁定状态。本轮同一时刻 iPhone 12 的 SpringBoard lockstate=1，devicectl passcodeRequired=false，截图黑屏，说明两者不能等同。前一天“未锁住还是又解锁”的推测仍保留为历史，不再作为当前根因判断。

## 真机操作与结果

设备：USB iPhone 12，UDID 00008101-0016212414D0001E。

1. 操作前 `com.apple.springboard.lockstate=0`。
2. 执行短按：

```bash
PYMOBILEDEVICE3_UDID=00008101-0016212414D0001E \
  /Users/mxj/nan-ios-tools/bin/pymobiledevice3 \
  developer core-device hid raw-button 0x0c 0x30 press
```

`raw-button` 在本机实现的 down/up 间隔为 0.05 秒；本轮没有再次使用有缺陷的 0.5 秒命令。

3. 等待后通知状态从 0 变为 1；独立读取仍为 1，截图为黑屏。
4. 手机保持锁屏，没有为验证重复唤醒或要求用户再次解锁。

截图 `/tmp/nan12-short-lock.png`（本机临时证据，不入库）。这是一次真机短按成功，不声称覆盖所有 iOS 版本与设备设置。

## 固化方法

本机环境 `/Users/mxj/nan-ios-tools` 已将以下两处锁屏时长从 0.5 改为 0.05 秒，与上游修复一致：

- `pymobiledevice3/cli/developer/core_device.py` 的 `ButtonName.LOCK`。
- `pymobiledevice3/remote/core_device/screen_stream.py` 的 lock 映射。

原文件备份在 `/Users/mxj/nan-ios-tools/patch-backups/2026-11-24-short-lock/`。只修锁屏映射，没有更新整套依赖。升级该环境后应核对修复是否仍存在。

推荐收尾使用仓库脚本（明确指定设备）：

```bash
/Users/mxj/nan-ios-tools/bin/python \
  /Users/mxj/workspace/NAN-iOS-Android/tools/lock-iphone.py \
  00008101-0016212414D0001E
```

脚本先读 SpringBoard 通知状态：已锁定则不按键；未锁定只短按一次，然后轮询确认。查询出错则失败，不盲按；确认失败也不自动重试。短按是 sleep/wake 切换，不能不查状态反复执行。通知读取需要支持该能力的系统（本脚本面向 iOS 27.2+）；不支持时应另行判断，不能把 passcodeRequired 当替代。

本轮脚本在已锁屏设备上输出 `Already locked; no button event sent.`，此分支实测通过。脚本未锁定分支与异常分支没有额外唤醒设备注入测试；前述 raw-button 已单独实测相同短按参数成功。
