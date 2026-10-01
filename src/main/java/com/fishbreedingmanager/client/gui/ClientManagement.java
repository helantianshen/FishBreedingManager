package com.fishbreedingmanager.client.gui;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;
import org.lwjgl.glfw.GLFW;
import com.fishbreedingmanager.network.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 持有当前客户端连接的管理会话、请求编号与非权威显示缓存
 *
 * <p>目录分块全部校验并收齐后才发布快照；写入超时表示结果未知，不能自动重发或宣称失败回滚
 * 关闭页面后保留在途写请求的回执识别，迟到回复只报告结果，不重新打开页面
 * 通用食物读取使用独立请求编号和有界缓存，不写入规则或编辑草稿；退出连接时清空全部会话状态
 */
public final class ClientManagement {
    private static final KeyMapping OPEN = new KeyMapping("key.fbm.open", GLFW.GLFW_KEY_K, "key.categories.fbm");
    private static final java.util.LinkedHashMap<String, ManagementData.NativeFoods> NATIVE_FOODS = new java.util.LinkedHashMap<>();
    private static String foodTarget = "";
    private static long foodRequest, foodSendAt, foodDeadline;
    private static long sequence;
    private static long readId;
    private static long writeId;
    private static long deadline;
    private static long writeDeadline;
    private static boolean active;
    private static byte[][] chunks;
    private static int received;
    private static int bytes;
    private static Consumer<ManagementData.Result> callback;
    private static ManagementData.Snapshot snapshot;
    private static boolean canEdit;
    private static boolean loading;
    private static boolean stale;
    private static boolean uncertain;
    private static String status = "gui.fbm.loading";
    private static String detail = "";
    private static String feedback = "";
    private static long feedbackUntil;
    private ClientManagement() { }

    /**
     * 注册默认使用 K 且可在原版设置中修改的按键
     *
     * @param event 生命周期事件
     */
    public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    /** 获取当前完整的权威目录副本 */
    static ManagementData.Snapshot snapshot() { return snapshot; }
    /** 当前权限状态不能替代服务端逐次验证 */
    static boolean canEdit() { return canEdit && !loading && !stale && writeId == 0; }
    /** 是否有服务端写请求尚未确认 */
    static boolean pending() { return writeId != 0; }
    /** 获取当前状态反馈文案 */
    static Component status() { return Component.translatable(status.equals("gui.fbm.ready")
            && System.currentTimeMillis() < feedbackUntil ? feedback : status); }
    /** 获取校验失败的具体信息 */
    static String detail() { return detail; }
    /** 目录正在载入时禁止把空缓存当成空搜索结果 */
    static boolean loading() { return loading; }
    /** 过期的草稿需要由用户明确重新加载 */
    static boolean stale() { return stale; }
    /** 展示权限不受请求忙碌状态影响 */
    static boolean permission() { return canEdit; }
    /** 只有正常就绪时允许使用普通提示代替网络结果 */
    static boolean ready() { return status.equals("gui.fbm.ready"); }

    /**
     * 在已进入世界且管理通道可用时打开根页面
     *
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
        if (!mc.getConnection().hasChannel(ManagementRequestPayload.TYPE)) {
            mc.player.displayClientMessage(Component.translatable("gui.fbm.protocol_missing"), false); return;
        }
        if (active) return;
        active = true; snapshot = null; canEdit = false; stale = false;
        mc.setScreen(new ManagementScreen(null, ManagementScreen.Page.MAIN, null));
        refresh();
    }

    /** 同类读取在客户端合并，重新载入不得自动重放写入 */
    static void refresh() {
        if (!active || loading) return;
        clearNativeFoods();
        readId = ++sequence; loading = true; chunks = null; bytes = received = 0;
        status = "gui.fbm.loading"; detail = ""; deadline = System.currentTimeMillis() + 15000;
        send(new ManagementData.Request(readId, 0, "read", "", null, false));
    }

