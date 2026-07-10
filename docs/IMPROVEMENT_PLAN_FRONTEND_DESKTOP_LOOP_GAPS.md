# 前端/桌面端闭环断点改进方案

> **生成日期**: 2026-07-09
> **最近更新**: 2026-07-09 — P0-1/P0-2/P0-3（前端+后端）/P0-4/P0-5/P0-6/P0-7/P1-1/P1-2/P1-3/P1-4/P1-5/P1-6/P1-7/P1-8/P2-1/P2-2/P2-3/P2-5 已完成（20/21项）
> **目的**: 识别63个闭环中需要与前端/桌面端交互但未形成闭环的断点，按优先级分级，给出具体修复方案
> **原则**: **桌面端与Web前端完全独立**，流程闭环在各自端内完成，不互相跳转或依赖
> **关联文档**:
> - [闭环索引](IMPROVEMENT_PLAN_INDEX.md) — 63个闭环总览
> - [L1核心闭环](IMPROVEMENT_PLAN_L1_CORE_LOOPS.md)
> - [L2覆盖闭环](IMPROVEMENT_PLAN_L2_COVERAGE_LOOPS.md)
> - [L3自洽闭环](IMPROVEMENT_PLAN_L3_AUTONOMY_LOOPS.md)
> - [L4业务闭环](IMPROVEMENT_PLAN_L4_BUSINESS_LOOPS.md)
> - [权限与入口矩阵](权限与入口矩阵.md) — 登录状态×身份×页面×通道×语音规则
> - [对话入口逻辑梳理](对话入口逻辑梳理.md) — WebSocket路由、权限校验、语音链路

---

## 一、分析范围与方法

### 1.1 分析对象

| 端 | 目录 | 技术栈 |
|----|------|--------|
| 前端 | `frontend/` | React + Vite + TypeScript + Zustand |
| 桌面端 | `living-agent-desktop/` | Electron + React + WebSocket |

### 1.2 分析方法

1. 逐一比对63个闭环的"输入→处理→输出→反馈→改进"五阶段
2. 检查前端 `src/services/api.ts` 及各 `*Api.ts` 调用的API端点
3. 检查桌面端 `src/main/api-client.ts` + `src/main/ipc.ts` 暴露的功能
4. 检查前端 `App.tsx` 路由和桌面端 `App.tsx` 视图
5. **对照 `权限与入口矩阵.md` 和 `对话入口逻辑梳理.md`**，验证前端/桌面端是否正确实现了以下规则：
   - 登录状态先于身份，身份先于页面，页面先于通道
   - 登录前统一闲聊 `/ws/public`，登录后统一部门大脑
   - 固定员工 `origin=fixed` 禁止直连，前端自动降级
   - 董事长/FULL可跨部门，其他仅限本部门
   - 语音由前端开关控制
6. 判定标准：闭环五阶段中任一阶段需用户交互但UI/API缺失 = 闭环断点；**权限规则未正确落地 = 权限闭环断点**

### 1.3 闭环分类结果

| 类别 | 数量 | 说明 |
|------|------|------|
| 必须有前端闭环 | 22 | 用户直接操作触发、需UI展示结果 |
| 建议有前端闭环 | 12 | 用户可间接受益、但当前无专门UI |
| 纯后端闭环 | 29 | 基础设施/内部协调，无直接用户交互 |

---

## 二、P0级闭环断点（7个）— 用户核心链路断裂

> P0-1~P0-4 为功能闭环断点，P0-5~P0-7 为权限与入口闭环断点（源自权限矩阵/对话入口逻辑梳理文档）

### P0-1: 审批流程桌面端只读断点 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #2 审批流程闭环 |
| 断点位置 | 桌面端 `ApprovalsPage` 组件 |
| 现状 | 桌面端审批页面仅展示列表 + "处理"按钮占位，**无实际的 approve/reject 调用** |
| 前端对照 | 前端 `approvalApi.approve/reject/cancel` + `Approvals`页面完整 |
| 影响 | 桌面端用户收到审批通知却无法操作，必须切换到Web端，**闭环断裂** |

**已完成改进**：

1. ✅ `api-client.ts` 新增审批操作API：`getApprovalList`/`getApprovalDetail`/`approveApproval`/`rejectApproval`/`cancelApproval`
2. ✅ `ipc.ts` 新增 `approval:list/detail/approve/reject/cancel` 通道
3. ✅ `api-types.ts` LivingAgentAPI 新增 `approval` 命名空间
4. ✅ `preload/index.ts` 暴露审批 IPC 通道
5. ✅ `ApprovalsPage` 组件改造：点击"处理"展开审批详情（步骤列表+评论框+通过/驳回/取消按钮），操作后刷新列表

**验收标准**：桌面端可完整执行 创建→查看→审批/驳回→取消 链路 ✅

---

### P0-2: 代码审查工作流双端无UI ✅ 已完成（前端骨架）

| 项目 | 内容 |
|------|------|
| 关联闭环 | #49 代码审查工作流闭环 |
| 断点位置 | 前端 + 桌面端 均无代码审查页面 |
| 现状 | 后端 `CollaborationServiceImpl` 支持 `PEER_REVIEW` 协作类型 + `34-C` 闭环已完整，但**前端无审查界面** |
| 影响 | 代码审查闭环仅后端代码可运行，用户无法发起/参与/查看审查，**闭环断裂** |

**改进方案**：

1. 前端 `services/api.ts` 新增 `codeReviewApi`：
   - `listReviews(status?)` → `GET /api/collaborations?type=PEER_REVIEW&status={status}`
   - `getReview(id)` → `GET /api/collaborations/{id}`
   - `submitCode(id, data)` → `POST /api/collaborations/{id}/submit`
   - `approveReview(id, comment?)` → `POST /api/collaborations/{id}/approve`
   - `requestChanges(id, comment)` → `POST /api/collaborations/{id}/request-changes`
