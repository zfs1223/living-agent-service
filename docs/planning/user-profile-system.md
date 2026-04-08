# 用户画像系统设计

> 统一用户画像框架 - 所有服务使用者的完整记录

---

## 一、概述

用户画像是 `living-agent-service` 中所有使用过服务的人员的完整记录。无论是正式员工、访客还是数字员工，都有对应的画像记录，只是详细程度不同。

### 1.1 设计原则

1. **全员记录**：所有使用过服务的人员都有画像记录
2. **分层详细**：根据身份不同，画像详细程度不同
3. **唯一标识**：每个用户有唯一标识，支持多种认证方式
4. **知识关联**：画像与知识体系（L1/L2/L3）紧密关联
5. **人格配置**：支持人格参数配置和进化

---

## 二、唯一标识体系

### 2.1 标识类型

| 标识类型 | 字段名 | 格式 | 说明 |
|---------|--------|------|------|
| **员工ID** | `employee_id` | `emp_xxxxxxxx` | 正式员工唯一标识 |
| **声纹ID** | `speaker_id` | `spk_xxxxxxxx` | 声纹唯一标识 |
| **会话ID** | `session_id` | `sess_xxxxxxxx` | 访客临时标识 |
| **数字员工ID** | `digital_id` | `digital://dept/name/instance` | 数字员工标识 |

### 2.2 标识关联关系

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         唯一标识关联关系                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【正式员工】                                                                │
│  employee_id ──────────────────────────────────────────────────────────────▶│
│       │                                                                     │
│       ├──▶ speaker_id (声纹注册后关联)                                      │
│       ├──▶ oauth_provider + oauth_user_id (OAuth登录关联)                   │
│       ├──▶ phone (手机号关联)                                               │
│       └──▶ email (邮箱关联)                                                 │
│                                                                             │
│  【访客】                                                                    │
│  session_id ──────────────────────────────────────────────────────────────▶│
│       │                                                                     │
│       └──▶ speaker_id (声纹记录后关联，待确认身份)                           │
│                                                                             │
│  【数字员工】                                                                │
│  digital_id ──────────────────────────────────────────────────────────────▶│
│       │                                                                     │
│       └──▶ neuron_id (神经元实例关联)                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 身份识别优先级

```
1. OAuth 登录 (钉钉/飞书/企业微信)
   └── 获取 employee_id → 完整画像

2. 声纹识别 (CAM++)
   └── 获取 speaker_id → 查找关联的 employee_id
       ├── 找到 → 完整画像
       └── 未找到 → 创建/更新访客画像

3. 手机号验证
   └── 获取 phone → 查找 employee_id → 完整画像

4. 人脸识别 (EyeNeuron)
   └── 获取 face_embedding → 查找关联的 employee_id

5. 会话标识
   └── 获取 session_id → 访客画像
```

---

## 三、画像详细程度

### 3.1 分层画像

| 身份 | 画像详细度 | 包含内容 |
|------|-----------|---------|
| **INTERNAL_CHAIRMAN** | 完整画像 | 身份、行为、知识、人格、声纹、权限 |
| **INTERNAL_ACTIVE** | 完整画像 | 身份、行为、知识、人格、声纹、权限 |
| **INTERNAL_PROBATION** | 部分画像 | 身份、行为、知识、人格、声纹 |
| **INTERNAL_DEPARTED** | 存档画像 | 身份、历史记录（只读） |
| **EXTERNAL_VISITOR** | 基础画像 | 会话信息、声纹记录、行为偏好 |
| **EXTERNAL_PARTNER** | 受限画像 | 身份、权限、协作记录 |
| **DIGITAL_EMPLOYEE** | 配置画像 | 人格、技能、知识、能力 |

