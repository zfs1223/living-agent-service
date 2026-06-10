# 数据组织与职责卡加载改进计划

> 创建日期：2026-06-03
> 范围：`data/` 目录组织、`documents/` 职责卡加载、员工任务追溯
> 前置依赖：现有 `EmployeeExecutionReceiptEntity`、`EmployeeExecutionReceiptRepository`、`ResponsibilityCardService`、`MemoryToKnowledgeExtractor`、三层知识库（L1/L2/L3）

---

## 一、问题分析

### 1.1 `data/` 目录 - 运行时数据组织

**现状**：

| 目录 | 当前结构 | 问题 |
|------|---------|------|
| `data/artifacts/` | `{dept}/{executionId}/` | 缺少员工维度，无法按员工查询产物 |
| `data/receipts/` | `{executionId}.json` | 包含所有员工 receipt，但没有员工索引 |
| `data/tasks/` | `{dept}/{executionId}/events.jsonl` | 按部门和 executionId 组织 |
| `data/conversations/` | `{conversationId}/events.jsonl` | 按 conversationId 组织 |
| `data/projects/` | `{dept}/{executionId}/events.jsonl` | 项目事件流 |

**关键问题**：
- 数字员工**无法直接查询**自己的任务历史和产物
- 部门大脑**无法直接看到**本部门的任务进展概况
- artifacts 缺少员工归属，**多员工协作时无法区分**谁的产物
- 数据库已支持按 `employeeCode` 查询（`EmployeeExecutionReceiptRepository.findByEmployeeCode`），但**目录结构未对齐**

### 1.2 `documents/` 目录 - 企业知识库

**现状**：
- `documents/department/{dept}/` - 部门政策/流程/模板（已存在但未启用）
- `documents/shared/company/hr-20~26-*.md` - 部门固定员工职责卡
- `documents/shared/governance/` - 治理文档
- `ResponsibilityCardService` - 已实现职责卡管理

**关键问题**（StandardLoadingChainService.java:159-171）：
```java
private String resolveDutyCardPath(String department) {
    return switch (department.toLowerCase()) {
        case "tech" -> "duty-cards/tech";      // ❌ 错误路径
        // 实际文件在: documents/shared/company/hr-22-tech-fixed-employee-duty-card.md
    };
}
```

- 路径硬编码错误，**职责卡根本加载不到**
- 路径格式不统一（`duty-cards/{dept}` vs `hr-22-tech-fixed-employee-duty-card.md`）
- 员工**不知道自己的职责卡**位置，需要自动发现

### 1.3 docker-compose.yml 卷映射（参考）

```yaml
volumes:
  - ./data:/app/data
  - ./documents:/app/documents      # 文档型资料文件夹（本地可直接查看/编辑）
  - ./data/knowledge:/app/data/knowledge
  - ./data/department-knowledge:/app/data/department-knowledge
  - ./data/personal-knowledge:/app/data/personal-knowledge
```

**注意到**：
- `data/knowledge/`、`data/department-knowledge/`、`data/personal-knowledge/` 三个目录已在挂载中
- 对应知识库 L3/L2/L1 三层架构，但**实际未使用**或分散存储
- 应该将现有 `data/` 目录重构以匹配此设计意图

---

## 二、改进目标

### 2.1 数据组织目标

1. **员工视角**：每个数字员工能直接查看自己的任务历史、产物、进展
2. **部门视角**：每个部门大脑能看到本部门的任务概览和员工负载
3. **职责卡自动加载**：员工启动时自动发现并加载自己的职责卡
4. **知识库三层对齐**：L1/L2/L3 与目录结构对应

### 2.2 目标目录结构

