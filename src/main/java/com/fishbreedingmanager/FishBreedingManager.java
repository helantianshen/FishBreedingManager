package com.fishbreedingmanager;

import com.fishbreedingmanager.attachment.ModAttachments;
import com.fishbreedingmanager.breeding.BreedingRuleManager;
import com.fishbreedingmanager.breeding.ReloadResult;
import com.fishbreedingmanager.breeding.WorldBreedingService;
import com.fishbreedingmanager.command.FBMCommands;
import com.fishbreedingmanager.network.ModNetworking;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * Main mod class for Fish Breeding Manager (FBM).
 *
 * <p>FBM is a world-level, data-driven, hot-reloadable fish breeding management framework for
 * Minecraft 1.21.1 NeoForge. It adds configurable vanilla-style breeding behavior to vanilla and
 * third-party fish entities without modifying their Java classes.
 *
 * <p>Architecture (see {@code docs/Project_Analysis_and_Summary.md}):
 * <ul>
 *   <li><b>Rule layer</b> (belongs to the world): {@code WorldBreedingData} (SavedData) +
 *       {@code BreedingRuleManager} holding an immutable {@code BreedingRuleSnapshot} swapped atomically.</li>
 *   <li><b>Runtime engine</b> (centralized, server-authoritative): {@code BreedingController} driven
 *       by {@code LevelTickEvent}, plus {@code EntityInteractionHandler} for feeding.</li>
 *   <li><b>State layer</b> (belongs to each entity): {@code BreedingState} attached via a Data Attachment.</li>
 * </ul>
 * Rules are queried dynamically at behavior time; they are never cached on entities, so hot reload
 * affects existing entities immediately. Already-running timers (cooldown/growth) are not retroactively
 * recomputed on reload.
 */
@Mod(FishBreedingManager.MOD_ID)
public final class FishBreedingManager {
    public static final String MOD_ID = "fishbreedingmanager";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FishBreedingManager(IEventBus modEventBus, ModContainer modContainer) {
        // Register DeferredRegisters that belong on the mod event bus.
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);

        // Register mod-bus listeners (networking payloads, etc.).
        modEventBus.addListener(ModNetworking::register);

        // Game-bus event handlers (feeding, ticking, command registration, entity lifecycle).
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.addListener(FBMCommands::register);
    }

    @SubscribeEvent
    private void onServerStarting(ServerStartingEvent event) {
        // 首次加载也走统一校验服务；新存档会在 WorldBreedingData 创建时种入四种默认鱼规则。
        ReloadResult result = WorldBreedingService.get().reload(event.getServer());
        if (!result.success()) {
            LOGGER.error("FBM 初始规则加载失败，未安装无效配置: {}", result.error());
        }
    }

    @SubscribeEvent
    private void onServerStopping(ServerStoppingEvent event) {
        // Drop the per-server manager so a later world load starts clean (no stale snapshot leak).
        BreedingRuleManager.remove(event.getServer());
    }
}
