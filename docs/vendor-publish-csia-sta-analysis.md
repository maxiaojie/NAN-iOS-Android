# Publish：CSIA 能力缺失与 STA 并发零尾部定位

日期：2026-11-18；关联 P19、P07。两项异常分别分析，未修改 App 或替换本轮 HAL 二进制。

## 本轮结论

1. **P19 的直接丢弃条件已确认**：iOS wifip2pd 明确记录 `Discovery: Dropping event ... missing CSIA GTKIGTKBIGTKSupported attribute`。不再只是根据 Wireshark 字段猜测 Apple 的过滤条件。
2. **CSIA 能力缺失在高通 HAL 下发驱动前已经存在**：Setup=false 的 Publish dump 没有 TLV `0x27`；Setup=true 增加 `27 00 01 00 04`。两种状态都要求 GTK。实际高通库中存在“capabilities=0 且 Setup=true 才补 4”的分支。
3. **P07 本轮独立复现**：STA 连接时 76 个 Publish 全部在最后合法属性之后多 23 字节 00；断开 STA 后，同一 NMI、相同服务、相同正常 CSIA 的代表 Publish 无额外尾部，并成功连接、双向收发。
4. Publish 的 HAL dump 没有对应的额外 23 字节；当前把 P07 缩小到 **HAL Publish 请求之后的 STA/NAN 并发构帧或长度处理路径**。尚无驱动发送缓冲区/固件内部日志，不能直接断言是哪一层具体函数分配错误。

**未验收 ROM 修复**：没有仅修正 cipher capability 后保持 Setup=false 重新测试；也没有仅修正发送长度后在 STA 连接状态下重测。因此“过滤原因已定位”不等于“所有连接问题已修复”。

## 环境及控制变量

- Android a0807d7 / TB323FU / Android 17，已部署用户编译的私有 Vendor V4 Wi-Fi HAL。另一台 303fdedc 未操作。
- iPhone 12，USB 实测报告 iOS 27.2.1，AwareBridge；使用已有凭据，不删除配对缓存。
- Android 发布 `_aware-bridge._udp`，PairSetup 默认 false，Verify/Cache=true；UI 开启一分钟窗口时使用 updatePublish，只改变 Setup。
- Ubuntu 192.168.31.211，wlp3s0mon；本轮有效 Publish 证据来自信道 6。149 的补充窗口没有目标 Publish，不用来判断异常消失。
- 开始阶段 STA 未连接。用户随后将平板连接 lenovo-internet，实际频率 5260 MHz（信道 52），再临时限制自动连接使 STA 断开；Wi-Fi 开关始终开启。
- 测试后撤销临时自动连接限制、重新连接原网络，停止双端 App 测试，恢复 Android 30 秒息屏。Ubuntu 收尾规则见下文。

注意 Android 初始系统时钟仍为 11 月 12 日，联网后自动同步到 11 月 18 日。下表保留原始 HAL 日志时间；跨端时间对齐以 PCAP、iOS 时间和操作顺序为准，不能把初始 Android 日期误当成旧日志。

## HAL 下发缓冲区：Setup 开、关对照

日志来自实际设备 `adb logcat` 的 WifiHAL 标签，使用 putNanPublish 最终 HexDump；按 NAN 私有请求头 16 字节后逐项解析 little-endian TLV。**这是 HAL 给驱动的私有命令，不是完整空口 SDF**。以下不输出密钥、PIN、NIK 和 PMKID。

| 原始 Android 时间 | 状态 | 请求总长 | TLV 0x15：SDEA 参数 | TLV 0x20：配对配置 | TLV 0x27：CSIA 能力 |
| --- | --- | --- | --- | --- | --- |
| 11-12 12:34:43.440 | 初始 Setup=false、STA 断开 | 342 | 40 04 00 00 | 0a 00 00 00 | **不存在** |
| 11-12 12:36:12.450 | Setup=true、STA 断开 | 347 | 40 04 00 00 | 0b 00 00 00 | **04** |
| 11-12 12:37:12.571 | 一分钟自动关闭 | 342 | 40 04 00 00 | 0a 00 00 00 | **不存在** |
| 11-18 17:40:41.938 | STA 已连接后重新 Publish，Setup=false | 342 | 40 04 00 00 | 0a 00 00 00 | **不存在** |
| 11-18 17:42:30.291 | STA 已连接、Setup=true | 347 | 40 04 00 00 | 0b 00 00 00 | **04** |
| 11-18 17:43:30.459 | STA 已连接、自动关闭 | 342 | 40 04 00 00 | 0a 00 00 00 | **不存在** |
| 11-18 17:45:23.558 | 临时断开 STA、再次打开 Setup | 347 | 40 04 00 00 | 0b 00 00 00 | **04** |

