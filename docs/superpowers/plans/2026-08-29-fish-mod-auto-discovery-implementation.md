# 第三方鱼类 Mod 自动发现 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不依赖任何第三方 Mod Java 类型的前提下，扫描已加载 Mod 与完整 EntityType Registry，按来源分组，并生成带识别理由和置信度的不可变鱼类候选快照。

**Architecture:** `FishDiscoveryEngine` 只处理可构造、可单测的描述对象，负责信号合并、置信度和来源统计；`MinecraftDiscoverySource` 是唯一读取 `ModList`、`BuiltInRegistries.ENTITY_TYPE` 与 Tag 的边界适配器；`FishDiscoveryManager` 为每个服务器原子发布最近一次成功快照。服务器启动和服务端 Tag 重载只触发重建，不在 Tick 中扫描，也不创建实体或玩法规则。

**Tech Stack:** Java 21、Minecraft 1.21.1、NeoForge 21.1.244、ModDevGradle 2.0.143、JUnit 5、Mockito 5、NeoForge ModList/Registry/TagsUpdatedEvent。

**Design:** `docs/superpowers/specs/2026-08-29-aquaculture-2-compatibility-design.md`

---

## 执行状态（2026-08-29）

- [x] Task 1–3：不可变模型、置信度、去重、来源分组、排序和失效导入。
- [x] Task 4：完整 EntityType Registry、`c:fish`/`c:fishes`、Aquatic Tag 与 ModList 适配。
- [x] Task 5：按服务器隔离的原子发现快照与失败保留。
- [x] Task 6：服务器启动、服务端 Tag 重载与停止清理生命周期。
- [x] Task 7：完整验证、独立代码审查与 HANDOFF 更新。
- [ ] 可选 Git 提交检查点：用户未授权 Git/GitHub 操作，因此均未执行。

实际验证：`test`、`javadoc`、`build` 均成功；20 个测试套件、51 个测试，0 failures/0 errors/0 skipped。Aquaculture 真实 Jar 客户端验收不属于本计划，尚未执行。

---

## 范围边界

本计划只交付自动发现纵向切片。以下内容另写后续计划：

- `-PfbmCompatModsDir` 与本地 Aquaculture Jar 的 `localRuntime` 接入；
- Smallmouth Bass 完整繁殖和 Minnow/Arapaima/Gar 烟雾验收；
- 导入/取消导入事务 API、网络 Payload、GUI 和 3D 预览；
- 只有真实 Jar 验收证明公共契约不足时才设计的可选 Adapter。

## 文件结构锁定

### 新建生产文件

- `src/main/java/com/fishbreedingmanager/discovery/CandidateConfidence.java`：HIGH/MEDIUM/LOW 发现置信度。
- `src/main/java/com/fishbreedingmanager/discovery/CompatibilityLevel.java`：UNVERIFIED/FULL/PARTIAL/UNSUPPORTED 运行兼容结论。
- `src/main/java/com/fishbreedingmanager/discovery/CandidateReason.java`：可解释且可独立计数的强弱信号。
- `src/main/java/com/fishbreedingmanager/discovery/LoadedModInfo.java`：从 ModList 提取的稳定元数据。
- `src/main/java/com/fishbreedingmanager/discovery/EntityDiscoveryInput.java`：Registry 边界转换后的纯输入。
- `src/main/java/com/fishbreedingmanager/discovery/CandidateEntity.java`：供后续命令/GUI 使用的候选输出。
- `src/main/java/com/fishbreedingmanager/discovery/ComponentCopies.java`：通过 Component Codec 深拷贝嵌套文本树，维持快照不可变性。
- `src/main/java/com/fishbreedingmanager/discovery/DetectedFishMod.java`：来源分组统计。
- `src/main/java/com/fishbreedingmanager/discovery/DiscoverySnapshot.java`：不可变完整发现快照。
- `src/main/java/com/fishbreedingmanager/discovery/FishDiscoveryEngine.java`：信号合并、置信度、去重、分组与排序。
- `src/main/java/com/fishbreedingmanager/discovery/MinecraftDiscoverySource.java`：读取 ModList、EntityType Registry 和 Tag。
- `src/main/java/com/fishbreedingmanager/discovery/FishDiscoveryManager.java`：按服务器持有并原子替换快照。
- `src/main/java/com/fishbreedingmanager/discovery/DiscoveryReloadResult.java`：结构化重建结果。

