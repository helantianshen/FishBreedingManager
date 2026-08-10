package com.fishbreedingmanager.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

import static org.mockito.Mockito.mock;

/**
 * 验证幼体渲染矩阵只在事件链最低优先级压栈，并按真实压栈记录精确恢复。
 */
class JuvenileRenderHandlerTest {
    /** 每个测试后清理当前测试线程的渲染记录。 */
    @AfterEach
    void clearTracking() {
        JuvenileRenderHandler.clearTrackedPushes();
    }

    /**
     * Pre 处理器必须在最低优先级运行，尽量确保其他 Mod 的取消决定先完成。
     */
    @Test
    void preHandlerRunsAtLowestPriority() throws NoSuchMethodException {
        Method method = JuvenileRenderHandler.class.getMethod(
                "onRenderLivingPre",
                net.neoforged.neoforge.client.event.RenderLivingEvent.Pre.class);

        SubscribeEvent annotation = method.getAnnotation(SubscribeEvent.class);

        assertEquals(EventPriority.LOWEST, annotation.priority());
    }

    /**
     * Post 只能消费一次真实 Pre 压栈记录，不能通过重新计算缩放猜测是否需要弹栈。
     */
    @Test
    void consumesOnlyActuallyTrackedPush() {
        Entity entity = mock(Entity.class);

        JuvenileRenderHandler.rememberPush(entity);

        assertTrue(JuvenileRenderHandler.consumePush(entity));
        assertFalse(JuvenileRenderHandler.consumePush(entity));
    }
}
