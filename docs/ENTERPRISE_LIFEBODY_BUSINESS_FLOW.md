# Living Agent Service 企业生命体业务流程总览

> 本文档整合所有核心业务流程、逻辑链路、权限入口和代码落点，作为企业生命体的完整业务蓝图。
>
> 版本：2026-05-15

---

## 一、企业生命体概述

### 1.1 核心理念

Living Agent Service 是一个**企业级AI生命体自治系统**：

```
感知层 → 神经层 → 执行层 → 企业系统
耳朵/嘴巴/眼睛/文字 → 神经元/大脑/通道 → 技能/工具/沙箱 → 飞书/钉钉/GitLab
```

### 1.2 三大特征

| 特征 | 说明 | 实现 |
|------|------|------|
| **仿生组织** | 模拟企业神经中枢架构 | 9个业务大脑 + 1个主脑 |
| **数字员工** | 具备感知、决策、执行能力 | 32个固定数字员工 |
| **自主进化** | 赚钱驱动经济独立 | autonomous包 |

---

## 二、组织架构

### 2.1 部门与大脑对照

| 大脑ID | 部门 | 核心能力 | 技能数 |
|--------|------|---------|--------|
| `MainBrain` | 跨部门 | 复杂推理、跨部门协调 | - |
| `TechBrain` | 技术部 | 代码审查、CI/CD、架构设计 | 25 |
| `HrBrain` | 人力资源 | 招聘管理、考勤、绩效 | 3 |
| `FinanceBrain` | 财务部 | 报销审批、发票、预算 | 4 |
| `SalesBrain` | 销售部 | 销售支持、市场营销 | 4 |
| `CsBrain` | 客服部 | 工单处理、问题解答 | 3 |
| `AdminBrain` | 行政部 | 文档处理、文案创作 | 15 |
| `LegalBrain` | 法务部 | 合同审查、合规检查 | 3 |
| `OpsBrain` | 运营部 | 数据分析、运营策略 | 9 |

**技能总数：76个**

### 2.2 数字员工编制 (32人)

| 部门 | 人数 | 代表职位 |
|------|------|----------|
| 技术部 | 10 | T01代码审查员、T02架构师、T09前端、T10后端 |
| 财务部 | 4 | F01财务会计、F02报销审核、F03成本核算、F04预算管理 |
| 运营部 | 4 | O01数据分析、O02运营、O03任务调度、O04流程管理 |
| 销售部 | 3 | S01销售、S02市场、S03渠道 |
| 人力资源 | 2 | H01招聘、H02绩效 |
| 客服部 | 2 | C01客服、C02工单处理 |
| 行政部 | 3 | A01行政、A02文档、A03文案 |
| 法务部 | 2 | L01合同审查、L02合规 |
| 跨部门 | 2 | M01协调、M02战略 |

---

## 三、核心业务流程

### 3.1 三大入口场景

| 场景 | 入口 | 通道 | 处理逻辑 |
|------|------|------|---------|
| **闲聊** | 未登录 | `/ws/public` | 直接路由到 Qwen3Neuron |
| **部门大脑** | 登录后部门对话 | `/ws/dept/{dept}` | 进入自治编排链路 |
| **智能体** | 个人助理 | `/ws/agent` | 进入 AgentService |

### 3.2 自治编排链路

```
用户请求 → 入口分类 → 主脑规划 → 部门路由 → 员工分派 
    → 任务单准备 → 执行派发 → 执行回执 → 回执聚合 
    → 产物记录 → 主脑收口 → 知识/绩效沉淀
```

**Trace阶段**：
1. `intake_classified` - 入口分类
2. `main_brain_planned` - 主脑规划
3. `brain_routed` - 大脑路由
4. `department_plan_created` - 部门计划
5. `employee_assignment_planned` - 员工分派
6. `assignment_batch_prepared` - 任务单准备
7. `employee_assigned` - 员工派发
8. `employee_execution_started` - 开始执行
9. `employee_execution_completed` - 执行完成
10. `execution_receipts_aggregated` - 回执聚合
11. `artifact_recorded` - 产物记录
12. `knowledge_recorded` - 知识沉淀
13. `performance_recorded` - 绩效记录
14. `result_aggregated` - 结果汇总
15. `main_brain_finalized` - 主脑收口

---

## 四、权限与入口矩阵

