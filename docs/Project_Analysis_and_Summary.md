# Fish Breeding Manager — 项目分析与开发总结

> 用途：本项目当前实现、架构决策与后续边界的通览总结，供开发接力和上下文恢复使用。
> 目标：Minecraft 1.21.1 / NeoForge 21.1.x / Java 21 的鱼类繁殖管理模组。
> 最近更新：2026-08-12，P0 B-lite 自动化与交互式客户端验收全部通过。
>
> 配套需求文档：[`Fish_Breeding_Manager_Requirements.md`](./Fish_Breeding_Manager_Requirements.md)

---

## 0. 一句话定位

**Fish Breeding Manager (FBM)** 是一个面向 MC 1.21.1 NeoForge 的**世界级、数据驱动、支持热重载**的鱼类繁殖管理框架，用于为原版及第三方 Mod 实体**动态增加并配置**类似原版动物的繁殖行为，且**不修改**第三方实体 Java 类。

---

## 1. 当前项目状态

### 1.1 工程现状

- 项目根目录：`E:/JavaCodes/FishBreedingManager/MDK-1.21.1-ModDevGradle/`。
- 示例 Mod 已完全替换为 `com.fishbreedingmanager` 业务代码，Mod ID 为 `fishbreedingmanager`。
- P0 B-lite 已实现：四种默认原版鱼规则、喂食 Love、同类型匹配、后代生成、父母冷却、固定 50% 幼体、按存档保存、
  热更新、实体加载恢复、客户端追踪补发和严格管理员命令。
- 配置存于主世界 SavedData，同一存档的所有维度共享一套规则；不同存档互相隔离。
- 所有管理命令在 `/fbm` 根节点严格要求权限等级 2；单人世界未开启作弊时同样不能执行。
- 已建立 NeoForge JUnit 环境，共 38 个测试；`test`、`build`、`javadoc` 均通过。
- 交互式 cod 客户端 11 项验收全部通过，真实结果记录在
  [`testing/P0_Cod_Acceptance.md`](./testing/P0_Cod_Acceptance.md)。
- 第三方鱼兼容验收、Variant/Adapter、管理 GUI 和实体预览仍未实现。

### 1.2 当前核心文件结构

```
MDK-1.21.1-ModDevGradle/
├── build.gradle                          # ModDevGradle 构建脚本
├── gradle.properties                     # 版本与 mod 元数据
├── settings.gradle
├── gradle/wrapper/                       # Gradle 9.6.1 wrapper
├── .github/workflows/build.yml           # CI: JDK21 + ./gradlew build
├── README.md                             # 一句话描述
├── docs/
│   ├── Fish_Breeding_Manager_Requirements.md   # ★ 需求文档（权威）
│   ├── Project_Analysis_and_Summary.md         # ★ 当前状态与架构总结
│   └── testing/P0_Cod_Acceptance.md            # ★ 交互式验收记录
└── src/main/
    ├── java/com/fishbreedingmanager/
    │   ├── breeding/                    # Rule、State、Snapshot、服务、索引与生成器
    │   ├── persistence/                 # 按存档共享的 SavedData
    │   ├── event/                       # 喂食和实体生命周期
    │   ├── network/ 与 client/          # 幼体视觉状态同步和渲染
    │   └── command/                     # 权限等级 2 的管理员命令
    ├── resources/assets/fishbreedingmanager/lang/  # 中英文命令反馈
    └── templates/META-INF/neoforge.mods.toml
```

### 1.3 当前开发规范

- 新增或修改的公共类型、公共 API 与关键私有流程必须包含详细中文 Javadoc。
- 文档注释允许并鼓励使用 IDEA/标准 Javadoc 语法：`<p>`、`<ul>`、`{@link}`、`{@code}`、`@param`、`@return`。
- 功能变更遵循失败测试 → 最小实现 → 全量回归；生成失败、规则校验失败等路径必须有明确结果类型。
- 规则属于世界，运行计时状态属于实体；禁止把规则缓存到 Attachment。

---

## 2. 技术基线与版本（已锁定）