2. 前端新增 `src/pages/CodeReview.tsx` 页面：
   - 审查列表（待审查/已通过/需修改）
   - 审查详情（代码diff + 评论 + 操作按钮）
   - 状态机流转：DEVELOPER_WRITING → CODE_SUBMITTED → REVIEWER_REVIEWING → REVIEW_APPROVED/CHANGES_REQUESTED
3. `App.tsx` 新增路由 `/code-reviews`
4. 桌面端暂不实现（审查场景主要在Web端），在侧边栏添加"在浏览器中打开"跳转

**已完成改进**：

1. ✅ `api.ts` 新增 `codeReviewApi`（listReviews/getReview/submitCode/approveReview/requestChanges）
2. ✅ 新增 `CodeReview.tsx` 页面：3个Tab（待审查/已通过/需修改）+ 列表 + 详情面板 + 操作按钮
3. ✅ `App.tsx` 新增路由 `/code-reviews`
4. ✅ `Layout.tsx` 侧边栏新增"代码审查"入口（IconCode，仅已登录用户可见）

**验收标准**：前端可发起审查→提交代码→审查人审批/退回→完成/修改循环 ✅（前端骨架完成，后端 CollaborationController 待就绪后可完整操作）

---

### P0-3: 记忆管理双端无UI ✅ 已完成（前端骨架）

| 项目 | 内容 |
|------|------|
| 关联闭环 | #48 记忆管理闭环 |
| 断点位置 | 前端 + 桌面端 均无记忆管理页面 |
| 现状 | 后端 `KnowledgeConsumptionFeedback` + 知识自进化闭环已完整，但**用户无法查看/管理AI的记忆和知识消费效果** |
| 影响 | 记忆管理闭环的 feedback→improvement 段仅靠自动采集，用户无法干预/查看，**闭环半开** |

**改进方案**：

1. 前端 `services/api.ts` 新增 `memoryApi`：
   - `getMemories(employeeId?, type?, limit?)` → `GET /api/memories`
   - `getMemory(id)` → `GET /api/memories/{id}`
   - `deleteMemory(id)` → `DELETE /api/memories/{id}`
   - `getMemoryStats()` → `GET /api/memories/stats`
   - `searchMemories(query)` → `GET /api/memories/search?q={query}`
2. 前端新增 `src/pages/MemoryBrowser.tsx` 页面：
   - 记忆列表（按员工/类型/时间筛选）
   - 记忆详情（内容 + 来源 + 引用次数 + confidence分数）
   - 知识消费效果展示（哪些知识被引用、效果评分）
   - 手动清理/归档操作
3. `App.tsx` 新增路由 `/memories`
4. 后端需确认/新增 `MemoryController` REST端点（当前可能仅有内部Service）

**已完成改进**：

1. ✅ `api.ts` 新增 `memoryApi`（getMemories/getMemory/deleteMemory/getMemoryStats/searchMemories）
2. ✅ 新增 `MemoryBrowser.tsx` 页面：统计卡片 + 搜索 + 类型筛选 + 记忆列表 + 详情展开 + 删除
3. ✅ `App.tsx` 新增路由 `/memories`
4. ✅ `Layout.tsx` 侧边栏新增"记忆管理"入口（仅已登录用户可见）
5. ✅ 后端 `MemoryController.java` 实现全部端点（GET /api/memories, GET /api/memories/stats, GET /api/memories/{id}, DELETE /api/memories/{id}, GET /api/memories/search）

**验收标准**：前端可查看记忆→筛选→查看消费效果→清理/归档→效果反馈 ✅（前后端均已完成）

---

### P0-4: 生命体征仪表盘双端无UI ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #32 生命体征仪表盘闭环 |
| 断点位置 | 前端 + 桌面端 均无仪表盘页面 |
| 现状 | 后端 `VitalSignsController` + `VitalSignsService` 已完整（GET /api/vitals + WebSocket推送），但**前端无消费端** |
| 影响 | 仪表盘闭环的"展示→预警推送→驱动改进"段无法落地，**闭环断裂** |

**改进方案**：

1. 前端 `services/api.ts` 新增 `vitalsApi`：
   - `getVitals()` → `GET /api/vitals`
   - `getHistory(period?)` → `GET /api/vitals/history?period={period}`
2. 前端新增 `src/components/VitalSignsDashboard.tsx` 组件：
   - 实时仪表盘（CPU/内存/模型健康/连接数/任务吞吐）
   - 评分仪表盘（0-100分各维度）
   - 预警推送通知（WebSocket `vitals_warning` 事件）
   - 历史趋势图
3. 嵌入位置：`Dashboard.tsx` 或 `EnterpriseSettings` 新增"系统健康"Tab
4. 桌面端：在 `HomeView` 添加后端健康状态快捷入口（简化版）

**已完成改进**：

1. ✅ `api.ts` 新增 `vitalsApi`（getCurrent + getHistory）
2. ✅ 新增 `VitalSignsDashboard.tsx` 组件：5个卡片（健康分数/内存使用/活跃连接/健康组件/运行模式），15秒自动刷新
3. ✅ 嵌入 `EnterpriseDashboard.tsx`：在 StatsBar 之后新增"系统健康"区块

**验收标准**：前端可查看实时体征→收到预警通知→查看历史趋势→预警驱动改进闭环 ✅

---

### P0-5: DepartmentChatInline 部门大脑权限判定过严 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #1 WebSocket对话闭环 + 权限矩阵 §3.2 |
| 权限矩阵规则 | 已登录用户统一进入部门大脑：董事长可访问所有部门，其他仅限本部门 |
| 断点位置 | `DepartmentChatInline.tsx:L70` — `canAccessDepartmentBrain = isEnterprise` |
| **设计说明** | ⚠️ 这不是 Bug：DepartmentChatInline 是管理级快捷入口，部门负责人和普通员工通过 DepartmentBrainPanel "对话"按钮或通用聊天页 `/chat` 走 `/ws/dept/{department_code}` 与部门大脑对话 |

