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

## 5. 改造方案（详细版）

### 5.1 短期方案：复用 free-claude-code-main 作为外部代理

#### 5.1.1 新增配置

在 `application.yml` 或配置类中增加：

```yaml
living-agent:
  claude-cli:
    enabled: true
    command: claude
    workspace: ./agent_workspace
    proxy-base-url: http://host.docker.internal:8082
    proxy-api-url: http://host.docker.internal:8082/v1
    auth-token: ${CLAUDE_PROXY_AUTH_TOKEN:}
    api-key-placeholder: sk-placeholder-key-for-proxy
    default-output-format: stream-json
    dangerously-skip-permissions: true
    allowed-dirs:
      - ./agent_workspace
    max-concurrent-sessions: 3
    session-timeout-minutes: 30
    job-timeout-minutes: 60
    stream-push-enabled: true
    stream-push-endpoint: /api/v1/events/claude
```

对应的 Java 配置类：

```java
@Data
@ConfigurationProperties(prefix = "living-agent.claude-cli")
public class ClaudeCliProperties {
    private Boolean enabled = true;
    private String command = "claude";
    private String workspace = "./agent_workspace";
    private String proxyBaseUrl = "http://localhost:8082";
    private String proxyApiUrl = "http://localhost:8082/v1";
    private String authToken = "";
    private String apiKeyPlaceholder = "sk-placeholder-key-for-proxy";
    private String defaultOutputFormat = "stream-json";
    private Boolean dangerouslySkipPermissions = true;
    private List<String> allowedDirs = List.of("./agent_workspace");
    private Integer maxConcurrentSessions = 3;
    private Integer sessionTimeoutMinutes = 30;
    private Integer jobTimeoutMinutes = 60;
    private Boolean streamPushEnabled = true;
    private String streamPushEndpoint = "/api/v1/events/claude";
}
```

#### 5.1.2 改造 ClaudeExecutionGateway — 环境变量注入

修改执行方法以支持环境变量注入。在 `ClaudeExecutionGateway` 中新增：

```java
public class ClaudeExecutionGateway {
    private final ClaudeCliProperties properties;
    private final SandboxService sandboxService;
    
    // 映射: livingSessionId -> (tempSessionId -> realSessionId)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> sessionMapping = new ConcurrentHashMap<>();
    // 进程池: sessionId -> ProcessHandle
    private final ConcurrentHashMap<String, ProcessHandle> processRegistry = new ConcurrentHashMap<>();
    
    /**
     * 注入 Claude CLI 所需环境变量
     */
    private Map<String, String> buildEnvMap(String sessionId) {
        Map<String, String> env = new HashMap<>();
        env.put("ANTHROPIC_API_KEY", properties.getApiKeyPlaceholder());
        env.put("ANTHROPIC_API_URL", properties.getProxyApiUrl());
        env.put("ANTHROPIC_BASE_URL", properties.getProxyBaseUrl());
        if (StringUtils.hasText(properties.getAuthToken())) {
            env.put("ANTHROPIC_AUTH_TOKEN", properties.getAuthToken());
        }
        env.put("TERM", "dumb");
        env.put("PYTHONIOENCODING", "utf-8");
        env.putAll(buildCustomEnv(sessionId));
        return env;
    }
    
    /**
     * 构建自定义环境变量（由模型路由结果决定）
     */
    private Map<String, String> buildCustomEnv(String sessionId) {
        Map<String, String> extra = new HashMap<>();
        ClaudeSessionState state = getSessionState(sessionId);
        if (state != null && state.getProxyModel() != null) {
            extra.put("MODEL", state.getProxyModel());
            if (state.getProxyModel().startsWith("lmstudio/")) {
                extra.put("LM_STUDIO_BASE_URL", properties.getLmStudioBaseUrl());
            }
            if (state.getProxyModel().startsWith("llamacpp/")) {
                extra.put("LLAMACPP_BASE_URL", properties.getLlamaCppBaseUrl());
            }
        }
        return extra;
    }
}
```

#### 5.1.3 执行链路改造

```java
public ClaudeExecutionResult execute(ClaudeCliRequest request) {
    String sessionId = generateSessionId();
    
    // 1. 创建工作区
    String workspace = createWorkspace(sessionId, request.getAllowedDirs());
    
    // 2. 注入环境变量（含模型选择结果）
    Map<String, String> env = buildEnvMap(sessionId);
    
    // 3. 拼装 CLI 命令
    List<String> args = buildCliArgs(request, sessionId);
    
    // 4. 启动进程
    ProcessHandle process = sandboxService.executeClaudeCommand(
        properties.getCommand(), args, env, workspace
    );
    processRegistry.put(sessionId, process);
    
    // 5. 启动事件流监听器（后台线程）
    eventStreamListener.start(sessionId, process.getInputStream(), properties);
    
    return new ClaudeExecutionResult(sessionId, ClaudeSessionState.RUNNING);
}
```

#### 5.1.4 运行方式

外部先启动 `free-claude-code-main`：

```bash
MODEL=lmstudio/qwen/qwen3-coder
LM_STUDIO_BASE_URL=http://host.docker.internal:1234/v1
ANTHROPIC_AUTH_TOKEN=<token>
PORT=8082
python -m free_claude_code.main
```

Living Agent 中 Claude CLI 工具调用时指向：

```yaml
proxy-base-url: http://host.docker.internal:8082
proxy-api-url: http://host.docker.internal:8082/v1
```

**优点**：改动最小，不影响现有 Brain 对话链路，可快速验证 Claude CLI + 本地模型工作流，复用已有转换/SSE/session 管理。

**缺点**：模型选择在 `free-claude-code-main` 的 `.env` 中，未接入模型池；多服务部署和监控复杂；认证、审计、流式事件需要跨服务整合。

### 5.2 中期方案：Java 侧内置 Anthropic Messages 兼容代理

#### 5.2.1 新增组件概览

在 `living-agent-service` 中新增以下组件，将 `free-claude-code-main` 的核心能力本地化：

```
living-agent-gateway/
  └─ proxy/
      ├─ ClaudeProxyController.java       # REST/SSE 端点 /v1/messages
      └─ ClaudeProxyWebSocket.java        # WebSocket 扩展（可选）
living-agent-core/
  └─ proxy/
      ├─ ClaudeProxyService.java          # 代理核心逻辑
      ├─ ClaudeProxyModelRouter.java      # 模型选择路由器
      ├─ converter/
      │   ├─ AnthropicToOpenAiConverter.java   # Anthropic->OpenAI
      │   ├─ OpenAiToAnthropicConverter.java   # OpenAI->Anthropic
      │   └─ ClaudeToolBlockMapper.java        # Claude tool_use <-> OpenAI tool_calls
      └─ sse/
          └─ AnthropicSseBuilder.java         # OpenAI stream -> Anthropic SSE
living-agent-config/
  └─ ClaudeProxyProperties.java             # 代理配置类
living-agent-persistence/
  └─ ClaudeSessionRepository.java           # 会话/任务持久化
```

#### 5.2.2 代理控制器 — `/v1/messages` 端点

