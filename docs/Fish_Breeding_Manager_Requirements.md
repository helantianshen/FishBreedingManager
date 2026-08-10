# Fish Breeding Manager（FBM）模组开发需求与技术交接文档

> 文档用途：交接给本地开发 Agent，作为项目需求、架构约束、开发顺序和验收标准的统一依据。  
> 目标平台：Minecraft 1.21.1 / NeoForge / Java 21  
> 项目名称：**Fish Breeding Manager**  
> 简称：**FBM**  
> 建议 Mod ID：`fishbreedingmanager`

---

## 1. 项目定位

Fish Breeding Manager 是一个面向 Minecraft 原版及第三方 Mod 生物的**鱼类繁殖管理模组**。

项目目标不是新增固定几种鱼的繁殖玩法，而是提供一套：

- 可配置；
- 数据驱动；
- 尽量不侵入第三方 Mod；
- 支持原版和第三方实体；
- 支持世界级独立配置；
- 支持运行时热重载；
- 支持 GUI 管理；

的通用鱼类繁殖系统。

核心目标：

> 允许玩家/服务器管理员选择原版或其他 Mod 注册的鱼类实体，并为这些原本不具备繁殖机制、或需要统一管理繁殖行为的实体配置繁殖能力。

---

## 2. 技术基线

### 2.1 Minecraft / NeoForge

目标版本：

```text
Minecraft 1.21.1
NeoForge 21.1.x
Java 21
```

项目基于 NeoForge 官方 1.21.1 ModDevGradle MDK 创建。

当前开发工程应继续保持在 **1.21.1 单版本 NeoForge**，不要为了兼容其他版本而提前引入复杂的多版本抽象。

### 2.2 构建工具

项目使用 NeoForge 官方 ModDevGradle 模板。

开发 Agent 应优先参考：

- NeoForge 1.21.1 官方文档；
- NeoForged 官方源码；
- NeoForge 1.21.1 API；
- 当前项目实际依赖版本。

不要套用：

- Forge 1.20.x 旧教程；
- `net.minecraftforge.*` 旧 API；
- 旧 Capability 教程；
- 旧 ForgeGradle 工程结构；
- 已废弃的配置、网络、事件 API。

### 2.3 Java

使用：

```text
Java 21
```

不要把 Java 25 等更高版本作为项目目标字节码版本。

---

# 3. 核心产品目标

FBM 最终需要完成以下能力：

1. 为原版鱼类提供可配置繁殖机制；
2. 为第三方 Mod 中原本没有繁殖行为的鱼类增加繁殖能力；
3. 支持以 EntityType 为核心进行规则管理；
4. 支持通过配置文件管理繁殖规则；
5. 支持 GUI 配置；
6. 支持自动发现“可能是鱼”的实体；
7. 支持高级模式手动导入其他实体；
8. 导入实体需持久化；
9. 每个世界独立保存配置；
10. 支持服务端权限控制；
11. 支持热重载；
12. 支持第三方实体没有原生幼体机制时的 FBM 幼体系统；
13. 尽量继承同一 EntityType 内部的 Variant；
14. 支持多个繁殖物品 / Tag；
15. 不要求第三方 Mod 主动适配 FBM。

---

# 4. 繁殖行为定义

繁殖行为采用 Minecraft 原版动物风格，而不是“喂食后直接生成幼体”。

完整行为流程：

```text
成年鱼 A                      成年鱼 B
   │                             │
玩家喂食                      玩家喂食
   │                             │
   ▼                             ▼
进入 Love 状态                进入 Love 状态
       \                       /
        \                     /
         └── 搜索同 EntityType 配偶
                      │
                      ▼
                 双方互相靠近
                      │
                      ▼
                    繁殖
                      │
                      ▼
              生成同类型幼体
                      │
               ┌──────┴──────┐
               ▼             ▼
           父母冷却        幼体成长
```

表现上尽量接近原版：

- 玩家使用繁殖物品喂食；
- 出现爱心粒子；
- 两个实体进入求偶状态；
- 主动寻找同类型配偶；
- 互相靠近；
- 生成后代；
- 父母进入繁殖冷却；
- 后代以幼体状态存在；
- 幼体经过配置时间后成年。

---

# 5. 后代规则

## 5.1 EntityType 必须相同

已确定：

> 后代一定与父母拥有相同的 EntityType。

即：

```text
cod + cod -> cod
bass + bass -> bass
```

