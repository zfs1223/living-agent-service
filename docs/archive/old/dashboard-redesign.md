# Dashboard 重新设计文档 (Dashboard Redesign)

> **状态**: 基于 2026-04-23 实际代码核对更新  
> **目标**: 让所有文档与 `living-agent-service` 实际代码完全对齐

---

## 0. 当前代码实际状态总结（核对结论）

### 0.1 已实现且稳定运行的

| 层级 | 文件 | 状态 |
|------|------|------|
| 后端 DTO | `DashboardDTOs.java` | ✅ 完整的 8 个 record（EnterpriseSummary、SystemHealth、EmployeeMetrics、TaskMetrics、CostAnalysis、DepartmentHealth、RiskAlert、StrategicSuggestion、DepartmentSummary、WorkspaceSummary） |
| 后端 Service | `DashboardServiceImpl.java` | ✅ 实现所有接口，数据来自 `EmployeeService`、`BrainRegistry`、`TaskCheckout`、`HealthMonitor`、`DepartmentRepository` |
| 后端 Controller | `DashboardController.java` | ✅ 6 个端点：`/overview`、`/enterprise/summary`、`/enterprise/departments`、`/enterprise/risks`、`/enterprise/costs`、`/department/{code}/summary`、`/employee/workspace` |
| 前端 API 层 | `dashboardApi.ts` | ✅ 6 个方法与后端完全对齐，TypeScript 接口定义完整 |
| 前端 Dashboard.tsx | `frontend/src/pages/Dashboard.tsx` | ✅ 三档路由逻辑：FULL 转 EnterpriseDashboard，有部门自动跳转部门页，其他显示工作台 |
| 前端 EnterpriseDashboard | `frontend/src/pages/Dashboard/EnterpriseDashboard.tsx` | ✅ 完整的 StatsBar、DepartmentHealthTable、RiskAlertsPanel、QuickActions |
| 前端 DepartmentDashboard | `frontend/src/pages/DepartmentDashboard.tsx` | ✅ 部门级 6 大板块：概览、活跃任务、团队成员、可用数字员工、知识库、快捷操作 |

### 0.2 已存在但数据为 mock/零值的

| 字段 | 当前值 | 原因 |
|------|--------|------|
| `CostAnalysis` 所有金额 | `BigDecimal.ZERO` | 尚未接入计费系统 |
| `TaskMetrics.totalTokensToday` | `0` | 未统计 token 消耗 |
| `StrategicSuggestion` | `List.of()` | 尚未实现 AI 战略建议生成 |
| `RiskAlert` 的 source/action | 写死 `"system"` / `null` | 尚未接入感知/异常检测联动 |

### 0.3 不存在/已删除的（文档需删除的内容）

| 旧设计中的概念 | 实际情况 |
|---------------|---------|
| CEO 专属 `/chairman` 频道 | 设计保留但当前用 `AccessLevel.FULL` 控制，不需要独立频道 |
| `CEOController.java` | 已改为 `DashboardController.java`，CEO 功能整合进 dashboard 端点 |
| "董事长驾驶舱"独立页面 | 已整合为 `EnterpriseDashboard.tsx`，通过 `user.access_level === 'FULL'` 触发 |

### 0.4 与文档差异

| 文档声称 | 实际情况 |
|---------|---------|
| "董事长需要特殊 `/chairman` WebSocket 频道" | 实际上使用 `/ws/dept/{dept}` 或 `/ws/agent` 加 `AccessLevel.FULL` 控制，不需要独立频道 |
| "需要新建 DashboardController" | 已存在，包含 6 个 REST 端点 |
| "需要新建 DashboardService" | 已存在，使用 `DashboardServiceImpl` |
| "需要新建 dashboardApi.ts" | 已存在，TypeScript 接口与后端完全对齐 |
| "Dashboard 页面需要从头构建" | `Dashboard.tsx` + `EnterpriseDashboard.tsx` + `DepartmentDashboard.tsx` 均已实现 |

---

## 1. 设计目标

为 living-agent-service 构建一套 **统一、现代化、数据驱动** 的 Dashboard 页面，满足以下需求：

