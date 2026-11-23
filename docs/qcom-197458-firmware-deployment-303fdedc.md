# 高通 ST197458 固件部署到 303fdedc（2026-11-20）

## 范围与结果

用户授权部署并备份原件。仅操作 303fdedc（TB323FU，Android17），替换 `/vendor/firmware_mnt/image/peach/amss20.bin`，未修改 HAL、驱动、BDF 或其他设备。原始包 `/Users/mxj/Downloads/WLAN.GNG.2.0.tar.gz` 内路径为 `buildpath0/wlan_proc/config/bsp/cnss_ram_v2_TO_link_patched/build/7880.wlanfw.eval_v2_TO/amss20.bin`。

原版 `WLAN.GNG.2.0-01536-QCAGNGSWPL_V2_TO_SILICONZ-1.185753.3`，SHA-256 `2a22e73834cc6af8e563e91fc5ede40de8887351203df3940891a1c9bbb4b761`。

新版 `WLAN.GNG.2.0-01536-QCAGNGSWPL_V2_TO_SILICONZ-1.197458.1`，SHA-256 `8d76f63e34584be27fc84e0ff526cb638380b5337395f51e97906a057c8b24d3`。本次为高通提供的调试固件，不是本项目自行生成的实验补丁。

## 备份及恢复

原件已核对三处 SHA-256：

- Mac：`/Users/mxj/workspace/nan-firmware-analysis/2026-11-20/303fdedc/qcom-197458/amss20.bin.original`
- 平板：`/data/local/tmp/nan-qcom-197458-backup/amss20.bin.original`
- 固件分区：`/vendor/firmware_mnt/image/peach/amss20.bin.qcom197458-original`

恢复脚本同时保存在 Mac 上述目录及平板 data 目录，源文件校验成功后才执行替换：

```bash
adb -s 303fdedc root
adb -s 303fdedc wait-for-device
adb -s 303fdedc shell sh /data/local/tmp/nan-qcom-197458-backup/restore-original.sh
adb -s 303fdedc reboot
```

脚本会停止 AwareBridge、关闭扫描及 Wi-Fi、remount 固件分区为 rw，校验临时复制文件后改名替换，恢复 ro、扫描和 Wi-Fi。原扫描/Wi-Fi 开关均为 1。

## 部署与验收证据

root 后先备份、校验；关闭扫描/Wi-Fi，将原本只读的 vfat 固件分区临时 remount 为 rw，使用同目录临时文件校验后改名替换、sync，恢复 ro。随后重启整机，sys.boot_completed=1，目标文件 SHA-256 仍为新版。

启动日志显示 ueventd 从实际目标路径加载 peach/amss20.bin，Firmware Service Ready。前面的 Direct firmware load -2 后有成功 fallback，不作为失败判定。WifiHealthMonitor 与 dumpsys wifi 当前 SW build 均报告 `.197458.1`，previous SW build 为 `.185753.3`；驱动仍为 5.2.1.147N。

Wi-Fi 自动连接 lenovo-internet，具备 IP；重启后 BSSID 变为 d0:4d:c6:cb:75:67、频率 5200 MHz（重启前为 BSSID 后缀50:a7、5260 MHz）。固件分区为 ro。尚未抓包、验证 23 字节问题、配对/NDP 或长稳；部署及基础 Wi-Fi 启动通过不等于问题修复。Ubuntu 未操作。固件二进制不提交 Git。


## 第二版 .197458.2 更新（2026-11-20）

新文件位于 `/Users/mxj/Downloads/buildpath0 2/wlan_proc/config/bsp/cnss_ram_v2_TO_link_patched/build/7880.wlanfw.eval_v2_TO/amss20.bin`。manifest 与内嵌版本均为 `WLAN.GNG.2.0-01536-QCAGNGSWPL_V2_TO_SILICONZ-1.197458.2`；SHA-256 `b6f89c304e8f01a276413bcc112807638f7d2340bb4c04b85b108799a3cbdae6`。

