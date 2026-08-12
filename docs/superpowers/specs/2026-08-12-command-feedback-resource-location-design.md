# 命令反馈实体 ID 类型修复设计

## 问题

执行 `/fbm rule set minecraft:cod 600 1200 minecraft:kelp` 时，规则事务已经成功提交，但成功反馈把
`ResourceLocation` 直接作为 `Component.translatable` 的格式参数。Minecraft 1.21.1 的
`TranslatableContents` 仅接受 `Component`、`Number`、`Boolean` 或 `String`，因此反馈构造抛出
`IllegalArgumentException`，并被命令的参数错误处理误报为规则操作失败。

## 修复范围

- 所有进入可翻译消息的实体 ID 均先调用 `ResourceLocation#toString()`。
- 覆盖规则更新、启用、禁用、删除成功反馈，以及查询不存在规则时的失败反馈。
- 不修改 Brigadier 命令语法、权限、规则校验、运行时快照或存档结构。
- 不尝试回滚截图中已经成功提交的规则；玩家可用 `/fbm rule show minecraft:cod` 确认当前规则。

## 测试

新增回归测试，直接执行统一的规则更新反馈路径，并强制求值 `sendSuccess` 接收的消息 Supplier。修复前测试应因
非法 `ResourceLocation` 翻译参数失败；修复后应成功生成消息，并包含字符串形式的实体 ID。随后运行完整测试、
构建和 Javadoc。

