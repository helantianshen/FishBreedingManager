# Fish Breeding Manager 项目交接

## 当前目标

在已完成并验收的 P0 繁殖核心之上进入 P1。ModList/完整 EntityType Registry 自动扫描、多信号鱼类识别和原子发现快照的第一纵向切片已经完成；仓库外本地目录的五个顶层 Mod Jar 已完成真实开发服务器共存、候选核对和代表实体创建验收。

当前已完成 Animal Feeding Trough 1.1.2 的零硬依赖代码适配：有启用 FBM 规则、可寻路但不继承 `Animal` 的鱼会获得幂等自助喂食 Goal，并复用统一 Love 状态服务。下一目标是用户使用已整理的客户端待测包，在可见客户端统一完成 Aquaculture、Upgrade Aquatic、Youkai Tuna 和喂食槽的完整繁殖、渲染与热更新人工验收；之后再进入导入事务、网络和 GUI 最小闭环。

## 当前状态

- 技术基线：Minecraft 1.21.1、NeoForge 21.1.244、Java 21、ModDevGradle 2.0.143，Mod 版本 0.1.0。
- P0 B-lite 已实现：世界级规则持久化、不可变运行时快照、热重载、实体繁殖状态、喂食进入 Love、配偶搜索和寻路、同类型后代生成、父母冷却、FBM 幼体成长、客户端幼体缩放、管理员命令及生命周期恢复。
- `docs/testing/P0_Cod_Acceptance.md` 记录了 2026-08-12 的自动化检查与 11 项鳕鱼人工验收全部通过；这是历史验收记录，不等同于本次会话重新执行了完整人工验收。
- P1 自动发现后端第一纵向切片已完成：存在完整 Registry 候选、来源分组、识别理由、置信度、失效导入报告和按服务器隔离的运行快照；高级搜索 UI、导入管理服务、管理 Screen、规则编辑 Payload 与 GUI 保存链路仍未实现。
- `WorldBreedingData` 已具备 `importedEntities` 的 NBT 持久化及增删方法，`BreedingRuleSnapshot` 也携带该集合，但目前没有面向玩家的发现/导入工作流。
- 仓库外本地 Mod 目录的兼容矩阵：Aquaculture 2.7.21 为 28 HIGH；Upgrade Aquatic 7.0.1 为 3 HIGH（Lionfish、Perch、Pike）；Youkai's Homecoming 4.2.13+1 为 1 MEDIUM（Tuna）；Farmer's Delight 1.3.2 与 Blueprint 8.1.1 无 HIGH/MEDIUM。生产代码不依赖这些 Mod 的 ID、实体 ID 或 Java 类型。
- Animal Feeding Trough 兼容代码已完成；Animal Feeding Trough 1.1.2 与 Architectury 13.0.11 已放入一次性客户端待测包，但尚未执行真实客户端取食与繁殖验收，不能标记为运行时验收通过。

## 已完成工作

