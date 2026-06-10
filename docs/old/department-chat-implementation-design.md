# 部门对话落地设计

> 基于当前 `DepartmentApiController`、`DepartmentWebSocketHandler`、`BrainRegistry`、`AgentService` 的实际实现整理，目标是在不推翻现有骨架的前提下，把部门对话从"示意级"补到"可稳定开发与联调"。
>
> **文档状态**: 2026-04-29 重大更新 — 统一登录后部门大脑对话规则，补充语音/文字模式

---

## 1. 目标与范围

本设计只讨论：

- 登录后的部门对话 REST 入口
- 登录后的部门对话 WebSocket 入口
- 部门到 brain 的解析
- 部门脑真实推理链路
- 语音/文字模式的处理
- 董事长跨部门访问 vs 普通员工仅限本部门的权限控制
- 部门对话的错误语义、反馈与审计

本设计不直接讨论：

- 登录前的闲聊入口（统一走 `/ws/public`，详见 `对话入口逻辑梳理.md`）
- `/chat?id=...` 的 agent 直连
- 模型池 provider 管理
- 自动进化策略本身

---

## 2. 核心设计原则

### 2.1 登录后统一部门大脑

| 原则 | 说明 |
|------|------|
| 唯一入口 | 登录后所有对话都通过"部门大脑"进行 |
| 权限区分 | 董事长可访问所有部门，其他仅限本部门 |
| 语音/文字 | 由前端对话页面开关控制 |
| 不暴露闲聊 | 登录后不再有独立的"闲聊"入口 |

### 2.2 语音 vs 文字

| 维度 | 文字模式 | 语音模式 |
|------|---------|---------|
| 前端控制 | 默认开启 | 对话页开关控制（默认关闭） |
| 输入 | 键盘输入 | 麦克风录音 → Opus 编码 → Base64 传输 |
| 输出 | 显示文本 | TTS 合成 PCM → Opus 编码 → Base64 → 前端播放 |
| Java 侧音频处理 | 无 | Rust JNI Opus 编解码 → WAV → Sherpa ASR → MeloTTS → PCM → Opus 编码 |
| Python Daemon | `service="chat"` | `recognizeSpeech`（ASR）+ `synthesizeSpeech`（TTS） |

### 2.3 权限矩阵

| 身份 | 可访问部门大脑 | WebSocket 通道 |
|------|---------------|---------------|
| 董事长/FULL | 所有部门 | `/ws/dept/{code}` + `/ws/enterprise` |
| 部门负责人 | 仅本部门 | `/ws/dept/{department_code}` |
| 普通员工 | 仅本部门 | `/ws/dept/{department_code}` |
| 访客/低权限 | 无 | `/ws/public`（闲聊） |

---

## 3. 当前代码实际状态

## 3.1 已存在的类与方法

### `DepartmentApiController`

已存在：
- `chat(...)`
- `getDepartmentInfo(...)`
- `getDepartmentMembers(...)`
- `getDepartmentBrains(...)`
- `getMyDepartment(...)`

当前特点：
- 权限校验骨架已存在
- 部门访问校验已存在
- `chat(...)` 已接入 `DepartmentChatService.processDepartmentChat()` 真实链路
- `members(...)` 仍返回示例员工
- `brains(...)` 仍返回静态构造 brain 信息

### `DepartmentWebSocketHandler`

已存在：
- `afterConnectionEstablished(...)`
- `handleTextMessage(...)`
- `handleChatMessage(...)`
- `processWithBrain(...)`

当前特点：
- 已按 URI 提取 department
- 已做鉴权与部门访问校验
- 已调用 `agentService.startSession(...)`
- 已调用 `agentService.processTextAsync(...)`
- 已有 thinking / done / error 等消息语义

### `DepartmentChatService`

已存在：
- `processDepartmentChat(...)` — REST 入口
- `processDepartmentResult(...)` — 统一结果处理
- `saveMessage(...)` — 消息存储
- `getHistory(...)` — 历史记录

当前特点：
- REST 和 WebSocket 共用 `processDepartmentResult()` 统一结果解析
- 已完成统一错误语义（PERMISSION_DENIED, INITIALIZING, SYSTEM_ERROR, NO_RESPONSE）

