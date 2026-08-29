package com.fishbreedingmanager.compat.feedingtrough;

import com.fishbreedingmanager.breeding.feed.BreedingFeedService;
import com.fishbreedingmanager.breeding.BreedingRuleManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;

/**
 * 让有 FBM 规则但不继承原版 Animal 的可寻路实体主动使用 Animal Feeding Trough。
 *
 * <p>目标不缓存食物或规则；搜索、继续执行和到达时都通过 {@link BreedingFeedService} 查询当前运行时快照，保证
 * 规则禁用、删除或换食物后立即停止生效。
 */
public final class FbmTroughSelfFeedGoal extends MoveToBlockGoal {
    private static final double SPEED_MODIFIER = 1.0D;
    private static final int SEARCH_RANGE = 8;

    private final BreedingRuleManager manager;
    private final BreedingFeedService feedService;
    private final AnimalFeedingTroughSource source;

    /**
     * 创建 FBM 喂食槽寻路目标。
     *
     * @param mob 接受目标的可寻路实体
     * @param manager 当前服务器规则管理器
     * @param feedService 统一 Love 状态转换服务
     * @param source 喂食槽库存来源
     */
    FbmTroughSelfFeedGoal(PathfinderMob mob, BreedingRuleManager manager,
                          BreedingFeedService feedService, AnimalFeedingTroughSource source) {
        super(mob, SPEED_MODIFIER, SEARCH_RANGE);
        this.manager = manager;
        this.feedService = feedService;
        this.source = source;
    }

    /**
     * 只允许在逻辑服务端开始方块搜索。
     *
     * @return 服务端存在符合当前规则的喂食槽时返回 {@code true}
     */
    @Override
    public boolean canUse() {
        return mob.level() instanceof ServerLevel && super.canUse();
    }

    /** 喂食槽允许实体靠近到两格内完成取食。 */
    @Override
    public double acceptedDistance() {
        return 2.0D;
    }

    /**
     * 判断位置当前是否为包含匹配 FBM 食物的可用喂食槽。
     *
     * @param level 搜索使用的世界视图
     * @param pos 候选位置
     * @return 来源与当前规则、实体状态同时接受时返回 {@code true}
     */
    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        ItemStack offered = source.peek(level, pos);
        return !offered.isEmpty() && feedService.canFeed(serverLevel, mob, offered, manager);
    }

    /** 到达目标后提交 Love，并且仅在提交成功时消费容器物品。 */
    @Override
    public void tick() {
        super.tick();
        if (isReachedTarget()) {
            feedAtTarget(blockPos);
        }
    }

    /**
     * 尝试从指定目标完成一次喂食；包级入口用于验证扣料顺序。
     *
     * @param pos 当前到达的喂食槽位置
     * @return Love 提交且成功从容器移除一个物品时返回 {@code true}
     */
    boolean feedAtTarget(BlockPos pos) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        ItemStack offered = source.peek(level, pos);
        if (offered.isEmpty()) {
            return false;
        }
        BreedingFeedService.FeedResult result = feedService.tryFeed(
                level, mob, offered, manager, () -> source.consumeOne(level, pos));
        return result == BreedingFeedService.FeedResult.FED;
    }
}
