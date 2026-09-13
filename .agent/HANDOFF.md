# Fish Breeding Manager 项目交接

## 当前目标

P0 繁殖核心已完成并通过人工验收，现处于 P1。已完成的部分：自动发现后端（ModList 与完整 EntityType Registry 扫描、多信号识别、原子快照）、Animal Feeding Trough 零硬依赖适配、按需求 §12 的后代 Variant 继承，以及一轮独立代码审查的全部修复。

**下一步不是写 GUI。** 先由用户决策两处需求偏差（见"待决策事项"），并用 `客户端待测试包_2026-08-31/` 在可见客户端完成第三方鱼与喂食槽的人工验收；确认没有公共契约缺口后，再进入导入事务、编辑网络和 GUI 最小闭环。

## 当前状态

- 技术基线：Minecraft 1.21.1、NeoForge 21.1.244、Java 21、ModDevGradle 2.0.143，Mod 版本 0.1.0。
- 自动化：29 个测试套件、96 个测试全部通过；`javadoc` 无警告；`build` 成功；Gradle 弃用告警为 0。
- P0 B-lite 全部实现：世界级规则持久化、不可变运行时快照、热重载、实体繁殖状态、喂食进入 Love、配偶搜索与寻路、同类型后代生成、Variant 继承、父母冷却、FBM 幼体成长、客户端幼体缩放、管理员命令、生命周期恢复。
- P1 自动发现后端第一纵向切片已完成：完整 Registry 候选、来源分组、识别理由、置信度、失效导入报告、按服务器隔离的运行快照。**高级搜索 UI、导入管理服务、管理 Screen、规则编辑 Payload 与 GUI 保存链路仍未实现。**
- `WorldBreedingData` 已持久化 `importedEntities` 并提供增删方法，`BreedingRuleSnapshot` 也携带该集合，但没有面向玩家的发现/导入工作流。
- 仓库外本地 Mod 兼容矩阵：Aquaculture 2.7.21 → 28 HIGH；Upgrade Aquatic 7.0.1 → 3 HIGH（Lionfish、Perch、Pike）；Youkai's Homecoming 4.2.13+1 → 1 MEDIUM（Tuna）；Farmer's Delight 1.3.2 与 Blueprint 8.1.1 无 HIGH/MEDIUM。生产代码不依赖这些 Mod 的 ID、实体 ID 或 Java 类型。
- 客户端人工验收整体尚未执行；喂食槽适配、Variant 继承和渲染改动都只有自动化与源码契约证据。

## 已实现能力与关键实现事实

按子系统组织；这里记录"现在是什么样"，不记录改动过程。

**规则与热更新**

- 默认内置原版鳕鱼、鲑鱼、热带鱼、河豚四条规则，只在新建存档时种入，玩家删空后不会被重新种入。
- 规则按存档保存在主世界 SavedData，同一存档跨维度共享；提交前对候选全集完整校验，通过后整体替换 SavedData 工作集与 `volatile` 不可变 Snapshot，失败保留旧运行时状态。
- `BreedingRuleSnapshot`、`DiscoverySnapshot`、`CandidateEntity` 三个快照类型都在规范构造器中做防御性复制，不可变性由类型自身保证。

**繁殖引擎**

- 实体状态通过 NeoForge Data Attachment 保存，规则不复制到实体；行为发生时动态查询当前 Snapshot。
- Love 活动索引避免每 Tick 扫描全部实体；配偶搜索半径固定 8 格，每 5 tick 一趟。自身与候选方使用同一组资格条件，与生成前复检对称。
- 后代与父母保持相同 `EntityType`；生成失败不会错误提交冷却。
- 后代 Variant 继承（需求 §12）：加入世界之前按 50/50 随机选出一方父母，依次尝试 `Bucketable` 桶数据与 Mojang 公共 `VariantHolder<T>` 接口，两条都不适用时保持默认个体。原版热带鱼由此完整继承 `BucketVariantTag` 打包的花纹与双色 —— 此前 `EntityType.create` 不走 `finalizeSpawn`，繁殖出的热带鱼全是固定变体 0；鳕鱼/鲑鱼/河豚桶标签只有通用字段，过滤后为空，安全退回默认个体。
- 幼体状态由 FBM 自维护，不要求实体实现原生年龄接口；客户端成年前固定 50%、成年瞬间恢复 100%。渲染 Pre 取 `LOWEST`、Post 取 `HIGHEST` 成镜像，保证 FBM 压入的矩阵最内层且最先弹出。客户端缓存在实体离开世界与退出连接时清理。
- `/fbm reload` 与 `/fbm rule list|show|set|enable|disable|remove` 统一要求权限等级 2，修改一律经 `WorldBreedingService`。

