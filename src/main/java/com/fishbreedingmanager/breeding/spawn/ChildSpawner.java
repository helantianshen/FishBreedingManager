package com.fishbreedingmanager.breeding.spawn;

import com.fishbreedingmanager.attachment.ModAttachments;
import com.fishbreedingmanager.breeding.BreedingRule;
import com.fishbreedingmanager.breeding.BreedingState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * 创建、定位、初始化并把同类型后代加入服务端世界。
 *
 * <p>该类只处理后代，不修改父母的 {@link BreedingState}。只有返回 {@link ChildSpawnStatus#SUCCESS} 后，调用方才可
 * 提交父母冷却并清除 Love；任何失败都必须保留父母的繁殖机会。
 */
public final class ChildSpawner {
    /**
     * 创建无内部状态的后代生成器。
     */
    public ChildSpawner() {
    }

    /**
     * 尝试在两亲本中点生成与第一亲本相同实体类型的幼体。
     *
     * @param level 后代应加入的服务端 Level
     * @param firstParent 第一亲本，同时提供后代 {@link EntityType}
     * @param secondParent 第二亲本，与第一亲本共同确定出生中点
     * @param rule 当前生效规则，提供固定成长时间
     * @param now 当前世界绝对游戏刻
     * @return 区分创建失败、加入失败和成功的结构化结果
     */
    public ChildSpawnResult spawn(ServerLevel level, Entity firstParent, Entity secondParent,
                                  BreedingRule rule, long now) {
        EntityType<?> type = firstParent.getType();
        Entity child = type.create(level);
        if (child == null) {
            return ChildSpawnResult.failure(ChildSpawnStatus.TYPE_CREATION_FAILED);
        }

        Vec3 midpoint = firstParent.position().add(secondParent.position()).scale(0.5D);
        child.moveTo(midpoint.x, midpoint.y, midpoint.z, 0.0F, 0.0F);
        BreedingState childState = child.getData(ModAttachments.BREEDING_STATE);
        childState.markJuvenile(now, rule.growthTimeTicks());
        if (!level.addFreshEntity(child)) {
            return ChildSpawnResult.failure(ChildSpawnStatus.ADD_TO_LEVEL_FAILED);
        }
        return ChildSpawnResult.success(child);
    }
}
