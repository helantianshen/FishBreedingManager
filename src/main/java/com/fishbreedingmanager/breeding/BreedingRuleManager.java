package com.fishbreedingmanager.breeding;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;

/**
 * 按逻辑服务器隔离当前运行时规则、初始化状态和管理修订号
 *
 * <p>经 {@link WorldBreedingService} 完整校验后，通过 volatile 引用发布不可变快照；失败时保留有效快照
 * 行为入口动态查询规则，实体附件只保存计时与配对状态，热更新不会重算既有冷却或成长截止时间
 * 首次加载失败时保留单独的存档诊断副本，GUI 可以查询该副本，繁殖运行时不会使用它
 */
public final class BreedingRuleManager {
    private static final Map<MinecraftServer, BreedingRuleManager> MANAGERS = new ConcurrentHashMap<>();

    private volatile BreedingRuleSnapshot snapshot = BreedingRuleSnapshot.EMPTY;
    private volatile boolean initialized;
    private volatile long revision;
    private BreedingRuleSnapshot storedSnapshot = BreedingRuleSnapshot.EMPTY;

    /**
     * 创建空快照管理器
     *
     * <p>构造器保持包级可见，生产代码通过 {@link #get(MinecraftServer)} 获取实例，同包测试可创建隔离管理器
     */
    BreedingRuleManager() {
    }

    /**
     * 返回与指定服务器生命周期绑定的运行时规则管理器
     *
     * <p>管理器只持有不可变 {@link BreedingRuleSnapshot} 的易失引用；调用方不得把查询到的规则缓存到实体附件
     * 否则现存实体将无法立即响应热更新
     *
     * @param server 当前逻辑服务器
     * @return 该服务器唯一的规则管理器
     */
    public static BreedingRuleManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, s -> new BreedingRuleManager());
    }

    /**
     * 移除已经停止服务器的管理器，防止下一存档复用旧 Snapshot
     *
     * @param server 正在停止的逻辑服务器
     */
    public static void remove(MinecraftServer server) {
        MANAGERS.remove(server);
    }

    /**
     * 返回当前不可变运行时快照
     *
     * @return 最近一次成功安装的完整快照
     */
    public BreedingRuleSnapshot snapshot() {
        return snapshot;
    }

    /**
     * 首次规则加载失败时仍允许只读诊断存档内容，运行时不会使用该副本
     * @return 当前有效快照，或尚未应用的存档诊断副本
     */
    public BreedingRuleSnapshot managementSnapshot() {
        return initialized ? snapshot : storedSnapshot;
    }

    /** 生命周期加载时保留只读诊断副本，查询入口不初始化 SavedData */
    void rememberStored(BreedingRuleSnapshot stored) {
        storedSnapshot = stored;
    }

    /**
     * 按稳定实体注册表 ID 查询当前规则
     *
     * @param entityId 实体注册表 ID
     * @return 当前规则；未配置时返回 {@code null}
     */
    public BreedingRule find(ResourceLocation entityId) {
        return snapshot.rules().get(entityId);
    }

    /**
     * 将实体类型转换为注册表 ID 后查询当前规则
     *
     * @param entityType 当前实体类型
     * @return 当前规则；类型未注册或未配置时返回 {@code null}
     */
    public BreedingRule find(EntityType<?> entityType) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        return id != null ? find(id) : null;
    }

    /**
     * 判断管理器是否已经成功安装过世界规则快照
     *
     * <p>该标记用于区分“服务端启动阶段尚未读取 SavedData”和“玩家合法配置了空规则集合”；实体 Join 事件在前一种
     * 状态下必须保持持久化 Love 不变，等待启动加载完成后统一恢复
     *
     * @return 至少成功安装过一次快照时返回 {@code true}
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 返回当前世界管理快照的修订号，任何成功安装都会使旧编辑基线失效
     *
     * @return 当前世界管理修订号
     */
    public long revision() {
        return revision;
    }

    /** 标签和目录变化使管理基线失效，但不重算已有实体计时 */
    public void invalidateDirectory() {
        revision++;
    }

    /**
     * 安装已经完整校验且不可变的运行时快照
     *
     * <p>入口保持包级可见，强制生产调用方经由 {@link WorldBreedingService} 完成候选全集校验后再替换
     * {@code volatile} 写入使后续行为线程立即看见完整的新快照
     *
     * @param next 下一份权威运行时快照
     */
    void install(BreedingRuleSnapshot next) {
        snapshot = next;
        initialized = true;
        revision++;
    }
}
