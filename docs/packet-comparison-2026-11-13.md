# TB323FU Publish 对照：11 月 12 日与 13 日

输入文件为 Ubuntu `/home/mxj/NAN/1112/3.pcapng`（用户所称 3.pcap）和本轮自动抓包 `ch6-20261113-100114-82604.pcap`。原始抓包保留于测试机和本机临时目录，未上传仓库。

| 项目 | 昨天 3.pcapng | 今天信道 6 抓包 |
| --- | --- | --- |
| 时间 / 代表帧 | 2026-11-12 16:07:28，265 | 2026-11-13 10:01，530 |
| 平板 NMI | 02:55:b6:3c:e3:15 | 02:64:5f:51:1d:51 |
| 检查的平板 Publish 数量 | 10 | 70 |
| 含 radiotap 的帧长 | 491 字节 | 494 字节 |
| NAN Action body 长度 | 411 字节 | 414 字节 |
| 服务 ID | 63:48:7a:70:d3:a0 | 相同 |
| Publish instance / requestor ID | 1 / 0 | 1 / 1 |
| SDEA control | 0x0440 | 相同 |
| Device Capability 原始值 | 00 89 04 14 15 22 10 2f 08 | 相同 |
| NAN Availability 属性长度 | 51、55 | 51、51 |
| SSI 总长 | 67 字节 | 59 字节 |
| SSI 内部头 | 04 3d 00 | 04 38 00 |
| TXT 实际长度 | 58 字节 | 56 字节 |
| modelName / pairingName / vendorName | android-pad / lapis / lenovo | TB323FU / TB323FU / LENOVO |
| TXT 后 SSI 附加数据 | dd 03 00 00 17 f2（6 字节） | 无 |
| Cipher Suite Info 原始值 | 04 05 01 07 01 | 相同 |
| Pairing Bootstrapping 原始值 | 00 00 00 02 00 | 相同（PIN Display） |
| NIRA 属性 | 17 字节 | 17 字节，nonce/tag 随运行变化 |
| Vendor Specific 属性 | OUI 00:00:f0，类型 0x41 长 33，类型 0x33 长 14 | 两项内容逐字节相同 |
| 最后一个属性之后 | 正常结束 | 额外 23 字节 00 |

23 字节额外零数据出现在本轮所检查的全部 70 个平板 Publish 中；昨天 10 个 Publish 均未出现。昨天最后一个厂商属性本身以 7 个零字节结尾，今天是同样的 7 字节加额外 23 字节；两者不能混为一谈。Wireshark 把额外数据解析为七个长度为零的 Master Indication 属性和不足一个属性头的尾部，报告 Malformed。

帧长差异可以精确核对：今天 Availability 少 4 字节；SDA 与 SDEA 中各复制一份 SSI，各少 8 字节；帧尾多 23 字节。因此总差异为 -4 -8 -8 +23 = +3 字节，491 变为 494。

## 能与不能得出的结论

- 服务 ID、SDEA control、配对引导方法和两个厂商属性一致；这几个字段不是这两份抓包中的变化点。
- requestor ID 与可用时间/信道位图发生变化；不能仅凭变化认定格式错误或过滤原因。
- 昨天这份抓包仍带旧 SSI：内部声明长度 61，实际 TXT 58 字节，头后实际还有 64 字节（含 6 字节尾部）。因此不能将它当作后续修正 SSI 长度、成功配对时的最终配置，也不能仅凭 Publish 存在认定 UI 已发现。
- 今天平板多出的尾部是明确的原始字节差异，但尚无 iOS 驱动丢弃原因日志，也未通过单独消除尾部的对照验证，不能断言它就是发现失败根因。
- 同一轮 Pixel 9 能被 iOS 发现，其对应 Publish 没有这一额外尾部。平板关闭配对缓存后 NIRA 消失，额外尾部保留，发现仍未恢复。

## 后续单设备切换结果

用户在同一台平板上断开普通 Wi-Fi 后，确认额外 23 字节消失且 iOS 恢复发现；USB 截图也确认新 App 显示平板设备名。该关联使 STA + NAN 并发路径成为主要排查方向。详见 [ROM 问题记录](tb323fu-sta-nan-issue.md)。
