package com.fishbreedingmanager.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fishbreedingmanager.breeding.BreedingRule;
import com.fishbreedingmanager.breeding.BreedingRuleSnapshot;
import com.fishbreedingmanager.breeding.DefaultRules;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 每世界持久化的繁殖规则与导入实体存储 需求§23/§44, 
 * 每世界/存档各自独立 FBM 配置, 世界游玩数据必须 per-world, 而非全局配置文件 
 *
 * <p>挂到 Overworld 的数据存储, 使其在一个存档的所有维度间共享 
 * Overworld 不会被完全卸载, NeoForge SavedData 文档推荐多 level 数据这样做 
 * 持久化到 {@code <world>/data/fbm_breeding_rules.dat} 
 *
 * <p>序列化用纯 {@link CompoundTag}/{@link ListTag}, 不用 {@code NbtOps}, 以求稳定 
 * 运行时不可变快照由 {@link #buildSnapshot()} 按需构建, 由 {@code BreedingRuleManager} 原子替换 
 * 本类仅是持久化数据源 
 */
public final class WorldBreedingData extends SavedData {
    private static final String DATA_NAME = "fbm_breeding_rules";

    private final Map<ResourceLocation, BreedingRule> rules = new LinkedHashMap<>();
    private final Set<ResourceLocation> importedEntities = new LinkedHashSet<>();

    public WorldBreedingData() {
    }

    private WorldBreedingData(List<BreedingRule> rulesList, List<ResourceLocation> importedList) {
        for (BreedingRule rule : rulesList) {
            rules.put(rule.entityTypeId(), rule);
        }
        importedEntities.addAll(importedList);
    }

    // ---- 访问 ----

    public static WorldBreedingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WorldBreedingData::create, WorldBreedingData::load),
                DATA_NAME);
    }

    public static WorldBreedingData get(ServerLevel level) {
        return get(level.getServer());
    }

    /** 新数据工厂, 种入默认原版鱼规则 需求§46 */
    private static WorldBreedingData create() {
        WorldBreedingData data = new WorldBreedingData();
        DefaultRules.seedInto(data);
        return data;
    }

    // ---- 修改器, 每个都调 setDirty 以便持久化 ----

    public void putRule(BreedingRule rule) {
        rules.put(rule.entityTypeId(), rule);
        setDirty();
    }

    public void removeRule(ResourceLocation id) {
        rules.remove(id);
        setDirty();
    }

    public BreedingRule getRule(ResourceLocation id) {
        return rules.get(id);
    }

    public void addImported(ResourceLocation id) {
        if (importedEntities.add(id)) {
            setDirty();
        }
    }

    public void removeImported(ResourceLocation id) {
        if (importedEntities.remove(id)) {
            setDirty();
        }
    }

    public Set<ResourceLocation> getImportedEntities() {
        return Set.copyOf(importedEntities);
    }

    public Collection<BreedingRule> allRules() {
        return List.copyOf(rules.values());
    }

    /** 为运行时管理器构建不可变快照 */
    public BreedingRuleSnapshot buildSnapshot() {
        return new BreedingRuleSnapshot(Map.copyOf(rules), Set.copyOf(importedEntities));
    }

    // ---- 序列化 手动 NBT, 不用 NbtOps ----

    public static WorldBreedingData load(CompoundTag tag, HolderLookup.Provider registries) {
        List<BreedingRule> rulesList = new ArrayList<>();
        ListTag rulesTag = tag.getList("rules", Tag.TAG_COMPOUND);
        for (int i = 0; i < rulesTag.size(); i++) {
            BreedingRule rule = readRule(rulesTag.getCompound(i));
            if (rule != null) {
                rulesList.add(rule);
            }
        }
        List<ResourceLocation> importedList = new ArrayList<>();
        ListTag importedTag = tag.getList("imported_entities", Tag.TAG_STRING);
        for (int i = 0; i < importedTag.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(importedTag.getString(i));
            if (id != null) {
                importedList.add(id);
            }
        }
        return new WorldBreedingData(rulesList, importedList);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rulesTag = new ListTag();
        for (BreedingRule rule : rules.values()) {
            rulesTag.add(writeRule(rule));
        }
        tag.put("rules", rulesTag);

        ListTag importedTag = new ListTag();
        for (ResourceLocation id : importedEntities) {
            importedTag.add(StringTag.valueOf(id.toString()));
        }
        tag.put("imported_entities", importedTag);

        return tag;
    }

    private static CompoundTag writeRule(BreedingRule rule) {
        CompoundTag rt = new CompoundTag();
        rt.putString("entity", rule.entityTypeId().toString());

        ListTag itemsTag = new ListTag();
        for (ResourceLocation id : rule.breedingItemIds()) {
            itemsTag.add(StringTag.valueOf(id.toString()));
        }
        rt.put("items", itemsTag);

        ListTag tagsTag = new ListTag();
        for (ResourceLocation id : rule.breedingTagIds()) {
            tagsTag.add(StringTag.valueOf(id.toString()));
        }
        rt.put("tags", tagsTag);

        rt.putInt("cooldown_ticks", rule.breedingCooldownTicks());
        rt.putInt("growth_ticks", rule.growthTimeTicks());
        rt.putBoolean("enabled", rule.enabled());
        return rt;
    }

    private static BreedingRule readRule(CompoundTag rt) {
        ResourceLocation entityId = ResourceLocation.tryParse(rt.getString("entity"));
        if (entityId == null) {
            return null;
        }
        List<ResourceLocation> items = readRlList(rt.getList("items", Tag.TAG_STRING));
        List<ResourceLocation> tags = readRlList(rt.getList("tags", Tag.TAG_STRING));
        int cooldown = rt.getInt("cooldown_ticks");
        int growth = rt.getInt("growth_ticks");
        boolean enabled = rt.getBoolean("enabled");
        return new BreedingRule(entityId, items, tags, cooldown, growth, enabled);
    }

    private static List<ResourceLocation> readRlList(ListTag listTag) {
        List<ResourceLocation> result = new ArrayList<>();
        for (int i = 0; i < listTag.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(listTag.getString(i));
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }
}
