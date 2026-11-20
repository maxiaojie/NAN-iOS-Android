# a0807d7 amss20.bin：NAN 23 字节候选路径反汇编

2026-11-20，关联 P07。按用户要求从设备只读导出并进行反汇编，未修改 App、HAL、驱动、固件或配对记录，未运行固件，不切换网络。

## 实际镜像和分析文件

- 设备：a0807d7，TB323FU，Android 17，构建 `maxj9_227.2611121127C`。
- 导出路径：`/vendor/firmware_mnt/image/peach/amss20.bin`。
- 大小：7,471,168 字节。
- SHA-256：`2a22e73834cc6af8e563e91fc5ede40de8887351203df3940891a1c9bbb4b761`。
- 镜像内版本字符串：`WLAN.GNG.2.0-01536-QCAGNGSWPL_V2_TO_SILICONZ-1.185753.3`；variant 为 `7880.wlanfw.eval_v2_TOQ`。这是镜像字符串，未单独通过运行时加载记录证明所执行的每个段与它完全对应。
- 本地目录：`/Users/mxj/workspace/nan-firmware-analysis/2026-11-20/a0807d7/`。原始二进制、完整反汇编及版本元数据留在此处，不进入 Git。

镜像外层 ELF 的 e_machine 声明为 ARM（40），但偏移 `0x80000` 有第二个 ELF，e_machine=164（Hexagon）、入口 `0x014ed7e8`。两者均没有节表。不能仅根据外层声明把整个文件按 ARM 解释；该尝试的输出不用于本报告。

内层程序段给出了加载地址，已据此提取镜像和生成分析用节映射。原始文件保持不变，`*.analysis.elf` 仅用于分析，不可刷机。使用本机 QEMU 11.1.3 的 Hexagon 解码器、v73 配置、通用 ELF loader 和 `-S` 暂停状态，通过 monitor 解码指令。**没有执行固件**。解码器配置不等于确认芯片实际 CPU 修订为 v73。

主要可执行段虚拟范围 `0x017f1000..0x01ae7000`，对应原文件偏移起点 `0x13a000`。其静态输出 `hexagon-load12.disasm.txt` 约 36MB。可执行标记段也可能夹杂数据；本文依靠连续函数、调用关系和字段写入识别，不能把所有解码输出都当作可靠代码。Hexagon 同一 packet 内指令并行执行，PC 相对目标按 packet 起点计算。

## 已确认存在 Extended WLAN Infrastructure 填充函数

无符号函数 `0x01a4f6d8`，对应原文件偏移 `0x3986d8`，实际包含以下写入：

| 虚拟地址 | 可确认行为 |
| --- | --- |
| `0x01a4f754` | 准备返回总长 `0x17`（23） |
| `0x01a4f760` | buffer[0] 写入 `0x1e` |
| `0x01a4f758..764` | 调用清零路径，参数指向 buffer+3、长度 12，符合两个 MAC 字段 |
| `0x01a4f768` | buffer[15] 写入角色 1 |
| `0x01a4f77c..7ac` | 填充操作类别、信道等字段 |
| `0x01a4f7b0..7bc` | 四字节 Beacon 信息清零 |
| `0x01a4f7c0` | buffer[1] 写入 `0x14` |
| `0x01a4f7c8` | buffer[2] 写入 `0x00` |

这不是仅搜索到常量 23：属性头、20 字节内容布局、角色和总长均吻合 Extended WLAN Infrastructure。函数先检查全局上下文及接口集合，其中包含接口类型、状态位和外部 ROM helper 条件；未恢复这些结构及 ROM helper 的原始符号，不能给每个判断强行命名。

函数支持两种用法：在满足前置条件时，传空 buffer 只返回所需长度 23，传有效 buffer 则写入完整属性再返回 23。无可用状态/上下文等分支会返回 0。可读伪代码如下，仅概括语义，不代替原始源码：

```c
unsigned build_ext_wlan_infra(unsigned char *buffer) {
    context = find_eligible_infrastructure_context();
    if (!context) return 0;
    if (buffer != NULL) {
        // 写入 1e 14 00、两个清零 MAC、角色 1、信道及 Beacon 信息。
        fill_extended_wlan_infrastructure(buffer, context);
    }
    return 23;
}
```

## 关键嫌疑点：末尾只计长度，未看到对应填充

在另一个无符号函数 `0x0184fb20` 内：