**喂食与第三方兼容**

- 所有喂食来源共用 `breeding/feed/BreedingFeedService`：每次动态读取当前规则，统一处理食物匹配、幼体/冷却/Love 资格、Attachment、活动索引和爱心粒子。玩家入口在 `FED` 后按创造模式语义扣手持物；容器来源把扣料回调交给服务，资格通过后先确认消费成功再提交 Love，并显式 `Container#setChanged`，因此不存在扣料失败却免费 Love 的路径。
- Animal Feeding Trough 适配：只按 `animal_feeding_trough:feeding_trough` 方块 ID 与标准 `Container` 槽位 0 工作，不导入第三方类型。给启用规则的非 `Animal` `PathfinderMob` 安装 `FbmTroughSelfFeedGoal`（优先级 3、水平半径 8 格、垂直半径 4 格、接受距离 2 格），并在实体 Join、初始 reload、规则 upsert/enable 后幂等补装。
- 上游 1.21.1 源码只向 `Animal` 注入自助喂食目标并调用 `Animal#setInLove`，这是原版鱼和多数第三方鱼用不上喂食槽的结构性缺口；FBM 不接管 `Animal` 层级，避免与上游双重扣料。
- `CompatibilityCoordinator` 是核心与具体第三方适配之间的唯一边界。单个兼容模块或单个实体异常只记录日志，不阻断其余模块，也不回滚已发布的规则 Snapshot。

**自动发现**

- `discovery` 扫描 `ModList` 与完整 `BuiltInRegistries.ENTITY_TYPE`，合并 `c:fish`/`c:fishes`、原版白名单、实体/来源关键词、Aquatic Tag、水生 MobCategory 和持久化导入信号；结果按来源稳定分组并以不可变快照原子发布。
- 接入 `ServerStartedEvent`、服务端 `TagsUpdatedEvent` 与 `ServerStoppingEvent`：启动与 Tag 重载时重建，失败保留旧快照，停止时清理服务器实例；不做每 Tick 扫描。
- `DiscoveryDiagnostics` 在发现成功后稳定记录每个有效来源的 HIGH/MEDIUM 数量与候选 Registry ID；LOW-only 来源不会被误报为可适配鱼类。

**工程**

- 源码目录按所有权划分，`FishBreedingManager` 收敛为注册组成根，服务器生命周期集中在 `server/ServerLifecycleHandler`；`docs/ARCHITECTURE.md` 记录包职责、依赖方向与新代码放置规则。
- `build.gradle` 支持显式 `fbmCompatModsDir` 与可选 `fbmCompatModsInclude`，只把匹配到的普通 Jar 作为 `localRuntime` 加载；属性缺失时不影响常规构建，也不发布第三方依赖或固化本机路径。
- 工程外层工作区有一次性 `客户端待测试包_2026-08-31/` 与同名 `.zip`：8 个按 `[Mod中文名]Mod本名` 命名的 Jar、一份统一客户端逐项清单 Word、四份历史验收 Word，5 份文档全部黑色宋体。该目录只用于用户复制测试，可整体删除，不属于生产源码或发布内容。

## 重要决策与约束

