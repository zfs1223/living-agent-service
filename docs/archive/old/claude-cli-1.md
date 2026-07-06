# Claude CLI 集成实现方案 B：Living Agent 内置 Anthropic `/v1/messages` 代理

> 日期：2026-05-14  
> 范围：`docker/living-agent-service`  
> 目标：不再依赖 `free-claude-code-main` 作为外部代理，而是在 Living Agent 内部实现 Anthropic Messages API 兼容代理，使 Claude CLI 可以通过 `ANTHROPIC_API_URL` 直接调用 Living Agent 模型池中的 OpenAI-compatible、本地 LM Studio、llama.cpp、Ollama 等模型。

---

## 1. 方案结论

采用 **方案 B：在 Living Agent 内置 Anthropic `/v1/messages` 代理**。

Claude CLI 不直接调用真实 Anthropic，而是通过环境变量指向 Living Agent Gateway 暴露的 Anthropic 兼容端点：

```text
Claude CLI
  -> ANTHROPIC_API_URL=http://living-agent-gateway:8480/api/v1/proxy/anthropic/v1
  -> POST /messages
  -> ClaudeProxyController
  -> ClaudeProxyService
  -> ClaudeProxyModelRouter
  -> BrainModelResolver / 模型池
  -> OpenAI-compatible Provider / Ollama / LM Studio / llama.cpp
  -> OpenAI stream chunks
  -> AnthropicSseBuilder
  -> Anthropic SSE stream
  -> Claude CLI stream-json
```

本方案的关键点：

1. **代理内置**：Living Agent 自身提供 Anthropic Messages API 兼容接口，不再要求单独启动 `free-claude-code-main`。
2. **模型池打通**：Claude CLI 使用的模型由 Living Agent 模型池解析，支持按员工、部门、大脑、任务类型路由。
3. **协议转换**：代理负责 Anthropic Messages schema 与 OpenAI-compatible Chat Completions schema 的双向转换。
4. **流式兼容**：代理将 OpenAI SSE chunk 转换为 Anthropic SSE event，保证 Claude CLI 可以正常消费。
5. **审计统一**：Claude CLI 请求、模型选择、工具调用块、输出事件、错误信息统一接入 Living Agent 的任务、产物、审计与监控体系。

---

## 2. 当前问题与改造目标

### 2.1 当前问题

现有代码已经有 `ClaudeCliTool` 和 `ClaudeExecutionGateway`，能够拼装并执行 `claude -p`、`--resume`、`--output-format stream-json` 等命令，但存在以下问题：

| 编号 | 问题 | 影响 |
|---|---|---|
| P1 | Claude CLI 没有指向 Living Agent 内部代理 | 默认会调用真实 Anthropic 或外部代理，无法统一治理 |
| P2 | 模型池只服务 Brain Provider 链路 | Claude CLI 无法按模型池配置选模型 |
| P3 | Living Agent 没有 Anthropic `/v1/messages` 兼容端点 | Claude CLI 无法直接将请求打到 Living Agent |
| P4 | 缺少 Anthropic ↔ OpenAI schema 转换 | OpenAI-compatible 本地模型无法直接理解 Claude 请求格式 |
| P5 | 缺少 OpenAI stream → Anthropic SSE 转换 | Claude CLI 的 stream-json 消费链路不完整 |
| P6 | `ProviderFactory` 当前 Brain 链路主要支持 `OPENAI_COMPATIBLE` | `ANTHROPIC` 协议枚举存在，但不适合作为当前主要调用路径 |
| P7 | CLI 会话、模型请求、任务产物没有统一审计 | 难以追踪代码执行任务使用了哪个员工/模型/输出 |

### 2.2 改造目标

本次改造的目标不是简单把 Claude CLI 指向某个外部服务，而是把 Claude CLI 作为 Living Agent 的内置代码执行工具纳入统一模型调度。

必须达成：

1. Living Agent Gateway 暴露 Claude CLI 可用的 Anthropic Messages API：
   - `POST /api/v1/proxy/anthropic/v1/messages`
   - `POST /api/v1/proxy/anthropic/v1/messages/count_tokens`
   - `GET /api/v1/proxy/anthropic/health`
2. `ClaudeExecutionGateway` 自动注入：
   - `ANTHROPIC_API_KEY`
   - `ANTHROPIC_API_URL`
   - `ANTHROPIC_BASE_URL`
   - `TERM=dumb`
   - `PYTHONIOENCODING=utf-8`
