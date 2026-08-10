package com.fishbreedingmanager.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fishbreedingmanager.persistence.WorldBreedingData;

import net.minecraft.resources.ResourceLocation;

/**
 * 验证新存档默认种入四种原版鱼规则并全部启用。
 */
class DefaultRulesTest {
    /**
     * 默认集合必须精确包含鳕鱼、鲑鱼、热带鱼和河豚，避免模板或后续重构悄悄改变新世界行为。
     */
    @Test
    void seedsExactlyFourEnabledVanillaFishRules() {
        WorldBreedingData data = new WorldBreedingData();

        DefaultRules.seedInto(data);

        Set<ResourceLocation> ids = data.allRules().stream()
                .map(BreedingRule::entityTypeId)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                ResourceLocation.parse("minecraft:cod"),
                ResourceLocation.parse("minecraft:salmon"),
                ResourceLocation.parse("minecraft:tropical_fish"),
                ResourceLocation.parse("minecraft:pufferfish")), ids);
        assertTrue(data.allRules().stream().allMatch(BreedingRule::enabled));
    }
}
