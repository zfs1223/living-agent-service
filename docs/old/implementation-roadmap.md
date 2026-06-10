# 实施路线图：记忆、知识、进化、补偿、绩效生产化收口

> 目标：把当前已经完成的文档边界收紧、桥接层收口、以及内存实现升级设计，整理成一份可直接执行的路线图。
>
> 本路线图优先面向当前代码状态，而不是历史设想。
>
> 额外目标：在核心闭环稳定后，逐步让系统具备“自我优化”的能力，即：通过监控、反馈、审计与建议机制，持续收敛到更稳定的实现。

---

## 1. 当前状态总览

目前项目已经形成以下清晰分层：

- **Memory**：底层记忆存储/检索基础设施
- **Knowledge**：企业知识治理层
- **Evolution / Task / Performance**：业务闭环层
- **Dashboard**：聚合展示层
- **Employee**：员工生命周期与编制层

当前最需要做的，不是再增加新概念，而是把下面几项逐步生产化：

1. 进化结果与反馈持久化
2. 员工补偿与奖励明细持久化
3. 绩效评估、排行、趋势持久化
4. 桥接层边界继续统一
5. 文档与代码继续保持对齐
6. 引入“自我优化”路径，让系统在运行中持续发现并收敛边界问题

---

## 2. 当前完成度（已更新）

截至当前轮次，以下内容已经完成或接近完成：

### 2.1 已完成的持久化闭环

- **进化**：结果、反馈、审计已接入持久化
- **补偿**：方案、账户、明细已接入持久化
- **绩效**：评估、指标、趋势已接入持久化

### 2.2 已完成的边界收口

- `KnowledgeGovernanceService`：只做治理入口
- `KnowledgePromotionAuditService`：只做晋升审计/历史/回滚
- `DashboardController`：只做总览聚合
- `MonitoringController`：只做监控聚合与告警确认
- `RecoveryController`：只做快照/恢复/验证
- `DepartmentController`：只做部门映射与员工筛选
- `OrgController`：只做组织层聚合查询
- `EnterpriseApiController`：只做董事长权限入口

### 2.3 已更新的文档

- `docs/evolution-persistence-design.md`
- `docs/employee-compensation-persistence-design.md`
- `docs/performance-persistence-design.md`
- `docs/implementation-roadmap.md`
- `docs/missing-implementation-checklist.md`
- `docs/04-knowledge-system.current.md`
- `docs/references/API_REFERENCE.md`

---

## 3. 推荐实施顺序

### Phase 1：进化结果生产化

#### 目标

将进化结果、反馈、审计从内存版升级为持久化版本。

#### 影响文件

- `EvolutionAdminController`
- `EvolutionFeedbackBridgeService`
- `EvolutionResultRepository`
- `InMemoryEvolutionResultRepository`
- `InMemoryEvolutionFeedbackService`

#### 对应设计

- `docs/evolution-persistence-design.md`

#### 验收标准

- 进化结果重启后可查询
- 最近结果与状态查询可用
- 反馈数据可统计
- 知识写回、通知链路不受影响

---

### Phase 2：员工补偿生产化

#### 目标

将奖励、处罚、账户余额、计划配置升级为持久化版本。

#### 影响文件

- `EmployeeCompensationService`
- `InMemoryEmployeeCompensationService`
- `TaskPerformanceBridgeService`
- `EmployeeController`

#### 对应设计

- `docs/employee-compensation-persistence-design.md`

#### 验收标准

- 奖励/处罚记录不会丢失
- 余额可回放
- 部门汇总稳定可查
- 任务审核回写可审计

---

### Phase 3：绩效评估生产化

#### 目标

将绩效评估、指标定义、排行、趋势升级为持久化版本。

#### 影响文件

- `PerformanceAssessmentService`
- `InMemoryPerformanceAssessmentService`
- `PerformanceDashboardService`
- `PerformanceController`

#### 对应设计

- `docs/performance-persistence-design.md`

#### 验收标准

- 评估结果可长期追踪
- 指标定义可管理
- 排行和趋势基于历史数据生成
- 看板数据稳定可追踪

---

### Phase 4：桥接层语义统一

#### 目标

进一步统一桥接层的职责边界，避免混层。

#### 重点检查对象

- `DashboardDataService`
- `TaskEventBridgeService`
- `TaskPerformanceBridgeService`
- `EvolutionFeedbackBridgeService`
- `KnowledgeGovernanceService`
- `KnowledgePromotionAuditService`

#### 验收标准

- Dashboard 只做聚合，不做业务执行
- Task / Evolution / Performance 的回写只进入对应业务层
- Knowledge 晋升/回滚/清理只进入知识治理层

---

### Phase 5：文档对齐与索引更新