```
data/
├── artifacts/                              # 任务产物
│   ├── by-execution/                       # 按执行ID
│   │   └── {executionId}/
│   │       └── {employeeCode}/
│   │           └── {filename}
│   └── by-employee/                        # 按员工（软链接/索引）
│       └── {employeeCode}/
│           └── {executionId}/{filename}
├── receipts/                               # 执行回执
│   ├── by-execution/
│   │   └── {executionId}.json
│   └── by-employee/                        # 新增：按员工索引
│       └── {employeeCode}/
│           └── {executionId}.json
├── tasks/                                  # 任务事件流（保持）
│   └── {dept}/{executionId}/events.jsonl
├── conversations/                          # 对话历史（保持）
│   └── {conversationId}/events.jsonl
├── projects/                               # 项目事件流（保持）
│   └── {dept}/{executionId}/events.jsonl
├── personal-knowledge/                     # L1 个人知识（已有挂载）
│   └── {employeeCode}/
│       ├── experiences.jsonl
│       └── learnings.jsonl
├── department-knowledge/                   # L2 部门知识（已有挂载）
│   └── {dept}/
│       ├── best-practices.jsonl
│       └── procedures.jsonl
├── knowledge/                              # L3 共享知识（已有挂载）
│   ├── policies/
│   └── governance/
└── duty-cards/                             # 职责卡软链接
    └── {dept}.md → documents/shared/company/hr-2X-{dept}-fixed-employee-duty-card.md

documents/
├── shared/
│   ├── company/
│   │   ├── hr-20~26-*-fixed-employee-duty-card.md  # 现有职责卡
│   │   └── duty-cards/                             # 新增：简化命名的职责卡
│   │       ├── hr.md → hr-20-hr-fixed-employee-duty-card.md
│   │       ├── tech.md → hr-22-tech-fixed-employee-duty-card.md
│   │       └── ...
│   └── governance/                                  # 现有治理文档
└── department/
    └── {dept}/                                       # 现有部门文档
```

---

## 三、具体改进项

### P0 - 必须实现

#### 改进 1：修复职责卡路径解析

**文件**：`living-agent-core/src/main/java/com/livingagent/core/brain/prompt/StandardLoadingChainService.java:159-171`

**修改内容**：
```java
private String resolveDutyCardPath(String department) {
    if (department == null) return null;
    // 优先匹配简化路径（新增软链接）
    String shortPath = "documents/shared/company/duty-cards/" + department.toLowerCase() + ".md";
    if (resourceExists(shortPath)) {
        return shortPath;
    }
    // 兜底：完整路径
    return switch (department.toLowerCase()) {
        case "hr" -> "documents/shared/company/hr-20-hr-fixed-employee-duty-card.md";
        case "finance" -> "documents/shared/company/hr-21-finance-fixed-employee-duty-card.md";
        case "tech" -> "documents/shared/company/hr-22-tech-fixed-employee-duty-card.md";
        case "sales" -> "documents/shared/company/hr-23-sales-fixed-employee-duty-card.md";
        case "ops", "admin" -> "documents/shared/company/hr-24-ops-fixed-employee-duty-card.md";
        case "cs" -> "documents/shared/company/hr-25-cs-fixed-employee-duty-card.md";
        case "legal" -> "documents/shared/company/hr-26-legal-fixed-employee-duty-card.md";
        default -> null;
    };
}
```

#### 改进 2：artifacts 按员工组织

**文件**：`living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/ToolBackedEmployeeTaskExecutor.java`

**修改内容**：
- 写入路径从 `data/artifacts/{dept}/{executionId}/{filename}`
- 改为 `data/artifacts/by-execution/{executionId}/{employeeCode}/{filename}`
- 同时在 `data/artifacts/by-employee/{employeeCode}/{executionId}/{filename}` 建立软链接（如果平台支持）或写索引文件

**兼容策略**：
- 保留旧路径读取能力
- 新任务写入新路径
- 提供 `migrate_artifacts.py` 迁移脚本

#### 改进 3：receipts 按员工索引

**文件**：新增 `EmployeeReceiptIndexService`

**功能**：
- receipt 写入时同时在 `data/receipts/by-employee/{employeeCode}/{executionId}.json` 写入副本
- 提供 `getEmployeeReceipts(employeeCode, limit)` 快速查询
- 提供 `getDepartmentReceipts(dept, limit)` 部门查询

### P1 - 重要功能

#### 改进 4：员工任务历史查询 API

