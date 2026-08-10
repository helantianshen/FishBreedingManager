# Fish Breeding Manager P0 B-lite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留现有 NeoForge 1.21.1 架构的基础上完成 B-lite 稳定化，使管理员能够事务式修改规则，并让 cod 的 Love、配对、后代、冷却、幼体和热更新流程具备可测试、可验收的实现。

**Architecture:** `WorldBreedingService` 统一规则校验、SavedData 提交和 Snapshot 原子替换；`ActiveLoveIndex` 负责实体载入后的 Love 恢复；`ChildSpawner` 返回结构化生成结果；`BreedingController` 只负责编排配对与成功提交。NeoForge Attachment 仍是实体状态权威，SavedData 仍是存档级规则权威。

**Tech Stack:** Java 21、Minecraft 1.21.1、NeoForge 21.1.244、ModDevGradle 2.0.143、JUnit 5、Mockito 5、Brigadier、NeoForge Data Attachment/SavedData/Payload API。

**Design:** `docs/superpowers/specs/2026-08-11-p0-b-lite-design.md`

---

## 文件结构锁定

### 新建生产文件

- `src/main/java/com/fishbreedingmanager/breeding/RuleValidationResult.java`：结构化规则校验结果。
- `src/main/java/com/fishbreedingmanager/breeding/RuleValidator.java`：Registry、Food 和时间校验。
- `src/main/java/com/fishbreedingmanager/breeding/ParsedFood.java`：命令 Food 解析结果。
- `src/main/java/com/fishbreedingmanager/breeding/FoodSpecParser.java`：逗号分隔 Item/Tag 解析。
- `src/main/java/com/fishbreedingmanager/breeding/RuleUpdateResult.java`：规则事务更新结果。
- `src/main/java/com/fishbreedingmanager/breeding/WorldBreedingService.java`：规则更新唯一入口。
- `src/main/java/com/fishbreedingmanager/breeding/ActiveLoveIndex.java`：每 Level 活跃 Love UUID 索引。
- `src/main/java/com/fishbreedingmanager/breeding/ChildSpawnStatus.java`：后代生成状态。
- `src/main/java/com/fishbreedingmanager/breeding/ChildSpawnResult.java`：后代生成结果。
- `src/main/java/com/fishbreedingmanager/breeding/ChildSpawner.java`：同 EntityType 后代创建。
- `src/main/java/com/fishbreedingmanager/event/EntityLifecycleHandler.java`：实体加入、离开和玩家追踪同步。
- `src/main/resources/assets/fishbreedingmanager/lang/zh_cn.json`：中文命令文本。
- `docs/testing/P0_Cod_Acceptance.md`：受控开发客户端验收记录。

### 新建测试文件

- `src/test/java/com/fishbreedingmanager/TestEnvironmentSmokeTest.java`
- `src/test/java/com/fishbreedingmanager/breeding/BreedingStateTest.java`
- `src/test/java/com/fishbreedingmanager/client/ClientJuvenileSyncTest.java`
- `src/test/java/com/fishbreedingmanager/network/JuvenileStatePayloadTest.java`
- `src/test/java/com/fishbreedingmanager/breeding/FoodSpecParserTest.java`
- `src/test/java/com/fishbreedingmanager/breeding/RuleValidatorTest.java`
- `src/test/java/com/fishbreedingmanager/breeding/WorldBreedingServiceTest.java`
- `src/test/java/com/fishbreedingmanager/breeding/ActiveLoveIndexTest.java`
- `src/test/java/com/fishbreedingmanager/breeding/ChildSpawnerTest.java`
- `src/test/java/com/fishbreedingmanager/breeding/BreedingControllerOutcomeTest.java`
- `src/test/java/com/fishbreedingmanager/command/FBMCommandsTest.java`

### 修改文件

- `build.gradle`
- `src/main/java/com/fishbreedingmanager/FishBreedingManager.java`
- `src/main/java/com/fishbreedingmanager/FishBreedingManagerClient.java`
- `src/main/java/com/fishbreedingmanager/attachment/ModAttachments.java`
- `src/main/java/com/fishbreedingmanager/breeding/BreedingController.java`
- `src/main/java/com/fishbreedingmanager/breeding/BreedingRule.java`
- `src/main/java/com/fishbreedingmanager/breeding/BreedingRuleManager.java`
- `src/main/java/com/fishbreedingmanager/breeding/BreedingRuleSnapshot.java`
- `src/main/java/com/fishbreedingmanager/breeding/BreedingState.java`
- `src/main/java/com/fishbreedingmanager/breeding/DefaultRules.java`
- `src/main/java/com/fishbreedingmanager/breeding/ReloadResult.java`
- `src/main/java/com/fishbreedingmanager/client/ClientJuvenileSync.java`
- `src/main/java/com/fishbreedingmanager/client/JuvenileRenderHandler.java`
- `src/main/java/com/fishbreedingmanager/command/FBMCommands.java`
- `src/main/java/com/fishbreedingmanager/event/EntityInteractionHandler.java`
- `src/main/java/com/fishbreedingmanager/network/JuvenileStatePayload.java`
- `src/main/java/com/fishbreedingmanager/network/ModNetworking.java`
- `src/main/java/com/fishbreedingmanager/persistence/WorldBreedingData.java`
- `src/main/resources/assets/fishbreedingmanager/lang/en_us.json`
- `docs/Project_Analysis_and_Summary.md`

---

### Task 0: 固化当前 FBM 基线

**Files:**
- Commit existing: `README.md`
- Commit existing: `docs/Fish_Breeding_Manager_Requirements.md`
- Commit existing: `docs/Project_Analysis_and_Summary.md`
- Commit existing: `gradle.properties`
- Commit existing: `gradle/wrapper/*`
- Commit existing: `gradlew`, `gradlew.bat`
- Commit existing: `src/main/**`

- [ ] **Step 1: 验证当前未修改基线仍可编译**

Run:

```powershell
.\gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 2: 只暂存项目基线，不暂存本地 MCP/IDE 配置**

Run:

```powershell
git add README.md docs/Fish_Breeding_Manager_Requirements.md docs/Project_Analysis_and_Summary.md gradle.properties gradle/wrapper gradlew gradlew.bat src/main
git diff --cached --stat
```

Expected: 只包含 MDK 向 FBM 的工程改造、需求文档和现有 P0 源码，不包含 `.mcp.json`、`.claude/`、`.idea/` 或 `run/`。

- [ ] **Step 3: 提交可回退基线**

```powershell
git commit -m "feat: establish Fish Breeding Manager P0 baseline"
```

Expected: 工作区仍可保留未跟踪的本地配置，但全部现有 FBM 源码已有可回退提交。

---

### Task 1: 建立 NeoForge JUnit 测试环境

**Files:**
- Modify: `build.gradle`
- Create: `src/test/java/com/fishbreedingmanager/TestEnvironmentSmokeTest.java`

- [ ] **Step 1: 先写测试环境冒烟测试**

```java
package com.fishbreedingmanager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 ModDevGradle 单元测试环境已经加载 Minecraft 与 NeoForge 注册表。
 */
final class TestEnvironmentSmokeTest {
    /**
     * 确认测试进程能够解析原版鳕鱼 EntityType。
     */
    @Test
    void 应加载原版实体注册表() {
        assertEquals(ResourceLocation.parse("minecraft:cod"),
                BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.COD));
    }
}
```

- [ ] **Step 2: 运行测试并确认测试依赖尚未配置**

Run:

```powershell
.\gradlew.bat test --tests com.fishbreedingmanager.TestEnvironmentSmokeTest
```

Expected: `compileTestJava` 失败，错误包含 `package org.junit.jupiter.api does not exist`。

- [ ] **Step 3: 在 `build.gradle` 配置 JUnit、Mockito 与 ModDevGradle unitTest**

在现有 `dependencies` 中加入：

```groovy
testImplementation platform('org.junit:junit-bom:5.11.4')
testImplementation 'org.junit.jupiter:junit-jupiter'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
testImplementation 'org.mockito:mockito-core:5.14.2'
```

在 `neoForge` 块内、`mods` 块之后加入：

```groovy
unitTest {
    enable()
    testedMod = mods."${mod_id}"
}
```

在文件末尾加入：

```groovy
tasks.named('test', Test).configure {
    useJUnitPlatform()
}
```

- [ ] **Step 4: 验证测试环境**

Run:

```powershell
.\gradlew.bat test --tests com.fishbreedingmanager.TestEnvironmentSmokeTest
```

Expected: 1 个测试通过，输出不含类加载异常。

- [ ] **Step 5: 提交测试基础设施**

```powershell
git add build.gradle src/test/java/com/fishbreedingmanager/TestEnvironmentSmokeTest.java
git commit -m "test: enable NeoForge JUnit environment"
```

---

### Task 2: 冻结 BreedingState 持久化与瞬时幼体显示语义

**Files:**
- Modify: `src/main/java/com/fishbreedingmanager/breeding/BreedingState.java`
- Modify: `src/main/java/com/fishbreedingmanager/network/JuvenileStatePayload.java`
- Modify: `src/main/java/com/fishbreedingmanager/client/ClientJuvenileSync.java`
- Modify: `src/main/java/com/fishbreedingmanager/client/JuvenileRenderHandler.java`
- Create: `src/test/java/com/fishbreedingmanager/breeding/BreedingStateTest.java`
- Create: `src/test/java/com/fishbreedingmanager/network/JuvenileStatePayloadTest.java`
- Create: `src/test/java/com/fishbreedingmanager/client/ClientJuvenileSyncTest.java`

- [ ] **Step 1: 写 Love 恢复和 mate 非持久化失败测试**

```java
package com.fishbreedingmanager.breeding;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证实体繁殖状态的计时边界和存档恢复语义。
 */
