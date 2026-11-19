# 新高通 HAL 部署及 CSIA 验证：未通过

时间：2026-11-18 18:34～18:46。设备 a0807d7 / TB323FU Android 17，iPhone 12 / iOS 27.2.1。对应问题 P19。

> 后续：用户确认本轮误改了 Subscribe 入口。11 月 19 日更正 Publish 后的部署和通过结果见 [新验证记录](csia-publish-fix-validation-2026-11-19.md)，以下保留当时失败证据。

## 结果

**部署成功，但本次库没有实现“Setup=false 时仍下发 cipher capability=4”的预期行为。** 新库确实被 Wi-Fi 服务加载，初始关闭、开启、超时自动关闭三种状态仍表现为：

| 状态 | HAL CSIA capability TLV 0x27 | 空口 CSIA payload | iOS 结果 |
| --- | --- | --- | --- |
| 初始 Publish(false) | 不存在 | 00 07 01 | 明确丢弃：missing CSIA GTKIGTKBIGTKSupported attribute |
| updatePublish(true) | 04 | 04 05 01 07 01 | Pair Verify 成功，NDP 和双向通信通过 |
| 一分钟自动 updatePublish(false) | 再次不存在 | 00 07 01 | 断开重连后再次报相同丢弃原因 |

未宣称 P19 修复；保留新候选库在设备上，未回滚。没有清除双方配对缓存。另一个 Android 303fdedc 未操作。

## 输入核验、备份与部署

用户先提供的 service 与 V4-ndk.so 实际与已安装版本完全相同，因此没有重复替换这两个文件。用户确认修改高通 Publish 入口后补充下载目录中的 libwifi-hal.so。

| 文件 | SHA-256 |
| --- | --- |
| 新 /Users/mxj/Downloads/libwifi-hal.so | 1837883062e14520511f37cb9411c551421208539af4d20598b79450cc2a876c |
| 备份的旧 libwifi-hal.so | 0602a90c34734d335b00dd1d2ce7154e5b1e5b30b8ad47e8a5226ee7f9dc47ae |

新库大小 704360 字节，ELF64 AArch64，SONAME libwifi-hal.so，BuildID b9fa24f01e0f4e911674b84299e67223。它依赖的 libcld80211/libpasn/libnl 等为现有系统库。

当前 service、V4 NDK 库、原高通库、VINTF XML 已完整备份：

```text
/Users/mxj/workspace/nan-capture-analysis/2026-11-18/vendor-before-csia-fix-1830-a0807d7/
```

本轮实际操作：

```bash
adb -s a0807d7 remount
adb -s a0807d7 shell stop vendor.wifi_hal_legacy
adb -s a0807d7 push /Users/mxj/Downloads/libwifi-hal.so /vendor/lib64/libwifi-hal.so
adb -s a0807d7 shell chmod 0644 /vendor/lib64/libwifi-hal.so
adb -s a0807d7 shell restorecon /vendor/lib64/libwifi-hal.so
adb -s a0807d7 shell sync
adb -s a0807d7 reboot
adb -s a0807d7 wait-for-device
adb -s a0807d7 root
adb -s a0807d7 wait-for-device
```

remount 虽提示部分无关分区不能挂载，但明确 /vendor RW 且 Remount succeeded，push 成功。重启后 adbd 恢复普通 shell，已重新 root。sys.boot_completed=1，Wi-Fi 服务 running（PID1253），设备文件 SHA 与新库一致，/proc/1253/maps 明确加载 /vendor/lib64/libwifi-hal.so。VINTF 和 AIDL V4 接口库维持上一轮已验证配置。

## 新库中仍存在的条件

在本次 ELF 中，导出 nan_publish_request 位于 0x97758，跳转到 0x674f0。

```text
0x675a0  读取 request 尾部 cipher_capabilities
0x675a4  非零则跳过默认填充
0x675a8  读取 pairing setup（偏移 0x13e8）
0x675ac  setup 为零则跳过填充
0x675b0  将 4 放入 w2
0x675b4  写入 cipher_capabilities（偏移 0x13fc）
```

putNanPublish 中 0x5fa48～0x5fa84 同样仍然是 capability 为零就跳过、非零才 addTlv(type=0x27,len=1)。并未看到预期取消 Setup 门限的控制流。反汇编最近符号有时显示 setNanDisabled，实际函数通过导出跳转与 Publish 调用关系确认，不能按最近符号名称误判。

