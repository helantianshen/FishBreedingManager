# Fish Breeding Manager P0 B-lite 稳定化设计

## 1. 目标

在保留现有 NeoForge 1.21.1 非侵入式繁殖架构的前提下，完成一次有边界的结构整理，修复已经确认的 P0 缺陷，并让原版 `minecraft:cod` 的完整繁殖与热更新流程具备可操作、可测试、可验收的基础。

本设计不进入 GUI 阶段，也不提前建设完整 Adapter API、外部 JSON 配置系统或多版本兼容层。

## 2. 已确认的产品语义

1. FBM 配置按 Minecraft 存档隔离；同一存档中的所有维度共享一份规则。
2. 新存档默认启用 `minecraft:cod`、`minecraft:salmon`、`minecraft:tropical_fish`、`minecraft:pufferfish` 的繁殖规则，管理员之后可以修改、禁用或删除。
3. P0 在 GUI 之前提供管理员调试命令，用于新增、修改、查看和删除规则并立即热应用。
4. Love 截止时间持久化。实体重新载入时，如果 Love 尚未过期，则重新加入求偶池；旧 mate 不作为必须恢复的持久关系。
5. 实体存在已启用的 FBM 规则时，FBM 接管匹配食物的繁殖交互；没有 FBM 规则时不干预实体原有行为。
6. FBM 幼体固定显示为成年体的 50%，成长时间到期后瞬间恢复为 100%，不做平滑插值。
7. 后代创建失败时不给父母施加冷却；清除无效配对，双方在剩余 Love 时间内可以重试。
8. 所有规则管理和重载操作严格要求权限等级 2；单机未开启作弊时不放行。

## 3. 采用 B-lite 的理由

现有代码已经具备 SavedData、不可变 Snapshot、Attachment、交互事件和集中式繁殖控制器，不需要推翻重写。但当前规则更新、Love 索引、配偶流程、后代生成和客户端同步边界混杂，继续直接堆叠 GUI 与第三方兼容会放大生命周期和错误处理问题。

B-lite 只提取已经由现有缺陷证明需要独立的职责：

- 世界规则服务与规则校验；
- 活跃 Love 实体索引；
- 后代创建及其成功/失败结果；
- 实体载入、卸载和玩家追踪生命周期处理。

集中式 `BreedingController` 仍保留为流程编排者。SavedData 继续作为具体持久化实现，不建立通用 Repository 接口；第三方 Adapter 等真实兼容差异出现后再设计。

## 4. 目标结构

```text
NeoForge Command / Event / Network
                 │
                 ▼
        WorldBreedingService
        ├── RuleValidator
        ├── WorldBreedingData
        └── BreedingRuleManager / Snapshot
                 │
                 ▼
          BreedingController
        ├── ActiveLoveIndex
        ├── mate validation/search
        └── ChildSpawner
                 │
        ┌────────┴────────┐
        ▼                 ▼
 BreedingState     Juvenile sync/render
 Attachment        lifecycle events
```

### 4.1 `WorldBreedingService`

它是规则修改的唯一应用层入口，命令和未来 GUI/网络请求不得直接修改 `WorldBreedingData`。

公开行为包含：

```java
ReloadResult reload(MinecraftServer server);
RuleUpdateResult upsert(MinecraftServer server, BreedingRule rule);
RuleUpdateResult remove(MinecraftServer server, ResourceLocation entityId);
RuleUpdateResult setEnabled(MinecraftServer server, ResourceLocation entityId, boolean enabled);
List<BreedingRule> list(MinecraftServer server);
```

更新流程必须是：

1. 从当前世界数据复制出候选规则集合；
2. 对整个候选集合执行校验；
3. 校验成功后写入 `WorldBreedingData` 并调用 `setDirty()`；
4. 构造不可变 `BreedingRuleSnapshot`；
5. 原子替换运行时 Snapshot；
6. 返回结构化成功结果。

任一步骤在提交前失败，都保留旧的 SavedData 和旧 Snapshot。

### 4.2 `RuleValidator`

P0 校验范围为：

- Entity ID 必须存在于 `ENTITY_TYPE` Registry；
- 至少配置一个 Item 或 Item Tag；
- Item ID 必须存在于 `ITEM` Registry；
- Tag ID 必须是合法 `ResourceLocation`，且运行时 Item Registry 中存在该 Tag；
- 冷却和成长时间必须大于或等于 0；
- 所有时间必须能够安全存入 `int` tick 字段；
- Snapshot 中的 Map 键必须与 `BreedingRule.entityTypeId()` 一致。

实体是否具备完整 Navigation 和安全生成能力需要实体实例与实机环境，P0 不在静态校验器中虚构判断。管理员可以配置已注册实体；运行时根据实际实例提供 Full 或 Partial 行为，创建失败时返回明确失败结果。

### 4.3 `ActiveLoveIndex`

它取代 `BreedingController` 内部的静态 `WeakHashMap`，只负责按 `ServerLevel` 保存活跃 Love UUID，并提供：

