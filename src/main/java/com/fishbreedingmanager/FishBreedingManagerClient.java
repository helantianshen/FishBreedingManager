package com.fishbreedingmanager;

import com.fishbreedingmanager.client.ClientJuvenileSync;
import com.fishbreedingmanager.client.gui.ClientManagement;
import net.neoforged.bus.api.IEventBus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * Fish Breeding Manager 的物理客户端入口
 *
 * <p>该类只在物理客户端加载，负责 GUI 回复处理、可修改的原版按键以及跨连接生命周期监听
 * 幼体缓存必须在退出世界时清空，防止进入另一个存档或
 * 服务器后，同一 UUID 错误复用上一连接的缩放状态；实体离开客户端世界时也要及时删除单条记录，避免长时间会话中因幼体死亡或移出追踪范围而无限累积
 * 重新进入追踪范围时，服务端的 {@code PlayerEvent.StartTracking} 会补发权威成年时刻
 */
@Mod(value = FishBreedingManager.MOD_ID, dist = Dist.CLIENT)
public final class FishBreedingManagerClient {
    /**
     * 注册 GUI 网络处理、按键、客户端 tick 与连接生命周期事件
     *
     * @param container 当前 Mod 容器；NeoForge 通过构造器注入
     * @param modEventBus 用于注册客户端原版按键的 Mod 事件总线
     */
    public FishBreedingManagerClient(ModContainer container, IEventBus modEventBus) {
        com.fishbreedingmanager.network.ModNetworking.setManagementClient(ClientManagement::handle);
        modEventBus.addListener(ClientManagement::registerKeys);
        NeoForge.EVENT_BUS.addListener(ClientManagement::tick);
        NeoForge.EVENT_BUS.addListener(FishBreedingManagerClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(FishBreedingManagerClient::onEntityLeaveLevel);
    }

    /**
     * 客户端退出当前连接时清空幼体渲染缓存与 GUI 会话
     *
     * @param event NeoForge 客户端退出事件
     */
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientJuvenileSync.clear();
        ClientManagement.clear();
    }

    /**
     * 实体离开客户端世界时删除其渲染缓存
     *
     * <p>该事件在服务端同样触发，因此必须显式过滤逻辑端，只处理客户端世界
     *
     * @param event NeoForge 实体离开世界事件
     */
    private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            ClientJuvenileSync.forget(event.getEntity().getUUID());
        }
    }
}