### 3.2 画像内容矩阵

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         画像内容矩阵                                          │
├──────────────────────┬───────────────────────────────────────────────────────┤
│                      │ 董事长 │ 正式员工 │ 试用期 │ 离职 │ 访客 │ 合作伙伴 │ 数字员工 │
├──────────────────────┼────────┼─────────┼────────┼──────┼──────┼─────────┼─────────┤
│ 基础身份信息          │   ✅   │    ✅    │   ✅   │  ✅  │  ✅  │    ✅    │    ✅    │
│ 组织架构信息          │   ✅   │    ✅    │   ✅   │  ✅  │  ❌  │    ❌    │    ✅    │
│ 权限配置              │   ✅   │    ✅    │   ✅   │  ❌  │  ❌  │    ✅    │    ✅    │
│ 声纹信息              │   ✅   │    ✅    │   ✅   │  ✅  │  ✅  │    ❌    │    ❌    │
│ 人格配置              │   ✅   │    ✅    │   ✅   │  ✅  │  ❌  │    ❌    │    ✅    │
│ 行为偏好              │   ✅   │    ✅    │   ✅   │  ✅  │  ✅  │    ✅    │    ✅    │
│ 私有知识 (L1)         │   ✅   │    ✅    │   ✅   │  ✅  │  ✅  │    ❌    │    ✅    │
│ 部门知识 (L2)         │   ✅   │    ✅    │   ✅   │  ❌  │  ❌  │    ✅    │    ✅    │
│ 共享知识 (L3)         │   ✅   │    ✅    │   ✅   │  ❌  │  ❌  │    ✅    │    ✅    │
│ 使用统计              │   ✅   │    ✅    │   ✅   │  ✅  │  ✅  │    ✅    │    ✅    │
│ 进化记录              │   ✅   │    ✅    │   ✅   │  ❌  │  ❌  │    ❌    │    ✅    │
└──────────────────────┴────────┴─────────┴────────┴──────┴──────┴─────────┴─────────┘
```

---

## 四、用户画像数据模型

### 4.1 核心实体

```java
public class UserProfile {
    // === 唯一标识 ===
    private String profileId;              // 画像唯一ID
    private String employeeId;             // 员工ID（可选）
    private String speakerId;              // 声纹ID（可选）
    private String digitalId;              // 数字员工ID（可选）
    
    // === 基础身份 ===
    private String name;                   // 姓名
    private String displayName;            // 显示名称
    private String email;                  // 邮箱
    private String phone;                  // 手机号
    private String avatarUrl;              // 头像URL
    private UserIdentity identity;         // 身份类型
    private AccessLevel accessLevel;       // 访问级别
    
    // === 组织信息 ===
    private String departmentId;           // 部门ID
    private String departmentName;         // 部门名称
    private String position;               // 职位
    private String managerId;              // 上级ID
    private List<String> roles;            // 角色列表
    private List<String> permissions;      // 权限列表
    
    // === 声纹信息 ===
    private String voicePrintId;           // 声纹ID
    private Integer voicePrintDimension;   // 声纹维度
    private Instant voicePrintRegisteredAt;// 声纹注册时间
    
    // === 人格配置 ===
    private PersonalityConfig personality; // 人格配置
    
    // === 行为偏好 ===
    private BehaviorPreferences behavior;  // 行为偏好
    
    // === 知识关联 ===
    private KnowledgeAssociation knowledge;// 知识关联
    
    // === 使用统计 ===
    private UsageStatistics statistics;    // 使用统计
    
    // === 时间戳 ===
    private Instant createdAt;             // 创建时间
    private Instant updatedAt;             // 更新时间
    private Instant lastActiveAt;          // 最后活跃时间
    private boolean active;                // 是否活跃
}
```

### 4.2 人格配置

```java
public class PersonalityConfig {
    private double rigor;                  // 严谨度 (0-1)
    private double creativity;             // 创造力 (0-1)
    private double riskTolerance;          // 风险容忍 (0-1)
    private double obedience;              // 服从度 (0-1)
    private double verbosity;              // 话唠度 (0-1) - 数字员工专用
    
    private PersonalitySource source;      // 来源
    private Instant updatedAt;             // 更新时间
    private List<PersonalityEvolution> evolutionHistory; // 进化历史
    
    public enum PersonalitySource {
        TEMPLATE,      // 模板默认
        DEPARTMENT,    // 部门继承
        INFERRED,      // 行为推断
        MANUAL,        // 手动配置
        EVOLVED        // 进化调整
    }
}
```

### 4.3 行为偏好

```java
public class BehaviorPreferences {
    // === 沟通偏好 ===
    private String preferredLanguage;      // 首选语言
    private String communicationStyle;     // 沟通风格 (formal/casual/technical)
    private boolean prefersVoice;          // 偏好语音交互
    private boolean prefersText;           // 偏好文本交互
    
    // === 工作习惯 ===
    private String preferredWorkHours;     // 首选工作时间
    private int averageSessionDuration;    // 平均会话时长
    private List<String> frequentlyUsedSkills; // 常用技能
    private List<String> frequentlyUsedTools;  // 常用工具
    
