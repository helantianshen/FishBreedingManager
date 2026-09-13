package com.fishbreedingmanager.breeding.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * 验证后代 Variant 继承只走 Minecraft 公共契约，并在任何不确定情况下退回默认个体（需求 §12、§12.1）。
 */
class VariantInheritanceTest {
    /** 捐赠方选择必须是父母双方各 50%，由随机源的布尔值直接决定。 */
    @Test
    void 按随机布尔值在两个亲本之间选择捐赠方() {
        TropicalFish child = tropicalFish();
        TropicalFish first = tropicalFish();
        TropicalFish second = tropicalFish();
        VariantInheritance inheritance = new VariantInheritance(
                source -> bucketTag(source == first ? 1 : 2));

        assertEquals(VariantInheritanceResult.BUCKET_DATA,
                inheritance.inherit(child, first, second, randomReturning(true)));
        assertEquals(VariantInheritanceResult.BUCKET_DATA,
                inheritance.inherit(child, first, second, randomReturning(false)));

        ArgumentCaptor<CompoundTag> captor = ArgumentCaptor.forClass(CompoundTag.class);
        verify(child, times(2)).loadFromBucketTag(captor.capture());
        assertEquals(1, captor.getAllValues().get(0).getInt("BucketVariantTag"));
        assertEquals(2, captor.getAllValues().get(1).getInt("BucketVariantTag"));
    }

    /** 只含通用个体字段的桶标签不构成 Variant，不得写入后代。 */
    @Test
    void 忽略只包含通用个体字段的桶标签() {
        TropicalFish child = tropicalFish();
        TropicalFish donor = tropicalFish();

        VariantInheritanceResult result = new VariantInheritance(source -> {
            CompoundTag tag = new CompoundTag();
            tag.putFloat("Health", 1.0F);
            tag.putBoolean("NoAI", true);
            tag.putBoolean("Invulnerable", true);
            return tag;
        }).inherit(child, donor, donor, randomReturning(true));

        assertEquals(VariantInheritanceResult.DEFAULT_INDIVIDUAL, result);
        verify(child, never()).loadFromBucketTag(any());
    }

    /** 通用字段集合必须与 {@code Bucketable.saveDefaultDataToBucketTag} 写入的键完全一致。 */
    @Test
    void 剔除全部通用个体字段并保留自有数据() {
        CompoundTag tag = new CompoundTag();
        for (String key : new String[] {"NoAI", "Silent", "NoGravity", "Glowing", "Invulnerable"}) {
            tag.putBoolean(key, true);
        }
        tag.putFloat("Health", 2.0F);
        tag.putInt("BucketVariantTag", 99);
        tag.putFloat("SomeModWeight", 4.5F);

        VariantInheritance.stripDefaultData(tag);

        assertEquals(2, tag.size());
        assertEquals(99, tag.getInt("BucketVariantTag"));
        assertTrue(tag.contains("SomeModWeight"));
    }

    /**
     * 不是 Bucketable 的实体应退回 Mojang 公共 {@link VariantHolder} 接口。
     *
     * <p>这里用原版兔子验证通道本身与鱼类无关：任何实现了该公共接口的实体都能自动获得继承能力。
     */
    @Test
    void 非桶装实体退回VariantHolder接口() {
        Rabbit child = mock(Rabbit.class);
        Rabbit donor = mock(Rabbit.class);
        doReturn(EntityType.RABBIT).when(child).getType();
        doReturn(EntityType.RABBIT).when(donor).getType();
        when(donor.getVariant()).thenReturn(Rabbit.Variant.BLACK);

        VariantInheritanceResult result = new VariantInheritance(source -> new CompoundTag())
                .inherit(child, donor, donor, randomReturning(true));

        assertEquals(VariantInheritanceResult.VARIANT_HOLDER, result);
        verify(child).setVariant(Rabbit.Variant.BLACK);
    }

