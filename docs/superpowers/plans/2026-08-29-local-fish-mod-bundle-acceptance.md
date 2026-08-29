# 本地第三方鱼类 Mod 组合验收 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在同一 NeoForge 开发服务器中加载本地目录的五个 Jar，验证 FBM 对 Upgrade Aquatic 及同目录辅助/内容 Mod 的自动扫描、误判边界、实体创建和共存稳定性。

**Architecture:** 使用现有 `fbmCompatModsDir` 与默认 `*.jar` include 加载完整本地组合，不增加任何 Mod ID、实体 ID 或 Java 类型生产特例。先以 Jar 元数据锁定依赖闭包，再以 FBM 真实来源摘要作为候选权威；只有 HIGH/MEDIUM 实体进入创建烟雾验收，未产生候选的内容 Mod 记录为“已扫描、无自动鱼类候选”。

**Tech Stack:** Java 21、Minecraft 1.21.1、NeoForge 21.1.244、ModDevGradle 2.0.143、Gradle `localRuntime`、本地 NeoForge Mod Jar、开发服务器 RCON。

---

## 文件边界

- Create: `docs/testing/Local_Fish_Mod_Bundle_Acceptance.md`：Jar 指纹、依赖、发现摘要、创建证据和人工边界。
- Modify: `docs/superpowers/plans/2026-08-29-local-fish-mod-bundle-acceptance.md`：真实执行状态。
- Modify: `.agent/HANDOFF.md`：跨会话当前兼容矩阵与下一步。
- Inspect only: `run/logs/latest.log`、`run/server.properties`。
- Production code: 本轮不预设修改；若真实结果暴露扫描缺陷，只记录可复现证据并另起 TDD 修复，不把单个 Mod 白名单作为补丁。

### Task 1: 锁定五个 Jar 的身份和依赖闭包

- [x] 记录五个 Jar 的文件名、大小和 SHA-256。
- [x] 从 `META-INF/neoforge.mods.toml` 核对 Mod ID、版本、Minecraft/NeoForge 范围与必需前置。
- [x] 确认 Upgrade Aquatic 7.0.1 + Blueprint 8.1.1 满足依赖；Farmer’s Delight 1.3.2、Youkai’s Homecoming 4.2.13+1 和 Aquaculture 2.7.21 没有目录外必需前置。
- [x] 读取 EntityType Tag，记录 Upgrade Aquatic 的 `c:fishes` 三项以及 Farmer’s Delight、Youkai、Blueprint 的公共鱼类 Tag 边界。

### Task 2: 真实加载完整本地组合并核对来源摘要

- [x] 在项目根设置 `$compatModsDir=(Resolve-Path -LiteralPath '..\常见鱼类mod及前置').Path`。
- [x] 运行 `.\gradlew.bat runServer "-PfbmCompatModsDir=$compatModsDir" "-PfbmCompatModsInclude=*.jar"`，确认 Gradle精确报告加载 5 个本地 Jar。
- [x] 从 Mod List 核对 `aquaculture`、`blueprint`、`farmersdelight`、`upgrade_aquatic`、`youkaishomecoming` 的实际加载版本。
- [x] 从 `FBM fish source discovered` 日志记录全部 HIGH/MEDIUM 来源、数量和排序 ID；Upgrade Aquatic 为 3 HIGH，Youkai Tuna 为 1 MEDIUM。
- [x] 对 Farmer’s Delight 与 Blueprint 记录“完整 Registry 已扫描但无 HIGH/MEDIUM 候选”，未将其误写为扫描失败。

### Task 3: 创建非 Aquaculture 候选并正常停服

- [x] 临时在项目 `run/server.properties` 中启用本机 RCON；保留默认关闭状态作为验收后目标状态。
- [x] 强制加载 `[0, 0]` 区块，依次生成并查询 `upgrade_aquatic:pike`、`upgrade_aquatic:perch`、`upgrade_aquatic:lionfish`。
- [x] 对额外出现的非 Aquaculture MEDIUM 候选 `youkaishomecoming:tuna` 完成生成与查询。
- [x] 删除测试实体、解除强制加载，通过 RCON `stop` 正常保存停服。
- [x] 恢复 `enable-rcon=false` 和空密码；日志无 FBM ERROR。另有 20 条 Youkai 配方 ERROR 和 1 条 Blueprint 示例 DataMap ERROR，均已追溯到对应 Jar 资源并如实记录。

### Task 4: 记录结论并更新交接

- [x] 新建 `docs/testing/Local_Fish_Mod_Bundle_Acceptance.md`，分开记录加载、扫描、误判边界、实体创建、第三方资源错误和未执行客户端观察。
- [x] 明确 Blueprint 是 Upgrade Aquatic 前置而非鱼类内容来源；Farmer’s Delight 无候选，Youkai 则实际发现一个 MEDIUM Tuna。
- [x] 更新 `.agent/HANDOFF.md` 的当前目标、兼容矩阵、验证情况、风险和推荐下一步。
- [x] 运行 `.\gradlew.bat test`，确认现有 21 suites、53 tests 无回归；未执行 Git/GitHub 命令。
