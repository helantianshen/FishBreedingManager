package com.fishbreedingmanager.client;

import java.util.IdentityHashMap;
import java.util.Map;

import com.fishbreedingmanager.FishBreedingManager;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
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
    /**
     * 当前渲染线程中各实体尚未由 Post 消费的真实压栈次数。
     *
     * <p>使用实体身份而非 UUID，允许嵌套渲染同一 UUID 的不同对象；计数支持同一实体发生递归渲染。
     */
    private static final ThreadLocal<Map<Entity, Integer>> PUSHED_ENTITIES =
            ThreadLocal.withInitial(IdentityHashMap::new);

    private JuvenileRenderHandler() {
    }

    /**
     * 在生物模型绘制前压入固定幼体缩放，并向上平移以尽量保持实体贴地。
     *
     * @param event NeoForge 生物渲染前事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
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
            rememberPush(entity);
        }
    }

    /**
     * 在生物模型绘制后弹出与 {@link #onRenderLivingPre} 对应的矩阵状态。
     *
     * <p>优先级必须与 Pre 侧成镜像：Pre 与 Post 都按 {@code HIGHEST → LOWEST} 触发，Pre 取 {@code LOWEST}
     * 表示本 Mod 最后压栈（最内层），因此 Post 必须取 {@code HIGHEST} 才能最先弹栈。若两侧都用默认优先级，
     * 其他同样使用 push/pop 的 Mod 在 Post 高优先级弹栈时会先弹掉 FBM 压入的那一层，导致矩阵错位。
     *
     * @param event NeoForge 生物渲染后事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (consumePush(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }

    /**
     * 记录一次已经实际执行的矩阵压栈。
     *
     * @param entity 当前渲染实体
     */
    static void rememberPush(Entity entity) {
        PUSHED_ENTITIES.get().merge(entity, 1, Integer::sum);
    }

    /**
     * 消费一次实体压栈记录；没有记录时不得弹出外部渲染器的矩阵。
     *
     * @param entity 当前渲染实体
     * @return 存在真实压栈记录并已消费时返回 {@code true}
     */
    static boolean consumePush(Entity entity) {
        Map<Entity, Integer> pushed = PUSHED_ENTITIES.get();
        Integer count = pushed.get(entity);
        if (count == null) {
            return false;
        }
        if (count == 1) {
            pushed.remove(entity);
        } else {
            pushed.put(entity, count - 1);
        }
        return true;
    }

    /**
     * 清理当前线程的渲染压栈记录，仅供客户端会话清理与单元测试使用。
     */
    static void clearTrackedPushes() {
        PUSHED_ENTITIES.remove();
    }
}
