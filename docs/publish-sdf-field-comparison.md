# Publish SDF 正常/异常逐属性对照

日期：2026-11-19；关联 P07。只读分析既有 PCAP，没有启动设备或改变网络。报文解析以本机 Wireshark/tshark 解码和独立 Python 属性边界检查互相核对；调度属性变化不自动等于协议错误。

## 选择的三帧

1. A：303fdedc/TB323FU，`303fdedc-sta/02-sta-off-on-ch6.pcap` 第 32181 帧，10:49:16.424685，STA 断开，正常。
2. B：同一文件第 33004 帧，10:49:17.473479，STA 开始连接阶段，23 字节异常零尾。与 A 同一 NMI、同一 Publish、同一 Setup 状态，适合隔离 STA 变化。
3. C：b2302624/TB522FU，`b2302624-sta/06-lenovo-internet-ch6.pcap` 第 11745 帧，11:33:05.641836，已连接 lenovo-internet/5260 MHz，正常。不同硬件/版本，只作补充对照。

上述相对目录的共同根路径：Ubuntu `/home/mxj/NAN/2026-11-19/`；Mac `/Users/mxj/workspace/nan-capture-analysis/2026-11-19/`。不要把各文件中的相同帧号混用。

## A/B 同机对照：完整属性顺序与长度

表中 offset 从 **802.11 Frame Control 的第一个字节**起算，十进制，不含 56 字节 radiotap。Wireshark Packet Bytes 整条记录偏移需加 56。offset 指属性 ID，payload 长度不含 ID+LE16 长度头的 3 字节。

| 属性 ID / 名称 | A offset / payload长度 | B offset / payload长度 | 内容对照 |
| --- | --- | --- | --- |
| 0x0f Device Capability | 30 / 9 | 30 / 9 | 完全一致 |
| 0x2a Device Capability Extension | 42 / 2 | 42 / 2 | 完全一致 |
| 0x12 Availability，Map 2 | 47 / 51 | 47 / 51 | 仅 Sequence ID 变化 |
| 0x12 Availability，Map 1 | 101 / 55 | 101 / 51 | 序号、Potential调度内容及长度变化 |
| 0x03 SDA | 159 / 96 | 155 / 96 | 完全一致 |
| 0x0e SDEA | 258 / 96 | 254 / 96 | 完全一致，设备 TXT 未变化 |
| 0x22 CSIA | 357 / 3 | 353 / 3 | 完全一致：00 07 01 |
| 0x23 Security Context Info | 363 / 20 | 359 / 20 | 完全一致；不公开安全字段原值 |
| 0x0b Country Code | 386 / 2 | 382 / 2 | 完全一致：US |
| 0x2c Pairing Bootstrapping | 391 / 5 | 387 / 5 | 完全一致：00 00 00 02 00 |
| 0x17 Unaligned Schedule | 无 | 395 / 16 | 新增，共19字节含头 |
| 0x2b NAN Identity Resolution | 399 / 17 | 414 / 17 | 完全一致；不公开安全字段原值 |
| 0xdd Vendor Specific，OUI 00:00:f0 / type 0x41 | 419 / 33 | 434 / 33 | 完全一致 |
| 0xdd Vendor Specific，OUI 00:00:f0 / type 0x33 | 455 / 14 | 470 / 14 | 完全一致 |
| 最后合法属性后的尾部 | 无 | offset487 / 23 | 全00，不在任何声明的合法属性内部 |

长度：A=528（含radiotap）/472（802.11），B=566/510。合法属性结束位置 A=472，B=487；异常尾部在 B 的整记录偏移543开始，结束于565。总差38=-4（Availability）+19（Unaligned Schedule）+23（异常零尾）。后续属性起点移动不代表它们内容改变。

## Availability 的实际变化

两个属性 Sequence ID 都从 `0x89`（137）变为 `0x8b`（139）。Map2的其余50字节完全一致；不能把序号变化当损坏。