    // === 交互模式 ===
    private InteractionPattern interactionPattern; // 交互模式
    private Map<String, Integer> topicInterests;   // 话题兴趣度
    
    // === 学习偏好 ===
    private String learningStyle;          // 学习风格
    private int preferredDetailLevel;      // 详情程度偏好 (1-5)
    
    public enum InteractionPattern {
        EXPLORATORY,   // 探索型
        TASK_FOCUSED,  // 任务型
        CONVERSATIONAL,// 对话型
        EFFICIENT      // 效率型
    }
}
```

### 4.4 知识关联

```java
public class KnowledgeAssociation {
    // === L1 私有知识 ===
    private int privateKnowledgeCount;     // 私有知识条数
    private long privateKnowledgeSize;     // 私有知识大小 (bytes)
    private String privateKnowledgeNamespace; // 私有知识命名空间
    
    // === L2 部门知识 ===
    private String departmentKnowledgeNamespace; // 部门知识命名空间
    private List<String> contributedKnowledge;   // 贡献的知识ID
    
    // === L3 共享知识 ===
    private List<String> sharedKnowledgeAccess;  // 可访问的共享知识
    
    // === 知识统计 ===
    private int totalKnowledgeCreated;     // 创建的知识总数
    private int totalKnowledgeAccessed;    // 访问的知识总数
    private double averageKnowledgeRating; // 知识平均评分
}
```

### 4.5 使用统计

```java
public class UsageStatistics {
    // === 会话统计 ===
    private long totalSessions;            // 总会话数
    private long totalInteractionTime;     // 总交互时间 (ms)
    private long averageSessionLength;     // 平均会话长度 (ms)
    
    // === 任务统计 ===
    private long totalTasksCompleted;      // 完成任务数
    private double taskSuccessRate;        // 任务成功率
    private double averageTaskDuration;    // 平均任务时长 (ms)
    
    // === 技能使用 ===
    private Map<String, Integer> skillUsageCount;   // 技能使用次数
    private Map<String, Double> skillSuccessRate;   // 技能成功率
    
    // === 时间分布 ===
    private Map<Integer, Integer> hourlyActivity;   // 每小时活动分布
    private Map<Integer, Integer> dailyActivity;    // 每日活动分布
    
    // === 最近活动 ===
    private Instant lastSessionAt;         // 最后会话时间
    private String lastUsedSkill;          // 最后使用的技能
    private String lastQueryTopic;         // 最后查询主题
}
```

---

## 五、数据库表设计

### 5.1 用户画像主表

```sql
CREATE TABLE user_profiles (
    profile_id VARCHAR(64) PRIMARY KEY,
    
    -- 唯一标识
    employee_id VARCHAR(100) UNIQUE,
    speaker_id VARCHAR(100) UNIQUE,
    digital_id VARCHAR(200) UNIQUE,
    
    -- 基础身份
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    email VARCHAR(128),
    phone VARCHAR(20),
    avatar_url VARCHAR(512),
    identity VARCHAR(32) NOT NULL DEFAULT 'EXTERNAL_VISITOR',
    access_level VARCHAR(16) NOT NULL DEFAULT 'CHAT_ONLY',
    
    -- 组织信息
    department_id VARCHAR(64),
    department_name VARCHAR(128),
    position VARCHAR(64),
    manager_id VARCHAR(100),
    roles TEXT[],
    permissions TEXT[],
    
    -- 声纹信息
    voice_print_id VARCHAR(64),
    voice_print_dimension INTEGER,
    voice_print_registered_at TIMESTAMP WITH TIME ZONE,
    
    -- 人格配置 (JSONB)
    personality JSONB,
    
    -- 行为偏好 (JSONB)
    behavior_preferences JSONB,
    
    -- 知识关联 (JSONB)
    knowledge_association JSONB,
    
    -- 使用统计 (JSONB)
    usage_statistics JSONB,
    
    -- 时间戳
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP WITH TIME ZONE,
    active BOOLEAN DEFAULT TRUE,
    
    -- 索引
    CONSTRAINT fk_employee FOREIGN KEY (employee_id) 
        REFERENCES enterprise_employees(employee_id) ON DELETE SET NULL
);

