# Living Agent Service - 项目规则

> 本文件是 AI Agent 在本项目中执行任务时的行为规则基线，基于项目实际代码、架构文档和闭环体系提炼。
>
> **⚠️ 重要**：禁止使用 `powershell -Command` 修改代码。本地命令用 `;` 分隔，不用 `&&`。少量问题及时修复，大量问题整理文档后批量修复。可以编译检查，不要构建服务。必须中文回复。修改代码必须先看代码结构与文件功能文件 `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md`

---

## 1. 项目概览

- **定位**：生命智能体自治系统 — 神经元群聊模式 + 仿脑神经中枢架构
- **核心能力**：感知(ASR/TTS/Vision) → 神经元决策 → 技能执行 → 进化学习
- **组织模型**：主脑(MainBrain) + 9个部门大脑 + 32个固定数字员工 + 个人助手

## 2. 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21 + Spring Boot 3.4 + Lombok + MapStruct |
| 原生 | Rust 1.85 (音频/通道/安全/内存) |
| 感知 | Python + Sherpa-ONNX + MeloTTS |
| 前端 | React 19 + TypeScript + Vite + Zustand + TanStack Query + React Router 7 |
| 桌面端 | Electron (living-agent-desktop/) |
| 数据库 | PostgreSQL + Redis + SQLite + Qdrant |
| 模型 | 动态模型池(BrainModelResolver + ModelPoolManager) |

## 3. 模块结构

```
living-agent-service/
├── living-agent-core/       # 核心模块：brain/neuron/channel/employee/autonomy/knowledge...
├── living-agent-native/     # Rust原生模块：audio/channel/memory/security/jni
├── living-agent-perception/ # 感知模块：ear(ASR)/mouth(TTS)/text
├── living-agent-skill/      # 技能模块：skills/配置
├── living-agent-gateway/    # 网关服务：controller/service/websocket/config
├── living-agent-app/        # 应用启动入口
├── living-agent-desktop/    # Electron桌面端
├── frontend/                # Web前端
└── docs/                    # 文档
    ├── core/                # 核心设计文档(01-07)
    ├── references/          # API参考
    └── 权限与入口矩阵.md     # 权限入口权威文档
```

## 4. 编码规则

### 4.1 Java 后端

- **包命名**：`com.livingagent.gateway.*`(网关) / `com.livingagent.core.*`(核心)
- **统一响应**：所有 API 使用 `common.ApiResponse<T>` — `ok(data)` / `err(code, desc)`
- **权限注解**：用 `@RequireAccess(resource, action, requireFull)` 声明式权限，不零散调用 `accessGateService.canRoute()`
- **事务**：JPA 写操作加 `@Transactional`，关键表用 `saveAndVerify()` 保证写入
- **数据库变更**：直接修改 `schema.sql` + `init-db/01_init.sql`，不创建 Flyway V 版本迁移文件
- **ID格式**：员工ID格式 `employee://digital/技术部/CI-CD流水线/023`，含 `/` 字符，推荐查询参数 `?id={encoded_id}`
- **神经ID**：`neuron://core/main-brain/001` 格式，不用前端拼接值 `brain_${brainKey}`

### 4.2 前端 (React + TypeScript)

- **状态管理**：Zustand store
- **数据请求**：TanStack Query (react-query)
- **路由**：React Router v7
- **API 基础路径**：`/api`，前端自动添加前缀
- **WebSocket URL 变更**：
  - 神经元对话：`/ws/agent?token=&agentId={neuronId}`
  - 大脑对话：`/ws/dept/{brainId}?token=`
  - 董事长频道：`/ws/enterprise?token=`
  - **不要使用**旧路径 `/ws/neuron/`、`/ws/brain/`、`/ws/chairman`
- **响应取值**：数据在 `response.data` 字段中
- **字段命名**：后端 camelCase，部分 @JsonProperty 转 snake_case

### 4.3 Rust 原生模块

- JNI 绑定在 `living-agent-native/src/jni/`
- 音频编解码参数：16kHz 采样率、单声道、960 帧大小
- JNI 调用结果验证：`NativeServiceWrapper` + `NativeException`

## 5. 权限与入口规则

### 5.1 登录优先级

**登录状态先于身份，身份先于页面，页面先于通道，通道先于流程**

1. 未登录 → 只能 `/ws/public` 闲聊，不可使用语音
2. 登录后 → 统一通过部门大脑对话，不暴露闲聊入口

### 5.2 AccessLevel 分级

| 级别 | level | 可访问 |
|------|-------|--------|
| CHAT_ONLY | 0 | 仅闲聊 |
| LIMITED | 1 | AdminBrain + CsBrain |
| DEPARTMENT | 2 | 本部门完整功能 + ToolNeuron |
| FULL | 3 | 所有大脑 + MainBrain |

### 5.3 固定员工直连禁令

- `origin=fixed` → **禁止 `/ws/agent` 直连**，前端自动降级到 `/ws/public`
- 前端判定字段：`agent.origin`（小写），非 `employee_origin`
- 后端 `AgentWebSocketHandler` 连接时强制拦截
- 正确入口：部门大脑 / 项目互动群 / 部门协调群

