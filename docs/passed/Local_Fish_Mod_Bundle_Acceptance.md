# 本地第三方鱼类 Mod 组合验收记录

> 2026-09-23 更新：下文保留 2026-08-29 五 Jar 服务端验收证据；完整七 Jar 客户端与 Pike、Perch、Lionfish、Tuna 交互仍待测。后续启动指令、逐物种步骤和预期见 [892cbf9 客户端验收手册](../pending/Compatibility_Test_Manual.md)。按用户要求，暂不继续实机测试。

## 结论

2026-08-29 在 Minecraft 1.21.1、NeoForge 21.1.244 开发服务器中同时加载本地目录的五个顶层 Jar，服务器到达 `Done`，FBM 完成 210 个 EntityType 的通用扫描并正常发布快照。

本轮新增验证结果：

- Upgrade Aquatic 7.0.1：3 HIGH、0 MEDIUM，Pike、Perch、Lionfish 全部创建并查询成功；
- Youkai's Homecoming 4.2.13+1：0 HIGH、1 MEDIUM，未提供公共鱼类 Tag 的 Tuna 仍被通用弱信号识别，创建并查询成功；
- Farmer's Delight 1.3.2：成功加载，完整 Registry 扫描后没有 HIGH/MEDIUM 鱼类候选；
- Blueprint 8.1.1：作为 Upgrade Aquatic 必需前置成功加载，不是鱼类内容来源，没有 HIGH/MEDIUM 候选；
- Aquaculture 2.7.21：与其他四个 Jar 共存时仍保持 28 HIGH、0 MEDIUM。

因此 FBM 的**自动扫描、候选分类、代表实体创建与五 Jar 共存验收通过**，没有新增任何第三方 Mod 白名单、实体特例或 Java 类型依赖。组合中另外存在 Youkai 与 Blueprint 自带数据资源错误，已单独定位和记录；它们没有阻止服务器启动或 FBM 工作，但意味着不能把整套 Mod 的资源加载状态写成“无错误”。

## Jar 身份与指纹

| Jar | Mod ID / 版本 | 大小 | SHA-256 |
| --- | --- | ---: | --- |
| `[碧海新生] upgrade_aquatic-1.21.1-7.0.1.jar` | `upgrade_aquatic` 7.0.1 | 4,925,541 | `AC4EDECC341435C38893D85964074A2E09E322E3FB32BE93D08F2A3165C0EBE7` |
| `blueprint-1.21.1-8.1.1.jar` | `blueprint` 8.1.1 | 1,202,822 | `6A60D361D6AAF61273B65036958A646FF05D9ECB22FC706074A7E886E55EC190` |
| `[农夫乐事] FarmersDelight-1.21.1-1.3.2.jar` | `farmersdelight` 1.3.2 | 3,163,042 | `8FF438D62E1FCE61542945FAAE45975D823E04BD6E73A07A121EA05CE2F03DE7` |
| `[妖怪们的归家（非官方移植）] youkaishomecoming-4.2.13+1.jar` | `youkaishomecoming` 4.2.13+1 | 10,009,234 | `7C3FB2FB7CD0FE34F3D331A1ECE09EB9B7AB7F7C22C570103E472D11FFB90C9B` |
| `[水产业2／水产品2] Aquaculture-1.21.1-2.7.21.jar` | `aquaculture` 2.7.21 | 605,949 | `45F00F9059838B2FECC988861111D8B3D4613A5F1B3688A8DBFA8655751B85BB` |

所有 Jar 均保留在仓库外的本地测试目录，未复制或打包进 FBM。

## 依赖与 Tag 静态核对

- Upgrade Aquatic 要求 Minecraft 1.21.1、NeoForge 21.1.129+、Blueprint 8.0.6+；当前 NeoForge 21.1.244 与 Blueprint 8.1.1 满足要求。
- Blueprint 要求 NeoForge 21.1.160+；当前版本满足。
- Farmer's Delight 要求 NeoForge 21.1.219+；当前版本满足，CraftTweaker 仅为可选依赖。
- Youkai 要求 NeoForge 21.1.62+，Curios、Thirst、Create 均为可选依赖；所需 L2 系列库通过 Jar-in-Jar 载入。
- Aquaculture 没有目录外必需前置。