final class BreedingStateTest {
    @Test
    void 重新载入时保留有效Love但清除旧配偶() {
        BreedingState state = new BreedingState();
        state.enterLove(100L, 600L);
        state.setMate(UUID.randomUUID());

        assertTrue(state.prepareForLevelJoin(200L));
        assertTrue(state.isInLove(200L));
        assertNull(state.getMate());
    }

    @Test
    void 重新载入时清理已经过期的Love() {
        BreedingState state = new BreedingState();
        state.enterLove(100L, 20L);

        assertFalse(state.prepareForLevelJoin(120L));
        assertFalse(state.isInLove(120L));
    }

    @Test
    void 新编码数据不写入mate且旧字段被忽略() {
        BreedingState state = new BreedingState();
        state.enterLove(100L, 600L);
        state.setMate(UUID.randomUUID());

        JsonObject encoded = BreedingState.CODEC.encodeStart(JsonOps.INSTANCE, state)
                .getOrThrow().getAsJsonObject();
        assertFalse(encoded.has("mate"));

        encoded.addProperty("mate", UUID.randomUUID().toString());
        BreedingState decoded = BreedingState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertNull(decoded.getMate());
        assertTrue(decoded.isInLove(200L));
    }

    @Test
    void 幼体在成年tick瞬间从一半尺寸恢复() {
        BreedingState state = new BreedingState();
        state.markJuvenile(100L, 20L);

        assertEquals(0.5F, state.visualScale(119L));
        assertEquals(1.0F, state.visualScale(120L));
    }
}
```

- [ ] **Step 2: 写 Payload adultAt 与客户端固定缩放失败测试**

```java
package com.fishbreedingmanager.network;

import com.fishbreedingmanager.breeding.BreedingState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 防止幼体同步包再次把出生时间误当作成年时间。
 */
final class JuvenileStatePayloadTest {
    @Test
    void 应从服务端状态读取成年时间() {
        UUID entityId = UUID.randomUUID();
        BreedingState state = new BreedingState();
        state.markJuvenile(1_000L, 1_200L);

        JuvenileStatePayload payload = JuvenileStatePayload.fromState(entityId, state);

        assertEquals(2_200L, payload.adultAt());
    }
}
```

```java
package com.fishbreedingmanager.client;

import com.fishbreedingmanager.network.JuvenileStatePayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证客户端只执行固定 50% 幼体缩放，不进行平滑插值。
 */
final class ClientJuvenileSyncTest {
    @AfterEach
    void 清理缓存() {
        ClientJuvenileSync.clear();
    }

    @Test
    void 成年前固定一半尺寸且到期立即恢复() {
        UUID entityId = UUID.randomUUID();
        ClientJuvenileSync.remember(new JuvenileStatePayload(entityId, 200L));

        assertEquals(0.5F, ClientJuvenileSync.scaleFor(entityId, 199L));
        assertEquals(1.0F, ClientJuvenileSync.scaleFor(entityId, 200L));
    }
}
```

- [ ] **Step 3: 运行三个测试并确认按预期失败**

Run:

```powershell
.\gradlew.bat test --tests '*BreedingStateTest' --tests '*JuvenileStatePayloadTest' --tests '*ClientJuvenileSyncTest'
```

Expected: 因 `prepareForLevelJoin`、`fromState`、`remember`、`clear` 不存在，以及 Payload 构造参数仍包含 `growthTicks` 而失败。

- [ ] **Step 4: 最小实现新的状态与 Payload 语义**

`BreedingState.CODEC` 只编码五个持久字段：

```java
public static final Codec<BreedingState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.fieldOf("in_love").forGetter(state -> state.inLove),
        Codec.LONG.fieldOf("love_until").forGetter(state -> state.loveUntil),
        Codec.LONG.fieldOf("cooldown_until").forGetter(state -> state.cooldownUntil),
        Codec.BOOL.fieldOf("juvenile").forGetter(state -> state.juvenile),
        Codec.LONG.fieldOf("adult_at").forGetter(state -> state.adultAt)
).apply(instance, BreedingState::new));

private BreedingState(boolean inLove, long loveUntil, long cooldownUntil,
                      boolean juvenile, long adultAt) {
    this.inLove = inLove;
    this.loveUntil = loveUntil;
    this.cooldownUntil = cooldownUntil;
    this.juvenile = juvenile;
    this.adultAt = adultAt;
}

/**
 * 在实体重新加入服务端 Level 时结算计时器并丢弃旧配偶关系。
 *
 * @param now 当前世界 game time
 * @return Love 是否仍有效；有效时调用方应重新加入活跃索引
 */
