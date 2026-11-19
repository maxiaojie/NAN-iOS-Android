# b2302624：新刷 TB522FU 的 STA 并发 Publish 检查

日期：2026-11-19；关联 P07。本轮信道 6 对照未复现额外 23 字节全零尾部。不能据此声称其他机型、固件或所有并发场景已修复。

## 环境与部署

- 平板 b2302624，TB522FU，SoC SM8750P，Android 17 / API 37。
- System / Vendor 指纹均为 `Lenovo/TB522FU_PRC/TB522FU:17/CQ2A.260712.001/10.076.261103C:userdebug/test-keys`。
- adb root/remount 成功，将下载目录的 `android.hardware.wifi.aware.xml` push 至 `/vendor/etc/permissions/android.hardware.wifi.aware.xml`，权限 0644，restorecon 后重启。系统 `pm list features` 确认 `android.hardware.wifi.aware`。
- 安装当前 AwareBridge，包名 `io.awarebridge.android`，versionName 1.0，min/target SDK 37；APK SHA-256 `b49fcf3870167ecc1935a54ccf3c0ae2afda30ff17113e25656b68d891f7cb10`。授予 Nearby、Fine Location、Local Network 权限。
- XML SHA-256 `6370914090ba4f81ef1410da719b6b2df5389180a8757ac95e77ca88d0076e85`。
- 新系统首次开启定位涉及百度网络定位许可；命令短暂开启后被自动关闭，App 弹出定位未开启错误。交由用户完成许可选择后确认定位开启，发布成功。没有修改 App 绕过定位检查。
- 保留原厂 HAL、驱动、固件，未部署 TB323FU 的替换库；`/vendor/lib64/libwifi-hal.so` SHA-256 `77505d54be92bf340eaf9c0b09049d561922f9742474a73440d4b0db77b7c399`。
- iPhone 14 Pro 使用当前已安装 AwareBridge，在添加 Android 系统页触发 Subscribe。没有操作 iPhone 12、a0807d7 或 303fdedc。

## 方法及信道 6 结果

Wi-Fi 始终开启，STA 断开用临时 auto-join restriction，随后解除限制分别连接两个用户指定接入点。Publish 保持同一会话、Setup=false，不进行配对或 NDP 测试。目标服务 ID `9aa7d1b3283c`，NMI `02:5e:98:cb:55:17`；驱动 `NAN_NEW_MAC_ADDR` 确认该地址属于本平板，SSI 内 modelName=TB522FU 再次核对身份。仅统计目标服务的 Publish。

| 文件 | STA 状态 | 有效目标 Publish | 额外全零尾部 | 代表帧 |
| --- | --- | ---: | --- | --- |
| 01-sta-off-ch6.pcap | 未连接 | 56 | 全部 0 字节 | 3842，11:22:25.349289 |
| 02-mxj-5G-ch6.pcap | mxj-5G，BSSID a4:a9:30:72:1a:93，5765 MHz / 信道 153 | 60 | 全部 0 字节 | 514，11:23:43.463690 |
| 03-mxj-5G_3-ch6.pcap | mxj-5G_3，BSSID a4:a9:30:72:1a:92，5240 MHz / 信道 48 | 46 | 全部 0 字节 | 1108，11:24:53.194474 |

全部 CSIA=`00 07 01`，SDEA control=`40 00`；SDA payload 92、SDEA payload 91 字节。基线记录长 473/457，连接后主要 453，radiotap 均 56 字节；长度变化来自合法 Availability 属性，最后合法属性之后没有剩余字节。未观察到旧 TB323FU 异常阶段所见 Unaligned Schedule 属性；该差异仅供定位，不证明它是旧问题根因。

当前 Setup=false 缺少 GTK capability 的 CSIA 问题仍与旧库一致，iOS 配对选择页未显示设备不能直接当作 23 字节异常证据。本轮不验收首次配对或数据链路。

## 信道 149 补充

