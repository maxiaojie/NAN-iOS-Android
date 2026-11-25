# 问题与解决记录

P19 后续（2026-11-18 晚）：用户提供新的高通 libwifi-hal.so，部署及实际加载已确认，但 Setup=false 时能力声明仍缺失，开窗后才恢复。候选库保留原 Setup 门限；不是旧库未加载，尚待核对隔离树的具体修改和输出路径。本次不标为修复。详见 [候选库部署验证](csia-candidate-deployment-2026-11-18.md)。

最新进展（2026-11-18）：P19 已通过 iOS 明确日志确认 CSIA 组密钥能力缺失导致丢弃；HAL 下发前缺少 0x27，候选转换修复待 ROM 编译验收。P07 独立复现 STA 连接时 76 个 Publish 均额外 23 字节，断开后同一 NMI 的代表帧边界完整且双向 13/13；HAL Publish 请求边界完整，驱动/固件具体责任仍待定位。详见 [HAL dump、帧号及修改建议](vendor-publish-csia-sta-analysis.md)。

P13/P16 管理改进：用户执行一次安装后，助手已可免密执行固定 prepare/capture/stop/restore/status，脚本由 root 持有，不开放任意 sudo 命令。恢复时 NetworkManager 存在就绪延迟，助手自行重试后普通 Wi-Fi、默认路由、外网 HTTPS 200 已恢复。

更新：2026-11-16。本文件是 NAN-iOS-Android 的统一问题台账，涵盖新项目调试及直接影响实现的原 wifi_aware 项目历史。后续遇到的问题必须持续追加；已解决的问题保留，不覆盖失败证据。原项目提交号用于追溯，未导入本仓库 Git 历史。

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
| P07 | TB323FU 连接普通 Wi-Fi 后无法发现、Publish 尾部异常 | a0807d7 实验固件当前 STA 场景空口通过；其他角色/设备待验证 |
| P08 | 已配对但 iOS 一直 Connecting，Android 有 IP | 端口/协议及数据路径已验证 |
| P09 | iOS Network.NWError -11987 | 重连已成功；底层原因待定位；自动重试分支待复现 |
| P10 | NDP 建立后应用数据协议不一致 | raw UDP JSON 已验证 |
| P11 | 空闲掉线、断开后 Android 仍保留旧会话 | 心跳和超时清理已验证 |
| P12 | iOS 真机签名、USB 触控、重启后设备消失 | 签名/触控已验证；11 月 16 日 USB 锁屏已确认 |
| P13 | Ubuntu SSH 主机密钥与抓包权限 | 专用主机记录和受限 sudo 抓包可用 |
| P14 | 抓包缺少 Host Publish，误把未抓到当成未发送 | 已调整证据判断方法 |
| P15 | setVendorData 是否必需 | 已移除并通过配对/链路验证 |
| P16 | Ubuntu managed 模式连接后无法上网 | 路由与认证已诊断 |
| P17 | 定位关闭时点击发布看似无响应 | 已增加弹窗并真机验证 |
| P18 | 新设备配对开关及 1 分钟窗口 | 状态切换/超时已验证；关闭后的重连受 P19 阻塞 |
| P19 | 配对后停留在发现所选设备，随后 -11988 | 已确认 iOS 因 CSIA 组密钥能力缺失丢弃；HAL 下发前已缺 0x27，待 ROM 修复 |
| P20 | iOS 发布、Android 订阅时心跳正常但 iperf3 无法启动 | 已修复，a0807d7 / iPhone 12 连续五轮通过 |

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

**此前未完成：** 用户要求测试后锁屏。尝试 USB Consumer Power/Sleep 事件后，设备仍报告 backlightState activeOn，不能声称已经锁屏；CoreDevice 锁定状态查询在本设备未实现。AssistiveTouch 诊断开关已恢复原值。最终锁屏须以屏幕关闭的实际检查或用户实体按键确认为准。

2026-11-16 补充：完成双端测试后，先断开 iOS、停止 Android 发布。USB HID 的长按 Consumer Power 会打开 Siri 设置页，未配置 Siri；一次短按退出该页，再对 App 前台发送 Consumer Power（usage page 0x0c / usage 0x30，约 50 ms），主显示 LCD 与全局 backlightState 均变为 off。随后 devicectl 的 lockState 查询返回 passcodeRequired=true、unlockedSinceBoot=true，结合息屏确认设备已经锁定。此结果取代此前“尚未锁屏”的最终状态；没有改动自动锁定时长。

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

### P01 后续：删除 framework-wifi.jar

2026-11-13，在 P15 删除 setVendorData 并通过全新配对验证后，移除 compileOnly 依赖和 javac bootstrapClasspath 覆盖。使用标准 Android SDK 36 执行 `:app:clean :app:assembleDebug :app:lintDebug` 成功（43 个任务实际执行，非沿用旧 class），没有缺失接口。随后删除 jar 文件及无效 import，当前编译不再需要定制 AOSP 接口。原条目保留为历史原因；当前仍不能由“标准 SDK 编译通过”推出所有 ROM 的无线能力均通过。JDK21/source8 与旧版 AGP/D8 的兼容性提示仍存在，此次未扩大范围升级工具链。

删除 jar 的标准 SDK 构建 APK 已覆盖安装到 TB323FU；iPhone 12 缓存配对重连、NDP 及双向 UDP/ACK 实测通过，11:38 两端均发送/接收 23/23、最近接收 0 秒。没有为本项再次清除配对；全新 PIN 流程是在上一项删除 VendorData 的版本验证，不能混写为本项再次验证。证据：本机 `/tmp/aware-reverse/no-framework-jar-build.log`、`no-jar-result.png` / `.xml`。

## P16：Ubuntu 恢复 managed 并连上 Wi-Fi 后无法上网

2026-11-13。Ubuntu 192.168.31.211 无线 wlp3s0 已恢复 managed，连接 lenovo-internet，地址 10.114.202.145/21、网关 10.114.207.254、DNS 10.10.8.211。有线 enx0826ae3b55dd 保留 192.168.31.211/24，供 SSH 使用。

检查发现两个独立问题：默认有线路由 metric 20100 优先于 Wi-Fi 的 20600，`ip route get 223.5.5.5` 实际走 192.168.31.1；有线网关可 ping，但外网 ICMP 无响应、HTTPS TCP 失败。绑定 wlp3s0 的 HTTP 请求能收到响应，却是 Aruba portal 的 meta refresh（`cmd=redirect&arubalp=12345`），HTTPS 报主机名与证书不符，表明 Wi-Fi 尚处认证重定向状态。不能仅凭 ICMP 不通判断外网必然不可用，也不能把 HTTPS 校验关闭当成修复。

处理方案：已在 Ubuntu `/home/mxj/fix-ubuntu-internet.sh` 准备脚本，保存原有线设置，将有线连接 ipv4.never-default=yes、ipv4.ignore-auto-dns=yes，再 device reapply；不 down 接口、不重启 NetworkManager，保留局域网地址和 SSH 路由。当前 SSH 用户仅对抓包包装器免密 sudo，网络配置需要用户在 Ubuntu 执行脚本并输入 sudo 密码。之后在 Ubuntu 浏览器访问 HTTP 页面触发并完成企业 Wi-Fi 认证。

状态：诊断已完成，脚本已上传，尚待用户执行及网页认证；未宣称恢复上网。修复后需检查默认路由经 wlp3s0、SSH 仍可访问、DNS 和 HTTPS 正常。未修改 monitor 配置，也未停止或删除任何普通 Wi-Fi 保存网络。

## P17：a0807d7 点击开始发布未启动

日期：2026-11-16。设备 a0807d7（TB323FU / Android 17），标准 SDK 构建的 AwareBridge。Wi-Fi 已开启但未连接 AP，NEARBY_WIFI_DEVICES、粗略/精确定位权限均已授予，系统 `location_mode=0`。

复现：唤醒并打开 App，点击开始发布，状态文字为“请先在系统设置中打开定位服务”，按钮仍为开始发布。发布前 dumpsys wifiaware 的 mClients 为空，mCurrentAwareConfiguration 为 null，没有进入 attach/publish。

原因：MainActivity.start() 显式检查 LocationManager.isLocationEnabled()，关闭时设置状态文字并直接返回；不是按钮事件无效，也不是 HAL 发布失败。当前界面只更新文字，用户容易认为点击无反应。这里确认的是应用前置检查阻止启动，不据此证明本 ROM 在定位关闭时底层一定不能运行 Aware。

处理/验证：使用 `cmd location set-location-enabled true` 打开该设备系统定位总开关，再次点击。UI 显示“服务已发布，请在 iOS 上添加设备 / UDP 端口 46260”，按钮变为停止。已确认发布成功；本轮没有验证 iOS 发现、配对或数据通信。定位保持开启、App 保持发布，供继续测试。证据保存在本机 `/tmp/a0807d7-location-block.xml` 和 `/tmp/a0807d7-location-enabled.xml`，原始日志未提交。

### P17 后续：定位关闭时显示错误弹窗

2026-11-16。用户要求启动被定位检查拦截时明确提示错误。MainActivity 现在显示“无法开始发布”弹窗，说明系统定位服务未开启，并提供“取消”和“打开定位设置”。打开设置的 Activity 不存在时，捕获 ActivityNotFoundException 并以状态文字和 Toast 提示手动开启。使用可见错误提示，不抛出会导致应用崩溃的未捕获异常；返回 App 后仍需主动点击开始发布。

验证：assembleDebug 与 lintDebug 通过，APK 覆盖安装到 a0807d7。显式关闭 user 0 的定位开关并确认 location_mode=0，点击后 UI 层级确认弹窗标题、原因与两个按钮；点击“打开定位设置”确认进入“位置信息”页面且服务关闭。恢复定位开启，返回再次点击后发布成功，UDP 端口 58196。测试结束定位已恢复开启。设置入口缺失的兜底分支未真机触发，本项未重新验证 iOS 发现/配对/数据通信。证据仅保存在本机 /tmp/location-dialog.xml、/tmp/location-settings.xml、/tmp/location-restored.xml；不上传原始设备日志。

## P18：将新设备配对改为显式开启的 1 分钟窗口

日期：2026-11-16。此前每次 Publish 固定 PairingSetupEnabled=true，配对成功仅清除 PIN，且 PIN 超时为 120 秒。用户要求默认不允许新配对，由 UI 开关控制；成功或 1 分钟超时后关闭新配对，但不终止服务。

处理：初次发布 Setup=false，Verification=true、Cache=true 保留。增加“允许新设备配对（1 分钟）”开关，发布成功前禁用。开启、手动关闭、配对成功和窗口超时均通过同一 PublishDiscoverySession.updatePublish 更新完整配置，不重新 attach、不主动释放数据链路。60 秒从开启配置收到成功回调时计算，不因收到 PIN 请求延长；关闭时立即清除所有 PIN、撤销计时器，拒绝之后的新配对请求。配对失败允许在剩余窗口内重试。停止、后台退出及资源清理均移除计时器并重置/禁用开关；再次发布仍默认关闭。

