# P0 鳕鱼受控验收记录

## 环境

- 执行日期：2026-08-12
- Minecraft：1.21.1
- NeoForge：21.1.244
- Java：21
- Mod：Fish Breeding Manager 0.1.0
- 开发世界：尚未创建
- 无作弊权限世界：尚未创建
- 运行日志：`run/logs/latest.log`

## 自动化前置检查

| 检查 | 状态 | 结果 |
|---|---|---|
| `./gradlew test` | PASS | 37 个 JUnit 测试通过 |
| `./gradlew build` | PASS | Mod JAR 构建成功 |
| `./gradlew javadoc` | PASS | 中文 Javadoc 与链接语法生成成功，无警告 |
| `./gradlew runClient` 启动检查 | PASS | 客户端完成资源加载后正常退出；`latest.log` 无 FBM ERROR/Exception |

## 交互式验收状态

当前状态：**PENDING（尚未人工观察，不得标记为 PASS）**。

以下步骤需要在可操作的开发客户端中执行。每项只有实际观察到对应行为后才能改为 `PASS`；失败时记录实际现象与
`run/logs/latest.log` 行号。

| 序号 | 操作 | 预期结果 | 状态 | 实际观察/日志 |
|---:|---|---|---|---|
| 1 | 在允许作弊的测试世界执行 `/fbm rule set minecraft:cod 600 1200 minecraft:kelp` | 命令成功，鳕鱼规则立即生效 | RETEST | 首次执行时规则已提交，但成功消息误传 `ResourceLocation`，客户端显示参数类型异常；已修复并增加回归测试，等待重启客户端复测 |
| 2 | 在水体中执行两次 `/summon minecraft:cod` | 两条鳕鱼正常生成 | PENDING | 未观察 |
| 3 | 分别手持海带右键两条鳕鱼 | 每条鱼消费一个海带并出现爱心 | PENDING | 未观察 |
| 4 | 等待繁殖 | 两条鱼主动靠近并生成第三条同类型鳕鱼 | PENDING | 未观察 |
| 5 | 检查父母状态 | 成功生成后进入 600 刻冷却，冷却期喂食不触发 | PENDING | 未观察 |
| 6 | 检查后代外观 | 新生鳕鱼固定为成年尺寸 50% | PENDING | 未观察 |
| 7 | 等待 1200 刻 | 幼体在截止时刻瞬间恢复 100%，无平滑过渡 | PENDING | 未观察 |
| 8 | 执行 `/fbm rule set minecraft:cod 600 1200 minecraft:seagrass` | 现存鳕鱼立即拒绝海带、接受海草 | PENDING | 未观察 |
| 9 | 执行 `/fbm rule remove minecraft:cod` | 现存鳕鱼交互不再被 FBM 消费或取消 | PENDING | 未观察 |
| 10 | 喂食一条鳕鱼后使区块卸载，并在 600 刻内返回 | Love 仍有效，但不会强制恢复旧 mate UUID | PENDING | 未观察 |
| 11 | 在未开启作弊的单人世界输入 `/fbm` | 命令不可见或无权执行 | PENDING | 未观察 |

## 日志复核

验收后执行：

```powershell
rg -n "FBM|ERROR|Exception" run/logs/latest.log
```

- 预期不存在 FBM 注册异常。
- 若出现后代生成失败，日志必须包含具体 `ChildSpawnStatus`，且人工确认父母没有进入冷却。
