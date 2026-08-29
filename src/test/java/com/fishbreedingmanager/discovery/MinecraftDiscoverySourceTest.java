package com.fishbreedingmanager.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** 验证 Minecraft/NeoForge 边界能只读捕获已注册实体描述。 */
class MinecraftDiscoverySourceTest {
    @Test
    void capturesVanillaCodFromCompleteRegistry() {
        MinecraftDiscoverySource source = new MinecraftDiscoverySource();

        List<EntityDiscoveryInput> inputs = source.captureEntities();
        EntityDiscoveryInput cod = inputs.stream()
                .filter(input -> input.entityTypeId().equals(ResourceLocation.parse("minecraft:cod")))
                .findFirst().orElseThrow();

        assertEquals("entity.minecraft.cod", cod.translationKey());
        assertTrue(cod.registryReasons().contains(CandidateReason.VANILLA_FISH));
        assertTrue(inputs.size() > 100, "必须扫描完整 Registry，而不是只枚举鱼类白名单");
    }

    @Test
    void treatsSingularAndPluralCommonFishTagsAsTheSameStrongSignal() {
        ResourceLocation thirdPartyFish = ResourceLocation.parse("example:bayad");

        Set<CandidateReason> singular = MinecraftDiscoverySource.registryReasons(
                thirdPartyFish, true, false, false);
        Set<CandidateReason> plural = MinecraftDiscoverySource.registryReasons(
                thirdPartyFish, false, true, false);

        assertEquals(Set.of(CandidateReason.COMMON_FISH_TAG), singular);
        assertEquals(singular, plural);
    }
}
