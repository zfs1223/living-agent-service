# 核心架构设计

> Living Agent Service 整体架构设计

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
│  │  │  │ Qwen3.5-27B │  │  qwen3.5-2b  │  │   BGE-M3    │          │   │   │
│  │  │  │ (决策神经元) │  │ (路由神经元) │  │ (记忆神经元) │          │   │   │
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
│                    三层LLM架构                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Layer 1: 主大脑 (MainBrain) - 灵活配置                                       │
│  ├── 职责: 复杂推理、跨部门协调、战略决策                                     │
│  ├── 默认: Qwen3.5-27B (云端API)                                             │
│  ├── 可选: Qwen3.5-14B / Qwen3-32B / DeepSeek-V3 等                          │
│  ├── 动态模型选择 (MainBrainModelSelector)                                   │
│  └── 配置: main-brain.model.default / main-brain.model.api-key               │
│                                                                             │
│  Layer 2: 闲聊神经元 (Qwen3Neuron) - Qwen3-0.6B (固定)                        │
│  ├── 职责: 日常对话、快速响应、简单任务                                      │
│  ├── 独立运行，不参与灵活配置                                                │
│  ├── 所有用户都可访问                                                        │
│  └── 本地部署，低延迟响应                                                    │
│                                                                             │
│  Layer 3: 工具神经元 (ToolNeuron) - 灵活配置                                  │
│  ├── 职责: 工具检测、兜底处理、触发进化信号                                  │
│  ├── 默认: Qwen3.5-2B (推荐，支持多模态)                                     │
│  ├── 备选: BitNet-1.58-3B (低资源环境)                                       │
│  ├── 动态模型选择 (ToolNeuronModelSelector)                                  │
│  └── 原生多模态能力                                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 模型选择器配置

**MainBrainModelSelector 配置:**
```yaml
main-brain:
  model:
    default: qwen3.5-27b        # 默认模型
    api-key: ${MAIN_BRAIN_API_KEY}
    base-url: ${MAIN_BRAIN_BASE_URL:}  # 可选，自定义API地址
    auto-select: true            # 自动选择（基于任务复杂度）
    available-models:
      - qwen3.5-27b
      - qwen3.5-14b
      - qwen3-32b
      - deepseek-v3
```

**ToolNeuronModelSelector 配置:**
```yaml
tool-neuron:
  model:
    default: qwen3.5-2b          # 默认模型（推荐）
    auto-select: true            # 根据硬件资源自动选择
    memory-threshold-mb: 2048    # 内存阈值
```

### 2.2 Layer 3 模型选择策略

| 模型 | 默认状态 | 内存需求 | 上下文长度 | 多模态 | 适用场景 |
|------|---------|---------|-----------|--------|---------|
| **Qwen3.5-2B** | ✅ 默认 | 4GB | 262K | ✅ | GPU推理、高性能场景 |
| **BitNet-1.58-3B** | 备选 | 1GB | 4K | ❌ | CPU推理、资源受限场景 |

**自动切换条件：**
- 内存 ≥ 4GB + CPU ≥ 4核 + CPU负载 < 80% → Qwen3.5-2B
- 内存 < 4GB 或 CPU负载 ≥ 80% → BitNet-1.58-3B

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
│  │  │   ├── CASUAL_CHAT → Qwen3Neuron (日常对话)                         │   │
│  │  │   ├── SIMPLE_QUESTION → Qwen3Neuron (简单问题)                     │   │
│  │  │   ├── TOOL_CALL → ToolNeuron (工具调用)                            │   │
│  │  │   └── COMPLEX_TASK → MainBrain (复杂任务)                          │   │
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
│  │  ├── MainBrain (Layer 1) - 复杂推理、跨部门协调                         │   │
│  │  │   └── 转发到部门大脑: TechBrain, HrBrain, FinanceBrain...         │   │
│  │  ├── Qwen3Neuron (Layer 2) - 日常对话、快速响应                        │   │
│  │  └── ToolNeuron (Layer 3) - 工具检测、兜底处理                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  技能执行层 (Skill Layer)                                             │   │
│  │  ├── 76个技能 - 按部门分类                                             │   │
│  │  └── 工具调用 - GitLab, Jira, Jenkins, 钉钉, 飞书...                   │   │
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
│  CHAT_ONLY    │  Qwen3-0.6B                 │  无 (仅闲聊神经元)            │
│  LIMITED      │  Qwen3.5-27B, Qwen3-0.6B    │  AdminBrain, CsBrain         │
│  DEPARTMENT   │  Qwen3.5-27B, Qwen3-0.6B,   │  本部门大脑 + AdminBrain,     │
│               │  BitNet-1.58-3B             │  CsBrain                      │
│  FULL         │  所有模型                    │  所有大脑 + MainBrain         │
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
| **embedding** | ✅ | 100% | 嵌入服务 (LocalEmbeddingService, BGE-M3) |
| **employee** | ✅ | 100% | 员工系统 (DigitalEmployee, HumanEmployee, EmployeePersonality) |
| **evolution** | ✅ | 100% | 进化系统 (KnowledgeEvolver, SkillGenerator, CapabilityEvaluator) |
| **knowledge** | ✅ | 100% | 知识管理 (KnowledgeManager, 三层知识库) |
| **memory** | ✅ | 100% | 记忆系统 (MemoryService, MemOS集成) |
| **model** | ✅ | 100% | 模型管理 (ModelManager, NamedPipeModelClient) |
| **neuron** | ✅ | 100% | 神经元 (AbstractNeuron, BitNetNeuron, Qwen3Neuron, EyeNeuron) |
| **ops** | ✅ | 100% | 运维 (RunQueue, TaskCheckout) |
| **planner** | ✅ | 100% | 任务规划 (TaskPlanner, TaskPlan, TaskStep) |
| **proactive** | ✅ | 100% | 主动预判 (AlertNotifier, CronService, EventHookManager) |
| **provider** | ✅ | 100% | 提供者 (AsrProvider, TtsProvider, BitNetProvider, QwenProvider) |
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
