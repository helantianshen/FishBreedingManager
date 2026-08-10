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
 * <p>状态字段分为：
 * <ul>
 *   <li>{@code inLove} / {@code loveUntil}：Love 状态及其绝对截止游戏刻。</li>
 *   <li>{@code cooldownUntil}：繁殖冷却绝对截止游戏刻，热重载时不重算。</li>
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

    /**
     * 创建成年、无 Love、无冷却、无配偶的默认状态。
     */
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

    /**
     * 判断当前时刻是否仍处于有效 Love 时间窗。
     *
     * @param now 当前世界绝对游戏刻
     * @return Love 标记存在且尚未到截止时刻时返回 {@code true}
     */
    public boolean isInLove(long now) {
        return inLove && now < loveUntil;
    }

    /**
     * 从当前时刻开始进入指定长度的 Love 时间窗。
     *
     * @param now 当前世界绝对游戏刻
     * @param loveDurationTicks Love 持续游戏刻数
     */
    public void enterLove(long now, long loveDurationTicks) {
        this.inLove = true;
        this.loveUntil = now + loveDurationTicks;
    }

    /**
     * 清除 Love 与临时配偶引用。
     *
     * <p>繁殖成功、Love 过期或规则被删除/禁用时调用；该方法不会改变已有冷却或成长计时器。
     */
    public void clearLove() {
        this.inLove = false;
        this.loveUntil = 0L;
        this.mate = null;
    }

    /**
     * 返回 Love 的绝对截止游戏刻。
     *
     * @return 未处于 Love 时通常为 {@code 0}
     */
    public long getLoveUntil() {
        return loveUntil;
    }

    // ---- Cooldown ----

    /**
     * 判断当前时刻是否仍处于繁殖冷却。
     *
     * @param now 当前世界绝对游戏刻
     * @return 尚未到冷却截止时刻时返回 {@code true}
     */
    public boolean isOnCooldown(long now) {
        return now < cooldownUntil;
    }

    /**
     * 从当前时刻开始记录指定长度的繁殖冷却。
     *
     * @param now 当前世界绝对游戏刻
     * @param cooldownTicks 冷却持续游戏刻数
     */
    public void startCooldown(long now, long cooldownTicks) {
        this.cooldownUntil = now + cooldownTicks;
    }

    /**
     * 返回繁殖冷却的绝对截止游戏刻。
     *
     * @return 未设置冷却时为 {@code 0}
     */
    public long getCooldownUntil() {
        return cooldownUntil;
    }

    // ---- Juvenile / growth ----

    /**
     * 判断实体当前是否仍处于 FBM 幼体阶段。
     *
     * @param now 当前世界绝对游戏刻
     * @return 幼体标记存在且尚未到成年时刻时返回 {@code true}
     */
    public boolean isJuvenile(long now) {
        return juvenile && now < adultAt;
    }

    /**
     * 标记新生后代为幼体，并保存绝对成年时刻 {@code now + growthTimeTicks}。
     *
     * @param now 当前世界绝对游戏刻
     * @param growthTimeTicks 本次出生时规则确定的成长游戏刻数；后续热更新不会重算
     */
    public void markJuvenile(long now, long growthTimeTicks) {
        this.juvenile = true;
        this.adultAt = now + growthTimeTicks;
    }

    /**
     * 立即清除幼体标记和成年截止时间。
     */
    public void growUp() {
        this.juvenile = false;
        this.adultAt = 0L;
    }

    /**
     * 返回实体出生时固定下来的绝对成年游戏刻。
     *
     * @return 成年截止游戏刻；未处于幼体阶段时可能为 {@code 0}
     */
    public long getAdultAt() {
        return adultAt;
    }

    /**
     * 返回当前时刻的固定视觉缩放系数。
     *
     * <p>成年截止时刻之前始终为 {@code 0.5F}，到达截止时刻后立即变为 {@code 1.0F}；不进行平滑插值。
     *
     * @param now 当前世界绝对游戏刻
     * @return 幼体半尺寸或成年完整尺寸
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

    /**
     * 返回当前仅存在于内存中的配偶 UUID。
     *
     * @return 当前配偶 UUID；未配对时为 {@code null}
     */
    public UUID getMate() {
        return mate;
    }

    /**
     * 设置或清除当前会话的临时配偶引用。
     *
     * <p>该字段不会由 {@link #CODEC} 保存；传入 {@code null} 表示解除配对。
     *
     * @param mate 配偶 UUID 或 {@code null}
     */
    public void setMate(UUID mate) {
        this.mate = mate;
    }

    // ---- Composite checks ----

    /**
     * 判断实体能否通过喂食进入新的 Love 时间窗。
     *
     * @param now 当前世界绝对游戏刻
     * @return 无冷却、非幼体且当前不在 Love 时返回 {@code true}
     */
    public boolean canEnterLove(long now) {
        return !isOnCooldown(now) && !isJuvenile(now) && !isInLove(now);
    }

    /**
     * 惰性结算已经到期的 Love 与幼体标记，保证后续读取一致。
     *
     * <p>Love 到期时同时解除配偶；幼体到期时只清除幼体布尔标记，绝对成年时刻仍可用于本次状态同步判断。
     *
     * @param now 当前世界绝对游戏刻
     */
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