| 项 | 值 | 来源 |
|---|---|---|
| Minecraft | 1.21.1 | `gradle.properties: minecraft_version` |
| NeoForge | 21.1.244 | `gradle.properties: neo_version` |
| Java | 21（toolchain） | `build.gradle` |
| Gradle | 9.6.1（wrapper） | `gradle-wrapper.properties` |
| ModDevGradle 插件 | 2.0.143 | `build.gradle` |
| Parchment mappings | 1.21.1 / 2024.11.17 | `gradle.properties` |
| 多版本/多加载器 | **不做**（仅 1.21.1 单版本 NeoForge） | 需求 §2.1/§56 |

### ⚠️ 版本时效性关键提醒（重要）

- **NeoForge 官方文档站 `docs.neoforged.net` 默认显示最新版（当前 26.1 / 对应 MC 1.21.11）**，其中部分 API 与 1.21.1 **不同**：
  - 新版用 `Identifier.fromNamespaceAndPath(...)`；**1.21.1 用 `ResourceLocation.fromNamespaceAndPath(ns, path)`**（或旧的 `new ResourceLocation(ns, path)` 已废弃但仍可用）。
  - 新版 SavedData 用 `SavedDataType` + `Identifier`；**1.21.1 用 `SavedData.Factory<>` + 字符串文件名**。
  - 新版 Data Attachment 有自动 `AttachmentSyncHandler`；**1.21.1 客户端同步需自己发包**。
  - 新版 Networking 用 `RegisterClientPayloadHandlersEvent` + `PacketDistributor` 静态方法；**1.21.1 用 `DirectionalPayloadHandler` + `RegisterPayloadHandlersEvent`**。
- **查阅 1.21.1 文档时必须切换版本到 "1.21 - 1.21.1"**，URL 前缀为：`https://docs.neoforged.net/docs/1.21.1/...`
- 当文档与实际 `21.1.244` 源码冲突时，**以源码为准**。可从 NeoForge GitHub `1.21.x` 分支拉取源码核对：
  `https://raw.githubusercontent.com/neoforged/NeoForge/1.21.x/src/main/java/net/neoforged/neoforge/...`
- **不要照搬** Forge 1.20.x 旧教程、`net.minecraftforge.*` 旧 API、旧 Capability 教程、ForgeGradle 工程结构。

---

## 3. 已核对的 NeoForge 1.21.1 关键 API（开发可直接使用）

> 以下 API 均已通过官方文档（1.21.1 版）和/或 NeoForge `1.21.x` 分支源码核对，可直接采信。

### 3.1 事件系统

事件总线两个：`NeoForge.EVENT_BUS`（游戏总线，运行期事件）和 mod 构造器传入的 `IEventBus modBus`（mod 总线，启动期事件）。

注册方式：`@EventBusSubscriber(modid=..., value=Dist.CLIENT)` 自动注册静态方法，或 `NeoForge.EVENT_BUS.addListener(Handler::method)`，或 `@SubscribeEvent`。

**核心事件及其包路径（1.21.1 已核对）：**

| 事件 | 完整类名 | 用途 | 关键方法 |
|---|---|---|---|
| 玩家右键实体 | `net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract` | **喂食触发** | `getTarget()`(目标实体), `getEntity()`(玩家), `getHand()`, `getItemStack()`, `getLevel()`, `setCancellationResult(SUCCESS)`+`setCanceled(true)` |
| 右键实体（带局部坐标） | `...PlayerInteractEvent.EntityInteractSpecific` | 精细化交互（FBM 一般用上面的） | `getLocalPos()`, `getTarget()` |
| 实体加入世界 | `net.neoforged.neoforge.event.entity.EntityJoinLevelEvent` | 跟踪新实体/幼体出生 | `getEntity()`, `getLevel()`, `loadedFromDisk()`，**可取消**（阻止加入） |
| 关卡 Tick | `net.neoforged.neoforge.event.tick.LevelTickEvent.Pre` / `.Post` | **BreedingController 主循环**（每个 Level 每 tick） | `getLevel()`, `hasTime()`，客户端+服务端都触发 |
| 服务器 Tick | `net.neoforged.neoforge.event.tick.ServerTickEvent.Pre` / `.Post` | 全局服务器级定时任务 | `getServer()`, `hasTime()`，仅服务端 |
| 注册命令 | `net.neoforged.neoforge.event.RegisterCommandsEvent` | 注册 `/fbm` 命令 | `getDispatcher()` (Brigadier `CommandDispatcher<CommandSourceStack>`) |
| 服务器启动 | `net.neoforged.neoforge.event.server.ServerStartingEvent` | 初始化世界数据 | `getServer()` |

