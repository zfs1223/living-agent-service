# 公司设置模块完善方案

> 基于 `CODE_STRUCTURE_AND_FILE_GUIDE.md` 后端实际能力与前端 `EnterpriseSettings.tsx` 现状分析
>
> 最后更新：2026-06-22

## 一、现状总览

公司设置页面（`EnterpriseSettings.tsx`）当前有 12 个 Tab：

| Tab | Key | 状态 | 说明 |
|-----|-----|------|------|
| 公司信息 | `info` | **基本完成** | 公司名称、时区、简介、主题色、广播、危险区域 |
| 模型池 | `llm` | **已完成** ✅ | 独立组件 `ModelPoolProviders` |
| 大脑配置 | `brain` | **已完成** ✅ | 独立组件 `BrainConfig` |
| 知识库 | `knowledge` | **已完成** ✅ | 独立组件 `KnowledgeTab`（知识条目CRUD+搜索+分类+统计+收藏+文件浏览+治理） |
| 工具 | `tools` | **基本完成** | 全局工具列表、MCP服务器添加、Agent安装工具、分类配置 |
| 技能管理 | `skills` | **基本完成** | 技能注册表、ClawHub搜索/安装、URL导入、GitHub Token、子Tab(文件/绑定/统计)、热更新 |
| 用户 | `users` | **基本完成** | 独立组件 `UserManagement`，配额编辑+用户详情弹窗+权限级别管理 |
| 组织管理 | `org` | **已完成** ✅ | 独立组件 `OrgTab`（SSO+身份提供商+组织同步+部门树+成员） |
| 邀请码 | `invites` | **已完成** ✅ | 独立组件 `InvitationCodes`，完整CRUD |
| 配额 | `quotas` | **基本完成** | 默认用户配额、Agent限制、触发器限制 |
| 审批 | `approvals` | **基本完成** | 独立组件 `ApprovalsTab`，分Tab(含我待审批)+筛选+详情弹窗+步骤时间线+评论+取消操作 |
| 审计日志 | `audit` | **基本完成** | 独立组件 `AuditLogTab`，搜索+分页+导出+详情展开 |

Tab顺序：`info → llm → brain → knowledge → tools → skills → users → org → invites → quotas → approvals → audit`

---

## 二、各模块详细分析

### 1. 公司信息（info）— 完善度：98% ✅

**当前实现：**
- 公司Logo上传 `CompanyLogoUploader`（拖拽/点击上传，2MB限制，PNG/JPG/SVG，本地预览fallback）
- 公司名称编辑器 `CompanyNameEditor`
- 时区编辑器 `CompanyTimezoneEditor`
- 公司简介（Markdown编辑/预览切换，保存到 `/enterprise/system-settings/company_intro/{tenantId}`）
- 主题色选择器 `ThemeColorPicker`
- 广播消息 `BroadcastSection`
- 危险区域（删除公司）

**后端已有能力：**
- `TenantController` — 租户CRUD
- `SystemConfigService` — 系统配置读写
- `EnterpriseController` — 企业设置

**已完成的改进：**
- [x] 将企业知识库文件浏览迁移到独立"知识库"Tab
- [x] 公司简介Markdown预览（编辑/预览切换按钮，简易Markdown渲染）
- [x] 公司Logo上传UI（`CompanyLogoUploader`组件，后端端点待实现时使用本地预览fallback）

**需完善：**
- [ ] Logo上传后端端点实现（前端UI已就绪，调用`POST /api/enterprise/logo`，方案见第八节）

---

### 2. 模型池（llm）— 完善度：100% ✅

独立组件 `ModelPoolProviders.tsx`，完整实现。

---

### 3. 大脑配置（brain）— 完善度：100% ✅

独立组件 `BrainConfig.tsx`，完整实现。

---

### 4. 知识库（knowledge）— 完善度：100% ✅（新增）

**已实现：**
- [x] 知识条目列表（搜索、分类筛选）
- [x] 知识条目创建/编辑表单（标题、内容、分类、类型、范围、重要性）
- [x] 知识条目删除（确认弹窗）
- [x] 全文搜索（调用 `/knowledge/search`）
- [x] 分类列表展示 + 按分类浏览
- [x] 知识统计（总量、生效数、分类数）
- [x] 收藏/取消收藏 + 收藏列表
- [x] 知识治理子Tab（统计概览、范围分布、收藏管理）
- [x] 企业知识库文件浏览（从info Tab迁移，`EnterpriseKBBrowser`）
- [x] 类型定义 `KnowledgeEntry`/`KnowledgeScope`/`KnowledgeType`/`KnowledgeStatus`/`KnowledgeStats`
- [x] i18n 中英文翻译（约40个条目）

