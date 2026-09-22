# 待测兼容项目与回归操作手册

截至 2026-09-23，用户决定暂停实机测试。本文保留操作步骤，不代表已执行；已确认结果见 [892cbf9 已通过记录](../passed/892cbf9_Client_Acceptance.md)。四种待测鱼执行 A–F；已测鱼和原版牛作为回归对照。

## 二、环境、依赖与启动

本轮环境：本地 Linux、Java 21、Minecraft 1.21.1、NeoForge 21.1.244。开发客户端自动加载本项目 FBM，不要再在兼容目录放一份 FBM Jar。

| 范围 | Jar / 固定版本 | 状态 |
| --- | --- | --- |
| 已测基础组合 | `Aquaculture-1.21.1-2.7.21.jar` | 已加载 |
| 已测基础组合 | `animal_feeding_trough-1.1.2+1.21-neoforge.jar` | 已加载 |
| 已测基础组合 | `architectury-13.0.8-neoforge.jar` | 已加载，喂食槽前置 |
| 后续追加 | `upgrade_aquatic-1.21.1-7.0.1.jar` | 当前客户端组合待测 |
| 后续追加 | `blueprint-1.21.1-8.1.1.jar` | Upgrade Aquatic 前置 |
| 后续追加 | `youkaishomecoming-4.2.13+1.jar` | 当前客户端组合待测 |
| 后续追加 | `FarmersDelight-1.21.1-1.3.2.jar` | Youkai 组合依赖 |

新增四 Jar 与 Aquaculture 的历史 SHA-256、依赖约束见 [本地组合记录](../passed/Local_Fish_Mod_Bundle_Acceptance.md)。本轮另两个文件的 SHA-256：

```text
animal_feeding_trough-1.1.2+1.21-neoforge.jar
236e7b7f789a08e5393c0b1570ffe4e77f4719cc818a0b41f0454850c1e5ffe2
architectury-13.0.8-neoforge.jar
5ec578f814e8cca87aeffa6e424032e78d9ea5ea6b603dd834c2dc13c31141ee
```

### 启动指令

在项目根目录执行。本轮历史客户端使用缓存 Gradle 9.6.1 和仓库外临时兼容目录，不证明当前 Wrapper 版本已通过实机测试。临时目录可能被清理，不是长期依赖存储。

先准备仓库外的稳定目录，核对版本和哈希，检查 Java 21 及当前 Wrapper 后再启动。以下绝对路径是占位示例，必须替换为本机真实目录：

```bash
./gradlew --no-daemon runClient "-PfbmCompatModsDir=/absolute/path/fbm-compat-mods" "-PfbmCompatModsInclude=*.jar"
```

Windows PowerShell 示例：

```powershell
.\gradlew.bat --no-daemon runClient "-PfbmCompatModsDir=D:\fbm-compat-mods" "-PfbmCompatModsInclude=*.jar"
```

先只放基础三 Jar 跑基线，再加入四 Jar 跑完整七 Jar 组合；不要混放不同版本或不同加载器文件。检查 Mod List、终端和 `run/logs/latest.log`，确认目标 Mod 全部加载且没有新增 FBM 异常。历史组合出现过 Youkai 配方 Codec 与 Blueprint 示例维度资源错误，具体见旧记录；不能只因错误数量相同就忽略新错误，需核对来源和堆栈。

## 三、统一测试场地

使用允许作弊的独立创造模式测试世界；历史兼容存档为 `run/saves/FBM-兼容实机-892cbf9`。变更依赖组合前备份存档或新建世界，不在重要存档移除内容 Mod。以下填充指令会覆盖指定区域，只能在预留测试区执行。每轮只放一种鱼，避免捕食、混种与旧幼体干扰。

```mcfunction
/gamemode creative
/gamerule doMobSpawning false
/effect give @s minecraft:water_breathing infinite 0 true
/effect give @s minecraft:night_vision infinite 0 true
/fill -177 133 221 -161 139 231 minecraft:glass hollow
/fill -176 134 222 -162 138 230 minecraft:water
/setblock -169 134 226 animal_feeding_trough:feeding_trough
/tp @s -166 136 223
/give @s minecraft:kelp 16
```

计时从成功喂食/繁殖现象记录，注明选用的起点。20 TPS 下 1200 tick 约 60 秒、600 tick 约 30 秒、6000 tick 约 5 分钟；暂停菜单或失焦暂停期间不按墙钟时间判失败。不要执行改变 tick 速度的指令。

## 四、逐物种兼容测试（待测）

目标为 Pike、Perch、Lionfish、Tuna；以下模板须将所有 `<ENTITY>` 替换成同一行完整 ID 后再粘贴，不是可直接执行的占位命令。

