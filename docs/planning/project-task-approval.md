# 项目任务审批系统设计

> 对接 WorkBuddy 前端的后端扩展模块

---

## 一、概述

本文档描述了为对接 WorkBuddy 前端而新增的项目管理、任务管理、审批流程三个核心模块的 API 设计与实现。

### 1.1 模块清单

| 模块 | Controller | Service | 说明 |
|------|------------|---------|------|
| 项目管理 | ProjectController | ProjectService | 项目全生命周期管理 |
| 任务管理 | TaskController | TaskCheckout | 任务调度 REST API |
| 审批流程 | ApprovalController | ApprovalService | 工作流引擎 |

---

## 二、项目管理模块

### 2.1 数据模型

#### Project 实体

```java
public class Project {
    private String projectId;           // 项目ID
    private String name;                // 项目名称
    private String description;         // 项目描述
    private ProjectStatus status;       // 项目状态
    private ProjectPhase currentPhase;  // 当前阶段
    private String ownerDepartment;     // 所属部门
    private String managerId;           // 项目经理
    private Instant startDate;          // 开始日期
    private Instant endDate;            // 结束日期
    private double progress;            // 进度百分比
    private List<ProjectPhaseRecord> phases;  // 阶段记录
    private Map<String, Object> metadata;     // 元数据
}
```

#### ProjectPhase 枚举（8阶段生命周期）

```java
public enum ProjectPhase {
    MARKET_ANALYSIS,    // 市场调研
    REQUIREMENT,        // 需求分析
    DESIGN,             // 方案设计
    DEVELOPMENT,        // 开发实施
    TESTING,            // 测试验收
    DEPLOYMENT,         // 上线部署
    OPERATION,          // 运营维护
    AFTER_SALES         // 售后服务
}
```

#### ProjectStatus 枚举

```java
public enum ProjectStatus {
    PLANNING,      // 规划中
    IN_PROGRESS,   // 进行中
    ON_HOLD,       // 暂停
    COMPLETED,     // 已完成
    CANCELLED      // 已取消
}
```

### 2.2 REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/projects` | 项目列表 |
| POST | `/api/projects` | 创建项目 |
| GET | `/api/projects/{projectId}` | 项目详情 |
| PUT | `/api/projects/{projectId}` | 更新项目 |
| DELETE | `/api/projects/{projectId}` | 删除项目 |
| POST | `/api/projects/{projectId}/start` | 启动项目 |
| POST | `/api/projects/{projectId}/complete` | 完成项目 |
| POST | `/api/projects/{projectId}/hold` | 暂停项目 |
| POST | `/api/projects/{projectId}/phases/{phase}/advance` | 推进阶段 |
| GET | `/api/projects/{projectId}/progress` | 获取进度 |
| PUT | `/api/projects/{projectId}/phases/{phase}/progress` | 设置阶段进度 |
| GET | `/api/projects/statistics` | 项目统计 |

### 2.3 请求/响应示例

#### 创建项目

```json
// POST /api/projects
// Request
{
    "name": "企业管理系统升级",
    "description": "升级现有企业管理系统，增加AI智能助手功能",
    "ownerDepartment": "tech",
    "managerId": "emp_001"
}

// Response
{
    "success": true,
    "data": {
        "projectId": "proj_a1b2c3d4",
        "name": "企业管理系统升级",
        "status": "PLANNING",
        "currentPhase": "MARKET_ANALYSIS",
        "progress": 0.0,
        "phases": [
            { "phase": "MARKET_ANALYSIS", "status": "PENDING", "progress": 0.0 },
            { "phase": "REQUIREMENT", "status": "PENDING", "progress": 0.0 },
            // ...
        ]
    }
}
```

#### 推进阶段

```json
// POST /api/projects/proj_a1b2c3d4/phases/requirement/advance
// Response
{
    "success": true,
    "data": {
        "projectId": "proj_a1b2c3d4",
        "currentPhase": "DESIGN",
        "progress": 25.0
    }
}
```

---

## 三、任务管理模块

### 3.1 数据模型

任务管理复用现有的 `TaskCheckout` 组件，通过 `TaskController` 暴露 REST API。

#### Task 结构

```java
public record Task(
    String taskId,              // 任务ID
    String taskType,            // 任务类型
    String description,         // 任务描述
    int priority,               // 优先级 (1-10)
    String requiredCapability,  // 所需能力
    Map<String, Object> context,// 上下文
    TaskStatus status,          // 状态
    Instant createdAt,          // 创建时间
    Instant checkedOutAt,       // 领取时间
    String assignedTo,          // 分配给
    Instant completedAt         // 完成时间
) {}
```

