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
 * 让后代随机继承父母其中一方的 Variant，且只依赖 Minecraft 的公共契约（需求 §12）。
 *
 * <p>父母双方 {@code EntityType} 相同，因此"继承"永远发生在同一实体类内部：先按 50/50 随机选出捐赠方，
 * 再依次尝试两条公共通道，第一条成功即停止。
 * <ol>
 *   <li><b>{@link Bucketable} 桶数据：</b>Minecraft 用"被水桶装走再放出来时必须保留什么"定义实体的身份数据。
 *       读取捐赠方 {@code saveToBucketTag} 写出的标签，剔除 {@link Bucketable#saveDefaultDataToBucketTag}
 *       负责的通用字段后，剩下的就是实体自己声明必须随身份保留的数据（原版热带鱼的完整 Variant、
 *       第三方鱼可能附带的花色或重量），再交给后代的 {@code loadFromBucketTag}。</li>
 *   <li><b>{@link VariantHolder}：</b>Mojang 的公共 Variant 读写接口，覆盖不是 {@code Bucketable}
 *       但实现了该接口的实体。</li>
 * </ol>
 *
 * <p>两条通道都用不上时后代保持 {@code EntityType.create} 的默认个体，这正是需求 §12.1 的规定。
 * 本类<b>不</b>使用反射、ASM、Mixin 或私有字段复制，也不要求第三方 Mod 实现任何 FBM 接口：
 * {@code Bucketable} 与 {@code VariantHolder} 都是 Minecraft 自己的公共类型，第三方只要正常继承原版鱼类
 * 或自行实现这两个接口即可自动获得继承能力（需求 §43、§47）。更复杂的私有 Variant 留给未来的
 * {@code BreedingAdapter}（需求 §48）。
 */
public final class VariantInheritance {
    /**
     * 由 {@link Bucketable#saveDefaultDataToBucketTag} 统一写入的通用字段。
     *
     * <p>这些键属于"这一条个体现在怎么样"，不属于"这是哪一种个体"，因此必须在继承前剔除，
     * 否则新生幼体会直接继承父母当前的血量或 AI 开关。
     */
    private static final Set<String> BUCKET_DEFAULT_KEYS = Set.of(
            "NoAI", "Silent", "NoGravity", "Glowing", "Invulnerable", "Health");

    private final Function<Bucketable, CompoundTag> bucketTagReader;

    /** 创建使用真实 {@link Bucketable} 桶标签的继承器。 */
    public VariantInheritance() {
        this(VariantInheritance::readBucketTag);
    }

    /**
     * 创建可注入桶标签读取边界的继承器，供同包测试在不构造真实 {@link ItemStack} 的情况下验证过滤与写入顺序。
     *
     * @param bucketTagReader 从捐赠方读取桶标签副本的函数
     */
    VariantInheritance(Function<Bucketable, CompoundTag> bucketTagReader) {
        this.bucketTagReader = bucketTagReader;
    }

    /**
     * 随机选出一方父母并尽量把其 Variant 复制到后代。
     *
     * <p>方法必须在后代加入世界之前调用，使 Variant 随首次实体生成包一起下发到客户端。任何失败都只影响
     * 外观继承，不会阻止后代出生。
     *
     * @param child 刚由 {@code EntityType.create} 创建、尚未加入世界的后代
     * @param firstParent 第一亲本
     * @param secondParent 第二亲本
     * @param random 用于 50/50 选择捐赠方的随机源
     * @return 实际生效的继承通道，或表示保持默认个体的结果
     */
    public VariantInheritanceResult inherit(Entity child, Entity firstParent,
                                            Entity secondParent, RandomSource random) {
        // 未确认同类型时不做任何转换：下面的 VariantHolder 写入依赖"同类必然同 Variant 类型"这一前提。
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
     * 通过桶数据契约复制捐赠方的身份数据。
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
     * 原地剔除桶标签中的通用个体状态字段，只保留实体自有的身份数据。
     *
     * <p>保持包级可见以便单独验证过滤集合，不需要构造实体或物品栈。
     *
     * @param bucketTag 捐赠方桶标签的可修改副本
     */
    static void stripDefaultData(CompoundTag bucketTag) {
        BUCKET_DEFAULT_KEYS.forEach(bucketTag::remove);
    }

    /**
     * 通过 Mojang 公共 {@link VariantHolder} 接口复制 Variant。
     *
     * <p>调用方已确认双方 {@code EntityType} 相同，因此两侧的 {@code VariantHolder} 类型参数必然一致，
     * 这里的非受检转换在运行时是安全的。
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
     * 用捐赠方自己的桶物品读取其桶标签副本；该过程只修改临时物品栈，不改变实体状态。
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
