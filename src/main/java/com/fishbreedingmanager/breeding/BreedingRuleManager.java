package com.fishbreedingmanager.breeding;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * 创建空快照管理器。
     *
     * <p>构造器保持包级可见，生产代码通过 {@link #get(MinecraftServer)} 获取实例，同包测试可创建隔离管理器。
     */
    BreedingRuleManager() {
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
     * 安装已经完整校验且不可变的运行时快照。
     *
     * <p>入口保持包级可见，强制生产调用方经由 {@link WorldBreedingService} 完成候选全集校验后再替换。
     * {@code volatile} 写入使后续行为线程立即看见完整的新快照。
     *
     * @param next 下一份权威运行时快照
     */
    void install(BreedingRuleSnapshot next) {
        snapshot = next;
    }
}
