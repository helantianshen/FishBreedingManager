package com.fishbreedingmanager.discovery;

/** 形成候选置信度的可解释且可独立计数的识别信号。 */
public enum CandidateReason {
    /** 原版四种鱼的稳定白名单。 */
    VANILLA_FISH,
    /** 命中 {@code c:fish} 或 {@code c:fishes}。 */
    COMMON_FISH_TAG,
    /** 命中 {@code minecraft:aquatic}。 */
    AQUATIC_TAG,
    /** Registry path 或翻译键实体部分命中鱼名词元。 */
    ENTITY_KEYWORD,
    /** Namespace、Mod ID 或 Mod 显示名命中鱼名词元。 */
    SOURCE_KEYWORD,
    /** EntityType 使用受支持的水生 MobCategory。 */
    WATER_CATEGORY,
    /** 管理员已在当前存档显式导入。 */
    IMPORTED
}
