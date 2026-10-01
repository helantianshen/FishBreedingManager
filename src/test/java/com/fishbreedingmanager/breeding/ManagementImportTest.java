package com.fishbreedingmanager.breeding;

import java.util.List;
import com.fishbreedingmanager.discovery.DiscoverySnapshot;
import com.fishbreedingmanager.discovery.FishDiscoveryManager;
import com.fishbreedingmanager.persistence.WorldBreedingData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anySet;

/** 导入标记与关联规则必须一起提交，目录构建失败保持旧状态 */
class ManagementImportTest {
    /** 首次加载失败的存档只能用于诊断，不能激活规则或标记脏数据 */
    @Test void failedInitialLoadCanBeInspectedWithoutActivatingRules() {
        WorldBreedingData data = new WorldBreedingData();
        var id = ResourceLocation.parse("removed_mod:fish");
        data.putRule(new BreedingRule(id, List.of(ResourceLocation.parse("minecraft:kelp")), List.of(), 600, 1200, true));
        data.setDirty(false);
        BreedingRuleManager manager = new BreedingRuleManager();
        manager.rememberStored(data.buildSnapshot());
        assertFalse(manager.isInitialized()); assertNull(manager.find(id));
        assertTrue(manager.managementSnapshot().rules().containsKey(id));
        assertEquals(0, manager.revision()); assertFalse(data.isDirty());
    }
    /** 发现目录构建失败时，规则删除与导入移除必须一起保持原状 */
    @Test void failedDiscoveryDoesNotPartiallyRemoveRuleOrImport() {
        WorldBreedingData data = new WorldBreedingData();
        var id = ResourceLocation.parse("minecraft:cod");
        var rule = new BreedingRule(id, List.of(ResourceLocation.parse("minecraft:kelp")), List.of(), 600, 1200, true);
        data.putRule(rule); data.addImported(id); data.setDirty(false);
        BreedingRuleManager manager = new BreedingRuleManager(); manager.install(data.buildSnapshot());
        var before = manager.snapshot(); long revision = manager.revision();
        FishDiscoveryManager discovery = mock(FishDiscoveryManager.class);
        when(discovery.prepare(anySet())).thenThrow(new IllegalStateException("broken directory"));
        var service = new WorldBreedingService(new RuleValidator());
        assertThrows(IllegalStateException.class, () -> service.changeImport(data, manager, discovery, id, false, true));
        assertSame(before, manager.snapshot()); assertEquals(revision, manager.revision());
        assertEquals(rule, data.getRule(id)); assertTrue(data.getImportedEntities().contains(id)); assertFalse(data.isDirty());
    }
    /** 导入不创建规则，联合移除只推进一次修订号 */
    @Test void importDoesNotEnableAndRemovalCommitsOneRevision() {
        WorldBreedingData data = new WorldBreedingData();
        var id = ResourceLocation.parse("minecraft:cod");
        BreedingRuleManager manager = new BreedingRuleManager(); manager.install(data.buildSnapshot());
        FishDiscoveryManager discovery = mock(FishDiscoveryManager.class);
        when(discovery.prepare(anySet())).thenReturn(DiscoverySnapshot.EMPTY);
        var service = new WorldBreedingService(new RuleValidator());
        assertTrue(service.changeImport(data, manager, discovery, id, true, false).success());
        assertNull(data.getRule(id)); assertTrue(manager.snapshot().importedEntities().contains(id));
        service.upsert(data, manager, new BreedingRule(id, List.of(ResourceLocation.parse("minecraft:kelp")), List.of(), 600, 1200, true));
        long before = manager.revision();
        assertTrue(service.changeImport(data, manager, discovery, id, false, true).success());
        assertEquals(before + 1, manager.revision()); assertNull(manager.find(id));
        assertFalse(data.getImportedEntities().contains(id)); assertTrue(data.isDirty());
    }
}