### 新建测试文件

- `src/test/java/com/fishbreedingmanager/discovery/DiscoverySnapshotTest.java`
- `src/test/java/com/fishbreedingmanager/discovery/FishDiscoveryEngineTest.java`
- `src/test/java/com/fishbreedingmanager/discovery/MinecraftDiscoverySourceTest.java`
- `src/test/java/com/fishbreedingmanager/discovery/FishDiscoveryManagerTest.java`

### 修改文件

- `src/main/java/com/fishbreedingmanager/FishBreedingManager.java`：注册启动、服务端 Tag 重载和停止清理入口。
- `.agent/HANDOFF.md`：同步实现结果、验证和剩余真实 Jar 验收。

---

### Task 1: 建立不可变发现数据模型

**Files:**
- Create: `src/main/java/com/fishbreedingmanager/discovery/CandidateConfidence.java`
- Create: `src/main/java/com/fishbreedingmanager/discovery/CompatibilityLevel.java`
- Create: `src/main/java/com/fishbreedingmanager/discovery/CandidateReason.java`
- Create: `src/main/java/com/fishbreedingmanager/discovery/LoadedModInfo.java`
- Create: `src/main/java/com/fishbreedingmanager/discovery/EntityDiscoveryInput.java`
- Create: `src/main/java/com/fishbreedingmanager/discovery/CandidateEntity.java`
- Create: `src/main/java/com/fishbreedingmanager/discovery/DetectedFishMod.java`
- Create: `src/main/java/com/fishbreedingmanager/discovery/DiscoverySnapshot.java`
- Test: `src/test/java/com/fishbreedingmanager/discovery/DiscoverySnapshotTest.java`

- [ ] **Step 1: 写快照深层不可变失败测试**

```java
package com.fishbreedingmanager.discovery;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DiscoverySnapshotTest {
    @Test
    void copiesAllCollectionsAndCandidateReasons() {
        ResourceLocation cod = ResourceLocation.parse("minecraft:cod");
        Set<CandidateReason> reasons = new LinkedHashSet<>(Set.of(CandidateReason.VANILLA_FISH));
        CandidateEntity candidate = new CandidateEntity(cod, Component.translatable("entity.minecraft.cod"),
                "minecraft", Component.literal("Minecraft"), CandidateConfidence.HIGH,
                CompatibilityLevel.UNVERIFIED, reasons);
        Map<ResourceLocation, CandidateEntity> candidates = new LinkedHashMap<>(Map.of(cod, candidate));
        DiscoverySnapshot snapshot = new DiscoverySnapshot(Map.of(), candidates, Set.of());

        reasons.add(CandidateReason.AQUATIC_TAG);
        candidates.clear();

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.candidates().put(cod, candidate));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.candidates().get(cod).reasons().add(CandidateReason.AQUATIC_TAG));
    }
}
```

- [ ] **Step 2: 运行测试并确认缺少模型类型**

Run:

```powershell
.\gradlew.bat test --tests com.fishbreedingmanager.discovery.DiscoverySnapshotTest
```

Expected: `compileTestJava` 因 `DiscoverySnapshot`、`CandidateEntity` 等类型不存在而失败。

- [ ] **Step 3: 实现最小模型与防御性复制**

枚举保持一项一个文件：

```java
public enum CandidateConfidence { HIGH, MEDIUM, LOW }
public enum CompatibilityLevel { UNVERIFIED, FULL, PARTIAL, UNSUPPORTED }
public enum CandidateReason {
    VANILLA_FISH, COMMON_FISH_TAG, AQUATIC_TAG,
    ENTITY_KEYWORD, SOURCE_KEYWORD, WATER_CATEGORY, IMPORTED
}
```

