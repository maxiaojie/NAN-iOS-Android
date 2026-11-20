# STA 并发的 23 字节零尾：Extended WLAN Infrastructure 候选路径

2026-11-20，关联 P07。用户提出：STA 连接时固件可能自动添加 Extended WLAN Infrastructure Attribute，长度恰为 23 字节；用户还观察到 Android Subscribe SDF 存在同样零尾。本轮复核已有抓包，不修改 App、ROM 或固件，不切换设备和 Ubuntu 网络。

## 属性格式与候选原因

[Wireshark 官方解析源码](https://raw.githubusercontent.com/wireshark/wireshark/master/epan/dissectors/packet-wifi-nan.c) 将 Extended WLAN Infrastructure 定义为 ID `0x1e`、内容长度 20。完整头部应为 `1e 14 00`，长度字段使用小端序；加上 3 字节属性头，总共正好 23 字节。其内容依次为 BSSID（6）、设备基础设施 MAC（6）、角色（1）、Non-NAN Operating Channel Information（3）、Non-NAN Beacon Information（4）。

因此“STA 并发启用该属性的相关路径”在语义和长度上都与现象相符，是值得优先排查的具体候选。但现有异常尾部连头部也是零，不能称为已成功发送这个合法属性。更具体的假设是：长度预估先计入 23，序列化却未执行、提前返回、内容被覆盖，或最终发送长度没有按实际写入结果回退，导致零初始化区域被带到空口。

这是候选解释，不是已从固件源码或反汇编证实的事实。仅长度相同不能排除其他 23 字节保留区。也不能认定合法属性的 BSSID/MAC 必须非零：本轮真实对照帧这两项均为零，但头部和角色字段有效。

## 本地原包复核

共同根目录 `/Users/mxj/workspace/nan-capture-analysis/`。原始空口文件不入 Git。

| 文件 / 帧 | SDA 类型 | 检查结果 |
| --- | --- | --- |
| `2026-11-18/09-sta-connected-ch6.pcap` / 99373 | Publish，a0807d7 | 记录 549 字节；最后合法属性后，从记录偏移 526 开始额外 23 个零，无合法 0x1e 属性；同源共 76 帧如此 |
| `2026-11-19/303fdedc-sta/02-sta-off-on-ch6.pcap` / 33004 | Publish，303fdedc | 记录 566 字节；最后合法属性后，从偏移 543 开始额外 23 个零，无合法 0x1e；同源异常共 100 帧，含不同 Setup 测试阶段 |
| 同上 / 141 | Subscribe | 记录 346 字节；最后合法属性后，从偏移 323 开始额外 23 个零；该发送地址共 13 帧如此 |
| 同上 / 33305 | Subscribe | 同为 346 字节，偏移 323 后额外 23 个零；该发送地址共 116 帧如此 |
| 同上 / 522 | Subscribe，另一发送地址 | 存在真实 Extended WLAN Infrastructure 属性：`1e 14 00`，内容 20、共 23 字节，角色为 1，BSSID/MAC 为零 |
| `2026-11-19/b2302624-sta/06-lenovo-internet-ch6.pcap` / 11745 | Publish，b2302624 | 记录 469 字节，属性边界完整，没有额外零尾；共 47 个目标 Publish 均无零尾 |

上述 141、33305、33004、522 帧用本机 Wireshark tshark 复核：SDA 类型分别为 Subscribe/Subscribe/Publish/Subscribe，捕获长度等于原始长度，radiotap 标记 FCS-present=false。Subscribe 异常两组地址尚未与当时设备日志建立物理设备映射，不能仅靠随机地址认定它们就是指定平板，也不把本轮称为重新验证了 Android 订阅角色的 STA 开/关实验。用户所述 Android Subscribe 现象单独保留为其观察。

零尾位于最后一个合法 Vendor Specific 属性之外；不是该属性内容自带的零字节，也不是服务 TXT、SDEA 设备信息或四字节 FCS。Wireshark 会将零尾部分误解为多个 ID=0/Length=0 的属性。

## 与已有证据的关系

此前同机 STA 断开→连接→断开可在不重新下发 Publish 的窗口内改变零尾，HAL 两份 Publish 请求的业务 payload 完全相同。此次候选符合这些事实：基础设施并发元数据可能在服务配置之后动态加入，共用 builder 也可能影响 Publish 和 Subscribe。但 HAL 的其他配置、驱动/WMI 参数、实际最终组帧模块仍未完全隔离，不能把候选升级为 Firmware 已确认缺陷。

TB522FU 和旧 Pixel 9 对照未复现，说明不是所有设备 STA 并发必然如此；没有定位到具体固件版本或修复提交。[既有驱动证据边界](303fdedc-sta-sdf-driver-analysis.md)及[三台属性对照](three-tablets-sta-publish-comparison.md)继续有效。

## 后续源码或固件静态分析重点

先定位实际 WLAN 固件文件、芯片架构及镜像格式。不能仅根据 `amss.bin` 文件名确定它属于哪个处理器或对应正在运行的 WLAN 固件。反汇编受符号剥离、压缩/加密、地址映射和工具架构支持限制。

在实际实现中查找 Extended WLAN Infrastructure / EXT_WLAN_INFRA / non-NAN infrastructure 的属性生成与长度统计路径；常量 0x1e、20、23 只能作为辅助定位线索，不能单独当作函数证据。重点关联 STA connected 状态与 Publish/Subscribe 共用 SDF builder。

建议在实际路径输出以下非敏感信息：STA 状态、属性是否启用、预估长度、属性添加函数返回值/实际写入长度、添加前后 cursor、最终有效帧长、提交发送长度。不要 dump 包含 NIK/NPK 的整个安全属性。

候选缺陷的关键判据是：预估已加 23，添加前后 cursor 差却为 0，最终发送长度仍包含该 23 字节；或实际写过 `1e 14 00`，但发送前/空口变为零。前者查构帧/错误回退，后者查覆盖、提交与 DMA 路径。只有获得这些边界证据后，才能确定责任层和修复位置。

当前不修改 App、不在抓包文件中裁零，也不盲目向固件补 23 字节。优先修复真实组帧路径长度与内容一致性，再回归 STA 开/关、发布/订阅、首次配对、缓存验证和 NDP。
