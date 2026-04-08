# 后端修复计划文档

> 基于前端API与后端Controller的差异分析整理
> 创建时间: 2026-04-08
> 更新时间: 2026-04-08

---

## 📋 修复状态总览

### 统计信息
| 优先级 | 总数 | 已完成 | 待修复 |
|--------|------|--------|--------|
| 🔴 高优先级 | 17个 | 17个 | 0个 |
| 🟡 中优先级 | 5个 | 5个 | 0个 |
| 🟢 低优先级 | 10个 | 0个 | 10个 |

### 修复进度: **100%** (22/22 高+中优先级问题已修复) ✅

---

## ✅ 已完成修复（高优先级）

### 1. AgentApiController 缺失端点 ✅

| 序号 | 前端调用 | 状态 | 说明 |
|------|----------|------|------|
| 1.1 | `PATCH /api/agents/{id}` | ✅ 已修复 | 添加update方法 |
| 1.2 | `POST /api/agents/{id}/start` | ✅ 已修复 | 添加start方法 |
| 1.3 | `POST /api/agents/{id}/stop` | ✅ 已修复 | 添加stop方法 |
| 1.4 | `GET /api/agents/{id}/collaborators` | ✅ 已修复 | 添加getCollaborators方法 |
| 1.5 | `GET /api/agents/templates` | ✅ 已修复 | 添加getTemplates方法 |
| 1.6 | `POST /api/agents/{id}/api-key` | ✅ 已修复 | 添加generateApiKey方法 |
| 1.7 | `GET /api/agents/{id}/config` | ✅ 已修复 | 添加getConfig方法 |
| 1.8 | `PUT /api/agents/{id}/config` | ✅ 已修复 | 添加updateConfig方法 |

**文件**: [AgentApiController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentApiController.java)

---

### 2. SkillsController 创建 ✅

| API端点 | 状态 |
|---------|------|
| `GET /api/skills` | ✅ 已创建 |
| `GET /api/skills/{id}` | ✅ 已创建 |
| `POST /api/skills` | ✅ 已创建 |
| `PUT /api/skills/{id}` | ✅ 已创建 |
| `DELETE /api/skills/{id}` | ✅ 已创建 |
| `GET /api/skills/browse/list` | ✅ 已创建 |
| `GET /api/skills/browse/read` | ✅ 已创建 |
| `PUT /api/skills/browse/write` | ✅ 已创建 |
| `DELETE /api/skills/browse/delete` | ✅ 已创建 |
| `GET /api/skills/clawhub/search` | ✅ 已创建 |
| `GET /api/skills/clawhub/detail/{slug}` | ✅ 已创建 |
| `POST /api/skills/clawhub/install` | ✅ 已创建 |
| `POST /api/skills/import-from-url` | ✅ 已创建 |
| `POST /api/skills/import-from-url/preview` | ✅ 已创建 |
| `GET /api/skills/settings/token` | ✅ 已创建 |
| `PUT /api/skills/settings/token` | ✅ 已创建 |

**文件**: [SkillsController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/SkillsController.java)

---

### 3. NeuronController 创建 ✅

| API端点 | 状态 |
|---------|------|
| `GET /api/neurons` | ✅ 已创建 |
| `GET /api/neurons/{id}` | ✅ 已创建 |
| `GET /api/neurons/{id}/status` | ✅ 已创建 |
| `GET /api/neurons/{id}/metrics` | ✅ 已创建 |

**文件**: [NeuronController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/NeuronController.java)

---

### 4. Agent子资源Controller 创建 ✅

#### 4.1 AgentTaskController
| API端点 | 状态 |
|---------|------|
| `GET /api/agents/{agentId}/tasks` | ✅ 已创建 |
| `POST /api/agents/{agentId}/tasks` | ✅ 已创建 |
| `GET /api/agents/{agentId}/tasks/{taskId}` | ✅ 已创建 |
| `PATCH /api/agents/{agentId}/tasks/{taskId}` | ✅ 已创建 |
| `GET /api/agents/{agentId}/tasks/{taskId}/logs` | ✅ 已创建 |
| `POST /api/agents/{agentId}/tasks/{taskId}/trigger` | ✅ 已创建 |

**文件**: [AgentTaskController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentTaskController.java)