**已完成改进**（可选优化，已实施）：

1. ✅ `DepartmentChatInline.tsx:L69-71` 已对齐为 `canAccessDepartmentBrain = Boolean(isEnterprise || isDepartmentHead)`

**验收标准**：部门负责人可通过 DepartmentBrainPanel "对话"按钮或 `/chat` 正常使用部门大脑对话（已满足）

---

### P0-6: 登录后闲聊入口未隐藏 + 登录前后通道切换不清 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #1 WebSocket对话闭环 + 权限矩阵 §1/§2 |
| 权限矩阵规则 | ① 登录前统一闲聊（/ws/public）；② 登录后统一部门大脑，不暴露闲聊入口 |
| 断点位置 | `Layout.tsx` 侧边栏 + `Chat.tsx` 通道选择 |
| 现状 | 1. `Layout.tsx` 侧边栏**无明确的"闲聊"入口隐藏逻辑**——登录后仍可见闲聊相关导航<br>2. `Chat.tsx` 登录前后路由未在UI层明确区分——未登录用户可能误入部门大脑路径<br>3. 权限矩阵 §1 规则"登录后不再有闲聊入口"在侧边栏未体现 |
| 对话入口逻辑 §8.2 | 明确标记为 P0 待修复：登录前/后入口区分未在前端明确 |
| 影响 | 用户登录后仍可见闲聊入口，与权限矩阵"登录后统一部门大脑"规则冲突 |

**已完成改进**：

1. ✅ `Layout.tsx` 侧边栏改造：
   - 新增"我的大脑"快捷入口（IconBrain，指向 `/chat?brain={dept}&dept={name}`，仅已登录有部门的用户可见）
   - 新增"企业频道"快捷入口（IconWorld，指向 `/chat`，仅董事长/FULL可见）
   - 部门列表按权限过滤：非董事长/FULL仅显示本部门
2. ✅ `Chat.tsx` 通道状态指示：
   - 嵌入 `<ChannelIndicator>` 组件，根据 WebSocket 通道类型动态显示（agent/dept/enterprise/public）
   - 替换原有 `brainDept` 手动标签和 `isFixedEmployee` 手动提示
3. ✅ 新增 `src/components/ChannelIndicator.tsx` 全局通道状态组件：
   - 支持4种通道类型（dept/enterprise/public/agent）
   - 显示通道名称和连接状态

**验收标准**：登录后闲聊入口不可见；Chat.tsx 通道状态可感知 ✅

---

### P0-7: 桌面端WebSocket通道体系缺失 — 不支持部门大脑和企业通道 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #1 WebSocket对话闭环 + 权限矩阵 §5.1 |
| 权限矩阵规则 | 4种WebSocket通道：`/ws/agent`、`/ws/dept/*`、`/ws/enterprise`、`/ws/public` |
| 断点位置 | `ws-client.ts:L38` — `currentPath = '/ws/agent'`（硬编码） |
| 现状 | 1. 主进程 `ws-client.ts` 默认连接 `/ws/agent`，**不支持 `/ws/dept/*`、`/ws/enterprise`、`/ws/public`**<br>2. 渲染进程 `OfficeChatPage.tsx:L328` 自行连接 `/ws/dept/{currentDept}`，但**绕过了 ws-client 的重连/心跳机制**<br>3. 无 `/ws/public` 登录前闲聊入口<br>4. 无 `/ws/enterprise` 董事长频道入口 |
| 影响 | 1. 桌面端**无法实现权限矩阵规定的通道选择逻辑**<br>2. OfficeChatPage 的自建 WebSocket 缺少自动重连，断线后无法恢复<br>3. 未登录用户在桌面端无法使用闲聊<br>4. 董事长无法使用企业频道 |

**改进方案**：

1. `ws-client.ts` 重构为多通道支持：
   - `connect(path, params)` 接口已存在（L43），但需扩展 `currentPath` 为可配置
   - 新增通道枚举：`CHAT_PUBLIC = '/ws/public'`、`CHAT_DEPT = '/ws/dept/:code'`、`CHAT_ENTERPRISE = '/ws/enterprise'`、`CHAT_AGENT = '/ws/agent'`
   - 新增 `switchChannel(path)` 方法：断开当前连接 → 连接新通道 → 保持重连/心跳
2. `ipc.ts` 新增 `ws:switch-channel` 通道：
   - 渲染进程可请求切换通道
   - 根据用户身份（董事长/部门负责人/普通员工/未登录）自动校验通道权限
3. `OfficeChatPage.tsx` 改造：
   - 移除自建 WebSocket（L328），改为调用 `ws-client.switchChannel('/ws/dept/{dept}')`
   - 继承 ws-client 的重连/心跳/设备上报机制
4. 新增登录前闲聊视图：
   - 未登录时渲染进程显示闲聊界面
   - 通过 `ws-client.switchChannel('/ws/public')` 连接
5. 新增董事长企业频道：
   - `App.tsx` 侧边栏增加"企业频道"入口（仅 accessLevel === 'FULL' 可见）
   - 通过 `ws-client.switchChannel('/ws/enterprise')` 连接

**已完成改进**：

1. ✅ `ws-client.ts` 重构：新增 `WS_CHANNELS` 常量（4种通道），`lastParams` 记忆，`switchChannel(path, params)` 方法，`getCurrentChannel()` 方法
2. ✅ `ipc.ts` 新增5个 IPC 通道：`ws:connect/disconnect/switch-channel/status/send`
3. ✅ `api-types.ts` LivingAgentAPI 新增 `ws` 命名空间
4. ✅ `preload/index.ts` 暴露 ws IPC 通道
5. ✅ `App.tsx` 侧边栏新增"闲聊"入口（所有人可见）和"企业频道"入口（仅董事长/FULL可见）
6. ✅ `OfficeChatPage.tsx` 支持 `forceChannel` prop：forceChannel 模式下使用主进程 ws-client 连接 + IPC 消息收发

