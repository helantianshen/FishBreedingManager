package com.fishbreedingmanager.client;

import com.fishbreedingmanager.FishBreedingManager;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * 客户端幼体渲染钩子：成年前将实体固定缩放为成年尺寸的约 {@code 50%}，成年时瞬间恢复完整尺寸。
 *
 * <p>{@link RenderLivingEvent.Pre} 在模型绘制前向 {@link PoseStack} 压入缩放变换，
 * {@link RenderLivingEvent.Post} 在绘制后恢复矩阵。缩放值来自服务端 Payload 驱动的 {@link ClientJuvenileSync}；
 * 未知实体和已成年实体均返回 {@code 1.0F}，不会修改渲染。
 *
 * <p>该实现只作用于物理客户端渲染栈，不修改实体、碰撞箱或 Renderer 类，也不影响服务端逻辑尺寸。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = FishBreedingManager.MOD_ID)
public final class JuvenileRenderHandler {
    private JuvenileRenderHandler() {
    }

    /**
     * 在生物模型绘制前压入固定幼体缩放，并向上平移以尽量保持实体贴地。
     *
     * @param event NeoForge 生物渲染前事件
     */
    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        Entity entity = event.getEntity();
        long now = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime()
                : 0L;
        float scale = ClientJuvenileSync.scaleFor(entity.getUUID(), now);
        if (scale != 1.0F) {
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            // 围绕实体原点缩放，并按包围盒高度补偿 Y 轴位置以减少悬空感。
            float inv = 1.0F - scale;
            pose.translate(0.0F, event.getEntity().getBbHeight() * 0.5F * inv, 0.0F);
            pose.scale(scale, scale, scale);
        }
    }

    /**
     * 在生物模型绘制后弹出与 {@link #onRenderLivingPre} 对应的矩阵状态。
     *
     * @param event NeoForge 生物渲染后事件
     */
    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        Entity entity = event.getEntity();
        long now = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime()
                : 0L;
        float scale = ClientJuvenileSync.scaleFor(entity.getUUID(), now);
        if (scale != 1.0F) {
            event.getPoseStack().popPose();
        }
    }
}
