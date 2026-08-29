package com.fishbreedingmanager.discovery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 验证发现快照只在完整构建成功后原子发布。 */
class FishDiscoveryManagerTest {
    @Test
    void replacesOnlyAfterSuccessfulCompleteBuild() {
        FishDiscoveryManager manager = new FishDiscoveryManager();
        DiscoverySnapshot first = new DiscoverySnapshot(Map.of(), Map.of(), Set.of());

        DiscoveryReloadResult success = manager.rebuild(() -> first);
        DiscoveryReloadResult failure = manager.rebuild(() -> {
            throw new IllegalStateException("broken tags");
        });

        assertTrue(success.success());
        assertFalse(failure.success());
        assertSame(first, manager.snapshot());
        assertTrue(failure.error().contains("broken tags"));
    }

    @Test
    void removeDiscardsServerBoundManager() {
        MinecraftServer server = Mockito.mock(MinecraftServer.class);
        FishDiscoveryManager first = FishDiscoveryManager.get(server);

        FishDiscoveryManager.remove(server);

        assertNotSame(first, FishDiscoveryManager.get(server));
        FishDiscoveryManager.remove(server);
    }

    @Test
    void reloadUsesPersistedWorldImportsAsDiscoveryInput() {
        ResourceLocation missing = ResourceLocation.parse("removed:old_fish");
        FishDiscoveryManager manager = new FishDiscoveryManager(
                List::of, List::of, server -> Set.of(missing));
        MinecraftServer server = Mockito.mock(MinecraftServer.class);

        DiscoveryReloadResult result = manager.reload(server);

        assertTrue(result.success());
        assertEquals(Set.of(missing), manager.snapshot().unavailableImports());
    }

    @Test
    void keepsConcurrentServersInSeparateManagers() {
        MinecraftServer firstServer = Mockito.mock(MinecraftServer.class);
        MinecraftServer secondServer = Mockito.mock(MinecraftServer.class);
        FishDiscoveryManager first = FishDiscoveryManager.get(firstServer);
        FishDiscoveryManager second = FishDiscoveryManager.get(secondServer);
        DiscoverySnapshot firstSnapshot = new DiscoverySnapshot(Map.of(), Map.of(), Set.of(
                ResourceLocation.parse("removed:first_fish")));

        first.rebuild(() -> firstSnapshot);

        assertNotSame(first, second);
        assertSame(firstSnapshot, first.snapshot());
        assertSame(DiscoverySnapshot.EMPTY, second.snapshot());
        FishDiscoveryManager.remove(firstServer);
        FishDiscoveryManager.remove(secondServer);
    }
}
