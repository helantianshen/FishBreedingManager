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
 * <p>Uses {@link RenderLivingEvent.Pre} to push a scale onto the {@link PoseStack} before the entity
 * model is rendered. The scale is read from {@link ClientJuvenileSync}, which is fed by the server's
 * {@code JuvenileStatePayload}. Non-juvenile or unknown entities get scale 1.0 (no change).
 *
 * <p>This is non-invasive: it does not modify any entity or renderer class.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = FishBreedingManager.MOD_ID)
public final class JuvenileRenderHandler {
    private JuvenileRenderHandler() {
    }

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
            // Scale around the entity's origin; y is shifted so it stays grounded.
            float inv = 1.0F - scale;
            pose.translate(0.0F, event.getEntity().getBbHeight() * 0.5F * inv, 0.0F);
            pose.scale(scale, scale, scale);
        }
    }

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