1. `docs/Fish_Breeding_Manager_Requirements.md` 是产品需求权威；`docs/Project_Analysis_and_Summary.md` 是当前架构和阶段总结。
2. Rule 属于世界，State 属于实体。行为发生时必须动态查询当前 Snapshot，禁止把规则永久缓存到实体。
3. 热更新采用“完整解析和校验后原子替换”；无效候选不能污染当前规则。
4. 已开始的 `cooldownUntil` 和 `adultAt` 不因规则热更新追溯重算，新事件采用新规则。
5. 玩法规则按存档保存并跨维度共享，不使用所有世界共用的全局规则文件。
6. 服务端是规则、权限和繁殖结果的唯一权威。未来 GUI 的任何修改请求都必须在服务端重新校验权限、Registry 和参数，并复用 `WorldBreedingService` 的事务提交路径。
7. 实体和规则主键统一使用 `ResourceLocation`/Registry ID，不使用 Java 类名、本地化名称或实例 UUID。
8. 繁殖食物必须保持多 Item 与 Item Tag 的 Ingredient 式语义，不能退化成单 Item 字段。
9. 第三方兼容保持非侵入：事件 + Attachment + 集中式 Runtime Controller；不把 Mixin、ASM、反射字段复制作为主路线。
10. Variant 继承只允许使用 Minecraft 公共契约：先按 50/50 随机选出捐赠方父母，再依次尝试 `Bucketable` 桶数据（剔除 `saveDefaultDataToBucketTag` 的六个通用字段）与 Mojang 公共 `VariantHolder<T>` 接口；两条都不适用时创建默认个体。禁止反射、ASM、Mixin 或私有字段复制，也不要求第三方 Mod 实现 FBM 接口。更复杂的私有 Variant 留给未来的 `BreedingAdapter`。
11. 第一版不做跨 `EntityType` 杂交、基因系统、复杂环境条件、文件监听自动重载、多版本或多加载器支持。
12. 后续每次实际修改项目前先读本文件，完成修改并验证后将本文件整理为新的当前状态；不要追加操作流水。
13. FBM 的第三方鱼类能力必须以自动扫描和通用识别为主：扫描已加载 Mod 与完整 EntityType Registry，通过 `c:fish`/`c:fishes`、ID、实体翻译键、Namespace、Aquatic Tag 和 MobCategory 等多信号分级；不得把单个 Mod ID 白名单作为主要兼容机制。
14. “自动适配”表示未知鱼类 Mod 无需 FBM 专用代码即可进入候选并复用通用繁殖引擎；它不表示未经管理员配置就自动启用玩法规则。
15. 弱信号按语义组计数：`ENTITY_KEYWORD`、`SOURCE_KEYWORD`、`AQUATIC_TAG/WATER_CATEGORY`；Aquatic Tag 与水生 MobCategory 同属水生属性，不能互相凑成 MEDIUM，避免水母误判。
16. 失效导入必须从 `WorldBreedingData` 的持久化集合读取，不能依赖可能因规则校验失败而未安装的规则运行快照。
17. 发现快照中的 `Component` 使用 Codec 深拷贝嵌套树，集合与显示组件都不得向调用方暴露可变内部状态。
18. 仓库外兼容 Jar 只能通过显式 Gradle 属性加入 `localRuntime`；默认构建不得读取固定本机目录，匹配结果必须限制为 Jar 并保持确定顺序，第三方文件不得复制或再分发。
19. 第三方组合验收必须区分“FBM 扫描/繁殖契约问题”和“Mod 自带数据资源问题”；后者如实记录来源和影响，但 FBM 不覆盖、过滤或修补第三方 Jar 数据。
20. 外部自动喂食入口必须复用 `BreedingFeedService`，来源只在 Love 成功提交后扣料；玩家手持物、容器和未来来源不得各自复制规则/状态转换逻辑。
21. Animal Feeding Trough 适配只依赖稳定 Mod/方块 Registry ID 和 Minecraft `Container` 契约，不引用第三方 Java 类型、Mixin 或反射；`Animal` 层级保留给上游实现，FBM 只补非 Animal PathfinderMob 的缺口。
22. Mod 入口类只负责注册与组成；服务器生命周期逻辑放在 `server`。核心生命周期与规则服务不得直接依赖具体第三方适配包，统一经过 `CompatibilityCoordinator`。
23. FBM 注入的任何 Goal 优先级必须严格小于它需要抢占的原版目标：`GoalSelector` 只允许更小的数字打断正在运行的目标。原版 `AbstractFish` 的 `FishSwimGoal` 在 4，`AvoidEntityGoal` 在 2，`PanicGoal` 在 0，因此鱼类相关的 FBM 目标使用 3。
24. 成对的渲染事件必须使用镜像优先级：Pre 取 `LOWEST` 就必须让 Post 取 `HIGHEST`，保证 FBM 压入的矩阵是最内层且最先弹出。
25. 新增或修改的公共类型、公共 API 与关键私有流程必须使用中文 Javadoc（与 `docs/Project_Analysis_and_Summary.md` §1.3 一致），不要混入英文文档注释。
26. 重建含中文路径的分发压缩包时，ZIP 条目的 `create_system` 必须标记为 Unix（`3`）。若沿用 Python `zipfile` 在 Windows 上的默认值 `0`（DOS/FAT），Info-ZIP `unzip` 与 Windows 资源管理器会对文件名再做一次本地代码页转换，把 UTF-8 字节译成乱码目录名；本机没有 `zip`/`7z` 可用，只能在 Python 侧显式设置。重建后必须实际解压一次并按字节比对确认。