输入模型：

```java
public record LoadedModInfo(String modId, String displayName, String version) {}

public record EntityDiscoveryInput(
        ResourceLocation entityTypeId,
        Component displayName,
        String translationKey,
        MobCategory category,
        Set<CandidateReason> registryReasons
) {
    public EntityDiscoveryInput {
        registryReasons = Set.copyOf(registryReasons);
    }
}
```

输出模型：

```java
public record CandidateEntity(
        ResourceLocation entityTypeId,
        Component displayName,
        String sourceModId,
        Component sourceModName,
        CandidateConfidence confidence,
        CompatibilityLevel compatibility,
        Set<CandidateReason> reasons
) {
    public CandidateEntity {
        reasons = Set.copyOf(reasons);
    }
}

public record DetectedFishMod(
        String modId,
        Component displayName,
        String version,
        int registeredEntityCount,
        int fishCandidateCount
) {}

public record DiscoverySnapshot(
        Map<String, DetectedFishMod> detectedMods,
        Map<ResourceLocation, CandidateEntity> candidates,
        Set<ResourceLocation> unavailableImports
) {
    public static final DiscoverySnapshot EMPTY = new DiscoverySnapshot(Map.of(), Map.of(), Set.of());

    public DiscoverySnapshot {
        detectedMods = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(detectedMods));
        candidates = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(candidates));
        unavailableImports = Set.copyOf(unavailableImports);
    }
}
```

- [ ] **Step 4: 运行模型测试并确认通过**

Run:

```powershell
.\gradlew.bat test --tests com.fishbreedingmanager.discovery.DiscoverySnapshotTest
```

Expected: 1 个测试通过。

- [ ] **Step 5: 可选本地提交检查点**

只有用户另行授权 Git 后才执行：

```powershell
git add src/main/java/com/fishbreedingmanager/discovery src/test/java/com/fishbreedingmanager/discovery/DiscoverySnapshotTest.java
git commit -m "新增鱼类发现数据模型"
```

---

### Task 2: 用独立强弱信号计算置信度

**Files:**
- Create: `src/main/java/com/fishbreedingmanager/discovery/FishDiscoveryEngine.java`
- Test: `src/test/java/com/fishbreedingmanager/discovery/FishDiscoveryEngineTest.java`

- [ ] **Step 1: 写强信号、中置信度和单一弱信号失败测试**

```java
package com.fishbreedingmanager.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.Test;

class FishDiscoveryEngineTest {
    private final FishDiscoveryEngine engine = new FishDiscoveryEngine();

    @Test
    void classifiesStrongTwoIndependentWeakAndSingleWeakSignals() {
        EntityDiscoveryInput tagged = input("aquaculture:bayad", MobCategory.WATER_CREATURE,
                Set.of(CandidateReason.COMMON_FISH_TAG));
        EntityDiscoveryInput medium = input("example:river_fish", MobCategory.WATER_CREATURE,
                Set.of(CandidateReason.AQUATIC_TAG));
        EntityDiscoveryInput low = input("example:jellyfish", MobCategory.WATER_CREATURE,
                Set.of(CandidateReason.AQUATIC_TAG));

        DiscoverySnapshot snapshot = engine.discover(
                List.of(new LoadedModInfo("aquaculture", "Aquaculture 2", "2.7.21"),
                        new LoadedModInfo("example", "Example", "1")),
                List.of(tagged, medium, low), Set.of());

        assertEquals(CandidateConfidence.HIGH,
                snapshot.candidates().get(tagged.entityTypeId()).confidence());
        assertEquals(CandidateConfidence.MEDIUM,
                snapshot.candidates().get(medium.entityTypeId()).confidence());
        assertEquals(CandidateConfidence.LOW,
                snapshot.candidates().get(low.entityTypeId()).confidence());
        assertTrue(snapshot.candidates().get(medium.entityTypeId()).reasons()
                .containsAll(Set.of(CandidateReason.ENTITY_KEYWORD,
                        CandidateReason.AQUATIC_TAG, CandidateReason.WATER_CATEGORY)));
    }

    private static EntityDiscoveryInput input(String id, MobCategory category,
            Set<CandidateReason> reasons) {
        ResourceLocation key = ResourceLocation.parse(id);
        return new EntityDiscoveryInput(key, Component.literal(key.toString()),
                "entity." + key.getNamespace() + "." + key.getPath(), category, reasons);
    }
}
```

