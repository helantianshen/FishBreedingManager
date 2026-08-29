# 第三方鱼类 Mod 自动发现与 Aquaculture 2 兼容设计

## 1. 背景与结论

Fish Breeding Manager（FBM）的 P0 已证明原版鳕鱼可以通过事件、Data Attachment、世界级规则快照和集中式繁殖控制器获得可热更新的繁殖能力。P1 的第一个外部兼容目标选定为：

```text
Mod: Aquaculture 2
Mod ID: aquaculture
本地测试版本: 2.7.21
文件: E:\JavaCodes\FishBreedingManager\常见鱼类mod及前置\[水产业2／水产品2] Aquaculture-1.21.1-2.7.21.jar
Minecraft: 1.21.1
Loader: NeoForge
最低 NeoForge: 21.0.75-beta
额外前置: 无
```

该 Mod 适合作为首个兼容目标，原因如下：

- 本地 Jar 包含 28 个通过 `c:fish` EntityType Tag 声明的鱼类实体；
- 这些鱼统一由 `AquaFishEntity` 实现，继承 `AbstractSchoolingFish`；
- `AquaFishEntity` 没有原生 `Animal` Love、年龄或繁殖接口，能够真实验证 FBM 自维护状态的设计；
- 各鱼种使用独立 `EntityType` 工厂创建，并由工厂保留自己的 `FishType`，适合验证 `EntityType.create(...)` 生成同类型后代；
- 没有必须编译依赖的公开繁殖 API，适合验证 FBM “不要求第三方主动适配”的产品目标。

本兼容采用“扫描已安装 Mod + 自动识别鱼类实体 + 通用契约适配 + 真实 Jar 黑盒验收”，不建立 Aquaculture 专用生产代码分支。

Aquaculture 只是第一份可重复的验收样本，不是自动识别逻辑中的特例。P1 的架构能力必须做到：安装一个遵循常见 Registry/Tag/命名约定的鱼类 Mod 后，FBM 无需更新自身版本或增加该 Mod 的硬编码，即可在下次进入世界时发现其鱼类实体、按来源 Mod 分组展示，并允许管理员直接配置通用繁殖规则。

## 2. 已核对事实

### 2.1 实体集合

本地 `data/c/tags/entity_type/fish.json` 声明以下 28 个实体：

```text
aquaculture:atlantic_cod
aquaculture:blackfish
aquaculture:pacific_halibut
aquaculture:atlantic_halibut
aquaculture:atlantic_herring
aquaculture:pink_salmon
aquaculture:pollock
aquaculture:rainbow_trout
aquaculture:bayad
aquaculture:boulti
aquaculture:capitaine
aquaculture:synodontis
aquaculture:smallmouth_bass
aquaculture:bluegill
aquaculture:brown_trout
aquaculture:carp
aquaculture:catfish
aquaculture:gar
aquaculture:minnow
aquaculture:muskellunge
aquaculture:perch
aquaculture:arapaima
aquaculture:piranha
aquaculture:tambaqui
aquaculture:brown_shrooma
aquaculture:red_shrooma
aquaculture:red_grouper
aquaculture:tuna
```

`aquaculture:jellyfish` 只出现在 `minecraft:aquatic`，不在 `c:fish` 中；三种海龟也不在 `c:fish` 中。因此它们不属于本次默认鱼类候选，但未来仍可通过高级 Registry 搜索手动导入。

### 2.2 实体实现

本地字节码确认：

```java
public class AquaFishEntity extends AbstractSchoolingFish {
    private final FishType fishType;
}
```

`FishRegistry` 为每个鱼 ID 注册独立 `EntityType<AquaFishEntity>`，工厂捕获对应 `FishType` 后创建实例。FishType 包含：

```text
SMALL
MEDIUM
LARGE
LONGNOSE
CATFISH
JELLYFISH
HALIBUT
```

这意味着后代必须继续通过父母自己的 `EntityType` 创建，不能直接调用 `new AquaFishEntity(...)`，也不能按 Java 类选择后代类型。FBM 当前的 `ChildSpawner` 已采用正确的 EntityType 路线。

### 2.3 当前兼容缺口

核心繁殖路径理论上已经适用于 Aquaculture，但目前存在三个尚未验收或实现的 P1 缺口：