### 4.1 访问级别

| 级别 | 权限范围 | 可访问资源 |
|------|----------|-----------|
| **CHAT_ONLY** | 仅闲聊 | Qwen3Neuron |
| **LIMITED** | 部分大脑 | AdminBrain, CsBrain |
| **DEPARTMENT** | 本部门 | 本部门大脑 + ToolNeuron |
| **FULL** | 所有 | 所有大脑 + MainBrain |

### 4.2 身份与权限

| 身份 | 访问级别 | 可访问大脑 |
|------|----------|-----------|
| 董事长 | FULL | 所有部门大脑 |
| 在职员工 | DEPARTMENT | 本部门大脑 |
| 试用期员工 | LIMITED | AdminBrain, CsBrain |
| 离职/访客 | CHAT_ONLY | Qwen3Neuron |

### 4.3 WebSocket通道

| 频道路径 | 权限要求 | 说明 |
|---------|---------|------|
| `/ws/public` | 无需登录 | 公共闲聊 |
| `/ws/dept/{dept}` | 本部门/董事长 | 部门大脑 |
| `/ws/enterprise` | 董事长 | 董事长频道 |
| `/ws/agent` | origin=personal | 个人助理 |

---

## 五、代码落点速查表

### 5.1 入口与网关

| 功能 | 文件 | 路径 |
|------|------|------|
| 部门WebSocket | `DepartmentWebSocketHandler` | `gateway/websocket/` |
| Agent WebSocket | `AgentWebSocketHandler` | `gateway/websocket/` |
| 部门聊天服务 | `DepartmentChatService` | `gateway/service/` |
| 自治编排 | `ConversationOrchestrator` | `core/autonomy/` |

### 5.2 核心业务

| 功能 | 接口/类 | 路径 |
|------|---------|------|
| 入口分析 | `DialogueAnalyzer` | `core/autonomy/` |
| 主脑规划 | `MainBrainTaskDirector` | `core/autonomy/` |
| 员工分派 | `FixedEmployeeDispatcher` | `core/autonomy/` |
| 执行协调 | `DepartmentExecutionCoordinator` | `core/autonomy/` |
| 回执服务 | `EmployeeExecutionReceiptService` | `core/autonomy/` |

### 5.3 大脑与员工

| 功能 | 文件 | 路径 |
|------|------|------|
| 大脑注册 | `BrainRegistryImpl` | `core/brain/impl/` |
| 主脑 | `MainBrain` | `core/brain/impl/` |
| 部门大脑 | `*Brain` | `core/brain/impl/` |
| 固定员工 | `FixedEmployeeRegistry` | `core/employee/registry/` |

### 5.4 模型与工具

| 功能 | 文件 | 路径 |
|------|------|------|
| 模型池 | `ModelPoolManager` | `core/model/pool/` |
| 大脑模型解析 | `BrainModelResolver` | `core/model/pool/` |
| 模型健康 | `ModelHealthRegistry` | `core/model/pool/` |
| 工具执行器 | `ToolBackedEmployeeTaskExecutor` | `core/autonomy/impl/` |

### 5.5 知识与记忆

| 功能 | 文件 | 路径 |
|------|------|------|
| 知识管理 | `KnowledgeManager` | `core/knowledge/` |
| 产物服务 | `ArtifactRecordService` | `core/autonomy/` |
| 记忆服务 | `MemoryService` | `core/memory/` |

---

## 六、安全与合规

### 6.1 权限检查顺序

```
登录状态 → 身份识别 → 访问级别 → 部门权限 → 资源权限 → 审批流
```

### 6.2 安全组件

| 组件 | 文件 | 路径 |
|------|------|------|
| 权限服务 | `PermissionService` | `core/security/` |
| 访问网关 | `AccessGateService` | `core/security/` |
| 沙箱执行 | `SandboxExecutor` | `core/sandbox/` |
| 工具Hook | `ToolHookManager` | `core/tool/hook/` |

### 6.3 审计字段

```
actor | employee_origin | department | role | channel | action | target | trace_id | timestamp
```

---

## 七、文件路径速查