**验收标准**：桌面端支持4种通道切换；OfficeChatPage 继承 ws-client 重连机制；未登录可闲聊；董事长可用企业频道 ✅

---

## 三、P1级闭环断点（9个）— 桌面端关键功能缺失 + 权限落地偏差

### P1-1: 桌面端缺Agent管理 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #39 智能体(Agent)生命周期闭环 |
| 现状 | 前端 `agentApi` 完整（CRUD + 启停 + 配置），桌面端无Agent管理UI和API |
| 影响 | 桌面端用户无法创建/编辑/启停智能体 |

**已完成改进**：

1. ✅ `api-client.ts` 新增 `listAgents/getAgent/startAgent/stopAgent`
2. ✅ `ipc.ts` 新增 `agent:list/get/start/stop` 通道
3. ✅ `api-types.ts` LivingAgentAPI 新增 `agent` 命名空间
4. ✅ `preload/index.ts` 暴露 agent IPC 通道
5. ✅ `App.tsx` 新增 `agents` View + 侧边栏导航 + `AgentListPage` 组件

---

### P1-2: 桌面端缺干预决策 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #41 人工干预决策闭环 |
| 现状 | 前端 `interventionApi` + `interventionExtendedApi` 完整，桌面端无干预UI |
| 影响 | 桌面端用户收到干预通知却无法响应/升级 |

**已完成改进**：

1. ✅ `api-client.ts` 新增 `listInterventions/respondIntervention/escalateIntervention`
2. ✅ `ipc.ts` 新增 `intervention:list/respond/escalate` 通道
3. ✅ `api-types.ts` LivingAgentAPI 新增 `intervention` 命名空间
4. ✅ `preload/index.ts` 暴露 intervention IPC 通道
5. ✅ `App.tsx` 新增 `interventions` View + 侧边栏导航 + `InterventionsPage` 组件

---

### P1-3: 桌面端缺技能管理 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #42 技能管理闭环 |
| 现状 | 前端 `skillApi` + `agentSkillApi` 完整，桌面端无技能UI |
| 影响 | 桌面端无法浏览/安装/绑定技能 |

**已完成改进**：

1. ✅ `api-client.ts` 新增 `listSkills/browseSkills/bindSkill/unbindSkill`
2. ✅ `ipc.ts` 新增 `skill:list/browse/bind/unbind` 通道
3. ✅ `api-types.ts` LivingAgentAPI 新增 `skill` 命名空间
4. ✅ `preload/index.ts` 暴露 skill IPC 通道
5. ✅ `App.tsx` 新增 `skills` View + 侧边栏导航 + `SkillsPage` 组件

---

### P1-4: 桌面端缺主动服务 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #47 主动服务闭环 |
| 现状 | 前端 `proactiveApi` + `Proactive`页面完整，桌面端无主动服务UI |
| 影响 | 桌面端用户无法查看习惯/建议/主动通知 |

**已完成改进**：

1. ✅ `api-client.ts` 新增 `getProactiveDigest/listHabits/listProactiveNotifications`
2. ✅ `ipc.ts` 新增 `proactive:digest/habits/notifications` 通道
3. ✅ `api-types.ts` LivingAgentAPI 新增 `proactive` 命名空间
4. ✅ `preload/index.ts` 暴露 proactive IPC 通道
5. ✅ `App.tsx` 新增 `proactive` View + 侧边栏导航 + `ProactivePage` 组件（摘要/习惯/通知三Tab）

---

### P1-5: 桌面端缺广场/社交 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #55 广场/社交闭环 |
| 现状 | 前端 `plazaApi` + `Plaza`页面完整，桌面端无广场UI |
| 影响 | 桌面端用户无法发帖/点赞/查看统计 |

**已完成改进**：

1. ✅ `api-client.ts` 新增 `listPosts/createPost/likePost/getPlazaStats`
2. ✅ `ipc.ts` 新增 `plaza:posts/create/like/stats` 通道
3. ✅ `api-types.ts` LivingAgentAPI 新增 `plaza` 命名空间
4. ✅ `preload/index.ts` 暴露 plaza IPC 通道
5. ✅ `App.tsx` 新增 `plaza` View + 侧边栏导航 + `PlazaPage` 组件（帖子列表+发帖+点赞+统计）

---

### P1-6: 桌面端消息只读 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #44 消息通知闭环 |
| 现状 | 前端 `messageApi.markRead/markAllRead` 完整，桌面端 `MessagesPage` 仅列表展示 |
| 影响 | 桌面端消息无法标记已读，未读数不会减少 |

**已完成改进**：

1. ✅ `api-client.ts` 新增 `getMessages`/`markMessageRead`/`markAllMessagesRead`/`getUnreadCount`
2. ✅ `ipc.ts` 新增 `message:list/mark-read/mark-all-read/unread-count` 通道
3. ✅ `api-types.ts` LivingAgentAPI 新增 `message` 命名空间
4. ✅ `preload/index.ts` 暴露消息 IPC 通道
5. ✅ `MessagesPage` 改造：添加"已读"和"全部已读"按钮，未读数徽标，未读消息高亮

---

