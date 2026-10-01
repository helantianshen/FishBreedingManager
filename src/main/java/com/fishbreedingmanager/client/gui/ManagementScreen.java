package com.fishbreedingmanager.client.gui;

import java.util.*;
import java.util.function.Consumer;
import com.fishbreedingmanager.network.ManagementData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * 管理页面持有当前筛选、单实体选择和未提交草稿，服务端回复决定实际规则状态
 *
 * <p>父页面对象用于恢复来源、搜索与滚动位置；返回脏编辑页的父页前确认保存或放弃，关闭整个 GUI 则丢弃未发送草稿
 * 列表行可以容纳多个卡片，但实体选中状态只使用稳定 Registry ID，不把整行焦点当作多选
 * 背景在控件之前完成模糊与底板绘制，来源菜单使用独立深度层和优先鼠标命中处理
 */
final class ManagementScreen extends Screen {
    /** 主页面与可返回的子页面类型 */
    enum Page { MAIN, REGISTRY, EDITOR, PICKER, CONFIRM }
    private final ManagementScreen parent;
    private final Page page;
    private final String entityId;
    private final EntityPreview preview = new EntityPreview();
    private final List<AbstractWidget> writeWidgets = new ArrayList<>();
    private final List<AbstractWidget> formWidgets = new ArrayList<>();
    private final List<Button> categoryButtons = new ArrayList<>();
    private final List<ManagementData.Food> draftFoods = new ArrayList<>();
    private int left, top, panelWidth, panelHeight, bodyTop, bodyBottom, contentLeft, detailLeft;
    private String query = "", source = "", selectedId = "";
    private int category;
    private boolean onlyNew, sourceExpanded, enabled, initializedDraft, tagsOnly, reloadDraft;
    private long baseline;
    private ManagementData.Rule original;
    private TimeValue cooldown = new TimeValue(600), growth = new TimeValue(1200);
    private Rows rows, details, sourceChoices;
    private EditBox search;
    private Button primary, remove, save, defaults, onlyNewButton, sourceButton;
    private double scroll, detailScroll;
    private int formOffset;
    private String selectedFood = "", replaceFood;
    private Consumer<ManagementData.Food> chooseFood;
    private Component question = Component.empty();
    private Runnable confirmAction, discardAction;
    private boolean confirmRequiresWrite;
    private String confirmLabel = "confirm";

    /** 只有根页面没有父级，编辑目标始终使用稳定 Registry ID */
    ManagementScreen(ManagementScreen parent, Page page, String entityId) {
        super(tr(page == Page.MAIN ? "title" : page == Page.REGISTRY ? "add_title" : page == Page.EDITOR ? "edit_title" : "select"));
        this.parent = parent; this.page = page; this.entityId = entityId;
    }

    private static Component tr(String key, Object... args) { return Component.translatable("gui.fbm." + key, args); }
    private ManagementData.Snapshot data() { return ClientManagement.snapshot(); }
    private ManagementData.Entity entity(String id) {
        return data() == null ? null : data().entities().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }
    private String name(ManagementData.Entity e) { return I18n.exists(e.translation()) ? I18n.get(e.translation()) : e.id(); }
    private Component state(ManagementData.Entity e) { return tr(data() != null && !data().operational() && e.rule() != null ? "not_applied" : e.rule() == null ? "unconfigured" : e.rule().enabled() ? "enabled" : "disabled"); }

    @Override protected void init() {
        writeWidgets.clear(); formWidgets.clear(); categoryButtons.clear(); primary = remove = save = defaults = onlyNewButton = sourceButton = null;
        rows = details = sourceChoices = null;
        panelWidth = Math.min(760, width - 12); panelHeight = Math.min(410, height - 12);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        bodyTop = top + 30; bodyBottom = top + panelHeight - 48;
        Button back = button("<", left + 6, top + 5, 20, this::back, false);
        back.active = parent != null;
        back.setTooltip(Tooltip.create(tr(parent == null ? "root" : "back")));
        button("X", left + panelWidth - 26, top + 5, 20, this::onClose, false).setTooltip(Tooltip.create(tr("close")));
        if (page != Page.CONFIRM) button(tr("refresh"), left + panelWidth - 86, top + 5, 54, this::refresh, false);
        if (page == Page.EDITOR) initEditor();
        else if (page == Page.PICKER) initPicker();
        else if (page == Page.CONFIRM) initConfirm();
        else initBrowser();
        updateActions();
    }