CREATE INDEX idx_profile_employee ON user_profiles(employee_id);
CREATE INDEX idx_profile_speaker ON user_profiles(speaker_id);
CREATE INDEX idx_profile_digital ON user_profiles(digital_id);
CREATE INDEX idx_profile_identity ON user_profiles(identity);
CREATE INDEX idx_profile_department ON user_profiles(department_id);
CREATE INDEX idx_profile_active ON user_profiles(active);
CREATE INDEX idx_profile_last_active ON user_profiles(last_active_at);
```

### 5.2 人格进化历史表

```sql
CREATE TABLE personality_evolution_history (
    id BIGSERIAL PRIMARY KEY,
    profile_id VARCHAR(64) NOT NULL,
    
    -- 进化信息
    evolution_type VARCHAR(32) NOT NULL,    -- INFERRED/MANUAL/EVOLVED
    trigger_event VARCHAR(64),              -- 触发事件
    
    -- 参数变化
    param_name VARCHAR(32) NOT NULL,        -- rigor/creativity/riskTolerance/obedience
    old_value DECIMAL(3,2),
    new_value DECIMAL(3,2),
    delta DECIMAL(3,2),
    
    -- 上下文
    context JSONB,
    
    -- 时间戳
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (profile_id) REFERENCES user_profiles(profile_id) ON DELETE CASCADE
);

CREATE INDEX idx_personality_evo_profile ON personality_evolution_history(profile_id);
CREATE INDEX idx_personality_evo_type ON personality_evolution_history(evolution_type);
CREATE INDEX idx_personality_evo_param ON personality_evolution_history(param_name);
```

### 5.3 行为模式表

```sql
CREATE TABLE user_behavior_patterns (
    id BIGSERIAL PRIMARY KEY,
    profile_id VARCHAR(64) NOT NULL,
    
    -- 模式信息
    pattern_type VARCHAR(32) NOT NULL,      -- INTERACTION/COMMUNICATION/WORKFLOW/LEARNING
    pattern_data JSONB NOT NULL,
    
    -- 统计
    occurrence_count INTEGER DEFAULT 1,
    confidence_score DECIMAL(3,2),
    
    -- 时间戳
    first_observed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_observed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (profile_id) REFERENCES user_profiles(profile_id) ON DELETE CASCADE,
    UNIQUE(profile_id, pattern_type)
);