不支持：

```text
fish_a + fish_b -> fish_c
```

第一版不考虑跨 EntityType 杂交。

约束：

```text
parentA.getType()
==
parentB.getType()
==
child.getType()
```

---

# 6. 可配置繁殖参数

第一版每个实体正式开放三个核心参数：

1. **繁殖物品**
2. **繁殖冷却时间**
3. **幼体成长时间**

概念数据结构：

```java
BreedingRule {
    EntityType<?> entityType;
    Ingredient breedingIngredient;
    int breedingCooldownTicks;
    int growthTimeTicks;
}
```

---

# 7. 繁殖物品设计

繁殖物品不能只支持单个 Item。

正式要求：

> 使用类似 Minecraft `Ingredient` 的抽象。

因此应支持：

### 单个物品

```text
minecraft:kelp
```

### 多个物品

```text
minecraft:kelp
minecraft:seagrass
```

### Item Tag

例如：

```text
#some_namespace:fish_breeding_food
```

GUI 中可以表现为：

```text
繁殖物品：
[海带] [海草] [+]
```

配置层应避免设计成单个 `Item` 字段，否则后续扩展成本较高。

---

# 8. 繁殖冷却时间

每次成功繁殖后，父母进入冷却。

配置单位在 UI 中可以使用：

```text
秒 / 分钟
```

内部统一转换为游戏 Tick。

例如：

```text
300 秒 = 6000 ticks
```

冷却状态属于实体运行时状态。

---

# 9. 幼体成长时间

每个繁殖规则配置：

```text
growthTime
```

表示新出生幼体多久后成年。

同样建议内部保存为 Tick。

---

# 10. 幼体机制

已确定：

> 即使目标实体本身没有原版 `AgeableMob` 一类的年龄系统，FBM 也要提供自己的幼体机制。

因此不能把 FBM 设计成只支持 `AgeableMob`。

推荐逻辑：

```text
目标实体有可安全使用的原生年龄系统
        │
       YES
        ↓
优先适配原生年龄系统

        │ NO
        ↓

FBM 自己维护 Juvenile 状态
        ↓
客户端缩小实体视觉尺寸
        ↓
经过 growthTime
        ↓
恢复成年状态
```

建议默认视觉：

```text
幼体尺寸 ≈ 成年体 50%
成年尺寸 = 100%
```

具体比例后续可以调整。

---

# 11. BreedingState

“规则”和“实体状态”必须彻底分离。

原则：

> **Rule 属于世界，State 属于实体。**

实体只保存运行时状态，不保存永久规则副本。

建议状态：

```java
BreedingState {
    boolean inLove;
    long loveUntil;

    long cooldownUntil;

    boolean juvenile;
    long adultAt;

    UUID mate;
}
```

可以进一步根据实际 API 调整。

建议优先研究 NeoForge 1.21.1 Data Attachment 作为状态附加方案。

目标：

- 不修改第三方实体 Java 类；
- 不要求目标实体实现 FBM 接口；
- 能给已有 Entity 附加 FBM 状态；
- 状态可按需要持久化。

---

# 12. Variant 继承

同一个 EntityType 可能存在不同 Variant。

例如：

```text
minecraft:tropical_fish
```

虽然 EntityType 相同，但可能拥有颜色、花纹等不同 Variant。

正式需求：

> 第一版尽量让后代随机继承父母其中一方的 Variant。

概念行为：

```text
父 A Variant
父 B Variant
      ↓
随机选择父 A 或父 B
      ↓
尝试复制其 Variant
```

例如：

```text
红色鱼 + 蓝色鱼

50% -> 红
50% -> 蓝
```

## 12.1 无法通用识别的第三方 Variant

第三方 Mod 可能把 Variant 存储在：

- 自定义字段；
- 自定义 SynchedEntityData；
- 私有数据结构；
- 自定义 Genome；
- 自定义组件；

中。

对于 FBM 无法安全识别的 Variant：

> 不允许通过反射、ASM 或暴力字段复制去猜测第三方 Mod 内部实现。

默认行为：

```text
EntityType.create(...)
    ↓
创建目标类型的默认个体
```

未来可以预留：

```text
BreedingAdapter API
```

用于对特定 Mod / Entity 提供 Variant 继承适配。

---

# 13. 实体发现系统

FBM 不应该默认展示整个 EntityType Registry。

原因：

Entity Registry 中不仅存在鱼类，还可能包含：