公共 EntityType Tag 边界：

- Upgrade Aquatic 的 `data/c/tags/entity_type/fishes.json` 明确包含 `pike`、`perch`、`lionfish`；
- Blueprint 的 `c:fishes` 只补充原版四种鱼；
- Farmer's Delight 和 Youkai 没有 `c:fish`/`c:fishes` EntityType Tag；
- Youkai Tuna 的 MEDIUM 结果来自实体 ID 关键词与水生属性两个独立弱信号，而不是专用 Mod 适配。

## 真实扫描结果

启动参数使用现有可选本地运行时机制，默认 `*.jar` include。Gradle 报告加载 5 个本地 Jar，Mod List 核对到五个预期顶层 Mod。

| 来源 | 注册 EntityType | HIGH | MEDIUM | HIGH/MEDIUM 候选 |
| --- | ---: | ---: | ---: | --- |
| `aquaculture` | 41 | 28 | 0 | 既有 28 种 Aquaculture 鱼，完整列表见 Aquaculture 专项验收 |
| `upgrade_aquatic` | 12 | 3 | 0 | `lionfish`、`perch`、`pike` |
| `youkaishomecoming` | 20 | 0 | 1 | `tuna` |
| `minecraft` | 130 | 4 | 0 | 原版四种鱼 |

Farmer's Delight 与 Blueprint 已加载但未出现在来源摘要中，含义是它们没有 HIGH/MEDIUM 候选，不是扫描失败。FBM 仍扫描完整 Registry，LOW 条目保留在发现快照中供未来高级搜索。

## 实体创建烟雾验收

服务器达到 `Done` 后，临时通过本机 RCON 在强制加载的 `[0, 0]` 区块创建候选并立即查询：

| EntityType | 创建结果 | 查询标记 |
| --- | --- | --- |
| `upgrade_aquatic:pike` | `Summoned new Pike` | `FBM_SMOKE_UA_PIKE_OK` |
| `upgrade_aquatic:perch` | `Summoned new Perch` | `FBM_SMOKE_UA_PERCH_OK` |
| `upgrade_aquatic:lionfish` | `Summoned new Lionfish` | `FBM_SMOKE_UA_LIONFISH_OK` |
| `youkaishomecoming:tuna` | `Summoned new Tuna` | `FBM_SMOKE_YH_TUNA_OK` |

测试实体随后全部删除，强制加载解除；服务器通过 `stop` 正常保存三维度并退出。`run/server.properties` 已恢复 `enable-rcon=false` 与空密码。日志中没有 FBM ERROR。

## 第三方资源错误诊断

组合启动时共有 21 条 ERROR 头，均可追溯到第三方 Jar 数据资源，而非 FBM：

1. Youkai Jar 内 10 个 `data/farmersdelight/recipe/cooking/*.json` 使用 `type: youkaishomecoming:kettle`，但 `result` 写成 ItemStack 风格的 `count`；同一 Jar 的有效 kettle 配方使用该 Codec 要求的 `amount`。因此 tea_mocha、white_tea、saidi_tea、cornflower_tea、oolong_tea、black_tea、green_tea、green_water、sakura_honey_tea、genmai_tea 各产生一条 Codec ERROR 和一条 RecipeManager ERROR。
2. Blueprint Jar 的 `data/blueprint/data_maps/dimension/modded_biome_slice_sizes.json` 含示例键 `modid:example`，目标维度不存在，产生一条 DataMapLoader ERROR。

这些错误在 FBM 扫描前发生，但没有中止数据加载、服务器启动、实体注册、候选发布或停服保存。FBM 不应修改、覆盖或静默过滤第三方数据资源；如需要消除日志错误，应由对应 Mod 更新资源文件或使用经确认兼容的版本组合。

## 后续覆盖

本记录保留当时通过的服务端检查及限制。后续客户端结果见 [892cbf9 已通过记录](892cbf9_Client_Acceptance.md)，未覆盖项目及步骤统一维护在 [待测手册](../pending/Compatibility_Test_Manual.md)。