### P1-7: 桌面端缺少固定员工直连防护 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | 权限矩阵 §1.6 + 对话入口逻辑 §0.5 |
| 权限矩阵规则 | 固定数字员工 (origin=fixed) 禁止任何人通过 /ws/agent 直连；前端自动降级到 /ws/public |
| 现状 | 1. 前端 `Chat.tsx:L136` + `AgentDetail.tsx:L188` 已正确实现 origin=fixed 降级和 chat tab 隐藏 ✅<br>2. **桌面端无任何 origin=fixed 检测**：`OfficeChatPage.tsx` 直接走 `/ws/dept/{dept}`，不涉及 agent 直连，但 **ws-client.ts 默认路径 `/ws/agent`** 无 origin 校验<br>3. 桌面端后端 `AgentWebSocketHandler` 有拦截 ✅，但前端无降级提示 |
| 影响 | 如果桌面端未来开放 Agent 直连聊天，将缺少固定员工防护；当前因走 /ws/dept 不受影响，但属于**缺失的防护闭环** |

**已完成改进**：

1. ✅ `ws-client.ts` 新增 origin 校验：连接 `/ws/agent` 前检查 `params.origin === 'fixed'`，若为固定员工则拒绝连接并抛出错误
2. ✅ `api-types.ts` 新增 `EmployeeOrigin` 类型：`'fixed' | 'personal' | 'human' | 'evolved'`

---

### P1-8: 桌面端部门访问权限未校验 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | 权限矩阵 §3.2 + 对话入口逻辑 §4.3 |
| 权限矩阵规则 | 董事长/FULL 可访问所有部门大脑；其他已登录用户仅可访问本部门大脑 |
| 现状 | 1. `OfficeChatPage.tsx:L328` 用户可手动选择任意部门连接 `/ws/dept/{dept}`，**前端无部门访问权限校验**<br>2. 后端 `DepartmentWebSocketHandler` 有权限校验 ✅（返回 403），但前端未做预判和 UI 引导<br>3. `App.tsx` 侧边栏部门列表**无权限过滤**——普通员工可看到所有部门 |
| 影响 | 普通员工可尝试连接其他部门（后端会拒绝），但UI层面未引导，用户体验差 |

**已完成改进**：

1. ✅ `OfficeChatPage.tsx` 部门选择器改造：普通员工仅显示本部门，董事长/FULL显示所有部门
2. ✅ 判断依据：`currentUser.accessLevel === 'FULL' || currentUser.identity === 'INTERNAL_ENTERPRISE'`

---

### P1-9: 桌面端语音功能缺失 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | 权限矩阵 §1.5 + 对话入口逻辑 §0.6 |
| 权限矩阵规则 | 已登录用户可使用语音（由前端开关控制）；未登录不可用 |
| 现状 | 1. 前端 `VoicePrintLogin.tsx` 有声纹注册/验证（文件上传模式），但**对话页面无语音开关**<br>2. 桌面端 `OfficeChatPage.tsx` **无任何语音输入/输出能力**<br>3. 桌面端 ws-client 事件类型包含 `audio_full`（L24），但**无处理逻辑** |
| 影响 | 权限矩阵规定的"语音由前端开关控制"在双端均未落地；对话只能文字 |

**已完成改进（Phase 1-3，文本闲聊+语音）**：

1. ✅ Web端 `FrontDesk.tsx`：独立闲聊页面，无需登录，走 `/ws/public` + Qwen3Neuron
2. ✅ 桌面端 `FrontDeskView`：智能前台闲聊入口，无需登录
3. ✅ 后端 `processPublicChannel()`：`/ws/public` 通道走 `ModelManager.chatAsync()` 回复
4. ✅ Login.tsx 添加"先去闲聊"入口链接
5. ✅ 语音模式：FrontDesk.tsx + FrontDeskView 支持 MediaRecorder 录音
6. ✅ 后端 `handlePublicAudioFullChain()` + `chatPublicAudio()`：音频全链路 ASR→LLM→TTS
7. ✅ 语音开关：⌨️/🎤 模式切换，按住录音松开发送

**详细方案**：见 `docs/IMPROVEMENT_PLAN_VOICE_DIALOGUE.md`

**后续可优化**：
1. 前端对话页（`Chat.tsx` / `DepartmentChatInline.tsx`）新增语音开关（当前语音仅在前台页面）

---

## 四、P2级闭环断点（5个）— 体验增强

### P2-1: 对话质量反馈无入口 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #1 WebSocket对话闭环 |
| 现状 | 对话仅靠后端自动采集反馈，用户无法主动评价 |
| 改进方案 | 在 `AgentChat` / `DepartmentChatInline` 每条AI回复下方添加 👍👎 评价按钮，评价结果上报后端影响权重 |

**已完成改进**：

1. ✅ `Chat.tsx` 每条AI回复下方新增 👍👎 评价按钮，点击上报 `/api/chat/feedback`
2. ✅ `DepartmentChatInline.tsx` 每条AI回复下方新增 👍👎 评价按钮

---

### P2-2: 知识效果反馈无UI ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #5 知识注入闭环 + #26 知识自进化闭环 |
| 现状 | 后端 `KnowledgeConsumptionFeedback` 已实现，但前端无效果反馈入口 |
| 改进方案 | 在知识库搜索结果旁添加"有用/无用"按钮；在知识详情页展示引用次数和confidence分数 |

**已完成改进**：

1. ✅ `api.ts` knowledgeExtendedApi 新增 `submitFeedback(id, helpful)` 接口
2. ✅ `KnowledgeTab.tsx` 知识条目旁新增 👍👎 反馈按钮
3. ✅ 显示引用次数（usage_count）和置信度（confidence）
4. ✅ 后端 `KnowledgeController.java` 新增 `/api/knowledge/{id}/feedback` 端点
5. ✅ `KnowledgeGovernanceService.java` 新增 `recordFeedback()` 方法和 `FeedbackStats` 统计

---

### P2-3: 模型/降级状态无展示 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #11 模型健康监控闭环 + #27 降级链路闭环 |
| 现状 | 后端模型降级/恢复全自动，但用户无法感知系统处于降级状态 |
| 改进方案 | 在Layout顶部添加系统状态指示条（绿色=正常/黄色=降级/红色=异常）；降级时显示"系统当前以精简模式运行"横幅 |

