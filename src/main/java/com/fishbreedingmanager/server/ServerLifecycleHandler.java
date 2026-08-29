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
 * Coordinates FBM's per-server rule, discovery, entity restoration, and cleanup lifecycle.
 *
 * <p>This handler owns game-bus event timing while the Mod entrypoint remains a small composition root. All mutable
 * runtime services are still scoped to the current {@link MinecraftServer} and are cleared when that server stops.
 */
public final class ServerLifecycleHandler {
    /** Creates the stateless server lifecycle event handler registered by the Mod composition root. */
    public ServerLifecycleHandler() {
    }

    /**
     * Loads the persisted rule set and restores entities that joined before the snapshot was available.
     *
     * @param event current server starting event
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
     * Builds the first discovery snapshot after the server has completed startup.
     *
     * @param event current server started event
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        rebuildDiscovery(event.getServer(), "server-started");
    }

    /**
     * Rebuilds discovery when server data tags are reloaded.
     *
     * @param event tag update event and its update cause
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
     * Clears state that must never leak into the next server or world opened in the same process.
     *
     * @param event current server stopping event
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ActiveLoveIndex.INSTANCE.clearAll();
        BreedingRuleManager.remove(event.getServer());
        FishDiscoveryManager.remove(event.getServer());
    }
}