```java
void add(ServerLevel level, UUID entityId);
void remove(ServerLevel level, UUID entityId);
List<UUID> snapshot(ServerLevel level);
void clear(ServerLevel level);
```

所有调用仍发生在服务端主线程。实现无需并发集合；对外只返回副本，允许控制器迭代期间安全增删。

### 4.4 Love 与 mate 生命周期

`BreedingState` 继续持久化 `inLove` 和 `loveUntil`，但 mate 只作为当前加载会话的临时关系：

- 新写出的 Attachment 数据不再序列化 mate；
- 读取旧存档时允许存在可选的旧 mate 字段，但解码后忽略它；
- `EntityJoinLevelEvent` 在服务端结算计时器；Love 有效则加入 `ActiveLoveIndex`；
- 实体卸载或离开 Level 时从索引移除；
- 区块重新加载后，如果 `loveUntil` 尚未到期，实体重新进入求偶池；
- 规则删除或禁用后，控制器清除对应实体的 Love 和 mate；
- 已配对实体每次尝试繁殖前重新验证双方仍存在、类型相同、Love 有效、未处于冷却/幼体状态，并且配对关系仍然互相一致。

世界 `gameTime` 在服务器离线时不推进，因此服务器重启后，未过期 Love 会从保存的剩余游戏时间继续。

### 4.5 `ChildSpawner`

`ChildSpawner` 只负责创建、定位并加入同类型后代，返回结构化结果：

```java
enum ChildSpawnStatus {
    SUCCESS,
    TYPE_CREATION_FAILED,
    ADD_TO_LEVEL_FAILED
}

record ChildSpawnResult(ChildSpawnStatus status, Entity child) {}
```

成功时：

- 后代 `EntityType` 与父母完全相同；
- 在父母中点生成；
- 写入 `juvenile=true` 和固定的 `adultAt=now+growthTimeTicks`；
- 成功加入 Level 后才对父母施加冷却、清除 Love 并发送表现与幼体同步。

失败时：

- 不施加冷却；
- 不消耗新的繁殖物品；
- 清除双方 mate，但保留尚未过期的 Love；
- 记录包含 Entity ID 和失败状态的警告日志；
- 后续控制器周期可以重新配对重试。

### 4.6 幼体同步和显示

服务端 Attachment 是幼体状态的权威来源。客户端只保存渲染所需的 `entityUUID -> adultAt`：

- 后代成功加入世界后，向当前追踪玩家发送幼体包；
- `PlayerEvent.StartTracking` 中，如果目标实体仍是 FBM 幼体，向该玩家补发幼体包；
- 客户端在 `now < adultAt` 时返回固定缩放 `0.5F`；
- 到期后删除客户端缓存并返回 `1.0F`；
- 客户端退出世界时清空缓存，防止跨存档残留；
- Payload 的 `adultAt` 必须来自 `BreedingState.getAdultAt()`，不能使用出生时的 `now`。

本阶段只缩放 `RenderLivingEvent` 支持的 Living Entity，不修改碰撞箱。

## 5. 管理员调试命令

所有子命令在命令树根部统一要求 `source.hasPermission(2)`。

P0 命令设计：

```text
/fbm reload
/fbm rule list
/fbm rule show <entity_id>
/fbm rule set <entity_id> <cooldown_ticks> <growth_ticks> <foods>
/fbm rule enable <entity_id>
/fbm rule disable <entity_id>
/fbm rule remove <entity_id>
```

`foods` 是逗号分隔的贪婪字符串：

```text
minecraft:kelp,minecraft:seagrass,#c:fish_breeding_food
```

- 不带 `#` 的值解析为 Item ID；
- 带 `#` 的值解析为 Item Tag ID；
- 空项、非法 ID、未知 Item/Tag 或负数时间全部拒绝；
- `set`、`enable`、`disable` 与 `remove` 成功后自动持久化并替换 Snapshot，不要求再执行 `reload`；
- `reload` 负责重新校验当前 SavedData 并重建 Snapshot，不承诺监听或读取外部 JSON/NBT 编辑。

P0 不增加导入命令。手动导入属于实体发现和 GUI 阶段；管理员已经可以直接通过 Entity ID 创建规则以验证第三方实体。

## 6. 默认规则

新存档仍创建四条启用规则：

- `minecraft:cod`：`minecraft:kelp`；
- `minecraft:salmon`：`minecraft:kelp`、`minecraft:seagrass`；
- `minecraft:tropical_fish`：`minecraft:seagrass`；
- `minecraft:pufferfish`：`minecraft:kelp`。

现有存档只读取自身 SavedData，不因升级再次补回管理员已经删除的默认规则。

## 7. 错误处理与一致性