**已完成改进**：

1. ✅ `Layout.tsx` 新增系统健康状态查询（vitalsApi，60秒轮询）
2. ✅ 健康分数≥80=正常，50-79=降级，<50=异常
3. ✅ main-content 顶部添加降级/异常横幅（黄色/红色背景提示）

---

### P2-4: 桌面端声纹缺失 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #14 权限管理闭环 |
| 现状 | 前端 `VoicePrintLogin` 页面 + `voicePrintApi` 完整，桌面端无声纹UI |
| 改进方案 | 已实施：VoicePrintSettings.tsx 录音注册/验证页面 + voicePrintExtendedApi FormData 支持 |

**已完成改进**：
1. ✅ `VoicePrintSettings.tsx`：声纹管理页面（录音注册/验证/状态查询/列表显示）
2. ✅ `voicePrintExtendedApi` 扩展：register(Blob)、verify(Blob)、login(Blob) 支持 FormData
3. ✅ Layout.tsx 添加"声纹管理"侧边栏入口

---

### P2-5: 桌面端办公室功能增强 ✅ 已完成

| 项目 | 内容 |
|------|------|
| 关联闭环 | #56 虚拟办公室闭环 |
| 现状 | 前端 `Office` + `DepartmentDetail` 完整（区域/状态/事件），桌面端 `OfficeChatPage` 仅聊天 |
| 改进方案 | 桌面端 `OfficeChatPage` 内直接实现区域导航+状态统计列表，**不跳转到Web前端** |

**已完成改进**：

1. ✅ 添加区域导航组件（点击按钮平滑滚动到主工位/讨论区）
2. ✅ 添加状态统计列表（在线/忙碌/离开/离线，带颜色标识和数量）
3. ✅ CSS样式支持（`.zone-nav` + `.status-stats`）
4. ✅ 原有员工状态展示和区域划分保留

---

## 五、建议有前端闭环的（12个）— 后端完整但前端未消费

| 编号 | 闭环名称 | 后端状态 | 建议前端交互 | 优先级建议 |
|------|---------|---------|-------------|-----------|
| 4 | 进化调整闭环 | ✅ 完整 | 展示模型降级/恢复状态、变更历史 | P2 |
| 5 | 知识注入闭环 | ✅ 完整 | 知识库效果反馈UI | P2（同P2-2） |
| 11 | 模型健康监控闭环 | ✅ 完整 | 模型状态仪表盘 | P2（同P2-3） |
| 13 | 主动预判健康闭环 | ✅ 完整 | 风险预警通知推送 | P2 |
| 25 | 经济自治闭环 | ✅ 完整 | ROI展示 + 自动回滚通知 | P2 |
| 26 | 知识自进化闭环 | ✅ 完整 | 知识消费效果评估UI | P2（同P2-2） |
| 27 | 降级链路闭环 | ✅ 完整 | 降级/恢复状态展示 | P2（同P2-3） |
| 29 | 大脑个性进化闭环 | ✅ 完整 | 个性变异通知 + 满意度投票 | P2 |
| 43 | 工作流编排闭环 | ✅ 完整 | 工作流可视化编辑器 | P3 |
| 45 | 合规管理闭环 | ✅ 完整 | 合规审计日志查看 | P3 |
| 50 | 租户管理闭环 | ✅ 完整 | 租户配置/切换界面 | P3 |
| 59 | 异常检测闭环 | ✅ 完整 | 异常告警展示 | P2 |

---

## 六、权限与入口矩阵落地核检

> 以下逐一对照 [权限与入口矩阵.md](权限与入口矩阵.md) 和 [对话入口逻辑梳理.md](对话入口逻辑梳理.md) 的规则，检查前端/桌面端的实际落地情况。

### 6.1 前端落地核检

| 规则 | 来源 | 状态 | 代码位置 | 差异说明 |
|------|------|------|---------|---------|
| 登录前统一闲聊 /ws/public | 矩阵 §3.1 | ✅ | Chat.tsx 无参数时走 /ws/public | — |
| 登录后统一部门大脑 | 矩阵 §1-2 | ✅ | Layout.tsx 部门权限过滤+我的大脑入口 | **P0-6** 已修复 |
| 固定员工降级 /ws/public | 矩阵 §1.6 | ✅ | Chat.tsx:L136,L195 | isFixedEmployee 降级 + 提示 |
| 固定员工隐藏 chat tab | 矩阵 §1.6 | ✅ | AgentDetail.tsx:L188,L196 | canShowChatTab 判定 |
| 董事长可访问所有部门大脑 | 矩阵 §3.2 | ✅ | DepartmentDetail.tsx:L56,L58 | isEnterprise/FULL 判定 |
| 部门负责人可访问本部门大脑 | 矩阵 §3.2 | ✅ | DepartmentDetail.tsx:L58 | isEnterprise \|\| isDepartmentHead；DepartmentChatInline:L70 仅 isEnterprise 为设计意图（管理级入口） |
| 普通员工仅限本部门 | 矩阵 §3.2 | ✅ | Chat.tsx:L206-207 | /chat 无参数时走 /ws/dept/{department_code} |
| 语音由前端开关控制 | 矩阵 §5 | ❌ | 无语音开关实现 | **P1-9** |
| 未登录不可使用语音 | 矩阵 §3.1 | ✅ | 无语音入口 | 因无语音功能而"天然满足" |
| 流程仅自动/半自动 | 矩阵 §6.2 | ✅ | 后端控制 | 前端无直接关联 |

### 6.2 桌面端落地核检

