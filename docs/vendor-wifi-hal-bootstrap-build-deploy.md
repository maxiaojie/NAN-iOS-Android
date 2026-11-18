# Bootstrap SSI：Vendor Wi-Fi HAL 修改、编译与部署

日期：2026-11-18。关联问题 P24。本文说明本次实验的修改路径、可复现编译命令，以及助手实际执行的部署步骤。结果和抓包见 [实机验证](vendor-v4-bootstrap-validation.md)。

## 本轮改了哪一层

产品 Framework 为 Android 17，Vendor 为 Android 16 基线（由用户说明）。Framework 已把 Bootstrap 的设备信息写入 AIDL Parcel，原 Vendor Wi-Fi AIDL V3 接收定义没有该字段，因而跳过信息。高通已有的 legacy 结构体和下层路径具备发送 Bootstrap SDEA SSI 的能力。

本次由用户在隔离源码服务器修改、编译 Vendor，提供两个产物到 Mac 下载目录；助手未访问该服务器，未取得完整私有 diff 或实际编译终端记录。已确认产物依赖 V4 NDK、运行报告 V4，新增转换日志显示 SSI 已复制，空口和 iOS 接收均成功。以下源文件修改按会话讨论、用户编译错误截图和实际产物结果还原；命令是对应操作方法，不能据此断言用户实际输入了每一条。

## 1. 给输入 AIDL 添加 SSI 字段

路径均相对于用户 Vendor 源码根目录。修改：

```text
hardware/interfaces/wifi/aidl/android/hardware/wifi/NanBootstrappingRequest.aidl
```

在已有字段末尾，即 discoverySessionId 后追加：

```aidl
    @nullable byte[] serviceSpecificInfo;
```

保留旧字段的顺序和类型，新增字段位置与 Android 17 Framework 的标准定义一致。此处只传 SSI 字节，不由 App 或 AIDL 手工构造整段 SDEA 属性。

不要手工创建一个只有该文件的 aidl_api/android.hardware.wifi/4 目录；冻结目录是整套接口的版本副本，不会从目录 3 自动继承缺少的文件。

## 2. 更新并冻结成私有 V4

修改：

```text
hardware/interfaces/wifi/aidl/Android.bp
```

在 name 为 android.hardware.wifi 的 aidl_interface 中，将 frozen 设置为 false，保留原有 versions_with_info 的 1～3。然后在源码根目录、已选择正确产品的构建终端执行：

```bash
source build/envsetup.sh
# 执行本产品平时使用的 lunch 命令；不能用其他产品名称代替。
m android.hardware.wifi-update-api
m android.hardware.wifi-freeze-api
```

前提是该树最新冻结版本为 3。update-api 根据 srcs 生成完整 current；freeze-api 创建完整 4 目录和 .hash，并在 Android.bp 登记版本 4、设置 frozen=true。若树中已存在 V4，则不能照此把“下一个版本”当成 V4。

检查目录：

```text
hardware/interfaces/wifi/aidl/aidl_api/android.hardware.wifi/current/
hardware/interfaces/wifi/aidl/aidl_api/android.hardware.wifi/4/
```

本轮按单字段私有 V4 实验处理，不宣称接口定义或 hash 与完整标准 AOSP V4 一致。完整标准 V4 还有其他 NAN 类型变化，后续兼容方案另行评估，不复制标准 hash 来标识不同内容。

## 3. 服务改为使用 V4 NDK 接口库

检查并修改实际参与产品编译的服务配置；AOSP 默认路径是：

```text
hardware/interfaces/wifi/aidl/default/Android.bp
```

让 Wi-Fi HAL 服务及相关服务库的接口依赖使用：

```bp
"android.hardware.wifi-V4-ndk",
```

替代原 V3 依赖。若厂商分支覆盖了服务模块，需改实际模块，不能仅改未参与编译的 AOSP 示例配置。避免同一个最终服务混用不同版本的同名 AIDL 类型。

本轮下载产物已直接检查 ELF DT_NEEDED，确实依赖 android.hardware.wifi-V4-ndk.so。

## 4. 补齐 AIDL 到高通 legacy 的 SSI 转换

文件：

```text
hardware/interfaces/wifi/aidl/default/aidl_struct_util.cpp
```

函数：

```cpp
convertAidlNanBootstrappingInitiatorRequestToLegacy(...)
```

保留原有字段转换，补充 serviceSpecificInfo → sdea_service_specific_info[_len]。@nullable byte[] 的 NDK 类型是 std::optional<std::vector<uint8_t>>，不是裸 vector。用户曾直接调用 .size()/.data()，在 3693/3694 行触发编译错误；需先检查 optional 是否有值。