    /** 只查询当前选择，短暂延迟合并快速切换，不将读取结果写入规则 */
    static ManagementData.NativeFoods nativeFoods(String entity) {
        ManagementData.NativeFoods cached = NATIVE_FOODS.get(entity);
        if (cached != null) return cached;
        if (!foodTarget.equals(entity)) {
            foodTarget = entity; foodRequest = 0; foodSendAt = System.currentTimeMillis() + 600;
        }
        return null;
    }

    private static void clearNativeFoods() {
        NATIVE_FOODS.clear(); foodTarget = ""; foodRequest = 0;
    }

    /**
     * 缓存已完成的食物读取并只刷新详情，结果数量受限且不进入规则保存路径
     */
    private static void acceptNativeFoods(ManagementData.NativeFoods foods) {
        NATIVE_FOODS.put(foods.entity(), foods);
        while (NATIVE_FOODS.size() > 32) NATIVE_FOODS.remove(NATIVE_FOODS.keySet().iterator().next());
        foodRequest = 0; foodTarget = "";
        if (Minecraft.getInstance().screen instanceof ManagementScreen screen) screen.nativeFoodsChanged();
    }

    /** 保存操作携带编辑时的修订号，不能用新目录替换旧基线 */
    static void write(String action, String entity, ManagementData.Rule rule, long revision,
                             boolean confirmed, Consumer<ManagementData.Result> done) {
        if (!canEdit()) return;
        writeId = ++sequence; callback = done; uncertain = false;
        writeDeadline = System.currentTimeMillis() + 15000;
        status = "gui.fbm.saving"; detail = "";
        try { send(new ManagementData.Request(writeId, revision, action, entity, rule, confirmed)); }
        catch (RuntimeException e) { writeId = 0; callback = null; status = "gui.fbm.too_large"; }
    }

    private static void send(ManagementData.Request request) {
        byte[] data = ManagementData.JSON.toJson(request).getBytes(StandardCharsets.UTF_8);
        if (data.length > 24576) throw new IllegalArgumentException("Request too large");
        if (Minecraft.getInstance().getConnection() != null)
            PacketDistributor.sendToServer(new ManagementRequestPayload(data));
    }

    /** X 直接退出全部页面，未发送草稿销毁，在途写入保留回执识别 */
    static void close() {
        clearNativeFoods();
        active = false; snapshot = null; chunks = null; loading = false; readId = 0;
        if (writeId == 0) send(new ManagementData.Request(++sequence, 0, "close", "", null, false));
        callback = null;
        Minecraft.getInstance().setScreen(null);
    }

    /** 断开连接后不保留任何上个世界的目录或待处理回执 */
    public static void clear() {
        clearNativeFoods();
        active = false; snapshot = null; chunks = null; callback = null;
        readId = writeId = 0; loading = stale = uncertain = canEdit = false;
    }