**已完成的改进：**
- [x] 知识条目状态流转操作（DRAFT→ACTIVE→DEPRECATED→ARCHIVED 按钮）
- [x] 知识晋升审核列表（L1→L2→L3）
- [x] 知识有效性标记（VERIFIED/OUTDATED/INVALID）
- [x] 搜索结果高亮展示（`highlightText`函数，`<mark>`标签高亮关键词）

**需完善：**
- 无

---

### 5. 工具（tools）— 完善度：90% ✅

**当前实现：**
- 全局工具列表（按分类分组，启用/禁用开关）
- MCP服务器添加（JSON配置/URL输入，连接测试，批量导入）
- Agent安装工具列表（查看、删除）
- 分类级配置（如 AgentBay）
- 单工具配置弹窗
- Jina API Key 管理
- Windows Automation Nodes
- 工具搜索/过滤功能
- 按部门筛选工具（调用 `toolApi.getByDepartment`）

**已完成的改进：**
- [x] 工具搜索/过滤功能
- [x] 按部门筛选工具（部门下拉框，调用 `toolApi.getByDepartment`）

**需完善：**

---

### 6. 技能管理（skills）— 完善度：95% ✅

**当前实现：**
- 技能文件浏览器（基于 `FileBrowser`，浏览/编辑/删除技能文件）
- ClawHub市场搜索与安装
- URL导入（GitHub URL预览+导入）
- GitHub Token / ClawHub API Key 管理
- 子Tab导航（文件/绑定关系/统计）
- 技能热更新按钮（🔄，调用 `evolutionExtendedApi.reloadSkills`）
- 技能计数badge（调用 `enterpriseSkillApi.getCounts`）
- 绑定关系列表（调用 `evolutionExtendedApi.listBindings`，含解绑按钮）
- 统计视图（按大脑分组展示技能数量）
- 热更新状态展示（调用 `evolutionExtendedApi.getHotreloadStatus`）

**后端已有能力：**
- `SkillsController` — 技能CRUD
- `SkillRegistry` / `SkillService` / `SkillBindingService` — 技能注册/绑定
- `SkillHotReloader` — 运行时热更新
- `enterpriseSkillApi.list/getByBrain/getCounts` — 企业技能查询
- `evolutionExtendedApi.listSkills/getSkill/reloadSkills/generateSkill/installSkill/uninstallSkill/bindSkill/unbindSkill/listBindings` — 技能管理扩展

**已完成的改进：**
- [x] 技能与大脑/神经元的绑定关系可视化（绑定子Tab）
- [x] 技能热更新操作（热更新按钮）
- [x] 技能按部门/大脑的统计视图（统计子Tab）
- [x] 技能计数badge
- [x] 技能作用域展示（绑定列表中显示global/evolved/personal标签）
- [x] 技能自动生成入口（✨按钮+生成弹窗，调用`evolutionExtendedApi.generateSkill`）

**需完善：**
- 无（`generateSkill`端点需联调确认，见风险提示第2条）

---

### 7. 邀请码（invites）— 完善度：99% ✅

**当前实现：**
- 批量创建邀请码（数量+最大使用次数）
- 邀请码列表（分页、搜索）
- 禁用/导出CSV
- 状态标签（Active/Exhausted/Disabled）
- 一键复制邀请码（📋按钮，`navigator.clipboard.writeText`）
- 使用详情按钮+弹窗（调用 `/invitation-codes/{code}/usages`，后端API待实现时显示空状态提示）

**已完成的改进：**
- [x] 邀请码一键复制功能（InvitationCodes.tsx:170-172）
- [x] 邀请码使用详情按钮+弹窗（`usageDetail`状态，调用`/invitation-codes/{code}/usages`）

**需完善：**
- [ ] 邀请码使用详情后端API实现（前端UI已就绪，方案见第八节P3）

---

### 8. 配额（quotas）— 完善度：85% ✅

**当前实现：**
- 默认用户配额（消息限制/周期、Agent数量/TTL/每日LLM调用）
- 系统限制（最小心跳间隔）
- 触发器限制（默认最大触发数、最小轮询间隔、最大Webhook频率）

**后端已有能力：**
- `GET/PATCH /enterprise/tenant-quotas` — 租户配额读写
- `CreditController` — 积分/余额接口
- `autonomous/incentive/*` — 激励/积分体系
- `CompensationPlanEntity/CompensationRecordEntity` — 薪酬计划/记录

**已完成的改进：**
- [x] 积分/信用余额展示（`CreditOverview` 组件）
- [x] 积分排行榜（`creditApi.getLeaderboard`）

