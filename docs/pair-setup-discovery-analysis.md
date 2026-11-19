# PairSetup 关闭后已配对设备发现超时：空口对照

日期：2026-11-16。问题索引：[P19](problem-log.md)。本次没有修改 App 协议或绕过配对开关，使用提交 9b4a51c 的双端版本继续分析。

## 结论与边界

**2026-11-18 更新：已取得 iOS 明确丢弃日志和 HAL 请求 dump。** Setup=false 时 wifip2pd 报 `missing CSIA GTKIGTKBIGTKSupported attribute`；HAL 下发前缺少 0x27 capability TLV，高通入口仅在 Setup=true 时将零能力补成 4。以下是 11 月 16 日历史判断；最新定位、字段转换缺口及待 ROM 验证的修改见 [HAL/空口专项定位](vendor-publish-csia-sta-analysis.md)。仍未部署单独修复 capability 的版本，不宣称修复验收通过。

已确认一个报文层面的不一致：Android PairSetup=false 时，Publish SDF 的 **SDEA 仍要求 GTK，但 CSIA 声明不支持 GTK/IGTK/BIGTK，且缺少 GTK cipher suite ID 5**；只开启 PairSetup 后，CSIA 改为支持这些组密钥，并增加 ID 5，iPhone 随即发现端点并建立链路。信道 6 与 149 两次受控对照得到相同结果。

这是当前优先排查的 ROM/HAL/固件能力声明问题，**尚未证明 Apple 的实际过滤分支必定就是这个字段**。开启 PairSetup 同时会更新其它内容，例如 Service Update Indicator；仅凭相关性不能排除更新指示、身份校验或其它因素。本次没有修改固件、单独补齐 GTK 声明做因果对照，也没有 Pixel 抓包，因此不宣称根因修复或所有 Android 都有该问题。

## 环境与步骤

- Android：USB a0807d7，TB323FU / Android 17；普通 Wi-Fi 未连接，定位开启，AwareBridge 在前台。两端已有配对缓存，不删除凭据。
- iOS：USB iPhone 12 / iOS 27.0，AwareBridge；通过 USB HID 实际点击连接/断开，由助手独立控制双方，避免人工交叉操作。
- Ubuntu：192.168.31.211，wlp3s0mon。重启后的 monitor 接口由用户执行准备脚本恢复；SSH 使用已保存的重装后主机别名严格校验密钥。每次固定一个信道，两个信道不是同时采集。
- 每轮确认 Android 已发布、配对开关关闭，iPhone 点击连接并停留发现阶段；随后保持同一次 Publish 会话，只通过 UI 开启配对窗口。观察端点发现、连接与双向计数。

信道 6：关闭状态截图显示“正在发现所选设备”，0/0；开启后截图显示已连接，7/6。信道 149 的有效第二轮也在关闭状态停留发现，开启后连接并达到 9/9。后续窗口自动关闭，两端均达到 77/77，iPhone 最近接收 0 秒，证明已有链路可以继续。

信道 149 的第一轮不作为能力对照：iOS 断开后 Android 的原有心跳超时会结束发布，随后平板自动息屏。该轮部分时间没有有效发布，已排除；有效第二轮重新唤醒、确认发布后开始，不将无会话误写成被 iOS 过滤。

## 文件与代表帧

原始抓包含身份解析及协商材料，仅本地保留，不上传 GitHub。

| 项目 | 信道 6 | 信道 149（有效第二轮） |
| --- | --- | --- |
| Ubuntu 文件 | /var/tmp/nan-capture/ch6-20261116-135057-8719.pcap | /var/tmp/nan-capture/ch149-20261116-135814-9067.pcap |
| Mac 副本 | /tmp/nan-pair-toggle-ch6.pcap | /tmp/nan-pair-toggle-ch149.pcap |
| 关闭代表帧 | 31871，t=29.466527 s | 13525，t=2.598685 s |
| 开启代表帧 | 68261，t=63.548682 s | 198292，t=42.439572 s |
| 关闭状态 Publish 数 | 53 | 66 |
| 开启状态 Publish 数 | 1 | 2 |