1. `0x0184fd20` 调用 `0x01845e34`。后者明确写入 ID=`0xdd`、OUI=`00:00:f0`、type=`0x33`，通常 payload 长度 14，加头共 17。它对应既有异常 PCAP 的最后合法 Vendor 属性，属性本身包含的零与额外零尾是两回事。
2. 紧接着 `0x0184fd28` 调用上述 `0x01a4f6d8`，同一 packet 明确设置 `R0=0`，即 **只查询长度**。
3. `0x0184fd38` 将查询所得长度加入累计长度；满足容量条件时，累计结果继续用于后续处理和函数返回。
4. 在该函数的这条后续路径内，**尚未看到向有效 buffer 再调用 `0x01a4f6d8` 的对应填充动作**。

它与空口“最后合法 type33 Vendor 属性之后，恰好多 23 个零”高度对应：已计入 Extended WLAN Infrastructure 的长度，但没有把属性写进去，是目前非常具体的静态嫌疑。

不能因此直接宣布根因已完全证实：需要确认运行时异常 SDF 走的确是此函数、返回长度如何进入最终发送描述符，以及后续或间接调用是否另有填充/覆盖。尚未完成整个发送链的动态追踪。

## 对照：同一镜像的 Follow-up 路径存在两阶段调用

后续通过配套 Data20.msc 日志字典，确认此处属于 `NANMAC_BuildFollowupFrame`；该函数路径在 `0x01a5ded8` 传空指针查询长度；在 `0x01a5df00` 可再传有效 buffer 执行填充。该路径还有“只计算长度”模式，跳过填充本身可能是正常预计算，不能只看到空指针就认定错误。

两套上层函数的指针相邻出现在原文件 `0x000b070c`（`0x0184fb20`）与 `0x000b0710`（`0x01a5dcec`）；还找到了直接调用位置。后续已通过日志字典确认它们分别是 BuildSDFFrame 和 BuildFollowupFrame，详见本文追加部分；具体运行时选择仍待确认，不能将它们分别命名为 Publish 和 Subscribe，也不能说镜像中的所有 SDF 都经过缺少填充的那一路。

因此这轮比“23 字节长度相同”更进一步：已经识别到对应属性 builder、真实长度查询以及可疑累计点，但仍缺运行时路径确认。

## 下一步给固件源码侧核对的具体位置

请在与上述镜像版本匹配的固件源码/map 中反查 `0x01a4f6d8`、`0x0184fb20`、`0x0180f51c`、`0x01a5dcec` 及相邻接口表，取得实际函数名与文件。优先核对 `0x0184fd28` 对应的代码是否只调用长度模式，后面遗漏填充、提前移动 cursor，或采用了应由另一阶段填充但实际未完成的约定。

运行时增加不含密钥的日志：服务类型、STA 状态、实际 builder 标识、buffer 是否为空、Extended WLAN Infrastructure 预估/实际写入长度、写前后 cursor、最终返回长度和 Tx 长度。若确认预估+23、实际填充+0、最终 Tx 仍+23，才可确定遗漏填充/长度回退缺陷；如果已经写入正确头，之后变零，则转查覆盖和提交路径。

本轮未做二进制补丁、未绕过签名、未刷写，不声称 App 层截掉 23 字节可以修复。应修复真正的构帧路径，并回归 STA 开关、Publish/Subscribe、首次配对、缓存验证及 NDP。

## 可复核产物

本地目录包含：原始 amss20.bin、提取的内层 ELF、`elf-layout.json`、`disassemble_hexagon.py`、`index_calls.py`、完整分段反汇编，以及四份重点片段 `extended-wlan-writer.txt`、`vendor-type33-writer.txt`、`sdf-tail-candidate.txt`、`alternative-two-pass-caller.txt`。

为准备工具曾启动完整 LLVM 下载，因本地已存在可用 Hexagon 解码器而取消，未依赖该未完成安装。所有固件分析在 Mac 完成；Ubuntu SSH 主机密钥校验未通过，未建立连接，也未改变其网络。先前抓包证据见 [23 字节属性候选与帧号](sta-zero-tail-extended-wlan-hypothesis.md)。


## 三处调用点所在函数已由日志字典确认（同日追加）

后续只读导出同目录 `Data20.msc`（2,024,278 字节，SHA-256 `1742dbe53887a239bb08d1a9824be1514a334ceebc29ab1e702eb8ec613e6e26`）。它是可读日志字典，首行 `VERSION:268928976`；原始字典仍只存本地，不入 Git。

将调用点附近的固件日志标识与字典核对，得到下表。源码行号是**附近长度不足日志所在行**，不是函数定义行或空指针调用的精确源码行。

