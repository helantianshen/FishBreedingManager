package com.fishbreedingmanager.network;

import com.fishbreedingmanager.client.ClientJuvenileSync;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 注册 FBM 的 {@code CustomPacketPayload} 
 *
 * <p>服务端是繁殖规则与幼体状态的唯一权威, NeoForge 1.21.1 中实体 data attachment 不会自动同步 
 * 故服务端通过 {@link JuvenileStatePayload} 显式向 tracking 客户端广播幼体状态 需求§10 客户端视觉缩放 
 */
public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // 服务端 → 客户端, 通知某子代是幼体 用于视觉缩放 
        registrar.playToClient(
                JuvenileStatePayload.TYPE,
                JuvenileStatePayload.STREAM_CODEC,
                ClientJuvenileSync::handleJuvenileState);
    }
}
