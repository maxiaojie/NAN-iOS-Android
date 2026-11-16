# Android 16 Baldur：设备信息属性位置与 iOS 发现失败

记录日期：2026-11-16。以下新增现象及历史背景来自用户本轮反馈；本轮仅整理记录，没有重新抓包、修改 ROM 或进行跨端复测。

## 现象与背景

在 Android 16 的 Baldur 上安装本项目 Android APK 后，iOS 无法发现设备。用户通过空口抓包发现：Android 将应用配置的设备信息填充到了 **Service Descriptor Attribute（SDA）** 中。

用户说明，之前已经向 Google 提交过相关问题，从 Android 17 开始，用户配置的服务信息放到了 **Service Descriptor Extension Attribute（SDEA）** 中。当前尚未提供 Google 问题编号、对应修复提交，以及此次 Baldur 抓包文件名/帧号；因此这里保留为用户提供的历史与版本对照线索，尚未独立核对具体变更或适用范围。

术语统一：用户原话中的 “Server Descriptor Attribute” 在本文写作 Service Descriptor Attribute，“Service Descroptor Extension Attribute” 写作 Service Descriptor Extension Attribute。这里要比较的是应用配置的设备信息/SSI 在空口属性中的承载位置，不表示应用直接构造整个 SDA 或 SDEA。

| 对象 | 当前记录 | 证据状态 |
| --- | --- | --- |
| Android 16 Baldur | 安装 APK 后 iOS 无法发现；抓包看到设备信息在 SDA 中 | 用户本轮反馈，待补对应原始帧 |
| Android 17 | 用户说明相关服务信息改放 SDEA，曾向 Google 提交问题 | 待补问题编号、修复提交及相同配置抓包 |
| Android 16 强制使用 SDEA | 后续 ROM 验证方案 | 尚未实施，发现及配对均未验收 |

Baldur 的本次序列号、具体固件版本及 APK 校验值尚未随本轮反馈给出，不自动将本记录归到此前某一台安装设备。也不把版本差异推断为所有 Android 16/17 ROM 的统一行为；既有历史样本曾在 SDA 与 SDEA 中各包含一份 SSI，需逐帧核对是否存在复制或不同格式封装。

## 后续验证：Android 16 强制将信息放入 SDEA

在 Android 16 对应 ROM/HAL/固件路径上调整设备信息的承载位置，确认实际空口报文进入 SDEA 后，再观察 iOS 能否正常发现并配对。这里记录的是验证方向，不预先指定修改层，也没有在本项目 App 中实现强制选择属性的开关。

建议执行以下对照并保留证据：

1. 固定同一 APK、设备信息内容、服务名、发布模式、配对设置、STA 状态和 iOS 测试步骤，保存修改前抓包与双端结果。
2. ROM 修改后检查 SDA/SDEA 的属性边界、SSI 内部长度、设备信息内容以及是否重复携带。不能只根据 Wireshark 中显示的字符串判断实际封装正确。
3. 单独确认 iOS 发现是否恢复，再测试新 PIN 配对、缓存验证重连、NDP 和双向心跳。发现成功不能直接记作配对或数据链路成功。
4. 与 Android 17 对应正常报文比较，记录帧号、属性偏移及实际字节。若同时改变位置与长度，需进一步拆分对照，避免把两项改动的共同结果归因于其中一项。

## 配对失败与长度错误的关系：待验证假设

用户提出：之前的配对问题，有可能受到接收到的报文长度错误影响。保留此假设，尚未确认具体错误长度字段、受影响帧、接收端处理路径及因果关系。

后续应同时核对属性 Length、SSI/封装内部长度与有效载荷长度，以及是否存在合法属性之外的额外数据。现有两类历史证据分别为：

- [早期 SSI 内部长度与实际内容不一致](packet-comparison-2026-11-13.md)。
- [STA 并发时 Publish 合法属性之后额外 23 字节零数据](tb323fu-sta-nan-issue.md)。

这两类长度/边界现象不自动视为同一个问题，也不能据此把当前 Baldur 的 SDA/SDEA 位置问题与此前 PairSetup=false 的 GTK 声明矛盾合并为同一已证实根因。需在修正长度或属性位置后分别复测，并保留此前失败历史。

## 当前结论

新增 Android 16 Baldur 的属性位置排查线索，后续尝试强制使用 SDEA，并重新验证发现与配对。尚无本轮修复结果，尚不能宣布 Android 16 跨端互通已通过。
