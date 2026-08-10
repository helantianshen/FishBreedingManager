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
 * <p>快照以 {@code volatile} 引用持有，由 {@link WorldBreedingService#reload} 或事务修改入口原子替换。
 * 所有行为通过 {@link #find} 动态查询快照, 规则绝不缓存到实体, 
 * 故热重载立即影响现存实体 需求§34 
 *
 * <p>reload 失败 解析/校验错误 时保留旧快照, 不破坏当前正常规则 
 * 已运行的计时器 冷却/成长 存于实体本身, 此处绝不重算 需求§38/§39 
 */
public final class BreedingRuleManager {
    private static final Map<MinecraftServer, BreedingRuleManager> MANAGERS = new ConcurrentHashMap<>();

    private volatile BreedingRuleSnapshot snapshot = BreedingRuleSnapshot.EMPTY;
    private volatile boolean initialized;

    /**
     * 创建空快照管理器。
     *
     * <p>构造器保持包级可见，生产代码通过 {@link #get(MinecraftServer)} 获取实例，同包测试可创建隔离管理器。
     */
    BreedingRuleManager() {
    }

    /**
     * 返回与指定服务器生命周期绑定的运行时规则管理器。
     *
     * <p>管理器只持有不可变 {@link BreedingRuleSnapshot} 的易失引用。调用方不得把查询到的规则缓存到实体附件，
     * 否则现存实体将无法立即响应热更新。
     *
     * @param server 当前逻辑服务器
     * @return 该服务器唯一的规则管理器
     */
    public static BreedingRuleManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, s -> new BreedingRuleManager());
    }

    /**
     * 移除已经停止服务器的管理器，防止下一存档复用旧 Snapshot。
     *
     * @param server 正在停止的逻辑服务器
     */
    public static void remove(MinecraftServer server) {
        MANAGERS.remove(server);
    }

    /**
     * 返回当前不可变运行时快照。
     *
     * @return 最近一次成功安装的完整快照
     */
    public BreedingRuleSnapshot snapshot() {
        return snapshot;
    }

    /**
     * 按稳定实体注册表 ID 查询当前规则。
     *
     * @param entityId 实体注册表 ID
     * @return 当前规则；未配置时返回 {@code null}
     */
    public BreedingRule find(ResourceLocation entityId) {
        return snapshot.rules().get(entityId);
    }

    /**
     * 将实体类型转换为注册表 ID 后查询当前规则。
     *
     * @param entityType 当前实体类型
     * @return 当前规则；类型未注册或未配置时返回 {@code null}
     */
    public BreedingRule find(EntityType<?> entityType) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return id != null ? find(id) : null;
    }

    /**
     * 判断当前快照是否包含指定实体规则，不考虑规则是否启用。
     *
     * @param entityId 实体注册表 ID
     * @return 快照包含该键时返回 {@code true}
     */
    public boolean hasRule(ResourceLocation entityId) {
        return snapshot.rules().containsKey(entityId);
    }

    /**
     * 判断管理器是否已经成功安装过世界规则快照。
     *
     * <p>该标记用于区分“服务端启动阶段尚未读取 SavedData”和“玩家合法配置了空规则集合”。实体 Join 事件在前一种
     * 状态下必须保持持久化 Love 不变，等待启动加载完成后统一恢复。
     *
     * @return 至少成功安装过一次快照时返回 {@code true}
     */
    public boolean isInitialized() {
        return initialized;
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
        initialized = true;
    }
}
