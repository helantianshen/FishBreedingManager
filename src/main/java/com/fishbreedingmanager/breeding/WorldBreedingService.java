package com.fishbreedingmanager.breeding;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.compat.CompatibilityCoordinator;
import com.fishbreedingmanager.persistence.WorldBreedingData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * 协调世界持久化规则与运行时快照的事务式应用服务。
 *
 * <p>每次修改先复制当前完整规则集合，在副本上应用变更并通过 {@link RuleValidator#validateAll} 校验候选全集。
 * 只有全部规则有效时，才依次替换 {@link WorldBreedingData} 与 {@link BreedingRuleManager} 快照；校验失败不会污染
 * 任一当前有效状态。所有生产入口都以 {@link MinecraftServer} 定位当前存档，包级重载仅供同包测试验证事务边界。
 */
public final class WorldBreedingService {
    private static final WorldBreedingService INSTANCE =
            new WorldBreedingService(
                    new RuleValidator(),
                    CompatibilityCoordinator.get()::refreshLoadedEntities);

    private final RuleValidator validator;
    private final Consumer<MinecraftServer> compatibilityRefresher;

    /**
     * 创建服务并注入规则校验器。
     *
     * @param validator 提交前使用的完整规则校验器
     */
    WorldBreedingService(RuleValidator validator) {
        this(validator, ignored -> { });
    }

    /**
     * 创建服务并注入校验器与规则发布后的可选兼容刷新边界。
     *
     * @param validator 提交前使用的完整规则校验器
     * @param compatibilityRefresher 成功发布 Snapshot 后执行的 best-effort 兼容刷新
     */
    WorldBreedingService(RuleValidator validator,
                         Consumer<MinecraftServer> compatibilityRefresher) {
        this.validator = validator;
        this.compatibilityRefresher = compatibilityRefresher;
    }

    /**
     * 返回生产环境共享服务实例。
     *
     * @return 世界规则应用服务
     */
    public static WorldBreedingService get() {
        return INSTANCE;
    }

    /**
     * 从当前世界存档重新构建并安装运行时快照。
     *
     * <p>持久化内容会先完整校验；任何错误或运行时异常都会保留旧快照并返回失败信息。
     *
     * @param server 当前 Minecraft 服务端
     * @return 重载结果及成功安装的规则数，或失败原因
     */
    public ReloadResult reload(MinecraftServer server) {
        try {
            WorldBreedingData data = WorldBreedingData.get(server);
            BreedingRuleManager manager = BreedingRuleManager.get(server);
            RuleValidationResult validation = validator.validateAll(data.allRules());
            if (!validation.valid()) {
                return ReloadResult.failure(String.join("; ", validation.errors()));
            }
            BreedingRuleSnapshot next = data.buildSnapshot();
            manager.install(next);
            refreshCompatibility(server);
            return ReloadResult.success(next.rules().size());
        } catch (RuntimeException exception) {
            FishBreedingManager.LOGGER.error("FBM 规则重载失败，保留旧运行时快照", exception);
            String message = exception.getMessage();
            return ReloadResult.failure(message == null
                    ? exception.getClass().getSimpleName()
                    : message);
        }
    }

    /**
     * 新增或整体替换一条实体繁殖规则。
     *
     * @param server 当前 Minecraft 服务端
     * @param rule 待写入的候选规则
     * @return 事务提交结果
     */
    public RuleUpdateResult upsert(MinecraftServer server, BreedingRule rule) {
        RuleUpdateResult result = upsert(
                WorldBreedingData.get(server), BreedingRuleManager.get(server), rule);
        if (result.success()) {
            refreshCompatibility(server);
        }
        return result;
    }

    /**
     * 使用已提供的数据容器执行新增或替换，供事务测试复用。
     *
     * @param data 当前世界持久化数据
     * @param manager 当前服务器运行时管理器
     * @param rule 待写入的候选规则
     * @return 事务提交结果
     */
    RuleUpdateResult upsert(WorldBreedingData data, BreedingRuleManager manager,
                            BreedingRule rule) {
        Map<ResourceLocation, BreedingRule> candidate = copyRules(data);
        candidate.put(rule.entityTypeId(), rule);
        return commit(data, manager, candidate);
    }

    /**
     * 删除指定实体的繁殖规则。
     *
     * @param server 当前 Minecraft 服务端
     * @param entityId 待删除规则的实体注册表 ID
     * @return 事务提交结果；规则不存在时返回失败
     */
    public RuleUpdateResult remove(MinecraftServer server, ResourceLocation entityId) {
        return remove(WorldBreedingData.get(server), BreedingRuleManager.get(server), entityId);
    }

    /**
     * 使用已提供的数据容器执行删除，供事务测试复用。
     *
     * @param data 当前世界持久化数据
     * @param manager 当前服务器运行时管理器
     * @param entityId 待删除规则的实体注册表 ID
     * @return 事务提交结果
     */
    RuleUpdateResult remove(WorldBreedingData data, BreedingRuleManager manager,
                            ResourceLocation entityId) {
        Map<ResourceLocation, BreedingRule> candidate = copyRules(data);
        if (candidate.remove(entityId) == null) {
            return RuleUpdateResult.failure(List.of("未找到实体规则: " + entityId));
        }
        return commit(data, manager, candidate);
    }

    /**
     * 设置指定规则的启用状态，同时保持其余配置字段不变。
     *
     * @param server 当前 Minecraft 服务端
     * @param entityId 目标实体注册表 ID
     * @param enabled 新启用状态
     * @return 事务提交结果；规则不存在时返回失败
     */
    public RuleUpdateResult setEnabled(MinecraftServer server, ResourceLocation entityId,
                                       boolean enabled) {
        RuleUpdateResult result = setEnabled(
                WorldBreedingData.get(server), BreedingRuleManager.get(server), entityId, enabled);
        if (result.success() && enabled) {
            refreshCompatibility(server);
        }
        return result;
    }

    /**
     * 使用已提供的数据容器更新启用状态，供事务测试复用。
     *
     * @param data 当前世界持久化数据
     * @param manager 当前服务器运行时管理器
     * @param entityId 目标实体注册表 ID
     * @param enabled 新启用状态
     * @return 事务提交结果
     */
    RuleUpdateResult setEnabled(WorldBreedingData data, BreedingRuleManager manager,
                                ResourceLocation entityId, boolean enabled) {
        Map<ResourceLocation, BreedingRule> candidate = copyRules(data);
        BreedingRule current = candidate.get(entityId);
        if (current == null) {
            return RuleUpdateResult.failure(List.of("未找到实体规则: " + entityId));
        }
        candidate.put(entityId, new BreedingRule(
                current.entityTypeId(),
                current.breedingItemIds(),
                current.breedingTagIds(),
                current.breedingCooldownTicks(),
                current.growthTimeTicks(),
                enabled));
        return commit(data, manager, candidate);
    }

    /**
     * 按实体注册表 ID 排序列出当前生效规则。
     *
     * @param server 当前 Minecraft 服务端
     * @return 不可修改语义的已排序规则列表
     */
    public List<BreedingRule> list(MinecraftServer server) {
        return BreedingRuleManager.get(server).snapshot().rules().values().stream()
                .sorted(Comparator.comparing(rule -> rule.entityTypeId().toString()))
                .toList();
    }

    /**
     * 校验并提交完整候选集合。
     *
     * @param data 当前世界持久化数据
     * @param manager 当前服务器运行时管理器
     * @param candidate 已应用单次修改的完整候选规则映射
     * @return 提交结果；校验失败时不修改任何当前状态
     */
    private RuleUpdateResult commit(WorldBreedingData data, BreedingRuleManager manager,
                                    Map<ResourceLocation, BreedingRule> candidate) {
        RuleValidationResult validation = validator.validateAll(candidate.values());
        if (!validation.valid()) {
            return RuleUpdateResult.failure(validation.errors());
        }

        BreedingRuleSnapshot next = new BreedingRuleSnapshot(
                Map.copyOf(candidate), data.getImportedEntities());
        data.replaceRules(candidate.values());
        manager.install(next);
        return RuleUpdateResult.success(candidate.size());
    }

    /**
     * 按当前持久化顺序复制全部规则，供候选集合安全修改。
     *
     * @param data 当前世界持久化数据
     * @return 与当前规则内容相同的可修改映射
     */
    private static Map<ResourceLocation, BreedingRule> copyRules(WorldBreedingData data) {
        Map<ResourceLocation, BreedingRule> result = new LinkedHashMap<>();
        for (BreedingRule rule : data.allRules()) {
            result.put(rule.entityTypeId(), rule);
        }
        return result;
    }

    /**
     * 隔离规则提交后的可选兼容刷新。
     *
     * <p>规则数据和运行时 Snapshot 在调用前已经成功提交，因此任何第三方实体或 Goal 异常只能记录，不能让命令
     * 返回失败或让启动流程误称旧快照仍被保留。
     *
     * @param server 已成功发布规则的当前服务器
     */
    void refreshCompatibility(MinecraftServer server) {
        try {
            compatibilityRefresher.accept(server);
        } catch (RuntimeException exception) {
            FishBreedingManager.LOGGER.error(
                    "FBM optional compatibility refresh failed after rule publication; rules remain committed",
                    exception);
        }
    }
}
