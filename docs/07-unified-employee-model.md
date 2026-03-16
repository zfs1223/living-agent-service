# 统一员工模型设计

> 真实员工与数字员工使用相同的模型，差异仅在于信息传递方式
>
> **核心原则：编制约束 + 实例动态**

---

## 零、编制与实例架构

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

---

## 一、核心概念

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

---

## 二、统一员工模型定义

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
    private WorkSchedule schedule;     // 工作时间
}

// 数字员工配置
public class DigitalConfig {
    private String neuronId;           // 神经元ID
    private List<String> subscribeChannels; // 订阅通道
    private List<String> publishChannels;   // 发布通道
    private List<WorkflowBinding> workflows; // 工作流绑定
    private LearningConfig learning;   // 学习配置
    private EvolutionConfig evolution; // 进化配置
    private Duration maxIdleTime;      // 最大空闲时间
    private boolean autoDormant;       // 自动休眠
}

// 员工类型
public enum EmployeeType {
    HUMAN,    // 真实员工
    DIGITAL   // 数字员工
}

// 员工状态
public enum EmployeeStatus {
    // 真实员工状态
    ONLINE,     // 在线
    OFFLINE,    // 离线
    BUSY,       // 忙碌
    AWAY,       // 离开
    
    // 数字员工状态
    ACTIVE,     // 活跃
    DORMANT,    // 休眠
    LEARNING,   // 学习中
    EVOLVING,   // 进化中
    
    // 通用状态
    DISABLED,   // 禁用
    TERMINATED  // 终止
}

// 访问级别
public enum AccessLevel {
    CHAT_ONLY,   // 仅聊天 (离职员工/外来人员)
    LIMITED,     // 受限访问
    DEPARTMENT,  // 部门级访问
    FULL         // 完全访问
}
```

---

## 三、员工ID命名规范

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    员工ID命名规范                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  真实员工ID:                                                                 │
│  ├── employee://human/{authProvider}/{accountId}                           │
│  │   ├── employee://human/dingtalk/123456                                  │
│  │   ├── employee://human/feishu/ou_xxxxx                                  │
│  │   └── employee://human/oa/zhangsan                                      │
│  │                                                                       │
│  └── 认证ID (authId): 直接使用企业系统账号                                   │
│      ├── dingtalk://123456                                                │
│      ├── feishu://ou_xxxxx                                                │
│      └── oa://zhangsan                                                    │
│                                                                             │
│  数字员工ID:                                                                 │
│  ├── employee://digital/{domain}/{name}/{instance}                         │
│  │   ├── employee://digital/tech/code-reviewer/001                         │
│  │   ├── employee://digital/hr/recruiter/001                              │
│  │   └── employee://digital/finance/accountant/001                        │
│  │                                                                       │
│  └── 认证ID (authId): 系统自动生成                                          │
│      └── system://digital/uuid-xxxx-xxxx                                  │
│                                                                             │
│  神经元ID (数字员工专用):                                                     │
│  └── neuron://{domain}/{name}/{instance}                                   │
│      ├── neuron://tech/code-reviewer/001                                   │
│      └── neuron://hr/recruiter/001                                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 四、信息传递方式对比

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    信息传递方式对比                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【真实员工 - 互动式传递】                                                     │
│                                                                             │
│  场景: 系统需要张三审批一份合同                                                │
│                                                                             │
│  流程:                                                                      │
│  1. 系统发送通知 → 钉钉/飞书/邮件                                             │
│  2. 张三收到通知 → 人工查看                                                   │
│  3. 张三做出决策 → 审批通过/驳回                                              │
│  4. 系统接收结果 → 继续后续流程                                               │
│                                                                             │
│  特点:                                                                      │
│  ├── 需要人工响应                                                            │
│  ├── 响应时间不确定                                                          │
│  ├── 可以拒绝或转交                                                          │
│  └── 需要考虑工作时间                                                        │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  NotificationService.send(employeeId, notification)                 │   │
│  │      │                                                               │   │
│  │      ├── 检查员工状态 (在线/离线/忙碌)                                 │   │
│  │      ├── 选择通知渠道 (钉钉/飞书/邮件/短信)                            │   │
│  │      ├── 发送通知                                                    │   │
│  │      └── 等待响应 (超时提醒)                                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  【数字员工 - 自主式传递】                                                     │
│                                                                             │
│  场景: 系统需要CodeReviewer审查代码                                           │
│                                                                             │
│  流程:                                                                      │
│  1. 代码提交 → 触发 channel://tech/code-review                              │
│  2. CodeReviewer 收到消息 → 自动处理                                         │
│  3. CodeReviewer 完成审查 → 发布结果                                         │
│  4. 系统继续后续流程 → 自动流转                                               │
│                                                                             │
│  特点:                                                                      │
│  ├── 自动响应，无需人工干预                                                   │
│  ├── 响应时间可预测                                                          │
│  ├── 24/7 可用                                                              │
│  └── 可以并行处理多任务                                                      │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ChannelManager.publish(channelId, message)                         │   │
│  │      │                                                               │   │
│  │      ├── 消息发布到通道                                               │   │
│  │      ├── 订阅的数字员工自动接收                                       │   │
│  │      ├── 数字员工自主处理                                             │   │
│  │      └── 结果发布到输出通道                                           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 五、统一员工服务接口

```java
// 统一员工服务接口
public interface EmployeeService {
    
