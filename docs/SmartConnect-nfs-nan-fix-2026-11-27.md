# SmartConnect NFS 经 NAN 的适配与验证（2026-11-27）

用户将当前任务切换为先解决 NFS；后续性能测试统一使用独立 NAN-Android / NAN-iOS（AwareBridge），不再给 SC 临时接入 iperf。本次无吞吐测试，不覆盖原10次测速失败记录。

## 环境与原问题

HA2E6NPB / TB323FU_US / Android17 SDK37，FW197458.2、驱动5.2.1.147N；iPhone12 / iOS27.2.1。平板保留 Pixel_2564 STA，iPhone也保持原Wi-Fi。使用SmartConnect产品源码：Android `/Users/mxj/workspace/master_android/readyfor`，iOS `/Users/mxj/workspace/SmartConnectIoS`。原NAN集成及其他本地修改保留，未向公司Gerrit提交。

原10轮SC控制连接可上线，但有173条NFS挂载失败和73条服务启动失败。NFS原有socket路径未完成NAN适配；控制WebSocket经Network framework成功不能证明NFS原生socket可直接访问iOS NAN。

## 已确认的原因与修改

1. **Android把IPv6错误拆成`fe80`。** `NfsClient.mount()`以第一个冒号拆`host:export`。新增`NfsServerAddress`，格式化为`[fe80::…%aware_data0]:/Photos`，统一修正挂载、根路径和旧路径解析；保持IPv4键格式，兼容旧的未加括号IPv6绝对导出路径。
2. **DT2路径只支持IPv4。** 原生`libnfs/lib/socket.c`在AF_INET6且SOCKET_DT2时明确返回`ipv6 is not supported by p2p socket`；NAN握手改为TCP/TLS(type2)，不生成IPv4 DT2 multipath列表。仅按networkType10选择，不关闭其他网络的DT2功能。
3. **iOS NFS需要Network framework。** 原生ShareHubServer仍只绑定IPv4loopback 127.0.0.1；新增与当前SC主route绑定的NFS TCP代理。原生libnfs/ShareHub保持原有TLS与指纹校验，Network framework承载无线TCP，按既有配对设备添加连接，数据代理使用bulk。接受端及发起端均支持多条TCP连接；SC主route回收时子listener、转发任务与pending read同步取消。异步完成回调不阻塞主线程。
4. **作用域不能照搬对端。** Android使用本机SC观测到的对端IPv6及`%aware_data0`，不采用iOS自报的`%nan0`；原生`getaddrinfo()`结果转换补传`sin6_scope_id`。
5. **Android TLS地址缓冲区仅16字节。** 修正前端解析后暴露潜在内存错误：TLS已完成且指纹验证后，`ssl_connect()`执行`strcpy(sz_ip,remoteIP)`，触发FORTIFY。扩大为NI_MAXHOST，并先校验长度，再拷贝完整地址；没有截断IPv6或关闭FORTIFY。
6. **iOS服务启动的所有权与指纹登记。** 让启动worker持有shared_ptr，防止5秒超时后释放仍在worker使用的serverInfo；修正错误的2秒日志。首次服务真正启动后再登记客户端指纹，再公布NAN端口。超时故障注入分支未做真机验收。
7. **Android外部图片预览兼容。** 将MIME扩展名查找统一为Locale.ROOT小写，兼容PhotoKit返回的`.PNG`等大写后缀；这一步本身未消除Google Photos空白预览。同一测试PNG通过普通FileProvider能显示，通过NFS虚拟文件描述符则空白，因此只在NAN的iOS图片打开路径中，先在IO线程完整读取NFS图片并核对stat大小，再通过私有缓存的普通FileProvider交给查看器。不改变其他网络的原预览路径。失败删除未完成文件；成功副本在下次打开时清理超过24小时的缓存。没有证据将空白精确归因于Google Photos中的某个系统调用。

新增NFS代理明确使用已认证NFS WebSocket消息提供的remoteIp。初版误用`IOSSCAPI_GetIp`，源码确认它返回本机IP，出现“No established SC NAN peer for NFS”；已取消这一路径，不通过模糊IP匹配或任取唯一连接绕过身份绑定。代理只关联已建立SC主连接，排除短时反向验证子连接。

## 构建及修改范围

- Android新增5项地址解析回归测试；NFS client/server模块共76项测试通过，完整productDebug APK通过。
- JDK21；公司Artifactory401时使用上次已下载依赖的本地Maven init script。命令：
  `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew --offline --init-script /Users/mxj/nan-ios-tools/smartconnect-local-maven.gradle :app:assembleProductDebug`
- iOS沿用既有NANValidation主App签名工程，iphoneos/TestDebug完整构建、覆盖安装通过。命令：`xcodebuild -workspace out/nan-validation-20261125/SmartConnect.xcworkspace -scheme NANValidation -configuration TestDebug -sdk iphoneos -destination id=00008101-0016212414D0001E -derivedDataPath out/nan-validation-20261125/DerivedData build`。只验证主App，不把签名中未包含的File Provider扩展、App Group、APNs作为通过。
- 不修改ROM/HAL/Firmware，不清除系统配对。临时root只用于Android诊断与ContentProvider读取，正常SC App仍运行在普通App UID。
- 增量补丁：[Android](SmartConnect-nfs-nan-android.patch)、[iOS](SmartConnect-nfs-nan-ios.patch)，以本轮开始时已有NAN集成的本地源码为基础，已在重建的基线副本上执行`git apply --check`通过。Android补丁17个文件，iOS补丁7个文件；它们是本轮NFS增量，并非可独立替代完整NAN集成的补丁。Android基于HEAD `1f030fcc641ed1eb869252ea36fcc53c43081daa`及本轮前的本地修改；iOS基于HEAD `092a1661e2e8e6842a7997ff2d257421467b1f1a`及本轮前的本地修改。

