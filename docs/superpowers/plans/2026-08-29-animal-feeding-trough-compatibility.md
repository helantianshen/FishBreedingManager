# Animal Feeding Trough Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Animal Feeding Trough 能以当前 FBM 规则驱动非 `Animal` 鱼类自动取食并进入 FBM Love，同时保持零硬依赖和热更新语义。

**Architecture:** 先把玩家交互中的 Love 转换抽成不负责扣料的 `BreedingFeedService`，再用标准 `Container` 实现喂食槽来源，并给符合条件的 `PathfinderMob` 幂等安装一个 `MoveToBlockGoal`。规则发布和实体加入时补装目标；目标执行时始终动态查询当前规则。

**Tech Stack:** Java 21、Minecraft 1.21.1、NeoForge 21.1.244、ModDevGradle 2.0.143、JUnit 5、Mockito 5。

---

### Task 1: 统一喂食状态转换

**Files:**
- Create: `src/main/java/com/fishbreedingmanager/breeding/BreedingFeedService.java`
- Create: `src/test/java/com/fishbreedingmanager/breeding/BreedingFeedServiceTest.java`
- Modify: `src/main/java/com/fishbreedingmanager/event/EntityInteractionHandler.java`

- [x] **Step 1: 写成功、错误食物和不可进入 Love 的失败测试**

测试用真实 `BreedingState`、模拟的 `BreedingRuleManager` 与注入式索引/粒子回调，断言 `tryFeed` 返回 `FED` 时 Love 截止为 `now + 600`，但不修改提供的 `ItemStack`；拒绝路径不触发任何副作用。

- [x] **Step 2: 运行定向测试并确认 RED**

Run: `./gradlew.bat test --tests com.fishbreedingmanager.breeding.BreedingFeedServiceTest`

Expected: FAIL，因为 `BreedingFeedService` 尚不存在。

- [x] **Step 3: 实现最小服务与结果枚举**

提供普通玩家使用的 `tryFeed(ServerLevel, Entity, ItemStack, BreedingRuleManager)`，以及自动来源使用的消费回调重载；顺序返回 `NO_RULE`、`RULE_DISABLED`、`WRONG_FOOD`、`INELIGIBLE`、`SOURCE_UNAVAILABLE` 或 `FED`。自动来源只有消费成功后才写状态、登记索引并发粒子。

- [x] **Step 4: 让玩家交互委托服务**

`EntityInteractionHandler` 只保留服务端/玩家/事件语义：收到 `FED` 后按创造模式规则扣除一个手持物、取消事件并记录日志；其他结果继续交给原版或其他 Mod。

- [x] **Step 5: 运行定向测试并确认 GREEN**

Run: `./gradlew.bat test --tests com.fishbreedingmanager.breeding.BreedingFeedServiceTest`

Expected: PASS。

### Task 2: 标准 Container 喂食槽来源

**Files:**
- Create: `src/main/java/com/fishbreedingmanager/compat/feedingtrough/AnimalFeedingTroughSource.java`
- Create: `src/test/java/com/fishbreedingmanager/compat/feedingtrough/AnimalFeedingTroughSourceTest.java`

- [x] **Step 1: 写来源识别、读取和扣料失败测试**

用可注入的期望方块 ID 在测试中指向 `minecraft:chest`，模拟实现 `Container` 的方块实体；断言仅正确方块 ID 与槽位 0 可读取，`consumeOne` 恰好移除一个物品，错误方块/非容器/空槽返回失败。

- [x] **Step 2: 运行定向测试并确认 RED**

Run: `./gradlew.bat test --tests com.fishbreedingmanager.compat.feedingtrough.AnimalFeedingTroughSourceTest`

Expected: FAIL，因为来源类尚不存在。

- [x] **Step 3: 实现无第三方类型依赖的来源**

生产实例固定使用 `animal_feeding_trough:feeding_trough`，通过 `BuiltInRegistries.BLOCK` 核对 ID，通过 `BlockEntity instanceof Container` 获取槽位 0，并用 `removeItem(0, 1)` 扣料和标记库存变化。

- [x] **Step 4: 运行定向测试并确认 GREEN**

Run: `./gradlew.bat test --tests com.fishbreedingmanager.compat.feedingtrough.AnimalFeedingTroughSourceTest`

Expected: PASS。

### Task 3: 非 Animal 自助喂食 Goal

