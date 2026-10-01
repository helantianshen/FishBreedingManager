package com.fishbreedingmanager.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 目录按 UTF-8 字节分块，客户端依据请求 ID 与序号重组
 * @param request 对应请求序号
 * @param kind 回复类型
 * @param index 从零开始的块序号
 * @param total 完整目录的块总数
 * @param data 受上限约束的内容字节
 */
public record ManagementReplyPayload(long request, String kind, int index, int total, byte[] data)
        implements CustomPacketPayload {
    /** 客户端管理回复类型 */
    public static final Type<ManagementReplyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("fishbreedingmanager", "management_reply"));
    /** 单块最多 24 KiB，序号与总数由客户端再次校验 */
    public static final StreamCodec<RegistryFriendlyByteBuf, ManagementReplyPayload> CODEC = new StreamCodec<>() {
        @Override public ManagementReplyPayload decode(RegistryFriendlyByteBuf buf) {
            return new ManagementReplyPayload(buf.readVarLong(), buf.readUtf(16), buf.readVarInt(),
                    buf.readVarInt(), buf.readByteArray(24576));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, ManagementReplyPayload value) {
            if (value.data.length > 24576) throw new IllegalArgumentException("Reply too large");
            buf.writeVarLong(value.request); buf.writeUtf(value.kind, 16);
            buf.writeVarInt(value.index); buf.writeVarInt(value.total); buf.writeByteArray(value.data);
        }
    };
    @Override public Type<ManagementReplyPayload> type() { return TYPE; }
}