**文件**：`living-agent-gateway/src/main/java/com/livingagent/gateway/controller/TaskController.java`

**新增端点**：
```
GET /api/tasks/executions/my              # 数字员工查询自己的任务历史
GET /api/tasks/executions/{executionId}/progress  # 任务整体进展
GET /api/tasks/executions/department/{dept}       # 部门任务列表
```

**响应结构**：
```json
{
  "code": 0,
  "data": {
    "executions": [
      {
        "receiptId": "...",
        "executionId": "...",
        "employeeCode": "T02",
        "status": "COMPLETED",
        "summary": "...",
        "completedAt": "2026-06-03T10:00:00Z",
        "modelName": "gpt-4o",
        "modelProvider": "openai"
      }
    ]
  }
}
```

#### 改进 5：前端员工任务进展展示

**文件**：`frontend/src/components/EmployeeStationCard.tsx` 或新建 `MyExecutionsPanel.tsx`

**功能**：
- 在员工卡片中显示"我的任务"入口
- 点击展开该员工的任务历史列表
- 显示状态、摘要、模型、产物链接
- 支持跳转查看产物（HTML 预览 / Markdown 渲染）

#### 改进 6：WebSocket 推送员工任务进展

**文件**：`living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java`

**新增消息类型**：
```json
{
  "type": "employee_task_update",
  "employeeCode": "T02",
  "executionId": "...",
  "status": "RUNNING|COMPLETED|FAILED",
  "timestamp": "..."
}
```

**推送时机**：
- receipt 写入时推送
- 任务状态变更时推送
- 产物生成完成时推送

### P2 - 增强功能

#### 改进 7：部门任务统计仪表板

**文件**：`frontend/src/pages/DepartmentDashboard.tsx`

**功能**：
- 本部门任务总数、成功率、平均耗时
- 员工任务负载热力图
- 模型使用分布
- 产物统计

#### 改进 8：知识库三层目录对齐

**文件**：`living-agent-core/src/main/java/com/livingagent/core/knowledge/impl/KnowledgeManagerImpl.java`

**修改内容**：
- `storePrivate()` 写入 `data/personal-knowledge/{employeeCode}/`
- `storeDomain()` 写入 `data/department-knowledge/{dept}/`
- `storeShared()` 写入 `data/knowledge/`
- 启动时检查目录存在性并创建

#### 改进 9：员工个人知识库

**文件**：`living-agent-core/src/main/java/com/livingagent/core/employee/EmployeePersonalKnowledgeService.java`（新增）

**功能**：
- 每个员工拥有独立的 `data/personal-knowledge/{employeeCode}/` 目录
- 任务完成后自动提取经验到个人知识库
- 知识晋升时通知员工
- 跨会话记忆持久化

---

## 四、实施顺序

| 步骤 | 改进项 | 预估风险 | 依赖 |
|------|--------|----------|------|
| 1 | 改进 1：修复职责卡路径 | 低 | 无 |
| 2 | 改进 2：artifacts 按员工组织 | 中 | 路径迁移 |
| 3 | 改进 3：receipts 按员工索引 | 低 | 无 |
| 4 | 改进 4：员工任务历史 API | 低 | 改进 3 |
| 5 | 改进 5：前端任务进展展示 | 中 | 改进 4 |
| 6 | 改进 6：WebSocket 推送 | 低 | 改进 3 |
| 7 | 改进 7：部门仪表板 | 中 | 改进 4-6 |
| 8 | 改进 8：知识库目录对齐 | 高 | 知识库重构 |
| 9 | 改进 9：员工个人知识库 | 中 | 改进 8 |

---

## 五、风险与兼容性

### 5.1 数据迁移

- `data/artifacts/` 旧结构需要保留或迁移
- `data/receipts/` 旧 JSON 文件继续保留
- 新数据写入新结构，旧数据可读

### 5.2 API 兼容

- 现有 API 路径保持不变
- 新增 API 路径使用 `/executions/` 前缀
- 前端先适配新 API，老路径保留 1-2 版本

### 5.3 软链接兼容性

