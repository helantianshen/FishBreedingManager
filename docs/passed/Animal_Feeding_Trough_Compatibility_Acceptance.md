# Animal Feeding Trough 兼容验收记录

> 2026-09-23 更新：本提交的三 Jar 客户端槽喂、60 秒冷却、规则开关、重进世界与原版牛回归已获用户实机确认。其余兼容项目按用户要求暂停，完整指令、预期及证据边界见 [892cbf9 客户端验收手册](../pending/Compatibility_Test_Manual.md)。下文运行时边界为 2026-08-29 的历史状态，不代表当前仍缺少喂食槽依赖。

## 范围与版本

- 目标 Mod：Animal Feeding Trough。
- 目标版本：`1.1.2+1.21-neoforge`，支持 Minecraft 1.21/1.21.1 NeoForge。
- 上游 Mod ID：`animal_feeding_trough`。
- 上游方块 ID：`animal_feeding_trough:feeding_trough`。
- 上游前置：Architectury API。
- 上游源码依据：[`AnimalEntityMixin`](https://github.com/Slexom/animal-feeding-trough/blob/1.21.1/common/src/main/java/slexom/animal_feeding_trough/platform/common/mixin/AnimalEntityMixin.java)、[`SelfFeedGoal`](https://github.com/Slexom/animal-feeding-trough/blob/1.21.1/common/src/main/java/slexom/animal_feeding_trough/platform/common/goal/entity/ai/SelfFeedGoal.java)、[`FeedingTroughBlockEntity`](https://github.com/Slexom/animal-feeding-trough/blob/1.21.1/common/src/main/java/slexom/animal_feeding_trough/platform/common/block/entity/FeedingTroughBlockEntity.java)。

上游只向 `Animal` 注入自助喂食目标，并在到达后调用 `Animal#setInLove`。FBM 适配覆盖有启用规则、继承 `PathfinderMob` 但不继承 `Animal` 的鱼类实体；原版 `Animal` 继续完全由上游处理。

## 实现契约

- 玩家右键和自动喂食槽共用 `BreedingFeedService`，规则在行为发生时从当前 Snapshot 动态读取。
- Love 服务不直接修改传入物品；玩家入口在收到 `FED` 后按创造模式语义扣手持物，容器入口则把扣料回调交给服务，在资格通过且来源成功移除一个物品后才提交 Love，避免免费 Love。
- 喂食槽来源只按稳定方块 Registry ID 识别，并通过 Minecraft 标准 `Container` 读取/移除槽位 0；FBM 对 Animal Feeding Trough 和 Architectury 均无编译硬依赖。
- `FbmTroughSelfFeedGoal` 水平搜索半径为 8 格，垂直搜索半径为 4 格，接受距离为 2 格；目标搜索、继续运行和实际喂食均重新验证当前规则、食物与实体状态。
- Goal 优先级为 3，必须严格小于原版 `AbstractFish` 中 `FishSwimGoal` 的 4，否则 `GoalSelector` 不允许打断正在运行的随机游动；同时保持大于 `AvoidEntityGoal`（2）与 `PanicGoal`（0）。
- Goal 在实体加入、初始规则加载和规则成功启用/更新时幂等安装；规则禁用或删除后已有 Goal 会因动态查询自动休眠。
- 不使用 Mixin、反射、ASM 或第三方私有字段。

## 自动化结果（2026-08-29）

- `BreedingFeedServiceTest`：成功 Love 不直接扣料；无规则/禁用/错误食物/冷却/幼体/已有 Love 均返回正确拒绝且无副作用；只读资格检查不提前写 Love；容器扣料失败不写 Love；多 tick 重试只消费一次。
- `AnimalFeedingTroughSourceTest`：正确 Registry ID/Container/槽位 0 可读取；错误方块不读取库存；成功精确移除一个物品。
- `FbmTroughSelfFeedGoalTest`：目标资格动态委托当前规则；运行途中规则/食物失效会停止；Love 成功后才扣料；状态拒绝时不扣料。
- `AnimalFeedingTroughCompatibilityTest`：只给启用规则的非 Animal PathfinderMob 安装；缺 Mod、未初始化、无规则、禁用规则和不支持实体均跳过；重复刷新不重复安装；Animal 层级排除；单个异常实体被隔离。
- `WorldBreedingServiceTest`：可选兼容刷新异常不会改变已经成功发布的核心规则事务结果。
- `EntityInteractionHandlerTest`：普通玩家成功只扣 1 并取消事件；创造模式免扣但取消事件；拒绝结果保持事件与物品不变。
- `./gradlew.bat test`：26 suites、78 tests、0 failures、0 errors、0 skipped。
- `./gradlew.bat javadoc --rerun-tasks`：BUILD SUCCESSFUL，无 Javadoc warning。
- `./gradlew.bat build`：BUILD SUCCESSFUL。

## 历史验收边界

2026-08-29 仅完成源码契约与自动化检查，当时未安装真实喂食槽依赖，不能作为客户端通过证据。2026-09 的客户端结果见 [892cbf9 已通过记录](892cbf9_Client_Acceptance.md)，后续步骤见 [待测手册](../pending/Compatibility_Test_Manual.md)。
