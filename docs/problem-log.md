# 问题与解决记录

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
| P07 | TB323FU 连接普通 Wi-Fi 后无法发现、Publish 尾部异常 | 待 ROM 定位；断开 STA 可恢复 |
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
| P19 | 配对后停留在发现所选设备，随后 -11988 | 已复现 Setup=false 时发现超时；Setup=true 可绕过 |

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
