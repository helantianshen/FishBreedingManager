package com.fishbreedingmanager.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.server.level.ServerLevel;

/**
 * 验证活跃 Love 索引按 {@link ServerLevel} 隔离，且迭代快照不会暴露内部可修改集合。
 */
class ActiveLoveIndexTest {
    /**
     * 同一实体 UUID 只应出现在被明确加入的 Level，删除后空 Level 条目也应被清理。
     */
    @Test
    void storesEachLevelIndependentlyAndReturnsSafeSnapshot() {
        ActiveLoveIndex index = new ActiveLoveIndex();
        ServerLevel first = mock(ServerLevel.class);
        ServerLevel second = mock(ServerLevel.class);
        UUID entityId = UUID.randomUUID();

        index.add(first, entityId);
        List<UUID> snapshot = index.snapshot(first);

        assertEquals(List.of(entityId), snapshot);
        assertTrue(index.snapshot(second).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(UUID.randomUUID()));

        index.remove(first, entityId);
        assertTrue(index.snapshot(first).isEmpty());
    }

    /**
     * 服务端切换时清空全部索引，不能让上一个存档的 UUID 泄漏到下一次会话。
     */
    @Test
    void clearAllRemovesServerSessionState() {
        ActiveLoveIndex index = new ActiveLoveIndex();
        ServerLevel level = mock(ServerLevel.class);
        index.add(level, UUID.randomUUID());

        index.clearAll();

        assertTrue(index.snapshot(level).isEmpty());
    }
}