```java
@RestController
@RequestMapping("/api/v1/proxy/anthropic")
public class ClaudeProxyController {
    private final ClaudeProxyService proxyService;
    private final StreamEventPusher streamPusher;
    
    /**
     * 主消息端点（SSE 流式响应）
     */
    @PostMapping("/v1/messages")
    public SseEmitter createMessage(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestHeader(value = "x-api-key", required = false) String xApiKey,
        @RequestBody AnthropicMessagesRequest request,
        @RequestParam(value = "sessionId", required = false) String externalSessionId,
        @RequestParam(value = "employeeId", required = false) String employeeId
    ) throws IOException {
        
        // 1. 鉴权
        validateAuth(authHeader, xApiKey);
        
        // 2. 按 employeeId 选择模型
        String resolvedModel = proxyService.resolveModel(
            request, employeeId, externalSessionId
        );
        
        // 3. 调用模型（流式）
        SseEmitter emitter = new SseEmitter(0L); // 无超时
        
        proxyService.forwardToModel(
            resolvedModel, request, emitter, externalSessionId
        );
        
        // 4. 同时推送事件到 stream-push 系统
        streamPusher.pushFromEmitter(
            emitter, externalSessionId, resolvedModel
        );
        
        return emitter;
    }
    
    /**
     * 停止请求
     */
    @PostMapping("/v1/messages/{sessionId}/stop")
    public ResponseEntity<Void> stopRequest(
        @PathVariable String sessionId
    ) {
        proxyService.cancelSession(sessionId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Token 计数
     */
    @PostMapping("/v1/messages/count_tokens")
    public ResponseEntity<AnthropicTokenCountResponse> countTokens(
        @RequestBody AnthropicTokenCountRequest request
    ) {
        // 使用 Claude CLI 自身的 token 计数能力
        // 或调用 proxy-model 的 /count_tokens 端点
        var response = proxyService.countTokens(request);
        return ResponseEntity.ok(response);
    }
}
```

#### 5.2.3 代理核心服务 — 模型转发

```java
@Service
public class ClaudeProxyService {
    private final AnthropicOpenAiConverter converter;
    private final ClaudeProxyModelRouter modelRouter;
    private final LlmClientFactory clientFactory;
    private final StreamJsonEventMapper eventMapper;
    private final ClaudeSessionRepository sessionRepo;
    
    /**
     * 按员工/部门/任务类型选择模型
     */
    public String resolveModel(
        AnthropicMessagesRequest request,
        String employeeId,
        String externalSessionId
    ) {
        // 从请求上下文提取 taskType（从 prompt 或 metadata）
        String taskType = extractTaskType(request);
        
        // 调用模型路由
        ModelResolutionResult result = modelRouter.resolve(
            employeeId, taskType, externalSessionId
        );
        
        return result.getModelId();
    }
    
    /**
     * 转发请求到选定模型（流式）
     */
    public void forwardToModel(
        String modelId,
        AnthropicMessagesRequest anthropicRequest,
        SseEmitter emitter,
        String sessionId
    ) {
        // 1. 构建 Claude session 状态
        ClaudeSessionState state = sessionRepo.save(
            new ClaudeSessionState(sessionId, modelId, ClaudeSessionState.RUNNING)
        );
        
        // 2. Anthropic request -> OpenAI request
        OpenAiChatCompletionRequest openAiRequest = 
            converter.toOpenAi(anthropicRequest, modelId);
        
        // 3. 调用模型（流式）
        LlmClient client = clientFactory.create(modelId);
        Flux<OpenAiChatCompletionChunk> stream = client.streamChat(openAiRequest);
        
        // 4. 转换 stream + 写入 emitter + 映射事件
        stream.subscribe(
            chunk -> {
                // 转换并写入 SSE
                AnthropicSseEvent sseEvent = AnthropicSseBuilder.fromChunk(chunk, modelId);
                emitter.send(SseEmitter.event()
                    .name("message")
                    .data(sseEvent.toJson())
                );
                
                // 映射到 autonomy-trace 事件
                AutonomyTraceEvent traceEvent = eventMapper.map(chunk);
                traceEvent.setSessionId(sessionId);
                traceEvent.setModelId(modelId);
                sessionRepo.saveTraceEvent(traceEvent);
            },
            error -> {
                emitter.send(SseEmitter.event()
                    .name("error")
                    .data(AnthropicSseBuilder.fromError(error))
                );
                sessionRepo.updateStatus(sessionId, ClaudeSessionState.ERROR);
                emitter.completeWithError(error);
            },
            () -> {
                emitter.send(SseEmitter.event()
                    .name("message")
                    .data(AnthropicSseBuilder.buildMessageEnd(sessionId, modelId))
                );
                sessionRepo.updateStatus(sessionId, ClaudeSessionState.COMPLETED);
                emitter.complete();
            }
        );
    }
}
```

#### 5.2.4 模型路由器 — 按员工/部门/任务类型选择

```java
@Service
public class ClaudeProxyModelRouter {
    private final ModelPoolManager modelPoolManager;
    private final BrainModelResolver brainModelResolver;
    private final ClaudeCliProperties properties;
    
    /**
     * 模型选择路由逻辑
     * 
     * 优先级：
     * 1. ClaudeCliProperties 中按 modelSelectorMode 定义的全局路由规则
     * 2. 按 employeeId 查找 Employee 的 department + taskType
     * 3. 查询 BrainModelAssignment 获取该 employee/department 的显式分配
     * 4. 按 usage_tags 过滤出 "claude-proxy" 类型的模型
     * 5. 调用 BrainModelResolver.resolveForEmployee()
     */
    public ModelResolutionResult resolve(
        String employeeId,
        String taskType,
        String externalSessionId
    ) {
        ModelSelectorMode mode = properties.getModelSelectorMode();
        
        switch (mode) {
            case FIXED_BY_CONFIG:
                // 模式 A：所有 Claude CLI 请求使用固定模型
                return resolveByFixedConfig();
                
            case SELECTOR_RULES:
                // 模式 B：按 selectorRules 匹配
                return resolveBySelectorRules(employeeId, taskType);
                
            case BRAIN_MODEL_RESOLVER:
                // 模式 C：复用 BrainModelResolver
                return resolveByBrainModelResolver(employeeId, taskType);
                
            default:
                throw new IllegalStateException("Unknown mode: " + mode);
        }
    }
    
    /**
     * 模式 A：固定模型配置
     */
    private ModelResolutionResult resolveByFixedConfig() {
        String modelId = properties.getProxyDefaultModel();
        if (!StringUtils.hasText(modelId)) {
            modelId = modelPoolManager.getFirstActiveModel("claude-proxy");
        }
        return new ModelResolutionResult(modelId, Mode.FIXED_BY_CONFIG);
    }
    
    /**
     * 模式 B：按 selectorRules 匹配
     */
    private ModelResolutionResult resolveBySelectorRules(
        String employeeId, String taskType
    ) {
        Employee employee = employeeService.getById(employeeId);
        String department = employee.getDepartment();
        
        for (SelectorRule rule : properties.getSelectorRules()) {
            if (rule.matches(department, taskType, employeeId)) {
                String modelId = rule.getModelId();
                return new ModelResolutionResult(modelId, Mode.SELECTOR_RULES);
            }
        }
        
        // 默认规则
        String defaultModel = properties.getProxyDefaultModel();
        return new ModelResolutionResult(defaultModel, Mode.DEFAULT);
    }
    
    /**
     * 模式 C：复用 BrainModelResolver
     */
    private ModelResolutionResult resolveByBrainModelResolver(
        String employeeId, String taskType
    ) {
        // 复用已有逻辑
        LlmModel model = brainModelResolver.resolveForEmployee(employeeId);
        
        // 但需要确保模型有 "claude-proxy" 用途标签
        // 若无，则尝试匹配 usage_tags 包含 "tech" 或 "code" 的模型
        if (!hasUsageTag(model, "claude-proxy")) {
            model = modelPoolManager.findByUsageTag("tech-executor", employeeId);
        }
        
        return new ModelResolutionResult(model.getId(), Mode.BRAIN_MODEL_RESOLVER);
    }
    
    private boolean hasUsageTag(LlmModel model, String tag) {
        Map<String, Object> metadata = model.getMetadata();
        if (metadata != null && metadata.containsKey("usage_tags")) {
            Object tags = metadata.get("usage_tags");
            if (tags instanceof List) {
                return ((List<?>) tags).contains(tag);
            }
        }
        return false;
    }
}
```