    private Button button(String text, int x, int y, int w, Runnable action, boolean write) {
        return button(Component.literal(text), x, y, w, action, write);
    }
    private Button button(Component text, int x, int y, int w, Runnable action, boolean write) {
        Button b = addRenderableWidget(Button.builder(text, ignored -> action.run()).bounds(x, y, Math.max(16, w), 20).build());
        if (write) { writeWidgets.add(b); b.setTooltip(Tooltip.create(tr("requires_op"))); }
        return b;
    }
    private EditBox field(int x, int y, int w, String value, Consumer<String> changed) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, Math.max(20, w), 18, tr("input")));
        box.setMaxLength(256); box.setValue(value); box.setResponder(changed); return box;
    }

    private void initBrowser() {
        boolean main = page == Page.MAIN;
        int sidebar = main ? Math.min(78, panelWidth / 6) : 0;
        int detailWidth = Math.max(108, panelWidth / 4);
        detailLeft = left + panelWidth - detailWidth - 6;
        contentLeft = left + 6 + sidebar;
        if (main) {
            String[] labels = {"all", "vanilla", "mods"};
            for (int i = 0; i < labels.length; i++) {
                int index = i;
                Button b = button(tr(labels[i]), left + 6, bodyTop + i * 24, sidebar - 6,
                        () -> { category = index; filter(); }, false);
                b.setTooltip(Tooltip.create(tr(labels[i])));
                categoryButtons.add(b);
            }
            button(tr("add_title"), left + 6, bodyBottom - 20, sidebar - 6,
                    () -> show(new ManagementScreen(this, Page.REGISTRY, null)), false);
        }
        search = field(contentLeft, bodyTop, detailLeft - contentLeft - 8, query, value -> { query = value; filter(); });
        search.setHint(tr("search"));
        int listTop = bodyTop + 24;
        if (!main) {
            sourceButton = button(tr("source", source.isEmpty() ? tr("all").getString() : source), contentLeft, listTop,
                    Math.max(90, (detailLeft - contentLeft) / 2), () -> { sourceExpanded = !sourceExpanded; rebuild(); }, false);
            onlyNewButton = button(tr(onlyNew ? "only_new_on" : "only_new_off"), contentLeft + (detailLeft - contentLeft) / 2 + 4,
                    listTop, (detailLeft - contentLeft) / 2 - 12, () -> { onlyNew = !onlyNew; filter(); }, false);
            listTop += 38;
        }
        rows = addRenderableWidget(new Rows(contentLeft, listTop, detailLeft - contentLeft - 8,
                Math.max(20, bodyBottom - listTop), main ? 84 : 32));
        populateEntities(); rows.setScrollAmount(scroll);
        int previewHeight = bodyBottom - bodyTop > 190 ? 82 : 0;
        details = addRenderableWidget(new Rows(detailLeft, bodyTop + previewHeight + 2, detailWidth,
                Math.max(20, bodyBottom - bodyTop - previewHeight - 26), 16));
        populateDetails(); details.setScrollAmount(detailScroll);
        primary = button(tr("view"), detailLeft, bodyBottom + 3, detailWidth,
                this::entityAction, false);
        remove = button(tr("remove_import"), contentLeft, bodyBottom + 3, Math.min(150, detailLeft - contentLeft - 8),
                this::removeImport, true);
        if (sourceExpanded) {
            List<String> sources = data() == null ? List.of() : data().entities().stream()
                    .map(ManagementData.Entity::source).distinct().sorted().toList();
            int menuHeight = Math.min(112, (sources.size() + 1) * 20 + 8);
            sourceChoices = addWidget(new Rows(contentLeft, bodyTop + 46,
                    Math.max(90, (detailLeft - contentLeft) / 2), Math.min(menuHeight, bodyBottom - bodyTop - 46), 20));
            sourceChoices.addSourceChoice(tr("all"), "");
            sources.forEach(value -> sourceChoices.addSourceChoice(Component.literal(value), value));
            setFocused(sourceChoices);
        }
    }

    private void selectSource(String value) { source = value; sourceExpanded = false; scroll = 0; if (rows != null) rows.setScrollAmount(0); rebuild(); }
    private void filter() { scroll = 0; if (rows != null) { populateEntities(); rows.setScrollAmount(0); } populateDetails(); updateActions(); }
    private boolean matches(String name, String id) {
        String term = query.strip().toLowerCase(Locale.ROOT);
        return name.toLowerCase(Locale.ROOT).contains(term) || id.toLowerCase(Locale.ROOT).contains(term);
    }
    /**
     * 将组合筛选后的实体按行布局；选中实体被过滤掉时清除选择，不自动选中其他实体
     */
    private void populateEntities() {
        if (rows == null || data() == null) return;
        rows.clear();
        List<ManagementData.Entity> filtered = data().entities().stream()
                .filter(e -> page != Page.MAIN || e.managed())
                .filter(e -> page != Page.MAIN || category == 0 || (category == 1) == e.source().equals("minecraft"))
                .filter(e -> source.isEmpty() || e.source().equals(source))
                .filter(e -> !onlyNew || !e.managed()).filter(e -> matches(name(e), e.id())).toList();
        if (filtered.stream().noneMatch(e -> e.id().equals(selectedId))) selectedId = "";
        int columns = page == Page.MAIN ? Math.max(1, rows.getWidth() / 106) : 1;
        for (int i = 0; i < filtered.size(); i += columns)
            rows.addEntities(filtered.subList(i, Math.min(filtered.size(), i + columns)), page == Page.MAIN, columns);
    }
    /**
     * 详情使用服务端快照，并将原有食物检测与 FBM 配置分开显示，不把检测结果转换为规则
     */
    private void populateDetails() {
        if (details == null) return;
        details.clear();
        ManagementData.Entity e = entity(selectedId);
        if (e == null) { details.addText(tr("select_entity")); return; }
        details.addText(Component.literal(name(e))); details.addText(Component.literal(e.id()));
        details.addText(tr("source", e.sourceName())); details.addText(state(e));
        details.addText(tr(e.available() ? (e.managed() ? "added" : "not_added") : "missing"));
        if (e.automatic()) details.addText(tr("automatic"));
        if (e.imported()) details.addText(tr("manual"));
        if (e.rule() != null) {
            details.addText(tr("fbm_foods"));
            details.addText(tr("cooldown_value", new TimeValue(e.rule().cooldown()).text()));
            details.addText(tr("growth_value", new TimeValue(e.rule().growth()).text()));
            e.rule().items().forEach(id -> details.addFood(new ManagementData.Food(id, false, List.of()), false));
            e.rule().tags().forEach(id -> details.addFood(new ManagementData.Food(id, true, List.of()), false));
        }
        if (e.available()) {
            details.addText(tr("native_foods"));
            ManagementData.NativeFoods foods = ClientManagement.nativeFoods(e.id());
            if (foods == null) details.addText(tr("loading"));
            else {
                if (!foods.status().equals("ready")) details.addText(tr("native_" + foods.status()));
                else if (foods.items().isEmpty()) details.addText(tr("native_empty"));
                foods.items().forEach(id -> details.addFood(new ManagementData.Food(id, false, List.of()), false));
                if (foods.status().equals("ready") || foods.status().equals("partial")) details.addText(tr("native_scope"));
            }
        }
        details.setScrollAmount(0);
    }

    /** 食物读取结果只刷新详情，不改变当前筛选、选择或编辑草稿 */
    void nativeFoodsChanged() {
        double offset = details == null ? 0 : details.getScrollAmount();
        populateDetails();
        if (details != null) details.setScrollAmount(offset);
    }

    private void entityAction() {
        ManagementData.Entity e = entity(selectedId);
        if (e == null) return;
        if (e.managed()) show(new ManagementScreen(this, Page.EDITOR, e.id()));
        else ClientManagement.write("add", e.id(), null, data().revision(), false, ignored -> { });
    }
    private void removeImport() {
        ManagementData.Entity e = entity(selectedId);
        if (e == null) return;
        Runnable action = () -> ClientManagement.write("remove", e.id(), null, data().revision(), true, result -> {
            if (result.success()) minecraft.setScreen(this);
        });
        if (e.rule() == null) action.run();
        else confirm(tr("remove_warning", name(e), e.id()), "remove_confirm", action, null, true);
    }

    /**
     * 进入编辑时记录规则与修订号；无规则时仅创建禁用草稿，默认数值不写入服务端
     */
    private void loadDraft() {
        ManagementData.Entity e = entity(entityId);
        if (e == null) return;
        original = e.rule(); baseline = data().revision();
        setDraft(original == null ? new ManagementData.Rule(entityId, List.of(), List.of(), 600, 1200, false) : original);
        initializedDraft = true;
    }
    private void setDraft(ManagementData.Rule rule) {
        enabled = rule.enabled(); cooldown = new TimeValue(rule.cooldown()); growth = new TimeValue(rule.growth());
        draftFoods.clear();
        rule.items().forEach(id -> draftFoods.add(new ManagementData.Food(id, false, List.of())));
        rule.tags().forEach(id -> draftFoods.add(new ManagementData.Food(id, true, List.of())));
    }
    private ManagementData.Rule draft() {
        return new ManagementData.Rule(entityId, draftFoods.stream().filter(f -> !f.tag()).map(ManagementData.Food::id).toList(),
                draftFoods.stream().filter(ManagementData.Food::tag).map(ManagementData.Food::id).toList(),
                cooldown.ticks(), growth.ticks(), enabled);
    }
    private boolean dirty() {
        if (!initializedDraft) return false;
        try {
            if (original != null) return !draft().equals(original);
            return enabled || !draftFoods.isEmpty() || cooldown.ticks() != 600 || growth.ticks() != 1200;
        } catch (RuntimeException e) { return true; }
    }
    private boolean valid() {
        try {
            draft();
            return !draftFoods.isEmpty() && draftFoods.size() <= 128 && data() != null
                    && draftFoods.stream().allMatch(f -> data().foods().stream().anyMatch(known -> foodKey(f).equals(foodKey(known))));
        } catch (RuntimeException e) { return false; }
    }

    private void initEditor() {
        if (!initializedDraft) loadDraft();
        int previewWidth = panelWidth >= 500 ? panelWidth / 4 : 0;
        contentLeft = left + 8 + previewWidth;
        int formWidth = panelWidth - previewWidth - 22;
        int y = bodyTop - formOffset;
        Button enable = button(tr(enabled ? "enable_on" : "enable_off"), contentLeft, y, formWidth,
                () -> { enabled = !enabled; rebuild(); }, true); formWidgets.add(enable);
        rows = addRenderableWidget(new Rows(contentLeft, y + 34, formWidth, 64, 22));
        for (ManagementData.Food f : draftFoods) rows.addFood(f, true);
        rows.setScrollAmount(scroll); formWidgets.add(rows);
        formWidgets.add(button(tr("add_item"), contentLeft, y + 102, formWidth / 2 - 3, () -> picker(false, null), true));
        formWidgets.add(button(tr("add_tag"), contentLeft + formWidth / 2 + 3, y + 102, formWidth / 2 - 3,
                () -> picker(true, null), true));
        initTime(cooldown, contentLeft, y + 140, formWidth, "cooldown");
        initTime(growth, contentLeft, y + 180, formWidth, "growth");
        defaults = button(tr("defaults"), left + 8, bodyBottom + 3, Math.min(100, panelWidth / 3), () -> {
            ManagementData.Entity e = entity(entityId);
            if (e != null && e.defaults() != null) { setDraft(e.defaults()); rebuild(); }
        }, true);
        save = button(tr("save"), left + panelWidth - Math.min(130, panelWidth / 2) - 8, bodyBottom + 3,
                Math.min(130, panelWidth / 2), () -> save(false), true);
        for (AbstractWidget widget : formWidgets)
            widget.visible = widget.getY() >= bodyTop && widget.getY() + widget.getHeight() <= bodyBottom;
    }
    private void initTime(TimeValue value, int x, int y, int w, String label) {
        EditBox input = field(x + Math.min(84, w / 3), y, w - Math.min(84, w / 3) - 65, value.text(), value::edit);
        input.setMaxLength(32); input.setTooltip(Tooltip.create(tr(label)));
        writeWidgets.add(input); formWidgets.add(input);
        Button unit = button(tr(value.unit() == 0 ? "seconds" : "minutes"), x + w - 60, y, 60, () -> {
            try { value.switchUnit(); rebuild(); } catch (RuntimeException ignored) { input.setTextColor(0xffff5555); }
        }, true); formWidgets.add(unit);
    }
    private boolean canModifyDraft() {
        ManagementData.Entity e = entity(entityId);
        return ClientManagement.canEdit() && e != null && e.available() && !e.compatibility().equals("UNSUPPORTED");
    }
    private void picker(boolean tag, ManagementData.Food replace) {
        if (!canModifyDraft()) return;
        ManagementScreen screen = new ManagementScreen(this, Page.PICKER, entityId);
        screen.tagsOnly = tag; screen.replaceFood = replace == null ? null : foodKey(replace);
        screen.selectedFood = screen.replaceFood == null ? "" : screen.replaceFood;
        screen.chooseFood = food -> {
            if (screen.replaceFood != null) draftFoods.removeIf(f -> foodKey(f).equals(screen.replaceFood));
            if (draftFoods.stream().noneMatch(f -> foodKey(f).equals(foodKey(food)))) draftFoods.add(food);
            minecraft.setScreen(this);
        };
        show(screen);
    }
    private void save(boolean confirmed) {
        if (!valid() || !ClientManagement.canEdit()) return;
        ManagementData.Entity e = entity(entityId);
        if (!confirmed && enabled && (original == null || !original.enabled())
                && (e == null || e.compatibility().equals("UNVERIFIED"))) {
            confirm(tr("compat_warning"), "save", () -> save(true), null, true); return;
        }
        ClientManagement.write("save", entityId, draft(), baseline, confirmed, result -> {
            if (result.success()) minecraft.setScreen(parent);
            else minecraft.setScreen(this);
        });
    }

    private void initPicker() {
        contentLeft = left + 8; detailLeft = left + panelWidth * 2 / 3;
        search = field(contentLeft, bodyTop, panelWidth - 16, query, value -> { query = value; populateFoods(); });
        search.setHint(tr(tagsOnly ? "search_tag" : "search_item"));
        rows = addRenderableWidget(new Rows(contentLeft, bodyTop + 24, detailLeft - contentLeft - 6,
                Math.max(20, bodyBottom - bodyTop - 24), 26));
        details = addRenderableWidget(new Rows(detailLeft, bodyTop + 24, left + panelWidth - detailLeft - 8,
                Math.max(20, bodyBottom - bodyTop - 24), 20));
        populateFoods(); rows.setScrollAmount(scroll); foodDetails();
        primary = button(tr("select_confirm"), left + panelWidth - 138, bodyBottom + 3, 130, () -> {
            ManagementData.Food selected = selectedFood();
            if (selected != null && selected.tag() == tagsOnly && ClientManagement.canEdit()) chooseFood.accept(selected);
        }, true);
    }
    private void populateFoods() {
        if (rows == null || data() == null) return;
        rows.clear();
        data().foods().stream().filter(f -> f.tag() == tagsOnly).filter(f -> matches(foodName(f), foodKey(f)))
                .sorted(Comparator.comparing(ManagementScreen::foodKey)).forEach(f -> rows.addFood(f, false));
        rows.setScrollAmount(0);
    }
    private ManagementData.Food selectedFood() {
        return data() == null ? null : data().foods().stream().filter(f -> foodKey(f).equals(selectedFood)).findFirst().orElse(null);
    }
    private void foodDetails() {
        if (details == null) return;
        details.clear(); ManagementData.Food f = selectedFood();
        if (f == null) return;
        details.addText(Component.literal(foodKey(f)));
        if (f.tag()) {
            details.addText(tr("members", f.members().size()));
            f.members().forEach(id -> details.addFood(new ManagementData.Food(id, false, List.of()), false));
        } else details.addFood(f, false);
    }
    private static String foodKey(ManagementData.Food f) { return (f.tag() ? "#" : "") + f.id(); }
    private static ItemStack stack(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }
    private static String foodName(ManagementData.Food f) { return f.tag() ? foodKey(f) : stack(f.id()).getHoverName().getString(); }

    private void confirm(Component text, String label, Runnable yes, Runnable discard, boolean write) {
        ManagementScreen screen = new ManagementScreen(this, Page.CONFIRM, entityId);
        screen.question = text; screen.confirmAction = yes; screen.discardAction = discard;
        screen.confirmRequiresWrite = write; screen.confirmLabel = label; show(screen);
    }
    private void initConfirm() {
        int count = discardAction == null ? 2 : 3;
        int w = (panelWidth - 24) / count;
        primary = button(tr(confirmLabel), left + 8, bodyBottom + 3, w - 4, confirmAction, confirmRequiresWrite);
        if (discardAction != null) button(tr("discard"), left + 8 + w, bodyBottom + 3, w - 4, discardAction, false);
        button(tr("cancel"), left + 8 + (count - 1) * w, bodyBottom + 3, w - 4, () -> minecraft.setScreen(parent), false);
        rows = addRenderableWidget(new Rows(left + 10, bodyTop, panelWidth - 20, Math.max(24, bodyBottom - bodyTop), 14));
        rows.clear();
        String text = question.getString();
        while (!text.isEmpty()) {
            String line = font.plainSubstrByWidth(text, panelWidth - 40);
            if (line.isEmpty()) break;
            rows.addText(Component.literal(line)); text = text.substring(line.length());
        }
    }

    private void show(ManagementScreen next) { rememberScroll(); minecraft.setScreen(next); }
    private void rememberScroll() { if (rows != null) scroll = rows.getScrollAmount(); if (details != null) detailScroll = details.getScrollAmount(); }
    /**
     * 重建控件前保存滚动位置，页面字段继续持有草稿与筛选状态
     */
    private void rebuild() { rememberScroll(); rebuildWidgets(); }
    private void back() {
        if (parent == null) return;
        if (page == Page.EDITOR && dirty()) {
            confirm(tr("unsaved_question"), "save_back", () -> save(false), () -> minecraft.setScreen(parent), true);
        } else minecraft.setScreen(parent);
    }
    private void refresh() {
        if (hasDraft() && dirtyEditor() != null) {
            ManagementScreen editor = dirtyEditor();
            confirm(tr("reload_question"), "refresh", () -> {
                editor.reloadDraft = true; minecraft.setScreen(editor); ClientManagement.refresh();
            }, null, false);
        } else { if (page == Page.EDITOR) reloadDraft = true; ClientManagement.refresh(); }
    }
    private ManagementScreen dirtyEditor() {
        if (page == Page.EDITOR && dirty()) return this;
        return parent == null ? null : parent.dirtyEditor();
    }
    /** 编辑页及其子页面保持旧基线，外部更新不能覆盖草稿 */
    boolean hasDraft() { return page == Page.EDITOR || parent != null && parent.hasDraft(); }
    /** 新目录发布时仅显式重载请求可以替换正在编辑的草稿 */
    void dataChanged() {
        if (parent != null) parent.dataChanged();
        if (reloadDraft || page == Page.EDITOR && !dirty()) { initializedDraft = false; reloadDraft = false; }
        rebuild();
    }
    @Override public void onClose() { ClientManagement.close(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { updateActions(); }
    /**
     * 依据当前连接状态更新控件可用性，客户端禁用仅用于交互反馈，不能替代服务端逐次校验
     */
    private void updateActions() {
        boolean writable = ClientManagement.canEdit();
        if (onlyNewButton != null) onlyNewButton.setMessage(tr(onlyNew ? "only_new_on" : "only_new_off"));
        for (AbstractWidget widget : writeWidgets) {
            widget.active = writable;
            if (widget instanceof EditBox box) box.setEditable(writable);
            widget.setTooltip(writable ? null : Tooltip.create(tr(ClientManagement.stale() ? "changed" : "requires_op")));
        }
        if (page == Page.MAIN || page == Page.REGISTRY) {
            ManagementData.Entity e = entity(selectedId);
            if (primary != null) {
                primary.active = e != null && (e.managed() || writable && e.available() && !e.compatibility().equals("UNSUPPORTED"));
                primary.setMessage(tr(e != null && !e.managed() ? "add" : writable && e != null && e.available()
                        && !e.compatibility().equals("UNSUPPORTED") ? "edit" : "view"));
                if (!primary.active) primary.setTooltip(Tooltip.create(tr(e == null ? "select_entity" : "requires_op")));
                else primary.setTooltip(null);
            }
            if (remove != null) { remove.visible = e != null && e.imported(); remove.active = writable && remove.visible; }
        } else if (page == Page.EDITOR) {
            ManagementData.Entity e = entity(entityId);
            boolean supported = e != null && e.available() && !e.compatibility().equals("UNSUPPORTED");
            for (AbstractWidget widget : writeWidgets) {
                widget.active &= supported;
                if (widget instanceof EditBox box) box.setEditable(writable && supported);
                if (!supported) widget.setTooltip(Tooltip.create(tr(e == null || !e.available() ? "missing" : "unsupported")));
            }
            if (save != null) { save.active = writable && supported && valid() && (dirty() || original == null);
                save.setTooltip(save.active ? null : Tooltip.create(tr(!writable ? "requires_op" : !valid() ? "invalid" : "unchanged"))); }
            if (defaults != null) { defaults.active = writable && supported && e.defaults() != null;
                defaults.setTooltip(defaults.active ? null : Tooltip.create(tr(!writable ? "requires_op" : "no_defaults"))); }
        } else if (page == Page.PICKER && primary != null) primary.active = writable && selectedFood() != null;
        else if (page == Page.CONFIRM && primary != null && confirmRequiresWrite) {
            primary.active = writable && (discardAction == null || parent.valid());
        }
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && sourceExpanded) { sourceExpanded = false; rebuild(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (sourceChoices != null) {
            Rows choices = sourceChoices;
            if (choices.isMouseOver(x, y)) {
                boolean handled = choices.mouseClicked(x, y, button);
                if (handled && sourceChoices == choices) {
                    setFocused(choices);
                    if (button == 0) setDragging(true);
                }
                return true;
            }
            boolean onSourceButton = sourceButton != null && sourceButton.isMouseOver(x, y);
            sourceExpanded = false;
            rebuild();
            if (onSourceButton) return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (sourceChoices != null && sourceChoices.isMouseOver(x, y)) return sourceChoices.mouseScrolled(x, y, horizontal, vertical);
        if (page == Page.EDITOR && y >= bodyTop && y < bodyBottom && (rows == null || !rows.visible || !rows.isMouseOver(x, y))) {
            formOffset = Math.max(0, Math.min(Math.max(0, 222 - (bodyBottom - bodyTop)), formOffset - (int) (vertical * 20)));
            rebuild(); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xffc6c6c6);
        g.renderOutline(left, top, panelWidth, panelHeight, 0xff373737);
        g.hLine(left + 1, left + panelWidth - 2, top + 1, 0xffffffff);
        g.vLine(left + 1, top + 1, top + panelHeight - 2, 0xffffffff);
        Component heading = page == Page.EDITOR ? tr(ClientManagement.permission() ? "edit_title" : "rule_title") : title;
        g.drawString(font, font.plainSubstrByWidth(heading.getString(), panelWidth - 130), left + 34, top + 11, 0xff202020, false);
        if (page == Page.MAIN || page == Page.REGISTRY) {
            ManagementData.Entity e = entity(selectedId);
            if (e != null && bodyBottom - bodyTop > 190) drawPreview(g, e.id(), detailLeft, bodyTop, left + panelWidth - detailLeft - 6, 80);
            if (page == Page.REGISTRY && rows != null) {
                g.drawString(font, tr("entity_column"), contentLeft + 4, rows.getY() - 11, 0xff333333, false);
                g.drawString(font, tr("state_column"), detailLeft - 73, rows.getY() - 11, 0xff333333, false);
            }
        } else if (page == Page.EDITOR) {
            if (panelWidth >= 500) {
                drawPreview(g, entityId, left + 8, bodyTop, contentLeft - left - 16, Math.min(110, bodyBottom - bodyTop - 28));
                g.drawString(font, font.plainSubstrByWidth(entityId, contentLeft - left - 16), left + 8, bodyBottom - 12, 0xff333333, false);
            }
            g.enableScissor(contentLeft, bodyTop, left + panelWidth - 6, bodyBottom);
            int y = bodyTop - formOffset;
            g.drawString(font, tr("foods"), contentLeft, y + 24, 0xff222222, false);
            g.drawString(font, tr("cooldown"), contentLeft, y + 145, 0xff222222, false);
            g.drawString(font, tr("growth"), contentLeft, y + 185, 0xff222222, false);
            timeHint(g, cooldown, contentLeft, y + 162);
            timeHint(g, growth, contentLeft, y + 202);
            g.disableScissor();
            if (bodyBottom - bodyTop < 222) g.drawString(font, "↕", left + panelWidth - 12, bodyTop, 0xff333333, false);
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if ((page == Page.MAIN || page == Page.REGISTRY) && rows != null && rows.children().isEmpty()) {
            g.enableScissor(rows.getX(), rows.getY(), rows.getRight(), rows.getBottom());
            Component message = tr(ClientManagement.loading() ? "loading" : "empty");
            g.drawCenteredString(font, message, rows.getX() + rows.getWidth() / 2,
                    rows.getY() + (rows.getHeight() - font.lineHeight) / 2, 0xffeeeeee);
            g.disableScissor();
        }
        if (page == Page.MAIN && category < categoryButtons.size()) {
            Button selectedCategory = categoryButtons.get(category);
            g.renderOutline(selectedCategory.getX(), selectedCategory.getY(),
                    selectedCategory.getWidth(), selectedCategory.getHeight(), 0xffffffff);
        }
        if (sourceChoices != null) {
            // 下拉菜单位于实体预览的深度层之上，且不参与底层控件的绘制顺序
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            sourceChoices.render(g, mouseX, mouseY, partialTick);
            g.flush();
            g.pose().popPose();
        }
        Component status = ClientManagement.status();
        if (page == Page.EDITOR && ClientManagement.ready()) status = dirty() ? tr("unsaved")
                : ClientManagement.permission() ? tr("ready") : tr("requires_op");
        g.drawString(font, font.plainSubstrByWidth(status.getString(), panelWidth - 16), left + 8,
                top + panelHeight - 13, 0xff333333, false);
        if (mouseY >= top + panelHeight - 16 && !ClientManagement.detail().isEmpty())
            g.renderTooltip(font, Component.literal(ClientManagement.detail()), mouseX, mouseY);
    }
    private void timeHint(GuiGraphics g, TimeValue value, int x, int y) {
        try { value.ticks(); if (value.approximate()) g.drawString(font, tr("approximate"), x, y, 0xff555555, false); }
        catch (RuntimeException e) { g.drawString(font, tr("invalid_time"), x, y, 0xffaa0000, false); }
    }
    private void drawPreview(GuiGraphics g, String id, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xff353535);
        if (!preview.render(g, id, x, y, w, h)) g.drawCenteredString(font, tr("no_preview"), x + w / 2, y + h / 2, 0xffaaaaaa);
    }

    /** 原版列表提供滚轮、拖动滚动条、焦点和键盘选择 */
    private final class Rows extends ObjectSelectionList<Row> {
        Rows(int x, int y, int w, int h, int rowHeight) { super(Minecraft.getInstance(), w, h, y, rowHeight); setX(x); }
        void clear() { clearEntries(); }
        void addText(Component text) { addEntry(new Row(text, null, null, null, false, 1)); }
        void addSourceChoice(Component text, String value) {
            Row row = new Row(text, () -> selectSource(value), null, null, false, 1);
            row.sourceValue = value;
            addEntry(row);
        }
        void addFood(ManagementData.Food food, boolean editable) { addEntry(new Row(null, null, food, null, editable, 1)); }
        void addEntities(List<ManagementData.Entity> entities, boolean grid, int columns) { addEntry(new Row(null, null, null, entities, grid, columns)); }
        @Override public int getRowWidth() { return getWidth() - 20; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
        @Override protected void renderListBackground(GuiGraphics g) {
            g.fill(getX(), getY(), getRight(), getBottom(), this == sourceChoices ? 0xff252525 : 0xff858585);
        }
        @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (this == sourceChoices) g.fill(getX() + 3, getY() + 3, getRight() + 3, getBottom() + 3, 0x88000000);
            super.renderWidget(g, mouseX, mouseY, partialTick);
            g.renderOutline(getX(), getY(), getWidth(), getHeight(), this == sourceChoices ? 0xffdddddd : 0xff5b5b5b);
        }
        @Override protected void renderListSeparators(GuiGraphics g) { }
        @Override protected void renderSelection(GuiGraphics g, int top, int width, int height, int outerColor, int innerColor) {
            // 网格行只承载布局与键盘焦点，选中边框由单个实体卡片绘制
            if (page == Page.MAIN && this == rows) return;
            super.renderSelection(g, top, width, height, outerColor, innerColor);
        }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            Row row = getSelected();
            if (row == null) row = getFocused();
            if (row != null && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) { row.activate(false); return true; }
            if (row != null && key == GLFW.GLFW_KEY_DELETE && row.food != null && page == Page.EDITOR && canModifyDraft()) {
                ManagementData.Food selected = row.food;
                draftFoods.removeIf(f -> foodKey(f).equals(foodKey(selected))); rebuild(); return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
    }

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final Component text;
        private final Runnable action;
        private final ManagementData.Food food;
        private final List<ManagementData.Entity> entities;
        private final boolean special;
        private final int columns;
        private int rowX, rowY, rowWidth, cell;
        private long lastClick;
        private String sourceValue;
        Row(Component text, Runnable action, ManagementData.Food food, List<ManagementData.Entity> entities, boolean special, int columns) {
            this.text = text; this.action = action; this.food = food; this.entities = entities; this.special = special; this.columns = columns;
        }
        @Override public Component getNarration() { return text != null ? text : Component.literal(food != null ? foodKey(food) : name(entities.get(0))); }
        @Override public void render(GuiGraphics g, int index, int y, int x, int w, int h, int mx, int my, boolean hovering, float partial) {
            rowX = x; rowY = y; rowWidth = w;
            if (sourceValue != null) {
                boolean selected = sourceValue.equals(source);
                boolean focused = sourceChoices != null && sourceChoices.getFocused() == this;
                g.fill(x, y, x + w, y + h, hovering || focused ? 0xff555555 : selected ? 0xff3e4c3a : 0xff252525);
                if (selected) g.drawString(font, ">", x + 3, y + 3, 0xffa8dd8a, false);
                g.drawString(font, font.plainSubstrByWidth(text.getString(), w - 18), x + 14, y + 3, 0xffeeeeee, false);
                if (hovering || focused) g.renderOutline(x, y, w, h, 0xffaaaaaa);
            } else if (entities != null) {
                for (int i = 0; i < entities.size(); i++) {
                    ManagementData.Entity e = entities.get(i); int cw = w / columns; int cx = x + i * cw;
                    if (special) {
                        g.fill(cx + 1, y, cx + cw - 3, y + h, 0xffc6c6c6);
                        drawPreview(g, e.id(), cx + 3, y + 2, cw - 8, h - 29);
                        g.drawString(font, font.plainSubstrByWidth(name(e), cw - 8), cx + 4, y + h - 24, 0xff222222, false);
                        g.drawString(font, state(e), cx + 4, y + h - 12, 0xff285528, false);
                    } else {
                        g.fill(cx, y, cx + cw - 2, y + h, e.id().equals(selectedId) ? 0xffbecab7
                                : hovering ? 0xffc4c4c4 : index % 2 == 0 ? 0xffaaaaaa : 0xffb5b5b5);
                        if (w > 260) drawPreview(g, e.id(), cx, y, 30, 26);
                        int tx = cx + (w > 260 ? 34 : 2);
                        g.drawString(font, font.plainSubstrByWidth(name(e), w / 2), tx, y + 2, 0xff111111, false);
                        g.drawString(font, font.plainSubstrByWidth(e.id(), w - 70), tx, y + 14, 0xff333333, false);
                        g.drawString(font, tr(e.managed() ? "added" : "not_added"), x + w - 62, y + 2, 0xff222222, false);
                        g.drawString(font, state(e), x + w - 62, y + 14, 0xff333333, false);
                    }
                    if (e.id().equals(selectedId)) g.renderOutline(cx, y, cw - 2, h, 0xffffffff);
                    if (mx >= cx && mx < cx + cw && my >= y && my < y + h) setTooltipLater(g, Component.literal(name(e) + " / " + e.id() + " / " + e.sourceName()), mx, my);
                }
            } else if (food != null) {
                if (!food.tag()) g.renderItem(stack(food.id()), x, y);
                int offset = food.tag() ? 2 : 20;
                g.drawString(font, font.plainSubstrByWidth(foodName(food), Math.max(12, w - offset - (special ? 42 : 0))), x + offset, y + 3, 0xff111111, false);
                if (special) {
                    g.drawString(font, "✎", x + w - 36, y + 3, ClientManagement.canEdit() ? 0xff111111 : 0xff777777, false);
                    g.drawString(font, "X", x + w - 16, y + 3, ClientManagement.canEdit() ? 0xff111111 : 0xff777777, false);
                }
                if (page == Page.PICKER && foodKey(food).equals(selectedFood)) g.renderOutline(x, y, w, h, 0xffffffff);
                if (hovering) setTooltipLater(g, Component.literal(foodKey(food)), mx, my);
            } else {
                g.drawString(font, font.plainSubstrByWidth(text.getString(), w - 4), x + 2, y + 3, 0xff111111, false);
                if (hovering && font.width(text) > w - 4) setTooltipLater(g, text, mx, my);
            }
        }
        private void setTooltipLater(GuiGraphics g, Component value, int mx, int my) {
            ManagementScreen.this.setTooltipForNextRenderPass(value);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0) return false;
            boolean doubleClick = System.currentTimeMillis() - lastClick < 250;
            lastClick = System.currentTimeMillis();
            if (entities != null) cell = Math.min(entities.size() - 1, Math.max(0, (int) ((x - rowX) / Math.max(1, rowWidth / columns))));
            if (food != null && special && page == Page.EDITOR && canModifyDraft() && x >= rowX + rowWidth - 42) {
                if (x >= rowX + rowWidth - 22) { draftFoods.removeIf(f -> foodKey(f).equals(foodKey(food))); rebuild(); }
                else picker(food.tag(), food);
                return true;
            }
            activate(doubleClick); return true;
        }
        void activate(boolean doubleClick) {
            if (action != null) { action.run(); return; }
            if (entities != null) {
                ManagementData.Entity e = entities.get(cell); selectedId = e.id(); populateDetails(); updateActions();
                if (doubleClick && e.managed()) show(new ManagementScreen(ManagementScreen.this, Page.EDITOR, e.id()));
            } else if (food != null && page == Page.PICKER && list == rows) {
                selectedFood = foodKey(food); foodDetails(); updateActions();
            } else if (food != null && special && canModifyDraft()) picker(food.tag(), food);
        }
    }
}
