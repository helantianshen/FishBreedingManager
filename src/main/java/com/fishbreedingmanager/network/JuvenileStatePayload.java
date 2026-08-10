package com.fishbreedingmanager.network;

import java.util.UUID;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.breeding.BreedingState;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端到客户端的幼体状态通知，声明某个实体将在指定绝对游戏刻成年。
 *
 * <p>NeoForge 1.21.1 不会自动把实体数据附件同步到客户端，因此服务端在子代生成及玩家开始追踪实体时发送本包。
 * 客户端只需要 {@code entityUuid → adultAt} 即可实现成年前固定 {@code 0.5F}、成年时瞬间恢复 {@code 1.0F}
 * 的视觉效果，不发送可由客户端误解为平滑成长依据的冗余时长。
 *
 * @param entityUuid 幼体实体的稳定 UUID
 * @param adultAt 实体成年的绝对游戏刻
 */
public record JuvenileStatePayload(UUID entityUuid, long adultAt) implements CustomPacketPayload {
    /** 本数据包在 FBM 命名空间中的网络类型标识。 */
    public static final CustomPacketPayload.Type<JuvenileStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FishBreedingManager.MOD_ID, "juvenile_state"));

    /**
     * 数据包二进制编解码器。
     *
     * <p>UUID 使用字符串编码以避开小版本间 UUID 辅助编解码 API 的差异，成年时刻使用变长长整数降低常见数据大小。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, JuvenileStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, p -> p.entityUuid().toString(),
                    ByteBufCodecs.VAR_LONG, JuvenileStatePayload::adultAt,
                    JuvenileStatePayload::fromStream);

    /**
     * 从服务端权威附件状态创建数据包。
     *
     * @param entityUuid 幼体实体 UUID
     * @param state 实体当前的 {@link BreedingState}
     * @return 采用附件绝对成年时刻的新数据包
     */
    public static JuvenileStatePayload fromState(UUID entityUuid, BreedingState state) {
        return new JuvenileStatePayload(entityUuid, state.getAdultAt());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static JuvenileStatePayload fromStream(String uuidStr, long adultAt) {
        return new JuvenileStatePayload(UUID.fromString(uuidStr), adultAt);
    }
}
