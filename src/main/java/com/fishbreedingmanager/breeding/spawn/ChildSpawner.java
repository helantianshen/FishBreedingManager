package com.fishbreedingmanager.breeding.spawn;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.attachment.ModAttachments;
import com.fishbreedingmanager.breeding.BreedingRule;
import com.fishbreedingmanager.breeding.BreedingState;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * 创建、定位、初始化并把同类型后代加入服务端世界。
 *
 * <p>该类只处理后代，不修改父母的 {@link BreedingState}。只有返回 {@link ChildSpawnStatus#SUCCESS} 后，调用方才可
 * 提交父母冷却并清除 Love；任何失败都必须保留父母的繁殖机会。
 *
 * <p>后代在加入世界之前会先尝试通过 {@link VariantInheritance} 随机继承父母一方的 Variant，因此 Variant 随首次
 * 实体生成包一起下发；继承失败只影响外观，不改变生成结果。
 */
public final class ChildSpawner {
    private final VariantInheritance variantInheritance;
    private final RandomSource random;

    /**
     * 创建使用真实 Variant 继承通道的后代生成器。
     */
    public ChildSpawner() {
        this(new VariantInheritance(), RandomSource.create());
    }

    /**
     * 创建可注入 Variant 继承边界与随机源的生成器，供测试固定捐赠方选择。
     *
     * @param variantInheritance Variant 继承实现
     * @param random 选择捐赠方使用的随机源
     */
    ChildSpawner(VariantInheritance variantInheritance, RandomSource random) {
        this.variantInheritance = variantInheritance;
        this.random = random;
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

        VariantInheritanceResult variantResult =
                variantInheritance.inherit(child, firstParent, secondParent, random);

        Vec3 midpoint = firstParent.position().add(secondParent.position()).scale(0.5D);
        child.moveTo(midpoint.x, midpoint.y, midpoint.z, 0.0F, 0.0F);
        BreedingState childState = child.getData(ModAttachments.BREEDING_STATE);
        childState.markJuvenile(now, rule.growthTimeTicks());
        if (!level.addFreshEntity(child)) {
            return ChildSpawnResult.failure(ChildSpawnStatus.ADD_TO_LEVEL_FAILED);
        }
        FishBreedingManager.LOGGER.debug("FBM 后代已生成: entity={}, variant={}",
                BuiltInRegistries.ENTITY_TYPE.getKey(type), variantResult);
        return ChildSpawnResult.success(child);
    }
}