- 默认内置原版鳕鱼、鲑鱼、热带鱼和河豚规则。
- 规则按存档保存到主世界 SavedData，并在同一存档的所有维度间共享。
- 规则提交前进行完整校验；成功后整体替换 SavedData 工作集与 `volatile` 不可变 Snapshot，失败保留旧运行时状态。
- 实体状态通过 NeoForge Data Attachment 保存，规则不复制到实体。
- Love 活动索引避免每 Tick 扫描全部实体；配偶搜索半径当前固定为 8 格。
- 后代与父母保持相同 `EntityType`；生成失败不会错误提交冷却。
- 幼体状态由 FBM 自维护，不要求实体实现原生年龄接口；客户端在成年前固定显示为 50%，成年时恢复 100%。
- `/fbm reload` 与 `/fbm rule list|show|set|enable|disable|remove` 统一要求权限等级 2，并通过 `WorldBreedingService` 修改规则。
- 当前测试报告包含 27 个测试套件、80 个测试，全部通过。
- 已完成 Aquaculture 2 兼容设计：自动扫描已加载 Mod 与 Registry、按来源分组、多信号置信度识别、公共 Tag 自动发现、零硬依赖、通用兼容等级、开发运行时 Jar 策略、真实客户端验收矩阵和完成标准。
- 已实现 `discovery` 后端：扫描 `ModList` 和完整 `BuiltInRegistries.ENTITY_TYPE`，合并 `c:fish`/`c:fishes`、原版白名单、实体/来源关键词、Aquatic Tag、水生 MobCategory 和持久化导入信号；结果按来源稳定分组并以不可变快照原子发布。
- 已接入 `ServerStartedEvent`、服务端 `TagsUpdatedEvent` 和 `ServerStoppingEvent`：启动/Tag 重载时重建，失败保留旧快照，停止时清理服务器实例；不进行每 Tick 扫描。
- `build.gradle` 支持显式 `fbmCompatModsDir` 与可选 `fbmCompatModsInclude`：只把匹配到的普通 Jar 作为 `localRuntime` 加载，属性缺失时不影响常规构建，也不会发布第三方依赖或固化本机路径。
- 新增通用 `DiscoveryDiagnostics`，在发现成功后稳定记录每个有效来源的 HIGH/MEDIUM 数量和候选 Registry ID；LOW-only 来源不会被误报为可适配鱼类。
- Aquaculture 2.7.21 真实服务器验收通过：41 个注册实体中自动识别 28 HIGH、0 MEDIUM，`jellyfish` 未入选；Smallmouth Bass、Minnow、Arapaima、Gar 均可创建并查询，服务器正常保存停服且最终日志无 ERROR/Exception。
- 五 Jar 组合真实服务器验收通过：同时加载 Aquaculture、Upgrade Aquatic、Youkai、Farmer's Delight 与 Blueprint；FBM 扫描 210 个 EntityType，除既有来源外自动发现 Upgrade Aquatic 3 HIGH 和未打公共鱼类 Tag 的 Youkai Tuna 1 MEDIUM；四个新增候选均可创建并查询，服务器正常保存退出且没有 FBM ERROR。
- 已定位组合中的第三方资源问题：Youkai Jar 内 10 个 Farmer's Delight 命名空间 kettle 配方将 `result.amount` 错写为 `result.count`，产生 20 条配方相关 ERROR；Blueprint 自带 `modid:example` 维度 data map，产生 1 条 ERROR。它们不属于 FBM，也未阻止本轮扫描和实体创建。
- 已将玩家右键中的 Love 转换抽为 `BreedingFeedService`：每次动态读取当前规则，统一处理食物、幼体/冷却/Love 资格、Attachment、活动索引和爱心粒子；玩家入口仍在 `FED` 后按创造模式语义扣手持物，自动容器则由服务在资格通过后先确认消费回调成功，再提交 Love。
- 已实现 Animal Feeding Trough 适配：按 `animal_feeding_trough:feeding_trough` 与标准 `Container` 读取槽位 0，不导入第三方类型；给启用规则的非 `Animal` `PathfinderMob` 安装 `FbmTroughSelfFeedGoal`，并在实体 Join、初始 reload、规则 upsert/enable 后幂等补装。
- 已确认上游 1.21.1 源码仅向 `Animal` 注入自助喂食目标并调用 `Animal#setInLove`，这是原版鱼和多数第三方鱼无法直接使用喂食槽的结构性缺口；FBM 不接管原版 `Animal`，避免与上游双重扣料。
- 代码审查后已隔离兼容刷新异常：规则 Snapshot 一旦提交成功，可选 Goal 刷新失败只记录错误，不会对外伪报事务失败；单个异常实体也不会中断其余实体刷新。容器消费现在由 `BreedingFeedService` 在同一服务端调用栈中先确认成功再提交 Love，消除了扣料失败却免费 Love 的路径，并显式调用 `Container#setChanged`。
- 已完成源码目录与架构整理：`FishBreedingManager` 收敛为注册组成根，服务器启动/Tag 重载/停止清理由 `server/ServerLifecycleHandler` 协调；喂食能力移动到 `breeding/feed`，后代生成移动到 `breeding/spawn`，测试目录同步镜像。
- 已新增 `CompatibilityCoordinator` 作为核心与具体第三方适配之间的统一边界。当前 Animal Feeding Trough 通过该入口接入；单个兼容模块异常不会阻断后续模块，也不会反向污染已经发布的规则 Snapshot。
- 已新增 `docs/ARCHITECTURE.md`，记录当前包职责、依赖方向和 P1 新代码放置规则；实际 Gradle/Git 工程根目录保持不变。
- 已在工程外层工作区创建一次性 `客户端待测试包_2026-08-30/`：包含当前 FBM 构建 Jar、五个既有本地第三方 Jar、Animal Feeding Trough 1.1.2、Architectury 13.0.11、一份统一客户端逐项清单 Word 文档和四份历史验收 Word 文档。5 份文档全部采用黑色宋体；8 个 Jar 均按 `[Mod中文名]Mod本名` 命名。该目录只用于用户复制测试，后续可整体删除，不属于生产源码或发布内容。

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
10. 无法安全识别第三方 Variant 时创建默认个体；未来通过显式 Adapter 扩展，不猜测第三方私有实现。
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
- `src/main/java/com/fishbreedingmanager/breeding/spawn/`：同 EntityType 后代创建及结构化结果。
- `src/main/java/com/fishbreedingmanager/server/ServerLifecycleHandler.java`：规则加载、发现重建和服务器会话清理。
- `src/main/java/com/fishbreedingmanager/compat/CompatibilityCoordinator.java`：核心面向的可选第三方兼容统一入口与故障隔离。
- `src/main/java/com/fishbreedingmanager/compat/feedingtrough/`：Animal Feeding Trough 的标准 Container 来源、非 Animal 自助喂食 Goal 和生命周期协调器。
- `src/test/java/com/fishbreedingmanager/compat/feedingtrough/`：喂食槽来源、动态 Goal、安装资格与幂等回归测试。
- `docs/superpowers/specs/2026-08-29-animal-feeding-trough-compatibility-design.md`：喂食槽兼容边界和验收标准。
- `docs/superpowers/plans/2026-08-29-animal-feeding-trough-compatibility.md`：本轮 TDD 实施计划。
- `docs/testing/Animal_Feeding_Trough_Compatibility_Acceptance.md`：自动化证据、本地 Jar 缺口和用户统一人工测试清单。
- `E:/JavaCodes/FishBreedingManager/客户端待测试包_2026-08-30/00_客户端统一测试清单.docx`：一次性客户端统一测试入口；同目录 `mods/` 含 8 个按 `[Mod中文名]Mod本名` 命名的待测 Jar，`参考验收记录/` 含四份 Word 记录。
- `src/main/java/com/fishbreedingmanager/persistence/WorldBreedingData.java`：按存档规则与导入实体集合的 SavedData。
- `src/main/java/com/fishbreedingmanager/command/FBMCommands.java`：当前管理员管理入口。
- `src/main/java/com/fishbreedingmanager/network/` 与 `client/`：目前仅含幼体状态下发和客户端缩放；P1 编辑网络与 GUI 尚未实现。
- `src/test/java/com/fishbreedingmanager/`：现有 P0 自动化回归测试。

