package com.fishbreedingmanager.event;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.attachment.ModAttachments;
import com.fishbreedingmanager.breeding.feed.BreedingFeedService;
import com.fishbreedingmanager.breeding.BreedingRuleManager;
import com.fishbreedingmanager.breeding.BreedingState;

import net.minecraft.core.registries.BuiltInRegistries;
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
 * <p>规则与状态转换委托给 {@link BreedingFeedService}，本类只负责玩家物品和交互事件语义。
 */
@EventBusSubscriber(modid = FishBreedingManager.MOD_ID)
public final class EntityInteractionHandler {
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

        if (player.getServer() == null || !(target.level() instanceof ServerLevel level)) {
            return;
        }

        BreedingFeedService.FeedResult result = BreedingFeedService.get().tryFeed(
                level, target, held, BreedingRuleManager.get(player.getServer()));
        if (!applyFeedResult(event, player, held, result)) {
            return;
        }

        BreedingState state = target.getData(ModAttachments.BREEDING_STATE);
        FishBreedingManager.LOGGER.debug("FBM: {} fed {} (love until {})",
                player.getName().getString(),
                BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()),
                state.getLoveUntil());
    }

    /**
     * 把统一喂食结果映射为玩家物品与 NeoForge 事件语义。
     *
     * <p>只有 {@link BreedingFeedService.FeedResult#FED} 会消费交互；普通玩家缩减一个物品，创造模式玩家保留物品。
     * 所有拒绝结果保持事件不变，使原版或其他 Mod 能继续处理。
     *
     * @param event 当前玩家实体交互事件
     * @param player 发起交互的玩家
     * @param held 当前手持物
     * @param result 统一喂食服务结果
     * @return 本入口实际消费交互时返回 {@code true}
     */
    static boolean applyFeedResult(PlayerInteractEvent.EntityInteract event, Player player,
                                   ItemStack held, BreedingFeedService.FeedResult result) {
        if (result != BreedingFeedService.FeedResult.FED) {
            return false;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        return true;
    }
}