所有这些请求均逐项解析到记录末尾，剩余字节为 0；SSI 与 SDEA SSI 的私有 TLV 长度均为 86。Setup 开启仅比关闭多一个 5 字节的 TLV：

```text
27 00   01 00   04
类型39  长度1   capability
```

关闭状态也下发了 0x9089、0x908a 两个组密钥相关 TLV（各 24 字节），其内容不公开。因而不能把现象简化成“关闭后根本没有组密钥材料”；明确的差异是能力 TLV 被省略。

十进制 27 = 0x1B，与十六进制 0x27 = 十进制 39 不同。用户先前口令分支截图是 `NAN_TLV_TYPE_NAN_PASSPHRASE`；后续正确截图为 `if (pReq->cipher_capabilities) ... NAN_TLV_TYPE_CSIA_CAP`。Publish passphrase、配对时输入的 PIN、CSIA capability 是不同参数；App 没有通过开关设置/清除固定 Publish 口令。本轮两种状态的口令相关 TLV 长度也相同，不支持“关闭时没设密码导致缺少能力声明”的解释。

## 实际高通二进制中的赋值及编码

设备实际加载 `/vendor/lib64/libwifi-hal.so`，SHA-256：

```text
0602a90c34734d335b00dd1d2ce7154e5b1e5b30b8ad47e8a5226ee7f9dc47ae
```

沿已确认的 `nan_publish_request` 跳转入口定位到 0x674f0。0x6759c～0x675b4 读取 request 尾部 capability 字节（偏移 0x13fc），若为 0 再读取 pairing setup（偏移 0x13e8），仅 Setup 非零时写入 4。其控制流等效于：

```cpp
if (request->cipher_capabilities == 0 &&
    request->nan_pairing_config.enable_pairing_setup) {
    request->cipher_capabilities = 0x04;
}
```

上述字段名与 legacy header 布局、用户提供的源码截图、实际日志共同对应；未读取隔离服务器上的完整私有源码。反汇编有时把未导出函数标在相邻 `NanCommand::setNanDisabled` 名下，不能按这个最近符号名称误认函数；本次依靠 Publish 跳转与调用链确认。

0x676f0 调用 `NanCommand::putNanPublish`（入口 0x5f13c）。其中 0x5fa48 读取 capability，0x5fa4c 为 0 时跳过，0x5fa78 置 TLV type=0x27，0x5fa7c 置 length=1，再调用 addTlv。用户截图与这一段完全一致。因此 Setup=false 时省略 TLV 的现象已存在于用户态 HAL，不需要先假设固件把一个正确的 capability 清零。

## Framework / AIDL 转换缺口及候选修改位置

Ubuntu 源码根目录 `/home/mxj/workspace/aosp-android17`：

- `packages/modules/Wifi/service/java/com/android/server/wifi/hal/WifiNanIfaceAidlImpl.java`：`enableFrameProtection` 将 requiresEnhancedFrameProtection、supportGtkAndIgtk、supportBigtksa 设为 true；发布请求有 PairingConfig 时调用，与 Setup 布尔值本身不同。
- `hardware/interfaces/wifi/aidl/default/aidl_struct_util.cpp`：`convertAidlNanPublishRequestToLegacy` 初始化 legacy request 为 0，将 requiresEnhancedFrameProtection 转为 `sdea_params.gtk_protection`，但没有给 `cipher_capabilities` 赋值。Subscribe 转换也应检查。
- 同文件的 DataPath 转换已将 supportGtkAndIgtk / supportBigtksa 映射到 capability 的 0x02 / 0x04，说明能力映射有现成参考。
- `hardware/interfaces/wifi/legacy_headers/include/hardware_legacy/wifi_nan.h`：NanPublishRequest、NanSubscribeRequest 均有 `u8 cipher_capabilities`。

