package com.fishbreedingmanager.discovery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.ModList;

/** 将当前 Minecraft/NeoForge 运行环境转换为加载器无关的发现输入。 */
public final class MinecraftDiscoverySource {
    private static final TagKey<EntityType<?>> COMMON_FISH = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "fish"));
    private static final TagKey<EntityType<?>> COMMON_FISHES = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "fishes"));
    private static final TagKey<EntityType<?>> AQUATIC = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace("aquatic"));
    private static final Set<ResourceLocation> VANILLA_FISH = Set.of(
            ResourceLocation.withDefaultNamespace("cod"),
            ResourceLocation.withDefaultNamespace("salmon"),
            ResourceLocation.withDefaultNamespace("tropical_fish"),
            ResourceLocation.withDefaultNamespace("pufferfish"));

    /** 创建无状态 Minecraft/NeoForge 发现输入适配器。 */
    public MinecraftDiscoverySource() {
    }

    /**
     * 捕获完整 EntityType Registry。该过程只读类型元数据和 Holder Tag，不调用实体工厂。
     *
     * @return 按 EntityType ID 稳定排序的完整 Registry 描述
     */
    public List<EntityDiscoveryInput> captureEntities() {
        List<EntityDiscoveryInput> result = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<EntityType<?>>, EntityType<?>> entry
                : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            EntityType<?> entityType = entry.getValue();
            Holder<EntityType<?>> holder = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entityType);
            Set<CandidateReason> reasons = registryReasons(id,
                    holder.is(COMMON_FISH), holder.is(COMMON_FISHES), holder.is(AQUATIC));
            result.add(new EntityDiscoveryInput(id,
                    Component.translatable(entityType.getDescriptionId()),
                    entityType.getDescriptionId(), entityType.getCategory(), reasons));
        }
        result.sort(Comparator.comparing(EntityDiscoveryInput::entityTypeId));
        return List.copyOf(result);
    }

    static Set<CandidateReason> registryReasons(ResourceLocation id,
            boolean inCommonFish, boolean inCommonFishes, boolean inAquatic) {
        EnumSet<CandidateReason> reasons = EnumSet.noneOf(CandidateReason.class);
        if (VANILLA_FISH.contains(id)) {
            reasons.add(CandidateReason.VANILLA_FISH);
        }
        if (inCommonFish || inCommonFishes) {
            reasons.add(CandidateReason.COMMON_FISH_TAG);
        }
        if (inAquatic) {
            reasons.add(CandidateReason.AQUATIC_TAG);
        }
        return Set.copyOf(reasons);
    }

    /**
     * 读取已加载 Mod 的显示名和版本，并按 Mod ID 稳定排序去重。
     *
     * @return 与 ModList 类型解耦的稳定元数据列表
     */
    public List<LoadedModInfo> captureLoadedMods() {
        Map<String, LoadedModInfo> result = new LinkedHashMap<>();
        ModList.get().getMods().stream()
                .sorted(Comparator.comparing(info -> info.getModId()))
                .forEach(info -> result.putIfAbsent(info.getModId(),
                        new LoadedModInfo(info.getModId(), info.getDisplayName(),
                                info.getVersion().toString())));
        return List.copyOf(result.values());
    }
}