    // ========== 员工管理 ==========
    
    // 创建员工 (真实员工从HR系统同步，数字员工从模板创建)
    Employee createEmployee(EmployeeCreationRequest request);
    
    // 获取员工信息
    Employee getEmployee(String employeeId);
    
    // 更新员工信息
    Employee updateEmployee(String employeeId, EmployeeUpdateRequest request);
    
    // 禁用/启用员工
    void setEmployeeStatus(String employeeId, EmployeeStatus status);
    
    // 获取员工列表
    List<Employee> listEmployees(EmployeeQuery query);
    
    // ========== 能力管理 ==========
    
    // 获取员工能力
    List<String> getCapabilities(String employeeId);
    
    // 添加能力
    void addCapability(String employeeId, String capability);
    
    // 获取员工技能
    List<String> getSkills(String employeeId);
    
    // 绑定技能 (数字员工)
    void bindSkill(String employeeId, String skillName);
    
    // ========== 通知/消息 ==========
    
    // 发送通知 (统一接口，内部根据员工类型选择方式)
    void notify(String employeeId, Notification notification);
    
    // 发送消息
    void sendMessage(String employeeId, Message message);
    
    // ========== 状态管理 ==========
    
    // 获取员工状态
    EmployeeStatus getStatus(String employeeId);
    
    // 更新状态
    void updateStatus(String employeeId, EmployeeStatus status);
    
    // 健康检查 (数字员工)
    HealthCheckResult healthCheck(String employeeId);
}

// 员工服务实现
@Service
public class EmployeeServiceImpl implements EmployeeService {
    
    @Autowired private HumanEmployeeService humanService;
    @Autowired private DigitalEmployeeService digitalService;
    @Autowired private NotificationService notificationService;
    @Autowired private ChannelManager channelManager;
    
    @Override
    public void notify(String employeeId, Notification notification) {
        Employee employee = getEmployee(employeeId);
        
        if (employee.getEmployeeType() == EmployeeType.HUMAN) {
            // 真实员工: 互动式传递
            humanService.notify(employee, notification);
        } else {
            // 数字员工: 自主式传递 (通过通道)
            digitalService.notify(employee, notification);
        }
    }
}

// 真实员工服务
@Service
public class HumanEmployeeService {
    
    public void notify(Employee employee, Notification notification) {
        // 检查员工状态
        if (employee.getStatus() == EmployeeStatus.OFFLINE) {
            // 离线时选择其他渠道
            notification.setUrgent(true);
        }
        
        // 选择通知渠道
        HumanConfig config = employee.getHumanConfig();
        NotificationPreference pref = config.getNotification();
        
        // 发送通知
        if (pref.isDingTalkEnabled()) {
            notificationService.sendDingTalk(employee.getAuthId(), notification);
        }
        if (pref.isFeishuEnabled()) {
            notificationService.sendFeishu(employee.getAuthId(), notification);
        }
        if (pref.isEmailEnabled()) {
            notificationService.sendEmail(employee.getEmail(), notification);
        }
    }
}

// 数字员工服务
@Service
public class DigitalEmployeeService {
    
    @Autowired private ChannelManager channelManager;
    @Autowired private NeuronRegistry neuronRegistry;
    