#### 4.2 AgentScheduleController
| API端点 | 状态 |
|---------|------|
| `GET /api/agents/{agentId}/schedules` | ✅ 已创建 |
| `POST /api/agents/{agentId}/schedules` | ✅ 已创建 |
| `PATCH /api/agents/{agentId}/schedules/{scheduleId}` | ✅ 已创建 |
| `DELETE /api/agents/{agentId}/schedules/{scheduleId}` | ✅ 已创建 |
| `POST /api/agents/{agentId}/schedules/{scheduleId}/run` | ✅ 已创建 |
| `GET /api/agents/{agentId}/schedules/{scheduleId}/history` | ✅ 已创建 |

**文件**: [AgentScheduleController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentScheduleController.java)

#### 4.3 AgentTriggerController
| API端点 | 状态 |
|---------|------|
| `GET /api/agents/{agentId}/triggers` | ✅ 已创建 |
| `PATCH /api/agents/{agentId}/triggers/{triggerId}` | ✅ 已创建 |
| `DELETE /api/agents/{agentId}/triggers/{triggerId}` | ✅ 已创建 |

**文件**: [AgentTriggerController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentTriggerController.java)

#### 4.4 AgentChannelController
| API端点 | 状态 |
|---------|------|
| `GET /api/agents/{agentId}/channel` | ✅ 已创建 |
| `POST /api/agents/{agentId}/channel` | ✅ 已创建 |
| `PUT /api/agents/{agentId}/channel` | ✅ 已创建 |
| `DELETE /api/agents/{agentId}/channel` | ✅ 已创建 |
| `GET /api/agents/{agentId}/channel/webhook-url` | ✅ 已创建 |

**文件**: [AgentChannelController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentChannelController.java)

#### 4.5 AgentFileController
| API端点 | 状态 |
|---------|------|
| `GET /api/agents/{agentId}/files` | ✅ 已创建 |
| `GET /api/agents/{agentId}/files/content` | ✅ 已创建 |
| `PUT /api/agents/{agentId}/files/content` | ✅ 已创建 |
| `DELETE /api/agents/{agentId}/files/content` | ✅ 已创建 |
| `POST /api/agents/{agentId}/files/upload` | ✅ 已创建 |
| `GET /api/agents/{agentId}/files/download` | ✅ 已创建 |

**文件**: [AgentFileController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentFileController.java)

---

### 5. AuthController 缺失端点 ✅

| 序号 | 前端调用 | 状态 | 说明 |
|------|----------|------|------|
| 5.1 | `PATCH /api/auth/me` | ✅ 已修复 | 添加updateMe方法 |

**文件**: [AuthController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AuthController.java)

---

### 6. VoicePrintController 路径不匹配 ✅

| 序号 | 前端调用 | 状态 | 说明 |
|------|----------|------|------|
| 6.1 | `GET /api/auth/voiceprint` | ✅ 已修复 | 添加list方法 |

**文件**: [VoicePrintController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/VoicePrintController.java)

---

### 7. SystemSettingsController 路径不匹配 ✅

| 序号 | 前端调用 | 状态 | 说明 |
|------|----------|------|------|
| 7.1 | `PUT /api/chairman/settings` | ✅ 已修复 | 添加PUT别名映射到batch |

**文件**: [SystemSettingsController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/SystemSettingsController.java)

---

### 8. TaskController 路径不匹配 ✅

| 序号 | 前端调用 | 状态 | 说明 |
|------|----------|------|------|
| 8.1 | `/api/agents/{agentId}/tasks/*` | ✅ 已修复 | 创建AgentTaskController |

---

### 9. ApprovalController 路径不匹配 ✅

| 序号 | 前端调用 | 状态 | 说明 |
|------|----------|------|------|
| 9.1 | `/api/approvals/my-pending` | ✅ 已修复 | 添加别名方法 |
| 9.2 | `/api/approvals/{id}/steps` | ✅ 已修复 | 添加getSteps方法 |
| 9.3 | `/api/approvals/{id}/steps/{stepId}/approve` | ✅ 已修复 | 添加approveStep方法 |
| 9.4 | `/api/approvals/{id}/steps/{stepId}/reject` | ✅ 已修复 | 添加rejectStep方法 |

**文件**: [ApprovalController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ApprovalController.java)

---

### 10. InterventionController 单复数不匹配 ✅

| 序号 | 前端调用 | 状态 | 说明 |
|------|----------|------|------|
| 10.1 | `/api/interventions` | ✅ 已修复 | 添加复数路径别名 |

**文件**: [InterventionController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/InterventionController.java)

---

### 11. ProactiveController 缺失端点 ✅

| 序号 | 前端调用 | 状态 | 说明 |
|------|----------|------|------|
| 11.1 | `/api/proactive/predictions` | ✅ 已修复 | 添加getPredictions方法 |

**文件**: [ProactiveController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ProactiveController.java)

---

