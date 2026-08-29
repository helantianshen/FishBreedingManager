package com.fishbreedingmanager.compat.feedingtrough;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fishbreedingmanager.breeding.feed.BreedingFeedService;
import com.fishbreedingmanager.breeding.BreedingRule;
import com.fishbreedingmanager.breeding.BreedingRuleManager;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;

/** 验证喂食槽 Goal 只对适合且受当前 FBM 规则管理的实体幂等安装。 */
class AnimalFeedingTroughCompatibilityTest {
    /** 非 PathfinderMob、未初始化管理器和无规则实体都不得触碰 GoalSelector。 */
    @Test
    void skipsUnsupportedUninitializedAndUnconfiguredEntities() {
        Entity plainEntity = mock(Entity.class);
        PathfinderMob mob = mock(PathfinderMob.class);
        GoalSelector selector = mock(GoalSelector.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        EntityType<?> type = mock(EntityType.class);

        assertFalse(AnimalFeedingTroughCompatibility.installIfEligible(
                plainEntity, manager, true));
        when(manager.isInitialized()).thenReturn(false, true);
        doReturn(type).when(mob).getType();
        when(manager.find(type)).thenReturn(null);

        assertFalse(AnimalFeedingTroughCompatibility.install(
                mob, selector, manager, true));
        assertFalse(AnimalFeedingTroughCompatibility.install(
                mob, selector, manager, true));
        verifyNoInteractions(selector);
    }

    /** 单个异常第三方实体不得中断其余实体的规则发布后刷新。 */
    @Test
    void containsPerEntityInstallationFailure() {
        PathfinderMob mob = mock(PathfinderMob.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        when(manager.isInitialized()).thenThrow(new IllegalStateException("broken entity boundary"));

        assertFalse(AnimalFeedingTroughCompatibility.installSafely(
                mob, manager, true));
    }

    /** 已加载喂食槽且规则启用的非 Animal PathfinderMob 应安装一次目标。 */
    @Test
    void installsGoalForEnabledNonAnimalPathfinderMob() {
        PathfinderMob mob = mock(PathfinderMob.class);
        GoalSelector selector = mock(GoalSelector.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingRule rule = mock(BreedingRule.class);
        EntityType<?> type = mock(EntityType.class);

        when(manager.isInitialized()).thenReturn(true);
        doReturn(type).when(mob).getType();
        when(manager.find(type)).thenReturn(rule);
        when(rule.enabled()).thenReturn(true);
        when(selector.getAvailableGoals()).thenReturn(Set.of());

        assertTrue(AnimalFeedingTroughCompatibility.install(
                mob, selector, manager, true));
        verify(selector).addGoal(
                eq(AnimalFeedingTroughCompatibility.GOAL_PRIORITY),
                isA(FbmTroughSelfFeedGoal.class));
    }

    /** 已有 FBM 喂食槽目标时，规则刷新不得重复添加。 */
    @Test
    void doesNotInstallDuplicateGoal() {
        PathfinderMob mob = mock(PathfinderMob.class);
        GoalSelector selector = mock(GoalSelector.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingRule rule = mock(BreedingRule.class);
        EntityType<?> type = mock(EntityType.class);
        FbmTroughSelfFeedGoal existing = new FbmTroughSelfFeedGoal(
                mob, manager, mock(BreedingFeedService.class), mock(AnimalFeedingTroughSource.class));

        when(manager.isInitialized()).thenReturn(true);
        doReturn(type).when(mob).getType();
        when(manager.find(type)).thenReturn(rule);
        when(rule.enabled()).thenReturn(true);
        when(selector.getAvailableGoals()).thenReturn(Set.of(
                new WrappedGoal(AnimalFeedingTroughCompatibility.GOAL_PRIORITY, existing)));

        assertFalse(AnimalFeedingTroughCompatibility.install(
                mob, selector, manager, true));
        verify(selector, never()).addGoal(
                AnimalFeedingTroughCompatibility.GOAL_PRIORITY,
                existing);
    }

    /** 未加载喂食槽或规则禁用时不得安装目标。 */
    @Test
    void skipsWhenModIsAbsentOrRuleIsDisabled() {
        PathfinderMob mob = mock(PathfinderMob.class);
        GoalSelector selector = mock(GoalSelector.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingRule rule = mock(BreedingRule.class);
        EntityType<?> type = mock(EntityType.class);

        when(manager.isInitialized()).thenReturn(true);
        doReturn(type).when(mob).getType();
        when(manager.find(type)).thenReturn(rule);
        when(rule.enabled()).thenReturn(false);

        assertFalse(AnimalFeedingTroughCompatibility.install(
                mob, selector, manager, false));
        assertFalse(AnimalFeedingTroughCompatibility.install(
                mob, selector, manager, true));
        verifyNoInteractions(selector);
    }

    /** Animal 必须保留给上游喂食槽实现，防止同一实体双重扣料。 */
    @Test
    void excludesVanillaAnimalHierarchy() {
        Animal animal = mock(Animal.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);

        assertFalse(AnimalFeedingTroughCompatibility.installIfEligible(
                animal, manager, true));
    }
}
