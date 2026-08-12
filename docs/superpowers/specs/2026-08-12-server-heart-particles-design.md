# 服务端爱心粒子修复设计

## 问题

FBM 支持的目标实体不限定为 `Animal`。当前代码使用 `broadcastEntityEvent(entity, (byte) 18)` 复用原版
`Animal` 爱心事件，但鳕鱼是 `AbstractFish/Mob`，客户端不会为该事件生成爱心。因此喂食、配对和产仔均能成功，
但鳕鱼没有视觉反馈。

## 方案

新增单一职责的 `LoveParticleEmitter`，在逻辑服务端通过 `ServerLevel.sendParticles(ParticleTypes.HEART, ...)`
直接向附近客户端发送粒子。喂食成功时对目标实体发送一次；产仔成功时分别对两个父母和后代发送一次。

该方式不依赖实体继承层次，在单人集成服务器、局域网和独立服务器使用相同同步路径。不修改 Love 状态、配对、
冷却、成长、权限或存档结构。

## 测试与验收

单元测试验证发送器使用爱心粒子、实体上方坐标、固定数量和散布参数调用 `ServerLevel.sendParticles`。修复后运行完整
测试、构建和 Javadoc。游戏内验收项 3 在重新启动客户端并实际观察到喂食爱心后，才能由 FAIL 更新为 PASS。

