# Apple 原版 Demo：允许新配对与普通监听的 Publish 对照

日期：2026-11-16。关联：[P19](problem-log.md)、[Android 配对开关空口分析](pair-setup-discovery-analysis.md)。

## 结果

**已取得 Apple 原版 Demo 在两种状态下的 Publish SDF。两种状态都要求 GTK，也都声明组密钥支持并包含 GTK CCMP-128；没有出现当前 Android 关闭 PairSetup 时的声明矛盾。**

这里“配对状态”指 Host 打开“+”的 DevicePairingView、允许新设备配对；“非配对状态”指关闭该页面，通过 Advertise 启动面向已配对设备的 NetworkListener。不是删除系统缓存前后的比较，也不是“正在输入 PIN”和“PIN 已完成”的握手阶段比较。两端已有配对记录保持不变，本轮没有完成新配对。

| 字段 | iPhone 普通监听 | iPhone 配对页 | Android Setup=false | Android Setup=true |
| --- | --- | --- | --- | --- |
| DCEA Capability Info | 0x0200 | 0x0300 | 0x0305 | 0x0305 |
| DCEA Pairing Enable（bit 8） | 0 | 1 | 1 | 1 |
| DCEA NPK/NIK Caching（bit 9） | 1 | 1 | 1 | 1 |
| SDEA Control | 0x0644 | 0x0644 | 0x0440 | 0x0640 |
| SDEA GTK Required（bit 10） | 1 | 1 | 1 | 1 |
| SDEA Security Required（bit 6） | 1 | 1 | 1 | 1 |
| SDEA Data Path Required（bit 2） | 1 | 1 | 0 | 0 |
| CSIA capability 字节 | 0x04 | 0x04 | 0x00 | 0x04 |
| CSIA group-key support 子字段 | 2：支持 | 2：支持 | 0：不支持 | 2：支持 |
| CSIA cipher IDs | 7、5 | 7、5 | 7 | 5、7 |
| NIRA | 存在，payload 17 字节，version 0 | 同左 | 同左 | 同左 |

