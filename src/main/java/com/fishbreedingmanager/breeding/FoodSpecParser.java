package com.fishbreedingmanager.breeding;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * 解析管理员命令中的逗号分隔繁殖食物表达式。
 *
 * <p>普通条目表示物品注册表 ID，例如 {@code minecraft:kelp}；以 {@code #} 开头的条目表示物品标签，
 * 例如 {@code #minecraft:planks}。本类只负责语法解析，ID 是否真实存在由 {@link RuleValidator} 统一校验。
 */
public final class FoodSpecParser {
    private FoodSpecParser() {
    }

    /**
     * 将逗号分隔的食物表达式解析为物品与标签两组资源 ID。
     *
     * <p>条目前后的空白会被忽略，但列表中的空项、只有 {@code #} 的空标签和非法资源 ID 都会被拒绝，
     * 从而避免命令看似执行成功却产生不可触发的规则。
     *
     * @param input 用户提供的逗号分隔表达式
     * @return 保持各自输入顺序的不可变解析结果
     * @throws IllegalArgumentException 输入为空、含空项或含非法资源 ID 时抛出
     */
    public static ParsedFood parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("繁殖物品不能为空");
        }

        List<ResourceLocation> itemIds = new ArrayList<>();
        List<ResourceLocation> tagIds = new ArrayList<>();
        for (String raw : input.split(",", -1)) {
            String token = raw.trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException("繁殖物品列表不能包含空项");
            }

            boolean tag = token.startsWith("#");
            String idText = tag ? token.substring(1) : token;
            if (idText.isEmpty()) {
                throw new IllegalArgumentException("物品标签 ID 不能为空: " + token);
            }
            ResourceLocation id = ResourceLocation.tryParse(idText);
            if (id == null) {
                throw new IllegalArgumentException("无效的资源 ID: " + token);
            }
            (tag ? tagIds : itemIds).add(id);
        }
        return new ParsedFood(itemIds, tagIds);
    }
}
