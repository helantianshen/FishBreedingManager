package com.fishbreedingmanager.compat;

import java.util.List;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.breeding.BreedingRuleManager;
import com.fishbreedingmanager.compat.feedingtrough.AnimalFeedingTroughCompatibility;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/**
 * 通过唯一的核心面向边界暴露全部可选第三方集成。
 *
 * <p>核心生命周期与规则服务只依赖本协调器，不依赖任何具体 Mod 适配。各模块彼此隔离，因此缺失或出错的可选集成
 * 不会阻止其余集成完成安装或刷新，也不会反向影响已经提交的规则快照。
 */
public final class CompatibilityCoordinator {
    private static final CompatibilityCoordinator INSTANCE = new CompatibilityCoordinator(List.of(
            new Module() {
                @Override
                public boolean installIfEligible(Entity entity, BreedingRuleManager manager) {
                    return AnimalFeedingTroughCompatibility.installIfEligible(entity, manager);
                }

                @Override
                public void refreshLoadedEntities(MinecraftServer server) {
                    AnimalFeedingTroughCompatibility.refreshLoadedEntities(server);
                }
            }));

    private final List<Module> modules;

    CompatibilityCoordinator(List<Module> modules) {
        this.modules = List.copyOf(modules);
    }

    /**
     * 返回生产环境共享的兼容注册表。
     *
     * @return 共享兼容协调器
     */
    public static CompatibilityCoordinator get() {
        return INSTANCE;
    }

    /**
     * 为单个实体安装全部适用的可选集成。
     *
     * @param entity 正在进入服务端世界的实体
     * @param manager 当前服务器规则管理器
     * @return 至少有一个模块实际安装了集成时返回 {@code true}
     */
    public boolean installIfEligible(Entity entity, BreedingRuleManager manager) {
        boolean installed = false;
        for (Module module : modules) {
            try {
                installed |= module.installIfEligible(entity, manager);
            } catch (RuntimeException exception) {
                FishBreedingManager.LOGGER.error(
                        "FBM optional compatibility install failed; remaining modules will continue",
                        exception);
            }
        }
        return installed;
    }

    /**
     * 在规则快照发布后重新评估已经加载的实体。
     *
     * @param server 当前逻辑服务器
     */
    public void refreshLoadedEntities(MinecraftServer server) {
        for (Module module : modules) {
            try {
                module.refreshLoadedEntities(server);
            } catch (RuntimeException exception) {
                FishBreedingManager.LOGGER.error(
                        "FBM optional compatibility refresh failed; remaining modules will continue",
                        exception);
            }
        }
    }

    /** 单个可选第三方集成向协调器暴露的最小契约。 */
    interface Module {
        /**
         * 尝试为指定实体安装本模块的集成。
         *
         * @param entity 正在进入服务端世界的实体
         * @param manager 当前服务器规则管理器
         * @return 本次实际新增集成时返回 {@code true}
         */
        boolean installIfEligible(Entity entity, BreedingRuleManager manager);

        /**
         * 在规则发布后为已加载实体补装本模块的集成。
         *
         * @param server 当前逻辑服务器
         */
        void refreshLoadedEntities(MinecraftServer server);
    }
}
