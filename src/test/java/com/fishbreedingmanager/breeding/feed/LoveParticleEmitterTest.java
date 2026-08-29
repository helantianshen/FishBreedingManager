package com.fishbreedingmanager.breeding.feed;

import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * 验证 FBM 爱心反馈通过服务端粒子 API 同步，不依赖目标实体处理 {@code Animal} 专用事件。
 */
class LoveParticleEmitterTest {
    /**
     * 爱心应生成在实体上方，并使用固定数量与散布范围发送给附近客户端。
     */
    @Test
    void emitsHeartParticlesAboveEntityThroughServerLevel() {
        ServerLevel level = mock(ServerLevel.class);
        Entity entity = mock(Entity.class);
        when(entity.getX()).thenReturn(12.0D);
        when(entity.getY()).thenReturn(30.0D);
        when(entity.getZ()).thenReturn(-4.0D);
        when(entity.getBbHeight()).thenReturn(0.6F);

        LoveParticleEmitter.emit(level, entity);

        verify(level).sendParticles(
                eq(ParticleTypes.HEART),
                eq(12.0D),
                doubleThat(value -> Math.abs(value - 30.85D) < 0.000001D),
                eq(-4.0D),
                eq(7),
                eq(0.3D),
                eq(0.3D),
                eq(0.3D),
                eq(0.0D));
    }
}
