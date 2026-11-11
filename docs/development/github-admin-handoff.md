# 请管理员启用 Vibe-Player 的 Review 与 CI 合并保护

仓库：[Axfff/Vibe-Player](https://github.com/Axfff/Vibe-Player)。目标分支：`main`。

## 已核实的现状

2026-11-11（Asia/Singapore）只读检查：`main.protected = false`；已有名为 **main branch protection** 的分支 ruleset，`enforcement = disabled`。当前登录账号 CroissantXinny 有 push 权限，没有 admin/maintain 权限。本次只准备规范和操作说明，**尚未启用保护**。

## 需要管理员执行

在仓库 **Settings → Rules → Rulesets** 编辑已有 **main branch protection**，确认规则目标为 `main` / default branch，将 Enforcement 改为 **Active**。优先复用该规则，不新建重复规则。

1. **Require a pull request before merging**：至少 **1 个 approval**；审阅必须来自未参与该改动编写、具有相应权限的队友。开启 **Dismiss stale pull request approvals when new commits are pushed**，并开启 **Require approval of the most recent reviewable push**。重要代码变化后需要有效的新审阅，不能沿用与当前修改不相符的旧批准。
2. **Require status checks to pass**：选择实际出现且来源为 GitHub Actions 的 **`build`** 和 **`Team line coverage`**。保持检查名称唯一。还应要求 **conversation resolution**。同仓库 PR 的 **`publish / publish`** 也应通过；只有确认所有允许的 PR 来源都会产生同名检查时，才把它设为跨来源 required check。Fork PR 使用另一条可信发布 workflow，不能要求它永远不会产生的名称。
3. 禁止 force push 和删除 `main`，不添加常规绕过者；保护应约束日常管理员操作。采用普通更新/整合提交，针对实际整合候选运行检查并复核批准是否适用，不以不同分支各自通过代替整合验证。

以上对应 [GitHub 官方保护分支说明](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)。规则名称和界面可能随 GitHub 调整；保存前以仓库当前 checks 和权限设置为准。

## 如何确认已经生效

- 保存后确认 ruleset 为 **Active** 且匹配 `main`；不要只保存一个未启用的规则。
- 打开一个现有 PR：缺少非作者有效批准时，GitHub 合并区应显示审阅要求未满足；必需检查失败或仍在运行时，也应禁止合并。无需实际合并、制造故障提交或强推来验证。
- 留下一条简短配置记录：实际日期、操作者、规则链接，以及 PR 合并区的截图或观察结果。使用现有流程 PR 即可，不创建空 PR，不追补虚假的历史 Review。

后续开发遵循 [统一工作流](workflow.md)。Agent 可以准备技术审查材料，但不得替队友提交批准，也不得绕过未满足的规则。
