package com.fishbreedingmanager.command;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fishbreedingmanager.breeding.BreedingRule;
import com.fishbreedingmanager.breeding.FoodSpecParser;
import com.fishbreedingmanager.breeding.ParsedFood;
import com.fishbreedingmanager.breeding.ReloadResult;
import com.fishbreedingmanager.breeding.RuleUpdateResult;
import com.fishbreedingmanager.breeding.WorldBreedingService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 构建并执行 {@code /fbm} 管理员命令树。
 *
 * <p>权限检查统一放在根节点，所有重载、查询和修改操作都严格要求
 * {@link CommandSourceStack#hasPermission(int) 权限等级 2}。这意味着未开启作弊的单人世界同样不能使用命令。
 * 修改操作只调用 {@link WorldBreedingService}，不会绕过完整规则校验直接写入存档。
 *
 * <p>支持的 P0 命令包括：
 * <ul>
 *   <li>{@code /fbm reload}</li>
 *   <li>{@code /fbm rule list}</li>
 *   <li>{@code /fbm rule show <entity>}</li>
 *   <li>{@code /fbm rule set <entity> <cooldown> <growth> <foods>}</li>
 *   <li>{@code /fbm rule enable|disable|remove <entity>}</li>
 * </ul>
 */
public final class FBMCommands {
    private FBMCommands() {
    }

    /**
     * 将完整 FBM 命令树注册到当前服务端命令分发器。
     *
     * @param event NeoForge 命令注册事件
     */
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(build());
    }

    /**
     * 构建可独立测试的完整命令树。
     *
     * <p>方法保持包级可见，单元测试可以在不启动真实服务端的情况下验证 Brigadier 权限与语法。
     *
     * @return 带根节点权限约束的 {@code fbm} 字面量构建器
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("fbm")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(FBMCommands::reload))
                .then(Commands.literal("rule")
                        .then(Commands.literal("list")
                                .executes(FBMCommands::list))
                        .then(Commands.literal("show")
                                .then(Commands.argument("entity", ResourceLocationArgument.id())
                                        .executes(FBMCommands::show)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("entity", ResourceLocationArgument.id())
                                        .then(Commands.argument("cooldown", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("growth", IntegerArgumentType.integer(0))
                                                        .then(Commands.argument("foods", StringArgumentType.greedyString())
                                                                .executes(FBMCommands::set))))))
                        .then(Commands.literal("enable")
                                .then(Commands.argument("entity", ResourceLocationArgument.id())
                                        .executes(context -> setEnabled(context, true))))
                        .then(Commands.literal("disable")
                                .then(Commands.argument("entity", ResourceLocationArgument.id())
                                        .executes(context -> setEnabled(context, false))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("entity", ResourceLocationArgument.id())
                                        .executes(FBMCommands::remove))));
    }

    /**
     * 重新校验当前存档中的完整规则集合并原子安装运行时快照。
     *
     * @param context Brigadier 命令上下文
     * @return 成功返回 {@link Command#SINGLE_SUCCESS}，失败返回 {@code 0}
     */
    private static int reload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ReloadResult result = WorldBreedingService.get().reload(source.getServer());
        if (!result.success()) {
            source.sendFailure(Component.translatable(
                    "commands.fbm.reload.failed", result.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.fbm.reload.success", result.ruleCount()), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 按实体 ID 排序输出当前运行时快照中的全部规则。
     *
     * @param context Brigadier 命令上下文
     * @return 至少为 {@code 1} 的输出条数
     */
    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<BreedingRule> rules = WorldBreedingService.get().list(source.getServer());
        source.sendSuccess(() -> Component.literal("FBM rules: " + rules.size()), false);
        for (BreedingRule rule : rules) {
            source.sendSuccess(() -> Component.literal(formatRule(rule)), false);
        }
        return Math.max(Command.SINGLE_SUCCESS, rules.size());
    }

    /**
     * 输出指定实体当前生效的完整规则。
     *
     * @param context Brigadier 命令上下文
     * @return 找到规则时返回成功，否则返回 {@code 0}
     */
    private static int show(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ResourceLocation entityId = ResourceLocationArgument.getId(context, "entity");
        BreedingRule rule = WorldBreedingService.get().list(source.getServer()).stream()
                .filter(candidate -> candidate.entityTypeId().equals(entityId))
                .findFirst()
                .orElse(null);
        if (rule == null) {
            source.sendFailure(Component.translatable(
                    "commands.fbm.rule.not_found", entityId.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(formatRule(rule)), false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 解析完整规则参数并事务式新增或替换实体规则。
     *
     * @param context Brigadier 命令上下文
     * @return 提交成功返回 {@link Command#SINGLE_SUCCESS}，解析或校验失败返回 {@code 0}
     */
    private static int set(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ResourceLocation entityId = ResourceLocationArgument.getId(context, "entity");
            int cooldown = IntegerArgumentType.getInteger(context, "cooldown");
            int growth = IntegerArgumentType.getInteger(context, "growth");
            ParsedFood foods = FoodSpecParser.parse(
                    StringArgumentType.getString(context, "foods"));
            BreedingRule rule = new BreedingRule(
                    entityId, foods.itemIds(), foods.tagIds(), cooldown, growth, true);
            return reportUpdate(source, entityId,
                    WorldBreedingService.get().upsert(source.getServer(), rule),
                    "commands.fbm.rule.updated");
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.translatable(
                    "commands.fbm.rule.failed", exception.getMessage()));
            return 0;
        }
    }

    /**
     * 事务式切换指定规则的启用状态。
     *
     * @param context Brigadier 命令上下文
     * @param enabled 目标启用状态
     * @return 提交成功返回 {@link Command#SINGLE_SUCCESS}，失败返回 {@code 0}
     */
    private static int setEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        CommandSourceStack source = context.getSource();
        ResourceLocation entityId = ResourceLocationArgument.getId(context, "entity");
        RuleUpdateResult result = WorldBreedingService.get().setEnabled(
                source.getServer(), entityId, enabled);
        return reportUpdate(source, entityId, result,
                enabled ? "commands.fbm.rule.enabled" : "commands.fbm.rule.disabled");
    }

    /**
     * 事务式删除指定实体规则。
     *
     * @param context Brigadier 命令上下文
     * @return 提交成功返回 {@link Command#SINGLE_SUCCESS}，失败返回 {@code 0}
     */
    private static int remove(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ResourceLocation entityId = ResourceLocationArgument.getId(context, "entity");
        RuleUpdateResult result = WorldBreedingService.get().remove(
                source.getServer(), entityId);
        return reportUpdate(source, entityId, result, "commands.fbm.rule.removed");
    }

    /**
     * 将统一的事务结果转换为命令反馈与 Brigadier 返回码。
     *
     * @param source 命令来源
     * @param entityId 本次操作目标实体 ID
     * @param result 服务层事务结果
     * @param successKey 成功消息翻译键
     * @return 成功返回 {@link Command#SINGLE_SUCCESS}，失败返回 {@code 0}
     */
    private static int reportUpdate(CommandSourceStack source, ResourceLocation entityId,
                                    RuleUpdateResult result, String successKey) {
        if (!result.success()) {
            source.sendFailure(Component.translatable(
                    "commands.fbm.rule.failed", String.join("; ", result.errors())));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                successKey, entityId.toString(), result.ruleCount()), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 将规则格式化为适合管理员调试输出的单行文本。
     *
     * @param rule 待输出规则
     * @return 包含实体、启用状态、计时器和食物来源的确定性单行文本
     */
    private static String formatRule(BreedingRule rule) {
        String items = rule.breedingItemIds().stream()
                .map(ResourceLocation::toString)
                .collect(Collectors.joining(","));
        String tags = rule.breedingTagIds().stream()
                .map(id -> "#" + id)
                .collect(Collectors.joining(","));
        String foods = Stream.of(items, tags)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(","));
        return rule.entityTypeId() + " enabled=" + rule.enabled()
                + " cooldown=" + rule.breedingCooldownTicks()
                + " growth=" + rule.growthTimeTicks()
                + " foods=" + foods;
    }
}
