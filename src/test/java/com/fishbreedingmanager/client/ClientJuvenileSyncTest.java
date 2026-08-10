package com.fishbreedingmanager.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fishbreedingmanager.network.JuvenileStatePayload;

/**
 * 验证客户端幼体缓存采用固定半尺寸并在成年时刻瞬间切换。
 */
class ClientJuvenileSyncTest {
    /**
     * 每个测试后清空静态缓存，避免测试执行顺序影响结果。
     */
    @AfterEach
    void clearCache() {
        ClientJuvenileSync.clear();
    }

    /**
     * 收到服务端状态后，成年前始终为 {@code 0.5F}，到达截止时刻立即变为 {@code 1.0F}。
     */
    @Test
    void scaleStaysFixedUntilAdultAt() {
        UUID entityUuid = UUID.randomUUID();
        ClientJuvenileSync.remember(new JuvenileStatePayload(entityUuid, 300L));

        assertEquals(0.5F, ClientJuvenileSync.scaleFor(entityUuid, 100L));
        assertEquals(0.5F, ClientJuvenileSync.scaleFor(entityUuid, 299L));
        assertEquals(1.0F, ClientJuvenileSync.scaleFor(entityUuid, 300L));
        assertEquals(1.0F, ClientJuvenileSync.scaleFor(entityUuid, 301L));
    }

    /**
     * 清空缓存后所有未知实体都应按成年尺寸渲染。
     */
    @Test
    void clearRemovesRememberedState() {
        UUID entityUuid = UUID.randomUUID();
        ClientJuvenileSync.remember(new JuvenileStatePayload(entityUuid, 300L));

        ClientJuvenileSync.clear();

        assertEquals(1.0F, ClientJuvenileSync.scaleFor(entityUuid, 200L));
    }
}