> ⚠️ 注意包路径：`PlayerInteractEvent` 在 `...event.entity.player`（有 `entity.`），不是 `...event.player`。
> ⚠️ Tick 事件是 NeoForge 重构后的新 API（`event.tick` 包，`Pre`/`Post` 内部类），**不是**旧的 `TickEvent.ServerTickEvent`/`TickEvent.LevelTickEvent`。

### 3.2 Data Attachment（实体运行时状态 — BreedingState）

文档（1.21.1 版）已核对。用法：

```java
// 注册
private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
    DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);

// 用 Codec 序列化（持久化到实体 NBT）
public static final Supplier<AttachmentType<BreedingState>> BREEDING_STATE =
    ATTACHMENT_TYPES.register("breeding_state",
        () -> AttachmentType.builder(() -> new BreedingState())
            .serialize(BreedingState.CODEC)   // Codec<BreedingState>
            .copyOnDeath(false)               // 鱼死亡不需要保留
            .build());

// mod 构造器中：ATTACHMENT_TYPES.register(modBus);

// 使用（实体上）
BreedingState state = entity.getData(BREEDING_STATE);   // 不存在则创建默认
entity.setData(BREEDING_STATE, newState);               // 自动 setUnsaved
if (entity.hasData(BREEDING_STATE)) { ... }
```

**1.21.1 注意**：实体 Attachment 的**客户端同步不会自动进行**，需自己用 Networking payload 同步（FBM 大部分状态是服务端权威，客户端只需同步渲染用的小状态，如 juvenile 缩放）。序列化可选三种：`INBTSerializable`（`AttachmentType.serializable(supplier)`）、codec（`.serialize(Codec)`）、或 `IAttachmentSerializer`。FBM 推荐用 **Codec**（与 SavedData 一致，便于统一）。

### 3.3 SavedData（世界级规则持久化 — WorldBreedingData）

文档（1.21.1 版）已核对。用法：

```java
public class WorldBreedingData extends SavedData {
    // 存放：rules Map<ResourceLocation, BreedingRule> + importedEntities Set<ResourceLocation>

    public static WorldBreedingData create() { return new WorldBreedingData(); }

    public static WorldBreedingData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldBreedingData data = create();
        // 用 Codec 解码 tag
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        // 用 Codec 编码写入 tag
        return tag;
    }

    public void mutate(...) { /* 改数据后 */ this.setDirty(); }

    // 获取当前世界的实例
    public static WorldBreedingData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(WorldBreedingData::create, WorldBreedingData::load),
            "fbm_breeding_rules");   // 文件名，不能含 / 或 \
    }
}
```

**1.21.1 注意**：
- `computeIfAbsent` 第二参数是**文件名字符串**（存到 `<world>/data/<name>.dat`），不是 `ResourceLocation`。
- 跨维度共享数据应挂到 Overworld（`server.overworld().getDataStorage()`）。
- `setDirty()` 必须在修改后调用，否则不写盘。
- FBM 是**每个存档独立配置**（需求 §23），当前确认语义为同一存档跨维度共享，因此统一挂到
  `server.overworld().getDataStorage()`；不能为每个维度分别保存，也不能使用全局 `config` 文件。

### 3.4 Networking（P0 幼体同步与未来 GUI 参考）

> 当前 P0 只实现服务端到客户端的 `JuvenileStatePayload`。下列双向 `UpdateRulePayload` 是未来 GUI 的 API 参考，
> 尚未存在于源码，届时必须复用 `WorldBreedingService` 和权限等级 2 校验。