计数是当前文件中按服务描述类型与相应字段组合统计的样本数；开启后 iOS 很快结束订阅并建立链路，因此 Publish 样本少，不是对发布频率/丢包率的测量。

免密抓包 wrapper 在定时停止后返回 137，未输出 tcpdump 的最终丢包统计；两份文件已成功落盘、改为 mxj 可读，并由 tshark 正常解析。它们可证明所列帧确实存在，不能证明抓到了该时段全部报文。

SHA-256：

- 信道 6：693d9481c1f53892b9e4b689f911a85bda26c94ff0ce7956901c032801b28cba
- 信道 149：fee49ef2f75a4f20a5c04c4c3c92650126ad5f5ff7c3aaee5136c72fa4f9c9db

## 属性逐项对照

以下字节仅为非密钥的能力字段；不列出 NIRA Nonce/Tag、PMKID、配对密钥。

| 属性 | Setup=false | Setup=true | 解释 |
| --- | --- | --- | --- |
| Service ID | 9a:a7:d1:b3:28:3c | 相同 | 对应本项目服务；不是切换到另一个服务 |
| SDA 类型 | Publish | Publish | 信道 149 两代表帧的完整 SDA 相同 |
| DCEA Capability Info | 0x0305 | 0x0305 | 全局声明并未随应用开关逐位变化，不能把 UI 状态直接等同此字段 |
| SDEA Control | 0x0440 | 0x0640 | 两者 Security Required、GTK Required 均置位；开启样本额外带 Service Update Indicator |
| CSIA payload | 00 07 01 | 04 05 01 07 01 | 首字节能力，后续为 cipher ID / publish instance ID 对 |
| CSIA 组密钥能力子字段 | 0：不支持 GTKSA/IGTKSA/BIGTKSA | 2：支持这些 SA | 差异位于 capability 字节的 bits 1–2 |
| Cipher IDs | 7 | 5、7 | ID 5 为 GTK CCMP-128；ID 7 为 PK PASN-128 |
| NIRA | 存在，payload 长 17，cipher version=0 | 同样存在 | 存在不代表其 Tag 与对端缓存匹配，尚未进行密码学校验 |
| SDEA Service Info Length | 90（信道 6） | 90（信道 6） | 没有通过修改 TXT 长度恢复连接 |

