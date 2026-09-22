# 测试文档

本目录仅保存测试人员需要的待测步骤、复跑指令和已通过记录。开发需求、架构和规则请到 [.agent](../.agent/README.md)。

## 待测试

[兼容测试操作手册](pending/Compatibility_Test_Manual.md) 包含依赖版本、启动指令、场地准备、逐物种操作、预期现象、未覆盖矩阵及留档要求。当前按用户要求暂停，未执行项不计通过。已测鱼和原版牛在手册中作为回归对照。

## 已通过内容

| 记录 | 范围与时间 |
| --- | --- |
| [892cbf9 客户端结果](passed/892cbf9_Client_Acceptance.md) | 2026-09-16 至 09-23 用户确认的原版与代表第三方鱼、喂食槽实机结果 |
| [鳕鱼 P0](passed/P0_Cod_Acceptance.md) | 2026-08-12 自动化和客户端历史验收 |
| [Aquaculture](passed/Aquaculture_2_Compatibility_Acceptance.md) | 2026-08-29 服务端扫描和实体创建，不代表全部鱼种客户端通过 |
| [喂食槽自动化](passed/Animal_Feeding_Trough_Compatibility_Acceptance.md) | 2026-08-29 契约与自动化历史结果；客户端结果另见 892cbf9 |
| [五 Jar 组合](passed/Local_Fish_Mod_Bundle_Acceptance.md) | 2026-08-29 服务端共存、扫描、实体创建及第三方资源错误边界 |

“通过”只对记录的版本、组合和场景成立，不扩展成所有兼容性保证。历史日志路径可能被后续运行覆盖，不代表原始日志仍在当前机器。GUI 尚未实现，不属于目前可执行验收项，完成开发后补专门手册。