- TNT；
- Arrow；
- Projectile；
- Boat；
- Minecart；
- ItemEntity；
- ExperienceOrb；
- Display Entity；
- 其他无意义目标。

因此默认界面必须进行自动筛选。

---

# 14. 默认实体筛选

已确定第一版主要方案：

> 通过关键词等方式筛选可能属于鱼类的实体。

默认可以组合：

- Registry ID；
- 实体本地化名称；
- Namespace；
- 已知原版鱼白名单；
- Entity 类型信息；
- MobCategory；
- 其他安全辅助信息。

关键词示例：

```text
fish
salmon
cod
bass
trout
shark
carp
tuna
puffer
eel
...
```

注意：

单纯使用 `fish` 不足以发现：

```text
minecraft:cod
minecraft:salmon
```

因此默认发现机制应是多策略组合。

建议抽象：

```text
CandidateEntityFilter
├── VanillaFishProvider
├── KeywordFilter
├── MobCategoryFilter
├── ImportedEntityProvider
└── FutureTagProvider
```

---

# 15. 高级实体搜索与导入

高级设置需要提供：

```text
搜索完整 EntityType Registry
```

用户可以输入实体名、ID 等关键字。

例如：

```text
搜索：bass

结果：
aquaculture:bass
some_mod:small_bass
othermod:sea_bass
```

用户可以点击：

```text
[导入到 FBM]
```

之后该实体加入 FBM 管理列表。

---

# 16. 手动导入实体持久化

已确定：

> 手动导入的实体必须保存，下次进入该世界仍然可以找到。

例如：

```text
weirdfishmod:moonfish
```

即使：

- 不包含 `fish` 关键词；
- 不符合默认筛选；
- 不属于默认白名单；

只要用户手动导入过，就必须继续显示。

逻辑：

```text
自动发现实体
      +
手动导入实体
      ↓
最终 FBM 管理候选列表
```

用户应支持：

```text
取消导入
```

如果该实体已经存在繁殖配置，删除/取消导入前应提示确认。

---

# 17. 实体身份标识

任何实体规则必须以：

```text
ResourceLocation / Registry ID
```

作为稳定主键。

例如：

```text
minecraft:cod
minecraft:salmon
aquaculture:bass
some_mod:blue_fish
```

不要使用：

- Java 类名；
- 本地化显示名；
- 翻译文本；
- 实例 UUID；

作为繁殖规则主键。

---

# 18. GUI

需要自行实现 FBM 管理 GUI。

基础结构可以类似：

```text
┌──────────────────────────────────────────────┐
│ Fish Breeding Manager                       │
├─────────────────────┬────────────────────────┤
│ 搜索：____________  │                        │
│                     │       实体模型          │
│ Minecraft           │                        │
│  鳕鱼               │          🐟            │
│  鲑鱼               │                        │
│  河豚               │                        │
│                     │                        │
│ Other Mod           ├────────────────────────┤
│  Bass               │ Entity: minecraft:cod │
│  Trout              │ Source: Minecraft     │
│                     │                        │
│                     │ 繁殖物品：[海带]        │
│                     │ 冷却：300 秒            │
│                     │ 成长：1200 秒           │
│                     │                        │
│ [高级实体搜索]       │      [保存配置]         │
└─────────────────────┴────────────────────────┘
```

---

# 19. GUI 基础功能

第一版 GUI 需要支持：

- 自动读取当前 Entity Registry；
- 默认鱼类候选筛选；
- 实体列表；
- 搜索；
- 显示 Entity ID；
- 显示本地化实体名；
- 显示来源 Mod / Namespace；
- 选择实体；
- 编辑繁殖物品；
- 编辑繁殖冷却；
- 编辑幼体成长时间；
- 保存配置；
- 高级实体搜索；
- 导入实体；
- 删除/取消导入；
- 配置保存后热应用。

---

# 20. GUI 3D 实体预览

正式需求中包含：

> 在 GUI 选中实体后，显示该实体的 3D 模型。

期望：

```text
实体名称
Entity ID

   [3D Entity Preview]

支持查看 / 旋转
```

该功能优先级低于核心繁殖、热重载和基础 GUI。

---

# 21. 权限模型

已确定采用：

> 单机玩家可以修改；多人服务器只有 OP / 有权限的管理员可以修改。

## 单机

允许：

- 查看；
- 导入；
- 删除；
- 修改；
- 保存；
- 热重载。

## Dedicated Server