- Windows 软链接需要管理员权限
- 采用文件复制而非软链接（妥协方案）
- 容器内使用硬链接（Linux 支持）

### 5.4 知识库目录

- `data/personal-knowledge/`、`data/department-knowledge/`、`data/knowledge/` 已在 docker-compose 中挂载
- 需要保证代码实际使用这些目录
- 数据库层（L1/L2/L3）可保持不变

---

## 六、验证标准

### 6.1 职责卡加载

- [ ] 员工启动时 `ResponsibilityCardService.loadDutyCard(dept)` 成功
- [ ] 加载的职责卡内容包含部门核心职责
- [ ] 加载失败有明确错误日志

### 6.2 任务历史查询

- [ ] `GET /api/tasks/executions/my` 返回当前员工的执行记录
- [ ] `GET /api/tasks/executions/{id}/progress` 返回整体进展
- [ ] 数字员工可在 WebSocket 中收到自己的任务更新

### 6.3 产物追溯

- [ ] artifacts 目录按员工可查找
- [ ] receipt 索引可按员工查询
- [ ] 前端可点击查看产物

### 6.4 知识库三层

- [ ] L1 知识写入 `data/personal-knowledge/{employeeCode}/`
- [ ] L2 知识写入 `data/department-knowledge/{dept}/`
- [ ] L3 知识写入 `data/knowledge/`
- [ ] 目录不存在时自动创建

---

## 七、附录：现有已实现能力

### 7.1 员工回执查询（已有）

```java
// EmployeeExecutionReceiptRepository
List<EmployeeExecutionReceiptEntity> findByEmployeeCode(String employeeCode)
List<EmployeeExecutionReceiptEntity> findByExecutionId(String executionId)
List<EmployeeExecutionReceiptEntity> findByExecutionIdAndStatus(executionId, status)
```

### 7.2 职责卡服务（已有）

```java
// ResponsibilityCardService
loadDutyCard(String department)         // 加载部门职责卡
saveDutyCard(...)                       // 保存职责卡
getResponsibilityCard(String employeeCode)  // 获取员工职责卡
```

### 7.3 三层知识库（已有）

```java
// KnowledgeManager
storePrivate(key, content, neuronId, brainDomain)  // L1
storeDomain(key, content, department, brainDomain) // L2
storeShared(key, content, brainDomain)             // L3
```

### 7.4 记忆到知识提取（已有）

```java
// MemoryToKnowledgeExtractor
extractHighValueMemories()  // 每小时执行
recordRecall(String key)    // 记录召回次数
```

---

## 八、决策点

请确认以下设计决策：

1. **artifacts 路径迁移策略**：是双写兼容，还是强制迁移？
2. **职责卡路径**：采用简化路径 + 兜底，还是直接修改为完整路径？
3. **员工任务历史 API 权限**：需要鉴权吗？数字员工能查询自己即可？
4. **前端展示位置**：在员工卡片中嵌入，还是独立页面？

---

## 九、实施进度（2026-06-03 更新）

| 改进项 | 状态 | 备注 |
|--------|------|------|
| P0-1: 修复职责卡路径解析 | ✅ 已完成 | 新增 `loadFile/exists` 方法到 `InstructionFileLoader`；`resolveDutyCardPath` 优先匹配简化路径；创建 `documents/shared/company/duty-cards/{dept}.md` 副本 |
| P0-2: artifacts 按员工组织 | ✅ 已完成 | `saveArtifactFile` 双写到 `by-execution/` 和 `by-employee/`，兼容旧路径 |
| P0-3: receipts 按员工索引 | ✅ 已完成 | `persistExecution` 额外写入 `data/receipts/by-employee/{employeeCode}/{executionId}.json` |
| P1-4: 员工任务历史 API | ✅ 已完成 | `TaskController` 中 `/api/tasks/executions/my` / `/progress` / `/department/{dept}` 已实现 |
| P1-5: 前端任务进展展示 | ✅ 已完成 | `frontend/src/services/api.ts` 新增 `globalTaskApi.getMyExecutions/getExecutionProgress/getDepartmentExecutions` |
| P1-6: WebSocket 推送员工任务进展 | ✅ 已完成 | `DepartmentWebSocketHandler.pushEmployeeTaskUpdate()` 推送 `employee_task_update` 消息 |
| P2-7: 部门任务统计仪表板 | ⏭️ 跳过 | 与当前主任务不相关，需要新的前端页面 |
| P2-8: 知识库三层目录对齐 | ✅ 已完成 | 新增 `KnowledgeFileMirrorService`，按 L1/L2/L3 镜像到 `data/personal-knowledge/`、`data/department-knowledge/`、`data/knowledge/` |
| P2-9: 员工个人知识库 | ✅ 已完成 | 集成到 P2-8，通过 `writePrivateKnowledge(employeeCode, ...)` 写入员工个人目录 |