**需完善：**
- [ ] 薪酬计划管理 — 后端完全无此API（`compensation/salary/pay`在API_REFERENCE.md中无匹配）
- [ ] 当前租户资源使用概览（已用/总量对比）
- [ ] 按用户维度的配额使用情况

---

### 9. 用户（users）— 完善度：95% ✅

**当前实现：**
- 用户列表（搜索、排序、分页）
- 单用户配额编辑（消息限制、Agent数量、TTL）
- 角色切换（`changingRoleUserId`）
- 用户详情弹窗（头像+基本信息网格+活跃状态+编辑入口+激活/停用按钮）
- 权限级别管理（CHAT_ONLY/LIMITED/DEPARTMENT/FULL，调用 `chairmanApi.updateEmployeeAccess`）
- 用户激活/停用操作（调用 `POST /enterprise/employees/{id}/activate|deactivate`，后端API待实现）

**已完成的改进：**
- [x] 用户详情弹窗（点击用户名查看完整信息）
- [x] 用户权限级别管理（详情弹窗中4级权限选择器，调用 `chairmanApi.updateEmployeeAccess`）
- [x] 用户激活/停用操作（详情弹窗Actions区域，左侧激活/停用按钮，右侧编辑/取消按钮）

**需完善：**
- [ ] 数字员工管理入口（固定员工/进化员工/人类员工，后端有完整体系，前端未在公司设置中整合）
- [ ] 用户激活/停用后端API实现（前端UI已就绪，方案见第八节P1）
- [ ] 用户部门归属编辑

---

### 10. 组织管理（org）— 完善度：90% ✅（Bug已修复）

**已完成的改进：**
- [x] **修复Bug**：将 `activeTab === 'org'` 的渲染内容改为 `<OrgTab tenant={currentTenant} />`
- [x] 部门新增操作（"+ 新增"按钮，调用 `POST /departments`）
- [x] 部门编辑操作（DeptTree节点编辑按钮，调用 `PUT /departments/{id}`）
- [x] 部门删除操作（DeptTree节点删除按钮，调用 `DELETE /departments/{id}`）

**OrgTab 已实现的功能：**
- SSO开关与域名配置
- 身份提供商管理（飞书/钉钉/企微/OAuth2）
- 组织架构同步（从OA系统同步部门和成员）
- 部门树浏览+新增/编辑/删除
- 成员搜索与列表

**需完善：**
- [ ] 部门CRUD后端API实现（前端UI已就绪，`DepartmentController`仅有GET查询，方案见第八节P2）
- [ ] 成员部门调动操作
- [ ] 组织架构可视化（树形图）

---

### 11. 审批（approvals）— 完善度：95% ✅

**已完成的改进：**
- [x] 审批详情弹窗（基本信息+审批步骤时间线+评论+通过/拒绝操作）
- [x] 审批类型筛选（leave/expense/purchase/contract/project/other）
- [x] 审批状态筛选（pending/approved/rejected/cancelled）
- [x] 审批评论功能（后端支持comment参数）
- [x] 全部/待审批/已处理/我待审批/我发起的 分Tab
- [x] 审批统计概览（待处理数、已通过数、已拒绝数）
- [x] 审批取消操作（调用 `approvalApi.cancel`，带确认弹窗）
- [x] 我发起的审批（调用 `approvalApi.getMyApprovals`，即 `/approvals/my`）

**需完善：**
- 无

---

### 12. 审计日志（audit）— 完善度：90% ✅

**已完成的改进：**
- [x] 日志搜索功能（按动作、Agent ID、详情内容搜索）
- [x] 日志详情展开/折叠（JSON格式化展示）
- [x] 日志导出功能（CSV/JSON）
- [x] 日志分页加载（每页50条）
- [x] 时间范围选择器（dateFrom/dateTo，带清除按钮）

**需完善：**
- [ ] 审计日志分类Tab（操作日志 / 合规日志 / 进化日志 / 工具调用日志）
- [ ] 合规报告生成 — 后端无公开API（仅有内部`StandardComplianceTraceService`，非REST端点）

---

## 三、实现进度

### P0 — 必须修复（已完成 ✅）

| 优先级 | 模块 | 任务 | 状态 |
|--------|------|------|------|
| P0 | 组织管理 | 修复org Tab渲染Bug | ✅ 已完成 |
| P0 | 知识库 | 新增独立知识库Tab | ✅ 已完成 |

### P1 — 重要完善（已完成 ✅）

| 优先级 | 模块 | 任务 | 状态 |
|--------|------|------|------|
| P1 | 审批 | 详情弹窗+筛选+分Tab | ✅ 已完成 |
| P1 | 审计日志 | 搜索+分页+导出+详情展开 | ✅ 已完成 |
| P1 | 用户 | 详情弹窗+信息展示+编辑入口 | ✅ 已完成 |
| P1 | 知识库 | 状态流转+晋升审核+有效性标记 | ✅ 已完成 |

