# Claude CLI 本地模型适配与 Living Agent 模型池改造方案

> 日期：2026-05-13
> 修订日期：2026-05-13
> 范围：`free-claude-code-main`、`living-agent-service`
> 目标：调研 `free-claude-code-main` 对本地模型和 Claude CLI 的适配方式，梳理 `living-agent-service` 现状，并给出可落地改造方案。
>
> **本次修订目标**：在原有框架基础上，细化长期方案的具体实现细节——包括数据模型定义、stream-json 事件到任务/产物/审计系统的映射规范、按员工/部门/任务类型的模型选择路由逻辑、接口契约、以及分阶段实施计划中的验收标准。

---

## 1. 结论摘要

`free-claude-code-main` 的核心思路不是直接改 Claude Code CLI，而是在本地启动一个 Anthropic Messages API 兼容代理服务，让 Claude CLI 通过环境变量把所有模型请求打到这个代理，再由代理转发到本地或云端模型。

关键路径如下：

```
Claude Code CLI
  -> ANTHROPIC_API_URL / ANTHROPIC_BASE_URL
  -> free-claude-code-main FastAPI /v1/messages
  -> provider 路由
  -> LM Studio / llama.cpp / OpenRouter / NVIDIA NIM
  -> Anthropic SSE stream 返回给 Claude CLI
```

`living-agent-service` 当前已经具备两条相关能力：

1. 模型池：`ProviderConfig`、`LlmModel`、`BrainModelAssignment`、`BrainModelResolver`、`ResolvedBrainModelProvider` 已能将大脑或员工映射到 OpenAI-compatible 模型。
2. Claude CLI 工具：`ClaudeCliTool` 和 `ClaudeExecutionGateway` 已能执行 `claude -p`、`--resume`、`--output-format stream-json`、`--add-dir`、异步 `start/poll/cancel` 等命令。

但两者还没有打通：

- `ClaudeExecutionGateway` 只负责拼 Claude CLI 参数，没有设置 `ANTHROPIC_API_URL`、`ANTHROPIC_BASE_URL`、`ANTHROPIC_AUTH_TOKEN` 等代理环境变量。
- Java 侧模型池主要面向 OpenAI-compatible `/chat/completions`，没有提供可供 Claude CLI 调用的 Anthropic `/v1/messages` 兼容代理。
- `ProviderFactory` 当前只支持 `OPENAI_COMPATIBLE` 创建 `ResolvedBrainModelProvider`；`ANTHROPIC` 协议虽然在测试客户端中存在，但主 Brain provider 链路会拒绝。
- `ResolvedBrainModelProvider` 不支持 Anthropic Messages schema、SSE streaming、Claude 工具块格式转换。
- Claude CLI 执行网关当前复用了 `SandboxService.SandboxConfig.TRAE_DEFAULT`，语义上可运行但缺少 Claude 专用工作区、环境变量、进程池、会话映射和取消机制。

建议采用"两层适配"方案：

1. **短期**：直接复用 `free-claude-code-main` 作为 Claude CLI 代理，在 `living-agent-service` 中新增 Claude CLI 代理配置并注入执行环境变量。（§5.1）
2. **中期**：在 `living-agent-service` 内实现轻量 Anthropic Messages 兼容代理，把模型池中的 OpenAI-compatible、本地 LM Studio、llama.cpp、Ollama 等模型暴露给 Claude CLI。（§5.2）
3. **长期**：把 Claude CLI 作为 tech/代码执行类工具纳入模型池与员工调度，支持按员工/部门/任务类型选择不同代理模型，并将 stream-json 事件接入任务、产物和审计系统。（§5.3-§5.6）

---

## 2. free-claude-code-main 适配方式调研

### 2.1 代理服务入口

`free-claude-code-main` 通过 FastAPI 暴露 Anthropic 兼容接口：

| 端点 | 方法 | 用途 |
|------|------|------|
| `/v1/messages` | POST | 主消息流（SSE） |
| `/v1/messages/count_tokens` | POST | Token 计数 |
| `/health` | GET | 健康检查 |
| `/stop` | POST | 停止当前请求 |

`/v1/messages` 处理流水线：

```
请求进入 api.routes.create_message()
  -> 解析 resolved_provider_model 或全局 settings.model
  -> 按 provider_type 路由到对应 provider
  -> Anthropic -> OpenAI 转换（如需要）
  -> 转发到后端模型服务
  -> 原始响应 -> SSEBuilder 格式化为 Anthropic SSE
  -> StreamingResponse(..., media_type="text/event-stream")
```

### 2.2 模型配置与模型名映射

`config.settings.Settings` 定义：

