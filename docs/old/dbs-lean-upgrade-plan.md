# DBS 智能精益升级计划（审判修订版）

> 文档状态：`修订中（已完成三轮审判中的前两轮）`
> 
> 版本：`v2.0`
> 
> 最近修订：`2026-04-13`

---

## 0. 审判说明（先定标准，再判文档）

本计划采用“审判式评估”方法，按以下标准判定完整性与可执行性：

1. **目标完整性**：目标是否可量化、可追责、可验收。
2. **架构一致性**：是否与现有三层 LLM、权限模型、ID 规范、ApiResponse 规范一致。
3. **实现可行性**：每个 Phase 是否具备接口、数据、任务、验收四要素。
4. **依赖闭环**：前后依赖是否清晰，是否可灰度上线。
5. **风险与降级**：是否包含故障降级、回滚、限流、熔断、预算保护。
6. **数据治理**：是否有存储模型、迁移脚本、索引、保留与清理策略。
7. **测试标准**：是否有单测、集成、性能、验收门槛。
8. **文字质量**：是否存在乱码、断链、路径错误、重复段落、歧义描述。

---

## 1. 审判结果（第1轮）

### 1.1 结论

原文档方向正确、覆盖面广，但存在以下严重问题：

- **P1：乱码严重**（多处出现乱码、截断、错位符号），影响可读性与可执行性。
- **P1：章节重复与编号冲突**（“二十三”重复，部分节次错位）。
- **P1：路径与链接不规范**（混入 `file:///` 本地绝对路径，不利于仓库协作）。
- **P1：部分语义未闭环**（如某些接口定义有，落地责任与里程碑未绑定）。
- **P2：验收标准分散**（有验收点但缺统一 DoD 门禁）。
- **P2：配置与数据库变更缺“上线次序”与“回滚触发条件”表述统一化。**

### 1.2 裁决

- 文档可作为技术蓝图，但**不能直接作为执行基线**。
- 需进行结构化重排、乱码修复、标准补齐、路径规范化。

---

## 2. 修订后的执行总纲

## 2.1 目标（保持不变，改为可验收）

将 `living-agent-service` 从“技术驱动自治系统”升级为“DBS 赋能的智能精益系统”，以四类结果为验收终点：

1. **可度量**：关键路径具备统一指标与告警机制。
2. **可改善**：进化决策具备根因分析与闭环验证。
3. **可导向**：VOC（客户之声）驱动与效率指标双目标共存。
4. **可对齐**：进化优先级与战略目标挂钩。

## 2.2 全局 DoD（Definition of Done）

每个 Phase 交付必须满足：

- [ ] 代码：接口 + 实现 + 单测
- [ ] 数据：表结构 + 索引 + 迁移脚本
- [ ] 配置：`application.yml` 可开关
- [ ] 运行：具备降级策略，不阻塞主链路
- [ ] 文档：更新本计划 + ADR（涉及架构决策时）

---

## 3. Phase A：度量基座与标准作业

> 目标：把系统从“可运行”升级到“可量化、可对比”。

### A1 MetricsCollector 实现

**交付清单**

- `MetricsCollectorImpl`
- `InMemoryMetricsStore`
- `MetricsAggregator`

**核心指标（首批）**

- `neuron.execution.time`（HISTOGRAM）
- `neuron.token.consumption`（COUNTER）
- `brain.request.count`（COUNTER）
- `brain.response.latency`（HISTOGRAM）
- `skill.execution.time`（HISTOGRAM）
- `skill.success.rate`（GAUGE）
- `knowledge.access.count`（COUNTER）
- `evolution.attempt.count`（COUNTER）
- `evolution.success.rate`（GAUGE）
- `user.satisfaction.score`（GAUGE）

**验收标准**

- [ ] MetricsCollector 接口方法全部实现
- [ ] 指标支持 p50/p95/p99
- [ ] 内存异步刷盘（批量 + 定时）
- [ ] 告警阈值可配置、可持久化

### A2 标准作业（Standard Work）

**交付清单**

- `StandardWork`
- `StandardWorkStep`
- `StandardWorkValidator`
- `StandardWorkValidatorImpl`

**兼容策略**

- 无 `standard_work` 的技能：默认 `compliant=true`，不阻断执行。

**验收标准**

- [ ] `SKILL.md` 支持 `standard_work` frontmatter
- [ ] SkillLoader 可解析步骤、基准耗时、质量门
- [ ] 验证结果可入库并可查询趋势

### A3 AI 价值流分析（Value Stream）

**交付清单**

- `ValueStreamMap`
- `ValueStreamNode`
- `ValueStreamEdge`
- `AIWasteType`
- `ValueStreamMapServiceImpl`