3. 代理根据请求上下文解析模型：
   - `employeeId`
   - `departmentId`
   - `brainId`
   - `taskType`
   - `requestedModel`
4. 代理把 Anthropic Messages 请求转换为 OpenAI-compatible Chat Completions 请求。
5. 代理把 OpenAI-compatible SSE 响应转换为 Anthropic SSE 响应。
6. 请求、响应、错误、模型选择、token 使用量进入 Living Agent 审计与监控。

---

## 3. 总体架构

### 3.1 组件划分

```text
living-agent-gateway
  └─ proxy/anthropic/
      ├─ ClaudeProxyController.java
      ├─ ClaudeProxyAuthFilter.java
      └─ dto/
          ├─ AnthropicMessagesRequest.java
          ├─ AnthropicMessagesResponse.java
          ├─ AnthropicContentBlock.java
          ├─ AnthropicTool.java
          └─ AnthropicTokenCountRequest.java

living-agent-core
  ├─ proxy/anthropic/
  │   ├─ ClaudeProxyService.java
  │   ├─ ClaudeProxyModelRouter.java
  │   ├─ ClaudeProxyRequestContext.java
  │   ├─ ClaudeProxyAuditService.java
  │   ├─ converter/
  │   │   ├─ AnthropicToOpenAiConverter.java
  │   │   ├─ OpenAiToAnthropicConverter.java
  │   │   └─ ClaudeToolBlockMapper.java
  │   └─ sse/
  │       ├─ AnthropicSseBuilder.java
  │       ├─ AnthropicSseEvent.java
  │       └─ OpenAiStreamChunkParser.java
  ├─ model/pool/
  │   ├─ BrainModelResolver.java
  │   ├─ ResolvedBrainModel.java
  │   ├─ LlmClientFactory.java
  │   └─ client/OpenAiCompatibleClient.java
  └─ sandbox/
      ├─ ClaudeExecutionGateway.java
      └─ ClaudeCliProperties.java
```

### 3.2 请求链路

```text
1. Agent/Tool 调用 claude_cli
2. ClaudeCliTool 构建 gateway 参数
3. ClaudeExecutionGateway 创建或复用 sandbox session
4. ClaudeExecutionGateway 注入 Anthropic 代理环境变量
5. Claude CLI 发起 POST /v1/messages
6. ClaudeProxyController 接收 Anthropic 请求
7. ClaudeProxyService 构建 ClaudeProxyRequestContext
8. ClaudeProxyModelRouter 调用 BrainModelResolver 解析模型
9. AnthropicToOpenAiConverter 转换请求
10. OpenAiCompatibleClient 以 stream=true 调用模型池中的 provider
11. OpenAiStreamChunkParser 解析 provider SSE chunk
12. AnthropicSseBuilder 输出 Claude CLI 兼容 SSE
13. Claude CLI 产生 stream-json stdout
14. ClaudeExecutionGateway 解析 session_id、事件与结果
15. ClaudeProxyAuditService 写入审计、任务、产物与监控
```

---

## 4. 配置设计

### 4.1 `application.yml`

```yaml
living-agent:
  claude-cli:
    enabled: true
    command: claude
    workspace: ./agent_workspace
    default-output-format: stream-json
    dangerously-skip-permissions: true
    allowed-dirs:
      - ./agent_workspace
    max-concurrent-sessions: 3
    session-timeout-minutes: 30
    job-timeout-minutes: 60

    proxy:
      enabled: true
      # Claude CLI 注入的 base url，不带 /v1/messages
      base-url: http://localhost:8480/api/v1/proxy/anthropic
      # Claude CLI 注入的 api url，通常到 /v1
      api-url: http://localhost:8480/api/v1/proxy/anthropic/v1
      api-key-placeholder: sk-living-agent-claude-proxy
      auth-token: ${CLAUDE_PROXY_AUTH_TOKEN:}
      require-auth: false
      default-brain-id: tech
      default-department-id: tech
      default-task-type: code_generation
      stream-timeout-seconds: 600
      max-input-tokens: 120000
      max-output-tokens: 8192
      audit-enabled: true
      push-stream-events: true
```

### 4.2 配置类

