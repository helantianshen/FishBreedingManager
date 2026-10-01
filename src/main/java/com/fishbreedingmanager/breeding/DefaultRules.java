package com.fishbreedingmanager.breeding;

import java.util.List;

import com.fishbreedingmanager.persistence.WorldBreedingData;

import net.minecraft.resources.ResourceLocation;

/**
 * 提供四种原版鱼的 FBM 内建规则，不代表从原版读取到的繁殖行为
 *
 * <p>只有新建 {@link WorldBreedingData} 时自动种入；读取已有数据或管理员删空规则时不补回默认值
 * GUI 恢复默认操作读取同一份定义，并在管理员保存后才应用到世界
 */
public final class DefaultRules {
    private DefaultRules() {
    }

    /**
     * 向新建世界数据种入四种默认启用的原版鱼规则
     *
     * <p>该方法只由新数据工厂调用；读取已有存档或用户主动删空规则时不会再次执行，因此不会覆盖玩家配置
     *
     * @param data 尚无持久化文件的新世界规则数据
     */
    public static void seedInto(WorldBreedingData data) {
        rules().forEach(data::putRule);
    }

    /**
     * 返回内建默认规则，不写入世界数据
     *
     * @return 四种原版鱼的默认规则
     */
    public static List<BreedingRule> rules() {
        return List.of(
                new BreedingRule(
                ResourceLocation.parse("minecraft:cod"),
                List.of(ResourceLocation.parse("minecraft:kelp")),
                List.of(),
                600, 1200, true),

                new BreedingRule(
                ResourceLocation.parse("minecraft:salmon"),
                List.of(ResourceLocation.parse("minecraft:kelp"), ResourceLocation.parse("minecraft:seagrass")),
                List.of(),
                600, 1200, true),

                new BreedingRule(
                ResourceLocation.parse("minecraft:tropical_fish"),
                List.of(ResourceLocation.parse("minecraft:seagrass")),
                List.of(),
                600, 1200, true),

                new BreedingRule(
                ResourceLocation.parse("minecraft:pufferfish"),
                List.of(ResourceLocation.parse("minecraft:kelp")),
                List.of(),
                600, 1200, true));
    }
}
