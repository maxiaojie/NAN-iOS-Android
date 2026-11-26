# a0807d7 固件日志字典更新

2026-11-26。用户要求更新与Firmware对应的日志字典；仅操作a0807d7 / TB323FU_PRC，不改HA2E6NPB。

## 变更与原因

设备实际运行高通固件 `WLAN.GNG.2.0-01536-QCAGNGSWPL_V2_TO_SILICONZ-1.197458.2`。原Data20字典与该固件随附版本不一致；虽然都标记VERSION268928976，消息ID映射/参数格式已经不同，影响NAN故障日志解读。详见[此前核对](SmartConnect-nan-reconnect-root-analysis-2026-11-25.md)。

新字典来自Mac `/Users/mxj/Downloads/buildpath0 2/wlan_proc/build/ms/wlan_diag_v2/Data20.msc`，部署到设备 `/vendor/firmware_mnt/image/peach/Data20.msc`。

| 文件 | 字节数 | SHA256 |
| --- | --- | --- |
| 原字典 | 2024278 | 1742dbe53887a239bb08d1a9824be1514a334ceebc29ab1e702eb8ec613e6e26 |
| 197458.2匹配字典 | 2024168 | fa711c932deb35bf9e1178e94db0d6a3565c557c42311cb169950cb197b18e45 |

## 备份和部署步骤

原件备份：

- Mac：`/Users/mxj/workspace/nan-firmware-analysis/2026-11-26/a0807d7-dictionary/Data20.msc.original`。
- 设备：`/data/local/tmp/nan-dictionary-backup-20261126/Data20.msc.original`。

通过adb root临时提权，复制并校验原件与新版SHA256。只停止日志服务 `vendor.cnss_diag`，临时remount固件分区为rw，将新版复制到同目录临时文件、校验后改名，sync，恢复ro，再启动日志服务。更新脚本有退出清理逻辑，失败时尝试恢复ro和日志服务；没有重启平板、关闭Wi-Fi或替换amss20.bin。

关键设备命令：

```sh
stop vendor.cnss_diag
mount -o remount,rw /vendor/firmware_mnt
# 同目录临时文件复制、校验，再改名为 Data20.msc
sync
mount -o remount,ro /vendor/firmware_mnt
start vendor.cnss_diag
```

完整实际执行脚本：设备 `/data/local/tmp/nan-dictionary-backup-20261126/update-dictionary.sh`，Mac同备份目录的 `update-dictionary.sh`。

## 验证结果

- 目标字典大小2024168字节，SHA256与新字典一致。
- 日志服务由原PID1775重启为PID19759，`init.svc.vendor.cnss_diag=running`。
- 11:12:12.425和11:12:12.496的CNSS日志明确记录 `loaded msc path:/vendor/firmware_mnt/image/peach/Data20.msc`，后一次chipset78802，确认重新读取目标字典。
- `cnss_fw_logs_current.txt`继续增长/输出，已检查到11:13:29的新日志。没有把旧文件头的时间误作服务没有更新：该服务会继续使用既有日志文件。
- `/vendor/firmware_mnt`为原vfat挂载，已恢复ro；文件仍system:system、firmware_file标签，权限r--r-----。
- 收尾adbd恢复非root。不操作iPhone和Ubuntu网络。

这是日志字典部署与加载验证；不是NDP故障修复，也未执行新的配对/重连验收。之前已按旧字典输出的文本不会因替换文件而自动修复，分析历史日志仍须匹配原始DIAG消息和正确字典。

## 回退

设备和Mac备份目录都提供 `restore-dictionary.sh`，已通过设备端 `sh -n` 语法检查；没有实际回退，以保留本次新版生效状态。

```sh
adb -s a0807d7 root
adb -s a0807d7 shell sh /data/local/tmp/nan-dictionary-backup-20261126/restore-dictionary.sh
adb -s a0807d7 unroot
```

脚本只接受当前文件为本次新版或原版哈希，校验备份后临时rw恢复、再ro，并重启cnss_diag。原始字典、新字典和完整日志属于厂商/本地诊断材料，不上传GitHub。