状态同步：配置更新串行化，等待回调期间禁用开关，回调成功才确认最终状态，程序更新开关不重复触发监听。若关闭请求与更新重叠，保留最新目标并在回调后继续更新。配置失败或等待超过 10 秒则明确提示错误并释放会话，防止界面显示关闭而底层仍允许配对。保持现有 SSI、加密与 UDP 配置。已提交给系统的配对握手没有在此调用中强制取消；关闭 Setup 不等于撤销已完成配对或删除凭据，迟到的成功回调仍执行关闭动作。

编译验证：标准 SDK 的 assembleDebug / lintDebug 通过。安装到 a0807d7，初始未发布时开关关闭且禁用；发布后关闭且可操作。实测开启/关闭均收到成功回调对应 UI，手动关闭后仍显示“停止”，保持原发布会话。过程中现有 iOS 对端连入并收到有效心跳，Android 计数达到发送/接收 3/3；随后对端停止流量，原有心跳超时清理触发（8/4），该轮不能充当一分钟窗口超时验证。重新发布后进行后续窗口验证，过程中用户完成了配对；不能把这一轮当成未配对超时。后续对照窗口的超时证据见下文。

补充真机证据（以下时刻为 Android 自身时钟，不能与 Mac 日期直接混用）：11:56:32.207 开启配置成功；11:56:34 发生 Bootstrapping；11:56:39.213 收到 Pairing Confirm 和 Security Association，随后 11:56:39.225 调用 updatePublish，39.305 关闭配置成功，UI 开关关闭，仍为原 sessionId=7 / pubSubId=1。这一轮实际覆盖了真实配对成功触发关闭，并非仅静态检查。

之后为排查 P19，11:59:04.219 临时再次开启 Setup，12:00:04.325 自动更新为 Setup=false；两条 NativeApi 配置均保留 Cache=true / Verification=true，sessionId=7 / pubSubId=1 未重建。Android UI 开关随后确认关闭，UDP 仍持续增长，iPhone USB 截图也确认双向通信。此处约 60 秒关闭证明窗口计时分支生效（期间系统 Pair Verify 不应延长窗口）。本机证据 /tmp/pair-window-end-aware.txt、/tmp/pair-discovery-11988-filtered.log、/tmp/pair-window-latest.xml、/tmp/nan-iphone-before.png；原始日志包含隐私字段，不提交。

限制：没有在本轮清除两端全部缓存重新验收“完全无凭据”场景；不把系统 Pair Verify 混写成新 PIN 配对。配置失败/无回调兜底未故障注入。用户后续接入 iPhone USB，继续进行受控重连测试。

## P19：配对后 iOS 一直发现所选设备，并报告 -11988

日期：2026-11-16。用户在 P18 版本上配对后点击连接，界面停留“正在发现所选设备”；随后临时开启 Android PairSetup 对照，用户报告错误码 -11988。当前 Android a0807d7 普通 Wi-Fi 未连接；另一台 303fdedc 没有发布会话。不应直接套用 STA 并发尾部异常，也不能将用户报错直接归因于 PairSetup=false。

证据：初始配对后关闭 Setup 的 updatePublish 成功，发布仍存在但未见后续 NDP。临时打开 Setup 后，11:59:45 出现缓存凭据验证（requestType=1），11:59:46 收到 NDP 请求、响应成功，Confirm accept=true/reason=0，信道信息包含 5745 / 5260 MHz。Android 回应携带应用信息 appInfo.length=16。后续即使 12:00:04 自动关闭 Setup，Android 仍有双向 UDP/ACK 收发；iPhone 重新接入 USB 后截图显示“已连接 · 双向数据通信中”，计数 101/101，Android 后续达到 225/223、最近接收 0 秒。不能将这一成功路径解释成 -11988 已根治，也不能由现有链路存活推出关闭 Setup 时新建连接必然成功。

状态：继续验证关闭 Setup 状态下主动断开/重连，并获取 iOS 端具体错误上下文。原始数字 -11988 尚未从当前 SDK 的 WAError 映射或 iOS 日志核实含义，不沿用 -11987 的解释。Ubuntu 192.168.31.211 当前 SSH Host is down，暂时没有新增空口证据；不修改设备配对缓存来掩盖问题。

### P19 受控双端复现与错误码核实

用户接入 iPhone USB 后，停止双方人工交叉操作。通过 USB HID 实际点击 iPhone“断开”并截图确认；最终 Android 版本重新安装/发布，开关默认关闭，保留两端配对记录。iPhone 点击连接后长时间停留在发现阶段，Android UI 发送/接收 0/0。随后仅打开 Android 的配对开关（同一 Publish 会话 updatePublish），iPhone 随即发现端点、Pair Verify、建立 NDP，iPhone 截图 6/6；后续配对窗口自动关闭，双向数据继续。这次对照重复了此前用户报告的现象，故不再将“关闭后已有链路能存活”当作“关闭后可以新建连接”的证据。

iOS syslog 在 11:42:51～52 收到驱动 Discovery Result 通知，11:42:52.960 之后出现 resolvedIdentityUUID / Discovered Peers 0→1，再由 App 的 NetworkBrowser 返回端点；但未抓到对应空口属性，不能直接判断此前缺少 NIRA、NIRA 校验失败或 iOS 某个过滤条件。日志中的 `parse(event:)` 即使在成功流程也以 ERROR 级别出现，不能单凭级别认定解析失败。成功结果中的 `pairSetup=NO` 也不能直接等同于 Android `setPairingSetupEnabled`；不同层级的能力位/要求位需要结合抓包字段确认。

试验记录（均确认 Android UI 已发布，发现计数 0/0；未清理配对）：

| 对照 | 结果 | 最终保留 |
| --- | --- | --- |
| Setup=false，PIN Bootstrap、原 TXT、Solicited | iOS 发现失败；打开 Setup 后同次发现恢复 | 保留用户要求的默认关闭，记录兼容性限制 |
| Setup=false 时 Bootstrap 方法设为 0 | 仍发现失败 | 否，恢复原声明 |
| Setup=false 时不带 SSI/TXT | 仍发现失败 | 否，恢复原 SSI |
| Setup=false 时改为 Unsolicited | 仍发现失败 | 否，恢复 Solicited |
| iOS 改为系统直接发现所选设备 `.selected([targetDevice])` | 仍出现超时，未解决本问题 | 保留精确目标选择及错误诊断，正常连接路径另测 |

已通过真机调用 `NWError.wifiAware` 扩展解析，截图明确得到 `subscriberTimeout(WiFiAware.WAError.SubscriberTimeoutDetails()) (-11988: Wi-Fi Aware)`。因此该次 -11988 是**订阅/发现超时**，不是此前 -11987 的 NDP 失败，也不是 UDP 端口验证失败。iOS 现在给该分支显示“未发现所选设备的服务（发现超时，…）”，其它 Aware 错误显示 SDK 解析结果和原码，避免只显示 Network.NWError 数字。本次没有新增“遇到 -11988 自动打开 Android PairSetup”的隐式行为。

当前可复现的临时操作：在 Android 打开 1 分钟配对窗口，再从 iOS 连接；确认双向心跳后窗口自动关闭，已有链路可继续。它会暂时允许新增配对，不能宣传为“关闭配对状态下已配对设备重连通过”。代码仍严格按用户要求默认 Setup=false，不把广播能力偷改为 true、只在 UI 假装关闭。

剩余工作：对比 Setup 开/关的 Publish SDF，重点核对 DCEA、SDEA、NIRA/身份解析、配对缓存能力及完整属性边界，并与 Pixel 对照；当前不能确定是 Android HAL/固件构帧还是 iOS 过滤实现问题。Ubuntu SSH 当前 Host is down，已请求用户确认可连接地址。`/tmp/nan-ios-discovery-live.log` 含系统配对材料，仅本机保留，不提交。安全证据以本文的时序和布尔状态为准。受控测试中一轮覆盖安装后平板自动息屏、未实际发布的样本被排除；随后唤醒并确认 UI“服务已发布”后重新测试，避免把暂停误判为过滤。


最终构建复验（2026-11-16）：Android assembleDebug / lintDebug 与 iOS 真机签名构建均通过，并分别安装 a0807d7 与 iPhone 12。最终代码（含 iOS 精确选择目标及错误解释）在打开窗口后建立连接；Android 自身时钟 12:41:38.094 开启 Setup，12:42:38.203 自动关闭，关闭后 Android 发送/接收 38/38、最近接收 0 秒，iPhone 39/38、最近接收 2 秒。不同时间截图的计数差不用于推断丢包率。这验证了最终版本的窗口超时与既有链路保留，不改变上述关闭窗口重新发现仍失败的结论。测试结束已断开两端，并通过 USB 锁定 iPhone，见 P12。


2026-11-16 抓包机恢复开机后的补充：已使用本机保存的 `ideapad-ubuntu-reinstalled` 主机别名严格校验 SSH 密钥并以专用 SSH key 连通，未绕过主机校验或覆盖旧 IP 的密钥记录。检查发现重启后只有 managed 模式 wlp3s0（信道 52），wlp3s0mon 已不存在。免密 sudo 仅允许 `/usr/local/sbin/nan-capture`，该脚本要求 monitor 接口已经存在，因此目前不能开始抓包。已在 Ubuntu 准备 `/home/mxj/prepare-nan-monitor.sh`，请求用户 sudo 执行：释放普通 Wi-Fi、将 wlp3s0 交出给 monitor 抓包，保留有线 SSH；未扩大 sudo 权限。iPhone 仍锁定，需要用户解锁后继续双端复现。目前没有新增空口结果。


### P19 空口补充：GTK 能力声明不一致

2026-11-16 用户恢复抓包机并解锁 iPhone 后，已独立完成信道 6、149 对照。关闭 Setup 时 Android 确实回复 Publish，NIRA 存在；同会话开启后 iPhone 立即连接。两信道均发现：SDEA 关闭/开启分别为 0x0440 / 0x0640，均要求 GTK；CSIA 却从关闭时的 `00 07 01`（不支持组密钥、只有 PASN-128）变为开启时的 `04 05 01 07 01`（组密钥支持、增加 GTK CCMP-128）。这是当前最具体的报文层不一致，优先核查 ROM/HAL/固件的配置与构帧关联，但尚未独立修改该字段验证因果，不宣称确认了 Apple 的过滤分支。两代表帧属性边界完整，无多余 23 字节尾部。详细帧号、时序、字节、依据、失败样本排除及后续 ROM 检查点见 [空口分析](pair-setup-discovery-analysis.md)。本轮没有 Pixel 对照，没有代码修复；自动关窗后两端 77/77。已停止两端并再次核验 iPhone 锁屏。

### P19 Apple 原版 Demo 对照准备（待解锁继续）

