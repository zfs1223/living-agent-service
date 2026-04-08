# Living Agent Service API 接口文档

> 基于 `living-agent-gateway` 模块的 Controller 整理
> 更新时间: 2026-04-08

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

后端使用统一的 `common.ApiResponse` 类：

```java
// 文件: living-agent-gateway/src/main/java/com/livingagent/gateway/controller/common/ApiResponse.java

public record ApiResponse<T>(
    boolean success,
    T data,
    String error,
    String errorDescription
) {
    // 成功响应
    public static <T> ApiResponse<T> ok(T data) { ... }
    public static <T> ApiResponse<T> ok() { ... }
    
    // 错误响应
    public static <T> ApiResponse<T> err(String error, String description) { ... }
    public static <T> ApiResponse<T> err(String error, String description, T data) { ... }
}
```

---

## 1. 认证模块

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

## 2. 系统模块

### SystemController (`/api/system`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/system/status` | 获取系统状态 |
| POST | `/api/system/register` | 注册创始人 |
| GET | `/api/system/config` | 获取系统配置 |
| PUT | `/api/system/config` | 更新系统配置 |
| GET | `/api/system/config/providers` | 获取提供商配置 |
| PUT | `/api/system/config/providers/{providerId}` | 更新提供商配置 |

### SystemSettingsController (`/api/chairman/settings`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/chairman/settings` | 获取所有设置 |
| GET | `/api/chairman/settings/{category}` | 获取分类设置 |
| GET | `/api/chairman/settings/{category}/{key}` | 获取具体设置 |
| PUT | `/api/chairman/settings/{category}/{key}` | 更新设置 |
| POST | `/api/chairman/settings/batch` | 批量更新 |
| PUT | `/api/chairman/settings` | 批量更新（别名） |
| GET | `/api/chairman/settings/history` | 获取变更历史 |
| POST | `/api/chairman/settings/{category}/{key}/reset` | 重置设置 |
| GET | `/api/chairman/settings/categories` | 获取分类列表 |

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
| GET | `/api/agents/{agentId}/tasks` | 获取任务列表 |
| GET | `/api/agents/{agentId}/activity` | 获取活动记录 |
| GET | `/api/agents/{agentId}/sessions` | 获取会话列表 |
| POST | `/api/agents/{agentId}/sessions` | 创建会话 |
| GET | `/api/agents/{agentId}/collaborators` | 获取协作者 |
| GET | `/api/agents/templates` | 获取模板列表 |
| POST | `/api/agents/{agentId}/api-key` | 生成API密钥 |
| GET | `/api/agents/{agentId}/config` | 获取配置 |
| PUT | `/api/agents/{agentId}/config` | 更新配置 |

**注意**: 员工ID格式为 `employee://digital/技术部/CI-CD流水线/023`，包含 `/` 字符。
推荐使用查询参数方式：`GET /api/agents?id={encoded_id}`

### AgentController (`/api`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 健康检查 |
| GET | `/api/status` | 获取系统状态 |
| POST | `/api/session/{sessionId}/start` | 启动会话 |
| POST | `/api/session/{sessionId}/end` | 结束会话 |
| GET | `/api/session/{sessionId}/status` | 获取会话状态 |

---

## 5. Agent 子资源模块

### AgentTaskController (`/api/agents/{agentId}/tasks`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agents/{agentId}/tasks` | 获取任务列表 |
| POST | `/api/agents/{agentId}/tasks` | 创建任务 |
| GET | `/api/agents/{agentId}/tasks/{taskId}` | 获取任务详情 |
| PATCH | `/api/agents/{agentId}/tasks/{taskId}` | 更新任务 |
| GET | `/api/agents/{agentId}/tasks/{taskId}/logs` | 获取任务日志 |
| POST | `/api/agents/{agentId}/tasks/{taskId}/trigger` | 触发任务 |

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

## 6. 部门模块

### DepartmentController (`/api/departments`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/departments` | 列出所有部门 |
| GET | `/api/departments/code/{code}` | 通过代码获取部门 |
| GET | `/api/departments/{id}/brain` | 获取部门大脑 |
| GET | `/api/departments/{id}/agents` | 获取部门数字员工 |
| GET | `/api/departments/{id}/members` | 获取部门成员 |

