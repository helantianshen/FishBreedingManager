package com.fishbreedingmanager.breeding.spawn;

/**
 * 后代 Variant 继承尝试的结构化结果（需求 §12）。
 *
 * <p>结果只用于诊断与验收记录，不影响后代是否成功加入世界：除 {@link #BUCKET_DATA} 与
 * {@link #VARIANT_HOLDER} 以外的所有取值都表示后代保持 {@code EntityType.create} 的默认个体，
 * 这正是需求 §12.1 对无法通用识别的第三方 Variant 规定的行为。
 */
public enum VariantInheritanceResult {
    /** 通过 Minecraft {@code Bucketable} 桶数据契约完整复制了捐赠方的自定义数据。 */
    BUCKET_DATA,
    /** 通过 Minecraft 公共 {@code VariantHolder} 接口复制了捐赠方的 Variant。 */
    VARIANT_HOLDER,
    /** 实体未通过任何公共契约暴露可继承的 Variant，后代为默认个体。 */
    DEFAULT_INDIVIDUAL,
    /** 读取或写入捐赠方 Variant 时第三方实现抛出异常，后代保持默认个体。 */
    FAILED
}
