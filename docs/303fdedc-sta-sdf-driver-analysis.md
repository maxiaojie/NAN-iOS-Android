# 303fdedc：STA 并发 Publish 零尾与驱动日志对照

日期：2026-11-19；关联 P07。结论：在第二台 TB323FU 上复现 STA 连接时额外 23 字节全零尾部；断开后同一 Publish 会话恢复正常。定位尚未到驱动/Firmware 的具体写错长度函数。

## 设备、版本及操作范围

- Android：303fdedc，TB323FU，API 37，构建 `Lenovo/TB323FU_PRC/TB323FU:17/CQ2A.260712.001/maxj9_227.2611121127C:userdebug/test-keys`，已有 root。
- 原 App 最后更新 11 月 13 日、min/target SDK 36。本轮覆盖安装当前 Android APK（包含自动恢复修改），实际 min/target SDK 37、安装成功。授予 Nearby/Fine Location/Local Network 权限并开启定位。
- 原 `/vendor/lib64/libwifi-hal.so` SHA-256 `0602a90c34734d335b00dd1d2ce7154e5b1e5b30b8ad47e8a5226ee7f9dc47ae`，本轮未替换 HAL、驱动或固件。它仍是 Setup=false 会省略 capability 的旧库，不能将它当作 a0807d7 的 CSIA 修复版本。
- iPhone 14 Pro：`00008120-001E44113AF8C01E`。按用户要求从当前源码重新签名编译、安装 AwareBridge，Bundle ID `com.slab.wifiawaredemo`，打开添加 Android 设备页触发订阅。自动签名起初因账号不可用失败，使用本机已有且包含本设备的 profile 手动签名后 BUILD SUCCEEDED、安装和启动通过；没有上传签名文件。
- 本轮不操作 a0807d7、iPhone 12；两者由用户交给另一会话测试。
- STA 网络 lenovo-internet，5260 MHz；Wi-Fi 开关一直保持开启。用临时 auto-join restriction 断开，不删除保存网络。
- Ubuntu 信道 6 monitor 抓包，原文件在 `/home/mxj/NAN/2026-11-19/303fdedc-sta/`，Mac 副本及完整日志在 `/Users/mxj/workspace/nan-capture-analysis/2026-11-19/303fdedc-sta/`。完整日志和报文可能含敏感字段，不入 Git。

## 同一发布会话中的 A/B/A 证据

Android NMI 始终 `02:6e:93:52:6b:93`，目标服务 ID `9aa7d1b3283c`。只统计该发送地址、该服务的 Publish，不计另一个会话/iPhone 的同名服务。

| 文件 | 目标 Publish 分组 | 代表帧/时间 |
| --- | --- | --- |
| 02-sta-off-on-ch6.pcap | STA 断开：72 帧，额外零尾 0，CSIA=00 07 01 | 907：10:48:31.342160；最后正常帧 32181：10:49:16.424685 |
| 同一文件 | STA 连接后：75 帧，额外零尾 23，CSIA 仍为 00 07 01 | 第一异常帧 33004：10:49:17.473479 |
| 同一文件 | 后续开启 Setup：25 帧，额外零尾仍为 23，CSIA=04 05 01 07 01 | 68277：10:50:10.950871 |
| 03-sta-on-off-ch6.pcap | 连接阶段：15 帧，零尾 23，CSIA=04 05 01 07 01 | 403：10:50:34.547823 |
| 同一文件 | 再次断开后：35 帧，零尾 0，CSIA=00 07 01 | 7379：10:53:35.287306 |
| 同一文件 | 断开 STA 后再次开启 Setup：49 帧，零尾 0，CSIA=04 05 01 07 01 | 20961：10:53:54.159824 |
| 04-sta-off-confirm-ch6.pcap | 未取得目标 Publish | 不能作为零尾消失的独立证据 |
| 05-sta-off-on-off-repeat-ch6.pcap | 再次切换 STA；有效目标 Publish 仅 6 帧，均在最后断开后、零尾 0 | 92831：10:57:57.431208；连接阶段无目标 Publish，不能声称完成第二轮独立 A/B 复现 |

02/03 已构成同一发布会话断开→连接→断开的对照。03 的抓包遇 Ubuntu SSH 失联，连接恢复后通过固定 stop 入口收尾、取回文件；中间数据有空窗，因此不按文件名的 120 秒描述成完整连续覆盖，也不推断 10:52:51 断开后的第一帧具体时间。未清除双方配对，本轮不是首次配对或数据链路验收；DevicePicker UI 的发现结果还受旧 CSIA 过滤影响，不能用 UI 单独判定 23 字节。

## 从帧边界确认额外长度

对 02 中最后正常帧 32181 与第一异常帧 33004：radiotap 均为 56 字节，radiotap FCS-present=false；cap_len=frame.len，无捕获截断。

| 项目 | 32181：STA 断开 | 33004：STA 连接 |
| --- | --- | --- |
| 记录总长 | 528 | 566 |
| 802.11 帧长（不含 radiotap） | 472 | 510 |
| 第二个 NAN Availability payload | 55 | 51 |
| Unaligned Schedule（属性 ID 23/0x17） | 无 | payload 16，连属性头共 19 |
| SDA / SDEA payload | 96 / 96 | 96 / 96 |
| CSIA payload | 00 07 01 | 00 07 01 |
| 最后合法属性后 | 0 字节 | 23 个 00 |

总长度差 38 = Availability 减少 4 + 合法 Unaligned Schedule 增加 19 + 异常零尾 23，不能直接把两个整帧长度差当作异常长度。最后合法属性仍为 Vendor Specific，零尾在它后面，不属于设备 TXT。