#### 5.2.5 `application.yml` 路由配置

```yaml
living-agent:
  claude-cli:
    # 模型选择模式: FIXED_BY_CONFIG / SELECTOR_RULES / BRAIN_MODEL_RESOLVER
    model-selector-mode: SELECTOR_RULES
    
    # 模式 A 用
    proxy-default-model: lmstudio/qwen/qwen3-coder:7b
    
    # 模式 B 用
    selector-rules:
      - department: "engineering"
        task-type-pattern: "^code.*"
        model-id: "lmstudio/qwen/qwen3-coder:7b"
        priority: 10
      - department: "engineering"
        task-type-pattern: "^test.*"
        model-id: "lmstudio/qwen/qwen3-coder:32b"
        priority: 20
      - department: "qa"
        task-type-pattern: ".*"
        model-id: "nvidia_nim/nemotron-4-340b"
        priority: 10
      - department: ".*"
        task-type-pattern: "^review.*"
        model-id: "anthropic/claude-sonnet-4-20250514"
        priority: 5
        
    # 本地模型地址
    lm-studio-base-url: http://host.docker.internal:1234/v1
    llamacpp-base-url: http://host.docker.internal:8080/v1
    ollama-base-url: http://host.docker.internal:11434/v1
```

**优点**：模型选择完全可控，与模型池深度集成，支持按员工/部门/任务类型的精细化路由，所有服务部署在同一 JVM。

**缺点**：需要实现完整的协议转换层（约 3000-5000 行代码），包括 Anthropic<->OpenAI 双向转换、SSE 格式化、Claude tool_use 映射。

### 5.3 长期方案：深度集成

#### 5.3.1 Claude CLI 作为 Tech 员工工具纳入模型池

在 `LlmModel` 中新增 `usage_tags` 字段，标记模型用途：

```json
{
    "id": "model-001",
    "providerId": "provider-lmstudio",
    "modelId": "qwen3-coder:7b",
    "modelType": "CHAT",
    "status": "ACTIVE",
    "metadata": {
        "usage_tags": ["claude-proxy", "tech-executor", "code-gen"],
        "max-context-window": 128000,
        "supports-tool-use": true,
        "proxy-type": "internal"
    }
}
```

在 `BrainModelAssignment` 中新增 `purpose` 字段：

| 字段 | 值 | 说明 |
|------|------|------|
| `purpose` | `BRAIN` | Brain 对话模型 |
| `purpose` | `TOOL` | 工具执行模型 |
| `purpose` | `CLAUDE_PROXY` | Claude CLI 代理模型 |

##### 5.3.1.1 员工角色模型

Tech 员工通过 `Employee` 实体的 `role` + `department` + `skill_tags` 三维标识来决定模型选择：

| 维度 | 值域 | 示例 |
|------|------|------|
| `role` | `CODER` / `QA` / `DEVOPS` / `ARCHITECT` / `TECH_LEAD` | 决定模型基础偏好 |
| `department` | `ENGINEERING` / `QA` / `INFRA` / `DATA` | 部门级默认模型 |
| `skill_tags` | `["python", "java", "devops", "frontend"]` | 技能标签影响工具执行偏好 |

##### 5.3.1.2 任务类型分类

`Task` 实体的 `category` 字段用于细化模型选择：

| `category` 值 | 说明 | 推荐模型特征 |
|---------------|------|-------------|
| `CODE_GENERATION` | 代码生成 | 强代码能力模型 (qwen3-coder, deepseek-coder) |
| `CODE_REVIEW` | 代码审查 | 强理解能力模型 (claude-sonnet, gpt-4) |
| `CODE_REFACTOR` | 代码重构 | 强代码理解 + 安全性 |
| `DEBUGGING` | 问题排查 | 强推理能力模型 |
| `DOC_GENERATION` | 文档生成 | 通用强文本模型 |
| `INFRA_DEPLOY` | 基础设施部署 | 强 Bash/DevOps 能力 |
| `DATA_PROCESS` | 数据处理 | 强推理 + 数学能力 |
| `TEST_GENERATION` | 测试代码生成 | 代码 + 测试框架理解 |

##### 5.3.1.3 三层路由决策链

Claude CLI 的模型选择遵循三层决策链，按优先级从高到低：

```
第 1 层：Task 级硬编码 (Task.overrideModelId)
  → 如果 Task 显式指定了 overrideModelId，直接使用，跳过其余层
  → 用于紧急任务、特定要求任务

第 2 层：员工级规则匹配 (Employee + TaskCategory)
  → 查询 Employee.department + Employee.role + Task.category
  → 匹配 SelectorRule 规则表 (见 5.3.2)
  → 取最高优先级匹配项

第 3 层：部门级默认 + 兜底 (Department default model)
  → Department.defaultModelId
  → 若为空，降级为 pool-first-active("claude-proxy")
```

决策逻辑伪代码：

```java
public ModelResolutionResult resolveForClaudeCli(
    String employeeId, Task task, String taskType
) {
    // 第 1 层: Task 硬编码
    if (task.getOverrideModelId() != null) {
        return new ModelResolutionResult(task.getOverrideModelId(),
            RoutingLayer.TASK_OVERRIDE);
    }
    
    // 第 2 层: 员工级规则
    Employee employee = employeeService.getById(employeeId);
    String department = employee.getDepartment();
    String role = employee.getRole();
    
    // 从配置或 DB 查询匹配规则
    SelectorRule rule = selectorRuleRepository.findBestMatch(
        department, role, task.getCategory()
    );
    if (rule != null) {
        return new ModelResolutionResult(rule.getModelId(),
            RoutingLayer.EMPLOYEE_RULE, rule.getId());
    }
    
    // 第 3 层: 部门默认
    Department dept = departmentService.getById(department);
    if (dept.getDefaultModelId() != null) {
        return new ModelResolutionResult(dept.getDefaultModelId(),
            RoutingLayer.DEPARTMENT_DEFAULT);
    }
    
    // 兜底: 池中最合适的 claude-proxy 模型
    String fallbackModel = modelPoolManager.getFirstActiveModel("claude-proxy");
    return new ModelResolutionResult(fallbackModel, RoutingLayer.FALLBACK);
}
```

##### 5.3.1.4 模型优先级配置

在 `application.yml` 中通过 `model-selector-rules` 定义规则：

```yaml
living-agent:
  claude-cli:
    model-selector-mode: SELECTOR_RULES
    
    model-selector-rules:
      # 规则 1: 工程团队代码生成 → qwen3-coder 7B (低成本高速度)
      - rule-id: "eng-code-gen"
        department: "engineering"
        role: "CODER"
        task-type-pattern: "^(CODE_GENERATION|CODE_REFACTOR)$"
        model-id: "model-qwen3-coder-7b"
        priority: 100
        
      # 规则 2: 工程团队代码审查 → claude-sonnet (高质量理解)
      - rule-id: "eng-code-review"
        department: "engineering"
        role: ["CODER", "DEVOPS"]
        task-type-pattern: "^CODE_REVIEW$"
        model-id: "model-sonnet-4"
        priority: 90
        
      # 规则 3: 工程团队调试 → qwen3-coder 32B (更强推理)
      - rule-id: "eng-debug"
        department: "engineering"
        role: "CODER"
        task-type-pattern: "^DEBUGGING$"
        model-id: "model-qwen3-coder-32b"
        priority: 85
        
      # 规则 4: QA 团队 → nemotron-4-340B (全面能力)
      - rule-id: "qa-default"
        department: "qa"
        role: "QA"
        task-type-pattern: ".*"
        model-id: "model-nemotron-4-340b"
        priority: 80
        
      # 规则 5: DevOps → llama-3-coder-instruct
      - rule-id: "infra-deploy"
        department: "infra"
        role: "DEVOPS"
        task-type-pattern: "^INFRA_DEPLOY$"
        model-id: "model-llama3-coder-70b"
        priority: 75
        
      # 规则 6: 默认 fallback
      - rule-id: "default-tech-exec"
        department: ".*"
        role: ".*"
        task-type-pattern: ".*"
        model-id: "model-qwen3-coder-7b"
        priority: 10
```

