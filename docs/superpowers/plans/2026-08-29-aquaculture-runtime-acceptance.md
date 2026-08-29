# Aquaculture 真实运行兼容验收 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 通过可选、非入库的本地运行时 Jar 加载 Aquaculture 2.7.21，证明 FBM 的通用扫描能自动识别其 28 个鱼类实体，并完成当前环境可自动化的真实运行兼容验收。

**Architecture:** Gradle 仅在显式提供 `fbmCompatModsDir` 时把匹配 `fbmCompatModsInclude` 的 Jar 加入 `localRuntime`，默认构建完全不依赖本机路径。发现诊断由纯 Java 格式化器从 `DiscoverySnapshot` 生成稳定来源摘要，再由服务器生命周期入口记录；诊断只统计 HIGH/MEDIUM 候选，不写 Aquaculture 专用识别逻辑。真实 Jar 验收优先使用无界面开发服务器，客户端渲染、玩家交互和鱼群行为仅在实际执行后记录为通过。

**Tech Stack:** Java 21、Minecraft 1.21.1、NeoForge 21.1.244、ModDevGradle 2.0.143、Gradle `localRuntime`、JUnit 5、Aquaculture 2.7.21。

**Design:** `docs/superpowers/specs/2026-08-29-aquaculture-2-compatibility-design.md`

---

## 范围边界

- 不复制、重命名或提交第三方 Jar；不在 `build.gradle`、`gradle.properties` 或文档命令中固化当前电脑的绝对路径。
- 不新增 Aquaculture 编译依赖、Mod ID 白名单、实体 ID 白名单或 Java 类型引用。
- 本轮不实现 GUI、导入事务或第三方 Variant Adapter。
- 无界面服务器可以证明加载、Tag/Registry 扫描、候选分类和实体创建；渲染、右键喂食、寻路竞争及体型观感必须明确标记为人工客户端验收，未执行不得写为通过。

### Task 1: 增加可选且可精确筛选的本地运行时 Jar

**Files:**
- Modify: `build.gradle`

- [x] 在 `dependencies` 之前读取可选 Gradle 属性 `fbmCompatModsDir` 和 `fbmCompatModsInclude`。
- [x] 仅当目录存在且匹配到普通 `.jar` 文件时，把按相对路径稳定排序的 Jar 作为 `localRuntime files(...)` 加载；属性缺失/空白时静默走正常默认路径，显式目录无效或没有匹配项时提示并继续。
- [x] 默认 include 为 `*.jar`；真实验收命令使用 Aquaculture 的精确文件名，避免同目录其他 Mod 干扰。
- [x] 运行 `.\gradlew.bat help` 与带无效目录的 `.\gradlew.bat help -PfbmCompatModsDir=...`，确认配置阶段不失败。

### Task 2: 以 TDD 增加通用发现来源诊断

**Files:**
- Create: `src/test/java/com/fishbreedingmanager/discovery/DiscoveryDiagnosticsTest.java`
- Create: `src/main/java/com/fishbreedingmanager/discovery/DiscoveryDiagnostics.java`
- Modify: `src/main/java/com/fishbreedingmanager/FishBreedingManager.java`

- [x] 先写失败测试：稳定输出每个存在 HIGH/MEDIUM 候选的来源、注册实体数、HIGH/MEDIUM 数量和排序后的候选 ID；LOW 候选不进入可适配列表。
- [x] 运行 `.\gradlew.bat test --tests com.fishbreedingmanager.discovery.DiscoveryDiagnosticsTest`，确认因类缺失而失败。
- [x] 实现无第三方 Mod 特例的纯格式化器，并在每次发现快照成功发布后逐来源写入 INFO 日志。
- [x] 重跑定向测试；审查后又补齐两个有效来源逆序输入、空快照和仅失效导入边界，并以 `--rerun-tasks` 确认通过。

### Task 3: 启动真实 Aquaculture 开发服务器并核对扫描

**Files:**
- Create: `docs/testing/Aquaculture_2_Compatibility_Acceptance.md`
- Inspect: `run/logs/latest.log`

- [x] 记录 Jar 文件名、版本、大小和 SHA-256，不把 Jar 复制进仓库。
- [x] 使用 `-PfbmCompatModsDir` 与精确 `-PfbmCompatModsInclude` 启动 `runServer`；开发运行未被 EULA 阻止。
- [x] 从启动日志证明 `aquaculture` 成功加载，来源摘要恰有 28 个 HIGH、0 个 MEDIUM 候选，并核对 28 个排序 ID；`aquaculture:jellyfish` 未进入 HIGH/MEDIUM 列表。
- [x] 服务器达到可用状态后通过临时本机 RCON 生成 Smallmouth Bass、Minnow、Arapaima 与 Gar，逐一查询到实体，然后删除测试实体、恢复关闭 RCON 并正常 `stop`。

### Task 4: 划分自动化证据与人工客户端边界

**Files:**
- Modify: `docs/testing/Aquaculture_2_Compatibility_Acceptance.md`

- [x] 记录服务端已验证项：加载、扫描数量、候选 ID、误判边界、代表实体创建、关闭无异常。
- [ ] 启动开发客户端并逐项记录 Smallmouth Bass 喂食/Love/同类型后代/冷却/幼体成长，以及 Minnow/Arapaima/Gar 渲染和尺寸烟雾结果；本轮未执行可见客户端人工操作。
- [x] 没有直接观察到的客户端项统一标记“待人工验收”，未依据静态代码或服务端日志推断通过。

### Task 5: 回归、审查和交接

**Files:**
- Modify: `.agent/HANDOFF.md`
- Modify: `docs/superpowers/plans/2026-08-29-aquaculture-runtime-acceptance.md`

- [x] 运行 `.\gradlew.bat test`、`.\gradlew.bat javadoc --rerun-tasks`、`.\gradlew.bat build`。
- [x] 使用 `requesting-code-review` skill 发起独立审查；无 Critical，修复两个 Important（有效来源排序覆盖、计划/HANDOFF 状态）并吸收 Jar 边界加固建议。
- [x] 将实际通过项、未执行人工项、命令参数约定和下一步整理进 HANDOFF；勾选本计划真实完成的任务。
- [x] 不执行任何 Git/GitHub 命令；本轮未获得相关授权。
