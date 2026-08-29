package com.fishbreedingmanager.compat.feedingtrough;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 验证喂食槽适配只依赖方块 Registry ID 与原版 Container 契约。 */
class AnimalFeedingTroughSourceTest {
    private static final BlockPos POS = new BlockPos(2, 3, 4);

    /** 正确方块 ID 且方块实体实现 Container 时应暴露槽位 0。 */
    @Test
    void readsSlotZeroFromMatchingContainerBlockEntity() {
        AnimalFeedingTroughSource source = chestBackedSource();
        LevelReader level = mock(LevelReader.class);
        BlockState state = mock(BlockState.class);
        BlockEntity blockEntity = mock(BlockEntity.class, withSettings().extraInterfaces(Container.class));
        Container container = (Container) blockEntity;
        ItemStack stack = mock(ItemStack.class);

        when(level.getBlockState(POS)).thenReturn(state);
        when(state.getBlock()).thenReturn(Blocks.CHEST);
        when(level.getBlockEntity(POS)).thenReturn(blockEntity);
        when(container.getContainerSize()).thenReturn(1);
        when(container.getItem(0)).thenReturn(stack);

        assertSame(stack, source.peek(level, POS));
    }

    /** Registry ID 不匹配时不得探测或修改碰巧实现 Container 的其他方块实体。 */
    @Test
    void rejectsDifferentBlockBeforeReadingInventory() {
        AnimalFeedingTroughSource source = chestBackedSource();
        LevelReader level = mock(LevelReader.class);
        BlockState state = mock(BlockState.class);
        BlockEntity blockEntity = mock(BlockEntity.class, withSettings().extraInterfaces(Container.class));
        Container container = (Container) blockEntity;

        when(level.getBlockState(POS)).thenReturn(state);
        when(state.getBlock()).thenReturn(Blocks.FURNACE);
        when(level.getBlockEntity(POS)).thenReturn(blockEntity);

        assertTrue(source.peek(level, POS).isEmpty());
        verify(container, never()).getItem(0);
    }

    /** 成功消费应通过 Container API 精确移除槽位 0 的一个物品。 */
    @Test
    void consumesExactlyOneItemThroughContainerApi() {
        AnimalFeedingTroughSource source = chestBackedSource();
        LevelReader level = mock(LevelReader.class);
        BlockState state = mock(BlockState.class);
        BlockEntity blockEntity = mock(BlockEntity.class, withSettings().extraInterfaces(Container.class));
        Container container = (Container) blockEntity;
        ItemStack removed = mock(ItemStack.class);

        when(level.getBlockState(POS)).thenReturn(state);
        when(state.getBlock()).thenReturn(Blocks.CHEST);
        when(level.getBlockEntity(POS)).thenReturn(blockEntity);
        when(container.getContainerSize()).thenReturn(1);
        when(container.removeItem(0, 1)).thenReturn(removed);
        when(removed.isEmpty()).thenReturn(false);

        assertTrue(source.consumeOne(level, POS));
        verify(container).removeItem(0, 1);
        verify(container).setChanged();
    }

    private static AnimalFeedingTroughSource chestBackedSource() {
        return new AnimalFeedingTroughSource(ResourceLocation.parse("minecraft:chest"));
    }
}
