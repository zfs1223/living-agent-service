# Living Agent Service 文档统一索引

> **目的**：统一索引 `docs/` 下所有文档，标注每个文档的状态（活跃 / 归档 / 已实施 / 被替代），便于快速定位。
>
> **重建日期**：2026-06-29
>
> **状态定义**：
> - **活跃**：当前仍在使用，保留在原位。
> - **归档**：内容过时或被替代，移至 `docs/archive/`。
> - **已实施**：方案已落地，移至 `docs/archive/pending-implemented/`。
> - **被替代**：被新文档取代，移至 `docs/archive/`。

---

## 目录结构

```
docs/
├── INDEX.md                      # 本文件（统一索引）
├── README.md                     # 文档中心入口
├── archive/                      # 归档区（不再活跃）
│   ├── old/                       # 原 docs/old 全部归档（48 份）
│   ├── pending-implemented/       # 已实施或被替代的待实施方案（7 份）
│   └── （根目录归档 4 份）
├── pending/                      # 仍待实施方案（2 份 + INDEX）
├── core/                         # 核心架构文档
├── adr/                          # 架构决策记录
├── analysis/                     # 分析文档
├── guides/                       # 指南
├── implemented/                  # 已实施记录
├── planning/                     # 规划文档
└── references/                   # 参考资料
```

---

## 一、docs/ 根目录 — 活跃文档（27 份）

| 文档路径 | 最后修改 | 说明 |
| --- | --- | --- |
| ACTIVE_FIXES_TODO.md | 2026-06-24 | 当前待处理问题清单（活跃修复看板） |
| ARCHITECTURE_INDEX.md | 2026-05-22 | 架构文档总索引 |
| BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md | 2026-06-15 | 大脑与固定数字员工做事规范索引 |
| CODE_LOGIC_AND_FLOW_ISSUES.md | 2026-05-29 | 代码逻辑与流程问题分析报告 |
| CODE_STRUCTURE_AND_FILE_GUIDE.md | 2026-06-26 | 代码结构与文件功能索引（权威，项目规则引用） |
| CODEGRAPH_INTEGRATION_IMPROVEMENT_PLAN.md | 2026-06-22 | CodeGraph 集成改进方案 |
| COMPLETE_LANDING_TODO.md | 2026-05-28 | 完全落地待办清单（仍被活跃文档引用） |
| DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md | 2026-06-29 | 桌面端 ↔ 后端对接闭环审计与改进计划（当前活跃） |
| DOCKER_SERVICE_LOG_ISSUES_AND_FIXES.md | 2026-05-11 | Docker 服务日志问题排查与解决方案 |
| ENTERPRISE_LIFEBODY_BUSINESS_FLOW.md | 2026-05-27 | 企业生命体业务流程总览 |
| ENTERPRISE_SETTINGS_IMPROVEMENT_PLAN.md | 2026-06-22 | 公司设置模块完善方案 |
| FLOW_IMPROVEMENT_REPORT.md | 2026-05-28 | 流程打通改进报告（74 项修复记录） |
| HERMES_COMPARISON_AND_BORROWING_PLAN.md | 2026-06-04 | Hermes Desktop 前端对比与借鉴分析 |
| LANDING_AUDIT_AND_IMPROVEMENT_PLAN.md | 2026-06-26 | 落地核查与改进计划（README 指定为权威核查基准） |
| LANDING_AUDIT_VERIFICATION_AND_FIX_PLAN.md | 2026-06-26 | 落地核查验证与修复方案（对上一份的细化修正） |
| LLM_AUTONOMY_HARDCODE_ANALYSIS.md | 2026-05-28 | LLM 自主决策硬编码问题分析 |
| MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md | 2026-06-24 | 模型职责与执行闭环优化方案 |
| PROJECT_RULES.md | 2026-04-13 | 项目规则（核心框架守护） |
| README.md | 2026-06-25 | 文档中心入口 |
| TASK_MODULE_CODE_PATH_ISSUES.md | 2026-05-22 | 任务模块代码路径问题与修复优先级 |
| TASK_MODULE_ISSUES_AND_REPAIR_PLAN.md | 2026-05-22 | 任务模块问题清单与修复优先级 |
| TASK_PROJECT_MODULE_SHARED_ISSUES_AND_REPAIR_PLAN.md | 2026-05-22 | 任务与项目管理模块共性问题统一修复计划 |
| TECH_BRAIN_PROJECT_MANAGEMENT_VERIFICATION.md | 2026-06-09 | 技术部大脑项目管理功能落地验证报告 |
| WINDOWS_MCP_INTEGRATION_PLAN.md | 2026-06-17 | Windows 自动化能力增强方案（Windows-MCP 借鉴） |
| websocket-architecture-review.md | 2026-06-15 | WebSocket 架构审查报告（仍被桌面端计划引用） |
| IMPROVEMENT_PLAN_PENDING_ITEMS.md | 2026-07-02 | DESKTOP+MODEL+IMPROVEMENT_PLAN三份文档未完成项统一跟踪 |
| IMPROVEMENT_PLAN_INDEX.md | 2026-07-02 | 闭环改进方案汇总索引（32个闭环覆盖状态/优先级/验收/进度跟踪） |
| IMPROVEMENT_PLAN_L1_CORE_LOOPS.md | 2026-07-02 | L1流程正确性闭环(1-14)改进方案 |
| IMPROVEMENT_PLAN_L2_COVERAGE_LOOPS.md | 2026-07-02 | L2覆盖完整性闭环(17-22,3-A/B,11-A/B)改进方案 |
| IMPROVEMENT_PLAN_L3_AUTONOMY_LOOPS.md | 2026-07-02 | L3生命体自洽闭环(24-32)改进方案 |
| 对话入口逻辑梳理.md | 2026-05-14 | 对话入口逻辑梳理（按岗位/页面/入口） |
| 权限与入口矩阵.md | 2026-05-26 | 权限与入口矩阵（项目规则引用的统一基线） |