1. **企业总览看板**: 面向 `AccessLevel.FULL` 用户，展示系统健康、人员、任务、部门健康度、风险预警
2. **部门级看板**: 面向部门成员，展示部门概览、活跃任务、团队成员、可用数字员工、知识库
3. **统一风格**: 基于 CSS 变量，暗黑主题，响应式
4. **多语言**: 所有文本通过 i18n 键
5. **实时数据**: 通过 REST API + 定时轮询 (refetchInterval)，暂不使用 WebSocket 推送
6. **可访问性**: ARIA 标签、键盘导航

---

## 2. 路由与角色映射

```text
/                          → 根据身份跳转
  → /enterprise            → INTERNAL_ENTERPRISE 或 FULL
  → /departments/{id}      → 有部门信息的用户
  → /workspace             → LIMITED/CHAT_ONLY 用户

/enterprise                → 企业总览 (EnterpriseDashboard)
/departments/{id}/overview → 部门看板 (DepartmentDashboard)
/dashboard                 → 通用 Dashboard (Dashboard.tsx 入口)
```

### 角色权限

| 用户标识 | 可见页面 | 数据来源 |
|---------|---------|---------|
| `INTERNAL_ENTERPRISE` / `FULL` | 企业总览 | `/api/dashboard/enterprise/summary` |
| 部门员工 | 部门看板 | `/api/dashboard/department/{code}/summary` |
| `LIMITED` / `CHAT_ONLY` | 个人工作台 | `/api/dashboard/employee/workspace` |

---

## 3. 后端 API 设计（当前实际）

### 3.1 DashboardController (`/api/dashboard`)

```
GET /api/dashboard/overview
  → DashboardDataService.buildOverview() (快速概览)

GET /api/dashboard/enterprise/summary
  → 需要 FULL 权限
  → 返回 EnterpriseSummary

GET /api/dashboard/enterprise/departments
  → 需要 FULL 权限
  → 返回 List<DepartmentHealth>

GET /api/dashboard/enterprise/risks
  → 需要 FULL 权限
  → 返回 List<RiskAlert>

GET /api/dashboard/enterprise/costs
  → 需要 FULL 权限
  → 返回 CostAnalysis

GET /api/dashboard/department/{code}/summary
  → 需要部门归属或 FULL 权限
  → 返回 DepartmentSummary

GET /api/dashboard/employee/workspace
  → 需要登录
  → 返回 WorkspaceSummary
```

### 3.2 响应格式

```json
//  ApiResponse<T> 包装
{ "success": true, "data": { ... } }

// EnterpriseSummary
{
  "generatedAt": "2026-04-23T15:00:00Z",
  "systemHealth": { "healthScore": 95.0, "status": "HEALTHY", "activeComponents": 12, "totalComponents": 13, "components": [...] },
  "employeeMetrics": { "totalEmployees": 50, "activeEmployees": 45, "riskEmployees": 3, "activationRate": 90.0, "digitalEmployees": 10, "humanEmployees": 40 },
  "taskMetrics": { "totalTasks": 200, "pendingTasks": 15, "completedToday": 30, "failedTasks": 2, "completionRate": 85.5, "totalTokensToday": 50000 },
  "costAnalysis": { "totalCosts": 0, "internalCosts": 0, "externalBounties": 0, "pendingBounties": 0, "costPerTask": 0, "outsourcingRate": 0.0, "breakdowns": [] },
  "departmentHealth": [ { "code": "tech", "name": "技术部", "memberCount": 15, "activeMembers": 12, "todayTasks": 30, "todayTokens": 10000, "healthScore": 80.0, "status": "HEALTHY", "riskCount": 1 } ],
  "riskAlerts": [ { "alertId": "alert-0", "level": "WARNING", "title": "...", "message": "...", "department": "system", "employeeId": null, "impact": "...", "detectedAt": "2026-04-23T15:00:00Z" } ],
  "strategicSuggestions": []
}
```

---

## 4. 前端架构

### 4.1 技术栈

| 层级 | 技术 |
|------|------|
| 框架 | React 18 + TypeScript |
| 数据获取 | @tanstack/react-query (useQuery, refetchInterval) |
| 路由 | react-router-dom v6 |
| i18n | react-i18next |
| 状态管理 | zustand (useAuthStore) |
| 样式 | CSS-in-JS (inline style + CSS 变量) |