### `BrainRegistry`

已存在：
- `getByDepartment(...)`
- `get(...)`
- `getAll()`
- `register(...)`
- `unregister(...)`

当前特点：
- 已支持按 department 取 brain
- 更偏运行时注册表，而不是完整组织查询层

### 其它相关能力
- `EmployeeService.listByDepartment(...)` 接口定义存在
- `Department.mapDepartmentToBrain(...)` / `mapBrainToDepartment(...)` 存在
- `AgentService.processTextAsync(...)` 已是当前 WebSocket 部门链路的真实推理入口
- `ChatNeuronRouter.route(...)` 负责意图分类和神经元路由

---

## 4. 当前主要问题

### 4.1 REST 和 WebSocket 语义基本统一
- WebSocket 部门链路已接入真实 `agentService.processTextAsync(...)`
- REST `chat(...)` 已接入 `DepartmentChatService.processDepartmentChat()`
- 两者共用 `processDepartmentResult()` 统一结果解析
- **状态：基本完成，待验证边界情况**

### 4.2 组织数据仍是示意级
- `getDepartmentMembers(...)` 还没有接员工库
- `getDepartmentBrains(...)` 没有读取真实 brain 状态或模型绑定
- `getMyDepartment(...)` 还没有和完整组织模型深度联动

### 4.3 语音/文字模式未在前端统一
- 前端对话页缺少统一的语音开关组件
- ASR/TTS 调用需要与 Python Daemon 对接
- 语音输入流程需与文字输入流程合并

### 4.4 董事长跨部门访问未在前端明确
- 部门列表页需要支持董事长选择任意部门进入对话
- 普通员工应被限制在本部门

### 4.5 Provider 注入初始化顺序问题
- Brain 的 Provider 注入受 Spring Bean 初始化顺序影响，可能导致部门大脑 ReAct 循环无法执行
- 详见 `model-pool-to-dialogue-flow-analysis.md`

---

## 5. 职责边界

## 5.1 `DepartmentApiController`

### 负责
- 提供部门对话 REST 入口
- 提供部门信息、成员、brains、我的部门等查询接口
- 做权限校验、部门访问校验、路由级校验

### 不负责
- 直接承载模型推理实现
- 决定登录前的闲聊语义
- 直接管理模型池 provider

## 5.2 `DepartmentWebSocketHandler`

### 负责
- 建立部门 channel 会话
- 接收部门频道消息
- 转发到统一推理入口
- 返回 streaming / done / error 语义

### 不负责
- 组织数据查询
- REST 接口语义定义
- 大脑绑定配置管理

## 5.3 `BrainRegistry`

### 负责
- 按 department 查询运行中的 brain
- 维护 brain 的运行时注册关系

### 不负责
- 组织模型全量查询
- `brain-models` 配置管理
- 成员列表查询

## 5.4 `DepartmentChatService`

### 负责
- 统一 REST / WebSocket 的部门脑调用语义
- 统一响应结构
- 统一错误语义
- 统一部门 -> brain 解析
- 统一反馈与审计写入

### 不负责
- WebSocket 会话管理
- 前端入口判定
- provider 配置管理

---

## 6. 与入口路由的关系

部门对话设计必须与 `对话入口逻辑梳理.md` 一致：

```text
# 登录前
/chat（无参数）        -> /ws/public（闲聊神经元）

# 登录后
/chat?brain=...        -> /ws/dept/{brain}（部门大脑硬路由）
/departments/:code     -> 点击"对话" -> /chat?brain={code}&dept={name}
/api/dept/{department} -> 部门信息与部门 chat 的 REST 补充接口
/ws/dept/{brain}       -> 部门脑会话链路
/ws/enterprise         -> 董事长频道
```

### 正确边界
- 登录后统一通过部门大脑对话
- 董事长可访问所有部门，其他仅限本部门
- `DepartmentApiController.chat(...)` 只是部门脑硬路由的 REST 补充
- 不覆盖登录前的闲聊语义
- `DepartmentWebSocketHandler` 只服务于部门脑会话链路

---