    public void notify(Employee employee, Notification notification) {
        DigitalConfig config = employee.getDigitalConfig();
        
        // 通过通道自主传递
        ChannelMessage message = ChannelMessage.builder()
            .from(notification.getFrom())
            .to(employee.getEmployeeId())
            .content(notification.getContent())
            .type(MessageType.TASK)
            .build();
        
        // 发布到员工订阅的通道
        for (String channelId : config.getSubscribeChannels()) {
            channelManager.publish(channelId, message);
        }
    }
}
```

---

## 六、数据库表设计

```sql
-- 统一员工表
CREATE TABLE employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(255) UNIQUE NOT NULL,  -- employee://human/dingtalk/123456
    employee_type VARCHAR(20) NOT NULL,        -- HUMAN / DIGITAL
    
    -- 认证信息
    auth_id VARCHAR(255) NOT NULL,             -- 认证ID
    auth_provider VARCHAR(50),                 -- DINGTALK/FEISHU/OA/SYSTEM
    
    -- 基本信息
    name VARCHAR(100) NOT NULL,
    title VARCHAR(200),
    icon VARCHAR(500),
    email VARCHAR(200),
    phone VARCHAR(50),
    
    -- 组织信息
    department_id VARCHAR(100),
    department VARCHAR(100),
    roles TEXT[],
    manager_id VARCHAR(255),
    
    -- 能力信息
    capabilities TEXT[],
    skills TEXT[],
    tools TEXT[],
    access_level VARCHAR(20) DEFAULT 'DEPARTMENT',
    
    -- 人格配置
    persona JSONB,
    
    -- 真实员工特有
    human_config JSONB,
    
    -- 数字员工特有
    digital_config JSONB,
    
    -- 状态
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    
    -- 统计
    task_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP,
    
    CONSTRAINT valid_employee_type CHECK (employee_type IN ('HUMAN', 'DIGITAL')),
    CONSTRAINT valid_status CHECK (status IN ('ONLINE', 'OFFLINE', 'BUSY', 'AWAY', 'ACTIVE', 'DORMANT', 'LEARNING', 'EVOLVING', 'DISABLED', 'TERMINATED'))
);

-- 创建索引
CREATE INDEX idx_employees_type ON employees(employee_type);
CREATE INDEX idx_employees_department ON employees(department_id);
CREATE INDEX idx_employees_auth_id ON employees(auth_id);
CREATE INDEX idx_employees_status ON employees(status);

-- 员工能力映射表
CREATE TABLE employee_capabilities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(255) NOT NULL REFERENCES employees(employee_id),
    capability VARCHAR(100) NOT NULL,
    proficiency DECIMAL(3,2) DEFAULT 0.5,      -- 熟练度 0-1
    acquired_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(employee_id, capability)
);

-- 员工技能映射表
CREATE TABLE employee_skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(255) NOT NULL REFERENCES employees(employee_id),
    skill_name VARCHAR(100) NOT NULL,
    skill_level VARCHAR(20) DEFAULT 'BASIC',    -- BASIC/INTERMEDIATE/ADVANCED/EXPERT
    bound_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(employee_id, skill_name)
);

