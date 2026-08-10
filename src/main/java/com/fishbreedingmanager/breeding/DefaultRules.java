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

    /**
     * 向新建世界数据种入四种默认启用的原版鱼规则。
     *
     * <p>该方法只由新数据工厂调用；读取已有存档或用户主动删空规则时不会再次执行，因此不会覆盖玩家配置。
     *
     * @param data 尚无持久化文件的新世界规则数据
     */
    public static void seedInto(WorldBreedingData data) {
        // 默认冷却 600 刻、成长 1200 刻；玩家可通过管理员命令逐条修改。
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