- 规则解析或校验错误：命令返回明确错误，旧 SavedData 和 Snapshot 均保持不变；
- `/fbm reload` 失败：旧 Snapshot 继续服务；
- 规则删除：当前 Snapshot 立即失去该规则，活跃索引中的对应实体在下一控制周期清除 Love；
- 配偶卸载或状态失效：清除双方可见的临时 mate，保留有效 Love；
- 后代创建失败：不冷却、不宣称成功；
- 客户端未收到幼体状态：只影响显示，不改变服务端幼体、冷却或繁殖资格；
- 所有玩法修改均在服务端主线程完成。

## 8. 测试与验收

### 8.1 自动单元测试

使用 JUnit 5 覆盖无需完整游戏客户端的逻辑：

- `BreedingState` Love、冷却和幼体边界时间；
- Love 载入时保留截止时间但清除旧 mate；
- 固定幼体缩放在成年 tick 从 `0.5F` 切换为 `1.0F`；
- Food 参数对 Item 与 `#tag` 的解析；
- 负时间、空 Food、未知 Registry ID 的校验失败；
- 候选规则校验失败时不替换当前 Snapshot；
- `ChildSpawnResult` 只有成功状态允许进入父母冷却提交路径；
- Juvenile Payload 使用 `adultAt` 而不是出生时间。

### 8.2 NeoForge GameTest 或受控服务端测试

用 `minecraft:cod` 验证：

1. 两条成年 cod 分别接受 kelp 并进入 Love；
2. 双方配对、寻路并生成同类型后代；
3. 父母只在生成成功后进入冷却；
4. 后代在服务端处于 juvenile，成长到期后可再次繁殖；
5. 用命令把食物改为 seagrass 后，现存 cod 立即拒绝 kelp、接受 seagrass；
6. 删除规则后，现存 cod 立即失去 FBM 行为；
7. 区块卸载再加载后，有效 Love 重新进入求偶池；
8. 新玩家开始追踪已有幼体时收到幼体状态。

客户端模型从 50% 瞬间恢复 100% 需要开发客户端实机观察，因为 GameTest 不验证渲染矩阵。

## 9. 实施边界

本阶段明确不实现：

- GUI 和 3D 实体预览；
- 完整 Registry 浏览、关键词发现和持久化导入流程；
- 外部 JSON 文件与文件监听；
- Variant 继承；
- 通用第三方 `BreedingAdapter` API；
- 跨 EntityType 杂交；
- 平滑幼体成长；
- 单机无作弊管理豁免；
- 对所有非 Living Entity 的客户端缩放支持。

## 10. 中文注释与 Javadoc 规范

本项目开发全程使用详细中文注释。新增或修改代码必须满足以下规范：

- 所有公共类、接口、Record、枚举、公共或受保护的方法与字段必须编写中文 Javadoc；
- 承担核心流程、状态转换、持久化、网络同步或兼容性判断的私有方法，也必须编写中文说明；
- Javadoc 应说明职责、调用时机、线程或逻辑端、Rule/State 所有权、热重载语义、失败行为和重要不变量；
- 参数、返回值和可能的失败结果分别使用 `@param`、`@return` 等标准标签说明；
- 类型、方法和字段引用优先使用 `{@link}`，代码值与命令使用 `{@code}`；
- 多段说明可以使用 `<p>`，结构化内容可以使用 `<ul>`、`<li>` 等 IDEA 支持的 Javadoc HTML 语法；
- 行内注释用于解释关键分支的设计原因和兼容性约束，不得只把 Java 语句逐字翻译成中文；
- 注释必须与真实实现保持一致。行为调整时同步更新注释，禁止保留已经失效的架构描述；
- Java 源文件继续使用 UTF-8 编码，确保中文注释在 Gradle、IDEA 和构建产物中一致可读。

示例：

```java
/**
 * 尝试为两个处于 FBM Love 状态的实体生成同类型后代。
 *
 * <p>本方法只负责实体创建与加入世界，不直接修改父母冷却。调用方必须在
 * {@link ChildSpawnStatus#SUCCESS} 时提交父母状态，以保证生成失败不会错误消耗繁殖机会。
 *
 * @param level 服务端实体所在的 {@link ServerLevel}
 * @param firstParent 第一个亲本，必须与第二个亲本具有相同 EntityType
 * @param secondParent 第二个亲本
 * @param rule 当前运行时快照中的繁殖规则
 * @param now 当前世界 game time
 * @return 包含成功状态和已生成后代的结构化结果
 */
ChildSpawnResult spawn(ServerLevel level, Entity firstParent, Entity secondParent,
                       BreedingRule rule, long now);
```

## 11. 完成标准

只有同时满足以下条件，B-lite P0 才视为完成：

- Gradle 编译和全部自动测试通过；
- 管理员命令能事务式修改、删除并热应用规则；
- 已确认的幼体 `adultAt` 同步错误有回归测试；
- Love 在实体重新载入后按本设计恢复，旧 mate 不被强制恢复；
- 后代创建失败不会错误施加冷却；
- 原版 cod 的需求文档 P0 验收流程通过；
- 尚未完成的第三方实体验收被明确记录为下一阶段，而不是误报为已完成。