### 4.2 文件结构

```
frontend/src/
├── pages/
│   ├── Dashboard.tsx              # 主入口，三档路由分发
│   ├── Dashboard/
│   │   └── EnterpriseDashboard.tsx  # 企业总览页面
│   └── DepartmentDashboard.tsx     # 部门级看板
├── services/
│   ├── dashboardApi.ts             # Dashboard API 客户端 + TypeScript 接口
│   └── api.ts                      # 通用 API 客户端 (agentApi, taskApi, activityApi)
└── stores/
    └── auth.ts                     # useAuthStore (token, user, login, logout)
```

### 4.3 Dashboard.tsx 路由逻辑

```typescript
export default function Dashboard() {
    // 1. FULL 身份 → 企业总览
    if (user.identity === 'INTERNAL_ENTERPRISE' || user.access_level === 'FULL') {
        return <EnterpriseDashboard />;
    }
    
    // 2. 有部门 → 跳转部门看板
    if (user.department_id) {
        navigate(`/departments/${encodeURIComponent(user.department_id)}/overview`, { replace: true });
        return null;
    }
    
    // 3. LIMITED/CHAT_ONLY → 个人工作台
    if (user.access_level === 'LIMITED' || user.access_level === 'CHAT_ONLY') {
        return <WorkspacePlaceholder />;
    }
    
    // 4. 默认 → Agent 列表看板 (StatsBar + AgentRow + ActivityFeed)
    return <AgentDashboard />;
}
```

### 4.4 EnterpriseDashboard.tsx 组件结构

```
EnterpriseDashboard
├── Header (标题 + Current Focus + System Health + Active Risks)
├── StatsBar (8 个 KPI 卡片，分两行)
│   ├── 第一行: 员工总数、待处理任务、Token 消耗、健康分数
│   └── 第二行: 总任务数、风险信号、成本提示、数字员工
├── DepartmentHealthTable (部门健康度表格，按健康分数排序)
├── RiskAlertsPanel (风险预警列表)
└── QuickActions (快捷操作按钮)
```

### 4.5 DepartmentDashboard.tsx 组件结构

```
DepartmentDashboard
├── Header (部门名称 + 健康分数 + 概览统计)
├── ActiveTasksTable (部门内活跃任务)
├── MembersTable (部门成员列表)
├── AvailableAgents (可用数字员工列表)
├── KnowledgeSection (部门知识库)
└── QuickActions (快捷操作)
```

---

## 5. 组件设计规范

### 5.1 StatsBar 组件

- 两行 4 列网格布局
- 每个卡片: icon + label + value + subtext
- 数据来自 `/api/dashboard/enterprise/summary`
- `refetchInterval: 30000` (30 秒刷新)

### 5.2 DepartmentHealthTable 组件

- 表格列: 部门名称、成员数、活跃成员、今日任务、健康分数、状态
- 按健康分数降序排序
- 点击行跳转到 `/departments/{code}/overview`
- 状态颜色: HEALTHY → green, WARNING → orange, CRITICAL → red

### 5.3 RiskAlertsPanel 组件

- 列表展示风险预警
- 每条包含: 级别标签、标题、消息、部门、检测时间
- 级别颜色: CRITICAL → red, WARNING → orange, INFO → blue
- 暂无数据时显示空状态提示

### 5.4 AgentRow 组件 (通用 Dashboard)

- 网格布局: Agent 信息 | 最新活动/任务 | Token 使用 | 最后活跃时间
- 点击跳转到 Agent 详情页
- Token 使用率进度条

### 5.5 ActivityFeed 组件

- 时间线展示活动流
- 包含: 时间、Agent 名称、活动摘要
- 最多展示 20 条

---

## 6. 样式规范

### 6.1 CSS 变量 (暗黑主题)

```css
:root {
  --bg-primary: #05060a;
  --bg-secondary: #0c121c;
  --bg-tertiary: #151b26;
  --bg-hover: rgba(255,255,255,0.06);
  
  --text-primary: rgba(255,255,255,0.96);
  --text-secondary: rgba(255,255,255,0.72);
  --text-tertiary: rgba(255,255,255,0.48);
  
  --accent-primary: #1890ff;
  --success: #52c41a;
  --warning: #faad14;
  --error: #ff4d4f;
  
  --border-subtle: rgba(255,255,255,0.08);
  
  --status-running: var(--success);
  --status-idle: var(--warning);
  --status-error: var(--error);
  --status-stopped: var(--text-tertiary);
  
  --radius-sm: 6px;
  --radius-md: 10px;
  --radius-lg: 18px;
  --radius-xl: 24px;
}
```

