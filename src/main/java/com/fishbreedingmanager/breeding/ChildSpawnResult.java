package com.fishbreedingmanager.breeding;

import java.util.Objects;

import net.minecraft.world.entity.Entity;

/**
 * 后代生成尝试的不可变结构化结果。
 *
 * <p>{@link ChildSpawnStatus#SUCCESS} 必须携带已经加入世界的后代实体，任何失败状态都不能携带实体，构造器会维护
 * 这一不变量。调用方应通过 {@link #successful()} 决定是否提交父母冷却和清除 Love。
 *
 * @param status 生成尝试终止状态
 * @param child 成功时的后代实体；失败时为 {@code null}
 */
public record ChildSpawnResult(ChildSpawnStatus status, Entity child) {
    /**
     * 验证状态与后代实体是否一致。
     */
    public ChildSpawnResult {
        Objects.requireNonNull(status, "status");
        if (status == ChildSpawnStatus.SUCCESS && child == null) {
            throw new IllegalArgumentException("成功结果必须包含后代实体");
        }
        if (status != ChildSpawnStatus.SUCCESS && child != null) {
            throw new IllegalArgumentException("失败结果不能包含后代实体");
        }
    }

    /**
     * 创建包含已加入世界后代的成功结果。
     *
     * @param child 已成功加入世界的后代实体
     * @return 成功结果
     */
    public static ChildSpawnResult success(Entity child) {
        return new ChildSpawnResult(ChildSpawnStatus.SUCCESS,
                Objects.requireNonNull(child, "child"));
    }

    /**
     * 创建不包含后代实体的失败结果。
     *
     * @param status 具体失败状态，不能是 {@link ChildSpawnStatus#SUCCESS}
     * @return 失败结果
     */
    public static ChildSpawnResult failure(ChildSpawnStatus status) {
        if (status == ChildSpawnStatus.SUCCESS) {
            throw new IllegalArgumentException("失败结果不能使用 SUCCESS 状态");
        }
        return new ChildSpawnResult(status, null);
    }

    /**
     * 判断后代是否已经成功加入世界。
     *
     * @return 仅成功状态返回 {@code true}
     */
    public boolean successful() {
        return status == ChildSpawnStatus.SUCCESS;
    }
}
