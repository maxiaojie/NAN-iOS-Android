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

## 对照：同一镜像也存在正确的两阶段调用

另一个函数路径在 `0x01a5ded8` 传空指针查询长度；在 `0x01a5df00` 可再传有效 buffer 执行填充。该路径还有“只计算长度”模式，跳过填充本身可能是正常预计算，不能只看到空指针就认定错误。

两套上层函数的指针相邻出现在原文件 `0x000b070c`（`0x0184fb20`）与 `0x000b0710`（`0x01a5dcec`）；还找到了直接调用位置。究竟由哪个服务类型、配置或发送阶段选择它们，尚未完全还原，不能仅凭有两个函数就分别命名为 Publish 和 Subscribe，也不能说镜像中的所有 SDF 都经过缺少填充的那一路。

因此这轮比“23 字节长度相同”更进一步：已经识别到对应属性 builder、真实长度查询以及可疑累计点，但仍缺运行时路径确认。

## 下一步给固件源码侧核对的具体位置

请在与上述镜像版本匹配的固件源码/map 中反查 `0x01a4f6d8`、`0x0184fb20`、`0x0180f51c`、`0x01a5dcec` 及相邻接口表，取得实际函数名与文件。优先核对 `0x0184fd28` 对应的代码是否只调用长度模式，后面遗漏填充、提前移动 cursor，或采用了应由另一阶段填充但实际未完成的约定。

运行时增加不含密钥的日志：服务类型、STA 状态、实际 builder 标识、buffer 是否为空、Extended WLAN Infrastructure 预估/实际写入长度、写前后 cursor、最终返回长度和 Tx 长度。若确认预估+23、实际填充+0、最终 Tx 仍+23，才可确定遗漏填充/长度回退缺陷；如果已经写入正确头，之后变零，则转查覆盖和提交路径。

本轮未做二进制补丁、未绕过签名、未刷写，不声称 App 层截掉 23 字节可以修复。应修复真正的构帧路径，并回归 STA 开关、Publish/Subscribe、首次配对、缓存验证及 NDP。

## 可复核产物

本地目录包含：原始 amss20.bin、提取的内层 ELF、`elf-layout.json`、`disassemble_hexagon.py`、`index_calls.py`、完整分段反汇编，以及四份重点片段 `extended-wlan-writer.txt`、`vendor-type33-writer.txt`、`sdf-tail-candidate.txt`、`alternative-two-pass-caller.txt`。

为准备工具曾启动完整 LLVM 下载，因本地已存在可用 Hexagon 解码器而取消，未依赖该未完成安装。所有固件分析在 Mac 完成；Ubuntu SSH 主机密钥校验未通过，未建立连接，也未改变其网络。先前抓包证据见 [23 字节属性候选与帧号](sta-zero-tail-extended-wlan-hypothesis.md)。