### 5.4 WebSocket 端点映射

| 通道 | 处理器 | 用途 |
|------|--------|------|
| `/ws/public` | DepartmentWebSocketHandler | 公共闲聊 |
| `/ws/dept/*` | DepartmentWebSocketHandler | 部门大脑对话 |
| `/ws/enterprise` | DepartmentWebSocketHandler | 董事长频道 |
| `/ws/agent` | AgentWebSocketHandler | 数字员工/个人助手直连 |

### 5.5 管理类 API（需 FULL 权限）

- `/api/model-pool/**`
- `/api/brain-models/**`
- `/api/windows-automation/**`
- `/api/v1/proxy/**`
- `/api/evolution/**`

## 6. 闭环体系与架构约束

### 6.1 四层闭环

| 层级 | 范围 | 闭环数 |
|------|------|--------|
| L1 | 流程正确性(1-14) | 14 |
| L2 | 覆盖完整性(17-22, 3-A/B, 11-A/B) | 9+1 |
| L3 | 生命体自洽(24-37) | 14 |
| L4 | 用户业务(38-64) | 26 |

### 6.2 主脑六步决策法

1. 意图识别 → 2. 路由决策 → 3. 需求就绪评估 → 4. 任务规划 → 5. 员工分派 → 6. 执行与交付

### 6.3 LLM-first / Rule-fallback

所有核心决策链路：LLM 自主决策优先，规则降级兜底。关键组件：
- `LlmBasedDialogueAnalyzer` → `RuleBasedDialogueAnalyzer`
- `LlmBasedFixedEmployeeDispatcher` → `RegistryBackedFixedEmployeeDispatcher`
- `LlmBasedFinalResponseCoordinator` → `DefaultFinalResponseCoordinator`

### 6.4 Trace 阶段

执行链路产生的标准 Trace 阶段：`intake_classified` → `main_brain_planned` → `brain_routed` → `department_plan_created` → `employee_assigned` → `employee_execution_started` → `employee_execution_completed` → `result_aggregated`

## 7. 关键命名与约定

### 7.1 命名区分

| 术语 | 含义 | 不要混淆 |
|------|------|---------|
| `autonomy` | 自治执行 | `autonomous`（自洽） |
| `employee_origin` | 后端字段 | 前端用 `agent.origin` |
| `brainId` | 后端返回真实ID | 不用前端拼接 `brain_${brainKey}` |

### 7.2 部门代码映射

`tech`/`hr`/`finance`/`sales`/`admin`/`cs`/`legal`/`ops`/`core`/`cross_dept`

### 7.3 员工来源 (origin)

| 值 | 说明 | 直连 |
|----|------|------|
| `fixed` | 固定数字员工 | 禁止 |
| `personal` | 个人助手 | 允许 |
| `human` | 真实人类 | 允许 |

### 7.4 任务状态机

`PENDING` → `CLAIMED` → `IN_PROGRESS` → `SUBMITTED` → `PENDING_REVIEW` → `REVIEWED` → `COMPLETED` / `REJECTED` / `NEEDS_REWORK`

## 8. 文件与数据规范

### 8.1 数据目录

```
data/
├── artifacts/              # 产物目录
├── conversations/          # 对话历史(按 tenant/user/taskKey)
├── conversations-by-id/    # 对话历史(按 conversationId)
├── indexes/                # 索引
├── knowledge.db            # 知识库(SQLite)
├── memory.db               # 记忆(SQLite)
├── projects/               # 项目数据
├── receipts/               # 执行回执
├── repo/                   # 代码仓库工作树
└── tasks/                  # 任务数据
```

### 8.2 API 注意事项

1. 路径不要带末尾斜杠：`/agents` 非 `/agents/`
2. 员工 ID 含 `/`，使用查询参数 `?id={encoded_id}`
3. 响应数据在 `data` 字段
4. 后端 camelCase，部分 @JsonProperty 转 snake_case
5. `API_BASE = '/api'`

## 9. 安全底线

- 不提交 `.env`、密钥、凭据文件
- Provider 列表接口返回 `apiKeyConfigured: boolean`，不返回实际密钥
- 任务 `claim`/`submit` 从 token 提取身份，不信任请求体 `employeeId`
- 沙箱进程隔离高风险操作
- 3次违规 → 黑名单 + 边界收紧 + TTL恢复

## 10. 文档权威源

| 领域 | 权威文档 |
|------|---------|
| 代码结构 | `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md` |
| 权限入口 | `docs/权限与入口矩阵.md` |
| API接口 | `docs/references/API_REFERENCE.md` |
| 闭环体系 | `docs/IMPROVEMENT_PLAN_INDEX.md` |
| 主脑规则 | `docs/core/MAINBRAIN_EXECUTION_RULES.md` |
| 文件规范 | `docs/core/FILE_MANAGEMENT_SPECIFICATION.md` |
| 核心架构 | `docs/core/02-core-architecture.md` |
| 员工模型 | `docs/core/03-employee-model.md` |
| 安全权限 | `docs/core/06-security-permission.md` |
| 固定员工SOP | `docs/FIXED_EMPLOYEE_ACTION_SOP_IMPROVEMENT_PLAN.md` |