**Files:**
- Create: `src/main/java/com/fishbreedingmanager/compat/feedingtrough/FbmTroughSelfFeedGoal.java`
- Create: `src/test/java/com/fishbreedingmanager/compat/feedingtrough/FbmTroughSelfFeedGoalTest.java`

- [x] **Step 1: 写动态资格、食物变化与成功扣料失败测试**

通过模拟来源与真实 `BreedingState` 验证：规则或食物不匹配时目标无效；到达提交成功后只扣一个；服务拒绝时不扣；规则在目标创建后禁用时立即失效。

- [x] **Step 2: 运行定向测试并确认 RED**

Run: `./gradlew.bat test --tests com.fishbreedingmanager.compat.feedingtrough.FbmTroughSelfFeedGoalTest`

Expected: FAIL，因为 Goal 尚不存在。

- [x] **Step 3: 实现 MoveToBlockGoal**

搜索半径设为 8、接受距离设为 2；`canUse`、`canContinueToUse`、`isValidTarget` 与到达后的提交均复用当前规则检查。把来源 `consumeOne` 作为回调交给 `BreedingFeedService.tryFeed`，只有扣料成功才提交 Love 并返回 `FED`。

- [x] **Step 4: 运行定向测试并确认 GREEN**

Run: `./gradlew.bat test --tests com.fishbreedingmanager.compat.feedingtrough.FbmTroughSelfFeedGoalTest`

Expected: PASS。

### Task 4: 幂等安装与热更新

**Files:**
- Create: `src/main/java/com/fishbreedingmanager/compat/feedingtrough/AnimalFeedingTroughCompatibility.java`
- Create: `src/test/java/com/fishbreedingmanager/compat/feedingtrough/AnimalFeedingTroughCompatibilityTest.java`
- Modify: `src/main/java/com/fishbreedingmanager/event/EntityLifecycleHandler.java`
- Modify: `src/main/java/com/fishbreedingmanager/breeding/WorldBreedingService.java`

- [x] **Step 1: 写安装资格与幂等失败测试**

断言未加载 Mod、非 `PathfinderMob`、`Animal`、无规则和禁用规则均不安装；符合条件时以固定优先级加入一次；重复调用或现有 Goal 不重复加入。

- [x] **Step 2: 运行定向测试并确认 RED**

Run: `./gradlew.bat test --tests com.fishbreedingmanager.compat.feedingtrough.AnimalFeedingTroughCompatibilityTest`

Expected: FAIL，因为协调器尚不存在。

- [x] **Step 3: 实现实体与服务器刷新入口**

用 `ModList.isLoaded("animal_feeding_trough")` 保护生产入口；`installIfEligible` 检查当前规则并扫描 `GoalSelector#getAvailableGoals` 去重；`refreshLoadedEntities` 只在生命周期边界遍历 `server.getAllLevels()` 的已加载实体。

- [x] **Step 4: 接入实体加入和规则发布**

`EntityLifecycleHandler.onEntityJoin` 在服务端恢复状态后尝试安装；`WorldBreedingService.reload/upsert/setEnabled` 只有成功发布新 Snapshot 后才刷新已加载实体。删除/禁用依赖动态查询休眠，不需要破坏性移除目标。

- [x] **Step 5: 运行定向测试并确认 GREEN**

Run: `./gradlew.bat test --tests com.fishbreedingmanager.compat.feedingtrough.AnimalFeedingTroughCompatibilityTest`

Expected: PASS。

### Task 5: 回归、文档与交接

**Files:**
- Create: `docs/testing/Animal_Feeding_Trough_Compatibility_Acceptance.md`
- Modify: `.agent/HANDOFF.md`

- [x] **Step 1: 运行全量测试**

Run: `./gradlew.bat test`

Expected: 全部测试通过，无 failure/error/skipped。

- [x] **Step 2: 运行 Javadoc 与构建**

Run: `./gradlew.bat javadoc --rerun-tasks`

Expected: BUILD SUCCESSFUL，无 Javadoc warning。

Run: `./gradlew.bat build`

Expected: BUILD SUCCESSFUL。

- [x] **Step 3: 写自动化证据与人工矩阵**

验收文档记录上游版本/依赖边界、自动化覆盖、当前本地目录缺少 Animal Feeding Trough 与 Architectury Jar 的事实，以及用户稍后统一执行的客户端测试步骤。

- [x] **Step 4: 更新交接文件**

整理 `.agent/HANDOFF.md` 的当前状态、修改文件、测试数量、已知风险和下一步；明确真实喂食槽运行验证尚待用户提供 Jar 并人工执行。
