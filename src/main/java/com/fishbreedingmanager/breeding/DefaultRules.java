package com.fishbreedingmanager.breeding;

import java.util.List;

import com.fishbreedingmanager.persistence.WorldBreedingData;

import net.minecraft.resources.ResourceLocation;

/**
 * 新世界首次创建时种入 {@link WorldBreedingData} 的默认繁殖规则 需求§46, 
 * 默认候选集含四种原版鱼 
 * 仅在无存档数据文件时种入, 故用户手动删光规则会被尊重 
 */
public final class DefaultRules {
    private DefaultRules() {
    }

    /** 把默认原版鱼规则种入给定数据存储, 已存在则安全无操作 */
    public static void seedInto(WorldBreedingData data) {
        // PoC 目标 需求§51, cod + kelp, 冷却 600t, 成长 1200t 
        data.putRule(new BreedingRule(
                ResourceLocation.parse("minecraft:cod"),
                List.of(ResourceLocation.parse("minecraft:kelp")),
                List.of(),
                600, 1200, true));

        data.putRule(new BreedingRule(
                ResourceLocation.parse("minecraft:salmon"),
                List.of(ResourceLocation.parse("minecraft:kelp"), ResourceLocation.parse("minecraft:seagrass")),
                List.of(),
                600, 1200, true));

        data.putRule(new BreedingRule(
                ResourceLocation.parse("minecraft:tropical_fish"),
                List.of(ResourceLocation.parse("minecraft:seagrass")),
                List.of(),
                600, 1200, true));

        data.putRule(new BreedingRule(
                ResourceLocation.parse("minecraft:pufferfish"),
                List.of(ResourceLocation.parse("minecraft:kelp")),
                List.of(),
                600, 1200, true));
    }
}