### 3.2 REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tasks` | 任务列表 |
| POST | `/api/tasks` | 创建任务 |
| GET | `/api/tasks/{taskId}` | 任务详情 |
| POST | `/api/tasks/{taskId}/checkout` | 领取任务 |
| POST | `/api/tasks/{taskId}/complete` | 完成任务 |
| POST | `/api/tasks/{taskId}/release` | 释放任务 |
| POST | `/api/tasks/{taskId}/reassign` | 重新分配 |
| GET | `/api/tasks/statistics` | 任务统计 |
| GET | `/api/tasks/pending` | 待领取任务 |
| GET | `/api/tasks/employee/{employeeId}` | 员工任务 |

### 3.3 请求/响应示例

#### 创建任务

```json
// POST /api/tasks
// Request
{
    "taskType": "code_review",
    "description": "审查用户模块代码",
    "priority": 7,
    "requiredCapability": "code_analysis",
    "context": {
        "project": "proj_a1b2c3d4",
        "branch": "feature/user-module"
    }
}

// Response
{
    "success": true,
    "data": {
        "taskId": "task_1712345678901",
        "taskType": "code_review",
        "status": "PENDING",
        "priority": 7
    }
}
```

#### 领取任务

```json
// POST /api/tasks/task_1712345678901/checkout
// Request
{
    "employeeId": "emp_tech_001",
    "capabilities": ["code_analysis", "java"]
}

// Response
{
    "success": true,
    "data": {
        "taskId": "task_1712345678901",
        "status": "CHECKED_OUT",
        "assignedTo": "emp_tech_001"
    }
}
```

#### 完成任务

```json
// POST /api/tasks/task_1712345678901/complete
// Request
{
    "employeeId": "emp_tech_001",
    "success": true,
    "output": "代码审查完成，发现3个问题已修复",
    "metrics": {
        "filesReviewed": 15,
        "issuesFound": 3
    }
}

// Response
{
    "success": true,
    "data": {
        "taskId": "task_1712345678901",
        "status": "COMPLETED"
    }
}
```

---

## 四、审批流程模块

### 4.1 数据模型

#### ApprovalWorkflow 工作流定义

```java
public class ApprovalWorkflow {
    private String workflowId;       // 工作流ID
    private String name;             // 名称
    private String description;      // 描述
    private List<ApprovalStep> steps;// 审批步骤
    private boolean enabled;         // 是否启用
}
```

#### ApprovalStep 审批步骤

```java
public class ApprovalStep {
    private String stepId;           // 步骤ID
    private String name;             // 名称
    private ApprovalType type;       // 类型: SINGLE/ALL/ANY
    private List<String> approvers;  // 审批人列表
    private int order;               // 顺序
}
```

#### ApprovalInstance 审批实例

```java
public class ApprovalInstance {
    private String instanceId;       // 实例ID
    private String workflowId;       // 工作流ID
    private String businessType;     // 业务类型
    private String businessId;       // 业务ID
    private String title;            // 标题
    private String description;      // 描述
    private ApprovalStatus status;   // 状态
    private int currentStep;         // 当前步骤
    private String submitterId;      // 提交人
    private List<ApprovalRecord> records; // 审批记录
}
```

#### ApprovalStatus 枚举

```java
public enum ApprovalStatus {
    PENDING,      // 待审批
    IN_PROGRESS,  // 审批中
    APPROVED,     // 已通过
    REJECTED,     // 已拒绝
    RETURNED,     // 已退回
    CANCELLED     // 已取消
}
```

### 4.2 预置工作流

| 工作流ID | 名称 | 步骤 |
|---------|------|------|
| default | 默认审批流程 | 部门主管审批 |
| project_approval | 项目审批流程 | 部门主管 → 财务部 → 董事长 |
| expense_approval | 报销审批流程 | 部门主管 → 财务部 |

### 4.3 REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/approvals/pending` | 待审批列表 |
| GET | `/api/approvals/my` | 我发起的审批 |
| POST | `/api/approvals` | 发起审批 |
| GET | `/api/approvals/{instanceId}` | 审批详情 |
| POST | `/api/approvals/{instanceId}/approve` | 通过 |
| POST | `/api/approvals/{instanceId}/reject` | 拒绝 |
| POST | `/api/approvals/{instanceId}/return` | 退回 |
| POST | `/api/approvals/{instanceId}/cancel` | 取消 |
| GET | `/api/approvals/{instanceId}/history` | 审批历史 |
| GET | `/api/approvals/workflows` | 工作流列表 |
| GET | `/api/approvals/workflows/{workflowId}` | 工作流详情 |
| POST | `/api/approvals/workflows` | 创建工作流 |

