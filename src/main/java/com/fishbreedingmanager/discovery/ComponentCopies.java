package com.fishbreedingmanager.discovery;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/** 为不可变发现快照创建包含嵌套内容与 sibling 的独立 Component 树。 */
final class ComponentCopies {
    private ComponentCopies() {
    }

    static Component deepCopy(Component original) {
        JsonElement encoded = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .result()
                .orElseThrow(() -> new IllegalArgumentException("component cannot be encoded"));
        return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, encoded)
                .result()
                .orElseThrow(() -> new IllegalArgumentException("component cannot be decoded"));
    }
}
