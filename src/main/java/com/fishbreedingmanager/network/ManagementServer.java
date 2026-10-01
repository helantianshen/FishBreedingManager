package com.fishbreedingmanager.network;

import java.nio.charset.StandardCharsets;
import java.util.*;
import com.fishbreedingmanager.FishBreedingManager;
import com.fishbreedingmanager.breeding.*;
import com.fishbreedingmanager.discovery.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * 处理服务端管理请求，按真实玩家连接隔离队列、幂等结果与食物读取任务
 *
 * <p>入口将工作调度到服务端线程，读取不要求 OP；每次写入先校验当前权限，再检查规则修订号与参数
 * 目录按服务器和修订号缓存，分块发送有字节与速率限制；食物读取仅检查独立实体，不加入世界或写入 SavedData
 * 停止服务器或发现连接失效时释放会话引用，避免后续存档复用旧状态
 */
public final class ManagementServer {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<net.minecraft.server.MinecraftServer, ManagementData.Snapshot> DIRECTORIES = new HashMap<>();
    private static final int MAX_DIRECTORY = 16 * 1024 * 1024;
    private ManagementServer() { }

    private static final class Session {
        final ServerPlayer player;
        final ArrayDeque<ManagementReplyPayload> queue = new ArrayDeque<>();
        final LinkedHashMap<Long, ManagementData.Result> results = new LinkedHashMap<>();
        long lastRead = Long.MIN_VALUE;
        long notified = -1;
        long rateTick;
        int requests;
        boolean permission;
        long foodRequest;
        long foodRevision;
        long lastFoodRead = Long.MIN_VALUE;
        NativeFoodProbe foodProbe;
        Session(ServerPlayer player) { this.player = player; }
    }

    /**
     * 命令只通知客户端打开界面，不把服务端数据写入作为打开的副作用
     *
     * @param player 服务端玩家
     * @return 命令成功返回1，否则返回0
     */
    public static int open(ServerPlayer player) {
        if (!player.connection.hasChannel(ManagementReplyPayload.TYPE)) {
            player.sendSystemMessage(Component.translatable("gui.fbm.protocol_missing"));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, new ManagementReplyPayload(0, "open", 0, 1, new byte[0]));
        return 1;
    }