下面为功能等价的参考片段，不是从隔离服务器导出的原始补丁。以 legacy 成员为固定字节数组、长度字段能容纳数组容量为前提：

```cpp
legacy_request->sdea_service_specific_info_len = 0;

if (!aidl_request.serviceSpecificInfo.has_value()) {
    LOG(INFO) << "NAN Bootstrap Request: serviceSpecificInfo is null";
} else {
    const auto& info = aidl_request.serviceSpecificInfo.value();
    const size_t capacity =
            sizeof(legacy_request->sdea_service_specific_info);

    if (info.size() > capacity) {
        LOG(ERROR) << "NAN Bootstrap Request: SDEA SSI exceeds capacity"
                   << ", length=" << info.size()
                   << ", capacity=" << capacity;
        return false;
    }

    if (!info.empty()) {
        memcpy(legacy_request->sdea_service_specific_info,
               info.data(), info.size());
        // 按厂商头文件中该长度字段的实际类型赋值。
        legacy_request->sdea_service_specific_info_len = info.size();
    }

    LOG(INFO) << "NAN Bootstrap Request: copied SSI to legacy request"
              << ", input length=" << info.size()
              << ", sdeaLength="
              << static_cast<size_t>(
                         legacy_request->sdea_service_specific_info_len);
}
```

日志使用 android-base/logging.h，memcpy 需要相应头文件。若编译选项要求显式窄化转换，应在检查长度字段可表示范围后转换为厂商头文件定义的类型；不能未经核对就假定类型。sizeof 适用于固定数组，不适用于指针。

本轮真实运行日志：

```text
NAN Bootstrap Request: copied SSI to legacy request, input length=86, sdeaLength=86
```

原样传递 Framework 已提供的 SSI，不额外重复添加 OUI、协议头或 TXT TLV 头，不误写入普通 service_specific_info。高通库、驱动和 Firmware 本轮未替换；是否还修改了用户隔离树的其他文件，助手没有完整 diff 可核对。

## 5. 同步 VINTF 声明

AOSP 默认源文件：

```text
hardware/interfaces/wifi/aidl/default/android.hardware.wifi-service.xml
```

将 android.hardware.wifi 条目设为：

```xml
<hal format="aidl">
    <name>android.hardware.wifi</name>
    <version>4</version>
    <fqname>IWifi/default</fqname>
</hal>
```

保留原 manifest 根节点及其他内容。实际服务 Android.bp 的 vintf_fragments 决定使用哪个 XML；如厂商覆盖则以实际文件为准。仅修改 XML 不会改变运行服务的版本号。

本轮用户提供的是服务和 so，助手在部署时将设备原 XML 的该版本从 3 改为 4；未声称用户提供了重新编译的 XML。

## 6. 编译服务与接口库

在已完成 envsetup 和本产品 lunch 的源码根目录，可明确执行：

```bash
m android.hardware.wifi-service android.hardware.wifi-V4-ndk
```

如果本产品使用默认目录中的服务模块，也可以：

```bash
cd hardware/interfaces/wifi/aidl/default
mm
```

mm 编译该目录模块及必要依赖；它不负责刷入设备。若只重新打包已有 vendor.img 而没有重建模块，AIDL 与转换修改不会因此自动生效。

重点产物：

```text
out/target/product/<产品名>/vendor/bin/hw/android.hardware.wifi-service
out/target/product/<产品名>/vendor/lib64/android.hardware.wifi-V4-ndk.so
out/target/product/<产品名>/vendor/etc/vintf/manifest/android.hardware.wifi-service.xml
```

转换函数可能静态链接进服务或位于厂商动态库中，以实际模块为准。本轮两个新产物部署后出现新增日志并成功发出 SDEA，说明本次转换代码已随部署产物生效。Wi-Fi Vendor HAL 不在 com.android.wifi.apex 中，本轮无需替换该 APEX，也未重编译部署 Framework。

## 7. 本轮实际部署到 a0807d7

Mac 输入产物：

```text
/Users/mxj/Downloads/android.hardware.wifi-service
/Users/mxj/Downloads/android.hardware.wifi-V4-ndk.so
```

设备原先已有 root adbd；执行 adb remount 成功。以下列出本轮操作流程；重复部署时应另建备份目录，避免覆盖首次原版备份。

```bash
adb -s a0807d7 root
adb -s a0807d7 wait-for-device
adb -s a0807d7 remount
```

原文件已备份到 Mac：

