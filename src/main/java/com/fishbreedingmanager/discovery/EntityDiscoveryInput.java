package com.fishbreedingmanager.discovery;

import java.util.Objects;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;

/**
 * 从 Minecraft Registry 捕获后交给纯发现引擎处理的实体描述。
 *
 * @param entityTypeId 稳定 EntityType Registry ID
 * @param displayName 实体显示组件
 * @param translationKey 实体翻译键
 * @param category EntityType 的 MobCategory
 * @param registryReasons Registry 与 Tag 边界直接产生的信号
 */
public record EntityDiscoveryInput(
        ResourceLocation entityTypeId,
        Component displayName,
        String translationKey,
        MobCategory category,
        Set<CandidateReason> registryReasons
) {
    /** 校验字段并复制 Registry 信号集合。 */
    public EntityDiscoveryInput {
        Objects.requireNonNull(entityTypeId, "entityTypeId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(translationKey, "translationKey");
        Objects.requireNonNull(category, "category");
        registryReasons = Set.copyOf(registryReasons);
    }
}
