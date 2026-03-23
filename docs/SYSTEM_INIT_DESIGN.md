# Living Agent 系统初始化与注册功能开发文档

## 一、需求背景

Living Agent 系统需要实现"第一次使用注册"功能，第一位注册的用户自动成为企业董事长，拥有系统最高权限（FULL 访问级别）。

## 二、前台访客访问机制

### 2.1 核心设计理念

企业"前台"作为对外服务的窗口，应该可以直接对接访客（未注册用户）。这种设计符合真实企业的业务场景：

| 访问场景 | 权限要求 | 说明 |
|---------|---------|------|
| 访客访问前台 | 无需登录 | 任何人都可以与前台数字员工交流 |
| 访客访问部门 | 需要登录 + 权限 | 非权限内人员不能访问对应部门 |
| 登录用户访问前台 | 需要登录 | 登录后可访问前台 |
| 登录用户访问部门 | 需要登录 + 权限 | 根据权限访问对应部门 |

### 2.2 访问控制流程

```
┌─────────────────────────────────────────────────────────────────┐
│                      访问控制流程                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  用户访问前端                                                     │
│     ↓                                                            │
│  ┌─────────────────────────────────────────┐                    │
│  │ 是否访问前台（Reception）？               │                    │
│  └─────────────────────────────────────────┘                    │
│     │ YES                        │ NO                           │
│     ↓                            ↓                              │
│  直接允许访问              检查是否已登录                          │
│  （无需登录）                   │                                │
│                         ┌───────┴───────┐                       │
│                         │ 未登录        │ 已登录                 │
│                         ↓               ↓                        │
│                    跳转登录页      检查部门权限                    │
│                                        │                        │
│                                  ┌─────┴─────┐                  │
│                                  │ 有权限    │ 无权限            │
│                                  ↓           ↓                  │
│                              允许访问    返回403错误              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 2.3 数字员工类型与访问权限

| 员工类型 | 访问要求 | 说明 |
|---------|---------|------|
| 前台（Reception） | 公开访问 | 作为企业对外服务窗口，访客可自由交流 |
| 部门员工 | 登录 + 部门权限 | 需要用户拥有对应部门的访问权限 |
| 管理员员工 | 登录 + 管理权限 | 需要管理员级别权限 |

## 三、Living Agent 当前实现分析

### 3.1 已实现的功能

| 模块 | 状态 | 说明 |
|------|------|------|
| UserIdentity.INTERNAL_CHAIRMAN | ✅ 已实现 | 董事长身份类型 |
| AccessLevel.FULL | ✅ 已实现 | 最高访问级别 |
| FounderService | ✅ 已实现 | 创始人检测服务 |
| SystemController | ✅ 已实现 | 注册 API |
| SystemInitInterceptor | ✅ 已实现 | 系统初始化拦截器 |
| 前端注册页面 | ✅ 已实现 | RegisterView.vue |
| 前端配置页面 | ✅ 已实现 | SystemSettingsView.vue |

### 3.2 存在的问题

1. **前台访问限制** - 前台数字员工可能被权限系统拦截，访客无法访问
2. **拦截器未生效** - 系统初始化拦截器可能被其他安全配置覆盖
3. **数据持久化缺失** - FounderService 使用内存存储，重启后丢失
4. **注册流程不完整** - 缺少验证码验证

## 四、最终设计方案

### 4.1 注册流程设计

```
┌─────────────────────────────────────────────────────────────────┐
│                      系统初始化流程                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. 用户首次访问系统                                              │
│     ↓                                                            │
│  2. 后端拦截器检查系统状态                                        │
│     - 调用 FounderService.hasFounder()                          │
│     - 检查数据库中是否有 is_founder = true 的用户                │
│     ↓                                                            │
│  3. 如果没有创始人                                                │
│     - API 请求返回 503 + {"error": "system_not_initialized"}    │
│     - 页面请求重定向到 /auth/register                            │
│     ↓                                                            │
│  4. 用户填写注册表单                                              │
│     - 姓名（必填）                                                │
│     - 邮箱（必填）                                                │
│     - 企业名称（必填）                                            │
│     ↓                                                            │
│  5. POST /api/system/register                                   │
│     - 创建用户记录                                                │
│     - 设置 identity = INTERNAL_CHAIRMAN                         │
│     - 设置 accessLevel = FULL                                   │
│     - 设置 isFounder = true                                     │
│     - 设置 position = "董事长"                                   │
│     ↓                                                            │
│  6. 注册成功后跳转到系统配置页面                                   │
│     - 配置企业信息                                                │
│     - 配置模型提供商 API Key                                      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 前台访客访问设计

