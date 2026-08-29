package com.fishbreedingmanager.breeding.feed;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import com.fishbreedingmanager.attachment.ModAttachments;
import com.fishbreedingmanager.breeding.ActiveLoveIndex;
import com.fishbreedingmanager.breeding.BreedingRule;
import com.fishbreedingmanager.breeding.BreedingRuleManager;
import com.fishbreedingmanager.breeding.BreedingState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * 将任意服务端喂食来源统一转换为 FBM Love 状态。
 *
 * <p>服务每次调用都从当前 {@link BreedingRuleManager} 查询规则，并负责 Attachment、活动索引和粒子反馈；它故意
 * 不消费 {@link ItemStack}，让玩家手持物、自动喂食容器等调用方只在收到 {@link FeedResult#FED} 后按各自语义扣料。
 */
public final class BreedingFeedService {
    /** 喂食后固定 600 游戏刻（约 30 秒）的 Love 时间窗。 */
    public static final long LOVE_DURATION_TICKS = 600L;

    private static final BreedingFeedService INSTANCE = new BreedingFeedService(
            (level, entityId) -> ActiveLoveIndex.INSTANCE.add(level, entityId),
            LoveParticleEmitter::emit);

    private final BiConsumer<ServerLevel, UUID> loveIndexer;
    private final BiConsumer<ServerLevel, Entity> particleEmitter;

    /**
     * 创建可注入副作用边界的服务实例。
     *
     * @param loveIndexer 成功后登记活动 Love 实体的回调
     * @param particleEmitter 成功后发送爱心粒子的回调
     */
    BreedingFeedService(BiConsumer<ServerLevel, UUID> loveIndexer,
                        BiConsumer<ServerLevel, Entity> particleEmitter) {
        this.loveIndexer = loveIndexer;
        this.particleEmitter = particleEmitter;
    }

    /**
     * 返回生产环境共享服务。
     *
     * @return 使用 FBM 活动索引与粒子发送器的服务
     */
    public static BreedingFeedService get() {
        return INSTANCE;
    }

    /**
     * 尝试用给定物品让实体进入 FBM Love。
     *
     * <p>拒绝路径不会创建 Attachment 或触发索引、粒子和扣料。匹配有效规则后才读取实体状态；已经 Love、冷却中
     * 或仍是 FBM 幼体的实体会返回 {@link FeedResult#INELIGIBLE}。
     *
     * @param level 目标所在的逻辑服务端 Level
     * @param target 待喂食实体
     * @param offered 喂食来源当前提供的物品；本方法不会修改它
     * @param manager 当前服务器规则管理器
     * @return 结构化接受或拒绝结果
     */
    public FeedResult tryFeed(ServerLevel level, Entity target, ItemStack offered,
                              BreedingRuleManager manager) {
        return tryFeed(level, target, offered, manager, () -> true);
    }

    /**
     * 尝试在来源成功消费一个物品后原子提交 FBM Love。
     *
     * <p>资格检查先完成；只有目标可接受时才调用 {@code sourceConsumer}。来源返回失败时不写 Love、索引或粒子，
     * 从而避免容器物品移除失败却免费进入 Love。调用方的消费回调与状态提交都运行在服务端主线程同一调用栈中。
     *
     * @param level 目标所在的逻辑服务端 Level
     * @param target 待喂食实体
     * @param offered 来源当前提供的物品快照
     * @param manager 当前服务器规则管理器
     * @param sourceConsumer 消费一个来源物品并报告是否成功的回调
     * @return 结构化接受或拒绝结果
     */
    public FeedResult tryFeed(ServerLevel level, Entity target, ItemStack offered,
                              BreedingRuleManager manager, BooleanSupplier sourceConsumer) {
        FeedCheck check = inspect(level, target, offered, manager);
        if (check.result() != FeedResult.FED) {
            return check.result();
        }
        if (!sourceConsumer.getAsBoolean()) {
            return FeedResult.SOURCE_UNAVAILABLE;
        }

        check.state().enterLove(check.now(), LOVE_DURATION_TICKS);
        loveIndexer.accept(level, target.getUUID());
        particleEmitter.accept(level, target);
        return FeedResult.FED;
    }

    /**
     * 判断当前物品能否喂给目标，而不提交新的 Love 状态。
     *
     * <p>该方法供自动喂食目标搜索与继续执行检查使用。它会惰性结算已经过期的计时器，但不会登记索引、发粒子或
     * 开启新的 Love 时间窗。
     *
     * @param level 目标所在的逻辑服务端 Level
     * @param target 待检查实体
     * @param offered 外部来源当前提供的物品
     * @param manager 当前服务器规则管理器
     * @return 当前规则、物品和状态均允许喂食时返回 {@code true}
     */
    public boolean canFeed(ServerLevel level, Entity target, ItemStack offered,
                           BreedingRuleManager manager) {
        return inspect(level, target, offered, manager).result() == FeedResult.FED;
    }

    private FeedCheck inspect(ServerLevel level, Entity target, ItemStack offered,
                              BreedingRuleManager manager) {
        BreedingRule rule = manager.find(target.getType());
        if (rule == null) {
            return new FeedCheck(FeedResult.NO_RULE, null, 0L);
        }
        if (!rule.enabled()) {
            return new FeedCheck(FeedResult.RULE_DISABLED, null, 0L);
        }
        if (!rule.testFood(offered)) {
            return new FeedCheck(FeedResult.WRONG_FOOD, null, 0L);
        }

        long now = level.getGameTime();
        BreedingState state = target.getData(ModAttachments.BREEDING_STATE);
        state.tickTimers(now);
        if (!state.canEnterLove(now)) {
            return new FeedCheck(FeedResult.INELIGIBLE, state, now);
        }
        return new FeedCheck(FeedResult.FED, state, now);
    }

    private record FeedCheck(FeedResult result, BreedingState state, long now) {
    }

    /** 喂食尝试的稳定结果，调用方只能在 {@link #FED} 时消费来源物品。 */
    public enum FeedResult {
        /** 当前快照中没有目标实体规则。 */
        NO_RULE,
        /** 目标规则存在但已禁用。 */
        RULE_DISABLED,
        /** 提供物品不匹配当前规则。 */
        WRONG_FOOD,
        /** 目标当前处于 Love、冷却或幼体状态。 */
        INELIGIBLE,
        /** 规则与状态允许，但外部来源未能消费对应物品。 */
        SOURCE_UNAVAILABLE,
        /** Love 已成功提交并已发送反馈。 */
        FED
    }
}
