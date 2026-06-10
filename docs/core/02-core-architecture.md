# 核心架构设计

> Living Agent Service 整体架构设计
>
> 本文档补充说明：
> - 员工、神经元、大脑、通道的职责边界
> - 主脑与部门脑的接管条件
> - 技能层与工具层的边界
> - 数据流与业务流的实际落点

## 一、整体架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AI 企业管理智能体架构                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    感知层 (Perception Layer)                         │   │
│  │                                                                     │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐      │   │
│  │  │  耳朵   │ │  嘴巴   │ │  眼睛   │ │  触觉   │ │  文字   │      │   │
│  │  │  ASR    │ │  TTS    │ │  Vision │ │ Sensor  │ │  Text   │      │   │
│  │  │  ✅已有 │ │  ✅已有 │ │  ✅已有 │ │  🔜规划 │ │  ✅已有 │      │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    神经元层 (Neuron Layer)                           │   │
│  │                                                                     │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │  核心神经元 (Core Neurons)                                    │   │   │
│  │  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │   │   │
│  │  │  │ 决策神经元  │  │  路由神经元  │  │  记忆神经元  │          │   │   │
│  │  │  │(动态选择)  │  │(动态选择)  │  │(动态选择)  │          │   │   │
│  │  │  └─────────────┘  └─────────────┘  └─────────────┘          │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  │                              │                                      │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │  业务大脑 (Business Brains)                                   │   │   │
│  │  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐           │   │   │
│  │  │  │ HR大脑  │ │ 财务大脑│ │ 技术大脑│ │ 运营大脑│           │   │   │
│  │  │  │HR-Brain │ │FIN-Brain│ │TECH-Brain│ │OPS-Brain│           │   │   │
│  │  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘           │   │   │
│  │  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐           │   │   │
│  │  │  │ 销售大脑│ │ 法务大脑│ │ 行政大脑│ │ 客服大脑│           │   │   │
│  │  │  │SAL-Brain│ │LEG-Brain│ │ADM-Brain│ │CS-Brain │           │   │   │
│  │  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘           │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    技能层 (Skill Layer) - 工具集                     │   │
│  │                                                                     │   │
│  │  基础工具: 智能家居控制、天气查询、HTTP请求、MCP客户端                  │   │
│  │  企业工具: GitLab、Jira、钉钉、飞书、ERP、CRM、HR系统                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    企业系统层 (Enterprise Systems)                   │   │
│  │                                                                     │   │
│  │  GitLab │ Jenkins │ Jira │ 钉钉 │ 飞书 │ ERP │ CRM │ HR系统 │ 财务  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 二、三层LLM架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    三层LLM架构                                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Layer 1: 主大脑 (MainBrain) - 复杂推理与跨部门协调                           │
│  ├── 职责: 复杂推理、跨部门协调、战略决策                                     │
│  ├── 模型选择: 通过 BrainModelResolver + ModelPoolManager 动态选择             │
│  ├── 选择策略: 基于模型健康状态、硬件资源、任务复杂度                          │
│  └── 配置: 大脑模型分配由 BrainModelAssigner 管理                            │
│                                                                             │
│  Layer 2: 闲聊神经元 (Qwen3Neuron) - 日常对话与快速响应                       │
│  ├── 职责: 日常对话、快速响应、简单任务                                      │
│  ├── 模型: 固定模型，在守护进程中调用                                        │
│  ├── 独立运行，不参与大脑模型池动态选择                                       │
│  ├── 所有用户都可访问                                                        │
│  └── 本地部署，低延迟响应                                                    │
│                                                                             │
│  Layer 3: 工具神经元 (ToolNeuron) - 工具检测与兜底处理                        │
│  ├── 职责: 工具检测、兜底处理、触发进化信号                                  │
│  ├── 模型: 固定模型，在守护进程中调用                                         │
│  ├── 不参与大脑模型池动态选择                                                │
│  └── 原生多模态能力（如果模型支持）                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 模型池与大脑模型分配

**模型池管理 (ModelPoolManager):**
```yaml
# 模型池通过 API 动态管理
- Provider: OpenAI兼容API、Anthropic等
- 模型: 按 Provider 配置动态发现
- 状态: AVAILABLE / DEGRADED / COOLDOWN / UNAVAILABLE / UNKNOWN
```