## 验证情况

- 历史记录（2026-08-12）：`gradlew test`、`gradlew build`、`gradlew javadoc`、开发客户端启动检查通过；鳕鱼 11 项人工验收通过。详情以 `docs/testing/P0_Cod_Acceptance.md` 为准。
- 本次会话：已静态核对需求、项目总结、验收记录、构建配置、主要生产代码和测试清单。
- 本次会话自动化（2026-08-29）：`.\gradlew.bat test` 成功（26 suites、78 tests、0 failures/0 errors/0 skipped），`.\gradlew.bat javadoc --rerun-tasks` 成功且无 Javadoc 警告，`.\gradlew.bat build` 成功。Gradle 仍提示使用了将在 Gradle 10 中不兼容的弃用特性，尚未展开 `--warning-mode all` 定位来源。
- 本次会话真实第三方运行：精确加载仓库外 Aquaculture 2.7.21 Jar；启动日志确认 28 HIGH、0 MEDIUM，完整 ID 已记录；四种代表实体创建/查询成功，测试实体已清理，RCON 已恢复关闭，服务端正常保存退出，最终日志没有 ERROR、Exception 或 Caused by 行。
- 本次会话五 Jar 共存运行：Gradle 确认加载 5 个本地 Jar；Mod List 版本全部吻合；FBM 总扫描 210 个 EntityType，Upgrade Aquatic 为 3 HIGH/0 MEDIUM，Youkai 为 0 HIGH/1 MEDIUM，Farmer's Delight 与 Blueprint 无 HIGH/MEDIUM；Pike、Perch、Lionfish、Youkai Tuna 创建/查询成功。测试实体已清理，RCON 已恢复关闭，三维度正常保存。
- 五 Jar组合有 21 条第三方数据资源 ERROR：20 条来自 Youkai 的 10 个格式不匹配 kettle 配方，1 条来自 Blueprint 的 `modid:example` data map。没有 FBM ERROR，详见组合验收记录。
- 本次会话未执行：可见开发客户端中的玩家喂食、Love、寻路、后代、成长与渲染人工验收。
- Animal Feeding Trough 本轮已完成官方 1.21.1 源码契约核对与自动化实现；本地目录没有该 Mod 及 Architectury 前置，所以未执行真实喂食槽加载、主动取食或双重扣料客户端观察。
- Aquaculture 静态调查仍作为背景证据：本地 2.7.21 Jar 无额外必需前置，28 种鱼通过公共 `c:fish` 暴露，且 `AquaFishEntity extends AbstractSchoolingFish`；真实服务器结果已经验证该公共契约可被 FBM 扫描并创建实体。
- 本次架构整理（2026-08-30）：重构前基线测试通过；整理后 `.\gradlew.bat test --rerun-tasks` 成功（27 suites、80 tests、0 failures/0 errors/0 skipped），`.\gradlew.bat javadoc --rerun-tasks` 成功且无警告，`.\gradlew.bat build --rerun-tasks` 成功。未改变存档格式、命令、网络 Payload 或第三方 Registry 契约。
- 本次架构整理独立代码审查结论为 Ready，无 Critical/Important 问题。Minor 仅为生命周期抽取尚无专门事件回归测试，以及公开 Java 包移动对潜在外部编译依赖属于破坏；FBM 0.1.0 尚未承诺稳定 Addon API，后续若开放应新增独立稳定 `api` 包。
- 客户端待测包整理（2026-08-30）：8 个 Jar 均可作为 ZIP/Jar 读取；Animal Feeding Trough 与 Architectury 下载文件通过 Modrinth SHA-512 校验，全部 Jar 的大小与 SHA-256 已写入统一清单。未移动或删除原本地 Jar，也未把第三方文件加入 Gradle/Git 工程。
- 客户端待测包文档整理（2026-08-30）：5 份 Markdown 副本已转换为 Word；共 19 页通过 Microsoft Word 渲染逐页检查，无截断、重叠或表格拆行异常；OOXML 结构检查覆盖 853 个文本片段，字体全部为宋体、颜色全部为 `000000`。8 个 Jar 重命名后 SHA-256 均未变化且仍可正常作为 ZIP/Jar 读取。

