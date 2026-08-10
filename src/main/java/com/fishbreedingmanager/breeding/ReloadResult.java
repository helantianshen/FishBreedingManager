package com.fishbreedingmanager.breeding;

/**
 * {@link BreedingRuleManager#reload} 操作的结果, 失败时保留旧快照 
 * 需求§33, 配置错误不能破坏当前正常工作的规则 
 */
public record ReloadResult(boolean success, int ruleCount, String error) {
    public static ReloadResult success(int ruleCount) {
        return new ReloadResult(true, ruleCount, null);
    }

    public static ReloadResult failure(String error) {
        return new ReloadResult(false, 0, error);
    }
}
