package com.fishbreedingmanager.breeding;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 实体级运行时繁殖状态, Rule vs State 分离, 规则属世界 状态属实体 
 * 通过 NeoForge AttachmentType 挂到实体上, 随实体 NBT 持久化, 不修改任何实体类 
 *
 * <p>存储字段, 
 * <ul>
 *   <li>{@code inLove} / {@code loveUntil} 临时 love 状态, game-time tick 截止 </li>
 *   <li>{@code cooldownUntil} 繁殖冷却截止, 跨 reload 持久化, 热重载时不重算 需求§38 </li>
 *   <li>{@code juvenile} / {@code adultAt} FBM 自有幼体系统, 用于无原生年龄系统的实体 需求§10, 
 *       {@code adultAt} 热重载时不重算 需求§39 </li>
 *   <li>{@code mate} 求偶中配对配偶的 UUID, 或 {@code null} </li>
 * </ul>
 *
 * <p>所有时间字段都是 {@link net.minecraft.world.level.Level#getGameTime()} level game-time tick 
 */
public final class BreedingState {
    public static final Codec<BreedingState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("in_love").forGetter(s -> s.inLove),
            Codec.LONG.fieldOf("love_until").forGetter(s -> s.loveUntil),
            Codec.LONG.fieldOf("cooldown_until").forGetter(s -> s.cooldownUntil),
            Codec.BOOL.fieldOf("juvenile").forGetter(s -> s.juvenile),
            Codec.LONG.fieldOf("adult_at").forGetter(s -> s.adultAt),
            // mate 存为字符串, 空串表示无配偶 
            // 只用保证稳定的 Codec.STRING.optionalFieldOf(name, default) API, 避免版本不确定的 UUID helper 
            Codec.STRING.optionalFieldOf("mate", "").forGetter(s -> s.mate == null ? "" : s.mate.toString())
    ).apply(instance, BreedingState::decode));

    private boolean inLove;
    private long loveUntil;
    private long cooldownUntil;
    private boolean juvenile;
    private long adultAt;
    private UUID mate;

    public BreedingState() {
        // 新生成成年实体的默认空状态 
    }

    private BreedingState(boolean inLove, long loveUntil, long cooldownUntil,
                          boolean juvenile, long adultAt, String mateStr) {
        this.inLove = inLove;
        this.loveUntil = loveUntil;
        this.cooldownUntil = cooldownUntil;
        this.juvenile = juvenile;
        this.adultAt = adultAt;
        this.mate = mateStr.isEmpty() ? null : UUID.fromString(mateStr);
    }

    private static BreedingState decode(boolean inLove, long loveUntil, long cooldownUntil,
                                        boolean juvenile, long adultAt, String mateStr) {
        return new BreedingState(inLove, loveUntil, cooldownUntil, juvenile, adultAt, mateStr);
    }

    // ---- Love ----

    public boolean isInLove(long now) {
        return inLove && now < loveUntil;
    }

    public void enterLove(long now, long loveDurationTicks) {
        this.inLove = true;
        this.loveUntil = now + loveDurationTicks;
    }

    /** 清除 love, 繁殖完成或规则被移除时调用 */
    public void clearLove() {
        this.inLove = false;
        this.loveUntil = 0L;
        this.mate = null;
    }

    public long getLoveUntil() {
        return loveUntil;
    }

    // ---- Cooldown ----

    public boolean isOnCooldown(long now) {
        return now < cooldownUntil;
    }

    public void startCooldown(long now, long cooldownTicks) {
        this.cooldownUntil = now + cooldownTicks;
    }

    public long getCooldownUntil() {
        return cooldownUntil;
    }

    // ---- Juvenile / growth ----

    public boolean isJuvenile(long now) {
        return juvenile && now < adultAt;
    }

    /** 标记新生后代为幼体, 成年时刻为 {@code now + growthTimeTicks} */
    public void markJuvenile(long now, long growthTimeTicks) {
        this.juvenile = true;
        this.adultAt = now + growthTimeTicks;
    }

    /** 立即成年, {@code now >= adultAt} 时也会惰性调用 */
    public void growUp() {
        this.juvenile = false;
        this.adultAt = 0L;
    }

    public long getAdultAt() {
        return adultAt;
    }

    /**
     * 客户端渲染缩放系数, 幼体 0.5 成年 1.0 需求§10 
     * 后续可存储原始成长时长实现平滑按比例缩放 
     */
    public float visualScale(long now) {
        return isJuvenile(now) ? 0.5F : 1.0F;
    }

    // ---- Mate ----

    public UUID getMate() {
        return mate;
    }

    public void setMate(UUID mate) {
        this.mate = mate;
    }

    // ---- Composite checks ----

    /** 当前实体是否可被喂食进入 love */
    public boolean canEnterLove(long now) {
        return !isOnCooldown(now) && !isJuvenile(now) && !isInLove(now);
    }

    /** 惰性结算过期计时器, 保证读取一致 */
    public void tickTimers(long now) {
        if (inLove && now >= loveUntil) {
            inLove = false;
            mate = null;
        }
        if (juvenile && now >= adultAt) {
            juvenile = false;
        }
    }
}
