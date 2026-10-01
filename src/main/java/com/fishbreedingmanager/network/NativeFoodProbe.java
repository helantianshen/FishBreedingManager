package com.fishbreedingmanager.network;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 分批调用独立 Animal 实例的通用食物判断，只生成用于展示的物品 ID 列表
 *
 * <p>每个物品使用默认堆栈，不穷举组件、NBT、驯服或环境状态，因此匹配结果不等于完整繁殖条件
 * 普通物品全部检查完成与接口缺失分别返回不同状态；回调异常或结果超过 128 项时标记为部分结果
 * 本类不运行 AI、交互或繁殖流程，也不写入 FBM 规则；调用方须在服务端线程推进任务
 */
final class NativeFoodProbe {
    private final String entityId;
    private final Animal animal;
    private final Iterator<Item> items;
    private final List<String> matches = new ArrayList<>();
    private boolean partial;
    private boolean done;

    NativeFoodProbe(String entityId, Entity entity, Iterator<Item> items) {
        this.entityId = entityId;
        this.animal = entity instanceof Animal value ? value : null;
        this.items = items;
        this.done = animal == null;
    }

    /** 每次最多检查指定数量的默认物品堆栈，异常不影响其他物品的检查 */
    void step(int budget) {
        if (done) return;
        for (int i = 0; i < budget && items.hasNext(); i++) {
            Item item = items.next();
            try {
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty() && animal.isFood(stack)) {
                    if (matches.size() == 128) { partial = true; done = true; return; }
                    matches.add(BuiltInRegistries.ITEM.getKey(item).toString());
                }
            } catch (RuntimeException exception) { partial = true; }
        }
        if (!items.hasNext()) done = true;
    }

    boolean done() { return done; }
    ManagementData.NativeFoods result() {
        return new ManagementData.NativeFoods(entityId, animal == null ? "unsupported" : partial ? "partial" : "ready", List.copyOf(matches));
    }
}
