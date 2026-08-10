package com.fishbreedingmanager.network;

import java.util.UUID;

import com.fishbreedingmanager.FishBreedingManager;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端→客户端通知, 某实体是幼体, 在给定 game-time tick 成年 
 *
 * <p>NeoForge 1.21.1 中实体 data attachment 不会自动同步到客户端, 
 * 故服务端在子代生成时广播此包 也可在 tracking 时重发 
 * 客户端存 {@code entityUUID → adultAt} 供幼体视觉缩放渲染钩子使用 
 * 同时发送成长时长, 使客户端可计算平滑的 0.5→1.0 缩放 
 *
 * @param entityUuid  幼体实体 
 * @param adultAt     成年的绝对 game-time tick 
 * @param growthTicks 原始成长时长, 用于按比例缩放 
 */
public record JuvenileStatePayload(UUID entityUuid, long adultAt, int growthTicks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<JuvenileStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FishBreedingManager.MOD_ID, "juvenile_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JuvenileStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    // UUID 以字符串发送, 跨 1.21.x 小版本 API 稳定 
                    ByteBufCodecs.STRING_UTF8, p -> p.entityUuid().toString(),
                    ByteBufCodecs.VAR_LONG, JuvenileStatePayload::adultAt,
                    ByteBufCodecs.VAR_INT, JuvenileStatePayload::growthTicks,
                    JuvenileStatePayload::fromStream);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static JuvenileStatePayload fromStream(String uuidStr, long adultAt, int growthTicks) {
        return new JuvenileStatePayload(UUID.fromString(uuidStr), adultAt, growthTicks);
    }
}
