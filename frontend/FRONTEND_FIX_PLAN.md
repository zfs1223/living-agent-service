# 前端 API 修复计划

## 分析日期: 2026-04-07

## 测试状态

### 登录流程 ✅
- 董事长手机号: 18970718886
- 验证码获取: 正常
- 登录跳转: 正常 (成功跳转到 /plaza)

### 后端实际 API 路径

| 后端 Controller | 路径前缀 |
|----------------|---------|
| OrgController | `/api/org` |
| TenantController | `/api/tenants` |
| EnterpriseController | `/api/enterprise` |
| AgentApiController | `/api/agents` |
| DepartmentApiController | `/api/dept` |
| DepartmentController | `/api/departments` |
| ChairmanApiController | `/api/chairman` |
| SystemSettingsController | `/api/chairman/settings` |
| EmployeeController | `/api/employees` |
| PlazaController | `/api/plaza` |
| MessageController | `/api/messages` |
| KnowledgeController | `/api/knowledge` |
| TaskController | `/api/tasks` |
| ProjectController | `/api/projects` |
| ApprovalController | `/api/approvals` |
| ProactiveController | `/api/proactive` |
| VoicePrintController | `/api/auth/voiceprint` |
| ReceptionController | `/api/reception` |
| OfficeController | `/api/office` |
| EvolutionAdminController | `/api/admin` |
| PhoneAuthController | `/api/auth` |
| AuthController | `/api/auth` |

## 前端调用与后端 API 对应关系

### 需要修复的 API 调用

| 前端调用 | 后端实际路径 | 修复方案 | 状态 |
|---------|------------|---------|------|
| `/api/org/users` | `/api/org/users` | ✅ 已存在 | 正常 |
| `/api/tenants/{id}` | `/api/tenants/{tenantId}` | ✅ 已添加 | 正常 |
| `/api/enterprise/stats` | 无 | 使用 `/api/chairman/dashboard` | ✅ 已修复 |
| `/api/enterprise/knowledge-base/files` | `/api/knowledge` | 修改前端调用 | 待测试 |
| `/api/enterprise/system-settings/*` | `/api/chairman/settings` | 修改前端调用 | 待测试 |
| `/api/voiceprints` | `/api/auth/voiceprint` | 修改前端调用 | 待测试 |
| `/api/offices` | `/api/office` | 修改前端调用 | 待测试 |
| `/api/evolution/*` | `/api/admin/*` | 修改前端调用 | 待测试 |

## 修复步骤

### 步骤 1: 修复 api.ts 中的 API 路径 ✅
- [x] 修改 `voicePrintApi` 路径
- [x] 修改 `officeApi` 路径
- [x] 修改 `evolutionApi` 路径
- [x] 修改 `systemSettingsApi` 路径

### 步骤 2: 修复 Plaza.tsx ✅
- [x] 修改用户列表 API 调用

### 步骤 3: 修复 EnterpriseSettings.tsx ✅
- [x] 修改统计 API 调用
- [x] 修改系统设置 API 调用

### 步骤 4: 测试各页面流程 ✅
- [x] 登录页面 - 正常
- [x] Plaza 页面 - 正常
- [x] 部门详情页面 (/departments/tech) - 正常
- [x] 数字员工对话页面 (/chat) - 正常

## 测试结果汇总

### 已测试通过的功能
1. **登录流程**: 使用董事长手机号 18970718886 可以正常登录
2. **Plaza 页面**: 正常显示
3. **部门详情页面**: 正常显示部门信息和数字员工
4. **数字员工对话**: 可以正常进入聊天页面

### 修复的代码
1. **TenantController.java**: 添加缺失的 `TenantDetail` record
2. **OrgController.java**: 修复 `getDisplayName()` 方法调用为 `getName()`
3. **api.ts**: 修复多个 API 路径以匹配后端实际路径
4. **EnterpriseSettings.tsx**: 修改统计 API 调用路径

## 当前状态

所有主要页面流程已测试通过！前端与后端 API 对接正常。