##### 5.3.1.5 与现有工具执行引擎的集成

Claude CLI 工具需与现有 `ToolExecutionEngine` 无缝集成，作为 tech 员工的"工具"而非"大脑"：

```java
@Component
public class ClaudeCliTool {
    private final ClaudeExecutionGateway gateway;
    private final ClaudeProxyModelRouter modelRouter;
    private final TaskService taskService;
    private final EmployeeService employeeService;
    
    @Override
    public ClaudeCliToolResult execute(ClaudeCliToolParams params) {
        // 1. 从上下文获取调用者信息
        Context context = ContextProvider.getCurrent();
        String employeeId = context.getEmployeeId(); // 当前调用员工
        String taskId = context.getTaskId();         // 当前任务
        
        // 2. 查询任务信息 (用于确定 task category)
        Task task = taskService.getById(taskId);
        String taskCategory = task != null ? task.getCategory() : "UNKNOWN";
        
        // 3. 按路由选择代理模型
        ModelResolutionResult resolution = modelRouter.resolveForClaudeCli(
            employeeId, task, taskCategory
        );
        
        // 4. 更新任务状态: 标记为使用 Claude CLI 执行
        taskService.updateTaskModelAssignment(
            taskId, resolution.getModelId(), resolution.getLayer()
        );
        
        // 5. 设置到 gateway 的 session 状态
        String sessionId = gateway.getSessionId();
        gateway.updateSessionState(sessionId, s -> s.setProxyModel(
            resolution.getModelId()
        ));
        
        // 6. 记录审计日志: 谁在什么任务上使用了哪个模型
        auditLogService.logModelSelection(
            employeeId, taskId, resolution.getModelId(),
            resolution.getLayer(), resolution.getRuleId()
        );
        
        // 7. 执行
        return gateway.execute(params);
    }
}
```

#### 5.3.2 stream-json 事件 → 任务/产物/审计系统

Claude CLI 的 `stream-json` 输出每一行是一个 JSON 事件对象。Living Agent 需要实时解析这些事件，映射到内部事件系统，并关联到任务、产物和审计日志。

##### 5.3.2.1 stream-json 事件类型完整映射

| stream-json 事件 | 内部事件类型 | 处理动作 | 关联实体 |
|-----------------|-------------|---------|---------|
| `stdin_prompt` | `TRACE_INPUT` | 记录用户输入 prompt 原文 | Task, ClaudeCliJob |
| `session_id` | `TRACE_SESSION_START` | 提取 session_id，创建 ClaudeCliJob 记录 | ClaudeCliJob |
| `content_block_start` (type=text) | `TRACE_TEXT_START` | 准备文本累积器 | - |
| `content_block_delta` (type=text_delta) | `TRACE_TEXT_CHUNK` | 追加文本，触发 SSE 推送 | ClaudeCliJob |
| `content_block_start` (type=tool_use) | `TRACE_TOOL_START` | 记录工具调用开始 | ClaudeCliJob, AuditLog |
| `content_block_delta` (type=input_json_delta) | `TRACE_TOOL_PARAM` | 累积 tool 参数 JSON | ClaudeCliJob |
| `content_block_stop` (type=tool_use) | `TRACE_TOOL_COMPLETE` | 工具调用完成，触发执行 | ClaudeCliJob |
| `tool_result` | `TRACE_TOOL_RESULT` | 记录工具返回结果，提取产物 | ClaudeCliJob, ArtifactRecord |
| `message_delta` | `TRACE_MESSAGE_END` | 记录 token 用量，更新 ClaudeCliJob 状态 | ClaudeCliJob, AuditLog |
| `error` | `TRACE_ERROR` | 记录错误，标记失败 | ClaudeCliJob, AuditLog |

##### 5.3.2.2 事件处理管线架构

```
Claude CLI stdout
  -> StreamJsonParser (逐行 parse，支持换行符转义)
  -> EventRouter
      ├─> SSE Pusher      → 实时推送到前端 /api/v1/events/claude/{sessionId}
      ├─> TraceEventStore  → 持久化到 autonomy_trace_event 表
      ├─> ArtifactExtractor → 检测文件产出 (Bash write/edit 操作)
      ├─> AuditLogger       → 记录安全审计日志
      └─> TaskStateUpdater  → 更新关联 Task 状态 ( RUNNING → COMPLETED/ERROR)
```

每个事件处理器按顺序执行，前一个处理器的输出可作为下一个的输入。

##### 5.3.2.3 SSE 推送格式规范

```json
{
    "type": "message",
    "data": {
        "type": "content_block_delta",
        "index": 0,
        "delta": {
            "type": "text_delta",
            "text": "正在分析代码..."
        }
    },
    "metadata": {
        "sessionId": "cl-xxx",
        "jobId": "job-xxx",
        "taskId": "task-xxx",
        "employeeId": "emp-tech-001",
        "modelId": "model-qwen3-coder-7b",
        "modelSelectionLayer": "EMPLOYEE_RULE",
        "timestamp": "2026-05-13T10:30:00Z"
    }
}
```

前端订阅方式：

```javascript
const evtSource = new EventSource(
    `/api/v1/events/claude/${sessionId}?taskId=${taskId}`
);

evtSource.onmessage = (event) => {
    const msg = JSON.parse(event.data);
    switch (msg.data.type) {
        case 'content_block_delta':
            // 追加文本到 UI
            appendText(msg.data.delta.text);
            break;
        case 'message_delta':
            // 更新 token 统计
            updateTokenStats(msg.metadata);
            break;
        case 'error':
            // 标记任务失败
            markTaskFailed(msg.data.error);
            break;
    }
};
```

##### 5.3.2.4 产物提取规则

Claude CLI 执行过程中可能产生的文件产物，需要通过检测 stream-json 事件中的 `tool_use` 调用参数来识别：

| 工具调用 | 参数检测 | 产物类型 | 提取规则 |
|---------|---------|---------|---------|
| `Bash` (write/edit) | input 包含 `cat >` / `tee` / `echo >` | `generated` | 正则提取目标文件路径 |
| `Bash` (mv/cp) | input 包含 `mv` / `cp` 操作 | `modified` | 记录源路径和目标路径 |
| `Bash` (mkdir) | input 包含 `mkdir -p` | `directory` | 创建目录记录 |
| `Bash` (rm) | input 包含 `rm` | `deleted` | 记录被删除文件，不存产物 |
| `Bash` (npm install/yarn) | input 包含包管理器命令 | `dependency` | 更新 package.json 哈希 |
| `Bash` (git commit/push) | input 包含 git 操作 | `commit` | 记录 commit hash |

产物提取正则表达式：

```java
// 匹配 cat > file 模式
private static final Pattern WRITE_FILE_PATTERN = Pattern.compile(
    "cat\\s*>\\s*<?\\s*([^\\s]+)", Pattern.MULTILINE
);

// 匹配 echo "content" > file 模式
private static final Pattern ECHO_WRITE_PATTERN = Pattern.compile(
    "echo\\s+[^>]+>\\s+([^\\s]+)", Pattern.MULTILINE
);

// 匹配 tee file 模式
private static final Pattern TEE_WRITE_PATTERN = Pattern.compile(
    "tee\\s+([-.\\w\\/]+)", Pattern.MULTILINE
);

// 匹配 mv/cp 目标路径
private static final Pattern MOVE_COPY_PATTERN = Pattern.compile(
    "(mv|cp)\\s+([^\\s]+)\\s+([^\\s]+)", Pattern.MULTILINE
);
```