**验收标准**

- [ ] 可生成端到端价值流快照
- [ ] 可识别 AI 七大浪费
- [ ] 可输出瓶颈识别结果和热力图数据

---

## 4. Phase B：深度改善与根因分析

> 目标：从“修症状”升级到“控根因”。

### B1 RCA（5-Why + A3）

**交付清单**

- `RootCauseAnalyzer`
- `FiveWhyResult`
- `A3Report`
- `LLMBasedRootCauseAnalyzer`

**决策引擎改造要点**

- ERROR 连续修复超过阈值时，强制 RCA。
- RCA 结果进入决策上下文，影响后续策略。

**验收标准**

- [ ] 连续 REPAIR 可触发 5-Why
- [ ] 自动生成 A3 报告并可追溯
- [ ] RCA 失败可降级到规则引擎

### B2 Kaizen 改善周

**交付清单**

- `KaizenScheduler`
- `KaizenEvent`
- `KaizenScope`
- `KaizenReport`

**验收标准**

- [ ] 每周自动触发改善审视
- [ ] 可识别改进机会并生成事件
- [ ] 具备防重、防环路与执行上限

### B3 人机协同审批

**交付清单**

- `EvolutionHistoryService`
- `EvolutionTimeline`
- `EvolutionHistoryController`

**验收标准**

- [ ] 高风险策略需审批
- [ ] 可通过 API 查看进化历史与待审批项
- [ ] 审批行为有审计记录

---

## 5. Phase C：客户价值与可视化

> 目标：把“系统性能”与“客户价值”统一进同一决策面板。

### C1 VOC 管道

**交付清单**

- `VOCExtractor`
- `VOCEntry`
- `VOCCategory`
- `VOCService`
- `VOCServiceImpl`

**信号映射**

- `USER_PAIN` -> `REPAIR`
- `USER_NEED` -> `INNOVATE`
- `USER_PRAISE` -> `OPTIMIZE`

### C2 部门日常管理看板

**交付清单**

- `DepartmentDashboard`
- `DepartmentDashboardService`

### C3 CEO 仪表盘接入真实数据

**改造目标**

- 替换硬编码数据源，统一来自 Metrics/VOC/ValueStream。

**验收标准（C阶段通用）**

- [ ] VOC 可自动提取并形成摘要
- [ ] 部门看板实时显示异常与队列
- [ ] CEO 看板数据不再依赖硬编码

---

## 6. Phase D：战略对齐与能力体系

> 目标：让进化“做正确的事”。

### D1 战略部署（Policy Deployment）

**交付清单**

- `StrategicGoal`
- `GoalCascade`
- `PolicyDeploymentService`
- `PolicyDeploymentServiceImpl`

**关键机制**

- 信号置信度叠加“战略对齐度加成”。
- 风险目标自动标记与升级。

### D2 能力等级

**交付清单**

- `CapabilityLevel`
- `LevelAssessmentService`

**评分维度建议权重**

- 任务成功率：30%
- 进化贡献：20%
- 知识贡献：20%
- 标准作业合规：15%
- 用户满意度：15%

---

## 7. Phase E：持续优化与治理文化

> 目标：让改进成为制度，而非偶发活动。

### E1 知识 5S 审计
- `Knowledge5SAuditor`

### E2 技能 5S 审计
- `Skill5SAuditor`

### E3 改善文化指标
- 增加 `kaizen.*` 指标集

### E4 A3 模板化
- `A3Template`
- `A3TemplateRegistry`

---

## 8. 数据与迁移标准

## 8.1 迁移脚本顺序（固定）

- `V25__metrics_tables.sql`
- `V26__standard_work_tables.sql`
- `V27__rca_tables.sql`
- `V28__kaizen_tables.sql`
- `V29__voc_tables.sql`
- `V30__strategy_tables.sql`
- `V31__capability_tables.sql`

## 8.2 分区与保留

- `metrics_data`：按月分区。
- 默认保留：90 天。
- 支持 `pg_partman` 或应用层分区管理。

## 8.3 回滚策略

- 每个模块均通过 `enabled` 开关独立回退。
- 回滚不影响主业务链路处理。

---

## 9. 配置项基线（application.yml）

建议统一落在以下命名空间：

- `metrics.collector.*`
- `skill.standard-work.*`
- `evolution.global.*`
- `evolution.rca.*`
- `evolution.kaizen.*`
- `evolution.voc.*`
- `evolution.capability.*`
- `strategy.deployment.*`

---

## 10. API 设计标准

新增 API 必须满足：

- 统一响应：`ApiResponse<T>`
- 路径不带末尾斜杠
- 分页接口统一支持 `page/size/sort`
- 审批相关接口必须有审计字段（操作人、时间、结果）

