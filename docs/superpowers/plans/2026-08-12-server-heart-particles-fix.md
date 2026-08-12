# Server Heart Particles Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让鳕鱼及其他非 `Animal` 实体在 FBM 喂食和繁殖成功时正确显示爱心粒子。

**Architecture:** 新增一个只负责服务端粒子同步的 `LoveParticleEmitter`，由交互处理器和繁殖控制器共用。发送器调用原生 `ServerLevel.sendParticles`，不引入自定义网络协议，也不修改繁殖状态机。

**Tech Stack:** Java 21、NeoForge 21.1.244、JUnit 5、Mockito

---

### Task 1: 服务端爱心粒子发送器

**Files:**
- Create: `src/main/java/com/fishbreedingmanager/breeding/LoveParticleEmitter.java`
- Create: `src/test/java/com/fishbreedingmanager/breeding/LoveParticleEmitterTest.java`
- Modify: `src/main/java/com/fishbreedingmanager/event/EntityInteractionHandler.java`
- Modify: `src/main/java/com/fishbreedingmanager/breeding/BreedingController.java`
- Modify: `docs/testing/P0_Cod_Acceptance.md`

- [x] **Step 1: 编写失败测试**

创建 `LoveParticleEmitterTest`，调用期望的 `emit(ServerLevel, Entity)` API，并验证 `ServerLevel.sendParticles` 收到
`ParticleTypes.HEART`、实体上方坐标、7 个粒子以及固定散布参数。

- [x] **Step 2: 确认测试因发送器尚不存在而失败**

```powershell
.\gradlew.bat test --tests com.fishbreedingmanager.breeding.LoveParticleEmitterTest --no-configuration-cache
```

预期：测试编译失败，指出 `LoveParticleEmitter` 不存在。

- [x] **Step 3: 实现发送器并接入两个调用路径**

实现带详细中文 Javadoc 的 `LoveParticleEmitter.emit`，调用服务端原生粒子 API；将喂食路径的一个事件广播和繁殖成功
路径的三个事件广播替换为该发送器。

- [x] **Step 4: 验证自动化结果**

```powershell
.\gradlew.bat test build javadoc --no-configuration-cache
```

预期：所有测试、构建和 Javadoc 通过，`git diff --check` 无错误。

- [x] **Step 5: 复审并提交**

复审只确认粒子调用范围和原有状态逻辑未改变；提交代码、测试、设计、计划与验收记录，不提交用户的 `.mcp.json`。
