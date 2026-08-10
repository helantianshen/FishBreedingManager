package com.fishbreedingmanager.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fishbreedingmanager.attachment.ModAttachments;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * 验证 {@link ChildSpawner} 明确区分实体类型创建失败、加入世界失败与成功结果。
 */
class ChildSpawnerTest {
    /**
     * 实体类型无法创建实例时应返回对应失败状态，且结果不能包含后代实体。
     */
    @Test
    void returnsTypeCreationFailureWhenEntityTypeCreatesNothing() {
        ServerLevel level = mock(ServerLevel.class);
        Entity first = mock(Entity.class);
        Entity second = mock(Entity.class);
        EntityType<?> type = mock(EntityType.class);
        doReturn(type).when(first).getType();
        when(type.create(level)).thenReturn(null);

        ChildSpawnResult result = new ChildSpawner().spawn(level, first, second, rule(), 100L);

        assertEquals(ChildSpawnStatus.TYPE_CREATION_FAILED, result.status());
        assertNull(result.child());
    }

    /**
     * 已创建后代但 Level 拒绝加入时不能报告成功，同时仍应完成出生位置计算。
     */
    @Test
    void returnsAddFailureWhenLevelRejectsChild() {
        SpawnFixture fixture = fixture(false);

        ChildSpawnResult result = new ChildSpawner().spawn(
                fixture.level(), fixture.first(), fixture.second(), rule(), 100L);

        assertEquals(ChildSpawnStatus.ADD_TO_LEVEL_FAILED, result.status());
        assertNull(result.child());
        verify(fixture.child()).moveTo(anyDouble(), anyDouble(), anyDouble(), anyFloat(), anyFloat());
    }

    /**
     * 后代成功加入世界时应返回该实体，并用规则成长时间标记权威幼体状态。
     */
    @Test
    void returnsChildAndMarksJuvenileAfterSuccessfulAdd() {
        SpawnFixture fixture = fixture(true);

        ChildSpawnResult result = new ChildSpawner().spawn(
                fixture.level(), fixture.first(), fixture.second(), rule(), 100L);

        assertEquals(ChildSpawnStatus.SUCCESS, result.status());
        assertSame(fixture.child(), result.child());
        assertEquals(1300L, fixture.childState().getAdultAt());
        assertEquals(0.5F, fixture.childState().visualScale(100L));
    }

    /**
     * 创建带固定父母位置、后代附件和可配置加入结果的测试夹具。
     *
     * @param addResult {@link ServerLevel#addFreshEntity(Entity)} 的返回值
     * @return 完整测试夹具
     */
    private static SpawnFixture fixture(boolean addResult) {
        ServerLevel level = mock(ServerLevel.class);
        Entity first = mock(Entity.class);
        Entity second = mock(Entity.class);
        Entity child = mock(Entity.class);
        EntityType<?> type = mock(EntityType.class);
        BreedingState childState = new BreedingState();
        doReturn(type).when(first).getType();
        when(first.position()).thenReturn(new Vec3(0.0D, 0.0D, 0.0D));
        when(second.position()).thenReturn(new Vec3(2.0D, 0.0D, 0.0D));
        when(type.create(level)).thenReturn(child);
        when(child.getData(ModAttachments.BREEDING_STATE)).thenReturn(childState);
        when(level.addFreshEntity(child)).thenReturn(addResult);
        return new SpawnFixture(level, first, second, child, childState);
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

    /** 测试所需的外部 Minecraft 对象和真实状态对象集合。 */
    private record SpawnFixture(ServerLevel level, Entity first, Entity second,
                                Entity child, BreedingState childState) {
    }
}
