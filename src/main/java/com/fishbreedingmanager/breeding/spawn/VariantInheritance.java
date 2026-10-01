package com.fishbreedingmanager.breeding.spawn;

import java.util.Set;
import java.util.function.Function;

import com.fishbreedingmanager.FishBreedingManager;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 通过 Minecraft 公共契约尝试继承同类型亲本的变种数据
 *
 * <p>先确认后代与双方亲本的 EntityType 相同，再等概率选择一方作为捐赠者
 * 优先读取 {@link Bucketable} 桶数据，剔除通用状态字段后交给后代；没有可复制桶数据时尝试 {@link VariantHolder}
 * 没有公共通道时保留默认个体，不反射或复制私有字段，也不要求第三方实现 FBM 专用接口
 *
 * <p>桶契约可能包含外观以外的数据，本类只过滤明确列出的通用字段，不保证识别所有第三方数据语义
 * 回调异常只记录失败，不阻止后代生成；第三方回调抛错前已经写入的字段没有通用回滚保证
 */
public final class VariantInheritance {
    /**
     * 由 {@link Bucketable#saveDefaultDataToBucketTag} 统一写入的通用字段
     *
     * <p>这些键包含血量、AI 与重力等通用个体状态，必须在复制变种数据前剔除，避免后代继承亲本的运行时状态
     */
    private static final Set<String> BUCKET_DEFAULT_KEYS = Set.of(
            "NoAI", "Silent", "NoGravity", "Glowing", "Invulnerable", "Health");

    private final Function<Bucketable, CompoundTag> bucketTagReader;

    /** 创建使用真实 {@link Bucketable} 桶标签的继承器 */
    public VariantInheritance() {
        this(VariantInheritance::readBucketTag);
    }

    /**
     * 创建可注入桶标签读取边界的继承器，供同包测试在不构造真实 {@link ItemStack} 的情况下验证过滤与写入顺序
     *
     * @param bucketTagReader 从捐赠方读取桶标签副本的函数
     */
    VariantInheritance(Function<Bucketable, CompoundTag> bucketTagReader) {
        this.bucketTagReader = bucketTagReader;
    }

    /**
     * 随机选出一方父母并尽量把其 Variant 复制到后代
     *
     * <p>方法必须在后代加入世界之前调用，使 Variant 随首次实体生成包一起下发到客户端
     * 回调异常转换为失败结果，由生成器继续处理出生流程，不保证回滚第三方已经写入的数据
     *
     * @param child 刚由 {@code EntityType.create} 创建、尚未加入世界的后代
     * @param firstParent 第一亲本
     * @param secondParent 第二亲本
     * @param random 用于 50/50 选择捐赠方的随机源
     * @return 实际生效的继承通道，或表示保持默认个体的结果
     */
    public VariantInheritanceResult inherit(Entity child, Entity firstParent,
                                            Entity secondParent, RandomSource random) {
        // 未确认同类型时不做任何转换：VariantHolder 写入依赖同一 EntityType 使用一致的变种类型
        if (child.getType() != firstParent.getType()
                || child.getType() != secondParent.getType()) {
            return VariantInheritanceResult.DEFAULT_INDIVIDUAL;
        }

        Entity donor = random.nextBoolean() ? firstParent : secondParent;
        try {
            if (copyBucketData(child, donor)) {
                return VariantInheritanceResult.BUCKET_DATA;
            }
            if (copyVariantHolder(child, donor)) {
                return VariantInheritanceResult.VARIANT_HOLDER;
            }
            return VariantInheritanceResult.DEFAULT_INDIVIDUAL;
        } catch (RuntimeException exception) {
            FishBreedingManager.LOGGER.error(
                    "FBM Variant 继承失败，后代保持默认个体: entity={}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(child.getType()), exception);
            return VariantInheritanceResult.FAILED;
        }
    }

    /**
     * 通过桶数据契约复制捐赠方的身份数据
     *
     * @param child 目标后代
     * @param donor 被选中的亲本
     * @return 实际写入了非通用字段时返回 {@code true}
     */
    private boolean copyBucketData(Entity child, Entity donor) {
        if (!(child instanceof Bucketable target) || !(donor instanceof Bucketable source)) {
            return false;
        }
        CompoundTag variantData = bucketTagReader.apply(source);
        stripDefaultData(variantData);
        if (variantData.isEmpty()) {
            return false;
        }
        target.loadFromBucketTag(variantData);
        return true;
    }

    /**
     * 原地剔除桶标签中的通用个体状态字段，只保留实体自有的身份数据
     *
     * <p>保持包级可见以便单独验证过滤集合，不需要构造实体或物品栈
     *
     * @param bucketTag 捐赠方桶标签的可修改副本
     */
    static void stripDefaultData(CompoundTag bucketTag) {
        BUCKET_DEFAULT_KEYS.forEach(bucketTag::remove);
    }

    /**
     * 通过 Mojang 公共 {@link VariantHolder} 接口复制 Variant
     *
     * <p>调用方已确认双方 {@code EntityType} 相同，因此两侧的 {@code VariantHolder} 类型参数必然一致
     * 这里的非受检转换在运行时是安全的
     *
     * @param child 目标后代
     * @param donor 被选中的亲本
     * @return 实际复制了 Variant 时返回 {@code true}
     */
    @SuppressWarnings("unchecked")
    private static boolean copyVariantHolder(Entity child, Entity donor) {
        if (!(child instanceof VariantHolder<?> target)
                || !(donor instanceof VariantHolder<?> source)) {
            return false;
        }
        Object variant = source.getVariant();
        if (variant == null) {
            return false;
        }
        ((VariantHolder<Object>) target).setVariant(variant);
        return true;
    }

    /**
     * 用捐赠方自己的桶物品读取其桶标签副本；该过程只修改临时物品栈，不改变实体状态
     *
     * @param source 被选中的亲本
     * @return 桶标签的可修改副本；实体不提供桶物品时返回空标签
     */
    private static CompoundTag readBucketTag(Bucketable source) {
        ItemStack probe = source.getBucketItemStack();
        if (probe.isEmpty()) {
            return new CompoundTag();
        }
        source.saveToBucketTag(probe);
        return probe.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY).copyTag();
    }
}
