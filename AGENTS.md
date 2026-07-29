# Living Agent Service - 项目规则

> 本文件是 AI Agent 在本项目中执行任务时的行为规则基线，基于项目实际代码、架构文档和闭环体系提炼。

## 核心规则

### ⚠️ 重要约束

- **禁止使用 `powershell -Command` 修改代码**
- **本地命令用 `;` 分隔，不用 `&&`**
- **少量问题及时修复，大量问题整理文档后批量修复**
- **可以编译检查，不要构建服务**
- **必须中文回复**

### 📋 文档规范

- 修改代码前必须先查看 `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md`
- 修改BUG时不能与以下文档产生冲突和重复：
  - `docs/IMPROVEMENT_PLAN_INDEX.md`
  - `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md`
  - `docs/LOOP_RELATIONSHIP_FLOW_DIAGRAMS.md`

### 🔐 用户注册规则

- **董事长注册**：通过 `POST /api/system/register-founder`（需要 companyName）
- **其他用户**：通过邀请码进入（导入手机号/密码，或邀请时填写）

### 👥 员工显示规则

- **办公室布局**：显示 FIXED + EVOLVED + HUMAN（所有部门员工），不显示 PERSONAL（个人助理不是员工）
- **聊天对象选择区**：只显示 HUMAN + PERSONAL

### 🔧 技能安全规则

- **可见性闸门**：个人助手只能选择 `personalSafe=true` 的技能（已实现：`Skill.isPersonalSafe()` + `getPersonalAssistantVisibleSkills(userId)`）
- **硬边界**：个人助手技能采用"大脑在服务器、双手在桌面"的双层架构——服务器提供推理通道+WebSocket转发，技能脚本执行在桌面端本地（复用闭环6 win-automation模式），服务器不执行技能脚本本身
- **默认不暴露**：所有 `global` 技能默认 `personalSafe=false`，需显式标注才对个人助手可见



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

1. 未登录 → 只能 `/ws/public` 闲聊，**可以使用语音**（ASR/TTS 在 model_daemon.py 内部加载，无需认证）
2. 登录后 → 统一通过部门大脑对话，不暴露闲聊入口，可根据前端开关使用语音

> **设计说明**：`model_daemon.py` 是独立的"智能前台"服务，包含 Qwen3/Qwen3.5/Sherpa-ONNX(ASR)/MeloTTS(TTS)/CAM++(声纹) 所有模型，对所有用户（含未登录）开放。未登录用户通过 `/ws/public` 直接使用这些能力，无需 Java 侧认证。

### 5.2 AccessLevel 分级

| 级别 | level | 可访问 |
|------|-------|--------|
| CHAT_ONLY | 0 | 仅闲聊 |
| LIMITED | 1 | AdminBrain + CsBrain |
| DEPARTMENT | 2 | 本部门完整功能 + ToolNeuron |
| FULL | 3 | 所有大脑 + MainBrain |

### 5.3 固定员工直连禁令

- `origin=fixed` → **禁止 `/ws/agent` 直连**
- 前端判定字段：`agent.origin`（小写），非 `employee_origin`
- 前端处理：
  - **桌面端**：点击 PixelEmployee 固定员工 → 弹 toast 提示"固定员工请通过部门大脑对话" → 自动跳转到对应部门 `/ws/dept/{department}`
  - **Web 前端**：`Chat.tsx:L136,L196` 检测到 `origin=fixed` 时自动降级到 `/ws/public`（历史实现）
- 后端 `AgentWebSocketHandler` 连接时强制拦截，返回 CloseStatus(4030)
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

### 5.6 部门大脑入口分层（Web 前端）

> 详情见 [权限与入口矩阵.md](docs/权限与入口矩阵.md) §4.2 和 [对话入口逻辑梳理.md](docs/对话入口逻辑梳理.md) §0.4

部门详情页 (`DepartmentDetail.tsx`) 有两个对话入口，面向不同用户：

| 入口 | 组件 | 权限 | 说明 |
|------|------|------|------|
| **DepartmentBrainPanel** | "对话"按钮 | `isEnterprise \|\| isDepartmentHead` | 董事长/FULL + 部门负责人可见，跳转到 `/chat?brain={code}&dept={name}` |
| **DepartmentChatInline** | 内嵌快捷聊天 | `isEnterprise` | 仅董事长/FULL 可用，管理级快捷入口，直连 `/ws/dept/{code}` |

**所有部门人类员工的实际对话入口**：
- 部门页"对话"按钮（DepartmentBrainPanel）→ 跳转到 Chat.tsx 完整对话页
- 通用聊天页 `/chat` → `/ws/dept/{department_code}`

### 5.7 语音对话完整链路

> 详情见 [CODE_STRUCTURE_AND_FILE_GUIDE.md](docs/CODE_STRUCTURE_AND_FILE_GUIDE.md) §14.5 和 [权限与入口矩阵.md](docs/权限与入口矩阵.md) §1.5

