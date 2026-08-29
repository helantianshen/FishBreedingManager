package com.fishbreedingmanager.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.Test;

/** 验证通用发现引擎的信号合并、置信度和来源分组语义。 */
class FishDiscoveryEngineTest {
    private final FishDiscoveryEngine engine = new FishDiscoveryEngine();

    @Test
    void classifiesStrongTwoIndependentWeakAndSingleWeakSignals() {
        EntityDiscoveryInput tagged = input("aquaculture:bayad", MobCategory.WATER_CREATURE,
                Set.of(CandidateReason.COMMON_FISH_TAG));
        EntityDiscoveryInput medium = input("example:river_fish", MobCategory.WATER_CREATURE,
                Set.of(CandidateReason.AQUATIC_TAG));
        EntityDiscoveryInput low = input("example:jellyfish", MobCategory.WATER_CREATURE,
                Set.of(CandidateReason.AQUATIC_TAG));

        DiscoverySnapshot snapshot = engine.discover(
                List.of(new LoadedModInfo("aquaculture", "Aquaculture 2", "2.7.21"),
                        new LoadedModInfo("example", "Example", "1")),
                List.of(tagged, medium, low), Set.of());

        assertEquals(CandidateConfidence.HIGH,
                snapshot.candidates().get(tagged.entityTypeId()).confidence());
        assertEquals(CandidateConfidence.MEDIUM,
                snapshot.candidates().get(medium.entityTypeId()).confidence());
        assertEquals(CandidateConfidence.LOW,
                snapshot.candidates().get(low.entityTypeId()).confidence());
        assertTrue(snapshot.candidates().get(medium.entityTypeId()).reasons()
                .containsAll(Set.of(CandidateReason.ENTITY_KEYWORD,
                        CandidateReason.AQUATIC_TAG, CandidateReason.WATER_CATEGORY)));
        assertEquals(Set.of(CandidateReason.AQUATIC_TAG, CandidateReason.WATER_CATEGORY),
                snapshot.candidates().get(low.entityTypeId()).reasons());
    }

    @Test
    void groupsByLoadedModOrNamespaceAndReportsUnavailableImports() {
        ResourceLocation bayad = ResourceLocation.parse("aquaculture:bayad");
        ResourceLocation unknown = ResourceLocation.parse("unknownmod:river_fish");
        ResourceLocation missing = ResourceLocation.parse("removed:old_fish");

        DiscoverySnapshot snapshot = engine.discover(
                List.of(new LoadedModInfo("aquaculture", "Aquaculture 2", "2.7.21")),
                List.of(input(unknown.toString(), MobCategory.WATER_CREATURE, Set.of()),
                        input(bayad.toString(), MobCategory.WATER_CREATURE,
                                Set.of(CandidateReason.COMMON_FISH_TAG))),
                Set.of(missing));

        assertEquals(List.of("aquaculture", "unknownmod"),
                snapshot.detectedMods().keySet().stream().toList());
        assertEquals("Aquaculture 2",
                snapshot.detectedMods().get("aquaculture").displayName().getString());
        assertEquals("2.7.21", snapshot.detectedMods().get("aquaculture").version());
        assertEquals("unknownmod",
                snapshot.detectedMods().get("unknownmod").displayName().getString());
        assertEquals(1, snapshot.detectedMods().get("aquaculture").registeredEntityCount());
        assertEquals(1, snapshot.detectedMods().get("aquaculture").fishCandidateCount());
        assertEquals(Set.of(missing), snapshot.unavailableImports());
    }

    @Test
    void mergesDuplicateRegistryInputsBeforeClassification() {
        EntityDiscoveryInput commonTag = input("example:river_fish", MobCategory.WATER_CREATURE,
                Set.of(CandidateReason.COMMON_FISH_TAG));
        EntityDiscoveryInput aquaticTag = input("example:river_fish", MobCategory.WATER_CREATURE,
                Set.of(CandidateReason.AQUATIC_TAG));

        DiscoverySnapshot snapshot = engine.discover(List.of(),
                List.of(commonTag, aquaticTag), Set.of());
        CandidateEntity merged = snapshot.candidates().get(commonTag.entityTypeId());

        assertEquals(1, snapshot.candidates().size());
        assertEquals(CandidateConfidence.HIGH, merged.confidence());
        assertTrue(merged.reasons().containsAll(Set.of(
                CandidateReason.COMMON_FISH_TAG, CandidateReason.AQUATIC_TAG)));
    }

    @Test
    void treatsUndergroundWaterCreatureAsAquaticNatureSignal() {
        EntityDiscoveryInput caveFish = input("example:cave_fish",
                MobCategory.UNDERGROUND_WATER_CREATURE, Set.of());

        CandidateEntity candidate = engine.discover(List.of(), List.of(caveFish), Set.of())
                .candidates().get(caveFish.entityTypeId());

        assertEquals(CandidateConfidence.MEDIUM, candidate.confidence());
        assertTrue(candidate.reasons().contains(CandidateReason.WATER_CATEGORY));
    }

    @Test
    void doesNotCountSourceNamespaceInsideTranslationKeyAsEntityKeyword() {
        EntityDiscoveryInput nonFish = input("fish:net", MobCategory.MISC, Set.of());

        CandidateEntity candidate = engine.discover(
                        List.of(new LoadedModInfo("fish", "Fish", "1")),
                        List.of(nonFish), Set.of())
                .candidates().get(nonFish.entityTypeId());

        assertEquals(CandidateConfidence.LOW, candidate.confidence());
        assertEquals(Set.of(CandidateReason.SOURCE_KEYWORD), candidate.reasons());
    }

    private static EntityDiscoveryInput input(String id, MobCategory category,
            Set<CandidateReason> reasons) {
        ResourceLocation key = ResourceLocation.parse(id);
        return new EntityDiscoveryInput(key, Component.literal(key.toString()),
                "entity." + key.getNamespace() + "." + key.getPath(), category, reasons);
    }
}
