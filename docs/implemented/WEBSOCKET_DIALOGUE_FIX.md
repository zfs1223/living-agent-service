# WebSocket 对话闭环修复方案

> 本文档记录了 WebSocket 对话闭环修复的完整方案，包括 P0 问题修复和后续迭代计划。
> 对应原文章节：第9章
> 状态：✅ 已完成

---

## 问题诊断

### P0-A：pushExecutionProgress 使用错误的 sessionId

**现象**：`Session not found or closed for execution progress: sessionId=T09`

**根因**：`DepartmentChatService.onReceiptRecorded()` 调用 `pushExecutionProgress(receipt.employeeCode(), ...)` 时，第一个参数传的是员工代码（如 `T09`），而不是 WebSocket session ID。

**修复**：
- 修改 `DepartmentChatService.onReceiptRecorded()` 从 `executionResult.metadata` 获取 `sessionId`
- 确保 `ChannelBackedDepartmentExecutionCoordinator.coordinate()` 在构建 `DepartmentExecutionResult` 时包含 `sessionId`

---

## 已完成的修复

| 编号 | 修复内容 | 状态 |
| --- | --- | --- |
| P0-A | pushExecutionProgress 使用正确的 sessionId | ✅ |
| P0-B | DepartmentExecutionResult.metadata 增加 sessionId | ✅ |
| P0-C | 聊天历史查询统一使用部门代码 | ✅ |
| P1-A | 用户消息立即持久化 | ✅ |
| P1-B | 超时/错误结果也保存到数据库 | ✅ |
| P1-C | pushExecutionProgress 增加 department 广播 fallback | ✅ |
| P1-D | processWithBrain 增加诊断日志 | ✅ |
| P2-A | SessionContext 和 ConnectionRegistry 实现 | ✅ |
| P2-B | taskKey 生成和任务归并 | ✅ |
| P2-C | 断线重连和执行恢复 | ✅ |

---

## 验收标准

1. **前端对话收到回复**：用户在部门对话中发送消息后，能收到 `done` 类型的 WebSocket 消息
2. **执行进度可追踪**：日志中不再出现 `Session not found or closed for execution progress`
3. **刷新后记录保留**：用户刷新页面后，之前的对话记录能从 API 正确加载
4. **超时/错误有记录**：即使大脑处理超时或失败，用户刷新页面后也能看到错误提示
5. **执行进度不丢失**：即使 WebSocket session 断开，执行进度也会通过部门广播 fallback
6. **诊断日志可追踪**：日志中包含 `processWithBrain` 的关键节点信息

---

## 相关文件

| 文件 | 说明 |
| --- | --- |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/service/DepartmentChatService.java` | 对话服务 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java` | WebSocket 处理器 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/ChannelBackedDepartmentExecutionCoordinator.java` | 执行协调器 |
