package com.fishbreedingmanager.compat;

import java.util.List;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.breeding.BreedingRuleManager;
import com.fishbreedingmanager.compat.feedingtrough.AnimalFeedingTroughCompatibility;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/**
 * Optional third-party integrations exposed through one core-facing boundary.
 *
 * <p>Core lifecycle and rule services depend on this coordinator rather than individual Mod adapters. Each module is
 * isolated so a missing or faulty optional integration cannot prevent later integrations from being installed or
 * refreshed.
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
     * Returns the production compatibility registry.
     *
     * @return shared compatibility coordinator
     */
    public static CompatibilityCoordinator get() {
        return INSTANCE;
    }

    /**
     * Installs every applicable integration on an entity.
     *
     * @param entity entity entering a server level
     * @param manager current server rule manager
     * @return whether at least one module installed an integration
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
     * Re-evaluates already loaded entities after a rule snapshot is published.
     *
     * @param server current logical server
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

    interface Module {
        boolean installIfEligible(Entity entity, BreedingRuleManager manager);

        void refreshLoadedEntities(MinecraftServer server);
    }
}
