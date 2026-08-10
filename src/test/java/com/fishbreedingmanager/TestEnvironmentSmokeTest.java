package com.fishbreedingmanager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 ModDevGradle 单元测试环境已经加载 Minecraft 与 NeoForge 注册表。
 */
final class TestEnvironmentSmokeTest {
    /**
     * 确认测试进程能够解析原版鳕鱼实体类型。
     */
    @Test
    void 应加载原版实体注册表() {
        assertEquals(ResourceLocation.parse("minecraft:cod"),
                BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.COD));
    }
}
