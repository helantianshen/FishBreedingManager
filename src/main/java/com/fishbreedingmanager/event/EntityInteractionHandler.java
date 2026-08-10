package com.fishbreedingmanager.event;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.attachment.ModAttachments;
import com.fishbreedingmanager.breeding.BreedingController;
import com.fishbreedingmanager.breeding.BreedingRule;
import com.fishbreedingmanager.breeding.BreedingRuleManager;
import com.fishbreedingmanager.breeding.BreedingState;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 喂食处理, 玩家右键实体且手持配置的繁殖物品时, 实体进入 FBM love 需求§4/§35 
 * 喂食是非侵入的, 用 {@link PlayerInteractEvent.EntityInteract} 事件而非修改任何实体类 
 *
 * <p>规则从当前运行时快照动态查询 需求§34, 实体自身的 {@link BreedingState} 持有临时 love 计时器 
 * 进入 love 后注册到 {@link BreedingController}, 使控制器无需每 tick 扫全图即可找配偶 需求§37 
 */
@EventBusSubscriber(modid = FishBreedingManager.MOD_ID)
public final class EntityInteractionHandler {
    /** 喂食后的 love 窗口 tick, 原版动物约 600 (30s) */
    public static final long LOVE_DURATION_TICKS = 600L;

    private EntityInteractionHandler() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity target = event.getTarget();
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack held = event.getItemStack();

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        // 对当前快照动态查规则, 热重载感知 
        BreedingRule rule = BreedingRuleManager.get(server).find(target.getType());
        if (rule == null || !rule.enabled()) {
            return;
        }
        if (!rule.testFood(held)) {
            return;
        }

        long now = target.level().getGameTime();
        BreedingState state = target.getData(ModAttachments.BREEDING_STATE);
        state.tickTimers(now);
        if (!state.canEnterLove(now)) {
            return;
        }

        // 进入 love 
        state.enterLove(now, LOVE_DURATION_TICKS);
        BreedingController.addLove((ServerLevel) target.level(), target.getUUID());

        // 消耗一个物品, 创造模式玩家豁免 
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        // 爱心粒子, 原版机制 同 Animal#setInLove 
        target.level().broadcastEntityEvent(target, (byte) 18);

        // 消费交互, 使原版/其他 handler 不再执行 
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        FishBreedingManager.LOGGER.debug("FBM: {} fed {} (love until {})",
                player.getName().getString(),
                BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()),
                state.getLoveUntil());
    }
}