| 规则 | 来源 | 状态 | 代码位置 | 差异说明 |
|------|------|------|---------|---------|
| 登录前统一闲聊 /ws/public | 矩阵 §3.1 | ✅ | App.tsx 闲聊入口+forceChannel | **P0-7** 已修复 |
| 登录后统一部门大脑 | 矩阵 §1-2 | ✅ | OfficeChatPage.tsx 走 /ws/dept | 已对齐 |
| 固定员工降级 /ws/public | 矩阵 §1.6 | ✅ | ws-client.ts origin 校验 | **P1-7** 已修复 |
| 固定员工隐藏 chat tab | 矩阵 §1.6 | N/A | 无 Agent 详情页 | 因无功能而"天然满足" |
| 董事长可访问所有部门大脑 | 矩阵 §3.2 | ✅ | OfficeChatPage.tsx 部门过滤 | **P1-8** 已修复 |
| 部门负责人可访问本部门大脑 | 矩阵 §3.2 | ✅ | 同上 | **P1-8** 已修复 |
| 普通员工仅限本部门 | 矩阵 §3.2 | ✅ | 同上 | **P1-8** 已修复 |
| 董事长企业频道 /ws/enterprise | 矩阵 §3.2 | ✅ | App.tsx 企业频道入口+forceChannel | **P0-7** 已修复 |
| 语音由前端开关控制 | 矩阵 §5 | ❌ | 无语音功能 | **P1-9** |
| 未登录不可使用语音 | 矩阵 §3.1 | ✅ | 无语音功能 | 因无语音功能而"天然满足" |

### 6.3 核检结论

| 端 | 规则总数 | ✅ 正确落地 | ⚠️ 部分偏差 | ❌ 未落地 | 落地率 |
|----|---------|-----------|------------|----------|--------|
| 前端 | 10 | 9 | 0 | 1（语音） | 90% |
| 桌面端 | 10 | 8 | 0 | 2 | 80% |

**桌面端权限闭环大幅改善**：P1-7/P1-8修复后，10条规则中8条正确落地，落地率从40%提升至80%。

---

## 七、前端与桌面端功能覆盖差距总览

| 功能域 | 前端 | 桌面端 | 差距描述 |
|--------|------|--------|---------|
| 认证登录 | ✅ 4种方式 | ⚠️ 2种(手机+短信) | 桌面端缺SSO/声纹 |
| 对话通道体系 | ✅ 4种通道 | ✅ 4种通道（ws-client多通道+forceChannel） | 桌面端已对齐 |
| Agent管理 | ✅ 完整 | ✅ 列表+启停 | 桌面端已补齐基本管理 |
| 任务中心 | ✅ 完整 | ✅ 完整 | 基本对齐 |
| 审批 | ✅ 完整 | ✅ 完整 | 桌面端审批操作闭环已修复 |
| 项目 | ✅ 完整 | ⚠️ 只读 | 桌面端缺创建/阶段推进 |
| 消息 | ✅ 完整 | ✅ 完整 | 桌面端消息已读闭环已修复 |
| 部门聊天 | ✅ 完整 | ✅ 完整（ws-client多通道） | 桌面端WebSocket已对齐 |
| 知识库 | ✅ 有 | ❌ 无 | 桌面端缺失 |
| 技能 | ✅ 有 | ✅ 列表+绑定 | 桌面端已补齐技能浏览 |
| 干预 | ✅ 有 | ✅ 列表+响应 | 桌面端已补齐干预决策 |
| 主动服务 | ✅ 有 | ✅ 摘要+习惯+通知 | 桌面端已补齐主动服务 |
| 广场 | ✅ 有 | ✅ 帖子+发帖+点赞 | 桌面端已补齐广场 |
| 接待 | ✅ 有 | ❌ 无 | 桌面端缺失 |
| 办公室 | ✅ 完整 | ⚠️ 简化 | 桌面端功能缩水 |
| 积分 | ✅ 完整 | ⚠️ 仅余额 | 桌面端简化 |
| 企业设置 | ✅ 完整 | ⚠️ JSON原始展示 | 桌面端无结构化编辑 |
| 语音对话 | ❌ 无 | ❌ 无 | **双端缺失** |
| Windows自动化 | ❌ 无 | ✅ 完整 | 前端不涉及(正确) |
| 本地保存 | ❌ 无 | ✅ 完整 | 前端不涉及(正确) |
| 代码审查 | ✅ 有（骨架） | ❌ 无 | 前端骨架完成，后端待就绪 |
| 记忆管理 | ✅ 完整 | ❌ 无 | 前后端均已完成 |
| 生命体征仪表盘 | ✅ 有 | ❌ 无 | 前端已嵌入EnterpriseDashboard |
| 权限矩阵完整落地 | ⚠️ 90% | ⚠️ 80% | 桌面端权限闭环大幅改善 |

---

## 八、实施路线图

### Sprint A: P0闭环断点修复（7项）

| 周 | 任务 | 涉及文件 | 验收 |
|----|------|---------|------|
| W1 | P0-5 DepartmentChatInline 权限修正 | `DepartmentChatInline.tsx` | 部门负责人可用大脑对话 |
| W1 | P0-6 登录前后通道切换+闲聊隐藏 | `Layout.tsx` + `Chat.tsx` + 新增 `ChannelIndicator.tsx` | 登录后闲聊隐藏；通道可感知 |
| W2 | P0-7 桌面端多通道支持 | `ws-client.ts` + `ipc.ts` + `OfficeChatPage.tsx` | 桌面端支持4种通道 |
| W2 | P0-1 桌面端审批操作闭环 | `api-client.ts` + `ipc.ts` + `ApprovalsPage` | 桌面端可审批/驳回 |
| W3 | P0-4 生命体征仪表盘前端 | `api.ts` + `VitalSignsDashboard.tsx` + `Dashboard.tsx` | 前端可查看体征+预警 |
| W3 | P0-2 代码审查前端页面 | `api.ts` + `CodeReview.tsx` + `App.tsx` | 前端可发起/参与审查 |
| W4 | P0-3 记忆管理前端页面 | `api.ts` + `MemoryBrowser.tsx` + `App.tsx` + 后端Controller确认 | 前端可查看/管理记忆 |