### P2 — 体验优化

| 优先级 | 模块 | 任务 | 状态 |
|--------|------|------|------|
| P2 | 工具 | 搜索功能 | ✅ 已完成 |
| P2 | 技能 | 绑定关系+热更新+统计 | ✅ 已完成 |
| P2 | 配额 | 积分展示+资源使用概览 | ✅ 已完成 |
| P2 | 邀请码 | 一键复制 | ✅ 已完成 |
| P2 | 审批 | 我待审批+取消操作 | ✅ 已完成 |
| P2 | 用户 | 权限级别管理 | ✅ 已完成 |
| P2 | 公司信息 | Markdown预览 | ✅ 已完成 |
| P2 | 审计日志 | 时间范围选择器 | ✅ 已完成 |
| P2 | 工具 | 按部门筛选 | ✅ 已完成 |
| P2 | 技能 | 作用域展示 | ✅ 已完成 |
| P2 | 审批 | 我发起的审批 | ✅ 已完成 |
| P2 | 公司信息 | Logo上传 | ✅ 前端已完成，待后端实现 |
| P2 | 用户 | 激活/停用操作 | ✅ 前端已完成，待后端实现 |
| P2 | 组织管理 | 部门CRUD | ✅ 前端已完成，待后端实现 |
| P2 | 邀请码 | 使用详情 | ✅ 前端已完成，待后端实现 |
| P3 | 知识库 | 搜索结果高亮 | ✅ 已完成 |
| P3 | 技能管理 | 自动生成入口 | ✅ 已完成 |

---

## 四、前端API对照表

以下API已在 `src/services/api.ts` 中定义：

| API | 方法 | 使用状态 |
|-----|------|---------|
| `knowledgeApi.list/get/create/update/delete` | 知识条目CRUD | ✅ 已在KnowledgeTab使用 |
| `knowledgeExtendedApi.search/categories/getByCategory/getStats/getFavorites/addFavorite/removeFavorite` | 知识搜索/分类/统计/收藏 | ✅ 已在KnowledgeTab使用 |
| `enterpriseApi.kbFiles/kbUpload/kbRead/kbWrite/kbDelete` | 企业知识库文件操作 | ✅ 已在KnowledgeTab文件子Tab使用 |
| `approvalApi.list/get/getMyPending/getMyApprovals/getSteps/approve/reject/cancel` | 审批完整操作 | ✅ 已在ApprovalsTab使用 |
| `toolApi.list/getByDepartment` | 企业工具按部门查询 | ✅ 已在ToolsTab使用 |
| `enterpriseSkillApi.list/getByBrain/getCounts` | 企业技能统计 | ✅ 已在SkillsTab使用 |
| `creditApi.getBalance/getHistory/getStats/getLeaderboard` | 积分/信用 | ✅ 已在CreditOverview使用 |
| `evolutionExtendedApi.listSkills/bindSkill/unbindSkill/listBindings` | 技能绑定管理 | ✅ 已在SkillsTab使用 |
| `evolutionExtendedApi.reloadSkills/generateSkill` | 技能热更新/生成 | ✅ 均已在SkillsTab使用 |
| `chairmanApi.getEmployees/getEmployee/updateEmployeeAccess` | 员工管理 | ✅ updateEmployeeAccess已在UserManagement使用 |

---

## 五、已完成的代码变更清单

### 变更1：修复组织管理Bug
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：将 `activeTab === 'org'` 渲染内容从审批列表改为 `<OrgTab tenant={currentTenant} />`

### 变更2：新增知识库Tab
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - 扩展 `activeTab` 类型加入 `'knowledge'`
  - Tab列表中插入到"大脑配置"之后
  - 创建 `KnowledgeTab` 组件（约400行），包含3个子Tab：
    - **知识条目**：列表+搜索+分类筛选+创建/编辑/删除+收藏
    - **企业文件**：复用 `EnterpriseKBBrowser` 组件
    - **知识治理**：统计概览+收藏列表+范围分布

### 变更3：从公司信息Tab移除EnterpriseKBBrowser
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：移除 `EnterpriseKBBrowser` 及相关状态变量 `infoRefresh`、`kbPromptModal`

### 变更4：知识库类型定义
- **文件**：`frontend/src/types/index.ts`
- **内容**：新增 `KnowledgeScope`、`KnowledgeType`、`KnowledgeStatus`、`Importance`、`Validity`、`KnowledgeEntry`、`KnowledgeStats`