Wireshark 把这段零尾的一部分继续解释为多个 Attribute Type=0/Length=0，末端仍余零字节；不要将它们当作有意发送的合法 Master Indication。额外 23 也不是四字节 FCS。

**调度路径是排查线索，不是已确定根因。** 属性 ID 的十进制 23 和尾部长度 23 是两个概念。并且昨天 a0807d7 的代表异常帧 99373 没有 Unaligned Schedule 属性却同样有 23 字节尾部，因此不能只凭本轮同时出现，就断言它本身必然写错。应查 STA 并发时可选属性的长度预估/实际填充/未写区域。

## 驱动日志给出的边界

本轮原始 `adb logcat` 保存为 nan-303-sta-live.log；另保存启动前及结束后的 dmesg、Aware dumpsys。

| 平板日志时间 | 实际记录 | 可得结论 |
| --- | --- | --- |
| 10:41:47.627 | target_if_nan_dmesg_handler: NAN_NEW_MAC_ADDR | NMI 02:6e:93:52:6b:93 |
| 10:41:47.955 | NAN_PUB_START handle=1 | 已开始发布 |
| 10:49:17.707 | send_vdev_start_cmd_tlv: vdev_id 0 freq 5260 | STA vdev 的 WMI 启动参数；不是 Publish SDF buffer |
| 10:49:17.909 | wlan0(vdevid-0) Connect ... SUCCESS, freq 5260 | STA 连接完成 |
| 10:52:51.473 / .564 | osif_cm_disconnect_sync / locally-generated disconnect | STA 断开 |
| 10:56:28.451 | wlan0 Connect ... SUCCESS, freq 5260 | 补充轮再次连接 |
| 10:57:28.623 / .734 | disconnect_sync / locally-generated disconnect | 补充轮再次断开 |

关键点：10:49:15～19 与 10:52:50～54 这些 STA 切换窗口里，日志没有新的 NAN_PUBLISH 下发，也没有 NAN_PUB_START 重建。Setup 引起的实际更新在 10:50:10.721、10:51:10.899、10:53:54.592、10:54:54.730 等独立时间点。因此这次异常可在不重新构建 App Publish 请求的情况下，随 STA 状态出现/消失，比单纯比较两次重新 Publish 的 dump 更能隔离应用配置。

PCAP 是 Ubuntu 时钟，驱动外层时间是 Android 时钟，内层方括号还有另一套时基；没有做精确时钟偏差校准，不能拿 10:49:17.473 与 .707 的差值断言异常发生在具体 WMI 命令前多少毫秒。

STA 连接附近还有 __hdd_start_tsf_sync: Host Target sync has not initialized、非 MLO 的上下文缺失、TWT/TDLS 并发不支持等日志。但它们没有直接给出 SDF 长度，也没有单独隔离验证，不能挑一个 E 标签就当成原因。断开时的 dp_peer_update_state Invalid state shift 针对 AP 对端，与零尾消失同窗，也不等于 NAN buffer 错误。

## 昨日 HAL dump 的进一步复核

重新解析 `/tmp/nan-gtk-android-live.log` 中 a0807d7 的 17:42:30.291（STA 连接）与 17:45:23.558（STA 断开）两份 347 字节 Publish 请求。两者逐字节比较：**只有偏移 6 不同，16 字节头后的 331 字节 payload 完全一致**，payload SHA-256 均为 `3f62b65d1b4f6b5e1ed0ad30390012cddac2604e993d82097fab9506d9ac8e9b`。不公开含密钥的完整内容。

该结论比“没有看到多 23 字节”更强，但只针对这两份 Publish 请求。不能由此排除 HAL 的其他 NAN_CONFIG 或 STA 调度控制路径。本轮日志也有周期性 NAN_CONFIG，不将“没有 Publish 重下发”扩大成“没有任何其他命令”。

## 当前定位与后续源码检查顺序

已确认：两个 TB323FU 都复现，Pixel 9 原 STA 对照正常；303fdedc 同一 Publish 会话随 STA 状态改变尾部，SSI/CSIA 不需要先发生改变。应用重新构建 Publish 配置不是该触发的必要条件。

当前缺少：驱动接收 NAN vendor command 的实际 byte buffer、NAN WMI 下发原始载荷、最终 SDF builder 的预估长度/写入 cursor/发送长度。现有 send_vdev_start_cmd_tlv 只是 STA vdev 启动，不能拿来证明 NAN WMI 数据已正确。

后续看私有片段时优先沿这几条线：

1. STA vdev 连接/断开如何通知 NAN 并发/可用性/Unaligned Schedule；列出它触发的配置命令，而非只盯 Publish API。
2. NAN vendor command → 驱动内部结构 → WMI 的长度拷贝和 TLV 对齐，区分有效长度与分配容量。
3. 如果 SDF 在固件构建，检查 STA 并发可选属性的 length calculation 与实际填充的差值；重点记录最后合法属性后到发送总长的 23 字节区域。如果在 host 构建，则同样记录提交管理帧前的这些值。

本轮没有读取隔离服务器源码、没有确定具体责任函数、没有部署 ROM 修复。收尾：303fdedc 停止 App，撤销 auto-join 限制、恢复 lenovo-internet，息屏时长恢复原值 1800000；iPhone 14 Pro 手动锁屏、backlightState=off，保持用户设定的永不自动锁屏选项。Ubuntu 已移除 monitor、managed/connected、默认路由恢复，HTTPS 清华镜像 HTTP/2 200。


追加：正常/异常逐属性与TB522FU补充对照已完成，见 [字段、偏移及调度条目](publish-sdf-field-comparison.md)。TB522FU正常帧和本机异常帧的Availability在去掉序号/控制头后内容一致，不能将调度bitmap变化本身作为零尾根因。