```java
@Data
@ConfigurationProperties(prefix = "living-agent.claude-cli")
public class ClaudeCliProperties {
    private boolean enabled = true;
    private String command = "claude";
    private String workspace = "./agent_workspace";
    private String defaultOutputFormat = "stream-json";
    private boolean dangerouslySkipPermissions = true;
    private List<String> allowedDirs = List.of("./agent_workspace");
    private int maxConcurrentSessions = 3;
    private int sessionTimeoutMinutes = 30;
    private int jobTimeoutMinutes = 60;
    private Proxy proxy = new Proxy();

    @Data
    public static class Proxy {
        private boolean enabled = true;
        private String baseUrl = "http://localhost:8480/api/v1/proxy/anthropic";
        private String apiUrl = "http://localhost:8480/api/v1/proxy/anthropic/v1";
        private String apiKeyPlaceholder = "sk-living-agent-claude-proxy";
        private String authToken = "";
        private boolean requireAuth = false;
        private String defaultBrainId = "tech";
        private String defaultDepartmentId = "tech";
        private String defaultTaskType = "code_generation";
        private int streamTimeoutSeconds = 600;
        private int maxInputTokens = 120000;
        private int maxOutputTokens = 8192;
        private boolean auditEnabled = true;
        private boolean pushStreamEvents = true;
    }
}
```

---

## 5. Claude CLI 执行网关改造

### 5.1 环境变量注入

`ClaudeExecutionGateway` 不再依赖外部代理配置，而是固定把 Claude CLI 指向 Living Agent Gateway 内置代理。

需要新增：

```java
private Map<String, String> buildClaudeProxyEnv(String sessionId, Map<String, Object> params) {
    Map<String, String> env = new HashMap<>();
    env.put("ANTHROPIC_API_KEY", properties.getProxy().getApiKeyPlaceholder());
    env.put("ANTHROPIC_API_URL", properties.getProxy().getApiUrl());
    env.put("ANTHROPIC_BASE_URL", properties.getProxy().getBaseUrl());
    env.put("TERM", "dumb");
    env.put("PYTHONIOENCODING", "utf-8");

    if (StringUtils.hasText(properties.getProxy().getAuthToken())) {
        env.put("ANTHROPIC_AUTH_TOKEN", properties.getProxy().getAuthToken());
    }

    putIfPresent(env, "LIVING_AGENT_SESSION_ID", sessionId);
    putIfPresent(env, "LIVING_AGENT_EMPLOYEE_ID", stringValue(params.get("employee_id"), null));
    putIfPresent(env, "LIVING_AGENT_DEPARTMENT_ID", stringValue(params.get("department_id"), null));
    putIfPresent(env, "LIVING_AGENT_BRAIN_ID", stringValue(params.get("brain_id"), null));
    putIfPresent(env, "LIVING_AGENT_TASK_TYPE", stringValue(params.get("task_type"), null));
    putIfPresent(env, "LIVING_AGENT_REQUESTED_MODEL", stringValue(params.get("model"), null));

    return env;
}
```

### 5.2 Sandbox 执行接口补充

当前 `SandboxSession.executeCommand(command, args)` 只能传命令和参数，不能传环境变量。需要新增重载：

```java
CompletableFuture<ExecutionResult> executeCommand(
    String command,
    List<String> args,
    Map<String, String> env
);
```

`SandboxSessionImpl` 中执行 Docker exec 时，可以使用 shell 包装方式注入环境变量：

```text
ANTHROPIC_API_KEY='...' ANTHROPIC_API_URL='...' claude -p '...' --output-format stream-json
```

或者扩展底层 Docker exec 创建逻辑，为命令显式设置 env。优先建议显式设置 env，避免 shell 转义风险。

### 5.3 CLI 参数扩展

`ClaudeCliTool` 增加以下可选参数：

| 参数 | 类型 | 用途 |
|---|---|---|
| `employee_id` | string | 指定员工模型路由 |
| `department_id` | string | 指定部门模型路由 |
| `brain_id` | string | 指定大脑模型路由 |
| `task_type` | string | 指定任务类型，如 `code_generation`、`code_review`、`debugging` |
| `model` | string | 显式请求模型，格式 `providerId/modelName` |
| `max_tokens` | number | 限制 Claude 请求输出 tokens |

---

## 6. Anthropic 代理接口设计

### 6.1 Controller 路由

