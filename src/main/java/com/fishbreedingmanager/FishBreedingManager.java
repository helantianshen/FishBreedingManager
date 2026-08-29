package com.fishbreedingmanager;

import com.fishbreedingmanager.attachment.ModAttachments;
import com.fishbreedingmanager.command.FBMCommands;
import com.fishbreedingmanager.network.ModNetworking;
import com.fishbreedingmanager.server.ServerLifecycleHandler;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Fish Breeding Manager（FBM）的通用逻辑入口与组成根。
 *
 * <p>FBM 为 Minecraft 1.21.1 NeoForge 提供按存档保存、可热更新的实体繁殖规则。服务端是规则、繁殖结果与实体状态
 * 的唯一权威；原版或第三方鱼类无需修改自身 Java 类即可通过事件、SavedData 和 Attachment 参与繁殖。
 *
 * <p>核心所有权分层如下：
 * <ul>
 *   <li><b>规则层：</b>属于世界存档，由 {@code WorldBreedingData} 持久化并以不可变 Snapshot 原子发布。</li>
 *   <li><b>引擎层：</b>属于逻辑服务端，由集中式 tick 控制器、交互事件和实体生命周期事件驱动。</li>
 *   <li><b>状态层：</b>属于实体，通过 {@code BreedingState} Attachment 保存 Love、冷却与成长截止时间。</li>
 * </ul>
 * 行为发生时始终查询当前 Snapshot，因此现存实体立即响应热更新；已经开始的冷却与成长计时器不会被重新计算。
 */
@Mod(FishBreedingManager.MOD_ID)
public final class FishBreedingManager {
    /** Mod 的稳定命名空间 ID，供注册表、网络类型和事件订阅统一使用。 */
    public static final String MOD_ID = "fishbreedingmanager";
    /** FBM 共用日志记录器，服务端失败路径会记录可诊断的结构化原因。 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 注册实体附件、网络数据包、管理员命令和服务器生命周期监听。
     *
     * @param modEventBus 当前 Mod 专用事件总线
     * @param modContainer NeoForge 注入的当前 Mod 容器
     */
    public FishBreedingManager(IEventBus modEventBus, ModContainer modContainer) {
        // Attachment 与网络 Payload 属于 Mod 总线注册阶段。
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(ModNetworking::register);

        // 命令与服务器生命周期属于 NeoForge 游戏总线。
        NeoForge.EVENT_BUS.register(new ServerLifecycleHandler());
        NeoForge.EVENT_BUS.addListener(FBMCommands::register);
    }
}
