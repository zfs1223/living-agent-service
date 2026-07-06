# Living Agent Service API 接口文档

> 基于 `living-agent-gateway` 模块的 Controller 整理
> 更新时间: 2026-05-27

## 0. 接口分区总览

> 为便于联调与维护，本文档将接口分为三类：
>
> - **稳定**：当前已与代码实现一致，可直接作为主文档使用
> - **待联调**：接口已存在，但仍建议继续确认前后端是否完全切换或闭环
> - **legacy 兼容**：保留的旧接口，主要用于兼容旧前端、旧联调或历史调用方式

### 0.1 稳定分区

- 认证模块
- 系统模块
- 健康检查模块（HealthController）
- 租户模块
- 智能体模块
- Agent 子资源模块
- 部门模块（含 `DepartmentController` 与 `DepartmentApiController`）
- 董事长模块
- 员工模块
- 固定员工模块
- 组织模块
- 技能模块
- 神经元模块
- 企业模块
- 审批模块
- 任务模块
- 项目模块
- 广场模块
- 消息模块
- 知识库模块
- 干预模块
- 主动服务模块
- 接待模块
- 办公模块
- 绩效考核模块
- 积分系统模块
- 监控模块
- 备份恢复模块
- 仪表盘模块
- Windows 自动化模块
- `MiscController`

### 0.2 待联调分区

- 进化模块（`EvolutionAdminController`）
- 模型池与大脑模型分配（`ModelPoolController`）
- 大脑模型配置（`BrainModelController` / `BrainModelConfigController`）
- 企业治理部分接口仍需和前端调用路径最终对齐（见下文）

### 0.3 legacy 兼容分区

- `POST /api/brain-models/switch`
- 旧版脑模型切换/查询参数写法
- 旧版 `brainId` 前端拼接值的兼容路径（如 `brain_${brainKey}` 相关历史调用）

## 统一响应格式

所有API响应使用统一的 `ApiResponse<T>` 格式：

```json
{
  "success": true,
  "data": T,
  "error": null,
  "errorDescription": null
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 请求是否成功 |
| `data` | T | 响应数据（成功时）|
| `error` | string | 错误代码（失败时）|
| `errorDescription` | string | 错误描述（失败时）|

### 统一 ApiResponse 类

当前代码中存在统一的 `com.livingagent.gateway.controller.common.ApiResponse`，主要控制器逐步统一到该格式。

```java
// 文件: living-agent-gateway/src/main/java/com/livingagent/gateway/controller/common/ApiResponse.java

public record ApiResponse<T>(
    boolean success,
    T data,
    String error,
    String errorDescription
) {
    public static <T> ApiResponse<T> ok(T data) { ... }
    public static <T> ApiResponse<T> ok() { ... }
    public static <T> ApiResponse<T> err(String error, String description) { ... }
    public static <T> ApiResponse<T> err(String error, String description, T data) { ... }
}
```

**注意**：部分 Controller 仍使用内部定义的 `ApiResponse` record（结构与统一类相同），正在逐步迁移到 `common.ApiResponse`。

---

## 安全与权限

### @RequireAccess 权限检查注解

所有需要权限控制的 API 方法使用 `@RequireAccess` 注解进行声明式权限检查，替代 Controller 中零散的 `accessGateService.canRoute()` 调用。

```java
// 文件: living-agent-gateway/src/main/java/com/livingagent/gateway/security/RequireAccess.java

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAccess {
    /** 资源类型：brain, model, tool */
    String resource();
    /** 资源名称/动作 */
    String action();
    /** 是否要求FULL权限（默认false） */
    boolean requireFull() default false;
}
```

**使用示例**：
```java
@RequireAccess(resource = "brain", action = "MainBrain")
public ResponseEntity<?> someMethod(@RequestHeader("X-Employee-Id") String employeeId) { ... }

