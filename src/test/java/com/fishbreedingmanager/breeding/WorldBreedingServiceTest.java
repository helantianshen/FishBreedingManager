package com.fishbreedingmanager.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fishbreedingmanager.persistence.WorldBreedingData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * 验证规则更新只有在候选全集通过校验后，才同时提交到 {@link WorldBreedingData} 与运行时快照。
 */
class WorldBreedingServiceTest {
    /** 可选兼容刷新属于提交后的 best-effort 副作用，失败不得改变核心事务的对外结果。 */
    @Test
    void containsCompatibilityRefreshFailureAfterRulePublication() {
        MinecraftServer server = mock(MinecraftServer.class);
        WorldBreedingService service = new WorldBreedingService(
                new RuleValidator(),
                ignored -> { throw new IllegalStateException("compat refresh failed"); });

        assertDoesNotThrow(() -> service.refreshCompatibility(server));
    }

    /**
     * 无效候选规则必须返回错误，并原样保留之前的存档数据与运行时对象引用。
     */
    @Test
    void invalidUpdatePreservesSavedDataAndRuntimeSnapshot() {
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

    /**
     * 有效候选规则必须同时替换持久化数据与运行时快照，并返回提交后的规则总数。
     */
    @Test
    void validUpdateReplacesSavedDataAndRuntimeSnapshotTogether() {
        WorldBreedingData data = new WorldBreedingData();
        BreedingRuleManager manager = new BreedingRuleManager();
        WorldBreedingService service = new WorldBreedingService(new RuleValidator());
        BreedingRule rule = codRule(ResourceLocation.parse("minecraft:seagrass"));

        RuleUpdateResult result = service.upsert(data, manager, rule);

        assertTrue(result.success());
        assertEquals(1, result.ruleCount());
        assertEquals(rule, data.getRule(rule.entityTypeId()));
        assertEquals(rule, manager.find(rule.entityTypeId()));
    }

    /**
     * 删除存在的规则应同时更新两个状态容器；删除不存在的规则则必须保持现状并返回失败。
     */
    @Test
    void removeCommitsOnlyWhenRuleExists() {
        WorldBreedingData data = new WorldBreedingData();
        BreedingRule original = codRule(ResourceLocation.parse("minecraft:kelp"));
        data.putRule(original);
        BreedingRuleManager manager = new BreedingRuleManager();
        manager.install(data.buildSnapshot());
        WorldBreedingService service = new WorldBreedingService(new RuleValidator());

        RuleUpdateResult missing = service.remove(
                data, manager, ResourceLocation.parse("minecraft:salmon"));
        assertFalse(missing.success());
        assertSame(original, manager.find(original.entityTypeId()));

        RuleUpdateResult removed = service.remove(data, manager, original.entityTypeId());
        assertTrue(removed.success());
        assertEquals(0, removed.ruleCount());
        assertEquals(null, data.getRule(original.entityTypeId()));
        assertEquals(null, manager.find(original.entityTypeId()));
    }

    /**
     * 启用状态更新只能改变 {@code enabled} 字段，其余食物与计时配置必须完整保留。
     */
    @Test
    void setEnabledPreservesOtherRuleFields() {
        WorldBreedingData data = new WorldBreedingData();
        BreedingRule original = codRule(ResourceLocation.parse("minecraft:kelp"));
        data.putRule(original);
        BreedingRuleManager manager = new BreedingRuleManager();
        manager.install(data.buildSnapshot());
        WorldBreedingService service = new WorldBreedingService(new RuleValidator());

        RuleUpdateResult result = service.setEnabled(data, manager, original.entityTypeId(), false);

        assertTrue(result.success());
        BreedingRule disabled = manager.find(original.entityTypeId());
        assertFalse(disabled.enabled());
        assertEquals(original.breedingItemIds(), disabled.breedingItemIds());
        assertEquals(original.breedingCooldownTicks(), disabled.breedingCooldownTicks());
        assertEquals(original.growthTimeTicks(), disabled.growthTimeTicks());
    }

    /**
     * 创建测试使用的鳕鱼规则，只改变食物 ID 以隔离事务行为。
     *
     * @param food 单个繁殖物品 ID
     * @return 启用且计时合法的鳕鱼规则
     */
    private static BreedingRule codRule(ResourceLocation food) {
        return new BreedingRule(ResourceLocation.parse("minecraft:cod"),
                List.of(food), List.of(), 600, 1200, true);
    }
}