- [ ] **Step 2: 运行测试并确认引擎不存在**

Run:

```powershell
.\gradlew.bat test --tests com.fishbreedingmanager.discovery.FishDiscoveryEngineTest
```

Expected: `compileTestJava` 因 `FishDiscoveryEngine` 不存在而失败。

- [ ] **Step 3: 实现最小分类器**

`FishDiscoveryEngine` 使用以下常量和规则：

```java
private static final Set<CandidateReason> STRONG = Set.of(
        CandidateReason.VANILLA_FISH,
        CandidateReason.COMMON_FISH_TAG,
        CandidateReason.IMPORTED);
private static final Set<String> ENTITY_KEYWORDS = Set.of(
        "fish", "cod", "salmon", "trout", "bass", "carp", "tuna", "perch",
        "minnow", "herring", "halibut", "catfish", "gar", "piranha", "arapaima");

private static CandidateConfidence confidence(Set<CandidateReason> reasons) {
    if (reasons.stream().anyMatch(STRONG::contains)) {
        return CandidateConfidence.HIGH;
    }
    int groups = 0;
    if (reasons.contains(CandidateReason.ENTITY_KEYWORD)) groups++;
    if (reasons.contains(CandidateReason.SOURCE_KEYWORD)) groups++;
    if (reasons.contains(CandidateReason.AQUATIC_TAG)
            || reasons.contains(CandidateReason.WATER_CATEGORY)) groups++;
    return groups >= 2 ? CandidateConfidence.MEDIUM : CandidateConfidence.LOW;
}
```

实体 ID path 与翻译键只要任一命中就加入一次 `ENTITY_KEYWORD`；Mob 分类只在 `WATER_CREATURE`、`WATER_AMBIENT` 或 `UNDERGROUND_WATER_CREATURE` 时加入 `WATER_CATEGORY`；`AQUATIC_TAG` 与 `WATER_CATEGORY` 属于同一个水生属性组，不能互相凑成 MEDIUM；手动导入在分类前加入 `IMPORTED`。所有候选初始兼容等级固定为 `UNVERIFIED`。

- [ ] **Step 4: 运行分类测试并确认通过**

Run:

```powershell
.\gradlew.bat test --tests com.fishbreedingmanager.discovery.FishDiscoveryEngineTest
```

Expected: 分类测试全部通过，`jellyfish + aquatic` 仍为 LOW。

- [ ] **Step 5: 可选本地提交检查点**

只有用户另行授权 Git 后才执行：

```powershell
git add src/main/java/com/fishbreedingmanager/discovery/FishDiscoveryEngine.java src/test/java/com/fishbreedingmanager/discovery/FishDiscoveryEngineTest.java
git commit -m "实现鱼类候选置信度分类"
```

---

### Task 3: 完成去重、来源分组、稳定排序和失效导入

**Files:**
- Modify: `src/main/java/com/fishbreedingmanager/discovery/FishDiscoveryEngine.java`
- Modify: `src/test/java/com/fishbreedingmanager/discovery/FishDiscoveryEngineTest.java`

- [ ] **Step 1: 写来源回退、统计、排序与失效导入失败测试**