##### 5.3.2.5 审计日志条目规范

每次 Claude CLI 调用产生以下审计日志条目：

| 事件 | 审计级别 | 记录内容 |
|------|---------|---------|
| Claude CLI 启动 | INFO | employeeId, taskId, modelId, promptHash(前 32 字符), 调用者 IP |
| 模型选择结果 | INFO | 使用的规则ID、优先级、选择的模型 |
| 工具调用 (Bash) | WARN | 工具名、参数摘要、是否超出 allowed-dirs 范围 |
| 文件写入 | INFO | 文件路径、文件大小(预估)、写入方式 |
| Token 用量 | INFO | inputTokens, outputTokens, 总成本(按模型单价计算) |
| 调用完成 | INFO | 总耗时、产物数量、最终状态 |
| 调用失败 | ERROR | 错误信息、重试次数、回滚操作 |

#### 5.3.3 会话/任务/产物的关联关系

```
Employee --(has)--> Task --(has)--> ClaudeCliJob
    ↓                        ↓
  department           ClaudeSession
                            ↓
                    AutonomyTraceEvent (stream-json)
                            ↓
                    ArtifactRecord (文件产物)
```

完整关系图：

```
Employee (emp-tech-001)
  ├── role: CODER
  ├── department: engineering
  └── skill_tags: [java, python]
       │
       ▼
  Task (task-001)
  ├── category: CODE_GENERATION
  ├── status: IN_PROGRESS
  ├── assignedTo: emp-tech-001
  └── ClaudeCliJob (job-001)
      ├── status: RUNNING
      ├── modelId: model-qwen3-coder-7b
      ├── modelSelectionRule: eng-code-gen
      ├── claudeSessionId: cl-xxx
      ├── startedAt: 2026-05-13T10:30:00Z
      ├── tokenUsage: { input: 5000, output: 3200 }
      ├── events: [TRACE_INPUT, TRACE_TEXT_START, ...]
      └── artifacts:
          ├── artifact-001: src/main/java/com/example/Service.java (generated)
          └── artifact-002: src/test/java/com/example/ServiceTest.java (generated)
```
                      ArtifactRecord (文件产出)
```

在 `AutonomyTraceEvent` 中新增字段：

```java
@Data
@Entity
@Table(name = "autonomy_trace_event")
public class AutonomyTraceEvent {
    @Id
    private String id;
    
    @Column(name = "session_id")
    private String sessionId;          // Claude session ID
    
    @Column(name = "task_id")
    private String taskId;             // 关联的 Task ID
    
    @Column(name = "employee_id")
    private String employeeId;         // 执行员工
    
    @Column(name = "model_id")
    private String modelId;            // 本次使用的代理模型
    
    @Column(name = "event_type")
    private String eventType;          // stream-json 事件类型
    
    @Column(name = "raw_payload")
    private String rawPayload;         // 原始 JSON 行
    
    @Column(name = "mapped_payload")
    private String mappedPayload;      // 映射后的结构化数据
    
    @Column(name = "timestamp")
    private Instant timestamp;
    
    @Column(name = "usage_input_tokens")
    private Integer usageInputTokens;
    
    @Column(name = "usage_output_tokens")
    private Integer usageOutputTokens;
}
```

在 `ClaudeCliJob`（新增实体）中：

```java
@Data
@Entity
@Table(name = "claude_cli_job")
public class ClaudeCliJob {
    @Id
    private String id;
    
    @Column(name = "task_id")
    private String taskId;
    
    @Column(name = "employee_id")
    private String employeeId;
    
    @Column(name = "parent_session_id")
    private String parentSessionId;    // Brain session
    
    @Column(name = "claude_session_id")
    private String claudeSessionId;    // Claude CLI session
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "prompt")
    private String prompt;
    
    @Column(name = "model_id")
    private String modelId;            // 代理模型
    
    @Column(name = "started_at")
    private Instant startedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "token_usage_input")
    private Integer tokenUsageInput;
    
    @Column(name = "token_usage_output")
    private Integer tokenUsageOutput;
    
    @Column(name = "output_format")
    private String outputFormat;       // stream-json / json
    
    @OneToOne(mappedBy = "claudeCliJob")
    private List<ArtifactRecord> artifacts;
}
```

---

### 5.4 stream-json 事件接入规范

Claude CLI 以 `--output-format stream-json` 输出每行一个 JSON 对象。Living Agent 需要将其映射到内部事件系统。

#### 5.4.1 事件类型映射

| stream-json 事件 | 内部事件类型 | 处理动作 |
|-----------------|-------------|---------|
| `stdin_prompt` | `TRACE_INPUT` | 记录用户输入 prompt |
| `session_id` | `TRACE_SESSION_START` | 提取 session_id，创建 session 记录 |
| `content_block_start` (type=text) | `TRACE_TEXT_START` | 准备文本累积器 |
| `content_block_delta` (type=text_delta) | `TRACE_TEXT_CHUNK` | 追加文本，触发 SSE 推送 |
| `content_block_start` (type=tool_use) | `TRACE_TOOL_START` | 记录工具调用开始 |
| `content_block_delta` (type=input_json_delta) | `TRACE_TOOL_PARAM` | 累积 tool 参数 JSON |
| `content_block_stop` (type=tool_use) | `TRACE_TOOL_COMPLETE` | 工具调用完成，触发执行 |
| `tool_result` | `TRACE_TOOL_RESULT` | 记录工具返回结果 |
| `message_delta` | `TRACE_MESSAGE_END` | 记录 token 用量，更新状态 |
| `error` | `TRACE_ERROR` | 记录错误，标记失败 |

#### 5.4.2 事件推送管线

```
Claude CLI stdout
  -> StreamJsonParser (每行 parse)
  -> EventRouter
      -> SSE Pusher (实时推送给前端)
      -> TraceEventStore (持久化到 autonomy_trace_event)
      -> ArtifactExtractor (检测文件产出)
      -> AuditLogger (审计日志)
```

#### 5.4.3 SSE 事件格式

```json
{
    "type": "message",
    "data": {
        "type": "content_block_delta",
        "index": 0,
        "delta": {
            "type": "text_delta",
            "text": "正在分析代码..."
        }
    },
    "metadata": {
        "sessionId": "cl-xxx",
        "employeeId": "emp-tech-001",
        "modelId": "model-qwen3-coder-7b",
        "timestamp": "2026-05-13T10:30:00Z"
    }
}
```

### 5.5 数据模型扩展

#### 5.5.1 ClaudeCliJob 实体

```java
@Data
@Entity
@Table(name = "claude_cli_job")
public class ClaudeCliJob {
    @Id
    private String id;
    
    @Column(name = "task_id")
    private String taskId;
    
    @Column(name = "employee_id")
    private String employeeId;
    
    @Column(name = "parent_session_id")
    private String parentSessionId;    // 触发此 Claude CLI 调用的 Brain session
    
    @Column(name = "claude_session_id")
    private String claudeSessionId;    // Claude CLI 生成的 session ID
    
    @Column(name = "status")
    private String status;             // RUNNING / COMPLETED / CANCELLED / ERROR
    
    @Column(name = "prompt")
    private String prompt;
    
    @Column(name = "model_id")
    private String modelId;            // 代理模型 ID
    
    @Column(name = "output_format")
    private String outputFormat;       // stream-json / json
    
