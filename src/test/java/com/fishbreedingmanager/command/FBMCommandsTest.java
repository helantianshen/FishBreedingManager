package com.fishbreedingmanager.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.fishbreedingmanager.breeding.RuleUpdateResult;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

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

    /**
     * 规则操作的成功反馈必须先把实体 ID 转成翻译组件支持的参数类型。
     *
     * <p>{@link Component#translatable(String, Object...)} 会在延迟消息真正求值时检查参数类型；
     * 因此测试主动执行 {@link Supplier#get()}，以覆盖真实客户端中出现异常的边界。
     *
     * @throws Exception 反射访问统一反馈方法失败时抛出
     */
    @Test
    void updateFeedbackAcceptsResourceLocationEntityId() throws Exception {
        CommandSourceStack source = mock(CommandSourceStack.class);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<Component> message = invocation.getArgument(0, Supplier.class);
            Component component = message.get();
            TranslatableContents contents = assertInstanceOf(
                    TranslatableContents.class, component.getContents());
            assertEquals("minecraft:cod", contents.getArgs()[0]);
            return null;
        }).when(source).sendSuccess(any(), eq(true));

        Method reportUpdate = FBMCommands.class.getDeclaredMethod(
                "reportUpdate",
                CommandSourceStack.class,
                ResourceLocation.class,
                RuleUpdateResult.class,
                String.class);
        reportUpdate.setAccessible(true);

        assertDoesNotThrow(() -> reportUpdate.invoke(
                null,
                source,
                ResourceLocation.parse("minecraft:cod"),
                RuleUpdateResult.success(4),
                "commands.fbm.rule.updated"));
    }
}
