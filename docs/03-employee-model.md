# 统一员工模型

> 真实员工与数字员工使用相同的模型，差异仅在于信息传递方式

## 一、编制与实例架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    编制 + 实例 架构                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【编制定义 FixedEmployeeDefinition】                                         │
│  ├── 定义：岗位角色、能力清单、工具清单、人格模板                               │
│  ├── 示例：code-reviewer (代码审查员)                                         │
│  │   ├── capabilities: [code-review, git, security-analysis]                │
│  │   ├── tools: [git-cli, sonarqube, codeql]                               │
│  │   └── personality: {rigor: 0.9, creativity: 0.4, ...}                   │
│  └── 作用：定义员工能力边界，约束行为范围                                       │
│                                                                             │
│  【员工实例 Employee Instance】                                               │
│  ├── 基于编制创建，可创建多个实例应对工作量                                     │
│  ├── ID格式：employee://digital/tech/code-reviewer/001                      │
│  │                部门      编制名      实例号                                │
│  └── 所有实例遵循同一编制定义                                                  │
│                                                                             │
│  【核心约束】                                                                 │
│  ├── ✅ 工作量大时：临时创建多个同类型实例                                      │
│  ├── ✅ 所有实例：遵循编制的能力边界和工具授权                                  │
│  └── ❌ 禁止行为：超出编制范围的操作（运行时校验）                              │
│                                                                             │
│  【示例场景】                                                                 │
│  ├── 正常：1个代码审查员实例处理日常审查                                       │
│  ├── 高峰：临时创建3个代码审查员实例应对PR积压                                 │
│  └── 约束：所有实例都只能做代码审查，不能做架构设计                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 二、核心概念

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    统一员工模型 (Unified Employee Model)                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【员工 Employee】                                                           │
│  ├── 定义：企业中的工作单元，可以是真人或数字智能体                             │
│  ├── 统一属性：认证ID、名称、部门、角色、权限、技能                             │
│  └── 差异：信息传递方式不同                                                   │
│                                                                             │
│  【真实员工 Human Employee】                                                  │
│  ├── 认证ID：企业系统账号 (钉钉/飞书/OA)                                      │
│  ├── 信息传递：互动式 (需要人工响应)                                          │
│  ├── 触发方式：被动接收通知，主动发起请求                                      │
│  └── 状态：在线/离线/忙碌                                                    │
│                                                                             │
│  【数字员工 Digital Employee】                                                │
│  ├── 认证ID：系统生成 (employee://digital/{domain}/{name}/{instance})        │
│  ├── 信息传递：自主式 (自动处理和传递)                                        │
│  ├── 触发方式：通道订阅、事件驱动、定时任务                                    │
│  └── 状态：活跃/休眠/学习中                                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 三、员工ID命名规范

```
员工ID格式：
├── 真实员工: employee://human/{authProvider}/{accountId}
│   ├── employee://human/dingtalk/123456
│   ├── employee://human/feishu/ou_xxxxx
│   └── employee://human/oa/zhangsan
│
├── 数字员工: employee://digital/{domain}/{name}/{instance}
│   ├── employee://digital/tech/code-reviewer/001
│   ├── employee://digital/hr/recruiter/001
│   └── employee://digital/finance/accountant/001
│
└── 神经元ID: neuron://{domain}/{name}/{instance}
    ├── neuron://tech/code-reviewer/001
    └── neuron://hr/recruiter/001

管路ID格式：
├── channel://{scope}/{name}
├── channel://enterprise/main          // 企业主群 (广播)
├── channel://department/tech          // 技术部门群
├── channel://private/{emp1}/{emp2}    // 私聊通道
├── channel://perception/{sessionId}   // 感知通道
├── channel://dispatch/{sessionId}     // 路由分发
└── channel://response/{sessionId}     // 响应通道
```

## 四、统一员工模型定义

```java
// 统一员工模型
public class Employee {
    // ========== 基础身份信息 (真实员工和数字员工相同) ==========
    
    // 唯一标识
    private String employeeId;         // 员工ID
    private String employeeType;       // HUMAN / DIGITAL
    
    // 认证信息
    private String authId;             // 认证ID (真实员工: 企业账号; 数字员工: 系统生成)
    private String authProvider;       // 认证提供者 (DINGTALK/FEISHU/OA/SYSTEM)
    
    // 基本信息
    private String name;               // 显示名称
    private String title;              // 职位标题
    private String icon;               // 头像/图标
    private String email;              // 邮箱 (数字员工可为空)
    private String phone;              // 电话 (数字员工可为空)
    
    // 组织信息
    private String department;         // 部门
    private String departmentId;       // 部门ID
    private List<String> roles;        // 角色列表
    private String managerId;          // 上级ID
    
    // ========== 能力信息 (真实员工和数字员工相同) ==========
    
    private List<String> capabilities; // 能力列表
    private List<String> skills;       // 技能列表
    private List<String> tools;        // 可用工具列表
    private AccessLevel accessLevel;   // 访问级别
    
    // ========== 人格配置 (真实员工和数字员工相同) ==========
    
    private EmployeePersona persona;   // 人格配置
    
    // ========== 差异化配置 ==========
    
    // 真实员工特有
    private HumanConfig humanConfig;   // 真实员工配置
    
    // 数字员工特有
    private DigitalConfig digitalConfig; // 数字员工配置
    
    // ========== 状态信息 ==========
    
    private EmployeeStatus status;     // 状态
    private Instant createdAt;         // 创建时间
    private Instant lastActiveAt;      // 最后活跃时间
}

// 人格配置 (统一)
public class EmployeePersona {
    private String role;               // 角色定义
    private String identity;           // 身份描述
    private String communicationStyle; // 沟通风格
    private List<String> principles;   // 工作原则
    private PersonalityParams personality; // 人格参数
}

// 人格参数
public class PersonalityParams {
    private double rigor;              // 严谨度 (0-1)
    private double creativity;         // 创造力 (0-1)
    private double riskTolerance;      // 风险容忍 (0-1)
    private double obedience;          // 服从度 (0-1)
}

// 真实员工配置
public class HumanConfig {
    private String dingTalkId;         // 钉钉ID
    private String feishuId;           // 飞书ID
    private String oaAccountId;        // OA账号
    private NotificationPreference notification; // 通知偏好
}

// 数字员工配置
public class DigitalConfig {
    private String neuronId;           // 关联的神经元ID
    private String brainDomain;        // 所属大脑领域
    private AutonomyLevel autonomy;    // 自治级别
    private List<String> subscribedChannels; // 订阅的通道
}
```

## 五、权限隔离规则

```
权限检查在路由之前执行：
├── CHAT_ONLY (离职/外来) → 只能到 Qwen3Neuron
│   └── 无法访问 MainBrain、部门大脑、企业知识库
│
├── LIMITED (试用期/客户/伙伴) → 受限访问
│   └── 可访问: Qwen3Neuron, AdminBrain, CsBrain
│
├── DEPARTMENT (在职员工) → 本部门完整功能
│   └── 可访问: Qwen3Neuron, ToolNeuron, 本部门大脑
│
└── FULL (董事长) → 所有资源
    └── 可访问: 所有神经元和大脑 + MainBrain
```

### 5.1 用户身份与权限

| 身份 | 访问级别 | 可用模型 |
|------|----------|----------|
| INTERNAL_ACTIVE | DEPARTMENT | Qwen3.5-27B |
| INTERNAL_PROBATION | LIMITED | Qwen3.5-27B |
| INTERNAL_DEPARTED | CHAT_ONLY | Qwen3-0.6B |
| EXTERNAL_VISITOR | CHAT_ONLY | Qwen3-0.6B |
| EXTERNAL_PARTNER | LIMITED | Qwen3.5-27B |

### 5.2 访问级别定义

| 级别 | 权限范围 |
|------|----------|
| CHAT_ONLY | 仅闲聊，禁止企业资源 |
| LIMITED | 部分大脑，禁止敏感知识 |
| DEPARTMENT | 本部门完整功能 |
| FULL | 所有大脑和工具 |

### 5.3 身份识别优先级

1. OAuth 登录 (钉钉/飞书/企业微信)
2. 声纹识别 (CAM++)
3. 手机号验证
4. 人脸识别 (EyeNeuron)

## 六、人格系统

人格系统采用层级设计：
- **BrainPersonality** - 部门大脑人格，定义部门整体风格（领导层）
- **EmployeePersonality** - 员工人格，可继承或覆盖部门默认（员工层）

### 6.1 人格参数 - 基础四维参数

```java
public class PersonalityParams {
    private double rigor;           // 严谨度 (0-1)
    private double creativity;      // 创造力 (0-1)
    private double riskTolerance;   // 风险容忍 (0-1)
    private double obedience;       // 服从度 (0-1)
}
```

### 6.2 大脑人格默认配置

| 大脑 | 严谨度 | 创造力 | 风险容忍 | 服从度 |
|------|--------|--------|---------|--------|
| TechBrain | 0.9 | 0.6 | 0.3 | 0.7 |
| FinanceBrain | 0.95 | 0.2 | 0.1 | 0.9 |
| LegalBrain | 0.95 | 0.3 | 0.2 | 0.8 |
| HrBrain | 0.7 | 0.5 | 0.5 | 0.8 |
| SalesBrain | 0.5 | 0.8 | 0.7 | 0.6 |
| AdminBrain | 0.6 | 0.7 | 0.5 | 0.7 |
| CsBrain | 0.6 | 0.5 | 0.4 | 0.8 |
| OpsBrain | 0.7 | 0.6 | 0.5 | 0.7 |

## 七、数字员工编制汇总

```
employee://digital/tech/ops-engineer/001      // 运维工程师
employee://digital/tech/model-admin/001       // AI模型管理员
employee://digital/tech/state-admin/001       // 状态管理员
employee://digital/tech/security-engineer/001 // 安全工程师
employee://digital/tech/config-admin/001      // 配置管理员
employee://digital/finance/cost-accountant/001 // 成本核算员
employee://digital/finance/budget-admin/001   // 预算管理员
employee://digital/ops/task-scheduler/001     // 任务调度员
employee://digital/ops/process-admin/001      // 流程管理员
employee://digital/sales/channel-manager/001  // 渠道经理
```

## 八、神经元通讯流程

数字员工之间通过通道直接沟通，缩短流程：

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    神经元直接通讯示例                                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【任务执行流程】                                                             │
│                                                                             │
│  渠道经理(S03) ──channel://ops/schedule──▶ 任务调度员(O03)                   │
│       │                                          │                          │
│       │                                          ▼                          │
│       │                              检出任务、分配执行者                      │
│       │                                          │                          │
│       │                                          ▼                          │
│       │                    ┌─────────────────────────────────────┐          │
│       │                    │  后端工程师(T10) 或 前端工程师(T09)  │          │
│       │                    └─────────────────────────────────────┘          │
│       │                                          │                          │
│       │                                          ▼                          │
│       │                              完成任务、提交成果                        │
│       │                                          │                          │
│       │                                          ▼                          │
│       └──────────────────────────────▶ 成本核算员(F03)                       │
│                                                  │                          │
│                                                  ▼                          │
│                                        记录成本、更新项目核算                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```