    @Column(name = "started_at")
    private Instant startedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "token_usage_input")
    private Integer tokenUsageInput;
    
    @Column(name = "token_usage_output")
    private Integer tokenUsageOutput;
    
    @OneToMany(mappedBy = "claudeCliJob", cascade = CascadeType.ALL)
    private List<ArtifactRecord> artifacts;
    
    @OneToMany(mappedBy = "claudeCliJob", cascade = CascadeType.ALL)
    private List<AuditLog> auditLogs;
}
```

#### 5.5.2 模型用途标签（usage_tags）

在 `LlmModel.metadata` 中新增用途标签，用于区分模型角色：

| 标签 | 含义 | 适用模型 |
|------|------|---------|
| `brain-conversation` | Brain 对话主模型 | GPT-4, Claude Sonnet, 等 |
| `claude-proxy` | Claude CLI 代理模型 | Qwen3-Coder, Llama 3, 等 |
| `tech-executor` | 技术员工执行模型 | 同上 |
| `code-gen` | 代码生成专用 | Qwen3-Coder, DeepSeek-Coder |
| `review` | 代码审查专用 | Claude Sonnet, GPT-4 |

### 5.6 可观测性

#### 5.6.1 指标

| 指标名称 | 类型 | 标签 | 说明 |
|---------|------|------|------|
| `claude_cli_jobs_total` | Counter | status, model_id, employee_id | Claude CLI 执行次数 |
| `claude_cli_job_duration_seconds` | Histogram | status, model_id | 执行耗时 |
| `claude_cli_tokens_total` | Counter | usage_direction(model_id) | Token 用量 |
| `claude_cli_active_sessions` | Gauge | model_id | 活跃会话数 |
| `claude_cli_process_count` | Gauge | - | 当前 Claude CLI 进程数 |
| `claude_cli_errors_total` | Counter | error_type | 错误次数 |

#### 5.6.2 日志

```java
// 结构化日志示例
log.info("claude-cli.job.started",
    "sessionId", sessionId,
    "employeeId", employeeId,
    "taskId", taskId,
    "modelId", modelId,
    "promptPreview", truncate(prompt, 100)
);

log.info("claude-cli.job.completed",
    "sessionId", sessionId,
    "durationMs", durationMs,
    "inputTokens", inputTokens,
    "outputTokens", outputTokens,
    "artifactsCount", artifacts.size()
);

log.error("claude-cli.job.failed",
    "sessionId", sessionId,
    "error", error.getMessage(),
    "retryCount", retryCount
);
```

#### 5.6.3 链路追踪

每个 Claude CLI 任务携带 OpenTelemetry trace context：

```java
Span span = tracer.spanBuilder("claude-cli.execute")
    .setSpanKind(SpanKind.PRODUCER)
    .setAttribute("employee.id", employeeId)
    .setAttribute("model.id", modelId)
    .setAttribute("claude.session.id", sessionId)
    .startSpan();

try (Scope scope = span.makeCurrent()) {
    // Claude CLI 执行
    gateway.execute(request);
} finally {
    span.end();
}
```

---

## 6. 分阶段实施计划

### Phase 0: 基础设施准备（第 1 周）

| 任务 | 产出 | 验收标准 |
|------|------|---------|
| 安装 Claude CLI + free-claude-code-main | 本地可运行环境 | 手动执行 `claude -p "hello"` 成功 |
| 确认 Java 25 + Spring Boot 3.x 运行环境 | 开发环境就绪 | `mvn test` 通过 |
| 搭建 Docker Compose 包含 PostgreSQL + Claude CLI sandbox | 开发 Docker 环境 | 容器全部 healthy |

### Phase 1: 短期方案落地（第 2-3 周）

| 任务 | 产出 | 验收标准 |
|------|------|---------|
| 新增 `ClaudeCliProperties` 配置类 | 配置类可用 | `@ConfigurationProperties` 绑定测试通过 |
| 改造 `ClaudeExecutionGateway` 注入环境变量 | 环境变量注入工作 | `ANTHROPIC_API_URL` 等正确设置 |
| 改造 `SandboxService` 支持 Claude 专用 sandbox config | Claude sandbox 可用 | 工作区隔离、权限控制生效 |
| `free-claude-code-main` 部署到 localhost:8082 | 代理服务运行 | `/health` 返回 200 |
| `ClaudeCliTool` 通过代理执行 Claude CLI | 端到端可运行 | 调用 `claude_cli(prompt="...")` 成功返回 |

### Phase 2: Java 侧代理核心（第 4-7 周）

| 任务 | 产出 | 验收标准 |
|------|------|---------|
| 实现 `AnthropicOpenAiConverter` | 双向转换器可用 | 通过 50+ 测试用例验证 |
| 实现 `AnthropicSseBuilder` | SSE 构建器可用 | 生成的 SSE 与 Claude API 兼容 |
| 实现 `ClaudeProxyController` `/v1/messages` 端点 | REST 端点可用 | curl 测试通过 |
| 实现 `ClaudeProxyService` 转发逻辑 | 代理转发可用 | 端到端请求成功 |
| 实现 `ClaudeProxyModelRouter` | 模型路由器可用 | 按 employee/department 返回不同模型 |
| 集成 `ModelPoolManager` + `LlmModel` | 模型池接入 | 从池中查询模型成功 |

### Phase 3: stream-json 事件接入（第 8-9 周）

| 任务 | 产出 | 验收标准 |
|------|------|---------|
| 实现 `StreamJsonEventMapper` | 事件映射器可用 | 映射覆盖率 > 95% |
| 改造 `ClaudeCliTool` 事件监听 | 事件实时推送 | SSE 流式推送延迟 < 200ms |
| 实现 `ClaudeCliEventProcessor` 产物提取 | 产物追踪可用 | Bash write/edit 操作被正确追踪 |
| 新增 `ClaudeCliJob` + `AutonomyTraceEvent` 实体 | 数据模型就绪 | 数据库表创建成功 |
| 持久化到 `autonomy_trace_event` 表 | 持久化可用 | 查询 trace 记录成功 |

### Phase 4: 深度集成与优化（第 10-12 周）

| 任务 | 产出 | 验收标准 |
|------|------|---------|
| `ClaudeCliTool` 接入模型选择路由 | 模型选择可用 | 按 employee/department 选择不同模型 |
| 实现 `ClaudeProcessPool` | 进程池可用 | 并发支持 `maxConcurrentSessions` |
| 添加 Prometheus 指标 + 结构化日志 | 可观测性就绪 | Grafana 面板展示数据 |
| 集成 OpenTelemetry 链路追踪 | 链路追踪可用 | Jaeger 可查看完整链路 |
| 性能优化 + 安全加固 | 生产就绪 | 压测支持 10+ 并发 Claude CLI 会话 |
| 文档更新 + 用户手册 | 文档完备 | 用户可独立使用 |

---

## 7. 风险与注意事项

---

## 7. 风险与注意事项

### 7.1 技术风险

| 风险 | 影响 | 缓解措施 | 概率 |
|------|------|---------|------|
| Anthropic->OpenAI 转换不完整 | 工具调用/复杂消息失败 | 逐步补充测试用例，覆盖主流场景 | 高 |
| SSE 格式错误导致 Claude CLI 解析失败 | 会话中断 | 与 `free-claude-code-main` 的 SSEBuilder 对齐，自动化对比测试 | 高 |
| 本地模型响应慢 | 用户体验差 | 设置合理的 timeout（60s+），增加进度提示 | 中 |
| Claude CLI 进程泄漏 | 资源耗尽 | 实现 `ClaudeProcessPool`，强制 session 超时清理 | 中 |
| 多并发会话冲突 | 输出混乱 | 每个会话独立工作区 + 进程隔离 | 低 |

### 7.2 安全风险

| 风险 | 缓解措施 |
|------|---------|
| Claude CLI 执行任意命令 | Sandbox 隔离 + `allowed-dirs` 白名单 + `--dangerously-skip-permissions` 可配置开关 |
| 代理代理绕过导致请求打到云端 | 网络层限制代理只转发到配置的本地 URL |
| Token 泄露 | 所有密钥通过环境变量注入，不写入日志 |
| 文件写入攻击 | Sandbox 挂载隔离 volume，限制写入范围 |

### 7.3 运维风险

| 风险 | 缓解措施 |
|------|---------|
| 本地模型不可用 | 配置 fallback 到云端模型（Anthropic API key） |
| 多节点部署时 Claude CLI 调度 | 初期单节点，后续引入任务队列（RabbitMQ/Kafka） |
| 日志磁盘增长 | 日志轮转 + trace 数据 TTL（默认 30 天） |

---

## 8. 推荐下一步

### 8.1 Claude CLI → 代理接口（短期）

Claude CLI 通过环境变量调用代理：

```
环境变量:
  ANTHROPIC_API_KEY=sk-placeholder-key-for-proxy
  ANTHROPIC_API_URL=http://host.docker.internal:8082/v1
  ANTHROPIC_BASE_URL=http://host.docker.internal:8082

