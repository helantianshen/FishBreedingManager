package com.fishbreedingmanager.command;

import com.fishbreedingmanager.breeding.ReloadResult;
import com.fishbreedingmanager.breeding.WorldBreedingService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 注册 {@code /fbm} 命令树 
 *
 * <p>当前子命令, 
 * <ul>
 *   <li>{@code /fbm reload}, 重读当前世界 {@code WorldBreedingData}, 重新校验并原子替换运行时 
 *       {@code BreedingRuleSnapshot}，严格要求权限等级 2。单人世界未启用作弊时同样无权执行。</li>
 * </ul>
 */
public final class FBMCommands {
    private FBMCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("fbm")
                .then(Commands.literal("reload")
                        .requires(cs -> cs.hasPermission(2))
                        .executes(ctx -> doReload(ctx.getSource()))));
    }

    private static int doReload(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ReloadResult result = WorldBreedingService.get().reload(server);
        if (result.success()) {
            source.sendSuccess(() -> Component.translatable("commands.fbm.reload.success", result.ruleCount()), true);
            return 1;
        }
        source.sendFailure(Component.translatable("commands.fbm.reload.failed", result.error()));
        return 0;
    }
}