**新 hash 不等于目标修改已进入被执行的分支。** 目前没有用户隔离构建树的源码 diff，无法确定是改了另一处同名函数、构建选错实现还是复制了错误输出；已请用户提供具体函数/修改片段或更新后的库，不能擅自选择其中一种作为已证实原因。

## 双端测试与日志

为排除 P07，临时限制平板 STA 自动连接，确认 Wi-Fi 开启而未连接普通 AP；App 版本、SSI、服务名、Verify/Cache 保持不变。iOS 先选择正确的平板缓存设备，再发起连接。所有 raw log 均保留在 Mac 临时分析目录，不入库，避免包含密钥/PIN。

实际 HAL Publish dump：

| Android 时间 | 请求总长 | 私有 TLV 0x15 | 私有 TLV 0x20 | 私有 TLV 0x27 |
| --- | --- | --- | --- | --- |
| 18:38:59.325 | 286 | 40 04 00 00 | 0a 00 00 00 | 无 |
| 18:42:03.349 | 347 | GTK Required 保留 | Setup 开启 | 04，日志 cipher capabilities :4 |
| 18:43:03.443 | 342 | GTK Required 保留 | Setup 关闭 | 无 |

初始请求 286 字节而非历史的 342，是本次重启后首次 Publish 未包含此前缓存的两个组密钥相关 TLV；不能将 56 字节差异认作 SSI 丢失。初始 dump 的 SSI 与 SDEA SSI 均为 86 字节，TLV 边界闭合。

iOS 明确日志（仅保留非敏感信息）：

```text
18:40:34.030518 Discovery: Dropping event, [Android NMI]
               missing CSIA GTKIGTKBIGTKSupported attribute
18:42:05.386044 NANPairingInitiator: state: authenticated
18:42:14.299710 NANPairingInitiator: state: authenticated
18:44:12.658048 Discovery: Dropping event, [same Android NMI]
               missing CSIA GTKIGTKBIGTKSupported attribute
```

Setup 开启后 iPhone 截图双向 13/12；Android 的相邻采样为 11/11。两次截图时间不同，不能将计数差视为丢包率测量。自动关窗后原有连接仍可持续，不代表关窗后的新发现正常。随后主动断开重连才复现第二条丢弃日志。

## 抓包证据

Ubuntu 路径 `/home/mxj/NAN/2026-11-18/`，Mac 副本 `/Users/mxj/workspace/nan-capture-analysis/2026-11-18/`。本轮只抓信道 6。平板 NMI 02:ec:ab:51:46:31。

| 文件 | 样本 | 代表帧 |
| --- | --- | --- |
| 14-csia-candidate-off-on-ch6.pcap | 关闭80个、开启5个 Publish | 621：18:41:16.484967，CSIA=00 07 01，SDEA=0440，527字节；59121：18:42:03.674171，CSIA=04 05 01 07 01，SDEA=0640，530字节 |
| 15-csia-candidate-auto-off-ch6.pcap | 自动关闭后5个 Publish | 32033：18:44:12.647852，CSIA=00 07 01，SDEA=0640，546字节 |

所有上述样本均无额外零尾部，STA 确实保持断开。关闭后的 SDEA 可以保留 Service Update Indicator，故为 0640，不能只根据 0440/0640 判断 Setup；实际 UI、配对配置和下发日志共同确认状态。

```text
14 SHA256 4f89c0e0559024c872aeb8cad61c9dfdaab247f4ca083259212a2a4fd00b426c
15 SHA256 0900a21aefb14bc4750b8da52cc0950fea38bd6dbe88103e19ed1f61ddce4fba
```

13-csia-candidate-initial-ch6.pcap 为前置采集窗口，尚未完成 iOS 选择及连接，不作为有效失败对照。

## 收尾

停止日志与抓包；撤销平板临时自动连接限制，重连原 lenovo-internet，恢复 30 秒息屏并停止 App。iPhone 退出连接后 USB 短按电源执行锁屏；CoreDevice lockState 查询仍返回 4016，不将此写成已读到 passcodeRequired。

Ubuntu 通过已授权的 nan-monitor-control 自行停止、退出 monitor、恢复普通 Wi-Fi；首次遇到 NetworkManager 就绪延迟后自行重试，最终 wlp3s0 managed/connected、默认路由恢复、HTTPS 200，无需用户 sudo。
