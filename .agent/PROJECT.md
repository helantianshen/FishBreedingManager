# 当前项目事实与开发约束

## 定位与版本来源

FBM 为原版和第三方实体提供世界级、数据驱动、可热更新的双亲繁殖管理，不新增固定鱼种。Mod ID 为 `fishbreedingmanager`，代码位于 `src/main/java/com/fishbreedingmanager/`。

- 目标 Java 21、Minecraft 1.21.1、NeoForge 21.1.x；精确版本以 `gradle.properties`、`build.gradle` 为准。
- Gradle 版本以 `gradle/wrapper/gradle-wrapper.properties` 为准。历史验收使用的 Gradle 不保证等于当前工作区版本，不擅自重写 Wrapper。
- 不提前实现多版本、多加载器或复杂公共 Adapter API。
- 本文区分已实现能力和有效产品目标；产品目标不代表功能已完成。

## 当前实现边界

核心双亲繁殖、世界规则持久化、事务校验与热更新、冷却和成长状态、幼体客户端同步、安全变种继承、自动鱼类发现及可选喂食槽适配已有实现。

管理 GUI、实体预览、规则管理网络协议尚未实现。手动导入集合已有持久化基础，但高级搜索、导入/取消导入的管理流程尚未打通。当前网络只同步幼体状态，命令要求权限等级 2；未开启作弊的单机同样受限。当前规则校验不等于完整的实体兼容能力判定。

有效产品目标：实体列表与本地化名称、Registry ID、来源 Mod、搜索、规则编辑和保存热应用；高级搜索完整 Registry，手动导入与取消导入按世界持久化，涉及已有规则时提示确认；GUI 服务端权限校验，不支持实体需禁止配置或强警告；3D 预览和旋转优先级低于基础管理。UI 可使用秒/分钟，内部以 tick 计时。

不包含跨 EntityType 杂交、复杂遗传、天气/群系等复杂繁殖条件、自动文件监听、所有第三方私有变种的专用适配、多版本或多加载器支持。

原版和第三方代表场景的实机通过范围以 [测试记录](../docs/README.md) 为准；剩余兼容项目保留为待测，不能以代表场景通过推断全部兼容性。

## 核心不变量

1. Rule 属于世界，State 属于实体；同一存档跨维度共享规则，不同存档隔离。
2. 行为发生时读取不可变 Runtime Snapshot，不把永久规则副本缓存到实体。候选校验失败保留旧状态，成功后整体发布。
3. 已开始的冷却和成长计时不因规则热更新追溯重算。
4. 后代和父母 EntityType 必须相同；安全创建并加入世界成功后才能提交父母繁殖结果。
5. 服务端是规则和玩法权威；命令及 GUI 写请求需服务端校验。GUI 不能仅隐藏按钮控制权限。
6. Registry ID 是稳定主键；自动发现不自动启用繁殖，不以鱼候选列表代替高级导入功能。
7. 支持多个物品和 Item Tag；幼体视觉默认 50%，不把碰撞箱缩放当作已实现能力。
8. 第三方 Variant 只使用可安全识别的公共契约；无法识别时允许默认个体，不使用反射、ASM 或暴力 NBT 复制。
9. 避免每 tick 扫全世界实体；兼容模块错误隔离，核心通过统一兼容入口访问具体适配。
10. 产品权限目标为单机可编辑、多人管理员修改；不能把现有命令限制直接扩展成最终 GUI 需求。

实现职责、AI 优先级与渲染事件约束详见 [ARCHITECTURE.md](ARCHITECTURE.md)。新增或修改公共类型、公共 API 与关键私有流程使用必要且准确的中文 Javadoc，遵循注释政策，不补无信息注释。

## 验证入口

先核实运行环境和 Wrapper；以下在项目根目录执行。Windows PowerShell 将 `./gradlew` 换为 `.\gradlew.bat`。

```bash
./gradlew test
./gradlew javadoc
./gradlew build
```

按任务需要使用 `--rerun-tasks`；测试数量以实际报告为准，不复制历史数量作为当前结论。

仅在用户需要实机测试时启动客户端：

```bash
./gradlew --no-daemon runClient
./gradlew --no-daemon runClient "-PfbmCompatModsDir=/absolute/path/to/compat-mods" "-PfbmCompatModsInclude=*.jar"
```

替换兼容目录为真实的仓库外路径，先检查 Jar 版本与前置，不放重复 FBM Jar。`run/` 是开发运行目录，不上传存档或日志。客户端显示、寻路、扣料和兼容表现需实际观察，不能由编译通过推定。测试操作及证据统一进入 [docs](../docs/README.md)。
