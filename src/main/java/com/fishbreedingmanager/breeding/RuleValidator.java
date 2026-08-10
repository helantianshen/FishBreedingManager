package com.fishbreedingmanager.breeding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * 在规则进入世界存档和运行时快照前执行完整校验。
 *
 * <p>校验器直接查询当前 Minecraft 注册表，拒绝未知实体、未知物品、未知或空标签以及负数计时参数。
 * 每次校验都会累积全部错误，便于管理员通过一条命令反馈完成修正。
 */
public final class RuleValidator {
    /**
     * 校验一条繁殖规则的注册表引用、食物来源和计时参数。
     *
     * @param rule 待校验的候选规则
     * @return 包含全部错误的结构化结果；无错误时返回成功结果
     */
    public RuleValidationResult validate(BreedingRule rule) {
        List<String> errors = new ArrayList<>();

        if (BuiltInRegistries.ENTITY_TYPE.getOptional(rule.entityTypeId()).isEmpty()) {
            errors.add("未知实体 ID: " + rule.entityTypeId());
        }
        if (rule.breedingItemIds().isEmpty() && rule.breedingTagIds().isEmpty()) {
            errors.add("至少需要一个繁殖物品或物品标签");
        }
        for (ResourceLocation itemId : rule.breedingItemIds()) {
            if (BuiltInRegistries.ITEM.getOptional(itemId).isEmpty()) {
                errors.add("未知物品 ID: " + itemId);
            }
        }
        for (ResourceLocation tagId : rule.breedingTagIds()) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
            if (BuiltInRegistries.ITEM.getTag(tag).isEmpty()) {
                errors.add("未知或空的物品标签: #" + tagId);
            }
        }
        if (rule.breedingCooldownTicks() < 0) {
            errors.add("繁殖冷却不能为负数");
        }
        if (rule.growthTimeTicks() < 0) {
            errors.add("成长时间不能为负数");
        }

        return errors.isEmpty()
                ? RuleValidationResult.success()
                : RuleValidationResult.failure(errors);
    }

    /**
     * 校验完整候选规则集合，并为每条错误添加所属实体 ID 前缀。
     *
     * <p>该方法用于事务式保存与热重载：只有整个集合通过校验，调用方才可以替换当前有效快照。
     *
     * @param rules 待校验的候选规则集合
     * @return 聚合全部规则错误的结构化结果
     */
    public RuleValidationResult validateAll(Collection<BreedingRule> rules) {
        List<String> errors = new ArrayList<>();
        for (BreedingRule rule : rules) {
            RuleValidationResult result = validate(rule);
            for (String error : result.errors()) {
                errors.add(rule.entityTypeId() + ": " + error);
            }
        }
        return errors.isEmpty()
                ? RuleValidationResult.success()
                : RuleValidationResult.failure(errors);
    }
}
