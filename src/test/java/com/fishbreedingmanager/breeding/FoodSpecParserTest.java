package com.fishbreedingmanager.breeding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

/**
 * 验证管理员命令中物品 ID 与物品标签 ID 的确定性解析。
 */
class FoodSpecParserTest {
    /**
     * 逗号分隔输入应保持原始顺序，并把带 {@code #} 前缀的条目归入标签集合。
     */
    @Test
    void parsesMultipleItemsAndTags() {
        ParsedFood parsed = FoodSpecParser.parse(
                "minecraft:kelp,minecraft:seagrass,#minecraft:planks");

        assertEquals(2, parsed.itemIds().size());
        assertEquals(ResourceLocation.parse("minecraft:kelp"), parsed.itemIds().getFirst());
        assertEquals(ResourceLocation.parse("minecraft:seagrass"), parsed.itemIds().get(1));
        assertEquals(1, parsed.tagIds().size());
        assertEquals(ResourceLocation.parse("minecraft:planks"), parsed.tagIds().getFirst());
    }

    /**
     * 空输入、空列表项、空标签和不符合资源 ID 语法的内容必须立即拒绝。
     */
    @Test
    void rejectsBlankEntriesAndInvalidResourceIds() {
        assertThrows(IllegalArgumentException.class, () -> FoodSpecParser.parse(" "));
        assertThrows(IllegalArgumentException.class,
                () -> FoodSpecParser.parse("minecraft:kelp,,minecraft:seagrass"));
        assertThrows(IllegalArgumentException.class, () -> FoodSpecParser.parse("#"));
        assertThrows(IllegalArgumentException.class, () -> FoodSpecParser.parse("Minecraft:Kelp"));
    }
}