## 修改 / 重要文件

- `.agent/HANDOFF.md`：跨会话共享的当前工作状态，后续任务必须持续维护。
- `docs/Fish_Breeding_Manager_Requirements.md`：完整产品需求、优先级和验收标准。
- `docs/Project_Analysis_and_Summary.md`：P0 实现映射、API 注意事项、下一阶段建议。
- `docs/ARCHITECTURE.md`：当前源码目录、所有权边界、依赖方向和新代码放置指南。
- `docs/testing/P0_Cod_Acceptance.md`：P0 自动化与人工验收记录及边界说明。
- `docs/superpowers/specs/2026-08-11-p0-b-lite-design.md`：P0 稳定化设计。
- `docs/superpowers/specs/2026-08-29-aquaculture-2-compatibility-design.md`：第三方鱼类 Mod 自动发现架构，以及 Aquaculture 首轮兼容设计和验收标准。
- `docs/superpowers/plans/2026-08-29-fish-mod-auto-discovery-implementation.md`：已执行的自动发现 TDD 实施计划和验收矩阵。
- `docs/superpowers/plans/2026-08-29-aquaculture-runtime-acceptance.md`：已执行的本地运行时接入、诊断和真实服务器验收计划；仅客户端人工项未执行。
- `docs/testing/Aquaculture_2_Compatibility_Acceptance.md`：Aquaculture Jar 指纹、28 个候选、误判边界、实体创建证据和待人工客户端清单。
- `docs/superpowers/plans/2026-08-29-local-fish-mod-bundle-acceptance.md`：五 Jar 依赖闭包、共存启动、候选与实体创建验收计划。
- `docs/testing/Local_Fish_Mod_Bundle_Acceptance.md`：五 Jar 指纹、依赖、真实发现矩阵、四个新增实体创建证据、第三方资源错误根因和人工验收边界。
- `build.gradle`：可选本地兼容 Jar 的 `localRuntime` 目录及 include 属性。
- `src/main/java/com/fishbreedingmanager/discovery/`：发现模型、Registry/ModList 边界、分类引擎、原子快照管理器和结构化重建结果。
- `src/test/java/com/fishbreedingmanager/discovery/`：自动发现与诊断测试，覆盖不可变性、置信度、误判边界、来源/ID 排序、Tag、失效导入、空输入和服务器隔离。
- `src/main/java/com/fishbreedingmanager/breeding/`：规则、状态、验证、事务服务、控制器和索引核心。
- `src/main/java/com/fishbreedingmanager/breeding/feed/`：玩家与外部自动喂食来源共享的 Love 转换和服务端粒子反馈。
- `src/main/java/com/fishbreedingmanager/breeding/spawn/`：同 EntityType 后代创建、公共契约 Variant 继承及结构化结果。
- `src/test/java/com/fishbreedingmanager/breeding/spawn/`：后代生成失败分支、继承发生在加入世界之前的顺序断言，以及 Variant 继承的捐赠方选择、通用字段过滤、`VariantHolder` 回退、类型不一致与第三方异常隔离。
- `src/main/java/com/fishbreedingmanager/server/ServerLifecycleHandler.java`：规则加载、发现重建和服务器会话清理。
- `src/main/java/com/fishbreedingmanager/compat/CompatibilityCoordinator.java`：核心面向的可选第三方兼容统一入口与故障隔离。
- `src/main/java/com/fishbreedingmanager/compat/feedingtrough/`：Animal Feeding Trough 的标准 Container 来源、非 Animal 自助喂食 Goal 和生命周期协调器。
- `src/test/java/com/fishbreedingmanager/compat/feedingtrough/`：喂食槽来源、动态 Goal、安装资格与幂等回归测试。
- `docs/superpowers/specs/2026-08-29-animal-feeding-trough-compatibility-design.md`：喂食槽兼容边界和验收标准。
- `docs/superpowers/plans/2026-08-29-animal-feeding-trough-compatibility.md`：本轮 TDD 实施计划。
- `docs/testing/Animal_Feeding_Trough_Compatibility_Acceptance.md`：自动化证据、本地 Jar 缺口和用户统一人工测试清单。
- `E:/JavaCodes/FishBreedingManager/客户端待测试包_2026-08-31/00_客户端统一测试清单.docx`：一次性客户端统一测试入口；同目录 `mods/` 含 8 个按 `[Mod中文名]Mod本名` 命名的待测 Jar，`参考验收记录/` 含四份 Word 记录。同级 `.zip` 为分发用压缩包。
- `src/main/java/com/fishbreedingmanager/persistence/WorldBreedingData.java`：按存档规则与导入实体集合的 SavedData。
- `src/test/java/com/fishbreedingmanager/persistence/WorldBreedingDataTest.java`：NBT 往返、损坏记录逐条跳过、加载不重新种入默认规则的持久化回归测试。
- `src/main/java/com/fishbreedingmanager/command/FBMCommands.java`：当前管理员管理入口。
- `src/main/java/com/fishbreedingmanager/network/` 与 `client/`：目前仅含幼体状态下发和客户端缩放；P1 编辑网络与 GUI 尚未实现。
- `src/test/java/com/fishbreedingmanager/`：现有 P0 自动化回归测试。

