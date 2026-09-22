# Aquaculture 2 兼容验收记录

## 结论

2026-08-29 在 Minecraft 1.21.1、NeoForge 21.1.244 开发服务器中真实加载 Aquaculture 2.7.21：FBM 无 Aquaculture 编译依赖或专用白名单，仍自动识别出 28 个 HIGH、0 个 MEDIUM 鱼类候选；四种代表实体均可由服务端创建并查询到，停服保存正常，最终日志没有 ERROR、Exception 或 Caused by 行。

因此本轮结论为：**通用自动扫描与服务端实体创建兼容通过；完整繁殖交互和客户端渲染仍待人工验收。**

## 测试对象与边界

- Jar：`[水产业2／水产品2] Aquaculture-1.21.1-2.7.21.jar`
- 文件大小：605,949 字节
- SHA-256：`45F00F9059838B2FECC988861111D8B3D4613A5F1B3688A8DBFA8655751B85BB`
- Jar 位于仓库外的开发者本地目录；未复制、重命名或打包进 FBM。
- 加载方式：显式提供 `fbmCompatModsDir`，并用 `fbmCompatModsInclude` 精确选择单个 Jar。
- 可复现命令：

  ```powershell
  .\gradlew.bat runServer "-PfbmCompatModsDir=<本地 Mod 目录>" "-PfbmCompatModsInclude=[水产业2／水产品2] Aquaculture-1.21.1-2.7.21.jar"
  ```

不提供属性、目录不存在或没有匹配文件时，FBM 的常规构建与运行仍不依赖该 Jar。

## 自动扫描证据

开发服务器 Mod List 报告 `Aquaculture 2 2.7.21 (aquaculture)`。服务器启动完成后，FBM 输出：

```text
source=aquaculture, version=2.7.21, registered=41, high=28, medium=0
```

HIGH/MEDIUM 候选为：

```text
aquaculture:arapaima
aquaculture:atlantic_cod
aquaculture:atlantic_halibut
aquaculture:atlantic_herring
aquaculture:bayad
aquaculture:blackfish
aquaculture:bluegill
aquaculture:boulti
aquaculture:brown_shrooma
aquaculture:brown_trout
aquaculture:capitaine
aquaculture:carp
aquaculture:catfish
aquaculture:gar
aquaculture:minnow
aquaculture:muskellunge
aquaculture:pacific_halibut
aquaculture:perch
aquaculture:pink_salmon
aquaculture:piranha
aquaculture:pollock
aquaculture:rainbow_trout
aquaculture:red_grouper
aquaculture:red_shrooma
aquaculture:smallmouth_bass
aquaculture:synodontis
aquaculture:tambaqui
aquaculture:tuna
```

`aquaculture:jellyfish` 不在 HIGH/MEDIUM 列表中，证明仅具水生属性的弱信号没有被误合并为自动适配候选。28 个结果来自公共 Registry/Tag 语义，生产代码中没有 Aquaculture Mod ID、实体 ID 或 Java 类型特例。

## 服务端实体创建烟雾验收

测试在强制加载的 `[0, 0]` 区块中依次执行 `summon`，随后用实体选择器确认实体存在，再删除测试实体并解除区块强制加载。

| 代表实体 | 创建结果 | 实体查询标记 |
| --- | --- | --- |
| `aquaculture:smallmouth_bass` | `Summoned new Smallmouth Bass` | `FBM_SMOKE_SMALLMOUTH_BASS_OK` |
| `aquaculture:minnow` | `Summoned new Minnow` | `FBM_SMOKE_MINNOW_OK` |
| `aquaculture:arapaima` | `Summoned new Arapaima` | `FBM_SMOKE_ARAPAIMA_OK` |
| `aquaculture:gar` | `Summoned new Gar` | `FBM_SMOKE_GAR_OK` |

Gradle 启动任务没有转发服务端标准输入，因此测试期间临时启用仅绑定本机连接的 RCON 发送命令。验收后使用 `stop` 正常停服，日志确认三维度全部保存；`run/server.properties` 已恢复为 `enable-rcon=false` 且密码为空。

## 后续覆盖

本记录保留当时通过的服务端检查及限制。后续客户端结果见 [892cbf9 已通过记录](892cbf9_Client_Acceptance.md)，未覆盖项目及步骤统一维护在 [待测手册](../pending/Compatibility_Test_Manual.md)。
