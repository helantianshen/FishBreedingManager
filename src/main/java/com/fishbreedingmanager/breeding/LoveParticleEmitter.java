package com.fishbreedingmanager.breeding;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * 通过逻辑服务端向附近客户端发送 FBM Love 爱心粒子。
 *
 * <p>FBM 的目标实体不限于 {@code Animal}；例如鳕鱼只继承 {@code AbstractFish/Mob}，不会处理原版
 * {@code Animal} 使用的实体事件 {@code 18}。直接调用 {@link ServerLevel#sendParticles} 不依赖实体继承关系，
 * 并且在单人集成服务器、局域网世界和独立服务器上使用同一套客户端同步机制。
 */
public final class LoveParticleEmitter {
    /** 每次反馈发送的爱心数量。 */
    private static final int PARTICLE_COUNT = 7;
    /** 三个坐标轴使用的随机散布范围。 */
    private static final double SPREAD = 0.3D;
    /** 粒子中心相对实体包围盒顶部的额外高度。 */
    private static final double HEIGHT_OFFSET = 0.25D;

    private LoveParticleEmitter() {
    }

    /**
     * 在实体上方生成一组爱心，并由服务端同步给附近玩家。
     *
     * @param level 实体所在的逻辑服务端世界
     * @param entity 需要显示 Love 反馈的实体
     */
    public static void emit(ServerLevel level, Entity entity) {
        level.sendParticles(
                ParticleTypes.HEART,
                entity.getX(),
                entity.getY() + entity.getBbHeight() + HEIGHT_OFFSET,
                entity.getZ(),
                PARTICLE_COUNT,
                SPREAD,
                SPREAD,
                SPREAD,
                0.0D);
    }
}
