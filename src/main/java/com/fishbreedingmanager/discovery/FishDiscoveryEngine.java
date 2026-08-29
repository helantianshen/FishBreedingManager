package com.fishbreedingmanager.discovery;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;

/** 将加载器无关的 Registry 描述转换为带理由和置信度的候选快照。 */
public final class FishDiscoveryEngine {
    private static final Set<CandidateReason> STRONG_REASONS = Set.of(
            CandidateReason.VANILLA_FISH,
            CandidateReason.COMMON_FISH_TAG,
            CandidateReason.IMPORTED);
    private static final Set<String> FISH_KEYWORDS = Set.of(
            "fish", "cod", "salmon", "trout", "bass", "carp", "tuna", "perch",
            "minnow", "herring", "halibut", "catfish", "gar", "piranha", "arapaima");

    /** 创建无状态发现引擎。 */
    public FishDiscoveryEngine() {
    }

    /**
     * 扫描纯输入并生成完整候选集合；所有 Registry 项都会保留，低置信度项供后续高级搜索使用。
     *
     * @param loadedMods 当前 ModList 元数据
     * @param inputs 完整 EntityType Registry 描述
     * @param importedEntities 当前存档持久化导入集合
     * @return 不可变且稳定排序的完整发现快照
     */
    public DiscoverySnapshot discover(List<LoadedModInfo> loadedMods,
            List<EntityDiscoveryInput> inputs, Set<ResourceLocation> importedEntities) {
        Map<String, LoadedModInfo> modsById = new LinkedHashMap<>();
        loadedMods.stream().sorted(Comparator.comparing(LoadedModInfo::modId))
                .forEach(loadedMod -> modsById.putIfAbsent(loadedMod.modId(), loadedMod));

        Map<ResourceLocation, EntityDiscoveryInput> mergedInputs = new HashMap<>();
        for (EntityDiscoveryInput input : inputs) {
            mergedInputs.merge(input.entityTypeId(), input, FishDiscoveryEngine::mergeInput);
        }

        Map<ResourceLocation, CandidateEntity> candidates = new LinkedHashMap<>();
        mergedInputs.values().stream()
                .sorted(Comparator.comparing((EntityDiscoveryInput input) ->
                                input.entityTypeId().getNamespace())
                        .thenComparing(input -> input.entityTypeId().getPath()))
                .forEach(input -> {
                    String sourceModId = input.entityTypeId().getNamespace();
                    LoadedModInfo loadedMod = modsById.get(sourceModId);
                    String sourceName = loadedMod != null ? loadedMod.displayName() : sourceModId;

                    EnumSet<CandidateReason> reasons = input.registryReasons().isEmpty()
                            ? EnumSet.noneOf(CandidateReason.class)
                            : EnumSet.copyOf(input.registryReasons());
                    if (importedEntities.contains(input.entityTypeId())) {
                        reasons.add(CandidateReason.IMPORTED);
                    }
                    if (hasFishKeyword(input.entityTypeId().getPath())
                            || hasFishKeyword(entityPartOfTranslationKey(input))) {
                        reasons.add(CandidateReason.ENTITY_KEYWORD);
                    }
                    if (hasFishKeyword(sourceModId) || hasFishKeyword(sourceName)) {
                        reasons.add(CandidateReason.SOURCE_KEYWORD);
                    }
                    if (isWaterCategory(input.category())) {
                        reasons.add(CandidateReason.WATER_CATEGORY);
                    }

                    candidates.put(input.entityTypeId(), new CandidateEntity(
                            input.entityTypeId(), input.displayName(), sourceModId,
                            Component.literal(sourceName), confidence(reasons),
                            CompatibilityLevel.UNVERIFIED, reasons));
                });

        Map<String, int[]> countsBySource = new HashMap<>();
        for (CandidateEntity candidate : candidates.values()) {
            int[] counts = countsBySource.computeIfAbsent(candidate.sourceModId(), key -> new int[2]);
            counts[0]++;
            if (candidate.confidence() != CandidateConfidence.LOW) {
                counts[1]++;
            }
        }

        Map<String, DetectedFishMod> detectedMods = new LinkedHashMap<>();
        countsBySource.keySet().stream().sorted().forEach(sourceModId -> {
            LoadedModInfo loadedMod = modsById.get(sourceModId);
            String displayName = loadedMod != null ? loadedMod.displayName() : sourceModId;
            String version = loadedMod != null ? loadedMod.version() : "";
            int[] counts = countsBySource.get(sourceModId);
            detectedMods.put(sourceModId, new DetectedFishMod(sourceModId,
                    Component.literal(displayName), version, counts[0], counts[1]));
        });

        Set<ResourceLocation> unavailableImports = new LinkedHashSet<>();
        importedEntities.stream().filter(id -> !mergedInputs.containsKey(id)).sorted()
                .forEach(unavailableImports::add);
        return new DiscoverySnapshot(detectedMods, candidates, unavailableImports);
    }

    private static EntityDiscoveryInput mergeInput(EntityDiscoveryInput first,
            EntityDiscoveryInput second) {
        EnumSet<CandidateReason> mergedReasons = first.registryReasons().isEmpty()
                ? EnumSet.noneOf(CandidateReason.class)
                : EnumSet.copyOf(first.registryReasons());
        mergedReasons.addAll(second.registryReasons());
        return new EntityDiscoveryInput(first.entityTypeId(), first.displayName(),
                first.translationKey(), first.category(), mergedReasons);
    }

    private static boolean hasFishKeyword(String value) {
        String[] tokens = value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String token : tokens) {
            if (FISH_KEYWORDS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String entityPartOfTranslationKey(EntityDiscoveryInput input) {
        String prefix = "entity." + input.entityTypeId().getNamespace() + ".";
        return input.translationKey().startsWith(prefix)
                ? input.translationKey().substring(prefix.length())
                : input.translationKey();
    }

    private static boolean isWaterCategory(MobCategory category) {
        return category == MobCategory.WATER_CREATURE
                || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.UNDERGROUND_WATER_CREATURE;
    }

    private static CandidateConfidence confidence(Set<CandidateReason> reasons) {
        if (reasons.stream().anyMatch(STRONG_REASONS::contains)) {
            return CandidateConfidence.HIGH;
        }
        int independentWeakGroups = 0;
        if (reasons.contains(CandidateReason.ENTITY_KEYWORD)) {
            independentWeakGroups++;
        }
        if (reasons.contains(CandidateReason.SOURCE_KEYWORD)) {
            independentWeakGroups++;
        }
        if (reasons.contains(CandidateReason.AQUATIC_TAG)
                || reasons.contains(CandidateReason.WATER_CATEGORY)) {
            independentWeakGroups++;
        }
        return independentWeakGroups >= 2
                ? CandidateConfidence.MEDIUM : CandidateConfidence.LOW;
    }
}