### 7.1 Gateway层
```
gateway/
├── config/
│   ├── GatewayConfig.java          # Bean注册
│   ├── WebSocketConfig.java       # WebSocket配置
│   └── SecurityConfig.java        # 安全配置
├── websocket/
│   ├── DepartmentWebSocketHandler.java  # 部门聊天
│   └── AgentWebSocketHandler.java      # Agent聊天
├── service/
│   ├── DepartmentChatService.java      # 部门聊天服务
│   └── AgentService.java              # Agent服务
└── controller/                        # REST API
```

### 7.2 Core层
```
core/
├── autonomy/                    # 自治编排
│   ├── ConversationOrchestrator.java
│   ├── DialogueAnalyzer.java
│   ├── MainBrainTaskDirector.java
│   ├── FixedEmployeeDispatcher.java
│   ├── DepartmentExecutionCoordinator.java
│   └── impl/                    # LLM实现
├── brain/                       # 大脑
│   ├── Brain.java
│   └── impl/
│       ├── MainBrain.java
│       ├── TechBrain.java
│       └── *Brain.java
├── employee/                    # 员工
│   ├── registry/
│   │   └── FixedEmployeeRegistry.java
│   └── neuron/
├── neuron/                      # 神经元
│   └── impl/
├── model/                       # 模型
│   └── pool/
│       ├── ModelPoolManager.java
│       ├── BrainModelResolver.java
│       └── ModelHealthRegistry.java
├── tool/                        # 工具
│   └── impl/
├── knowledge/                   # 知识
├── memory/                      # 记忆
├── security/                    # 安全
├── sandbox/                     # 沙箱
│   └── impl/
└── database/                    # 数据库
    └── entity/
```

---

## 八、快速导航

### 8.1 按功能查找

| 想改什么 | 找哪里 |
|---------|--------|
| 部门聊天入口 | `DepartmentWebSocketHandler` |
| 自治编排逻辑 | `ConversationOrchestrator` |
| 员工分派 | `FixedEmployeeDispatcher` |
| 模型选择 | `ModelPoolManager` |
| 工具执行 | `ToolBackedEmployeeTaskExecutor` |
| 权限校验 | `PermissionService` |
| 产物记录 | `ArtifactRecordService` |

### 8.2 按页面查找

| 前端页面 | 后端入口 |
|---------|---------|
| Chat.tsx | `/ws/public` |
| DepartmentDetail | `/ws/dept/{dept}` |
| AgentDetail | `/ws/agent` |

---

## 九、数字员工执行详情

### 9.1 执行环境

| 环境 | 说明 | 使用场景 |
|------|------|---------|
| `DOCKER_SANDBOX` | Docker容器隔离 | 代码执行、构建 |
| `LOCAL_RESTRICTED` | 本地受限 | 简单脚本 |
| `ARTIFACT_ONLY` | 仅产物生成 | 纯LLM生成 |
| `HUMAN_REVIEW_REQUIRED` | 需要人工审核 | 高风险操作 |

### 9.2 任务类型处理

| 任务类型 | 执行策略 | 产物 |
|----------|---------|------|
| `web_prototype` | 生成HTML/CSS/JS | index.html |
| `web_development` | 多文件项目 | HTML/CSS/JS |
| `document_generation` | 生成文档 | Markdown/Doc |
| `data_analysis` | 分析报告 | 报告文件 |
| `review` | 审核报告 | 审核文档 |

### 9.3 回执状态

| 状态 | 说明 |
|------|------|
| `COMPLETED` | 正常完成 |
| `DEGRADED` | 降级完成 |
| `FAILED` | 执行失败 |
| `NEEDS_RETRY` | 需要重试 |

---

## 十、产物存储结构

```
data/
└── artifacts/
    └── {department}/
        └── {executionId}/
            ├── index.html
            ├── style.css
            ├── script.js
            └── report.md
```

---

## 十一、知识晋升机制

```
L1(私有) ──验证通过──▶ L2(部门) ──多部门验证──▶ L3(共享)
    │                      │                      │
    └── 个人经验            └── 最佳实践            └── 制度规范
```

---

## 十二、相关文档

| 文档 | 说明 |
|------|------|
| `core/02-core-architecture.md` | 核心架构设计 |
| `权限与入口矩阵.md` | 权限入口规则 |
| `MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md` | 实施计划 |
| `CODE_STRUCTURE_AND_FILE_GUIDE.md` | 代码结构指南 |
| `LLM_AUTONOMY_HARDCODE_ANALYSIS.md` | LLM自治分析 |