@RequireAccess(resource = "model", action = "pool", requireFull = true)
public ResponseEntity<?> adminMethod(@RequestHeader("X-Employee-Id") String employeeId) { ... }
```

**权限检查流程**：
1. AOP 切面 `RequireAccessAspect` 拦截所有 `@RequireAccess` 注解方法
2. 从请求头 `X-Employee-Id` 获取员工ID
3. 若 `requireFull=true`，先检查是否具有 FULL 权限（董事长级别）
4. 调用 `AccessGateService.canRoute()` 进行通用权限判断
5. 越权时统一返回 `ApiResponse.err("forbidden", "...")` 格式

### API 权限分级

| 权限级别 | level值 | 可访问范围 | 适用身份 |
|----------|---------|-----------|---------|
| CHAT_ONLY | 0 | 仅闲聊，禁止企业资源 | 离职员工、外来访客 |
| LIMITED | 1 | AdminBrain + CsBrain，禁止敏感知识 | 试用期员工、客户、合作伙伴 |
| DEPARTMENT | 2 | 本部门完整功能 + ToolNeuron | 在职员工 |
| FULL | 3 | 所有大脑和工具 + MainBrain | 董事长 |

### 管理类 API（需 FULL 权限）

以下 API 路径要求 FULL 权限（董事长级别），通过 `@RequireAccess(requireFull = true)` 或 `AccessGateService.hasFullAccess()` 检查：

| API 路径 | 说明 |
|----------|------|
| `/api/model-pool/**` | 模型池管理（供应商/模型/分配） |
| `/api/brain-models/**` | 大脑模型配置与切换 |
| `/api/windows-automation/**` | Windows 自动化节点管理 |
| `/api/v1/proxy/**` | Claude CLI 代理 |
| `/api/evolution/**` | 进化系统管理（反馈/调整/回滚） |

### 部门 API（需认证 + 部门权限）

以下 API 路径要求用户已认证且具有对应部门权限（DEPARTMENT 级别或 FULL 级别）：

| API 路径 | 说明 |
|----------|------|
| `/api/tech/**` | 技术部（department=='tech' \|\| FULL） |
| `/api/hr/**` | 人力资源 |
| `/api/finance/**` | 财务部 |
| `/api/sales/**` | 销售部 |
| `/api/admin/**` | 行政部 |
| `/api/cs/**` | 客服部 |
| `/api/legal/**` | 法务部 |
| `/api/ops/**` | 运营部 |

### 公开 API（无需登录）

| API 路径 | 说明 |
|----------|------|
| `/api/public/**` | 公开对话、企业信息、前台接待 |
| `/api/auth/**` | 认证相关（登录/注册/OAuth） |
| `/api/system/register` | 创始人注册 |

### 统一越权响应

所有权限检查失败统一返回：

```json
{
  "success": false,
  "data": null,
  "error": "forbidden",
  "errorDescription": "Access denied before routing"
}
```

### 安全模块类名变更记录

> ⚠️ 以下安全模块类名已变更：

| 旧类名 | 新类名 | 说明 |
|--------|--------|------|
| `security.Employee` | `SecurityIdentity` | 安全上下文员工信息，避免与 `employee.Employee` 混淆 |
| `security.EmployeeService` | `AuthEmployeeService` | 安全员工服务，认证/声纹/OAuth 查找 |
| `security.EmployeeServiceImpl` | `AuthEmployeeServiceImpl` | 安全员工服务实现，内存 Map 存储（认证用） |

---

## 1. 稳定：认证模块

### PhoneAuthController (`/api/auth`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/sms/send` | 发送短信验证码 |
| POST | `/api/auth/phone/login` | 手机号登录 |
| POST | `/api/auth/phone/bind` | 绑定手机号 |

### AuthController (`/api/auth`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/auth/oauth/{provider}/url` | 获取OAuth URL |
| POST | `/api/auth/oauth/{provider}/callback` | OAuth回调 |
| GET | `/api/auth/user` | 获取当前用户 |
| GET | `/api/auth/me` | 获取当前用户（别名） |
| PATCH | `/api/auth/me` | 更新当前用户信息 |
| POST | `/api/auth/refresh` | 刷新令牌 |
| POST | `/api/auth/logout` | 登出 |
| GET | `/api/auth/providers` | 获取OAuth提供商列表 |

### VoicePrintController (`/api/auth/voiceprint`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/auth/voiceprint` | 获取声纹列表 |
| POST | `/api/auth/voiceprint/register` | 注册声纹 |
| POST | `/api/auth/voiceprint/login` | 声纹登录 |
| POST | `/api/auth/voiceprint/verify` | 验证声纹 |
| GET | `/api/auth/voiceprint/status` | 获取声纹服务状态 |

---

## 2. 稳定：系统模块

### SystemController (`/api/system`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/system/status` | 获取系统状态 |
| POST | `/api/system/register` | 注册创始人 |
| GET | `/api/system/config` | 获取系统配置 |
| PUT | `/api/system/config` | 更新系统配置 |
| GET | `/api/system/config/providers` | 获取提供商配置 |
| PUT | `/api/system/config/providers/{providerId}` | 更新提供商配置 |
| GET | `/api/system/health` | 获取系统健康状态 |
| GET | `/api/system/health/detail` | 获取系统健康详情 |

**说明**：`/api/system/register` 会创建董事长身份并可联动创建租户/公司初始化配置。

### HealthController (`/api/health`)

> **P12-B 专用健康检查端点**，用于 Kubernetes 容器探针和桌面端状态检查。无需登录，轻量级响应。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 基础健康状态（status, timestamp, version, uptime） |
| GET | `/api/health/live` | Liveness 探针（返回 `{status: "alive"}`） |
| GET | `/api/health/ready` | Readiness 探针（检查 DB + model_daemon 连通性） |

**响应示例（GET /api/health）**：
```json
{
  "success": true,
  "data": {
    "status": "UP",
    "timestamp": "2026-07-06T01:42:10Z",
    "version": "1.0.0",
    "uptime": "2h30m"
  }
}
```

**响应示例（GET /api/health/ready）**：
```json
{
  "success": true,
  "data": {
    "status": "ready",
    "checks": {
      "database": "ok",
      "model_daemon": "ok"
    }
  }
}
```

**说明**：
- `/api/health/live` 仅检查 JVM 存活，适合 K8s livenessProbe
- `/api/health/ready` 检查关键依赖可用性，适合 K8s readinessProbe
- 与 `SystemController` `/api/system/health` 不同，后者返回完整健康详情并需权限检查

### SystemSettingsController (`/api/enterprise/settings`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/enterprise/settings` | 获取所有设置 |
| GET | `/api/enterprise/settings/{category}` | 获取分类设置 |
| GET | `/api/enterprise/settings/{category}/{key}` | 获取具体设置 |
| PUT | `/api/enterprise/settings/{category}/{key}` | 更新设置 |
| POST | `/api/enterprise/settings/batch` | 批量更新 |
| PUT | `/api/enterprise/settings` | 批量更新（别名） |
| GET | `/api/enterprise/settings/history` | 获取变更历史 |
| POST | `/api/enterprise/settings/{category}/{key}/reset` | 重置设置 |
| GET | `/api/enterprise/settings/categories` | 获取分类列表 |
| GET | `/api/enterprise/settings/versions` | 获取版本历史 |
| POST | `/api/enterprise/settings/versions/{versionId}/rollback` | 回滚到指定版本 |
| GET | `/api/enterprise/settings/workspace/config` | 获取工作区配置（FileEditTool/BuildTool） |
| PUT | `/api/enterprise/settings/workspace/config` | 热更新工作区配置 |

---

## 3. 租户模块

### TenantController (`/api/tenants`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tenants/registration-config` | 获取注册配置 |
| POST | `/api/tenants/self-create` | 自建租户 |
| POST | `/api/tenants/join` | 加入租户 |
| GET | `/api/tenants/resolve-by-domain` | 通过域名解析租户 |
| GET | `/api/tenants/{tenantId}` | 获取租户详情 |
| PUT | `/api/tenants/{tenantId}` | 更新租户 |

### Admin 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tenants/admin/companies` | 获取公司列表（管理员） |
| POST | `/api/tenants/admin/companies/{id}/toggle` | 切换公司状态 |
| GET | `/api/tenants/admin/platform-settings` | 获取平台设置 |

---

## 4. 智能体模块

### AgentApiController (`/api/agents`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/agents` | 创建智能体（数字员工） |
| GET | `/api/agents` | 列出所有智能体 |
| GET | `/api/agents?id={id}` | 通过查询参数获取智能体详情 |
| GET | `/api/agents/{agentId}` | 获取智能体详情 |
| PATCH | `/api/agents/{agentId}` | 更新智能体 |
| POST | `/api/agents/{agentId}/start` | 启动智能体 |
| POST | `/api/agents/{agentId}/stop` | 停止智能体 |
| GET | `/api/agents/{agentId}/status` | 获取智能体状态 |
| POST | `/api/agents/{agentId}/action` | 触发动作 |
| GET | `/api/agents/{agentId}/skills` | 获取技能列表 |
| POST | `/api/agents/{agentId}/skills/{skillName}` | 绑定技能 |
| DELETE | `/api/agents/{agentId}/skills/{skillName}` | 解绑技能 |
| GET | `/api/agents/{agentId}/metrics` | 获取指标 |
| GET | `/api/agents/{agentId}/activity` | 获取活动记录 |
| GET | `/api/agents/{agentId}/sessions` | 获取会话列表 |
| POST | `/api/agents/{agentId}/sessions` | 创建会话 |
| GET | `/api/agents/{agentId}/collaborators` | 获取协作者 |
| GET | `/api/agents/templates` | 获取模板列表 |
| POST | `/api/agents/{agentId}/api-key` | 生成API密钥 |
| GET | `/api/agents/{agentId}/config` | 获取配置 |
| PUT | `/api/agents/{agentId}/config` | 更新配置 |
| GET | `/api/agents/config?id={id}` | 通过查询参数获取配置 |
| PUT | `/api/agents/config?id={id}` | 通过查询参数更新配置 |

**注意**: 员工ID格式为 `employee://digital/技术部/CI-CD流水线/023`，包含 `/` 字符。
推荐使用查询参数方式：`GET /api/agents?id={encoded_id}`

#### POST /api/agents 创建智能体

**请求体**:
```json
{
  "name": "智能体名称",
  "title": "职位/角色",
  "role_description": "角色描述",
  "icon": "头像URL",
  "department": "部门名称",
  "department_id": "部门ID",
  "agent_type": "native|openclaw",
  "personality": {
    "traits": ["专业", "高效"],
    "communication_style": "professional",
    "response_tone": "friendly"
  },
  "boundaries": {},
  "primary_model_id": "主模型ID",
  "fallback_model_id": "备用模型ID",
  "template_id": "模板ID",
  "permission_scope_type": "user|company",
  "max_tokens_per_day": 100000,
  "max_tokens_per_month": 3000000,
  "skill_ids": ["skill1", "skill2"],
  "capabilities": ["chat", "task"],
  "permission_access_level": "use|admin",
  "tenant_id": "租户ID",
  "suggested_id": "建议的员工ID"
}
```

**响应**: 返回创建的智能体详情 `AgentDetail`

### AgentController (`/api`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/status` | 获取系统状态 |
| POST | `/api/session/{sessionId}/start` | 启动会话 |
| POST | `/api/session/{sessionId}/end` | 结束会话 |
| GET | `/api/session/{sessionId}/status` | 获取会话状态 |

**说明**：`/api/health` 已移至 `HealthController`（P12-B 专用健康检查端点）。

---

## 5. Agent 子资源模块

### AgentTaskController (`/api/agents/{agentId}/tasks`) — 已废弃

> ⚠️ **已标记 `@Deprecated`**：所有任务操作统一使用 `TaskController` 的 `/tasks` 路由。
> 
> 该 Controller 保留仅为兼容旧前端调用，新开发请直接使用 `/api/tasks` 系列接口。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agents/{agentId}/tasks` | 获取任务列表（兼容接口） |
| POST | `/api/agents/{agentId}/tasks` | 创建任务（兼容接口） |
| GET | `/api/agents/{agentId}/tasks/{taskId}` | 获取任务详情（兼容接口） |
| PATCH | `/api/agents/{agentId}/tasks/{taskId}` | 更新任务（兼容接口） |
| GET | `/api/agents/{agentId}/tasks/{taskId}/logs` | 获取任务日志（兼容接口） |
| POST | `/api/agents/{agentId}/tasks/{taskId}/trigger` | 触发任务（兼容接口） |

### AgentScheduleController (`/api/agents/{agentId}/schedules`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agents/{agentId}/schedules` | 获取定时任务列表 |
| POST | `/api/agents/{agentId}/schedules` | 创建定时任务 |
| PATCH | `/api/agents/{agentId}/schedules/{scheduleId}` | 更新定时任务 |
| DELETE | `/api/agents/{agentId}/schedules/{scheduleId}` | 删除定时任务 |
| POST | `/api/agents/{agentId}/schedules/{scheduleId}/run` | 手动运行 |
| GET | `/api/agents/{agentId}/schedules/{scheduleId}/history` | 获取历史 |

### AgentTriggerController (`/api/agents/{agentId}/triggers`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agents/{agentId}/triggers` | 获取触发器列表 |
| PATCH | `/api/agents/{agentId}/triggers/{triggerId}` | 更新触发器 |
| DELETE | `/api/agents/{agentId}/triggers/{triggerId}` | 删除触发器 |

### AgentChannelController (`/api/agents/{agentId}/channel`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agents/{agentId}/channel` | 获取频道配置 |
| POST | `/api/agents/{agentId}/channel` | 创建频道 |
| PUT | `/api/agents/{agentId}/channel` | 更新频道 |
| DELETE | `/api/agents/{agentId}/channel` | 删除频道 |
| GET | `/api/agents/{agentId}/channel/webhook-url` | 获取Webhook URL |

### AgentFileController (`/api/agents/{agentId}/files`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agents/{agentId}/files` | 列出文件 |
| GET | `/api/agents/{agentId}/files/content` | 读取文件 |
| PUT | `/api/agents/{agentId}/files/content` | 写入文件 |
| DELETE | `/api/agents/{agentId}/files/content` | 删除文件 |
| POST | `/api/agents/{agentId}/files/upload` | 上传文件 |
| GET | `/api/agents/{agentId}/files/download` | 下载文件 |

---

## 6. 稳定：部门模块

### DepartmentController (`/api/departments`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/departments` | 列出所有部门 |
| GET | `/api/departments/code/{code}` | 通过代码获取部门 |
| GET | `/api/departments/{id}/brain` | 获取部门大脑 |
| GET | `/api/departments/{id}/agents` | 获取部门数字员工 |
| GET | `/api/departments/{id}/members` | 获取部门成员 |

**说明**：该控制器对部门数据做静态映射与员工列表筛选，访问前要求 `AccessGateService` 路由校验通过。
### DepartmentApiController (`/api/dept`)

**状态**：已稳定（REST 部门入口已接入真实对话链路，成员/brain/我的部门接口已接入组织查询层）

**部门文本对话路由约定**：`POST /api/dept/{department}/chat` 是部门大脑入口。普通咨询类文本消息会路由到对应部门大脑（如 `tech` → `TechBrain`、`hr` → `HrBrain`、`finance` → `FinanceBrain`）。执行类任务会先进入 `ConversationOrchestrator`，再经过主脑规划、部门路由、固定员工分派、任务单准备、部门执行协调、员工任务通道派发、回执收集，以及 artifact / knowledge / performance 沉淀链路。该文本链路不经过 `AgentService` 的通用智能体会话，不进入 `Qwen3Neuron / chat` 闲聊神经元链路，也不触发 ASR/TTS；ASR/TTS 仅用于语音入口。

**执行类任务行为说明**：当前公开 API 路径和请求/响应结构保持不变，但接口内部语义已从“部门脑直接回复”升级为“任务编排 + 员工执行推进 + 结果沉淀”。后续执行类任务的最终用户回复应回到主大脑统一总结收口；咨询类消息仍可由部门大脑直接答复。

内部 Trace 阶段包括但不限于：`intake_classified`、`main_brain_planned`、`brain_routed`、`department_plan_created`、`employee_assignment_planned`、`assignment_batch_prepared`、`employee_assigned`、`employee_execution_started`、`employee_execution_completed`、`department_execution_completed`、`artifact_recorded`、`knowledge_recorded`、`performance_recorded`、`result_aggregated`。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/dept/{department}/chat` | 部门聊天（文本直连部门大脑） |
| GET | `/api/dept/{department}/info` | 获取部门信息 |
| GET | `/api/dept/{department}/members` | 获取部门成员 |
| GET | `/api/dept/{department}/brains` | 获取部门大脑列表 |
| GET | `/api/dept/my` | 获取我的部门 |
| GET | `/api/dept/{department}/conversations` | 列出当前用户在指定部门的活跃对话（需 Authorization） |
| GET | `/api/dept/conversations/{conversationId}/history` | 获取指定对话的消息历史（需 Authorization，参数 limit 默认 100） |
| DELETE | `/api/dept/conversations/{conversationId}` | 软删除指定对话（需 Authorization，仅 owner 可删除） |

部门代码映射：
- `tech` → 技术部
- `hr` → 人力资源
- `finance` → 财务部
- `sales` → 销售部
- `admin` → 行政部
- `cs` → 客服部
- `legal` → 法务部
- `ops` → 运营部
- `core` → 核心层
- `cross_dept` → 跨部门协调

---

## 7. 稳定：企业治理模块

### EnterpriseApiController (`/api/enterprise`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/enterprise/dashboard` | 获取董事长仪表盘 |
| GET | `/api/enterprise/employees` | 获取所有员工 |
| GET | `/api/enterprise/employees/{employeeId}` | 获取员工详情 |
| POST | `/api/enterprise/employees/{employeeId}/access-level` | 更新员工权限 |
| GET | `/api/enterprise/departments` | 获取所有部门 |
| GET | `/api/enterprise/system/status` | 获取系统状态 |
| GET | `/api/enterprise/identity-providers` | 获取身份提供商列表 |
| POST | `/api/enterprise/identity-providers` | 创建身份提供商 |
| POST | `/api/enterprise/identity-providers/oauth2` | 创建 OAuth2 身份提供商 |
| PUT | `/api/enterprise/identity-providers/{id}` | 更新身份提供商 |
| PATCH | `/api/enterprise/identity-providers/{id}/oauth2` | 更新 OAuth2 身份提供商 |
| DELETE | `/api/enterprise/identity-providers/{id}` | 删除身份提供商 |
| GET | `/api/enterprise/tenant-quotas` | 获取租户配额 |
| PATCH | `/api/enterprise/tenant-quotas` | 更新租户配额 |
| GET | `/api/enterprise/invitation-codes` | 获取邀请码列表 |
| POST | `/api/enterprise/invitation-codes` | 创建邀请码 |
| DELETE | `/api/enterprise/invitation-codes/{id}` | 删除邀请码 |
| GET | `/api/enterprise/invitation-codes/export` | 导出邀请码 CSV |

**说明**：该控制器要求董事长/创始人身份，部分接口还会额外走 `AccessGateService` 前置路由校验。企业治理相关能力已从“待实现”收敛为当前实际实现，但其中部分数据仍以内存配置形式承载，后续如需持久化可再单独演进。
---

## 8. 稳定：员工模块

### EmployeeController (`/api/employees`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/employees/summary` | 获取员工汇总 |
| GET | `/api/employees/origin/{origin}` | 按来源查询员工 |
| POST | `/api/employees/refresh-idle` | 刷新空闲员工 |
| POST | `/api/employees/import` | 导入员工花名册（CSV / Excel） |
| POST | `/api/employees/import/preview` | 预览员工花名册导入结果 |

**说明**：当前控制器聚焦于员工汇总、来源筛选、空闲刷新与花名册导入入口，不提供通用 CRUD。花名册导入核心能力由 `EmployeeImporter` 提供，Controller 仅负责上传、预览与结果包装。

---

## 9. 稳定：固定员工模块

### FixedEmployeeController (`/api/fixed-employees`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/fixed-employees/summary` | 获取固定员工汇总 |
| GET | `/api/fixed-employees/definitions` | 获取所有固定员工定义 |
| GET | `/api/fixed-employees/definitions/{code}` | 获取指定定义 |
| GET | `/api/fixed-employees/definitions/by-department/{department}` | 按部门获取定义 |
| GET | `/api/fixed-employees/grouped` | 按部门分组获取定义 |
| GET | `/api/fixed-employees/profiles` | 获取所有固定员工长期画像 |
| GET | `/api/fixed-employees/profiles/{code}` | 获取指定固定员工长期画像 |
| GET | `/api/fixed-employees/personas` | 获取所有固定员工外观/个性化配置 |
| GET | `/api/fixed-employees/personas/{code}` | 获取指定固定员工外观/个性化配置 |

---

## 10. 稳定：组织模块

### OrgController (`/api/org`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/org/users` | 获取用户列表 |
| GET | `/api/org/departments` | 获取部门列表 |

**说明**：该控制器按当前代码仅提供组织层用户与部门的静态聚合查询，返回同样使用统一响应包装。
---

## 11. 稳定：技能模块

### SkillsController (`/api/skills`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/skills` | 列出所有技能 |
| GET | `/api/skills/{id}` | 获取技能详情 |
| POST | `/api/skills` | 创建技能 |
| PUT | `/api/skills/{id}` | 更新技能 |
| DELETE | `/api/skills/{id}` | 删除技能 |
| GET | `/api/skills/browse/list` | 浏览技能文件 |
| GET | `/api/skills/browse/read` | 读取技能文件 |
| PUT | `/api/skills/browse/write` | 写入技能文件 |
| DELETE | `/api/skills/browse/delete` | 删除技能文件 |
| GET | `/api/skills/clawhub/search` | 搜索ClawHub |
| GET | `/api/skills/clawhub/detail/{slug}` | 获取ClawHub详情 |
| POST | `/api/skills/clawhub/install` | 安装ClawHub技能 |
| POST | `/api/skills/import-from-url` | 从URL导入 |
| POST | `/api/skills/import-from-url/preview` | 预览URL导入 |
| GET | `/api/skills/settings/token` | 获取令牌设置 |
| PUT | `/api/skills/settings/token` | 更新令牌设置 |

---

## 12. 稳定：神经元模块

### NeuronController (`/api/neurons`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/neurons` | 列出所有神经元 |
| GET | `/api/neurons/{id}` | 获取神经元详情 |
| GET | `/api/neurons/{id}/status` | 获取神经元状态 |
| GET | `/api/neurons/{id}/metrics` | 获取神经元指标 |

---

## 13. 稳定：企业模块

### EnterpriseController (`/api/enterprise`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/enterprise/llm-models` | 获取LLM模型列表 |
| GET | `/api/enterprise/llm-providers` | 获取LLM提供商列表 |
| GET | `/api/enterprise/skills` | 获取技能列表 |
| GET | `/api/enterprise/skills/by-brain/{brain}` | 按大脑获取技能 |
| GET | `/api/enterprise/tools` | 获取工具列表 |
| GET | `/api/enterprise/tools/by-department/{department}` | 按部门获取工具 |
| GET | `/api/enterprise/skill-counts` | 获取技能统计 |
| POST | `/api/enterprise/llm-models` | 创建LLM模型 |
| PUT | `/api/enterprise/llm-models/{modelId}` | 更新LLM模型 |
| DELETE | `/api/enterprise/llm-models/{modelId}` | 删除LLM模型 |
| POST | `/api/enterprise/llm-test` | 测试LLM模型 |

### 文档端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/enterprise/documents/files` | 获取文档文件列表 |
| POST | `/api/enterprise/documents/upload` | 上传文档 |
| GET | `/api/enterprise/documents/content` | 读取文档内容 |
| PUT | `/api/enterprise/documents/content` | 写入文档内容 |
| DELETE | `/api/enterprise/documents/content` | 删除文档 |

---

## 14. 稳定：审批模块

### ApprovalController (`/api/approvals`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/approvals/pending` | 获取待审批列表 |
| GET | `/api/approvals/my-pending` | 获取我的待审批 |
| GET | `/api/approvals/my` | 获取我的审批 |
| POST | `/api/approvals` | 创建审批 |
| GET | `/api/approvals/{instanceId}` | 获取审批详情 |
| GET | `/api/approvals/{instanceId}/steps` | 获取审批步骤 |
| POST | `/api/approvals/{instanceId}/steps/{stepId}/approve` | 批准审批步骤 |
| POST | `/api/approvals/{instanceId}/steps/{stepId}/reject` | 拒绝审批步骤 |
| POST | `/api/approvals/{instanceId}/approve` | 批准审批 |
| POST | `/api/approvals/{instanceId}/reject` | 拒绝审批 |
| POST | `/api/approvals/{instanceId}/return` | 退回审批 |
| POST | `/api/approvals/{instanceId}/cancel` | 取消审批 |
| GET | `/api/approvals/{instanceId}/history` | 获取审批历史 |
| GET | `/api/approvals/workflows` | 列出工作流 |
| GET | `/api/approvals/workflows/{workflowId}` | 获取工作流详情 |
| POST | `/api/approvals/workflows` | 创建工作流 |

---

## 15. 稳定：任务模块

### TaskController (`/api/tasks`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tasks` | 获取任务列表 |
| POST | `/api/tasks` | 创建任务 |
| GET | `/api/tasks/{taskId}` | 获取任务详情 |
| POST | `/api/tasks/{taskId}/checkout` | 检出任务 |
| POST | `/api/tasks/{taskId}/complete` | 完成任务 |
| POST | `/api/tasks/{taskId}/release` | 释放任务 |
| POST | `/api/tasks/{taskId}/reassign` | 重新分配任务 |
| GET | `/api/tasks/statistics` | 获取统计 |
| GET | `/api/tasks/pending` | 获取待处理任务 |
| GET | `/api/tasks/my` | 获取当前用户任务（从 token 提取身份，支持 `status` 查询参数过滤） |
| GET | `/api/tasks/employee/{employeeId}` | 获取员工任务（兼容接口，支持 `status` 查询参数过滤） |
| GET | `/api/tasks/public` | 获取公共任务列表 |
| POST | `/api/tasks/{taskId}/claim` | 接取任务（从 token 提取身份，不再需要请求体传入 employeeId） |
| POST | `/api/tasks/{taskId}/submit` | 提交任务结果（从 token 提取身份，进入 SUBMITTED 状态） |
| POST | `/api/tasks/{taskId}/review` | 审核任务（审核通过进入 COMPLETED，拒绝进入 REJECTED 或 NEEDS_REWORK） |

**任务状态机说明**：
- `submit` 不再直接完成任务，而是进入 `SUBMITTED` 状态
- `review` 审核通过后才进入 `COMPLETED` 状态
- 完整状态流转：`PENDING` → `CLAIMED` → `IN_PROGRESS` → `SUBMITTED` → `PENDING_REVIEW` → `REVIEWED` → `COMPLETED` / `REJECTED` / `NEEDS_REWORK`

**统一身份字段**：
- 任务实体包含 `userId`、`tenantId`、`taskKey`、`executionId`、`departmentCode`、`projectId` 等统一身份字段
- 支持按用户、租户、任务键、执行ID、项目ID 等多维度查询

**说明**：当前 `TaskController` 不包含通用 `PUT/DELETE` 任务接口；任务生命周期通过 checkout / complete / release / reassign / claim / submit / review 完成。

**身份认证说明**：
- `claim`、`submit`、`/tasks/my` 接口优先从 `Authorization: Bearer <token>` 中提取用户身份
- 不再信任请求体中的 `employeeId` 字段，防止身份伪造
- `X-Employee-Id` header 仅作为兼容降级手段，当 token 无效时使用

### AgentTaskController (`/api/agents/{agentId}/tasks`) — 已废弃

> ⚠️ **已标记 `@Deprecated`**：所有任务操作统一使用 `TaskController` 的 `/tasks` 路由。
> 
> 该 Controller 保留仅为兼容旧前端调用，新开发请直接使用 `/api/tasks` 系列接口。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agents/{agentId}/tasks` | 获取任务列表（兼容接口） |
| POST | `/api/agents/{agentId}/tasks` | 创建任务（兼容接口） |
| GET | `/api/agents/{agentId}/tasks/{taskId}` | 获取任务详情（兼容接口） |
| PATCH | `/api/agents/{agentId}/tasks/{taskId}` | 更新任务（兼容接口） |
| GET | `/api/agents/{agentId}/tasks/{taskId}/logs` | 获取任务日志（兼容接口） |
| POST | `/api/agents/{agentId}/tasks/{taskId}/trigger` | 触发任务（兼容接口） |
---

## 16. 稳定：项目模块

### ProjectController (`/api/projects`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/projects` | 获取项目列表 |
| POST | `/api/projects` | 创建项目 |
| GET | `/api/projects/{projectId}` | 获取项目详情 |
| PUT | `/api/projects/{projectId}` | 更新项目 |
| DELETE | `/api/projects/{projectId}` | 删除项目 |
| POST | `/api/projects/{projectId}/start` | 启动项目 |
| POST | `/api/projects/{projectId}/complete` | 完成项目 |
| POST | `/api/projects/{projectId}/hold` | 暂停项目 |
| POST | `/api/projects/{projectId}/phases/{phase}/advance` | 推进阶段 |
| GET | `/api/projects/{projectId}/progress` | 获取进度 |
| PUT | `/api/projects/{projectId}/phases/{phase}/progress` | 设置阶段进度 |
| GET | `/api/projects/statistics` | 获取统计 |
| GET | `/api/projects/{projectId}/tasks` | 获取项目任务列表（已接入真实 TaskRepository） |
| POST | `/api/projects/{projectId}/tasks` | 创建项目任务 |
| PUT | `/api/projects/{projectId}/tasks/{taskId}` | 更新项目任务 |
| DELETE | `/api/projects/{projectId}/tasks/{taskId}` | 删除项目任务 |

**统一身份字段**：
- 项目实体包含 `tenantId`、`creatorUserId`、`projectKey`、`sourceTaskKey`、`sourceConversationId`、`dataNamespace`、`managerId`、`ownerDepartment` 等统一身份字段
- 支持按租户、创建者、项目键、所属部门等多维度查询

**项目任务子资源说明**：
- `/projects/{projectId}/tasks` 已接入真实 `TaskRepository`，不再是 stub
- 创建的项目任务会关联 `projectId`，支持按项目查询任务

---

## 17. 稳定：广场模块

### PlazaController (`/api/plaza`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/plaza/posts` | 获取帖子列表 |
| GET | `/api/plaza/stats` | 获取统计信息 |
| POST | `/api/plaza/posts` | 创建帖子 |
| POST | `/api/plaza/posts/{postId}/like` | 点赞 |

---

## 18. 稳定：消息模块

### MessageController (`/api/messages`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/messages/inbox` | 获取收件箱 |
| GET | `/api/messages/unread-count` | 获取未读数 |
| PUT | `/api/messages/{messageId}/read` | 标记已读 |
| PUT | `/api/messages/read-all` | 全部标记已读 |

---

## 19. 稳定：知识库模块

### KnowledgeController (`/api/knowledge`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/knowledge/summary` | 获取知识概览 |
| POST | `/api/knowledge/cleanup` | 清理过期知识 |
| POST | `/api/knowledge/{key}/promote` | 晋升知识 |
| GET | `/api/knowledge/{key}/history` | 获取知识晋升历史 |

**说明**：当前知识控制器聚焦于知识治理、晋升与审计，不提供通用 CRUD。

---

## 20. 稳定：干预模块

### InterventionController (`/api/intervention`, `/api/interventions`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/intervention/evaluate` | 评估操作 |
| GET | `/api/intervention/pending` | 获取待处理决策 |
| POST | `/api/intervention/{decisionId}/respond` | 响应决策 |
| POST | `/api/intervention/{decisionId}/escalate` | 升级决策 |
| GET | `/api/intervention/statistics` | 获取统计 |
| POST | `/api/intervention/rules` | 注册规则 |
| DELETE | `/api/intervention/rules/{ruleId}` | 注销规则 |
| GET | `/api/intervention/rules` | 获取适用规则 |

**注意**: 同时支持 `/api/intervention` 和 `/api/interventions` 路径

---

## 21. 稳定：主动服务模块

### ProactiveController (`/api/proactive`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/proactive/digest` | 获取每日摘要 |
| GET | `/api/proactive/habits` | 获取习惯列表 |
| POST | `/api/proactive/habits` | 创建习惯 |
| PUT | `/api/proactive/habits/{id}` | 更新习惯 |
| DELETE | `/api/proactive/habits/{id}` | 删除习惯 |
| POST | `/api/proactive/habits/{habitId}/checkin` | 习惯打卡 |
| GET | `/api/proactive/notifications` | 获取通知列表 |
| POST | `/api/proactive/notifications/{id}/read` | 标记通知已读 |
| POST | `/api/proactive/notifications/read-all` | 全部标记已读 |
| GET | `/api/proactive/meeting-notes` | 获取会议记录列表 |
| GET | `/api/proactive/meeting-notes/{id}` | 获取会议记录详情 |
| GET | `/api/proactive/analytics` | 获取分析数据 |
| GET | `/api/proactive/suggestions` | 获取建议列表 |
| GET | `/api/proactive/predictions` | 获取预测列表 |

**说明**：当前主动服务模块的日历/习惯/通知/分析接口返回以占位和建议驱动为主，后续可继续接入更完整的持久化数据源。
### ProactiveOrchestratorController (`/api/proactive/orchestrator`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/proactive/orchestrator` | 运行主动编排器 |

**说明**：该接口需要 `userId` 参数，并要求管理员级路由前鉴权通过。
---

## 22. 稳定：接待模块

### ReceptionController (`/api/reception`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/reception/status` | 获取接待状态 |
| POST | `/api/reception/chat` | 聊天 |
| POST | `/api/reception/chat/stream` | 流式聊天 |
| GET | `/api/reception/visitors` | 获取访客列表 |
| POST | `/api/reception/check-in` | 访客登记 |

---

## 23. 稳定：办公模块

### OfficeController (`/api/office`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/office` | 获取办公室列表 |
| POST | `/api/office` | 创建办公室 |
| GET | `/api/office/status` | 获取办公状态 |
| GET | `/api/office/agents` | 获取智能体列表 |
| GET | `/api/office/agents/{id}` | 获取智能体详情 |
| POST | `/api/office/agent/state` | 更新智能体状态 |
| GET | `/api/office/areas` | 获取区域列表 |
| GET | `/api/office/department/{department}` | 获取部门状态快照（页面首屏基线） |
| GET | `/api/office/yesterday-memo` | 获取昨日备忘 |

**实时动态说明**：
- 页面首屏以 `/api/office/department/{department}` 返回的快照为准，重建部门办公室状态。
- 前端不再使用纯前端模拟的“等待接入实时动态”占位逻辑，而是由后端返回的快照驱动。

---

## 24. 待联调：进化模块

### EvolutionAdminController (`/api/evolution`)

**状态**：待联调（核心接口已存在，建议继续验证自动调整/回滚的实际业务闭环）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/evolution/feedback` | 提交进化反馈 |
| GET | `/api/evolution/feedback/recent` | 获取最近反馈 |
| POST | `/api/evolution/trigger-auto-adjust` | 手动触发自动进化调整 |
| POST | `/api/evolution/rollback/{brainId}` | 回滚指定 brain 的模型分配 |
| GET | `/api/evolution/history` | 获取进化历史 |

**说明**：进化反馈链路当前已接入结果持久化、反馈持久化与审计日志；自动调整与回滚接口为管理员路由，需先通过路由前鉴权。

---

## 25. 稳定：绩效考核模块

### PerformanceController (`/api/performance`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/performance/my-assessment` | 获取当前用户绩效 |
| GET | `/api/performance/assessments/{employeeId}` | 获取指定员工绩效 |
| GET | `/api/performance/rankings` | 获取部门绩效排行榜 |
| GET | `/api/performance/trends/{employeeId}` | 获取绩效趋势 |
| GET | `/api/performance/departments/{deptId}` | 获取部门绩效 |
| GET | `/api/performance/company-rankings` | 获取公司绩效排行榜 |
| GET | `/api/performance/company-bottom-rankings` | 获取公司绩效倒序排行榜 |

**说明**：`company-rankings` 与 `company-bottom-rankings` 当前保留展示层兼容分支，底层已可接入持久化实现。

### 绩效等级

| 等级 | 分数范围 | 说明 |
|------|----------|------|
| S | 95-100 | 卓越 |
| A | 85-94 | 优秀 |
| B | 70-84 | 良好 |
| C | 60-69 | 合格 |
| D | 40-59 | 待改进 |
| F | 0-39 | 不合格 |

---

## 26. 稳定：积分系统模块

### CreditController (`/api/credits`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/credits/balance` | 获取当前用户积分余额 |
| GET | `/api/credits/balance/{employeeId}` | 获取指定员工积分 |
| GET | `/api/credits/history` | 获取积分交易历史 |
| GET | `/api/credits/leaderboard` | 获取积分排行榜 |
| GET | `/api/credits/stats` | 获取积分统计 |

### 积分计算规则

| 因素 | 说明 | 乘数 |
|------|------|------|
| 基础分 | 任务难度系数 × 100 | - |
| 质量 | 成功率 ≥98% | 1.5x |
| 质量 | 成功率 ≥95% | 1.3x |
| 质量 | 成功率 ≥90% | 1.2x |
| 质量 | 成功率 ≥80% | 1.0x |
| 时效 | <1秒完成 | 1.3x |
| 时效 | <5秒完成 | 1.1x |
| 时效 | <30秒完成 | 1.0x |

---

## 27. 稳定：监控模块

### MonitoringController (`/api/monitoring`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/monitoring/health` | 获取健康状态 |
| GET | `/api/monitoring/components` | 获取组件状态 |
| GET | `/api/monitoring/issues` | 获取问题列表 |
| GET | `/api/monitoring/alerts` | 获取告警列表 |
| POST | `/api/monitoring/alerts/{alertId}/ack` | 确认告警 |

**说明**：该控制器返回以监控服务聚合数据为主，所有接口均要求路由前鉴权通过。
---

## 28. 稳定：备份恢复模块

### RecoveryController (`/api/recovery`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/recovery/snapshot` | 创建快照 |
| GET | `/api/recovery/snapshots` | 获取快照列表 |
| POST | `/api/recovery/snapshots/{snapshotId}/restore` | 恢复快照 |
| POST | `/api/recovery/snapshots/{snapshotId}/verify` | 验证一致性 |

**说明**：快照支持按 `scope` 区分；恢复与验证接口要求管理员级路由前鉴权。

---

## 29. 稳定：仪表盘模块

### DashboardController (`/api/dashboard`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dashboard/overview` | 获取仪表盘概览 |

**说明**：该控制器仅做总览聚合，不直接执行业务动作。

---

## 30. 稳定：任务产物模块（Artifact API）

### ArtifactController (`/api/artifacts`)

**状态**：新增（2026-05-13 实施）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/artifacts` | 获取产物列表（支持 department/executionId/employeeCode/type 过滤，分页）|
| GET | `/api/artifacts/{artifactId}` | 获取产物详情 |
| GET | `/api/artifacts/{artifactId}/download` | 下载产物文件 |
| GET | `/api/artifacts/{artifactId}/preview` | 预览产物（内联显示）|
| GET | `/api/artifacts/by-execution/{executionId}` | 按执行ID获取产物列表 |
| GET | `/api/artifacts/by-department/{department}` | 按部门获取产物列表 |
| GET | `/api/artifacts/by-employee/{employeeCode}` | 按员工获取产物列表 |
| GET | `/api/artifacts/stats` | 获取产物统计（支持 department/executionId 过滤）|
| POST | `/api/artifacts/reindex` | 重新索引文件系统产物（支持 baseDir 参数）|

**查询参数**：
- `department` - 部门过滤
- `executionId` - 执行ID过滤
- `employeeCode` - 员工代码过滤
- `type` - 产物类型过滤（html/css/js/markdown/report等）
- `page` - 页码（默认0）
- `size` - 每页大小（默认20）

**产物类型**：
- `html` - HTML网页文件
- `css` - 样式文件
- `js` - JavaScript文件
- `markdown` - Markdown文档
- `report` - 分析报告
- `json` - JSON数据
- `text` - 文本文件
- `python` - Python脚本
- `java` - Java源文件
- `other` - 其他类型

**说明**：该控制器提供任务产物的专用 REST API，支持列表、详情、下载、预览、统计和重新索引。底层使用 `JpaArtifactRecordService` 进行数据库持久化，或使用 `InMemoryArtifactRecordService` 作为 fallback。

---

## 31. 稳定：执行状态模块

### ExecutionStatusController (`/api/executions`)

**状态**：已存在（2026-05-13 确认）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/executions/{executionId}` | 获取执行状态详情 |
| GET | `/api/executions` | 获取执行列表（支持分页和过滤）|

**说明**：该控制器提供执行状态的查询 API，用于长任务异步进度追踪。前端可通过 WebSocket `execution_progress` 消息类型实时接收进度推送，同时可通过此 API 查询执行详情。

---

## 32. 待联调：模型池与大脑模型分配

> 说明：以下为当前前端已经在调用、且模型池方案中需要对齐的接口约定。若后端 controller 尚未落地，应视为待联调，不要误认为已稳定可用。

### ModelPoolController (`/api/model-pool`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/model-pool/providers` | 获取供应商列表（已脱敏，不返回 apiKey）|
| GET | `/api/model-pool/providers/{id}` | 获取供应商详情（已脱敏）|
| POST | `/api/model-pool/providers` | 新增供应商（支持 upsert）|
| PUT | `/api/model-pool/providers/{id}` | 更新供应商 |
| DELETE | `/api/model-pool/providers/{id}` | 删除供应商 |
| POST | `/api/model-pool/providers/{id}/test` | 测试供应商连接 |
| POST | `/api/model-pool/providers/{id}/discover` | 自动发现模型 |
| GET | `/api/model-pool/models` | 获取模型列表 |
| GET | `/api/model-pool/models/{id}` | 获取模型详情 |
| GET | `/api/model-pool/models/provider/{providerId}` | 按供应商获取模型 |
| POST | `/api/model-pool/models` | 新增模型 |
| PUT | `/api/model-pool/models/{id}` | 更新模型 |
| DELETE | `/api/model-pool/models/{id}` | 删除模型 |
| GET | `/api/model-pool/models/available` | 获取可用模型列表 |
| GET | `/api/model-pool/assignments` | 获取所有大脑模型分配 |
| GET | `/api/model-pool/assignments/{brainId}` | 获取单个大脑的模型分配 |
| POST | `/api/model-pool/assignments/{brainId}` | 分配模型到大脑 |
| DELETE | `/api/model-pool/assignments/{brainId}` | 清除大脑模型分配 |
| GET | `/api/model-pool/providers/manifest` | 获取供应商注册清单 |
| GET | `/api/model-pool/providers/{id}/default-base-url` | 获取供应商默认 baseUrl |

**说明**：
- Provider 列表接口已实现安全脱敏，返回 `apiKeyConfigured` 布尔值而非实际密钥
- 新增 `/assignments` 路径，作为 `BrainModelController` 的替代路径
- 新增 `/providers/manifest` 和 `/providers/{id}/default-base-url` 辅助接口

### BrainModelController (`/api/brain-models`)

**状态**：待联调（核心接口已存在，部分路径参数/查询参数形式需继续确认前端是否完全切换）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/brain-models` | 获取所有大脑模型分配 |
| GET | `/api/brain-models/{brainId}` | 获取单个大脑的模型分配 |
| PUT | `/api/brain-models/{brainId}` | 分配/更新大脑模型 |
| DELETE | `/api/brain-models/{brainId}` | 清除大脑模型分配 |
| GET | `/api/brain-models/available` | 获取可用模型列表 |
| POST | `/api/brain-models/switch` | 兼容旧版大脑模型切换接口 |

**补充说明**：
- `PUT /api/brain-models` 与 `DELETE /api/brain-models` 在当前实现中使用 `brainId` 查询参数；
- `POST /api/brain-models/switch` 为 legacy 兼容接口，仍保留以便旧前端或旧联调用法继续可用。

### 前端页面约定

- `frontend/src/pages/ModelPool.tsx` 目前直接依赖上述模型池接口
- `frontend/src/pages/ModelPool.tsx` 中的 `brainId` 不能用 `brain_${brainKey}` 这种前端拼接值
- 应使用后端返回的真实 `brainId`（例如 `neuron://core/main-brain/001`）
- 旧版切换接口 `POST /api/brain-models/switch` 仍可用于兼容，但新调用应优先使用 `PUT /api/brain-models?brainId=...`

---

## 33. legacy 兼容接口

### BrainModelController 旧版切换接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/brain-models/switch` | 旧版大脑模型切换接口，保留兼容 |

**说明**：该接口属于兼容保留路径，新开发优先使用 `PUT /api/brain-models?brainId=...` 或 `GET /api/brain-models/{brainId}` 等主路径。

---

## 34. 稳定：其他模块

### llama.cpp / 本地 OpenAI-compatible 服务

- 可作为 `OPENAI_COMPATIBLE` Provider 候选
- `baseUrl` 示例：`http://192.168.0.249:2026/v1`
- 需先验证至少一个可用模型名和 `/v1/chat/completions` 或 `/v1/completions` 的兼容性
- 若仅支持 `completion` 而不支持标准 chat 格式，后端需要对应适配器，而不能直接假设标准 OpenAI Chat API 可用

---

## 35. 其他模块

### MiscController (`/api`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/version` | 获取版本信息 |
| GET | `/api/notifications/unread-count` | 获取未读通知数 |

**说明**：该控制器使用统一的 `common.ApiResponse` 响应包装。

---

## 36. 稳定：Windows 自动化模块

### WindowsAutomationController (`/api/windows-automation`)

> 用于管理 Windows 自动化客户端节点，支持自动注册、心跳、启用/禁用、删除等操作。
> 客户端 `server.py` 启动时自动调用注册 API，前端可在"公司设置" → 工具 Tab 管理节点。

#### 客户端调用（server.py）

| 方法 | 路径 | 说明 | 请求体 |
|------|------|------|--------|
| POST | `/api/windows-automation/nodes/register` | 客户端注册 | `{node_id, ip, port, hostname, cpu_count, memory_gb, applications, description?, tenant_id?, user_id?}` |
| POST | `/api/windows-automation/nodes/{nodeId}/heartbeat` | 心跳上报 | `{status, ip?, active_sessions?}` |

#### 前端管理调用

| 方法 | 路径 | 说明 | 请求体 |
|------|------|------|--------|
| GET | `/api/windows-automation/nodes` | 列出所有节点 | Query: `tenantId?` |
| PUT | `/api/windows-automation/nodes/{nodeId}` | 更新节点（启用/禁用、描述） | `{description?, enabled?, tenant_id?}` |
| DELETE | `/api/windows-automation/nodes/{nodeId}` | 删除节点 | - |
| GET | `/api/windows-automation/nodes/{nodeId}/status` | 检查节点在线状态 | - |

#### 响应示例

**注册成功**：
```json
{
  "success": true,
  "message": "注册成功",
  "heartbeat_interval": 60
}
```

**节点列表**：
```json
{
  "success": true,
  "nodes": [
    {
      "node_id": "node-abc123",
      "ip_address": "192.168.1.101",
      "port": 8765,
      "hostname": "FINANCE-PC01",
      "cpu_count": 8,
      "memory_gb": 16.0,
      "description": "财务电脑01-金蝶KIS",
      "status": "online",
      "last_heartbeat": "2026-05-21T16:30:00Z",
      "registered_at": "2026-05-21T16:00:00Z",
      "tenant_id": "tenant-001",
      "user_id": "user-001",
      "enabled": true
    }
  ],
  "count": 1
}
```

**说明**：
- 客户端 `server.py` 首次启动时生成 `node_id`（UUID），保存到本地 `node_id.txt`
- 心跳超时 90 秒视为离线
- 节点启用/禁用会同步到 `WindowsAppTool` 运行时

---

## WebSocket 端点

| 路径 | 说明 |
|------|------|
| `/ws/agent` | 智能体对话 |
| `/ws/dept/{dept}` | 部门群聊（文本消息直连部门大脑） |
| `/ws/enterprise` | 董事长频道 |
| `/ws/public` | 访客对话 |

**WebSocket 路由说明**：`/ws/dept/{dept}` 的部门文本消息由 `DepartmentChatService` 处理，不进入 `/ws/agent` 智能体链路，也不进入 `Qwen3Neuron / chat`。非语音部门对话不使用 ASR/TTS。`/ws/agent` 用于智能体/神经元直连会话，二者职责边界应保持清晰。

**部门 WebSocket 执行语义**：当消息被识别为任务/项目/审批/跨部门类执行请求时，服务端会触发内部自治编排链路：主脑规划 → 部门路由 → 固定员工分派 → 任务单准备 → 员工执行派发 → 回执收集 → 产物/知识/绩效沉淀。当前这些执行状态主要通过服务端 Trace 记录，不作为独立公开 WebSocket 事件协议稳定承诺；前端仍按聊天响应处理最终文本结果。

连接格式：
```
ws://localhost:8382/ws/agent?token={authToken}&agentId={agentId}
```

### 前端 WebSocket URL 变更记录

> ⚠️ 以下前端 WebSocket URL 已变更，旧路径已废弃：

| 前端变量 | 旧路径 | 新路径 | 说明 |
|----------|--------|--------|------|
| `wsApi.neuronUrl` | `/ws/neuron/{neuronId}` | `/ws/agent?token=&agentId={neuronId}` | 神经元对话统一走 `/ws/agent` |
| `wsApi.brainUrl` | `/ws/brain/{brainId}` | `/ws/dept/{brainId}?token=` | 大脑对话统一走 `/ws/dept` |
| `wsApi.chairmanUrl` | `/ws/chairman` | `/ws/enterprise?token=` | 董事长频道统一走 `/ws/enterprise` |

---

## 前端 API 调用注意事项

1. **API 路径不要带末尾斜杠**：`/agents` 而非 `/agents/`
2. **员工 ID 包含特殊字符**：使用查询参数方式 `?id={encoded_id}`
3. **响应数据在 `data` 字段**：`{ success: true, data: T }`
4. **字段命名**：后端使用 camelCase，部分接口使用 @JsonProperty 转换为 snake_case
5. **API_BASE = '/api'**：前端请求会自动添加 `/api` 前缀

---

## 更新记录

### 2026-04-16 更新

#### API 路径修正
- **InterventionController** - 确认同时支持 `/api/intervention` 和 `/api/interventions` 路径
- **EvolutionAdminController** - 路径从 `/api/admin` 修正为 `/api/evolution`
- **ProactiveOrchestratorController** - 新增 `/api/proactive/orchestrator` 端点

#### 新增模块
- **FixedEmployeeController** (`/api/fixed-employees`) - 固定员工定义管理
- **OrgController** (`/api/org`) - 组织结构管理
- **DashboardController** (`/api/dashboard`) - 仪表盘数据
- **MonitoringController** (`/api/monitoring`) - 系统监控
- **RecoveryController** (`/api/recovery`) - 备份恢复

#### 主动服务模块扩展
- 新增 `/api/proactive/predictions` 预测列表
- 新增 `/api/proactive/suggestions` 建议列表
- 新增 `/api/proactive/habits/{habitId}/checkin` 习惯打卡

#### 任务模块扩展
- 新增公共任务相关端点：`/api/tasks/public`, `/api/tasks/{taskId}/claim`, `/api/tasks/{taskId}/submit`, `/api/tasks/{taskId}/review`

### 2026-04-08 更新

#### 新增绩效考核与积分系统
- **PerformanceController** (`/api/performance`) - 绩效考核API
  - 个人/员工绩效查询
  - 排行榜、趋势分析
  - 部门绩效统计
- **CreditController** (`/api/credits`) - 积分系统API
  - 积分余额、历史查询
  - 排行榜、统计信息
- **TaskController 扩展** (`/api/tasks`) - 公共任务栏
  - 公共任务列表
  - 任务接取/提交/审核

#### API 路径统一
- **AgentController** 路径从 `/api/v1` 统一为 `/api`
- 新增端点：`/api/health`, `/api/status`, `/api/session/{id}/start`, `/api/session/{id}/end`, `/api/session/{id}/status`

#### 统一 ApiResponse 类
- 创建 `common.ApiResponse<T>` 统一响应类
- 所有 Controller 逐步迁移到使用统一响应类
- 方法：`ok()`, `ok(T data)`, `err(String error, String description)`

#### 新增 API 模块

| 模块 | 路径 | 说明 |
|------|------|------|
| 绩效考核 | `/api/performance` | 绩效评估、排行榜 |
| 积分系统 | `/api/credits` | 积分余额、历史、排行 |
| 公共任务栏 | `/api/tasks/public` | 任务接取与审核 |
| 技能管理 | `/api/skills` | 技能CRUD、ClawHub集成 |
| 神经元管理 | `/api/neurons` | 神经元状态/指标 |
| Agent任务 | `/api/agents/{id}/tasks` | 任务管理 |
| Agent定时任务 | `/api/agents/{id}/schedules` | CRON定时任务 |
| Agent触发器 | `/api/agents/{id}/triggers` | 事件触发器 |
| Agent频道 | `/api/agents/{id}/channel` | 频道配置 |
| Agent文件 | `/api/agents/{id}/files` | 文件管理 |
| 文档管理 | `/api/enterprise/documents` | 企业文档浏览 |
| 访客管理 | `/api/reception/visitors` | 前台访客 |
| 办公室 | `/api/office` | 办公室管理 |
| 项目任务 | `/api/projects/{id}/tasks` | 项目任务子资源 |
| 主动预测 | `/api/proactive/predictions` | AI预测 |
| 进化状态 | `/api/evolution/feedback` | 系统进化反馈 |
| 公司管理 | `/api/tenants/admin/companies` | 租户管理 |

### 2026-04-29 更新

#### 模型池模块扩展
- **ModelPoolController** 新增以下端点：
  - `/api/model-pool/assignments` - 获取所有大脑模型分配
  - `/api/model-pool/assignments/{brainId}` - 获取单个大脑的模型分配
  - `/api/model-pool/assignments/{brainId}` (POST) - 分配模型到大脑
  - `/api/model-pool/assignments/{brainId}` (DELETE) - 清除大脑模型分配
  - `/api/model-pool/providers/manifest` - 获取供应商注册清单
  - `/api/model-pool/providers/{id}/default-base-url` - 获取供应商默认 baseUrl
  - `/api/model-pool/models/available` - 获取可用模型列表

#### API 安全改进
- **Provider 列表接口** 已实现安全脱敏，返回 `apiKeyConfigured` 布尔值而非实际密钥
- **POST /api/model-pool/providers** 支持 upsert 行为（provider 已存在时自动更新）

#### 文档修正
- **FixedEmployeeController** - 删除重复的 profiles/personas 端点记录
- **OfficeController** - 删除不存在的 `/events` 端点及相关说明（当前仅支持快照，不支持事件流）

### 2026-05-08 更新

#### 自治执行闭环基础设施（内部架构升级，无新增公开 REST 端点）

本轮在 `living-agent-core/src/main/java/com/livingagent/core/autonomy/` 下完成了从"主脑规划→部门路由→固定员工分派→任务单准备→员工执行派发→真实回执等待→产物/知识/绩效沉淀→响应编排"的完整执行闭环。**公开 API 路径和请求/响应结构保持不变**，但 `/api/dept/{department}/chat` 和 `/ws/dept/{dept}` 的内部执行语义已显著增强。

##### 新增核心组件

| 组件 | 位置 | 职责 |
|------|------|------|
| `DynamicEmployeeTaskConsumerRegistry` | `autonomy/impl/` | 基于 `FixedEmployeeRegistry` 在启动时自动注册所有固定员工的任务消费者。`@Bean(initMethod="registerAll")`，订阅通道 `channel://employee/{neuronId}/tasks`，执行完成后回发 receipt |
| `MainBrainResponseComposer` | `autonomy/` | 组合主脑规划结果 + 执行团队信息 + 执行状态，生成最终用户响应 |
| `DefaultMainBrainResponseComposer` | `autonomy/impl/` | 默认实现，返回包含执行人员、状态（WAITING_RECEIPT/COMPLETED）、交付物摘要的结构化响应 |
| `ExecutionResultAggregator` | `autonomy/` | 聚合所有员工 receipt 为执行摘要（completedCount/failedCount/totalCount） |
| `DefaultExecutionResultAggregator` | `autonomy/impl/` | 默认实现，按员工维度汇总执行结果 |
| `ArtifactRecord` | `autonomy/` | 任务产物记录结构 |
| `ArtifactRecordService` | `autonomy/` | 产物记录接口 |
| `InMemoryArtifactRecordService` | `autonomy/impl/` | 内存版产物记录服务（第一版） |
| `KnowledgeCaptureResult` | `autonomy/` | 知识沉淀结果结构 |
| `KnowledgeCaptureService` | `autonomy/` | 知识沉淀接口 |
| `DefaultKnowledgeCaptureService` | `autonomy/impl/` | 默认知识沉淀实现 |
| `PerformanceCaptureResult` | `autonomy/` | 绩效记录结果结构 |
| `PerformanceCaptureService` | `autonomy/` | 绩效记录接口 |
| `DefaultPerformanceCaptureService` | `autonomy/impl/` | 默认绩效记录实现 |

##### 执行状态流变更

```text
旧流程：publish 员工任务 → 模拟回执 → 直接标记 COMPLETED
新流程：publish 员工任务 → DISPATCHED → 等待真实回执 → WAITING_RECEIPT
```

- **已删除模拟回执路径**：`ChannelBackedDepartmentExecutionCoordinator` 不再在 publish 后自动标记 COMPLETED
- **状态流转**：`NO_ASSIGNMENT` → `DISPATCHED` → `WAITING_RECEIPT`（等待 `DynamicEmployeeTaskConsumerRegistry` 的真实回执）
- **已移除** `MinimalEmployeeTaskExecutor`（由 `DynamicEmployeeTaskConsumerRegistry` 替代）
- `DepartmentChatService` 已集成 `MainBrainResponseComposer`，用户响应由编排器组合后返回

##### 完整 Trace 阶段

当前自治链路产生的 Trace 阶段包括：

```text
intake_classified        → 入口分类完成
main_brain_planned       → 主脑任务规划完成
brain_routed             → 大脑路由完成
department_plan_created  → 部门计划创建
employee_assignment_planned → 员工分派计划完成
assignment_batch_prepared → 任务单准备批次完成
employee_assigned        → 员工任务已派发（DISPATCHED）
employee_execution_started → 员工开始执行
employee_execution_completed → 员工执行完成（收到回执）
department_execution_completed → 部门执行完成
artifact_recorded        → 产物已记录
knowledge_recorded       → 知识已沉淀
performance_recorded     → 绩效已记录
result_aggregated        → 结果已汇总
response_composed        → 用户响应已编排
```

##### GatewayConfig Bean 变更

| Bean | 变更类型 | 说明 |
|------|----------|------|
| `DynamicEmployeeTaskConsumerRegistry` | 新增 | `@Bean(initMethod="registerAll")`，替代 `MinimalEmployeeTaskExecutor` |
| `MainBrainResponseComposer` | 新增 | 指向 `DefaultMainBrainResponseComposer` |
| `ExecutionResultAggregator` | 新增 | 指向 `DefaultExecutionResultAggregator` |
| `DepartmentExecutionCoordinator` | 修改 | 构造函数移除 `EmployeeExecutionReceiptService` 参数 |
| `MinimalEmployeeTaskExecutor` | 移除 | 功能由 `DynamicEmployeeTaskConsumerRegistry` 替代 |

##### 待后续推进

- 🔲 扩展到 finance/legal/hr/ops 部门的完整执行闭环
- 🔲 持久化回执服务（当前为内存版 `InMemoryEmployeeExecutionReceiptService`）
- ~~🔲 接入真实 LLM 调用替代 `RuleBasedMainBrainTaskDirector` 的规则匹配~~ ✅ 已由 `LlmBasedMainBrainTaskDirector` 完成
- ~~🔲 `FinalResponseCoordinator`：决定咨询类直出 vs 执行类回主脑收口的最终出口策略~~ ✅ 已由 `LlmBasedFinalResponseCoordinator` 完成

### 2026-05-11 更新

#### LLM-first / Rule-fallback 自治决策升级（内部架构升级，无新增公开 REST 端点）

本轮按照 `docs/LLM_AUTONOMY_HARDCODE_ANALYSIS.md` 的分析，将所有核心决策链路从"规则驱动"升级为"LLM 自主决策 + 规则降级兜底"。**公开 API 路径和请求/响应结构保持不变**。

##### LLM-first 决策链路变更

| 决策环节 | 旧实现（规则驱动） | 新实现（LLM-first） | 降级兜底 |
|----------|-------------------|---------------------|----------|
| 对话意图分析 | `RuleBasedDialogueAnalyzer` | `LlmBasedDialogueAnalyzer` | ✅ 已有 |
| 主脑任务规划 | `RuleBasedMainBrainTaskDirector` | `LlmBasedMainBrainTaskDirector` | ✅ 已有 |
| 员工分派 | `RegistryBackedFixedEmployeeDispatcher` | `LlmBasedFixedEmployeeDispatcher` | ✅ 新增 |
| 回复策略 | `DefaultFinalResponseCoordinator` | `LlmBasedFinalResponseCoordinator` | ✅ 新增 |
| 响应编排 | `DefaultMainBrainResponseComposer` | `LlmBasedMainBrainResponseComposer` | ✅ 新增 |
| 聊天意图分类 | `ChatIntentClassifier`（纯关键词） | `ChatIntentClassifier`（LLM-first） | ✅ 新增 |
| 主动建议 | `PatternPredictor`（纯统计规则） | `LlmProactiveAdvisor` | ✅ 新增 |
| 风险评估 | `RiskPredictor`（固定阈值） | `LlmRiskAssessor` | ✅ 新增 |

##### 新增组件

| 组件 | 位置 | 职责 |
|------|------|------|
| `LlmBasedFixedEmployeeDispatcher` | `autonomy/impl/` | LLM 根据员工能力/负载/绩效动态选人，输出 `selectionReason`/`confidence` |
| `LlmBasedFinalResponseCoordinator` | `autonomy/impl/` | LLM 动态选择回复策略（7种），输出 `strategy`/`reason` |
| `LlmBasedMainBrainResponseComposer` | `autonomy/impl/` | LLM 根据执行结果动态生成自然语言回复 |
| `LlmProactiveAdvisor` / `LlmProactiveAdvisorImpl` | `proactive/llm/` | LLM 主动建议接口和实现 |
| `LlmRiskAssessor` / `LlmRiskAssessorImpl` | `proactive/llm/` | LLM 风险评估接口和实现 |
| `LLMEmployeeCreationService` / `LLMEmployeeCreationServiceImpl` | `autonomy/` | LLM 驱动的动态员工创建，确保新员工有专属名字/编号/能力/职责 |

##### FinalResponseStrategy 枚举扩展

从 2 种扩展到 7 种：

| 策略 | 说明 |
|------|------|
| `DIRECT_ANSWER` | 简单咨询，直接回答 |
| `ASK_CLARIFICATION` | 用户意图不明确，需要追问 |
| `MAIN_BRAIN_COMPOSE` | 执行类任务，主脑汇总 |
| `WAIT_FOR_RECEIPTS` | 任务已派发，等待回执 |
| `DEPARTMENT_BRAIN_DIRECT` | 部门内咨询，部门大脑直接回复 |
| `ESCALATE_TO_HUMAN` | 高风险，需人工介入 |
| `REQUEST_APPROVAL` | 涉及审批流程 |

##### 固定员工重复创建 Bug 修复

- **根因**：`generateEmployeeId` 用员工姓名生成 ID，`getEmployeeByCode` 用 neuronId 派生的 ID 查找，两者永远不匹配
- **修复**：`FixedEmployeeRegistry.createFixedEmployee` 传入 `IdUtils.neuronToEmployeeId(def.neuronId())` 作为确定性 ID
- **数据库清理**：已删除 416 条重复的数字员工记录（ID 含中文名），保留 32 条固定员工

##### Dashboard 统计公式修复

- **旧公式**：`digital = fixedEmployeeRegistry.getActiveEmployeeCount()`（会话级），`human = total - digital`
- **新公式**：`digital = isDigital().count()`，`human = isHuman().count()`（DB 级真实属性）

##### DigitalEmployeeEntity 新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `origin` | String(20) | 员工来源：`FIXED`（32固定员工）/ `EVOLVED`（LLM自主创建）/ `PERSONAL`（默认） |

##### GatewayConfig Bean 变更

| Bean | 变更类型 | 说明 |
|------|----------|------|
| `FixedEmployeeDispatcher` | 修改 | 从 `RegistryBackedFixedEmployeeDispatcher` 切换到 `LlmBasedFixedEmployeeDispatcher` |
| `FinalResponseCoordinator` | 修改 | 从 `DefaultFinalResponseCoordinator` 切换到 `LlmBasedFinalResponseCoordinator` |
| `MainBrainResponseComposer` | 修改 | 从 `DefaultMainBrainResponseComposer` 切换到 `LlmBasedMainBrainResponseComposer` |
| `LLMEmployeeCreationService` | 新增 | LLM 驱动的动态员工创建 |
| `LlmProactiveAdvisor` | 新增 | LLM 主动建议 |
| `LlmRiskAssessor` | 新增 | LLM 风险评估 |
| `DynamicEmployeeTaskConsumerRegistry` | 修改 | 构造函数新增 `BrainModelResolver` 和 `ModelPoolManager` 参数 |
| `LlmBasedDialogueAnalyzer` | 修改 | 构造函数新增 `FixedEmployeeRegistry` 参数；Prompt 动态注入部门/员工信息；增加 `requiresClarification`/`riskReasons` 字段 |
| `LlmBasedMainBrainTaskDirector` | 修改 | 构造函数新增 `FixedEmployeeRegistry` 参数；Prompt 动态注入员工画像；移除 `tech`/`T02/T09` 硬编码默认值 |
| `ExecutionResultAggregator` | 修改 | 从 `DefaultExecutionResultAggregator` 切换到 `LlmBasedExecutionResultAggregator` |
| `ExecutionReceiptReviewer` | 新增 | LLM 审核回执是否满足验收标准 |
| `AssignmentReadinessEvaluator` | 新增 | LLM 评估任务分派准备度（4种状态） |
| `HybridContextCompactor` | 新增 | 程序提取 + LLM 语义压缩的混合上下文压缩器 |

### 2026-05-12 更新

#### 统一决策上下文与 LLM 决策客户端（内部架构升级，无新增公开 REST 端点）

本轮按照 `docs/LLM_AUTONOMY_HARDCODE_ANALYSIS.md` 的建议，建设统一的决策上下文组装器和 LLM 决策客户端。**公开 API 路径和请求/响应结构保持不变**。

##### 新增组件

| 组件 | 位置 | 职责 |
|------|------|------|
| `DecisionContext` | `autonomy/context/` | 统一决策上下文结构，聚合请求、用户、大脑、员工、工具、知识、项目、审批、约束上下文 |
| `DecisionContextBuilder` | `autonomy/context/` | 决策上下文构建器接口，支持不同构建选项 |
| `DefaultDecisionContextBuilder` | `autonomy/context/impl/` | 默认决策上下文构建器实现，从各注册表/服务聚合上下文 |
| `LlmDecisionClient` | `autonomy/llm/` | 统一 LLM 决策客户端接口，支持 JSON Schema 校验、修复重试、降级兜底 |
| `DefaultLlmDecisionClient` | `autonomy/llm/impl/` | 默认 LLM 决策客户端实现，支持 Prompt 版本管理、JSON 提取、Schema 校验、修复重试、Trace 记录 |
| `EmployeeTaskExecutionOutcome` | `autonomy/` | 员工任务执行结果结构，支持 COMPLETED/DEGRADED/FAILED/NEEDS_RETRY 状态 |
| `ToolCallRecord` | `autonomy/` | 工具调用记录结构 |

##### 员工执行状态扩展

| 状态 | 说明 |
|------|------|
| `COMPLETED` | 正常完成，有真实产物 |
| `DEGRADED` | 降级完成，模型不可用时的降级摘要 |
| `FAILED` | 执行失败 |
| `NEEDS_RETRY` | 需要重试 |
| `PARTIAL` | 部分完成 |

##### 任务分派准备度评估

`AssignmentReadinessEvaluator` 已接入 `DepartmentChatService` 的派发前置流程：

| 准备度状态 | 行为 |
|------------|------|
| `READY` | 正常派发到员工执行通道 |
| `BLOCKED` | 阻塞派发，返回阻塞原因 |
| `NEEDS_CLARIFICATION` | 需要用户补充信息，返回追问问题 |
| `PARTIALLY_READY` | 部分准备就绪，可继续但需注意风险 |

##### 降级摘要修复

- **问题**：`DynamicEmployeeTaskConsumerRegistry` 在所有模型调用失败后返回降级摘要，但仍标记为 `COMPLETED`
- **修复**：引入 `EmployeeTaskExecutionOutcome`，模型不可用时返回 `DEGRADED` 状态，不再误标记为 `COMPLETED`
- **影响**：`FinalResponseCoordinator` 遇到 `DEGRADED` 状态时会选择 `WAIT_FOR_RECEIPTS` 或 `ESCALATE_TO_HUMAN` 策略

### 2026-05-13 更新

#### 新增 Artifact API 模块
- **ArtifactController** (`/api/artifacts`) - 任务产物专用 REST API
  - `GET /api/artifacts` - 获取产物列表（支持 department/executionId/employeeCode/type 过滤，分页）
  - `GET /api/artifacts/{artifactId}` - 获取产物详情
  - `GET /api/artifacts/{artifactId}/download` - 下载产物文件
  - `GET /api/artifacts/{artifactId}/preview` - 预览产物（内联显示）
  - `GET /api/artifacts/by-execution/{executionId}` - 按执行ID获取产物列表
  - `GET /api/artifacts/by-department/{department}` - 按部门获取产物列表
  - `GET /api/artifacts/by-employee/{employeeCode}` - 按员工获取产物列表
  - `GET /api/artifacts/stats` - 获取产物统计
  - `POST /api/artifacts/reindex` - 重新索引文件系统产物

#### 新增数据库实体和迁移
- **ArtifactRecordEntity** - Artifact 记录实体，支持 artifactId/executionId/department/employee/type/path/size/sha256/metadata 等字段
- **ArtifactRecordRepository** - 支持按 executionId/department/employee/type 查询，分页和统计
- **V8__artifact_records.sql** - Flyway 迁移脚本，创建 artifact_records 表及索引

#### 新增核心服务组件
- **JpaArtifactRecordService** - JPA 数据库产物记录服务，支持持久化、查询、分页、目录扫描索引
- **MainBrainFinalSummaryService** - 主脑最终总结服务接口
- **LlmMainBrainFinalSummaryService** - LLM 驱动的主脑最终总结，支持自动降级
- **DefaultMainBrainFinalSummaryService** - 默认主脑最终总结实现（模板方式 fallback）
- **EmployeeTaskExecutor** - 员工任务执行器接口
- **ToolBackedEmployeeTaskExecutor** - 工具驱动的员工任务执行器，支持 DOCKER_SANDBOX/ARTIFACT_ONLY/LOCAL_RESTRICTED/HUMAN_REVIEW_REQUIRED 执行环境
- **ModelHealthRegistry** - 模型健康注册表，支持 AVAILABLE/DEGRADED/COOLDOWN/UNAVAILABLE/UNKNOWN 五种状态
- **ExecutionReviewResult** - 执行评审结果，支持 passed/needsRework/failed 三种状态

#### 增强现有组件
- **DynamicEmployeeTaskConsumerRegistry** - 已接入 `EmployeeTaskExecutor`，优先使用真实工具执行器，LLM 文本作为 fallback
- **ToolBackedEmployeeTaskExecutor** - 已集成 `DockerSandboxService`，支持 DOCKER_SANDBOX 执行环境
- **BrainModelResolver** - 已集成 `ModelHealthRegistry`，支持熔断过滤
- **EmployeeExecutionReceiptService** - 新增 `ReceiptListener` 机制，支持 receipt 到达后通知 WebSocket 推送进度
- **FileBasedEmployeeExecutionReceiptService** - 实现 listener 通知机制
- **DepartmentWebSocketHandler** - 新增 `pushExecutionProgress()` 方法，支持 `execution_progress` WebSocket 消息类型推送
- **InMemoryArtifactRecordService** - 更新以匹配新接口方法签名
- **ArtifactRecord** - record 增加 `sizeBytes` 和 `sha256` 字段
- **ArtifactRecordService** - 接口扩展（12 个新方法）

#### Bean 注册更新
- **GatewayConfig** - 新增注册 `ModelHealthRegistry`、`mainBrainFinalSummaryService`、`employeeTaskExecutor` Bean
- **GatewayConfig** - 修改 `dynamicEmployeeTaskConsumerRegistry` Bean 注册，传入 `employeeTaskExecutor` 依赖
- **GatewayConfig** - 修改 `employeeTaskExecutor` Bean 注册，通过 `Optional<SandboxService>` 可选注入

##### GatewayConfig Bean 变更

| Bean | 变更类型 | 说明 |
|------|----------|------|
| `DecisionContextBuilder` | 新增 | 统一决策上下文构建器 |
| `LlmDecisionClient` | 新增 | 统一 LLM 决策客户端 |
| `DepartmentChatService` | 修改 | 构造函数新增 `AssignmentReadinessEvaluator` 参数 |

##### Trace 阶段扩展

新增 Trace 阶段：

```text
readiness_evaluated      → 任务分派准备度评估完成
```

### 2026-05-18 更新

#### 任务与项目模块统一身份字段和持久化

##### 新增数据库实体
- **TaskEntity** - 任务持久化主表，包含 taskType/description/priority/status/userId/tenantId/taskKey/executionId/departmentCode/sourceType/projectId/submissionResult/reviewerId 等统一身份字段
- **ProjectEntity** - 项目持久化主表，包含 tenantId/creatorUserId/projectKey/sourceTaskKey/sourceConversationId/dataNamespace/managerId/ownerDepartment 等统一身份字段

##### 新增 Repository
- **TaskRepository** - 支持 findByAssignedToAndStatus/findByUserId/findByTaskKey/findByExecutionId/findByProjectId 等多维度查询
- **ProjectRepository** - 支持 findByTenantId/findByCreatorUserId/findByProjectKey/findByOwnerDepartment 等多维度查询

##### 任务状态机扩展
- TaskStatus 枚举从 5 种扩展到 10 种：PENDING/CLAIMED/IN_PROGRESS/SUBMITTED/PENDING_REVIEW/REVIEWED/COMPLETED/REJECTED/NEEDS_REWORK/FAILED
- `submit` 不再直接完成任务，而是进入 SUBMITTED 状态
- `review` 审核通过后才进入 COMPLETED 状态

##### API 语义变更
- **TaskController** - `GET /api/tasks/employee/{employeeId}` 新增 `status` 查询参数过滤
- **TaskController** - `POST /api/tasks/{taskId}/submit` 语义变更：进入 SUBMITTED 状态而非直接完成
- **TaskController** - `POST /api/tasks/{taskId}/review` 语义明确：审核通过进入 COMPLETED，拒绝进入 REJECTED 或 NEEDS_REWORK

##### 废弃接口
- **AgentTaskController** - 已标记 `@Deprecated`，所有任务操作统一使用 `TaskController` 的 `/tasks` 路由

##### 项目任务子资源真实接入
- **ProjectController** - `/projects/{projectId}/tasks` 已接入真实 TaskRepository，不再是 stub
- 创建的项目任务会关联 projectId，支持按项目查询任务

##### 持久化同步
- **TaskCheckout** - 注入 TaskRepository，关键操作同步持久化；新增 `submitTask()` 和 `reviewTask()` 方法
- **ProjectServiceImpl** - 注入 ProjectRepository，关键操作同步持久化

##### 前端 API 统一
- **taskApi** - list/create/update/getLogs/trigger 改为调用 `/tasks` 路由

##### 身份认证强化
- **TaskController** - 新增 `GET /api/tasks/my` 端点，从 token 提取当前用户身份，替代 `/tasks/employee/{employeeId}`
- **TaskController** - `POST /api/tasks/{taskId}/claim` 不再需要请求体传入 `employeeId`，从 token 中提取
- **TaskController** - `POST /api/tasks/{taskId}/submit` 不再信任请求体中的 `employeeId`，从 token 中提取
- **前端 taskApi** - `claimTask` 不再传入 `employeeId` 参数
- **前端 taskApi** - `submitTask` 不再传入 `employeeId` 参数
- **前端 taskApi** - `getEmployeeTasks` 改为 `getMyTasks`，不再传入 `employeeId`
- **前端 globalTaskApi** - `getByEmployee` 改为 `getMyTasks`

##### Artifact 关联 taskId/projectId
- **ArtifactRecord** - 新增 `taskId` 和 `projectId` 字段
- **ArtifactRecordEntity** - 新增 `task_id` 和 `project_id` 列
- **ArtifactRecordService** - 新增 `getByTaskId`、`getByProjectId`、`associateTaskAndProject` 方法
- **ArtifactRecordRepository** - 新增 `findByTaskId`、`findByProjectId` 等查询方法

##### 权限服务集成
- **TaskController** - `getTask` 添加 `canViewTask` 权限检查
- **TaskController** - `submitTask` 添加 `canEditTask` 权限检查
- **TaskController** - `reviewTask` 添加 `canReviewTask` 权限检查
- **ProjectController** - `getProject` 添加 `canViewProject` 权限检查
- **ProjectController** - `updateProject` 添加 `canEditProject` 权限检查
- **ProjectController** - `deleteProject` 添加 `canManageProject` 权限检查

##### WebSocket 连接注册
- **DepartmentWebSocketHandler** - 连接建立时注册到 `ConnectionRegistry`，关闭时注销
- **DepartmentChatService** - 创建 execution 时绑定到 `ConnectionRegistry`，写入 `RuntimeEventStore` 事件

##### Receipt 反写
- **ExecutionReceiptTaskProjectBridge** - receipt 到达时自动更新关联 Task/Project 状态

### 2026-05-19 更新

#### NEEDS_CLARIFICATION 澄清分支闭环修复

##### P0-A：澄清直接返回
- **DepartmentChatService** - `handleClarificationOrBlocked()` 方法：NEEDS_CLARIFICATION/BLOCKED 时直接 compose 澄清消息、save assistant message、trace `clarification_requested`/`execution_blocked`、push WebSocket event、return `DepartmentChatResult.success()`，不再进入 `processWithBrain()` 订阅 output channel 等待 brain.process()
- **DepartmentChatService** - 新增 `extractClarificationQuestions()`/`extractBlockingIssues()`/`composeClarificationMessage()`/`composeBlockedMessage()` 辅助方法

##### P0-B：TechBrain 兜底输出
- **TechBrain** - 空消息时调用 `publishFallbackResponse()` 发布兜底响应，不再静默返回
- **TechBrain** - ReAct loop 成功但空内容时发布兜底响应
- **TechBrain** - 异常处理使用 `publishFallbackResponse()`，确保即使 error 发布失败也有兜底
- **TechBrain** - 新增 `publishFallbackResponse()` 方法：构造带 `fallback=true` metadata 的 ChannelMessage

##### P1-A：澄清状态进入正式任务状态机
- **TaskStatus** - 新增 `NEEDS_CLARIFICATION` 和 `CLARIFICATION_PENDING` 枚举值
- **TaskEntity** - 新增 `readinessStatus`/`clarificationQuestions`/`clarificationAnswer`/`clarificationRequestedAt`/`blockingIssues` 字段
- **TaskRepository** - 新增 `findByReadinessStatus()` 查询
- **TaskCheckout** - 新增 `requestClarification()` 和 `resolveClarification()` 方法

##### P1-B：轻量 execution/work item
- **DepartmentChatService** - `handleClarificationOrBlocked()` 中使用 `WorkItemKeyGenerator` 生成 taskKey/executionId
- **DepartmentChatService** - 新增 `createClarificationTaskEntity()` 方法：创建轻量 TaskEntity

##### P1-C：WebSocket 结构化 execution events
- **DepartmentWebSocketHandler** - 新增 `pushExecutionEvent()` 方法
- **DepartmentChatService** - 新增 `pushExecutionEventSafe()` 辅助方法
- 关键节点推送：`intake_classified`/`main_brain_planned`/`readiness_evaluated`/`clarification_requested`/`execution_started`/`receipt_received`/`finalized`

#### 部门对话长期可恢复会话

##### 新增实体和 Repository
- **DepartmentConversationEntity** - 长期部门对话实体，包含 conversationId/conversationKey/tenantId/ownerUserId/departmentCode/title/status/lastMessageAt/activeTaskKey/activeExecutionId/retentionPolicy/archivedAt/deletedAt
- **DepartmentConversationRepository** - 按 conversationId/ownerUserId+departmentCode+status/tenantId+status 查询

##### 消息实体增强
- **DepartmentChatMessageEntity** - 新增 conversationId/taskKey/executionId/messageType/tenantId/deletedAt 字段
- **DepartmentChatMessageRepository** - 新增按 conversationId 查询方法

##### ConnectionRegistry 增强
- **ConnectionRegistry** - 新增 `bindConversation`/`unbindConversation`/`getSessionIdByConversationId` 接口方法
- **InMemoryConnectionRegistry** - 实现 conversationIdToSession 映射

##### DepartmentChatService 以 conversationId 为核心
- 注入 `DepartmentConversationRepository`/`WorkItemKeyGenerator`
- 新增 `findOrCreateConversation`/`findConversation`/`listActiveConversations`/`updateConversationLastMessage`/`updateConversationContext`/`archiveConversation`/`softDeleteConversation`/`getConversationHistory`/`resolveTaskKeyForConversation` 方法
- `saveMessage` 扩展为支持 conversationId/taskKey/executionId 的重载版本
- `processDepartmentBrainAsync` 入口处 findOrCreateConversation 并 bindConversation
- `handleClarificationOrBlocked`/`processWithBrain`/`processBrainResponse` 全链路传递 conversationId

##### 新增 REST 端点
- **DepartmentApiController** - `GET /api/dept/{department}/conversations` 列出活跃对话
- **DepartmentApiController** - `GET /api/dept/conversations/{conversationId}/history` 获取对话历史
- **DepartmentApiController** - `DELETE /api/dept/conversations/{conversationId}` 软删除对话

##### WebSocket 断线重连
- **DepartmentWebSocketHandler** - 支持 `?conversationId=xxx` 查询参数，重连时绑定 conversationId 并发送 reconnected 消息

##### 权限增强
- **WorkItemPermissionService** - 新增 `canViewConversation`/`canEditConversation`/`canDeleteConversation`

##### 前端 API
- **api.ts** - 新增 `listConversations`/`getConversationHistory`/`deleteConversation`
- **DepartmentChatInline.tsx** - 新增 `conversationId` state，发送/接收携带 conversationId

#### 大脑与员工规范体系

##### 统一输出契约
- **BrainOutputContract** - 统一大脑输出：status/summary/plan/clarificationQuestions/blockingIssues/riskLevel/conversationId/taskKey/executionId
- **EmployeeOutputContract** - 统一员工输出：employeeCode/status/summary/completedItems/failedItems/riskLevel/retryable/failedReason/failedStage

##### 越权拦截
- **ExecutionBoundaryEnforcer** - 8 个部门管辖权映射、跨部门操作白名单、高风险任务识别

##### 大脑职责边界
- **BrainBoundaryEnforcer** - 9 个大脑的 allowedActions/forbiddenActions/escalationTriggers/mustEscalateScenarios

##### 规范强制加载链
- **StandardLoadingChainService** - 职责卡→Prompt→runbook→文档工作流→自定义指令强制加载

##### 规范合规追踪
- **StandardComplianceTraceService** - 边界检查/标准加载/澄清/升级/回执合规/权限检查追踪

### 2026-05-25 更新

#### 安全注解与权限强化

- **@RequireAccess** — 新增统一权限检查注解，用于 Controller 方法声明式权限控制
  - 参数：`resource`（资源类型）、`action`（资源名称/动作）、`requireFull`（是否要求 FULL 权限，默认 false）
  - 配套 AOP 切面：`RequireAccessAspect`，拦截注解方法并执行权限检查
- **管理类 API 权限升级**：以下 API 路径现在需要 FULL 权限（董事长级别）
  - `/api/model-pool/**` — 模型池管理
  - `/api/brain-models/**` — 大脑模型配置与切换
  - `/api/windows-automation/**` — Windows 自动化节点管理
  - `/api/v1/proxy/**` — Claude CLI 代理
  - `/api/evolution/**` — 进化系统管理

#### 前端 WebSocket 路径变更

| 前端变量 | 旧路径 | 新路径 |
|----------|--------|--------|
| `wsApi.neuronUrl` | `/ws/neuron/{neuronId}` | `/ws/agent?token=&agentId={neuronId}` |
| `wsApi.brainUrl` | `/ws/brain/{brainId}` | `/ws/dept/{brainId}?token=` |
| `wsApi.chairmanUrl` | `/ws/chairman` | `/ws/enterprise?token=` |

#### 安全模块类名变更

| 旧类名 | 新类名 | 说明 |
|--------|--------|------|
| `security.Employee` | `SecurityIdentity` | 安全上下文员工信息，避免与 `employee.Employee` 混淆 |
| `security.EmployeeService` | `AuthEmployeeService` | 安全员工服务 |
| `security.EmployeeServiceImpl` | `AuthEmployeeServiceImpl` | 安全员工服务实现 |

### 2026-05-27 更新

#### 新增工作区热配置 API

- **SystemSettingsController** 新增工作区配置端点：
  - `GET /api/enterprise/settings/workspace/config` — 获取工作区配置（需 FULL 权限）
  - `PUT /api/enterprise/settings/workspace/config` — 热更新工作区配置（需 FULL 权限）

**请求体（PUT）**：
```json
{
  "root": "/app/workspace",
  "writeEnabled": true,
  "allowedExtensions": ".java,.xml,.yml,.yaml,.properties,.json,.md,.sql,.ts,.tsx,.js,.jsx,.css,.html,.py,.sh,.ps1,.txt"
}
```

**响应示例（GET）**：
```json
{
  "root": "/app/workspace",
  "writeEnabled": true,
  "allowedExtensions": ".java,.xml,.yml,..."
}
```

**说明**：
- 修改 `root` 路径时会同步热更新 `FileEditTool` 和 `BuildTool` 的工作区根路径，无需重启服务
- Docker 卷挂载（`F:\SoarCloudAI → /app/workspace`）在容器创建时设定，不可热改
- 但工作区子路径可以热切换，例如从 `/app/workspace` 切换到 `/app/workspace/docker/living-agent-service`

#### 新增 FileEditTool 和 BuildTool

- **FileEditTool**（注册名：`file_edit`）— 源码文件编辑工具，LLM 可通过 function calling 读写工作区文件
  - `read_file` — 读取文件内容
  - `write_file` — 写入文件（需审批）
  - `list_dir` — 列出目录内容
  - `search_code` — 在文件中搜索文本模式
- **BuildTool**（注册名：`build`）— 构建触发工具，修改源码后触发编译和部署
  - `compile` — 执行 `mvn compile`
  - `build` — 执行 `mvn package -DskipTests`
  - `restart` — 执行 `docker restart`
  - `status` — 检查 Docker 容器状态

#### MiscController 权限简化

- `GET /api/version` — 移除权限检查，改为公共端点
- `GET /api/notifications/unread-count` — 移除权限检查，改为公共端点

#### PermissionService 数据库 fallback

- **PermissionServiceImpl** — 新增 `EnterpriseEmployeeService` 依赖，从内存存储找不到员工时 fallback 到数据库查询
- 修复了已登录用户访问 API 时因内存存储未同步而返回 403 的问题