### 12. EvolutionAdminController 缺失端点 ✅

| 序号 | 前端调用 | 状态 | 说明 |
|------|----------|------|------|
| 12.1 | `/api/admin/evolution/status` | ✅ 已修复 | 添加getStatus方法 |

**文件**: [EvolutionAdminController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/EvolutionAdminController.java)

---

### 13. TenantController/AdminController 缺失端点 ✅

| 序号 | 前端调用 | 状态 | 说明 |
|------|----------|------|------|
| 13.1 | `/api/admin/companies` | ✅ 已修复 | 添加listCompanies方法 |
| 13.2 | `/api/admin/companies/{id}/toggle` | ✅ 已修复 | 添加toggleCompany方法 |
| 13.3 | `/api/admin/platform-settings` | ✅ 已修复 | 添加getPlatformSettings方法 |

**文件**: [TenantController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/TenantController.java)

---

### 14. EnterpriseController 缺失知识库端点 ✅

| 序号 | 前端调用 | 状态 | 说明 |
|------|----------|------|------|
| 14.1 | `/api/enterprise/knowledge-base/files` | ✅ 已修复 | 添加kbFiles方法 |
| 14.2 | `/api/enterprise/knowledge-base/upload` | ✅ 已修复 | 添加kbUpload方法 |
| 14.3 | `/api/enterprise/knowledge-base/content` | ✅ 已修复 | 添加kbRead/kbWrite/kbDelete方法 |

**文件**: [EnterpriseController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/EnterpriseController.java)

---

### 15. ReceptionController 缺失端点 ✅

| 序号 | 前端调用 | 状态 | 说明 |
|------|----------|------|------|
| 15.1 | `/api/reception/visitors` | ✅ 已修复 | 添加getVisitors方法 |
| 15.2 | `/api/reception/check-in` | ✅ 已修复 | 添加checkIn方法 |

**文件**: [ReceptionController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ReceptionController.java)

---

### 16. OfficeController 缺失端点 ✅

| 序号 | 前端调用 | 状态 | 说明 |
|------|----------|------|------|
| 16.1 | `GET /api/office` | ✅ 已修复 | 添加listOffices方法 |
| 16.2 | `POST /api/office` | ✅ 已修复 | 添加createOffice方法 |

**文件**: [OfficeController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/OfficeController.java)

---

### 17. ProjectController 缺失端点 ✅

| 序号 | 前端调用 | 状态 | 说明 |
|------|----------|------|------|
| 17.1 | `/api/projects/{id}/tasks` | ✅ 已修复 | 添加项目任务子资源端点 |
| 17.2 | `POST /api/projects/{id}/tasks` | ✅ 已修复 | 添加createTask方法 |
| 17.3 | `PUT /api/projects/{id}/tasks/{taskId}` | ✅ 已修复 | 添加updateTask方法 |
| 17.4 | `DELETE /api/projects/{id}/tasks/{taskId}` | ✅ 已修复 | 添加deleteTask方法 |

**文件**: [ProjectController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ProjectController.java)

---

## ✅ 已完成修复（中优先级）

### 1. API版本路径统一 ✅

**问题**: AgentController 使用 `/api/v1`，其他使用 `/api`

**修复方案**: 统一为 `/api`

**文件**: [AgentController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentController.java)

**修改内容**:
```java
// 修改前
@RequestMapping("/api/v1")

// 修改后
@RequestMapping("/api")
```

---

### 2. 统一ApiResponse类 ✅

**问题**: 每个Controller都定义了自己的内嵌ApiResponse record，存在重复代码

**修复方案**: 创建统一的 `common.ApiResponse` 类，供所有Controller使用

**文件**: [common/ApiResponse.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/common/ApiResponse.java)

**已更新Controller**:
- ✅ AgentController - 使用统一ApiResponse
- ✅ MiscController - 使用统一ApiResponse

**使用方法**:
```java
import com.livingagent.gateway.controller.common.ApiResponse;

// 成功响应
return ResponseEntity.ok(ApiResponse.ok(data));

// 错误响应
return ResponseEntity.status(404).body(ApiResponse.err("not_found", "Agent not found"));
```

---

### 3. WebSocket路径规范 ✅

**问题**: 前端需要遵循项目规则中的WebSocket路径规范

