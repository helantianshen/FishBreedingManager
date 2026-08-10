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
 * 单个 {@link net.minecraft.world.entity.EntityType EntityType} 的不可变繁殖规则，以实体注册表 ID 为稳定主键。
 *
 * <p>繁殖物品存为 item id 列表和 item-tag id 列表, Ingredient 式抽象, 支持多物品+Tag 需求§7 
 * 运行时用 {@link ItemStack#is(Item)} / {@link ItemStack#is(TagKey)} 匹配, 
 * 避免版本不确定的 {@code Ingredient} codec, 语义等价 
 *
 * <p>规则属世界 存于 {@code WorldBreedingData}, 绝不缓存到实体 
 * 每次行为都查当前运行时快照 需求§31/§34 
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
     * 创建规则并复制食物列表，防止规则发布到不可变快照后被外部修改。
     */
    public BreedingRule {
        breedingItemIds = List.copyOf(breedingItemIds);
        breedingTagIds = List.copyOf(breedingTagIds);
    }

    /**
     * 判断给定物品栈是否匹配本规则配置的任一物品 ID 或物品标签。
     *
     * <p>未知物品 ID 会被安全忽略；正常情况下它们已由 {@link RuleValidator} 在提交前拒绝。标签在每次交互时动态
     * 查询当前注册表内容，因此数据包标签重载可立即反映到匹配结果。
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
