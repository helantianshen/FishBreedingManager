package com.fishbreedingmanager.compat;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;

import com.fishbreedingmanager.breeding.BreedingRuleManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

class CompatibilityCoordinatorTest {
    @Test
    void installInvokesEveryModuleAndAggregatesSuccessfulInstallation() {
        Entity entity = mock(Entity.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        TrackingModule first = new TrackingModule(false, false);
        TrackingModule second = new TrackingModule(true, false);

        CompatibilityCoordinator coordinator =
                new CompatibilityCoordinator(List.of(first, second));

        assertTrue(coordinator.installIfEligible(entity, manager));
        assertTrue(first.installCalled);
        assertTrue(second.installCalled);
    }

    @Test
    void moduleFailureDoesNotBlockRemainingInstallOrRefreshModules() {
        Entity entity = mock(Entity.class);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        MinecraftServer server = mock(MinecraftServer.class);
        TrackingModule failing = new TrackingModule(false, true);
        TrackingModule healthy = new TrackingModule(true, false);

        CompatibilityCoordinator coordinator =
                new CompatibilityCoordinator(List.of(failing, healthy));

        assertTrue(coordinator.installIfEligible(entity, manager));
        coordinator.refreshLoadedEntities(server);

        assertTrue(healthy.installCalled);
        assertTrue(healthy.refreshCalled);
    }

    private static final class TrackingModule implements CompatibilityCoordinator.Module {
        private final boolean installResult;
        private final boolean fail;
        private boolean installCalled;
        private boolean refreshCalled;

        private TrackingModule(boolean installResult, boolean fail) {
            this.installResult = installResult;
            this.fail = fail;
        }

        @Override
        public boolean installIfEligible(Entity entity, BreedingRuleManager manager) {
            installCalled = true;
            if (fail) {
                throw new IllegalStateException("install failed");
            }
            return installResult;
        }

        @Override
        public void refreshLoadedEntities(MinecraftServer server) {
            refreshCalled = true;
            if (fail) {
                throw new IllegalStateException("refresh failed");
            }
        }
    }
}