普通玩家：

```text
查看：可以考虑允许
修改：禁止
```

OP / 管理员：

- 查看；
- 导入；
- 删除；
- 修改；
- 保存；
- 热重载。

所有修改请求必须在服务端再次进行权限校验。

不能只靠：

```text
客户端隐藏按钮
```

保证安全。

---

# 22. 网络模型

推荐：

```text
Client GUI
    │
    │ UpdateRulePayload
    ▼
Server
    │
    ├── 权限检查
    ├── 参数校验
    ├── Registry 校验
    ├── 更新世界规则
    ├── 持久化
    └── 更新 Runtime Snapshot
```

之后根据需要：

```text
Server -> Client
```

同步最新规则和管理列表。

服务端必须是繁殖规则的最终权威来源。

---

# 23. 每世界独立配置

已确定：

> 每个世界拥有独立的 Fish Breeding Manager 配置。

例如：

```text
World A
├── cod
│   ├── kelp
│   ├── cooldown = 300s
│   └── growth = 1200s
└── ...

World B
├── cod
│   ├── seagrass
│   ├── cooldown = 120s
│   └── growth = 600s
└── ...
```

Dedicated Server 同样拥有自己的世界级规则。

不要设计成：

```text
.minecraft/config/fbm-rules.json
```

然后所有世界共享繁殖规则。

---

# 24. 全局配置与世界配置分离

可以存在全局配置，但全局配置只保存：

- GUI 偏好；
- 调试模式；
- 默认筛选关键词；
- 客户端显示选项；
- 其他不直接影响某个世界玩法的数据。

真正影响玩法的数据必须属于世界：

- Entity ID；
- 手动导入实体；
- 繁殖物品；
- 繁殖冷却；
- 幼体成长时间；
- 未来其他 BreedingRule。

---

# 25. 热重载是 P0 架构要求

这是本项目极其重要的约束。

热重载不是后期附加功能。

正式要求：

> 从第一版底层架构开始，就必须优先选择可以支持热重载的实现路线。

定义：

> 不退出世界、不重启服务器、不重启 Minecraft 的情况下，修改繁殖配置并使现存实体后续行为采用新规则。

---

# 26. 热重载行为

例如当前：

```text
minecraft:cod
breeding_item = minecraft:kelp
```

运行过程中修改为：

```text
minecraft:cod
breeding_item = minecraft:seagrass
```

执行保存或：

```text
/fbm reload
```

之后：

```text
现存 cod：
kelp      -> 不再触发
seagrass  -> 立即触发
```

无需：

- 退出世界；
- 重启 Minecraft；
- 重启服务器；
- 重新生成 cod；
- 重新加载 Chunk。

---

# 27. 新增实体规则热生效

运行中新增：

```text
some_mod:bass
```

之后：

```text
当前世界已存在的 bass
+
之后新生成的 bass
```

都应该立即受到 FBM 管理。

---

# 28. 删除规则热生效

如果运行中删除：

```text
minecraft:cod
```

规则：

- 现存 cod 立即失去 FBM 提供的繁殖能力；
- 新生成 cod 同样不再被 FBM 管理；
- 当前 FBM Love 状态建议清除；
- 当前 mate 关系建议取消；
- 本次尚未完成的 FBM 繁殖流程建议终止。

---

# 29. 热重载架构约束

禁止把主方案设计为：

```text
Mod Load
   ↓
扫描目标 Entity Class
   ↓
ASM / 字节码永久注入繁殖行为
```

理由：

- Class 加载后难以动态撤销；
- 热重载困难；
- 第三方兼容风险高；
- 容易与其他 Coremod / Mixin 冲突；
- 不适合作为动态配置管理 Mod 的主路线。

Mixin / ASM / Coremod 只能作为：

> 极端兼容场景的最后手段。

不能成为 FBM v1 的核心实现。

---

# 30. Runtime Breeding Engine

推荐架构：

```text
                    ┌──────────────────────┐
世界配置 / GUI ────→│ BreedingRuleManager  │
                    │ Runtime Snapshot     │
                    └──────────┬───────────┘
                               │
                        动态查询当前规则
                               │
              ┌────────────────┴──────────────┐
              ▼                               ▼
    InteractionHandler             BreedingController
              │                               │
              ▼                               ▼
      玩家喂食判断                    求偶 / 移动 / 繁殖
              │                               │
              └──────────────┬────────────────┘
                             ▼
                      BreedingState
                             │
                             ▼
                           Entity
```

