package com.fishbreedingmanager.discovery;

import java.util.Objects;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 自动发现输出中的单个实体候选；不持有第三方实体实例或繁殖规则。
 *
 * @param entityTypeId 稳定 EntityType Registry ID
 * @param displayName 可本地化的实体显示组件
 * @param sourceModId 来源 Mod ID 或回退 Namespace
 * @param sourceModName 来源 Mod 显示名或回退 Namespace
 * @param confidence 自动识别置信度
 * @param compatibility 真实运行兼容等级
 * @param reasons 形成置信度的全部可解释信号
 */
public record CandidateEntity(
        ResourceLocation entityTypeId,
        Component displayName,
        String sourceModId,
        Component sourceModName,
        CandidateConfidence confidence,
        CompatibilityLevel compatibility,
        Set<CandidateReason> reasons
) {
    /** 校验字段并防御性复制组件树与理由集合。 */
    public CandidateEntity {
        Objects.requireNonNull(entityTypeId, "entityTypeId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(sourceModId, "sourceModId");
        Objects.requireNonNull(sourceModName, "sourceModName");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(compatibility, "compatibility");
        displayName = ComponentCopies.deepCopy(displayName);
        sourceModName = ComponentCopies.deepCopy(sourceModName);
        reasons = Set.copyOf(reasons);
    }

    /**
     * 返回实体显示组件。
     *
     * @return 与内部状态隔离的实体显示组件副本
     */
    @Override
    public Component displayName() {
        return ComponentCopies.deepCopy(displayName);
    }

    /**
     * 返回来源 Mod 显示组件。
     *
     * @return 与内部状态隔离的来源显示组件副本
     */
    @Override
    public Component sourceModName() {
        return ComponentCopies.deepCopy(sourceModName);
    }
}
