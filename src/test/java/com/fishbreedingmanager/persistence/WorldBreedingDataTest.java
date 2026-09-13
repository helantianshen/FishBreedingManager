package com.fishbreedingmanager.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fishbreedingmanager.breeding.BreedingRule;
import com.fishbreedingmanager.breeding.BreedingRuleSnapshot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

/**
 * 验证按存档保存的规则与导入实体在 NBT 边界上的往返一致性与容错行为。
 *
 * <p>这是玩家全部 FBM 配置的唯一持久化路径，因此除了正常往返，还必须锁定两条边界：
 * 单条损坏记录不能阻止整个存档加载；已存在的数据文件不能在加载时被重新种入默认规则。
 */
class WorldBreedingDataTest {
    private static final ResourceLocation COD = ResourceLocation.parse("minecraft:cod");
    private static final ResourceLocation SALMON = ResourceLocation.parse("minecraft:salmon");
    private static final ResourceLocation KELP = ResourceLocation.parse("minecraft:kelp");
    private static final ResourceLocation SEAGRASS = ResourceLocation.parse("minecraft:seagrass");
    private static final ResourceLocation FOOD_TAG = ResourceLocation.parse("c:fish_food");
    private static final ResourceLocation IMPORTED = ResourceLocation.parse("weirdfishmod:moonfish");

    /**
     * 全部规则字段、食物 Tag、启用状态、导入集合和持久化顺序都必须完整往返。
     */
    @Test
    void savesAndLoadsEveryRuleFieldAndImportedEntity() {
        WorldBreedingData original = new WorldBreedingData();
        BreedingRule cod = new BreedingRule(
                COD, List.of(KELP, SEAGRASS), List.of(FOOD_TAG), 600, 1200, true);
        BreedingRule salmon = new BreedingRule(
                SALMON, List.of(SEAGRASS), List.of(), 120, 60, false);
        original.putRule(cod);
        original.putRule(salmon);
        original.addImported(IMPORTED);

        WorldBreedingData restored = roundTrip(original);

        assertEquals(List.of(cod, salmon), List.copyOf(restored.allRules()));
        assertEquals(Set.of(IMPORTED), restored.getImportedEntities());
        assertEquals(cod, restored.getRule(COD));
        assertFalse(restored.getRule(SALMON).enabled());
    }

    /**
     * 往返后构建的运行时快照必须与原始工作集等价，保证重载路径不引入语义漂移。
     */
    @Test
    void restoredSnapshotMatchesOriginalWorkingSet() {
        WorldBreedingData original = new WorldBreedingData();
        original.putRule(new BreedingRule(COD, List.of(KELP), List.of(), 600, 1200, true));
        original.addImported(IMPORTED);

        BreedingRuleSnapshot before = original.buildSnapshot();
        BreedingRuleSnapshot after = roundTrip(original).buildSnapshot();

        assertEquals(before.rules(), after.rules());
        assertEquals(before.importedEntities(), after.importedEntities());
    }

    /**
     * 空数据必须往返为空，加载器不得在已有数据文件上重新种入默认原版鱼规则。
     */
    @Test
    void loadDoesNotReseedDefaultRules() {
        WorldBreedingData emptied = roundTrip(new WorldBreedingData());

        assertTrue(emptied.allRules().isEmpty());
        assertTrue(emptied.getImportedEntities().isEmpty());
    }

    /**
     * 非法实体 ID、非法食物 ID 和非法导入 ID 只能被逐条跳过，不能让整个存档加载失败。
     */
    @Test
    void skipsMalformedEntriesWithoutFailingTheWholeLoad() {
        CompoundTag broken = new CompoundTag();
        ListTag rules = new ListTag();
        rules.add(ruleTag("NOT A VALID ID", "minecraft:kelp"));
        rules.add(ruleTag("minecraft:cod", "Invalid Item"));
        broken.put("rules", rules);
        ListTag imported = new ListTag();
        imported.add(StringTag.valueOf("Not An Id"));
        imported.add(StringTag.valueOf(IMPORTED.toString()));
        broken.put("imported_entities", imported);

        WorldBreedingData loaded = WorldBreedingData.load(broken, null);

        assertEquals(1, loaded.allRules().size());
        BreedingRule surviving = loaded.getRule(COD);
        assertTrue(surviving.breedingItemIds().isEmpty());
        assertEquals(Set.of(IMPORTED), loaded.getImportedEntities());
    }

    /**
     * 完全缺失的字段必须按空集合处理，而不是抛出异常。
     */
    @Test
    void loadsEmptyTagAsEmptyData() {
        WorldBreedingData loaded = WorldBreedingData.load(new CompoundTag(), null);

        assertTrue(loaded.allRules().isEmpty());
        assertTrue(loaded.getImportedEntities().isEmpty());
        assertNull(loaded.getRule(COD));
    }

    private static WorldBreedingData roundTrip(WorldBreedingData source) {
        return WorldBreedingData.load(source.save(new CompoundTag(), null), null);
    }

    private static CompoundTag ruleTag(String entityId, String itemId) {
        CompoundTag tag = new CompoundTag();
        tag.putString("entity", entityId);
        ListTag items = new ListTag();
        items.add(StringTag.valueOf(itemId));
        tag.put("items", items);
        tag.put("tags", new ListTag());
        tag.putInt("cooldown_ticks", 600);
        tag.putInt("growth_ticks", 1200);
        tag.putBoolean("enabled", true);
        return tag;
    }
}