---

# 31. Rule 与 State 分离

必须遵循：

```text
Rule:
“这种鱼应该怎么繁殖？”

State:
“这条具体的鱼现在是什么状态？”
```

### Rule

属于世界：

```text
BreedingRuleManager
```

### State

属于单个 Entity：

```text
BreedingState
```

不能把 Rule 在实体生成时复制进去永久使用，否则热重载无法影响现存实体。

---

# 32. Runtime Rule Snapshot

规则建议解析成不可变 Runtime Snapshot。

流程：

```text
世界持久化数据 / JSON
          ↓
        解析
          ↓
        校验
          ↓
Map<EntityType<?>, BreedingRule>
          ↓
Immutable Runtime Snapshot
```

运行时只读。

例如：

```java
public record BreedingRuleSnapshot(
    Map<EntityType<?>, BreedingRule> rules,
    Set<ResourceLocation> importedEntities
) {}
```

管理器：

```java
public final class BreedingRuleManager {
    private volatile BreedingRuleSnapshot snapshot;
}
```

或者使用其他线程安全的原子替换方案。

---

# 33. 热重载采用 Atomic Swap

推荐：

```text
旧 Snapshot
      │
      │ Reload
      ▼
读取新配置
      ↓
完整解析
      ↓
完整校验
      ↓
成功？
├── NO -> 保留旧 Snapshot
└── YES
       ↓
  Atomic Swap
       ↓
新 Snapshot
```

好处：

- 配置错误不会破坏当前正常规则；
- 不需要运行过程中不断锁 Map；
- 查询成本低；
- 热重载实现简单；
- 更容易保证状态一致性。

---

# 34. 实际行为必须动态读取当前 Rule

例如喂食事件：

```java
BreedingRule rule = ruleManager.find(entity.getType());
```

每一次真正发生行为时查当前 Snapshot。

不能：

```text
Entity Spawn
   ↓
把 breedingItem 缓存在实体中
```

否则修改配置不会影响现存实体。

---

# 35. 喂食交互

概念逻辑：

```java
onEntityInteract(player, entity, hand) {
    BreedingRule rule = ruleManager.find(entity.getType());

    if (rule == null) {
        return;
    }

    if (!rule.breedingIngredient().test(player.getItemInHand(hand))) {
        return;
    }

    if (!state.canBreed()) {
        return;
    }

    state.enterLoveMode();

    consumeItem();
    spawnHeartParticles();
}
```

实际 API 以 NeoForge 1.21.1 当前官方事件系统为准。

---

# 36. 求偶 AI 路线

为了热重载和兼容性，优先研究：

> FBM 中央 BreedingController

而不是启动时向所有目标实体永久插入 AI Goal。

概念：

```text
Server Tick / 定时 Tick
      ↓
找到处于 FBM Love 状态的实体
      ↓
查找附近同 EntityType + 同样可繁殖实体
      ↓
配对
      ↓
驱动双方 Navigation
      ↓
距离满足条件
      ↓
生成后代
```

优势：

- 无需修改第三方 Class；
- 热重载天然支持；
- 删除 Rule 可以立即停止处理；
- 新增 Rule 可以立即影响现存实体；
- 行为集中管理；
- 调试简单。

---

# 37. 性能要求

不要每 Tick 扫描全世界全部实体。

BreedingController 应采用低成本策略，例如：

- 只跟踪进入 FBM Love 状态的实体；
- 使用 ServerLevel 附近实体查询；
- 每若干 Tick 执行一次配偶搜索；
- 已有 mate 后无需重复全范围搜索；
- 使用合理搜索半径；
- Rule 查找使用 O(1) Map。

避免：

```text
每 Tick
  ×
世界全部 Entity
  ×
全部 Rule
```

---

# 38. 热重载后的计时器语义

正式建议并冻结：

> 已经开始运行的计时器不追溯修改。

例如：

当前某实体还有：

```text
100 秒繁殖冷却
```

管理员把规则：

```text
300 秒 -> 60 秒
```

修改后：

- 当前实体仍剩 100 秒；
- 下一次成功繁殖开始采用 60 秒。

---

# 39. 幼体成长计时同样不追溯

例如：

幼体出生时：

```text
growthTime = 1200 秒
```

出生后管理员改为：

```text
600 秒
```

则：

- 当前幼体仍按出生时计算好的 `adultAt`；
- 新出生幼体采用 600 秒。

