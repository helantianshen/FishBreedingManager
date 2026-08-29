package com.fishbreedingmanager.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * 验证发现快照不会暴露可变集合，避免扫描中间状态泄露给未来命令或 GUI。
 */
class DiscoverySnapshotTest {
    @Test
    void copiesAllCollectionsAndCandidateReasons() {
        ResourceLocation cod = ResourceLocation.parse("minecraft:cod");
        MutableComponent displayName = Component.literal("Cod");
        MutableComponent sourceName = Component.literal("Minecraft");
        MutableComponent groupedSourceName = Component.literal("Minecraft");
        Set<CandidateReason> reasons = new LinkedHashSet<>(Set.of(CandidateReason.VANILLA_FISH));
        CandidateEntity candidate = new CandidateEntity(cod, displayName,
                "minecraft", sourceName, CandidateConfidence.HIGH,
                CompatibilityLevel.UNVERIFIED, reasons);
        Map<ResourceLocation, CandidateEntity> candidates = new LinkedHashMap<>(Map.of(cod, candidate));
        Map<String, DetectedFishMod> detectedMods = new LinkedHashMap<>(Map.of("minecraft",
                new DetectedFishMod("minecraft", groupedSourceName, "1.21.1", 1, 1)));
        Set<ResourceLocation> unavailableImports = new LinkedHashSet<>(Set.of(
                ResourceLocation.parse("removed:old_fish")));
        DiscoverySnapshot snapshot = new DiscoverySnapshot(detectedMods, candidates, unavailableImports);

        reasons.add(CandidateReason.AQUATIC_TAG);
        displayName.append(" changed");
        sourceName.append(" changed");
        groupedSourceName.append(" changed");
        candidates.clear();
        unavailableImports.clear();

        assertEquals(Set.of(CandidateReason.VANILLA_FISH),
                snapshot.candidates().get(cod).reasons());
        assertEquals(Set.of(ResourceLocation.parse("removed:old_fish")),
                snapshot.unavailableImports());
        assertEquals("Cod", snapshot.candidates().get(cod).displayName().getString());
        assertEquals("Minecraft", snapshot.candidates().get(cod).sourceModName().getString());
        assertEquals("Minecraft", snapshot.detectedMods().get("minecraft").displayName().getString());

        ((MutableComponent) snapshot.candidates().get(cod).displayName()).append(" exposed");
        ((MutableComponent) snapshot.detectedMods().get("minecraft").displayName()).append(" exposed");

        assertEquals("Cod", snapshot.candidates().get(cod).displayName().getString());
        assertEquals("Minecraft", snapshot.detectedMods().get("minecraft").displayName().getString());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.candidates().put(cod, candidate));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.candidates().get(cod).reasons().add(CandidateReason.AQUATIC_TAG));
    }

    @Test
    void deeplyCopiesNestedComponentSiblings() {
        ResourceLocation cod = ResourceLocation.parse("minecraft:cod");
        MutableComponent child = Component.literal(" child");
        MutableComponent displayName = Component.literal("Cod").append(child);
        CandidateEntity candidate = new CandidateEntity(cod, displayName, "minecraft",
                Component.literal("Minecraft"), CandidateConfidence.HIGH,
                CompatibilityLevel.UNVERIFIED, Set.of(CandidateReason.VANILLA_FISH));

        child.append(" mutated");
        MutableComponent exposed = (MutableComponent) candidate.displayName();
        ((MutableComponent) exposed.getSiblings().getFirst()).append(" exposed");

        assertEquals("Cod child", candidate.displayName().getString());
    }
}