字段依据：上游 hostap 的 [nan_defs.h](https://raw.githubusercontent.com/freebsd/freebsd-src/main/contrib/wpa/src/common/nan_defs.h) 中 `NAN_SDEA_CTRL_GTK_REQ = BIT(10)`、`nan_cipher_suite_id` 与 CSIA group capability 定义；[Wireshark NAN dissector](https://raw.githubusercontent.com/wireshark/wireshark/master/epan/dissectors/packet-wifi-nan.c) 用于解析帧和能力字段。本机 Wireshark 没有完整显示较新的 SDEA bit 10，因此不能把 0x0400 当作 PairSetup 位或无意义保留位。

信道 149 代表帧的属性序列均为：15、42、18、18、3、14、34、35、11、44、43、221、221。关闭帧的最后一个属性恰好结束于抓包字节偏移 527，记录长度也为 527；开启帧相应值均为 530（长度包括 radiotap）。这两帧没有此前普通 Wi-Fi 并发问题中的额外 23 字节尾部。不能据代表帧推断其它固件/所有报文均无此问题。

NIRA 在两帧之间字节不同，但不同时间的 Nonce/Tag 本来可能变化，不能据此认定开关改变了身份密钥，亦不能认定身份解析已通过。

## ROM 侧下一步

1. 核对 App → framework → HAL → 固件中 enablePairingSetup / enablePairingVerification / enablePairingCache 与 GTK 能力构帧之间的关联。关闭新配对不应仅因该操作产生“要求 GTK 但声明不支持”的组合。
2. 对照 CSIA：关闭状态为何为 00 07 01，而开启后为 04 05 01 07 01；检查是否在 setup=false 分支错误清除了 GTK capability / cipher，而 SDEA GTK Required 没有同步。
3. 保留 Setup=false，只修正/独立控制相应 GTK 声明后重新测试 iOS 发现，才能确认该矛盾是否为实际过滤原因；同时排除单纯 updatePublish / Service Update Indicator 刷新导致恢复。
4. 补充 Pixel 同配置的开/关抓包，比较是否也有该组合；本轮没有 USB Pixel，不能把此前 Pixel 的普通 Wi-Fi 并发正常结果当作本问题的对照。
5. 必要时结合 iOS 身份解析日志核验 NIRA 与缓存匹配，现有属性存在性不足以排除身份校验问题。

App 继续保留用户要求的默认关闭及一分钟窗口；暂时打开窗口连接仍只是绕过。测试结束已由 USB 断开 iOS、停止 Android 发布，iPhone 主 LCD 与全局背光均为 off，lockState 返回 passcodeRequired=true，已锁屏。

## Apple 原版 Demo 的后续对照

同日已取得原版 Host 普通监听与“+”配对页的 Publish：SDEA 均为 0x0644，CSIA 均为 `04 07 01 05 01`，同时要求并支持 GTK；DCEA 为 0x0200 / 0x0300，Pairing Enable 位随状态变化。它没有出现本报告中 Android Setup=false 的 GTK 声明矛盾。完整方法、帧号及未成功的补充复验边界见 [Apple 原版对照](apple-original-publish-comparison.md)。这仍不能替代 Android 单字段固件修复及 iOS 过滤分支的因果验证。

## 新建 Publish 与 updatePublish 的区别核对

2026-11-16 补充核对：并非所有关闭状态样本都来自 updatePublish。信道 149 有效第二轮之前，旧会话已因停止收发后的心跳超时清理；唤醒平板后 UI 为“开始发布”，配对开关关闭。点击开始发布后 UI 显示 UDP 40813、按钮“停止”、配对开关仍关闭。代码此时走 `aware.publish(publishConfig(false), ...)`，不是用 updatePublish 将旧会话改为关闭。该轮抓包首个代表 Publish（13525）依然是 SDEA=0x0440、CSIA cipher=7、group-key support=0，iPhone 仍停留发现。之后开启窗口才恢复 GTK 声明并连接。

因此，新建 Setup=false 的 Publish 也能复现，现有证据不支持“仅 updatePublish 更新遗留状态，重建后就正常”。这个测试包含完整 Aware 资源清理后重新启动；尚未单独实现“保留原 attach/网络请求，只 close PublishDiscoverySession 再创建 Publish”的替代关闭分支，不将两种生命周期操作混为完全相同。配对缓存保留，未通过删除凭据改变条件。本节是对现有记录及本机 XML/抓包的复核，不是新增真机重测。


### P19：2026-11-19 Publish 修复通过当前组合验证

用户确认前一候选库误改 Subscribe，本次改正 Publish。新库 SHA-256 `0cd80cb19390ebb38cf46f982359bf53545e8f2ed386a244550c782237c6a546` 已部署并重启、确认实际加载。默认 Setup=false 时 HAL 0x27=04，空口 CSIA=04 07 01，iPhone 缓存 Pair Verify、NDP 和双向通信通过。开关 true→一分钟自动 false 均保留 capability=4，链路持续到 48/48；随后重新 Publish(false) 也再次连接成功。P19 的当前硬件/ROM正常路径修复已验证，非首次清缓存配对、非跨机型全面验收；STA 23 字节 P07 未因此解决。另观察 iOS 主动断开后 App 心跳超时会停止 Publish，重连需先重新发布，避免与 CSIA 过滤混淆。详见 [完整部署和证据](csia-publish-fix-validation-2026-11-19.md)。
