# Dashboard 开发计划 (Dashboard Development Plan)

> **状态**: 基于 2026-04-23 实际代码核对更新  
> **设计文档**: `dashboard-redesign.md`

---

## 0. 当前开发状态总结

### 0.1 已完成（无需开发）

| 模块 | 状态 | 说明 |
|------|------|------|
| 后端 DTO | ✅ 完成 | `DashboardDTOs.java` - 8 个 record 完整 |
| 后端 Service | ✅ 完成 | `DashboardServiceImpl.java` - 所有方法已实现 |
| 后端 Controller | ✅ 完成 | `DashboardController.java` - 6 个端点完整 |
| 前端 API 层 | ✅ 完成 | `dashboardApi.ts` - 6 个方法 + TypeScript 接口 |
| 前端 Dashboard.tsx | ✅ 完成 | 三档路由分发逻辑完整 |
| 前端 EnterpriseDashboard | ✅ 完成 | StatsBar、DepartmentHealthTable、RiskAlertsPanel、QuickActions |
| 前端 DepartmentDashboard | ✅ 完成 | 6 大板块完整 |

### 0.2 需要完善（有代码但数据不完整）

| 模块 | 问题 | 优先级 | 预计工作量 |
|------|------|--------|-----------|
| CostAnalysis | 所有金额为 0 | P1 | 依赖计费系统 |
| Token 统计 | `totalTokensToday` 为 0 | P1 | 需要 Agent 追踪 |
| 战略建议 | 空列表 | P2 | 需要 LLM 集成 |
| 风险预警 | source/action 写死 | P2 | 需要感知联动 |

### 0.3 不存在/已删除（不需要开发）

| 旧计划中的任务 | 实际情况 |
|---------------|---------|
| "新建 DashboardController" | ✅ 已存在 |
| "新建 DashboardService" | ✅ 已存在 |
| "新建 dashboardApi.ts" | ✅ 已存在 |
| "从头构建 Dashboard 页面" | ✅ 已存在 3 个页面 |
| "CEO 专属控制器" | ✅ 已整合进 DashboardController |

---

## 1. Phase 1: 数据源完善（当前重点）

> 目标：让已存在的 UI 组件展示真实数据，而非 mock 值

### 1.1 接入 Token 统计

**影响文件**: `DashboardServiceImpl.java` → `buildTaskMetrics()`

**当前实现**:
```java
return new DashboardDTOs.TaskMetrics(
    totalTasks, stats.pendingCount(), (int) completedToday, 0, completionRate, 0);
//                                                                                ↑ totalTokensToday = 0
```

**完善方案**:
- 在 `TaskCheckout` 中添加 `getTokenStatistics()` 方法
- 或在 `AgentService` 中追踪 token 消耗
- 汇总今日所有 agent 的 token 使用量

**验收标准**: `GET /api/dashboard/enterprise/summary` 返回的 `taskMetrics.totalTokensToday` > 0

### 1.2 接入成本分析

**影响文件**: `DashboardServiceImpl.java` → `buildCostAnalysis()`

**当前实现**:
```java
return new DashboardDTOs.CostAnalysis(
    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
    BigDecimal.ZERO, 0.0, List.of()
);
```

**完善方案**:
- 定义计费规则 (token 单价、任务单价等)
- 在 `TaskCheckout` 或新建 `BillingService` 中统计成本
- 按内部/外部、部门等维度拆分

**验收标准**: `GET /api/dashboard/enterprise/costs` 返回非零成本数据

### 1.3 接入战略建议

**影响文件**: `DashboardServiceImpl.java` → `buildStrategicSuggestions()`

**当前实现**:
```java
return List.of();
```

**完善方案**:
- 基于系统健康度、员工活跃度、任务完成率等指标
- 调用 LLM 生成建议 (或使用规则引擎)
- 分类: 人员、任务、成本、风险

**验收标准**: `GET /api/dashboard/enterprise/summary` 返回至少 1-2 条建议

### 1.4 完善风险预警

**影响文件**: `DashboardServiceImpl.java` → `buildRiskAlerts()`

**当前实现**:
```java
alerts.add(new DashboardDTOs.RiskAlert(
    "alert-" + i,
    "WARNING",
    issue.getDescription(),
    issue.getDescription(),
    "system",  // ← 写死
    null,       // ← null
    issue.getDescription(),
    Instant.now()
));
```

**完善方案**:
- 接入 `HealthMonitor` 的真实 issue 分类
- 根据 issue 类型设置正确的 level (CRITICAL/WARNING/INFO)
- 关联到具体部门/员工
- 提供可操作建议 (action 字段)

