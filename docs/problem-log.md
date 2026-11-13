# 问题与解决记录

更新：2026-11-13。本文件是 NAN-iOS-Android 的统一问题台账，涵盖新项目调试及直接影响实现的原 wifi_aware 项目历史。后续遇到的问题必须持续追加；已解决的问题保留，不覆盖失败证据。原项目提交号用于追溯，未导入本仓库 Git 历史。

状态含义：**已验证**＝列出的条件下实际通过；**已处理、待复现**＝代码已改但失败分支尚未重新触发；**待定位**＝现象有证据，根因未确定；**历史记录**＝依据旧提交，不代表当前设备重新验证。

## 问题索引

| 编号 | 问题 | 当前状态 |
| --- | --- | --- |
| P01 | SDK 缺少编译接口、framework-wifi.jar 与 ROM 权限混淆 | 编译已验证，ROM 仍有条件 |
| P02 | instant mode 不支持时调用、部分平台启用配对导致发布失败 | 历史记录，需逐平台适配 |
| P03 | 服务名相同，iOS 仍不显示 Android | SSI 格式已修正并验证 |
| P04 | 设备名写死、中文名称和字节长度 | 已验证 |
| P05 | iOS 要求 PIN，Android 没有显示 | 已验证 |
| P06 | 旧配对缓存干扰、如何删除 | 已完成测试缓存清理 |
| P07 | TB323FU 连接普通 Wi-Fi 后无法发现、Publish 尾部异常 | 待 ROM 定位；断开 STA 可恢复 |
| P08 | 已配对但 iOS 一直 Connecting，Android 有 IP | 端口/协议及数据路径已验证 |
| P09 | iOS Network.NWError -11987 | 重连已成功；底层原因待定位；自动重试分支待复现 |
| P10 | NDP 建立后应用数据协议不一致 | raw UDP JSON 已验证 |
| P11 | 空闲掉线、断开后 Android 仍保留旧会话 | 心跳和超时清理已验证 |
| P12 | iOS 真机签名、USB 触控、重启后设备消失 | 签名/触控已验证；锁屏尚未确认 |
| P13 | Ubuntu SSH 主机密钥与抓包权限 | 专用主机记录和受限 sudo 抓包可用 |
| P14 | 抓包缺少 Host Publish，误把未抓到当成未发送 | 已调整证据判断方法 |

## P01：编译接口、jar 和 ROM 是三个层面

**现象/依据：** 原项目 `50c1467` 引入 AOSP `framework-wifi.jar`，同时配置 `compileOnly` 和 javac bootstrap classpath。Android SDK stub 中没有当前实验所需的全部接口，单纯添加普通依赖也可能仍先解析 SDK stub。当前 jar 的 class file 版本为 65，需要 JDK 21 读取。

**处理：** 新项目保留 jar 作为编译依赖，使用 JDK 21，并配置编译解析顺序；不打进 APK，不替换设备 Wi-Fi 模块。原项目 `6e6b374` 另有编译修正，不能把所有编译问题都归因于 jar 缺失。

**验证/限制：** Android assemble/lint 通过；当前测试 ROM 可发布、配对和建立 NDP。修改 jar 的可见性只解决编译，普通 ROM 仍可能因隐藏 API、权限、HAL 能力而运行失败。当前 `setVendorData` 不作为通用公开 SDK 能力宣传。详见 [ROM 条件](android-rom.md) 和 [第三方说明](../THIRD_PARTY_NOTICES.md)。

## P02：能力探测及平台差异

**历史证据：** `64813cd` 记录调用 `setInstantCommunicationModeEnabled` 前应检查支持，否则出现 panic；`e460f29` 记录某高通平台启用配对后 firmware 异常、无法发布。

**处理：** 不把某一版本存在 API 当成每台设备都支持；检查 Aware/配对能力，保留错误提示，按 ROM 分析发布失败。服务重启、资源被占用、固件退出等都可能导致 session terminated，不能只看 `AwareSessionTerminated` 名称就断定单一原因。

**状态：** 历史风险记录。本轮 TB323FU/Pixel 对照不等于所有高通平台固件缺陷已修复，也没有为最早的 session terminated 建立唯一根因。

## P03：服务名相同不足以保证 iOS DevicePicker 显示