CLI 命令:
  claude -p "<prompt>" --output-format stream-json \
    --dangerously-skip-permissions \
    --add-dir <workspace>
```

### 8.2 代理 → 模型池接口（中期）

Java 内部接口：

```java
public interface IAnthropicProxyService {
    /**
     * 处理 Anthropic Messages 请求（SSE 流式返回）
     */
    SseEmitter handleMessage(
        AnthropicMessagesRequest request,
        String employeeId
    );
    
    /**
     * 取消会话
     */
    void cancelSession(String sessionId);
    
    /**
     * Token 计数
     */
    AnthropicTokenCountResponse countTokens(
        AnthropicTokenCountRequest request
    );
}
```

### 8.3 事件推送接口

```java
public interface IEventPusher {
    /**
     * 推送事件到指定 session 的 SSE 连接
     */
    void push(String sessionId, StreamJsonEvent event);
    
    /**
     * 持久化 trace 事件
     */
    void persistTrace(AutonomyTraceEvent event);
}
```

### 8.4 推荐下一步

1. **立即启动 Phase 0 + Phase 1**（短期方案），2 周内完成端到端验证。
2. 在 Phase 1 的基础上评估转换复杂度，决定是否加速 Phase 2（Java 侧代理）。
3. 对 `free-claude-code-main` 的 `message_converter.py` 和 `sse_builder.py` 做代码审查，列出所有需要转换的 corner case。
4. 搭建 Claude CLI 端到端 Demo：Claude CLI → `free-claude-code-main` → LM Studio Qwen3-Coder，验证基本工具调用流程。

---

## 9. 附录 A：数据模型定义

### A.1 LlmModel 扩展字段

```yaml
# LlmModel YAML 示例
id: "model-qwen3-coder-7b"
providerId: "provider-lmstudio"
modelId: "qwen3-coder:7b"
modelType: CHAT
status: ACTIVE
description: "Qwen3 Coder 7B, 用于 Claude CLI 代理"
metadata:
  usage_tags:
    - claude-proxy
    - tech-executor
    - code-gen
  max_context_window: 128000
  supports_tool_use: true
  proxy_type: internal
  base_url: "http://host.docker.internal:1234/v1"
  health_check_interval_seconds: 30
```

### A.2 ClaudeCliJob 完整字段

| 字段 | 类型 | 说明 |
|------|------|------|
| id | String (UUID) | 任务唯一标识 |
| taskId | String | 关联的 Task 表 ID |
| employeeId | String | 执行员工 ID |
| parentSessionId | String | 触发此调用的 Brain session |
| claudeSessionId | String | Claude CLI 生成的 session |
| status | Enum | RUNNING / COMPLETED / CANCELLED / ERROR / PENDING |
| prompt | Text | 发送给 Claude 的 prompt |
| modelId | String | 代理模型 ID |
| outputFormat | String | stream-json / json |
| startedAt | Instant | 开始时间 |
| completedAt | Instant | 结束时间 |
| tokenUsageInput | Integer | 输入 token 数 |
| tokenUsageOutput | Integer | 输出 token 数 |
| errorMessage | Text | 失败时的错误信息 |
| artifactCount | Integer | 产生的文件产物数 |

### A.3 会话状态机

```
PENDING ──start──> RUNNING ──completed──> COMPLETED
     │                   │                      │
     │              cancelled              error │
     │                   │                      │
     └─────── CANCELLED <──┴─────── ERROR ───────┘
```

状态转换事件：

| 事件 | 源状态 | 目标状态 | 触发条件 |
|------|--------|---------|---------|
| start | PENDING | RUNNING | 进程启动成功 |
| completed | RUNNING | COMPLETED | Claude CLI 正常退出 |
| cancelled | RUNNING/PENDING | CANCELLED | 收到取消请求 |
| error | RUNNING/PENDING | ERROR | 进程异常退出 |

---

## 10. 附录 B：stream-json 事件映射规范

### B.1 事件映射表

| stream-json 事件 (type 字段) | AutonomyTraceEvent.eventType | 映射逻辑 |
|------------------------------|-----------------------------|---------|
| `stdin_prompt` | `TRACE_INPUT` | prompt -> trace.inputContent |
| `session_id` | `TRACE_SESSION_START` | sessionId -> trace.sessionId, trace.systemEvent=true |
| `content_block_start` (text) | `TRACE_TEXT_START` | index -> textBlockIndex, 初始化文本累积 |
| `content_block_delta` (text_delta) | `TRACE_TEXT_CHUNK` | 追加 text 到累积器, 推送 SSE |
| `content_block_start` (tool_use) | `TRACE_TOOL_CALL_START` | name -> toolName, id -> toolCallId |
| `content_block_delta` (input_json_delta) | `TRACE_TOOL_PARAM` | 追加 JSON 片段到 toolParams |
| `content_block_stop` (tool_use) | `TRACE_TOOL_CALL_COMPLETE` | toolParams -> toolInput, 触发工具执行 |
| `tool_result` | `TRACE_TOOL_RESULT` | content -> toolResultContent, status -> SUCCESS/ERROR |
| `message_delta` | `TRACE_MESSAGE_END` | usage -> trace.tokenUsage, stop_reason -> stopReason |
| `message_stop` | `TRACE_MESSAGE_END` | 同 message_delta, 确认结束 |
| `error` | `TRACE_ERROR` | error.message -> trace.errorMessage |
| `tool_use` (standalone) | `TRACE_TOOL_CALL` | 兼容旧格式, 等同 content_block 系列 |
| `bash` (standalone) | `TRACE_TOOL_CALL` | 快捷命令工具调用 |

### B.2 stream-json 原始事件示例

```json
// 1. 输入
{"type":"stdin_prompt","prompt":"请帮我写一个 Java 方法..."}

// 2. 会话创建
{"type":"session_id","session_id":"cl-proj-abc123"}

// 3. 文本开始
{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

// 4. 文本增量
{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"好的，我来帮你写这个方法。"}}

// 5. 工具调用开始
{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"tu_01","name":"Bash","input":{}}}

// 6. 工具参数增量
{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"command\":\"mkdir -p /workspace/src\"}"}}

// 7. 工具调用完成
{"type":"content_block_stop","index":1}

// 8. 工具结果
{"type":"tool_result","content_block_id":"tu_01","is_error":false,"content":{"type":"text","text":"Command executed successfully."}}

// 9. 消息结束
{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"input_tokens":1200,"output_tokens":85}}