```text
/Users/mxj/workspace/nan-capture-analysis/2026-11-18/vendor-v4-backup-a0807d7/
    android.hardware.wifi-service       # 原 V3 服务
    android.hardware.wifi-service.xml   # 原声明 3
    android.hardware.wifi-service-v4.xml # 部署用声明 4
```

备份命令对应：

```bash
adb -s a0807d7 pull /vendor/bin/hw/android.hardware.wifi-service <新备份目录>/android.hardware.wifi-service
adb -s a0807d7 pull /vendor/etc/vintf/manifest/android.hardware.wifi-service.xml <新备份目录>/android.hardware.wifi-service.xml
```

复制原 XML 为新文件，只将 Wi-Fi 版本 3 改为 4，保留其余内容。实际停止服务并部署：

```bash
adb -s a0807d7 shell stop vendor.wifi_hal_legacy

adb -s a0807d7 push /Users/mxj/Downloads/android.hardware.wifi-V4-ndk.so /vendor/lib64/android.hardware.wifi-V4-ndk.so
adb -s a0807d7 push /Users/mxj/Downloads/android.hardware.wifi-service /vendor/bin/hw/android.hardware.wifi-service
adb -s a0807d7 push /Users/mxj/workspace/nan-capture-analysis/2026-11-18/vendor-v4-backup-a0807d7/android.hardware.wifi-service-v4.xml /vendor/etc/vintf/manifest/android.hardware.wifi-service.xml

adb -s a0807d7 shell chmod 0755 /vendor/bin/hw/android.hardware.wifi-service
adb -s a0807d7 shell chmod 0644 /vendor/lib64/android.hardware.wifi-V4-ndk.so /vendor/etc/vintf/manifest/android.hardware.wifi-service.xml
adb -s a0807d7 shell restorecon /vendor/bin/hw/android.hardware.wifi-service /vendor/lib64/android.hardware.wifi-V4-ndk.so /vendor/etc/vintf/manifest/android.hardware.wifi-service.xml
adb -s a0807d7 shell sync
adb -s a0807d7 reboot
```

原 V3 NDK 库未删除，设备部署前没有 V4 NDK 库。本轮没有全量刷 vendor.img，没有操作另一台 Android 303fdedc。需确保每一步成功，尤其 root/remount 失败时不能继续假定 push 已写入。

## 8. 重启后的检查

```bash
adb -s a0807d7 wait-for-device
adb -s a0807d7 root
adb -s a0807d7 wait-for-device
adb -s a0807d7 shell getprop sys.boot_completed
adb -s a0807d7 shell getprop init.svc.vendor.wifi_hal_legacy
adb -s a0807d7 shell service call android.hardware.wifi.IWifi/default 16777215
adb -s a0807d7 shell pidof android.hardware.wifi-service
adb -s a0807d7 shell cat /vendor/etc/vintf/manifest/android.hardware.wifi-service.xml
```

实际结果：boot_completed=1，服务 running，版本查询 Parcel 为 00000000 00000004，XML 版本 4。查询进程 PID 后检查其 /proc/<PID>/maps，本轮 PID=1266，实际加载 V4 NDK 库。普通 shell 的一次查询曾显示服务不存在，切回 root 后正常；不能单凭该次输出判断服务未启动。

本轮产物 SHA-256，设备与 Mac 一致：

| 产物 | SHA-256 |
|---|---|
| 服务 | b2af3812b34720cf85fe09853f4428fb73ba0211785b6968d9c8b929d96668b2 |
| V4 NDK 库 | 26c5f7d42131d8a2eb0381e8b1e428f48e21b35edd2cad482351c8521c279bc7 |

版本和加载检查之后，依次验收：HAL 转换日志非零长度 → 空口 Bootstrap Follow-up 包含 SDEA → iOS 非空解析设备 TXT → 首次 PIN 配对 → 缓存验证 → NDP 与双向心跳。上述本轮均已通过，详细帧号、长度和持续时长见实机验证文档。

## 9. App 权限与回退

HAL 修复后首轮 NDP 已建立，但 Android UDP 首包因缺少 Android 17 ACCESS_LOCAL_NETWORK 权限返回 EPERM。App 补齐 manifest 声明与 MainActivity 运行时申请，assembleDebug/lintDebug 通过，安装并授权后持续双向通信成功。这是独立的 App 权限修复，不是继续修改 Vendor。

若需回退本轮 HAL 实验，在 root/remount 后停止同一服务，恢复首次备份的原服务和版本 3 XML，移除本轮新增的 V4 NDK 库，恢复权限/标签并重启。原 V3 NDK 库保留可供原服务使用。本文只给出回退方法，本轮未执行回退；设备仍保留验证成功的 V4 实验产物。