### 已修改文件

- `living-agent-core/src/main/java/com/livingagent/core/brain/prompt/InstructionFileLoader.java` - 新增 `loadFile/exists` 方法
- `living-agent-core/src/main/java/com/livingagent/core/brain/prompt/StandardLoadingChainService.java` - 修复职责卡路径
- `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/ToolBackedEmployeeTaskExecutor.java` - artifacts 按员工组织
- `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/FileBasedEmployeeExecutionReceiptService.java` - receipts 按员工索引
- `living-agent-core/src/main/java/com/livingagent/core/knowledge/KnowledgeFileMirrorService.java` - **新增** 知识库文件镜像服务
- `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/TaskController.java` - 修复 `toReceiptSummary` 字段访问
- `living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java` - 新增 `pushEmployeeTaskUpdate`
- `frontend/src/services/api.ts` - 新增 execution API 方法
- `documents/shared/company/duty-cards/*.md` - **新增** 简化命名的职责卡

### 验证

- `mvn compile` 通过，无编译错误

---

## 十、前端虚拟办公室展示优化（保持 Office 架构）

### 10.1 现有前端架构分析

**目录结构**：`frontend/src/pages/DepartmentDetail/`

| 文件 | 职责 |
|------|------|
| `DepartmentDetail.tsx` | 部门详情主页（路由入口、Tab 切换、加载数据） |
| `DepartmentTabs.tsx` | 标签页（"办公室总览" + "公共任务栏"） |
| `DepartmentHero.tsx` | 顶部部门头图 |
| `DepartmentBrainPanel.tsx` | 部门大脑面板 |
| `EmployeeStationCard.tsx` | 单个员工工位卡（虚拟办公室核心元素） |
| `EmployeeStationGrid.tsx` | 员工工位卡网格 |
| `DepartmentActivityFeed.tsx` | 部门动态 Feed |
| `ActivityTimeline.tsx` | 状态流时间轴 |
| `OverviewKpis.tsx` | 概览 KPI 卡片 |
| `OfficeFloor.tsx` / `OfficeFloorGrid.tsx` | 楼层布局 |
| `LoungeStrip.tsx` | 休息区条 |
| `useOfficePresence.ts` | 状态/事件 Hook（管理 zones/transitions/timeline） |
| `officeMotion.ts` | 动作/严重度/分类定义 |
| `officeEventAdapter.ts` | 后端事件 → OfficeEvent 适配器 |
| `status.ts` | 状态 → 区域映射 |

**现有数据流**：
1. `DepartmentDetail.tsx` 调用 `fixedEmployeeApi` / `agentApi` 加载员工
2. `useOfficePresence` 接收后端事件并维护虚拟办公室 zones（workstation/lounge/collaboration/alert/offline）
3. `EmployeeStationCard` 渲染每个员工的当前状态、当前任务

**OfficeEvent 事件类型**：
```typescript
action: 'arrive' | 'walk' | 'sit' | 'return' | 'alert' | 'offline' | 'task-start' | 'task-complete'
category: 'movement' | 'status' | 'task' | 'alert'
severity: 'info' | 'success' | 'warning' | 'error'
```

### 10.2 现状与差距

