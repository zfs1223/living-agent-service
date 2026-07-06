# 待实施方案索引

> 本目录保留**仍待实施**的方案文档。已完成或被新文档替代的方案已迁移至 `docs/archive/pending-implemented/`。
>
> 重建日期：2026-06-29

---

## 待实施项总览

| 文档 | 优先级 | 状态 | 简要说明 |
| --- | --- | --- | --- |
| [RUVIEW_INTEGRATION_PLAN.md](RUVIEW_INTEGRATION_PLAN.md) | P3 | 待实施 | RuView WiFi 感知微服务融合方案（物理世界感知能力） |
| [SELF_EVOLUTION_IMPROVEMENT_PLAN.md](SELF_EVOLUTION_IMPROVEMENT_PLAN.md) | P2 | 进行中 | Living Agent 代码自我感知与自修复/自进化改进方案 |

---

## 保留原因

### RUVIEW_INTEGRATION_PLAN.md
- 创建日期：2026-05-21
- 代码核查：`RuView` 关键字仅出现在 `docker-compose.yml`、`.env.example`、`application.yml`、`README.md` 等配置占位中，**无实际代码实现**。
- 结论：方案尚未落地，保留待实施。

### SELF_EVOLUTION_IMPROVEMENT_PLAN.md
- 最后更新：2026-06-22
- 代码核查：`EvolutionOrchestrator`、`ProfessionalKnowledgeSeeder`、`DynamicPromptBuilder` 等基础设施已存在，但自感知/自修复闭环仍在迭代。
- 结论：方案进行中，保留待实施。

---

## 已迁出文档（已实施 / 被替代）

下列文档已迁移至 `docs/archive/pending-implemented/`：

| 文档 | 迁出原因 |
| --- | --- |
| CLAUDE_CLI_SUPPORT_SELF_CHECK.md | `ClaudeCliTool.java`、`ClaudeExecutionGateway.java` 已实现 |
| DEPARTMENT_CHAT_FLOW_REVIEW_AND_IMPROVEMENT_PLAN.md | 被 `LANDING_AUDIT_AND_IMPROVEMENT_PLAN.md` 替代 |
| FIXED_EMPLOYEE_CODE_ARTIFACT_AND_REVIEW_FLOW.md | `FixedEmployeeRegistry.java` 已实现 |
| INDEX.md（原 pending 索引） | 所有条目自述 ✅ 已完成，被本索引替代 |
| WEBSOCKET_BACKEND_IMPROVEMENT_PLAN.md | 自述 15/15 项已修复 |
| WEBSOCKET_RECONNECT_SOLUTION.md | ✅ 已完成（2026-05-21），`SessionPersistenceService` 已实现 |
| WINDOWS_AUTOMATION_IMPROVEMENT_PLAN.md | ✅ 已完成（2026-05-21），`WindowsAppTool`/`WindowsAutomationTool` 已注册 |
