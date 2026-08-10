package com.fishbreedingmanager.breeding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.attachment.ModAttachments;
import com.fishbreedingmanager.network.JuvenileStatePayload;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 集中式运行时繁殖引擎 需求§31, Rule vs State vs Engine 分离 
 *
 * <p>服务端由 {@link LevelTickEvent.Pre} 驱动, 每 {@link ServerLevel} 维护内存 <b>love registry</b> 
 * 只跟踪当前处于 FBM love 的实体, 故引擎不会每 tick 扫全图 需求§37 
 * 每 tick 节流 后, 
 * <ol>
 *   <li>丢弃规则被删/禁用的实体, 清除其 FBM love 需求§28 </li>
 *   <li>结算过期的 love </li>
 *   <li>在搜索半径内配对同类型 in-love 实体并寻路靠近 </li>
 *   <li>配对足够近时生成同类型后代 需求§5 不杂交, 
 *       标记幼体, 给父母冷却, 清除 love </li>
 * </ol>
 *
 * <p>规则从 {@link BreedingRuleManager} 动态查询, 实体上的计时器 reload 时绝不重算 需求§38/§39 
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

    /** 每 level 的 love registry, WeakHashMap 使已卸载 level 的数据可回收 */
    private static final Map<ServerLevel, Set<UUID>> LOVE_REGISTRIES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private BreedingController() {
    }

    /** 注册刚进入 FBM love 的实体到给定 level */
    public static void addLove(ServerLevel level, UUID entityId) {
        LOVE_REGISTRIES.computeIfAbsent(level, l -> Collections.synchronizedSet(Collections.newSetFromMap(new java.util.HashMap<>())))
                .add(entityId);
    }

    /** 从 level 的 love registry 移除实体, 繁殖或 love 过期后调用 */
    private static void removeLove(ServerLevel level, UUID entityId) {
        Set<UUID> set = LOVE_REGISTRIES.get(level);
        if (set != null) {
            set.remove(entityId);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % SEARCH_INTERVAL != 0L) {
            return;
        }
        Set<UUID> registry = LOVE_REGISTRIES.get(level);
        if (registry == null || registry.isEmpty()) {
            return;
        }

        MinecraftServer server = level.getServer();
        BreedingRuleManager manager = BreedingRuleManager.get(server);
        long now = level.getGameTime();

        // 快照 id 列表, 以便迭代时可修改活跃集合 
        List<UUID> ids;
        synchronized (registry) {
            ids = new ArrayList<>(registry);
        }

        for (UUID id : ids) {
            Entity entity = level.getEntity(id);
            if (entity == null || entity.isRemoved()) {
                registry.remove(id);
                continue;
            }

            BreedingRule rule = manager.find(entity.getType());
            BreedingState state = entity.getData(ModAttachments.BREEDING_STATE);
            state.tickTimers(now);

            // 规则被删/禁用 → 立即清除该实体 FBM love 需求§28 
            if (rule == null || !rule.enabled()) {
                state.clearLove();
                registry.remove(id);
                continue;
            }
            // love 计时器过期 
            if (!state.isInLove(now)) {
                registry.remove(id);
                continue;
            }

            if (state.getMate() != null) {
                handlePaired(level, entity, state, rule, now, registry);
            } else {
                findAndPair(level, entity, state, manager, now, registry);
            }
        }
    }

    /** 已配对, 向配偶移动, 足够近则繁殖 */
    private static void handlePaired(ServerLevel level, Entity entity, BreedingState state,
                                     BreedingRule rule, long now, Set<UUID> registry) {
        Entity mate = level.getEntity(state.getMate());
        if (mate == null || mate.isRemoved()) {
            state.setMate(null);
            return;
        }
        navigateToward(entity, mate);
        if (entity.distanceToSqr(mate) <= BREED_DISTANCE_SQR) {
            breed(level, entity, mate, rule, now, registry);
        }
    }

    /** 未配对, 寻找附近同类型 in-love 伙伴并互相认定 */
    private static void findAndPair(ServerLevel level, Entity entity, BreedingState state,
                                    BreedingRuleManager manager, long now, Set<UUID> registry) {
        EntityType<?> type = entity.getType();
        List<UUID> candidates;
        synchronized (registry) {
            candidates = new ArrayList<>(registry);
        }
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
            if (!otherState.isInLove(now) || otherState.getMate() != null) {
                continue;
            }
            if (entity.distanceToSqr(other) > SEARCH_RADIUS_SQR) {
                continue;
            }
            // 互相认定 
            state.setMate(other.getUUID());
            otherState.setMate(entity.getUUID());
            navigateToward(entity, other);
            navigateToward(other, entity);
            return;
        }
    }

    /** 两亲本产生后代, 应用冷却并清除 love 需求§5/§35 */
    private static void breed(ServerLevel level, Entity a, Entity b, BreedingRule rule,
                              long now, Set<UUID> registry) {
        BreedingState sa = a.getData(ModAttachments.BREEDING_STATE);
        BreedingState sb = b.getData(ModAttachments.BREEDING_STATE);

        // 在中点生成同类型后代 不杂交 需求§5 
        EntityType<?> type = a.getType();
        Entity child = type.create(level);
        if (child != null) {
            Vec3 mid = a.position().add(b.position()).scale(0.5D);
            child.moveTo(mid.x, mid.y, mid.z, 0.0F, 0.0F);
            BreedingState childState = child.getData(ModAttachments.BREEDING_STATE);
            childState.markJuvenile(now, rule.growthTimeTicks());
            if (level.addFreshEntity(child)) {
                // 后代出生爱心粒子 
                level.broadcastEntityEvent(child, (byte) 18);
                // 通知 tracking 客户端这是幼体 客户端 attachment 不自动同步 
                PacketDistributor.sendToPlayersTrackingEntity(child,
                        new JuvenileStatePayload(child.getUUID(), now, rule.growthTimeTicks()));
            }
        }

        // 父母冷却 + 清除 love 
        sa.startCooldown(now, rule.breedingCooldownTicks());
        sb.startCooldown(now, rule.breedingCooldownTicks());
        sa.clearLove();
        sb.clearLove();
        level.broadcastEntityEvent(a, (byte) 18);
        level.broadcastEntityEvent(b, (byte) 18);

        registry.remove(a.getUUID());
        registry.remove(b.getUUID());

        FishBreedingManager.LOGGER.debug("FBM: {} bred, child spawned",
                BuiltInRegistries.ENTITY_TYPE.getKey(a.getType()));
    }

    /** 若 {@code self} 有寻路则向 {@code target} 移动, 非 Mob 实体无法寻路 */
    private static void navigateToward(Entity self, Entity target) {
        if (self instanceof Mob mob) {
            mob.getNavigation().moveTo(target, NAV_SPEED);
        }
        // 非 Mob 实体 部分兼容 需求§42 跳过寻路 
    }
}