2026-11-16 用户要求比较 Apple 原版 Host 的配对/非配对发布属性。已检查 `/Users/mxj/XcodeProjects/BuildingPeerToPeerApps`：业务源码无工作区修改，只有已有本机 Xcode 项目签名设置变更；原版服务为 `_sat-simulation._udp`。Host 的 DevicePairingView 用于新设备配对，NetworkListener 的 `.allPairedDevices` 用于已配对设备监听，两者必须分别实测，不能把仅进入 Host 或只见 Beacon 当作已取得非配对 Publish。原版真机签名构建通过，已临时覆盖安装同 Bundle ID 的测试 App；AwareBridge 原签名产物保留，测试结束恢复。启动被 iOS 明确拒绝：Locked / FBSOpenApplicationErrorDomain 7，需要用户解锁，已请求。Ubuntu monitor 可用。303fdedc 的旧 Android App 已验证可以主动订阅原版服务，随后停止；未进行新配对。当前尚无 Apple 原版新空口样本，不能宣称属性已对照或与 Android 一致。

### P19 Apple 原版对照已取得报文

用户解锁后，原版 Host 普通监听与配对页分别抓到 7 个 Publish SDF：SDEA 均为 0x0644、CSIA 均为 `04 07 01 05 01`（支持组密钥，PASN-128 与 GTK CCMP-128），DCEA 0x0200 / 0x0300。Apple 普通监听关闭新配对仍保留 GTK 支持，没有 Android Setup=false 的 GTK 矛盾。NIRA 两状态均存在。详见 [完整对照与样本限制](apple-original-publish-comparison.md)。早期 Android 订阅 App 会自动引导配对，用户指出只需比较 iPhone 后已停止；配对页有效文件没有同期 Android 发射。后续完全停用 Android、以及去掉自动引导的临时订阅版本均未补到新的 Publish，记录为未成功复验，不伪装单变量重复通过。303fdedc 已恢复测试前 APK、停止订阅；iPhone 已恢复 AwareBridge 并核验锁屏。原工程及新项目业务源码未改，本轮只有诊断文档更新。

### P16 monitor 模式的反向恢复脚本

2026-11-16 用户要求提供 `/home/mxj/prepare-nan-monitor.sh` 的完整逆向脚本。已生成仓库 `scripts/restore-normal-wifi.sh` 并上传 Ubuntu `/home/mxj/restore-normal-wifi.sh`：删除专用 wlp3s0mon，恢复/必要时重建 wlp3s0 的 managed 类型，启用接口、交回 NetworkManager，并尝试连接已保存的 Wi-Fi；不删除保存网络、不改有线配置。自动连接失败时明确提示接口已恢复、网络仍需手动选择。本机与 Ubuntu 的 `bash -n` 均通过。远程账号此前执行 nmcli 被权限拒绝，免密 sudo 仅允许抓包脚本，因此本次仅完成脚本部署与语法检查，等待用户在 Ubuntu 执行 `sudo bash /home/mxj/restore-normal-wifi.sh`；不宣称已恢复联网。

### 实机 HAL 库与 Publish 指针定位

2026-11-16 AOSP 源码环境补充：按用户要求，在 Ubuntu `192.168.31.211` 的 `/home/mxj/workspace/aosp-main` 下载完整 `main` 分支源码文件（浅克隆，不含完整 Git 历史）。Google 源直连超时，清华镜像 Git 拉取排队且首次初始化失败；改用中科大 `https://mirrors.ustc.edu.cn/aosp/platform/manifest.git` 后初始化成功。已启动独立于 SSH 的下载进程，首次检查 `.repo` 已有 58MB，状态 RUNNING，**此时尚未全部下载完成**。脚本 `/home/mxj/workspace/sync-aosp-main.sh` 使用 4 路并发、失败重试与文件锁；日志 `aosp-main-sync.log`、状态 `aosp-main-sync.status` 均在 `/home/mxj/workspace/`。成功后生成 `aosp-main-manifest.xml`，状态改为 COMPLETE；重跑脚本可续传。没有改 Ubuntu 全局 Git 配置。`main` 是用户指定分支，不代表与 TB323FU 私有 Android 17 固件完全对应，不能用上游源码直接代替设备私有实现证据。

2026-11-16：USB 检查 a0807d7，Wi-Fi HAL 进程实际加载 `/vendor/lib64/libwifi-hal.so`；初始化代码把 Publish 表项赋为 `nan_publish_request`，后者调用 `NanCommand::putNanPublish`。设备另有 `libwifi-hal-qcom.so`，但本次进程未加载。没有动态跟踪 dlopen 的实参，不能把该动态加载分支一定执行当作结论。详见 [库标识、表项偏移及调用地址](device-wifi-hal-library-analysis.md)。两个 ROM 问题仍未修复。

### 两个 ROM 排查主线与 Publish SDF 的组帧边界

2026-11-16 再次确认当前主要两项：P07 的 STA 并发额外尾部，与 P19 的 PairSetup=false 时 GTK 声明矛盾，触发条件不同，不应合并为同一个已证实根因。第一项是 TB323FU 特定条件下的抓包与发现失败对应关系，Pixel 原 STA 并发对照未出现；第二项在普通 Wi-Fi 未连接时也复现，Apple 原版普通监听仍正确声明 GTK。第二项尚未通过仅修复 CSIA 后重测确认 iOS 的实际过滤原因。

对于“Publish SDF 在哪里组帧”：本项目 AwareController 只构建 PublishConfig 和 SSI，调用 publish/updatePublish，没有拼接完整 802.11 SDF。已阅读当前上游 AOSP 源码，调用边界如下（仅作 ROM 源码导航，不保证与当前设备私有分支逐行相同）：

- `packages/modules/Wifi/service/java/com/android/server/wifi/aware/WifiAwareNativeApi.java`：publish 将配置传给 WifiNanIface。
- 同模块 `hal/WifiNanIfaceAidlImpl.java`：createNanPublishRequest 构造 HAL 请求，createAidlPairingConfig 逐项复制 Setup / Verification / Cache，随后调用 startPublishRequest。这是请求结构构造，不是完整空口 SDF 组帧。
- `hardware/interfaces/wifi/aidl/default/wifi_nan_iface.cpp`：startPublishRequestInternal 转换请求后调用 legacy HAL 的 nanPublishRequest。
- 同目录 `aidl_struct_util.cpp`：convertAidlNanPublishRequestToLegacy 拷贝 SSI 及长度、cipher_type、安全配置和 pairing 配置。此处需要检查参数/长度映射，但不能把它称作已找到具体 SDF 帧尾或 CSIA 的写入函数。
- 后续进入厂商 NAN HAL / 驱动 / 固件。在 NAN offload 实现中，完整管理帧通常由固件生成和调度；当前尚未取得设备对应厂商源码或固件实现，不能断言两个问题均发生在固件，也不能给出未经核对的最终组帧函数名。

本机只读 getprop 确认 a0807d7 的 ro.hardware=qcom、ro.board.platform=canoe。应从这台设备对应的高通 WLAN/NAN 实现继续追踪，而不是将别的平台路径直接套用。建议分别检查 STA 并发时属性总长度/尾部填充，以及 Setup=false 分支是否清除了 CSIA GTK capability/cipher 却保留 SDEA GTK Required；沿 framework → HAL → 驱动/固件逐层比较实际参数后再确定责任层。