```java
@RestController
@RequestMapping("/api/v1/proxy/anthropic")
@RequiredArgsConstructor
public class ClaudeProxyController {
    private final ClaudeProxyService proxyService;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "ok",
            "service", "living-agent-anthropic-proxy"
        );
    }

    @PostMapping(value = "/v1/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter createMessage(
        @RequestHeader HttpHeaders headers,
        @RequestBody AnthropicMessagesRequest request,
        HttpServletRequest servletRequest
    ) {
        ClaudeProxyRequestContext context = ClaudeProxyRequestContext.from(headers, servletRequest);
        return proxyService.createMessage(request, context);
    }

    @PostMapping("/v1/messages/count_tokens")
    public AnthropicTokenCountResponse countTokens(@RequestBody AnthropicTokenCountRequest request) {
        return proxyService.countTokens(request);
    }
}
```

### 6.2 ClaudeProxyRequestContext

```java
@Data
@Builder
public class ClaudeProxyRequestContext {
    private String requestId;
    private String sessionId;
    private String employeeId;
    private String departmentId;
    private String brainId;
    private String taskType;
    private String requestedModel;
    private String apiKey;
    private String authToken;
    private Instant receivedAt;
}
```

上下文来源优先级：

1. HTTP query/header：`x-living-agent-employee-id`、`x-living-agent-brain-id` 等。
2. Claude CLI 环境变量透传出的 session 绑定信息。
3. `ClaudeExecutionGateway` 的 session registry。
4. 默认配置：`defaultBrainId=tech`、`defaultDepartmentId=tech`、`defaultTaskType=code_generation`。

---

## 7. 模型路由设计

### 7.1 路由优先级

`ClaudeProxyModelRouter` 按以下顺序解析模型：

1. 如果 `requestedModel` 存在，按 `providerId/modelName` 精确解析。
2. 如果 `employeeId` 存在，调用：
   - `BrainModelResolver.resolveForEmployee(employeeId, departmentId, departmentBrainId)`
3. 如果 `brainId` 存在，调用：
   - `BrainModelResolver.resolve(brainId)`
4. 如果 `departmentId=tech` 且任务类型是代码类任务，优先选择 `bestFor` 包含代码、编程、开发、推理的模型。
5. 回退到配置推荐模型。
6. 最后回退到 `BrainModelResolver.resolveDefault("tech")`。

### 7.2 路由输出

```java
public record ClaudeProxyResolvedModel(
    String providerId,
    String modelId,
    String modelName,
    String displayName,
    String baseUrl,
    String apiKey,
    Protocol protocol,
    Integer contextWindow,
    Integer maxOutputTokens,
    Double temperature,
    Boolean supportsToolChoice
) {}
```

### 7.3 协议限制

本阶段代理的模型调用目标统一使用 `OPENAI_COMPATIBLE`。

也就是说：

- Ollama 使用 `/v1/chat/completions`
- LM Studio 使用 `/v1/chat/completions`
- llama.cpp OpenAI server 使用 `/v1/chat/completions`
- OpenRouter/OpenAI-compatible 服务使用 `/v1/chat/completions`

`Protocol.ANTHROPIC` 暂不作为模型池下游调用主路径。后续如需支持真实 Anthropic，可在 `LlmClientFactory` 中新增 Anthropic 下游 client，但不影响 Claude CLI 入口协议。

---

## 8. Anthropic → OpenAI 请求转换

### 8.1 字段映射

| Anthropic 字段 | OpenAI-compatible 字段 | 说明 |
|---|---|---|
| `model` | `model` | 实际使用路由后的模型名覆盖 |
| `system` | `messages[role=system]` | 支持 string 与 content block list |
| `messages[].role=user` | `messages[].role=user` | 文本与图片 block 需要规范化 |
| `messages[].role=assistant` | `messages[].role=assistant` | text/thinking/tool_use 需要拆分 |
| `messages[].content[].type=text` | `content` | 合并为字符串或 OpenAI content array |
| `messages[].content[].type=tool_result` | `role=tool` | 使用 `tool_call_id` 关联 |
| `tools[].input_schema` | `tools[].function.parameters` | Claude tool schema 转 OpenAI function tool |
| `tool_choice` | `tool_choice` | 尽量透传，不支持时降级 |
| `max_tokens` | `max_tokens` | 受配置上限裁剪 |
| `temperature` | `temperature` | 透传或使用模型默认值 |
| `stream` | `stream=true` | Claude CLI 默认需要流式 |

### 8.2 thinking block 处理

