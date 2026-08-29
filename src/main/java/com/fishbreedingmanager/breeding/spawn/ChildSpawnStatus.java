package com.fishbreedingmanager.breeding.spawn;

/**
 * 一次后代生成尝试的明确终止状态。
 *
 * <p>状态用于区分实体类型无法创建实例与实例无法加入世界，控制器可记录准确原因，同时对两种失败采用相同的
 * “不消耗父母繁殖机会”策略。
 */
public enum ChildSpawnStatus {
    /** 后代已经创建、初始化并成功加入服务端世界。 */
    SUCCESS,
    /** {@link net.minecraft.world.entity.EntityType#create} 未能创建后代实例。 */
    TYPE_CREATION_FAILED,
    /** 后代实例已经创建，但 {@link net.minecraft.server.level.ServerLevel#addFreshEntity} 拒绝加入。 */
    ADD_TO_LEVEL_FAILED
}