文档（1.21.1 版）已核对。用法：

```java
// Payload 定义（record + CustomPacketPayload）
public record UpdateRulePayload(String entityId, /*...规则字段...*/) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpdateRulePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "update_rule"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateRulePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, UpdateRulePayload::entityId,
            /* ...每个字段: codec, getter... */,
            UpdateRulePayload::new);
    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}

// 注册（mod 总线）
@SubscribeEvent
static void register(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar reg = event.registrar("1");
    reg.playBidirectional(UpdateRulePayload.TYPE, UpdateRulePayload.STREAM_CODEC,
        new DirectionalPayloadHandler<>(ClientHandler::handle, ServerHandler::handle));
}

// 发送
ClientPacketDistributor.sendToServer(new UpdateRulePayload(...));     // 客户端→服务端
PacketDistributor.sendToPlayer(serverPlayer, new SyncRulesPayload(...)); // 服务端→指定客户端
PacketDistributor.sendToAllPlayers(...);
```

**1.21.1 注意**：
- 用 `ResourceLocation.fromNamespaceAndPath(ns, path)`，不是 `Identifier`。
- 双向用 `playBidirectional` + `DirectionalPayloadHandler(clientHandler, serverHandler)`；单向用 `playToClient` / `playToServer`。
- 客户端→服务端 payload < 32 KiB；服务端→客户端 < 1 MiB。
- 默认在主线程执行；重计算用 `.executesOn(HandlerThread.NETWORK)`。
- 客户端 payload handler 在 1.21.1 用 `DirectionalPayloadHandler`，**不是**新版的 `RegisterClientPayloadHandlersEvent`。

### 3.5 喂食/繁殖相关 vanilla API（P0 已验证）

> 这些是 vanilla MC 1.21.1（Mojang mappings + Parchment）的 API，FBM 需复用以接近原版表现。

- **进入 Love 状态**：vanilla `Animal#setInLove(Player)` / `Mob#setInLoveTime(int)` / `isInLove()`。但 FBM 目标实体**可能不是 Animal**（需求 §10），所以 FBM **用自己的 BreedingState.inLove**，不依赖 vanilla love 系统。
- **爱心粒子**：`level.broadcastEntityEvent(entity, (byte)18)` 触发客户端爱心粒子（vanilla Animal 用法），或 `ServerLevel#sendParticles(ParticleTypes.HEART, ...)`。
- **消耗手持物品**：`ItemStack#shrink(1)` 或 `Player.getUseItem().shrink(...)`，参考 `Animal#usePlayerItem`。
- **配偶搜索**：`ActiveLoveIndex` 按 Level 保存 Love 实体 UUID，控制器只在索引快照中按 EntityType、距离和状态过滤，禁止扫描全世界实体。
- **寻路靠近**：`Mob#getNavigation().moveTo(Entity, double speed)`。非 Mob 实体无 Navigation → 降级为"仅生成后代不主动寻路"（需求 §42 Partial 兼容等级）。
- **生成后代**：`EntityType.create(Level)` 创建同类型实例，设置位置，`level.addFreshEntity(child)`。或 `entityType.create(level, CompoundTag, Consumer, BlockPos, Rotation, boolean, MobSpawnType)`。
- **繁殖冷却**：vanilla `Animal#setAge(growthTime)` 同时承担冷却与成长；FBM 自己用 `BreedingState.cooldownUntil`（gameTime tick）。
- **繁殖物品抽象**：`BreedingRule` 保存多个物品 ID 与物品标签 ID，运行时用 `ItemStack#is(Item)` / `ItemStack#is(TagKey)` 匹配，语义等价于 P0 所需 Ingredient 子集且 NBT 格式稳定。
- **Variant 继承**：vanilla `TropicalFish` 用 `SynchedEntityData` 存 variant（`TropicalFish.DATA_ID_TYPE_VARIANT` 等）。FBM v1 对**无法通用识别**的第三方 variant **只创建默认个体**（需求 §12.1），不反射/不 ASM。原版热带鱼可后续用 `BreedingAdapter` 专门适配。