Claude CLI 可能使用 thinking 相关 content block。OpenAI-compatible 模型不一定原生支持 thinking block，本阶段采用文本标签兼容策略：

```text
<thinking>
...
</thinking>
```

当 Anthropic assistant 消息中存在 `thinking` block 时，转换为 assistant 文本内容的一部分。

### 8.3 tool_use 处理

Anthropic `tool_use` block 转换为 OpenAI `tool_calls`：

```json
{
  "role": "assistant",
  "tool_calls": [
    {
      "id": "toolu_xxx",
      "type": "function",
      "function": {
        "name": "tool_name",
        "arguments": "{...}"
      }
    }
  ]
}
```

Anthropic `tool_result` block 转换为 OpenAI tool message：

```json
{
  "role": "tool",
  "tool_call_id": "toolu_xxx",
  "content": "..."
}
```

---

## 9. OpenAI stream → Anthropic SSE 转换

### 9.1 SSE 事件顺序

Claude CLI 需要的标准事件顺序：

```text
event: message_start
data: {"type":"message_start",...}

event: content_block_start
data: {"type":"content_block_start","index":0,...}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"..."}}

event: content_block_stop
data: {"type":"content_block_stop","index":0}

event: message_delta
data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":...}}

event: message_stop
data: {"type":"message_stop"}
```

### 9.2 AnthropicSseBuilder 职责

`AnthropicSseBuilder` 负责：

1. 生成 `message_start`。
2. 根据 OpenAI delta 类型生成 content block。
3. 将 `delta.content` 转换为 `text_delta`。
4. 将 OpenAI `tool_calls` delta 聚合为 Anthropic `tool_use` block。
5. 识别 `<thinking>` 标签并尽量转换为 thinking block；如果无法稳定识别，则作为普通文本输出。
6. 在结束时生成 `message_delta` 与 `message_stop`。
7. 错误时生成 Anthropic error event。

### 9.3 错误事件

下游 provider 调用失败时返回：

```text
event: error
data: {"type":"error","error":{"type":"api_error","message":"..."}}
```

同时写入审计记录，包含：

- `requestId`
- `sessionId`
- `employeeId`
- `brainId`
- `providerId`
- `modelName`
- `errorType`
- `errorMessage`
- `durationMs`

---

## 10. ClaudeProxyService 处理流程

```java
@Service
@RequiredArgsConstructor
public class ClaudeProxyService {
    private final ClaudeProxyModelRouter modelRouter;
    private final AnthropicToOpenAiConverter requestConverter;
    private final OpenAiStreamClient openAiStreamClient;
    private final AnthropicSseBuilder sseBuilder;
    private final ClaudeProxyAuditService auditService;

    public SseEmitter createMessage(AnthropicMessagesRequest request, ClaudeProxyRequestContext context) {
        SseEmitter emitter = new SseEmitter(0L);
        long start = System.currentTimeMillis();

        CompletableFuture.runAsync(() -> {
            ClaudeProxyResolvedModel model = null;
            try {
                auditService.recordRequestReceived(request, context);

                model = modelRouter.resolve(request, context);
                auditService.recordModelResolved(context, model);

                OpenAiChatCompletionRequest openAiRequest = requestConverter.convert(request, model);

                sseBuilder.sendMessageStart(emitter, request, model);

                openAiStreamClient.stream(model, openAiRequest, chunk -> {
                    List<AnthropicSseEvent> events = sseBuilder.convertChunk(chunk);
                    for (AnthropicSseEvent event : events) {
                        emitter.send(SseEmitter.event().name(event.name()).data(event.payload()));
                        auditService.recordStreamEvent(context, model, event);
                    }
                });

                sseBuilder.sendMessageStop(emitter);
                auditService.recordCompleted(context, model, System.currentTimeMillis() - start);
                emitter.complete();
            } catch (Exception e) {
                sseBuilder.sendError(emitter, e);
                auditService.recordFailed(context, model, e, System.currentTimeMillis() - start);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
```

---

## 11. OpenAI-compatible Client 要求

### 11.1 请求格式

```json
{
  "model": "qwen3-coder",
  "messages": [
    {"role": "system", "content": "..."},
    {"role": "user", "content": "..."}
  ],
  "temperature": 0.7,
  "max_tokens": 8192,
  "stream": true,
  "tools": [],
  "tool_choice": "auto"
}
```

### 11.2 支持的 provider