```java
@Test
void groupsByLoadedModOrNamespaceAndReportsUnavailableImports() {
    ResourceLocation bayad = ResourceLocation.parse("aquaculture:bayad");
    ResourceLocation unknown = ResourceLocation.parse("unknownmod:river_fish");
    ResourceLocation missing = ResourceLocation.parse("removed:old_fish");

    DiscoverySnapshot snapshot = engine.discover(
            List.of(new LoadedModInfo("aquaculture", "Aquaculture 2", "2.7.21")),
            List.of(input(unknown.toString(), MobCategory.WATER_CREATURE, Set.of()),
                    input(bayad.toString(), MobCategory.WATER_CREATURE,
                            Set.of(CandidateReason.COMMON_FISH_TAG))),
            Set.of(missing));

    assertEquals(List.of("aquaculture", "unknownmod"),
            snapshot.detectedMods().keySet().stream().toList());
    assertEquals("Aquaculture 2", snapshot.detectedMods().get("aquaculture").displayName().getString());
    assertEquals("unknownmod", snapshot.detectedMods().get("unknownmod").displayName().getString());
    assertEquals(1, snapshot.detectedMods().get("aquaculture").fishCandidateCount());
    assertEquals(Set.of(missing), snapshot.unavailableImports());
}
```

- [ ] **Step 2: 运行测试并确认分组行为尚未实现**

Run:

```powershell
.\gradlew.bat test --tests com.fishbreedingmanager.discovery.FishDiscoveryEngineTest
```

Expected: 新测试在 `detectedMods`、顺序或 `unavailableImports` 断言处失败。

- [ ] **Step 3: 实现确定性聚合**

`discover(...)` 必须：

```java
Map<String, LoadedModInfo> modsById = loadedMods.stream().collect(java.util.stream.Collectors.toMap(
        LoadedModInfo::modId, java.util.function.Function.identity(), (left, right) -> left));
Set<ResourceLocation> registeredIds = inputs.stream()
        .map(EntityDiscoveryInput::entityTypeId).collect(java.util.stream.Collectors.toSet());
Set<ResourceLocation> unavailable = importedEntities.stream()
        .filter(id -> !registeredIds.contains(id)).collect(java.util.stream.Collectors.toUnmodifiableSet());
```

候选先按 `sourceModId`、再按 `entityTypeId.path` 排序后放入 `LinkedHashMap`。来源统计包含该 Namespace 的全部 Registry 实体；`fishCandidateCount` 只统计 HIGH 与 MEDIUM。ModList 精确匹配时使用显示名和版本；没有匹配项时显示名回退为 Namespace、版本使用空字符串。来源也按 Mod ID 排序后放入 `LinkedHashMap`。

- [ ] **Step 4: 运行引擎测试并确认通过**

Run:

```powershell
.\gradlew.bat test --tests com.fishbreedingmanager.discovery.FishDiscoveryEngineTest
```

Expected: 分类、分组、稳定排序和失效导入测试全部通过。

- [ ] **Step 5: 可选本地提交检查点**

只有用户另行授权 Git 后才执行：

```powershell
git add src/main/java/com/fishbreedingmanager/discovery/FishDiscoveryEngine.java src/test/java/com/fishbreedingmanager/discovery/FishDiscoveryEngineTest.java
git commit -m "完善鱼类候选来源分组"
```

---

### Task 4: 适配 Minecraft Registry、公共 Tag 与 ModList

**Files:**
- Create: `src/main/java/com/fishbreedingmanager/discovery/MinecraftDiscoverySource.java`
- Test: `src/test/java/com/fishbreedingmanager/discovery/MinecraftDiscoverySourceTest.java`

- [ ] **Step 1: 写原版 Registry 冒烟失败测试**

```java
package com.fishbreedingmanager.discovery;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class MinecraftDiscoverySourceTest {
    @Test
    void capturesVanillaCodWithoutInstantiatingEntities() {
        MinecraftDiscoverySource source = new MinecraftDiscoverySource();
        EntityDiscoveryInput cod = source.captureEntities(Set.of()).stream()
                .filter(input -> input.entityTypeId().equals(ResourceLocation.parse("minecraft:cod")))
                .findFirst().orElseThrow();

        assertTrue(cod.registryReasons().contains(CandidateReason.VANILLA_FISH));
        assertTrue(cod.registryReasons().contains(CandidateReason.AQUATIC_TAG));
    }
}
```

- [ ] **Step 2: 运行测试并确认 Registry 适配器不存在**

Run:

```powershell
.\gradlew.bat test --tests com.fishbreedingmanager.discovery.MinecraftDiscoverySourceTest
```