## 7. 语音/文字处理设计

### 7.1 文字模式

```
前端键盘输入 → WebSocket 发送文本
    │
    ▼
DepartmentWebSocketHandler.handleChatMessage()
    │
    ▼
processWithBrain()
    │
    ▼
agentService.processTextAsync(sessionId, text, channel)
    │
    ▼
ChatNeuronRouter.route() → 意图分类 → 选择神经元
    │
    ├── GREETING/CASUAL_CHAT → Qwen3Neuron → Python Daemon (service="chat")
    ├── TOOL_CALL → Qwen3Neuron → Python Daemon (自动路由)
    └── COMPLEX_TASK → 部门大脑 → AbstractBrain.executeReActLoop()
    │
    ▼
响应文本 → 前端显示
```

### 7.2 语音模式

**完整音频处理链路**：

```
前端按住说话 → 麦克风录音 → Opus 编码 → Base64
    │
    ▼
WebSocket 发送 "audio" 类型消息 → AgentWebSocketHandler
    │
    ▼
AgentService.processAudioFullChain(sessionId, base64OpusData)
    │
    ├─ 1. Base64 解码 → Base64 字符串 → byte[]
    │
    ├─ 2. AudioNative.Processor.decodeOpus()     ← Rust JNI (opus_codec.rs)
    │      Opus → 解码 → PCM 音频 (16kHz, 单声道)
    │
    ├─ 3. savePcmToWav()
    │      PCM → 保存为临时 WAV 文件
    │
    ├─ 4. modelManager.recognizeSpeech()         ← Sherpa-ONNX ASR
    │      WAV 文件 → ASR 识别 → 文本
    │
    ├─ 5. ChatNeuronRouter.route(recognizedText) → 意图分类 → 选择神经元
    │      同文字模式流程
    │
    ├─ 6. modelManager.synthesizeSpeechRaw()      ← MeloTTS
    │      响应文本 → 合成 → PCM 音频
    │
    ├─ 7. AudioNative.Processor.encodePcm()       ← Rust JNI (opus_codec.rs)
    │      PCM → 编码 → Opus 包列表
    │
    └─ 8. Opus 包 → Base64 编码 → WebSocket 返回 → 前端解码播放
```

### 7.3 Rust 原生 Opus 音频编解码

**核心架构**：Java 负责业务逻辑，Rust 负责音频编解码。

| 层级 | 组件 | 说明 |
|------|------|------|
| Rust 实现 | `opus_codec.rs` | OpusEncoder / OpusDecoder，使用 Rust `opus` crate |
| JNI 绑定 | `audio_jni.rs` | 注册 Java native 方法，桥接 Rust ↔ Java |
| Java 调用 | `AudioNative.Processor` | 提供 `decodeOpus()`、`encodePcm()` 等方法 |
| 编码参数 | 16kHz 采样率、单声道、960 帧大小 | 语音通信标准参数 |
| 默认配置 | 48kHz、单声道、20ms 帧、24kbps 码率 | OpusConfig 默认值 |

**核心文件**：
- [opus_codec.rs](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-native/src/audio/opus_codec.rs) — Opus 编解码器实现
- [audio_jni.rs](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-native/src/jni/audio_jni.rs) — JNI 绑定
- [AgentService.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/service/AgentService.java) — Java 侧调用入口

**音频传输协议**：
- 前端 → 后端：`{"type": "audio", "audio": "<Base64 Opus>", "format": "opus"}`
- 后端 → 前端：`{"type": "audio_response", "audio": "<Base64 Opus>", "text": "..."}`

### 7.4 前端语音开关设计

| 组件 | 说明 |
|------|------|
| 位置 | 对话页右上角（或底部工具栏） |
| 默认状态 | 关闭（文字模式） |
| 开启后 | 输入框变为麦克风按钮，支持语音输入 |
| 权限控制 | 未登录不可使用，登录后根据前端开关决定 |
| 音频格式 | Opus 编码，Base64 传输 |

### 7.5 ASR/TTS 模型说明

