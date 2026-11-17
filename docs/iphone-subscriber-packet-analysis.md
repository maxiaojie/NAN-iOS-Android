# iPhone 订阅端历史抓包核对

日期：2026-11-17。来源：Ubuntu 192.168.31.211 的 /home/mxj/NAN/1112/1.pcapng 至 4.pcapng。原始文件下载到 Mac /Users/mxj/workspace/nan-capture-analysis/2026-11-17，不提交原始包及完整设备识别信息。

## 结果

| 文件 | Subscribe SDF 数量 | 发送者 | 代表帧 |
| --- | --- | --- | --- |
| 1.pcapng | 0 | — | — |
| 2.pcapng（历史信道 6） | 18 | 96:94:3e:c1:26:1c | 501、858、1328 |
| 3.pcapng（历史信道 149） | 14 | 96:94:3e:c1:26:1c | 263、734、1405 |
| 4.pcapng | 18 | 3e:a3:0b:50:15:b8 | 40、180、390 |

这些订阅帧的 SDA 均没有 Service Info。SDEA Service Info Length 均为 4，仅含 OUI 50:6f:9a 与协议类型 02（Generic），没有应用 SSI 内容，也没有 modelName、pairingName、vendorName TXT。Wireshark 在零应用负载处显示 <MISSING>，不能将这个字样误认为存在设备 TXT 或据此断言报文损坏。2.pcapng 的帧 501 与 4.pcapng 的帧 40，SDEA Control 均为 0x0044；本结论依据每个 Subscribe 的字段统计，不把 Publish 的 DeviceInfo 混入。

同一批文件可见 Publish 中的 Pairing Bootstrapping 属性，但它是发布能力声明，不能视为已发生 Bootstrap Request。本轮未检出可确认的 NAN Pairing Bootstrap Request/Response 握手，不据此推断所有信道、完整配对过程中都没有发送。

## 对 Android 订阅实现的含义

[Apple PairingInfo 文档](https://developer.apple.com/documentation/wifiaware/wapaireddevice/pairinginfo-swift.struct) 指出：首次配对时，Publisher 在允许 Setup 的 Publish 携带设备信息，Subscriber 在配对 Bootstrap Request 携带设备信息；配对后不再发送这些身份信息。

历史包支持“普通 Subscribe 不携带设备 TXT”的事实。因此不能要求 SubscribeConfig 必须设置 DeviceInfo，也不能把 Android 的空 SSI 订阅降级直接认定为首次配对超时的根因。下一步应核对真正的引导请求及 ROM 从哪项配置构造其设备信息；Android 公共 initiateBootstrappingRequest 只有 peerHandle 和 method 参数，不能在该调用中直接传入 TXT。

## 文件校验

- 1.pcapng：718056159cf070f65be36ba01eab0bd1a289d9a3fd61c1edb0ae20e2fdec2362
- 2.pcapng：8465cb7211aaabc18247356bda48ea86cd8ade2c27e9708a255f920298027f54
- 3.pcapng：b50d5fbd9ef111e0ee51ebc17f774dcc9c39a3c7cde735ae9db4d0f4b13f7568
- 4.pcapng：3c11f82c54b1ab009457dd5011f6d74c453135882f35eaab7d4e5ddeb6643967
