package com.fishbreedingmanager.compat.feedingtrough;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.breeding.feed.BreedingFeedService;
import com.fishbreedingmanager.breeding.BreedingRule;
import com.fishbreedingmanager.breeding.BreedingRuleManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.animal.Animal;
import net.neoforged.fml.ModList;

/**
 * 协调 Animal Feeding Trough 可选适配目标的安装与规则热更新。
 *
 * <p>协调器只在第三方 Mod 已加载时工作，不引用其任何 Java 类型。目标只安装到当前规则启用的非 {@link Animal}
 * {@link PathfinderMob}，并通过 Goal 类型检查保证实体重复加入或规则重复发布时仍然幂等。
 */
public final class AnimalFeedingTroughCompatibility {
    /** Animal Feeding Trough 的稳定 Mod ID。 */
    public static final String MOD_ID = "animal_feeding_trough";
    /**
     * 取食目标的固定优先级，必须严格小于原版随机游动目标的优先级。
     *
     * <p>{@code GoalSelector} 只允许更小的优先级数字抢占正在运行的目标
     * （{@code WrappedGoal#canBeReplacedBy} 要求 {@code other.getPriority() < this.getPriority()}）。
     * 原版 {@code AbstractFish} 在优先级 {@code 4} 注册 {@code FishSwimGoal}，因此本目标必须使用 {@code 3}；
     * 取相同的 {@code 4} 会导致鱼只要正在随机游动就永远无法转去取食。同时保持大于
     * {@code AvoidEntityGoal}（{@code 2}）与 {@code PanicGoal}（{@code 0}），避免压制躲避和恐慌行为。
     */
    public static final int GOAL_PRIORITY = 3;

    private AnimalFeedingTroughCompatibility() {
    }

    /**
     * 在实体加入服务端世界时尝试安装可选喂食槽目标。
     *
     * @param entity 当前加入的实体
     * @param manager 当前服务器规则管理器
     * @return 本次实际新增目标时返回 {@code true}
     */
    public static boolean installIfEligible(Entity entity, BreedingRuleManager manager) {
        return installSafely(entity, manager, ModList.get().isLoaded(MOD_ID));
    }

    /** 包级安全边界保证单个异常实体不会中断规则发布或其他实体刷新。 */
    static boolean installSafely(Entity entity, BreedingRuleManager manager,
                                 boolean troughLoaded) {
        try {
            return installIfEligible(entity, manager, troughLoaded);
        } catch (RuntimeException exception) {
            FishBreedingManager.LOGGER.error(
                    "FBM Animal Feeding Trough goal installation failed for one entity; skipping it",
                    exception);
            return false;
        }
    }

    /** 包级入口允许测试显式控制第三方 Mod 是否加载。 */
    static boolean installIfEligible(Entity entity, BreedingRuleManager manager,
                                     boolean troughLoaded) {
        if (!(entity instanceof PathfinderMob mob) || entity instanceof Animal) {
            return false;
        }
        return install(mob, mob.goalSelector, manager, troughLoaded);
    }

    /** 包级安装核心允许测试隔离 GoalSelector。 */
    static boolean install(PathfinderMob mob, GoalSelector selector,
                           BreedingRuleManager manager, boolean troughLoaded) {
        if (!troughLoaded || !manager.isInitialized()) {
            return false;
        }
        BreedingRule rule = manager.find(mob.getType());
        if (rule == null || !rule.enabled()) {
            return false;
        }
        boolean alreadyInstalled = selector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof FbmTroughSelfFeedGoal);
        if (alreadyInstalled) {
            return false;
        }
        selector.addGoal(GOAL_PRIORITY, new FbmTroughSelfFeedGoal(
                mob,
                manager,
                BreedingFeedService.get(),
                AnimalFeedingTroughSource.INSTANCE));
        return true;
    }

    /**
     * 在初始加载或规则成功发布后给当前已加载实体补装目标。
     *
     * <p>该遍历只发生在规则生命周期边界，不进入每 tick 路径。禁用或删除规则时无需移除目标，因为目标执行时会
     * 动态查询当前快照并自动休眠。
     *
     * @param server 当前逻辑服务器
     * @return 本轮新安装的目标数量
     */
    public static int refreshLoadedEntities(MinecraftServer server) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return 0;
        }
        BreedingRuleManager manager = BreedingRuleManager.get(server);
        int installed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (installSafely(entity, manager, true)) {
                    installed++;
                }
            }
        }
        if (installed > 0) {
            FishBreedingManager.LOGGER.info(
                    "FBM Animal Feeding Trough goals installed: count={}", installed);
        }
        return installed;
    }
}