```
┌─────────────────────────────────────────────────────────────────┐
│                      前台访客访问流程                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. 访客打开系统首页                                              │
│     ↓                                                            │
│  2. 前端检测系统状态                                              │
│     - GET /api/system/status                                    │
│     ↓                                                            │
│  3. 系统已初始化？                                                │
│     │ YES                        │ NO                           │
│     ↓                            ↓                              │
│  显示前台对话界面           跳转到注册页面                         │
│     │                                                           │
│     ↓                                                           │
│  4. 访客与前台数字员工交流                                        │
│     - POST /api/chat/reception                                  │
│     - 无需认证                                                   │
│     ↓                                                            │
│  5. 访客尝试访问其他部门？                                        │
│     │ YES                        │ NO                           │
│     ↓                            ↓                              │
│  提示需要登录              继续前台对话                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 数据库设计

#### 4.3.1 员工表新增字段（已完成）

```sql
ALTER TABLE enterprise_employees ADD COLUMN is_founder BOOLEAN DEFAULT FALSE;
CREATE INDEX idx_employees_founder ON enterprise_employees(is_founder);
```

#### 4.3.2 数字员工表访问控制字段

```sql
ALTER TABLE digital_employees ADD COLUMN access_type VARCHAR(32) DEFAULT 'department';
ALTER TABLE digital_employees ADD COLUMN is_public BOOLEAN DEFAULT FALSE;