**验收标准**: 风险预警包含准确的 level、department、action 字段

---

## 2. Phase 2: UI/UX 增强

> 目标：提升用户体验，添加可视化图表

### 2.1 添加趋势图表

**组件**: 新建 `TrendChart.tsx`

**图表类型**:
- 健康度趋势 (7 天/30 天)
- 任务完成趋势
- Token 消耗趋势
- 员工活跃度趋势

**技术方案**:
- 使用 `recharts` 或 `chart.js` 轻量图表库
- 数据来自后端新增的 `/api/dashboard/enterprise/trends` 端点

### 2.2 优化响应式布局

**当前问题**: 固定 4 列网格在小屏幕上会溢出

**完善方案**:
- 添加 CSS media queries
- 1200px → 3 列
- 900px → 2 列
- 600px → 1 列

### 2.3 添加加载骨架屏

**当前问题**: 加载时显示 "加载中" 文字，体验不佳

**完善方案**:
- 使用 CSS 动画骨架屏
- 在 `useQuery` 的 `isLoading` 状态时显示

---

## 3. Phase 3: 高级功能

> 目标：提供数据分析和管理能力

### 3.1 导出报表

**功能**: 将 Dashboard 数据导出为 PDF/CSV

**方案**:
- 前端使用 `jspdf` + `jspdf-autotable`
- 或后端生成 PDF 返回

### 3.2 自定义看板

**功能**: 用户可自定义看板布局和可见模块

**方案**:
- 用户偏好存储在 localStorage 或数据库
- 拖拽布局使用 `react-grid-layout`

### 3.3 告警通知

**功能**: 风险预警时推送通知

**方案**:
- 使用现有的 NotificationBar 系统
- 或添加浏览器通知

---

## 4. 开发任务清单（按优先级排序）

### P1 - 必须完成

| 序号 | 任务 | 涉及文件 | 验收标准 |
|------|------|---------|---------|
| 1 | 接入 Token 统计 | `DashboardServiceImpl.java` | `totalTokensToday` > 0 |
| 2 | 接入成本分析 | `DashboardServiceImpl.java` | 成本金额非零 |
| 3 | 完善风险预警字段 | `DashboardServiceImpl.java` | level/department/action 准确 |
| 4 | 添加战略建议 | `DashboardServiceImpl.java` | 返回 1-2 条建议 |

### P2 - 建议完成

| 序号 | 任务 | 涉及文件 | 验收标准 |
|------|------|---------|---------|
| 1 | 添加趋势图表 | 新建 `TrendChart.tsx` + 后端端点 | 图表正确渲染 |
| 2 | 优化响应式 | CSS 文件 | 各断点布局正常 |
| 3 | 添加骨架屏 | 各 Dashboard 组件 | 加载时显示骨架屏 |

### P3 - 可选完成

| 序号 | 任务 | 涉及文件 | 验收标准 |
|------|------|---------|---------|
| 1 | 导出报表 | 前端组件 | 可导出 PDF/CSV |
| 2 | 自定义看板 | 前端组件 + 存储 | 布局可保存 |
| 3 | 告警通知 | NotificationBar | 风险预警推送 |

---

## 5. 开发顺序建议

```
1. 先完善 DashboardServiceImpl 中的数据源 (P1-1~4)
   ↓
2. 验证 API 返回数据正确
   ↓
3. 优化前端 UI/UX (P2)
   ↓
4. 按需实现高级功能 (P3)
```

---

## 6. 测试策略

### 6.1 后端测试

- 单元测试: `DashboardServiceImpl` 各 build 方法
- 集成测试: `DashboardController` 端点 + 权限校验

### 6.2 前端测试

- 组件测试: StatsBar、DepartmentHealthTable、RiskAlertsPanel
- 集成测试: 路由分发逻辑 (Dashboard.tsx)
- E2E 测试: 完整用户流程

---

## 7. 里程碑

| 里程碑 | 内容 | 完成标准 |
|--------|------|---------|
| M1 | 数据源完善 | 所有 mock 值替换为真实数据 |
| M2 | UI 增强 | 图表、响应式、骨架屏完成 |
| M3 | 高级功能 | 导出、自定义、告警完成 |

---

## 8. 注意事项

1. **不要重复开发**: 所有后端 Controller/Service 和前端页面已存在，只需完善数据源
2. **保持代码风格**: 遵循现有 Java 和 TypeScript 代码规范
3. **权限控制**: 使用 `AccessGateService` 和 `user.access_level` 控制访问
4. **i18n**: 所有文本通过 i18n 键，不硬编码中文/英文
5. **CSS 变量**: 使用现有 CSS 变量，不新增硬编码颜色