#### 目标

让旧文档、当前对照版、修复建议三类文档形成可持续维护的结构。

#### 重点文件

- `docs/old/04-knowledge-system.md`
- `docs/04-knowledge-system.current.md`
- `docs/memory.md`
- `docs/memory-fix-suggestions.md`
- `docs/missing-implementation-checklist.md`

#### 验收标准

- 旧文档保留历史基线
- 当前对照版能反映真实代码
- 修复建议能指导下一轮改代码

---

## 4. 自我优化路径（新增）

在核心闭环稳定后，建议让系统具备“自我优化”的能力，具体做法不是让系统随意改代码，而是让它**自动发现、自动建议、人工确认、逐步收敛**。

### 4.1 监控驱动

利用现有：

- `MonitoringService`
- `DashboardDataService`
- `PerformanceDashboardService`
- `TaskEventBridgeService`
- `EvolutionFeedbackBridgeService`

持续收集：

- 任务审核异常率
- 绩效评估偏差
- 进化失败/升级频次
- 知识回滚频次
- 补偿异常与部门余额波动

### 4.2 建议驱动

利用现有：

- `ProactiveOrchestrator`
- `ProactiveSuggestionService`
- `RiskPredictor`

让系统主动产出：

- 任务复盘建议
- 绩效异常提示
- 进化失败复核建议
- 知识晋升待审建议
- 补偿策略调整建议

### 4.3 审计驱动

利用现有或新增：

- `EvolutionAuditLogRepository`
- `KnowledgePromotionAuditService`
- 未来可扩展的任务/绩效审计

将每次异常、回滚、升级都记录下来，作为后续优化依据。

### 4.4 人工确认机制

系统建议应进入：

- 管理员审阅
- 部门负责人审阅
- 风险/知识治理审阅

避免系统直接修改关键业务规则。

---

## 5. 过渡分支收敛清单（新增）

当前系统已经进入收敛期，以下内容建议按优先级逐步从“临时/占位/兼容”走向“真实数据驱动”。

### 5.1 高优先级收敛项

1. `ProactiveController` 中的静态/占位返回
   - `digest`
   - `habits`
   - `meeting-notes`
   - `analytics`
   - `predictions`

2. `PerformanceController` 的公司排行兼容分支
   - `company-rankings`
   - `company-bottom-rankings`

3. 少量看板/聚合服务中的静态兜底数据
   - `DashboardDataService`
   - 部分监控/主动预测展示字段

### 5.2 中优先级收敛项

4. 主动建议服务与真实运行反馈的联动
5. 习惯/会议纪要/预测的持久化源接入
6. Dashboard 指标来源进一步统一

### 5.3 低优先级收敛项

7. 仅用于兼容前端的展示层兜底
8. 文档中的历史说明与当前说明进一步分层

---

## 6. 当前优先级建议（已更新）

### 高优先级

1. 进化结果持久化收口
2. 员工补偿持久化收口
3. 绩效评估持久化收口
4. 过渡分支中的静态返回收敛

原因：

- 这三块是业务闭环的核心结果
- 兼容/占位分支会影响系统“自我完善”的可信度
- 当前以内存或静态为主的残留兼容点仍需逐步收敛

### 中优先级

5. 桥接层语义统一
6. Dashboard 指标分组
7. 自我优化建议链路打通

原因：

- 这些模块不会直接导致数据丢失
- 但容易造成边界混乱
- 也决定系统是否能持续自我收敛

### 低优先级

8. 旧文档维护
9. 当前对照版继续补充细节

原因：

- 主要是维护效率问题
- 不会立即影响业务稳定性

---

## 7. 落地方式建议

### 7.1 不要一次性大重构

建议按“保留内存版 + 新增持久化版 + 切换默认 Bean”的方式演进。

### 7.2 不要先改 controller 协议

现有 API 协议已经能支撑当前闭环，优先保持兼容。

### 7.3 先做结果型数据，再做聚合型数据

优先顺序：

- 结果（result / record / assessment）
- 明细（feedback / compensation record / indicators）
- 聚合（ranking / dashboard / summary）

### 7.4 自我优化先从“建议”开始

先让系统持续输出建议与风险提示，再由人工确认执行；不要让系统直接修改核心规则。

---

## 8. 结论

当前最合适的推进方式不是继续散点补文件，而是按下面顺序推进：

1. 进化结果生产化
2. 员工补偿生产化
3. 绩效生产化
4. 过渡分支收敛
5. 桥接层收口
6. 文档持续对齐
7. 建立“建议—审阅—执行—审计”的自我优化闭环

这条路线能最大程度减少返工，同时保证当前已经建立好的边界不被打乱，并逐步让系统具备持续完善自己的能力。