**未登录用户语音闭环**：
```
前端语音录制 → Opus 编码 → Base64
    ↓
/ws/public "audio_full" 消息 → DepartmentWebSocketHandler
    ↓
AgentService.processAudioFullChain()
    ├─ Base64 解码
    ├─ AudioNative.Processor.decodeOpus()  ← Rust JNI (opus_codec.rs)
    ├─ modelManager.recognizeSpeech()      ← Sherpa-ONNX ASR
    ├─ modelManager.processChatWithIntent() ← Qwen3 意图分类 + LLM
    ├─ modelManager.synthesizeSpeechRaw()  ← MeloTTS TTS
    ├─ AudioNative.Processor.encodePcm()   ← Rust JNI
    └─ Base64 编码 → WebSocket 返回 → 前端播放
```

**关键文件**：
- Rust Opus 编解码：`living-agent-native/src/audio/opus_codec.rs`
- JNI 绑定：`living-agent-native/src/jni/audio_jni.rs`
- Java 调用：`AudioNative.Processor` 类
- ASR 模型：Sherpa-ONNX（通过 `modelManager.recognizeSpeech()`）
- TTS 模型：MeloTTS（通过 `modelManager.synthesizeSpeechRaw()`）

### 5.8 桌面端权限差异

> 详情见 [权限与入口矩阵.md](docs/权限与入口矩阵.md) §4.5 和 [CODE_STRUCTURE_AND_FILE_GUIDE.md](docs/CODE_STRUCTURE_AND_FILE_GUIDE.md) §17

| 项目 | Web 前端 | 桌面端 | 说明 |
|------|---------|--------|------|
| 闲聊入口 | `/ws/public` | ❌ 不存在 | 桌面端无 `/ws/public` 支持 |
| 董事长频道 | `/ws/enterprise` | ❌ 不存在 | 桌面端无 `/ws/enterprise` 支持 |
| 部门选择 | 按权限过滤 | 显示所有部门 | 桌面端无前端权限过滤，依赖后端拦截 |
| 固定员工防护 | `Chat.tsx:L136,L196` 降级 | PixelEmployee onClick toast + 部门跳转 | 桌面端已实现 P28 |
| 语音功能 | 前端开关控制 | ⚠️ 待实现 | 后端链路已就绪 |
| WebSocket 连接 | `ws-client.ts` | `OfficeChatPage.tsx` 自建 | 桌面端绕过 `ws-client.ts`，缺少自动重连 |

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

## 11. 实现文件索引

> 权限与入口规则的实现文件，便于快速定位代码。

### 11.1 Web 前端（React + TypeScript）

| 文件 | 说明 | 关键行号 |
|------|------|---------|
| `frontend/src/pages/Chat.tsx` | 通道选择逻辑、固定员工降级 | L190-211, L136, L196 |
| `frontend/src/pages/AgentDetail.tsx` | origin 判定、chat tab 隐藏 | L187-199 |
| `frontend/src/pages/DepartmentDetail/DepartmentDetail.tsx` | 部门大脑权限判断 | L56-58 |
| `frontend/src/pages/DepartmentDetail/DepartmentChatInline.tsx` | 内嵌聊天权限 | L69-70 |
| `frontend/src/pages/DepartmentDetail/DepartmentBrainPanel.tsx` | "对话"按钮入口 | L35-39 |
| `frontend/src/pages/Layout.tsx` | 侧边栏导航 |

### 11.2 桌面端（Electron）

| 文件 | 说明 | 关键行号 |
|------|------|---------|
| `living-agent-desktop/src/renderer/App.tsx` | 根组件、部门锁定、P10 语音登录态校验 | L470-483 |
| `living-agent-desktop/src/renderer/pages/OfficeChat/OfficeChatPage.tsx` | 部门聊天、WebSocket 自建 | L322-328, L861-877 |
| `living-agent-desktop/src/renderer/pages/OfficeChat/PixelEmployee.tsx` | 像素员工、origin 区分、P28 toast | L2197 |
| `living-agent-desktop/src/main/ws-client.ts` | WebSocket 客户端、通道切换 | L35-50 |
| `living-agent-desktop/src/main/shortcuts.ts` | 任务快捷键 | - |

### 11.3 后端（Java）

| 文件 | 说明 |
|------|------|
| `living-agent-gateway/src/main/java/com/livingagent/gateway/config/WebSocketConfig.java` | WebSocket 端点注册 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/AgentWebSocketHandler.java` | 数字员工 WebSocket 处理、固定员工拦截 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/DepartmentWebSocketHandler.java` | 部门大脑/董事长/公共闲聊 WebSocket 处理 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/service/AgentService.java` | Agent 会话服务、语音处理链路 |
| `living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentApiController.java` | Agent REST API |
| `living-agent-core/src/main/java/com/livingagent/core/security/DepartmentAccessService.java` | 部门访问权限判断 |

### 11.4 Rust 原生模块

| 文件 | 说明 |
|------|------|
| `living-agent-native/src/audio/opus_codec.rs` | Opus 音频编解码 |
| `living-agent-native/src/jni/audio_jni.rs` | 音频 JNI 绑定 |

### 11.5 Python Daemon

| 文件 | 说明 |
|------|------|
| `scripts/python/model_daemon.py` | 智能前台核心服务（Qwen3/Qwen3.5/ASR/TTS/声纹） |
| `scripts/python/tts/run_melotts.py` | MeloTTS TTS 服务 |