**现象：** 原项目 `58d2a7c` 将发布/订阅服务名改成与 iOS 相同后仍不能发现。随后检查 Apple 示例所需的服务元数据、发布模式和实际 Publish SDF。

**证据/处理：** 原项目 `7595044` 记录修正长度多算 3 字节及 TXT 后附加数据后 iOS 可发现。提交文字写“SSID”，此处实际是 **SSI（Service Specific Information）**，不是接入点 SSID。当前编码为 `04 + uint16LE(TXT字节数) + TXT`，长度不含这 3 字节头；不再追加未经确认的 TXT 尾部数据。发布使用 solicited，正式服务名 `_aware-bridge._udp`。

**验证：** iOS 已显示 Android 设备并完成配对。设备可见性还受 P07 的 ROM 报文异常影响，不能用 SSI 修正解释后来所有发现失败。

## P04：名称从设备读取及 UTF-8 长度

**现象：** 调试时 modelName、pairingName、vendorName 曾写死；名称过长或中文按字符数而非字节计长会破坏 TXT。

**处理：** `modelName=Build.MODEL`，`pairingName=Settings.Global.device_name`（缺失回退 MODEL），`vendorName=Build.MANUFACTURER`。每条 TXT 使用 1 字节长度，值按 UTF-8 字节限制并在完整码点处截断。原项目 `3126803` 是设备信息读取的历史依据。

**验证：** 最终版本 iOS 显示平板中文系统名称。短名称只是诊断变量，未作为解决 STA 并发异常的最终改动。

## P05：配对时 Android 不显示代码

**现象：** iOS 系统界面要求输入 Android 显示的代码，旧 App 没有对应显示。

**处理：** 支持 PIN Display 引导，在配对回调中维护同一 peer 的 6 位随机 PIN，提交配对接受所需的 PIN，并在 UI 展示；成功/失败/超时清理。原项目 `80dfcf2`、`b6804b3`、`3126803` 记录回调、弹框与修正过程。

**验证：** iPhone ↔ TB323FU 系统 PIN 配对成功，后续缓存配对验证成功。PIN 是配对验证码，不是本演示数据路径固定 passphrase；不能混用。

## P06：系统配对存储与旧别名

**现象：** 重装/换 App 后旧别名可能仍在系统存储，不能靠停止发布或清空 UI 判断已解除配对。

**证据：** 当前平板系统存储位于 `/data/misc_ce/0/apexdata/com.android.wifi/WifiConfigStoreAware.xml`，包含按应用划分的别名/NIK/安全信息映射。此路径为当前 ROM 观察结果，不保证所有 ROM 相同。

**处理/验证：** 优先使用 `getPairedDevices`、`removePairedDevice(alias)`、`resetPairedDevices()` 管理调用应用自己的配对。测试中以旧 App 身份删除 aware-100 并确认映射已清理；之后新 App 重新配对成功。`cmd wifiaware reset` 不等于解除配对。临时 ADB root 已恢复为 shell，不提交含密钥 XML 或完整安全日志。

## P07：TB323FU STA/NAN 并发异常，Pixel 对照正常

**现象：** 平板连接普通 Wi-Fi 时，旧/新 App 均曾无法被 iOS 发现；用户断开普通 Wi-Fi、保持 Wi-Fi 开关开启后恢复。Pixel 9 连接普通 Wi-Fi 时旧/新 App 可发现。

**关键证据：** 同一份 6 信道抓包中，TB323FU 70 个 Publish 在完整属性之后均多出 23 个零字节，Pixel 32 个 Publish 属性边界完整。平板代表帧 530 的 NAN body 偏移 391–413 为额外数据，对应完整抓包偏移 471–493；最后一个有效 VSA 自身的 7 个零不计入额外 23 字节。Wireshark 将尾部拆成多个无效 Master Indication 属性。用户另行抓包确认断开 STA 后额外尾部消失。

**处理/状态：** 当前测试临时断开平板 STA；用户将在 ROM 流程继续分析，应用未修复固件。不能推出“所有设备连接 Wi-Fi 都不能用 Aware”，也不能证明 23 字节是唯一原因。详细原始十六进制、长度、旧抓包 3、对照条件与待验证点见 [专门记录](tb323fu-sta-nan-issue.md) 和 [抓包对比](packet-comparison-2026-11-13.md)。