### 4.4 请求/响应示例

#### 发起审批

```json
// POST /api/approvals
// Request
{
    "workflowId": "project_approval",
    "businessType": "PROJECT",
    "businessId": "proj_a1b2c3d4",
    "title": "企业管理系统升级项目立项",
    "description": "申请启动企业管理系统升级项目，预算50万元"
}

// Response
{
    "success": true,
    "data": {
        "instanceId": "appr_e5f6g7h8",
        "workflowId": "project_approval",
        "status": "IN_PROGRESS",
        "currentStep": 0
    }
}
```

#### 审批通过

```json
// POST /api/approvals/appr_e5f6g7h8/approve
// Request
{
    "comment": "同意项目立项，请财务部审核预算"
}

// Response
{
    "success": true,
    "data": {
        "instanceId": "appr_e5f6g7h8",
        "status": "IN_PROGRESS",
        "currentStep": 1
    }
}
```

#### 审批拒绝

```json
// POST /api/approvals/appr_e5f6g7h8/reject
// Request
{
    "comment": "预算超支，请重新评估"
}

// Response
{
    "success": true,
    "data": {
        "instanceId": "appr_e5f6g7h8",
        "status": "REJECTED"
    }
}
```

---

## 五、与 WorkBuddy 前端对接

### 5.1 API 映射关系

| WorkBuddy 前端功能 | 后端 API |
|-------------------|----------|
| 总经理看板 | `/api/chairman/dashboard` |
| 项目流程 | `/api/projects/*` |
| Agent 中心 | `/api/agents`, `/api/employees` |
| 任务审核 | `/api/approvals/*` |
| 通知中心 | `/api/proactive/notifications` |

### 5.2 数据流

```
WorkBuddy 前端
    │
    ├── 登录页面 ──────────▶ /api/auth/oauth/*
    │
    ├── 总经理看板 ────────▶ /api/chairman/dashboard
    │
    ├── 项目流程 ──────────▶ /api/projects/*
    │   ├── 项目列表
    │   ├── 创建项目
    │   ├── 推进阶段
    │   └── 项目统计
    │
    ├── Agent 中心 ────────▶ /api/agents
    │   └── /api/employees
    │
    ├── 任务审核 ──────────▶ /api/approvals/*
    │   ├── 待审批列表
    │   ├── 审批操作
    │   └── 审批历史
    │
    └── 通知中心 ──────────▶ /api/proactive/notifications
```

### 5.3 WebSocket 实时通信

| 频道 | 用途 |
|------|------|
| `/ws/agent` | Agent 对话 |
| `/ws/dept/{department}` | 部门群聊 |
| `/ws/chairman` | 董事长专属频道 |

---

## 六、文件清单

### 6.1 新增文件

```
living-agent-core/src/main/java/com/livingagent/core/
├── project/
│   ├── Project.java              # 项目实体
│   ├── ProjectStatus.java        # 项目状态枚举
│   ├── ProjectPhase.java         # 项目阶段枚举
│   ├── ProjectPhaseRecord.java   # 阶段记录
│   ├── ProjectService.java       # 服务接口
│   ├── ProjectStatistics.java    # 统计数据
│   └── impl/
│       └── ProjectServiceImpl.java
│
└── approval/
    ├── ApprovalWorkflow.java     # 工作流定义
    ├── ApprovalStep.java         # 审批步骤
    ├── ApprovalInstance.java     # 审批实例
    ├── ApprovalRecord.java       # 审批记录
    ├── ApprovalService.java      # 服务接口
    └── impl/
        └── ApprovalServiceImpl.java

living-agent-gateway/src/main/java/com/livingagent/gateway/controller/
├── ProjectController.java        # 项目管理API
├── TaskController.java           # 任务管理API
└── ApprovalController.java       # 审批流程API
```

### 6.2 修改文件

- `PROJECT_FRAMEWORK.md` - 更新模块结构、能力清单
- `DEVELOPMENT_PLAN.md` - 添加 Phase 21 进度

---

## 七、后续扩展

### 7.1 数据库持久化

当前实现使用内存存储，后续需要：

1. 添加 PostgreSQL 表定义
2. 实现 Repository 层
3. 添加数据迁移脚本

### 7.2 权限集成

1. 与现有 `AuthContext` 集成
2. 添加部门权限检查
3. 审批人自动匹配

### 7.3 通知集成

1. 审批待办通知
2. 项目进度通知
3. 任务分配通知