| 物种 | `<ENTITY>` |
| --- | --- |
| Pike | `upgrade_aquatic:pike` |
| Perch | `upgrade_aquatic:perch` |
| Lionfish | `upgrade_aquatic:lionfish` |
| Tuna | `youkaishomecoming:tuna` |
| 已测对照 Smallmouth Bass | `aquaculture:smallmouth_bass` |
| 已测对照 Minnow / Arapaima / Gar | `aquaculture:minnow` / `aquaculture:arapaima` / `aquaculture:gar` |

### A. 新建父母与手动投喂

先清空槽位 0。仅在本测试池清理上一轮指定物种；切换物种时先使用上一轮 ID 清理，再换成下一轮 ID。

```mcfunction
/execute positioned -169 134 226 run kill @e[type=<ENTITY>,distance=..20]
/item replace block -169 134 226 container.0 with minecraft:air
/fbm rule set <ENTITY> 1200 600 minecraft:kelp
/fbm rule show <ENTITY>
/summon <ENTITY> -174 135 225 {PersistenceRequired:1b,Tags:["fbm_parent_a","fbm_test_parent"]}
/summon <ENTITY> -174 135 227 {PersistenceRequired:1b,Tags:["fbm_parent_b","fbm_test_parent"]}
```

1. 各右键投喂一次海带。预期两个父母出现爱心，接近同类型配偶，生成一只同 EntityType 幼体；不存在重复出生、持续寻路互相抢占或明显模型异常。
2. 立即重复投喂父母，预期冷却期间拒绝；投喂幼体也应拒绝，不提前成年。
3. 观察幼体约 30 秒恢复成年渲染，父母尺寸一直正常；记录是否有持续抖动、卡住或模型拉伸。轻微位置偏移单独记录，不把碰撞箱未减半判为错误。
4. 要验证手持扣料，另开一轮新父母并执行 `/gamemode survival`，记录海带前后数量，成功每只减少 1，拒绝不扣；完成后 `/gamemode creative`。注意第三方鱼可能有攻击行为，保留安全退路。创造模式只检查交互，不检查手持扣料。

### B. 自动槽喂、60 秒冷却

按 A 清理并重建父母，但改用长幼体周期，防止幼体在观察窗口成年后参与取食：

```mcfunction
/fbm rule set <ENTITY> 1200 6000 minecraft:kelp
/item replace block -169 134 226 container.0 with minecraft:kelp 4
/data get block -169 134 226 Items
```

不再手动投喂。预期父母主动接近槽，各取 1 个，出现爱心和一个幼体，槽内剩 2。约 20 秒、50 秒检查仍为 2；约 60 秒冷却结束后允许再次取食、第二次繁殖，槽内用完。若鱼暂未到达，记录实际取食时间，不仅凭一分钟内未出生断言失败。空库存可能表现为 `Items: []`。

另开新父母高差轮：把 A 中两次 summon 的 Y 从 135 改成 138，槽仍为 Y=134，放 2 海带。预期能在该可达水域下游取食并繁殖。此轮仅验证实际高差场景；严格搜索边界需要限制初始移动并增加目标搜索观测，另列待测。

### C. 错误食物、禁用与恢复

用 A 的新父母，先设置 `1200/6000` 规则：

```mcfunction
/item replace block -169 134 226 container.0 with minecraft:carrot 4
```

观察约 30 秒，应无爱心、无新后代且仍为 4。然后执行：

```mcfunction
/fbm rule disable <ENTITY>
/item replace block -169 134 226 container.0 with minecraft:kelp 4
```

观察约 30 秒仍为 4；再执行 `/fbm rule enable <ENTITY>`，不重新生成鱼，预期已加载父母恢复取食，首轮剩 2 并出现后代。

删除规则单独用新父母测试：`/fbm rule remove <ENTITY>` 后放 4 海带，应不再由 FBM 取食/繁殖；用 A 的 set 命令恢复后，无需重生实体即可工作。若第三方自带相同食物的原生繁殖，先区分来源，不把原生行为误归 FBM。

### D. 重进世界、冷却与重复扣料

1. 新父母，规则 `1200/6000`；先 disable，再放 4 海带，保存退出到标题界面再进入，确认槽内仍为 4、规则仍禁用。
2. enable，预期首轮繁殖后剩 2，约 20 秒仍剩 2，没有一次成功取食扣两份。
3. 在冷却尚未结束时清空槽，保存退出再进入；立即手动投喂应拒绝，不能通过重进世界绕过剩余冷却。记录退出前已消耗时间，不把正常到期误报为丢失。
4. `/fbm reload` 后 `/fbm rule show <ENTITY>`，规则值和启用状态应与保存值一致；观察已加载实体仍按当前规则工作。

### E. 幼体持久化与追踪同步

新父母使用 `1200/6000`、2 海带，出生后立即标记唯一幼体：

```mcfunction
/execute positioned -169 134 226 run data merge entity @e[type=<ENTITY>,tag=!fbm_test_parent,distance=..20,limit=1,sort=nearest] {PersistenceRequired:1b,CustomName:'{"text":"FBM幼体验收"}',CustomNameVisible:1b}
```

