# Command Feedback ResourceLocation Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 FBM 规则命令在生成包含实体 ID 的可翻译反馈时抛出参数类型异常的问题。

**Architecture:** 保持命令、服务和存档架构不变，只在 UI 消息边界将 `ResourceLocation` 转换为受支持的 `String`。回归测试直接执行生产反馈路径并求值延迟消息，以覆盖真实异常触发点。

**Tech Stack:** Java 21、NeoForge 21.1.244、Brigadier、JUnit 5、Mockito

---

### Task 1: 修复实体 ID 翻译参数

**Files:**
- Modify: `src/test/java/com/fishbreedingmanager/command/FBMCommandsTest.java`
- Modify: `src/main/java/com/fishbreedingmanager/command/FBMCommands.java`

- [x] **Step 1: 编写失败回归测试**

在 `FBMCommandsTest` 中通过反射调用真实 `reportUpdate`，让模拟的 `sendSuccess` 立即求值消息 Supplier，并断言消息能正常构造且包含 `minecraft:cod`。

- [x] **Step 2: 确认测试因现有缺陷失败**

运行：

```powershell
.\gradlew.bat test --tests com.fishbreedingmanager.command.FBMCommandsTest --no-configuration-cache
```

预期：新增测试因 `TranslatableContents` 拒绝 `ResourceLocation` 参数而失败。

- [x] **Step 3: 实现最小修复**

在 `FBMCommands.reportUpdate` 和 `FBMCommands.show` 的翻译参数位置使用 `entityId.toString()`，不改动其他逻辑。

- [x] **Step 4: 验证测试和完整构建**

运行：

```powershell
.\gradlew.bat test build javadoc --no-configuration-cache
```

预期：全部测试、构建和 Javadoc 通过。

- [x] **Step 5: 检查并提交**

运行 `git diff --check`，确认只有预期文件变化后提交修复。
