package com.fishbreedingmanager.breeding;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;

/**
 * 按服务端 Level 隔离的活跃 Love 实体内存索引。
 *
 * <p>索引只保存当前已加载且处于 FBM Love 时间窗的实体 UUID，使繁殖控制器无需每次扫描世界全部实体。
 * {@link WeakHashMap} 允许已经卸载的 Level 被回收，但正常生命周期仍会主动调用 {@link #clear(ServerLevel)} 或
 * {@link #clearAll()}。本类不做并发同步，全部方法只允许在 Minecraft 服务端主线程调用。
 */
public final class ActiveLoveIndex {
    /** 生产环境共享索引；一个进程中的各服务器会话在停止事件中统一清空。 */
    public static final ActiveLoveIndex INSTANCE = new ActiveLoveIndex();

    private final Map<ServerLevel, Set<UUID>> byLevel = new WeakHashMap<>();

    /**
     * 创建空索引。保持包级可见以供同包单元测试创建隔离实例。
     */
    ActiveLoveIndex() {
    }

    /**
     * 将实体加入指定 Level 的活跃 Love 集合。
     *
     * <p>重复加入同一 UUID 不会产生重复项。仅允许服务端主线程调用。
     *
     * @param level 实体当前所在的服务端 Level
     * @param entityId 实体 UUID
     */
    public void add(ServerLevel level, UUID entityId) {
        byLevel.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(entityId);
    }

    /**
     * 从指定 Level 删除实体，并在集合变空时删除 Level 键。
     *
     * @param level 实体离开或状态结束前所在的服务端 Level
     * @param entityId 实体 UUID
     */
    public void remove(ServerLevel level, UUID entityId) {
        Set<UUID> ids = byLevel.get(level);
        if (ids == null) {
            return;
        }
        ids.remove(entityId);
        if (ids.isEmpty()) {
            byLevel.remove(level);
        }
    }

    /**
     * 获取指定 Level 当前活跃 UUID 的不可修改迭代快照。
     *
     * <p>控制器可以在遍历返回列表时安全地修改原索引，不会触发并发修改异常。
     *
     * @param level 待查询的服务端 Level
     * @return 保持加入顺序的不可修改 UUID 列表；无记录时返回空列表
     */
    public List<UUID> snapshot(ServerLevel level) {
        Set<UUID> ids = byLevel.get(level);
        return ids == null ? List.of() : List.copyOf(ids);
    }

    /**
     * 清除一个已卸载 Level 的全部活跃记录。
     *
     * @param level 已卸载的服务端 Level
     */
    public void clear(ServerLevel level) {
        byLevel.remove(level);
    }

    /**
     * 清除当前服务器会话的全部索引，服务端停止时必须调用。
     */
    public void clearAll() {
        byLevel.clear();
    }
}
