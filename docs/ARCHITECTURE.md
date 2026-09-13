# Fish Breeding Manager 架构说明

本文描述当前源码的职责边界和依赖方向。产品行为以 `Fish_Breeding_Manager_Requirements.md` 为准，跨会话进度以 `.agent/HANDOFF.md` 为准。

## 1. 工程入口

- `FishBreedingManager` 是服务端与通用逻辑的组成根，只注册 Attachment、网络、命令和 `ServerLifecycleHandler`。
- `FishBreedingManagerClient` 只注册客户端同步与渲染行为。
- `server/ServerLifecycleHandler` 负责规则初始加载、已加载实体恢复、发现快照重建和服务器停止清理。

入口类不承载玩法算法。新增生命周期工作优先放入 `server` 或对应功能包，再由入口完成注册。

## 2. 源码目录

```text
com/fishbreedingmanager/
├─ attachment/            实体 Attachment 注册
├─ breeding/              规则、实体状态、运行时快照和繁殖编排
│  ├─ feed/               所有喂食来源共享的 Love 转换与粒子反馈
│  └─ spawn/              后代创建、Variant 继承及结构化生成结果
├─ client/                客户端幼体状态缓存与渲染缩放
├─ command/               服务端管理员命令适配层
├─ compat/                第三方兼容统一入口
│  └─ feedingtrough/      Animal Feeding Trough 的具体适配
├─ discovery/             Mod/EntityType 扫描、分类和不可变发现快照
├─ event/                 实体交互、加入、离开和追踪事件适配层
├─ network/               Payload 注册与稳定传输模型
├─ persistence/           按存档保存的规则与导入实体集合
└─ server/                服务器生命周期协调
```

测试目录镜像生产包。包级测试构造器随能力一起移动，避免为了测试扩大生产 API。

当前版本为 0.1.0，尚未发布稳定的第三方 Java API。`breeding.feed` 与 `breeding.spawn` 是本次目录整理后的包名；若未来正式开放 Addon API，应在独立的稳定 `api` 包中提供契约，并通过弃用周期迁移，不应让 Addon 直接依赖内部运行时包。

## 3. 核心所有权

- Rule 属于世界：`WorldBreedingData` 是持久化权威，`BreedingRuleSnapshot` 是运行时只读视图。
- State 属于实体：`BreedingState` Attachment 保存 Love、冷却和成长截止时间。
- 服务端是唯一玩法权威：命令、未来 GUI Payload 和第三方来源最终都必须调用服务端事务或运行时服务。
- 行为发生时读取当前 Snapshot：不得把完整规则缓存到实体、Goal 或客户端。

## 4. 依赖方向

```text
entrypoint -> server/event/command/network registration
server/event/command -> breeding + discovery
breeding -> persistence + attachment + network contracts
compat -> breeding public services + Minecraft stable contracts
core lifecycle -> CompatibilityCoordinator -> concrete compat adapters
client -> network state only
```

关键限制：

1. `breeding`、`event` 和 `server` 不直接引用具体第三方适配包；统一经过 `CompatibilityCoordinator`。
2. 具体兼容适配不得引用第三方 Java 类型，优先使用 Registry ID、Tag 和 Minecraft 公共接口。
3. `discovery` 只识别候选，不擅自启用玩法规则；管理员导入与规则提交属于后续独立事务层。
4. `command` 和未来 GUI 都是输入适配层，不自行复制校验、持久化或 Snapshot 发布逻辑。
5. `client` 不决定权限、规则有效性、繁殖结果或持久化状态。

## 5. 热更新与失败边界

规则变更先构造完整候选集合并校验，通过后才替换 SavedData 工作集和不可变 Snapshot。失败保留旧状态。规则发布后的兼容刷新是 best-effort：单个兼容模块或实体失败只记录日志，不回滚已经提交的规则，也不阻断其余模块。

## 6. 新代码放置指南

- 新的喂食来源：调用 `breeding.feed.BreedingFeedService`，来源只负责提供与消费物品。
- 新的后代创建策略：放入 `breeding.spawn`，成功前不得修改父母状态。
- 新的 Variant 继承通道：放入 `breeding.spawn.VariantInheritance`，只允许使用 Minecraft 公共契约（当前为 `Bucketable` 桶数据与 `VariantHolder` 接口），禁止反射、ASM 或私有字段复制；无法识别时必须安全退回默认个体。
- 新的第三方 Mod：在 `compat/<mod>` 实现，并只通过 `CompatibilityCoordinator` 接入核心生命周期。
- 新的注入式 AI 目标：优先级必须严格小于需要抢占的原版目标，`GoalSelector` 只允许更小的数字打断正在运行的目标。原版 `AbstractFish` 的 `FishSwimGoal` 在 4、`AvoidEntityGoal` 在 2、`PanicGoal` 在 0，因此鱼类目标使用 3。同时不要依赖 `MoveToBlockGoal` 三参构造器的默认 ±1 垂直搜索范围，水生实体需显式放宽。
- 成对的客户端渲染事件：Pre 与 Post 必须使用镜像优先级（`LOWEST` 压栈对应 `HIGHEST` 弹栈），保证 FBM 的矩阵是最内层且最先弹出。
- 新的扫描信号或候选字段：放入 `discovery`，保持 Snapshot 深度不可变和稳定排序。
- 新的 GUI/网络写操作：客户端只发送意图；服务端校验权限、Registry 和参数后复用统一事务服务。
- 新增或修改的公共类型、公共 API 与关键私有流程使用中文 Javadoc，不要混入英文文档注释。

## 7. 验证入口

```powershell
.\gradlew.bat test --rerun-tasks
.\gradlew.bat javadoc --rerun-tasks
.\gradlew.bat build --rerun-tasks
```

第三方 Mod 的真实运行与人工验收步骤位于 `docs/testing/`；自动化通过不能替代水下寻路、渲染和容器扣料的可见客户端验收。