保持同一 Publish 和 Setup=false，连接 mxj-5G_3 抓取 `04-mxj-5G_3-ch149.pcap`，38 个目标 Publish 均额外零尾 0；切回 mxj-5G 抓取 `05-mxj-5G-ch149.pcap`，46 个均为 0。两组 CSIA、SDEA control 与信道 6 一致，记录长均 453。03 代表帧 cap_len=frame.len=453、radiotap FCS-present=false，未发生捕获截断。五份文件合计 246 个目标 Publish，均未见额外 23 字节。

这说明问题并非所有 Android 17 平板连接 STA 必然出现；TB323FU 与本 TB522FU 在平台、HAL、固件及调度属性上均存在差异，不能据此单独归因于某一层或认定已找到修复提交。

## 日志、证据与限制

Ubuntu 原始抓包保存 `/home/mxj/NAN/2026-11-19/b2302624-sta/`；Mac 副本、状态和驱动日志保存 `/Users/mxj/workspace/nan-capture-analysis/2026-11-19/b2302624-sta/`。完整空口和日志不入 Git，避免泄露配对安全字段，接入点密码也不入文档。

平板时钟为 11 月 4 日，与抓包机 11 月 19 日不同，不能直接按日志墙钟对齐；表中时间来自抓包机。信道 6 有效帧构成主证据。本轮只覆盖指定两个接入点与当前版本，未对其他带宽、DFS、漫游或长期并发作保证。

## 收尾

停止平板 App，恢复原 30 秒息屏，解除临时 auto-join restriction，平板保留连接 mxj-5G；权限、功能声明和两个用户要求的网络配置保留。iPhone 14 Pro 已手动锁屏，devicectl 确认 passcodeRequired=true。Ubuntu restore 首次因设备暂不可用失败，重试成功，wlp3s0 managed/connected、默认路由恢复，清华镜像 HTTPS 返回 HTTP/2 200。

## 追加：lenovo-internet / DFS 信道 52

用户将 b2302624 连接 lenovo-internet 后，使用同一 APK、原厂 HAL 和 Setup=false 再测，未切换用户指定的 STA 网络。实际 BSSID `d0:4d:c6:cb:50:a7`、5260 MHz（信道 52，DFS）、11ax。平板系统时钟本轮已为 11 月 19 日，与上一轮不同。

新 Aware 会话 NMI `02:5b:ff:06:91:5b`，11:32:32.184 驱动 `NAN_NEW_MAC_ADDR` 确认，报文 TXT 中 modelName=TB522FU。iPhone14 Pro 打开系统添加页触发订阅。

| 文件 | 目标 Publish 数量 | 额外零尾 | 代表帧 / 抓包机时间 |
| --- | ---: | --- | --- |
| 06-lenovo-internet-ch6.pcap | 47 | 全部 0 字节 | 11745 / 11:33:05.641836 |
| 07-lenovo-internet-ch149.pcap | 67 | 全部 0 字节 | 613 / 11:34:01.351631 |

合计 114 个目标 Publish 均没有额外 23 字节。CSIA=`00 07 01`、SDEA control=`40 00`，记录总长 469、radiotap 56，SDA/SDEA payload 为 92/91，两个 Availability payload 都是 51。06 代表帧 cap_len=frame.len=469，FCS-present=false；末尾合法属性后没有残留字节。

与前轮 mxj-5G、mxj-5G_3 对照，本机在三个接入点上均未复现；即使 STA 主信道与之前 TB323FU 复现时同为 52，也不足以触发本机异常。不能将“STA 位于 DFS 信道 52”单独当作充分条件，也不能由本轮反推 TB323FU 已修复。未进行配对/NDP 验收。

06/07 原始包已保存到前述 Ubuntu 日期目录，Mac 同目录保留对应状态、logcat 与 dmesg。测试结束停止 App、恢复 30 秒息屏，保留平板连接 lenovo-internet。

本轮收尾复核：Ubuntu 已恢复 managed/connected、默认路由和 HTTPS HTTP/2 200；iPhone14 Pro 已锁屏，passcodeRequired=true。
