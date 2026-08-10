package com.fishbreedingmanager.breeding;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * 管理员命令中繁殖食物表达式的不可变解析结果。
 *
 * <p>普通资源 ID 与带 {@code #} 前缀的物品标签分别保存，调用方可直接将两组数据交给
 * {@link BreedingRule}，无需再次解析用户输入。
 *
 * @param itemIds 按输入顺序排列的物品注册表 ID
 * @param tagIds 按输入顺序排列的物品标签 ID，内容不包含 {@code #} 前缀
 */
public record ParsedFood(List<ResourceLocation> itemIds, List<ResourceLocation> tagIds) {
    /**
     * 创建解析结果并复制输入列表，防止外部修改已经完成校验的命令参数。
     */
    public ParsedFood {
        itemIds = List.copyOf(itemIds);
        tagIds = List.copyOf(tagIds);
    }
}
