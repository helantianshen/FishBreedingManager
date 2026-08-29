package com.fishbreedingmanager.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** 验证真实运行日志所需的来源摘要稳定、通用且不会把 LOW 候选误报为可适配鱼类。 */
class DiscoveryDiagnosticsTest {
    @Test
    void summarizesStrongCandidatesBySourceAndExcludesLowOnlySources() {
        Map<String, DetectedFishMod> mods = new LinkedHashMap<>();
        mods.put("unknown", new DetectedFishMod("unknown", Component.literal("Unknown"), "1", 1, 0));
        mods.put("zoology", new DetectedFishMod("zoology", Component.literal("Zoology"), "3", 1, 1));
        mods.put("aquaculture", new DetectedFishMod("aquaculture",
                Component.literal("Aquaculture 2"), "2.7.21", 4, 2));

        Map<ResourceLocation, CandidateEntity> candidates = new LinkedHashMap<>();
        candidates.put(ResourceLocation.parse("zoology:river_fish"),
                candidate("zoology:river_fish", "zoology", CandidateConfidence.HIGH));
        candidates.put(ResourceLocation.parse("aquaculture:jellyfish"),
                candidate("aquaculture:jellyfish", "aquaculture", CandidateConfidence.LOW));
        candidates.put(ResourceLocation.parse("unknown:thing"),
                candidate("unknown:thing", "unknown", CandidateConfidence.LOW));
        candidates.put(ResourceLocation.parse("aquaculture:river_fish"),
                candidate("aquaculture:river_fish", "aquaculture", CandidateConfidence.MEDIUM));
        candidates.put(ResourceLocation.parse("aquaculture:bayad"),
                candidate("aquaculture:bayad", "aquaculture", CandidateConfidence.HIGH));

        DiscoverySnapshot snapshot = new DiscoverySnapshot(mods, candidates, Set.of());

        assertEquals(List.of(
                        "source=aquaculture, version=2.7.21, registered=4, high=1, "
                                + "medium=1, candidateIds=[aquaculture:bayad, aquaculture:river_fish]",
                        "source=zoology, version=3, registered=1, high=1, medium=0, "
                                + "candidateIds=[zoology:river_fish]"),
                DiscoveryDiagnostics.sourceSummaries(snapshot));
    }

    @Test
    void returnsNoSummariesForEmptySnapshotOrUnavailableImportsOnly() {
        assertEquals(List.of(), DiscoveryDiagnostics.sourceSummaries(DiscoverySnapshot.EMPTY));
        assertEquals(List.of(), DiscoveryDiagnostics.sourceSummaries(new DiscoverySnapshot(
                Map.of(), Map.of(), Set.of(ResourceLocation.parse("removed:old_fish")))));
    }

    private static CandidateEntity candidate(String id, String source,
            CandidateConfidence confidence) {
        return new CandidateEntity(ResourceLocation.parse(id), Component.literal(id), source,
                Component.literal(source), confidence, CompatibilityLevel.UNVERIFIED, Set.of());
    }
}
