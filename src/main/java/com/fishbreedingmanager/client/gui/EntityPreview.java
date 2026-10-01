package com.fishbreedingmanager.client.gui;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.CodRenderer;
import net.minecraft.client.renderer.entity.SalmonRenderer;
import net.minecraft.client.renderer.entity.TropicalFishRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pufferfish;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.item.DyeColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.renderer.entity.PufferfishRenderer;
import org.joml.Quaternionf;

/**
 * 渲染当前浏览所需的独立客户端实体，不将预览对象加入世界或执行实体 tick
 *
 * <p>模型缓存最多保留 64 个对象，失败类型在当前客户端 Level 内降级为占位提示
 * 四种原版鱼采用固定展示形态与轮廓参数，其他实体按碰撞箱估算尺寸；资源包替换模型不保证沿用相同轮廓
 * 渲染结束恢复矩阵、裁剪、相机朝向和 GUI 光照，预览形态不影响真实世界实体
 */
final class EntityPreview {
    private final LinkedHashMap<String, Entity> entities = new LinkedHashMap<>(64, .75f, true);
    private final Set<String> failed = new HashSet<>();
    private net.minecraft.client.multiplayer.ClientLevel level;

    /**
     * 在给定逻辑像素区域内裁剪实体模型，运行时渲染异常返回占位信号
     *
     * @param graphics 当前 GUI 绘制上下文
     * @param id 实体 Registry ID
     * @param x 预览左边界
     * @param y 预览上边界
     * @param width 预览宽度
     * @param height 预览高度
     * @return 成功完成渲染时为 true，无法创建或渲染时为 false
     */
    boolean render(GuiGraphics graphics, String id, int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (level != mc.level) { level = mc.level; entities.clear(); failed.clear(); }
        if (mc.level == null || failed.contains(id) || width < 12 || height < 12) return false;
        Entity entity;
        try {
            entity = entities.computeIfAbsent(id, key -> BuiltInRegistries.ENTITY_TYPE
                    .getOptional(ResourceLocation.parse(key)).map(type -> createPreview(type, mc)).orElse(null));
            if (entity == null) { failed.add(id); return false; }
            while (entities.size() > 64) entities.remove(entities.keySet().iterator().next());
        } catch (RuntimeException exception) { failed.add(id); return false; }
        var dispatcher = mc.getEntityRenderDispatcher();
        Quaternionf camera = new Quaternionf(dispatcher.cameraOrientation());
        graphics.enableScissor(x, y, x + width, y + height);
        graphics.pose().pushPose();
        try {
            EntityRenderer<?> renderer = dispatcher.getRenderer(entity);
            float centerX = 0, centerY = entity.getBbHeight() / 2, centerZ = 0;
            float displayWidth = Math.max(.5f, entity.getBbWidth() * 1.5f);
            float displayHeight = Math.max(.5f, entity.getBbHeight());
            boolean dryFish = !entity.isInWater() && (renderer instanceof CodRenderer
                    || renderer instanceof SalmonRenderer || renderer instanceof TropicalFishRenderer);
            // 四种原版模型按静止姿态的轮廓校准，包含离水旋转后的位移与鲑鱼渲染器的轴向偏移
            if (dryFish && renderer instanceof CodRenderer) {
                centerX = -.1f; centerY = .057f; centerZ = -.119f;
                displayWidth = 1.0f; displayHeight = .65f;
            } else if (dryFish && renderer instanceof SalmonRenderer) {
                centerX = -.1f; centerY = .098f; centerZ = -.194f;
                displayWidth = 1.5f; displayHeight = .85f;
            } else if (dryFish && renderer instanceof TropicalFishRenderer) {
                centerX = -.1f; centerY = .02f; centerZ = -.188f;
                displayWidth = .85f; displayHeight = .65f;
            } else if (renderer instanceof PufferfishRenderer) {
                centerY = .456f;
                displayWidth = .95f; displayHeight = .9f;
            }
            float scale = Math.min((width - 12) / displayWidth, (height - 12) / displayHeight);
            graphics.pose().translate(x + width / 2f, y + height / 2f, 50);
            graphics.pose().scale(scale, scale, -scale);
            graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.PI)
                    .rotateX((float) Math.toRadians(-20)).rotateY((float) Math.toRadians(150)));
            graphics.pose().translate(-centerX, -centerY, -centerZ);
            // 原版鱼渲染器会将离水模型侧躺，预览对象不进入水体，需抵消该局部旋转
            if (dryFish) graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.PI / 2));
            Lighting.setupForEntityInInventory();
            dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0, 0, 0, 0, 0,
                    graphics.pose(), graphics.bufferSource(), 15728880));
            graphics.flush();
            return true;
        } catch (RuntimeException exception) {
            failed.add(id); entities.remove(id); return false;
        } finally {
            dispatcher.setRenderShadow(true);
            dispatcher.overrideCameraOrientation(camera);
            graphics.pose().popPose();
            graphics.disableScissor();
            Lighting.setupFor3DItems();
        }
    }

    /** 展示形态只写入独立预览对象，不读取或修改世界中的实体 */
    private static Entity createPreview(EntityType<?> type, Minecraft mc) {
        Entity entity = type.create(mc.level);
        if (type == EntityType.PUFFERFISH && entity instanceof Pufferfish fish) {
            fish.setPuffState(2);
        } else if (type == EntityType.TROPICAL_FISH && entity instanceof TropicalFish fish) {
            CompoundTag tag = new CompoundTag();
            tag.putInt(TropicalFish.BUCKET_VARIANT_TAG,
                    new TropicalFish.Variant(TropicalFish.Pattern.KOB, DyeColor.ORANGE, DyeColor.WHITE).getPackedId());
            fish.loadFromBucketTag(tag);
        }
        return entity;
    }

}
