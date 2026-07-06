# 设计方案 vs 代码实现 对比分析表

> 基于 5 份设计文档与实际代码的逐项对照，评估落地完成度与遗留问题。
> 更新时间: 2026-04-16

---

## 一、进化持久化设计方案 vs 实现

**设计文档**: `docs/evolution-persistence-design.md`

### 1.1 组件落地对照

| 设计要求 | 实际实现 | 状态 | 备注 |
|----------|----------|------|------|
| `EvolutionResultEntity` | 已存在，`@Entity` + `@Table` | ✅ 完成 | 含 `fromDomain`/`toDomain` 转换，4个索引 |
| `EvolutionFeedbackEntity` | 已存在，`@Entity` + `@Table` | ⚠️ 基本完成 | 缺少 `fromDomain`/`toDomain` 转换方法 |
| `EvolutionAuditLogEntity` | 已存在，`@Entity` + `@Table` | ✅ 完成 | 3个索引，审计日志无需反向转换 |
| `JpaEvolutionResultRepositoryAdapter` | 已存在，`@Service` + `@Primary` | ✅ 完成 | 适配器模式，桥接领域接口与JPA仓库 |
| `JpaEvolutionFeedbackService` | 已存在，`@Service` + `@Primary` | ✅ 完成 | 优先于内存版注入 |
| `EvolutionFeedbackRepository`(JPA) | 已存在，`@Repository` | ✅ 完成 | Spring Data JPA，5个派生查询 |
| `EvolutionResultRepository`(领域接口) | 已存在 | ✅ 完成 | 4个方法签名 |
| `EvolutionFeedbackService`(领域接口) | 已存在 | ✅ 完成 | 3个方法签名 |
| `InMemoryEvolutionResultRepository` | 已存在，`@Service` | ✅ 完成 | 无 `@Primary`，作为备选 |
| `InMemoryEvolutionFeedbackService` | 已存在，`@Service` | ✅ 完成 | 无 `@Primary`，作为备选 |

### 1.2 迁移策略对照

| 设计阶段 | 实际状态 | 备注 |
|----------|----------|------|
| 阶段1: 双实现并存 | ✅ 已完成 | JPA版 `@Primary`，内存版保留 |
| 阶段2: 结果先落库 | ✅ 已完成 | `JpaEvolutionResultRepositoryAdapter` 已实现 |
| 阶段3: 反馈与审计补齐 | ✅ 已完成 | `JpaEvolutionFeedbackService` + `EvolutionAuditLogEntity` 已实现 |
| 阶段4: 保留内存版做兜底 | ✅ 已完成 | 内存版保留，无 `@Primary` |

### 1.3 遗留问题

| 问题 | 严重度 | 说明 |
|------|--------|------|
| `EvolutionFeedbackEntity` 缺少 `fromDomain`/`toDomain` | 低 | 转换逻辑散落在 `JpaEvolutionFeedbackService` 中，不影响功能 |

### 1.4 总体完成度: **95%**

---

## 二、员工补偿持久化设计方案 vs 实现

**设计文档**: `docs/employee-compensation-persistence-design.md`

### 2.1 组件落地对照

| 设计要求 | 实际实现 | 状态 | 备注 |
|----------|----------|------|------|
| `CompensationPlanEntity` | 已存在，`@Entity` + `@Table` | ✅ 完成 | 3个索引，`@PreUpdate` 自动更新 |
| `CompensationAccountEntity` | 已存在，`@Entity` + `@Table` | ✅ 完成 | 2个索引，余额+状态 |
| `CompensationRecordEntity` | 已存在，`@Entity` + `@Table` | ✅ 完成 | 3个索引，含 `sourceTaskId`/`sourceReviewId` |
| `JpaEmployeeCompensationService` | 已存在，`@Service` + `@Primary` | ✅ 完成 | 7个接口方法+1个扩展方法 |
| `CompensationPlanRepository` | 已存在，`@Repository` | ✅ 完成 | 3个派生查询 |
| `CompensationAccountRepository` | 已存在，`@Repository` | ✅ 完成 | 3个派生查询 |
| `CompensationRecordRepository` | 已存在，`@Repository` | ✅ 完成 | 4个派生查询 |
| `EmployeeCompensationService`(接口) | 已存在 | ✅ 完成 | 7个方法 + 2个内嵌 record |
| `InMemoryEmployeeCompensationService` | 已存在，`@Service` | ✅ 完成 | 无 `@Primary`，作为备选 |
| `TaskPerformanceBridgeService` | 已存在，`@Service` | ✅ 完成 | 桥接任务审核与补偿 |

### 2.2 迁移策略对照