## 验证情况

**自动化（最近一次为 2026-08-31）**

- `.\gradlew.bat test javadoc build --rerun-tasks --warning-mode all` 全部成功：29 suites、96 tests、0 failures/0 errors/0 skipped；Javadoc 无警告；Gradle 弃用告警为 0。
- 持久化路径有专门的 NBT 往返回归测试：全字段往返、往返后快照等价、损坏记录逐条跳过、`load` 不重新种入默认规则、空 tag 容错。
- 已用本地 1.21.1 反编译源码核对过的上游契约（不是凭记忆）：`AbstractFish` 在优先级 4 注册 `FishSwimGoal`；`WrappedGoal#canBeReplacedBy` 要求严格更小的优先级；`MoveToBlockGoal` 三参构造器的垂直范围为 ±1；`TropicalFish.saveToBucketTag` 在通用字段之外只写 `BucketVariantTag`；`AbstractFish.loadFromBucketTag` 不置 `fromBucket`；`Bucketable.saveDefaultDataToBucketTag` 的通用键恰为代码中过滤的六个；`VariantHolder<T>` 为公共接口。

**真实开发服务器（2026-08-29）**

- Aquaculture 2.7.21 单独加载：41 个注册实体中自动识别 28 HIGH、0 MEDIUM，`jellyfish` 未入选；Smallmouth Bass、Minnow、Arapaima、Gar 均可创建并查询；服务器正常保存停服，最终日志无 ERROR/Exception/Caused by。
- 五 Jar 共存：Mod List 版本全部吻合；FBM 共扫描 210 个 EntityType，Upgrade Aquatic 3 HIGH/0 MEDIUM，Youkai 0 HIGH/1 MEDIUM，Farmer's Delight 与 Blueprint 无 HIGH/MEDIUM；Pike、Perch、Lionfish、Youkai Tuna 创建/查询成功；三维度正常保存。测试实体已清理，RCON 已恢复关闭。
- 组合中有 21 条第三方数据资源 ERROR（Youkai 20 条 + Blueprint 1 条），无 FBM ERROR，详见组合验收记录。