-- 员工工具权限表
CREATE TABLE employee_tools (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(255) NOT NULL REFERENCES employees(employee_id),
    tool_name VARCHAR(100) NOT NULL,
    permission_level VARCHAR(20) DEFAULT 'READ', -- READ/WRITE/ADMIN
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(employee_id, tool_name)
);
```

---

## 七、部门员工配置示例

### 7.1 真实员工示例

```json
{
  "employeeId": "employee://human/dingtalk/123456",
  "employeeType": "HUMAN",
  "authId": "dingtalk://123456",
  "authProvider": "DINGTALK",
  "name": "张三",
  "title": "高级开发工程师",
  "icon": "https://xxx.com/avatar/zhangsan.jpg",
  "email": "zhangsan@company.com",
  "phone": "13800138000",
  "department": "技术部",
  "departmentId": "dept_tech",
  "roles": ["开发工程师", "代码审查员"],
  "managerId": "employee://human/dingtalk/123000",
  "capabilities": ["代码开发", "代码审查", "技术方案设计"],
  "skills": ["gitlab-mr-review", "jenkins-build", "java-development"],
  "tools": ["gitlab_tool", "jenkins_tool", "jira_tool"],
  "accessLevel": "DEPARTMENT",
  "persona": {
    "role": "Senior Software Engineer",
    "identity": "负责核心业务系统开发和代码审查",
    "communicationStyle": "专业、简洁",
    "principles": ["代码质量优先", "及时响应"],
    "personality": {
      "rigor": 0.8,
      "creativity": 0.6,
      "riskTolerance": 0.5,
      "obedience": 0.7
    }
  },
  "humanConfig": {
    "dingTalkId": "123456",
    "notification": {
      "dingTalk": true,
      "email": true,
      "sms": false
    },
    "schedule": {
      "workDays": ["MON", "TUE", "WED", "THU", "FRI"],
      "workHours": "09:00-18:00"
    }
  },
  "status": "ONLINE"
}
```

### 7.2 数字员工示例

```json
{
  "employeeId": "employee://digital/tech/code-reviewer/001",
  "employeeType": "DIGITAL",
  "authId": "system://digital/uuid-xxxx-xxxx",
  "authProvider": "SYSTEM",
  "name": "CodeReviewer",
  "title": "代码审查专家",
  "icon": "🔍",
  "department": "技术部",
  "departmentId": "dept_tech",
  "roles": ["代码审查员"],
  "capabilities": ["代码审查", "安全审计", "性能分析"],
  "skills": ["gitlab-mr-review", "code-quality-check", "security-scan"],
  "tools": ["gitlab_tool", "http_tool", "file_read_tool"],
  "accessLevel": "DEPARTMENT",
  "persona": {
    "role": "Senior Code Reviewer",
    "identity": "专注于代码质量、安全性和最佳实践",
    "communicationStyle": "专业、简洁、建设性",
    "principles": [
      "所有代码变更必须经过审查",
      "关注代码可读性和可维护性",
      "识别潜在的安全漏洞"
    ],
    "personality": {
      "rigor": 0.9,
      "creativity": 0.4,
      "riskTolerance": 0.2,
      "obedience": 0.85
    }
  },
  "digitalConfig": {
    "neuronId": "neuron://tech/code-reviewer/001",
    "subscribeChannels": [
      "channel://tech/code-review",
      "channel://dispatch/tech"
    ],
    "publishChannels": [
      "channel://tech/review-result"
    ],
    "workflows": [
      {
        "trigger": "MR|merge request|代码审查",
        "workflowId": "code-review-workflow",
        "description": "[MR] 执行代码审查"
      }
    ],
    "learning": {
      "enabled": true,
      "sources": ["review-feedback", "security-updates"]
    },
    "maxIdleTime": "7d",
    "autoDormant": true
  },
  "status": "ACTIVE"
}
```

---

## 八、协作场景示例

### 8.1 真实员工 + 数字员工协作

```
场景: 功能开发完整流程

用户请求: "帮我完成用户登录功能的开发和测试"
    │
    ▼
MainBrain 分析任务，创建协作流程
    │
    ├── Step 1: 需求分析
    │   └── 数字员工 Analyst-001 → 输出需求文档
    │
    ├── Step 2: 架构设计
    │   └── 数字员工 Architect-001 → 输出架构设计
    │
    ├── Step 3: 开发实现
    │   └── 通知真实员工 张三 (钉钉) → 人工开发
    │       └── 张三完成开发 → 提交MR
    │
    ├── Step 4: 代码审查
    │   └── 数字员工 CodeReviewer-001 → 自动审查
    │       └── 发现问题 → 通知张三修改
    │
    ├── Step 5: 测试验证
    │   └── 数字员工 QA-Tester-001 → 自动测试
    │
    └── Step 6: 上线部署
        └── 通知真实员工 李四 (运维) → 人工审批部署
```

### 8.2 统一通知流程

```java
// 统一通知服务
@Service
public class UnifiedNotificationService {
    
    @Autowired private EmployeeService employeeService;
    
    public void notifyEmployee(String employeeId, TaskNotification notification) {
        Employee employee = employeeService.getEmployee(employeeId);
        
        // 统一的通知内容
        NotificationContent content = NotificationContent.builder()
            .title(notification.getTitle())
            .body(notification.getBody())
            .actionUrl(notification.getActionUrl())
            .priority(notification.getPriority())
            .build();
        
        if (employee.getEmployeeType() == EmployeeType.HUMAN) {
            // 真实员工: 互动式通知
            notifyHumanEmployee(employee, content);
        } else {
            // 数字员工: 自主式处理
            notifyDigitalEmployee(employee, content);
        }
    }
    