原则：

```text
规则数据：
繁殖物品等 -> 热重载立即影响

已经产生的事件状态：
cooldownUntil
adultAt
-> 不追溯修改
```

这样可以显著降低热重载复杂度和歧义。

---

# 40. Reload 入口

第一版至少支持两种：

## GUI Save

```text
GUI 修改
   ↓
发送服务端
   ↓
校验
   ↓
保存世界数据
   ↓
替换 Runtime Snapshot
   ↓
立即生效
```

## Command

```text
/fbm reload
```

用于：

- 开发调试；
- 整合包作者手动改配置；
- 服务器管理员手工编辑配置文件后重新加载。

---

# 41. 文件监听

第一版不要求：

```text
WatchService
文件一变化自动 Reload
```

原因：

- 文件可能处于写入中；
- 需要额外线程；
- 跨平台差异；
- 错误处理复杂；
- 收益不高。

第一版：

```text
编辑配置
-> /fbm reload
```

已经满足热重载要求。

GUI 保存则应自动触发 Reload。

---

# 42. 第三方实体兼容等级

不要对外承诺：

> 所有 LivingEntity 都能 100% 完整繁殖。

第三方实现不可控。

建议内部划分兼容等级。

## Full

满足：

```text
Mob
+
正常 Navigation
+
EntityType 可正常创建实例
```

支持：

- 喂食；
- Love；
- 求偶；
- 移动；
- 后代；
- 冷却；
- 成长。

## Partial

例如：

```text
LivingEntity
但 Navigation / Mob 能力不完整
```

可能支持：

- 喂食；
- 状态；
- 后代；

但无法保证：

- 主动寻路求偶。

## Unsupported

例如：

- TNT；
- Arrow；
- Boat；
- Display；
- Projectile；
- 其他非合理目标。

禁止配置或显示强警告。

---

# 43. 非侵入式设计原则

核心原则：

```text
不修改目标 Mod Java 类
不要求目标 Mod 实现 FBM Interface
不要求目标 Mod 注册 FBM API
不在 Mod 启动阶段永久注入规则
```

优先使用：

- NeoForge Event；
- Data Attachment；
- Runtime Controller；
- Registry ID；
- 服务端 Tick；
- Network Payload；
- 世界 SavedData / 世界级持久化；
- 必要的客户端渲染扩展。

---

# 44. 世界级持久化方案

具体技术实现由 Agent 根据 NeoForge 1.21.1 API 选择。

优先方向：

```text
World SavedData / 世界存档级数据
```

或者 NeoForge 1.21.1 当前更合适的世界持久化 API。

需要持久化：

```text
BreedingRule
ImportedEntity IDs
未来世界级 FBM 设置
```

不要直接依赖普通全局 `ModConfigSpec` 保存全部动态规则。

---

# 45. 配置格式

逻辑结构可以类似：

```json
{
  "imported_entities": [
    "some_mod:bass"
  ],
  "rules": {
    "minecraft:cod": {
      "enabled": true,
      "breeding_ingredient": {
        "items": [
          "minecraft:kelp",
          "minecraft:seagrass"
        ]
      },
      "breeding_cooldown_ticks": 6000,
      "growth_time_ticks": 24000
    }
  }
}
```

具体 Codec / JSON / SavedData 序列化结构由开发 Agent 根据当前 API 实现。

需求层不强制 JSON 文件必须直接作为唯一存储形式。

---

# 46. 内置原版鱼支持

默认自动候选至少应该包含原版：

```text
minecraft:cod
minecraft:salmon
minecraft:tropical_fish
minecraft:pufferfish
```

后续可以加入更多明确属于水生生物的候选。

---

# 47. 第三方 Mod Preset

未来可以提供常见 Mod 鱼类 Preset。

但不是 v1 核心阻塞项。

正确优先级：

```text
通用 Registry 扫描
+
手动导入
```

优先于：

```text
为每个 Mod 手写兼容列表
```

---

# 48. Adapter API

未来可以设计：

```java
BreedingAdapter<T extends Entity>
```

用于解决第三方特殊行为，例如：

- Variant 复制；
- 自定义幼体机制；
- 特殊 Spawn；
- 自定义 Navigation；
- 遗传数据；
- 自定义繁殖表现。

v1 先预留扩展点即可，不要求第一版完成完整公共 API。

---

# 49. 开发顺序

正式建议顺序：

