package com.fishbreedingmanager.command;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;

/**
 * 验证所有 FBM 管理命令都在根节点严格执行权限等级 {@code 2} 校验。
 */
class FBMCommandsTest {
    /**
     * 无权限来源不能进入 {@code fbm} 根节点，因此任何子命令都不可执行。
     */
    @Test
    void sourceWithoutPermissionCannotUseFbmTree() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(FBMCommands.build());
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.hasPermission(2)).thenReturn(false);

        assertThrows(Exception.class,
                () -> dispatcher.execute("fbm rule list", source));
    }

    /**
     * 有权限来源应能解析包含多个物品和标签的完整 {@code rule set} 语法。
     */
    @Test
    void setCommandParsesMultipleFoodEntries() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(FBMCommands.build());
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.hasPermission(2)).thenReturn(true);

        var parse = dispatcher.parse(
                "fbm rule set minecraft:cod 600 1200 minecraft:kelp,#minecraft:planks",
                source);

        assertTrue(parse.getExceptions().isEmpty());
        assertTrue(parse.getReader().getRemaining().isEmpty());
    }
}