// 10. 消息停止
{"type":"message_stop"}
```

### B.3 映射后的 AutonomyTraceEvent 示例

```java
AutonomyTraceEvent trace = new AutonomyTraceEvent();
trace.setId("trace-" + UUID.randomUUID());
trace.setSessionId("cl-proj-abc123");
trace.setEmployeeId("emp-tech-001");
trace.setTaskId("task-0042");
trace.setModelId("model-qwen3-coder-7b");
trace.setEventType("TRACE_TEXT_CHUNK");
trace.setRawPayload("{\"type\":\"content_block_delta\",...}");
trace.setMappedPayload("{\"text\":\"好的，我来帮你写这个方法。\"}");
trace.setTimestamp(Instant.now());
trace.setUsageInputTokens(1200);
trace.setUsageOutputTokens(85);
```

### B.4 特殊事件处理规则

| 场景 | 处理规则 |
|------|---------|
| 长文本响应 | 每 2KB 触发一次 SSE 推送，避免内存溢出 |
| 工具调用参数不完整 | 累积所有 input_json_delta 直到 content_block_stop |
| Bash 命令含敏感操作 | 记录命令前缀但脱敏密码参数（匹配 `password|secret|token`） |
| Claude 超时 | 捕获 `content_block_delta` 长时间无数据，标记 TIMEOUT |
| 非 JSON 输出行 | 记录为 `TRACE_RAW_OUTPUT`，不影响主事件流 |

---

## 11. 附录 C：接口契约

### C.1 Anthropic Messages 兼容接口

#### 创建消息 (POST /v1/messages)

请求体:
```json
{
  "model": "qwen3-coder:7b",
  "max_tokens": 4096,
  "messages": [
    {"role": "user", "content": "请帮我写一个 Java 方法..."},
    {"role": "assistant", "content": "..."},
    {"role": "user", "content": "..."}
  ],
  "stream": true,
  "tools": [
    {
      "name": "Bash",
      "description": "执行 shell 命令",
      "input_schema": {
        "type": "object",
        "properties": {
          "command": {"type": "string", "description": "要执行的命令"}
        },
        "required": ["command"]
      }
    },
    {
      "name": "ReadFile",
      "description": "读取文件内容",
      "input_schema": {
        "type": "object",
        "properties": {
          "path": {"type": "string", "description": "文件路径"}
        },
        "required": ["path"]
      }
    }
  ]
}
```

响应 (SSE 流式):
```
HTTP/1.1 200 OK
Content-Type: text/event-stream
Connection: keep-alive

data: {"type":"message_start","message":{"id":"msg-01","type":"message","role":"assistant","content":[],"model":"qwen3-coder:7b","stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":120,"output_tokens":0}}}

data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"好的，我来"}}

data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"input_tokens":120,"output_tokens":25}}

data: [DONE]
```

#### 创建消息（非流式）

响应体:
```json
{
  "id": "msg-01",
  "type": "message",
  "role": "assistant",
  "content": [
    {
      "type": "text",
      "text": "好的，我来帮你写这个方法。"
    },
    {
      "type": "tool_use",
      "id": "tu_01",
      "name": "Bash",
      "input": {"command": "echo hello"}
    }
  ],
  "model": "qwen3-coder:7b",
  "stop_reason": "tool_use",
  "usage": {"input_tokens": 120, "output_tokens": 25}
}
```

#### Token 计数 (POST /v1/messages/count_tokens)

请求体:
```json
{
  "model": "qwen3-coder:7b",
  "messages": [{"role": "user", "content": "Hello"}]
}
```

响应体:
```json
{"input_tokens": 10}
```

### C.2 Java 内部 SSE 接口

#### Brain → ProxyService

```java
public class AnthropicMessagesRequest {
    private String modelId;          // 模型 ID（路由用）
    private List<ContentBlock> messages;  // 消息列表
    private Integer maxTokens;       // 最大 token 数
    private List<ToolDefinition> tools;    // 可用工具
    private boolean stream;          // 是否流式
    private String employeeId;       // 执行员工（追踪用）
    private String sessionId;        // 关联 Brain session ID
}

public class AnthropicMessagesResponse {
    private String messageId;
    private List<ContentBlock> content;
    private String stopReason;
    private AnthropicTokenCount usage;
}
```

#### ProxyService → Claude CLI 进程

```java
public class ClaudeCliSession {
    private Process process;
    private PrintWriter stdin;
    private BufferedReader stderr;
    private BufferedReader stdout;
    private String claudeSessionId;
    private volatile boolean running;
    private Map<String, ToolExecutionQueue> toolQueues;
}
```

### C.3 错误码

| HTTP 状态码 | 含义 | 处理建议 |
|-------------|------|---------|
| 400 | 请求格式错误 | 校验 messages/tools 字段 |
| 401 | API Key 无效 | 检查代理配置 |
| 403 | 模型不可用 | 检查模型池状态 |
| 404 | 会话不存在 | 检查 sessionId 有效性 |
| 408 | 请求超时 | 重试或降级模型 |
| 429 | 频率限制 | 排队等待 |
| 500 | 内部错误 | 记录 trace，重试或失败 |
| 503 | 服务不可用 | 切换模型或队列等待 |

---

## 12. 附录 D：时序图

### D.1 短期方案 - Claude CLI 端到端流程

```
┌──────────┐     ┌──────────────────┐     ┌──────────────────┐     ┌────────────┐
│  Claude  │     │  free-claude-    │     │   proxy /        │     │  本地模型   │
│   CLI    │     │  code-main       │     │  message_        │     │  (LMStudio/ │
│          │     │                  │     │  converter       │     │   Ollama)   │
└────┬─────┘     └────────┬─────────┘     └────────┬─────────┘     └──────┬─────┘
     │ -p "..."           │                        │                       │
     │ ──────────────────>│                        │                       │
     │                    │ parse_prompt()         │                       │
     │                    │ ───────────────────────────────>              │
     │                    │                        │ convert_to_api()      │
     │                    │                        │ ─────────────────────────>
     │                    │                        │                       │
     │                    │                        │                       │ request()
     │                    │                        │                       │ ──>
     │                    │                        │                       │ <───
     │                    │                        │                   response
     │                    │                        │ <───────────────────────
     │                    │ process_stream_json()  │                       │
     │                    │ <────────────────────── │                       │
     │ <───────────────── │                        │                       │
     │ stream-json output │                        │                       │
```

### D.2 中期方案 - Java 内部 SSE 流程

```
┌──────────┐     ┌──────────────────┐     ┌──────────────────┐     ┌────────────┐
│  Brain   │     │  AnthropicProxy  │     │  ClaudeCli       │     │  本地模型   │
│          │     │  Service         │     │  SessionMgr      │     │            │
└────┬─────┘     └────────┬─────────┘     └────────┬─────────┘     └────┬─────┘
     │ SSE connect        │                        │                    │
     │ ──────────────────>│                        │                    │
     │                    │ createSession()        │                    │
     │                    │ ──────────────────────>│                    │
     │                    │                        │ spawn_process()    │
     │                    │                        │ ──────────────────>│
     │                    │                        │                    │
     │                    │ handleMessage()        │                    │
     │                    │ ──────────────────────>│                    │
     │                    │                        │ convert()          │
     │                    │                        │ ──────────────────>│
     │                    │                        │                    │
     │                    │                        │ <──────────────────│
     │                    │ stream_json_event()    │                    │
     │                    │ <──────────────────────│                    │
     │ <───────────────── │ push_sse()             │                    │
     │ sse_event          │                        │                    │
```

### D.3 事件流与产物追踪

```
  Claude CLI ──stream-json──> proxy ──map──> AutonomyTraceEvent ──persist──> DB
                                                        │
                                                        ├──> SSE push ──> Brain
                                                        │
                                                        ├──> TaskArtifact (stdout)
                                                        │
                                                        └──> FileArtifact (modified files)
```
