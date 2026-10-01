package com.fishbreedingmanager.breeding.spawn;

/**
 * 后代变种继承尝试的诊断结果，不决定后代是否加入世界
 *
 * <p>没有公共继承通道时后代保持默认个体；回调失败仅表示继承流程未完整结束，不保证撤销回调已经写入的字段
 */
public enum VariantInheritanceResult {
    /** 通过 Minecraft {@code Bucketable} 桶数据契约复制了剔除通用字段后的捐赠方桶数据 */
    BUCKET_DATA,
    /** 通过 Minecraft 公共 {@code VariantHolder} 接口复制了捐赠方的 Variant */
    VARIANT_HOLDER,
    /** 实体未通过任何公共契约暴露可继承的 Variant，后代为默认个体 */
    DEFAULT_INDIVIDUAL,
    /** 读取或写入变种时回调抛出异常，已经发生的第三方写入不保证回滚 */
    FAILED
}
