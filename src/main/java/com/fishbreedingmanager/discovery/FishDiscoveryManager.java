package com.fishbreedingmanager.discovery;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fishbreedingmanager.persistence.WorldBreedingData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** 每个逻辑服务器独立持有最近一次成功构建的鱼类发现快照。 */
public final class FishDiscoveryManager {
    private static final Map<MinecraftServer, FishDiscoveryManager> MANAGERS =
            new ConcurrentHashMap<>();
    private static final MinecraftDiscoverySource MINECRAFT_SOURCE = new MinecraftDiscoverySource();

    private final Supplier<List<LoadedModInfo>> loadedModsSource;
    private final Supplier<List<EntityDiscoveryInput>> entitySource;
    private final Function<MinecraftServer, Set<ResourceLocation>> importedEntitySource;
    private volatile DiscoverySnapshot snapshot = DiscoverySnapshot.EMPTY;

    FishDiscoveryManager() {
        this(MINECRAFT_SOURCE::captureLoadedMods, MINECRAFT_SOURCE::captureEntities,
                server -> WorldBreedingData.get(server).getImportedEntities());
    }

    FishDiscoveryManager(Supplier<List<LoadedModInfo>> loadedModsSource,
            Supplier<List<EntityDiscoveryInput>> entitySource,
            Function<MinecraftServer, Set<ResourceLocation>> importedEntitySource) {
        this.loadedModsSource = Objects.requireNonNull(loadedModsSource, "loadedModsSource");
        this.entitySource = Objects.requireNonNull(entitySource, "entitySource");
        this.importedEntitySource = Objects.requireNonNull(importedEntitySource,
                "importedEntitySource");
    }

    /**
     * 返回与当前服务器生命周期绑定的发现管理器。
     *
     * @param server 当前逻辑服务器
     * @return 该服务器唯一的发现管理器
     */
    public static FishDiscoveryManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, key -> new FishDiscoveryManager());
    }

    /**
     * 清理已停止服务器的内存快照。
     *
     * @param server 正在停止的逻辑服务器
     */
    public static void remove(MinecraftServer server) {
        MANAGERS.remove(server);
    }

    /**
     * 返回最近一次成功发布的不可变快照。
     *
     * @return 当前发现快照
     */
    public DiscoverySnapshot snapshot() {
        return snapshot;
    }

    /**
     * 从当前 NeoForge 环境和世界持久化导入集合重建候选快照。
     *
     * @param server 当前逻辑服务器
     * @return 成功状态、候选数或失败原因
     */
    public DiscoveryReloadResult reload(MinecraftServer server) {
        return rebuild(() -> new FishDiscoveryEngine().discover(
                loadedModsSource.get(), entitySource.get(), importedEntitySource.apply(server)));
    }

    /**
     * 先完整构建局部快照，再通过单次 volatile 写发布；失败时保留旧引用。
     */
    DiscoveryReloadResult rebuild(Supplier<DiscoverySnapshot> builder) {
        try {
            DiscoverySnapshot next = Objects.requireNonNull(builder.get(), "discovery snapshot");
            snapshot = next;
            return DiscoveryReloadResult.success(next.candidates().size());
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            return DiscoveryReloadResult.failure(message != null
                    ? message : exception.getClass().getSimpleName());
        }
    }
}
