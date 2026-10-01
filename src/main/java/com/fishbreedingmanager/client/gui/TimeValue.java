package com.fishbreedingmanager.client.gui;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 保存时间字段的精确整数 tick 与当前展示单位
 *
 * <p>秒按 20 tick、分钟按 1200 tick 换算，均为游戏时间而非墙钟时间
 * 未编辑时直接保留原始 tick；用户输入后使用十进制精确转换，拒绝小数 tick、负数和整数溢出
 * 分钟显示最多保留九位小数，近似展示不会反向覆盖尚未编辑的精确值
 */
final class TimeValue {
    private int ticks;
    private int factor = 20;
    private boolean edited;
    private String text;

    /** 以已有规则的精确 tick 初始化 */
    TimeValue(int ticks) { this.ticks = ticks; this.text = display(); }
    /** 获取当前输入文本 */
    String text() { return text; }
    /** 用户编辑后进入精确十进制校验路径 */
    void edit(String value) { text = value; edited = true; }
    /** 获取秒或分钟的单位索引 */
    int unit() { return factor == 20 ? 0 : 1; }
    /** 单位切换失败时保持原有输入与单位 */
    void switchUnit() {
        ticks = ticks(); factor = factor == 20 ? 1200 : 20; edited = false; text = display();
    }
    /** 无损转换用户输入，拒绝非整数 tick、负数和溢出 */
    int ticks() {
        if (!edited) return ticks;
        if (text.length() > 32) throw new IllegalArgumentException("Time too long");
        int value = new BigDecimal(text).multiply(BigDecimal.valueOf(factor)).intValueExact();
        if (value < 0) throw new IllegalArgumentException("Negative time");
        return value;
    }
    /** 分钟不能有限表示时标明展示近似，底层值仍保持精确 */
    boolean approximate() {
        if (edited) return false;
        return new BigDecimal(text).multiply(BigDecimal.valueOf(factor)).compareTo(BigDecimal.valueOf(ticks)) != 0;
    }
    private String display() {
        return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(factor), 9, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
