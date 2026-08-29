package com.fishbreedingmanager.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.fishbreedingmanager.breeding.feed.BreedingFeedService;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** 验证统一喂食服务结果在玩家入口保持原有扣料与事件传播语义。 */
class EntityInteractionHandlerTest {
    /** 普通玩家成功喂食只扣一个物品并消费后续实体交互。 */
    @Test
    void consumesOneItemAndCancelsEventAfterSuccessfulFeed() {
        PlayerInteractEvent.EntityInteract event = mock(PlayerInteractEvent.EntityInteract.class);
        Player player = mock(Player.class);
        ItemStack held = mock(ItemStack.class);
        when(player.getAbilities()).thenReturn(new Abilities());

        assertTrue(EntityInteractionHandler.applyFeedResult(
                event, player, held, BreedingFeedService.FeedResult.FED));

        verify(held).shrink(1);
        verify(event).setCancellationResult(InteractionResult.SUCCESS);
        verify(event).setCanceled(true);
    }

    /** 创造模式成功喂食仍消费事件，但不得减少手持物。 */
    @Test
    void preservesCreativeStackWhileCancellingSuccessfulInteraction() {
        PlayerInteractEvent.EntityInteract event = mock(PlayerInteractEvent.EntityInteract.class);
        Player player = mock(Player.class);
        ItemStack held = mock(ItemStack.class);
        Abilities abilities = new Abilities();
        abilities.instabuild = true;
        when(player.getAbilities()).thenReturn(abilities);

        assertTrue(EntityInteractionHandler.applyFeedResult(
                event, player, held, BreedingFeedService.FeedResult.FED));

        verify(held, never()).shrink(1);
        verify(event).setCancellationResult(InteractionResult.SUCCESS);
        verify(event).setCanceled(true);
    }

    /** 任意拒绝结果必须保持物品和事件不变，让原版或其他 Mod 继续处理。 */
    @Test
    void leavesRejectedInteractionUntouched() {
        PlayerInteractEvent.EntityInteract event = mock(PlayerInteractEvent.EntityInteract.class);
        Player player = mock(Player.class);
        ItemStack held = mock(ItemStack.class);

        assertFalse(EntityInteractionHandler.applyFeedResult(
                event, player, held, BreedingFeedService.FeedResult.WRONG_FOOD));

        verifyNoInteractions(event, player, held);
    }
}