**这些安全配置字段在标准 AIDL V3 中已经存在，V4 也相同。** 当前 GTK capability 问题不需要像 Bootstrap SSI 一样另加 AIDL 字段。用户私有 Vendor 源码不在本地，须核对对应转换是否同样漏赋值；不能将下载的 AOSP 文件当成已读取的设备私有源码。

建议先在 Vendor 的 `hardware/interfaces/wifi/aidl/default/aidl_struct_util.cpp` 的 Publish 转换函数中，紧接现有 gtk_protection 赋值后补齐以下映射和非敏感日志。Subscribe 具有同名字段，可在其转换中同步核对。

```cpp
const auto& security = aidl_request.baseConfigs.securityConfig;
// Request was zero-initialized earlier. Group-key support is independent of Setup.
if (security.supportGtkAndIgtk) {
    legacy_request->cipher_capabilities |= security.supportBigtksa ? 0x04 : 0x02;
}
LOG(INFO) << "NAN Publish security: setup="
          << legacy_request->nan_pairing_config.enable_pairing_setup
          << " gtkRequired=" << security.requiresEnhancedFrameProtection
          << " gtkIgtkSupported=" << security.supportGtkAndIgtk
          << " bigtkSupported=" << security.supportBigtksa
          << " cipherCapabilities="
          << static_cast<unsigned int>(legacy_request->cipher_capabilities);
```

日志应放在 pairingConfig 已完成转换之后，避免读到尚未填入的 setup 值。这是**待用户 ROM 编译验证的候选修改**，不是本次已部署的修复；如私有转换已存在其他 capability bits，应保留原映射，不能用固定 4 覆盖整字节。不要关闭 GTK Required 来掩盖矛盾，也不要永久开启 PairSetup 绕过问题。

修复验收：初始 Publish(false)、true→false updatePublish、重新创建 Publish(false) 三种路径都应下发 0x27=04；空口声明正常，iOS 不再报 missing CSIA，继而完成缓存验证、NDP、双向心跳。现有二进制尚未做此项单变量修复验收。

## 空口与 iOS 日志

Ubuntu 文件均在 `/home/mxj/NAN/2026-11-18/`；Mac 副本在 `/Users/mxj/workspace/nan-capture-analysis/2026-11-18/`。原始包/完整日志不入库。

| 文件 | 有效 Publish 样本 | 代表帧及状态 |
| --- | --- | --- |
| 07-pairsetup-off-on-ch6.pcap | 60 个关闭、55 个开启；均无额外尾部 | 24145：17:33:01.422510，CSIA=00 07 01，SDEA=0440；66539：17:33:40.218827，CSIA=04 05 01 07 01，SDEA=0640 |
| 09-sta-connected-ch6.pcap | 76 个开启，**全部额外 23 字节** | 99373：17:42:30.647770，长度549，CSIA=04 05 01 07 01，SDEA=0640 |
| 10-sta-disconnected-connect-ch6.pcap | 1 个目标 Publish，无额外尾部 | 4703：17:45:27.335863，长度530，CSIA=04 05 01 07 01，SDEA=0640；之后迅速进入连接 |

后两份包的平板 NMI 相同，02:06:86:df:57:25；前一轮是 02:1d:28:24:0a:05。NMI 为会话地址，不是永久 MAC。不能直接拿 549-530=19 当尾部长：正常 Availability 等属性也会变化，23 的结论来自逐项 TLV 边界检查。

STA 连接代表帧 99373 的最后合法属性之后恰有 23 个 00；断开代表帧 4703 恰好在最后属性处结束。不是 SSI TXT 后缀，也不是最后一个合法 VSA 自己包含的零数据。旧记录中的 Pixel STA 正常对照继续有效，本轮没有连接 Pixel 进行新对照。

iPhone 的 wifip2pd USB 系统日志：

