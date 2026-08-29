package com.fishbreedingmanager.discovery;

/** 实体经过实际运行验证后可记录的通用繁殖兼容等级。 */
public enum CompatibilityLevel {
    /** 尚未经过真实实体运行验收。 */
    UNVERIFIED,
    /** 通用繁殖、导航和显示路径完整可用。 */
    FULL,
    /** 核心繁殖可用，但存在已记录的安全降级。 */
    PARTIAL,
    /** 无法通过公共契约安全支持。 */
    UNSUPPORTED
}
