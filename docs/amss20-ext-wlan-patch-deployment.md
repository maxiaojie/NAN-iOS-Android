# a0807d7 Extended WLAN Infrastructure 固件实验补丁部署

2026-11-20。用户明确要求生成补丁、刷入并备份原固件。本轮实际目标仅为 **a0807d7 / TB323FU / Android 17**，未修改 b2302624、iOS 或 Ubuntu。

## 补丁范围

源码工具：[patch_amss20_ext_wlan.py](../tools/firmware/patch_amss20_ext_wlan.py)。仅接受下述原始 SHA-256、逐条检查原指令和原代码段哈希，另存输出，不覆盖输入。固件二进制保留本地、不上传 Git。

修正候选位置为 `NANMAC_BuildSDFFrame` 的 `0x0184fd3c..0x0184fd50` 六个指令字，不使用代码洞或新段：

1. 保留 `0x0184fd28` 的空 buffer 长度查询。
2. 保留原来的严格容量条件 `capacity > used + attrLen`；无属性或容量不足走不追加分支。
3. 容量足够时，传 `frameBase + used` 调用 `0x01a4f6d8` 实际填充，避免覆盖前面的 Vendor 属性。
4. 累计长度使用实际返回长度；第二次调用因状态变化返回 0 时，不留下 23 字节空洞。

为容纳第二次调用，复用原容量不足日志的指令空间，因此本分支不再打印原来的 overflow 诊断；容量不足时仍不写入、保留旧长度。其他相邻指令和 Beacon 路径未修改。`NANBCN_PrepareBeaconFrameExtended` 原本就有实际填充调用，重复追加不正确。

同时更新原文件 `0x80660` 的主可执行段 SHA-256；没有重签名、没有修改认证逻辑。该设备在本次实验中接受并启动了补丁镜像，不据此推断其他产品或固件都能接受。

| 文件 | SHA-256 |
| --- | --- |
| 原始 amss20.bin | `2a22e73834cc6af8e563e91fc5ede40de8887351203df3940891a1c9bbb4b761` |
| 实验补丁 amss20.bin | `23f5ed17af4d6ff8ddb5818e87a9cc2c5328a820beb976c27cf4a89bb15ea038` |

两者大小相同，均为 7,471,168 字节。除六条指令字及对应 32 字节段哈希外无其他修改。原有签名未重新生成。

## 指令和边界测试

使用暂停加载的 QEMU Hexagon 解码器重新解码所有改动，确认分支落点、call 目标、packet 边界和参数计算。随后在仿真器中**仅执行该短补丁片段**，在 builder 入口由测试桩模拟返回值；不执行真实固件启动或硬件逻辑。

六个用例均通过：容量充足；容量恰好等于 used+23（沿用原严格条件，不追加）；容量不足；查询返回 0；查询返回 23、填充返回 0；初始 used=0。检查了调用参数、调用次数、累计长度、buffer 前后保护区及关键寄存器。测试桩模拟写入，因此不将这些结果称为实际完整 builder 或空口验证。

测试脚本与报告位于 Mac 分析目录：`test_patch_control_flow.py`、`patch-control-flow-tests.json`、`patched-sdf-tail.disasm.txt`。

## 原始备份

Mac：

```text
/Users/mxj/workspace/nan-firmware-analysis/2026-11-20/a0807d7/amss20.bin
```

平板两份，均已与原 SHA-256 校验一致：

```text
/data/local/tmp/nan-firmware-backup-20261120/amss20.bin.original
/vendor/firmware_mnt/image/peach/amss20.bin.nan-original
```

Mac 实验镜像与元数据：

```text
/Users/mxj/workspace/nan-firmware-analysis/2026-11-20/a0807d7/amss20-sdf-ext-wlan-test.bin
/Users/mxj/workspace/nan-firmware-analysis/2026-11-20/a0807d7/amss20-sdf-ext-wlan-test.patch.json
```

## 实际部署及加载证据

先停止本应用、暂时关闭扫描和 Wi-Fi，通过 per-file bind 临时覆盖实际固件路径，验证系统可重新启动 WLAN。临时测试的自动清理脚本采用 `mountpoint -q` 未正确识别文件 bind；已在持久部署前显式 `umount` 并确认露出的原文件 SHA-256 恢复原值，没有将该脚本误作成功恢复的证据。最终恢复脚本已改为检查 `/proc/mounts` 的精确文件挂载项。

临时测试成功后，在 Wi-Fi 关闭状态下将 `/vendor/firmware_mnt` 临时改为可写，备份原文件，向同目录临时文件复制补丁、校验 SHA-256、sync 后原子改名替换 `amss20.bin`，随后恢复分区只读。**当前是持久文件替换，不是仍依赖临时 bind**，没有刷写整块 modem 分区或改变分区表。

持久部署后，15:35:44 驱动重新请求 `peach/amss20.bin`。日志中先出现 Direct firmware load error -2，随后明确进入 ueventd fallback、找到并加载 `/vendor/firmware_mnt/image/peach/amss20.bin`，15:35:45 收到 Firmware Service Ready（build version 200a7fff）。因此不能把前面的 -2 单独当作固件加载失败。

重新启动后 Wi-Fi 已连接 lenovo-internet。确认实际路径 SHA-256 为补丁值、旁边原始备份为原值、固件分区挂载为 ro，没有 per-file bind 残留。恢复了原先扫描开关值 1。这个过程是 WLAN 关闭/重新上电并实际下载固件，未重启整台 Android；跨整机重启稳定性未验收。

## 一键恢复原固件

平板已放置并通过 shell 语法检查：

```bash
adb -s a0807d7 shell sh /data/local/tmp/nan-firmware-backup-20261120/restore-original.sh
```

脚本先校验备份，停止 App、关闭扫描与 Wi-Fi，必要时撤销临时文件挂载，再将备份复制到固件目录临时文件，校验、原子改名、sync、恢复只读，最后恢复扫描与开启 Wi-Fi。此命令是恢复操作，本轮持久部署后没有执行，当前保留补丁用于测试。恢复脚本本身不会绕过认证或修改其他设备。

如果调试导致 ADB 不可用，需在恢复到可连接状态后执行恢复或使用已备份原始文件；不要把分析用 `.analysis.elf` 当作固件刷入。

## 验收边界

已完成补丁生成、静态解码、短片段合成分支测试、备份、临时加载、持久文件替换和 WLAN 重新加载/STA 连接检查。随后启动 App 发布，系统确认有效会话 `mIsPublishSession=true`、`mPubSubId=1`；这只证明发布启动成功，不证明已发出正确空口属性。收尾停止 App，恢复原 USB 常亮设置 0、扫描设置 1，保留补丁固件及正常 Wi-Fi。**尚未通过新空口包确认 Publish/Subscribe 的 23 字节零尾消失，不标记 P07 已修复**；首次配对、缓存验证、NDP/吞吐及长稳也未因本次部署而重新验收。

此前得到的 builder 名称、地址和责任边界见 [反汇编记录](amss20-nan-disassembly-2026-11-20.md)。


## 后续空口验证已完成（同日）

保持 STA 的 a0807d7 已在信道6和149取得三次发布会话的 Publish，均有实际 1e 14 00 属性且额外零尾为0；iPhone12能发现baldur-1。上文“尚未抓包”为部署阶段的历史状态，最新验收范围、帧号及用户要求保留Ubuntu新monitor状态，见 [空口专项记录](amss20-ext-wlan-air-validation.md)。
