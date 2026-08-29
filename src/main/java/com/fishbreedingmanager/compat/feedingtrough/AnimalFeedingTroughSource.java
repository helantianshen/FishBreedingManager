package com.fishbreedingmanager.compat.feedingtrough;

import com.fishbreedingmanager.FishBreedingManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 通过稳定方块 ID 与 Minecraft {@link Container} 契约访问 Animal Feeding Trough 的槽位 0。
 *
 * <p>本类不引用第三方 Java 类型，因此没有编译或运行硬依赖。若未来版本不再以标准 Container 暴露该方块实体，
 * 来源会安全地表现为空，而不是反射第三方私有字段。
 */
public final class AnimalFeedingTroughSource {
    /** Animal Feeding Trough 1.21.1 的稳定喂食槽方块 ID。 */
    public static final ResourceLocation FEEDING_TROUGH_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath("animal_feeding_trough", "feeding_trough");
    /** 生产环境共享来源。 */
    public static final AnimalFeedingTroughSource INSTANCE =
            new AnimalFeedingTroughSource(FEEDING_TROUGH_BLOCK_ID);

    private final ResourceLocation blockId;

    /**
     * 创建指定方块 ID 的来源；包级构造器允许测试使用已注册的原版容器方块验证契约。
     *
     * @param blockId 被视为喂食槽的方块 Registry ID
     */
    AnimalFeedingTroughSource(ResourceLocation blockId) {
        this.blockId = blockId;
    }

    /**
     * 查看目标方块实体槽位 0，不修改库存。
     *
     * @param level 当前世界只读视图
     * @param pos 待检查位置
     * @return 当前槽位物品；来源无效或没有槽位时返回 {@link ItemStack#EMPTY}
     */
    public ItemStack peek(LevelReader level, BlockPos pos) {
        Container container = findContainer(level, pos);
        if (container == null || container.getContainerSize() <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = container.getItem(0);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    /**
     * 从有效喂食槽槽位 0 移除一个物品。
     *
     * @param level 当前世界视图
     * @param pos 喂食槽位置
     * @return 实际移除到非空物品时返回 {@code true}
     */
    public boolean consumeOne(LevelReader level, BlockPos pos) {
        Container container = findContainer(level, pos);
        if (container == null || container.getContainerSize() <= 0) {
            return false;
        }
        ItemStack removed = container.removeItem(0, 1);
        if (removed == null || removed.isEmpty()) {
            return false;
        }
        container.setChanged();
        return true;
    }

    private Container findContainer(LevelReader level, BlockPos pos) {
        ResourceLocation actualId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!blockId.equals(actualId)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Container container) {
            return container;
        }
        FishBreedingManager.LOGGER.debug("FBM feeding trough source at {} does not expose Container", pos);
        return null;
    }
}