保存退出再进入：幼体仍半尺寸，父母正常。随后执行：

```mcfunction
/tp @s 350 150 226
```

等待约 5 秒，再 `/tp @s -166 136 223`。预期重新追踪后幼体恢复正确半尺寸，无持续闪烁或父母被错误缩放。总活跃时间控制在 5 分钟以内，避免自然成年影响判断；最后观察其按剩余成长时间成年。尚未成年时只保留一只幼体、清理本池父母并放 2 海带，可独立观察幼体约 20 秒不扣槽料，避免把父母冷却误当幼体拒绝证据。

### F. 变种继承与 AI 共存

1. 针对存在可见变种的物种，准备外观不同的两只父母；通过自然生成/刷怪蛋筛选，并分别标记父母，或先核实该固定版本公开变种契约后再设置字段。不得猜测第三方 NBT 键和值。
2. 使用 `/data get entity @e[tag=fbm_parent_a,limit=1]` 和父母 B 的对应命令保存数据；出生后用 E 的非父母选择器查询幼体，比较公开、安全的变种字段与外观。
3. 若支持继承，子代应为合法父母变种，不出现无效混合；不应继承父母 CustomName、位置、冷却等无关状态。需要统计父母选择比例时另行设计足够样本，不由单个子代判断概率。
4. 若实体没有支持的公开变种契约，默认个体可能符合设计；记录采用的契约/回退及可见差异，再判断是否存在真实缺口，不直接要求复制全部 NBT 或增加专用适配。
5. 每种单独连续观察 5–10 分钟，记录追食、寻偶、原生游动和可能的攻击/逃避行为；预期不存在持续导航抢占、重复出生或反复报错。此为行为观察，不是性能基准。

## 五、上游原生动物回归（已测，可复跑）

使用测试池旁预留区域，不给牛新增 FBM 规则。先 `/fbm rule show minecraft:cow`，如果已有规则，换干净测试世界，避免更改原有配置。

```mcfunction
/fill -156 133 222 -148 137 230 minecraft:glass hollow
/fill -155 134 223 -149 136 229 minecraft:air
/setblock -152 134 226 animal_feeding_trough:feeding_trough
/summon minecraft:cow -154 134 225 {PersistenceRequired:1b}
/summon minecraft:cow -154 134 227 {PersistenceRequired:1b}
/item replace block -152 134 226 container.0 with minecraft:wheat 4
/tp @s -152 138 226
```

预期上游自动喂食正常、出现小牛，4 小麦变 2，约 20 秒后仍为 2，不发生 FBM 和上游重复扣料。复跑前只清理这个独立牛栏内上一轮牛和库存。

## 六、尚未覆盖的矩阵

- [ ] 完整七 Jar 开发客户端启动与错误归因。
- [ ] Pike：逐项执行第四节 A–F。
- [ ] Perch：逐项执行第四节 A–F。
- [ ] Lionfish：逐项执行第四节 A–F。
- [ ] Tuna：逐项执行第四节 A–F。
- [ ] Aquaculture 其余鱼种及实际存在的公开变种/重量契约；当前代表模型通过不代替全部覆盖。
- [ ] 已 Love 实体的独立拒绝测试：新轮只手动喂父母 A，槽保持空；在 Love 仍有效且未繁殖时重复手喂 A，确认拒绝；槽内给 2 海带后，应只由 B 扣 1 并配对，余 1，记录时序避免 Love 自然到期干扰。
- [ ] 生存模式手持精确扣料、严格水平/垂直搜索边界。
- [ ] 其他渲染 Mod 的共存与事件交互：先明确具体 Mod、版本和依赖，记录无该 Mod 的基线，再单独加入并复跑幼体出生、重进、追踪恢复、成年；当前没有指定或验收任何额外渲染 Mod。

## 七、结果留档与结束

每项记录：日期、FBM 提交/工作区改动、Java/加载器版本、Jar 名称及哈希、存档、实体 ID、实际规则、步骤、预期、实际现象、PASS/FAIL/待测、截图与对应日志时间。异常须包含复现轮次和最小组合，不能只写“符合”或“应该正常”。

退出游戏后复制本次 `run/logs/latest.log` 及实际存在的调试日志到自行指定的测试归档目录，避免下次启动覆盖；不要把个人存档、第三方 Jar 或大体积日志提交仓库。测试结束正常保存退出，不删除世界；本次仅整理文档，不执行以上待测步骤、不更改规则、不启动或关闭游戏。

相关历史记录：[鳕鱼基础](../passed/P0_Cod_Acceptance.md)、[Aquaculture](../passed/Aquaculture_2_Compatibility_Acceptance.md)、[喂食槽](../passed/Animal_Feeding_Trough_Compatibility_Acceptance.md)、[第三方组合](../passed/Local_Fish_Mod_Bundle_Acceptance.md)。
