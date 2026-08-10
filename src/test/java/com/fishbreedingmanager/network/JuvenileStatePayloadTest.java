package com.fishbreedingmanager.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fishbreedingmanager.breeding.BreedingState;

/**
 * 验证 {@link JuvenileStatePayload} 只传输客户端渲染所需的权威状态。
 */
class JuvenileStatePayloadTest {
    /**
     * 数据包必须直接采用附件中的绝对成年时刻，不能以发送时刻重新计算成长周期。
     */
    @Test
    void fromStateUsesPersistedAdultAt() {
        UUID entityUuid = UUID.randomUUID();
        BreedingState state = new BreedingState();
        state.markJuvenile(500L, 200L);

        JuvenileStatePayload payload = JuvenileStatePayload.fromState(entityUuid, state);

        assertEquals(entityUuid, payload.entityUuid());
        assertEquals(700L, payload.adultAt());
    }
}
