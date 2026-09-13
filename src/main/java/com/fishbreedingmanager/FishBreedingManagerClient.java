package com.fishbreedingmanager;

import com.fishbreedingmanager.client.ClientJuvenileSync;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * Fish Breeding Manager 的物理客户端入口。
 *
 * <p>该类只在物理客户端加载，负责注册跨连接生命周期监听。幼体缓存必须在退出世界时清空，防止进入另一个存档或
 * 服务器后，同一 UUID 错误复用上一连接的缩放状态；实体离开客户端世界时也要及时删除单条记录，避免长时间会话中
 * 因幼体死亡或移出追踪范围而无限累积。重新进入追踪范围时，服务端的
 * {@code PlayerEvent.StartTracking} 会补发权威成年时刻。
 */
@Mod(value = FishBreedingManager.MOD_ID, dist = Dist.CLIENT)
public final class FishBreedingManagerClient {
    /**
     * 注册客户端连接与实体生命周期事件。
     *
     * @param container 当前 Mod 容器；NeoForge 通过构造器注入
     */
    public FishBreedingManagerClient(ModContainer container) {
        NeoForge.EVENT_BUS.addListener(FishBreedingManagerClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(FishBreedingManagerClient::onEntityLeaveLevel);
    }

    /**
     * 客户端退出当前连接时清空非权威幼体渲染缓存。
     *
     * @param event NeoForge 客户端退出事件
     */
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientJuvenileSync.clear();
    }

    /**
     * 实体离开客户端世界时删除其渲染缓存。
     *
     * <p>该事件在服务端同样触发，因此必须显式过滤逻辑端，只处理客户端世界。
     *
     * @param event NeoForge 实体离开世界事件
     */
    private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            ClientJuvenileSync.forget(event.getEntity().getUUID());
        }
    }
}