Expected: `compileTestJava` 因 `MinecraftDiscoverySource` 不存在而失败。

- [ ] **Step 3: 实现 Registry 与 Tag 捕获**

`MinecraftDiscoverySource` 定义稳定 Tag 与原版白名单：

```java
private static final TagKey<EntityType<?>> COMMON_FISH = TagKey.create(
        Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "fish"));
private static final TagKey<EntityType<?>> COMMON_FISHES = TagKey.create(
        Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "fishes"));
private static final TagKey<EntityType<?>> AQUATIC = TagKey.create(
        Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace("aquatic"));
private static final Set<ResourceLocation> VANILLA_FISH = Set.of(
        ResourceLocation.withDefaultNamespace("cod"),
        ResourceLocation.withDefaultNamespace("salmon"),
        ResourceLocation.withDefaultNamespace("tropical_fish"),
        ResourceLocation.withDefaultNamespace("pufferfish"));
```

`captureEntities(...)` 单次遍历 `BuiltInRegistries.ENTITY_TYPE.entrySet()`，通过 Holder Tag 关系合并 `COMMON_FISH_TAG` 和 `AQUATIC_TAG`，读取 `EntityType#getDescriptionId()`、`EntityType#getCategory()`，使用 `Component.translatable(...)` 构建显示组件。方法不得调用 `EntityType#create`。

`captureLoadedMods()` 使用 `ModList.get().getMods()` 映射为：

```java
new LoadedModInfo(info.getModId(), info.getDisplayName(), info.getVersion().toString())
```

并按 `modId` 排序、按 ID 去重。

- [ ] **Step 4: 运行 Registry 适配器和引擎测试**

Run:

```powershell
.\gradlew.bat test --tests com.fishbreedingmanager.discovery.MinecraftDiscoverySourceTest --tests com.fishbreedingmanager.discovery.FishDiscoveryEngineTest
```

Expected: 原版 cod 同时具备 `VANILLA_FISH` 和 `AQUATIC_TAG`，全部测试通过。

- [ ] **Step 5: 可选本地提交检查点**

只有用户另行授权 Git 后才执行：

```powershell
git add src/main/java/com/fishbreedingmanager/discovery/MinecraftDiscoverySource.java src/test/java/com/fishbreedingmanager/discovery/MinecraftDiscoverySourceTest.java
git commit -m "接入实体注册表和模组扫描"
```

---

### Task 5: 原子发布发现快照并保留失败前状态

**Files:**
- Create: `src/main/java/com/fishbreedingmanager/discovery/DiscoveryReloadResult.java`
- Create: `src/main/java/com/fishbreedingmanager/discovery/FishDiscoveryManager.java`
- Test: `src/test/java/com/fishbreedingmanager/discovery/FishDiscoveryManagerTest.java`

- [ ] **Step 1: 写成功替换、失败保留和服务器隔离失败测试**

```java
package com.fishbreedingmanager.discovery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FishDiscoveryManagerTest {
    @Test
    void replacesOnlyAfterSuccessfulCompleteBuild() {
        FishDiscoveryManager manager = new FishDiscoveryManager();
        DiscoverySnapshot first = new DiscoverySnapshot(java.util.Map.of(), java.util.Map.of(), java.util.Set.of());

        DiscoveryReloadResult success = manager.rebuild(() -> first);
        DiscoveryReloadResult failure = manager.rebuild(() -> { throw new IllegalStateException("broken tags"); });

        assertTrue(success.success());
        assertFalse(failure.success());
        assertSame(first, manager.snapshot());
    }
}
```

- [ ] **Step 2: 运行测试并确认管理器不存在**

Run:

```powershell
.\gradlew.bat test --tests com.fishbreedingmanager.discovery.FishDiscoveryManagerTest
```

Expected: `compileTestJava` 因管理器和结果类型不存在而失败。

- [ ] **Step 3: 实现最小原子管理器**