1. FBM 尚未扫描已加载 Mod 元数据并把 EntityType Registry 按来源 Mod/Namespace 建立索引；
2. FBM 没有基于公共 Tag、Registry ID、实体翻译键、Namespace 和实体类别的多信号鱼类识别模块；
3. 当前从未在同时加载 Aquaculture 真实 Jar 的客户端中执行繁殖、幼体渲染、热更新和存档恢复验收。

因此第一轮工作不是编写 Aquaculture 专用 Adapter，而是先建立通用 Mod 扫描与鱼类识别管线，再执行真实兼容验收。

## 3. 目标

本 Spec 完成后，实施结果应满足：

1. 服务端完成 Registry 和数据包 Tag 加载后，FBM 自动枚举已加载 Mod 并扫描完整 EntityType Registry；
2. 扫描结果按来源 Mod/Namespace 分组，并保存显示名、实体数量和鱼类候选数量；
3. FBM 通过强弱多信号为实体给出高、中、低置信度，不能只依赖一个 `fish` 关键词；
4. 同时安装 FBM 与 Aquaculture 2 时，FBM 能把 `c:fish` 中的 28 种 Aquaculture 鱼识别为高置信度候选；
5. 符合通用契约的未知鱼类 Mod 无需 FBM 硬编码也能进入候选列表并使用通用繁殖引擎；
6. 候选发现不自动创建或启用世界规则，不擅自改变现有存档玩法；
7. 管理员可通过现有 `/fbm rule set` 为任一 Aquaculture 鱼建立规则；
8. 已有和新生成的 Aquaculture 鱼立即采用当前世界的最新规则；
9. 两个相同 EntityType 的 Aquaculture 鱼可以进入 Love、靠近、生成同类型后代、进入冷却；
10. 后代使用 FBM 幼体机制并在客户端正确缩放，成年时间到达后恢复；
11. 修改食物、禁用或删除规则能热生效；
12. Aquaculture 未安装时，FBM 仍能独立启动、测试和运行；
13. FBM 的发布产物和 POM 不包含、重打包或强制依赖 Aquaculture；
14. 兼容实现不导入 `com.teammetallurgy.aquaculture.*` 生产类型，不依赖其内部字段或方法。

## 4. 非目标

本阶段明确不做：

- 不为 Aquaculture 28 种鱼分别编写硬编码规则；
- 不维护“已知鱼类 Mod ID 白名单”作为主要识别方式；本地 Mod 只用作验收样本；
- 不在新世界自动启用全部 Aquaculture 鱼；
- 不适配 `aquaculture:jellyfish` 或三种海龟的默认发现；
- 不复制第三方私有状态、鱼重或未来可能增加的 Variant；
- 不修改 Aquaculture 源码、Mixin 或注册表；
- 不把 Aquaculture Jar 提交、重分发或打入 FBM Jar；
- 不在本 Spec 中实现完整 GUI、网络编辑和高级 Registry 搜索；
- 不把 Upgrade Aquatic、妖怪归家或 Animal Feeding Trough 混入本轮范围；
- 不为单个兼容目标引入通用 Adapter API；只有黑盒验收证明通用契约不足时，才另行设计 Adapter。

## 5. 兼容策略

### 5.1 生产代码零硬依赖

生产代码只能使用 Minecraft/NeoForge 的公共概念：

```text
ResourceLocation
EntityType
TagKey<EntityType<?>>
Entity / Mob / PathfinderMob
ItemStack
BuiltInRegistries.ENTITY_TYPE
```

禁止：

```java
import com.teammetallurgy.aquaculture.entity.AquaFishEntity;
import com.teammetallurgy.aquaculture.init.FishRegistry;
```

这样 Aquaculture 缺失、升级或内部包名变化时，不会导致 FBM 类加载失败。

### 5.2 扫描已加载 Mod 与完整 Registry

扫描在逻辑服务端执行，输入为：

```text
NeoForge ModList 中的已加载 Mod 元数据
BuiltInRegistries.ENTITY_TYPE 的完整注册项
当前已加载的 EntityType Tags
当前存档 importedEntities
```

扫描输出为不可变 `DiscoverySnapshot`：

