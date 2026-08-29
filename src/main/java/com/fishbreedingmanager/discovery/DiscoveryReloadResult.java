package com.fishbreedingmanager.discovery;

import java.util.Objects;

/**
 * 一次发现快照重建的结构化结果。
 *
 * @param success 是否成功发布新快照
 * @param candidateCount 成功快照中的完整候选数
 * @param error 失败原因；成功时为空字符串
 */
public record DiscoveryReloadResult(boolean success, int candidateCount, String error) {
    /** 校验结果字段。 */
    public DiscoveryReloadResult {
        Objects.requireNonNull(error, "error");
        if (candidateCount < 0) {
            throw new IllegalArgumentException("candidateCount must be non-negative");
        }
    }

    /**
     * 创建成功结果。
     *
     * @param candidateCount 已发布候选数
     * @return 成功结果
     */
    public static DiscoveryReloadResult success(int candidateCount) {
        return new DiscoveryReloadResult(true, candidateCount, "");
    }

    /**
     * 创建失败结果。
     *
     * @param error 失败原因
     * @return 失败结果
     */
    public static DiscoveryReloadResult failure(String error) {
        return new DiscoveryReloadResult(false, 0, error);
    }
}