CREATE INDEX idx_behavior_pattern_profile ON user_behavior_patterns(profile_id);
CREATE INDEX idx_behavior_pattern_type ON user_behavior_patterns(pattern_type);
```

---

## 六、画像创建流程

### 6.1 正式员工画像创建

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    正式员工画像创建流程                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. 系统初始化/HR导入                                                        │
│     ├── 创建 enterprise_employees 记录                                      │
│     └── 触发 UserProfileService.createProfile()                             │
│                                                                             │
│  2. 画像初始化                                                               │
│     ├── 生成 profile_id                                                     │
│     ├── 关联 employee_id                                                    │
│     ├── 设置身份和权限                                                       │
│     ├── 继承部门人格配置                                                     │
│     └── 初始化行为偏好                                                       │
│                                                                             │
│  3. 声纹注册 (可选)                                                          │
│     ├── 用户在个人中心注册声纹                                               │
│     ├── 创建 speaker_id                                                     │
│     ├── 更新 profile.speaker_id                                             │
│     └── 创建 speaker_profiles 记录                                          │
│                                                                             │
│  4. 画像进化                                                                 │
│     ├── 行为分析更新偏好                                                     │
│     ├── 任务执行更新统计                                                     │
│     ├── 知识贡献更新关联                                                     │
│     └── 人格推断更新配置                                                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 访客画像创建

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    访客画像创建流程                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. 首次访问                                                                 │
│     ├── 创建 session_id                                                     │
│     ├── 创建基础画像 (identity=EXTERNAL_VISITOR)                            │
│     └── 记录访问来源                                                         │
│                                                                             │
│  2. 声纹记录 (前台接待)                                                       │
│     ├── 访客说话时自动提取声纹                                               │
│     ├── 创建 speaker_id                                                     │
│     ├── 更新 profile.speaker_id                                             │
│     └── 记录声纹但不用于登录                                                 │
│                                                                             │
│  3. 身份确认                                                                 │
│     ├── 确认访客身份后升级画像                                               │
│     ├── 关联到正式员工记录 (如果是员工)                                      │
│     └── 或标记为合作伙伴                                                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.3 数字员工画像创建

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    数字员工画像创建流程                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. 模板选择                                                                │
│     ├── 从员工模板库选择模板                                                 │
│     └── 或通过 SkillGenerator 生成                                          │
│                                                                             │
│  2. 画像创建                                                                │
│     ├── 生成 digital_id                                                     │
│     ├── 设置人格配置 (从模板或部门继承)                                      │
│     ├── 配置技能和工具                                                       │
│     └── 初始化知识关联                                                       │
│                                                                             │
│  3. 能力配置                                                                │
│     ├── 关联神经元实例                                                       │
│     ├── 配置工作流程                                                         │
│     └── 设置进化参数                                                         │
│                                                                             │
│  4. 持续进化                                                                │
│     ├── 任务执行积累经验                                                     │
│     ├── 人格参数动态调整                                                     │
│     └── 技能能力提升                                                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 七、人格配置继承关系

### 7.1 人格配置层级

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    人格配置层级关系                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【Level 1: 部门大脑人格】                                                   │
│  BrainPersonality                                                          │
│  ├── TechBrain: rigor=0.8, creativity=0.6, risk=0.5, obedience=0.7         │
│  ├── AdminBrain: rigor=0.7, creativity=0.4, risk=0.3, obedience=0.9        │
│  └── ... (其他部门)                                                         │
│                                                                             │
│  【Level 2: 员工人格】                                                       │
│  EmployeePersonality / PersonalityConfig                                    │
│  ├── 继承自部门大脑 (source=DEPARTMENT)                                     │
│  ├── 手动配置覆盖 (source=MANUAL)                                           │
│  ├── 行为推断调整 (source=INFERRED)                                         │
│  └── 进化系统调整 (source=EVOLVED)                                          │
│                                                                             │
│  【Level 3: 数字员工人格】                                                   │
│  PersonalityConfig (digital employee)                                       │
│  ├── 从模板继承 (source=TEMPLATE)                                           │
│  ├── 从部门大脑继承 (source=DEPARTMENT)                                     │
│  └── 进化调整 (source=EVOLVED)                                              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.2 人格配置优先级

```
优先级 (高 → 低):

1. MANUAL (手动配置)         - 用户/管理员明确设置
2. EVOLVED (进化调整)        - 进化系统自动调整
3. INFERRED (行为推断)       - 从用户行为推断
4. DEPARTMENT (部门继承)     - 从部门大脑继承
5. TEMPLATE (模板默认)       - 系统模板默认值
```

---

## 八、知识关联机制

### 8.1 知识层级与画像关联

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    知识层级与画像关联                                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【L1: 私有知识】                                                            │
│  ├── 命名空间: profile:{profile_id}:private                                 │
│  ├── 所有者: profile_id                                                     │
│  ├── 内容: 个人经验、对话历史、学习记录                                      │
│  └── 访问: 仅所有者                                                         │
│                                                                             │
│  【L2: 部门知识】                                                            │
│  ├── 命名空间: department:{department_id}                                   │
│  ├── 所有者: department_id                                                  │
│  ├── 内容: 部门最佳实践、业务规则                                           │
│  └── 访问: 部门成员 + 有权限的合作伙伴                                       │
│                                                                             │
│  【L3: 共享知识】                                                            │
│  ├── 命名空间: shared:global                                                │
│  ├── 所有者: system                                                         │
│  ├── 内容: 通用知识、公司制度                                               │
│  └── 访问: 所有正式员工                                                     │
│                                                                             │
│  【知识晋升路径】                                                            │
│  L1 → L2: 个人知识被部门验证后晋升                                          │
│  L2 → L3: 部门知识被多部门验证后晋升                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 8.2 知识访问权限

| 身份 | L1 私有知识 | L2 部门知识 | L3 共享知识 |
|------|-----------|-----------|-----------|
| INTERNAL_CHAIRMAN | ✅ 自己的 | ✅ 所有部门 | ✅ 全部 |
| INTERNAL_ACTIVE | ✅ 自己的 | ✅ 本部门 | ✅ 全部 |
| INTERNAL_PROBATION | ✅ 自己的 | ✅ 本部门 | ✅ 全部 |
| INTERNAL_DEPARTED | ✅ 自己的 | ❌ | ❌ |
| EXTERNAL_VISITOR | ✅ 自己的 | ❌ | ❌ |
| EXTERNAL_PARTNER | ✅ 自己的 | ✅ 授权部门 | ✅ 授权范围 |
| DIGITAL_EMPLOYEE | ✅ 自己的 | ✅ 所属部门 | ✅ 全部 |

---

## 九、服务接口

### 9.1 UserProfileService

```java
public interface UserProfileService {
    