| Provider | baseUrl 示例 | 说明 |
|---|---|---|
| Ollama | `http://host.docker.internal:11434/v1` | 推荐用于本地模型 |
| LM Studio | `http://host.docker.internal:1234/v1` | 推荐用于本地 OpenAI-compatible server |
| llama.cpp | `http://host.docker.internal:8080/v1` | 需要启用 OpenAI-compatible server |
| OpenRouter | `https://openrouter.ai/api/v1` | 云端兼容服务 |
| 其他 OpenAI-compatible | `https://xxx/v1` | 只要兼容 `/chat/completions` 即可 |

---

## 12. 审计与任务系统集成

### 12.1 审计事件

需要记录以下事件：

| 事件 | 触发时机 |
|---|---|
| `CLAUDE_PROXY_REQUEST_RECEIVED` | 收到 `/v1/messages` 请求 |
| `CLAUDE_PROXY_MODEL_RESOLVED` | 完成模型池路由 |
| `CLAUDE_PROXY_PROVIDER_STREAM_STARTED` | 开始调用下游 provider |
| `CLAUDE_PROXY_STREAM_EVENT` | 输出 Anthropic SSE event |
| `CLAUDE_PROXY_TOOL_USE_DETECTED` | 发现 tool_use block |
| `CLAUDE_PROXY_COMPLETED` | 正常完成 |
| `CLAUDE_PROXY_FAILED` | 异常失败 |
| `CLAUDE_PROXY_CANCELLED` | 请求取消 |

### 12.2 产物记录

Claude CLI 任务完成后，`ClaudeExecutionGateway` 解析 stream-json，并把以下内容写入产物系统：

- prompt
- stdout events
- parsed Claude session id
- final response text
- touched files（后续可扩展）
- generated patches（后续可扩展）
- model provider/model name
- duration/token usage

---

## 13. 安全设计

### 13.1 代理鉴权

支持两种模式：

1. 开发模式：`require-auth=false`，允许本地 Claude CLI 直接访问。
2. 生产模式：`require-auth=true`，校验：
   - `x-api-key`
   - `Authorization: Bearer <token>`
   - `ANTHROPIC_AUTH_TOKEN` 注入值

### 13.2 访问边界

Claude CLI 本身具备文件操作能力，因此需要：

1. `allowed_dirs` 默认只允许 `./agent_workspace`。
2. 不允许默认挂载整个宿主机项目目录。
3. 需要访问项目代码时，由任务系统创建专用 worktree 或受控目录。
4. `dangerously-skip-permissions` 只允许在 sandbox 内使用。
5. 生产环境默认要求人工审批或策略审批。

### 13.3 命令注入防护

`SandboxSession.executeCommand(command,args,env)` 必须避免简单字符串拼接。优先使用 Docker exec 的数组命令与环境变量能力；如果必须 shell 包装，必须对参数与环境变量做严格 shell escaping。

---

## 14. 分阶段实施计划

### 阶段 1：代理骨架与 CLI 指向 Living Agent

目标：Claude CLI 能够请求 Living Agent 内置 `/v1/messages`，并得到合法 Anthropic SSE。

任务：

1. 新增 `ClaudeCliProperties.proxy` 配置。
2. 改造 `ClaudeExecutionGateway`，注入 `ANTHROPIC_API_URL`、`ANTHROPIC_BASE_URL`、`ANTHROPIC_API_KEY`。
3. 为 `SandboxSession` 增加带 env 的 `executeCommand` 重载。
4. 新增 `ClaudeProxyController`：
   - `GET /api/v1/proxy/anthropic/health`
   - `POST /api/v1/proxy/anthropic/v1/messages`
   - `POST /api/v1/proxy/anthropic/v1/messages/count_tokens`
5. 先用 mock response 返回最小可用 SSE。

验收：

```bash
ANTHROPIC_API_KEY=sk-living-agent-claude-proxy \
ANTHROPIC_API_URL=http://localhost:8480/api/v1/proxy/anthropic/v1 \
ANTHROPIC_BASE_URL=http://localhost:8480/api/v1/proxy/anthropic \
claude -p "只回复 OK" --output-format stream-json --verbose
```

能够看到 Claude CLI 正常输出 stream-json，且代理收到请求。

### 阶段 2：接入模型池与 OpenAI-compatible Provider

目标：代理真实调用模型池中的模型。

任务：

