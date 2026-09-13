package com.fishbreedingmanager.server;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.breeding.ActiveLoveIndex;
import com.fishbreedingmanager.breeding.BreedingRuleManager;
import com.fishbreedingmanager.breeding.ReloadResult;
import com.fishbreedingmanager.breeding.WorldBreedingService;
import com.fishbreedingmanager.discovery.DiscoveryDiagnostics;
import com.fishbreedingmanager.discovery.DiscoveryReloadResult;
import com.fishbreedingmanager.discovery.FishDiscoveryManager;
import com.fishbreedingmanager.event.EntityLifecycleHandler;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 协调单个服务器会话的规则加载、自动发现、实体恢复与清理生命周期。
 *
 * <p>本处理器持有游戏总线的事件时序，使 Mod 入口保持为纯注册组成根。所有可变运行时服务仍然按当前
 * {@link MinecraftServer} 隔离，并在该服务器停止时清空，避免同一进程内下一个存档复用上一会话的状态。
 */
public final class ServerLifecycleHandler {
    /** 创建由 Mod 组成根注册的无状态服务器生命周期处理器。 */
    public ServerLifecycleHandler() {
    }

    /**
     * 加载持久化规则集合，并恢复在快照可用之前就已经加入世界的实体。
     *
     * @param event 当前服务器启动中事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ReloadResult result = WorldBreedingService.get().reload(event.getServer());
        if (!result.success()) {
            FishBreedingManager.LOGGER.error(
                    "FBM 初始规则加载失败，未安装无效配置: {}", result.error());
            return;
        }
        EntityLifecycleHandler.restoreLoadedEntities(event.getServer());
    }

    /**
     * 在服务器完成启动后构建第一份发现快照。
     *
     * @param event 当前服务器已启动事件
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        rebuildDiscovery(event.getServer(), "server-started");
    }

    /**
     * 服务端数据 Tag 重载时重建发现快照。
     *
     * @param event Tag 更新事件及其触发原因
     */
    @SubscribeEvent
    public void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getLevel(Level.OVERWORLD) == null) {
            return;
        }
        server.execute(() -> rebuildDiscovery(server, "server-tags-updated"));
    }

    private static void rebuildDiscovery(MinecraftServer server, String trigger) {
        FishDiscoveryManager discoveryManager = FishDiscoveryManager.get(server);
        DiscoveryReloadResult result = discoveryManager.reload(server);
        if (result.success()) {
            FishBreedingManager.LOGGER.info(
                    "FBM fish discovery rebuilt: trigger={}, candidates={}",
                    trigger, result.candidateCount());
            DiscoveryDiagnostics.sourceSummaries(discoveryManager.snapshot()).forEach(summary ->
                    FishBreedingManager.LOGGER.info("FBM fish source discovered: {}", summary));
        } else {
            FishBreedingManager.LOGGER.error(
                    "FBM fish discovery failed; previous snapshot retained: trigger={}, reason={}",
                    trigger, result.error());
        }
    }

    /**
     * 清除绝不允许泄漏到同一进程内下一个服务器或世界的运行时状态。
     *
     * @param event 当前服务器停止中事件
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ActiveLoveIndex.INSTANCE.clearAll();
        BreedingRuleManager.remove(event.getServer());
        FishDiscoveryManager.remove(event.getServer());
    }
}