## 真机证据与失败保留

- 17:11:41 iPhone的NFS client挂载Android成功。
- Android修正后的TLS握手已成功，但17:11:43、17:12:05在`ssl_connect`复制IPv6地址时App崩溃。堆栈`libnativenfsclient.so+0x36f0d0`定位到`ssl/sslsocket.c:227`，为`FORTIFY: strcpy: prevented 24/25-byte write into 16-byte buffer`。这是App原生库错误，不是Wi-Fi固件CrashDump；失败包已由修正版替换。
- 修复后17:17:29 iOS再次挂载成功，17:17:31 Android返回nativeInit SUCCESS及MOUNT_SUCCESS；Android修正版native BuildId为31dd2358a3f22cb91e9ea151a829395865f6dada。
- 17:24:32 iPhone通过NFS下载平板的574633字节测试PNG并正常显示；从App的NFSClientDownload目录取回文件，SHA256与原件同为`b0b54bfa44f2ec78c6837ba163235744a2133e10f41b1a0b90e437d9bf1ab3af`。缩略图亦通过实际NFS读取。
- iPhone原来拒绝SC照片权限，首次“保存图像”被PhotoKit拒绝；后在系统照片权限页显式启用。17:52:24保存测试PNG成功，PhotoKit明确`success:YES,inserts:[Asset:1]`。只以本轮创建的测试图作为反向文件验证对象，不上传个人照片、相册清单或完整日志。
- 冷启动复测中另有一次SC“无法连接”，尚未进入NFS阶段；重启iOS SC后恢复，保留失败，不归为NFS挂载成功。18:50安装最终Android包后再次出现SC“无法连接”；重启iOS后恢复，18:51重新建立NAN控制链路和NFS，未把失败轮次隐藏。后续曾因自动连接已完成后又点击到“断开连接”，导致NFS context被正常卸载；该次空读取不是NFS自发丢失，已恢复连接复查。

## 双向完整数据验证结果

| 操作 | 实际路径 | 字节数 | 结果 |
| --- | --- | --- | --- |
| iPhone读取平板文件 | 平板Download测试PNG → Android NFS/TLS → NAN → iOS Network framework/loopback → SC下载缓存 | 574633 | SHA256与原件相同，正常预览 |
| Android读取iPhone照片 | iPhone最近项目中的测试图IMG_0002.PNG → iOS NFS/TLS及Network framework → NAN → Android NFS client → DocumentsProvider读取 | 574633 | SHA256与原件及iPhone下载副本相同 |
| Android从SC相册点击iPhone图片 | 同一测试图 → NFS完整读取 → 私有缓存FileProvider → Google Photos | 574633 | 18:53:48日志确认完整读取，实际界面正常显示，缓存SHA256与原件相同 |

原件与三个接收/预览副本SHA256均为`b0b54bfa44f2ec78c6837ba163235744a2133e10f41b1a0b90e437d9bf1ab3af`。反向通过Android实际NFS DocumentsProvider的`content read`取回数据；root仅用于跨UID访问该受MANAGE_DOCUMENTS保护的测试入口，收发仍由普通App进程完成，没有用ADB代替无线传输。

PhotoKit将保存后的文件命名为IMG_0002.PNG；初次按原文件名查询返回ENOENT。改为枚举实际NFS目录，仅按本轮素材大小和新增时间匹配，使用服务端实际document_id读取后字节校验通过。没有把“挂载成功”或“相册缩略图可见”代替完整文件验证。

验证素材与哈希见[CSV](results/sc-nan-nfs-files-2026-11-27.csv)。本轮也确认多次重建SC/NDP后两端重新挂载，包括17:30、17:44、18:01和恢复主动断开后的轮次；不是正式10次NFS可靠性统计。保留上述SC冷启动失败。

## 验收边界及收尾

- 已通过：双端完整构建、76项Android NFS回归测试、两端真实TLS挂载、iPhone浏览平板目录/预览及完整读取、Android完整读取iPhone测试照片，两个方向文件SHA256一致；Android从SC相册打开iPhone测试照片并正常显示。
- 未验收：批量/长时间传输、任意文件写入与删除、后台持续性、全部网络切换、多设备并发，以及系统File Provider扩展发布签名。新增照片在Android相册页未即时出现的列表刷新行为单独保留，不冒充传输错误或已修复。
- SC冷启动连接失败仍是独立剩余问题；本次NFS修复不宣称解决所有SC连接异常，也不宣称修复之前临时iperf反向超时。
- iPhone照片权限原为“无”，为实际照片验证已在系统设置开启“完全访问”；本轮仅使用自己创建的测试图。素材保留在平板Download和iPhone照片库，供后续复现，准备阶段未使用的1MiB二进制文件已移除。
- Ubuntu本轮未使用，未切换monitor或改动其网络。
- 双端已安装NFS修正版；Android最终包包含图片预览缓存修复。iPhone短按侧键后SpringBoard lockstate实测0→1，确认锁屏。Android已恢复adbd非root（uid2000）、充电保持亮屏设置恢复0，STA仍连接Pixel_2564/5805MHz。本轮日志采集均已停止。

## 本机证据

`/Users/mxj/workspace/nan-capture-analysis/2026-11-27/nfs/` 保存修改前Patch、构建日志、native崩溃堆栈、重建基线、双端日志、原始测试PNG及收发文件。本地完整日志含产品认证字段，禁止公开上传。