```text
17:33:01.037057  Discovery: Dropping event, [Android NMI]
                missing CSIA GTKIGTKBIGTKSupported attribute
17:33:53...     NIRA match found / Found matching UUID [redacted]
17:35:28.362530 Discovery: Dropping event, [Android NMI]
                missing CSIA GTKIGTKBIGTKSupported attribute
17:45:27.994521 NANPairingInitiator: state: authenticated
17:45:27.994677 Indicating auth success to NANDatapathInitiator (Pair Verify)
```

17:45 的 STA 断开 + Setup 开启对照成功建立链路，iOS 截图显示双向通信 13/13。这次使用已有反向配对留下的凭据进行验证，没有重新输入 PIN。早期 iOS 选项默认落在另一条同名/旧设备上，不能把这些轮次的 UI 不连接一概归因于 GTK；本报告过滤结论依赖具体对端的丢弃日志与字段，而非那几次 UI 等待。

STA 连接的异常帧没有对应本轮明确的 iOS“因尾部长度而拒收”日志；不能将 CSIA 的显式日志套用成 23 字节的拒收日志。

SHA-256（按上表顺序）：

```text
145bfa32e23852c12cb2780d7c4759a8075851079164cfdf0cc8415d099bf131
d265d8bf035e6c3775f2a62c4ad3f941bc1b3f4d5c2306fd714b9cddad0a7c14
605277c3f35ab9438d3ab4987a44abbd9d6712a89afcfc5f65fe67b113fc361c
```

## 23 字节问题下一步应加哪些日志

Publish 请求是服务配置，HAL dump 不含完整空口帧，并不意味着 HAL 所有其他控制命令都已排除。需沿相同设备、STA 连接状态增加：

1. HAL 下发请求的实际长度、各 TLV 类型/长度与尾指针；本轮 Publish dump 已证明边界闭合。另对 STA 状态变化触发的 NAN 配置/调度请求取同样信息，不能只检查 Publish 一个入口。
2. 驱动接收 vendor command 后传给 WMI/固件的有效长度和 TLV 边界，区分业务长度与分配容量。
3. 固件 SDF builder 每项 attribute 的写入字节数、最终 cursor 与实际发送长度；重点检查 STA 并发分支预留区、可选属性未写入而长度仍累加的路径。
4. 若管理帧由 host 组帧，则在管理帧提交发送前记录帧长及最后合法属性结束位置，与 monitor 包逐字节对齐。

在不知道哪个模块负责最终构帧前，不能因为 HAL request 完整就跳过驱动断言 firmware 错误，更不能仅在抓包文件里截掉 23 字节作为修复。

## Ubuntu 一次性抓包管理授权

用户不希望每次恢复接口都输入 sudo 密码。本轮提供 `tools/setup-nan-monitor-control.sh`，复制到 Ubuntu `/home/mxj/setup-nan-monitor-control.sh` 后由用户执行一次。安装 root 持有的 `/usr/local/sbin/nan-monitor-control` 和固定 sudoers 条目；不运行用户目录下可被修改的恢复脚本。

后续助手使用：

```bash
sudo -n /usr/local/sbin/nan-monitor-control prepare
sudo -n /usr/local/sbin/nan-monitor-control capture 6 120
sudo -n /usr/local/sbin/nan-monitor-control stop
sudo -n /usr/local/sbin/nan-monitor-control restore
sudo -n /usr/local/sbin/nan-monitor-control status
```

仅允许固定动作；抓包入口仍限制 6/149 信道及 10～120 秒，stop 只匹配 wlp3s0mon 上的固定 tcpdump 调用，restore 删除 monitor 并激活固定保存网络 lenovo-internet，不改有线 SSH。没有开放任意命令、PID、接口、输出路径或任意 sudo。

实际验收：安装后免密执行 prepare、10 秒抓包、status、restore；另启动 120 秒抓包后通过 stop 主动终止，tcpdump 正常结束并输出统计，随后恢复。首次 restore 的 nmcli 曾因接口尚未就绪返回 unavailable，助手再次执行 restore 后成功；此情况不需要重新授权。最终确认 wlp3s0 managed、连接 lenovo-internet、无 monitor、默认路由恢复，清华镜像 HTTPS 返回 200。11/12 两份管理测试包也保存在上述日期目录，不作为 Publish 问题的业务证据。