    /** 既不是 Bucketable 也不是 VariantHolder 的实体保持默认个体。 */
    @Test
    void 无公共契约的实体保持默认个体() {
        Entity child = mock(Entity.class);
        Entity donor = mock(Entity.class);
        doReturn(EntityType.COD).when(child).getType();
        doReturn(EntityType.COD).when(donor).getType();

        assertEquals(VariantInheritanceResult.DEFAULT_INDIVIDUAL,
                new VariantInheritance().inherit(child, donor, donor, randomReturning(true)));
    }

    /** 类型不一致时绝不写入，避免非受检的 Variant 类型转换在运行时失败。 */
    @Test
    void 类型不一致时不做任何继承() {
        TropicalFish child = tropicalFish();
        Cod donor = mock(Cod.class);
        doReturn(EntityType.COD).when(donor).getType();

        assertEquals(VariantInheritanceResult.DEFAULT_INDIVIDUAL,
                new VariantInheritance().inherit(child, donor, donor, randomReturning(true)));
        verify(child, never()).loadFromBucketTag(any());
    }

    /** 第三方实现抛出异常时只记录并保持默认个体，不影响后代出生。 */
    @Test
    void 第三方异常被隔离为默认个体() {
        TropicalFish child = tropicalFish();
        TropicalFish donor = tropicalFish();

        VariantInheritanceResult result = new VariantInheritance(source -> {
            throw new IllegalStateException("broken third-party bucket data");
        }).inherit(child, donor, donor, randomReturning(true));

        assertEquals(VariantInheritanceResult.FAILED, result);
        verify(child, never()).loadFromBucketTag(any());
    }

    /**
     * 生产读取器必须真正走 {@code getBucketItemStack} 到 {@code BUCKET_ENTITY_DATA} 组件的公共路径，
     * 并在写入后代之前剔除通用字段。
     */
    @Test
    void 生产桶标签读取器走真实组件路径() {
        TropicalFish child = tropicalFish();
        TropicalFish donor = tropicalFish();
        when(donor.getBucketItemStack()).thenReturn(new ItemStack(Items.TROPICAL_FISH_BUCKET));
        doAnswer(invocation -> {
            ItemStack stack = invocation.getArgument(0);
            CompoundTag tag = bucketTag(917504);
            tag.putFloat("Health", 3.0F);
            stack.set(DataComponents.BUCKET_ENTITY_DATA, CustomData.of(tag));
            return null;
        }).when(donor).saveToBucketTag(any());

        VariantInheritanceResult result = new VariantInheritance()
                .inherit(child, donor, donor, randomReturning(true));

        assertEquals(VariantInheritanceResult.BUCKET_DATA, result);
        ArgumentCaptor<CompoundTag> captor = ArgumentCaptor.forClass(CompoundTag.class);
        verify(child).loadFromBucketTag(captor.capture());
        assertEquals(917504, captor.getValue().getInt("BucketVariantTag"));
        assertFalse(captor.getValue().contains("Health"));
    }

    /** 捐赠方不提供桶物品时安全退化，不得调用写出方法。 */
    @Test
    void 捐赠方没有桶物品时安全退化() {
        TropicalFish child = tropicalFish();
        TropicalFish donor = tropicalFish();
        when(donor.getBucketItemStack()).thenReturn(ItemStack.EMPTY);

        assertEquals(VariantInheritanceResult.DEFAULT_INDIVIDUAL,
                new VariantInheritance().inherit(child, donor, donor, randomReturning(true)));
        verify(donor, never()).saveToBucketTag(any());
    }

    private static CompoundTag bucketTag(int packedVariant) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("BucketVariantTag", packedVariant);
        return tag;
    }

    private static TropicalFish tropicalFish() {
        TropicalFish fish = mock(TropicalFish.class);
        doReturn(EntityType.TROPICAL_FISH).when(fish).getType();
        return fish;
    }

    private static RandomSource randomReturning(boolean value) {
        RandomSource random = mock(RandomSource.class);
        when(random.nextBoolean()).thenReturn(value);
        return random;
    }
}