上游源：[WifiAwareNativeApi](https://android.googlesource.com/platform/packages/modules/Wifi/+/refs/heads/main/service/java/com/android/server/wifi/aware/WifiAwareNativeApi.java)、[WifiNanIfaceAidlImpl](https://android.googlesource.com/platform/packages/modules/Wifi/+/refs/heads/main/service/java/com/android/server/wifi/hal/WifiNanIfaceAidlImpl.java)、[wifi_nan_iface.cpp](https://android.googlesource.com/platform/hardware/interfaces/+/refs/heads/main/wifi/aidl/default/wifi_nan_iface.cpp)、[aidl_struct_util.cpp](https://android.googlesource.com/platform/hardware/interfaces/+/refs/heads/main/wifi/aidl/default/aidl_struct_util.cpp)。本次无业务代码或设备配置修改。

### P19 重新 Publish 是否能绕过 GTK 矛盾

用户提出不用 updatePublish，结束旧 Publish 后以 Setup=false 新建。已复核信道 149 有效第二轮：原会话清理后 UI“开始发布”→重新开始、UDP 40813，初始配置走 publish(false)，尚未通过 updatePublish 关闭。帧 13525 仍为 SDEA=0x0440、CSIA 仅 ID 7、组密钥支持=0，iOS 仍发现失败。因此完整重建后的默认关闭也异常，不能仅归因于 updatePublish 残留。保留原 attach 只重建 Publish 的专门分支尚未单测；未新增该代码改动。详见 [已有样本复核](pair-setup-discovery-analysis.md#新建-publish-与-updatepublish-的区别核对)。

### AwareController 可读性整理

2026-11-16 用户要求整改同一行堆叠链式调用的写法。已统一 AwareController.java 中的 Builder 调用为每项独立一行，并展开压缩的回调、try/catch、同一行多条语句与资源清理块，增加方法间分隔。修改前后 Java 令牌序列（含字符串和注释）一致，仅调整空白/换行；assembleDebug、git diff --check 通过。本轮未安装真机，未变更配对或网络逻辑，不作为互通问题的修复。用户的未跟踪 publish.txt 未改动、未提交。

## P20：HA2DAQGD 安装应用与添加 Aware feature 声明

2026-11-16：用户要求将 Mac 下载目录的 `android.hardware.wifi.aware.xml` 写入 HA2DAQGD 的 `/vendor/etc/permissions/`，并安装本项目 APK。设备为 TB323FU、Android 16/API 36，固件 `Lenovo/TB323FU/TB323FU:16/BQ2A.250831.001-BP2A.250605.031.A3/02.126.251223W:user/release-keys`，不同于此前 Android 17 的 a0807d7。

- XML 内容是 `<feature name="android.hardware.wifi.aware" />`，源文件存在，设备目标文件原先不存在，`pm list features` 未声明 Aware。
- `adb root` 返回 `adbd cannot run as root in production builds`；直接 push 返回 `Read-only file system`，最终核对目标仍不存在。设备没有可用 su，`ro.debuggable=0`、Verified Boot 为 green、bootloader 为 locked；`adb remount` 也不可用。因此 XML 写入未完成，未进行解锁、刷机或清数据操作。需要可写 vendor 的工程固件/root 环境，或者在 ROM 构建中加入该声明后部署。
- 当前源码 `assembleDebug` 成功；`adb install -r` 返回 Success；`am start -W` 冷启动 Status ok。包名 `io.awarebridge.android`，版本 1.0/code 1。
- 本轮只确认 APK 安装与启动，没有验证该设备的 NAN HAL 支持、发现、配对或 NDP；仅添加 feature XML 也不能作为底层能力已支持的证明。
- 用户要求继续实际执行 root/remount 后，再次运行 `adb root` 仍被 production build 拒绝；`adb remount`、`adb disable-verity` 分别报告 remount、disable-verity 不存在；`mount -o remount,rw /vendor` 返回 `/dev/block/dm-10 not user mountable in fstab`，`su -c id` 返回 su 不存在。`/proc/mounts` 确认 vendor 为 ext4、ro，shell 仍 uid=2000。未取得 root、未关闭 verity、未修改分区；XML 仍不存在，APK 安装保留。不能通过重复普通 ADB 命令把当前锁定的 user 固件变为工程固件。

## P21：b2302624 添加 Aware 声明并安装 APK

2026-11-16：用户指定 b2302624（TB522FU，Android 16/API 36），固件 `Lenovo/TB522FU_PRC/TB522FU:16/BQ2A.250831.001/maxj9_377.2605201137C:userdebug/test-keys`。原先 vendor 中没有 Aware XML，系统 feature 列表也没有 Aware。

`adb root` 成功，`adb remount` 报告 Verity 已关闭、vendor remount RW 成功（同时有部分分区 overlayfs/未挂载分区提示，但最终返回 Remount succeeded）。已将 Mac 下载目录的 `android.hardware.wifi.aware.xml` 写入 `/vendor/etc/permissions/`，设置 root:root、0644、restorecon 后标签为 vendor_configs_file；两端 SHA-256 均为 `6370914090ba4f81ef1410da719b6b2df5389180a8757ac95e77ca88d0076e85`。

安装本项目当前 debug APK 返回 Success，随后重启设备。重启后 boot_completed=1，XML 保留，`pm list features` 出现 `android.hardware.wifi.aware`，服务列表出现 `wifiaware`；普通 adb shell 已恢复 uid=2000。`io.awarebridge.android` 1.0/code 1 冷启动 Status ok。没有关闭/重新开启 verity、解锁 bootloader 或刷固件。本轮验证到配置加载、安装和启动，不包含发布、iOS 发现、配对或 NDP；feature/service 存在不能代替 NAN 硬件功能验收。

## P22：HA2E6NPB 有 root 但 vendor 仍不可写

2026-11-16：用户要求同样写入 Aware XML 并安装 APK。设备为 TB323FU、Android 16/API 36，固件 `Lenovo/TB323FU/TB323FU:16/BQ2A.250831.001/maxj9_319.2605211035W:userdebug/test-keys`。连接时 adbd 已是 root，`ro.debuggable=1`，存在 su；但 Bootloader locked、flash.locked=1、Verified Boot green，vendor 为 `/dev/block/dm-10` 的只读 ext4。

实际执行 `adb root` 返回 already running as root；`adb remount` 和 `adb disable-verity` 都返回 `Device must be bootloader unlocked`。root 下直接 `mount -o remount,rw /vendor` 仍返回设备只读。直接 push 失败，最终目标 XML 仍不存在，系统未声明 Aware。由此确认该设备与 P21 的可写工程机不同，root 身份本身不保证能写验证保护的 vendor。没有执行 Bootloader 解锁、刷机、清数据，也未重启，因为 XML 没有成功变更。

本项目 debug APK 安装 Success，`io.awarebridge.android` 1.0/code 1 冷启动 Status ok（248ms）。本轮只完成 APK 安装与启动；XML 写入仍受阻，需要可写 vendor 的环境或从 ROM 构建部署声明，未验证 NAN 发布、配对或 NDP。

### P22 用户解锁后重新部署成功

2026-11-16：用户自行解锁 Bootloader 后请求重试，实机确认 flash.locked=0、vbmeta.device_state=unlocked。`adb root` 成功；首次 `adb remount` 关闭 verity、启用 overlayfs，并要求重启。按提示重启后再次 root/remount，vendor 返回 RW、Remount succeeded。

成功写入 `/vendor/etc/permissions/android.hardware.wifi.aware.xml`，设置 root:root、0644、restorecon，SELinux 标签为 vendor_configs_file。SHA-256 为 `6370914090ba4f81ef1410da719b6b2df5389180a8757ac95e77ca88d0076e85`，与源文件一致。本项目 debug APK 重新安装返回 Success，随后再次重启加载声明。

最终核验 boot_completed=1，XML 及校验值重启后保留，`pm list features` 出现 `android.hardware.wifi.aware`，存在 wifiaware 服务；普通 shell 为 uid=2000。应用冷启动 Status ok（200ms）。因此之前的写入阻塞已解除，本轮完成配置部署与安装/启动；仍未验证此设备的发布、iOS 发现、配对或 NDP。Bootloader 解锁由用户执行，本轮没有执行解锁、刷机或清数据。

## P23：Android 16 Baldur 的设备信息位于 SDA，iOS 无法发现

2026-11-16 用户反馈：安装本项目 APK 到 Android 16 Baldur 后，iOS 无法发现；空口抓包显示设备信息填入 Service Descriptor Attribute（SDA）。用户说明此前向 Google 提交过相关问题，从 Android 17 开始用户配置的服务信息放入 Service Descriptor Extension Attribute（SDEA）。问题编号、对应提交及本轮帧号待补；该版本对照作为用户提供线索记录，不将其概括成所有 ROM 均已确认的行为。

此前提出在 Android 16 ROM 中强制将设备信息放入 SDEA 的验证方案。同日用户明确该设备后续只升级 Android 17，不再更新 Android 16，因此取消该修改与验证，按版本维护策略结束此项跟进，历史现象保留，不标为修复通过。后续以升级 Android 17 后的互通验证为准。此前配对问题可能受接收长度错误影响仍为假设；STA 并发额外尾部及 GTK 声明问题继续独立跟踪。本轮没有修改代码/ROM，也没有新功能验收。详见 [现象、历史线索与取消决定](android16-baldur-ssi-placement.md)。

## P24：反向流程（iOS 发布、Android 订阅/连接）及实机阻碍

**环境：** 2026-11-16，a0807d7 TB323FU / Android 17 + iPhone 12 / iOS 27，Android 无 STA 连接。

**实现：** 新增角色切换、Android 主动订阅/PIN Keypad、iOS DevicePairingView/UDP listener、Android 首包和双向心跳。原 Android 发布方向保留。反向固定端口 45454，并允许框架未通知端口时按应用约定回退。

**问题/证据/处理：** 带 SSI 的 NAN_SUBSCRIBE 被 native 拒绝（status=5，Invalid TLV type），空 SSI 对照成功；最终只降级重试一次并提示首次配对限制。iOS listener 与配对页同时运行曾出现配对页 publisher error=5，改为先配对后监听。引导请求到达 iOS，但名称为空、Android 引导超时；本轮只确认缓存验证，首次 PIN 尚未验收。NDP 曾 reason=2，去掉反向固定 PSK 后有成功样本，但仍有间歇失败；成功时框架端口=0/协议=-1，约定端口回退后双向通。主线程 UDP connect 异常已修复。iOS 默认 120 秒 publisherTimeout 曾连带取消通信；延长 active 请求未改变系统 120 秒期限，改为独立持有 accepted connection 后越过到期点仍持续收发。

**验证/限制：** 两端构建和原有协议检查通过；新应用已安装、角色 UI 已操作；反向发现、缓存配对验证、NDP 和双向心跳实际成功。首次反向 PIN 与底层拒绝原因仍待验证/ROM 分析，不宣称完整新配对通过。原方向回归仍出现 -11987 / NDP reason=2，重启发布及 Wi-Fi 后未通过，不排除角色切换/缓存状态相关性。完整失败时序、状态返回、区分已确认与推测见 [专项记录](reverse-flow-validation.md)。


2026-11-17 P24 补充：已有缓存反向链路演示再次通过；用户删除 iPhone 记录后首次配对仍失败。发现请求方法误用本地 Keypad，已改为对端声明的 Display；修改后请求 method=2，但约 20 秒确认超时、reason=1，未显示 PIN，不能宣称首次配对修复。详见反向专项记录。

Apple 的 [PairingInfo 文档](https://developer.apple.com/documentation/wifiaware/wapaireddevice/pairinginfo-swift.struct) 明确：首次配对时，Publisher 在开启 Setup 的 Publish 帧携带设备信息，Subscriber 在 Pairing Bootstrap Request 携带信息；配对后不再发送这些标识。因此 SubscribeConfig 的 SSI 缺失不直接等同于 Bootstrap 信息缺失，也未证实它是本次引导超时原因。

NanErrorTranslation 打印位置在公开高通 HAL 的 hardware/qcom/wlan/legacy/qcwcn/wifi_hal/nan_rsp.cpp。Subscribe 响应分支将 pFwRsp->status/value 传入，函数把输入 firmwareError 转为 frameworkError，并打印转换后的 status 及响应 value。这表明它在 HAL 处理下层响应，非 Framework Java 参数检查；设备实际拒绝位置仍需对应二进制、驱动及固件证据，不能仅凭公开实现断言固件具体错误条件。

## P25：App 缺少配对缓存管理入口

**环境/需求：** 2026-11-17，a0807d7 TB323FU / Android 17。用户需要在 App 查询及清除自身 NAN 配对缓存；iPhone 删除不会同步清除 Android 缓存。

**处理：** 新增“管理配对缓存”，getPairedDevices 展示别名并刷新；removePairedDevice 删除单条，resetPairedDevices 清空全部。删除需确认，提交后重读系统列表；空列表禁止清空。发布/订阅运行中禁用管理入口，避免活动会话缓存状态失效。查询有超时和错误提示，退出时忽略旧回调并关闭确认框。只管理本应用，不展示密钥，不直接改系统存储文件。

**验证/限制：** JDK 21 assembleDebug/lintDebug 通过，APK 安装 a0807d7；普通 App 进程查询空列表、刷新、空列表禁用清空、开始订阅后禁用管理均通过。此前两条真实缓存已经按用户要求清空（API 与落盘都核验为空），本轮没有伪造缓存；有记录时逐条删除和清空的 UI 分支尚未实机覆盖，首次反向配对仍未通过。服务异常/查询超时仅代码检查，未模拟系统故障。

**iOS：** 当前 DeviceDiscoveryUI 流程使用 WAPairedDevice 查询，已查 SDK 无公开删除方法；通过“设置 → 隐私与安全 → 配对设备”删除。AccessorySetupKit 的 removeAccessory 适用于其管理的配件，不作为当前流程的通用删除接口。

2026-11-17 P24 空口补充：从 Ubuntu 取回历史 1～4.pcapng，2/3/4 共 50 个 iPhone Subscribe 均没有设备 TXT；SDEA 的长度 4 只有 OUI 与 Generic 类型。未获得实际 Bootstrap 握手样本，Publish 中的 Bootstrapping 能力声明不计作握手。详见 [逐文件与代表帧分析](iphone-subscriber-packet-analysis.md)。普通订阅不含 DeviceInfo 与本次首次配对失败必须分开判断。

2026-11-17 P24 最终修正：按 Apple 文档及历史包，普通订阅直接不设置 DeviceInfo SSI，删除 omitSubscribeInfo、失败后重试及“ROM 不接受设备信息”提示。最终 APK assembleDebug/lintDebug 通过并安装 a0807d7，首次 startSubscribe 即 status=0，UI 正常发现服务且无降级提示。这个修改避开订阅配置错误，不记作首次配对修复。

### P24 补充：iPhone 对照定位到实际引导过滤分支

后续代码处理：按用户要求将 SubscriberController.pair 的 Bootstrap 请求方法由 Display（2）改为 Keypad（32），正式保留已验证能通过 iOS 方法检查的行为；普通 Subscribe 的本地配置原本即为 Keypad，不需改动。该修正只解决第一道方法筛选，Bootstrap 元数据仍为空，不能据此宣称首次配对通过。此前恢复原 APK 的记录是诊断阶段历史。

2026-11-17：两台 iPhone 实测首次 PIN 配对成功、NDP ready。a0807d7 的 Bootstrap method=2 在 iPhone 接收端被方法筛选拒绝，日志明确为 `None/Multiple Pairing Methods Requested. NPBA: [], PBEA: []`。临时 APK 改请求 method=32 后越过该分支，但接收端记录 `TXT Record size: 0 bytes`、`Received Pairing SSI: nil` 和 `Missing required pairing metadata in Bootstrap Request`，最终仍超时。因此普通 Subscribe 空 SSI 与 Bootstrap 配对元数据缺失是两件事，后者已取得直接接收日志证据。原版 APK 与源代码恢复，未宣称修复。详细环境、时刻、空口范围和 SDK 方法语义限制见 [iOS 发布端引导对照](ios-publisher-bootstrap-comparison.md)。Ubuntu 原包按用户要求放在 `/home/mxj/NAN/2026-11-17/`，不入 Git。

### P24 补充：API 37 Bootstrap message 接入后的真机结果

2026-11-17：用户指出 SDK 37 新增三参数 initiateBootstrappingRequest。已从官方文档及 a0807d7 运行时确认接口与 createTxtRecordTlvBuffer；App 使用官方助手编码设备的 modelName、pairingName、vendorName，API 37 传 message，API 36 保留旧路径，普通 Subscribe 不加设备信息。为编译公共接口升级 compileSdk 37、AGP 9.1.1、Gradle 9.3.1，min/targetSdk 仍为 36。

新 APK 构建/lint 通过并安装 a0807d7。第二次对照 App 明确打印 API 37 / TXT TLV 86 字节；系统 NativeApi 收到 method=32，但 iPhone 12 在 Mac 时间 17:49:44.883 仍打印 TXT Record size: 0 bytes、Received Pairing SSI: nil、Missing required pairing metadata。首次配对未修复。确认 App 生成非空 message 与 iOS 接收为空，不足以确定中间是设备 Framework、HAL、固件哪层丢失；需继续检查实际设备实现，不能再断言无公共 API。没有中断 Ubuntu Android 17 下载或切换网卡抓包。详见专项文档。

## P26：订阅发现列表缺少直观设备名称

**环境/表现：** 2026-11-17，a0807d7 Android 17 订阅 iPhone 12。原列表常显示“iOS 服务 #编号”，旧代码只扫描 pairingName，未展示型号、厂商。

**处理：** 增加独立 PeerDeviceInfo 解析器，支持已知的 0x04 + 小端长度 TXT TLV 和原始 DNS-SD TXT；按完整记录边界解析，校验长度与 UTF-8，拒绝控制字符和不完整数据，避免从任意二进制偏移提取伪名称。优先 pairingName，其次 modelName；列表补充不同的型号、厂商、会话内编号与配对状态。新回调没有有效字段时保留同一 Peer 已获得的信息；没有名称时明确显示“iOS 设备（未提供名称）”。名称仅用于显示，不作为对端认证或连接选择身份，连接继续使用 PeerHandle。日志仅输出长度与字段名，不输出设备名称内容。

**验证/限制：** 中文名称、型号/厂商、两种编码、空信息、截断、异常字节、非法 UTF-8、控制字符与空名称的解析检查通过；assembleDebug/lintDebug 通过，安装 a0807d7。真机发现两次回调均 SSI bytes=189、fields=[]，UI 显示“iOS 设备（未提供名称）#138”；本轮未在真机取得有效名称，不能宣称已显示真实 iPhone 名称。此前 Framework onMatch 也出现过类似非 TXT 字节内容，但无法单凭此确认空口未发送信息或确定 ROM 出错层。要显示真实名称，须发现回调提供有效 TXT；App 不能据服务名/PeerHandle 推断用户设备名。解析测试加入 scripts/check.sh。

## P27：最低系统版本限定为 Android 17

2026-11-17 用户要求只支持 Android 17 及以上。将 minSdk 从 36 提升至 37，targetSdk 同步设为 37，compileSdk 仍为 37；删除 Bootstrap 的 API 36 两参数兼容路径及不再需要的 TargetApi 注解，直接使用三参数接口。README 更新支持范围，专项文档保留历史配置并追加当前规则。

验证：JDK 21 assembleDebug / lintDebug 通过；aapt2 检查最终 APK 的 minSdkVersion=37、targetSdkVersion=37、compileSdkVersion=37。不再允许低于 API 37 的系统安装。本轮未操作真机或重新验证配对/NDP；此前 Bootstrap 元数据接收为空的问题仍未解决。

### P24：2026-11-18 定位 Bootstrap SSI 的 HAL V3 断点

AOSP Android 17 下载完成后，重新安装当前 APK，使用 a0807d7 / iPhone 12 抓包及读取双端日志。App 仍传非空 TXT TLV 86 字节；信道 6 抓包 01-android-bootstrap-ch6.pcap 第 81821 帧为单播 NAN SDF Follow-up，SDA Service Control=2、Pairing Bootstrapping Request 属性 44、方法 0x0020，**完整属性序列没有 SDEA（14）**；81824 是同 SN Retry。iPhone 10:24:50.798 再次记录 TXT 0 / SSI nil / Missing required pairing metadata。

新证据：设备运行时 getInterfaceVersion 返回 3，VINTF 声明 3，实际服务 ELF 链接且进程 maps 加载 android.hardware.wifi-V3-ndk.so；设备 Java 模块已把 SSI 写入新字段，但 V3 NDK NanBootstrappingRequest::readFromParcel 在读完旧六个字段后跳过剩余 Parcel，没有读取 serviceSpecificInfo。AOSP 冻结接口对照确认该字段 V4 才加入。因此本次已定位到 vendor Binder 接口版本断点，不再仅称“可能是 Framework/HAL/固件”。正常兼容跳过新字段也解释了为何请求受理成功、仍然发出缺少元数据的 Bootstrap。

用户询问 ROM 升级 V4，已记录实际控制位置：default/Android.bp 中 service-lib/service/lazy 的 V4 NDK 依赖、aidl/Android.bp 的冻结 V4 定义及 VINTF fragment；必须同步 V4 实现、SSI 到 legacy 的转换与匹配结构体，并核对高通 wifi_nan_bootstrapping_request 序列化，不能只修改 XML 或库名。本轮没有改 ROM、没有验证 V4 修复，也未取得首次 PIN 成功；V4 解决接口断点不代表下游必然支持。源码提交、二进制哈希/地址、逐帧证据和升级核对步骤详见 [专项分析](android17-bootstrap-hal-v3.md)。原包保存 Ubuntu /home/mxj/NAN/2026-11-18/，未入 Git。测试完成 Android 停止、恢复 30 秒息屏，iPhone 12 锁屏确认，缓存未删除。


### P24：Wi-Fi AIDL V3/V4 冻结接口完整对比

2026-11-18 按用户要求递归比较 Android 17 hardware/interfaces 冻结快照：V3 140 个 .aidl，V4 141 个，修改 6 个、新增 1 个，均为 NAN 类型。所有 IWifi* 方法/回调接口文件完全相同，没有新增 Binder 业务方法；修正此前升级文档“包括新接口方法”的宽泛表述。新增内容为 Bootstrap 请求/接收事件的 SSI、Bootstrap 响应方法、NONE 方法枚举、两个组密钥 cipher mask、周期测距间隔 bitmap 及对应枚举。SSI 的 V3 边界缺失结论保持；组密钥枚举新增不能代替此前 GTK 构帧问题的因果验证，发现 SSI 不可解析也不能与 Bootstrap RequestInd 新字段混同。完整表格及回移植范围见 [V3/V4 差异](wifi-hal-aidl-v3-v4-comparison.md)。本轮仅源码分析，未改 ROM、未操作真机或验收配对修复。


### P24：部署用户编译的 Vendor V4 验证产物（2026-11-18）

Downloads 中服务及 V4 NDK 库已备份后部署到 a0807d7，VINTF 同步声明 4，重启后 Binder 返回版本 4、进程实际加载 V4 库，文件 hash 匹配。另一台平板未操作。本轮只通过部署/加载检查，尚未验证 Bootstrap SSI 空口与首次配对；备份位置、证据和回退说明见 [部署记录](wifi-hal-aidl-v3-v4-comparison.md#vendor-私有-v4-产物部署2026-11-18)。


### P24：Vendor V4 修复后首次配对通过（2026-11-18）

a0807d7 订阅 iPhone 12：App/HAL SSI=86，空口 03-vendor-v4-bootstrap-ch6.pcap 第 122062 帧为带 SDEA 的 Bootstrap Follow-up；iOS TXT=83、三项设备信息完整，用户协助输入 PIN 后首次配对及后续缓存验证通过。86 包含 3 字节 TXT TLV 头，83 为 TXT，不是截断。详见 [实机验证](vendor-v4-bootstrap-validation.md)。

### P28：Android 17 本地网络权限缺失导致心跳 EPERM

首次配对后 NDP 已建立，但 Android sendto 返回 EPERM，AppOps 记录 ACCESS_LOCAL_NETWORK 被拒绝，原 APK 没有声明该权限。添加权限声明与运行时申请，assembleDebug/lintDebug 通过并覆盖安装 a0807d7；本轮用 pm grant 授权后有效 UID 拒绝消失，双向心跳开始增长。首次系统权限弹窗未单独验收；持续通信结果及设备范围见 [实机验证](vendor-v4-bootstrap-validation.md)。


### P24：明确 Framework / Vendor / 高通实现的支持边界

用户要求补齐修改及构建部署记录，已新增 [Vendor Wi-Fi HAL 修改、编译与部署](vendor-wifi-hal-bootstrap-build-deploy.md)，列明 AIDL 输入/快照、Android.bp、optional SSI 转换、VINTF、m/mm、真实备份与 push 命令、权限标签、运行版本检查和回退。隔离服务器实际命令与完整 diff 未直接取得，文档区分操作方法与助手已执行部署，不把建议伪装为原始操作日志。

Framework Android 17、Vendor Android 16 基线为用户提供的产品背景；直接验证的运行接口为 Vendor Wi-Fi AIDL V3。Framework 已写出 Bootstrap SSI，旧 Vendor reader 缺字段而跳过；现有高通下层本来具备 SDEA SSI 能力，本轮不替换高通库/驱动/Firmware，仅补 AIDL 类型及转换后空口、首次 PIN 成功，根因应表述为接口与适配未对齐。已更新 [根因文档](android17-bootstrap-hal-v3.md#修复后确认的层次关系)，避免把 V3 等同整个 Vendor 版本或泛称高通不支持。

Ubuntu 抓包收尾规则已加入 AGENTS.md；退出 monitor 后需额外 sudo nmcli 激活保存连接，用户执行后外网 HTTPS 200 已验证。


### P19：2026-11-19 Publish 修复通过当前组合验证

用户确认前一候选库误改 Subscribe，本次改正 Publish。新库 SHA-256 `0cd80cb19390ebb38cf46f982359bf53545e8f2ed386a244550c782237c6a546` 已部署并重启、确认实际加载。默认 Setup=false 时 HAL 0x27=04，空口 CSIA=04 07 01，iPhone 缓存 Pair Verify、NDP 和双向通信通过。开关 true→一分钟自动 false 均保留 capability=4，链路持续到 48/48；随后重新 Publish(false) 也再次连接成功。P19 的当前硬件/ROM正常路径修复已验证，非首次清缓存配对、非跨机型全面验收；STA 23 字节 P07 未因此解决。另观察 iOS 主动断开后 App 心跳超时会停止 Publish，重连需先重新发布，避免与 CSIA 过滤混淆。详见 [完整部署和证据](csia-publish-fix-validation-2026-11-19.md)。


### P11：发布端断链后保留 Publish（2026-11-19）

已修改 AwareController：心跳/UDP 错误及网络丢失只释放旧数据链路，一秒后重新请求网络并更新端口，保留 Publish/attach 和配对开关；generation 过滤旧回调。手动停止和发现会话失败仍完整清理。编译及 lint 通过，完整双端重连尚未验收。详见 [恢复策略](publisher-reconnect-recovery.md)。


### P11：自动恢复双端复测与最终修正（2026-11-19）

首版只重建网络请求未通过双端实测：Framework 旧 NDP state=104 阻挡新请求；只重建 Publish 也失败。最终改为自动重建 attach/Publish，保留系统配对缓存、App 配对状态及定时关窗任务，并过滤三层旧回调。a0807d7/iPhone12，STA 断开、Setup=false，iOS 主动断开 → Android 心跳超时自动恢复 → 仅点 iOS 连接 → Pair Verify/NDP/双向心跳 13/13 通过。无需 Android 手动重新发布。底层 teardown 根因未修复，此为 App 恢复方案；详见 [失败对照及成功时序](publisher-reconnect-recovery.md)。


### 2026-11-19 反向 STA 并发验证

a0807d7 订阅 iPhone12：STA 5260 MHz 下两次 NDP reason=2（其中一次 Pair Verify 成功），STA 断开后双向 29/29；重新连到另一 BSSID、5320 MHz 后已有链路持续，重新建链也通过、iPhone 15/15。不能概括连接 STA 必然失败，亦未证明与 Publish 23 字节同根因。固定信道重复对照及空口待补；详见 [时序及限制](reverse-sta-validation-2026-11-19.md)。


### P07：303fdedc 第二台平板同会话复现（2026-11-19）

303fdedc 与 iPhone14 Pro 均安装当前 App，保留该平板原 HAL。信道6同一 NMI/Publish 的 STA 断开72帧无零尾，连接后75帧多23字节，后续Setup开启25帧仍多23；再次断开取得35帧（Setup关闭）与49帧（开启）无零尾。STA切换窗口没有重新下发Publish。代表帧正常528字节→异常566字节，38字节差含合法调度属性变化及23字节异常尾部，不能直接相减判定。新线索是 STA 并发/Availability/Unaligned Schedule 路径；尚不能判定驱动还是Firmware。昨日两份347字节HAL请求复核仅头部偏移6不同，payload完全相同。完整 [驱动日志及空口对照](303fdedc-sta-sdf-driver-analysis.md)。Ubuntu已恢复Wi-Fi和HTTPS200；303fdedc恢复原网络，iPhone14 Pro已锁屏。


### P07：b2302624 新刷 TB522FU 对照（2026-11-19）

已 push Wi-Fi Aware XML、重启确认 feature 并安装当前 API37 App；保留原厂 HAL。STA 断开、mxj-5G（5765MHz / 信道153）、mxj-5G_3（5240MHz / 信道48），信道6和149共246个目标Publish均未见额外23字节，CSIA始终00 07 01。不是配对/NDP验收，也不代表TB323FU问题已修复。详见 [部署、帧号与双频抓包](b2302624-sta-sdf-validation.md)。


### P07：b2302624 的 lenovo-internet 补测（2026-11-19）

STA BSSID d0:4d:c6:cb:50:a7，5260MHz / DFS信道52；iPhone14 Pro触发订阅，信道6取得47个、149取得67个目标Publish，共114个均无额外23字节。与先前TB323FU同为STA信道52也未复现，不能归结为只要DFS52就必然异常。详见 [追加抓包记录](b2302624-sta-sdf-validation.md#追加lenovo-internet--dfs-信道-52)。


### P07：正常/异常 Publish 逐属性对照（2026-11-19）

303fdedc第32181/33004帧同机对照：除MAC序号、抓包元数据，NAN属性仅两份Availability变化（Map2仅Sequence；Map1调整Potential并减4字节）及新增19字节Unaligned Schedule；SDA/SDEA/CSIA/安全上下文/NIRA/Vendor内容完全一致。TB522FU正常STA帧的两份Availability去掉前3字节后，与TB323FU异常帧逐字节相同，不能据此把候选信道/bitmap调整认定为异常。完整偏移、长度、条目及97字节跨机差额对账见 [逐属性分析](publish-sdf-field-comparison.md)。未改代码或进行新真机测试。


### P07：三台均STA条件的跨机对照（2026-11-19）

以b230正常11745、303异常33004、a080异常99373重新比较。Availability去头3字节后主体完全一致；异常两台共有额外Capability Extension、Country Code及33字节type41 Vendor属性，但这些同机STA断开时也存在。Unaligned Schedule只在303出现；CSIA和Setup测试状态不统一。SDA差4来自TXT，SDEA另差1来自更新指示。97/80字节整帧差均完整对账，无额外未解释长度。详见 [三台STA逐属性对照](three-tablets-sta-publish-comparison.md)。只读分析，未重新操作设备。


### 双端UDP吞吐测试集成（2026-11-19）

用户要求Android/iOS都增加性能测试。已实现现有Aware UDP链路上10秒/目标50Mbps的单向测试、唯一接收计数与结果回报，心跳保留；两端分别点击可切换方向。双端编译、Android lint与Java/Swift协议互通及本地发送循环通过，APK安装b2302624。iPhone14当前unavailable，双端真实吞吐尚未验收，不将Mac回环值当Aware性能。见 [用法、协议与限制](throughput-test.md)。


### libiperf3 与 Android 分区改版（2026-11-19）

测试型号：Android 为联想拯救者 Y700 平板 **TB323FU**（a0807d7，Android 17 / API 37）；iOS 为 **Apple iPhone 12**（iPhone13,2，iOS 27.2.1）。以下吞吐样本均对应此组合。

环境补充：本轮吞吐结果来自办公室存在无线干扰的实际环境，尚未量化干扰影响，不代表性能上限。用户后续会在更干净的无线环境复测，另行记录并与本轮结果对照。

初版自定义单线程 UDP 固定负载不满足多线程性能测试要求，现改为内嵌 ESnet libiperf3 3.22。默认 4 并行流，TCP/UDP、双向；取消固定 50 Mbps，UDP 发送速率可配置，0 表示不限速。Android 按用户要求采用配对和连接、数据链路、性能测试、配对缓存管理四区域。心跳统计仅在数据链路区域。双端编译、Android lint、安装通过。

iPhone App 出现一条 iPhone、两条同名 Y700，系统设置三条却均显示 iPhone。用户授权清除全部，系统取消配对后显示“先前配对的设备”，App 列表为空；a0807d7 仍有一个旧别名，通过新 UI 清空并确认零条。重新发布和配对，输入 PIN 后 iPhone 只剩一条 Y700，并建立双向心跳。仅确认名称显示差异，未确定系统名称来源。单端删除后以及配对窗口过期的尝试未取得有效 PIN，不计成功。

真实吞吐验证先遇到接口就绪过早读取以及 BSD socket 的 No route to host。Apple 官方答复确认 Aware 应使用 Network framework；加入 loopback → Network framework 适配层，并从公开 NWBrowser 发现结果取得可指定端口的 Aware 端点，修复测试连接。a0807d7 发布 / iPhone12 订阅时，双向 TCP/UDP 4 线程真实测速通过，2 线程切换及 UDP 100 Mbps 总速率参数也通过；结果和本机适配层开销限制见 [专项记录](throughput-test.md)。反向发现角色下新增测试连接尚未验收。

心跳频率已抓包核对：每端约 5 秒，两端相位约差 1 秒，ACK 紧随收到的心跳；不能将两端叠加或每秒 UI 刷新当作单端 1 秒心跳。iOS 将说明移至数据链路区；数字速率键盘增加“完成”按钮。


### 性能测试时长可配置（2026-11-19）

表现与原因：双端 UI 只允许测试 10 秒；Android `iperf_request.duration`、iOS 调用 `ab_iperf_run` 也写死 10，原生包装另有限制最长 60 秒。仅改界面不足以改变实际时长。

处理：Android、iOS 性能测试区新增秒数输入，默认 10 秒，允许 1～3600 秒。Android 通过控制消息传递所选时长，iOS 自身发起或处理 Android 请求时均传给 libiperf；两端入口及原生客户端校验范围，运行状态显示本次目标时长。缺少 duration 的旧控制请求仍默认 10 秒。心跳仍独立按原频率运行，目标时长不包含连接建立和结果交换。

验证：Android assembleDebug / lintDebug 与 iOS iphoneos 构建通过；Android APK 已覆盖安装 a0807d7。iOS 本轮为禁用签名的编译检查，尚未安装本次改动；新增时长尚未做双端真机传输验收，不能把上文原有 10 秒测速结果当作可调时长的验收。后续需双端安装后分别从两端发起非 10 秒 TCP/UDP 测试，核对接收汇总时间，并检查非法输入及断链取消。


时长验证补充：上轮只完成无签名编译，遗漏 iOS 安装。本轮已使用现有开发签名配置成功安装 iPhone 12，并为 303fdedc 覆盖安装同版 Android APK。a0807d7→iPhone 12 的 TCP 4 线程从 Android UI 设置 15 秒并运行完成，iOS 完整结果 **63.45 Mbps / 119.01 MB / 15.0 秒**，确认非固定 10 秒。界面单行结果会省略显示，最终通过 iOS 可访问性接口读取完整汇总；期间 Android 曾切到后台、之后 iOS 报 connectionTerminated(-11985)，不能将该收尾断链混作时长测试失败，也未确定切到后台的触发来源。iOS 输入发起及其他时长/协议待验收。详见 [验收补充](validation.md)。


### 单端删除配对后的重新配对与成功状态防护（2026-11-19）

用户观察：iPhone 删除配对、Android 保留时，重新添加可能不显示 Android PIN，却提示配对完成。本轮未复现该完整操作链，不能确认由缓存直接导致，也不能确认 UI 提示意味着两端完成新配对。

流程核对：公开 AOSP `WifiAwareStateManager.onPairingRequestReceivedLocal` 区分 Setup 与 Verification；Setup 通知应用，Verification 才查询缓存并响应。因此接到真正的新 Setup 应继续 PIN 配对，不应仅因存在本地缓存而直接成功。当前发布端原本已经在配对窗口内使用 `acceptPairingRequest(..., PIN)`，并无按缓存跳过 PIN 的 App 分支。公开参考：https://android.googlesource.com/platform/packages/modules/Wifi/+/c1981d605012aff5ed9729359d1ffaa0670576a6/service/java/com/android/server/wifi/aware/WifiAwareStateManager.java 。该参考不能替代设备 ROM 对应实现。

App 防护：发布端记录当前已接受的 Setup 请求，仅该请求的成功回调才提示新配对成功、关闭配对窗口；没有本次接受记录的成功回调记录警告并忽略。新的 Display Bootstrap 生成新 PIN，已接受的在途 Setup 不旋转 PIN；同一 peer 的重复请求去重，不同并发请求拒绝。Pair Verification 回调独立显示已有配对验证，不冒充新配对，也不提前关闭配对窗口。关闭配对、停止与重建发布时清理本次请求状态；日志只记录事件和请求编号，不打印 PIN 或密钥。

使用方案：Android 打开一分钟配对开关，对端重新添加，应该重新执行 Bootstrap→Setup→PIN 校验；保留其他设备缓存。App 不全量清除缓存，不按名称或 PeerHandle 猜测并删除旧配对。若设备层未上报新 Bootstrap/Setup、或不上报请求却报成功，本次防护可避免误报，但还需 ROM/HAL/FW 日志定位；必要时用户可在配对缓存管理中仅删除目标旧记录后重新配对，不能把此绕过称为自动重配修复。

验证状态：Android 编译/lint 检查，不代表单端删配对真机验收。后续需只删除 iOS 目标记录、保留 Android 缓存，核对回调和 PIN、新缓存及 NDP；本轮不删除用户配对记录。


### 每次发布默认配对与单端删除后重新配对（2026-11-19）

每次新建 Android Publish 默认开启 60 秒 PairSetup（含自动重建），成功或超时后关闭；UI/README 已同步。iOS 仅删除 303fdedc 记录、Android 保留缓存后，新 PIN → Setup → 成功回调通过；Android 仅删除 a0807d7 缓存、iOS 保留记录后，反向 Bootstrap 设备信息非空、新 PIN 配对通过。两种场景分别在两台 TB323FU 上完成，不能说 303fdedc 的 V3 反向路径通过；该尝试尚未完成。旧别名可能累积，未自动清全量缓存。本轮不验收 NDP/吞吐，不认定原始误报根因已修复。Android assembleDebug/lintDebug 通过，完整环境、操作及时序见 [专项记录](single-sided-repair-validation-2026-11-19.md)。


### 303fdedc 替换 HAL 后反向配对通过（2026-11-19）

备份原文件后，部署与 a0807d7 校验一致的 V4 Wi-Fi 服务、V4 NDK 和 libwifi-hal.so，VINTF 改 4；重启运行版本 4、校验一致。HAL Bootstrap SDEA SSI=86，iPhone 12 收到 TXT=83 且三项设备信息非空。清理 Android 更早的两条旧缓存、保留 iOS 配对记录，重置 Wi-Fi 会话并断开 STA 后，20:06 新 PIN 配对成功。此前几轮 PIN 未显示，iOS 有属性更新 Invalid argument，恢复因素未隔离；不能宣称无干预重配全部修复。本轮不验收 NDP/吞吐，也未抓新空口包。完整部署、失败对照与成功时序见 [追加记录](single-sided-repair-validation-2026-11-19.md#303fdedc-补齐-hal-后的反向重新配对同日追加)。


### iPhone 14 Pro 最新 App 覆盖安装（2026-11-20）

按用户要求，从当前仓库源码重新签名构建 iOS App（构建成功），通过 devicectl 覆盖安装 USB 连接的 iPhone 14 Pro（iPhone15,2），返回 App installed，bundleID=com.slab.wifiawaredemo。本轮仅验收构建及安装，不重测配对、数据链路或吞吐，没有删除配对记录或操作 Ubuntu。


### baldur-1 重配后验证失败：重复身份缓存（2026-11-20）

a0807d7 改名、iOS 删配对后新 PIN Setup 成功，但 Pair Verification 被 iOS 以 Invalid Security Parameters 拒绝。Android 三条缓存 LocalNik/PeerNik 相同、NPK 不同；实际设备 service-wifi.jar 的 PairingConfigManager 按身份首条匹配而不优先最新凭据。经用户授权仅删除旧 awarebridge-139/awarebridge-ios-138，保留新 awarebridge-141，iOS 配对不动、不重输 PIN，重新发布后 Verification/NDP 成功，双向心跳持续超过两分钟至 42/42。最终只剩新缓存，安全字段未变；未捕获失败轮次实际选中别名，保留这一诊断边界。未改 App/ROM，不把单次清理说成自动重配永久修复。完整 [证据、ROM 方法与处理建议](baldur1-repair-cache-verification-2026-11-20.md)。


### 配对缓存身份识别：暂缓实现，回退 App 与 Framework 补丁（2026-11-20）

问题沿用 baldur-1 重配案例：同一 Peer NIK 存在不同 alias/NPK，Framework 首条匹配可能选用旧安全关联；App 的公开缓存列表只有 alias，无法可靠分辨同一身份。用户要求先记录，后续在 ROM 中处理。

提交 4040c2f 曾实现 App 身份读取入口和 Framework 查询补丁草案，未部署 ROM、未安装该轮 APK。本轮按要求回退全部实现、补丁工具和测试，App 恢复至该提交前，仅保留问题及后续 ROM 修改建议；不保留 Priv-App，不解析配对文件，不自动删除缓存。原先仅编译/合成测试通过，不计真机功能验收。

后续优先在 PairingConfigManager 按应用范围和已确认 Peer NIK 更新同身份安全关联，避免旧 NPK 累积；如确需 App 识别，再考虑 Framework 直接返回 alias + 派生 peerId，无需暴露密钥或 Priv-App 中转。方案尚未实施。详见 [问题、回退范围与后续验收要求](paired-peer-identity-api.md)。


### P07：Extended WLAN Infrastructure 23 字节候选路径（2026-11-20）

用户补充 Android Subscribe 在 STA 连接时也有 23 字节零尾，并提出 Extended WLAN Infrastructure 属性。官方 Wireshark 解析定义该属性 ID=0x1e、payload=20，连头恰为 23；合法头应为 1e 14 00。既有异常 Publish 零尾连头也全零，因此重点候选是长度已计入、实际属性未写入/被覆盖或发送长度未回退，尚未证明 Firmware 具体缺陷。

旧 303 抓包 141/33305 确有 Subscribe 类型 SDF 在合法属性外多 23 零（两源共 13/116 帧，物理设备映射未核对）；522 有真实 1e 14 00 属性可对照。Publish 33004、a080 的 99373 继续确认零尾。未新抓包/操作设备，未改变代码或固件。已记录后续 builder 长度/cursor 日志判据及静态分析限制，详见 [候选、帧号与排查步骤](sta-zero-tail-extended-wlan-hypothesis.md)。


### 2026-11-20：清除缓存后反向 Bootstrap 超时

Android 请求接受、60 字节 SSI 到达 iOS 且解析出 baldur-1/LENOVO/TB323FU，但 iOS 用户授权响应路径未完成；Android 20 秒 Framework 超时生成 INTERNAL_FAILURE。iOS 出现 No discovery for appID、ENOENT，配对页为空；其因果尚待对照验证，不能认定旧密钥或方式不兼容。详见 [证据与边界](bootstrap-timeout-2026-11-20.md)。


### P07：导出 amss20.bin 并定位 Extended WLAN Infrastructure builder（2026-11-20）

从 a0807d7 只读导出 peach/amss20.bin（SHA-256 2a22e73834cc6af8e563e91fc5ede40de8887351203df3940891a1c9bbb4b761），识别偏移 0x80000 内层 Hexagon ELF，通过暂停状态的 QEMU 静态解码，未执行或修改固件。0x01a4f6d8 明确写属性头 1e 14 00、角色 1 并返回 23，支持空 buffer 查询长度。

重点嫌疑：0x0184fb20 中先写末尾 00:00:f0/type33 Vendor 属性，随后 0x0184fd28 空 buffer 查询上述 23 字节并计入长度，在该函数后续路径未看到对应实际填充；与空口位置吻合。镜像同时存在另一套查询+填充路径，运行时异常帧实际选择及后续间接调用尚未证明，因此未判定最终根因或修复。具体镜像版本、地址、语义、可复核本地产物及下一步 map/日志位置见 [反汇编记录](amss20-nan-disassembly-2026-11-20.md)。无设备/Ubuntu 网络更改，完整二进制不入库。


### P07：配套固件日志字典恢复三个调用者函数名（2026-11-20）

只读导出 Data20.msc，并将三个属性调用点附近日志标识与字典相互核对。0x0184cb44 属于 NANBCN_PrepareBeaconFrameExtended（wlan_nan_beacon_tx.c，日志行128）；0x0184fd28 属于 NANMAC_BuildSDFFrame（wlan_nan_mac_ext.c，日志行2171）；0x01a5ded8 属于 NANMAC_BuildFollowupFrame（同文件，日志行2354）。后者是 Follow-up 的两阶段查询/填充，不是已确认的正常 Subscribe 路径。运行时 Publish/Subscribe 的入口仍需核对；静态可疑的只计长位置保留，未宣称根因已完全证实。详见 [同日追加的函数确认](amss20-nan-disassembly-2026-11-20.md)。


### P07：实验固件补丁已备份并部署 a0807d7（2026-11-20）

用户明确授权生成二进制补丁并刷入。仅对已锁定 SHA-256 的 amss20.bin 修改六个指令字，保留先查询/检查容量，再实际填充并按返回长度累计；更新代码段哈希，未重签名。Beacon 已有填充，未重复修改。Hexagon 解码与六个合成分支用例通过；原件在 Mac、平板 data 与固件目录均有备份。先临时加载，再持久替换，固件分区恢复只读。实际 WLAN 重新下载、Service Ready、STA 连接和 Publish 启动通过，仍未抓新空口包，不标记 23 字节问题已修复。恢复脚本和具体哈希见 [部署/回滚说明](amss20-ext-wlan-patch-deployment.md)。收尾停止测试 App、恢复常亮/扫描原设置，未操作 Ubuntu。


### P07：高通 ST197458 调试固件部署（2026-11-20）

已按用户授权部署到303fdedc，原固件Mac/data/固件分区三处备份并校验。整机重启后系统实际报告WLAN.GNG.2.0-01536-QCAGNGSWPL_V2_TO_SILICONZ-1.197458.1，Wi-Fi连接通过，固件分区恢复只读。尚未抓包验收23字节异常，详见[部署及回滚](qcom-197458-firmware-deployment-303fdedc.md)。


### P07：实验补丁空口验证通过（a0807d7 当前 STA 场景，2026-11-20）

保持连接 STA，先抓包再触发发布/订阅匹配，信道6的544/5131和149的18898共3个目标 Publish 均有 1e 14 00 + 20字节内容，合法属性后额外零尾0；NMI已与a080驱动日志对应、TXT均baldur-1，iPhone12选择页能发现。另一baldur-2的8个异常Publish单独排除，未做其固件检查。只验收本机当前三次发布样本，反向Subscribe、配对/NDP和长稳待测。Ubuntu16:15已恢复managed/Wi-Fi/HTTPS200，随后用户启动新Wireshark并明确要求保留monitor，按最新指令执行；iPhone用户手动锁屏，已确认锁定。详见 [帧号、原包目录和限制](amss20-ext-wlan-air-validation.md)。

### P20：iOS 发布角色的 iperf3 启动与结束修复（2026-11-20）

a0807d7 / TB323FU / Android 17 与 iPhone 12 / iOS 27.2.1：NDP/心跳正常，Android 按钮提示启动失败 null；iOS 自身发起提示 No additional Aware endpoint discovered。修正控制 datagram 在主线程发送的问题；iOS 发布路径改用已有连接复制端口、保留 nan0 scope 的 Network framework 端点，避免搜索并未发布服务的 Android 订阅端。首次测速结束又实际触发 Android fdsan 原生崩溃，定位到 libiperf 服务端 TEST_END / CLIENT_TERMINATE 关闭 stream socket 未置 -1，cleanup 重复关闭；已修正。

双端构建/安装、Android lint、JSON 协议检查通过；保持 STA、4 线程、10 秒连续五轮，TCP/UDP 双向与 iOS 按钮发起均取得结果，Android 进程未重启、服务重新监听、心跳持续。UDP iOS→Android 在 100 Mbps 总目标下接收 70.61 Mbps、丢包 24.62%，不标为无损。办公室干扰环境样本，不代表上限；CLIENT_TERMINATE 失败分支未单独注入。完整 [根因和修复](iperf-ios-publisher-fix.md)、[参数与结果](throughput-test.md)。

### 家庭性能复测：UDP300结束未返回、Android双机USB中断（2026-11-20 晚）

家中a0807d7+iPhone12无Android STA，TCP双向各三轮与UDP100双向正常；UDP300 Android→iOS返回测试中后持续无汇总、心跳仍通，具体结束分支待定位。原生Android双机PSK-NDP已取得560.46/621.74 Mbps两向TCP，但第三轮a080 USB消失，不能报告剩余重复/UDP通过。USB重连问题已向用户询问；303临时选网限制撤销并恢复普通Wi-Fi。详见 [完整条件与证据](home-throughput-2026-11-20.md)。

### 家庭双 Android 性能测试：用户确认 a0807d7 Crash（2026-11-20 晚）

用户确认a0807d7正进行高通Crash收集，取代此前仅USB失联的判断；暂停该设备操作，不重启/重刷/重连测试。前两轮JSON于20:58:00、20:58:12保存，第三轮客户端20:58:33为空；303对端20:58:33报NDP_END KEEPALIVE及onDataPathEnd，不能替代a080异常栈。a080运行实验amss20补丁、303固件不同，根因及补丁关联待dump分析，不宣称连续稳定性通过。详见 [事件更新和证据边界](home-throughput-2026-11-20.md)。

### 双 Android TCP 2/4/8/16 线程：8线程反向再次firmware hang（2026-11-20）

按用户要求重启后仅测TCP，目标每轮10秒，两向2/4/8/16线程。a080/303均无STA、5745MHz/80MHz/2SS；2线程接收595.18/671.43 Mbps，4线程515.77/666.85，8线程303→a080为604.01。第六项8线程a080→303时客户端接收超时，a080内核实际捕获Received firmware hang event及SYS ERROR / RAMDUMP DOWNLOAD MODE，随后USB消失。停止剩余16线程测试、保留现场，不自动重启。只能确认固件异常状态，具体函数/触发责任及实验补丁关系待dump；303已恢复普通Wi-Fi，a080离线无法完成临时选网限制清理。完整 [条件、五个有效样本及异常证据](android-tcp-streams-2026-11-20.md)。


2026-11-20 第二版高通固件：303fdedc已更新至`.197458.2`，重启后WifiHealthMonitor确认实际运行版本；第一版与原厂备份均保留且哈希核对，固件分区ro，STA连接FTTR-5G。23字节空口异常仍待验收。[部署及回滚](qcom-197458-firmware-deployment-303fdedc.md)。

2026-11-20家庭抓包尝试：新固件303fdedc在触发iPhone订阅前后掉线，用户确认无法开机、正在充电排除电量问题。无Android Publish有效样本，23字节修复未验收；Ubuntu已退出monitor并HTTPS验证联网。详见[部署记录追加](qcom-197458-firmware-deployment-303fdedc.md)。


2026-11-21 P07高通第二版实测：a0807d7部署197458.2，整机重启/STA/Publish正常；STA5180MHz两轮Publish尾部均为完整0x1e/20字节属性，无23零尾；STA断开样本无0x1e且边界完整。每组仅1目标帧，未新PIN/NDP/长稳，不排除303fdedc故障关联。Ubuntu已恢复managed和外网，iPhone已锁屏。[完整证据](qcom197458-2-a0807d7-validation-2026-11-21.md)。

### Xcode直接构建：模拟器平台与本机签名配置（2026-11-23）

用户先选模拟器，libiperf真机静态库链接报平台不匹配；改选真机后报缺Development Team。此前助手用iphoneos及命令行签名覆盖，未将个人参数保存到项目。另确认target固定App ID/签名方式掩盖Local.xcconfig同名覆盖；现将默认值移到基础xcconfig并先于本机配置引入，Debug/Release均去掉target固定值。本机写入已忽略的签名文件，用原有效Wi-Fi Aware profile；不带任何签名覆盖参数的真机构建与codesign检查通过，未安装/运行设备，模拟器仍不支持。详见 [操作方法和原因](ios-xcode-build.md)。


### 配对缓存及单侧删除补充

2026-11-20 用户验证补充：已确认“两端配对后，仅 Android 删除缓存，iPhone 保留旧记录，再发起反向配对”会触发上述 Bootstrap 超时。该触发条件已确认（用户验证）；iOS 内部阻止新配对响应的具体逻辑仍待定位，详见专项文档补充。 详见[单侧删除分析](bootstrap-timeout-2026-11-20.md)及[Android17缓存匹配源码](aosp17-multiple-paired-device-selection.md)。


### HA2E6NPB Android17部署（2026-11-23）

原件双端备份后使能Aware、部署V4服务/NDK/CSIA修正库与高通197458.2、安装已有APK。重启、功能声明、Binder版本4、实际固件版本、进程库映射和App发布均通过；跨端配对/NDP/长稳待验收。[部署与恢复记录](ha2e6npb-android17-deployment-2026-11-23.md)。


### 三种平台组合与角色解耦（2026-11-23）

移除固定 Android server / iOS client 和只接收异平台报文的限制，协议 v2 分离 platform/role，两端按发布/订阅分配 iperf server/client；新增 Android client 与 iOS Aware server 转接、多流并发及 native 生命周期保护。双 Android 首次 PIN、连接/心跳及一方向 TCP 通过；iOS 双机使用已有配对，双向 TCP 通过；跨平台两个角色均通过 NDP、心跳和双向 TCP，复用配对换向成功。修复 iOS 发布端测速请求遗漏端口，发布端 UI 复测 131.06 Mbps。一次跨平台接收超时仍记录为未明偶发问题；双 Android 第二方向发生 a0807d7 CrashDump，按用户要求保留现场，未声称全部长稳通过。完整设备、参数、结果、失败与实现见[专项记录](multi-platform-2026-11-23.md)。


### USB 锁屏命令与实际状态不一致（2026-11-23）

iPhone 14 Pro 同一模拟电源键命令后锁定状态为真；iPhone 12 命令退出成功但状态仍未锁定，用户确认手动锁屏后再次只读查询和截图也未显示锁定。已核对命令是 HID 0x0c/0x30 按下 0.5 秒后松开，并非系统锁定完成确认。未证明机型不支持、权限不足或 ROM 缺陷，亦未排除后续解锁/其他操作；不再将命令成功直接作为锁屏验收。见[诊断补充](multi-platform-2026-11-23.md)。


### iPhone 12 USB 锁屏已定位：长按触发 Siri（2026-11-24）

核对本机实现和上游 #2210/#2212，旧 lock 命令按住电源键 0.5 秒会触发 Siri。改用 0.05 秒短按，在 iPhone 12 上 SpringBoard lockstate 从 0 变 1，截图黑屏；同期 devicectl passcodeRequired 仍 false，纠正此前将它等同屏幕锁定的判断。已修本机 CLI/web 两处映射并备份，增加幂等收尾脚本：先查状态、只短按一次、验证状态、不自动重试。脚本已锁屏不发按键分支通过；未额外唤醒注入异常测试。[方法与证据](iphone-usb-lock.md)。


### iOS 普通 Publish 名称缺失：SDEA 空口与发现路径核对（2026-11-25）

iPhone 12 发布、HA2E6NPB 订阅，6/149 信道各抓到40/38个目标 Publish。SDA无Service Info，SDEA存在，Service Info 10字节（4字节WFA头+6字节Specific Info），没有设备名称TXT。Framework isExtendedServiceSpecificInfo=true，App收到6字节、fields=[]；但空口 dd03000017f2 与Java日志 b8a7b3010000 不同，保留为下层待查，不声称传递逐字节一致。AOSP NanMatchInd 的 sdea_service_specific_info[_len] → AIDL extendedServiceSpecificInfo → eventMatch；此字段V1/V3/V4均已有，与Bootstrap新增字段无关。SDA非空优先、空才回退SDEA，但本轮未命中优先级覆盖。Ubuntu已恢复managed和HTTP200，iPhone12已锁屏。[抓包、结构体与源码调用链](ios-publish-sdea-match-2026-11-25.md)。


### SDEA 6字节的 MAC/TLV 解释核对（2026-11-25）

USB归档读取本轮iPhone12历史日志，确认nan0与Publish responder均为56:f8:44:71:55:d2；普通发布配置blob/txtRecordData/instanceName均null。因此dd03000017f2不是本轮NAN接口地址。dd|0300|0017f2完整符合Type1字节+Length2字节LE+Apple OUI三字节的TLV，0xdd作为NAN Vendor Specific也有参考实现支持，但日志未直接解释此SSI的0xdd语义，明确保留该边界，不认定为六字节设备MAC。手机未唤醒、仍锁屏。[日志摘录与解释边界](ios-publish-sdea-match-2026-11-25.md)。