| 设计阶段 | 实际状态 | 备注 |
|----------|----------|------|
| 阶段1: 双实现并存 | ✅ 已完成 | JPA版 `@Primary`，内存版保留 |
| 阶段2: 记账先落库 | ✅ 已完成 | `recordReward`/`recordPenalty` 已持久化 |
| 阶段3: 补部门汇总 | ✅ 已完成 | `summarizeDepartment` 基于 JPA 查询 |
| 阶段4: 保留内存版做兜底 | ✅ 已完成 | 内存版保留 |

### 2.3 遗留问题

| 问题 | 严重度 | 说明 |
|------|--------|------|
| `summarizeDepartment` 使用 `findAll()` 全表扫描 | 中 | 数据量大时性能风险 |
| `record()` 扩展方法为包级可见 | 低 | `sourceTaskId`/`sourceReviewId` 无法从接口层传入 |

### 2.4 总体完成度: **90%**

---

## 三、绩效评估持久化设计方案 vs 实现

**设计文档**: `docs/performance-persistence-design.md`

### 3.1 组件落地对照

| 设计要求 | 实际实现 | 状态 | 备注 |
|----------|----------|------|------|
| `PerformanceAssessmentEntity` | 已存在，`@Entity` + `@Table` | ✅ 完成 | 含 `fromDomain`/`toDomain`，4个索引 |
| `PerformanceIndicatorEntity` | 已存在，`@Entity` + `@Table` | ✅ 完成 | 3个索引 |
| `PerformanceTrendSnapshotEntity` | 已存在，`@Entity` + `@Table` | ✅ 完成 | 3个索引，缺少 `fromDomain`/`toDomain` |
| `JpaPerformanceAssessmentService` | 已存在，`@Service` + `@Primary` | ✅ 完成 | 13个方法全部实现 |
| `PerformanceAssessmentRepository` | 已存在，`@Repository` | ✅ 完成 | 5个派生查询 |
| `PerformanceIndicatorRepository` | 已存在，`@Repository` | ✅ 完成 | 3个派生查询 |
| `PerformanceTrendRepository` | 已存在，`@Repository` | ✅ 完成 | 3个派生查询 |
| `InMemoryPerformanceAssessmentService` | 已存在，`@Service` | ✅ 完成 | 无 `@Primary`，部分硬编码 |
| `PerformanceAssessmentServiceImpl` | 已存在，`@Service` | ⚠️ 存根 | 几乎所有方法返回空/默认值 |
| `PerformanceAssessmentService`(接口) | 已存在 | ✅ 完成 | 13个方法 + 4个内嵌类型 |
| `PerformanceDashboardService` | 已存在，`@Service` | ⚠️ 有耦合问题 | `instanceof` 检查导致公司排名失效 |
| `PerformanceRankingService` | **不存在** | ❌ 未实现 | 设计文档标注为"可选" |

### 3.2 迁移策略对照

| 设计阶段 | 实际状态 | 备注 |
|----------|----------|------|
| 阶段1: 双实现并存 | ✅ 已完成 | JPA版 `@Primary`，内存版+存根版保留 |
| 阶段2: 评估结果落库 | ✅ 已完成 | `assessEmployee` 结果持久化 |
| 阶段3: 排行与趋势基于历史数据 | ⚠️ 部分完成 | 部门排行可用，公司排行因耦合问题失效 |
| 阶段4: 保留内存版做兜底 | ✅ 已完成 | 内存版保留 |

### 3.3 遗留问题

| 问题 | 严重度 | 说明 |
|------|--------|------|
| `PerformanceDashboardService` 的 `instanceof` 耦合 | 高 | 当 `@Primary` 生效时注入 `JpaPerformanceAssessmentService`，`instanceof InMemoryPerformanceAssessmentService` 失败，公司级排名返回空列表 |
| `PerformanceAssessmentServiceImpl` 冗余存根 | 低 | 几乎空实现，仍被 Spring 容器管理，属于冗余代码 |
| `PerformanceRankingService` 未实现 | 低 | 设计文档标注为"可选"，排名逻辑内联在各实现类中 |
| `PerformanceTrendSnapshotEntity` 缺少转换方法 | 低 | 转换逻辑在 `JpaPerformanceAssessmentService.toTrendSnapshot()` 中 |

### 3.4 总体完成度: **80%**

---

## 四、实施路线图 vs 实际进度

**设计文档**: `docs/implementation-roadmap.md`

### 4.1 各阶段完成度

