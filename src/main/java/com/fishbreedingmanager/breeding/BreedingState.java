package com.fishbreedingmanager.breeding;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 实体级运行时繁殖状态，负责保存跨存档仍然有效的 Love、冷却和成长计时器。
 *
 * <p>该对象通过 NeoForge AttachmentType 挂载到实体并随实体 NBT 持久化，不要求修改原版实体类。
 * 配偶 UUID 只用于当前加载会话中的临时导航，因此明确排除在 {@link #CODEC} 之外；实体重新加入世界后会在仍有效的
 * Love 时间窗中重新寻找配偶。
 *
 * <p>存储字段, 
 * <ul>
 *   <li>{@code inLove} / {@code loveUntil} 临时 love 状态, game-time tick 截止 </li>
 *   <li>{@code cooldownUntil} 繁殖冷却截止, 跨 reload 持久化, 热重载时不重算 需求§38 </li>
 *   <li>{@code juvenile} / {@code adultAt}：FBM 自有幼体状态，{@code adultAt} 热重载时不重算。</li>
 *   <li>{@code mate}：仅存在于内存的配偶 UUID，卸载或读档后必须重新匹配。</li>
 * </ul>
 *
 * <p>所有时间字段均采用 {@link net.minecraft.world.level.Level#getGameTime()} 的绝对游戏刻。
 */
public final class BreedingState {
    /**
     * 实体附件持久化编解码器。
     *
     * <p>这里只保存能够跨加载恢复的五个字段。旧存档中多余的 {@code mate} 字段会被 Mojang Codec 忽略，
     * 从而在兼容旧数据的同时切断已经失效的配偶关系。
     */
    public static final Codec<BreedingState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("in_love").forGetter(s -> s.inLove),
            Codec.LONG.fieldOf("love_until").forGetter(s -> s.loveUntil),
            Codec.LONG.fieldOf("cooldown_until").forGetter(s -> s.cooldownUntil),
            Codec.BOOL.fieldOf("juvenile").forGetter(s -> s.juvenile),
            Codec.LONG.fieldOf("adult_at").forGetter(s -> s.adultAt)
    ).apply(instance, BreedingState::new));

    private boolean inLove;
    private long loveUntil;
    private long cooldownUntil;
    private boolean juvenile;
    private long adultAt;
    private UUID mate;

    public BreedingState() {
        // Java 默认值正好表示：成年、无 Love、无冷却、无配偶。
    }

    private BreedingState(boolean inLove, long loveUntil, long cooldownUntil,
                          boolean juvenile, long adultAt) {
        this.inLove = inLove;
        this.loveUntil = loveUntil;
        this.cooldownUntil = cooldownUntil;
        this.juvenile = juvenile;
        this.adultAt = adultAt;
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

    /**
     * 在实体进入服务端世界时结算计时器并清除临时配偶关系。
     *
     * <p>仍处于 Love 时间窗的实体会返回 {@code true}，调用方据此将其重新加入活动索引；过期 Love 则会在
     * {@link #tickTimers(long)} 中清除。无论 Love 是否有效，都不能沿用卸载前的配偶 UUID。
     *
     * @param now 当前世界的绝对游戏刻
     * @return 实体是否仍处于有效 Love 时间窗
     */
    public boolean prepareForLevelJoin(long now) {
        tickTimers(now);
        mate = null;
        return isInLove(now);
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