## 已知问题 / 风险

- Aquaculture、Upgrade Aquatic 与 Youkai Tuna 的开发服务器加载和代表实体创建已经验证，但原生 AI/FBM 导航竞争、玩家喂食、渲染、幼体尺寸及默认后代 Variant 行为仍需可见客户端人工验收。
- 当前 Youkai 4.2.13+1 与 Blueprint 8.1.1 Jar 含上述数据资源错误；虽然不阻断服务器和 FBM，正式整合包发布前应优先寻找修正版 Mod 或上游修复，不应由 FBM 接管第三方资源。
- `importedEntities` 目前只是持久化基础；直接调用其增删方法不会自动形成完整的服务端事务、权限校验和 Snapshot 发布工作流，P1 不应从 GUI 直接操作 SavedData。
- 自动发现后端目前没有命令或 GUI 查询入口；快照已可供后续 P1 网络/界面消费，但真实玩家侧展示尚未实现。
- GUI 需要处理未知/不可安全实例化的第三方实体，尤其是 3D 预览；3D 预览属于低于基础编辑流程的后续增强，不应阻塞 P1 核心闭环。
- 历史人工验收指出：未命名鳕鱼可能被原版自然消失，不能用作区块卸载恢复测试；应先命名实体或保证只卸载不删除。
- 当前环境中的全局 `local-environment` policy 描述 WSL2，但本会话实际工具环境是 Windows PowerShell；执行命令时以当前可验证环境为准，不据此改动全局环境。
- 喂食槽适配依赖上游 1.21.1 方块 ID 与标准 `Container` 槽位 0 契约；若上游未来版本改变库存契约，FBM 会安全视为无来源，但需要新增版本适配。当前只声明 1.1.2/MC 1.21.1 目标。
- `FbmTroughSelfFeedGoal` 的真实水下导航、与第三方鱼原生 AI 的优先级竞争以及规则 disable/enable 的玩家侧表现仍需可见客户端验证。

## 剩余工作

### P1（下一阶段）

1. 用户使用一次性客户端待测包，在可见客户端完成 Smallmouth Bass、Upgrade Aquatic Pike/Perch/Lionfish、Youkai Tuna 及喂食槽的完整繁殖、渲染、扣料和规则热更新验收；按包内 Word 清单和参考验收记录执行。
2. 为导入/取消导入建立服务端事务 API，补齐实体存在性校验、权限边界、与规则删除的确认语义及发现 Snapshot 更新。
3. 实现完整 Entity Registry 的高级搜索，并为危险或无法展示的实体提供安全降级。
4. 实现服务端到客户端的候选/规则同步与客户端到服务端的编辑 Payload；所有写入由服务端再次校验。
5. 实现基础 Entity Browser 与 Rule Editor GUI：搜索、来源、ID、食物列表/Tag、冷却、成长、启用状态、保存和热应用。
6. 为导入、网络权限、参数校验和 GUI 保存闭环补充自动化测试与人工验收记录。

### P2（P1 稳定后）

- 3D 实体预览、Variant/Adapter API、Preset、Tag/Namespace 批量管理和其他增强能力。

## 推荐下一步

先不要直接写 GUI。先使用一次性客户端待测包启动可见客户端，按包内统一清单以及 Aquaculture、本地鱼类组合和喂食槽三份 Word 验收记录，完成玩家交互、自动取食、完整繁殖、热更新和渲染观察；同时保留 Youkai/Blueprint 第三方数据资源风险。若无公共契约缺口，再锁定导入事务、网络数据模型和 GUI 最小闭环。
