package com.fishbreedingmanager.breeding;

import java.util.List;

/**
 * 一次规则校验的不可变结构化结果。
 *
 * <p>失败结果会保留本轮发现的全部错误，而非只报告第一项，管理员可以一次修正整条规则或整个候选快照。
 *
 * @param valid 是否通过全部校验
 * @param errors 按校验顺序排列的中文错误信息；成功时为空列表
 */
public record RuleValidationResult(boolean valid, List<String> errors) {
    /**
     * 创建校验结果并复制错误列表，确保结果发布后不会被调用方修改。
     */
    public RuleValidationResult {
        errors = List.copyOf(errors);
    }

    /**
     * 创建不含错误的成功结果。
     *
     * @return 成功校验结果
     */
    public static RuleValidationResult success() {
        return new RuleValidationResult(true, List.of());
    }

    /**
     * 创建包含全部已发现错误的失败结果。
     *
     * @param errors 非空错误信息列表
     * @return 失败校验结果
     */
    public static RuleValidationResult failure(List<String> errors) {
        return new RuleValidationResult(false, errors);
    }
}