```java
public record DiscoveryReloadResult(boolean success, int candidateCount, String error) {
    public static DiscoveryReloadResult success(int candidateCount) {
        return new DiscoveryReloadResult(true, candidateCount, "");
    }

    public static DiscoveryReloadResult failure(String error) {
        return new DiscoveryReloadResult(false, 0, error);
    }
}
```

`FishDiscoveryManager` 使用 `ConcurrentHashMap<MinecraftServer, FishDiscoveryManager>` 管理服务器隔离实例，内部以 `volatile DiscoverySnapshot snapshot = DiscoverySnapshot.EMPTY` 保存状态。包级 `rebuild(Supplier<DiscoverySnapshot>)` 先完整构建局部变量，再赋值并返回成功；捕获 `RuntimeException` 时返回失败且不修改旧引用。公共 `reload(server)` 从 `WorldBreedingData.get(server).getImportedEntities()` 读取持久化导入集合，不能依赖可能因规则校验失败而未安装的规则运行快照；随后组合 `MinecraftDiscoverySource` 与 `FishDiscoveryEngine`。`remove(server)` 删除已停止服务器实例。

- [ ] **Step 4: 运行管理器和全部 discovery 测试**

Run:

```powershell
.\gradlew.bat test --tests 'com.fishbreedingmanager.discovery.*'
```

Expected: 所有 discovery 测试通过，失败重建保持旧对象引用。

- [ ] **Step 5: 可选本地提交检查点**

只有用户另行授权 Git 后才执行：

```powershell
git add src/main/java/com/fishbreedingmanager/discovery/DiscoveryReloadResult.java src/main/java/com/fishbreedingmanager/discovery/FishDiscoveryManager.java src/test/java/com/fishbreedingmanager/discovery/FishDiscoveryManagerTest.java
git commit -m "实现鱼类发现快照原子发布"
```

---

### Task 6: 接入服务器启动、Tag 重载和停止生命周期

**Files:**
- Modify: `src/main/java/com/fishbreedingmanager/FishBreedingManager.java`
- Modify: `src/test/java/com/fishbreedingmanager/discovery/FishDiscoveryManagerTest.java`

- [ ] **Step 1: 写服务器管理器移除失败测试**

```java
@Test
void removeDiscardsServerBoundManager() {
    net.minecraft.server.MinecraftServer server = org.mockito.Mockito.mock(net.minecraft.server.MinecraftServer.class);
    FishDiscoveryManager first = FishDiscoveryManager.get(server);

    FishDiscoveryManager.remove(server);

    org.junit.jupiter.api.Assertions.assertNotSame(first, FishDiscoveryManager.get(server));
    FishDiscoveryManager.remove(server);
}
```

- [ ] **Step 2: 运行测试并确认 remove 行为尚未满足**

Run:

```powershell
.\gradlew.bat test --tests com.fishbreedingmanager.discovery.FishDiscoveryManagerTest
```

Expected: 新测试因 `get/remove` 尚未存在或未清理而失败。

- [ ] **Step 3: 在 Mod 入口接入生命周期**

在 `FishBreedingManager` 增加：

```java
@SubscribeEvent
private void onServerStarted(ServerStartedEvent event) {
    rebuildDiscovery(event.getServer(), "server-started");
}

@SubscribeEvent
private void onTagsUpdated(TagsUpdatedEvent event) {
    if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
        return;
    }
    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
    if (server == null || server.getLevel(Level.OVERWORLD) == null) {
        return;
    }
    server.execute(() -> rebuildDiscovery(server, "server-tags-updated"));
}

private static void rebuildDiscovery(MinecraftServer server, String trigger) {
    DiscoveryReloadResult result = FishDiscoveryManager.get(server).reload(server);
    if (result.success()) {
        LOGGER.info("FBM fish discovery rebuilt: trigger={}, candidates={}",
                trigger, result.candidateCount());
    } else {
        LOGGER.error("FBM fish discovery failed; previous snapshot retained: trigger={}, reason={}",
                trigger, result.error());
    }
}
```

在现有 `onServerStopping` 尾部调用：

```java
FishDiscoveryManager.remove(event.getServer());
```

