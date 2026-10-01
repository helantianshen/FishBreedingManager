package com.fishbreedingmanager.breeding;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 单个 {@link net.minecraft.world.entity.EntityType EntityType} 的不可变繁殖规则，以实体注册表 ID 为稳定主键
 *
 * <p>食物由物品 ID 与物品标签 ID 两组列表表示，任一条目匹配即可喂食；不表达物品组件或 NBT 条件
 * 标签通过 {@link ItemStack#is(TagKey)} 动态查询，规则保存于世界 SavedData，行为入口读取当前快照
 * 实体附件不保存规则副本，避免热更新后继续使用旧配置
 *
 * @param entityTypeId           规则所管实体的注册表 id
 * @param breedingItemIds        喂食触发繁殖的物品列表
 * @param breedingTagIds         喂食触发繁殖的物品 Tag 列表
 * @param breedingCooldownTicks  繁殖成功后父母的冷却 tick
 * @param growthTimeTicks        新生幼体成年所需 tick
 * @param enabled                规则是否启用
 */
public record BreedingRule(
        ResourceLocation entityTypeId,
        List<ResourceLocation> breedingItemIds,
        List<ResourceLocation> breedingTagIds,
        int breedingCooldownTicks,
        int growthTimeTicks,
        boolean enabled
) {
    /**
     * 创建规则并复制食物列表，防止规则发布到不可变快照后被外部修改
     */
    public BreedingRule {
        breedingItemIds = List.copyOf(breedingItemIds);
        breedingTagIds = List.copyOf(breedingTagIds);
    }

    /**
     * 判断给定物品栈是否匹配本规则配置的任一物品 ID 或物品标签
     *
     * <p>未知物品 ID 会被安全忽略；正常情况下它们已由 {@link RuleValidator} 在提交前拒绝；标签在每次交互时动态
     * 查询当前注册表内容，因此数据包标签重载可立即反映到匹配结果
     *
     * @param stack 玩家当前用于交互的物品栈
     * @return 匹配任一食物来源时返回 {@code true}
     */
    public boolean testFood(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (ResourceLocation id : breedingItemIds) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
            if (item.isPresent() && stack.is(item.get())) {
                return true;
            }
        }
        for (ResourceLocation id : breedingTagIds) {
            if (stack.is(TagKey.create(Registries.ITEM, id))) {
                return true;
            }
        }
        return false;
    }
}