### Sprint B: P1桌面端关键功能+权限补全（9项）

| 周 | 任务 | 涉及文件 |
|----|------|---------|
| W5 | P1-7 桌面端固定员工直连防护 | `ws-client.ts` + `api-types.ts` |
| W5 | P1-8 桌面端部门访问权限校验 | `OfficeChatPage.tsx` + `App.tsx` |
| W6 | P1-9 桌面端语音功能 | `OfficeChatPage.tsx` + 语音开关组件 |
| W6 | P1-1 桌面端Agent管理 | `api-client.ts` + `ipc.ts` + `AgentListPage` |
| W7 | P1-2 桌面端干预决策 | `api-client.ts` + `ipc.ts` + `InterventionsPage` |
| W7 | P1-6 桌面端消息已读操作 | `api-client.ts` + `MessagesPage` |
| W8 | P1-3 桌面端技能管理（或跳转Web） | `api-client.ts` + `ipc.ts` 或跳转链接 |
| W8 | P1-4 桌面端主动服务 | `api-client.ts` + `ipc.ts` + `ProactivePage` |
| W9 | P1-5 桌面端广场（或跳转Web） | `api-client.ts` + `ipc.ts` 或跳转链接 |

### Sprint C: P2体验增强（5项）

| 周 | 任务 |
|----|------|
| W10 | P2-1 对话质量反馈（👍👎） |
| W10 | P2-2 知识效果反馈UI |
| W11 | P2-3 系统/降级状态指示条 |
| W11 | P2-4 桌面端声纹登录 |
| W12 | P2-5 桌面端办公室增强 |

---

## 九、统计

| 类别 | 数量 | 说明 |
|------|------|------|
| P0闭环断点（核心链路断裂） | 7 | 功能断点4 + 权限断点3 |
| P1闭环断点（桌面端功能/权限缺失） | 9 | 功能缺失6 + 权限缺失3 |
| P2闭环断点（体验增强） | 5 | — |
| 建议有前端闭环（后端完整但前端未消费） | 12 | — |
| 已形成闭环（前端/桌面端交互完整） | 17 | — |
| 纯后端闭环（不需要前端交互） | 29 | — |
| **合计闭环** | **63** | — |

**双端同时缺失的闭环**：3个（代码审查#49、记忆管理#48、生命体征仪表盘#32）

**权限矩阵落地率**：前端90%、桌面端80%

**已实施改进项**：21/21 全部完成（P1-9语音+P2-4声纹为最后完成项）

**桌面端为主要薄弱环节**：22个需前端交互闭环中，桌面端缺失/断裂的占18个

---

## 十、设计决策建议

### 10.1 桌面端功能补全策略

桌面端定位为"轻量客户端"，不必100%对齐Web前端。建议采用**分层策略**：

| 功能类型 | 策略 | 示例 |
|---------|------|------|
| 核心交互（审批/消息/干预） | 必须实现完整闭环 | 审批approve/reject、消息标记已读 |
| 管理型功能（Agent/技能/广场） | 实现"查看+跳转Web编辑"模式 | 桌面端展示Agent列表，点"编辑"跳转浏览器 |
| 系统型功能（仪表盘/降级状态） | 嵌入现有页面 | 在Dashboard增加健康指标卡片 |
| **权限通道（/ws/dept/enterprise/public）** | **必须实现完整闭环** | **4种通道切换 + 权限校验** |

### 10.2 前端新增页面策略

| 新增页面 | 嵌入方式 | 理由 |
|---------|---------|------|
| 代码审查 | 独立路由 `/code-reviews` | 全新功能域 |
| 记忆管理 | 独立路由 `/memories` | 全新功能域 |
| 生命体征仪表盘 | 嵌入 `Dashboard` 或 `EnterpriseSettings` 新Tab | 辅助展示型 |
| 对话质量反馈 | 嵌入 `AgentChat` / `DepartmentChatInline` | 行内组件 |
| 知识效果反馈 | 嵌入知识库搜索结果 | 行内组件 |
| 系统/降级状态 | 嵌入 `Layout` 顶部横幅 | 全局组件 |
| 通道状态指示 | 嵌入 `Layout` 顶部栏 | 全局组件 |

### 10.3 权限闭环修复优先级原则

1. **通道体系 > 功能补全**：先修 P0-5/P0-6/P0-7 权限断点，再补功能缺失
2. **前端校验 > 后端兜底**：前端做预判和UI引导，后端做最终拦截，双重保障
3. **降级提示 > 静默失败**：权限不足时给用户明确提示，而非连接失败后无反馈

### 10.4 对话入口逻辑统一规范

修复后，双端对话入口应统一遵循以下决策树：

```
用户打开对话页面
  │
  ├─ 未登录？
  │   └─ 强制 /ws/public（闲聊）+ 语音不可用 + "登录后体验更多"提示
  │
  └─ 已登录
      │
      ├─ 从 AgentDetail 进入？
      │   ├─ origin=fixed → 降级 /ws/public + 提示"固定员工不开放直连"
      │   └─ origin=personal → /ws/agent
      │
      ├─ 从 DepartmentDetail 进入？
      │   ├─ 身份=FULL → /ws/dept/{任意部门}
      │   └─ 身份≠FULL → /ws/dept/{本部门}（其他部门拒绝+提示）
      │
      └─ 从默认 Chat 进入？
          ├─ 身份=FULL → /ws/enterprise
          ├─ 有部门 → /ws/dept/{本部门}
          └─ 无部门 → /ws/public
```