### 变更5：i18n翻译（知识库）
- **文件**：`frontend/src/i18n/zh.json`、`frontend/src/i18n/en.json`
- **内容**：Tab标签 `knowledge` + `enterprise.knowledge` 翻译块（约40个条目）

### 变更6：完善审批Tab
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - 创建 `ApprovalsTab` 组件（约250行），替代原来缺失的审批Tab渲染
  - 分Tab：全部/待审批/已处理
  - 状态筛选（pending/approved/rejected/cancelled）
  - 类型筛选（leave/expense/purchase/contract/project/other）
  - 统计概览（待审批/已通过/已拒绝计数）
  - 审批详情弹窗（基本信息+审批步骤时间线+评论+通过/拒绝操作）
  - 调用 `approvalApi.getSteps/approve/reject`

### 变更7：完善审计日志Tab
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - 创建 `AuditLogTab` 组件（约150行），替代原来的内联渲染
  - 搜索功能（按动作、Agent ID、详情内容搜索）
  - 分页加载（每页50条，替代原来一次加载200条）
  - 日志详情展开/折叠（点击展开JSON格式化展示）
  - 导出功能（CSV/JSON）
  - 保留原有三级筛选（全部/后台/操作）

### 变更8：完善用户管理
- **文件**：`frontend/src/pages/UserManagement.tsx`
- **内容**：
  - 新增用户详情弹窗（头像+基本信息网格+活跃状态+编辑入口）
  - 点击用户名可打开详情弹窗

### 变更9：i18n翻译（审批+审计日志）
- **文件**：`frontend/src/i18n/zh.json`、`frontend/src/i18n/en.json`
- **内容**：`enterprise.approval` 翻译块（约25个条目）+ `enterprise.audit` 补充翻译

### 变更10：更新CODE_STRUCTURE_AND_FILE_GUIDE.md
- **文件**：`docs/CODE_STRUCTURE_AND_FILE_GUIDE.md`
- **内容**：更新 `EnterpriseSettings.tsx` 描述，反映12个Tab的完整结构

### 变更11：知识库状态流转+晋升审核+有效性标记
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - 知识条目列表中添加状态流转按钮（DRAFT→ACTIVE→DEPRECATED→ARCHIVED）
  - 治理子Tab中添加知识晋升审核列表（L1→L2→L3，一键晋升）
  - 治理子Tab中添加有效性标记（UNVERIFIED/VERIFIED/OUTDATED/INVALID 下拉选择）

### 变更12：邀请码一键复制
- **文件**：`frontend/src/pages/InvitationCodes.tsx`
- **内容**：邀请码旁添加📋复制按钮，点击复制到剪贴板

### 变更13：配额积分展示+资源概览
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - 新增 `CreditOverview` 组件（积分余额卡片+累计获得/消耗+积分排行榜Top10）
  - 配额Tab中添加"积分与资源概览"区域
  - 调用 `creditApi.getBalance/getStats/getLeaderboard`

### 变更14：工具搜索
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - 工具Tab顶部添加搜索框
  - 全局工具列表项添加 `data-tool-name` 属性，支持实时DOM过滤
  - MCP测试结果工具项也添加 `data-tool-name`

### 变更15：技能Tab子Tab+绑定+热更新+统计
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - 添加子Tab导航（files/bindings/stats）
  - 绑定关系列表（调用 `evolutionExtendedApi.listBindings`，含解绑按钮）
  - 技能热更新按钮（调用 `evolutionExtendedApi.reloadSkills`）
  - 技能计数badge（调用 `enterpriseSkillApi.getCounts`）
  - 统计视图（按大脑分组展示技能数量+热更新状态）

### 变更16：审批Tab我待审批+取消操作
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - 添加"我待审批"子Tab（调用 `approvalApi.getMyPending`）
  - 审批详情弹窗添加"取消审批"按钮（调用 `approvalApi.cancel`，带确认弹窗）
  - 操作按钮区域重构：通过/拒绝仅在存在pending步骤时显示，取消按钮始终显示

### 变更17：用户权限级别管理
- **文件**：`frontend/src/pages/UserManagement.tsx`
- **内容**：
  - 导入 `chairmanApi`
  - 添加 `access_level` 字段到 `UserInfo` 接口
  - 定义4级权限（CHAT_ONLY=0/LIMITED=1/DEPARTMENT=2/FULL=3）
  - 添加 `handleAccessLevelChange` 函数（调用 `chairmanApi.updateEmployeeAccess`）
  - 用户详情弹窗中添加权限级别选择器（4个按钮+描述）

