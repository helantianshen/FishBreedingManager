package com.fishbreedingmanager.discovery;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/**
 * 最近一次成功完成的 Mod/EntityType 自动发现结果。
 *
 * @param detectedMods 按 Mod ID/Namespace 稳定排序的来源统计
 * @param candidates 按来源和实体 path 稳定排序的完整 Registry 候选
 * @param unavailableImports 当前 Registry 中已经不存在的持久化导入 ID
 */
public record DiscoverySnapshot(
        Map<String, DetectedFishMod> detectedMods,
        Map<ResourceLocation, CandidateEntity> candidates,
        Set<ResourceLocation> unavailableImports
) {
    /** 尚未执行或没有可见 Registry 项时使用的空快照。 */
    public static final DiscoverySnapshot EMPTY = new DiscoverySnapshot(Map.of(), Map.of(), Set.of());

    /** 防御性复制所有集合，保留输入映射的稳定顺序。 */
    public DiscoverySnapshot {
        detectedMods = Collections.unmodifiableMap(new LinkedHashMap<>(detectedMods));
        candidates = Collections.unmodifiableMap(new LinkedHashMap<>(candidates));
        unavailableImports = Set.copyOf(unavailableImports);
    }
}
