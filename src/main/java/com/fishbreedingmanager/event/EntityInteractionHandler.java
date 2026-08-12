package com.fishbreedingmanager.event;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.attachment.ModAttachments;
import com.fishbreedingmanager.breeding.ActiveLoveIndex;
import com.fishbreedingmanager.breeding.BreedingRule;
import com.fishbreedingmanager.breeding.BreedingRuleManager;
import com.fishbreedingmanager.breeding.BreedingState;
import com.fishbreedingmanager.breeding.LoveParticleEmitter;

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
 * 服务端喂食交互处理器：玩家用当前规则食物右键实体时，使其进入 FBM Love。
 *
 * <p>规则从当前运行时快照动态查询，实体自身的 {@link BreedingState} 持有 Love 绝对截止时间。
 * 进入 Love 后注册到 {@link ActiveLoveIndex}，使控制器无需每 tick 扫描全世界实体即可寻找配偶。
 */
@EventBusSubscriber(modid = FishBreedingManager.MOD_ID)
public final class EntityInteractionHandler {
    /** 喂食后固定 600 游戏刻（约 30 秒）的 Love 时间窗。 */
    public static final long LOVE_DURATION_TICKS = 600L;

    private EntityInteractionHandler() {
    }

    /**
     * 在逻辑服务端处理实体右键喂食并决定是否消费原交互。
     *
     * <p>没有 FBM 规则、规则禁用或手持物不匹配时直接返回，不取消事件，允许原版和其他 Mod 继续处理。仅当 FBM
     * 实际使实体进入 Love 后才消耗一个物品、播放爱心并取消后续处理。
     *
     * @param event NeoForge 玩家右键实体事件
     */
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
        ActiveLoveIndex.INSTANCE.add((ServerLevel) target.level(), target.getUUID());

        // 消耗一个物品, 创造模式玩家豁免 
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        // 服务端直接发送粒子，兼容鳕鱼等不处理 Animal 实体事件 18 的目标。
        LoveParticleEmitter.emit((ServerLevel) target.level(), target);

        // 消费交互, 使原版/其他 handler 不再执行 
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        FishBreedingManager.LOGGER.debug("FBM: {} fed {} (love until {})",
                player.getName().getString(),
                BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()),
                state.getLoveUntil());
    }
}
