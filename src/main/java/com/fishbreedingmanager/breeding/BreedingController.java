package com.fishbreedingmanager.breeding;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.attachment.ModAttachments;
import com.fishbreedingmanager.network.JuvenileStatePayload;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 服务端权威的集中式运行时繁殖引擎，保持 Rule、State 与 Engine 分离。
 *
 * <p>引擎由 {@link LevelTickEvent.Pre} 驱动，通过 {@link ActiveLoveIndex} 只遍历当前 Level 中处于 FBM Love 的实体，
 * 不扫描全世界。每五个游戏刻执行一次以下流程：
 * <ol>
 *   <li>清理实体缺失、规则删除/禁用或 Love 过期的索引；</li>
 *   <li>在搜索半径内为同类型、可用的 Love 实体建立双向临时配对；</li>
 *   <li>寻路靠近并在生成前重新验证双方状态；</li>
 *   <li>仅在同类型后代成功加入世界后提交父母冷却与 Love 清除。</li>
 * </ol>
 *
 * <p>每轮都从 {@link BreedingRuleManager} 动态查询规则，热更新立即影响现存实体；附件上的冷却与成长截止时间不重算。
 */
@EventBusSubscriber(modid = FishBreedingManager.MOD_ID)
public final class BreedingController {
    /** 每 N tick 跑一次搜索/繁殖 pass 以限制开销 需求§37 */
    private static final long SEARCH_INTERVAL = 5L;
    /** 查找配偶 AABB 的半宽, 单位方块 */
    private static final double SEARCH_RADIUS = 8.0;
    private static final double SEARCH_RADIUS_SQR = SEARCH_RADIUS * SEARCH_RADIUS;
    /** 两实体此距离 平方 内可产生后代 */
    private static final double BREED_DISTANCE_SQR = 2.25D;
    /** 靠近配偶时的寻路速度倍率 */
    private static final double NAV_SPEED = 1.0D;
    /** 只负责创建后代、不修改父母状态的生成器。 */
    private static final ChildSpawner CHILD_SPAWNER = new ChildSpawner();

    private BreedingController() {
    }

    /**
     * 在服务端 Level 的节流 tick 中推进清理、匹配、寻路与繁殖。
     *
     * @param event NeoForge Level tick 前置事件
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % SEARCH_INTERVAL != 0L) {
            return;
        }
        List<UUID> ids = ActiveLoveIndex.INSTANCE.snapshot(level);
        if (ids.isEmpty()) {
            return;
        }

        MinecraftServer server = level.getServer();
        BreedingRuleManager manager = BreedingRuleManager.get(server);
        long now = level.getGameTime();

        for (UUID id : ids) {
            Entity entity = level.getEntity(id);
            if (entity == null || entity.isRemoved()) {
                ActiveLoveIndex.INSTANCE.remove(level, id);
                continue;
            }

            BreedingRule rule = manager.find(entity.getType());
            BreedingState state = entity.getData(ModAttachments.BREEDING_STATE);
            state.tickTimers(now);

            // 规则被删/禁用 → 立即清除该实体 FBM love 需求§28 
            if (rule == null || !rule.enabled()) {
                state.clearLove();
                ActiveLoveIndex.INSTANCE.remove(level, id);
                continue;
            }
            // love 计时器过期 
            if (!state.isInLove(now)) {
                ActiveLoveIndex.INSTANCE.remove(level, id);
                continue;
            }

            if (state.getMate() != null) {
                handlePaired(level, entity, state, rule, now);
            } else {
                findAndPair(level, entity, state, now);
            }
        }
    }

    /**
     * 验证已配对双方，保持寻路并在距离足够近时尝试繁殖。
     *
     * @param level 当前服务端 Level
     * @param entity 当前处理实体
     * @param state 当前实体状态
     * @param rule 当前动态规则
     * @param now 当前绝对游戏刻
     */
    private static void handlePaired(ServerLevel level, Entity entity, BreedingState state,
                                     BreedingRule rule, long now) {
        Entity mate = level.getEntity(state.getMate());
        if (mate == null) {
            state.setMate(null);
            return;
        }
        BreedingState mateState = mate.getData(ModAttachments.BREEDING_STATE);
        state.tickTimers(now);
        mateState.tickTimers(now);
        if (!isValidPair(entity, state, mate, mateState, now)) {
            clearPairReferences(entity, state, mate, mateState);
            return;
        }
        navigateToward(entity, mate);
        if (entity.distanceToSqr(mate) <= BREED_DISTANCE_SQR) {
            breed(level, entity, mate, rule, now);
        }
    }

    /**
     * 为未配对实体寻找附近同类型可用伙伴，并建立双向临时 UUID 引用。
     *
     * @param level 当前服务端 Level
     * @param entity 当前处理实体
     * @param state 当前实体状态
     * @param now 当前绝对游戏刻
     */
    private static void findAndPair(ServerLevel level, Entity entity, BreedingState state,
                                    long now) {
        EntityType<?> type = entity.getType();
        List<UUID> candidates = ActiveLoveIndex.INSTANCE.snapshot(level);
        for (UUID otherId : candidates) {
            if (otherId.equals(entity.getUUID())) {
                continue;
            }
            Entity other = level.getEntity(otherId);
            if (other == null || other.isRemoved() || other.getType() != type) {
                continue;
            }
            BreedingState otherState = other.getData(ModAttachments.BREEDING_STATE);
            otherState.tickTimers(now);
            if (!otherState.isInLove(now)
                    || otherState.isOnCooldown(now)
                    || otherState.isJuvenile(now)
                    || otherState.getMate() != null) {
                continue;
            }
            if (entity.distanceToSqr(other) > SEARCH_RADIUS_SQR) {
                continue;
            }
            // 配偶引用必须双向写入，后续生成前会再次验证互相指向。
            state.setMate(other.getUUID());
            otherState.setMate(entity.getUUID());
            navigateToward(entity, other);
            navigateToward(other, entity);
            return;
        }
    }