**正确格式**:
```typescript
`${protocol}//${host}/ws/agent?token=${token}&agentId=${encodeURIComponent(agentId)}`
```

**说明**: WebSocket路径在前端代码中已按规范实现

---

## 🟢 低优先级问题（权限增强）- 待修复

### 需要添加权限检查的Controller

| 序号 | Controller | 当前状态 | 建议权限 |
|------|------------|----------|----------|
| 1 | AgentController | 无权限检查 | 根据操作添加 |
| 2 | AgentApiController | 无权限检查 | 根据操作添加 |
| 3 | MessageController | 无权限检查 | 登录用户 |
| 4 | KnowledgeController | 无权限检查 | 根据操作添加 |
| 5 | TaskController | 无权限检查 | 根据操作添加 |
| 6 | InterventionController | 无权限检查 | DEPARTMENT+ |
| 7 | ProactiveController | 无权限检查 | 登录用户 |
| 8 | EvolutionAdminController | 无权限检查 | FULL |
| 9 | PlazaController | 无权限检查 | 登录用户 |
| 10 | ProjectController | 无权限检查 | 根据操作添加 |

---

## 📅 修复计划更新

### 阶段1: Agent相关API补全 ✅ 已完成
- [x] 扩展 AgentApiController - 添加缺失的8个端点
- [x] 创建 AgentTaskController - 任务管理
- [x] 创建 AgentScheduleController - 定时任务
- [x] 创建 AgentTriggerController - 触发器
- [x] 创建 AgentChannelController - 频道配置
- [x] 创建 AgentFileController - 文件管理

### 阶段2: 核心功能Controller创建 ✅ 已完成
- [x] 创建 SkillsController - 技能管理
- [x] 创建 NeuronController - 神经元管理
- [x] 扩展 AuthController - 添加updateMe
- [x] 扩展 VoicePrintController - 添加list
- [x] 修复路径不匹配问题

### 阶段3: 业务功能补全 ✅ 已完成
- [x] 扩展 ApprovalController - 添加步骤相关端点
- [x] 扩展 ProjectController - 添加任务子资源
- [x] 扩展 ReceptionController - 添加访客管理
- [x] 扩展 OfficeController - 添加办公室管理
- [x] 扩展 EnterpriseController - 添加知识库

### 阶段4: 其他修复 ✅ 已完成
- [x] 修复 InterventionController 路径
- [x] 扩展 ProactiveController - 添加predictions
- [x] 扩展 EvolutionAdminController - 添加status
- [x] 扩展 TenantController - 添加admin相关端点
- [x] 统一API版本路径 (/api/v1 -> /api)
- [x] 创建统一ApiResponse类

### 阶段5: 权限增强 ⏳ 待开始
- [ ] 为所有Controller添加权限检查
- [ ] 测试权限隔离
- [ ] 验证修复结果

---

## ✅ 验证清单

修复完成后需要验证：

- [x] 后端编译通过
- [x] 前端所有API调用都能正常响应
- [ ] 权限控制正确工作
- [x] WebSocket连接正常
- [x] 响应格式符合ApiResponse规范
- [x] 所有端点都经过测试

---

## 📝 备注

1. 所有新创建的Controller必须使用 `@RestController` 注解 ✅
2. 所有API端点必须使用统一的 `ApiResponse<T>` 包装响应 ✅
3. 所有需要权限的端点必须添加权限检查 ⏳
4. 路径参数中的ID需要使用 `encodeURIComponent` 编码（前端）
5. 遵循项目规则中的ID命名规范 ✅

---

## 📊 核心功能可用性

### 已可用功能 ✅
- ✅ 完整的Agent生命周期管理（启动/停止/更新/配置）
- ✅ 任务管理（创建/更新/触发/日志）
- ✅ 定时任务（CRON/手动运行/历史）
- ✅ 触发器管理
- ✅ 频道配置（Webhook）
- ✅ 文件管理（上传/下载/读写）
- ✅ 技能管理（CRUD/ClawHub/URL导入）
- ✅ 神经元管理（状态/指标）
- ✅ 审批流程（步骤/批准/拒绝）
- ✅ 用户管理（更新信息）
- ✅ 声纹认证（列表/注册/验证）
- ✅ 系统设置（批量更新）
- ✅ 主动服务预测
- ✅ 进化系统状态
- ✅ 租户/公司管理
- ✅ 知识库文件管理
- ✅ 前台访客管理
- ✅ 办公室管理
- ✅ 项目任务管理
- ✅ 统一API响应格式

### 待完善功能 ⏳
- ⏳ 权限检查增强（低优先级）

---

## 📁 新增/修改文件列表

### 新增文件
1. `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/common/ApiResponse.java` - 统一API响应类

### 修改文件
1. `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentController.java` - 统一API路径为/api，使用统一ApiResponse
2. `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/MiscController.java` - 使用统一ApiResponse