    private void notifyHumanEmployee(Employee employee, NotificationContent content) {
        // 检查在线状态
        if (employee.getStatus() == EmployeeStatus.OFFLINE) {
            // 离线时发送短信
            smsService.send(employee.getPhone(), content);
        }
        
        // 发送即时消息
        HumanConfig config = employee.getHumanConfig();
        if (config.getDingTalkId() != null) {
            dingTalkService.sendMessage(config.getDingTalkId(), content);
        }
        
        // 发送邮件
        if (employee.getEmail() != null) {
            emailService.send(employee.getEmail(), content);
        }
    }
    
    private void notifyDigitalEmployee(Employee employee, NotificationContent content) {
        // 数字员工通过通道接收任务
        DigitalConfig config = employee.getDigitalConfig();
        
        ChannelMessage message = ChannelMessage.builder()
            .from("system://main")
            .to(employee.getEmployeeId())
            .content(content)
            .type(MessageType.TASK)
            .priority(content.getPriority())
            .build();
        
        // 发布到订阅通道
        for (String channelId : config.getSubscribeChannels()) {
            channelManager.publish(channelId, message);
        }
    }
}
```

---

## 九、与现有系统集成

### 9.1 真实员工同步

```java
// 真实员工同步服务
@Service
public class HumanEmployeeSyncService {
    
    @Autowired private EmployeeService employeeService;
    @Autowired private DingTalkSyncAdapter dingTalkAdapter;
    @Autowired private FeishuSyncAdapter feishuAdapter;
    
    @Scheduled(cron = "0 0 * * * ?")  // 每小时同步
    public void syncEmployees() {
        // 从钉钉同步
        List<Employee> dingTalkEmployees = dingTalkAdapter.fetchEmployees();
        for (Employee emp : dingTalkEmployees) {
            employeeService.createOrUpdateEmployee(emp);
        }
        
        // 从飞书同步
        List<Employee> feishuEmployees = feishuAdapter.fetchEmployees();
        for (Employee emp : feishuEmployees) {
            employeeService.createOrUpdateEmployee(emp);
        }
    }
}
```

### 9.2 数字员工生命周期

```java
// 数字员工生命周期管理
@Service
public class DigitalEmployeeLifecycleService {
    
    @Autowired private EmployeeService employeeService;
    @Autowired private NeuronRegistry neuronRegistry;
    
    // 创建数字员工
    public Employee createDigitalEmployee(DigitalEmployeeRequest request) {
        Employee employee = Employee.builder()
            .employeeId(generateEmployeeId(request))
            .employeeType(EmployeeType.DIGITAL)
            .authId(generateAuthId())
            .authProvider("SYSTEM")
            .name(request.getName())
            .title(request.getTitle())
            .department(request.getDepartment())
            .capabilities(request.getCapabilities())
            .skills(request.getSkills())
            .persona(request.getPersona())
            .digitalConfig(request.getDigitalConfig())
            .status(EmployeeStatus.ACTIVE)
            .build();
        
        // 创建对应的神经元
        Neuron neuron = createNeuron(employee);
        neuronRegistry.register(neuron);
        
        return employeeService.createEmployee(employee);
    }
    
    // 自动休眠
    @Scheduled(fixedRate = 3600000)  // 每小时检查
    public void checkIdleEmployees() {
        List<Employee> idleEmployees = employeeService.findIdleDigitalEmployees();
        
        for (Employee emp : idleEmployees) {
            DigitalConfig config = emp.getDigitalConfig();
            if (config.isAutoDormant()) {
                employeeService.updateStatus(emp.getEmployeeId(), EmployeeStatus.DORMANT);
            }
        }
    }
}
```

---

## 十、总结

| 特性 | 真实员工 | 数字员工 |
|------|---------|---------|
| **认证ID** | 企业系统账号 | 系统生成 |
| **名称/标题** | ✅ 统一 | ✅ 统一 |
| **部门/角色** | ✅ 统一 | ✅ 统一 |
| **能力/技能** | ✅ 统一 | ✅ 统一 |
| **人格配置** | ✅ 统一 | ✅ 统一 |
| **信息传递** | 互动式 | 自主式 |
| **通知方式** | 钉钉/飞书/邮件 | 通道订阅 |
| **工作时间** | 9-6 工作日 | 24/7 |
| **响应方式** | 人工响应 | 自动处理 |