**人工验收**

- `docs/testing/P0_Cod_Acceptance.md` 记录 2026-08-12 的 11 项鳕鱼人工验收全部通过。这是历史记录，不代表之后的改动被重新验收过。
- **尚未执行**：可见客户端中的玩家喂食、Love、寻路、后代、成长、渲染观察；喂食槽真实取食与双重扣料检查；Variant 继承的实际观感。喂食槽 Goal 优先级/垂直范围、渲染优先级镜像和 Variant 继承三项行为改动都只有自动化与源码契约证据。

**客户端待测包（2026-08-31）**

- FBM Jar 为当前最新构建（105,565 字节），已用 `javap` 与 Jar 条目清单确认 `GOAL_PRIORITY = 3`、`MoveToBlockGoal.<init>(mob, 1.0, 8, 4)` 与 `VariantInheritance.class` 均进入产物。七个第三方 Jar 的 SHA-256 与 08-30 版一致。
- 5 份 Word 通过 ZIP 完整性、XML 合法性与 `python-docx` 对象模型解析，字体全部宋体、颜色全部 `000000`；文本片段总数由 838 增至 842，增量恰好等于新增的 4 个段落，确认无原有内容丢失。
- 压缩包重建后实际解压回读：13 个文件字节级一致，中文路径正常，8 个 Jar 与 5 份 Word 解压后仍可读。
- 本机无 `zip`/`7z`，也无 pywin32，因此本轮未做 Microsoft Word 渲染逐页复核（08-30 的 19 页渲染检查是那一轮做的）。

## 待决策事项（阻塞 GUI）

两处需求与实现的偏差改变产品语义，Agent 不应单方面决定，代码尚未改动：

1. **需求 §42 的 Unsupported 兼容等级完全未落地。** `RuleValidator` 只校验 EntityType 是否已注册，因此 `/fbm rule set minecraft:tnt 600 1200 minecraft:kelp` 会提交成功并进入运行快照；`CompatibilityLevel` 的 FULL/PARTIAL/UNSUPPORTED 从未被写入或用于拦截，运行时永远是 `UNVERIFIED`。需要决定是硬拦（例如要求目标必须是 `PathfinderMob`）还是只在 GUI 显示强警告、命令层放行。
2. **需求 §21 写明单机玩家可以修改，实现是根节点一律 `hasPermission(2)`。** 未开作弊的单人世界连命令树都看不到。这条结论直接决定 P1 GUI 的服务端权限校验怎么写，必须在动 GUI 之前定下来。

## 已知问题 / 风险

