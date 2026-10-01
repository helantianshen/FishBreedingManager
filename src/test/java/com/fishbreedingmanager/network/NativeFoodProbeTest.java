package com.fishbreedingmanager.network;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 通用食物读取受预算约束，异常与缺少接口不能被误报为完整规则 */
class NativeFoodProbeTest {
    /** 每次推进必须遵守物品预算，且不得调用喂食、tick 或其他实体行为 */
    @Test void readsOnlyFoodPredicateInBoundedBatches() {
        Animal animal = mock(Animal.class);
        when(animal.isFood(any())).thenAnswer(call -> call.<net.minecraft.world.item.ItemStack>getArgument(0).is(Items.WHEAT));
        NativeFoodProbe probe = new NativeFoodProbe("test:animal", animal, List.of(Items.WHEAT, Items.STONE).iterator());
        probe.step(1);
        assertFalse(probe.done());
        verify(animal, times(1)).isFood(any());
        probe.step(1);
        assertTrue(probe.done());
        assertEquals(List.of("minecraft:wheat"), probe.result().items());
        assertEquals("ready", probe.result().status());
        verify(animal, times(2)).isFood(any());
        verifyNoMoreInteractions(animal);
    }

    /** 缺少通用接口时直接返回不支持读取，不触发物品检查 */
    @Test void nonAnimalDoesNotScanItemsOrInteract() {
        Entity entity = mock(Entity.class);
        NativeFoodProbe probe = new NativeFoodProbe("test:entity", entity, List.of(Items.WHEAT).iterator());
        probe.step(64);
        assertTrue(probe.done());
        assertEquals("unsupported", probe.result().status());
        assertTrue(probe.result().items().isEmpty());
        verifyNoInteractions(entity);
    }

    /** 单个模组回调失败不吞掉已知匹配，但结果必须声明不完整 */
    @Test void failingPredicateKeepsOtherMatchesButMarksPartial() {
        Animal animal = mock(Animal.class);
        when(animal.isFood(any())).thenThrow(new IllegalStateException("mod failure")).thenReturn(true);
        NativeFoodProbe probe = new NativeFoodProbe("test:animal", animal, List.of(Items.STONE, Items.WHEAT).iterator());
        probe.step(64);
        assertTrue(probe.done());
        assertEquals("partial", probe.result().status());
        assertEquals(List.of("minecraft:wheat"), probe.result().items());
    }

    /** 限制匹配数量并保证网络往返保留部分结果状态 */
    @Test void limitsResultsAndRoundTripsStatus() {
        Animal animal = mock(Animal.class);
        when(animal.isFood(any())).thenReturn(true);
        NativeFoodProbe probe = new NativeFoodProbe("test:animal", animal,
                java.util.Collections.nCopies(129, Items.WHEAT).iterator());
        probe.step(256);
        assertTrue(probe.done());
        assertEquals(128, probe.result().items().size());
        assertEquals("partial", probe.result().status());
        assertEquals(probe.result(), ManagementData.JSON.fromJson(
                ManagementData.JSON.toJson(probe.result()), ManagementData.NativeFoods.class));
    }

    /** 接口存在但没有匹配项，与接口缺失必须区别显示 */
    @Test void emptyMatchIsDistinctFromUnsupported() {
        Animal animal = mock(Animal.class);
        NativeFoodProbe probe = new NativeFoodProbe("test:animal", animal, List.of(Items.STONE).iterator());
        probe.step(64);
        assertEquals("ready", probe.result().status());
        assertTrue(probe.result().items().isEmpty());
    }
}