public boolean prepareForLevelJoin(long now) {
    tickTimers(now);
    mate = null;
    return isInLove(now);
}
```

`JuvenileStatePayload` 改为：

```java
public record JuvenileStatePayload(UUID entityUuid, long adultAt) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<JuvenileStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    FishBreedingManager.MOD_ID, "juvenile_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JuvenileStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, payload -> payload.entityUuid().toString(),
                    ByteBufCodecs.VAR_LONG, JuvenileStatePayload::adultAt,
                    (uuid, adultAt) -> new JuvenileStatePayload(UUID.fromString(uuid), adultAt));

    /**
     * 从服务端 Attachment 权威状态创建同步包。
     *
     * @param entityUuid 幼体实体 UUID
     * @param state 幼体的服务端繁殖状态
     * @return 使用真实 {@link BreedingState#getAdultAt()} 的 Payload
     */
    public static JuvenileStatePayload fromState(UUID entityUuid, BreedingState state) {
        return new JuvenileStatePayload(entityUuid, state.getAdultAt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

`ClientJuvenileSync` 将缓存改为 `ConcurrentHashMap<UUID, Long>`，并实现：

```java
static void remember(JuvenileStatePayload payload) {
    JUVENILES.put(payload.entityUuid(), payload.adultAt());
}

public static void handleJuvenileState(JuvenileStatePayload payload, IPayloadContext context) {
    remember(payload);
}

public static float scaleFor(UUID entityUuid, long now) {
    Long adultAt = JUVENILES.get(entityUuid);
    if (adultAt == null) {
        return 1.0F;
    }
    if (now >= adultAt) {
        JUVENILES.remove(entityUuid);
        return 1.0F;
    }
    return 0.5F;
}

public static void clear() {
    JUVENILES.clear();
}
```

- [ ] **Step 5: 更新渲染注释并验证测试变绿**

删除 `JuvenileRenderHandler` 中“平滑长大”的描述，保留 Pre/Push 与 Post/Pop 对称逻辑。

Run:

```powershell
.\gradlew.bat test --tests '*BreedingStateTest' --tests '*JuvenileStatePayloadTest' --tests '*ClientJuvenileSyncTest'
```

Expected: 全部通过。

- [ ] **Step 6: 提交状态语义**

```powershell
git add src/main/java/com/fishbreedingmanager/breeding/BreedingState.java src/main/java/com/fishbreedingmanager/network/JuvenileStatePayload.java src/main/java/com/fishbreedingmanager/client/ClientJuvenileSync.java src/main/java/com/fishbreedingmanager/client/JuvenileRenderHandler.java src/test/java/com/fishbreedingmanager/breeding/BreedingStateTest.java src/test/java/com/fishbreedingmanager/network/JuvenileStatePayloadTest.java src/test/java/com/fishbreedingmanager/client/ClientJuvenileSyncTest.java
git commit -m "fix: preserve love and correct juvenile timing"
```

---

### Task 3: 实现 Food 解析与完整规则校验

**Files:**
- Create: `src/main/java/com/fishbreedingmanager/breeding/ParsedFood.java`
- Create: `src/main/java/com/fishbreedingmanager/breeding/FoodSpecParser.java`
- Create: `src/main/java/com/fishbreedingmanager/breeding/RuleValidationResult.java`
- Create: `src/main/java/com/fishbreedingmanager/breeding/RuleValidator.java`
- Create: `src/test/java/com/fishbreedingmanager/breeding/FoodSpecParserTest.java`
- Create: `src/test/java/com/fishbreedingmanager/breeding/RuleValidatorTest.java`

- [ ] **Step 1: 写 Food 解析失败测试**

```java
package com.fishbreedingmanager.breeding;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证管理员命令中 Item 与 Item Tag 的确定性解析。
 */
final class FoodSpecParserTest {
    @Test
    void 解析多个物品和标签() {
        ParsedFood parsed = FoodSpecParser.parse(
                "minecraft:kelp,minecraft:seagrass,#minecraft:planks");

        assertEquals(2, parsed.itemIds().size());
        assertEquals(ResourceLocation.parse("minecraft:kelp"), parsed.itemIds().getFirst());
        assertEquals(1, parsed.tagIds().size());
        assertEquals(ResourceLocation.parse("minecraft:planks"), parsed.tagIds().getFirst());
    }

    @Test
    void 拒绝空项和非法资源ID() {
        assertThrows(IllegalArgumentException.class,
                () -> FoodSpecParser.parse("minecraft:kelp,,minecraft:seagrass"));
        assertThrows(IllegalArgumentException.class,
                () -> FoodSpecParser.parse("Minecraft:Kelp"));
    }
}
```

- [ ] **Step 2: 写规则校验失败测试**

```java
package com.fishbreedingmanager.breeding;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证无效 Registry ID 和计时参数不能进入运行时快照。
 */
final class RuleValidatorTest {
    private final RuleValidator validator = new RuleValidator();

    @Test
    void 接受已注册的鳕鱼和海带规则() {
        BreedingRule rule = new BreedingRule(
                ResourceLocation.parse("minecraft:cod"),
                List.of(ResourceLocation.parse("minecraft:kelp")),
                List.of(), 600, 1200, true);

        assertTrue(validator.validate(rule).valid());
    }

    @Test
    void 拒绝未知实体未知物品空食物和负时间() {
        BreedingRule rule = new BreedingRule(
                ResourceLocation.parse("fishbreedingmanager:missing_entity"),
                List.of(ResourceLocation.parse("fishbreedingmanager:missing_item")),
                List.of(), -1, -1, true);

        RuleValidationResult result = validator.validate(rule);

        assertFalse(result.valid());
        assertTrue(result.errors().size() >= 3);
    }

    @Test
    void 拒绝没有任何食物来源的规则() {
        BreedingRule rule = new BreedingRule(
                ResourceLocation.parse("minecraft:cod"),
                List.of(), List.of(), 600, 1200, true);

        assertFalse(validator.validate(rule).valid());
    }
}
```

- [ ] **Step 3: 运行测试并确认类型尚不存在**

```powershell
.\gradlew.bat test --tests '*FoodSpecParserTest' --tests '*RuleValidatorTest'
```

Expected: `ParsedFood`、`FoodSpecParser`、`RuleValidator` 未定义导致编译失败。

- [ ] **Step 4: 实现不可变解析结果与解析器**

```java
public record ParsedFood(List<ResourceLocation> itemIds, List<ResourceLocation> tagIds) {
    public ParsedFood {
        itemIds = List.copyOf(itemIds);
        tagIds = List.copyOf(tagIds);
    }
}
```

```java
public final class FoodSpecParser {
    private FoodSpecParser() {
    }

    public static ParsedFood parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("繁殖物品不能为空");
        }
        List<ResourceLocation> items = new ArrayList<>();
        List<ResourceLocation> tags = new ArrayList<>();
        for (String raw : input.split(",", -1)) {
            String token = raw.trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException("繁殖物品列表不能包含空项");
            }
            boolean tag = token.startsWith("#");
            String idText = tag ? token.substring(1) : token;
            ResourceLocation id = ResourceLocation.tryParse(idText);
            if (id == null) {
                throw new IllegalArgumentException("无效的资源 ID: " + token);
            }
            (tag ? tags : items).add(id);
        }
        return new ParsedFood(items, tags);
    }
}
```

- [ ] **Step 5: 实现结构化校验结果与校验器**

```java
public record RuleValidationResult(boolean valid, List<String> errors) {
    public RuleValidationResult {
        errors = List.copyOf(errors);
    }

    public static RuleValidationResult success() {
        return new RuleValidationResult(true, List.of());
    }

    public static RuleValidationResult failure(List<String> errors) {
        return new RuleValidationResult(false, errors);
    }
}
```

`RuleValidator.validate` 必须按以下顺序累积全部错误，不能遇到第一项错误就提前返回：

```java
public RuleValidationResult validate(BreedingRule rule) {
    List<String> errors = new ArrayList<>();
    if (BuiltInRegistries.ENTITY_TYPE.getOptional(rule.entityTypeId()).isEmpty()) {
        errors.add("未知实体 ID: " + rule.entityTypeId());
    }
    if (rule.breedingItemIds().isEmpty() && rule.breedingTagIds().isEmpty()) {
        errors.add("至少需要一个繁殖物品或物品标签");
    }
    for (ResourceLocation itemId : rule.breedingItemIds()) {
        if (BuiltInRegistries.ITEM.getOptional(itemId).isEmpty()) {
            errors.add("未知物品 ID: " + itemId);
        }
    }
    for (ResourceLocation tagId : rule.breedingTagIds()) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
        if (BuiltInRegistries.ITEM.getTag(tag).isEmpty()) {
            errors.add("未知或空的物品标签: #" + tagId);
        }
    }
    if (rule.breedingCooldownTicks() < 0) {
        errors.add("繁殖冷却不能为负数");
    }
    if (rule.growthTimeTicks() < 0) {
        errors.add("成长时间不能为负数");
    }
    return errors.isEmpty()
            ? RuleValidationResult.success()
            : RuleValidationResult.failure(errors);
}
```

同时按 Entity ID 前缀汇总整个候选集合的错误：

```java
public RuleValidationResult validateAll(Collection<BreedingRule> rules) {
    List<String> errors = new ArrayList<>();
    for (BreedingRule rule : rules) {
        RuleValidationResult result = validate(rule);
        for (String error : result.errors()) {
            errors.add(rule.entityTypeId() + ": " + error);
        }
    }
    return errors.isEmpty()
            ? RuleValidationResult.success()
            : RuleValidationResult.failure(errors);
}
```

- [ ] **Step 6: 验证解析和校验测试**

```powershell
.\gradlew.bat test --tests '*FoodSpecParserTest' --tests '*RuleValidatorTest'
```

Expected: 全部通过。

- [ ] **Step 7: 提交解析与校验边界**

```powershell
git add src/main/java/com/fishbreedingmanager/breeding/ParsedFood.java src/main/java/com/fishbreedingmanager/breeding/FoodSpecParser.java src/main/java/com/fishbreedingmanager/breeding/RuleValidationResult.java src/main/java/com/fishbreedingmanager/breeding/RuleValidator.java src/test/java/com/fishbreedingmanager/breeding/FoodSpecParserTest.java src/test/java/com/fishbreedingmanager/breeding/RuleValidatorTest.java
git commit -m "feat: validate breeding rules and food specifications"
```

---

### Task 4: 建立事务式 WorldBreedingService

**Files:**
- Create: `src/main/java/com/fishbreedingmanager/breeding/RuleUpdateResult.java`
- Create: `src/main/java/com/fishbreedingmanager/breeding/WorldBreedingService.java`
- Modify: `src/main/java/com/fishbreedingmanager/breeding/BreedingRuleManager.java`
- Modify: `src/main/java/com/fishbreedingmanager/persistence/WorldBreedingData.java`
- Modify: `src/main/java/com/fishbreedingmanager/FishBreedingManager.java`
- Modify: `src/main/java/com/fishbreedingmanager/command/FBMCommands.java`
- Create: `src/test/java/com/fishbreedingmanager/breeding/WorldBreedingServiceTest.java`

- [ ] **Step 1: 写事务失败不污染旧状态的测试**

```java
package com.fishbreedingmanager.breeding;

import com.fishbreedingmanager.persistence.WorldBreedingData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证规则更新只有在完整校验成功后才同时提交 SavedData 与 Snapshot。
 */
final class WorldBreedingServiceTest {
    @Test
    void 无效更新保留旧数据和旧快照() {
        WorldBreedingData data = new WorldBreedingData();
        BreedingRule original = codRule(ResourceLocation.parse("minecraft:kelp"));
        data.putRule(original);
        BreedingRuleManager manager = new BreedingRuleManager();
        manager.install(data.buildSnapshot());
        WorldBreedingService service = new WorldBreedingService(new RuleValidator());

        BreedingRule invalid = codRule(ResourceLocation.parse("fishbreedingmanager:missing_item"));
        RuleUpdateResult result = service.upsert(data, manager, invalid);

        assertFalse(result.success());
        assertEquals(original, data.getRule(original.entityTypeId()));
        assertSame(original, manager.find(original.entityTypeId()));
    }

    @Test
    void 有效更新同时替换存档数据和运行时快照() {
        WorldBreedingData data = new WorldBreedingData();
        BreedingRuleManager manager = new BreedingRuleManager();
        WorldBreedingService service = new WorldBreedingService(new RuleValidator());
        BreedingRule rule = codRule(ResourceLocation.parse("minecraft:seagrass"));

        RuleUpdateResult result = service.upsert(data, manager, rule);

        assertTrue(result.success());
        assertEquals(rule, data.getRule(rule.entityTypeId()));
        assertEquals(rule, manager.find(rule.entityTypeId()));
    }

    private static BreedingRule codRule(ResourceLocation food) {
        return new BreedingRule(ResourceLocation.parse("minecraft:cod"),
                List.of(food), List.of(), 600, 1200, true);
    }
}
```

- [ ] **Step 2: 运行测试并确认服务类型和可测试入口尚不存在**

```powershell
.\gradlew.bat test --tests '*WorldBreedingServiceTest'
```

Expected: `WorldBreedingService`、`RuleUpdateResult`、`install` 和可见构造器缺失导致编译失败。

- [ ] **Step 3: 增加 SavedData 整体替换能力**

在 `WorldBreedingData` 中增加：

```java
/**
 * 用已完整校验的规则集合整体替换持久化数据。
 *
 * @param replacement 通过 {@link RuleValidator} 校验的候选规则
 */
public void replaceRules(Collection<BreedingRule> replacement) {
    rules.clear();
    for (BreedingRule rule : replacement) {
        rules.put(rule.entityTypeId(), rule);
    }
    setDirty();
}
```

- [ ] **Step 4: 限制 Snapshot 安装入口并实现更新结果**

将 `BreedingRuleManager` 构造器改为包可见，增加：

```java
/**
 * 安装已经完整校验且不可变的运行时快照。
 *
 * @param next 下一份权威 Snapshot
 */
void install(BreedingRuleSnapshot next) {
    snapshot = next;
}
```

创建：

```java
public record RuleUpdateResult(boolean success, int ruleCount, List<String> errors) {
    public RuleUpdateResult {
        errors = List.copyOf(errors);
    }

    public static RuleUpdateResult success(int ruleCount) {
        return new RuleUpdateResult(true, ruleCount, List.of());
    }

    public static RuleUpdateResult failure(List<String> errors) {
        return new RuleUpdateResult(false, 0, errors);
    }
}
```

- [ ] **Step 5: 实现 WorldBreedingService 的候选提交算法**

生产入口从 `MinecraftServer` 获取 `WorldBreedingData` 和 `BreedingRuleManager`；包可见重载供单元测试注入现成实例。核心提交方法必须完整实现：

```java
RuleUpdateResult upsert(WorldBreedingData data, BreedingRuleManager manager,
                        BreedingRule rule) {
    Map<ResourceLocation, BreedingRule> candidate = copyRules(data);
    candidate.put(rule.entityTypeId(), rule);
    return commit(data, manager, candidate);
}

private RuleUpdateResult commit(WorldBreedingData data, BreedingRuleManager manager,
                                Map<ResourceLocation, BreedingRule> candidate) {
    RuleValidationResult validation = validator.validateAll(candidate.values());
    if (!validation.valid()) {
        return RuleUpdateResult.failure(validation.errors());
    }
    BreedingRuleSnapshot next = new BreedingRuleSnapshot(
            Map.copyOf(candidate), data.getImportedEntities());
    data.replaceRules(candidate.values());
    manager.install(next);
    return RuleUpdateResult.success(candidate.size());
}

private static Map<ResourceLocation, BreedingRule> copyRules(WorldBreedingData data) {
    Map<ResourceLocation, BreedingRule> result = new LinkedHashMap<>();
    for (BreedingRule rule : data.allRules()) {
        result.put(rule.entityTypeId(), rule);
    }
    return result;
}
```

类的完整服务入口按以下方式实现；所有生产入口都复用包可见的可测试重载：

```java
public final class WorldBreedingService {
    private static final WorldBreedingService INSTANCE =
            new WorldBreedingService(new RuleValidator());

    private final RuleValidator validator;

    WorldBreedingService(RuleValidator validator) {
        this.validator = validator;
    }

    public static WorldBreedingService get() {
        return INSTANCE;
    }

    public ReloadResult reload(MinecraftServer server) {
        try {
            WorldBreedingData data = WorldBreedingData.get(server);
            BreedingRuleManager manager = BreedingRuleManager.get(server);
            RuleValidationResult validation = validator.validateAll(data.allRules());
            if (!validation.valid()) {
                return ReloadResult.failure(String.join("; ", validation.errors()));
            }
            BreedingRuleSnapshot next = data.buildSnapshot();
            manager.install(next);
            return ReloadResult.success(next.rules().size());
        } catch (RuntimeException exception) {
            FishBreedingManager.LOGGER.error("FBM 规则重载失败，保留旧 Snapshot", exception);
            return ReloadResult.failure(exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage());
        }
    }

    public RuleUpdateResult upsert(MinecraftServer server, BreedingRule rule) {
        return upsert(WorldBreedingData.get(server), BreedingRuleManager.get(server), rule);
    }

    RuleUpdateResult upsert(WorldBreedingData data, BreedingRuleManager manager,
                            BreedingRule rule) {
        Map<ResourceLocation, BreedingRule> candidate = copyRules(data);
        candidate.put(rule.entityTypeId(), rule);
        return commit(data, manager, candidate);
    }

    public RuleUpdateResult remove(MinecraftServer server, ResourceLocation entityId) {
        WorldBreedingData data = WorldBreedingData.get(server);
        Map<ResourceLocation, BreedingRule> candidate = copyRules(data);
        if (candidate.remove(entityId) == null) {
            return RuleUpdateResult.failure(List.of("未找到实体规则: " + entityId));
        }
        return commit(data, BreedingRuleManager.get(server), candidate);
    }

    public RuleUpdateResult setEnabled(MinecraftServer server, ResourceLocation entityId,
                                       boolean enabled) {
        WorldBreedingData data = WorldBreedingData.get(server);
        Map<ResourceLocation, BreedingRule> candidate = copyRules(data);
        BreedingRule current = candidate.get(entityId);
        if (current == null) {
            return RuleUpdateResult.failure(List.of("未找到实体规则: " + entityId));
        }
        candidate.put(entityId, new BreedingRule(current.entityTypeId(),
                current.breedingItemIds(), current.breedingTagIds(),
                current.breedingCooldownTicks(), current.growthTimeTicks(), enabled));
        return commit(data, BreedingRuleManager.get(server), candidate);
    }

    public List<BreedingRule> list(MinecraftServer server) {
        return BreedingRuleManager.get(server).snapshot().rules().values().stream()
                .sorted(Comparator.comparing(rule -> rule.entityTypeId().toString()))
                .toList();
    }

    private RuleUpdateResult commit(WorldBreedingData data, BreedingRuleManager manager,
                                    Map<ResourceLocation, BreedingRule> candidate) {
        RuleValidationResult validation = validator.validateAll(candidate.values());
        if (!validation.valid()) {
            return RuleUpdateResult.failure(validation.errors());
        }
        BreedingRuleSnapshot next = new BreedingRuleSnapshot(
                Map.copyOf(candidate), data.getImportedEntities());
        data.replaceRules(candidate.values());
        manager.install(next);
        return RuleUpdateResult.success(candidate.size());
    }

    private static Map<ResourceLocation, BreedingRule> copyRules(WorldBreedingData data) {
        Map<ResourceLocation, BreedingRule> result = new LinkedHashMap<>();
        for (BreedingRule rule : data.allRules()) {
            result.put(rule.entityTypeId(), rule);
        }
        return result;
    }
}
```

- [ ] **Step 6: 让启动和 `/fbm reload` 后续统一走服务层**

`FishBreedingManager.onServerStarting` 改为：

```java
ReloadResult result = WorldBreedingService.get().reload(event.getServer());
if (!result.success()) {
    LOGGER.error("FBM 初始规则加载失败: {}", result.error());
}
```

删除 `BreedingRuleManager.reload`。同时把当前 `FBMCommands.doReload` 改为调用
`WorldBreedingService.get().reload(server)`，确保 Task 4 结束时项目仍能编译，且不存在绕过
`RuleValidator` 的旧重载入口。

- [ ] **Step 7: 运行服务测试与全部回归测试**

```powershell
.\gradlew.bat test --tests '*WorldBreedingServiceTest'
.\gradlew.bat test
```

Expected: 全部通过。

- [ ] **Step 8: 提交事务式规则服务**

```powershell
git add src/main/java/com/fishbreedingmanager/breeding/RuleUpdateResult.java src/main/java/com/fishbreedingmanager/breeding/WorldBreedingService.java src/main/java/com/fishbreedingmanager/breeding/BreedingRuleManager.java src/main/java/com/fishbreedingmanager/persistence/WorldBreedingData.java src/main/java/com/fishbreedingmanager/FishBreedingManager.java src/main/java/com/fishbreedingmanager/command/FBMCommands.java src/test/java/com/fishbreedingmanager/breeding/WorldBreedingServiceTest.java
git commit -m "feat: add transactional world breeding service"
```

---

### Task 5: 提取 ActiveLoveIndex 并恢复实体生命周期

**Files:**
- Create: `src/main/java/com/fishbreedingmanager/breeding/ActiveLoveIndex.java`
- Create: `src/main/java/com/fishbreedingmanager/event/EntityLifecycleHandler.java`
- Modify: `src/main/java/com/fishbreedingmanager/breeding/BreedingController.java`
- Modify: `src/main/java/com/fishbreedingmanager/event/EntityInteractionHandler.java`
- Modify: `src/main/java/com/fishbreedingmanager/FishBreedingManager.java`
- Modify: `src/main/java/com/fishbreedingmanager/FishBreedingManagerClient.java`
- Modify: `src/main/java/com/fishbreedingmanager/client/ClientJuvenileSync.java`
- Create: `src/test/java/com/fishbreedingmanager/breeding/ActiveLoveIndexTest.java`

- [ ] **Step 1: 写索引隔离和快照测试**

```java
package com.fishbreedingmanager.breeding;

import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 验证活跃 Love 索引按 Level 隔离且迭代快照不暴露内部集合。
 */
final class ActiveLoveIndexTest {
    @Test
    void 每个Level独立保存并返回安全副本() {
        ActiveLoveIndex index = new ActiveLoveIndex();
        ServerLevel first = mock(ServerLevel.class);
        ServerLevel second = mock(ServerLevel.class);
        UUID entityId = UUID.randomUUID();

        index.add(first, entityId);
        assertEquals(1, index.snapshot(first).size());
        assertTrue(index.snapshot(second).isEmpty());

        index.remove(first, entityId);
        assertTrue(index.snapshot(first).isEmpty());
    }

    @Test
    void clearAll清理服务器切换遗留状态() {
        ActiveLoveIndex index = new ActiveLoveIndex();
        ServerLevel level = mock(ServerLevel.class);
        index.add(level, UUID.randomUUID());

        index.clearAll();

        assertTrue(index.snapshot(level).isEmpty());
    }
}
```

- [ ] **Step 2: 运行测试并确认索引尚不存在**

```powershell
.\gradlew.bat test --tests '*ActiveLoveIndexTest'
```

Expected: `ActiveLoveIndex` 未定义导致编译失败。

- [ ] **Step 3: 实现主线程使用的简单索引**

```java
public final class ActiveLoveIndex {
    public static final ActiveLoveIndex INSTANCE = new ActiveLoveIndex();

    private final Map<ServerLevel, Set<UUID>> byLevel = new WeakHashMap<>();

    ActiveLoveIndex() {
    }

    public void add(ServerLevel level, UUID entityId) {
        byLevel.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(entityId);
    }

    public void remove(ServerLevel level, UUID entityId) {
        Set<UUID> ids = byLevel.get(level);
        if (ids != null) {
            ids.remove(entityId);
            if (ids.isEmpty()) {
                byLevel.remove(level);
            }
        }
    }

    public List<UUID> snapshot(ServerLevel level) {
        Set<UUID> ids = byLevel.get(level);
        return ids == null ? List.of() : List.copyOf(ids);
    }

    public void clear(ServerLevel level) {
        byLevel.remove(level);
    }

    public void clearAll() {
        byLevel.clear();
    }
}
```

为类、常量和全部公开方法补充详细中文 Javadoc，注明只允许服务端主线程调用。

- [ ] **Step 4: 将交互和控制器改为使用 ActiveLoveIndex**

- `EntityInteractionHandler` 进入 Love 后调用 `ActiveLoveIndex.INSTANCE.add(level, uuid)`；
- `BreedingController` 删除原 `LOVE_REGISTRIES`、同步集合和 `addLove/removeLove`；
- tick 时调用 `ActiveLoveIndex.INSTANCE.snapshot(level)`；
- 过期、删除规则和繁殖完成时调用 `remove`。

- [ ] **Step 5: 实现实体加入、离开和追踪事件**

`EntityLifecycleHandler` 的核心逻辑：

```java
@SubscribeEvent
public static void onEntityJoin(EntityJoinLevelEvent event) {
    if (!(event.getLevel() instanceof ServerLevel level)) {
        return;
    }
    Entity entity = event.getEntity();
    BreedingState state = entity.getData(ModAttachments.BREEDING_STATE);
    if (!state.prepareForLevelJoin(level.getGameTime())) {
        return;
    }
    BreedingRule rule = BreedingRuleManager.get(level.getServer()).find(entity.getType());
    if (rule == null || !rule.enabled()) {
        state.clearLove();
        return;
    }
    ActiveLoveIndex.INSTANCE.add(level, entity.getUUID());
}

@SubscribeEvent
public static void onEntityLeave(EntityLeaveLevelEvent event) {
    if (event.getLevel() instanceof ServerLevel level) {
        ActiveLoveIndex.INSTANCE.remove(level, event.getEntity().getUUID());
    }
}

@SubscribeEvent
public static void onStartTracking(PlayerEvent.StartTracking event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) {
        return;
    }
    Entity target = event.getTarget();
    long now = target.level().getGameTime();
    BreedingState state = target.getData(ModAttachments.BREEDING_STATE);
    state.tickTimers(now);
    if (state.isJuvenile(now)) {
        PacketDistributor.sendToPlayer(player,
                JuvenileStatePayload.fromState(target.getUUID(), state));
    }
}
```

- [ ] **Step 6: 清理服务器与客户端会话缓存**

`FishBreedingManager.onServerStopping` 增加：

```java
ActiveLoveIndex.INSTANCE.clearAll();
```

`FishBreedingManagerClient` 注册客户端退出监听：

```java
NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
        ClientJuvenileSync.clear());
```

- [ ] **Step 7: 运行索引测试和完整测试**

```powershell
.\gradlew.bat test --tests '*ActiveLoveIndexTest'
.\gradlew.bat test
.\gradlew.bat compileJava
```

Expected: 全部通过。

- [ ] **Step 8: 提交 Love 生命周期恢复**

```powershell
git add src/main/java/com/fishbreedingmanager/breeding/ActiveLoveIndex.java src/main/java/com/fishbreedingmanager/event/EntityLifecycleHandler.java src/main/java/com/fishbreedingmanager/breeding/BreedingController.java src/main/java/com/fishbreedingmanager/event/EntityInteractionHandler.java src/main/java/com/fishbreedingmanager/FishBreedingManager.java src/main/java/com/fishbreedingmanager/FishBreedingManagerClient.java src/main/java/com/fishbreedingmanager/client/ClientJuvenileSync.java src/test/java/com/fishbreedingmanager/breeding/ActiveLoveIndexTest.java
git commit -m "fix: restore active love across entity reloads"
```

---

### Task 6: 提取 ChildSpawner 并修正失败与配偶语义

**Files:**
- Create: `src/main/java/com/fishbreedingmanager/breeding/ChildSpawnStatus.java`
- Create: `src/main/java/com/fishbreedingmanager/breeding/ChildSpawnResult.java`
- Create: `src/main/java/com/fishbreedingmanager/breeding/ChildSpawner.java`
- Modify: `src/main/java/com/fishbreedingmanager/breeding/BreedingController.java`
- Create: `src/test/java/com/fishbreedingmanager/breeding/ChildSpawnerTest.java`
- Create: `src/test/java/com/fishbreedingmanager/breeding/BreedingControllerOutcomeTest.java`

- [ ] **Step 1: 写后代创建结构化结果失败测试**

```java
package com.fishbreedingmanager.breeding;

import com.fishbreedingmanager.attachment.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

/**
 * 验证 ChildSpawner 区分创建失败、加入世界失败和成功结果。
 */
final class ChildSpawnerTest {
    @Test
    void EntityType无法创建时返回明确失败() {
        ServerLevel level = mock(ServerLevel.class);
        Entity first = mock(Entity.class);
        Entity second = mock(Entity.class);
        EntityType<?> type = mock(EntityType.class);
        when(first.getType()).thenReturn(type);
        when(type.create(level)).thenReturn(null);

        ChildSpawnResult result = new ChildSpawner().spawn(
                level, first, second, rule(), 100L);

        assertEquals(ChildSpawnStatus.TYPE_CREATION_FAILED, result.status());
        assertNull(result.child());
    }

    @Test
    void 加入世界失败时不报告成功() {
        ServerLevel level = mock(ServerLevel.class);
        Entity first = mock(Entity.class);
        Entity second = mock(Entity.class);
        Entity child = mock(Entity.class);
        EntityType<?> type = mock(EntityType.class);
        BreedingState childState = new BreedingState();
        when(first.getType()).thenReturn(type);
        when(first.position()).thenReturn(new Vec3(0, 0, 0));
        when(second.position()).thenReturn(new Vec3(2, 0, 0));
        when(type.create(level)).thenReturn(child);
        when(child.getData(ModAttachments.BREEDING_STATE)).thenReturn(childState);
        when(level.addFreshEntity(child)).thenReturn(false);

        ChildSpawnResult result = new ChildSpawner().spawn(
                level, first, second, rule(), 100L);

        assertEquals(ChildSpawnStatus.ADD_TO_LEVEL_FAILED, result.status());
        verify(child).moveTo(anyDouble(), anyDouble(), anyDouble(), anyFloat(), anyFloat());
    }

    private static BreedingRule rule() {
        return new BreedingRule(net.minecraft.resources.ResourceLocation.parse("minecraft:cod"),
                List.of(net.minecraft.resources.ResourceLocation.parse("minecraft:kelp")),
                List.of(), 600, 1200, true);
    }
}
```

- [ ] **Step 2: 写只有成功结果才冷却父母的失败测试**

```java
package com.fishbreedingmanager.breeding;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证生成结果提交策略不会在失败时错误消耗繁殖机会。
 */
final class BreedingControllerOutcomeTest {
    @Test
    void 失败时清除配对但保留Love和冷却空闲() {
        BreedingState first = stateInLove();
        BreedingState second = stateInLove();
        first.setMate(UUID.randomUUID());
        second.setMate(UUID.randomUUID());

        boolean committed = BreedingController.applySpawnResult(
                ChildSpawnResult.failure(ChildSpawnStatus.TYPE_CREATION_FAILED),
                first, second, rule(), 100L);

        assertFalse(committed);
        assertTrue(first.isInLove(100L));
        assertFalse(first.isOnCooldown(100L));
        assertNull(first.getMate());
        assertNull(second.getMate());
    }

    @Test
    void 成功时提交冷却并清除Love() {
        BreedingState first = stateInLove();
        BreedingState second = stateInLove();
        net.minecraft.world.entity.Entity child =
                org.mockito.Mockito.mock(net.minecraft.world.entity.Entity.class);

        boolean committed = BreedingController.applySpawnResult(
                ChildSpawnResult.success(child), first, second, rule(), 100L);

        assertTrue(committed);
        assertTrue(first.isOnCooldown(100L));
        assertFalse(first.isInLove(100L));
    }

    private static BreedingState stateInLove() {
        BreedingState state = new BreedingState();
        state.enterLove(0L, 600L);
        return state;
    }

    private static BreedingRule rule() {
        return new BreedingRule(net.minecraft.resources.ResourceLocation.parse("minecraft:cod"),
                List.of(net.minecraft.resources.ResourceLocation.parse("minecraft:kelp")),
                List.of(), 600, 1200, true);
    }
}
```

- [ ] **Step 3: 运行测试并确认新生成边界尚不存在**

```powershell
.\gradlew.bat test --tests '*ChildSpawnerTest' --tests '*BreedingControllerOutcomeTest'
```

Expected: `ChildSpawner`、结果类型和 `applySpawnResult` 未定义导致失败。

- [ ] **Step 4: 实现生成状态和结果类型**

```java
public enum ChildSpawnStatus {
    SUCCESS,
    TYPE_CREATION_FAILED,
    ADD_TO_LEVEL_FAILED
}
```

```java
public record ChildSpawnResult(ChildSpawnStatus status, @Nullable Entity child) {
    public ChildSpawnResult {
        if (status == ChildSpawnStatus.SUCCESS && child == null) {
            throw new IllegalArgumentException("成功结果必须包含后代实体");
        }
        if (status != ChildSpawnStatus.SUCCESS && child != null) {
            throw new IllegalArgumentException("失败结果不能包含后代实体");
        }
    }

    public static ChildSpawnResult success(Entity child) {
        return new ChildSpawnResult(ChildSpawnStatus.SUCCESS,
                java.util.Objects.requireNonNull(child, "child"));
    }

    public static ChildSpawnResult failure(ChildSpawnStatus status) {
        if (status == ChildSpawnStatus.SUCCESS) {
            throw new IllegalArgumentException("失败结果不能使用 SUCCESS 状态");
        }
        return new ChildSpawnResult(status, null);
    }

    public boolean successful() {
        return status == ChildSpawnStatus.SUCCESS;
    }
}
```

- [ ] **Step 5: 实现 ChildSpawner**

```java
public ChildSpawnResult spawn(ServerLevel level, Entity firstParent, Entity secondParent,
                              BreedingRule rule, long now) {
    EntityType<?> type = firstParent.getType();
    Entity child = type.create(level);
    if (child == null) {
        return ChildSpawnResult.failure(ChildSpawnStatus.TYPE_CREATION_FAILED);
    }
    Vec3 midpoint = firstParent.position().add(secondParent.position()).scale(0.5D);
    child.moveTo(midpoint.x, midpoint.y, midpoint.z, 0.0F, 0.0F);
    BreedingState childState = child.getData(ModAttachments.BREEDING_STATE);
    childState.markJuvenile(now, rule.growthTimeTicks());
    if (!level.addFreshEntity(child)) {
        return ChildSpawnResult.failure(ChildSpawnStatus.ADD_TO_LEVEL_FAILED);
    }
    return ChildSpawnResult.success(child);
}
```

完整中文 Javadoc 必须说明：该类不修改父母状态，只有调用方在 `SUCCESS` 后提交冷却。

- [ ] **Step 6: 重写控制器成功提交和配偶验证**

增加包可见纯状态提交方法：

```java
static boolean applySpawnResult(ChildSpawnResult result, BreedingState first,
                                BreedingState second, BreedingRule rule, long now) {
    if (!result.successful()) {
        first.setMate(null);
        second.setMate(null);
        return false;
    }
    first.startCooldown(now, rule.breedingCooldownTicks());
    second.startCooldown(now, rule.breedingCooldownTicks());
    first.clearLove();
    second.clearLove();
    return true;
}
```

`handlePaired` 在寻路或生成前必须调用新的 `isValidPair`，检查：双方未移除、EntityType 相同、双方 Love 有效、双方不在冷却、双方不是幼体、mate UUID 互相指向。失败时清除仍指向对方的 mate，不施加冷却。

`breed` 调用 `ChildSpawner.spawn`；失败时记录警告并保留索引；成功后调用 `applySpawnResult`、从索引移除父母、发送粒子，并用：

```java
Entity child = java.util.Objects.requireNonNull(result.child());
BreedingState childState = child.getData(ModAttachments.BREEDING_STATE);
JuvenileStatePayload payload = JuvenileStatePayload.fromState(child.getUUID(), childState);
PacketDistributor.sendToPlayersTrackingEntity(child, payload);
```

发送幼体包。

- [ ] **Step 7: 运行生成测试和全部回归测试**

```powershell
.\gradlew.bat test --tests '*ChildSpawnerTest' --tests '*BreedingControllerOutcomeTest'
.\gradlew.bat test
.\gradlew.bat compileJava
```

Expected: 全部通过，旧的 `new JuvenileStatePayload(child.getUUID(), now, growthTicks)` 调用已不存在。

- [ ] **Step 8: 提交生成失败语义**

```powershell
git add src/main/java/com/fishbreedingmanager/breeding/ChildSpawnStatus.java src/main/java/com/fishbreedingmanager/breeding/ChildSpawnResult.java src/main/java/com/fishbreedingmanager/breeding/ChildSpawner.java src/main/java/com/fishbreedingmanager/breeding/BreedingController.java src/test/java/com/fishbreedingmanager/breeding/ChildSpawnerTest.java src/test/java/com/fishbreedingmanager/breeding/BreedingControllerOutcomeTest.java
git commit -m "fix: commit breeding only after child spawn succeeds"
```

---

### Task 7: 实现权限严格的管理员规则命令

**Files:**
- Modify: `src/main/java/com/fishbreedingmanager/command/FBMCommands.java`
- Modify: `src/main/resources/assets/fishbreedingmanager/lang/en_us.json`
- Create: `src/main/resources/assets/fishbreedingmanager/lang/zh_cn.json`
- Create: `src/test/java/com/fishbreedingmanager/command/FBMCommandsTest.java`

- [ ] **Step 1: 写命令树权限和语法测试**

```java
package com.fishbreedingmanager.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证所有 FBM 管理命令都在根节点执行权限等级 2 校验。
 */
final class FBMCommandsTest {
    @Test
    void 无权限来源看不到fbm命令树() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(FBMCommands.build());
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.hasPermission(2)).thenReturn(false);

        assertThrows(Exception.class,
                () -> dispatcher.execute("fbm rule list", source));
    }

    @Test
    void set命令能够解析多个food参数() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(FBMCommands.build());
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.hasPermission(2)).thenReturn(true);

        var parse = dispatcher.parse(
                "fbm rule set minecraft:cod 600 1200 minecraft:kelp,#minecraft:planks",
                source);

        assertTrue(parse.getExceptions().isEmpty());
    }
}
```

- [ ] **Step 2: 运行测试并确认 `build` 尚不存在**

```powershell
.\gradlew.bat test --tests '*FBMCommandsTest'
```

Expected: `FBMCommands.build()` 未定义导致编译失败。

- [ ] **Step 3: 构建完整 Brigadier 命令树**

`register` 只负责注册 `build()`；权限放在根节点：

```java
public static void register(RegisterCommandsEvent event) {
    event.getDispatcher().register(build());
}

static LiteralArgumentBuilder<CommandSourceStack> build() {
    return Commands.literal("fbm")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("reload").executes(FBMCommands::reload))
            .then(Commands.literal("rule")
                    .then(Commands.literal("list").executes(FBMCommands::list))
                    .then(Commands.literal("show")
                            .then(Commands.argument("entity", ResourceLocationArgument.id())
                                    .executes(FBMCommands::show)))
                    .then(Commands.literal("set")
                            .then(Commands.argument("entity", ResourceLocationArgument.id())
                                    .then(Commands.argument("cooldown", IntegerArgumentType.integer(0))
                                            .then(Commands.argument("growth", IntegerArgumentType.integer(0))
                                                    .then(Commands.argument("foods", StringArgumentType.greedyString())
                                                            .executes(FBMCommands::set)))))
                    .then(Commands.literal("enable")
                            .then(Commands.argument("entity", ResourceLocationArgument.id())
                                    .executes(context -> setEnabled(context, true))))
                    .then(Commands.literal("disable")
                            .then(Commands.argument("entity", ResourceLocationArgument.id())
                                    .executes(context -> setEnabled(context, false))))
                    .then(Commands.literal("remove")
                            .then(Commands.argument("entity", ResourceLocationArgument.id())
                                    .executes(FBMCommands::remove))));
}
```

- [ ] **Step 4: 实现命令到 WorldBreedingService 的唯一调用路径**

命令执行方法完整实现如下；`reload`、`set`、`enable`、`disable`、`remove` 只调用服务层，
不直接访问 `WorldBreedingData`：

```java
private static int reload(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    ReloadResult result = WorldBreedingService.get().reload(source.getServer());
    if (!result.success()) {
        source.sendFailure(Component.translatable(
                "commands.fbm.reload.failed", result.error()));
        return 0;
    }
    source.sendSuccess(() -> Component.translatable(
            "commands.fbm.reload.success", result.ruleCount()), true);
    return 1;
}

private static int list(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    List<BreedingRule> rules = WorldBreedingService.get().list(source.getServer());
    source.sendSuccess(() -> Component.literal("FBM rules: " + rules.size()), false);
    for (BreedingRule rule : rules) {
        source.sendSuccess(() -> Component.literal(formatRule(rule)), false);
    }
    return Math.max(1, rules.size());
}

private static int show(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    ResourceLocation entityId = ResourceLocationArgument.getId(context, "entity");
    BreedingRule rule = WorldBreedingService.get().list(source.getServer()).stream()
            .filter(candidate -> candidate.entityTypeId().equals(entityId))
            .findFirst()
            .orElse(null);
    if (rule == null) {
        source.sendFailure(Component.translatable(
                "commands.fbm.rule.not_found", entityId));
        return 0;
    }
    source.sendSuccess(() -> Component.literal(formatRule(rule)), false);
    return 1;
}

private static int set(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    try {
        ResourceLocation entityId = ResourceLocationArgument.getId(context, "entity");
        int cooldown = IntegerArgumentType.getInteger(context, "cooldown");
        int growth = IntegerArgumentType.getInteger(context, "growth");
        ParsedFood foods = FoodSpecParser.parse(
                StringArgumentType.getString(context, "foods"));
        BreedingRule rule = new BreedingRule(entityId, foods.itemIds(), foods.tagIds(),
                cooldown, growth, true);
        return reportUpdate(source, entityId,
                WorldBreedingService.get().upsert(source.getServer(), rule),
                "commands.fbm.rule.updated");
    } catch (IllegalArgumentException exception) {
        source.sendFailure(Component.translatable(
                "commands.fbm.rule.failed", exception.getMessage()));
        return 0;
    }
}

private static int setEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
    CommandSourceStack source = context.getSource();
    ResourceLocation entityId = ResourceLocationArgument.getId(context, "entity");
    RuleUpdateResult result = WorldBreedingService.get().setEnabled(
            source.getServer(), entityId, enabled);
    return reportUpdate(source, entityId, result,
            enabled ? "commands.fbm.rule.enabled" : "commands.fbm.rule.disabled");
}

private static int remove(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    ResourceLocation entityId = ResourceLocationArgument.getId(context, "entity");
    RuleUpdateResult result = WorldBreedingService.get().remove(
            source.getServer(), entityId);
    return reportUpdate(source, entityId, result, "commands.fbm.rule.removed");
}

private static int reportUpdate(CommandSourceStack source, ResourceLocation entityId,
                                RuleUpdateResult result, String successKey) {
    if (!result.success()) {
        source.sendFailure(Component.translatable("commands.fbm.rule.failed",
                String.join("; ", result.errors())));
        return 0;
    }
    source.sendSuccess(() -> Component.translatable(
            successKey, entityId, result.ruleCount()), true);
    return 1;
}

private static String formatRule(BreedingRule rule) {
    String items = rule.breedingItemIds().stream()
            .map(ResourceLocation::toString)
            .collect(Collectors.joining(","));
    String tags = rule.breedingTagIds().stream()
            .map(id -> "#" + id)
            .collect(Collectors.joining(","));
    String foods = Stream.of(items, tags)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.joining(","));
    return rule.entityTypeId() + " enabled=" + rule.enabled()
            + " cooldown=" + rule.breedingCooldownTicks()
            + " growth=" + rule.growthTimeTicks()
            + " foods=" + foods;
}
```

- [ ] **Step 5: 完整补充中英文命令文本**

`zh_cn.json` 至少包含：

```json
{
  "commands.fbm.reload.success": "Fish Breeding Manager 已重新加载，共 %s 条规则。",
  "commands.fbm.reload.failed": "Fish Breeding Manager 重新加载失败：%s",
  "commands.fbm.rule.updated": "已更新 %s 的繁殖规则，当前共 %s 条规则。",
  "commands.fbm.rule.removed": "已删除 %s 的繁殖规则，当前共 %s 条规则。",
  "commands.fbm.rule.enabled": "已启用 %s 的繁殖规则，当前共 %s 条规则。",
  "commands.fbm.rule.disabled": "已禁用 %s 的繁殖规则，当前共 %s 条规则。",
  "commands.fbm.rule.not_found": "未找到 %s 的繁殖规则。",
  "commands.fbm.rule.failed": "规则操作失败：%s"
}
```

`en_us.json` 使用完全对应的键，避免非中文客户端出现未翻译键名。

- [ ] **Step 6: 验证权限、语法、测试和构建**

```powershell
.\gradlew.bat test --tests '*FBMCommandsTest'
.\gradlew.bat test
.\gradlew.bat build
```

Expected: 全部通过；单机无作弊的命令来源因 `hasPermission(2)` 为 false 而不能执行。

- [ ] **Step 7: 提交管理员命令**

```powershell
git add src/main/java/com/fishbreedingmanager/command/FBMCommands.java src/main/resources/assets/fishbreedingmanager/lang/en_us.json src/main/resources/assets/fishbreedingmanager/lang/zh_cn.json src/test/java/com/fishbreedingmanager/command/FBMCommandsTest.java
git commit -m "feat: add permission-gated breeding rule commands"
```

---

### Task 8: 执行全项目中文 Javadoc 审计

**Files:**
- Modify: every touched file under `src/main/java/com/fishbreedingmanager/**`

- [ ] **Step 1: 列出所有公共类型和成员**

Run:

```powershell
rg -n "^public |^    public |public record|public enum|public interface|public final class" src/main/java/com/fishbreedingmanager
```

Expected: 得到待逐项核对清单。

- [ ] **Step 2: 将英文或过时架构注释改成详细中文 Javadoc**

每个公共类型/API 和关键私有流程必须说明：职责、逻辑端/线程、Rule 与 State 所有权、热更新影响、失败行为。格式示例必须遵守已批准规范：

```java
/**
 * 返回当前服务器的运行时规则管理器。
 *
 * <p>管理器只持有不可变 {@link BreedingRuleSnapshot} 的易失引用。调用方不得把查询到的
 * {@link BreedingRule} 缓存到实体 Attachment，否则现存实体将无法立即响应热更新。
 *
 * @param server 当前逻辑服务器
 * @return 与该服务器生命周期绑定的规则管理器
 */
public static BreedingRuleManager get(MinecraftServer server) {
    return MANAGERS.computeIfAbsent(server, ignored -> new BreedingRuleManager());
}
```

- [ ] **Step 3: 检查注释没有复述代码或保留错误描述**

重点删除以下过时内容：

- 幼体平滑从 50% 增长到 100%；
- Payload 仍携带 `growthTicks`；
- `BreedingController` 自己维护同步 `WeakHashMap`；
- `/fbm reload` 可以读取外部 JSON/NBT；
- mate 会跨载入恢复为原配偶；
- 后代无论生成成功与否都会进入冷却。

- [ ] **Step 4: 编译并执行全部测试**

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test
git diff --check
```

Expected: 全部成功，没有空白错误或乱码。

- [ ] **Step 5: 提交中文文档化改造**

```powershell
git add src/main/java/com/fishbreedingmanager
git commit -m "docs: standardize detailed Chinese Javadocs"
```

---

### Task 9: 执行受控 cod 开发客户端验收

**Files:**
- Create: `docs/testing/P0_Cod_Acceptance.md`
- Modify after testing: `docs/testing/P0_Cod_Acceptance.md`

- [ ] **Step 1: 创建明确的验收记录模板**

文档必须记录环境版本、世界名、执行日期、每项命令、观察结果和日志位置。检查项使用以下命令与顺序：

```text
/fbm rule set minecraft:cod 600 1200 minecraft:kelp
/summon minecraft:cod
/summon minecraft:cod
分别手持 kelp 右键两条 cod
/fbm rule set minecraft:cod 600 1200 minecraft:seagrass
验证现存 cod 拒绝 kelp、接受 seagrass
/fbm rule remove minecraft:cod
验证现存 cod 不再被 FBM 交互处理
```

同时记录：爱心、主动靠近、第三条 cod 出现、父母冷却、幼体 50%、1200 tick 后瞬间成年。

- [ ] **Step 2: 启动开发客户端**

```powershell
.\gradlew.bat runClient
```

Expected: 客户端进入标题界面且日志中无 FBM 注册异常。创建允许作弊的专用测试世界，在水体中执行验收。

- [ ] **Step 3: 验证严格权限**

另建未开启作弊的单机世界，输入 `/fbm`。

Expected: 命令不可见或不可执行；这符合已确认的管理员/作弊权限语义。

- [ ] **Step 4: 验证 Love 跨区块载入恢复**

喂食一条 cod 后立即离开使其区块卸载，在 600 个世界 tick 内返回。

Expected: 实体重新加入 Level 后仍处于有效 Love，可与另一条已喂食 cod 重新配对；不会强制恢复旧 mate UUID。

- [ ] **Step 5: 检查最新运行日志**

```powershell
rg -n "FBM|ERROR|Exception" run/logs/latest.log
```

Expected: 没有 FBM 异常；若后代创建失败，日志必须显示明确 `ChildSpawnStatus`，且父母没有进入冷却。

- [ ] **Step 6: 填写真实结果**

将每项标记为 `PASS` 或 `FAIL`，FAIL 必须附实际现象和日志行号。不得在没有观察到行为时填写 PASS。

- [ ] **Step 7: 提交验收记录**

```powershell
git add docs/testing/P0_Cod_Acceptance.md
git commit -m "test: record controlled cod P0 acceptance"
```

---

### Task 10: 最终回归、状态文档与代码审查

**Files:**
- Modify: `docs/Project_Analysis_and_Summary.md`

- [ ] **Step 1: 更新项目状态文档**

删除“项目仍是空 MDK”等过时描述，明确记录：

- B-lite P0 已实现的模块；
- 自动测试数量和命令；
- cod 验收真实结果；
- 第三方鱼、GUI、Variant 和 Adapter 仍未完成；
- 世界配置按存档共享所有维度；
- 管理操作严格要求权限等级 2；
- 全程中文 Javadoc 规范。

- [ ] **Step 2: 执行完整验证命令**

```powershell
.\gradlew.bat clean test build
git diff --check
rg -n "TO[D]O|FIX[M]E|TB[D]|UnsupportedOperationException" src docs
```

Expected: `BUILD SUCCESSFUL`；没有未解释占位内容；仅允许需求文档中作为普通语义出现的“后续”描述。

- [ ] **Step 3: 使用 requesting-code-review 技能进行独立审查**

审查重点：

- 无效规则是否可能先写 SavedData 再失败；
- 是否仍有路径绕过 `WorldBreedingService`；
- 生成失败是否可能施加冷却；
- 配偶验证是否双方互认；
- Love 索引是否在加入、离开和服务器停止时一致；
- StartTracking 是否发送 `BreedingState.getAdultAt()`；
- 客户端是否固定 50% 而非平滑；
- 所有公共 API 和关键私有流程是否有准确中文 Javadoc。

- [ ] **Step 4: 处理审查意见后重新验证**

```powershell
.\gradlew.bat test build
git diff --check
```

Expected: 全部通过。

- [ ] **Step 5: 提交最终状态文档与审查修正**

```powershell
git add docs/Project_Analysis_and_Summary.md src/main src/test
git commit -m "chore: finalize P0 B-lite stabilization"
```

- [ ] **Step 6: 输出交接摘要**

交接必须区分：自动验证、开发客户端人工验证、尚未进行的第三方实体验收；不得把编译成功等同于游戏行为通过。

---

## 实施时的硬性检查

- 每个行为变更都先写失败测试并确认失败原因正确，再写最小实现。
- 每次只解决当前测试对应的问题，不顺手进入 GUI、Variant 或 Adapter。
- 所有新增和修改的公共 API、核心私有流程都使用详细中文 Javadoc。
- Javadoc 使用 `{@link}`、`{@code}`、`<p>`、`@param`、`@return` 等 IDEA 支持语法时必须引用真实存在的类型和成员。
- 所有规则修改只允许经过 `WorldBreedingService`。
- 所有运行时行为每次查询当前 Snapshot，不向实体缓存规则。
- SavedData 和 Snapshot 必须在完整校验成功后一起提交。
- 只有后代成功加入 Level 才允许父母进入冷却。
- 不得把未执行的开发客户端验收标记为通过。

## 版本固定参考

- ModDevGradle JUnit 配置：`https://github.com/NeoForged/ModDevGradle#unit-testing-with-junit`
- NeoForge 1.21.1 Data Attachments：`https://docs.neoforged.net/docs/1.21.1/datastorage/attachments`
- NeoForge 1.21.1 Payload：`https://docs.neoforged.net/docs/1.21.1/networking/payload`
- 本地锁定 API 源码：`build/moddev/artifacts/neoforge-21.1.244-sources.jar`
- 已核对事件：`EntityJoinLevelEvent`、`EntityLeaveLevelEvent`、`PlayerEvent.StartTracking`、`ClientPlayerNetworkEvent.LoggingOut`。