| 调用点 | 所在函数入口 | 字典确认的函数名 | 源码/日志行 | 日志标识 / 字典 ID |
| --- | --- | --- | --- | --- |
| `0x0184cb44` | `0x0184ca84` | `NANBCN_PrepareBeaconFrameExtended` | `wlan_nan_beacon_tx.c:128` | `0xf2032c90` / 29385 |
| `0x0184fd28` | `0x0184fb20` | `NANMAC_BuildSDFFrame` | `wlan_nan_mac_ext.c:2171` | `0xf2033450` / 29509 |
| `0x01a5ded8` | `0x01a5dcec` | `NANMAC_BuildFollowupFrame` | `wlan_nan_mac_ext.c:2354` | `0xf2033460` / 29510 |

这三组标识的数值关系均吻合 `((token - 0xf2000000) >> 4) == (dictionaryId & 0x3fff)`；本次只用于核对这批字典与固件，不将其泛化为所有高通日志版本的解码规则。三处日志的参数数量、WlanInfra 属性长度不足分支及函数类别也相互对应。

### Beacon：NANBCN_PrepareBeaconFrameExtended

可见代码更新帧内偏移 24～31 的 8 字节时间值、维护信息元素长度、填充 Vendor 扩展内容。`0x0184cb44` 查询 Extended WLAN Infrastructure 长度，容量足够时在 `0x0184cb60` 传 buffer 实际写入。这是 Beacon 扩展准备路径，不是此次 Publish SDA/SSI 的构造入口。具体 Beacon 子类和完整原型仍未恢复。

### 通用 SDF：NANMAC_BuildSDFFrame

入口四个参数的可见使用可概括为：`R0` 控制是否执行构建分支，`R1` 为帧 buffer，`R2` 为已用长度，`R3` 为容量。主分支以 `buffer + used` 作为追加位置，逐项追加 NAN 属性，保存帧指针/长度并返回新增长度；这是逆向整理的语义，不是已获取的原始 C 原型。

这一函数包含可疑的 `0x0184fd28`：在最后 type33 Vendor 属性后只查 Extended WLAN Infrastructure 长度并累计，当前函数后续未看到实际写入。精确函数名现在可直接交给固件源码侧搜索。**不能仅根据函数名认定它专门处理 Publish，或已经证明用户异常的 Publish/Subscribe 一定调用此入口。**

### Follow-up：NANMAC_BuildFollowupFrame

入口 `R1` 为零时进入只计算长度模式；有 buffer 时，追加位置为 buffer + 已用长度。`0x01a5ded8` 先空指针查询；容量足够且为实际构建模式时，`0x01a5df00` 再传 buffer 填充。因此它的两次调用并不矛盾。

此处处理的是 NAN Follow-up 组帧，不能把它命名为“正常 Subscribe builder”，也不能仅凭它正常就判定 Publish/Subscribe 使用了错误函数。上层 `0x0180f0c8`/`0x0180f39c` 涉及服务记录/帧 buffer 遍历，但配套字典部分日志明确属于 Follow-up；仍需完整调用链和运行时消息类型才能划分所有发送场景。

下一步应按以上**实际函数名及匹配版本源码日志行**核对 `NANMAC_BuildSDFFrame` 中 WlanInfra 的长度查询后是否遗漏填充，并确认 Publish/Subscribe 的运行入口。本轮已把函数用途从推断推进到日志字典确认，但没有取得源码、调试符号或运行时调用轨迹，P07 仍未标记修复。


### 0x01a4f6d8 的源码位置已定位，原始符号名尚未恢复

函数内 `0x01a4f7dc..7e4` 引用日志标识 `0xf2032080`，对应 Data20.msc 消息 29192：`[ wlan_nan_attr_ext.c : 1157 ] STA_CHAN NULL!`。因此可以将这一 Extended WLAN Infrastructure builder 定位到 **wlan_nan_attr_ext.c 的 1157 行附近**；该行是函数内部日志行，并非定义起始行。另一个断言路径传入 `0x46d`（1133），与该范围相容，但不单独将这个数字当作已解析的源码行号。

与前面三个调用者不同，此日志没有打印函数名，现有 ELF 没有可用的常规符号表，所以目前不能给出经过证实的原始 C 函数名。可以用语义名 `build_ext_wlan_infra` 标注分析，但它只是逆向命名，不是恢复出的符号。固件源码侧应先查 `wlan_nan_attr_ext.c` 中 `STA_CHAN NULL!`，或用同版本 map/未剥离 ELF 对照虚拟地址 `0x01a4f6d8`。