```text
01. Entity identification
      ↓
02. 世界级 BreedingRuleStore
      ↓
03. Runtime Snapshot
      ↓
04. Reload / Atomic Swap
      ↓
05. BreedingState Attachment
      ↓
06. Feed Interaction
      ↓
07. Love State
      ↓
08. Mate Search
      ↓
09. Navigation
      ↓
10. Child Creation
      ↓
11. Juvenile System
      ↓
12. Cooldown
      ↓
13. /fbm reload
      ↓
14. 第三方 Mod 实体验证
      ↓
15. Entity Browser GUI
      ↓
16. Advanced Entity Import
      ↓
17. GUI Rule Editor
      ↓
18. GUI Save -> Live Reload
      ↓
19. 3D Entity Preview
      ↓
20. Variant Adapter
```

---

# 50. 不要先开发 GUI

第一阶段不要把主要时间投入 GUI。

原因：

项目最大的技术风险不是 GUI，而是：

1. 任意 EntityType 是否能非侵入式繁殖；
2. 第三方实体 Navigation 是否可控；
3. 后代是否能稳定创建；
4. 无原生年龄系统实体能否实现 FBM 幼体；
5. 热重载是否能影响现存实体。

这些必须先通过 PoC 验证。

---

# 51. 第一阶段 PoC

推荐第一个目标实体：

```text
minecraft:cod
```

初始规则：

```text
breeding item = minecraft:kelp
cooldown = 600 ticks
growth = 1200 ticks
```

---

# 52. 第一阶段验收标准

必须完成：

1. 两条原版 cod 默认无法繁殖；
2. 安装 FBM；
3. FBM 加载 cod BreedingRule；
4. 玩家分别用 kelp 喂两条 cod；
5. 两条 cod 进入 Love 状态；
6. 出现爱心表现；
7. 两条 cod 主动向对方移动；
8. 距离满足后生成新的 `minecraft:cod`；
9. 父母进入配置的繁殖冷却；
10. 幼体进入 FBM juvenile 状态；
11. 幼体模型明显小于成年体；
12. 经过 growthTime 后成年；
13. 不退出世界；
14. 修改配置：`kelp -> seagrass`；
15. 执行 `/fbm reload`；
16. 世界无需重新加载；
17. 现存 cod 立即不再接受 kelp；
18. 现存 cod 立即接受 seagrass；
19. 删除 cod BreedingRule；
20. 执行 `/fbm reload`；
21. 现存 cod 立即失去 FBM 繁殖行为。

只有这一套完整打通后，再继续 GUI。

---

# 53. 第二阶段第三方 Mod 验收

至少选择一个：

> 原本没有繁殖机制的第三方鱼类 Entity。

要求：

- 能通过 Entity Registry 找到；
- 能手动导入；
- 能保存到当前世界；
- 重进世界仍然存在；
- 能应用 BreedingRule；
- 能喂食；
- 能 Love；
- 能寻找同 EntityType；
- 能生成同 EntityType 后代；
- 能运行 FBM 幼体系统；
- 修改规则后可以热重载；
- 删除规则后现存实体立即停止受 FBM 管理。

---

# 54. GUI 阶段验收

GUI 完成后：

1. 打开 FBM；
2. 自动显示原版鱼候选；
3. 自动显示符合关键词的 Mod 鱼；
4. 可以搜索；
5. 可以看到 Registry ID；
6. 可以看到本地化名称；
7. 可以看到来源 Mod；
8. 可以进入高级实体搜索；
9. 可以搜索完整 Registry；
10. 可以导入一个未被默认发现的实体；
11. 导入实体会保存；
12. 重新进入世界仍能看到；
13. 可以配置 Ingredient；
14. 可以配置冷却；
15. 可以配置成长时间；
16. 保存后立即热生效；
17. 服务端普通玩家不能修改；
18. OP 可以修改；
19. GUI 最终支持 3D Entity Preview。

---

# 55. P0 / P1 / P2 优先级

## P0

必须优先完成：

- 世界级 BreedingRule；
- Runtime Snapshot；
- 热重载；
- Entity BreedingState；
- 喂食；
- Love；
- Mate；
- Navigation；
- Child Spawn；
- 同 EntityType；
- Cooldown；
- Juvenile；
- Growth；
- 第三方 EntityType 支持；
- `/fbm reload`。

## P1