### 6.2 响应式断点

```css
/* 桌面端: 4 列网格 */
@media (max-width: 1200px) {
  /* 表格端: 3 列网格 */
}

@media (max-width: 900px) {
  /* 平板端: 2 列网格 */
}

@media (max-width: 600px) {
  /* 移动端: 1 列 */
}
```

---

## 7. i18n 键规划

```typescript
{
  "dashboard": {
    "stats": {
      "totalEmployees": "员工总数",
      "activeEmployees": "活跃员工",
      "pendingTasks": "待处理任务",
      "completedToday": "今日完成 {{count}}",
      "todayTokens": "今日 Tokens",
      "allAgentsTotal": "所有智能体总计",
      "healthScore": "健康分数"
    },
    "enterprise": {
      "title": "企业经营总览",
      "subtitle": "这里汇总公司运转、任务执行、数字员工产出、风险信号和成本趋势，帮助企业管理者快速判断业务健康度与战略节奏。",
      "overviewBadge": "Enterprise Operations Overview",
      "focus": "Current Focus",
      "focusValue": "公司运行与收益监控",
      "systemHealth": "System Health",
      "activeRisks": "Active Risks",
      "totalTasks": "Total Tasks",
      "riskSignals": "Risk Signals",
      "costHint": "Cost / Tokens",
      "digitalEmployees": "Digital Employees",
      "quickActions": "Quick Actions",
      "settings": "Company Settings"
    },
    "department": {
      "title": "部门看板",
      "healthScore": "健康分数",
      "activeTasks": "活跃任务",
      "members": "团队成员",
      "availableAgents": "可用数字员工",
      "knowledgeBase": "知识库"
    },
    "table": {
      "department": "部门",
      "members": "成员数",
      "activeMembers": "活跃成员",
      "todayTasks": "今日任务",
      "healthScore": "健康分数",
      "status": "状态",
      "agent": "智能体",
      "latestActivity": "最新活动",
      "active": "最后活跃"
    },
    "riskAlerts": "风险预警",
    "alerts": "条预警",
    "departmentHealth": "Department Health",
    "sortedByHealth": "Sorted by health score",
    "noDepartments": "暂无部门数据",
    "noAlerts": "暂无风险预警",
    "noActivity": "暂无活动记录",
    "noAgents": "暂无智能体",
    "loadError": "加载失败",
    "workspace": {
      "title": "个人工作台",
      "subtitle": "查看您的任务和可访问的数字员工"
    }
  }
}
```

---

## 8. 数据流架构

```
用户访问 /dashboard
    ↓
Dashboard.tsx 判断用户身份
    ↓
    ├─ FULL → EnterpriseDashboard.tsx
    │           ↓
    │       useQuery → dashboardApi.getEnterpriseSummary()
    │           ↓
    │       GET /api/dashboard/enterprise/summary
    │           ↓
    │       DashboardController → DashboardService → DashboardServiceImpl
    │           ↓
    │       EmployeeService + BrainRegistry + TaskCheckout + HealthMonitor + DepartmentRepository
    │
    ├─ 有部门 → navigate /departments/{id}/overview
    │           ↓
    │       DepartmentDashboard.tsx
    │           ↓
    │       useQuery → dashboardApi.getDepartmentSummary(code)
    │           ↓
    │       GET /api/dashboard/department/{code}/summary
    │
    └─ LIMITED/CHAT_ONLY → 个人工作台
                ↓
            useQuery → dashboardApi.getWorkspaceSummary()
                ↓
            GET /api/dashboard/employee/workspace
```

---

## 9. 已实现功能清单

### 9.1 企业总览 (EnterpriseDashboard)