### 3.6 管理命令（P0 已实现）

```java
@SubscribeEvent
public static void onRegisterCommands(RegisterCommandsEvent event) {
    CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
    dispatcher.register(Commands.literal("fbm")
        .requires(source -> source.hasPermission(2))
        .then(Commands.literal("reload") /* ... */)
        .then(Commands.literal("rule")
            /* list/show/set/enable/disable/remove */));
}
```

权限放在根节点，所有子命令严格要求等级 2；未开启作弊的单人世界也不例外。修改命令统一调用
`WorldBreedingService`，先校验完整候选集合，再同时提交 SavedData 与运行时 Snapshot。

### 3.7 实体身份与注册表

- **实体主键**：`ResourceLocation`（Registry ID），通过 `BuiltInRegistries.ENTITY_TYPE.getKey(entityType)` / `EntityType.getKey()` 获取，`BuiltInRegistries.ENTITY_TYPE.get(rl)` 反查。
- 遍历注册表：`BuiltInRegistries.ENTITY_TYPE.stream()` / `forEach(...)`。
- **不要**用 Java 类名/本地化名/UUID 作主键（需求 §17）。

---

## 4. 核心架构映射（需求 → 实现）

### 4.1 三层分离（需求 §31，P0 架构红线）

```
┌─────────────────────────────────────────────────────────┐
│  Rule 层（属于世界）                                       │
│  WorldBreedingData (SavedData) ──持久化──> <world>/data/  │
│         │ load/parse/validate                            │
│         ▼                                                │
│  BreedingRuleManager ──持有──> BreedingRuleSnapshot      │
│  (volatile 不可变快照, Atomic Swap)                       │
└─────────────────────────────────────────────────────────┘
                          │  find(entityType) O(1) 动态查询
                          ▼
┌─────────────────────────────────────────────────────────┐
│  Runtime Engine 层（属于服务端，集中式）                    │
│  BreedingController (LevelTickEvent.Pre 驱动)            │
│    ├─ ActiveLoveIndex → 配对 → moveTo                     │
│    └─ ChildSpawner → 成功后提交冷却与 Love 清理             │
│  EntityInteractionHandler / EntityLifecycleHandler       │
│    └─ 喂食 → 写 BreedingState.inLove                     │
└─────────────────────────────────────────────────────────┘
                          │  getData/setData
                          ▼
┌─────────────────────────────────────────────────────────┐
│  State 层（属于单个 Entity）                               │
│  BreedingState (Data Attachment, Codec 序列化)            │
│   持久化: { inLove, loveUntil, cooldownUntil,             │
│             juvenile, adultAt }                          │
│   仅内存: { mate:UUID }，实体重载后重新匹配                 │
└─────────────────────────────────────────────────────────┘
```

### 4.2 热重载 = Atomic Swap（需求 §25-§34, §38-§39）

- 规则解析为**不可变 `BreedingRuleSnapshot`**（`Map<ResourceLocation, BreedingRule>` + `Set<ResourceLocation> importedEntities`）。
- `BreedingRuleManager` 持 `private volatile BreedingRuleSnapshot snapshot;`。
- Reload 流程：读 WorldBreedingData → 完整解析 → 完整校验 → 成功才 `snapshot = newSnapshot`（原子替换）；失败保留旧快照。
- **所有行为每次发生时查当前 snapshot**（`ruleManager.find(entity.getType())`），**绝不**把规则缓存进实体。
- **计时器不追溯**（需求 §38-§39）：`cooldownUntil`/`adultAt` 按出生/繁殖时计算，规则热重载后**已存在的计时不变**，新事件采用新规则。这显著降低复杂度。
- 当前入口：`/fbm reload` 与 `/fbm rule set|enable|disable|remove`；未来 GUI Save 必须复用同一服务层。
- **禁止** ASM/Mixin/字节码永久注入作为主路线（需求 §29），因为 Class 加载后难撤销、热重载困难、兼容风险高。