---

## 二、docs/pending/ — 仍待实施（2 份 + 索引）

详见 [pending/INDEX.md](pending/INDEX.md)。

| 文档路径 | 最后修改 | 说明 |
| --- | --- | --- |
| pending/RUVIEW_INTEGRATION_PLAN.md | 2026-05-21 | RuView WiFi 感知微服务融合方案（无代码实现，待实施） |
| pending/SELF_EVOLUTION_IMPROVEMENT_PLAN.md | 2026-06-22 | 代码自我感知与自修复/自进化改进方案（进行中） |

---

## 三、docs/archive/ — 归档区

### 3.1 archive/ 根目录 — 过时根文档（5 份）

| 文档路径 | 最后修改 | 归档原因 |
| --- | --- | --- |
| archive/LOOP_COVERAGE_ANALYSIS_AND_SUPPLEMENTARY_IMPROVEMENT_PLAN.md | 2026-07-02 | 被 IMPROVEMENT_PLAN_INDEX.md + L1/L2/L3 三份拆分文件替代 |
| archive/CODE_DIRECTORY.md | 2026-05-08 | 被 `CODE_STRUCTURE_AND_FILE_GUIDE.md` 替代（自述为衍生） |
| archive/DOCKER_ISSUES_AND_SOLUTIONS.md | 2026-05-03 | Docker 早期问题清单，无引用，问题已解决 |
| archive/新模板.md | 2026-05-20 | 草稿模板片段，非正式文档 |
| archive/检查核对.md | 2026-05-23 | 临时文件路径核对清单，非正式文档 |

### 3.2 archive/pending-implemented/ — 已实施 / 被替代的方案（7 份）

| 文档路径 | 最后修改 | 归档原因 |
| --- | --- | --- |
| archive/pending-implemented/CLAUDE_CLI_SUPPORT_SELF_CHECK.md | 2026-05-27 | `ClaudeCliTool`、`ClaudeExecutionGateway` 已实现 |
| archive/pending-implemented/DEPARTMENT_CHAT_FLOW_REVIEW_AND_IMPROVEMENT_PLAN.md | 2026-05-27 | 被 `LANDING_AUDIT_AND_IMPROVEMENT_PLAN.md` 替代 |
| archive/pending-implemented/FIXED_EMPLOYEE_CODE_ARTIFACT_AND_REVIEW_FLOW.md | 2026-05-27 | `FixedEmployeeRegistry` 已实现 |
| archive/pending-implemented/INDEX.md | 2026-05-22 | 原 pending 索引，所有条目自述 ✅ 已完成 |
| archive/pending-implemented/WEBSOCKET_BACKEND_IMPROVEMENT_PLAN.md | 2026-06-15 | 自述 15/15 项已修复 |
| archive/pending-implemented/WEBSOCKET_RECONNECT_SOLUTION.md | 2026-05-22 | ✅ 已完成，`SessionPersistenceService` 已实现 |
| archive/pending-implemented/WINDOWS_AUTOMATION_IMPROVEMENT_PLAN.md | 2026-05-21 | ✅ 已完成，`WindowsAppTool`/`WindowsAutomationTool` 已注册 |

### 3.3 archive/old/ — 原 docs/old 全量归档（48 份）

> 全部为历史过时文档，按原文件名平移归档，保留目录结构。

