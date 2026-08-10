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

    /**
     * 注册服务端到客户端的幼体状态 Payload 与处理器。
     *
     * <p>协议版本 {@code 1} 当前只传输实体 UUID 与绝对成年时刻；客户端不会据此参与任何权威状态判断。
     *
     * @param event NeoForge Payload 处理器注册事件
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // 服务端到客户端：通知某个被追踪实体在何时瞬间恢复成年尺寸。
        registrar.playToClient(
                JuvenileStatePayload.TYPE,
                JuvenileStatePayload.STREAM_CODEC,
                ClientJuvenileSync::handleJuvenileState);
    }
}
