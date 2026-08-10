package com.fishbreedingmanager.client;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fishbreedingmanager.network.JuvenileStatePayload;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端幼体状态缓存，保存服务端同步的绝对成年时刻。
 *
 * <p>渲染钩子通过实体 UUID 查询缓存：在 {@code adultAt} 之前固定返回 {@code 0.5F}，到达成年时刻后立即返回
 * {@code 1.0F} 并惰性删除缓存。缓存不是权威游戏状态，只服务于客户端视觉表现。
 */
public final class ClientJuvenileSync {
    /** 实体 UUID 到绝对成年游戏刻的并发映射。 */
    private static final ConcurrentHashMap<UUID, Long> JUVENILES = new ConcurrentHashMap<>();

    private ClientJuvenileSync() {
    }

    /**
     * 处理服务端幼体状态包。
     *
     * @param payload 服务端权威幼体状态
     * @param context NeoForge 网络处理上下文；当前状态写入不依赖额外上下文操作
     */
    public static void handleJuvenileState(JuvenileStatePayload payload, IPayloadContext context) {
        remember(payload);
    }

    /**
     * 记录一个服务端同步的幼体状态。
     *
     * <p>保持包级可见性，使同包测试能够验证缓存行为，而无需暴露额外的公共运行时 API。
     *
     * @param payload 待缓存的幼体状态包
     */
    static void remember(JuvenileStatePayload payload) {
        JUVENILES.put(payload.entityUuid(), payload.adultAt());
    }

    /**
     * 查询实体当前的客户端视觉缩放。
     *
     * @param entityUuid 实体 UUID
     * @param now 客户端世界当前绝对游戏刻
     * @return 已知且尚未成年的实体返回 {@code 0.5F}，未知或已成年实体返回 {@code 1.0F}
     */
    public static float scaleFor(UUID entityUuid, long now) {
        Long adultAt = JUVENILES.get(entityUuid);
        if (adultAt == null) {
            return 1.0F;
        }
        if (now >= adultAt) {
            JUVENILES.remove(entityUuid);
            return 1.0F;
        }
        return 0.5F;
    }

    /**
     * 删除单个实体的客户端缓存，通常在实体卸载时调用。
     *
     * @param entityUuid 已卸载实体 UUID
     */
    public static void forget(UUID entityUuid) {
        JUVENILES.remove(entityUuid);
    }

    /**
     * 清空当前连接的全部幼体缓存。
     *
     * <p>客户端退出世界时必须调用，避免进入另一存档或服务器后复用旧 UUID 对应的视觉状态。
     */
    public static void clear() {
        JUVENILES.clear();
    }
}