Map1的Committed条目完全一致：Entry Control=0x1219，Usage Preference=3，Rx NSS=2，16 TU/bit、周期512 TU、bitmap=`00 0f 00 00`，Operating Class=128，channel set=155，primary bitmap=01。

Potential条目发生调整：

| 条目 | A：STA断开 | B：STA连接阶段 |
| --- | --- | --- |
| 原 `00 f0 00 ff` bitmap | Control=0x120a、Preference=1；class128、set155、primary01 | 同样内容，移动到最后一项 |
| `ff 00 ff 00` bitmap | Control=0x120a、Preference=1；两个channel entry：class128/set155/primary0f，以及class128/set42/primary0f；条目Length18 | Control=0x121a、Preference=3；一个channel entry：class128/set58/primary01；条目Length14 |

减少一个4字节channel entry，使该属性总payload从55降为51。class128是80 MHz operating class，这里的155/42/58是该class的信道集合标识，不应直接读成实际正在抓取/发送的20 MHz主信道；本文件抓包实际在信道6。

这些时隙、候选信道与偏好调整符合 STA 并发会影响调度这一现象，但本轮没有完成标准一致性审查，不能宣称每个位都已认证正确。

## 新增的 Unaligned Schedule

B payload为 `00 02 00 00 00 00 00 00 00 00 00 00 00 00 00 00`：Wireshark解码Control=0x0200，Schedule ID=0、Sequence ID=2；Starting Time/Duration/Period/Count Down/ULW Overwrite均0。这是**有属性ID及长度的16字节payload**，不应和最后23个没有合法属性归属的00混淆。

它在本组数据中与异常一起出现，值得查可选调度属性的插入、长度预算和发送长度；仅凭Period/Duration=0不能确定实现违反了哪一条规范，也不能断言它导致iOS丢帧。此前a0807d7代表异常帧没有该属性仍多23字节，见[既有定位限制](303fdedc-sta-sdf-driver-analysis.md)。

## C：TB522FU 同样连接 STA，却正常

B/C 的两份 Availability 都是51字节。**分别去掉前3字节（Sequence ID及Attribute Control）后，两个payload剩余48字节均逐字节一致**；区别仅在序号和Changed标志：B为 `8b 22 00`/`8b 21 00`，C为 `0b 12 00`/`0b 11 00`。也就是说，B中的候选信道、时隙bitmap、Usage Preference、Rx NSS调整并非异常帧独有。

C 与 B 的其他差异：

- C没有 Device Capability Extension、Country Code、Unaligned Schedule，以及OUI00:00:f0/type41的33字节Vendor属性。仅缺省或新增某属性不能直接证明对端协议异常；type41私有payload的语义本轮没有源码依据，不能自行命名。
- Device Capability、CSIA=`00 07 01`、Pairing Bootstrapping均与B完全相同。两台原HAL的GTK能力缺失是独立问题，不是区别有无23字节的条件。
- SDA/SDEA长度分别为92/91，而B为96/96；两机设备名、型号等TXT不同，不能直接按原始位置比较成“损坏”。Security Context/NIRA也因设备、会话不同而不同，不公开原值。
- C合法帧末尾直接结束，记录长469；B记录长566。两机差97字节可完整对账：额外属性5+5+19+36=65、SDA/SDEA额外4+5=9、非法零尾23，共97。

## MAC头与抓包元数据

A/B源地址、目的地址、BSSID相同，MAC序号2351→2364；radiotap TSFT 402580402→403629879，信号强度其中一根天线-38→-37 dBm。这些是不同发送时刻的正常变化，radiotap由抓包端附加，不是HAL构造的NAN属性。两帧radiotap长度56，cap_len=frame.len、FCS-present=false，不是FCS或抓包截断造成23字节。

结论：前面的差异确实存在，主差异是调度属性调整和新增Unaligned Schedule；本组中服务信息、安全声明和私有Vendor内容均未随STA改变。TB522FU正常帧进一步削弱了“Availability payload本身错误”的假设；仍应追查TB323FU在可选属性组装/长度累计/最终TX长度处的处理，不能仅凭PCAP锁定Firmware具体函数。
