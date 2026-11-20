# a0807d7 固件实验补丁：STA 并发空口验证

2026-11-20。结论：**在本轮 a0807d7 / baldur-1 的 STA 连接条件下，补丁后的 3 个目标 Publish SDF 均实际携带 Extended WLAN Infrastructure，合法属性之后额外零尾为 0；iPhone 12 设备选择页能发现 baldur-1。** 不将这一小样本扩展为所有机型、订阅角色或长稳全部通过。

## 环境与操作

- Android：a0807d7 / Lenovo TB323FU / Android 17，设备名 baldur-1。实际固件 SHA-256 `23f5ed17af4d6ff8ddb5818e87a9cc2c5328a820beb976c27cf4a89bb15ea038`，与此前部署的六指令字补丁一致。
- STA：保持连接 lenovo-internet，没有为本轮验证断开 STA 或切换回原始固件。收尾快照为 5260 MHz / 信道 52，网络 VALIDATED；本轮未对每帧 STA 频率做精确同步，不能由收尾快照断言每一帧的瞬时信道都相同。
- iOS：USB iPhone 12，AwareBridge 订阅角色，打开“添加 Android 设备”的系统选择页；截图直接看到 baldur-1。未点击重新配对、未删除任何配对记录。
- 抓包：Ubuntu 192.168.31.211，wlp3s0mon，分别固定信道 6、149。使用固定 sudo 管理命令，无需用户再次输入 sudo。

初次窗口中未捕获目标 Publish，只看到 Beacon 或 iOS Subscribe，不能据此宣称修复。随后先启动抓包，再停止/重新发布并重开 iOS 选择页，覆盖 solicited Publish 首次匹配响应。目标 Publish 样本很少，因此必须按实际数量报告，不能把整包数万帧当作数万次 Publish 验证。

## 目标帧与属性边界

Ubuntu 根目录：`/home/mxj/NAN/2026-11-20/amss20-patch/`。
Mac 副本根目录：`/Users/mxj/workspace/nan-capture-analysis/2026-11-20/amss20-patch/`。

| 文件 / 信道 | 帧号 | a0807d7 的 NMI | 捕获/原始长度 | 最后 0x1e 属性起点 | 属性总长 | 额外零尾 |
| --- | --- | --- | --- | --- | --- | --- |
| `04-ch6-restart.pcap` / 6 | 544 | `02:2c:64:d6:b5:3b` | 515 / 515 | 492 | 23 | 0 |
| 同上 / 6 | 5131 | `02:42:4f:eb:e5:57` | 516 / 516 | 493 | 23 | 0 |
| `05-ch149-restart.pcap` / 149 | 18898 | `02:4c:40:8e:d9:83` | 496 / 496 | 473 | 23 | 0 |

偏移均相对于完整捕获记录、从 0 开始，含 56 字节 radiotap。三帧 FCS-present=false、cap_len=frame.len，没有捕获截断；独立 Python 边界扫描与 Wireshark tshark 的属性类型/长度清单相符。

三次 NMI 均与 a0807d7 同步驱动日志 `NAN_NEW_MAC_ADDR` 对上；服务 TXT 均为 modelName=TB323FU、pairingName=baldur-1、vendorName=LENOVO。三帧属于不同发布会话，不能把 NMI 改变误作另一台设备。

三帧最后属性均为：

```text
1e 14 00 [20 字节内容]
```

例如信道 149 第 18898 帧：

```text
1e 14 00
00 00 00 00 00 00     BSSID 字段
00 00 00 00 00 00     基础设施 MAC 字段
01                    非 AP STA 角色
76 34 00              Operating Class 118、信道 52、辅助字段 0
00 00 00 00           Beacon 信息
```

该帧恰好在属性末尾结束：473 + 3 + 20 = 496，没有额外 23 字节。属性内容中的两个 MAC 和 Beacon 信息为零，与已逆向的 builder 实现一致；这些是长度覆盖范围内的数据，不能再按“帧尾看见零”判成越界零尾。

信道 6 两帧的属性信道字段为 `51 06 00`（Operating Class 81、信道 6）。本轮未独立同步每帧对应的 STA 上下文，因此不对这两个值的完整并发语义作额外验收；本轮明确验收的是属性实际写入和帧边界，149 样本的信道声明与 STA 信道 52 快照一致。

## 区分旁边另一台 baldur-2

`04-ch6-restart.pcap` 中还捕获到源地址 `02:ef:f4:eb:99:17` 的 8 个 Publish，均多 23 个零，例如第 11 帧。其 TXT 为 **pairingName=baldur-2**，不是本轮目标 baldur-1，不能混入补丁设备统计。

这提供同场异常现象的旁证，但本轮没有读取 baldur-2 的固件或确认其补丁状态，不把它称为严格同机 A/B，也不操作该设备。历史 a0807d7 原始固件的同机异常记录继续保留。结合目标帧的明确身份、实际出现 0x1e 头和无额外零尾，本轮支持当前补丁对目标症状生效。

## 文件和复核信息

原始包及完整日志不入 Git，避免暴露配对安全属性。Ubuntu 日期目录保留五份抓包，另有只读分析脚本 `analyze_sdf.py`、不含 SSI/安全密钥的 `summary.json`。

| 文件 | 总记录数 | 本轮用途 | SHA-256 |
| --- | --- | --- | --- |
| `02-ch6-discovery.pcap` | 15485 | 没有目标 Publish，不作为修复验收 | `5eaf5752adbb3ff40598a022bbae211991bd752b99af06b0911f15966bbb7036` |
| `03-ch149-discovery.pcap` | 99420 | 捕获 iOS Subscribe，没有目标 Publish，不作为修复验收 | `4a9bba8b3e9a80dcaa5c9930868ff0ff1b0faa6912a529e9451e27ae0c7e9574` |
| `04-ch6-restart.pcap` | 43481 | 2 个目标 Publish | `d080008870bc80f663fbfe1eb485225804d42ca29bb755842ce268630a5cf5e5` |
| `05-ch149-restart.pcap` | 111969 | 1 个目标 Publish | `fca993b54a91b60d7d83a7bf4405facf7a05164a53f7820ad0f296773e4c94b2` |

固定抓包包装命令本轮有 timeout 后返回 137 的情况，文件仍经收尾改为 mxj 可读；已逐记录验证用于分析的 PCAP 无半截记录。不将该情况解释为无线设备崩溃，也不宣称抓包无丢帧。

## 收尾与验收边界

Android 停止测试 App，恢复 USB 常亮设置 0，保持补丁固件和 STA 连接；没有改动配对缓存。iPhone USB 锁屏指令未生效，用户随后手动锁屏；devicectl 已确认 passcodeRequired=true，不虚报自动锁屏成功。

Ubuntu 首次恢复因接口尚未就绪未连接，重试后 **16:15 恢复 managed 模式、lenovo-internet、默认路由，HTTPS HTTP/2 200**。之后机器被新启动的 root Wireshark 再次切为 monitor 信道 10；用户明确要求“是，保留当前抓包模式”，因此最终按最新指令保留其新抓包状态，不再次覆盖该操作。本轮取包已结束。

已验收：本机当前 STA 场景，两个抓包信道、三次发布会话的目标 Publish 属性填充/边界及 iOS 选择页发现。尚未验收：Android Subscribe 的对应帧、首次配对、NDP/吞吐、整机重启、长期运行、多 AP/信道和其他固件版本。不是全面发布版固件修复结论。

[补丁、原件备份和一键恢复](amss20-ext-wlan-patch-deployment.md)；[函数与地址分析](amss20-nan-disassembly-2026-11-20.md)。