| 能力 | 模型 | 说明 |
|------|------|------|
| ASR | Sherpa-ONNX | 通过 `modelManager.recognizeSpeech()` 调用，接收 WAV 文件 |
| TTS | MeloTTS / Supertonic | 通过 `modelManager.synthesizeSpeechRaw()` 调用，返回 PCM 数据 |
| 闲聊 | Qwen3-0.6B / Qwen3.5-2B | Python Daemon DualModelIntentClassifier 自动路由 |

---

## 8. 董事长 vs 普通员工权限控制

### 8.1 后端权限校验

| 接口 | 校验逻辑 |
|------|---------|
| `DepartmentWebSocketHandler.hasDepartmentAccess()` | FULL/founder → 所有部门；普通 → 仅本部门 |
| `DepartmentApiController.chat()` | 同上，通过 `agentService.processTextAsync()` 间接校验 |

### 8.2 前端入口控制

| 页面 | 董事长 | 普通员工 |
|------|--------|---------|
| 部门列表 | 所有部门可点击"对话" | 仅本部门可点击 |
| 部门详情页 | "对话"按钮始终可见 | 仅本部门可见 |
| 通用聊天页 | `/ws/enterprise` | `/ws/dept/{department_code}` |

---

## 9. 建议的落地顺序

## 9.1 P0：统一部门脑推理入口

目标：让 REST 和 WebSocket 都走真实部门脑处理链路。

### 要做什么
1. `DepartmentApiController.chat(...)` 已接入真实链路，验证边界情况
2. `DepartmentChatService` 已抽取，REST 与 WebSocket 共用
3. 统一成功 / 权限不足 / 初始化中 / 无响应 / 异常的响应结构

### 验收标准
- 调用 `POST /api/dept/{department}/chat` 时，返回真实推理结果
- REST 与 WebSocket 对同一部门提问时，成功/失败语义基本一致

## 9.2 P0：修复 Provider 注入初始化顺序问题

目标：确保部门大脑的 Provider 正确注入，使 ReAct 循环可执行。

### 要做什么
1. 修改 `AbstractBrain.start()` 允许更新已运行 Brain 的 context 中的 Provider
2. 或调整 Spring Bean 初始化顺序，确保 Brain 先注册再创建 EmployeeNeuron
3. 详见 `model-pool-to-dialogue-flow-analysis.md` v5

### 验收标准
- 部门大脑 COMPLEX_TASK 意图可正确执行 ReAct 循环
- 不再返回 "Provider 未配置" 错误

## 9.3 P1：前端语音/文字统一

目标：对话页统一语音开关，支持 ASR/TTS 调用。

### 要做什么
1. 创建统一语音开关组件
2. 对接 Python Daemon 的 `asr` 和 `tts` service
3. 语音输入流程与文字输入流程合并

### 验收标准
- 对话页右上角/底部有统一的语音开关
- 开启后可录音输入，ASR 转文本
- 响应可 TTS 合成语音播放

## 9.4 P1：董事长跨部门访问前端支持

目标：董事长可在部门列表选择任意部门进入对话。

### 要做什么
1. 部门列表页根据 `user.access_level` 判断是否显示所有部门
2. 普通员工仅显示本部门
3. 董事长显示所有部门，每个部门有"对话"按钮

### 验收标准
- 董事长可选择任意部门进入对话
- 普通员工仅能进入本部门对话

## 9.5 P1：把成员和 brains 查询接真实数据源

目标：`members` 与 `brains` 不再是示例数据。

### 要做什么
1. `getDepartmentMembers(...)` 接 `EmployeeService.listByDepartment(...)`
2. `getDepartmentBrains(...)` 接 `BrainRegistry` 的真实 brain 状态
3. 如需展示模型绑定，再补接 `brain-models` / `BrainModelAssigner`

## 9.6 P2：补反馈、审计与会话留痕

目标：部门对话可追踪，而不是一次性响应。

### 要做什么
1. REST 与 WebSocket 都补充反馈/审计写入
2. 明确 requestId / sessionId / department / brain / model 等关键字段
3. 区分用户错误、权限错误、模型错误、系统错误

---

## 10. 开发任务清单版

> 本节按"文件名 / 方法名 / 具体改动 / 依赖关系 / 验收标准"整理，便于直接按任务推进。

