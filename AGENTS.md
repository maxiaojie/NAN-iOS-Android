# 项目问题记录要求

这是用户明确要求的持续维护规则：本项目遇到的问题、分析过程和解决方法都必须记录到 Markdown。

- 统一入口为 `docs/problem-log.md`。新问题追加编号，已有问题补充过程，保留失败与修复历史。
- 每项记录环境/版本、复现步骤、表现、证据、原因（区分确认与推测）、处理、验证结果、剩余限制。
- 长篇抓包或 ROM 分析放到 `docs/` 专项文档，并从台账链接；真实验收状态同步 `docs/validation.md`。
- 修改代码或配置解决问题时，在同一批变更中同步记录。未解决也要记录已尝试方案与下一步。
- 明确区分编译、真机正常路径、失败恢复分支；没有验证的内容不得宣称通过。
- 不提交配对密钥、私钥、签名资料或包含敏感字段的完整日志。
- Ubuntu 抓包测试结束后，必须退出 monitor 模式、恢复普通 Wi-Fi 和上网能力，并检查接口、路由及外网；不要将抓包模式作为默认收尾状态。若恢复操作被 sudo 密码阻止，明确告知用户并在其执行后继续验证。
- Ubuntu 已于 2026-11-18 一次授权固定命令 `/usr/local/sbin/nan-monitor-control`。使用 `sudo -n` 加 prepare、capture（信道 6/149，10～120 秒）、stop、restore、status；以后不要重复要求用户 sudo 恢复。restore 若因 NetworkManager 接口暂未就绪失败，继续自行重试并验证 HTTPS。安装源文件为 tools/setup-nan-monitor-control.sh，安装后的命令和 sudoers 由 root 持有。

- iPhone USB 收尾锁屏使用 `tools/lock-iphone.py <明确UDID>`（本机 Python：`/Users/mxj/nan-ios-tools/bin/python`）。先查 SpringBoard lockstate，已锁定不要再按侧键；只用 0.05 秒短按，不能使用旧版 0.5 秒长按（会触发 Siri）。以实际 lockstate 转换确认，不能仅凭 devicectl passcodeRequired 或命令返回成功。详见 `docs/iphone-usb-lock.md`。
