# 从"基本完成"到"闭环落地"改进计划

> 基于 `MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md` 中 8 个"基本完成"模块的深度缺口分析。
> 创建日期：2026-06-02

---

## 核心问题归纳

8 个"基本完成"模块的缺口可归纳为 4 个核心主题：

| 主题 | 根因 | 表现 |
|------|------|------|
| **假完成** | receipt 标记 COMPLETED 但无真实产物 | 用户以为任务完成了，磁盘上没有文件 |
| **收不到** | receipt 超时/通知发错/异步无闭环 | 用户发了消息，永远等不到基于真实执行结果的回复 |
| **丢消息** | channel 不存在时静默丢弃、执行无超时 | 任务消息丢失，线程永久阻塞 |
| **重启丢状态** | 澄清上下文和执行结果仅存内存 | 服务重启后，用户回答无法恢复原编排 |

---

## P0 — 闭环断裂（必须修复）

### P0-1: receipt 等待超时仅 5 秒

- **模块**: 回执驱动最终回复
- **文件**: `living-agent-gateway/.../DepartmentChatService.java`
- **位置**: `RECEIPT_WAIT_TIMEOUT_MS = 5_000`
- **问题**: LLM 生成代码/文档通常需要 10-30 秒，5 秒超时后 receipt 列表为空，completionGate 为 BLOCKED，知识/绩效/产物沉淀全部跳过
- **修复**: 超时从 5s → 60s，并增加自适应等待（收到部分 receipt 后延长等待）
- **状态**: ✅ 已修复

### P0-2: LLM fallback 标记 COMPLETED 但无真实产物

- **模块**: 固定员工派发
- **文件**: `living-agent-core/.../DynamicEmployeeTaskConsumerRegistry.java`
- **位置**: 第 137-152 行 `handleEmployeeTaskMessage()` 的 fallback 路径
- **问题**: ToolBacked 执行失败后回退到 LLM 文本执行，LLM 只返回纯文本 summary，不生成文件，但 receipt 状态仍为 COMPLETED
- **修复**: fallback 路径的 receipt 状态改为 `DEGRADED`，summary 中标注"无文件产物"
- **状态**: ✅ 已修复

### P0-3: sendArtifactMessage 传参错误

- **模块**: Artifact 下载/预览
- **文件**: `living-agent-gateway/.../DepartmentChatService.java`
- **位置**: 第 2022 行 `sendArtifactMessage(department, ...)`
- **问题**: 传 `department` 而非 `sessionId`，但 `DepartmentWebSocketHandler.sendArtifactMessage()` 用 `sessionIndex.get(sessionId)` 查找，产物通知永远不会送达用户
- **修复**: 第一个参数改为从 executionResult.metadata 获取 sessionId
- **状态**: ✅ 已修复

### P0-4: onReceiptRecorded 不触发最终回复

- **模块**: 回执驱动最终回复
- **文件**: `living-agent-gateway/.../DepartmentChatService.java`
- **位置**: `onReceiptRecorded()` 回调
- **问题**: 只推送 WebSocket 进度消息，不触发"所有 receipt 到齐后自动聚合并发送最终回复"。如果轮询超时但 receipt 后来才到达，用户永远收不到基于 receipt 的最终回复
- **修复**: 在 `onReceiptRecorded()` 中检测是否所有 receipt 已到齐，若到齐则触发异步最终回复
- **状态**: ✅ 已修复

---

## P1 — 可靠性缺失（不修复会导致间歇性故障）

### P1-1: 无服务端心跳探测

- **模块**: WebSocket 稳定性
- **文件**: `living-agent-gateway/.../DepartmentWebSocketHandler.java`
- **问题**: 只被动响应客户端 PING，如果客户端静默断开，服务端不感知，残留僵尸 session
- **修复**: 定时（30秒）向所有连接发送 PING，超时未响应的 session 主动关闭
- **状态**: ✅ 已修复

### P1-2: 无执行超时机制

- **模块**: 固定员工派发
- **文件**: `living-agent-core/.../DynamicEmployeeTaskConsumerRegistry.java`
- **位置**: `handleEmployeeTaskMessage()` 同步执行
- **问题**: 如果 LLM 调用卡住，channel 消费线程被永久阻塞
- **修复**: 用 `CompletableFuture.supplyAsync().get(timeout)` 包裹执行逻辑，超时返回 FAILED receipt
- **状态**: ✅ 已修复

### P1-3: channel 消息静默丢弃

- **模块**: 回执通道生命周期
- **文件**: `living-agent-core/.../ChannelManagerImpl.java`
- **位置**: `publish()` 方法
- **问题**: 对不存在的 channel 静默丢弃消息，如果 subscriber 尚未注册，任务消息丢失
- **修复**: 在 `ChannelBackedDepartmentExecutionCoordinator.coordinate()` 中发布消息前确保目标 channel 已创建；`ChannelManagerImpl.publish()` 增加日志警告
- **状态**: ✅ 已修复

### P1-4: executionResults 不持久化

- **模块**: 回执通道生命周期
- **文件**: `living-agent-core/.../FileBasedEmployeeExecutionReceiptService.java`
- **位置**: `executionResults` 内存 Map
- **问题**: 重启后 executionResult 丢失，导致进度推送失败
- **修复**: 将 executionResults 也持久化到 `data/receipts/{executionId}-meta.json`，启动时恢复
- **状态**: ✅ 已修复