    /**
     * 尝试生成后代，并且只在后代真正加入世界后提交父母状态。
     *
     * @param level 两亲本所在的服务端 Level
     * @param a 第一亲本
     * @param b 第二亲本
     * @param rule 当前生效规则
     * @param now 当前绝对游戏刻
     */
    private static void breed(ServerLevel level, Entity a, Entity b, BreedingRule rule,
                              long now) {
        BreedingState sa = a.getData(ModAttachments.BREEDING_STATE);
        BreedingState sb = b.getData(ModAttachments.BREEDING_STATE);

        ChildSpawnResult result = CHILD_SPAWNER.spawn(level, a, b, rule, now);
        if (!applySpawnResult(result, sa, sb, rule, now)) {
            FishBreedingManager.LOGGER.warn("FBM 后代生成失败，保留父母 Love 以便重试: entity={}, status={}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(a.getType()), result.status());
            return;
        }

        Entity child = Objects.requireNonNull(result.child());
        BreedingState childState = child.getData(ModAttachments.BREEDING_STATE);
        LoveParticleEmitter.emit(level, child);
        PacketDistributor.sendToPlayersTrackingEntity(child,
                JuvenileStatePayload.fromState(child.getUUID(), childState));
        LoveParticleEmitter.emit(level, a);
        LoveParticleEmitter.emit(level, b);

        ActiveLoveIndex.INSTANCE.remove(level, a.getUUID());
        ActiveLoveIndex.INSTANCE.remove(level, b.getUUID());

        FishBreedingManager.LOGGER.debug("FBM: {} bred, child spawned",
                BuiltInRegistries.ENTITY_TYPE.getKey(a.getType()));
    }

    /**
     * 根据后代生成结果提交或回滚父母的临时配对状态。
     *
     * <p>失败时只清除配偶 UUID，保留 Love 与空闲冷却以便在剩余时间窗内重试；成功时才启动双方冷却并清除 Love。
     * 方法不依赖 Minecraft 实体，保持包级可见以便直接验证状态策略。
     *
     * @param result 后代生成结果
     * @param first 第一亲本状态
     * @param second 第二亲本状态
     * @param rule 当前生效规则
     * @param now 当前绝对游戏刻
     * @return 是否已经提交成功繁殖状态
     */
    static boolean applySpawnResult(ChildSpawnResult result, BreedingState first,
                                    BreedingState second, BreedingRule rule, long now) {
        if (!result.successful()) {
            first.setMate(null);
            second.setMate(null);
            return false;
        }
        first.startCooldown(now, rule.breedingCooldownTicks());
        second.startCooldown(now, rule.breedingCooldownTicks());
        first.clearLove();
        second.clearLove();
        return true;
    }

    /**
     * 验证当前配对仍满足同类型、双方有效 Love、无冷却、非幼体且 UUID 互相指向。
     *
     * @param first 第一亲本实体
     * @param firstState 第一亲本状态
     * @param second 第二亲本实体
     * @param secondState 第二亲本状态
     * @param now 当前绝对游戏刻
     * @return 仅在双方仍可继续本次配对时返回 {@code true}
     */
    static boolean isValidPair(Entity first, BreedingState firstState,
                               Entity second, BreedingState secondState, long now) {
        return first != second
                && !first.isRemoved()
                && !second.isRemoved()
                && first.getType() == second.getType()
                && firstState.isInLove(now)
                && secondState.isInLove(now)
                && !firstState.isOnCooldown(now)
                && !secondState.isOnCooldown(now)
                && !firstState.isJuvenile(now)
                && !secondState.isJuvenile(now)
                && second.getUUID().equals(firstState.getMate())
                && first.getUUID().equals(secondState.getMate());
    }

    /**
     * 只清除仍然互相指向当前双方的配偶引用，避免覆盖已经重新建立的新配对。
     *
     * @param first 第一实体
     * @param firstState 第一实体状态
     * @param second 第二实体
     * @param secondState 第二实体状态
     */
    private static void clearPairReferences(Entity first, BreedingState firstState,
                                            Entity second, BreedingState secondState) {
        if (second.getUUID().equals(firstState.getMate())) {
            firstState.setMate(null);
        }
        if (first.getUUID().equals(secondState.getMate())) {
            secondState.setMate(null);
        }
    }

    /**
     * 若实体属于 {@link Mob} 则使用原生导航向配偶移动；非 Mob 实体保持原位但仍兼容近距离生成。
     *
     * @param self 需要移动的实体
     * @param target 目标配偶
     */
    private static void navigateToward(Entity self, Entity target) {
        if (self instanceof Mob mob) {
            mob.getNavigation().moveTo(target, NAV_SPEED);
        }
        // 非 Mob 实体没有通用导航 API，跳过移动而不破坏其他繁殖状态。
    }
}