```text
DiscoverySnapshot
├── detectedMods: mod/namespace -> DetectedFishMod
├── candidates: entity id -> CandidateEntity
└── unavailableImports: imported id set
```

实体 ID 的 Namespace 通常与 Mod ID 相同，精确匹配时使用 ModList 的显示名和版本元数据；无法匹配时仍按 Namespace 分组，显示名回退为 Namespace。不能因为 Namespace 无对应 ModContainer 就丢弃实体。

扫描只在以下检查点重建，不允许每 Tick 扫描 Registry：

- 服务端启动且 Registry/Tag 已可读取时；
- 数据包或 Tag 重载成功后；
- 当前存档的手动导入集合事务提交后。

完整候选构建成功后原子替换旧 `DiscoverySnapshot`；构建异常时保留旧快照并记录诊断信息，采用与规则热重载相同的安全发布原则。

### 5.3 多信号鱼类识别

P1 候选发现至少识别以下公共 Tag：

```text
c:fish
c:fishes
```

原因：本地 Aquaculture 2 使用单数 `c:fish`，本地 Upgrade Aquatic 和 Blueprint 使用复数 `c:fishes`。两者都应视为高置信度鱼类信号。

识别信号分级：

```text
强信号
├── 原版鱼白名单
├── c:fish
├── c:fishes
└── 当前存档 importedEntities（用户明确确认）

弱信号
├── Registry path 关键词
├── 实体翻译键关键词
├── Namespace/Mod 显示名关键词
├── minecraft:aquatic Tag
└── WATER_CREATURE / WATER_AMBIENT 等 MobCategory
```

置信度规则：

```text
HIGH   = 至少一个强信号
MEDIUM = 至少两个相互独立的弱信号
LOW    = 只有一个弱信号或仅存在于完整 Registry
```

独立性按语义分组计算：`ENTITY_KEYWORD`、`SOURCE_KEYWORD`、`AQUATIC_TAG/WATER_CATEGORY` 共三组；`AQUATIC_TAG` 与水生 `MobCategory` 都只说明“它生活在水中”，因此同时命中时仍只算一组。这样水母不会仅凭两个同源水生属性进入默认鱼类候选。

- `HIGH` 和 `MEDIUM` 默认出现在 FBM 管理候选列表，并显示识别理由；
- `LOW` 只出现在高级 Registry 搜索，不自动宣称为鱼；
- 用户手动导入后成为 `HIGH/IMPORTED`，并按存档持久化；
- 所有置信度都只是发现结论，不代表规则已启用。

`minecraft:aquatic` 不能单独作为高置信度鱼类信号，因为它还可能包含水母、海龟、鱿鱼、守卫者和其他水生实体。它只能与关键词、MobCategory 等弱信号组合，或供高级搜索显示。

### 5.4 自动发现不等于自动启用

数据语义必须保持分离：

```text
discovered candidate
    = 当前 Registry 和 Tag 推导出的可管理候选

imported entity
    = 用户通过高级搜索显式导入并按存档持久化的实体

breeding rule
    = 当前存档中已经配置的权威玩法规则
```

Aquaculture 鱼进入候选列表时：

- 不写入 `WorldBreedingData.importedEntities`；
- 不自动写入默认 `BreedingRule`；
- 不改变现存实体行为；
- 只有管理员保存规则后才进入 FBM 管理。

### 5.5 自动适配的含义

“自动适配鱼类 Mod”在 P1 中定义为：

```text
识别到候选 EntityType
    -> 无需编写该 Mod 专用 Java 代码
    -> 管理员为候选保存世界规则
    -> FBM 通用事件/状态/控制器立即接管繁殖
    -> 失败时给出明确兼容等级和原因
```

它不等于“安装 Mod 后自动给所有鱼启用同一食物和计时”。繁殖食物、冷却和成长时间仍属于每个世界的管理员决策。

通用兼容等级：

```text
FULL
  EntityType 可创建同类型后代，Mob 导航可完成配对，LivingEntity 可正常渲染缩放

PARTIAL
  可以进入 Love/生成后代，但导航、渲染、原生 Variant 或其他表现存在安全降级

UNSUPPORTED
  EntityType 无法安全创建、不是可支持的实体，或运行时出现不可恢复的第三方约束
```