### P1-5: RuleBased 降级不触发澄清

- **模块**: 主脑/部门脑规划
- **文件**: `living-agent-core/.../RuleBasedMainBrainTaskDirector.java`
- **问题**: 使用 `MainBrainTaskPlan.of()` 不设置 `requirementStatus`，降级路径下模糊需求直接进入分派
- **修复**: 设置 `requirementStatus=REQUIREMENT_CONFIRMED`，并支持根据关键词检测模糊需求时返回 `NEEDS_CLARIFICATION`
- **状态**: ✅ 已修复

### P1-6: completionGate BLOCKED 无用户反馈

- **模块**: 回执驱动最终回复
- **文件**: `living-agent-gateway/.../DepartmentChatService.java`
- **问题**: completionGate 为 BLOCKED 时只记录 trace 事件，不向用户发送"任务执行受阻"的消息
- **修复**: BLOCKED 时向用户发送明确的提示消息
- **状态**: ✅ 已修复

---

## P2 — 健壮性增强（不修复不影响核心链路，但影响生产质量）

### P2-1: WebSocket 连接数无上限保护

- **文件**: `DepartmentWebSocketHandler.java`
- **修复**: 增加单部门和全局连接数上限，超出时拒绝新连接
- **状态**: ✅ 已修复

### P2-2: 断线重连不恢复 receipt 订阅

- **文件**: `DepartmentWebSocketHandler.java`
- **修复**: 重连时重新绑定 receipt channel 订阅
- **状态**: ✅ 已修复

### P2-3: 澄清上下文仅存内存

- **文件**: `ConversationOrchestrator.java` `pendingClarifications`
- **修复**: 改为持久化存储，或至少在启动时从 conversation 表恢复
- **状态**: ✅ 已修复

### P2-4: mapDepartmentToBrain 硬编码 switch 仍存在

- **文件**: `ConversationOrchestrator.java` 第 557-568 行
- **修复**: 删除硬编码 switch，仅保留 BrainRegistry 查找 + 通用 fallback `{department}Brain`
- **状态**: ✅ 已修复

### P2-5: isExecutionComplete() 边界问题

- **文件**: `FileBasedEmployeeExecutionReceiptService.java`
- **修复**: 当 expectedDispatchIds 为空时返回 false 而非 true
- **状态**: ✅ 已修复

### P2-6: executionEnvironment 未传入 assignment context

- **文件**: `DynamicEmployeeTaskConsumerRegistry.java`
- **修复**: 在 assignment context 中加入 `executionEnvironment`
- **状态**: ✅ 已修复

---

## 修复顺序

```
第1步（P0）：修复闭环断裂
  P0-2 → P0-1 → P0-3 → P0-4

第2步（P1）：修复可靠性
  P1-2 → P1-3 → P1-1 → P1-5 → P1-6 → P1-4

第3步（P2）：增强健壮性
  P2-1 → P2-2 → P2-3 → P2-4 → P2-5 → P2-6
```

---

## 进度追踪

| 日期 | 完成项 | 备注 |
|------|--------|------|
| 2026-06-02 | 文档创建 | — |
| 2026-06-02 | P0-1 ✅ receipt 等待超时 5s→60s + 自适应等待 | DepartmentChatService.java |
| 2026-06-02 | P0-2 ✅ LLM fallback 标记 DEGRADED | DynamicEmployeeTaskConsumerRegistry.java |
| 2026-06-02 | P0-3 ✅ sendArtifactMessage 传 sessionId | DepartmentChatService.java |
| 2026-06-02 | P0-4 ✅ onReceiptRecorded 触发异步最终回复 | DepartmentChatService.java |
| 2026-06-02 | P1-1 ✅ 服务端心跳探测（30s 间隔，60s 超时清僵尸） | DepartmentWebSocketHandler.java |
| 2026-06-02 | P1-2 ✅ 执行超时机制（120s） | DynamicEmployeeTaskConsumerRegistry.java |
| 2026-06-02 | P1-3 ✅ channel 消息不静默丢弃 + 自动创建 channel | ChannelManagerImpl.java + ChannelBackedDepartmentExecutionCoordinator.java |
| 2026-06-02 | P1-4 ✅ executionResults 持久化到 -meta.json | FileBasedEmployeeExecutionReceiptService.java |
| 2026-06-02 | P1-5 ✅ RuleBased 降级触发澄清 | RuleBasedMainBrainTaskDirector.java |
| 2026-06-02 | P1-6 ✅ BLOCKED 状态用户反馈 | DepartmentChatService.java |
| 2026-06-02 | P2-1 ✅ WebSocket 连接数上限保护 | DepartmentWebSocketHandler.java |
| 2026-06-02 | P2-2 ✅ 断线重连恢复 receipt 订阅 | DepartmentWebSocketHandler.java |
| 2026-06-02 | P2-3 ✅ 澄清上下文持久化 | ConversationOrchestrator.java |
| 2026-06-02 | P2-4 ✅ 删除 mapDepartmentToBrain 硬编码 switch | ConversationOrchestrator.java |
| 2026-06-02 | P2-5 ✅ isExecutionComplete() 空集合返回 false | FileBasedEmployeeExecutionReceiptService.java |
| 2026-06-02 | P2-6 ✅ executionEnvironment 传入 assignment context | DynamicEmployeeTaskConsumerRegistry.java |
