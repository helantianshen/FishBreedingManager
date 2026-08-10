package com.fishbreedingmanager.attachment;

import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.breeding.BreedingState;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * FBM 所用 NeoForge {@link AttachmentType} 的集中注册入口。
 *
 * <p>Attachment 存储实体级运行时 {@code BreedingState}, Rule vs State 分离, 规则属世界 状态属实体 
 * 提供了序列化器 codec, 故实体 attachment 随实体 NBT 自动持久化, 
 * 冷却/成长计时器跨 reload 保留且不重算 需求§38/§39 
 *
 * <p>注意, NeoForge 1.21.1 中 attachment 仅支持 block entity chunk 和实体, 
 * <b>不支持</b> level, level 级数据 规则本身 改用 {@code SavedData} 存储 
 */
public final class ModAttachments {
    /** FBM Attachment 类型的延迟注册器，必须在 Mod 事件总线注册。 */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, FishBreedingManager.MOD_ID);

    /**
     * 实体级繁殖运行时状态附件。
     *
     * <p>默认工厂创建空的 {@link BreedingState}，并通过 {@link BreedingState#CODEC} 随实体 NBT 保存可持久化字段。
     */
    public static final Supplier<AttachmentType<BreedingState>> BREEDING_STATE =
            ATTACHMENT_TYPES.register("breeding_state",
                    () -> AttachmentType.builder(BreedingState::new)
                            .serialize(BreedingState.CODEC)
                            .build());

    private ModAttachments() {
    }
}