| 维度 | 现状 | 差距 |
|------|------|------|
| 员工任务状态 | `EmployeeStationCard.current_task` 字符串 | 没有产物链接、模型、进度、ETA |
| 任务历史 | 无展示 | P0-3 已实现后端按员工索引，前端无 UI |
| 任务进展 | 无展示 | P0-3 已实现 receipt，WebSocket `employee_task_update` 已实现 |
| 产物展示 | 无 | P0-2 已实现按员工组织的 artifacts，前端无法访问 |
| 个人知识库 | 无 | P2-8 已实现 L1 文件镜像 |
| 职责卡展示 | 无 | P0-1 已修复职责卡路径，员工无法看到自己的职责卡内容 |
| 部门任务列表 | `DepartmentTabs` 只有"公共任务栏" | 没有"我的任务"和"部门执行历史" |
| KPI 概览 | `OverviewKpis` 仅显示数字 | 没有任务成功率、平均耗时、模型分布等 |

### 10.3 改进方案（保持虚拟办公室架构）

#### 改进 10.1：在 `EmployeeStationCard` 中展示任务进展

**设计原则**：保持工位卡视觉风格，仅增强信息密度。

**新增字段**（扩展 `AgentLike` 类型）：
```typescript
export type AgentLike = {
  // ... 现有字段
  currentTask?: {
    executionId: string;
    status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'BLOCKED';
    summary: string;
    modelName?: string;
    startedAt: string;
    artifactUrl?: string;      // P0-2 产物路径
    progress?: number;          // 0-100
  };
  recentExecutions?: number;    // 最近 24h 完成数
  successRate?: number;         // 0-1
};
```

**UI 改造**：在工位卡 `.station-card__body` 中增加任务指示器
- 状态色条（按 `status` 上色）
- 进度条（如果 `progress` 有值）
- 模型小标签（右下角）
- 产物图标（点击打开新窗口预览 HTML / 渲染 Markdown）

#### 改进 10.2：扩展 `OfficeEvent` 支持 receipt 事件

**扩展 `officeMotion.ts` 的事件类型**：
```typescript
action: ... | 'receipt-arrived' | 'artifact-ready' | 'knowledge-promoted'
category: ... | 'receipt' | 'artifact' | 'knowledge'
```

**新增 `BackendOfficeEvent` 字段**：
```typescript
{
  type: 'employee_task_update',           // 对应 WebSocket 推送
  employeeCode, executionId, status, summary, modelName,
  receiptId,                              // P0-3 receipt ID
  artifactPath,                           // P0-2 产物路径
  knowledgePromoted,                      // P2-8 知识晋升
}
```

**`useOfficePresence` 处理**：
- 收到 `employee_task_update` → 触发 `receipt-arrived` 动画
- 收到 `task-complete` + `artifactPath` → 触发 `artifact-ready` 动画
- 收到 `knowledge-promoted` → 触发 `knowledge` 分类事件

#### 改进 10.3：员工详情抽屉 — "我的工位"

**新建组件**：`EmployeeStationDrawer.tsx`

**入口**：点击工位卡 → 弹出右侧抽屉（不破坏 Office 布局）

**面板内容**：
1. **基本信息**：姓名、职责卡摘要（P0-1 自动加载的）、能力标签
2. **当前任务**：
   - 状态 + 进度条
   - 任务描述
   - 使用模型
   - 产物链接（点击新窗口预览）
3. **最近 24h 任务历史**：调用 `/api/tasks/executions/my?limit=10`
4. **个人知识库**：列出 `data/personal-knowledge/{employeeCode}/` 中的经验
5. **任务统计**：成功率、平均耗时、模型偏好

**依赖 API**：
- `GET /api/tasks/executions/my?employeeCode={code}` - P1-4 已实现
- `GET /api/employees/{code}/knowledge` - **待实现**
- `GET /api/tasks/executions/{execId}/progress` - P1-4 已实现

#### 改进 10.4：部门任务面板（Tab 扩展）

**修改 `DepartmentTabs.tsx`**：在"公共任务栏"和"办公室总览"之间增加"部门执行" Tab

**新建组件**：`DepartmentExecutionPanel.tsx`

