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
import com.fishbreedingmanager.breeding.RuleValidator;

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
 * 每个世界存档独立持久化的繁殖规则与导入实体集合。
 *
 * <p>数据挂到主世界 {@link ServerLevel} 的 SavedData 存储，使同一存档的所有维度共享一套 FBM 配置，并保存到
 * {@code <world>/data/fbm_breeding_rules.dat}。规则不使用全局配置文件，因此不同存档互不影响。
 *
 * <p>序列化用纯 {@link CompoundTag}/{@link ListTag}, 不用 {@code NbtOps}, 以求稳定 
 * 运行时不可变快照由 {@link #buildSnapshot()} 按需构建, 由 {@code BreedingRuleManager} 原子替换 
 * 本类仅是持久化数据源 
 */
public final class WorldBreedingData extends SavedData {
    private static final String DATA_NAME = "fbm_breeding_rules";

    private final Map<ResourceLocation, BreedingRule> rules = new LinkedHashMap<>();
    private final Set<ResourceLocation> importedEntities = new LinkedHashSet<>();

    /**
     * 创建不含规则和导入实体的空数据对象。
     *
     * <p>主要供加载器、测试和 {@link #create()} 新存档工厂使用；默认规则由新存档工厂另行种入。
     */
    public WorldBreedingData() {
    }

    private WorldBreedingData(List<BreedingRule> rulesList, List<ResourceLocation> importedList) {
        for (BreedingRule rule : rulesList) {
            rules.put(rule.entityTypeId(), rule);
        }
        importedEntities.addAll(importedList);
    }

    // ---- 访问 ----

    /**
     * 获取指定服务器当前存档共享的 FBM SavedData。
     *
     * <p>首次创建数据文件时会通过 {@link #create()} 种入四种默认原版鱼规则；已有文件则通过 {@link #load} 恢复。
     *
     * @param server 当前逻辑服务器
     * @return 与该世界存档绑定的数据对象
     */
    public static WorldBreedingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WorldBreedingData::create, WorldBreedingData::load),
                DATA_NAME);
    }

    /**
     * 从任意维度获取同一存档共享的 FBM SavedData。
     *
     * @param level 当前服务端 Level
     * @return 主世界数据存储中的共享数据对象
     */
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

    /**
     * 按实体 ID 新增或替换规则并标记数据待保存。
     *
     * <p>生产修改应优先经由 {@link com.fishbreedingmanager.breeding.WorldBreedingService}，避免绕过完整候选校验。
     *
     * @param rule 待保存规则
     */
    public void putRule(BreedingRule rule) {
        rules.put(rule.entityTypeId(), rule);
        setDirty();
    }

    /**
     * 删除指定实体规则并标记数据待保存。
     *
     * @param id 实体注册表 ID
     */
    public void removeRule(ResourceLocation id) {
        rules.remove(id);
        setDirty();
    }

    /**
     * 用已经完整校验的规则集合整体替换持久化数据。
     *
     * <p>该入口由事务式世界服务在候选全集通过 {@link RuleValidator} 后调用。整体替换可确保删除操作与更新操作
     * 使用相同的提交路径，并统一标记 {@link SavedData} 为待保存状态。
     *
     * @param replacement 通过完整校验的候选规则集合
     */
    public void replaceRules(Collection<BreedingRule> replacement) {
        rules.clear();
        for (BreedingRule rule : replacement) {
            rules.put(rule.entityTypeId(), rule);
        }
        setDirty();
    }

    /**
     * 查询持久化工作集中的指定规则。
     *
     * @param id 实体注册表 ID
     * @return 当前规则；不存在时为 {@code null}
     */
    public BreedingRule getRule(ResourceLocation id) {
        return rules.get(id);
    }

    /**
     * 记录一个由管理员手动导入的实体 ID，并在集合变化时标记待保存。
     *
     * @param id 导入实体注册表 ID
     */
    public void addImported(ResourceLocation id) {
        if (importedEntities.add(id)) {
            setDirty();
        }
    }

    /**
     * 删除手动导入实体记录，并在集合变化时标记待保存。
     *
     * @param id 导入实体注册表 ID
     */
    public void removeImported(ResourceLocation id) {
        if (importedEntities.remove(id)) {
            setDirty();
        }
    }

    /**
     * 返回手动导入实体 ID 的不可修改副本。
     *
     * @return 当前导入实体集合副本
     */
    public Set<ResourceLocation> getImportedEntities() {
        return Set.copyOf(importedEntities);
    }

    /**
     * 返回按持久化插入顺序排列的全部规则副本。
     *
     * @return 不暴露内部映射的规则集合
     */
    public Collection<BreedingRule> allRules() {
        return List.copyOf(rules.values());
    }

    /**
     * 从当前持久化工作集构建不可变运行时快照。
     *
     * @return 复制规则映射与导入集合的新快照
     */
    public BreedingRuleSnapshot buildSnapshot() {
        return new BreedingRuleSnapshot(Map.copyOf(rules), Set.copyOf(importedEntities));
    }

    // ---- 序列化 手动 NBT, 不用 NbtOps ----

    /**
     * 从世界 NBT 恢复规则与导入实体；单个非法资源 ID 会被跳过而非阻止整个存档加载。
     *
     * @param tag SavedData 根 NBT
     * @param registries 当前注册表查询提供器；手动 NBT 格式暂不需要读取
     * @return 恢复后的世界规则数据
     */
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

    /**
     * 将当前规则和导入实体完整写入 SavedData NBT。
     *
     * @param tag NeoForge 提供的目标根标签
     * @param registries 当前注册表查询提供器；手动 NBT 格式暂不需要读取
     * @return 写入完成的同一根标签
     */
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