**大脑模型分配 (BrainModelAssigner):**
```yaml
# 大脑模型分配策略
- 按 brainId 分配对应模型
- 支持模型切换和回退
- 模型健康状态实时监控
- 熔断过滤: 故障模型自动排除
```

### 2.2 模型选择策略

| 选择维度 | 说明 |
|----------|------|
| **模型健康** | AVAILABLE 状态优先，COOLDOWN/UNAVAILABLE 排除 |
| **硬件资源** | GPU/内存/CPU 监控，动态选择 |
| **任务复杂度** | 简单任务可选轻量模型，复杂任务选强力模型 |
| **部门适配** | 按部门大脑配置选择合适模型 |

**动态切换条件：**
- 当前模型不可用 → 自动切换到可用候选
- 模型健康状态恶化 → 熔断后切换
- 硬件资源变化 → 重新评估选择

## 三、业务大脑设计

### 3.1 标准业务大脑

| 大脑 | 部门 | 核心能力 | 技能数量 |
|------|------|---------|---------|
| **TechBrain** | 技术部 | 代码审查、CI/CD、架构设计 | 25 |
| **HrBrain** | 人力资源 | 招聘管理、考勤、绩效 | 3 |
| **FinanceBrain** | 财务部 | 报销审批、发票、预算 | 4 |
| **SalesBrain** | 销售部 | 销售支持、市场营销 | 4 |
| **CsBrain** | 客服部 | 工单处理、问题解答 | 3 |
| **AdminBrain** | 行政部 | 文档处理、文案创作 | 15 |
| **LegalBrain** | 法务部 | 合同审查、合规检查 | 3 |
| **OpsBrain** | 运营部 | 数据分析、运营策略 | 9 |
| **MainBrain** | 跨部门 | 协调多部门协作 | - |

**技能总数：76个，覆盖9个业务大脑**

### 3.2 数字员工职位定义

#### 技术部 (10人)

| 编号 | 职位 | 神经元ID | 核心职责 |
|------|------|---------|---------|
| T01 | 代码审查员 | `neuron://tech/code-reviewer/001` | 代码质量审查、PR审核 |
| T02 | 架构师 | `neuron://tech/architect/001` | 系统架构设计、技术选型 |
| T03 | DevOps工程师 | `neuron://tech/devops/001` | CI/CD流水线、部署自动化 |
| T04 | 运维工程师 | `neuron://tech/ops/001` | 心跳服务、资源调度、并发控制 |
| T05 | AI模型管理员 | `neuron://tech/model-admin/001` | 适配器注册、模型切换、性能监控 |
| T06 | 状态管理员 | `neuron://tech/state-admin/001` | 会话管理、状态持久化、中断恢复 |
| T07 | 安全工程师 | `neuron://tech/security/001` | 沙箱执行、资源限制、安全隔离 |
| T08 | 配置管理员 | `neuron://tech/config-admin/001` | 配置版本、变更审计、回滚支持 |
| T09 | 前端工程师 | `neuron://tech/frontend/001` | 前端开发、UI交互 |
| T10 | 后端工程师 | `neuron://tech/backend/001` | 后端开发、API设计 |

#### 财务部 (4人)

| 编号 | 职位 | 神经元ID | 核心职责 |
|------|------|---------|---------|
| F01 | 财务会计 | `neuron://finance/accountant/001` | 账务处理、财务报表 |
| F02 | 报销审核员 | `neuron://finance/auditor/001` | 报销审批、发票核验 |
| F03 | 成本核算员 | `neuron://finance/cost-accountant/001` | Token成本估算、项目独立核算 |
| F04 | 预算管理员 | `neuron://finance/budget-admin/001` | 月度预算管理、超支预警 |

#### 运营部 (4人)

| 编号 | 职位 | 神经元ID | 核心职责 |
|------|------|---------|---------|
| O01 | 数据分析师 | `neuron://ops/analyst/001` | 数据分析、报表生成 |
| O02 | 运营专员 | `neuron://ops/operator/001` | 日常运营、活动策划 |
| O03 | 任务调度员 | `neuron://ops/scheduler/001` | 任务检出、原子分配、冲突避免 |
| O04 | 流程管理员 | `neuron://ops/process-admin/001` | 运行队列、并发控制、优先级调度 |

