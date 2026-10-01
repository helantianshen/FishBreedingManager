package com.fishbreedingmanager.attachment;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.breeding.BreedingState;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * 集中注册 FBM 的实体状态附件
 *
 * <p>附件通过 {@link BreedingState#CODEC} 随实体 NBT 保存 Love、冷却和成长截止时间；临时配偶引用不持久化
 * 世界级规则由 SavedData 保存，附件只保存实体状态，规则热重载不会重新计算已经开始的计时器
 */
public final class ModAttachments {
    /** FBM Attachment 类型的延迟注册器，必须在 Mod 事件总线注册 */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, FishBreedingManager.MOD_ID);

    /**
     * 实体级繁殖运行时状态附件
     *
     * <p>默认工厂创建空的 {@link BreedingState}，并通过 {@link BreedingState#CODEC} 随实体 NBT 保存可持久化字段
     */
    public static final Supplier<AttachmentType<BreedingState>> BREEDING_STATE =
            ATTACHMENT_TYPES.register("breeding_state",
                    () -> AttachmentType.builder(BreedingState::new)
                            .serialize(BreedingState.CODEC)
                            .build());

    private ModAttachments() {
    }
}