发现阶段不得批量实例化实体来探测兼容等级。兼容等级来自安全的静态信号、实际实体运行观察和结构化失败结果；初次未知候选标记为 `UNVERIFIED`，第一次真实验收后记录为 FULL/PARTIAL/UNSUPPORTED。

### 5.6 核心繁殖继续复用现有服务

Aquaculture 鱼必须走与原版鳕鱼相同的路径：

```text
EntityInteractionHandler
    -> BreedingRuleManager.find(entity.getType())
    -> BreedingState.enterLove(...)
    -> ActiveLoveIndex
    -> BreedingController
    -> ChildSpawner
    -> parent.getType().create(level)
```

不得为 `aquaculture:*` 增加分支，也不得缓存 Aquaculture 规则到实体。

### 5.7 失败优先修复通用边界

如果真实验收失败，按以下顺序处理：

1. 判断失败是否来自当前 FBM 对所有第三方 `Mob` 都可能存在的通用假设；
2. 优先在通用层修复实体创建、导航、渲染、状态或生命周期边界；
3. 使用 Aquaculture EntityType ID 写回归测试，但不引用 Aquaculture Java 类；
4. 只有无法通过公共 Minecraft/NeoForge 契约解决时，才提出单独的可选 Adapter Spec。

## 6. 候选发现设计

### 6.1 建议类型

```java
public record CandidateEntity(
        ResourceLocation entityTypeId,
        Component displayName,
        String sourceModId,
        Component sourceModName,
        CandidateConfidence confidence,
        CompatibilityLevel compatibility,
        Set<CandidateReason> reasons
) {}

public record DetectedFishMod(
        String modId,
        Component displayName,
        int registeredEntityCount,
        int fishCandidateCount
) {}

public enum CandidateConfidence {
    HIGH,
    MEDIUM,
    LOW
}

public enum CompatibilityLevel {
    UNVERIFIED,
    FULL,
    PARTIAL,
    UNSUPPORTED
}

public enum CandidateReason {
    VANILLA_FISH,
    COMMON_FISH_TAG,
    AQUATIC_TAG,
    ENTITY_KEYWORD,
    SOURCE_KEYWORD,
    WATER_CATEGORY,
    IMPORTED
}
```

`Registry path` 与实体翻译键中的实体名称部分共同归入 `ENTITY_KEYWORD`，避免同一实体名称被重复计算为两个独立弱信号；翻译键中的 Namespace 段不得再次算作实体关键词。Namespace、Mod ID 和 Mod 显示名归入 `SOURCE_KEYWORD`。`AQUATIC_TAG` 与 `WATER_CATEGORY` 共同归入水生属性组；`MEDIUM` 必须命中两个不同的弱信号语义组。

候选对象不持有第三方实体实例，也不缓存规则。

### 6.2 发现顺序

```text
读取 ModList 元数据并建立 Mod/Namespace 分组
    -> 读取当前 EntityType Registry
    -> 原版鱼白名单
    -> c:fish / c:fishes Tag
    -> Registry ID、实体翻译键、Namespace 关键词
    -> 安全的 MobCategory 辅助信号
    -> 当前存档 importedEntities
    -> 计算 HIGH / MEDIUM / LOW 置信度
    -> 以 ResourceLocation 合并去重
    -> 生成不可变 DiscoverySnapshot
    -> 按 source mod + entity path 稳定排序
```

Aquaculture 的 28 种鱼应通过 `COMMON_FISH_TAG` 入选，不依赖名称关键词。这样 `bayad`、`boulti`、`capitaine`、`synodontis` 等没有常见英文 fish 关键词的实体也不会遗漏。

### 6.3 无效和消失条目

- Tag 指向不存在实体时忽略并记录 Debug 日志，不阻断全部发现；
- `importedEntities` 中已经不再注册的 ID 保留在世界数据中，但候选结果标记为 unavailable，供未来 GUI 提示清理；
- 不调用每个 EntityType 的工厂进行候选发现，避免构造危险或要求特殊上下文的第三方实体。

## 7. 本地开发依赖策略

Aquaculture Jar 仅作为兼容验收运行时依赖：

