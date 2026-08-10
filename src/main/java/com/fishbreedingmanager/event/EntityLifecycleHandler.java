package com.fishbreedingmanager.event;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.attachment.ModAttachments;
import com.fishbreedingmanager.breeding.ActiveLoveIndex;
import com.fishbreedingmanager.breeding.BreedingRule;
import com.fishbreedingmanager.breeding.BreedingRuleManager;
import com.fishbreedingmanager.breeding.BreedingState;
import com.fishbreedingmanager.network.JuvenileStatePayload;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 恢复实体加载、卸载与玩家追踪边界上的 FBM 临时运行时状态。
 *
 * <p>Love 截止时间和幼体成年时间存于实体附件，但活动索引与客户端渲染缓存只存在于内存。本处理器在实体重新加载时
 * 恢复仍有效的 Love 索引，在卸载时删除索引，并在玩家开始追踪幼体时补发权威成年时刻。
 */
@EventBusSubscriber(modid = FishBreedingManager.MOD_ID)
public final class EntityLifecycleHandler {
    private EntityLifecycleHandler() {
    }

    /**
     * 实体加入服务端 Level 时恢复仍有效且规则启用的 Love 状态。
     *
     * @param event NeoForge 实体加入事件
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Entity entity = event.getEntity();
        BreedingState state = entity.getData(ModAttachments.BREEDING_STATE);
        if (!state.prepareForLevelJoin(level.getGameTime())) {
            return;
        }

        BreedingRule rule = BreedingRuleManager.get(level.getServer()).find(entity.getType());
        if (rule == null || !rule.enabled()) {
            state.clearLove();
            return;
        }
        ActiveLoveIndex.INSTANCE.add(level, entity.getUUID());
    }

    /**
     * 实体离开服务端 Level 时删除其活动索引；持久化 Love 截止时间仍由附件保存。
     *
     * @param event NeoForge 实体离开事件
     */
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ActiveLoveIndex.INSTANCE.remove(level, event.getEntity().getUUID());
        }
    }

    /**
     * 玩家开始追踪实体时补发尚未成年的客户端视觉状态。
     *
     * <p>该路径覆盖玩家登录、跨维度和实体后来进入追踪距离等情况；已经成年或未知状态不会发送数据包。
     *
     * @param event NeoForge 玩家开始追踪实体事件
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Entity target = event.getTarget();
        long now = target.level().getGameTime();
        BreedingState state = target.getData(ModAttachments.BREEDING_STATE);
        state.tickTimers(now);
        if (state.isJuvenile(now)) {
            PacketDistributor.sendToPlayer(player,
                    JuvenileStatePayload.fromState(target.getUUID(), state));
        }
    }
}
