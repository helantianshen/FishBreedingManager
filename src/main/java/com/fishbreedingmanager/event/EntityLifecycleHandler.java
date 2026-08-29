package com.fishbreedingmanager.event;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.attachment.ModAttachments;
import com.fishbreedingmanager.breeding.ActiveLoveIndex;
import com.fishbreedingmanager.breeding.BreedingRule;
import com.fishbreedingmanager.breeding.BreedingRuleManager;
import com.fishbreedingmanager.breeding.BreedingState;
import com.fishbreedingmanager.compat.CompatibilityCoordinator;
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
        BreedingRuleManager manager = BreedingRuleManager.get(level.getServer());
        restoreEntity(level, event.getEntity(), manager);
        CompatibilityCoordinator.get().installIfEligible(event.getEntity(), manager);
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
        BreedingState state = target.getExistingDataOrNull(ModAttachments.BREEDING_STATE);
        if (state == null) {
            return;
        }
        state.tickTimers(now);
        if (state.isJuvenile(now)) {
            PacketDistributor.sendToPlayer(player,
                    JuvenileStatePayload.fromState(target.getUUID(), state));
        }
    }

    /**
     * 在初始规则快照安装后，恢复启动阶段已经随出生区或强加载区进入世界的实体。
     *
     * <p>NeoForge 的 {@code ServerStartingEvent} 晚于部分实体 Join 事件，因此这些早期实体会被 Join 处理器暂时跳过。
     * 本方法遍历当前已加载实体，但只读取已经存在的 FBM Attachment，不会给普通实体创建空状态。
     *
     * @param server 已完成初始规则加载的逻辑服务器
     */
    public static void restoreLoadedEntities(net.minecraft.server.MinecraftServer server) {
        BreedingRuleManager manager = BreedingRuleManager.get(server);
        if (!manager.isInitialized()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                restoreEntity(level, entity, manager);
            }
        }
    }

    /**
     * 恢复单个实体已经存在的 FBM Love 状态。
     *
     * <p>管理器尚未初始化时完全不触碰实体，避免把启动暂态的空 Snapshot 误认为玩家配置；初始化后也使用
     * {@link Entity#getExistingDataOrNull}，从而不会给无关实体创建并持久化五个默认字段。
     *
     * @param level 实体当前所在的服务端 Level
     * @param entity 待恢复实体
     * @param manager 当前服务器规则管理器
     * @return 实体是否恢复到 {@link ActiveLoveIndex}
     */
    static boolean restoreEntity(ServerLevel level, Entity entity, BreedingRuleManager manager) {
        if (!manager.isInitialized()) {
            return false;
        }

        BreedingState state = entity.getExistingDataOrNull(ModAttachments.BREEDING_STATE);
        if (state == null || !state.prepareForLevelJoin(level.getGameTime())) {
            return false;
        }

        BreedingRule rule = manager.find(entity.getType());
        if (rule == null || !rule.enabled()) {
            state.clearLove();
            return false;
        }
        ActiveLoveIndex.INSTANCE.add(level, entity.getUUID());
        return true;
    }
}