- 使用 Gradle `localRuntime` 或开发运行目录加载；
- 不使用 `implementation`、`api` 或 `compileOnly`；
- 不把用户机器的绝对路径写入可共享构建配置；
- 推荐使用可选 Gradle Property 指向外部兼容测试目录，未配置时保持当前构建完全不变；
- Jar 文件继续留在仓库外的 `E:\JavaCodes\FishBreedingManager\常见鱼类mod及前置`，不得复制进版本控制；
- 自动化单元测试不得要求下载或加载 Aquaculture；真实兼容验收单独记录版本和文件哈希。

建议后续实施采用：

```text
-PfbmCompatModsDir=<local directory>
```

仅在该 Property 存在时把目录内 Jar 加入 `localRuntime`。属性缺失、目录缺失或目录为空时，普通 `test`、`build` 和 `runClient` 配置仍应可用。

## 8. 验收方案

### 8.1 自动化回归

不加载 Aquaculture Jar 的默认测试必须覆盖：

1. `c:fish` 和 `c:fishes` 都产生 `COMMON_FISH_TAG` 候选；
2. Registry 实体按 Mod ID/Namespace 分组，无法匹配 ModContainer 时安全回退到 Namespace；
3. 强信号得到 HIGH，两个独立弱信号得到 MEDIUM，单一弱信号保持 LOW；
4. 多个来源命中同一 ID 时只返回一个候选，并合并 reasons；
5. Tag 候选不会写入 `importedEntities`；
6. 候选发现不会创建默认规则；
7. `minecraft:aquatic` 单独命中不会被误判为高置信度鱼；
8. 不存在的导入 ID 能以 unavailable 状态返回或被单独报告；
9. 扫描结果整体构建失败时保留旧 DiscoverySnapshot；
10. Aquaculture 不在 classpath 时所有现有测试继续通过。

### 8.2 真实 Jar 启动验收

使用本地 `Aquaculture-1.21.1-2.7.21.jar`：

1. 启动开发客户端并进入允许命令的测试世界；
2. 确认 `aquaculture:smallmouth_bass`、`aquaculture:minnow`、`aquaculture:arapaima` 存在；
3. 确认 28 个 `c:fish` 实体全部进入发现结果；
4. 确认 `aquaculture:jellyfish` 和三种海龟没有进入默认鱼类候选；
5. 检查日志无 FBM/Aquaculture 注册异常、类加载错误或 Mixin 冲突。

### 8.3 主流程验收

主验收实体使用：

```text
aquaculture:smallmouth_bass
```

规则：

```text
food = minecraft:kelp
cooldown = 600 ticks
growth = 1200 ticks
enabled = true
```

步骤：

1. 创建并命名两条 Smallmouth Bass，保证它们不会因自然消失破坏持久化测试；
2. 分别用海带喂食，两个实体各消费一个海带并出现爱心；
3. 两者进入 FBM Love，在 8 格范围内搜索并靠近；
4. 生成第三条 `aquaculture:smallmouth_bass`；
5. 父母进入 600 Tick 冷却，冷却期喂食不被 FBM 消费；
6. 后代在客户端显示为 50% 尺寸；
7. 1200 Tick 后后代恢复成年尺寸；
8. 后代实体的类型、掉落/桶装行为和基础游动行为保持 Aquaculture 原有语义；
9. 日志中不出现 `ChildSpawnStatus` 失败或第三方异常。

### 8.4 热更新验收

对仍在世界中的同一批 Smallmouth Bass：

1. 把食物从 `minecraft:kelp` 改成 `minecraft:seagrass`；
2. 不退出世界，确认现存实体立即拒绝海带并接受海草；
3. 禁用规则，确认交互不再被 FBM 消费；
4. 重新启用规则，确认恢复；
5. 删除规则，确认现存和新生成的 Smallmouth Bass 都失去 FBM 繁殖能力；
6. 热更新前已开始的冷却和成长计时不追溯重算。

### 8.5 代表性体型烟雾验收

为避免只验证一种模型尺寸，至少再检查：

```text
aquaculture:minnow     # 小型
aquaculture:arapaima   # 大型
aquaculture:gar        # 长吻型
```

每种至少验证：EntityType 可创建同类型后代、幼体缩放不导致渲染矩阵泄漏、成年后恢复正常。该检查是烟雾验收，不要求重复完整热更新流程。