## P08：配对成功 / 有 IP 不等于应用连接完成

**现象：** iOS 点击 Discovery & Connect 一直 Connecting，Android 已显示 IP。系统配对、NDP、端口发现、socket 首包是不同阶段。

**处理：** Android publisher 注册 responder 的 Aware 网络请求，配置已验证的加密参数，并通过 network specifier 声明监听端口及 UDP 协议号 17；socket 绑定 Aware Network。发布端可用 PublishDiscoverySession 接受连接，不要求先收发发现消息取得 PeerHandle（原项目 `fb4cbe1`）；原项目 `f10c105` 记录完整链路成功。

**验证：** 本轮通过 NDP、IPv6 link-local 和 UDP 双向数据。服务名用于发现，端口供应用定位 socket；NDP 是 IP 链路，后续可另行协商 TCP 端口，当前尚未测试 TCP。

## P09：-11987、首次 NDP 协商失败与有限重试

**证据：** 10:31 iOS Pair Verify 已 authenticated，随后 NDP early termination、报 -11987；Android response 1 / driver reason 9010 / channels 0 / 无有效 NDI，未进入 UDP。`network request not found for ndpId` 不能独立证明 App 没注册请求。后续未改变服务/口令/协议，10:40 重连成功。

**处理：** iOS 将 pre-ready 的 waiting(-11987) 结束为当前尝试错误，释放旧任务后再浏览同一设备并重连；总计最多 3 次，间隔 2 秒。按连接标识隔离迟到回调，用户断开会取消重试，ready 后不自动替换会话。

**验证/限制：** 正常重连和连续双向收发已验证；修改后没有再次触发 -11987，自动重试失败分支未完成真机复现。底层根因仍未解决。详见 [错误与恢复记录](ndp-connection-recovery.md)，不把泛化错误码直接解释为端口错误。

## P10：链路正常但应用载荷不能互解

**风险：** Apple 示例的 Coder/TLV 编解码层与 Android 原始 JSON socket 不是相同线格式；NDP ready 不能代替应用层互通验证。

**处理：** 两端统一 raw UDP JSON v1，包含 platform、session、seq、timestamp、text、ackSession；ACK 不再回复 ACK，限制报文大小并检查会话。iOS 不沿用示例 Coder/TLV 封装。

**验证：** Swift 报文样本检查通过，覆盖 Android 格式、中文、ACK、非法版本和负序号；最终真机双方收到对方 heartbeat/ACK，计数持续增长。详见 [协议](protocol.md)。

## P11：定时心跳与旧链路清理

**现象：** 链路可能因无数据被系统拆除；实测 iOS 停止后 Android 系统 NDP 已空，App 却未收到预期 onLost，仍保留旧 peer/session 并发送。

**处理：** 每 5 秒双向心跳和 ACK；Android 收到首个合法数据后，超过 20 秒无有效接收，在下一次定时检查中释放 socket、timer、网络请求、发布会话并要求重新发布。尚未收到首包的空闲发布不按这个规则误超时。

**真机验证：** 最终两端版本连续运行约 2 分钟，iOS 47/47、Android 48/48，最近接收 0 秒。10:59:06 iOS 停止后，Android 显示“心跳超时，连接已释放；请重新发布并连接”；系统 mClients、Active NDPs、mUsedNdis、mNetworkRequestsCache 均为空。心跳不能保证后台存活，当前退后台主动停止，不宣传后台保活能力。

## P12：签名与 USB 自动操作的边界

**现象/处理：** 无签名编译成功不能直接安装 iPhone；本机使用已有带 Subscribe entitlement 的 profile/Bundle ID 做签名安装，仓库保留独立 ID 和本地配置模板，不上传签名资料。iPhone 重启后曾从 USB 列表消失，经用户重新插拔、开机后恢复。

**验证：** USB 已实际控制 DevicePicker、连接和断开，并截图核对，非仅启动 App。系统 PIN 与配对流程已真机通过。

**未完成：** 用户要求测试后锁屏。尝试 USB Consumer Power/Sleep 事件后，设备仍报告 backlightState activeOn，不能声称已经锁屏；CoreDevice 锁定状态查询在本设备未实现。AssistiveTouch 诊断开关已恢复原值。最终锁屏须以屏幕关闭的实际检查或用户实体按键确认为准。