    /**
     * 客户端只接受当前请求分块，拒绝重复块和超限目录
     *
     * @param payload 有界网络包
     * @param context 网络处理上下文
     */
    public static void handle(ManagementReplyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> receive(payload));
    }

    /**
     * 只处理当前连接的有效请求，收齐目录前不暴露部分结果
     *
     * <p>食物查询、目录读取和写入分别核对请求编号；修订失效后清理检测缓存，保留需要用户处理的编辑草稿
     */
    private static void receive(ManagementReplyPayload payload) {
        try {
            if (payload.kind().equals("native_foods")) {
                if (!active || foodRequest == 0 || payload.request() != foodRequest) return;
                var foods = ManagementData.JSON.fromJson(new String(payload.data(), StandardCharsets.UTF_8), ManagementData.NativeFoods.class);
                if (foods == null || !foodTarget.equals(foods.entity()) || foods.items() == null || foods.items().size() > 128)
                    throw new IllegalArgumentException("Invalid native foods");
                acceptNativeFoods(foods);
                return;
            }
            if (payload.kind().equals("open")) { open(); return; }
            if (payload.kind().equals("invalidate")) {
                if (!active) return;
                var result = ManagementData.JSON.fromJson(new String(payload.data(), StandardCharsets.UTF_8), ManagementData.Result.class);
                canEdit = result.canEdit();
                if (snapshot == null || result.revision() != snapshot.revision()) {
                    clearNativeFoods();
                    stale = true; status = "gui.fbm.changed";
                    if (Minecraft.getInstance().screen instanceof ManagementScreen screen && !screen.hasDraft()) refresh();
                }
                return;
            }
            if (payload.kind().equals("result")) {
                var result = ManagementData.JSON.fromJson(new String(payload.data(), StandardCharsets.UTF_8), ManagementData.Result.class);
                if (payload.request() == writeId && writeId != 0) {
                    writeId = 0; canEdit = result.canEdit(); status = result.message(); detail = result.detail();
                    if (result.success()) { feedback = result.message(); feedbackUntil = System.currentTimeMillis() + 4000; }
                    Consumer<ManagementData.Result> done = callback; callback = null;
                    if (active) {
                        if (result.success()) refresh();
                        else if (result.message().equals("gui.fbm.conflict")) stale = true;
                        if (done != null) done.accept(result);
                    } else {
                        var player = Minecraft.getInstance().player;
                        if (player != null) player.displayClientMessage(Component.translatable(result.message()), false);
                        send(new ManagementData.Request(++sequence, 0, "close", "", null, false));
                    }
                } else if (payload.request() == readId && active) {
                    loading = false; status = result.message(); detail = result.detail(); chunks = null;
                }
                return;
            }
            if (!active || payload.request() != readId || !payload.kind().equals("snapshot")) return;
            if (payload.total() < 1 || payload.total() > 820 || payload.index() < 0 || payload.index() >= payload.total())
                throw new IllegalArgumentException("Invalid chunk index");
            if (chunks == null) chunks = new byte[payload.total()][];
            if (chunks.length != payload.total() || chunks[payload.index()] != null) throw new IllegalArgumentException("Invalid chunk sequence");
            chunks[payload.index()] = payload.data(); bytes += payload.data().length; received++;
            if (bytes > 16 * 1024 * 1024) throw new IllegalArgumentException("Directory too large");
            if (received == chunks.length) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(bytes);
                for (byte[] chunk : chunks) out.writeBytes(chunk);
                ManagementData.Snapshot next = ManagementData.JSON.fromJson(out.toString(StandardCharsets.UTF_8), ManagementData.Snapshot.class);
                snapshot = next; canEdit = next.canEdit(); loading = stale = false; chunks = null;
                status = next.operational() ? "gui.fbm.ready" : "gui.fbm.not_applied"; detail = "";
                if (uncertain) { writeId = 0; callback = null; uncertain = false; status = "gui.fbm.reconciled"; }
                if (Minecraft.getInstance().screen instanceof ManagementScreen screen) screen.dataChanged();
            }
        } catch (RuntimeException e) {
            loading = false; chunks = null; status = "gui.fbm.failed";
        }
    }

    /**
     * 按键只在游戏中触发；超时保留未知结果并提供权威重读
     *
     * @param event 生命周期事件
     */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN.consumeClick()) if (mc.screen == null) open();
        if (active && !loading && !stale && snapshot != null && !foodTarget.isEmpty()) {
            long now = System.currentTimeMillis();
            if (foodRequest == 0 && now >= foodSendAt) {
                foodRequest = ++sequence; foodDeadline = now + 15000;
                send(new ManagementData.Request(foodRequest, snapshot.revision(), "native_foods", foodTarget, null, false));
            } else if (foodRequest != 0 && now > foodDeadline) {
                acceptNativeFoods(new ManagementData.NativeFoods(foodTarget, "failed", java.util.List.of()));
            }
        }
        if (loading && System.currentTimeMillis() > deadline) { loading = false; chunks = null; status = "gui.fbm.timeout"; }
        if (writeId != 0 && !uncertain && System.currentTimeMillis() > writeDeadline) {
            uncertain = true; stale = true; status = "gui.fbm.unknown";
        }
    }
}