```python
MODEL         = "provider_type/model/name"     # 默认模型
MODEL_OPUS    = "<override for opus>"          # Opus 请求覆盖
MODEL_SONNET  = "<override for sonnet>"        # Sonnet 请求覆盖
MODEL_HAIKU   = "<override for haiku>"         # Haiku 请求覆盖
```

支持的 provider type：

| Provider | 环境变量 | 默认 URL | 转换方式 |
|----------|---------|---------|---------|
| `nvidia_nim` | NIM_BASE_URL | - | 直接转发或转换 |
| `open_router` | OPEN_ROUTER_BASE_URL | https://openrouter.ai/api | 标准 OpenAI 兼容 |
| `lmstudio` | LM_STUDIO_BASE_URL | http://localhost:1234/v1 | 原样转发或转换 |
| `llamacpp` | LLAMACPP_BASE_URL | http://localhost:8080/v1 | 原样转发或转换 |

### 2.3 Anthropic -> OpenAI 转换

核心类：`providers.common.message_converter.AnthropicToOpenAIConverter`

| Anthropic 元素 | OpenAI 映射 | 说明 |
|---------------|------------|------|
| `system` (string/list) | `messages[{"role":"system",...}]` | 列表格式转 content array |
| `messages[].role=user` | `messages[{"role":"user",...}]` | 直转 |
| `messages[].role=assistant` (text block) | `messages[{"role":"assistant","content":"..."}]` | 文本转 content |
| `messages[].role=assistant` (thinking block) | `messages[{"role":"assistant","content":"<thinking>...\n\n...\n</thinking>"}]` | thinking -> 标签包裹；OpenRouter 额外保留 `reasoning_content` |
| `messages[].role=assistant` (tool_use block) | `messages[{"role":"assistant","tool_calls":[...]}]` | 每个 tool_use 转一个 tool_call |
| `messages[].role=user` (tool_result) | `messages[{"role":"tool","tool_call_id":"...","content":"..."}]` | role 映射为 tool |
| `tools` array | `tools` array | function schema 转换：name/description/input_schema |
| `max_tokens` | `max_tokens` | 透传 |

### 2.4 OpenAI stream -> Anthropic SSE 转换

需要实现 `SSEBuilder` 逻辑：

```
OpenAI stream chunk (delta.content)
  -> ThinkTagParser 检测 <thinking>...</thinking>
  -> HeuristicToolParser 检测工具调用模式
  -> 组装为 Anthropic SSE event：
     - message_start: {"type":"message_start","message":{"stop_reason":null,"role":"assistant","content":[],"model":"...","stop_sequence":null,"usage":{"input_tokens":0,"output_tokens":0}}}
     - content_block_start: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}
     - content_block_delta: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"..."}}
     - content_block_stop: {"type":"content_block_stop","index":0}
     - message_delta: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":...}}
     - message_stop: {"type":"message_stop"}
     - error: {"type":"error","error":{"type":"...",...}}
```

### 2.5 Claude CLI 会话管理

`cli.session.CLISession.start_task()` 是关键：

- 启动命令：`claude -p <prompt> --output-format stream-json --dangerously-skip-permissions --verbose`
- 恢复会话：`claude --resume <session_id>` + 可选 `--fork-session`
- 自动注入环境变量：

```bash
ANTHROPIC_API_KEY=sk-placeholder-key-for-proxy
ANTHROPIC_API_URL=<proxy api url>        # 如 http://localhost:8082/v1
ANTHROPIC_BASE_URL=<proxy base url>      # 如 http://localhost:8082
TERM=dumb
PYTHONIOENCODING=utf-8
```

- 支持 `--add-dir <path>` 白名单
- 从 stream-json stdout 提取 `session_id` / `sessionId` 建立映射
- 每个会话独立 CLI subprocess

---

## 3. living-agent-service 现状梳理

### 3.1 ClaudeCliTool — 工具层封装

`ClaudeCliTool` 提供名称为 `claude_cli` 的工具能力：

| 方法 | 用途 |
|------|------|
| `prompt` | 发送任务给 Claude CLI |
| `resume` | 恢复已有 Claude session |
| `status` | 查询 CLI session 状态 |
| `start` | 异步启动 Claude CLI job |
| `poll` | 轮询异步 job 状态 |
| `cancel` | 取消异步 job |

参数映射接近 `free-claude-code-main`：

```java
ClaudeCliToolParams {
    String prompt;
    String resumeSessionId;     // --resume
    Boolean forkSession;        // --fork-session
    String outputFormat;        // --output-format (默认 stream-json)
    Boolean verbose;            // --verbose
    List<String> allowedDirs;   // --add-dir (多个)
    Map<String, Object> options; // --settings JSON
}
```

工具执行链路：