| 文档路径 | 最后修改 |
| --- | --- |
| archive/old/ACTIVE_FIXES_TODO_2026-06-09.md | 2026-06-10 |
| archive/old/ACTIVE_ISSUES.md | 2026-06-05 |
| archive/old/ARTIFACT_TYPE_ENUM.md | 2026-05-21 |
| archive/old/backend-api-improvements.md | 2026-05-14 |
| archive/old/backend-improvement-plan.md | 2026-05-14 |
| archive/old/brain-model-selector-improvement.md | 2026-05-14 |
| archive/old/claude-cli-0-plan.md | 2026-05-14 |
| archive/old/claude-cli-1.md | 2026-05-14 |
| archive/old/claude-cli-2.md | 2026-05-13 |
| archive/old/claude-cli-local-model-adaptation-plan copy.md | 2026-05-13 |
| archive/old/CODE_LOGIC_LANDING_VERIFICATION.md | 2026-06-05 |
| archive/old/compliance-management.md | 2026-03-28 |
| archive/old/dashboard-redesign.md | 2026-04-24 |
| archive/old/dbs-lean-upgrade-plan.md | 2026-05-14 |
| archive/old/DCS_INTEGRATION_PLAN_AGGREGATION_SELFCLAIM_LLM.md | 2026-06-24 |
| archive/old/department-chat-implementation-design.md | 2026-05-14 |
| archive/old/department-isolation.md | 2026-04-21 |
| archive/old/design-vs-implementation-analysis.md | 2026-04-21 |
| archive/old/employee-compensation-persistence-design.md | 2026-05-14 |
| archive/old/evolution-implementation-design.md | 2026-05-14 |
| archive/old/evolution-persistence-design.md | 2026-05-14 |
| archive/old/EXECUTION_CAPABILITY_ENUM.md | 2026-05-21 |
| archive/old/EXECUTION_MODE_ENUM.md | 2026-05-21 |
| archive/old/FIXED_EMPLOYEE_PERSISTENCE_ISSUES.md | 2026-04-27 |
| archive/old/framework-ambiguities-consolidated-code-status-recommendations.md | 2026-05-14 |
| archive/old/human-intervention.md | 2026-03-28 |
| archive/old/implementation-roadmap.md | 2026-05-14 |
| archive/old/IMPROVEMENT_PLAN.md | 2026-06-05 |
| archive/old/IMPROVEMENT_PLAN_DATA_ORG.md | 2026-06-04 |
| archive/old/IMPROVEMENT_PLAN_FROM_BASIC_TO_PRODUCTION.md | 2026-06-02 |
| archive/old/INDEX.md | 2026-05-21 |
| archive/old/living-agent-service-analysis-report.md | 2026-06-04 |
| archive/old/MAINBRAIN_PROMPT_TEMPLATE.md | 2026-05-21 |
| archive/old/memory.md | 2026-05-14 |
| archive/old/memory-fix-suggestions.md | 2026-05-14 |
| archive/old/missing-implementation-checklist.md | 2026-05-14 |
| archive/old/model-pool-dialogue-code-improvement-plan.md | 2026-05-14 |
| archive/old/operation-assessment.md | 2026-03-27 |
| archive/old/organization-model-implementation-design.md | 2026-05-14 |
| archive/old/performance-persistence-design.md | 2026-05-14 |
| archive/old/proactive-prediction.md | 2026-03-10 |
| archive/old/project-task-approval.md | 2026-04-21 |
| archive/old/rd-collaboration-optimization-plan.md | 2026-05-14 |
| archive/old/README.md | 2026-05-27 |
| archive/old/REQUIREMENT_READINESS_EVALUATOR_RULES.md | 2026-05-21 |
| archive/old/REQUIREMENT_STATUS_STATE_MACHINE.md | 2026-05-21 |
| archive/old/routing-consistency-implementation-design.md | 2026-05-14 |
| archive/old/user-profile-system.md | 2026-04-21 |

---

## 四、其他子目录（保持原状，未评估）

| 目录 | 文件数 | 说明 |
| --- | --- | --- |
| core/ | 19 | 核心架构文档（含 7 篇 + 8 个 MODULE 文档） |
| adr/ | 3 | 架构决策记录 |
| analysis/ | 8 | 分析文档 |
| guides/ | 8 | 指南文档 |
| implemented/ | 4 | 已实施记录 |
| planning/ | 7 | 规划文档 |
| references/ | 1 | 参考资料（API_REFERENCE.md 等） |

> 本次整理范围仅限 `docs/old/`、`docs/pending/`、`docs/` 根目录。子目录文档状态未变动。

---

## 五、统计汇总

| 区域 | 文件数 |
| --- | --- |
| docs/ 根目录（活跃） | 32 |
| docs/pending/（待实施 + 索引） | 3 |
| docs/archive/（根目录归档） | 5 |
| docs/archive/pending-implemented/ | 7 |
| docs/archive/old/ | 48 |
| **本次归档总计** | **59** |
