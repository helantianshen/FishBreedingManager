package com.fishbreedingmanager;

import com.fishbreedingmanager.client.ClientJuvenileSync;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Fish Breeding Manager 的物理客户端入口。
 *
 * <p>该类只在物理客户端加载，负责注册跨连接生命周期监听。幼体缓存必须在退出世界时清空，防止进入另一个存档或
 * 服务器后，同一 UUID 错误复用上一连接的缩放状态。
 */
@Mod(value = FishBreedingManager.MOD_ID, dist = Dist.CLIENT)
public final class FishBreedingManagerClient {
    /**
     * 注册客户端连接生命周期事件。
     *
     * @param container 当前 Mod 容器；NeoForge 通过构造器注入
     */
    public FishBreedingManagerClient(ModContainer container) {
        NeoForge.EVENT_BUS.addListener(FishBreedingManagerClient::onLoggingOut);
    }

    /**
     * 客户端退出当前连接时清空非权威幼体渲染缓存。
     *
     * @param event NeoForge 客户端退出事件
     */
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientJuvenileSync.clear();
    }
}
