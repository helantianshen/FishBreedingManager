package com.fishbreedingmanager.breeding;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.persistence.WorldBreedingData;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;

/**
 * 单服务器运行时持有当前 {@link BreedingRuleSnapshot} 
 *
 * <p>快照以 {@code volatile} 引用持有, {@link #reload} 时原子替换 需求§33 
 * 所有行为通过 {@link #find} 动态查询快照, 规则绝不缓存到实体, 
 * 故热重载立即影响现存实体 需求§34 
 *
 * <p>reload 失败 解析/校验错误 时保留旧快照, 不破坏当前正常规则 
 * 已运行的计时器 冷却/成长 存于实体本身, 此处绝不重算 需求§38/§39 
 */
public final class BreedingRuleManager {
    private static final Map<MinecraftServer, BreedingRuleManager> MANAGERS = new ConcurrentHashMap<>();

    private volatile BreedingRuleSnapshot snapshot = BreedingRuleSnapshot.EMPTY;

    private BreedingRuleManager() {
    }

    /** 返回 给定服务器 的规则管理器, 不存在则创建 */
    public static BreedingRuleManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, s -> new BreedingRuleManager());
    }

    /** 移除停止中服务器的管理器, 防止跨世界加载泄漏 */
    public static void remove(MinecraftServer server) {
        MANAGERS.remove(server);
    }

    public BreedingRuleSnapshot snapshot() {
        return snapshot;
    }

    public BreedingRule find(ResourceLocation entityId) {
        return snapshot.rules().get(entityId);
    }

    public BreedingRule find(EntityType<?> entityType) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return id != null ? find(id) : null;
    }

    public boolean hasRule(ResourceLocation entityId) {
        return snapshot.rules().containsKey(entityId);
    }

    /**
     * 重读世界 {@link WorldBreedingData}, 构建新不可变快照并原子替换 
     * 失败时保留旧快照 
     */
    public ReloadResult reload(MinecraftServer server) {
        try {
            WorldBreedingData data = WorldBreedingData.get(server);
            BreedingRuleSnapshot next = data.buildSnapshot();
            this.snapshot = next; // 原子替换 
            FishBreedingManager.LOGGER.info("FBM reloaded: {} rule(s), {} imported entit(ies)",
                    next.rules().size(), next.importedEntities().size());
            return ReloadResult.success(next.rules().size());
        } catch (Exception e) {
            FishBreedingManager.LOGGER.error("FBM reload failed; keeping previous snapshot", e);
            return ReloadResult.failure(e.getMessage());
        }
    }
}
