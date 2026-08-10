package com.fishbreedingmanager.breeding;

import java.util.List;

/**
 * 事务式规则修改的不可变结果。
 *
 * <p>成功结果报告提交后的规则总数；失败结果携带完整校验或查找错误，并保证持久化数据与运行时快照均未改变。
 *
 * @param success 候选规则集合是否已经提交
 * @param ruleCount 成功提交后的规则总数；失败时为 {@code 0}
 * @param errors 失败原因列表；成功时为空列表
 */
public record RuleUpdateResult(boolean success, int ruleCount, List<String> errors) {
    /**
     * 创建结果并复制错误列表，防止发布后被外部修改。
     */
    public RuleUpdateResult {
        errors = List.copyOf(errors);
    }

    /**
     * 创建成功提交结果。
     *
     * @param ruleCount 提交后的规则总数
     * @return 成功结果
     */
    public static RuleUpdateResult success(int ruleCount) {
        return new RuleUpdateResult(true, ruleCount, List.of());
    }

    /**
     * 创建未提交的失败结果。
     *
     * @param errors 阻止提交的全部错误
     * @return 失败结果
     */
    public static RuleUpdateResult failure(List<String> errors) {
        return new RuleUpdateResult(false, 0, errors);
    }
}
