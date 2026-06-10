# 端到端验收标准

> 本文档定义了 Living Agent 端到端验收标准。
> 版本：2026-05-20

---

## 验收样例

以"帮我做一个红色小球跳动的网页"为验收样例。

---

## 5.1 Trace 验收

必须包含：

```text
intake_classified(analyzer_type=llm_based 或 fallback reason)
main_brain_planned(director_type=llm_based 或 fallback reason)
brain_routed
department_plan_created
employee_assignment_planned(dispatcher_type=llm_based 或 fallback reason)
assignment_batch_prepared
employee_assigned
employee_execution_receipt_received
execution_receipts_aggregated
artifact_recorded
main_brain_finalized(summary_source=llm_main_brain 或 fallback_composer)
```

---

## 5.2 结果验收

- 至少一个员工 receipt 为 `COMPLETED`
- completion gate 为 `PASSED`
- 最终回复包含执行员工、完成状态、artifact 路径、验收结论、限制和下一步

---

## 5.3 文件产物验收

- `data/artifacts/tech/{executionId}/` 下存在 HTML 或多文件项目
- HTML 文件可直接打开并显示红色小球跳动动画
- 可通过 `AgentFileController` 或后续 `ArtifactController` 下载/预览

---

## 5.4 稳定性验收

- 不再出现 WebSocket `TEXT_PARTIAL_WRITING` 异常风暴
- 不再 fallback 到 Ollama 不存在的模型
- 回执通道不再出现 `Channel not found`
- 模型失败能进入 cooldown，并写入 receipt failure reason

---

## 5.5 WebSocket 对话闭环验收

1. **前端对话收到回复**：用户在部门对话中发送消息后，能收到 `done` 类型的 WebSocket 消息
2. **执行进度可追踪**：日志中不再出现 `Session not found or closed for execution progress`
3. **刷新后记录保留**：用户刷新页面后，之前的对话记录能从 API 正确加载
4. **超时/错误有记录**：即使大脑处理超时或失败，用户刷新页面后也能看到错误提示
5. **执行进度不丢失**：即使 WebSocket session 断开，执行进度也会通过部门广播 fallback
6. **诊断日志可追踪**：日志中包含 `processWithBrain` 的关键节点信息

---

## 5.6 需求澄清验收

1. **澄清问题可见**：当 `AssignmentReadinessEvaluator` 返回 `NEEDS_CLARIFICATION` 时，前端必须收到澄清问题
2. **不错误等待 output channel**：`NEEDS_CLARIFICATION` 分支不应继续订阅等待
3. **澄清消息持久化**：澄清问题必须保存为 assistant 消息
4. **任务可续接**：用户回答澄清问题后，应尽量复用同一个 `taskKey/executionId`
5. **Trace 可观测**：日志中应出现 `clarification_requested` 或等价 trace stage
6. **无误导性超时**：对于 readiness 明确为 `NEEDS_CLARIFICATION` 的请求，不允许最终结果为 `TIMEOUT/部门大脑响应超时`

---

## 5.7 长期会话验收

1. **刷新不丢对话**：用户刷新部门页面后，历史消息仍按同一 `conversationId` 加载
2. **断线可续接**：WebSocket 重连后自动绑定原 `conversationId`，并补发未完成 execution 状态
3. **几天后可继续**：用户几天后打开同一部门对话，可以继续追问或回答澄清问题
4. **澄清可恢复**：`WAITING_USER/NEEDS_CLARIFICATION` 状态不会因为连接断开而丢失
5. **任务不串线**：同一会话内多个任务通过不同 `taskKey` 区分
6. **用户可归档/软删除**：用户可清理自己的会话列表
7. **最高权限可销毁**：最高权限用户可物理销毁指定对话
8. **权限隔离**：普通用户不能查看、恢复或销毁他人的会话

---

## 相关文档

| 文档 | 说明 |
| --- | --- |
| `docs/guides/INDEX.md` | 开发指南索引 |
| `docs/implemented/INDEX.md` | 已完成方案索引 |
