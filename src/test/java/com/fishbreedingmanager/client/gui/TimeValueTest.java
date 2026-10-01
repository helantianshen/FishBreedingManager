package com.fishbreedingmanager.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 精确时间输入不能因展示单位、溢出或小数截断改变规则 */
class TimeValueTest {
    /** 单位反复切换必须保留单 tick、非整秒与最大整数边界 */
    @Test void unitSwitchPreservesSingleTickAndMaximumValue() {
        for (int ticks : new int[]{0, 1, 601, Integer.MAX_VALUE}) {
            TimeValue value = new TimeValue(ticks);
            for (int i = 0; i < 10; i++) { value.switchUnit(); assertEquals(ticks, value.ticks()); }
        }
    }
    /** 输入只允许精确整数 tick，近似分钟显示不损失已经接受的值 */
    @Test void rejectsFractionalTicksNegativeAndOverflow() {
        for (String text : new String[]{"0.01", "-1", "2147483647", "NaN", "", "1e999999999"}) {
            TimeValue value = new TimeValue(0); value.edit(text);
            assertThrows(RuntimeException.class, value::ticks, text);
        }
        TimeValue valid = new TimeValue(0); valid.edit("0.05"); assertEquals(1, valid.ticks());
        valid.switchUnit(); assertEquals(1, valid.ticks()); assertTrue(valid.approximate());
    }
}
