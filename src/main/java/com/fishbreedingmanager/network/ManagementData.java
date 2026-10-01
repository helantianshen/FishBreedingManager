package com.fishbreedingmanager.network;

import java.util.List;
import com.google.gson.Gson;
import com.fishbreedingmanager.breeding.BreedingRule;
import net.minecraft.resources.ResourceLocation;

/** 管理协议仅传输稳定 ID 与普通值，不持有世界对象或客户端类型 */
public final class ManagementData {
    /** 管理消息共用 JSON 编解码器，字节与集合上限由 Payload 和处理入口校验 */
    public static final Gson JSON = new Gson();
    private ManagementData() { }

    /**
     * 完整规则传输模型，保存时由服务端重新验证引用
     *
     * @param entity 目标实体ID
     * @param items 物品ID列表
     * @param tags 标签ID列表
     * @param cooldown 冷却tick
     * @param growth 成长tick
     * @param enabled 启用状态
     */
    public record Rule(String entity, List<String> items, List<String> tags,
                       int cooldown, int growth, boolean enabled) {
        /**
         * 从已验证的规则创建传输副本
         *
         * @param rule 候选规则
         * @return 独立的规则传输副本
         */
        public static Rule from(BreedingRule rule) {
            return new Rule(rule.entityTypeId().toString(),
                    rule.breedingItemIds().stream().map(Object::toString).toList(),
                    rule.breedingTagIds().stream().map(Object::toString).toList(),
                    rule.breedingCooldownTicks(), rule.growthTimeTicks(), rule.enabled());
        }
        /**
         * 将传输模型转换为待验证的候选规则
         *
         * @return 尚需验证的候选规则
         */
        public BreedingRule candidate() {
            return new BreedingRule(ResourceLocation.parse(entity),
                    items.stream().distinct().map(ResourceLocation::parse).toList(),
                    tags.stream().distinct().map(ResourceLocation::parse).toList(), cooldown, growth, enabled);
        }
    }

    /**
     * 目录条目独立表达收录状态、实际规则和兼容性
     *
     * @param id 稳定标识
     * @param translation 实体翻译键
     * @param source 来源命名空间
     * @param sourceName 来源显示名
     * @param automatic 自动收录标记
     * @param imported 手动导入标记
     * @param available 注册存在标记
     * @param compatibility 兼容等级
     * @param rule 候选规则
     * @param defaults 内建默认规则
     */
    public record Entity(String id, String translation, String source, String sourceName,
                         boolean automatic, boolean imported, boolean available,
                         String compatibility, Rule rule, Rule defaults) {
        /**
         * 自动候选、手动导入与已有规则共同决定主列表范围
         *
         * @return 是否属于主管理列表
         */
        public boolean managed() { return automatic || imported || rule != null; }
    }

    /**
     * 食物目录包含普通物品或当前非空物品标签成员
     *
     * @param id 稳定标识
     * @param tag 标签标记
     * @param members 标签成员
     */
    public record Food(String id, boolean tag, List<String> members) { }

    /**
     * 通用接口读取的食物与 FBM 配置相互独立，不代表完整繁殖条件
     *
     * @param entity 实体标识
     * @param status 读取状态
     * @param items 匹配的默认物品 ID
     */
    public record NativeFoods(String entity, String status, List<String> items) { }

    /**
     * 一个完整目录版本在所有分块收齐后才可使用
     *
     * @param revision 世界管理修订号
     * @param canEdit 服务端写权限
     * @param operational 世界规则是否已成功安装到运行时
     * @param entities 实体目录
     * @param foods 食物目录
     */
    public record Snapshot(long revision, boolean canEdit, List<Entity> entities, List<Food> foods, boolean operational) { }

    /**
     * 每个写操作携带客户端读取基线以及明确确认意图
     *
     * @param id 稳定标识
     * @param revision 世界管理修订号
     * @param action 请求动作
     * @param entity 目标实体ID
     * @param rule 候选规则
     * @param confirmed 明确确认标记
     */
    public record Request(long id, long revision, String action, String entity, Rule rule, boolean confirmed) { }

    /**
     * 服务端结果保留结构化文案键和当前权限
     *
     * @param success 提交成功标记
     * @param message 本地化反馈键
     * @param detail 具体校验信息
     * @param revision 世界管理修订号
     * @param canEdit 服务端写权限
     */
    public record Result(boolean success, String message, String detail, long revision, boolean canEdit) { }
}
