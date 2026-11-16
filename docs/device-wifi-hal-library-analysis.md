# TB323FU 实际 Wi-Fi HAL 库与 Publish 入口

## 环境与检查方式

2026-11-16，通过 USB 只读检查 a0807d7（TB323FU），固件指纹：

```text
Lenovo/TB323FU_PRC/TB323FU:17/CQ2A.260712.001/maxj9_227.2611121127C:userdebug/test-keys
```

使用设备已有 `su 0` 读取进程映射和库，没有访问隔离源码服务器，没有执行 adb root、修改 SELinux、重启 Wi-Fi 或改变发布/配对配置。结束时普通 adb shell 仍为 uid=2000。

## 实际加载的库

`android.hardware.wifi-service` 本次 PID 为 1298，完整 `/proc/1298/maps` 包含 `/vendor/lib64/libwifi-hal.so`。服务 ELF 的 `DT_NEEDED` 同样包含 `libwifi-hal.so`。该库包含厂商 NAN 实现，不能仅凭通用文件名把它认作纯包装层。

库标识：

- AArch64 ELF，704360 字节。
- GNU Build ID：`47b6b5a54709a8b090a2b029d6a7494c`。
- SHA-256：`0602a90c34734d335b00dd1d2ce7154e5b1e5b30b8ad47e8a5226ee7f9dc47ae`。

设备另有 `/vendor/lib64/libwifi-hal-qcom.so`（702048 字节，Build ID `38f9b44dcf743926420b468dbd2c9648`），也导出初始化及 Publish 符号，但本次进程完整 maps 没有它。`libwifi-hal-ctrl.so` 同样存在而未出现在该进程映射。不能因为平台为 qcom 就把 qcom 后缀的库当作当前执行库。

## 初始化与函数表赋值

地址均为本次 `libwifi-hal.so` 的 **ELF 虚拟地址**，不是 ASLR 后进程绝对地址，也不应直接当文件偏移。

| 位置 | ELF 地址 | 证据 |
| --- | --- | --- |
| 初始化导出入口 | `0x97ac0` | `init_wifi_vendor_hal_func_table`，BTI 后跳到实际代码 `0x7e2d0` |
| Publish 地址的 GOT 项 | `0x9e4b8` | packed relocation 解码为 `R_AARCH64_GLOB_DAT nan_publish_request` |
| 初始化代码读取 GOT | `0x7e54c` | `ldr x10, [x10, #0x4b8]`，页基址 `0x9e000` |
| 写入函数表 | `0x7e55c` | `str x10, [x8, #0x238]`，x8 保存传入表地址 |
| Publish 导出入口 | `0x97768` | `nan_publish_request`，BTI 后跳到实际代码 `0x674f0` |
| 请求编码调用点 | `0x676f0` | 调用 `NanCommand::putNanPublish` 的 PLT 入口 |
| 请求编码函数 | `0x5f13c` | `NanCommand::putNanPublish`，符号长度 `0xcf0` |

初始化机器码与重定位记录确认等价赋值：

```cpp
fn->wifi_nan_publish_request = nan_publish_request;
```

本次 64 位库的表项偏移为 `+0x238`，不能直接套到其他版本或 32 位库。继续追踪的调用链为：

```text
init_wifi_vendor_hal_func_table
  → 填写 Publish 函数指针
  → nan_publish_request
  → NanCommand::putNanPublish(transactionId, request, groupKey)
```

编码函数完整符号：

```text
_ZN10NanCommand13putNanPublishEtPK17NanPublishRequestPK9nanGrpKey
```

Publish 实现存在调用 `nan_pairing_derive_grp_keys` 的分支，随后把组密钥指针传给 `putNanPublish`。这是 GTK 排查入口，但本轮没有完成结构字段与所有分支条件还原，不能据此宣称找到了 Setup=false 时 GTK 矛盾的根因。

## dlopen/dlsym 分支的证据边界

服务二进制含有初始化符号、`/vendor/etc/wifi/vendor_hals` 和符号查找失败提示等字符串，但本机该目录不存在。服务通过 `DT_NEEDED` 链接 `libwifi-hal.so`；该库的 `HalTool::InitFunctionTable` 在 `0x3c550` 直接调用 `init_wifi_vendor_hal_func_table@plt`。

已确认的是当前进程加载的实现库、该库的初始化赋值和 Publish 调用链。**本轮没有动态跟踪启动时 dlopen 的实参，不能声称已观察到 `path` 的值，或者该动态加载分支本次一定执行。** 当前证据支持存在默认链接初始化路径，应结合隔离服务器上的对应构建配置核对。

## 下一步与验证限制

- STA 并发额外 23 字节：从 `putNanPublish` 的长度计算与请求编码继续对照驱动/固件收到的数据，完整空口 SDF 最终组帧位置尚未确认。
- Setup=false GTK 矛盾：检查 Publish 入口的组密钥处理、`putNanPublish` 的 pairing/security 编码，再对照驱动/固件的 CSIA、SDEA，责任层尚未确认。

本轮通过 maps、ELF 依赖/动态符号、Android packed relocations 和静态反汇编相互核对，没有进行新配对、NDP 或数据通信回归。库和完整进程资料仅保存于本机临时诊断目录，不上传仓库。
