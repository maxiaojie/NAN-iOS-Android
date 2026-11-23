# HA2E6NPB Android 17 Wi-Fi Aware 部署（2026-11-23）

用户已刷Android17 ROM，并要求使能Aware、替换高通固件/HAL/服务、安装APK。目标TB323FU_US、canoe、SDK37、slot_a，系统TB323FU_ROW_OPEN_UD_Q00037.0_A17_ZUXOS_18.5.10.267_ST_261118。仅操作HA2E6NPB；未操作a0807d7、iPhone或Ubuntu。

## 备份

覆盖前的service、libwifi-hal.so、VINTF XML和amss20.bin，均备份至设备 `/data/local/tmp/nan-deploy-20261123/original/` 和Mac `/Users/mxj/workspace/nan-device-backups/2026-11-23/HA2E6NPB/original/`。原V4库及Aware声明不存在。

| 原文件 | SHA-256 |
|---|---|
| service | 3d11e0000a694180f05ff155e3a791a16ec6b7a67101c84780346ce330e7eac6 |
| libwifi-hal.so | 0602a90c34734d335b00dd1d2ce7154e5b1e5b30b8ad47e8a5226ee7f9dc47ae |
| VINTF XML | b0244c7741b58325669b372154776f5b800f6c19c73f08688ad1aaa45ba596a8 |
| amss20.bin | 2a22e73834cc6af8e563e91fc5ede40de8887351203df3940891a1c9bbb4b761 |

首次备份遇到root连接状态变化/Permission denied，未覆盖目标；重新确认uid=0后全部备份成功才部署。

## 变更

adb root/remount，临时关闭扫描和Wi-Fi、停止vendor.wifi_hal_legacy后部署：

| 目标 | 新文件SHA-256 |
|---|---|
| /vendor/bin/hw/android.hardware.wifi-service | b2af3812b34720cf85fe09853f4428fb73ba0211785b6968d9c8b929d96668b2 |
| /vendor/lib64/android.hardware.wifi-V4-ndk.so | 26c5f7d42131d8a2eb0381e8b1e428f48e21b35edd2cad482351c8521c279bc7 |
| /vendor/lib64/libwifi-hal.so | 0cd80cb19390ebb38cf46f982359bf53545e8f2ed386a244550c782237c6a546 |
| /vendor/firmware_mnt/image/peach/amss20.bin | b6f89c304e8f01a276413bcc112807638f7d2340bb4c04b85b108799a3cbdae6 |

HAL文件来自Downloads已验证版本，固件来自Downloads/buildpath0 2。添加 `/vendor/etc/permissions/android.hardware.wifi.aware.xml`，Wi-Fi VINTF fragment从3改4，service0755、so/XML0644、restorecon；原V3库保留。固件分区临时rw、校验同目录新文件后改名替换、sync恢复ro。

安装当前已有 `NAN-Android/app/build/outputs/apk/debug/app-debug.apk`（Nov20构建，非本轮重新编译），adb install -r返回Success。为App授予附近Wi-Fi、精确/粗略定位及ACCESS_LOCAL_NETWORK权限，开启系统定位。恢复扫描/Wi-Fi并整机重启。

## 验证

- sys.boot_completed=1，vendor.wifi_hal_legacy=running。
- pm list features出现android.hardware.wifi.aware。
- Binder getInterfaceVersion返回4（service call IWifi/default 16777215）。
- dumpsys wifi当前固件版本为WLAN.GNG.2.0-01536-QCAGNGSWPL_V2_TO_SILICONZ-1.197458.2。
- 设备新产物hash均与输入一致；HAL进程maps实际加载V4 NDK及libwifi-hal.so；固件分区ro。
- App启动通过，唤醒后点击开始发布，UI确认“服务已发布”及UDP端口59194。首次点击时屏幕已息屏、未形成发布，唤醒后重新验证通过。
- 未验收跨端发现、新PIN配对、Bootstrap SSI、NDP、吞吐/长稳；未抓空口。本机部署通过不代表所有互通功能通过。

## 恢复

Mac与设备均保存rollback.sh；恢复前先adb root、adb remount，再执行 `/data/local/tmp/nan-deploy-20261123/rollback.sh` 并整机重启。脚本恢复四个原文件、删除本轮新增V4库及Aware声明，App安装和运行时权限不自动撤销。脚本已保存但未执行；不把它视为已实测恢复。原厂固件及HAL原件均保留，不提交二进制/敏感日志。
