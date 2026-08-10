package com.fishbreedingmanager.breeding;

/**
 * {@link WorldBreedingService#reload} 操作的不可变结果。
 *
 * <p>失败时保留旧运行时快照，配置错误不能破坏当前正常工作的规则。
 *
 * @param success 是否成功安装新快照
 * @param ruleCount 成功安装的规则数量；失败时为 {@code 0}
 * @param error 失败原因；成功时为 {@code null}
 */
public record ReloadResult(boolean success, int ruleCount, String error) {
    /**
     * 创建成功重载结果。
     *
     * @param ruleCount 已安装快照的规则数
     * @return 成功结果
     */
    public static ReloadResult success(int ruleCount) {
        return new ReloadResult(true, ruleCount, null);
    }

    /**
     * 创建失败重载结果。
     *
     * @param error 阻止快照安装的原因
     * @return 保留旧快照的失败结果
     */
    public static ReloadResult failure(String error) {
        return new ReloadResult(false, 0, error);
    }
}
