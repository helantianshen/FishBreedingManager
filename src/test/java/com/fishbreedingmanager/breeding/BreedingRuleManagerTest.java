package com.fishbreedingmanager.breeding;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 验证运行时规则管理器能区分“尚未初始化”与“已成功安装空快照”。
 */
class BreedingRuleManagerTest {
    /**
     * 空规则集可能是玩家的合法配置，因此只有明确安装后才能视为初始化完成。
     */
    @Test
    void becomesInitializedAfterInstallingEvenAnEmptySnapshot() {
        BreedingRuleManager manager = new BreedingRuleManager();

        assertFalse(manager.isInitialized());

        manager.install(BreedingRuleSnapshot.EMPTY);

        assertTrue(manager.isInitialized());
    }
}
