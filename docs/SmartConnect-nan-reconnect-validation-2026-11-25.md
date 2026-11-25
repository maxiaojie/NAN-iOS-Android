# SmartConnect 已配对设备双端重连复测

日期：2026-11-25。设备 a0807d7 / TB323FU_PRC / SDK37，iPhone12 / iPhone13,2 / iOS27.2.1。代码基于本仓库8e92ce4导出的SmartConnect产品补丁。用户要求在已有配对设备页面分别从两端点击连接，报告iPhone发起曾失败。

## 结论

自动匹配alias已证明可以工作，但不能据此宣称端到端重连稳定。常规页面最初连续五轮均成功，后续冷启动仍出现配对验证成功后的NDP失败。此外，临时配对服务WSS成功不保证持续业务服务和iOS界面成功。本轮未定位到足以修复全部问题的根因，不宣称已修复。

## 常规设备页面的测试

未删除系统配对，未输入新PIN。测试先断开原连接，再在已有设备卡片/详情页点击连接。Android以UI“断开连接”、iOS同样的连接控件、NAN TCP及两端WSS日志交叉确认。

| 发起端/时间 | 结果 | 证据 |
| --- | --- | --- |
| iPhone 17:51 | 成功 | Android自动匹配、缓存验证；iOS17:51:10反向验证true，serviceId8/connType10/result1；两端UI已连接 |
| Android 17:53 | 成功 | Android17:53:34 WSS认证完成；两端UI已连接 |
| iPhone 17:55 | 成功 | 17:55:25 Android已连接，iOS也有断开连接控件 |
| Android 17:56 | 成功 | 17:56:30 两端UI已连接 |
| iPhone 17:57 | 成功 | 17:57:27 两端UI已连接 |
| iPhone，冷启动18:10 | 失败，随后后台重试曾恢复 | 18:10:21.361 alias自动匹配，21.957缓存验证成功；22.566 NDP请求失败。18:10:42.384第二次缓存验证成功，45.875 NDP仍失败。驱动前一次PEER_DATA_END，后一次INSTALL_RX_FAIL；18:11:45后台WSS认证又成功 |
| Android 18:12 | 不能算完整业务成功 | Android18:12:25认证成功并显示已连接；iOS只记录serviceId19/connType10/result1，UI仍有“无法连接”弹窗。不是两端业务界面一致成功 |

“从哪端点击”不等于NAN的发布/订阅角色。最初五轮实际均为iOS发布、Android订阅。不存在已经通过这些测试就证明两个NAN角色都稳定的结论。

冷启动测试前退出了额外的扫码场景，恢复8e92ce4对应的功能代码并重新安装双端，使用原配对记录。额外测试造成的一个Android App私有重试暂停标记恢复为原先状态；保留alias关联，没有读取/删除系统NIK/NPK或系统配对文件。故此轮不作为“验证失败后完全自动恢复”的验收。

## 失败阶段及解释边界

1. ServiceDiscoveryInfo.getPairedAlias非空且匹配App关联，只证明系统找到缓存记录。
2. 两次失败前都实际收到onPairingVerificationSucceed，故不能把本轮失败简单解释成“App没识别已配对设备”。
3. 首次NDP记录PEER_DATA_END，随后一次有NDP_REQ/RSP/CONFIRM，但最终INSTALL_RX_FAIL。失败已到数据链路协商阶段，尚不能单凭该字符串确定为固件代码错误、密钥错误或对端应用释放。
4. iOS同期存在多个服务邀请/关闭/重建publisher：18:10:17端口60692、23秒重建60694，后面继续变化。需进一步把每条Go WSS实例、native publisher、NDP与实际取消关联起来，不能仅比较端口就认定谁先释放。
5. 18:12 Android成功对应iOS临时配对服务19，而不是此前成功的持续服务8；旧失败弹窗可能没有及时回收，也可能后续业务仍失败。尚未区分，不将其直接说成“只是UI问题”。

## 额外扫码场景：不计入用户要求的常规复测

为强制相反NAN角色曾额外打开Android二维码、让iOS订阅。18:03:38 Android publisher启动，41秒后台subscriber也启动，18:04:01该subscriber配对验证失败。用户指出只需要已有配对设备的连接测试后，退出扫码。该场景与常规失败分别记录，不能据此认定用户先前失败的根因。

曾尝试双端角色互斥，但未完成适用范围和WFD回退影响的验证，已将本轮功能改动完整撤回，并重新构建安装原8e92ce4对应代码。两端产品仓库对已发布Patch的git apply --reverse --check均通过。此实验不作为已交付修复。

## 构建、记录与收尾

Android完整productDebug构建及六项身份/Policy单测通过；恢复功能代码后的Android和iOS签名构建通过，均重新安装。编译通过不替代上面的失败结果。

本机日志：/tmp/sc-bidirectional-android.log、/tmp/sc-bidirectional-android2.log、/tmp/sc-bidirectional-ios.log、/tmp/sc-both-ndp-status.txt；完整日志含产品遗留敏感字段，不上传。本文仅保留必要状态与时间，不含凭据。

本轮未使用Ubuntu抓包，未改Ubuntu网络。收尾停止本轮日志、清理临时日志级别、恢复Android非root；iPhone按既有要求用短按锁屏并核对SpringBoard状态。具体核对结果在台账后续补充。

收尾核对：iPhone12实际SpringBoard lockstate从0变1；Android adbd恢复非root、临时日志标签已清除，停止的只有本轮两个采集进程。Ubuntu未改动。
