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
 * 单个 {@link net.minecraft.world.entity.EntityType EntityType} 的繁殖规则, 以其注册表 id 为主键 
 * 需求§17, 稳定主键是 ResourceLocation / 注册表 id, 绝非类名 显示名或 UUID 
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
    public BreedingRule {
        breedingItemIds = List.copyOf(breedingItemIds);
        breedingTagIds = List.copyOf(breedingTagIds);
    }

    /** 给定手持物品是否触发本规则的繁殖 */
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
