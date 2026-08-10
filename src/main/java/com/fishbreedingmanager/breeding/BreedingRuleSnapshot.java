package com.fishbreedingmanager.breeding;

import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/**
 * 单个世界/存档全部繁殖规则的不可变运行时快照 需求§32 
 *
 * <p>由 {@link BreedingRuleManager} 以 {@code volatile} 引用持有, reload 时原子替换 
 * 需求§33, 解析/校验失败时保留旧快照 
 * 所有行为动态读取当前快照, 故热重载立即影响现存实体 需求§34 
 *
 * @param rules           实体 id -> 规则, O(1) 查找 
 * @param importedEntities 手动导入的实体 id, 跨会话持久化 需求§16 
 */
public record BreedingRuleSnapshot(
        Map<ResourceLocation, BreedingRule> rules,
        Set<ResourceLocation> importedEntities
) {
    public static final BreedingRuleSnapshot EMPTY =
            new BreedingRuleSnapshot(Map.of(), Set.of());
}