#### 销售部 (3人)

| 编号 | 职位 | 神经元ID | 核心职责 |
|------|------|---------|---------|
| S01 | 销售代表 | `neuron://sales/representative/001` | 客户开发、销售跟进 |
| S02 | 市场专员 | `neuron://sales/marketer/001` | 市场调研、营销推广 |
| S03 | 渠道经理 | `neuron://sales/channel-manager/001` | 平台集成、GitHub/Upwork对接 |

#### 人力资源 (2人)

| 编号 | 职位 | 神经元ID | 核心职责 |
|------|------|---------|---------|
| H01 | 招聘专员 | `neuron://hr/recruiter/001` | 招聘管理、人才筛选 |
| H02 | 绩效管理员 | `neuron://hr/performance/001` | 绩效考核、培训管理 |

#### 客服部 (2人)

| 编号 | 职位 | 神经元ID | 核心职责 |
|------|------|---------|---------|
| C01 | 客服专员 | `neuron://cs/agent/001` | 客户咨询、问题解答 |
| C02 | 工单处理员 | `neuron://cs/ticket-handler/001` | 工单处理、问题跟踪 |

#### 行政部 (3人)

| 编号 | 职位 | 神经元ID | 核心职责 |
|------|------|---------|---------|
| A01 | 行政助理 | `neuron://admin/assistant/001` | 行政事务、日程管理 |
| A02 | 文档管理员 | `neuron://admin/doc-manager/001` | 文档管理、档案维护 |
| A03 | 文案策划 | `neuron://admin/copywriter/001` | 文案创作、内容策划 |

#### 法务部 (2人)

| 编号 | 职位 | 神经元ID | 核心职责 |
|------|------|---------|---------|
| L01 | 合同审查员 | `neuron://legal/contract-reviewer/001` | 合同审查、风险识别 |
| L02 | 合规专员 | `neuron://legal/compliance/001` | 合规检查、政策解读 |

#### 跨部门协调 (2人)

| 编号 | 职位 | 神经元ID | 核心职责 |
|------|------|---------|---------|
| M01 | 协调员 | `neuron://main/coordinator/001` | 跨部门协调、资源调配 |
| M02 | 战略规划师 | `neuron://main/strategist/001` | 战略规划、决策支持 |

## 四、神经元通讯架构

### 4.1 核心概念

- **神经网络** = 多个神经元之间的通讯群（聊天群）
- **神经元** = 数字员工的内部实现，具有特定功能的智能体/LLM
- **管路** = 神经元之间的通讯通道
- **标识** = 每个神经元/管路的唯一身份标记

### 4.2 ID命名规范

**员工ID命名规范 (统一):**
```
employee://{type}/{domain}/{identifier}
├── employee://human/dingtalk/123456          // 真实员工
├── employee://digital/tech/code-reviewer/001 // 数字员工
└── employee://digital/hr/recruiter/001       // 数字员工
```

**神经元ID命名规范 (内部实现):**
```
neuron://{domain}/{name}/{instance}
├── neuron://tech/code-reviewer/001           // 对应数字员工
└── neuron://hr/recruiter/001                 // 对应数字员工
```

**管路ID命名规范:**
```
channel://{scope}/{name}
├── channel://enterprise/main          // 企业主群 (广播)
├── channel://department/tech          // 技术部门群
├── channel://private/{emp1}/{emp2}    // 私聊通道
├── channel://perception/{sessionId}   // 感知通道
├── channel://dispatch/{sessionId}     // 路由分发
└── channel://response/{sessionId}     // 响应通道
```

### 4.3 通道类型

| 通道类型 | 说明 | 使用场景 |
|----------|------|---------|
| **BroadcastChannel** | 广播通道 | 一对多通知 |
| **PriorityChannel** | 优先级通道 | 按优先级处理消息 |
| **RoundRobinChannel** | 轮询通道 | 负载均衡分发 |
| **UnicastChannel** | 单播通道 | 一对一通讯 |

### 4.4 标准通道定义

