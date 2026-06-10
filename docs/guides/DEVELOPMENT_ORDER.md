# 推荐开发顺序

> 本文档定义了 Living Agent 的推荐开发顺序。
> 版本：2026-05-20

---

## Sprint 1：先让 LLM 主路径可观测

1. 检查 `GatewayConfig` Bean 注册，确保 LLM-first 默认启用
2. 补齐 analyzer/director/dispatcher/composer Trace source
3. 初步新增 `DecisionContext` / `DecisionContextBuilder`，先注入员工和工具上下文
4. 文档和日志确认不再只靠规则链路判断

---

## Sprint 2：让最终回复真正回主脑

1. 新增 `MainBrainFinalSummaryService`
2. `DepartmentChatService` 在 `MAIN_BRAIN_COMPOSE` 时调用该服务
3. MainBrain LLM 不可用时降级现有 `MainBrainResponseComposer`
4. 更新 E2E Trace 验收

---

## Sprint 3：补齐执行验收层

1. 新增 `ExecutionReceiptReviewer`
2. 程序硬规则检查 receipt/artifact
3. 可选接入 LLM 语义验收
4. 未通过时输出返工建议或二次派发策略

---

## Sprint 4：真实工具执行第一版

1. 修复员工 resolved tools
2. 新增 `EmployeeTaskExecutor`
3. `DynamicEmployeeTaskConsumerRegistry` 委托 executor
4. web/code 任务生成多文件项目产物

---

## Sprint 5：生产化治理

1. Artifact 数据库持久化和专用 API
2. 固定员工数据库启用态治理
3. 模型熔断与超时分层
4. 结构化观测和日志降噪

---

## Sprint 6：长期会话与任务持久化

1. 部门对话长期会话支持
2. Task 和 Project 数据库持久化
3. WebSocket 断线重连完善
4. 部门对话 REST API

---

## 相关文档

| 文档 | 说明 |
| --- | --- |
| `docs/guides/INDEX.md` | 开发指南索引 |
| `docs/implemented/INDEX.md` | 已完成方案索引 |