    // === 画像创建 ===
    UserProfile createProfile(UserProfileCreateRequest request);
    UserProfile createProfileFromEmployee(String employeeId);
    UserProfile createProfileForVisitor(String sessionId);
    UserProfile createProfileForDigital(DigitalEmployeeConfig config);
    
    // === 画像查询 ===
    Optional<UserProfile> findById(String profileId);
    Optional<UserProfile> findByEmployeeId(String employeeId);
    Optional<UserProfile> findBySpeakerId(String speakerId);
    Optional<UserProfile> findByDigitalId(String digitalId);
    Optional<UserProfile> findBySessionId(String sessionId);
    
    // === 画像更新 ===
    UserProfile updateProfile(String profileId, UserProfileUpdateRequest request);
    UserProfile updatePersonality(String profileId, PersonalityConfig personality);
    UserProfile updateBehaviorPreferences(String profileId, BehaviorPreferences preferences);
    
    // === 声纹关联 ===
    UserProfile linkVoicePrint(String profileId, String speakerId);
    UserProfile unlinkVoicePrint(String profileId);
    
    // === 身份变更 ===
    UserProfile upgradeToEmployee(String profileId, String employeeId);
    UserProfile downgradeToVisitor(String profileId);
    
    // === 统计更新 ===
    void recordSession(String profileId, long durationMs);
    void recordTaskCompletion(String profileId, String skillId, boolean success);
    void recordKnowledgeAccess(String profileId, String knowledgeId);
    
    // === 人格进化 ===
    PersonalityConfig inferPersonality(String profileId);
    PersonalityConfig evolvePersonality(String profileId, PersonalityEvolution evolution);
}
```

### 9.2 PersonalityService

```java
public interface PersonalityService {
    
    // === 人格获取 ===
    PersonalityConfig getPersonality(String profileId);
    PersonalityConfig getDefaultForDepartment(String departmentId);
    PersonalityConfig getInherited(String profileId);
    
    // === 人格更新 ===
    PersonalityConfig updatePersonality(String profileId, PersonalityUpdateRequest request);
    PersonalityConfig applyEvolution(String profileId, PersonalityEvolution evolution);
    
    // === 人格推断 ===
    PersonalityConfig inferFromBehavior(String profileId);
    PersonalityEvolution suggestEvolution(String profileId);
    
    // === 人格历史 ===
    List<PersonalityEvolution> getEvolutionHistory(String profileId);
}
```

---

## 十、实施计划

### 10.1 阶段划分

| 阶段 | 内容 | 状态 |
|------|------|------|
| Phase 16.1 | 用户画像表设计与创建 | 🔜 待开始 |
| Phase 16.2 | UserProfileService 实现 | 🔜 待开始 |
| Phase 16.3 | 人格配置服务完善 | 🔜 待开始 |
| Phase 16.4 | 知识关联机制实现 | 🔜 待开始 |
| Phase 16.5 | 行为分析与推断 | 🔜 待开始 |
| Phase 16.6 | 前端集成 | 🔜 待开始 |

### 10.2 依赖关系

```
Phase 16.1 (表设计)
    ↓
Phase 16.2 (服务实现) ← 依赖 EnterpriseEmployeeService
    ↓
Phase 16.3 (人格服务) ← 依赖 BrainPersonality
    ↓
Phase 16.4 (知识关联) ← 依赖 KnowledgeService
    ↓
Phase 16.5 (行为分析) ← 依赖 UserBehaviorPattern
    ↓
Phase 16.6 (前端集成) ← 依赖前端 API
```

---

## 十一、相关文档

- [07-unified-employee-model.md](./07-unified-employee-model.md) - 统一员工模型
- [05-knowledge-system.md](./05-knowledge-system.md) - 知识体系
- [06-evolution-system.md](./06-evolution-system.md) - 进化系统
- [08-database-design.md](./08-database-design.md) - 数据库设计
- [SYSTEM_INIT_DESIGN.md](./SYSTEM_INIT_DESIGN.md) - 系统初始化设计