```java
public class NeuronCoordinator {
    // 会话隔离的通道命名格式
    private static final String PERCEPTION_CHANNEL_PREFIX = "channel://perception/";
    private static final String DISPATCH_CHANNEL_PREFIX = "channel://dispatch/";
    private static final String TOOL_INTENT_CHANNEL_PREFIX = "channel://tool-intent/";
    private static final String RESPONSE_CHANNEL_PREFIX = "channel://response/";
    
    // 实际通道ID示例:
    // channel://perception/session-a1b2c3d4
    // channel://dispatch/session-a1b2c3d4
    // channel://tool-intent/session-a1b2c3d4
    // channel://response/session-a1b2c3d4
}
```

## 五、数据流与业务流程

### 5.1 完整业务流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    前端 → 后端完整业务流程                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  前端 (living-agent-frontend)                                        │   │
│  │  ├── ChatView.vue - 聊天界面                                          │   │
│  │  ├── useWebSocket.ts - WebSocket 连接管理                             │   │
│  │  └── useVoiceDialogue.ts - 语音对话处理                               │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                     │                                       │
│                                     │ WebSocket (ws://localhost:8382/ws/agent)│
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Gateway 层 (living-agent-gateway)                                   │   │
│  │  ├── AgentWebSocketHandler - WebSocket 处理器                        │   │
│  │  └── AgentService - 服务入口                                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  神经元路由层 (ChatNeuronRouter)                                     │   │
│  │  ├── ChatIntentClassifier - 意图分类                                  │   │
│  │  │   ├── GREETING → Qwen3Neuron (闲聊)                               │   │
│  │  │   ├── CASUAL_CHAT → Qwen3Neuron (日常对话)                        │   │
│  │  │   ├── SIMPLE_QUESTION → Qwen3Neuron (简单问题)                    │   │
│  │  │   ├── TOOL_CALL → ToolNeuron (工具调用)                           │   │
│  │  │   └── COMPLEX_TASK → MainBrain (复杂任务)                         │   │
│  │  └── 兜底逻辑: 首选不可用时自动降级                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Channel 群聊层                                                       │   │
│  │  ├── channel://input/user - 用户输入通道                               │   │
│  │  ├── channel://dispatch/* - 路由分发通道                               │   │
│  │  ├── channel://tech/tasks - 技术部门通道                               │   │
│  │  └── channel://output/main - 输出通道                                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  大脑处理层 (Brain Layer)                                             │   │
│  │  ├── MainBrain (Layer 1) - 复杂推理、跨部门协调                      │   │
│  │  │   └── 转发到部门大脑: TechBrain, HrBrain, FinanceBrain...         │   │
│  │  ├── Qwen3Neuron (Layer 2) - 日常对话、快速响应                       │   │
│  │  └── ToolNeuron (Layer 3) - 工具检测、兜底处理                        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  技能执行层 (Skill Layer)                                             │   │
│  │  ├── 76个技能 - 按部门分类                                             │   │
│  │  └── 工具调用 - GitLab, Jira, Jenkins, 钉钉, 飞书...                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  响应返回                                                             │   │
│  │  ├── TTS 合成 (MeloTTS)                                              │   │
│  │  ├── Opus 编码 (Rust Native)                                         │   │
│  │  └── WebSocket 返回前端                                               │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 权限与模型对应关系

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    权限级别与可访问模型对照表                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  权限级别      │  可用模型                    │  可访问大脑                   │
│  ─────────────┼─────────────────────────────┼─────────────────────────────  │
│  CHAT_ONLY    │  动态选择(闲聊模型)         │  无 (仅闲聊神经元)            │
│  LIMITED      │  动态选择(受限模型)        │  AdminBrain, CsBrain         │
│  DEPARTMENT    │  动态选择(部门模型)        │  本部门大脑 + AdminBrain,     │
│               │                             │  CsBrain                      │
│  FULL         │  动态选择(全功能模型)      │  所有大脑 + MainBrain         │
│                                                                             │
│  用户身份      │  默认权限                    │  特殊说明                     │
│  ─────────────┼─────────────────────────────┼─────────────────────────────  │
│  董事长        │  FULL                       │  可跨部门协调                 │
│  在职员工      │  DEPARTMENT                 │  仅本部门                     │
│  试用期员工    │  LIMITED                    │  受限访问                     │
│  离职员工      │  CHAT_ONLY                  │  仅闲聊                       │
│  外来访客      │  CHAT_ONLY                  │  仅闲聊                       │
│  客户         │  LIMITED                    │  受限访问                     │
│  合作伙伴      │  LIMITED                    │  受限访问                     │
│                                                                             │
│  模型选择说明:                                                               │
│  - 所有层级的模型都通过 BrainModelResolver + ModelPoolManager 动态选择       │
│  - 模型选择基于模型健康状态、硬件资源、任务复杂度                              │
│  - 不再硬编码具体模型名称                                                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.3 WebSocket端点

| 频道路径 | 权限要求 | 说明 |
|---------|---------|------|
| `/ws/agent` | 需要登录 | 智能体对话频道 |
| `/ws/public` | 无需登录 | 访客对话频道 |
| `/ws/dept/tech` | tech部门 | 技术部群聊频道 |
| `/ws/dept/hr` | hr部门 | 人力资源群聊频道 |
| `/ws/dept/finance` | finance部门 | 财务部群聊频道 |
| `/ws/dept/sales` | sales部门 | 销售部群聊频道 |
| `/ws/dept/admin` | admin部门 | 行政部群聊频道 |
| `/ws/dept/cs` | cs部门 | 客服部群聊频道 |
| `/ws/dept/legal` | legal部门 | 法务部群聊频道 |
| `/ws/dept/ops` | ops部门 | 运营部群聊频道 |
| `/ws/admin` | 董事长 | 董事长专属频道 |

## 六、核心模块实现状态

| 模块 | 状态 | 完成度 | 说明 |
|------|------|--------|------|
| **anomaly** | ✅ | 100% | 异常检测 (AnomalyDetector, AnomalyContext, AnomalyResult) |
| **brain** | ✅ | 100% | 9个部门大脑全部实现 |
| **channel** | ✅ | 100% | 通道通信 (Broadcast/Unicast/Priority/RoundRobin) |
| **diagnosis** | ✅ | 100% | 诊断系统 (HealthMonitor, HealthCheck, HealthAlert) |
| **embedding** | ✅ | 100% | 嵌入服务 (LocalEmbeddingService) |
| **employee** | ✅ | 100% | 员工系统 (DigitalEmployee, HumanEmployee, EmployeePersonality) |
| **evolution** | ✅ | 100% | 进化系统 (KnowledgeEvolver, SkillGenerator, CapabilityEvaluator) |
| **knowledge** | ✅ | 100% | 知识管理 (KnowledgeManager, 三层知识库) |
| **memory** | ✅ | 100% | 记忆系统 (MemoryService, MemOS集成) |
| **model** | ✅ | 100% | 模型管理 (ModelManager, NamedPipeModelClient, ModelPool) |
| **neuron** | ✅ | 100% | 神经元 (AbstractNeuron, 多种Neuron实现, EyeNeuron) |
| **ops** | ✅ | 100% | 运维 (RunQueue, TaskCheckout) |
| **planner** | ✅ | 100% | 任务规划 (TaskPlanner, TaskPlan, TaskStep) |
| **proactive** | ✅ | 100% | 主动预判 (AlertNotifier, CronService, EventHookManager) |
| **provider** | ✅ | 100% | 提供者 (AsrProvider, TtsProvider, 多模型Provider) |
| **scenario** | ✅ | 100% | 场景处理 (ScenarioHandler, WeeklyReport, EmployeeOnboarding) |
| **security** | ✅ | 100% | 安全 (SandboxExecutor, SkillVetter, BrainAccessControl) |
| **service** | ✅ | 100% | 服务 (AsrService, TtsService, LocalModelService) |
| **skill** | ✅ | 100% | 技能 (SkillRegistry, BountyTask, 76个技能) |
| **tool** | ✅ | 100% | 工具 (20+工具实现) |

## 七、Rust Native模块

| 模块 | 状态 | 完成度 | 关键功能 |
|------|------|--------|----------|
| **audio** | ✅ | 100% | Opus编解码、VAD语音检测、重采样 |
| **channel** | ✅ | 100% | MPSC通道、广播通道、消息定义 |
| **memory** | ✅ | 100% | SQLite后端、记忆条目、查询接口 |
| **knowledge** | ✅ | 100% | SQLite后端、向量存储、相似度计算、LRU缓存 |
| **security** | ✅ | 100% | 安全验证器、沙箱配置、命令黑名单 |
| **jni** | ✅ | 100% | 5个JNI接口完整实现 |

## 八、补充约束与澄清

### 8.1 组织治理与职责卡

- `documents/shared/company/hr-15-founder-enterprise-system.md`、`hr-16-digital-employee-strategy.md`、`hr-17-digital-employee-performance.md` 说明，董事长，数字员工、岗位编制、绩效与权限应作为治理实体统一管理，不应仅仅理解成界面入口或聊天对象。
- `documents/shared/company/fixed-employee-duty-card-template.md` 与各岗位职责卡，是编制定义的现实参考。

### 8.2 通道与部门协作

- `documents/shared/company/fixed-employee-document-workflow.md`、`fixed-employee-agent-prompt.md`、`fixed-employee-autonomous-runbook.md` 已说明固定员工的工作流、自动运行和职责卡结构，说明通道是"协作和路由"，不是"群聊即业务本体"。

### 8.3 部门目录与知识落点

- `documents/department/**/README.md` 与 `documents/shared/governance/` 说明部门治理、部门知识和部门协作要相互匹配，不能脱离部门职责而独立运行。

### 8.4 模型选择机制

- 所有模型的调用都通过 BrainModelResolver + ModelPoolManager 动态选择
- 模型健康状态实时监控，熔断机制自动过滤故障模型
- 不再硬编码具体模型名称，所有模型通过配置和发现机制动态管理

### 8.5 代码产物与审查统一约定

- **主链路标识**：所有代码派发、执行回执、审查状态机、artifact 记录统一使用 `taskId` 作为主关联键。`assignmentId`、`dispatchId` 仅兼容追溯。
- **任务分类**：`taskType`（code_review/bug_fix/test_generate/release_prep）、`taskScope`（ADHOC/SCHEDULED/PROJECT/PIPELINE）、`workflowType`（SINGLE_PASS/REVIEW_LOOP/RECURRING/PARALLEL）。
- **产物绑定**：worktree / diff / review report / final summary 必须通过 `ArtifactRecord` 与 `taskId` 关联，物理路径在 `<repoRoot>/.worktrees/<taskId>/`。
- **审查状态机**：13 阶段状态流转，`canTransition()` 校验合法性，`MAX_REVIEW_ROUNDS=3` 限制自动审查轮次，超过升级人工。
- **持久化**：审查状态持久化到 `code_review_states` 表，产物元数据持久化到 `artifact_records` 表。
- **API 端点**：审查操作通过 `/api/artifacts/reviews/{taskId}/` 系列 API 暴露（submit/approve/requestChanges/resubmit/escalate/accept/reject）。
- **详细文档**：参见 `MODULE_AUTONOMY_ORCHESTRATION.md`（元数据约定）、`MODULE_TOOL_SKILL.md`（产物保存）、`MODULE_BRAIN.md`（审查状态机）、`MODULE_EMPLOYEE.md`（任务单/回执字段）。

## 九、修补建议

### 9.1 建议优先修补的旧文档点

- 增补"员工 / 脑 / 神经元 / 通道"的边界说明
- 增补主脑接管条件与部门脑处理条件
- 增补"通道不是聊天群，是协作与路由层"的说明
- 增补技能层与工具层的边界
- 确认模型选择不再硬编码，使用模型池动态选择

## 十、结论

核心架构并不缺概念，缺的是与实际代码和治理文档一致的边界定义。结合 `documents/` 的补充资料后，`02-core-architecture.md` 更应强调：

- 企业生命体中的组织治理、岗位职责、通道路由和大脑协作是同一套体系中的不同层级
- 模型选择通过动态模型池机制，不再硬编码具体模型名称
- 安全、权限、审批、审计继续由硬规则兜底

## 十一、LLM 与确定性规则边界

### 11.1 核心设计原则

> **判断逻辑让 LLM 自主决定，执行规则必须硬编码确定性。**

这条原则贯穿整个系统架构，决定了哪些决策交给 LLM 自主推理，哪些必须由确定性代码执行。

### 11.2 分类标准

| 类别 | 决策方式 | 特征 | 示例 |
|------|----------|------|------|
| **判断类** | LLM 自主决定 | 需要语义理解、上下文推理、模糊决策 | 意图分析、需求评估、任务规划、员工分派、回执审查 |
| **执行类** | 硬编码确定性 | 涉及安全、权限、状态流转、数据一致性 | 权限检查、状态机转换、审批流程、审计记录 |
| **混合类** | LLM 判断 + 规则约束 | LLM 决策但受规则边界约束 | 模型选择（LLM 推荐 + 健康检查约束）、任务路由（LLM 分析 + 权限校验） |

### 11.3 LLM 自主决定的场景

以下接口使用 `LlmBased*` 实现，`RuleBased*` 作为降级备用：

| 接口 | LLM 实现 | 规则兜底 | 判断内容 |
|------|----------|----------|----------|
| `DialogueAnalyzer` | `LlmBasedDialogueAnalyzer` | `RuleBasedDialogueAnalyzer` | 意图分类、部门路由 |
| `RequirementReadinessEvaluator` | `LlmRequirementReadinessEvaluator` | 默认放行 | 需求充分性评估 |
| `MainBrainTaskDirector` | `LlmBasedMainBrainTaskDirector` | `RuleBasedMainBrainTaskDirector` | 任务拆解与规划 |
| `FixedEmployeeDispatcher` | `LlmBasedFixedEmployeeDispatcher` | `RegistryBackedFixedEmployeeDispatcher` | 员工任务分派 |
| `ExecutionReceiptReviewer` | `LlmExecutionReceiptReviewer` | defaultAccept 降级 | 回执质量审查 |
| `ExecutionResultAggregator` | `LlmBasedExecutionResultAggregator` | `DefaultExecutionResultAggregator` | 执行结果聚合 |
| `MainBrainResponseComposer` | `LlmBasedMainBrainResponseComposer` | `DefaultMainBrainResponseComposer` | 最终响应编排 |
| `FinalResponseCoordinator` | `LlmBasedFinalResponseCoordinator` | `DefaultFinalResponseCoordinator` | 响应协调 |
| `AssignmentReadinessEvaluator` | `LlmAssignmentReadinessEvaluator` | 无 | 分派就绪评估 |

### 11.4 必须硬编码确定性的场景

| 场景 | 代码落点 | 原因 |
|------|----------|------|
| 权限检查 | `PermissionServiceImpl` | 安全不可由 LLM 绕过，权限先于路由 |
| 状态机转换 | `CodeReviewWorkflowService.canTransition()` | 状态流转必须合法，非法转换抛异常 |
| 审批限制 | `MAX_REVIEW_ROUNDS=3` | 防止无限循环，硬性约束 |
| 权限级别 | `AccessLevel(CHAT_ONLY=0, LIMITED=1, DEPARTMENT=2, FULL=3)` | 安全分级不可模糊 |
| WebSocket 入口 | 部门匹配 + 权限校验 | 入口安全不可委托 |
| 模型健康探测 | `ModelHealthProber` 失败分类 | 临时性 vs 非临时性基于 HTTP 状态码，确定性判断 |
| 工具能力声明 | `Tool.capabilities()` | 工具边界必须确定 |
| 员工定义 | `FixedEmployeeRegistry` | 组织架构是确定性配置 |

### 11.5 降级策略

```text
LLM 调用成功 → 使用 LLM 结果
LLM 调用失败（超时/异常/空响应）→ 降级到 RuleBased 实现
RuleBased 也失败 → 返回安全默认值（如：需求评估默认 SUFFICIENT、回执审查 defaultAccept）
```

降级触发条件：
1. LLM 调用超时（>30s）
2. LLM 返回空响应
3. LLM 返回结果无法解析
4. LLM 服务不可用

降级后的行为：
- 不阻塞用户请求
- 返回低置信度结果（如 qualityScore=0.5）
- 记录降级事件到 Trace

### 11.6 禁止硬编码的场景

以下场景**不应使用关键词匹配、规则枚举等硬编码方式**，应交给 LLM 自主判断：

| 禁止硬编码 | 原因 | 正确方式 |
|-----------|------|----------|
| 需求充分性评估 | 关键词永远覆盖不全，"帮我总结"都可能被误判 | `LlmRequirementReadinessEvaluator` |
| 意图分类 | 用户表达方式无限，无法穷举 | `LlmBasedDialogueAnalyzer` |
| 任务拆解 | 任务类型和复杂度需要语义理解 | `LlmBasedMainBrainTaskDirector` |
| 员工匹配 | 技能匹配需要语义推理 | `LlmBasedFixedEmployeeDispatcher` |