**功能**：
- 调用 `GET /api/tasks/executions/department/{dept}` - P1-4 已实现
- 展示本部门所有员工的最新任务（按时间倒序）
- 按状态筛选：全部 / 进行中 / 已完成 / 失败
- 按员工筛选：下拉选择
- 点击某个任务 → 跳转到"我的工位"抽屉并定位到该员工

#### 改进 10.5：实时任务通知（与虚拟办公室动画融合）

**接收 `employee_task_update` WebSocket 消息**（P1-6 已实现推送）：
- 触发对应员工动画：
  - `RUNNING` → 移动到 workstation 区域，开始"coding"姿势动画
  - `COMPLETED` → 完成动作 + 产物图标显示
  - `FAILED` → 移动到 alert 区域
  - `BLOCKED` → 静止 + 黄色感叹号

**视觉反馈**：
- 工位卡上显示实时进度条（通过 WebSocket 增量更新）
- 状态流时间轴增加 receipt 事件
- 部门动态 Feed 自动滚动到最新

#### 改进 10.6：产物预览面板

**新建组件**：`ArtifactPreviewPanel.tsx`

**触发**：在"我的工位"抽屉中点击产物链接

**支持类型**：
- HTML：iframe 嵌入预览（`data/artifacts/by-execution/{execId}/{empCode}/index.html`）
- Markdown：使用现有 `MarkdownRenderer` 组件渲染
- 报告：组合展示元数据 + 内容

**URL 路由**：需要后端增加 HTTP 静态文件服务
- `GET /api/artifacts/{execId}/{empCode}/{filename}` - **待实现**

#### 改进 10.7：KPI 概览增强

**修改 `OverviewKpis.tsx` 调用的 KPI 数据**：

| KPI | 数据来源 | 字段 |
|-----|---------|------|
| 部门总任务数 | `GET /api/tasks/executions/department/{dept}` | `.length` |
| 成功率 | 同上 | `completed / total` |
| 平均耗时 | receipt metadata | `avg(completedAt - startedAt)` |
| 模型分布 | receipt metadata.modelName | 统计各模型占比 |
| 活跃员工 | WebSocket 在线状态 | 实时 |
| 阻塞任务 | receipt status=BLOCKED | 计数 |

### 10.4 实施顺序

| 步骤 | 改进项 | 风险 | 依赖 |
|------|--------|------|------|
| 1 | 改进 10.1: 工位卡任务展示 | 低 | `currentTask` 数据填充 |
| 2 | 改进 10.2: OfficeEvent 扩展 | 低 | WebSocket 推送已就绪 |
| 3 | 改进 10.3: 员工详情抽屉 | 中 | 新组件 + API |
| 4 | 改进 10.4: 部门任务面板 | 中 | 新 Tab + 现有 API |
| 5 | 改进 10.5: 实时动画联动 | 中 | WebSocket 订阅 hook |
| 6 | 改进 10.6: 产物预览 | 高 | 后端 HTTP 服务 |
| 7 | 改进 10.7: KPI 增强 | 低 | receipt 聚合 API |

### 10.5 兼容性

- **不修改现有组件视觉风格**：仅在工位卡中增加信息密度，保留 pixel 风格
- **不破坏现有数据流**：`useOfficePresence` 仍为主 Hook，新事件作为扩展
- **渐进式启用**：新字段缺失时优雅降级（显示"暂无任务"）

### 10.6 验证标准

- [ ] 工位卡显示当前任务状态、模型、进度
- [ ] 点击工位卡打开"我的工位"抽屉
- [ ] 抽屉显示员工最近 10 条执行记录
- [ ] 抽屉显示员工个人知识库（前 5 条）
- [ ] 部门任务面板列出本部门所有任务
- [ ] WebSocket `employee_task_update` 触发对应员工动画
- [ ] 产物可点击预览（HTML iframe / Markdown 渲染）
- [ ] KPI 面板显示任务成功率、平均耗时

---

**全部 P0/P1 已完成，P2 已部分完成（8/9）。前端优化方案已规划（10.1-10.7）。**