## P13：Ubuntu 访问与抓包权限

**现象：** SSH 旧 known_hosts 与当前 ED25519 密钥不一致；保存抓包可读，Wireshark root 临时抓包不可读；monitor 接口抓包/换信道需要 sudo。

**处理：** 使用已核对指纹的专用 known_hosts 记录和现有 SSH 密钥，不关闭主机身份检查。生成受限抓包脚本，由用户执行配置，仅允许固定 wlp3s0mon、限定信道和时长，输出抓包授权 mxj 读取，不授予账号任意免密 root。

**验证/限制：** 6/149 信道自动抓包可用。包装命令曾返回 137（超时发送 INT 后仍需 KILL），保存的 pcap 可解析、权限已落地；不能把退出码当作抓包权限失败，也不能把可解析等同每个窗口完整覆盖发现过程。该退出行为尚未另行修正。

## P14：抓包窗口与 NAN 属性判断

**现象：** 最初若干窗口 iOS Host 只有 Discovery Beacon，看不到 Publish SDF，引发“Host 如何公布服务”的疑问。之后用户在 6 信道抓包 4 中实际找到 Host Publish。

**处理：** 按 MAC、帧类别、信道、时间窗口分析；Beacon 不能代替服务描述属性，也不因仅看到 Beacon 就断定 Host 从不发送 Publish 或 Beacon 必含 SDEA。149 信道能看到 NAN 不表示所有服务发现帧均在该信道。对照包须保留发送者、目的地址、请求实例、属性边界；当前 Pixel/TB 对照并非同一个 iOS Subscribe 的响应对。

**验证/限制：** 已对旧包 3 与新抓包逐属性比较；部分后续窗口只有 Beacon、没有 Publish，不能拿它们独立证明尾部异常已消失。诊断坚持区分用户确认、实测帧证据和待验证推测。

## 每次后续排查必须记录

每个新问题追加编号，并至少填写：日期/设备与系统/网络状态/代码版本、复现步骤、预期和实际表现、相关回调/日志/帧号及字段、已排除项、根因或推测、修改文件或配置、验证方法与结果、残留风险及后续动作。涉及旧问题时更新原条目并保留历史，专项长分析另建文档并在这里链接。

禁止把“编译通过”写成“真机通过”，把“重试恢复”写成“根因已修复”，或把“某窗口没抓到”写成“设备从未发送”。密钥、PIN、私钥、完整敏感日志和签名配置不进入公开仓库。

## P15：setVendorData 是否为互通必需项

日期：2026-11-13。当前配置继承旧项目 `OUI=0x506f9a`、bundle `android=false`。此前所有成功测试保留此项，未单独验证删除后的发现、首次配对、缓存配对重连及 NDP。

源码原注释称其 Required，超出了现有证据，现改为“从已验证配置保留，必要性尚未通过 A/B 测试隔离”。VendorData 是额外厂商参数；`android=false` 在当前厂商实现中的具体解释，不能凭字段名认定为伪装 iOS 或必需开关。也不能把它与 SSI 的 modelName/pairingName/vendorName，或抓包中的所有 Vendor Specific 属性视为同一项。

状态：待验证。不因本次讨论直接删除工作配置；应只移除该调用，保持 SSI、配对、服务名、加密参数不变，在 TB323FU（STA 断开）及 Pixel 9 对照发现、首次 PIN 配对、缓存重连、NDP 和双向心跳；结合厂商 HAL/固件对该 OUI/bundle 的处理，才能判定是否可永久删除。删除调用本身也不等于所有 framework-wifi.jar 编译依赖都已消除。

### P15 后续真机结果：已验证可删除

已删除调用并重新编译安装。TB323FU（STA 断开）与 iPhone 12 通过缓存重连；随后清除两端测试配对，重新发现、输入新 PIN、配对成功，并建立 NDP、双向收发心跳/ACK。保留上面的待验证描述作为历史，当前结论更新为这组设备/条件下不必设置 VendorData。中间一次配对超时也保留记录，详见 [完整验证](vendor-data-removal-validation.md)。
