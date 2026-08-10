package com.fishbreedingmanager.breeding;

import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/**
 * 单个世界存档全部繁殖规则与导入实体集合的不可变运行时快照。
 *
 * <p>由 {@link BreedingRuleManager} 以 {@code volatile} 引用持有, reload 时原子替换 
 * 需求§33, 解析/校验失败时保留旧快照 
 * 所有行为动态读取当前快照, 故热重载立即影响现存实体 需求§34 
 *
 * @param rules 实体注册表 ID 到规则的不可变映射，供行为路径进行 O(1) 查询
 * @param importedEntities 手动导入且跨会话持久化的实体注册表 ID 集合
 */
public record BreedingRuleSnapshot(
        Map<ResourceLocation, BreedingRule> rules,
        Set<ResourceLocation> importedEntities
) {
    /** 尚未从世界数据成功加载时使用的空快照。 */
    public static final BreedingRuleSnapshot EMPTY =
            new BreedingRuleSnapshot(Map.of(), Set.of());
}
