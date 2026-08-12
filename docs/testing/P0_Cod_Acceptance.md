# P0 鳕鱼受控验收记录

## 环境

- 执行日期：2026-08-12
- Minecraft：1.21.1
- NeoForge：21.1.244
- Java：21
- Mod：Fish Breeding Manager 0.1.0
- 开发世界：已创建并完成鳕鱼流程测试
- 无作弊权限世界：已创建并完成权限测试
- 运行日志：`run/logs/latest.log`

## 自动化前置检查

| 检查 | 状态 | 结果 |
|---|---|---|
| `./gradlew test` | PASS | 38 个 JUnit 测试通过 |
| `./gradlew build` | PASS | Mod JAR 构建成功 |
| `./gradlew javadoc` | PASS | 中文 Javadoc 与链接语法生成成功，无警告 |
| `./gradlew runClient` 启动检查 | PASS | 客户端完成资源加载后正常退出；`latest.log` 无 FBM ERROR/Exception |

## 交互式验收状态

当前状态：**PARTIAL PASS（9 项通过；爱心粒子修复后需复测，父母冷却期拒绝喂食需补测）**。

以下步骤需要在可操作的开发客户端中执行。每项只有实际观察到对应行为后才能改为 `PASS`；失败时记录实际现象与
`run/logs/latest.log` 行号。

| 序号 | 操作 | 预期结果 | 状态 | 实际观察/日志 |
|---:|---|---|---|---|
| 1 | 在允许作弊的测试世界执行 `/fbm rule set minecraft:cod 600 1200 minecraft:kelp` | 命令成功，鳕鱼规则立即生效 | PASS | 修复命令反馈参数后复测成功；截图显示规则更新为 `kelp`，共 4 条规则 |
| 2 | 在水体中执行两次 `/summon minecraft:cod` | 两条鳕鱼正常生成 | PASS | 两条鳕鱼正常生成并参与后续繁殖 |
| 3 | 分别手持海带右键两条鳕鱼 | 每条鱼消费一个海带并出现爱心 | FAIL | 喂食与 Love 状态生效，但未观察到爱心；当前使用仅适用于 `Animal` 的实体事件 18，鳕鱼是 `AbstractFish/Mob` |
| 4 | 等待繁殖 | 两条鱼主动靠近并生成第三条同类型鳕鱼 | PASS | 两条鱼靠近后成功生成第三条小鳕鱼；因初始距离较近，配对后很快产仔 |
| 5 | 检查父母状态 | 成功生成后进入 600 刻冷却，冷却期喂食不触发 | RETEST | 已观察到 1200 刻后可再次喂食，但尚未在成功产仔后的前 600 刻内尝试喂食，不能据此确认冷却期拒绝交互 |
| 6 | 检查后代外观 | 新生鳕鱼固定为成年尺寸 50% | PASS | 新生鳕鱼显示为较小幼体 |
| 7 | 等待 1200 刻 | 幼体在截止时刻瞬间恢复 100%，无平滑过渡 | PASS | 约 60 秒后瞬间长大 |
| 8 | 执行 `/fbm rule set minecraft:cod 600 1200 minecraft:seagrass` | 现存鳕鱼立即拒绝海带、接受海草 | PASS | 现存鳕鱼接受海草并拒绝海带 |
| 9 | 执行 `/fbm rule remove minecraft:cod` | 现存鳕鱼交互不再被 FBM 消费或取消 | PASS | 删除规则后鳕鱼不再接受喂食交互 |
| 10 | 喂食一条鳕鱼后使区块卸载，并在 600 刻内返回 | Love 仍有效，但不会强制恢复旧 mate UUID | PASS | 区块卸载并返回后行为符合预期 |
| 11 | 在未开启作弊的单人世界输入 `/fbm` | 命令不可见或无权执行 | PASS | `/fbm` 被命令分发器拒绝；日志 169—172 行为输入错误与无权限世界中的未知命令提示，无 FBM 异常 |

## 日志复核

验收后执行：

```powershell
rg -n "FBM|ERROR|Exception" run/logs/latest.log
```

- 预期不存在 FBM 注册异常。
- 若出现后代生成失败，日志必须包含具体 `ChildSpawnStatus`，且人工确认父母没有进入冷却。
