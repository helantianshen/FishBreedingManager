package com.fishbreedingmanager.network;

import java.util.List;
import com.fishbreedingmanager.breeding.BreedingRuleManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 伪造客户端请求不能绕过服务端权限或旧版本冲突检查 */
class ManagementServerTest {
    /** 过期写入必须在注册表与持久化访问前终止 */
    @Test void staleRevisionIsRejectedBeforeRegistryOrPersistenceAccess() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getServer()).thenReturn(server); when(player.hasPermissions(2)).thenReturn(true);
        BreedingRuleManager manager = mock(BreedingRuleManager.class);
        when(manager.isInitialized()).thenReturn(true); when(manager.revision()).thenReturn(9L);
        try (var managers = mockStatic(BreedingRuleManager.class)) {
            managers.when(() -> BreedingRuleManager.get(server)).thenReturn(manager);
            var result = ManagementServer.mutate(player, new ManagementData.Request(1, 8, "save", "minecraft:cod", null, true));
            assertFalse(result.success()); assertEquals("gui.fbm.conflict", result.message());
            verifyNoInteractions(server);
        }
    }
    /** 所有写入动作均受服务端权限约束，拒绝时规则与修订号不变 */
    @Test void deniesEveryWriteBeforeWorldAccess() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getServer()).thenReturn(server);
        when(player.hasPermissions(2)).thenReturn(false);
        var before = BreedingRuleManager.get(server).snapshot();
        for (String action : List.of("save", "add", "remove", "enable", "reload")) {
            var result = ManagementServer.mutate(player, new ManagementData.Request(1, 0, action, "minecraft:cod", null, true));
            assertFalse(result.success()); assertEquals("gui.fbm.denied", result.message());
            assertSame(before, BreedingRuleManager.get(server).snapshot());
            assertEquals(0, BreedingRuleManager.get(server).revision());
        }
        verifyNoInteractions(server);
        BreedingRuleManager.remove(server);
    }
    /** 同一玩家两次请求之间撤销权限，后一次仍必须重新检查 */
    @Test void permissionIsRecheckedForEachAttempt() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getServer()).thenReturn(server);
        when(player.hasPermissions(2)).thenReturn(true);
        var request = new ManagementData.Request(1, 0, "add", "minecraft:cod", null, false);
        assertEquals("gui.fbm.not_ready", ManagementServer.mutate(player, request).message());
        when(player.hasPermissions(2)).thenReturn(false);
        assertEquals("gui.fbm.denied", ManagementServer.mutate(player, request).message());
        BreedingRuleManager.remove(server);
    }
}