核心资源建议：

- `/api/metrics/*`
- `/api/standard-works/*`
- `/api/value-stream/*`
- `/api/rca/*`
- `/api/a3/*`
- `/api/kaizen/*`
- `/api/voc/*`
- `/api/dashboard/*`
- `/api/strategy/*`
- `/api/capability/*`

---

## 11. 测试与验收门槛

## 11.1 单测覆盖要求

- MetricsCollectorImpl：>= 80%
- StandardWorkValidatorImpl：>= 90%
- LLMBasedRootCauseAnalyzer：>= 70%
- KaizenScheduler：>= 80%
- VOCExtractor / VOCService：>= 80%
- PolicyDeploymentServiceImpl：>= 80%
- LevelAssessmentService：>= 85%

## 11.2 关键集成场景

- 指标 -> 告警 -> 进化信号
- 标准作业 -> 验证 -> 基准更新
- VOC -> 进化 -> 指标改善验证
- Kaizen -> 执行 -> 报告
- 战略对齐 -> 决策优先级变化

## 11.3 性能基线

- Metrics 写入吞吐：>= 10k/s
- 常用查询延迟：p95 < 100ms
- RCA 调用延迟：< 30s（超时必须降级）

---

## 12. 风险、降级与防护

## 12.1 高优先风险

1. 指标系统影响主链路延迟
2. RCA 调用导致决策阻塞
3. Kaizen 触发环路
4. VOC 误判导致噪声信号

## 12.2 必须具备的降级

- Metrics 失败：静默降级，不阻断业务。
- RCA 失败：回落规则引擎。
- VOC 失败：跳过本批次，记录审计。
- 分区失败：降级为单表写入并告警。

## 12.3 速率限制

- 全局每小时/每日上限
- 每 brain 每小时上限
- 每 skill 每小时上限

---

## 13. 架构兼容性审查结论

- 与三层 LLM 架构：**兼容**
- 与权限隔离：**兼容**（战略与审批为高权限操作）
- 与 ID 命名：**兼容**（goal/a3/voc/kaizen 统一 URI 风格）
- 与 API 格式：**兼容**（统一 ApiResponse）

---

## 14. 执行顺序（最终）

1. Phase A（先度量）
2. Phase B 与 C（并行推进）
3. Phase D（战略与能力）
4. Phase E（治理固化）

**里程碑**

- M1 可度量
- M2 有标准
- M3 善改善
- M4 有导向
- M5 有聚焦
- M6 可持续

---

## 15. 审判轮次记录

### 第1轮（已完成）

- 判定：问题较多，不可直接执行
- 处理：完成结构重排、规则补齐、乱码清理第一阶段

### 第2轮（本次修订后）

- 判定：已达到“可执行文档”标准
- 剩余：需在后续提交中补充“具体类级别落地状态追踪表”

### 第3轮（抽检）

- 若无新增重大问题，可结束重复；如新增模块变更，重启三轮机制。

---

## 16. 后续强制动作

1. 新增 ADR：`ADR-0002-dbs-lean-upgrade-governance.md`
2. 将本计划拆分为可追踪任务清单（issue/epic）
3. 每个 Phase 结束后同步更新“实现状态表（代码路径+PR+验收结果）”

---

## 17. 实现状态表模板（执行中必须维护）

| Phase | 模块 | 目标类/文件 | 状态（未开始/进行中/完成） | PR | 验收结果 | 备注 |
|------|------|-------------|--------------------------|----|---------|------|
| A1 | MetricsCollector | `operation/metrics/impl/MetricsCollectorImpl.java` | 未开始 | - | - | - |
| A2 | StandardWork | `skill/impl/StandardWorkValidatorImpl.java` | 未开始 | - | - | - |
| B1 | RCA | `evolution/rca/impl/LLMBasedRootCauseAnalyzer.java` | 未开始 | - | - | - |
| C1 | VOC | `evolution/voc/impl/VOCServiceImpl.java` | 未开始 | - | - | - |
| D1 | Strategy | `strategy/impl/PolicyDeploymentServiceImpl.java` | 未开始 | - | - | - |

---

## 18. 相关文档（统一相对路径）

- `docs/02-core-architecture.md`
- `docs/04-knowledge-system.md`
- `docs/05-evolution-system.md`
- `docs/06-security-permission.md`
- `docs/planning/proactive-prediction.md`
- `docs/planning/operation-assessment.md`
- `docs/planning/user-profile-system.md`
- `docs/planning/project-task-approval.md`

> 注：原文档中的 `file:///` 绝对路径链接已废弃，统一使用仓库相对路径。