### 变更18：i18n翻译补充
- **文件**：`frontend/src/i18n/zh.json`、`frontend/src/i18n/en.json`
- **内容**：
  - 审批：`myPending`/`cancel`/`cancelConfirm`
  - 用户：`accessLevel`/`accessChatOnly`/`accessLimited`/`accessDepartment`/`accessFull`及描述/`accessLevelUpdated`/`confirmAccessChange`

### 变更19：公司简介Markdown预览
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - 添加 `companyIntroMode` 状态（edit/preview）
  - 编辑/预览切换按钮
  - 预览模式使用 `dangerouslySetInnerHTML` 渲染简易Markdown（h1-h3/bold/italic/code/li）
  - i18n翻译：`companyIntro.edit`/`companyIntro.preview`/`companyIntro.empty`

### 变更20：审计日志时间范围选择器
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - 添加 `dateFrom`/`dateTo` 状态
  - 搜索栏旁添加日期选择器（从/至）+ 清除按钮
  - 过滤逻辑中添加时间范围过滤
  - i18n翻译：`audit.dateFrom`/`audit.dateTo`

### 变更21：工具按部门筛选
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - 导入 `toolApi`
  - 添加 `toolDeptFilter`/`deptTools`/`loadingDeptTools` 状态
  - 搜索栏旁添加部门下拉框（8个部门）
  - 选择部门时调用 `toolApi.getByDepartment`
  - 工具列表渲染使用 `displayTools`（根据部门筛选动态选择数据源）

### 变更22：技能作用域展示
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - 绑定列表中添加scope标签（global/evolved/personal，不同颜色）
  - 读取 `b.scope`/`b.skill_scope` 字段