| Phase | 目标 | 完成度 | 说明 |
|-------|------|--------|------|
| Phase 1 | 进化结果生产化 | **95%** | JPA实体+仓库+适配器全部就位，`@Primary` 已切换 |
| Phase 2 | 员工补偿生产化 | **90%** | JPA实体+仓库+服务全部就位，`@Primary` 已切换，`summarizeDepartment` 有性能隐患 |
| Phase 3 | 绩效评估生产化 | **80%** | JPA实体+仓库+服务全部就位，但公司级排名因 `instanceof` 耦合失效 |
| Phase 4 | 桥接层语义统一 | **85%** | 6个桥接服务职责清晰，但 `PerformanceDashboardService` 存在跨层耦合 |
| Phase 5 | 文档对齐与索引更新 | **70%** | 旧文档/当前对照版/修复建议三类文档已形成结构，但部分文档内容未同步最新代码 |

### 4.2 自我优化路径对照

| 路径 | 实现状态 | 说明 |
|------|----------|------|
| 监控驱动 | ✅ 已实现 | `MonitoringService` 10个方法，`DashboardDataService` 聚合12个依赖 |
| 建议驱动 | ✅ 已实现 | `ProactiveSuggestionService` 6个方法，`ProactiveOrchestrator` 编排三路数据 |
| 审计驱动 | ⚠️ 部分实现 | 进化审计已实现，任务/绩效审计尚未独立 |
| 人工确认机制 | ⚠️ 部分实现 | 审批流程已实现，但系统建议的审阅流程尚未闭环 |

### 4.3 过渡分支收敛清单

| 收敛项 | 优先级 | 状态 | 说明 |
|--------|--------|------|------|
| `ProactiveController` 静态/占位返回 | 高 | ⚠️ 未收敛 | `digest`/`habits`/`meeting-notes`/`analytics`/`predictions` 仍为占位数据 |
| `PerformanceController` 公司排行兼容分支 | 高 | ⚠️ 未收敛 | `company-rankings`/`company-bottom-rankings` 因 `instanceof` 耦合失效 |
| 看板/聚合服务静态兜底数据 | 高 | ⚠️ 未收敛 | `DashboardDataService` 部分字段仍为硬编码 |
| 主动建议与真实运行反馈联动 | 中 | ⚠️ 未收敛 | 建议生成逻辑已实现，但数据源未完全接入 |
| 习惯/会议纪要/预测持久化 | 中 | ❌ 未实现 | 仍为内存/占位数据 |
| Dashboard 指标来源统一 | 中 | ⚠️ 未收敛 | 部分指标来自硬编码，部分来自真实服务 |

---

## 五、框架歧义点澄清 vs 代码现状

**设计文档**: `docs/framework-ambiguities-consolidated-code-status-recommendations.md`

### 5.1 七份核心文档歧义点澄清状态

| 文档 | 歧义点数量 | 已澄清 | 已在代码中落地 | 遗留 |
|------|-----------|--------|---------------|------|
| 01-system-overview | 5 | 5 | 3 | "赚钱能力"未形成闭环；仪表盘仍偏展示层 |
| 02-core-architecture | 5 | 5 | 4 | 通道职责边界未完全硬约束 |
| 03-employee-model | 5 | 5 | 4 | 编制vs实例运行规则未在代码中强制约束 |
| 04-knowledge-system | 5 | 5 | 4 | 知识晋升门槛未统一为代码级门禁 |
| 05-evolution-system | 5 | 5 | 4 | 熔断/回滚机制未统一为代码级门禁 |
| 06-security-permission | 6 | 6 | 5 | WebSocket频道权限未与部门严格绑定 |
| 07-deployment-operations | 6 | 6 | 4 | 健康检查分层未完全落地；主动预判与业务事件联动未闭环 |

### 5.2 七条主链路收口状态

| 链路 | 收口状态 | 说明 |
|------|----------|------|
| 权限前置 | ✅ 基本收口 | 所有 Controller 均使用 `AccessGateService.canRoute()` 前置检查 |
| 状态统一 | ⚠️ 部分收口 | 任务/审批状态机已统一，但员工实例状态（激活/休眠/学习/恢复/销毁）未统一 |
| 边界统一 | ⚠️ 部分收口 | 桥接层边界已清晰，但 `PerformanceDashboardService` 存在跨层耦合 |
| 进化门禁统一 | ⚠️ 部分收口 | `EvolutionConstraints` 已存在，但熔断/回滚未统一为代码级门禁 |
| 运维闭环统一 | ⚠️ 部分收口 | 监控/告警/恢复已实现，但主动预判与业务事件联动未闭环 |

---

## 六、桥接层实现状态

### 6.1 桥接服务对照