```
ClaudeCliTool.execute(params)
  -> 拼装默认参数: dangerously_skip_permissions=true
  -> ClaudeExecutionGateway.execute(params)
  -> SandboxSession.executeCommand("claude", args)
  -> 等待 stdout JSON event lines
  -> 提取 session_id 并建立映射
  -> 返回 ClaudeExecutionResult
```

### 3.2 ClaudeExecutionGateway — 执行网关

当前维护的映射：

| 映射 | 类型 | 用途 |
|------|------|------|
| sessionId -> SandboxSession | `Map<String, SandboxSession>` | 会话->进程 |
| sessionId -> ClaudeSessionState | `Map<String, ClaudeSessionState>` | 会话状态机 |
| jobId -> ClaudeAsyncJob | `Map<String, ClaudeAsyncJob>` | 异步任务管理 |

参数拼装支持：
- `--resume <id>`、`--fork-session`、`-p <prompt>`
- `--output-format <format>`、`--verbose`
- `--dangerously-skip-permissions`
- `--add-dir <path>`（多个）、`--settings <json>`

**当前主要缺口**：

| 缺口编号 | 问题 | 影响 |
|---------|------|------|
| G1 | 无 Claude CLI 专用环境变量注入 | 无法指向代理，无法选择模型 |
| G2 | 无代理 URL/鉴权 token/模型选择配置 | 硬编码或缺失 |
| G3 | 无 temp session <-> real Claude session 映射持久化 | resume/fork 不可靠 |
| G4 | 异步 cancel 仅 `CompletableFuture.cancel(true)` | 子进程可能残留 |
| G5 | 复用 `SandboxConfig.TRAE_DEFAULT` | 缺少 Claude 专用 sandbox config |
| G6 | stdout 集中解析，无实时推送 | 无法流式回传 stream-json 事件 |
| G7 | 无工作区隔离策略 | 安全风险提示 |
| G8 | 无进程池管理 | 无法并发管理多个 CLI 实例 |

### 3.3 模型池现状

#### 3.3.1 现有组件

| 组件 | 职责 |
|------|------|
| `ProviderConfig` | 供应商连接配置 |
| `LlmModel` | 模型定义（providerId, modelId, modelType, status） |
| `BrainModelAssignment` | Brain/员工到模型的分配 |
| `ModelPoolManager` | 模型池 CRUD + 健康检查 |
| `BrainModelResolver` | 模型选择逻辑（显式分配->Selector->推荐->fallback） |
| `ModelHealthRegistry` | 模型健康状态 |
| `LlmClientFactory` | 创建 LlmClient |
| `ResolvedBrainModelProvider` | 运行时执行器（OpenAI `/chat/completions`） |

#### 3.3.2 模型选择逻辑（BrainModelResolver.resolve）

```
resolve(brainId, employeeId, taskId?)
  -> 1. BrainModelAssignment 查找显式分配
  -> 2. Employee.department -> Selector 匹配
  -> 3. 推荐模型（推荐模型优先级表）
  -> 4. 第一个可用模型
  -> 5. Ollama fallback
```

`resolveForEmployee(employeeId)` 可按员工和部门选择执行模型。

#### 3.3.3 主要限制

| 限制编号 | 问题 | 影响 |
|---------|------|------|
| L1 | `ProviderFactory` 仅支持 `Protocol.OPENAI_COMPATIBLE` | Anthropic 协议无法作为主 provider |
| L2 | `LlmClientFactory` 中 `AnthropicClient` 仅用于测试 | 生产链路不走 |
| L3 | `OpenAiCompatibleClient` 和 `ResolvedBrainModelProvider` 不支持 streaming | 无法流式处理 |
| L4 | `ResolvedBrainModelProvider` 输出 OpenAI payload | 不能服务 Claude CLI |
| L5 | 模型池不区分 Brain 对话模型和 Claude CLI 代理模型 | 无法针对性选择 |
| L6 | `Protocol` 无 `ANTHROPIC_PROXY` / `CLAUDE_CODE_PROXY` 概念 | 缺少路由标识 |

#### 3.3.4 现有数据模型（关键字段）

```
ProviderConfig {
    id: String,
    name: String,
    protocol: Protocol,          // OPENAI_COMPATIBLE / ANTHROPIC / GEMINI / OPENAI_RESPONSES
    endpoint: String,
    apiKey: Secret,
    status: ACTIVE / INACTIVE,
    config: Map<String, Object>  // 扩展字段
}

LlmModel {
    id: String,
    providerId: String,
    modelId: String,              // "qwen3-coder", "gpt-4", 等
    modelType: CHAT / COMPLETION / EMBEDDING
    status: String,
    description: String,
    metadata: Map<String, Object> // 扩展元数据
}

BrainModelAssignment {
    id: String,
    targetType: BRAIN / EMPLOYEE / DEPARTMENT
    targetId: String,
    modelId: String,
    priority: Integer,
    status: ACTIVE / INACTIVE
}
```

