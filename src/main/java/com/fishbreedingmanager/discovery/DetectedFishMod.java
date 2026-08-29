package com.fishbreedingmanager.discovery;

import java.util.Objects;

import net.minecraft.network.chat.Component;

/**
 * EntityType 来源 Mod/Namespace 的稳定分组统计。
 *
 * @param modId Mod ID 或回退 Namespace
 * @param displayName Mod 显示名或回退 Namespace
 * @param version ModList 报告的版本；回退来源为空字符串
 * @param registeredEntityCount 该来源注册的唯一 EntityType 数量
 * @param fishCandidateCount HIGH 与 MEDIUM 候选数量
 */
public record DetectedFishMod(
        String modId,
        Component displayName,
        String version,
        int registeredEntityCount,
        int fishCandidateCount
) {
    /** 校验统计边界并防御性复制显示组件树。 */
    public DetectedFishMod {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(version, "version");
        displayName = ComponentCopies.deepCopy(displayName);
        if (registeredEntityCount < 0 || fishCandidateCount < 0
                || fishCandidateCount > registeredEntityCount) {
            throw new IllegalArgumentException("invalid entity counts");
        }
    }

    /**
     * 返回来源显示组件。
     *
     * @return 与内部状态隔离的显示组件副本
     */
    @Override
    public Component displayName() {
        return ComponentCopies.deepCopy(displayName);
    }
}
