package com.fishbreedingmanager.discovery;

import java.util.Objects;

/**
 * 从 NeoForge ModList 提取并与加载器类型解耦的来源元数据。
 *
 * @param modId 稳定 Mod ID
 * @param displayName Mod 显示名
 * @param version 加载版本文本
 */
public record LoadedModInfo(String modId, String displayName, String version) {
    /** 校验所有元数据字段非空。 */
    public LoadedModInfo {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(version, "version");
    }
}