部署前确认303fdedc仍加载第一版，原扫描/Wi-Fi均为1。第一版另存 Mac `/Users/mxj/workspace/nan-firmware-analysis/2026-11-20/303fdedc/qcom-197458-2/amss20.bin.previous`、设备 `/data/local/tmp/nan-qcom-197458-2-backup/amss20.bin.previous`，设备哈希为 `8d76f63e34584be27fc84e0ff526cb638380b5337395f51e97906a057c8b24d3`。分区另存 `amss20.bin.before197458-2`，初始原厂备份全部保留。使用同样关闭Wi-Fi、临时rw、校验后改名替换、恢复ro方式部署，已请求整机重启。

返回第一版脚本：`adb -s 303fdedc shell sh /data/local/tmp/nan-qcom-197458-2-backup/restore-previous.sh`，需先adb root，执行后建议重启。返回原厂仍使用上一节restore-original.sh。更新版本不等同于确认CR4402077修复，尚需空口验收。

整机重启验收：sys.boot_completed=1，WifiHealthMonitor 当前及from SW均报告 `.197458.2`，from Memory为上一版 `.197458.1`。目标及两份分区备份哈希与预期分别一致，分区ro，Wi-Fi连接FTTR-5G。基础启动通过；尚未抓包验证。


第二版抓包准备：用户告知家庭抓包机为mxj@192.168.1.2。该IP ping通过，SSH 22端口Connection refused，尚未登录、切换monitor或开始空口抓包；等待Ubuntu启动SSH或提供实际端口。303fdedc当前STA为FTTR-5G/5180MHz，App已打开但未启动Publish，临时屏幕超时已恢复30000ms；不将本次准备记为修复验收。

家庭抓包环境更新：mxj@192.168.1.2（mxj-AMD）已通过用户确认ED25519指纹并公钥登录。空闲Intel/iwlwifi网卡wlp1s0/phy0支持monitor；联网MediaTek/mt7925e网卡wlp7s0连接FTTR-5G，有线enp6s0承载SSH，HTTPS可用。专用固定管理命令安装脚本已上传/home/mxj/setup-nan-home-control.sh，等待用户一次sudo安装；只操作wlp1s0，采集6/149信道10～120秒，文件暂存root持有目录再由用户账号复制到/home/mxj/NAN/日期目录。未开始抓包。


### 第二版抓包尝试及设备掉线

家庭Ubuntu脚本已由用户安装并验证可免密运行。采集三份包（149信道20秒、6信道20秒、6信道30秒），保存在Ubuntu `/home/mxj/NAN/2026-11-20/qcom197458-2/` 和Mac `/Users/mxj/workspace/nan-capture-analysis/2026-11-20/qcom197458-2/`。前两份没有目标服务SDF；iPhone12恢复USB操作并切换订阅角色、打开添加Android页面后，第三份只有iPhone Subscribe样本，没有Android Publish，不可用于验收23字节修复。

期间303fdedc从ADB列表消失。用户随后确认平板无法开机，正在充电排除电量不足；尚不能确定电量、固件或其他原因。停止设备操作，等待用户确认供电/开机状态，不宣称新固件稳定或已修复。此前已启动及联网成功的证据保留。Android临时屏幕超时600000ms因掉线尚不能恢复，恢复连接后应设回30000ms。

iPhone实际已通过USB连接，之前仅凭ADB列表及旧工具路径判定没有iPhone是不准确的。实际工具位于 `/Users/mxj/nan-ios-tools/bin/pymobiledevice3`，已成功截图、切换角色、打开及关闭系统发现页，收尾发送锁屏命令。Ubuntu已恢复wlp1s0为managed，联网wlp7s0保持FTTR-5G，有线及默认路由保留，HTTPS 200确认外网。没有可用Android Publish样本，不以抓到的iPhone属性解析情况作Android固件判断。
