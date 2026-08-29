# Animal Feeding Trough 兼容设计

## 目标

让安装了 Animal Feeding Trough 1.1.2（Minecraft 1.21/1.21.1 NeoForge）的服务端中，受 FBM 启用规则管理、具备寻路能力但不继承原版 `Animal` 的鱼类，也能寻找装有其 FBM 繁殖食物的喂食槽，消费一份食物并进入 FBM Love。

第三方鱼类仍由 FBM 现有 ModList/EntityType Registry 自动扫描和多信号识别体系发现；本适配只补齐“外部自动喂食源”入口，不新增鱼类 Mod 白名单。

## 上游行为与缺口

Animal Feeding Trough 1.21.1 的 `AnimalEntityMixin` 只注入 `Animal`，并从精确类型为 `TemptGoal` 的目标复制食物谓词。其 `SelfFeedGoal` 到达喂食槽后从槽位 0 扣除一个物品并调用 `Animal#setInLove(null)`。

原版鱼、Aquaculture 鱼及多数鱼类 Mod 实体继承 `AbstractFish`/`PathfinderMob`，而不是 `Animal`。因此它们既不会获得上游 `SelfFeedGoal`，也不能用 `Animal#setInLove` 表达 FBM Attachment 中的 Love 状态。

## 设计

### 1. 统一喂食状态转换

新增 `BreedingFeedService`，作为玩家右键与外部自动喂食的共同服务端入口。每次尝试都动态读取当前 `BreedingRuleManager` 快照，并按以下顺序检查：

1. 规则存在且启用；
2. 提供的 `ItemStack` 匹配规则的 Item/Item Tag；
3. Attachment 当前不是幼体、冷却或 Love；
4. 成功时写入 600 tick Love、加入 `ActiveLoveIndex` 并发送爱心粒子。

服务不直接修改传入的 `ItemStack`。玩家入口继续在收到 `FED` 后按创造模式语义扣除手持物；自动容器入口则向服务提供一个消费回调，服务在资格检查通过后先确认来源成功移除一个物品，再原子提交 Love。来源消费失败返回 `SOURCE_UNAVAILABLE`，不会写 Love、索引或粒子。

### 2. 无硬依赖的喂食槽来源

新增 `AnimalFeedingTroughSource`，通过 Registry ID `animal_feeding_trough:feeding_trough` 识别方块，并仅使用 Minecraft 标准 `Container` 接口读取和移除槽位 0。

生产代码不导入 Animal Feeding Trough 或 Architectury 的 Java 类，不复制其私有字段，也不修改或再分发第三方 Jar。方块实体缺失、不是 `Container`、槽位为空或方块 ID 不匹配时均视为不可用，不抛出兼容异常。

### 3. 非 Animal 鱼的自助喂食目标

新增 `FbmTroughSelfFeedGoal extends MoveToBlockGoal`：

- 只安装到有启用 FBM 规则的 `PathfinderMob`；
- 排除 `Animal`，避免与上游目标重复扣料；
- 搜索半径 8 格、目标距离 2 格；
- 搜索、继续执行和最终扣料前均动态检查当前规则和食物；
- 到达后由 `BreedingFeedService` 在同一服务端调用栈中协调扣料与 Love：资格通过、`Container` 成功移除一个物品后才提交 Love；
- 规则被禁用、删除、食物被换走或实体进入冷却时立即停止有效执行。

### 4. 生命周期与热更新

新增 `AnimalFeedingTroughCompatibility` 协调器：

- 仅在 `ModList` 确认 `animal_feeding_trough` 已加载时工作；
- 实体加入服务端 Level 时尝试安装目标；
- 初始规则重载以及命令热更新成功后遍历一次当前已加载实体，给新启用规则对应的实体补装目标；
- 通过检查 `GoalSelector` 中已有 `FbmTroughSelfFeedGoal` 保证幂等；
- 禁用或删除规则无需移除目标，因为目标的每次行为判断都读取当前快照并自动休眠。

遍历只发生在规则发布与实体加入边界，不增加每 tick 全世界实体扫描。

## 非目标

- 不替换 Animal Feeding Trough 对原版 `Animal` 的行为。
- 不自动为扫描候选创建或启用 FBM 繁殖规则。
- 不适配其他未知喂食器的方块 ID 或私有库存实现。
- 不处理喂食槽的经验存储、GUI、漏斗或配方。
- 不保证没有 `PathfinderMob` 导航能力的实体能主动靠近喂食槽。

## 验收标准

### 自动化

- 玩家交互与喂食槽共用同一 Love 状态转换，食物只在成功时由来源消费。
- 无规则、禁用、错误食物、冷却、幼体或已有 Love 均不消费。
- 喂食槽来源只接受目标 Registry ID 与标准 `Container` 槽位 0。
- 兼容目标仅对已加载喂食槽 Mod、启用规则、非 `Animal` 的 `PathfinderMob` 安装，且重复刷新不重复添加。
- 全量单元测试、Javadoc 与构建通过。

### 人工整合

用户提供 Animal Feeding Trough 1.1.2 NeoForge 与对应 Architectury API Jar 后，在可见开发客户端统一验证：

1. 原版鳕鱼、Aquaculture 代表鱼、Upgrade Aquatic 代表鱼和 Youkai Tuna 各放置两只成年实体；
2. 水中可达位置放置喂食槽，并在槽位 0 放入各实体当前 FBM 规则食物；
3. 两只鱼主动接近槽、每只只消费一个食物并出现爱心；
4. 生成同 EntityType 后代，父母进入冷却，幼体按 FBM 规则成长；
5. 错误食物、幼体、冷却中或已 Love 的实体不扣料；
6. 禁用规则后现存实体立即停止使用槽，重新启用后无需重进世界即可恢复；
7. 原版 `Animal` 仍由 Animal Feeding Trough 自己处理，不发生双重扣料。
