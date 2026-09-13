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
    /**
     * 防御性复制两个集合，使不可变性由类型自身保证而非依赖调用方传入副本。
     *
     * <p>{@link BreedingRule} 本身已经是复制过食物列表的不可变 record，因此浅复制映射即可获得完整不可变快照。
     */
    public BreedingRuleSnapshot {
        rules = Map.copyOf(rules);
        importedEntities = Set.copyOf(importedEntities);
    }

    /** 尚未从世界数据成功加载时使用的空快照。 */
    public static final BreedingRuleSnapshot EMPTY =
            new BreedingRuleSnapshot(Map.of(), Set.of());
}
