---
alwaysApply: true
description: Git 与 GitHub 权限和风险控制。
---
# Git / GitHub 操作规则
修改代码不等于获得 Git 权限；Git 权限不等于 Commit、Push、PR 或 Merge。无法确定时按未授权处理。
只读本地操作如 status、diff、log、show 可在任务需要时执行。创建分支、切换、暂存、提交和本地 tag 修改需要明确授权。提交只包含当前任务内容，避免 git add . 或 git add -A。
reset、clean、覆盖工作区、删除分支或 tag、rebase、改写历史、强制推送等高风险操作必须有针对该操作的明确意图。
查看 PR、Issue、Actions 或远程信息属于 GitHub 操作；Push、创建或修改 PR、Issue、Release、Review、远程分支或仓库设置必须分别获得明确授权。Push 不从 Commit 或“准备 PR”授权中自动推导。
提交信息默认使用简洁准确的中文标题，不写工作流水、模型署名或无关 footer。假定未提交内容可能属于用户或其他 Agent，不得擅自 discard、reset、clean、stash、覆盖或混入提交。执行完授权操作后停止在授权范围内。
