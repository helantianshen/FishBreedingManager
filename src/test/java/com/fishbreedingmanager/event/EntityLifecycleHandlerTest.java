package com.fishbreedingmanager.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fishbreedingmanager.attachment.ModAttachments;
import com.fishbreedingmanager.breeding.ActiveLoveIndex;
import com.fishbreedingmanager.breeding.BreedingRule;
import com.fishbreedingmanager.breeding.BreedingRuleManager;
import com.fishbreedingmanager.breeding.BreedingState;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * 验证实体加载恢复不会在规则快照初始化前破坏持久化 Love，也不会给无关实体创建空 Attachment。
 */
class EntityLifecycleHandlerTest {
    /** 每个测试后清理生产单例索引，防止 UUID 跨测试泄漏。 */
    @AfterEach
    void clearIndex() {
        ActiveLoveIndex.INSTANCE.clearAll();
    }

    /**
     * 启动早期 Snapshot 尚未安装时必须完全跳过实体，不读取或清除附件状态。
     */
    @Test
    void leavesEntityUntouchedBeforeRuleManagerInitialization() {
        ServerLevel level = mock(ServerLevel.class);
        Entity entity = mock(Entity.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        when(manager.isInitialized()).thenReturn(false);

        boolean restored = EntityLifecycleHandler.restoreEntity(level, entity, manager);

        assertFalse(restored);
        verifyNoInteractions(entity);
    }

    /**
     * 已初始化后，无 FBM Attachment 的普通实体也只能执行只读查询，不能通过 {@code getData} 创建默认状态。
     */
    @Test
    void doesNotCreateAttachmentForUnrelatedEntity() {
        ServerLevel level = mock(ServerLevel.class);
        Entity entity = mock(Entity.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        when(manager.isInitialized()).thenReturn(true);
        doReturn(null).when(entity).getExistingDataOrNull(ModAttachments.BREEDING_STATE);

        boolean restored = EntityLifecycleHandler.restoreEntity(level, entity, manager);

        assertFalse(restored);
        verify(entity).getExistingDataOrNull(ModAttachments.BREEDING_STATE);
        verify(entity, never()).getData(ModAttachments.BREEDING_STATE);
    }

    /**
     * 已初始化且规则启用时，应恢复有效 Love 索引并清除旧会话 mate。
     */
    @Test
    void restoresExistingActiveLoveAfterInitialization() {
        ServerLevel level = mock(ServerLevel.class);
        Entity entity = mock(Entity.class);
        EntityType<?> type = mock(EntityType.class);
        UUID entityId = UUID.randomUUID();
        BreedingState state = new BreedingState();
        state.enterLove(100L, 100L);
        state.setMate(UUID.randomUUID());
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        BreedingRule rule = new BreedingRule(ResourceLocation.parse("minecraft:cod"),
                List.of(ResourceLocation.parse("minecraft:kelp")), List.of(), 600, 1200, true);
        when(manager.isInitialized()).thenReturn(true);
        doReturn(state).when(entity).getExistingDataOrNull(ModAttachments.BREEDING_STATE);
        doReturn(type).when(entity).getType();
        when(entity.getUUID()).thenReturn(entityId);
        when(level.getGameTime()).thenReturn(150L);
        when(manager.find(type)).thenReturn(rule);

        boolean restored = EntityLifecycleHandler.restoreEntity(level, entity, manager);

        assertTrue(restored);
        assertTrue(state.isInLove(150L));
        assertTrue(ActiveLoveIndex.INSTANCE.snapshot(level).contains(entityId));
    }
}