### DepartmentApiController (`/api/dept`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/dept/{department}/chat` | 部门聊天 |
| GET | `/api/dept/{department}/info` | 获取部门信息 |
| GET | `/api/dept/{department}/members` | 获取部门成员 |
| GET | `/api/dept/{department}/brains` | 获取部门大脑列表 |
| GET | `/api/dept/my` | 获取我的部门 |

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

## 7. 董事长模块

### ChairmanApiController (`/api/chairman`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/chairman/dashboard` | 获取仪表盘 |
| GET | `/api/chairman/employees` | 获取所有员工 |
| GET | `/api/chairman/employees/{employeeId}` | 获取员工详情 |
| POST | `/api/chairman/employees/{employeeId}/access-level` | 更新员工权限 |
| GET | `/api/chairman/departments` | 获取所有部门 |
| GET | `/api/chairman/system/status` | 获取系统状态 |

---

## 8. 员工模块

### EmployeeController (`/api/employees`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/employees` | 列出员工 |
| POST | `/api/employees` | 添加员工 |
| PUT | `/api/employees/{employeeId}` | 更新员工 |
| DELETE | `/api/employees/{employeeId}` | 删除员工 |
| GET | `/api/employees/{employeeId}` | 获取员工详情 |
| GET | `/api/employees/departments` | 获取部门列表 |

---

## 9. 技能模块

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

## 10. 神经元模块

### NeuronController (`/api/neurons`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/neurons` | 列出所有神经元 |
| GET | `/api/neurons/{id}` | 获取神经元详情 |
| GET | `/api/neurons/{id}/status` | 获取神经元状态 |
| GET | `/api/neurons/{id}/metrics` | 获取神经元指标 |

---

## 11. 企业模块

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

### 知识库端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/enterprise/knowledge-base/files` | 获取知识库文件列表 |
| POST | `/api/enterprise/knowledge-base/upload` | 上传知识库文件 |
| GET | `/api/enterprise/knowledge-base/content` | 读取知识库内容 |
| PUT | `/api/enterprise/knowledge-base/content` | 写入知识库内容 |
| DELETE | `/api/enterprise/knowledge-base/content` | 删除知识库内容 |

---

## 12. 审批模块

### ApprovalController (`/api/approvals`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/approvals` | 获取审批列表 |
| GET | `/api/approvals/pending` | 获取待审批列表 |
| GET | `/api/approvals/my-pending` | 获取我的待审批 |
| GET | `/api/approvals/my` | 获取我的审批 |
| POST | `/api/approvals` | 创建审批 |
| GET | `/api/approvals/{id}` | 获取审批详情 |
| GET | `/api/approvals/{id}/steps` | 获取审批步骤 |
| POST | `/api/approvals/{id}/steps/{stepId}/approve` | 批准审批步骤 |
| POST | `/api/approvals/{id}/steps/{stepId}/reject` | 拒绝审批步骤 |
| POST | `/api/approvals/{id}/approve` | 批准审批 |
| POST | `/api/approvals/{id}/reject` | 拒绝审批 |
| POST | `/api/approvals/{id}/return` | 退回审批 |
| POST | `/api/approvals/{id}/cancel` | 取消审批 |
| GET | `/api/approvals/{id}/history` | 获取审批历史 |
| GET | `/api/approvals/workflows` | 列出工作流 |
| GET | `/api/approvals/workflows/{id}` | 获取工作流详情 |
| POST | `/api/approvals/workflows` | 创建工作流 |

---

## 13. 任务模块

### TaskController (`/api/tasks`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tasks` | 获取任务列表 |
| POST | `/api/tasks` | 创建任务 |
| GET | `/api/tasks/{taskId}` | 获取任务详情 |
| PUT | `/api/tasks/{taskId}` | 更新任务 |
| DELETE | `/api/tasks/{taskId}` | 删除任务 |
| POST | `/api/tasks/{taskId}/checkout` | 检出任务 |
| POST | `/api/tasks/{taskId}/complete` | 完成任务 |
| POST | `/api/tasks/{taskId}/release` | 释放任务 |
| POST | `/api/tasks/{taskId}/reassign` | 重新分配任务 |
| GET | `/api/tasks/statistics` | 获取统计 |
| GET | `/api/tasks/pending` | 获取待处理任务 |
| GET | `/api/tasks/employee/{employeeId}` | 获取员工任务 |

---

## 14. 项目模块