1. 新增 `ClaudeProxyModelRouter`。
2. 复用 `BrainModelResolver`，支持 `brainId`、`employeeId`、`departmentId`、`taskType`。
3. 新增 `AnthropicToOpenAiConverter`。
4. 新增或复用 `OpenAiCompatibleClient` 的 streaming 调用能力。
5. 实现 `OpenAiStreamChunkParser`。
6. 实现 `AnthropicSseBuilder` 基础文本流转换。

验收：

- 模型池配置 Ollama/LM Studio 模型后，Claude CLI 请求可以打到该模型。
- 日志能看到 resolved provider/model。
- Claude CLI 可以收到真实模型输出。

### 阶段 3：工具块、thinking、审计与错误处理

目标：增强 Claude Code 类任务兼容性。

任务：

1. 实现 `ClaudeToolBlockMapper`。
2. 支持 Anthropic `tool_use` ↔ OpenAI `tool_calls`。
3. 支持 `tool_result` 转换。
4. 支持 `<thinking>` 标签兼容。
5. 完善 error event。
6. 接入 `ClaudeProxyAuditService`。
7. 记录 token usage、duration、model usage。

验收：

- Claude CLI 执行代码类任务时不会因 tool schema 转换失败而中断。
- 下游 provider 报错时，Claude CLI 能得到 Anthropic error event。
- 审计表中可以看到完整请求链路。

### 阶段 4：会话、产物与任务系统整合

目标：把 Claude CLI 作为 Living Agent Tech 员工的标准代码执行工具。

任务：

1. `ClaudeExecutionGateway` 持久化 Living Agent session 与 Claude session id 映射。
2. `start/poll/cancel` 对接真实进程/exec 生命周期。
3. stream-json 输出接入任务事件系统。
4. 输出内容归档为 artifact。
5. 与审批/权限系统联动。

验收：

- `claude_cli.start` 返回 job id。
- `claude_cli.poll` 可看到实时或准实时事件。
- `claude_cli.cancel` 能终止底层执行。
- 任务详情页可查看模型、prompt、输出、错误、产物。

---

## 15. 最小可用实现清单

优先实现以下文件：

```text
living-agent-core/src/main/java/com/livingagent/core/sandbox/ClaudeCliProperties.java
living-agent-core/src/main/java/com/livingagent/core/sandbox/ClaudeExecutionGateway.java
living-agent-core/src/main/java/com/livingagent/core/sandbox/SandboxSession.java
living-agent-core/src/main/java/com/livingagent/core/sandbox/impl/SandboxSessionImpl.java

living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ClaudeProxyController.java
living-agent-gateway/src/main/java/com/livingagent/gateway/dto/anthropic/AnthropicMessagesRequest.java
living-agent-gateway/src/main/java/com/livingagent/gateway/dto/anthropic/AnthropicTokenCountRequest.java

living-agent-core/src/main/java/com/livingagent/core/proxy/anthropic/ClaudeProxyService.java
living-agent-core/src/main/java/com/livingagent/core/proxy/anthropic/ClaudeProxyModelRouter.java
living-agent-core/src/main/java/com/livingagent/core/proxy/anthropic/ClaudeProxyRequestContext.java
living-agent-core/src/main/java/com/livingagent/core/proxy/anthropic/converter/AnthropicToOpenAiConverter.java
living-agent-core/src/main/java/com/livingagent/core/proxy/anthropic/sse/AnthropicSseBuilder.java
```

---

## 16. 成功标准

本方案完成后，应满足：

1. 不需要启动 `free-claude-code-main`。
2. Claude CLI 请求全部进入 Living Agent Gateway。
3. Claude CLI 使用的模型来自 Living Agent 模型池。
4. 可按 `employeeId`、`departmentId`、`brainId`、`taskType` 路由不同模型。
5. OpenAI-compatible 本地模型可以服务 Claude CLI。
6. Claude CLI `--output-format stream-json` 正常可用。
7. 请求、模型、输出、错误、token usage 可审计。
8. 后续可以自然扩展到任务产物、审批、安全沙箱、多人协作开发流程。

---

## 17. 明确不采用的方案

本文件明确不再采用“外部代理优先”的方案：

```text
Claude CLI -> free-claude-code-main -> 本地模型
```

外部代理可以保留为调研参考或临时调试工具，但 Living Agent 正式集成路径以内部代理为准：

```text
Claude CLI -> Living Agent Anthropic Proxy -> Living Agent Model Pool -> 本地/云端模型
```
