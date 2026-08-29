package com.fishbreedingmanager.compat.feedingtrough;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import com.fishbreedingmanager.breeding.feed.BreedingFeedService;
import com.fishbreedingmanager.breeding.BreedingRuleManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;

/** 验证非 Animal 鱼使用喂食槽时仍遵循当前 FBM 规则和状态。 */
class FbmTroughSelfFeedGoalTest {
    private static final BlockPos TROUGH_POS = new BlockPos(4, 5, 6);

    /** Goal 运行途中规则禁用或食物换走时，真实继续执行检查必须立即停止。 */
    @Test
    void stopsContinuingWhenCurrentEligibilityChanges() {
        ServerLevel level = mock(ServerLevel.class);
        PathfinderMob mob = mock(PathfinderMob.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingFeedService service = mock(BreedingFeedService.class);
        AnimalFeedingTroughSource source = mock(AnimalFeedingTroughSource.class);
        ItemStack offered = mock(ItemStack.class);
        FbmTroughSelfFeedGoal goal = new FbmTroughSelfFeedGoal(mob, manager, service, source);

        doReturn(level).when(mob).level();
        when(source.peek(level, BlockPos.ZERO)).thenReturn(offered);
        when(offered.isEmpty()).thenReturn(false);
        when(service.canFeed(level, mob, offered, manager)).thenReturn(true, false);

        assertTrue(goal.canContinueToUse());
        assertFalse(goal.canContinueToUse());
    }

    /** 目标有效性必须每次委托统一服务，因而能立即看到规则或食物变化。 */
    @Test
    void validatesTroughFoodAgainstCurrentFeedEligibility() {
        ServerLevel level = mock(ServerLevel.class);
        PathfinderMob mob = mock(PathfinderMob.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingFeedService service = mock(BreedingFeedService.class);
        AnimalFeedingTroughSource source = mock(AnimalFeedingTroughSource.class);
        ItemStack offered = mock(ItemStack.class);
        FbmTroughSelfFeedGoal goal = new FbmTroughSelfFeedGoal(mob, manager, service, source);

        doReturn(level).when(mob).level();
        when(source.peek(level, TROUGH_POS)).thenReturn(offered);
        when(offered.isEmpty()).thenReturn(false);
        when(service.canFeed(level, mob, offered, manager)).thenReturn(false, true);

        assertFalse(goal.isValidTarget(level, TROUGH_POS));
        assertTrue(goal.isValidTarget(level, TROUGH_POS));
        assertEquals(2.0D, goal.acceptedDistance());
    }

    /** Love 提交成功后才允许喂食槽精确消费一个物品。 */
    @Test
    void consumesOneTroughItemAfterSuccessfulLoveCommit() {
        ServerLevel level = mock(ServerLevel.class);
        PathfinderMob mob = mock(PathfinderMob.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingFeedService service = mock(BreedingFeedService.class);
        AnimalFeedingTroughSource source = mock(AnimalFeedingTroughSource.class);
        ItemStack offered = mock(ItemStack.class);
        FbmTroughSelfFeedGoal goal = new FbmTroughSelfFeedGoal(mob, manager, service, source);

        doReturn(level).when(mob).level();
        when(source.peek(level, TROUGH_POS)).thenReturn(offered);
        when(offered.isEmpty()).thenReturn(false);
        when(source.consumeOne(level, TROUGH_POS)).thenReturn(true);
        when(service.tryFeed(eq(level), eq(mob), eq(offered), eq(manager), any(BooleanSupplier.class)))
                .thenAnswer(invocation -> {
                    BooleanSupplier consumer = invocation.getArgument(4);
                    return consumer.getAsBoolean()
                            ? BreedingFeedService.FeedResult.FED
                            : BreedingFeedService.FeedResult.SOURCE_UNAVAILABLE;
                });

        assertTrue(goal.feedAtTarget(TROUGH_POS));
        verify(source).consumeOne(level, TROUGH_POS);
    }

    /** 统一服务拒绝状态转换时不得从喂食槽扣料。 */
    @Test
    void leavesTroughUntouchedWhenFeedServiceRejectsTarget() {
        ServerLevel level = mock(ServerLevel.class);
        PathfinderMob mob = mock(PathfinderMob.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingFeedService service = mock(BreedingFeedService.class);
        AnimalFeedingTroughSource source = mock(AnimalFeedingTroughSource.class);
        ItemStack offered = mock(ItemStack.class);
        FbmTroughSelfFeedGoal goal = new FbmTroughSelfFeedGoal(mob, manager, service, source);

        doReturn(level).when(mob).level();
        when(source.peek(level, TROUGH_POS)).thenReturn(offered);
        when(offered.isEmpty()).thenReturn(false);
        when(service.tryFeed(eq(level), eq(mob), eq(offered), eq(manager), any(BooleanSupplier.class)))
                .thenReturn(BreedingFeedService.FeedResult.INELIGIBLE);

        assertFalse(goal.feedAtTarget(TROUGH_POS));
        verify(source, never()).consumeOne(level, TROUGH_POS);
    }
}