### ProjectController (`/api/projects`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/projects` | 获取项目列表 |
| POST | `/api/projects` | 创建项目 |
| GET | `/api/projects/{id}` | 获取项目详情 |
| PUT | `/api/projects/{id}` | 更新项目 |
| DELETE | `/api/projects/{id}` | 删除项目 |
| POST | `/api/projects/{id}/start` | 启动项目 |
| POST | `/api/projects/{id}/complete` | 完成项目 |
| POST | `/api/projects/{id}/hold` | 暂停项目 |
| POST | `/api/projects/{id}/phases/{phase}/advance` | 推进阶段 |
| GET | `/api/projects/{id}/progress` | 获取进度 |
| PUT | `/api/projects/{id}/phases/{phase}/progress` | 设置阶段进度 |
| GET | `/api/projects/statistics` | 获取统计 |

### 项目任务子资源

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/projects/{id}/tasks` | 获取项目任务列表 |
| POST | `/api/projects/{id}/tasks` | 创建项目任务 |
| PUT | `/api/projects/{id}/tasks/{taskId}` | 更新项目任务 |
| DELETE | `/api/projects/{id}/tasks/{taskId}` | 删除项目任务 |

---

## 15. 广场模块

### PlazaController (`/api/plaza`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/plaza/posts` | 获取帖子列表 |
| GET | `/api/plaza/stats` | 获取统计信息 |
| POST | `/api/plaza/posts` | 创建帖子 |
| POST | `/api/plaza/posts/{postId}/like` | 点赞 |

---

## 16. 消息模块

### MessageController (`/api/messages`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/messages/inbox` | 获取收件箱 |
| GET | `/api/messages/unread-count` | 获取未读数 |
| PUT | `/api/messages/{messageId}/read` | 标记已读 |
| PUT | `/api/messages/read-all` | 全部标记已读 |

---

## 17. 知识库模块

### KnowledgeController (`/api/knowledge`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/knowledge` | 获取知识库列表 |
| POST | `/api/knowledge` | 创建知识条目 |
| GET | `/api/knowledge/{id}` | 获取知识详情 |
| PUT | `/api/knowledge/{id}` | 更新知识条目 |
| DELETE | `/api/knowledge/{id}` | 删除知识条目 |
| POST | `/api/knowledge/{id}/favorite` | 收藏知识 |
| DELETE | `/api/knowledge/{id}/favorite` | 取消收藏 |
| GET | `/api/knowledge/favorites` | 获取收藏列表 |
| GET | `/api/knowledge/stats` | 获取统计 |
| GET | `/api/knowledge/search` | 搜索知识 |
| GET | `/api/knowledge/categories` | 获取分类 |
| GET | `/api/knowledge/category/{category}` | 获取分类知识 |

---

## 18. 干预模块

### InterventionController (`/api/intervention`, `/api/interventions`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/interventions` | 获取干预列表 |
| POST | `/api/interventions` | 创建干预 |
| GET | `/api/interventions/{id}` | 获取干预详情 |
| POST | `/api/interventions/{id}/respond` | 响应干预 |
| POST | `/api/interventions/{id}/escalate` | 升级干预 |
| GET | `/api/interventions/statistics` | 获取统计 |
| POST | `/api/interventions/rules` | 注册规则 |
| DELETE | `/api/interventions/rules/{ruleId}` | 注销规则 |
| GET | `/api/interventions/rules` | 获取适用规则 |
| POST | `/api/intervention/evaluate` | 评估操作 |
| GET | `/api/intervention/pending` | 获取待处理决策 |

---

## 19. 主动服务模块

### ProactiveController (`/api/proactive`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/proactive/predictions` | 获取预测列表 |
| GET | `/api/proactive/digest` | 获取每日摘要 |
| GET | `/api/proactive/habits` | 获取习惯 |
| POST | `/api/proactive/habits` | 创建习惯 |
| PUT | `/api/proactive/habits/{id}` | 更新习惯 |
| DELETE | `/api/proactive/habits/{id}` | 删除习惯 |
| POST | `/api/proactive/habits/{habitId}/checkin` | 习惯打卡 |
| GET | `/api/proactive/notifications` | 获取通知 |
| POST | `/api/proactive/notifications/{id}/read` | 标记通知已读 |
| POST | `/api/proactive/notifications/read-all` | 全部标记已读 |
| GET | `/api/proactive/meeting-notes` | 获取会议记录 |
| GET | `/api/proactive/meeting-notes/{id}` | 获取会议记录详情 |
| GET | `/api/proactive/analytics` | 获取分析数据 |
| GET | `/api/proactive/suggestions` | 获取建议 |

---

## 20. 接待模块