### 变更23：审批"我发起的"分Tab
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`、`frontend/src/services/api.ts`
- **内容**：
  - api.ts中添加 `approvalApi.getMyApprovals`（调用 `/approvals/my`）
  - 添加 `my-initiated` 子Tab类型
  - 添加 `myInitiatedApprovals`/`loadingMyInitiated` 状态和 `loadMyInitiated` 函数
  - 子Tab列表添加"我发起的"按钮
  - 过滤逻辑中包含 `my-initiated` 数据源
  - i18n翻译：`approval.myInitiated`

### 变更24：知识搜索结果高亮
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - 添加 `highlightText` 辅助函数（正则分割+`<mark>`标签高亮）
  - 搜索结果标题使用 `highlightText(entry.title, searchQuery)`
  - 搜索结果内容使用 `highlightText(entry.content?.slice(0, 200), searchQuery)`
  - 非搜索模式下保持原有截断显示

### 变更25：技能自动生成入口
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - 添加 `showGenerateModal`/`generateForm`/`generating` 状态
  - SkillsTab按钮区添加"✨ 自动生成"按钮
  - 生成弹窗：技能名称+目标部门+技能描述/需求
  - 调用 `evolutionExtendedApi.generateSkill` 提交生成请求
  - 生成成功后刷新技能列表

### 变更26：公司Logo上传
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - 新增 `CompanyLogoUploader` 组件（72x72预览框+拖拽/点击上传+2MB限制+PNG/JPG/SVG校验+本地预览fallback）
  - 调用 `POST /api/enterprise/logo`，后端端点不存在时使用本地预览
  - 公司信息Tab添加 `<CompanyLogoUploader key={logo-${selectedTenantId}} />`
  - i18n翻译：`enterprise.logo.*`（title/upload/hint/sizeLimit/formatLimit/uploaded）

### 变更27：用户激活/停用
- **文件**：`frontend/src/pages/UserManagement.tsx`
- **内容**：
  - 用户详情弹窗Actions区域重构：左侧激活/停用按钮，右侧编辑/取消按钮
  - 激活调用 `POST /enterprise/employees/{id}/activate`
  - 停用调用 `POST /enterprise/employees/{id}/deactivate`，带确认弹窗
  - i18n翻译：`userMgmt.activate/deactivate/activated/deactivated/confirmDeactivate`

### 变更28：邀请码使用详情
- **文件**：`frontend/src/pages/InvitationCodes.tsx`
- **内容**：
  - 新增 `usageDetail` 状态（`{ code: string; usages: any[] } | null`）
  - 邀请码行操作列添加"详情"按钮（`used_count > 0`时显示）
  - 调用 `/invitation-codes/{code}/usages`
  - 使用详情弹窗（code展示+使用记录列表+空状态提示）
  - i18n翻译：`enterprise.invites.usageDetail/usageDetailTitle/noUsageData/copied/copy`

### 变更29：部门CRUD
- **文件**：`frontend/src/pages/EnterpriseSettings.tsx`
- **内容**：
  - `DeptTree` 组件增强：每个部门节点右侧添加编辑✏/删除✕按钮
  - 编辑：`prompt`输入新名称 → `PUT /departments/{id}`
  - 删除：`confirm`确认 → `DELETE /departments/{id}`
  - 组织管理Tab部门树区域添加"+ 新增"按钮
  - 新增：`prompt`输入名称 → `POST /departments`
  - i18n翻译：`enterprise.org.departments/addDept/newDeptName`

---

## 六、总结

| 模块 | 完善度 | 优先级 | 变更 |
|------|--------|--------|------|
| 公司信息 | 98% | — | **新增Markdown预览+Logo上传**（编辑/预览切换，Logo上传UI已就绪待后端） |
| 模型池 | 100% | — | — |
| 大脑配置 | 100% | — | — |
| 知识库 | 100% | — | **新增Tab**，含条目CRUD+搜索+分类+统计+收藏+文件+治理+状态流转+晋升审核+有效性标记+搜索高亮 |
| 工具 | 90% | — | **新增搜索框+按部门筛选** |
| 技能管理 | 95% | — | **新增子Tab+绑定+热更新+统计+作用域+自动生成** |
| 邀请码 | 99% | — | **新增一键复制+使用详情**（详情UI已就绪待后端） |
| 配额 | 85% | — | **新增CreditOverview**，含积分余额+排行榜 |
| 用户 | 95% | — | **新增详情弹窗+权限级别+激活/停用**（激活/停用UI已就绪待后端） |
| 组织管理 | 90% | — | **修复Bug+部门CRUD**（CRUD UI已就绪待后端） |
| 审批 | 95% | — | **新增我待审批+我发起的+取消操作** |
| 审计日志 | 90% | — | **新增时间范围选择器** |

---

## 七、待完善项汇总与可行性分析

基于代码实际检查与后端API对照，剩余待完善项分类如下：

### 可立即实现（前端单方面即可）— ✅ 全部已完成

| 项目 | 模块 | 状态 |
|------|------|------|
| 搜索结果高亮 | 知识库 | ✅ 已完成（`highlightText`函数+`<mark>`标签） |
| 技能自动生成入口 | 技能管理 | ✅ 已完成（✨按钮+生成弹窗，调用`generateSkill`） |

### 需后端配合（后端无API或需确认）

| 项目 | 模块 | 前端状态 | 后端状态 |
|------|------|----------|----------|
| Logo上传 | 公司信息 | ✅ UI已就绪 | 无专用上传端点，需新增或复用文档上传 |
| 用户激活/停用 | 用户 | ✅ UI已就绪 | `EmployeeController`不提供CRUD，需新增端点 |
| 部门CRUD | 组织管理 | ✅ UI已就绪 | `DepartmentController`仅有GET，需新增CUD端点 |
| 薪酬计划管理 | 配额 | ❌ 未实现 | 完全不存在，需从零设计 |
| 合规报告 | 审计日志 | ❌ 未实现 | 仅有内部服务，需暴露为REST API |
| 邀请码使用详情 | 邀请码 | ✅ UI已就绪 | 无专用API，需新增 |

### 风险提示

1. **路径前缀不一致**：前端`chairmanApi.updateEmployeeAccess`使用`/chairman/employees/{id}/access-level`，后端API_REFERENCE.md记录为`/api/enterprise/employees/{id}/access-level`，需联调确认。
2. **evolutionExtendedApi路径未记录**：前端`evolutionExtendedApi`整个模块调用的`/admin/skills/*`和`/admin/evolution/*`路径在API_REFERENCE.md中均未记录，需联调确认这些端点是否真实存在。

---

## 八、后端改进方案

以下为需要后端新增或修改的API端点方案，按优先级排序。

### P1 — Logo上传

**现状**：`GET /api/system/config` 返回 `logo_url` 字段，但无上传端点。

**方案**：复用现有文档上传基础设施，新增Logo专用端点。

```
POST /api/enterprise/logo
Content-Type: multipart/form-data
Body: file (图片文件，限制 2MB，支持 PNG/JPG/SVG)

Response: { "logo_url": "https://..." }
```

**后端改动**：
- `EnterpriseController` 新增 `uploadLogo` 方法
- 复用 `DocumentService.upload()` 处理文件存储
- 更新 `SystemConfigService` 中 `logo_url` 配置项
- 文件存储到 `{tenantId}/logos/` 路径下

**前端改动**：
- 公司信息Tab添加Logo上传区域（拖拽/点击上传）
- 调用新端点，成功后更新 `logo_url` 显示

---

### P1 — 用户激活/停用

**现状**：`EmployeeController` 不提供通用CRUD，仅有 `access-level` 权限变更。

**方案**：在 `EmployeeController` 新增激活/停用端点。

```
POST /api/enterprise/employees/{employeeId}/activate
POST /api/enterprise/employees/{employeeId}/deactivate
Body: { "reason": "可选停用原因" }

Response: ApiResponse.ok(employee)
```

**后端改动**：
- `EmployeeController` 新增 `activate`/`deactivate` 方法
- `EmployeeLifecycleService` 新增 `activate()`/`deactivate()` 方法
- 停用时清除该员工的WebSocket连接和活跃会话
- 激活时恢复访问权限
- 审计日志记录操作

**前端改动**：
- `UserManagement.tsx` 用户详情弹窗添加激活/停用按钮
- 调用新端点，成功后刷新用户列表

---

### P2 — 部门CRUD

**现状**：`DepartmentController` 仅有 GET 查询，部门数据通过身份提供商同步获取。

**方案**：新增部门创建/更新/删除端点，支持手动管理部门。

```
POST   /api/departments              创建部门
Body: { "name": "...", "code": "...", "parent_id": "可选", "description": "可选" }

PUT    /api/departments/{id}          更新部门
Body: { "name": "...", "description": "..." }

DELETE /api/departments/{id}          删除部门（需无子部门和成员）
```

**后端改动**：
- `DepartmentController` 新增 `create`/`update`/`delete` 方法
- `DepartmentService` 新增对应业务逻辑
- 删除前校验：无子部门、无成员
- 审计日志记录

**前端改动**：
- OrgTab部门树添加"新增部门"按钮
- 部门节点添加右键菜单（编辑/删除）
- 部门编辑弹窗

---

### P2 — 知识使用排行

**现状**：知识模块仅有 summary/cleanup/promote/history，无使用排行。

**方案**：新增知识使用排行端点。

```
GET /api/knowledge/popular?limit=20&scope=L3_SHARED

Response: [
  { "key": "...", "title": "...", "usage_count": 42, "last_used_at": "..." }
]
```

**后端改动**：
- `KnowledgeController` 新增 `getPopular` 方法
- `KnowledgeService` 新增 `getPopular()` 方法
- 按知识的 `metadata.usage_count` 降序排列
- 支持按 scope 过滤

**前端改动**：
- KnowledgeTab治理子Tab添加"热门知识"列表
- 调用新端点展示Top20

---

### P3 — 邀请码使用详情

**现状**：邀请码仅有创建/列表/禁用/导出，无使用详情。

**方案**：新增邀请码使用记录端点。

```
GET /api/invitation-codes/{code}/usages

Response: [
  { "user_id": "...", "username": "...", "used_at": "..." }
]
```

**后端改动**：
- `InvitationCodeController` 新增 `getUsages` 方法
- `InvitationCodeService` 新增 `getUsages()` 方法
- 查询 `invitation_code_usage` 表（如不存在需新建）

**前端改动**：
- InvitationCodes列表中邀请码行添加"使用详情"按钮
- 弹窗展示使用记录列表

---

### P3 — 薪酬计划管理

**现状**：完全不存在，需从零设计。

**方案**：新增薪酬计划CRUD端点。

```
GET    /api/enterprise/compensation-plans           列出薪酬计划
POST   /api/enterprise/compensation-plans           创建薪酬计划
PUT    /api/enterprise/compensation-plans/{id}      更新薪酬计划
DELETE /api/enterprise/compensation-plans/{id}      删除薪酬计划

Body: {
  "name": "基础薪酬计划",
  "type": "SALARY|BONUS|CREDIT",
  "rules": [{ "condition": "...", "amount": 100 }],
  "target_scope": "ALL|DEPARTMENT|INDIVIDUAL",
  "is_active": true
}
```

**后端改动**：
- 新建 `CompensationPlanController`
- 新建 `CompensationPlanService`
- 复用 `CompensationPlanEntity`/`CompensationRecordEntity`
- 数据库迁移脚本

**前端改动**：
- 配额Tab添加"薪酬计划"子区域
- 薪酬计划列表+创建/编辑弹窗

---

### P3 — 合规报告

**现状**：仅有内部 `StandardComplianceTraceService`，非公开API。

**方案**：暴露合规检查结果为REST端点。

```
GET /api/enterprise/compliance/report

Response: {
  "score": 85,
  "total_checks": 20,
  "passed": 17,
  "failed": 3,
  "details": [
    { "check": "data_retention", "status": "PASS", "message": "..." },
    { "check": "access_control", "status": "FAIL", "message": "..." }
  ]
}
```

**后端改动**：
- `EnterpriseController` 新增 `getComplianceReport` 方法
- 复用 `StandardComplianceTraceService` 执行检查
- 聚合结果返回

**前端改动**：
- 审计日志Tab添加"合规报告"子Tab
- 展示合规评分+检查详情