### 4.3 繁殖行为流程（需求 §4, §35-§36）

```
玩家右键喂食鱼A (PlayerInteractEvent.EntityInteract)
  → rule = find(A.type); rule.testFood(heldItem)?
  → stateA.canBreed()? (未冷却、非幼体)
  → stateA.inLove=true; loveUntil=now+loveDuration; 消耗物品; 爱心粒子
玩家右键喂食鱼B (同上) → stateB.inLove=true

BreedingController (每 N tick, 仅遍历 inLove 实体):
  → 找到同 EntityType + 同 inLove + 可繁殖 的配偶
  → stateA.mate=B.uuid; stateB.mate=A.uuid
  → A.navigation.moveTo(B); B.navigation.moveTo(A)
  → 距离 ≤ 阈值:
      → 生成 child = A.type.create(level)，并尝试 addFreshEntity(child)
      → 创建/加入失败：只解除 mate，保留 Love 与空闲冷却以便重试
      → 加入成功后：
      → child BreedingState: juvenile=true; adultAt=now+rule.growthTime
      → stateA.cooldownUntil=now+rule.cooldown; stateB 同
      → stateA.inLove=false; stateB.inLove=false
      → 爱心粒子
```

### 4.4 幼体机制（需求 §10）

- P0 统一由 FBM 自维护 `juvenile` + `adultAt`，不依赖目标实体是否实现原生年龄系统。
- 客户端视觉缩放：成年前固定为 50%，到达 `adultAt` 瞬间恢复 100%，不进行平滑插值，也不修改逻辑碰撞箱。
- `JuvenileStatePayload` 只同步实体 UUID 与绝对成年时刻；出生时广播，玩家后来开始追踪时补发，退出连接时清缓存。

### 4.5 实体发现（需求 §13-§16, §46）

```
CandidateEntityFilter (多策略组合)
├─ VanillaFishProvider   : minecraft:cod/salmon/tropical_fish/pufferfish (白名单)
├─ KeywordFilter         : fish/salmon/cod/bass/trout/shark/carp/tuna/puffer/eel... (匹配 Registry ID + 本地化名 + namespace)
├─ MobCategoryFilter     : MobCategory.WATER_CREATURE 等辅助
└─ ImportedEntityProvider: 用户手动导入并持久化的实体
→ 合并去重 → 候选列表
高级搜索: 搜完整 ENTITY_TYPE registry → 用户点[导入] → 写入 WorldBreedingData.importedEntities (持久化)
```

### 4.6 权限与网络（需求 §21-§22）

- 单机与专用服务端统一要求权限等级 2；未开启作弊的单人世界玩家无权查看或修改 `/fbm` 命令树。
- **服务端是规则唯一权威**。当前命令直接调用事务服务；未来客户端 GUI Payload 必须在服务端再次校验权限、参数和 Registry，
  并复用相同的 SavedData + Snapshot 提交路径。
- 不能只靠客户端隐藏按钮（需求 §21 安全要求）。

### 4.7 当前包结构与后续预留

```
fishbreedingmanager/
├── FishBreedingManager.java          # @Mod 组成根，仅负责注册
├── breeding/
│   ├── BreedingRule.java             # 不可变规则
│   ├── BreedingRuleSnapshot.java     # 不可变快照 record
│   ├── BreedingRuleManager.java      # volatile snapshot + 动态查询
│   ├── WorldBreedingService.java     # 校验后事务提交 SavedData + Snapshot
│   ├── BreedingController.java       # LevelTickEvent 驱动配对/寻路/繁殖
│   ├── BreedingState.java            # Data Attachment 值对象 + Codec
│   ├── ActiveLoveIndex.java          # 按 Level 隔离的活动 UUID 索引
│   ├── feed/                         # 统一喂食与爱心粒子
│   └── spawn/                        # 后代创建与结构化结果
├── attachment/ModAttachments.java    # DeferredRegister<AttachmentType>
├── server/ServerLifecycleHandler.java # 规则/发现/清理生命周期
├── event/
│   ├── EntityInteractionHandler.java # PlayerInteractEvent.EntityInteract
│   └── EntityLifecycleHandler.java   # Join/Leave + StartTracking
├── discovery/                        # Mod/Registry 扫描与发现快照
├── compat/
│   ├── CompatibilityCoordinator.java # 核心面向的统一兼容入口
│   └── feedingtrough/                # Animal Feeding Trough 适配
├── persistence/WorldBreedingData.java # 主世界 SavedData，跨维度共享
├── command/FBMCommands.java          # /fbm reload + rule 管理命令
├── network/
│   ├── ModNetworking.java            # RegisterPayloadHandlersEvent
│   └── JuvenileStatePayload.java     # 服务端到客户端幼体时刻
├── client/
│   ├── ClientJuvenileSync.java
│   └── JuvenileRenderHandler.java
└── 未来预留：client/screen、network/editor、compat/adapter
```