| 服务 | 职责 | 状态 | 是否只做桥接 | 备注 |
|------|------|------|-------------|------|
| `DashboardDataService` | 仪表盘聚合 | ✅ 完整 | ✅ 是 | 聚合12个依赖，1个公共方法 |
| `TaskEventBridgeService` | 任务事件桥接 | ✅ 完整 | ✅ 是 | 风险+建议+通知 |
| `TaskPerformanceBridgeService` | 任务绩效桥接 | ✅ 完整 | ✅ 是 | 补偿+风险+通知 |
| `EvolutionFeedbackBridgeService` | 进化反馈桥接 | ✅ 完整 | ✅ 是 | 反馈+审计+知识+通知+任务 |
| `KnowledgeGovernanceService` | 知识治理 | ✅ 完整 | ✅ 是 | 摘要+质量+晋升+清理+搜索 |
| `KnowledgePromotionAuditService` | 知识晋升审计 | ✅ 完整 | ✅ 是 | 晋升+回滚+历史 |
| `MonitoringService` | 监控聚合 | ✅ 完整 | ✅ 是 | 健康+组件+问题+告警+指标 |
| `ProactiveOrchestrator` | 主动编排 | ✅ 完整 | ✅ 是 | 建议+告警+模式统计 |

### 6.2 桥接层验收标准对照

| 验收标准 | 状态 | 说明 |
|----------|------|------|
| Dashboard 只做聚合，不做业务执行 | ✅ 通过 | `buildOverview()` 仅聚合数据 |
| Task/Evolution/Performance 回写只进入对应业务层 | ⚠️ 部分通过 | `PerformanceDashboardService` 存在 `instanceof` 跨层耦合 |
| Knowledge 晋升/回滚/清理只进入知识治理层 | ✅ 通过 | `KnowledgeGovernanceService` + `KnowledgePromotionAuditService` 职责清晰 |

---

## 七、总体评估

### 7.1 三大持久化方案完成度

| 方案 | 完成度 | 关键遗留 |
|------|--------|----------|
| 进化持久化 | **95%** | `EvolutionFeedbackEntity` 缺少转换方法 |
| 补偿持久化 | **90%** | `summarizeDepartment` 全表扫描；`record()` 扩展方法未暴露 |
| 绩效持久化 | **80%** | 公司级排名因耦合失效；冗余存根实现；排名服务未独立 |

### 7.2 实施路线图整体完成度: **85%**

- Phase 1-3（持久化）: 基本完成，JPA 实现已作为 `@Primary` 切换
- Phase 4（桥接层）: 大部分完成，`PerformanceDashboardService` 耦合问题需修复
- Phase 5（文档对齐）: 结构已建立，内容同步需持续

### 7.3 框架歧义澄清完成度: **85%**

- 歧义点已全部在文档层面澄清
- 代码层面约 80% 已落地
- 主要遗留：编制vs实例约束、知识晋升门禁、熔断/回滚统一

### 7.4 高优先级待修复项

| # | 问题 | 影响 | 建议修复方式 |
|---|------|------|-------------|
| 1 | `PerformanceDashboardService` 的 `instanceof` 耦合 | 公司级排名功能失效 | 在 `PerformanceAssessmentService` 接口中新增 `getCompanyTopPerformers`/`getCompanyBottomPerformers` 方法，移除 `instanceof` 检查 |
| 2 | `PerformanceAssessmentServiceImpl` 冗余存根 | Spring 容器中存在无意义 Bean | 删除或标注 `@ConditionalOnMissingBean` |
| 3 | `JpaEmployeeCompensationService.summarizeDepartment` 全表扫描 | 数据量大时性能问题 | 改用 `SUM` 聚合查询 |
| 4 | `ProactiveController` 占位数据 | 前端展示不真实 | 逐步接入真实数据源 |
| 5 | `EmployeeCompensationService.record()` 扩展方法未暴露 | 任务审核无法传入溯源信息 | 在接口中新增带 `sourceTaskId`/`sourceReviewId` 的重载方法 |

### 7.5 中优先级待收敛项

| # | 问题 | 建议修复方式 |
|---|------|-------------|
| 6 | 习惯/会议纪要/预测未持久化 | 新增 JPA 实体和服务 |
| 7 | Dashboard 部分指标硬编码 | 接入真实服务数据 |
| 8 | 员工实例状态机未统一 | 在 `EmployeeService` 中定义统一状态转换 |
| 9 | 知识晋升门禁未代码级强制 | 在 `KnowledgePromotionAuditService.promote()` 中增加验证条件 |
| 10 | 进化熔断/回滚未统一 | 在 `EvolutionConstraints` 中增加熔断阈值和回滚策略 |