-- 访问类型说明：
-- 'public' - 公开访问，无需登录（前台）
-- 'authenticated' - 需要登录，无部门限制
-- 'department' - 需要登录 + 部门权限（默认）
```

#### 4.3.3 系统配置表（新增）

```sql
CREATE TABLE IF NOT EXISTS system_config (
    config_key VARCHAR(64) PRIMARY KEY,
    config_value TEXT,
    config_type VARCHAR(32) DEFAULT 'string',
    description VARCHAR(256),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_config (config_key, config_value, config_type, description) VALUES
('system.initialized', 'false', 'boolean', '系统是否已初始化'),
('company.name', '', 'string', '企业名称'),
('company.logo', '', 'string', '企业Logo URL'),
('model.default', 'qwen_local', 'string', '默认模型'),
('registration.open', 'false', 'boolean', '是否开放注册');
```

#### 4.3.4 模型提供商配置表（新增）

```sql
CREATE TABLE IF NOT EXISTS model_providers (
    provider_id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    api_key VARCHAR(256),
    api_secret VARCHAR(256),
    base_url VARCHAR(256),
    options JSONB,
    enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO model_providers (provider_id, name, base_url, enabled) VALUES
('openai', 'OpenAI', 'https://api.openai.com/v1', FALSE),
('anthropic', 'Anthropic (Claude)', 'https://api.anthropic.com', FALSE),
('deepseek', 'DeepSeek', 'https://api.deepseek.com', FALSE),
('qwen_local', 'Qwen Local (Ollama)', 'http://localhost:11434/v1', TRUE);
```

### 4.4 后端 API 设计

| 接口 | 方法 | 权限 | 说明 |
|------|------|------|------|
| `/api/system/status` | GET | 公开 | 获取系统状态 |
| `/api/system/register` | POST | 公开 | 注册创始人 |
| `/api/chat/reception` | POST | 公开 | 与前台对话（访客可用） |
| `/api/chat/department/{id}` | POST | 登录+部门权限 | 与部门员工对话 |
| `/api/system/config` | GET | 董事长 | 获取系统配置 |
| `/api/system/config` | PUT | 董事长 | 更新系统配置 |
| `/api/system/providers` | GET | 董事长 | 获取模型提供商列表 |
| `/api/system/providers/{id}` | PUT | 董事长 | 更新模型提供商配置 |

### 4.5 前端页面设计

#### 4.5.1 首页设计

**访客模式：**
- 显示前台对话界面
- 可与前台数字员工交流
- 显示"登录"按钮，引导用户登录获取更多权限

**登录用户模式：**
- 显示前台对话界面
- 显示可访问的部门列表
- 可与权限内的数字员工交流

#### 4.5.2 注册页面 (`/auth/register`)

**表单字段：**
- 姓名（必填，2-20字符）
- 邮箱（必填，邮箱格式验证）
- 企业名称（必填，2-50字符）

**交互流程：**
1. 页面加载时检查系统状态
2. 如果已有创始人，跳转到登录页
3. 提交注册表单
4. 注册成功后跳转到系统配置页

#### 4.5.3 系统配置页面 (`/settings/system`)

**Tab 页签：**
1. 企业信息 - 企业名称、Logo
2. 模型配置 - 各提供商 API Key 配置
3. 默认模型 - 选择默认使用的模型

**权限控制：**
- 仅董事长（`isChairman = true`）可访问

### 4.6 安全设计

#### 4.6.1 白名单路径

以下路径无需检查系统初始化状态：
- `/api/system/status`
- `/api/system/register`
- `/api/chat/reception` - 前台对话接口（公开）
- `/auth/register`
- `/auth/callback/**`
- `/login`
- `/error`
- 静态资源

#### 4.6.2 注册限制

- 系统已有创始人后，注册接口返回 400 错误
- 后续用户只能通过 OAuth 登录（钉钉/飞书/企业微信）

#### 4.6.3 前台访问安全

- 前台对话有频率限制（防止滥用）
- 访客对话记录不持久化（或有限期保存）
- 敏感操作需要登录验证

### 4.7 初始化任务

系统初始化时执行以下任务：

```java
public class SystemInitTask {
    public void initialize(String founderId) {
        initCompany();
        initDepartments();
        initDigitalEmployees();
        initReceptionEmployee();
        initKnowledgeBases();
        initSystemConfig();
        markInitialized();
    }
    
    private void initReceptionEmployee() {
        DigitalEmployee reception = new DigitalEmployee();
        reception.setName("前台小助手");
        reception.setAccessType("public");
        reception.setIsPublic(true);
        reception.setDescription("企业前台接待，欢迎访客咨询");
        digitalEmployeeRepository.save(reception);
    }
}
```

## 五、实施计划

### 阶段一：修复现有问题（优先级：高）

| 任务 | 说明 | 状态 |
|------|------|------|
| 修复前台访问限制 | 前台数字员工允许公开访问 | 🔄 进行中 |
| 修复拦截器问题 | 确保 SystemInitInterceptor 生效 | 🔄 进行中 |
| 数据持久化 | FounderService 连接数据库 | ⏳ 待开始 |

### 阶段二：完善注册功能（优先级：高）

| 任务 | 说明 | 状态 |
|------|------|------|
| 创建数据库表 | system_config, model_providers | ⏳ 待开始 |
| 实现数据持久化 | SystemConfigService 连接数据库 | ⏳ 待开始 |
| 前端表单验证 | 增强表单验证逻辑 | ⏳ 待开始 |

### 阶段三：完善前台访客功能（优先级：高）

| 任务 | 说明 | 状态 |
|------|------|------|
| 前台公开访问API | /api/chat/reception 无需认证 | ⏳ 待开始 |
| 访客对话界面 | 首页显示前台对话 | ⏳ 待开始 |
| 访客引导登录 | 提示访客登录获取更多权限 | ⏳ 待开始 |

### 阶段四：完善配置功能（优先级：中）

| 任务 | 说明 | 状态 |
|------|------|------|
| 模型提供商配置 | API Key 加密存储 | ⏳ 待开始 |
| 企业信息配置 | Logo 上传功能 | ⏳ 待开始 |
| 权限验证 | 董事长权限验证 | ⏳ 待开始 |

## 六、风险与注意事项

1. **数据安全**：API Key 需要加密存储
2. **并发问题**：多人同时注册需要加锁
3. **数据迁移**：现有系统需要考虑数据迁移
4. **回滚机制**：初始化失败需要回滚
5. **前台滥用**：访客访问需要频率限制

## 七、测试用例

### 7.1 功能测试

| 用例 | 预期结果 |
|------|---------|
| 访客访问首页 | 显示前台对话界面 |
| 访客与前台对话 | 成功交流，无需登录 |
| 访客访问部门 | 提示需要登录 |
| 首次访问系统 | 跳转到注册页面 |
| 注册创始人 | 成功创建董事长账户 |
| 再次访问注册页 | 跳转到登录页面 |
| 注册后登录 | 正常登录 |
| 登录用户访问部门 | 根据权限显示可访问部门 |
| 访问系统配置 | 董事长可访问，其他用户不可访问 |

### 7.2 安全测试

| 用例 | 预期结果 |
|------|---------|
| 重复注册 | 返回 400 错误 |
| 未登录访问部门 | 返回 401 错误 |
| 非权限用户访问部门 | 返回 403 错误 |
| 前台频率限制 | 超过限制返回 429 错误 |

---

**文档版本**: v2.0  
**创建日期**: 2026-03-18  
**更新日期**: 2026-03-20  
**作者**: AI Assistant
