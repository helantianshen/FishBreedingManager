package com.fishbreedingmanager.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

/**
 * 验证无效注册表 ID、空食物来源和非法计时参数不能进入运行时规则快照。
 */
class RuleValidatorTest {
    private final RuleValidator validator = new RuleValidator();

    /**
     * 已注册的原版鳕鱼与海带应构成可用规则。
     */
    @Test
    void acceptsRegisteredCodAndKelpRule() {
        BreedingRule rule = new BreedingRule(
                ResourceLocation.parse("minecraft:cod"),
                List.of(ResourceLocation.parse("minecraft:kelp")),
                List.of(), 600, 1200, true);

        assertTrue(validator.validate(rule).valid());
    }

    /**
     * 校验器必须累积未知实体、未知物品和两个负数计时错误，不能在第一个错误处提前返回。
     */
    @Test
    void rejectsUnknownIdsAndNegativeDurationsTogether() {
        BreedingRule rule = new BreedingRule(
                ResourceLocation.parse("fishbreedingmanager:missing_entity"),
                List.of(ResourceLocation.parse("fishbreedingmanager:missing_item")),
                List.of(), -1, -1, true);

        RuleValidationResult result = validator.validate(rule);

        assertFalse(result.valid());
        assertEquals(4, result.errors().size());
    }

    /**
     * 规则必须至少提供一个物品 ID 或物品标签 ID，避免创建永远无法触发的启用规则。
     */
    @Test
    void rejectsRuleWithoutAnyFoodSource() {
        BreedingRule rule = new BreedingRule(
                ResourceLocation.parse("minecraft:cod"),
                List.of(), List.of(), 600, 1200, true);

        RuleValidationResult result = validator.validate(rule);

        assertFalse(result.valid());
        assertTrue(result.errors().contains("至少需要一个繁殖物品或物品标签"));
    }

    /**
     * 批量校验应为每条错误添加对应实体 ID 前缀，便于命令与日志定位具体规则。
     */
    @Test
    void validateAllPrefixesErrorsWithEntityId() {
        ResourceLocation entityId = ResourceLocation.parse("fishbreedingmanager:missing_entity");
        BreedingRule rule = new BreedingRule(
                entityId,
                List.of(ResourceLocation.parse("minecraft:kelp")),
                List.of(), 600, 1200, true);

        RuleValidationResult result = validator.validateAll(List.of(rule));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().allMatch(error -> error.startsWith(entityId + ": ")));
    }
}