- Aquaculture、Upgrade Aquatic 与 Youkai Tuna 的服务器加载和实体创建已验证，但原生 AI 与 FBM 导航的长期竞争、玩家喂食、渲染、幼体尺寸和 Variant 继承观感仍需可见客户端人工验收。
- `FbmTroughSelfFeedGoal` 的真实水下导航仍未验证。优先级已降到 3、垂直搜索已放宽到 ±4，但"鱼能否实际游到槽位"取决于水体形状与 `WaterBoundPathNavigation`，只能由客户端观察确认。
- Variant 继承只覆盖 `Bucketable` 与 `VariantHolder` 两条公共契约。把 Variant 存在私有 `SynchedEntityData`、自定义 Genome 或数据组件中、且不参与桶数据往返的第三方实体仍然只能得到默认个体。这符合需求 §12.1，但需要在客户端验收中逐条记录哪些鱼实际继承成功。
- 当前 Youkai 4.2.13+1 与 Blueprint 8.1.1 Jar 自带数据资源错误（Youkai 10 个 kettle 配方把 `result.amount` 错写为 `result.count`，产生 20 条 ERROR；Blueprint 自带 `modid:example` 维度 data map，产生 1 条 ERROR）。不阻断服务器和 FBM，但正式整合包发布前应寻找修正版或上游修复，不应由 FBM 接管第三方资源。
- `importedEntities` 目前只是持久化基础；直接调用其增删方法不会形成完整的服务端事务、权限校验和 Snapshot 发布工作流，P1 不应从 GUI 直接操作 SavedData。
- 自动发现后端没有命令或 GUI 查询入口；快照已可供 P1 网络/界面消费，但玩家侧展示尚未实现。
- GUI 需要处理未知或不可安全实例化的第三方实体，尤其是 3D 预览；3D 预览优先级低于基础编辑流程，不应阻塞 P1 核心闭环。
- 历史人工验收指出：未命名鳕鱼可能被原版自然消失机制删除，不能用作区块卸载恢复测试；应先命名实体或确保只卸载不删除。
- 喂食槽适配依赖上游 1.21.1 方块 ID 与标准 `Container` 槽位 0 契约。若上游改变库存契约，FBM 会安全视为无来源，但需要新增版本适配；当前只声明 1.1.2 / MC 1.21.1 目标。
- `neoforge.mods.toml` 仍是 MDK 模板：`authors`、`issueTrackerURL`、`displayURL`、`logoFile` 全为空或被注释。需要用户提供真实信息后填写，不应由 Agent 编造。
- `discovery.ComponentCopies` 用 Codec JSON 往返做防御性深拷贝（约束 17），每个候选构造 2 次、每次 accessor 再 1 次。210 个 EntityType 的成本可接受，但 2000+ EntityType 的大整合包每次发现重建都要付这个代价；若成为瓶颈可改为基于 `MutableComponent.create` 的手写深拷贝。
- 全局 `local-environment` 规则描述 WSL2，但本会话实际工具环境是 Windows 上的 Git Bash（`python` 为 Windows Python，`/tmp` 映射到 `%LOCALAPPDATA%\Temp`）。执行命令时以当前可验证环境为准，不据此改动全局环境。
- `ServerLifecycleHandler` 与 `BreedingController.onLevelTick` 主循环仍无事件级回归测试，目前只覆盖 `applySpawnResult`/`isValidPair` 两个纯函数。

## 剩余工作

### P1（下一阶段）

1. 用户使用 `客户端待测试包_2026-08-31/`（Jar 已是含 Variant 继承的最新构建，无需手动替换），在可见客户端完成原版鳕鱼、热带鱼、Smallmouth Bass、Upgrade Aquatic Pike/Perch/Lionfish、Youkai Tuna 及喂食槽的完整繁殖、Variant 继承、渲染、扣料和规则热更新验收；按包内 Word 清单和参考验收记录执行。E 组结果不可与 08-30 版本直接对比。
2. 为导入/取消导入建立服务端事务 API，补齐实体存在性校验、权限边界、与规则删除的确认语义及发现 Snapshot 更新。
3. 实现完整 Entity Registry 的高级搜索，并为危险或无法展示的实体提供安全降级。
4. 实现服务端到客户端的候选/规则同步与客户端到服务端的编辑 Payload；所有写入由服务端再次校验。
5. 实现基础 Entity Browser 与 Rule Editor GUI：搜索、来源、ID、食物列表/Tag、冷却、成长、启用状态、保存和热应用。
6. 为导入、网络权限、参数校验和 GUI 保存闭环补充自动化测试与人工验收记录。
7. 补 `ServerLifecycleHandler` 与 `BreedingController.onLevelTick` 主循环的事件级回归测试。

### P2（P1 稳定后）

- 3D 实体预览、`BreedingAdapter` 式 Variant/行为适配 API（覆盖私有 Variant 存储）、第三方 Preset、Tag/Namespace 批量管理和其他增强能力。

## 推荐下一步

1. 先答"待决策事项"里的两个问题（§42 拦截语义、§21 单机权限），它们决定 GUI 的服务端校验形状。
2. 用 `客户端待测试包_2026-08-31/` 启动可见客户端，按包内统一清单跑完 A–G 组；重点是喂食槽自动取食（本轮改了优先级和搜索范围）与热带鱼 Variant 继承（本轮新增），这两项此前从未被真实观察过。
3. 客户端确认没有公共契约缺口后，再锁定导入事务、网络数据模型和 GUI 最小闭环。仍然不要先写 GUI。