详细依赖方向和新代码放置规则见 [`ARCHITECTURE.md`](./ARCHITECTURE.md)。

---

## 5. 开发阶段与验收（需求 §49-§54）

### 5.1 P0 B-lite — 自动化与交互验收全部通过

已完成：WorldBreedingData → Runtime Snapshot → 事务校验与原子替换 → BreedingState Attachment → 喂食 → Love →
配偶搜索 → 寻路 → 生成后代 → 幼体 → 冷却 → 生命周期恢复 → `/fbm rule` 管理命令。

自动化层已有 80 个 JUnit 测试并通过完整构建；实际客户端中的爱心、寻路、模型尺寸、冷却、热更新、区块重载和
权限可见性均已按 [`testing/P0_Cod_Acceptance.md`](./testing/P0_Cod_Acceptance.md) 完成人工观察，P0 最终验收为 PASS。

验收确认的运行边界：配偶搜索半径固定为 8 格，9 格外不会建立配对；区块恢复只适用于仍存在的实体，未命名鳕鱼被
原版自然消失机制删除后无法恢复，因此持久化测试需要命名目标实体或确保只发生卸载。

**第一阶段 PoC 验收（需求 §52，必须全打通）**：用 `minecraft:cod`，规则 `kelp / cooldown=600t / growth=1200t`：
1. 原版 cod 默认不可繁殖；2. 装 FBM 后加载 cod 规则；
3. kelp 喂两条 cod → 4. 双方 Love + 爱心；5. 主动靠近；
6. 生成新 cod；7. 父母冷却；8. 幼体明显缩小；9. growthTime 后成年；
10. **不退出世界**改 `kelp→seagrass` + `/fbm reload` → 现存 cod 立即只接受 seagrass；
11. 删除 cod 规则 + reload → 现存 cod 立即失去 FBM 繁殖行为。

**第二阶段第三方验收（需求 §53）**：选一个原无繁殖的第三方鱼，重复以上能力 + 手动导入 + 持久化 + 热重载。

### 5.2 P1 — 第三方实体与 GUI

第三方鱼兼容验收、Entity Browser GUI、默认筛选、高级 Registry 搜索、导入持久化、Rule Editor、GUI Network Sync
和 GUI Save→热更新。命令权限已在 P0 以根节点权限等级 2 实现，GUI 将复用同一服务端权限语义。

### 5.3 P2 — 增强

3D 实体预览、更完善 Variant 继承、Adapter API、第三方 Preset、Tag/Namespace 批量、复杂繁殖条件。

### 5.4 明确不做（需求 §56）

跨 EntityType 杂交、基因/遗传、天气/Biome/光照/水深条件、群体密度、多胎/随机成功率、文件监听自动 reload、为所有第三方私有 variant 写兼容、多版本、多加载器、Coremod/ASM 主路线。

---

## 6. 关键约束与易错点清单（开发时反复核对）