    /**
     * 管理网络处理始终在服务端合法线程中执行
     *
     * @param payload 有界网络包
     * @param context 网络处理上下文
     */
    public static void handle(ManagementRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> process(payload, (ServerPlayer) context.player()));
    }

    /**
     * 分发有界请求，读取分支不进入写权限或持久化事务路径
     *
     * <p>同一连接限制每 tick 请求数量；写请求结果按请求编号保留最近 32 项，重试命中时只返回已有结果
     */
    private static void process(ManagementRequestPayload payload, ServerPlayer player) {
        ManagementData.Request request = null;
        try {
            if (payload.data().length > 24576) return;
            request = ManagementData.JSON.fromJson(new String(payload.data(), StandardCharsets.UTF_8), ManagementData.Request.class);
            if (request == null || request.id() <= 0 || request.action() == null) return;
            if (request.action().equals("close")) {
                SESSIONS.remove(player.getUUID());
                return;
            }
            Session session = SESSIONS.get(player.getUUID());
            if (session == null || session.player != player) {
                session = new Session(player);
                SESSIONS.put(player.getUUID(), session);
            }
            long tick = player.getServer().getTickCount();
            if (session.rateTick != tick) { session.rateTick = tick; session.requests = 0; }
            if (++session.requests > 8) return;
            if (request.action().equals("read")) {
                if (!session.queue.isEmpty() || (session.lastRead != Long.MIN_VALUE && tick - session.lastRead < 10)) {
                    reply(player, request.id(), result(player, false, "busy", ""));
                    return;
                }
                session.lastRead = tick;
                ManagementData.Snapshot snapshot = snapshot(player);
                byte[] bytes = ManagementData.JSON.toJson(snapshot).getBytes(StandardCharsets.UTF_8);
                if (bytes.length > MAX_DIRECTORY) throw new IllegalArgumentException("Directory exceeds 16 MiB");
                int total = (bytes.length + 20479) / 20480;
                for (int i = 0; i < total; i++) session.queue.add(new ManagementReplyPayload(request.id(),
                        "snapshot", i, total, Arrays.copyOfRange(bytes, i * 20480, Math.min(bytes.length, (i + 1) * 20480))));
                session.notified = snapshot.revision();
                session.permission = snapshot.canEdit();
                return;
            }
            if (request.action().equals("native_foods")) {
                ResourceLocation id = ResourceLocation.tryParse(request.entity() == null ? "" : request.entity());
                if (id == null || (session.lastFoodRead != Long.MIN_VALUE && tick - session.lastFoodRead < 10)) {
                    replyFoods(player, request.id(), new ManagementData.NativeFoods(request.entity(), "busy", List.of()));
                    return;
                }
                session.lastFoodRead = tick;
                session.foodProbe = null;
                session.foodRequest = request.id();
                session.foodRevision = BreedingRuleManager.get(player.getServer()).revision();
                if (request.revision() != session.foodRevision) {
                    replyFoods(player, request.id(), new ManagementData.NativeFoods(request.entity(), "stale", List.of()));
                    return;
                }
                try {
                    var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
                    var entity = type == null ? null : type.create(player.getServer().overworld());
                    if (entity == null) throw new IllegalArgumentException("Entity unavailable");
                    session.foodProbe = new NativeFoodProbe(id.toString(), entity, BuiltInRegistries.ITEM.iterator());
                } catch (RuntimeException exception) {
                    replyFoods(player, request.id(), new ManagementData.NativeFoods(request.entity(), "failed", List.of()));
                }
                return;
            }
            ManagementData.Result remembered = session.results.get(request.id());
            if (remembered != null) { reply(player, request.id(), remembered); return; }
            ManagementData.Result result = mutate(player, request);
            session.results.put(request.id(), result);
            while (session.results.size() > 32) session.results.remove(session.results.keySet().iterator().next());
            reply(player, request.id(), result);
        } catch (RuntimeException exception) {
            FishBreedingManager.LOGGER.warn("FBM management request failed", exception);
            if (request != null) reply(player, request.id(), result(player, false, "failed", ""));
        }
    }

    /** 权限失败优先于目录扫描及持久化访问，旧修订请求不覆盖新状态 */
    static ManagementData.Result mutate(ServerPlayer player, ManagementData.Request request) {
        if (!player.hasPermissions(2)) return result(player, false, "denied", "");
        BreedingRuleManager manager = BreedingRuleManager.get(player.getServer());
        if (!manager.isInitialized()) return result(player, false, "not_ready", "");
        if (request.revision() != manager.revision()) return result(player, false, "conflict", "");
        ResourceLocation id = ResourceLocation.tryParse(request.entity() == null ? "" : request.entity());
        if (id == null) return result(player, false, "missing", "");
        CandidateEntity candidate = FishDiscoveryManager.get(player.getServer()).snapshot().candidates().get(id);
        if (candidate != null && candidate.compatibility() == CompatibilityLevel.UNSUPPORTED && !request.action().equals("remove"))
            return result(player, false, "unsupported", "");
        boolean available = BuiltInRegistries.ENTITY_TYPE.getOptional(id).isPresent();
        if (!available && !request.action().equals("remove")) return result(player, false, "missing", "");
        RuleUpdateResult update;
        switch (request.action()) {
            case "save" -> {
                ManagementData.Rule rule = request.rule();
                if (rule == null || !request.entity().equals(rule.entity()) || rule.items() == null || rule.tags() == null
                        || rule.items().size() + rule.tags().size() > 128)
                    return result(player, false, "invalid", "");
                if (rule.items().stream().anyMatch(v -> v == null || v.length() > 256)
                        || rule.tags().stream().anyMatch(v -> v == null || v.length() > 256))
                    return result(player, false, "invalid", "");
                BreedingRule current = manager.find(id);
                if (rule.enabled() && (current == null || !current.enabled())
                        && (candidate == null || candidate.compatibility() == CompatibilityLevel.UNVERIFIED)
                        && !request.confirmed()) return result(player, false, "confirm_compatibility", "");
                update = WorldBreedingService.get().upsert(player.getServer(), rule.candidate());
            }
            case "add" -> {
                ManagementData.Entity entity = entries(player).stream().filter(e -> e.id().equals(id.toString())).findFirst().orElse(null);
                if (entity == null || entity.managed()) return result(player, false, "already_added", "");
                update = WorldBreedingService.get().changeImport(player.getServer(), id, true, false);
            }
            case "remove" -> {
                if (!manager.snapshot().importedEntities().contains(id)) return result(player, false, "not_imported", "");
                if (manager.find(id) != null && !request.confirmed()) return result(player, false, "confirm_remove", "");
                update = WorldBreedingService.get().changeImport(player.getServer(), id, false, true);
            }
            default -> { return result(player, false, "invalid", ""); }
        }
        String successMessage = request.action().equals("remove") && update.success()
                && entries(player).stream().anyMatch(e -> e.id().equals(id.toString()) && e.automatic())
                ? "removed_automatic" : "success";
        return result(player, update.success(), update.success() ? successMessage : "invalid",
                update.success() ? "" : String.join("; ", update.errors()));
    }

    private static ManagementData.Result result(ServerPlayer player, boolean success, String message, String detail) {
        return new ManagementData.Result(success, "gui.fbm." + message, detail,
                BreedingRuleManager.get(player.getServer()).revision(), player.hasPermissions(2)
                        && BreedingRuleManager.get(player.getServer()).isInitialized());
    }

    private static void reply(ServerPlayer player, long request, ManagementData.Result result) {
        PacketDistributor.sendToPlayer(player, new ManagementReplyPayload(request, "result", 0, 1,
                ManagementData.JSON.toJson(result).getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 将食物读取结果限制在单个回复包内，超限时返回明确状态而非截断 JSON
     */
    private static void replyFoods(ServerPlayer player, long request, ManagementData.NativeFoods foods) {
        byte[] bytes = ManagementData.JSON.toJson(foods).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 20480) bytes = ManagementData.JSON.toJson(
                new ManagementData.NativeFoods(foods.entity(), "too_large", List.of())).getBytes(StandardCharsets.UTF_8);
        PacketDistributor.sendToPlayer(player, new ManagementReplyPayload(request, "native_foods", 0, 1, bytes));
    }

    /**
     * 合并完整 Registry、规则与导入记录，缺失实体仍保留诊断条目
     *
     * <p>自动收录资格单独从无手动导入的发现结果计算，避免把手动导入误认为自动发现
     */
    private static List<ManagementData.Entity> entries(ServerPlayer player) {
        BreedingRuleSnapshot rules = BreedingRuleManager.get(player.getServer()).managementSnapshot();
        DiscoverySnapshot discovery = FishDiscoveryManager.get(player.getServer()).snapshot();
        DiscoverySnapshot automatic = new FishDiscoveryEngine().discover(new MinecraftDiscoverySource().captureLoadedMods(),
                new MinecraftDiscoverySource().captureEntities(), Set.of());
        Map<String, ManagementData.Rule> defaults = new HashMap<>();
        DefaultRules.rules().forEach(rule -> defaults.put(rule.entityTypeId().toString(), ManagementData.Rule.from(rule)));
        Set<ResourceLocation> ids = new TreeSet<>(Comparator.comparing(Object::toString));
        ids.addAll(discovery.candidates().keySet()); ids.addAll(rules.rules().keySet()); ids.addAll(rules.importedEntities());
        List<ManagementData.Entity> result = new ArrayList<>();
        for (ResourceLocation id : ids) {
            CandidateEntity entry = discovery.candidates().get(id);
            CandidateEntity auto = automatic.candidates().get(id);
            BreedingRule rule = rules.rules().get(id);
            result.add(new ManagementData.Entity(id.toString(), "entity." + id.getNamespace() + "." + id.getPath(),
                    id.getNamespace(), entry == null ? id.getNamespace() : entry.sourceModName().getString(),
                    auto != null && auto.confidence() != CandidateConfidence.LOW, rules.importedEntities().contains(id),
                    BuiltInRegistries.ENTITY_TYPE.getOptional(id).isPresent(),
                    entry == null ? "UNVERIFIED" : entry.compatibility().name(),
                    rule == null ? null : ManagementData.Rule.from(rule), defaults.get(id.toString())));
        }
        return result;
    }

    /**
     * 目录按服务器修订号复用，当前玩家的权限与运行就绪状态在每次返回时重新计算
     */
    private static ManagementData.Snapshot snapshot(ServerPlayer player) {
        BreedingRuleManager manager = BreedingRuleManager.get(player.getServer());
        ManagementData.Snapshot cached = DIRECTORIES.get(player.getServer());
        if (cached != null && cached.revision() == manager.revision())
            return new ManagementData.Snapshot(cached.revision(), player.hasPermissions(2) && manager.isInitialized(), cached.entities(), cached.foods(), manager.isInitialized());
        List<ManagementData.Food> foods = new ArrayList<>();
        BuiltInRegistries.ITEM.keySet().stream().sorted(Comparator.comparing(Object::toString))
                .forEach(id -> foods.add(new ManagementData.Food(id.toString(), false, List.of())));
        BuiltInRegistries.ITEM.getTags().forEach(pair -> {
            List<String> members = pair.getSecond().stream().map(holder ->
                    BuiltInRegistries.ITEM.getKey(holder.value()).toString()).toList();
            if (!members.isEmpty()) foods.add(new ManagementData.Food(pair.getFirst().location().toString(), true, members));
        });
        ManagementData.Snapshot next = new ManagementData.Snapshot(manager.revision(), player.hasPermissions(2) && manager.isInitialized(), entries(player), foods, manager.isInitialized());
        DIRECTORIES.put(player.getServer(), next);
        return next;
    }

    /**
     * 每 tick 推进有界目录发送与食物查询，并检测修订号和权限变化；不扫描世界实体
     *
     * @param event 生命周期事件
     */
    public static void tick(ServerTickEvent.Post event) {
        var iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (session.player.getServer() != event.getServer()) continue;
            if (event.getServer().getPlayerList().getPlayer(session.player.getUUID()) != session.player) {
                iterator.remove(); continue;
            }
            if (session.foodProbe != null) {
                if (session.foodRevision != BreedingRuleManager.get(event.getServer()).revision()) {
                    replyFoods(session.player, session.foodRequest,
                            new ManagementData.NativeFoods(session.foodProbe.result().entity(), "stale", List.of()));
                    session.foodProbe = null;
                } else {
                    session.foodProbe.step(64);
                    if (session.foodProbe.done()) {
                        replyFoods(session.player, session.foodRequest, session.foodProbe.result());
                        session.foodProbe = null;
                    }
                }
            }
            for (int i = 0; i < 4 && !session.queue.isEmpty(); i++)
                PacketDistributor.sendToPlayer(session.player, session.queue.remove());
            long revision = BreedingRuleManager.get(event.getServer()).revision();
            boolean permission = session.player.hasPermissions(2) && BreedingRuleManager.get(event.getServer()).isInitialized();
            if (session.queue.isEmpty() && (revision != session.notified || permission != session.permission)) {
                session.notified = revision; session.permission = permission;
                PacketDistributor.sendToPlayer(session.player, new ManagementReplyPayload(0, "invalidate", 0, 1,
                        ManagementData.JSON.toJson(result(session.player, false, "changed", "")).getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    /**
     * 服务器停止时清除持有玩家引用的管理会话
     *
     * @param event 生命周期事件
     */
    public static void stop(ServerStoppingEvent event) {
        SESSIONS.values().removeIf(session -> session.player.getServer() == event.getServer());
        DIRECTORIES.remove(event.getServer());
    }
}
