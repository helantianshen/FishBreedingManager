package com.fishbreedingmanager.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

/**
 * 验证 {@link BreedingState} 的持久化边界和绝对时间语义。
 *
 * <p>配偶 UUID 只属于当前运行会话中的导航关系，不能随实体附件保存；Love、冷却和成长截止时间则必须跨存档保留。
 */
class BreedingStateTest {
    /**
     * 实体重新加入世界时应保留仍有效的 Love 窗口，但必须放弃旧配偶并重新参与匹配。
     */
    @Test
    void prepareForLevelJoinPreservesActiveLoveAndClearsMate() {
        BreedingState state = new BreedingState();
        state.enterLove(100L, 40L);
        state.setMate(UUID.randomUUID());

        boolean active = state.prepareForLevelJoin(120L);

        assertTrue(active);
        assertTrue(state.isInLove(120L));
        assertNull(state.getMate());
    }

    /**
     * 已经过期的 Love 在实体加入世界时应被惰性结算，且不能进入活动索引。
     */
    @Test
    void prepareForLevelJoinExpiresOldLove() {
        BreedingState state = new BreedingState();
        state.enterLove(100L, 20L);
        state.setMate(UUID.randomUUID());

        boolean active = state.prepareForLevelJoin(120L);

        assertFalse(active);
        assertFalse(state.isInLove(120L));
        assertNull(state.getMate());
    }

    /**
     * 编码结果不能包含临时配偶关系，防止卸载前的 UUID 在读档后继续控制实体。
     */
    @Test
    void codecDoesNotPersistMate() {
        BreedingState state = new BreedingState();
        state.enterLove(10L, 30L);
        state.setMate(UUID.randomUUID());

        JsonElement encoded = BreedingState.CODEC.encodeStart(JsonOps.INSTANCE, state)
                .result()
                .orElseThrow();

        assertFalse(encoded.getAsJsonObject().has("mate"));
    }

    /**
     * 旧版本存档可能仍含 {@code mate} 字段；新解码器应忽略该字段并保留其余有效状态。
     */
    @Test
    void codecIgnoresLegacyMateField() {
        JsonElement legacy = JsonParser.parseString("""
                {
                  "in_love": true,
                  "love_until": 80,
                  "cooldown_until": 120,
                  "juvenile": false,
                  "adult_at": 0,
                  "mate": "00000000-0000-0000-0000-000000000001"
                }
                """);

        BreedingState decoded = BreedingState.CODEC.parse(JsonOps.INSTANCE, legacy)
                .result()
                .orElseThrow();

        assertTrue(decoded.isInLove(60L));
        assertEquals(80L, decoded.getLoveUntil());
        assertEquals(120L, decoded.getCooldownUntil());
        assertNull(decoded.getMate());
    }

    /**
     * 幼体在截止时刻之前保持幼体状态，到达截止时刻后立即成年，不做平滑过渡。
     *
     * <p>客户端的固定缩放由 {@code ClientJuvenileSync.scaleFor} 依据同一个绝对成年时刻决定，这里只锁定
     * 服务端权威状态的瞬时切换语义。
     */
    @Test
    void juvenileFlagFlipsInstantlyAtAdultAt() {
        BreedingState state = new BreedingState();
        state.markJuvenile(200L, 40L);

        assertEquals(240L, state.getAdultAt());
        assertTrue(state.isJuvenile(239L));
        assertFalse(state.isJuvenile(240L));
    }
}