并补齐 `ServerStartedEvent`、`TagsUpdatedEvent`、`ServerLifecycleHooks`、`Level` 和 discovery 类型 import。初始构建使用 `ServerStartedEvent`，保证规则快照与主世界 SavedData 已就绪；Tag 事件只处理 `SERVER_DATA_LOAD`，忽略客户端同步。

- [ ] **Step 4: 运行 discovery 测试和完整回归**

Run:

```powershell
.\gradlew.bat test --tests 'com.fishbreedingmanager.discovery.*'
.\gradlew.bat test
```

Expected: discovery 测试全部通过；完整测试数从现有 38 个增加且 0 failures/0 errors。

- [ ] **Step 5: 编译生产代码**

Run:

```powershell
.\gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`，无 NeoForge API 签名错误。

- [ ] **Step 6: 可选本地提交检查点**

只有用户另行授权 Git 后才执行：

```powershell
git add src/main/java/com/fishbreedingmanager/FishBreedingManager.java src/main/java/com/fishbreedingmanager/discovery src/test/java/com/fishbreedingmanager/discovery
git commit -m "接入鱼类自动发现生命周期"
```

---

### Task 7: 更新交接并锁定下一轮真实 Mod 验收

**Files:**
- Modify: `.agent/HANDOFF.md`

- [ ] **Step 1: 汇总实际验证事实**

记录实际测试数量、执行命令、成功或失败输出；没有执行开发客户端时必须明确写“未执行”，不得把单元测试视为 Aquaculture 真实兼容通过。

- [ ] **Step 2: 更新当前状态而非追加流水**

将 HANDOFF 中关于扫描功能尚未落地的旧描述替换为实际完成范围，并把下一步调整为：

```text
通过可选 fbmCompatModsDir/localRuntime 加载 Aquaculture 2.7.21，
在开发客户端核对 28 个 c:fish 候选，并执行 Smallmouth Bass 完整繁殖验收。
```

- [ ] **Step 3: 最终静态自检**

Run:

```powershell
$markers = @('TO' + 'DO', 'TB' + 'D', 'FIX' + 'ME', '待' + '定')
rg -n ($markers -join '|') docs/superpowers/plans/2026-08-29-fish-mod-auto-discovery-implementation.md .agent/HANDOFF.md
```

Expected: 没有未决占位内容。

---

## 最终验收矩阵

| 能力 | 自动化证据 |
|---|---|
| 强/弱信号置信度 | `FishDiscoveryEngineTest` |
| `c:fish`/`c:fishes` 合并 | `MinecraftDiscoverySourceTest` 与后续真实 Jar 验收 |
| Mod/Namespace 来源回退 | `FishDiscoveryEngineTest` |
| 全 Registry 低置信度搜索基础 | `FishDiscoveryEngineTest` |
| 失效导入报告 | `FishDiscoveryEngineTest` |
| 快照原子替换与失败保留 | `FishDiscoveryManagerTest` |
| 服务器实例隔离与停止清理 | `FishDiscoveryManagerTest` |
| 不创建实体 | `MinecraftDiscoverySource` 代码审查与 Registry 冒烟测试 |
| 不创建/启用规则 | discovery 包不依赖规则写入服务；完整 P0 回归 |
| Aquaculture 零生产硬依赖 | `compileJava` 与源码 import 扫描 |

## 实施自检

- Spec 第 5.2、5.3、5.4、5.5、6.1、6.2、6.3 的发现能力分别由 Task 1–6 覆盖。
- Spec 第 7、8.2–8.6 的本地依赖与真实游戏验收不属于本计划，已经明确路由到下一份计划。
- `CandidateReason` 使用 `ENTITY_KEYWORD` 与 `SOURCE_KEYWORD`，并把 `AQUATIC_TAG/WATER_CATEGORY` 合并为一个水生属性组，与 Spec 修订后的独立弱信号语义一致。
- 所有新生产方法都在对应任务中先有失败测试；生命周期注解 wiring 通过编译和完整回归验证。
- Git 检查点仅作为计划中的可选边界，执行仍需用户明确授权。