### 10.1 P0：验证并完善部门脑推理入口

| 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|
| `DepartmentApiController.java` | `chat(...)` | 验证已接入真实链路，修复边界情况 | 依赖 `DepartmentChatService` | REST 部门 chat 返回真实推理结果 |
| `DepartmentWebSocketHandler.java` | `processWithBrain(...)` | 继续作为真实处理链路基线 | 依赖 `AgentService.processTextAsync(...)` | REST 与 WebSocket 语义一致 |

### 10.2 P0：修复 Provider 注入初始化顺序

| 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|
| `AbstractBrain.java` | `start(BrainContext)` | 允许更新 context 中的 Provider | 无 | COMPLEX_TASK 意图可执行 ReAct 循环 |
| `FixedEmployeeRegistry.java` | `init()` | 调整初始化顺序 | 依赖 `BrainRegistry` | EmployeeNeuron 创建时 Brain 已注册 |

### 10.3 P1：前端语音/文字统一

| 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|
| `frontend/src/components/` | 新建 `VoiceToggle` 组件 | 统一语音开关 | 无 | 对话页统一语音控制 |
| `frontend/src/pages/Chat.tsx` | 集成语音开关 | 对接 ASR/TTS | 依赖 `VoiceToggle` | 语音输入/输出正常工作 |

### 10.4 P1：董事长跨部门访问前端支持

| 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|
| `frontend/src/pages/DepartmentList.tsx` | 根据权限显示部门 | 董事长显示所有部门 | 依赖 `user.access_level` | 董事长可选择任意部门 |
| `frontend/src/pages/DepartmentDetail.tsx` | 修复 `canAccessDepartmentBrain` | 改为动态判断 | 依赖 `user.department_code` | 部门"对话"按钮正常可见 |

### 10.5 P1：接入真实成员与 brain 数据源

| 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|
| `DepartmentApiController.java` | `getDepartmentMembers(...)` | 接 `EmployeeService.listByDepartment(...)` | 依赖员工数据源 | members 接口不再返回示例员工 |
| `DepartmentApiController.java` | `getDepartmentBrains(...)` | 接 `BrainRegistry` / 绑定摘要 | 依赖 `BrainRegistry` | brains 接口反映真实运行态 |

### 10.6 P2：补反馈、审计与会话留痕

| 文件名 | 方法名 | 具体改动 | 依赖关系 | 验收标准 |
|---|---|---|---|---|
| `DepartmentChatService.java` | chat 执行与记录方法 | 统一记录 requestId、sessionId、department、brain、model | 依赖反馈/审计服务 | 任一部门对话都可追踪 |

---

## 11. 关键实现建议

### 11.1 统一响应结构

REST 建议统一成：

```json
{
  "success": true,
  "department": "tech",
  "brain": "neuron://tech/tech-brain/001",
  "text": "...",
  "model": "qwen3.5-27b",
  "status": "SUCCESS",
  "reason": null
}
```

失败场景：

```json
{
  "success": false,
  "department": "tech",
  "brain": "neuron://tech/tech-brain/001",
  "status": "INITIALIZING",
  "reason": "模型会话仍在初始化，请稍后重试"
}
```

### 11.2 统一错误分类

至少区分：
- `FORBIDDEN`
- `PERMISSION_DENIED`
- `INITIALIZING`
- `NO_RESPONSE`
- `BRAIN_NOT_FOUND`
- `SYSTEM_ERROR`

### 11.3 统一部门脑解析顺序

```text
department code
  -> 静态映射 Department.mapDepartmentToBrain(...)
  -> BrainRegistry.getByDepartment(...)
  -> 读取运行状态 / 模型绑定摘要
```

---

## 12. 一句话结论

当前部门对话：

- **登录后统一通过部门大脑对话**
- **董事长可访问所有部门，其他仅限本部门**
- **语音/文字由前端对话页面开关控制**
- **WebSocket 和 REST 链路已基本统一，待验证边界情况**
- **Provider 注入初始化顺序问题需修复**

后续重点：

**修复 Provider 注入问题，统一前端语音/文字控制，完善董事长跨部门访问体验。**