ID 7 为 PK PASN-128，ID 5 为 GTK CCMP-128。套件排列顺序不同不等于能力不同。iPhone 两种状态的完整 CSIA payload 均为 `04 07 01 05 01`，其中 01 是关联的 Publish ID；DCEA payload 从 `00 02` 变为 `00 03`（小端）。字段定义来源沿用 [Android 报告中的上游定义链接](pair-setup-discovery-analysis.md#属性逐项对照)。

因此，Apple 样本说明：**关闭新配对与继续声明 GTK 支持可以同时存在**。当前 Android 却在应用关闭 PairSetup 后丢失 GTK 声明，且 DCEA Pairing Enable 未随应用开关变化。这加强了检查 Android framework/HAL/固件配置映射与构帧分支的必要性，但仍不等于已经确定 Apple 过滤服务的具体条件。DCEA 是设备级声明，需排除其它会话/全局配置的聚合影响；不能只凭一个字段定位到某行固件代码。

## 应用与环境

- Apple 原版工程：`/Users/mxj/XcodeProjects/BuildingPeerToPeerApps`，HEAD `af19a00`（Added handling of Wi-Fi Aware Errors.）。业务源码无工作区修改；仅有此前保存的本机 Xcode 签名配置改动。
- 原版服务 `_sat-simulation._udp`，空口 Service ID `63:48:7a:70:d3:a0`；与新项目 `_aware-bridge._udp` 不同。本次比较能力字段，不把两个不同服务当作相同业务会话。
- iPhone 12 / iOS 27.0，通过 USB 操作 Host Simulation、Advertise、关闭监听、“+”配对页及截屏确认。
- Ubuntu wlp3s0mon，固定信道 6；本轮 Apple 样本未在信道 149 复验。
- Android 303fdedc 的旧 App 用于早期主动订阅；a0807d7 的 AwareBridge 未启动发布。没有使用 Android 的 Publish 作为 Apple 样本。
- 原版真机签名构建通过并临时安装，使用已有授权的测试 Bundle ID；本轮结束已恢复原 AwareBridge 安装。

## 抓包证据

| 状态 | Ubuntu 文件 | Mac 文件 | 代表帧 | 同组合 Publish 数 |
| --- | --- | --- | --- | --- |
| 普通监听 | /var/tmp/nan-capture/ch6-20261116-141645-9790.pcap | /tmp/apple-demo-listen-ch6.pcap | 65855，t=70.733248 s | 7（至帧 68802，t=73.881921 s） |
| 配对页 | /var/tmp/nan-capture/ch6-20261116-142031-10238.pcap | /tmp/apple-demo-pair-only-ch6.pcap | 4070，t=4.581428 s | 7（至帧 6903，t=7.726018 s） |

两组代表帧都是 iPhone 发出的 Publish，目的地址为 NAN 组播地址 `51:6f:9a:01:00:00`，SDA Requestor Instance ID 为 0。MAC 随系统隐私机制变化：普通监听样本为 d2:4e:0e:a1:e9:e1，配对页样本为 b6:28:df:30:43:ee；均由 UI 操作、时序和报文属性共同确认，不能要求两次 MAC 必须相同。

普通监听样本由 UI 的 Stop Advertising 按钮确认 Listener 正在运行，DCEA Pairing Enable 为 0。该文件早期有 Android 主动订阅，旧 App 在服务发现后还会主动发起 bootstrapping，因此它不是完全无其它请求的单变量环境。这里仅将实际捕获的 Publish 能力字段用于比较，不根据该段推断 iOS 新配对/连接成功。

配对页抓包开始前已强制停止旧 Android App。该文件中的 NAN 发射源仅为 iPhone；捕获到 Beacon 和上述 7 个 Publish，没有 Android Subscribe。配对页 UI 明确显示“You are appearing as iPhone”及等待附近设备请求配对的说明。这个窗口证明 iPhone 在无同期 Android 订阅发射的情况下发出了这些 Publish，但不能由此推断长期持续发布，也不能排除此前发现事件或系统缓存对发布时序的影响。

原始文件 SHA-256：

- 普通监听：d57a5b16bd4c5882c18f63c79d71112905403350100275111d69e1d2d5b67508
- 配对页：7a596af9f37017baab9dc77c887e42fca854b313f22df1d82bbcdc6f4a33b26f

原始抓包含身份解析与安全协商材料，仅本机保留，不上传 GitHub。抓包 wrapper 仍返回 137，文件可由 tshark 解析；没有 tcpdump 完整丢包统计，不宣称捕获全部报文。

## 为什么曾操作 Android，以及排除的复验

目标始终是比较 iPhone 的 Publish。主动订阅端原本用于促使 Host 在普通监听状态下发出服务响应；仅被动抓包可能只看到 Beacon。用户指出不需要测试 Android 链路后，已停止旧 App，后续不进行 PIN 配对或数据链路验收。

继续尝试严格对照，但没有取得新的 Publish，以下不用于结果表：

1. `/tmp/apple-demo-clean-toggle-ch6.pcap`（Ubuntu 14:23:03 文件）：Android 全程停止，只捕获到 iPhone Beacon。包含切换页面的操作，部分关闭按钮坐标未命中，不以该段宣称已抓到完整两状态 Publish。
2. 临时复制旧 Android 工程到 `/tmp/apple-passive-subscribe-probe`，只去掉服务发现回调中的自动 `initiateBootstrappingRequest`，保留主动订阅；修正临时副本的 jar 路径以便本机构建。原工程源码未改。构建通过，临时覆盖安装 303fdedc，未清除数据。
3. `/tmp/apple-demo-no-pair-probe-ch6.pcap`（Ubuntu 14:28:28 文件）有 Android Subscribe、iPhone Beacon，但无新 Publish；普通监听的 UI 后来确认开启，不能把整个文件当作单一状态。303fdedc 仍连接普通 Wi-Fi，Subscribe 尾部可见零字节被解析成额外属性；此处未进一步独立定位，不用无响应推导 Apple 的 CSIA 内容或否定此前真实帧。

这些复验没有补成“仅改变 iOS 配对开关、其它一切完全相同”的重复验证，不将它们包装为额外通过。已备份并重新安装 303fdedc 测试前的原 APK；临时订阅 App 已停止，普通 Wi-Fi 配置未更改。最终 iPhone 已恢复 AwareBridge，USB 短按 Power 后主 LCD 与全局 backlightState 均为 off，lockState 返回 passcodeRequired=true，已锁屏。
