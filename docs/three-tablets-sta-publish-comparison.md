# 三台平板均连接 STA 时的 Publish 对照

2026-11-19，P07。只分析既有抓包，不混用 STA 断开帧作主对照。正常指没有额外23字节，不表示配对功能全部通过。三次均为 lenovo-internet / 5260 MHz；不同时间和BSSID，不是同一时刻、同一AP的严格硬件隔离实验。

## 文件与代表帧

- 正常 b2302624/TB522FU：`2026-11-19/b2302624-sta/06-lenovo-internet-ch6.pcap`，11745。
- 异常 303fdedc/TB323FU：`2026-11-19/303fdedc-sta/02-sta-off-on-ch6.pcap`，33004，STA连接切换阶段第一异常帧；后续持续已连接帧也有23零尾。它不是已稳定连接数十秒后的采样。
- 异常 a0807d7/TB323FU：`2026-11-18/09-sta-connected-ch6.pcap`，99373。

Mac共同根目录 `/Users/mxj/workspace/nan-capture-analysis/`；Ubuntu共同根目录 `/home/mxj/NAN/`。三者均在信道6捕获；STA信道52与SDF实际抓取信道6不是同一概念。

## 属性清单与payload长度

长度均不含3字节NAN属性头。顺序按帧中顺序。

| 属性 | 正常 b2302624 | 异常 303fdedc | 异常 a0807d7 |
| --- | --- | --- | --- |
| Device Capability / 0x0f | 9 | 9，完全相同 | 9，完全相同 |
| Device Capability Extension / 0x2a | 无 | 2：05 03 | 2：05 03 |
| Availability Map2 / 0x12 | 51 | 51 | 51 |
| Availability Map1 / 0x12 | 51 | 51 | 51 |
| SDA / 0x03 | 92 | 96 | 96 |
| SDEA / 0x0e | 91 | 96 | 96 |
| CSIA / 0x22 | 3：00 07 01 | 3：00 07 01 | 5：04 05 01 07 01 |
| Security Context / 0x23 | 20 | 20 | 20 |
| Country Code / 0x0b | 无 | 2：US | 2：US |
| Pairing Bootstrapping / 0x2c | 5：00 00 00 02 00 | 相同 | 相同 |
| Unaligned Schedule / 0x17 | 无 | 16：00 02 后14个00 | 无 |
| NIRA / 0x2b | 17 | 17 | 17 |
| Vendor Specific / 0xdd，00:00:f0/type41 | 无 | 33 | 33，与303完全一致 |
| Vendor Specific / 0xdd，00:00:f0/type33 | 14 | 14，完全相同 | 14，完全相同 |
| 最后合法属性后的额外零尾 | 0 | 23 | 23 |
| 总长：含radiotap / 不含 | 469 / 413 | 566 / 510 | 549 / 493 |

## 前面的内容到底差在哪

### Availability：调度主体完全相同

两份属性各自前3字节为Sequence ID和Attribute Control：

| | b230正常 | 303异常 | a080异常 |
| --- | --- | --- | --- |
| Map2 | 0b 12 00 | 8b 22 00 | 18 02 00 |
| Map1 | 0b 11 00 | 8b 21 00 | 18 01 00 |

去掉这3字节后，每个属性剩余48字节在三台之间都完全一致。包括Committed/Potential、时间bitmap、信道条目、Usage Preference、Rx NSS。b230设置Committed Changed，303设置Potential Changed，a080代表帧两者均未设置；这些是更新状态，不能因为原始字节不同就当成调度错误。

### 两台异常设备共有、正常设备缺少的属性

- Device Capability Extension的payload为05 03，Wireshark解码包含6GHz regulatory information、Pairing Enable和NPK/NIK Caching Enable。这是设备能力声明，不能与App的PairSetup开关直接等同。
- Country Code为US。
- 00:00:f0/type41的33字节Vendor payload，两台TB323FU逐字节一致。其私有字段语义缺少实现依据，本轮不自行命名或认定它错误。它值得沿ROM/固件组帧入口查，但**同机STA断开正常帧也带这个完全相同的属性**，所以“存在该属性”不足以触发23零尾。

### 服务信息、SDEA和安全能力

SDA服务ID均为9aa7d1b3283c，Instance/Requestor ID均01，Service Control均10。两台TB323FU的SDA内容完全相同；TB522FU设备TXT是modelName=TB522FU、pairingName=联想平板 拯救者 Y900 13，两台TB323FU是TB323FU、联想平板 拯救者 Y700 五代，vendorName均LENOVO。TXT总长前者79、后者83，解释SDA的4字节差。

SDEA控制字段：正常为0x0040；两个异常代表帧为0x0640，有Service Update Indicator，其值303=3、a080=2。两台TB323FU的SDEA只在此更新序号字节不同，其余内容完全一致；TXT差4字节加额外更新指示1字节，共解释SDEA的5字节差。控制字段差异受服务更新历史影响，不能直接归为坏帧原因：先前同台303的正常帧32181也为0x0640、更新序号3。

CSIA并不是两台异常设备共有的相同条件：303为00 07 01，与正常TB522FU一样；a080为04 05 01 07 01，与当次开启Setup的测试配置相关。不要把3台不同测试时刻都描述为同一Setup状态。本次没有给三台重新统一配对配置。

Security Context和NIRA长度相同，但跨设备内容不同，属于不同设备/会话的安全上下文；不公开原值，不能用它们不同来判定损坏。

### 只在303代表帧出现的Unaligned Schedule

303多出19字节（含头），a080无此属性仍有23零尾，故它不是异常必需条件。payload的Schedule ID=0、Sequence ID=2，其余时间、Duration、Period、Countdown、Overwrite均0。不能仅凭这种形式认定违反规范。

## 整帧长度逐项对账

- 303比正常多97：Device Capability Extension 5 + Country Code 5 + type41 Vendor 36 + Unaligned Schedule 19 + SDA差4 + SDEA差5 + 零尾23 =97。
- a080比正常多80：Device Capability Extension 5 + Country Code 5 + type41 Vendor 36 + SDA差4 + SDEA差5 + CSIA差2 + 零尾23 =80。

两台异常帧相差17：303多19字节Unaligned Schedule，a080多2字节CSIA，净差17。没有其他未对账的隐藏长度。

## 判断

可以收窄为TB323FU平台/版本相关的STA并发组帧或发送长度问题，但不能仅靠跨机PCAP判断驱动还是Firmware。共有的额外能力/国家码/私有Vendor属性值得沿其长度统计路径检查；它们也存在于TB323FU的正常STA断开帧，单独出现不是充分条件。两台异常设备并不共同具备Unaligned Schedule，也不共同具备某个CSIA值或Setup状态。三台调度主体相同，不能简单归因为Availability的信道表或bitmap本身坏了。

本轮只读抓包，不更改设备和Ubuntu网络。参考[同机STA切换对照](publish-sdf-field-comparison.md)与[驱动证据边界](303fdedc-sta-sdf-driver-analysis.md)。
