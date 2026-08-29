package com.fishbreedingmanager.breeding;

import com.fishbreedingmanager.breeding.spawn.ChildSpawnResult;
import com.fishbreedingmanager.breeding.spawn.ChildSpawnStatus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * 验证生成结果的状态提交策略不会在失败时错误消耗父母的繁殖机会。
 */
class BreedingControllerOutcomeTest {
    /**
     * 后代生成失败时只解除当前配对，双方 Love 与空闲冷却状态必须保留以便再次匹配。
     */
    @Test
    void failureClearsPairButPreservesLoveAndAvailableCooldown() {
        BreedingState first = stateInLove();
        BreedingState second = stateInLove();
        first.setMate(UUID.randomUUID());
        second.setMate(UUID.randomUUID());

        boolean committed = BreedingController.applySpawnResult(
                ChildSpawnResult.failure(ChildSpawnStatus.TYPE_CREATION_FAILED),
                first, second, rule(), 100L);

        assertFalse(committed);
        assertTrue(first.isInLove(100L));
        assertTrue(second.isInLove(100L));
        assertFalse(first.isOnCooldown(100L));
        assertFalse(second.isOnCooldown(100L));
        assertNull(first.getMate());
        assertNull(second.getMate());
    }

    /**
     * 后代成功加入世界后才提交双方冷却并清除 Love。
     */
    @Test
    void successStartsCooldownAndClearsLove() {
        BreedingState first = stateInLove();
        BreedingState second = stateInLove();
        Entity child = mock(Entity.class);

        boolean committed = BreedingController.applySpawnResult(
                ChildSpawnResult.success(child), first, second, rule(), 100L);

        assertTrue(committed);
        assertTrue(first.isOnCooldown(100L));
        assertTrue(second.isOnCooldown(100L));
        assertFalse(first.isInLove(100L));
        assertFalse(second.isInLove(100L));
    }

    /**
     * 同类型、有效 Love、无冷却、非幼体且 UUID 互相指向的两个不同实体应构成有效配对。
     */
    @Test
    void acceptsOnlyMutuallyLinkedEligiblePair() {
        Entity first = mock(Entity.class);
        Entity second = mock(Entity.class);
        EntityType<?> type = mock(EntityType.class);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        doReturn(type).when(first).getType();
        doReturn(type).when(second).getType();
        when(first.getUUID()).thenReturn(firstId);
        when(second.getUUID()).thenReturn(secondId);
        BreedingState firstState = stateInLove();
        BreedingState secondState = stateInLove();
        firstState.setMate(secondId);
        secondState.setMate(firstId);

        assertTrue(BreedingController.isValidPair(
                first, firstState, second, secondState, 100L));

        secondState.setMate(UUID.randomUUID());
        assertFalse(BreedingController.isValidPair(
                first, firstState, second, secondState, 100L));
    }

    /**
     * 创建处于有效 Love 时间窗的状态。
     *
     * @return Love 截止到第 600 刻的实体状态
     */
    private static BreedingState stateInLove() {
        BreedingState state = new BreedingState();
        state.enterLove(0L, 600L);
        return state;
    }

    /**
     * 返回测试使用的合法鳕鱼繁殖规则。
     *
     * @return 冷却 600 刻、成长 1200 刻的启用规则
     */
    private static BreedingRule rule() {
        return new BreedingRule(ResourceLocation.parse("minecraft:cod"),
                List.of(ResourceLocation.parse("minecraft:kelp")),
                List.of(), 600, 1200, true);
    }
}