1. **每存档独立且跨维度共享配置**：统一挂主世界 `getDataStorage()`，不要按维度拆分，也不要全局共享 `config/fbm-rules.json`。
2. **Rule vs State 分离**：规则属世界（运行时动态查询），状态属实体（不缓存规则副本）——否则热重载不影响现存实体（需求 §31, §34）。
3. **Atomic Swap**：规则解析成不可变 snapshot，`volatile` 引用，整体替换；配置错误保留旧 snapshot（需求 §32-§33）。
4. **计时器不追溯**：`cooldownUntil`/`adultAt` 不因热重载重算（需求 §38-§39）。
5. **服务端权威**：所有修改服务端二次校验权限+参数+Registry（需求 §21-§22）。
6. **非侵入**：不改第三方 Java 类、不要求实现接口、不启动期永久注入；用 Event + Data Attachment + Runtime Controller（需求 §43）。
7. **主键用 ResourceLocation**：不用类名/本地化名/UUID（需求 §17）。
8. **繁殖物品用 Ingredient 抽象**：支持多 Item + Tag，不要单 Item 字段（需求 §7）。
9. **后代 EntityType 必须与父母相同**：不杂交（需求 §5）。
10. **Variant 不可识别时只创建默认个体**：不反射不 ASM（需求 §12.1）。
11. **性能**：Controller 只跟踪 inLove 实体、N tick 一次配偶搜索、合理半径、O(1) Map 查找，禁止每 tick 扫全实体×全规则（需求 §37）。
12. **包路径坑**：`PlayerInteractEvent` 在 `...event.entity.player`（有 `entity.`）；Tick 事件在 `...event.tick`（新 API，`Pre`/`Post` 内部类）。
13. **1.21.1 API 版本坑**：`ResourceLocation`（非 `Identifier`）、`SavedData.Factory<>`（非 `SavedDataType`）、Attachment 客户端同步需手动发包、`DirectionalPayloadHandler`（非 `RegisterClientPayloadHandlersEvent`）。
14. **不要先做 GUI**：先证明 cod PoC 全打通（含热重载），再第三方鱼，最后 GUI（需求 §50）。
15. **每完成一阶段都保证热重载不被破坏**（需求 §58）。

---

## 7. 参考资料来源（已核对）

- **需求文档**：`docs/Fish_Breeding_Manager_Requirements.md`（本项目权威需求，60 节）
- **NeoForge 1.21.1 官方文档**（切换到 "1.21 - 1.21.1" 版本）：
  - Data Attachments: https://docs.neoforged.net/docs/1.21.1/datastorage/attachments
  - Saved Data: https://docs.neoforged.net/docs/1.21.1/datastorage/saveddata
  - Registering Payloads: https://docs.neoforged.net/docs/1.21.1/networking/payload
  - Events: https://docs.neoforged.net/docs/1.21.1/concepts/events
- **NeoForge 源码（1.21.x 分支，核对确切 API）**：
  - PlayerInteractEvent: https://github.com/neoforged/NeoForge/blob/1.21.x/src/main/java/net/neoforged/neoforge/event/entity/player/PlayerInteractEvent.java
  - EntityJoinLevelEvent: .../event/entity/EntityJoinLevelEvent.java
  - LevelTickEvent / ServerTickEvent: .../event/tick/
  - RegisterCommandsEvent: .../event/RegisterCommandsEvent.java
- **工程配置**：`gradle.properties`（neo_version=21.1.244, mc=1.21.1, java=21）、`build.gradle`（ModDevGradle 2.0.143）

---

## 8. 下一步行动建议

1. 选择一个原本不可繁殖的第三方鱼执行兼容验收，确认默认后代和无通用导航时的降级行为。
2. 实现第三方实体发现、高级 Registry 搜索、手动导入与按存档持久化。
3. 在当前事务服务和严格权限边界上继续实现 Entity Browser 与 Rule Editor GUI。
4. GUI 稳定后再设计 Variant 继承和 BreedingAdapter API，避免提前绑定第三方私有实现。

> 开发原则重申（需求 §58）：优先查 1.21.1 官方文档/API → 不确定查 NeoForged 源码 → 不套用旧 Forge 教程 → 不提前 ASM → 不过度抽象 → 先证明 cod PoC → 每阶段保证热重载不破坏。