---

## 4. 目标架构

### 4.1 总体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                       Living Agent 入口                          │
├───────────────────────┬─────────────────────────────────────────┤
│   Brain 对话链路       │         Tech 员工 / 代码执行链路         │
│                       │                                         │
│ BrainModelResolver    │ ClaudeCliTool                           │
│     resolve()         │     prompt()                            │
│         ↓             │         ↓                               │
│ ResolvedBrainModel    │ ClaudeExecutionGateway                  │
│   Provider            │   inject env vars                       │
│         ↓             │         ↓                               │
│  OpenAI-compatible    │  claude CLI (stream-json)               │
│    /chat/completions  │         ↓                               │
│                       │  Anthropic Messages Proxy (本节核心)     │
│                       │         ↓                               │
│                       │  ├─ Anthropic <-> OpenAI 转换器          │
│                       │  ├─ SSE Builder                         │
│                       │  └─ 模型选择路由器                       │
│                       │         ↓                               │
│                       │  ├─ LM Studio / llama.cpp / Ollama      │
│                       │  ├─ OpenRouter / NVIDIA NIM             │
│                       │  └─ Anthropic API (云端)                │
└───────────────────────┴─────────────────────────────────────────┘
```

### 4.2 核心设计原则

1. **Claude CLI 不直接知道模型池**：CLI 只通过环境变量指向 Anthropic-compatible proxy，proxy 负责模型路由。
2. **模型池是源**：所有模型选择决策的起点是 `ModelPoolManager` + `LlmModel`，proxy 通过该选择结果。
3. **Proxy 是翻译层**：负责协议转换（Anthropic <-> OpenAI）和 SSE 事件格式化。
4. **ClaudeExecutionGateway 是进程管理器**：负责进程、工作区、权限、会话、事件解析。
5. **技术员工与 Brain 对话解耦**：Claude CLI 是"工具"而非"大脑"，通过工具调用链路接入，不替代普通 Brain 对话。

### 4.3 数据流

```
1. 触发：用户/系统通过 ToolExecutionEngine 调用 claude_cli(prompt=..., ...)
2. 参数构建：ClaudeExecutionGateway 按员工/部门/任务类型从模型池获取代理模型配置
3. 环境准备：创建工作区目录，注入 ANTHROPIC_API_URL / BASE_URL / AUTH_TOKEN
4. 进程启动：spawn claude -p <prompt> --output-format stream-json --dangerously-skip-permissions ...
5. 流式处理：stdout -> stream-json parser -> AutonomyTraceEvent -> 持久化到 ArtifactRecord
6. 结果聚合：最终响应回传给调用方，包含 session_id、产出文件列表、事件摘要
```

### 4.4 组件分层

| 层 | 组件 | 职责 |
|----|------|------|
| L0 工具层 | `ClaudeCliTool` | 对外暴露工具接口 |
| L1 网关层 | `ClaudeExecutionGateway` | 进程管理、环境变量、会话映射 |
| L2 Proxy 层 | `ClaudeProxyService` / `ClaudeProxyController` | 协议转换、SSE 格式化 |
| L3 模型路由层 | `ClaudeProxyModelRouter` | 按员工/部门/任务类型选择模型 |
| L4 模型池层 | `ModelPoolManager` + `LlmModel` | 模型注册、发现、健康检查 |
| L5 执行层 | `OpenAiCompatibleClient` / 原生 Anthropic Client | 实际 HTTP 请求 |

### 4.5 新增组件总览

| 新增组件 | 包路径 | 职责 |
|----------|--------|------|
| `ClaudeCliProperties` | `living-agent-config` | 代理连接配置 |
| `ClaudeCliEnvInjector` | `living-agent-core/tools` | 环境变量注入 |
| `ClaudeProxyController` | `living-agent-gateway` | `/v1/messages` REST/SSE 端点 |
| `ClaudeProxyService` | `living-agent-core/proxy` | 代理核心逻辑 |
| `AnthropicOpenAiConverter` | `living-agent-core/proxy/converter` | Anthropic <-> OpenAI 双向转换 |
| `ClaudeProxyModelRouter` | `living-agent-core/proxy/router` | 按员工/部门/任务类型的模型选择 |
| `StreamJsonEventMapper` | `living-agent-core/trace` | stream-json -> AutonomyTraceEvent |
| `ClaudeSessionRepository` | `living-agent-persistence` | 会话/任务持久化 |
| `ClaudeProcessPool` | `living-agent-core/runtime` | CLI 进程池管理 |
| `Protocol.CLAUDE_PROXY` | `living-agent-core/model` | 模型协议枚举扩展 |
| `usage_tags` | `LlmModel.metadata` | 模型用途标签 |

---