- Entity Browser GUI；
- 默认鱼类筛选；
- 高级 Registry 搜索；
- 实体导入；
- 导入持久化；
- GUI Rule Editor；
- 权限；
- Network Sync；
- GUI Save 热重载。

## P2

- 3D Entity Preview；
- 更完善的 Variant 继承；
- Adapter API；
- 常见第三方 Mod Preset；
- 更高级筛选；
- Tag / Namespace 批量管理；
- 更复杂的繁殖条件。

---

# 56. 当前不做的内容

v1 不要求：

- 不同 EntityType 杂交；
- 基因系统；
- 复杂遗传；
- 天气繁殖条件；
- Biome 条件；
- 光照条件；
- 水深条件；
- 群体密度限制；
- 多胎概率；
- 随机繁殖成功率；
- 文件系统自动监听；
- 自动为所有第三方私有 Variant 写兼容；
- 为所有 Minecraft 版本同时维护；
- Fabric / Forge 多加载器支持；
- Coremod / ASM 作为主要实现。

---

# 57. 建议包结构

仅作为实现参考，不是硬性需求：

```text
fishbreedingmanager/
├── FishBreedingManager.java
│
├── breeding/
│   ├── BreedingRule.java
│   ├── BreedingRuleManager.java
│   ├── BreedingRuleSnapshot.java
│   ├── BreedingController.java
│   ├── BreedingState.java
│   ├── ChildSpawner.java
│   ├── JuvenileController.java
│   └── VariantHandler.java
│
├── attachment/
│   └── ModAttachments.java
│
├── event/
│   ├── EntityInteractionHandler.java
│   ├── ServerTickHandler.java
│   └── EntityLifecycleHandler.java
│
├── persistence/
│   ├── WorldBreedingData.java
│   └── RuleCodec.java
│
├── command/
│   └── FBMCommand.java
│
├── network/
│   ├── ModNetworking.java
│   ├── UpdateRulePayload.java
│   └── SyncRulesPayload.java
│
├── client/
│   ├── screen/
│   ├── widget/
│   └── render/
│
├── discovery/
│   ├── EntityDiscoveryService.java
│   ├── CandidateEntityFilter.java
│   └── ImportedEntityStore.java
│
└── compat/
    └── adapter/
```

---

# 58. 开发 Agent 的重要约束

开发过程中必须：

1. 优先查 NeoForge **1.21.1 官方文档/API**；
2. API 不确定时直接查 NeoForged 源码；
3. 不从旧 Forge 教程照搬代码；
4. 不提前使用 ASM；
5. 不为了“看起来通用”而过度抽象；
6. 首先证明 cod PoC；
7. 再证明第三方鱼；
8. 再开发 GUI；
9. 每完成一个阶段都保证热重载不被破坏；
10. Rule 必须运行时动态查询；
11. State 与 Rule 必须分离；
12. 服务端必须是规则权威；
13. 世界玩法配置必须每世界独立；
14. 当前实体必须能响应新增/删除 Rule；
15. 当前计时状态不因规则热重载被追溯重算。

---

# 59. 项目最终一句话定义

> **Fish Breeding Manager 是一个面向 Minecraft 1.21.1 NeoForge 的世界级、数据驱动、支持热重载的鱼类繁殖管理框架，用于为原版及第三方 Mod 实体动态增加和配置类似原版动物的繁殖行为。**

---

# 60. 当前需求状态

本文档中的以下决策已经确认，不需要开发 Agent 再询问：

- 繁殖方式：原版动物式双亲繁殖；
- 参数：繁殖物品、繁殖冷却、幼体成长时间；
- 后代 EntityType：必须与父母相同；
- 权限：单机可编辑，服务器仅 OP/管理员可编辑；
- 默认实体发现：关键词等方式筛选；
- 高级实体导入：支持完整 Registry 搜索；
- 导入实体：必须持久化；
- 热重载：P0 架构要求；
- 第三方无幼体系统：FBM 自己提供幼体机制；
- Variant：尽量随机继承父母一方，无法通用识别时创建默认个体；
- 繁殖物品：允许多个 Item / Tag，按 Ingredient 思路设计；
- 配置作用域：每个世界独立；
- 已开始的 cooldown / growth 计时不因热重载追溯修改；
- GUI 不优先于核心 PoC；
- ASM / Coremod 不作为主路线。

如果具体 NeoForge 1.21.1 API 与本文建议实现存在冲突，允许调整**技术实现**，但不得改变上述产品需求和热重载语义。