### ReceptionController (`/api/reception`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/reception/status` | 获取接待状态 |
| POST | `/api/reception/chat` | 聊天 |
| POST | `/api/reception/chat/stream` | 流式聊天 |
| GET | `/api/reception/visitors` | 获取访客列表 |
| POST | `/api/reception/check-in` | 访客登记 |

---

## 21. 办公模块

### OfficeController (`/api/office`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/office` | 获取办公室列表 |
| POST | `/api/office` | 创建办公室 |
| GET | `/api/office/status` | 获取办公状态 |
| GET | `/api/office/agents` | 获取智能体 |
| GET | `/api/office/agents/{id}` | 获取智能体详情 |
| POST | `/api/office/agent/state` | 更新智能体状态 |
| GET | `/api/office/areas` | 获取区域 |
| GET | `/api/office/department/{department}` | 获取部门状态 |
| GET | `/api/office/yesterday-memo` | 获取昨日备忘 |

---

## 22. 进化模块

### EvolutionAdminController (`/api/admin`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/evolution/status` | 获取进化状态 |
| GET | `/api/admin/evolution/results` | 获取进化结果 |
| GET | `/api/admin/evolution/results/{resultId}` | 获取具体结果 |
| POST | `/api/admin/evolution/trigger` | 触发进化 |
| POST | `/api/admin/evolution/extract-signals` | 提取信号 |
| GET | `/api/admin/skills` | 列出技能 |
| GET | `/api/admin/skills/{name}` | 获取技能 |
| POST | `/api/admin/skills/reload` | 重新加载技能 |
| POST | `/api/admin/skills/generate` | 生成技能 |
| POST | `/api/admin/skills/{skillName}/install` | 安装技能 |
| DELETE | `/api/admin/skills/{skillName}` | 卸载技能 |
| POST | `/api/admin/skills/{skillName}/bind/{neuronId}` | 绑定技能到神经元 |
| DELETE | `/api/admin/skills/{skillName}/bind/{neuronId}` | 解绑技能 |
| GET | `/api/admin/bindings` | 获取绑定 |
| GET | `/api/admin/hotreload/status` | 获取热重载状态 |
| POST | `/api/admin/hotreload/trigger` | 触发热重载 |

---

## 23. 其他模块

### MiscController (`/api`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/version` | 获取版本信息 |
| GET | `/api/notifications/unread-count` | 获取未读通知数 |

---

## WebSocket 端点

| 路径 | 说明 |
|------|------|
| `/ws/agent` | 智能体对话 |
| `/ws/dept/{dept}` | 部门群聊 |
| `/ws/chairman` | 董事长频道 |
| `/ws/public` | 访客对话 |

连接格式：
```
ws://localhost:8382/ws/agent?token={authToken}&agentId={agentId}
```

---

## 前端 API 调用注意事项

1. **API 路径不要带末尾斜杠**：`/agents` 而非 `/agents/`
2. **员工 ID 包含特殊字符**：使用查询参数方式 `?id={encoded_id}`
3. **响应数据在 `data` 字段**：`{ success: true, data: T }`
4. **字段命名**：后端使用 camelCase，部分接口使用 @JsonProperty 转换为 snake_case
5. **API_BASE = '/api'**：前端请求会自动添加 `/api` 前缀

---

## 更新记录

### 2026-04-08 更新

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
| 技能管理 | `/api/skills` | 技能CRUD、ClawHub集成 |
| 神经元管理 | `/api/neurons` | 神经元状态/指标 |
| Agent任务 | `/api/agents/{id}/tasks` | 任务管理 |
| Agent定时任务 | `/api/agents/{id}/schedules` | CRON定时任务 |
| Agent触发器 | `/api/agents/{id}/triggers` | 事件触发器 |
| Agent频道 | `/api/agents/{id}/channel` | 频道配置 |
| Agent文件 | `/api/agents/{id}/files` | 文件管理 |
| 知识库 | `/api/enterprise/knowledge-base` | 企业知识库 |
| 访客管理 | `/api/reception/visitors` | 前台访客 |
| 办公室 | `/api/office` | 办公室管理 |
| 项目任务 | `/api/projects/{id}/tasks` | 项目任务子资源 |
| 主动预测 | `/api/proactive/predictions` | AI预测 |
| 进化状态 | `/api/admin/evolution/status` | 系统进化状态 |
| 公司管理 | `/api/tenants/admin/companies` | 租户管理 |
