package com.fishbreedingmanager.network;

import com.fishbreedingmanager.client.ClientJuvenileSync;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 注册幼体状态同步和双向管理协议
 *
 * <p>服务端是规则与幼体状态的权威；幼体附件通过 {@link JuvenileStatePayload} 显式同步给追踪客户端
 * 管理请求与回复共用版本协商，物理客户端从组成根注入管理回复处理器，避免通用注册入口提前解析 GUI 类
 */
public final class ModNetworking {
    private static IPayloadHandler<ManagementReplyPayload> managementClient = (payload, context) -> { };

    /**
     * 客户端组成根在网络注册前提供处理器，专用服务器不解析 GUI 类型
     *
     * @param handler 客户端回复处理器
     */
    public static void setManagementClient(IPayloadHandler<ManagementReplyPayload> handler) {
        managementClient = handler;
    }
    private ModNetworking() {
    }

    /**
     * 注册幼体状态、管理请求和管理回复的 Payload 与处理器
     *
     * <p>管理写入通过服务端玩家权限及修订号校验；客户端回复处理器由客户端组成根提供
     *
     * @param event NeoForge Payload 处理器注册事件
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("3");
        registrar.playToServer(ManagementRequestPayload.TYPE, ManagementRequestPayload.CODEC, ManagementServer::handle);
        registrar.playToClient(ManagementReplyPayload.TYPE, ManagementReplyPayload.CODEC, managementClient);
        // 服务端到客户端：通知某个被追踪实体在何时瞬间恢复成年尺寸
        registrar.playToClient(
                JuvenileStatePayload.TYPE,
                JuvenileStatePayload.STREAM_CODEC,
                ClientJuvenileSync::handleJuvenileState);
    }
}
