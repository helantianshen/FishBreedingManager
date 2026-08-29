package com.fishbreedingmanager.breeding.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fishbreedingmanager.attachment.ModAttachments;
import com.fishbreedingmanager.breeding.BreedingRule;
import com.fishbreedingmanager.breeding.BreedingRuleManager;
import com.fishbreedingmanager.breeding.BreedingState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/** 验证所有喂食入口共享同一套服务端 FBM Love 状态转换。 */
class BreedingFeedServiceTest {
    /** 无规则与禁用规则应返回可诊断原因，并且都不创建实体状态。 */
    @Test
    void distinguishesMissingAndDisabledRulesBeforeCreatingState() {
        ServerLevel level = mock(ServerLevel.class);
        Entity target = mock(Entity.class);
        EntityType<?> type = mock(EntityType.class);
        ItemStack offered = mock(ItemStack.class);
        BreedingRule rule = mock(BreedingRule.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingFeedService service = new BreedingFeedService(
                (actualLevel, id) -> { },
                (actualLevel, entity) -> { });

        doReturn(type).when(target).getType();
        when(manager.find(type)).thenReturn(null, rule);
        when(rule.enabled()).thenReturn(false);

        assertEquals(BreedingFeedService.FeedResult.NO_RULE,
                service.tryFeed(level, target, offered, manager));
        assertEquals(BreedingFeedService.FeedResult.RULE_DISABLED,
                service.tryFeed(level, target, offered, manager));
        verify(target, never()).getData(ModAttachments.BREEDING_STATE);
    }

    /** 冷却与幼体都必须经统一服务拒绝，且不登记 Love 副作用。 */
    @Test
    void rejectsCooldownAndJuvenileStates() {
        ServerLevel level = mock(ServerLevel.class);
        Entity cooldownTarget = mock(Entity.class);
        Entity juvenileTarget = mock(Entity.class);
        EntityType<?> type = mock(EntityType.class);
        ItemStack offered = mock(ItemStack.class);
        BreedingRule rule = mock(BreedingRule.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingState cooldownState = new BreedingState();
        cooldownState.startCooldown(50L, 100L);
        BreedingState juvenileState = new BreedingState();
        juvenileState.markJuvenile(50L, 100L);
        BreedingFeedService service = new BreedingFeedService(
                (actualLevel, id) -> { throw new AssertionError("must not index"); },
                (actualLevel, entity) -> { throw new AssertionError("must not emit"); });

        when(level.getGameTime()).thenReturn(100L);
        doReturn(type).when(cooldownTarget).getType();
        doReturn(type).when(juvenileTarget).getType();
        doReturn(cooldownState).when(cooldownTarget).getData(ModAttachments.BREEDING_STATE);
        doReturn(juvenileState).when(juvenileTarget).getData(ModAttachments.BREEDING_STATE);
        when(manager.find(type)).thenReturn(rule);
        when(rule.enabled()).thenReturn(true);
        when(rule.testFood(offered)).thenReturn(true);

        assertEquals(BreedingFeedService.FeedResult.INELIGIBLE,
                service.tryFeed(level, cooldownTarget, offered, manager));
        assertEquals(BreedingFeedService.FeedResult.INELIGIBLE,
                service.tryFeed(level, juvenileTarget, offered, manager));
    }

    /** 首次成功后第二个 tick 的重复尝试不得再次调用来源扣料。 */
    @Test
    void consumesExternalSourceOnlyOnFirstSuccessfulAttempt() {
        ServerLevel level = mock(ServerLevel.class);
        Entity target = mock(Entity.class);
        EntityType<?> type = mock(EntityType.class);
        ItemStack offered = mock(ItemStack.class);
        BreedingRule rule = mock(BreedingRule.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingState state = new BreedingState();
        AtomicInteger consumptions = new AtomicInteger();
        BreedingFeedService service = new BreedingFeedService(
                (actualLevel, id) -> { },
                (actualLevel, entity) -> { });

        when(level.getGameTime()).thenReturn(100L);
        doReturn(type).when(target).getType();
        when(target.getUUID()).thenReturn(UUID.randomUUID());
        doReturn(state).when(target).getData(ModAttachments.BREEDING_STATE);
        when(manager.find(type)).thenReturn(rule);
        when(rule.enabled()).thenReturn(true);
        when(rule.testFood(offered)).thenReturn(true);

        assertEquals(BreedingFeedService.FeedResult.FED,
                service.tryFeed(level, target, offered, manager,
                        () -> { consumptions.incrementAndGet(); return true; }));
        assertEquals(BreedingFeedService.FeedResult.INELIGIBLE,
                service.tryFeed(level, target, offered, manager,
                        () -> { consumptions.incrementAndGet(); return true; }));
        assertEquals(1, consumptions.get());
    }

    /** 自动来源扣料失败时不得先写 Love，避免产生免费喂食。 */
    @Test
    void leavesLoveUntouchedWhenSourceConsumptionFails() {
        ServerLevel level = mock(ServerLevel.class);
        Entity target = mock(Entity.class);
        EntityType<?> type = mock(EntityType.class);
        ItemStack offered = mock(ItemStack.class);
        BreedingRule rule = mock(BreedingRule.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingState state = new BreedingState();
        List<UUID> indexed = new ArrayList<>();
        List<Entity> emitted = new ArrayList<>();
        BreedingFeedService service = new BreedingFeedService(
                (actualLevel, id) -> indexed.add(id),
                (actualLevel, entity) -> emitted.add(entity));

        when(level.getGameTime()).thenReturn(100L);
        doReturn(type).when(target).getType();
        doReturn(state).when(target).getData(ModAttachments.BREEDING_STATE);
        when(manager.find(type)).thenReturn(rule);
        when(rule.enabled()).thenReturn(true);
        when(rule.testFood(offered)).thenReturn(true);

        BreedingFeedService.FeedResult result = service.tryFeed(
                level, target, offered, manager, () -> false);

        assertEquals(BreedingFeedService.FeedResult.SOURCE_UNAVAILABLE, result);
        assertFalse(state.isInLove(100L));
        assertEquals(List.of(), indexed);
        assertEquals(List.of(), emitted);
    }

    /** 目标搜索阶段应能只读判断当前规则和状态，而不提前进入 Love。 */
    @Test
    void reportsEligibilityWithoutEnteringLove() {
        ServerLevel level = mock(ServerLevel.class);
        Entity target = mock(Entity.class);
        EntityType<?> type = mock(EntityType.class);
        ItemStack offered = mock(ItemStack.class);
        BreedingRule rule = mock(BreedingRule.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingState state = new BreedingState();
        BreedingFeedService service = new BreedingFeedService(
                (actualLevel, id) -> { },
                (actualLevel, entity) -> { });

        when(level.getGameTime()).thenReturn(100L);
        doReturn(type).when(target).getType();
        doReturn(state).when(target).getData(ModAttachments.BREEDING_STATE);
        when(manager.find(type)).thenReturn(rule);
        when(rule.enabled()).thenReturn(true);
        when(rule.testFood(offered)).thenReturn(true);

        assertTrue(service.canFeed(level, target, offered, manager));
        assertFalse(state.isInLove(100L));
        assertEquals(0L, state.getLoveUntil());
    }

    /** 成功转换应写 Love、登记索引和粒子，但把扣料责任留给调用方。 */
    @Test
    void entersLoveWithoutConsumingTheSourceStack() {
        ServerLevel level = mock(ServerLevel.class);
        Entity target = mock(Entity.class);
        EntityType<?> type = mock(EntityType.class);
        ItemStack offered = mock(ItemStack.class);
        BreedingRule rule = mock(BreedingRule.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingState state = new BreedingState();
        UUID targetId = UUID.randomUUID();
        List<UUID> indexed = new ArrayList<>();
        List<Entity> emitted = new ArrayList<>();
        BreedingFeedService service = new BreedingFeedService(
                (actualLevel, id) -> indexed.add(id),
                (actualLevel, entity) -> emitted.add(entity));

        when(level.getGameTime()).thenReturn(100L);
        doReturn(type).when(target).getType();
        when(target.getUUID()).thenReturn(targetId);
        doReturn(state).when(target).getData(ModAttachments.BREEDING_STATE);
        when(manager.find(type)).thenReturn(rule);
        when(rule.enabled()).thenReturn(true);
        when(rule.testFood(offered)).thenReturn(true);

        BreedingFeedService.FeedResult result = service.tryFeed(level, target, offered, manager);

        assertEquals(BreedingFeedService.FeedResult.FED, result);
        assertEquals(700L, state.getLoveUntil());
        assertEquals(List.of(targetId), indexed);
        assertEquals(List.of(target), emitted);
        verify(offered, never()).shrink(1);
    }

    /** 错误食物必须在创建实体 Attachment 之前被拒绝。 */
    @Test
    void rejectsWrongFoodWithoutCreatingState() {
        ServerLevel level = mock(ServerLevel.class);
        Entity target = mock(Entity.class);
        EntityType<?> type = mock(EntityType.class);
        ItemStack offered = mock(ItemStack.class);
        BreedingRule rule = mock(BreedingRule.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingFeedService service = new BreedingFeedService(
                (actualLevel, id) -> { },
                (actualLevel, entity) -> { });

        doReturn(type).when(target).getType();
        when(manager.find(type)).thenReturn(rule);
        when(rule.enabled()).thenReturn(true);
        when(rule.testFood(offered)).thenReturn(false);

        BreedingFeedService.FeedResult result = service.tryFeed(level, target, offered, manager);

        assertEquals(BreedingFeedService.FeedResult.WRONG_FOOD, result);
        verify(target, never()).getData(ModAttachments.BREEDING_STATE);
    }

    /** 已经 Love 的实体不得重新进入 Love，也不得重复登记或发粒子。 */
    @Test
    void rejectsEntityThatCannotEnterLove() {
        ServerLevel level = mock(ServerLevel.class);
        Entity target = mock(Entity.class);
        EntityType<?> type = mock(EntityType.class);
        ItemStack offered = mock(ItemStack.class);
        BreedingRule rule = mock(BreedingRule.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingState state = new BreedingState();
        state.enterLove(50L, 200L);
        List<UUID> indexed = new ArrayList<>();
        List<Entity> emitted = new ArrayList<>();
        BreedingFeedService service = new BreedingFeedService(
                (actualLevel, id) -> indexed.add(id),
                (actualLevel, entity) -> emitted.add(entity));

        when(level.getGameTime()).thenReturn(100L);
        doReturn(type).when(target).getType();
        doReturn(state).when(target).getData(ModAttachments.BREEDING_STATE);
        when(manager.find(type)).thenReturn(rule);
        when(rule.enabled()).thenReturn(true);
        when(rule.testFood(offered)).thenReturn(true);

        BreedingFeedService.FeedResult result = service.tryFeed(level, target, offered, manager);

        assertEquals(BreedingFeedService.FeedResult.INELIGIBLE, result);
        assertEquals(250L, state.getLoveUntil());
        assertEquals(List.of(), indexed);
        assertEquals(List.of(), emitted);
    }
}