- [x] StatsBar - 8 个 KPI 卡片 (员工、任务、Token、健康度、风险、成本)
- [x] DepartmentHealthTable - 部门健康度表格
- [x] RiskAlertsPanel - 风险预警列表
- [x] QuickActions - 快捷操作按钮
- [x] Header - 标题 + 焦点卡片 + 系统健康 + 风险计数
- [x] 30 秒定时刷新
- [x] i18n 多语言
- [x] 权限控制 (FULL 访问)

### 9.2 部门看板 (DepartmentDashboard)

- [x] 部门概览 (健康分数、活跃任务、成员数)
- [x] 活跃任务表格
- [x] 成员列表
- [x] 可用数字员工列表
- [x] 知识库区域 (展示文档数量)
- [x] 快捷操作
- [x] 权限控制 (部门成员或 FULL)

### 9.3 通用 Dashboard

- [x] StatsBar (Agent 统计 + 企业信号)
- [x] AgentRow (Agent 信息 + 活动 + Token + 最后活跃)
- [x] ActivityFeed (全局活动流)
- [x] 身份路由分发
- [x] 空状态处理

### 9.4 后端 API

- [x] `/api/dashboard/overview` - 快速概览
- [x] `/api/dashboard/enterprise/summary` - 企业总览
- [x] `/api/dashboard/enterprise/departments` - 部门健康
- [x] `/api/dashboard/enterprise/risks` - 风险预警
- [x] `/api/dashboard/enterprise/costs` - 成本分析
- [x] `/api/dashboard/department/{code}/summary` - 部门摘要
- [x] `/api/dashboard/employee/workspace` - 工作区摘要

---

## 10. 待完善功能 (按优先级排序)

### P1 - 数据源完善

| 字段 | 当前值 | 目标 | 依赖 |
|------|--------|------|------|
| `CostAnalysis` 所有金额 | `BigDecimal.ZERO` | 接入真实计费数据 | 计费系统 |
| `TaskMetrics.totalTokensToday` | `0` | 统计 Token 消耗 | Agent token 追踪 |
| `StrategicSuggestion` | 空列表 | AI 生成战略建议 | LLM 调用 |
| `RiskAlert` 的 source/action | 写死 | 接入感知/异常检测 | 感知系统联动 |

### P2 - UI/UX 增强

| 功能 | 描述 | 优先级 |
|------|------|--------|
| 图表可视化 | 添加健康度趋势图、任务完成趋势图 | 中 |
| 实时更新 | WebSocket 推送替代轮询 | 低 |
| 导出功能 | 导出报表为 PDF/CSV | 低 |
| 自定义看板 | 用户可自定义看板布局 | 低 |

### P3 - 高级功能

| 功能 | 描述 | 优先级 |
|------|------|--------|
| 对比分析 | 部门间/时间对比 | 低 |
| 预测分析 | 基于历史数据预测趋势 | 低 |
| 告警通知 | 风险预警推送通知 | 中 |

---

## 11. 关键实现建议

### 11.1 统一 API 客户端

```typescript
// dashboardApi.ts 已实现统一 request 函数
// - 自动注入 Authorization token
// - 自动注入 X-Employee-Id
// - 401 自动跳转登录
// - 提取 ApiResponse.data
```

### 11.2 权限控制

```typescript
// 前端: 根据 user.access_level 控制可见内容
// 后端: 使用 accessGateService 校验权限
// 不需要独立的 CEO 控制器或频道
```

### 11.3 数据刷新策略

```typescript
// EnterpriseDashboard: 30 秒刷新
// DepartmentDashboard: 60 秒刷新
// AgentDashboard: 15 秒刷新 agents, 30 秒刷新 activities
// 暂不使用 WebSocket 推送
```

---

## 12. 与 CEO 频道的关系

CEO 频道 (`/ws/chairman`) 在架构中保留，但 Dashboard 功能已完全整合：

- `/api/dashboard/enterprise/*` 端点替代了 `CEOController`
- `AccessLevel.FULL` 控制访问，不需要独立频道
- 未来如需 CEO 专属实时推送，可在 WebSocket 层添加

---

## 13. 开发计划参考

开发计划见 `dashboard-development-plan.md`。

核心原则：
1. 先完善数据源，再增强 UI
2. 先实现核心功能，再做高级特性
3. 保持与现有代码风格一致
4. 所有文本通过 i18n 键
5. 响应式设计
