package com.fishbreedingmanager.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端请求在 JSON 解析前受到字节上限约束
 *
 * @param data 有界 UTF-8 内容
 */
public record ManagementRequestPayload(byte[] data) implements CustomPacketPayload {
    /** 服务端管理入口类型 */
    public static final Type<ManagementRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("fishbreedingmanager", "management_request"));
    /** 请求最多占用 24 KiB，低于服务端自定义包上限 */
    public static final StreamCodec<RegistryFriendlyByteBuf, ManagementRequestPayload> CODEC = new StreamCodec<>() {
        @Override public ManagementRequestPayload decode(RegistryFriendlyByteBuf buf) {
            return new ManagementRequestPayload(buf.readByteArray(24576));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, ManagementRequestPayload value) {
            if (value.data.length > 24576) throw new IllegalArgumentException("Request too large");
            buf.writeByteArray(value.data);
        }
    };
    @Override public Type<ManagementRequestPayload> type() { return TYPE; }
}