### 8.6 存档恢复验收

1. 规则启用时让已命名 Smallmouth Bass 进入 Love；
2. 卸载并重新加载区块，确认仍存在的实体恢复有效 Love 索引，但不强制恢复旧 mate UUID；
3. 保存并重新进入世界，确认世界规则仍存在；
4. Aquaculture Jar 暂时移除时，FBM 加载世界数据不得崩溃；未知 `aquaculture:*` 规则应被当前校验策略明确报告，而不是静默映射到错误实体；
5. 恢复 Jar 后，原 ID 对应规则可再次被正确解析。

## 9. 风险与处理

### 9.1 学校行为与 FBM 配对寻路竞争

`AquaFishEntity` 使用自定义同类型鱼群跟随 Goal。FBM 在 Love 期间调用导航靠近配偶，可能与鱼群 Goal 竞争。

处理：先观察真实行为。若无法稳定靠近，修复应限制在 FBM Love 期间的通用导航控制策略，不修改 Aquaculture Goal，也不通过反射删除 Goal。

### 9.2 不同 FishType 的渲染边界

FBM 当前只缩放模型，不修改碰撞箱。大型或长吻鱼的视觉中心可能出现轻微悬空或下沉。

处理：P1 只要求模型缩放稳定、矩阵正确恢复且成年时还原；不同模型的精细贴地偏移属于后续渲染增强，不阻断核心繁殖兼容。

### 9.3 第三方版本漂移

Aquaculture 未来可能修改 Tag 名称、实体基类或注册方式。

处理：生产兼容只依赖公共 Registry/Tag/EntityType；真实验收记录精确版本。升级兼容时重新运行本 Spec 的验收矩阵，不根据类名推断支持。

### 9.4 许可证与测试资产

本地 Jar 标记为 All Rights Reserved。

处理：只在用户本机作为运行时测试资产使用，不复制、不重打包、不提交、不通过 FBM 构建产物分发。

## 10. 完成标准

本兼容阶段只有同时满足以下条件才能标记完成：

- Tag 发现自动化测试通过；
- 已加载 Mod 与完整 EntityType Registry 扫描、分组和置信度测试通过；
- 现有 38 个 P0 测试继续通过；
- Aquaculture 缺失时 FBM 独立构建和运行不受影响；
- Aquaculture 2.7.21 真实 Jar 启动检查通过；
- Smallmouth Bass 完整繁殖、冷却、幼体、热更新、删除规则和存档恢复验收通过；
- Minnow、Arapaima、Gar 代表性体型烟雾验收通过；
- 没有 Aquaculture Java 类型硬依赖和按实体 ID 分支；
- 没有提交或重分发第三方 Jar；
- `docs/testing/` 中新增真实兼容验收记录；
- `.agent/HANDOFF.md` 更新为实际完成状态，并准确记录未通过项或限制。

## 11. 后续顺序

Aquaculture 兼容通过后，再使用本地 Upgrade Aquatic 7.0.1 + Blueprint 8.1.1 测试异构水生实体：

```text
Perch       -> AbstractSchoolingFish
Pike        -> Blueprint BucketableWaterAnimal + VariantHolder
Lionfish    -> Blueprint BucketableWaterAnimal
Nautilus    -> 自定义水生导航
Jellyfish   -> 自定义抽象基类和动画
```

这一步用于验证 FBM 是否仍对“非 AbstractFish、带 Variant、自定义导航”的实体保持安全降级；它应有独立 Spec，不扩展本文件范围。

## 12. 参考资料

- 用户提供的本地 Aquaculture 2.7.21 NeoForge Jar 及其 `neoforge.mods.toml`、EntityType Tag 和字节码。
- Aquaculture 官方仓库：https://github.com/TeamMetallurgy/Aquaculture
- Aquaculture 官方发布入口：https://www.curseforge.com/minecraft/mc-mods/aquaculture
- FBM 权威需求：`docs/Fish_Breeding_Manager_Requirements.md`
- FBM 当前架构：`docs/Project_Analysis_and_Summary.md`
- FBM P0 验收：`docs/testing/P0_Cod_Acceptance.md`
