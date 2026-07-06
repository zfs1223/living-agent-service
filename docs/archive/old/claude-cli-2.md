
## 6. 分阶段实施计划

### Phase 0: 基础设施准备（第 1 周）

| 任务 | 产出 | 代码落点 | 验收标准 |
|------|------|---------|---------|
| 安装 Claude CLI + free-claude-code-main | 本地可运行环境 | 无代码变更，仅需 pip install | 手动执行 `claude -p "hello"` 成功 |
| 确认 Java 25 + Spring Boot 3.x 运行环境 | 开发环境就绪 | `pom.xml` / `build.gradle` | `mvn test` 全部通过 |
| 搭建 Docker Compose 包含 PostgreSQL + Claude CLI sandbox | 开发 Docker 环境 | `docker/docker-compose.dev.yml` | `docker compose up` 后所有容器 healthy |
| 配置 `application-local.yml` 开发环境 | 本地开发配置 | `src/main/resources/application-local.yml` | 本地启动无需外部依赖 |

**阶段门禁**：
- [ ] `claude --version` 返回 ≥ 0.2.0
- [ ] `pip show free-claude-code-main` 显示已安装
- [ ] `http://localhost:8082/health` 返回 `{"status": "ok"}`

---

### Phase 1: 短期方案落地 — Claude CLI 接入自由代理（第 2-3 周）

> **核心目标**：通过环境变量将 Claude CLI 连接到 free-claude-code-main 代理服务，实现端到端可调用。
>
> **前置条件**：Phase 0 门禁全部通过。

| # | 任务 | 产出 | 代码落点 | 验收标准 |
|---|------|------|---------|---------|
| 1.1 | 新增 `ClaudeCliProperties` 配置类 | `ClaudeCliProperties.java` | `living-agent-config/src/main/java/com/soarcloud/agent/config/claude/ClaudeCliProperties.java` | `@ConfigurationProperties(prefix = "living-agent.claude-cli")` 绑定测试通过 |
| 1.2 | 改造 `ClaudeExecutionGateway` 注入代理环境变量 | 环境变量注入 | `living-agent-core/tools/.../ClaudeExecutionGateway.java` | `startSession()` 时 `ANTHROPIC_API_URL`/`ANTHROPIC_BASE_URL`/`ANTHROPIC_AUTH_TOKEN` 正确设置 |
| 1.3 | 改造 `SandboxService` 支持 Claude 专用 sandbox config | Claude sandbox | `living-agent-core/runtime/.../SandboxService.java` | 新增 `SandboxConfig.CLAUDE_DEFAULT`，工作区隔离、权限控制生效 |
| 1.4 | 部署 free-claude-code-main 到 localhost:8082 | 代理服务运行 | `docker/docker-compose.dev.yml` 新增 claude-proxy 服务 | `/health` 返回 200，`/v1/messages` 可接收请求 |
| 1.5 | `ClaudeCliTool` 通过代理执行 Claude CLI | 端到端可运行 | `living-agent-core/tools/.../ClaudeCliTool.java` | 调用 `ClaudeCliTool.execute(prompt="编写一个 Hello World")` 成功返回 |
| 1.6 | 实现 `--output-format stream-json` 解析 | 事件流解析 | 新文件：`living-agent-core/trace/.../StreamJsonParser.java` | 对标准 stream-json 输出逐行 parse，正确识别 `content_block_delta`、`tool_result` 等事件 |

**关键接口设计 — Phase 1**：

```java
/**
 * Phase 1: Claude CLI 工具接口
 */
public interface ClaudeCliTool {
    /**
     * 同步执行 Claude CLI 命令
     */
    ClaudeCliResult execute(String prompt, String taskId, String employeeId);
    
    /**
     * 异步启动 Claude CLI 会话
     */
    String startSession(ClaudeCliRequest request);
    
    /**
     * 轮询会话状态
     */
    SessionStatus pollSession(String sessionId);
    
    /**
     * 取消会话
     */
    void cancelSession(String sessionId);
}

@Data
public class ClaudeCliResult {
    private String sessionId;
    private String response;          // 最终响应文本
    private List<ArtifactRecord> artifacts;  // 产生产物列表
    private TokenUsage tokenUsage;
    private Instant completedAt;
}

@Data
public class ClaudeCliRequest {
    private String prompt;
    private String taskId;
    private String employeeId;
    private String parentSessionId;
    private List<String> allowedDirs;
}
```

**阶段门禁**：
- [ ] `ClaudeCliTool.execute("hello")` 端到端成功
- [ ] stream-json 输出被 StreamJsonParser 正确解析
- [ ] 环境变量注入不泄漏到非 Claude CLI 进程
- [ ] `docker compose up -d claude-proxy && curl -f http://localhost:8082/health` 通过

---

### Phase 2: Java 侧 Anthropic Messages 兼容代理（第 4-7 周）

> **核心目标**：在 Spring Boot 内部实现轻量 Anthropic Messages 兼容代理，模型池中的 OpenAI-compatible 模型（LM Studio、Ollama、llama.cpp）暴露给 Claude CLI。
>
> **前置条件**：Phase 1 门禁全部通过。

| # | 任务 | 产出 | 代码落点 | 验收标准 |
|---|------|------|---------|---------|
| 2.1 | 实现 `AnthropicOpenAiConverter` | 双向转换器 | `living-agent-core/proxy/converter/AnthropicOpenAiConverter.java` | 50+ 测试用例通过，覆盖 messages/system/工具调用 |
| 2.2 | 实现 `AnthropicSseBuilder` | SSE 构建器 | `living-agent-core/proxy/converter/AnthropicSseBuilder.java` | 生成的 SSE 与 Claude API `/v1/messages` 响应格式 1:1 兼容 |
| 2.3 | 实现 `ClaudeProxyController` `/v1/messages` 端点 | REST 端点 | `living-agent-gateway/.../ClaudeProxyController.java` | `curl -X POST http://localhost:8081/v1/messages` 返回 SSE 流 |
| 2.4 | 实现 `ClaudeProxyService` 转发逻辑 | 代理转发 | `living-agent-core/proxy/ClaudeProxyService.java` | 端到端请求成功，支持多轮对话 |
| 2.5 | 集成 `ModelPoolManager` 查询模型 | 模型池接入 | `ClaudeProxyService` 内部注入 `ModelPoolManager` | 从池中查询可用模型成功，支持 provider 过滤 |
| 2.6 | 实现 `ClaudeProxyModelRouter` | 模型路由器 | `living-agent-core/proxy/router/ClaudeProxyModelRouter.java` | 按 employee/department/任务类型返回不同模型 |

**关键设计 — 模型路由决策链**：

```
用户请求 (ClaudeCliRequest)
    ↓
ClaudeProxyModelRouter.resolve(ModelSelectionContext)
    ↓
Layer 1: 检查 Task 级别 override (Task.modelOverrideId)
    ├─ 命中 → 使用该模型，记录选择原因
    └─ 未命中 → 进入 Layer 2
    ↓
Layer 2: 检查 Employee 的 department + role 规则
    ├─ 命中 → 使用该模型，记录选择原因
    └─ 未命中 → 进入 Layer 3
    ↓
Layer 3: 使用 Department Default 模型
    ├─ 命中 → 使用该模型
    └─ 未命中 → 进入 Layer 4
    ↓
Layer 4: Fallback 到系统默认模型 (model_type = CHAT + usage_tags 含 claude-proxy)
```

**关键接口设计 — Phase 2**：

```java
/**
 * 模型路由决策上下文
 */
@Data
public class ModelSelectionContext {
    private String employeeId;
    private String department;
    private String employeeRole;
    private String skillTags;        // "java,spring-boot"
    private String taskCategory;     // 任务分类
}

/**
 * 模型选择结果
 */
@Data
public class ModelSelectionResult {
    private String modelId;
    private String modelProvider;
    private String selectionReason;   // 选择原因，用于审计
    private String selectedLayer;     // TASK_OVERRIDE / EMPLOYEE_RULE / DEPARTMENT_DEFAULT / FALLBACK
    private Instant selectedAt;
}

/**
 * 模型路由器
 */
public interface ClaudeProxyModelRouter {
    /**
     * 根据上下文选择代理模型
     */
    ModelSelectionResult resolve(ModelSelectionContext context);
}

/**
 * Anthropic Messages 兼容代理接口
 */
public interface IAnthropicProxyService {
    /**
     * 处理 Anthropic Messages 请求（SSE 流式返回）
     */
    SseEmitter handleMessage(AnthropicMessagesRequest request, String employeeId);
    
    /**
     * 取消会话
     */
    void cancelSession(String sessionId);
    
    /**
     * Token 计数
     */
    AnthropicTokenCountResponse countTokens(AnthropicTokenCountRequest request);
}
```

**测试用例覆盖矩阵**：

| 场景 | 请求类型 | 预期行为 | 测试方法 |
|------|---------|---------|---------|
| 基础聊天 | 简单 prompt | 返回 SSE text_delta | `testBasicChat()` |
| 工具调用 | 含 tool_use | 返回 tool_use 块 + input_json | `testToolCall()` |
| 多轮对话 | 带 history | 正确拼接 system/messages | `testMultiTurn()` |
| 超时处理 | 长 prompt | 返回超时错误事件 | `testTimeout()` |
| 空响应 | 模型不可用 | 返回错误事件 + 状态码 503 | `testModelUnavailable()` |
| 大文本 | 超长 response | 正确分块 SSE 推送 | `testLongResponse()` |
| 系统消息 | 含 system prompt | 正确转换 Anthropic → OpenAI | `testSystemMessage()` |

**阶段门禁**：
- [ ] 50+ 测试用例全部通过
- [ ] `curl` 端到端请求（Claude CLI → Java 代理 → LM Studio → SSE 返回）成功
- [ ] AnthropicSseBuilder 输出与 Claude API 格式对齐（diff 比较）
- [ ] ModelRouter 对 3 种不同 employee 返回不同模型

---

### Phase 3: stream-json 事件接入 — 任务/产物/审计系统（第 8-9 周）

> **核心目标**：将 Claude CLI 的 stream-json 输出接入 Living Agent 内部事件系统，持久化到 `ClaudeCliJob`、`ArtifactRecord`、`AuditLog`。
>
> **前置条件**：Phase 2 门禁全部通过。

| # | 任务 | 产出 | 代码落点 | 验收标准 |
|---|------|------|---------|---------|
| 3.1 | 实现 `StreamJsonEventMapper` | 事件映射器 | `living-agent-core/trace/.../StreamJsonEventMapper.java` | 10 种 stream-json 事件类型映射覆盖率 100% |
| 3.2 | 改造 `ClaudeCliTool` 事件监听 | 实时事件流 | `ClaudeCliTool.java` 内部新增事件监听器 | SSE 推送延迟 < 200ms（从 CLI stdout 到 SSE 发出） |
| 3.3 | 实现 `ClaudeCliEventProcessor` 产物提取 | 产物追踪 | `living-agent-core/trace/.../ClaudeCliEventProcessor.java` | Bash write/edit 操作被正确提取为 ArtifactRecord |
| 3.4 | 新增 `ClaudeCliJob` + `AutonomyTraceEvent` 实体 | 数据模型 | `living-agent-persistence/.../entity/ClaudeCliJob.java` | 数据库迁移脚本执行成功，表创建 |
| 3.5 | 实现 `ClaudeCliJobRepository` + `ArtifactRecordRepository` | 持久化 DAO | `living-agent-persistence/.../repository/` | CRUD 操作正常，JPQL 查询通过 |
| 3.6 | 实现 `AuditLogRecorder` | 审计日志 | `living-agent-core/audit/.../AuditLogRecorder.java` | CLI_START、TOOL_CALL、MODEL_SELECTION 事件正确记录 |
| 3.7 | 实现 SSE 事件推送管线 | SSE 推送 | `living-agent-gateway/.../ClaudeEventSseController.java` | 前端可通过 `EventSource` 接收实时事件 |

**事件处理管线架构**：

```
┌─────────────────────────────────────────────────────────────────┐
│                    ClaudeCliTool                                 │
│                                                                  │
│  Process (Claude CLI)                                            │
│      │                                                           │
│      ├─ stdin (prompt)                                           │
│      └─ stdout (stream-json)                                     │
│            │                                                     │
│            ▼                                                     │
│  StreamJsonParser                                                │
│      │ (逐行 parse, 事件分发)                                     │
│      ├─────────────────┬─────────────────┬─────────────────┐     │
│      ▼                 ▼                 ▼                 ▼     │
│  TraceEventStore    ArtifactExtractor  AuditLogger       SSEPusher│
│      │                 │                 │                 │     │
│      ▼                 ▼                 ▼                 ▼     │
│  autonomy_        artifact_      claude_cli        WebSocket    │
│  trace_event      record         audit_log         /SSE         │
└─────────────────────────────────────────────────────────────────┘
```

**事件类型完整映射**：

| stream-json 事件 | 内部事件类型 | 持久化到 | SSE 推送 | 产物提取 |
|-----------------|-------------|---------|---------|---------|
| `stdin_prompt` | `TRACE_INPUT` | `ClaudeCliJob.prompt_hash` | ✓ | ✗ |
| `session_id` | `TRACE_SESSION_START` | `ClaudeCliJob` | ✓ | ✗ |
| `content_block_start` (text) | `TRACE_TEXT_START` | `AutonomyTraceEvent` | ✓ | ✗ |
| `content_block_delta` (text_delta) | `TRACE_TEXT_CHUNK` | (累积到 trace) | ✓ | ✗ |
| `content_block_start` (tool_use) | `TRACE_TOOL_START` | `AutonomyTraceEvent` | ✓ | ✗ |
| `content_block_delta` (input_json_delta) | `TRACE_TOOL_PARAM` | (累积参数) | ✓ | ✗ |
| `content_block_stop` (tool_use) | `TRACE_TOOL_COMPLETE` | `AutonomyTraceEvent` | ✓ | ✗ |
| `tool_result` | `TRACE_TOOL_RESULT` | `AutonomyTraceEvent` | ✓ | ✗ |
| `content_block_start` (file) | `TRACE_FILE_WRITE` | `ArtifactRecord` | ✗ | ✓ |
| `message_delta` | `TRACE_MESSAGE_END` | `ClaudeCliJob` | ✗ | ✗ |
| `error` | `TRACE_ERROR` | `ClaudeCliJob.error_message` | ✓ | ✗ |

**产物提取规则**：

```java
public class ArtifactExtractor {
    /**
     * 从 stream-json 事件中提取文件变更
     */
    public List<ArtifactRecord> extract(ParsedEvent event, String jobId) {
        List<ArtifactRecord> artifacts = new ArrayList<>();
        
        switch (event.getType()) {
            case FILE_WRITE:       // Claude CLI 生成/写入文件
                artifacts.add(buildArtifact(jobId, "generated", event));
                break;
            case FILE_DELETE:      // 删除文件
                artifacts.add(buildArtifact(jobId, "deleted", event));
                break;
            case FILE_MOVE:        // mv/cp 操作
                artifacts.add(buildArtifact(jobId, "modified", event));
                break;
            case FILE_MKDIR:       // mkdir
                artifacts.add(buildArtifact(jobId, "directory", event));
                break;
            case GIT_COMMIT:       // git commit/push
                artifacts.add(buildArtifact(jobId, "commit", event));
                break;
            case DEPENDENCY_INSTALL: // npm install / pip install
                artifacts.add(buildArtifact(jobId, "dependency", event));
                break;
        }
        return artifacts;
    }
}
```

**阶段门禁**：
- [ ] StreamJsonEventMapper 映射覆盖率 ≥ 100%（10 种事件类型均覆盖）
- [ ] ClaudeCliJob 记录创建、更新、完成完整生命周期
- [ ] ArtifactRecord 对至少 5 种产物类型提取正确
- [ ] AuditLog 对 CLI_START、MODEL_SELECTION、TOOL_CALL 记录详细 JSON
- [ ] SSE 推送端到端延迟 < 200ms（p95）
- [ ] 数据库表 `claude_cli_job`、`artifact_record`、`claude_cli_audit_log` 创建成功

---

### Phase 4: 深度集成 — 模型选择路由 + 可观测性（第 10-12 周）

> **核心目标**：Claude CLI 完全纳入模型池调度，支持按员工/部门/任务类型选择代理模型，并实现完善的可观测性。
>
> **前置条件**：Phase 3 门禁全部通过。

| # | 任务 | 产出 | 代码落点 | 验收标准 |
|---|------|------|---------|---------|
| 4.1 | `ClaudeCliTool` 接入 `ClaudeProxyModelRouter` | 模型选择可用 | `ClaudeCliTool.execute()` 内部调用 `modelRouter.resolve(context)` | 按 employee/department 返回不同模型 |
| 4.2 | 实现 `EmployeeSkillTagService` | skill_tags CRUD | `living-agent-core/employee/.../EmployeeSkillTagService.java` | skill_tags 增删查正常 |
| 4.3 | 实现 `ClaudeProcessPool` | 进程池管理 | `living-agent-core/runtime/.../ClaudeProcessPool.java` | 并发支持 `maxConcurrentSessions`，无进程泄漏 |
| 4.4 | 添加 Prometheus 指标 | `/actuator/prometheus` | `living-agent-core/monitor/.../ClaudeCliMetrics.java` | Grafana 面板展示 Claude CLI 指标 |
| 4.5 | 集成 OpenTelemetry 链路追踪 | trace context | `ClaudeCliTool.execute()` 内部创建 `Span` | Jaeger 可查看完整链路 |
| 4.6 | 性能优化 + 安全加固 | 生产就绪 | 各组件内部优化 | 压测支持 10+ 并发 Claude CLI 会话 |
| 4.7 | 文档更新 + 用户手册 | 文档完备 | `docs/user-guide/claude-cli-guide.md` | 用户可独立使用 |

**关键设计 — 模型优先级配置**：

```yaml
# application.yml 模型路由配置
living-agent:
  claude-cli:
    model-routing:
      enabled: true
      layers:
        task-override:
          enabled: true
          priority: 100
          description: "任务级别模型覆盖"
        employee-rule:
          enabled: true
          priority: 80
          description: "员工/角色规则"
        department-default:
          enabled: true
          priority: 60
          description: "部门默认模型"
        fallback:
          enabled: true
          priority: 10
          description: "回退到系统默认模型"
      employee-rules:
        - employee-role: "CODER"
          department: "engineering"
          model-id: "model-qwen3-coder-7b"
          skill-match: ["java", "python", "spring-boot"]
        - employee-role: "FRONTEND"
          department: "engineering"
          model-id: "model-deepseek-coder-v2"
          skill-match: ["typescript", "react"]
        - employee-role: "TESTER"
          department: "engineering"
          model-id: "model-qwen3-coder-7b"
          skill-match: ["python", "java"]
      department-defaults:
        engineering: "model-qwen3-coder-7b"
        research: "model-claude-sonnet"
        support: "model-gpt-4o-mini"
```

**阶段门禁**：
- [ ] 模型路由对 5+ 员工返回正确模型
- [ ] ClaudeProcessPool 并发 3 会话无泄漏
- [ ] Prometheus 指标通过 `/actuator/prometheus` 暴露
- [ ] Jaeger trace 完整链路可追踪
- [ ] 压测 10 并发 Claude CLI 会话稳定运行 ≥ 1 小时

---

### Phase 里程碑总结

| Phase | 周次 | 核心交付 | 验收方式 |
|-------|------|---------|---------|
| Phase 0 | W1 | Claude CLI 可运行环境 | 手动命令成功 |
| Phase 1 | W2-3 | 短期方案端到端可调用 | ClaudeCliTool 调用成功 |
| Phase 2 | W4-7 | Java 侧 Anthropic 代理 + 模型池集成 | 50+ 测试通过 |
| Phase 3 | W8-9 | stream-json 事件接入任务/产物/审计 | 数据模型就绪 |
| Phase 4 | W10-12 | 深度集成 + 可观测性 | 压测通过 |

---

## 7. 风险与注意事项

### 7.1 技术风险矩阵

| 编号 | 风险描述 | 影响 | 概率 | 风险等级 | 缓解措施 | 应急方案 |
|------|---------|------|------|---------|---------|---------|
| T1 | Anthropic→OpenAI 消息格式转换不完整，导致工具调用/复杂消息失败 | 高：Claude CLI 工具调用链中断 | 高 | 🔴 严重 | ① `AnthropicOpenAiConverter` 覆盖 50+ 测试用例（覆盖 system prompt、多轮对话、工具调用、嵌套工具调用）；② 与 `free-claude-code-main` 的 `message_converter.py` 逐字段对比测试 | 回退到 Phase 1 短期方案（环境变量直连代理）；③ 记录不兼容消息格式到 `AuditLog`，后续优先补充 |
| T2 | SSE 格式错误导致 Claude CLI 解析失败，会话中断 | 高：用户会话被中断 | 高 | 🔴 严重 | ① `AnthropicSseBuilder` 1:1 对齐 Claude API `/v1/messages` SSE 格式；② 集成 `free-claude-code-main` 的 `sse_builder.py` 自动化对比测试（每 PR 必跑） | 服务端检测 Claude CLI 连接异常后自动重建 SSE 会话（最多重试 3 次） |
| T3 | 本地模型响应慢（> 60s），用户体验差 | 中：等待超时但任务可完成 | 高 | 🟡 中等 | ① `ClaudeProxyService` 设置可配置 timeout（默认 120s）；② SSE 推送中持续发送 `data: {type: "ping"}` 心跳事件 | 触发 timeout 后向 SSE 推送 `{"type":"error","error":{"message":"模型响应超时，已回退到缓存策略"}}` |
| T4 | Claude CLI 进程泄漏导致资源耗尽 | 高：系统资源耗尽，服务不可用 | 中 | 🟡 中等 | ① `ClaudeProcessPool` 管理进程生命周期；② 强制 `sessionTimeout`（默认 30 分钟）；③ 后台守护线程每 5 分钟扫描僵尸进程 | 守护线程检测到泄漏后自动 kill -9 进程，清理 PID 映射表，记录 `AuditLog.error` |
| T5 | 多并发会话之间输出混乱 | 中：产物交叉污染 | 低 | 🟢 低风险 | ① 每个会话独立工作区目录（`/workspace/{sessionId}/`）；② `ProcessBuilder` 独立 stdin/stdout 管道 | 检测到交叉污染时自动中断冲突会话，标记 `ClaudeCliJob.status = ERROR` |
| T6 | stream-json 解析异常导致事件丢失 | 中：审计不完整但任务可继续 | 低 | 🟢 低风险 | ① `StreamJsonParser` 容错设计（JSON parse 失败记录为 `TRACE_RAW_OUTPUT`）；② 每行独立 parse，一行失败不影响其他行 | 异常事件写入独立 `claude_cli_audit_log` 表的 `raw_output` 字段，供人工复核 |
| T7 | 模型路由决策延迟 > 1s | 低：增加请求端到端延迟 | 低 | 🟢 低风险 | ① `ClaudeProxyModelRouter` 内部缓存员工/部门路由规则（Guava Cache，5 分钟过期）；② 路由决策 < 10ms | 缓存失效时降级到默认模型，记录 `selectionReason = "FALLBACK_CACHE_MISS"` |

**风险等级定义**：
- 🔴 严重：影响核心功能，必须在本 Phase 解决
- 🟡 中等：影响用户体验，可在 Phase 4 优化中解决
- 🟢 低风险：影响局部，可在后续迭代中解决

---

### 7.2 安全风险与防护体系

#### 7.2.1 命令执行安全

```
┌─────────────────────────────────────────────────────────────────┐
│                    Claude CLI 命令安全控制层级                     │
│                                                                 │
│  Layer 1: Docker 容器级隔离                                      │
│      └─ 容器运行在非特权用户（非 root），drop ALL capabilities       │
│      └─ 禁用网络访问（除非显式 allow），防止外连恶意服务             │
│                                                                 │
│  Layer 2: Sandbox 工作区隔离                                      │
│      └─ `allowed-dirs` 白名单机制，只允许访问指定目录               │
│      └─ 挂载为只读（除了 `/workspace/{sessionId}/`）                │
│      └─ 禁止挂载 /proc、/sys、/dev 等敏感路径                      │
│                                                                 │
│  Layer 3: 命令过滤器                                              │
│      └─ 正则黑名单：rm -rf /、mkfs、dd of=/dev/、curl | bash 等      │
│      └─ 敏感操作审计：cat /etc/shadow、su、sudo 等记录到 AuditLog   │
│      └─ --dangerously-skip-permissions 默认为 false，需显式启用      │
│                                                                 │
│  Layer 4: 产物校验                                                │
│      └─ 写入文件后自动触发 hash 校验（SHA-256）                    │
│      └─ 大文件（> 50MB）需要人工审批                               │
│      └─ 可执行文件（chmod +x）需要签名验证                         │
└─────────────────────────────────────────────────────────────────┘
```

#### 7.2.2 代理代理绕过防护

| 攻击场景 | 防护手段 | 实现位置 |
|---------|---------|---------|
| Claude CLI 环境变量被篡改指向外部 API | `ClaudeExecutionGateway` 覆写 `ANTHROPIC_API_URL` 为硬编码的本地代理地址 | `ClaudeExecutionGateway.java` |
| 代理 `ClaudeProxyController` 被调用指向未授权模型 | `ClaudeProxyService` 校验 `modelId` 必须在 `ModelPoolManager` 的活跃模型列表中 | `ClaudeProxyService.java` |
| 恶意请求绕过认证直接调用 `/v1/messages` | Spring Security 配置 `ANTHROPIC_PROXY_ENDPOINT` 仅允许内部服务调用（JWT + service account） | `SecurityConfig.java` |

#### 7.2.3 Token 与密钥管理

```yaml
# application-local.yml 密钥管理配置
living-agent:
  claude-cli:
    env-secrets:
      ANTHROPIC_API_KEY: ${CLAUDE_PROXY_API_KEY:sk-placeholder-key-for-proxy}  # 从环境变量读取，不写日志
      ANTHROPIC_API_URL: ${CLAUDE_PROXY_URL:http://localhost:8082/v1}
      ANTHROPIC_BASE_URL: ${CLAUDE_PROXY_URL:http://localhost:8082}
    
    # 密钥轮换
    key-rotation:
      enabled: true
      interval-days: 90
      algorithm: AES-256-GCM
```

**密钥安全原则**：
1. 所有密钥通过环境变量注入，**绝不**写入日志、trace、审计表
2. 密钥在内存中使用 `char[]` 存储（非 String，可手动清零）
3. 密钥轮换通过 KMS（HashiCorp Vault / AWS Secrets Manager）统一管理
4. 日志中的 API Key 前 4 位和后 4 位可见，中间用 `****` 脱敏

---

### 7.3 运维风险与应急预案

#### 7.3.1 本地模型故障处理

```
┌─────────────────────────────────────────────────────────────────┐
│                    模型故障降级流程                                │
│                                                                 │
│  模型 health_check 失败                                          │
│      │                                                          │
│      ▼                                                          │
│  第 1 级：自动切换同 provider 的备用模型                           │
│      └─ ModelPoolManager.failoverTo(modelId)                     │
│      └─ 记录到 Prometheus 指标：claude_model_failover_count      │
│      └─ 发送告警：钉钉/企微 Webhook                              │
│                                                                 │
│  第 2 级：同 department 的另一个 provider 模型                     │
│      └─ ClaudeProxyModelRouter.fallbackToDifferentProvider()     │
│      └─ 记录 selectionReason = "FALLBACK_PROVIDER_SWITCH"        │
│      └─ 用户端可见进度提示："正在切换到备用模型..."                 │
│                                                                 │
│  第 3 级：回退到云端 Claude API（Anthropic API key）              │
│      └─ 需提前配置 ANTHROPIC_API_KEY（生产环境可选）               │
│      └─ 记录 selectionReason = "FALLBACK_CLOUD_FALLBACK"         │
│      └─ 记录到 AuditLog：cloud_fallback=true                     │
│                                                                 │
│  第 4 级：返回错误信息，任务排队重试                               │
│      └─ SSE 推送: {"type":"error","error":{"code":"NO_MODEL"}}  │
│      └─ 任务进入 PENDING 状态，5 分钟后重试                       │
│      └─ 最多重试 3 次                                            │
└─────────────────────────────────────────────────────────────────┘
```

#### 7.3.2 多节点部署调度

| 部署规模 | 架构方案 | 组件 |
|---------|---------|------|
| 单节点（开发/小团队） | Spring Boot + Claude CLI 同机部署 | `ClaudeCliTool` 直接管理进程 |
| 双节点（中团队，高可用） | Spring Boot + Claude CLI 同机，Nginx 负载均衡 | `Nginx` 调度到节点 A/B |
| 多节点（生产） | Spring Boot 无状态 + Claude CLI 有状态节点 | `RabbitMQ` 任务队列 + `ClaudeCliNode` 进程管理 |
| 云原生（K8s） | Spring Boot 部署为 Deployment + Claude CLI 部署为 StatefulSet | `HorizontalPodAutoscaler` 按 CPU/内存自动扩缩 |

**多节点调度核心逻辑**：

```java
/**
 * 分布式任务调度器
 */
public interface INodeScheduler {
    /**
     * 选择目标节点处理 Claude CLI 任务
     */
    NodeInfo selectNode(ClaudeCliRequest request);
    
    /**
     * 提交任务到指定节点
     */
    String submitTask(NodeInfo node, ClaudeCliRequest request);
    
    /**
     * 心跳检测，定期更新节点状态
     */
    void heartbeat(String nodeId);
}

@Data
public class NodeInfo {
    private String nodeId;
    private String host;
    private int availableSlots;  // 剩余并发 slot
    private Instant lastHeartbeat;
    private NodeStatus status;   // ONLINE / DRAINING / OFFLINE
}
```

#### 7.3.3 日志与存储管理

| 数据类型 | 存储位置 | 保留策略 | 磁盘预估（100 员工） |
|---------|---------|---------|-------------------|
| `autonomy_trace_event` | PostgreSQL `trace_event` 表 | 90 天，后归档到 S3 | ~5 GB |
| `claude_cli_job` | PostgreSQL `claude_cli_job` 表 | 180 天 | ~500 MB |
| `artifact_record` | PostgreSQL `artifact_record` 表 | 180 天 + 产物文件（Git） | ~2 GB |
| `claude_cli_audit_log` | Elasticsearch | 30 天 | ~1 GB |
| Claude CLI 原始 stream-json 输出 | S3 / 本地磁盘 | 7 天（可配置） | ~2 GB |

**日志轮转配置**：

```yaml
# application.yml
logging:
  logback:
    rolling-policy:
      max-file-size: 100MB
      max-history: 30
      total-size-cap: 3GB

# trace 数据 TTL
living-agent:
  trace-ttl:
    autonomy-trace-days: 90
    claude-cli-job-days: 180
    audit-log-days: 30
    artifact-data-days: 180
```

---

### 7.4 关键注意事项

#### 7.4.1 Claude CLI 版本兼容性

| Claude CLI 版本 | free-claude-code-main 版本 | 兼容性 | 备注 |
|----------------|--------------------------|--------|------|
| ≥ 0.2.0 | ≥ 1.0.0 | ✅ 完全兼容 | 推荐版本 |
| 0.1.x | ≥ 1.0.0 | ⚠️ 部分兼容 | stream-json 格式略有差异 |
| ≥ 0.2.0 | < 1.0.0 | ❌ 不兼容 | 消息格式不匹配 |

**版本锁定**：建议在 `docker/docker-compose.dev.yml` 中锁定版本：

```yaml
services:
  claude-cli:
    image: anthropics/claude-code:${CLAUDE_CLI_VERSION:-0.2.13}
  claude-proxy:
    build:
      context: ./free-claude-code-main
      dockerfile: Dockerfile
    environment:
      - CLAUDE_PROXY_VERSION=1.0.0
```

#### 7.4.2 Java 版本兼容性

| Java 版本 | 支持度 | 备注 |
|----------|--------|------|
| Java 21+ | ✅ 支持 | Spring Boot 3.x 推荐版本 |
| Java 25 (Early Access) | ⚠️ 开发环境可用 | 生产环境建议使用 GA 版本 |
| OpenJDK | ✅ 支持 | 推荐 Eclipse Temurin / Amazon Corretto |

**注意**：如果项目使用 Java 25 EA 版本，需注意：
- 某些库可能未测试 Java 25，需提前验证兼容性
- 生产环境部署前确认 Java 25 GA 发布状态

#### 7.4.3 Docker 网络配置

```yaml
# docker-compose.dev.yml 网络配置示例
networks:
  claude-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.28.0.0/16

services:
  spring-boot:
    networks:
      - claude-network
    extra_hosts:
      - "host.docker.internal:host-gateway"  # 访问宿主机服务
  
  claude-proxy:
    networks:
      - claude-network
    ports:
      - "8082:8082"
```

---

## 8. 推荐下一步

### 8.1 实施路线图与决策点

```
Week 1         Week 2-3         Week 4-7          Week 8-9         Week 10-12
┌──────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Phase 0   │ │ Phase 1      │ │ Phase 2       │ │ Phase 3       │ │ Phase 4       │
│ 基础设施   │ │ 短期方案     │ │ Java 代理核心  │ │ 事件接入      │ │ 深度集成     │
│          │ │              │ │              │ │              │ │              │
│ ✓ Claude CLI 安装  │ │ ✓ Proxy env   │ │ ✓ Converter  │ │ ✓ StreamJson │ │ ✓ ModelRouter │
│ ✓ 环境确认        │ │   注入         │ │   + SSE      │ │   Mapper     │ │ ✓ Metrics    │
│ ✓ Docker 搭建      │ │ ✓ Sandbox 隔离  │ │   Builder    │ │ ✓ Artifact   │ │ ✓ Trace      │
│ ✓ 配置开发        │ │ ✓ 端到端可运行  │ │   + Proxy    │ │   Extractor  │ │ ✓ ProcessPool│
│              │ │              │ │   Controller │ │ ✓ Event SSE  │ │              │
│              │ │              │ │   + Service  │ │   Pusher     │ │              │
│              │ │              │ │   + ModelRouter│ │              │ │              │
└──────────┘ └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘
   │                │                   │                   │                   │
   ▼                ▼                   ▼                   ▼                   ▼
Phase 0 门禁     Phase 1 门禁        Phase 2 门禁        Phase 3 门禁        Phase 4 门禁
   │                │                   │                   │                   │
   └───────────────┴───────────────────┴───────────────────┴───────────────────┘
                                              │
                                              ▼
                                          生产部署评估
```

**关键决策点（Gates）**：

| Gate | 触发时机 | 决策内容 | 通过标准 |
|------|---------|---------|---------|
| G1 | Phase 1 完成后 | 是否加速 Phase 2？ | Phase 1 端到端成功 + Proxy 可用 |
| G2 | Phase 2 完成后 | 模型池集成是否就绪？ | 50+ 测试通过 + 3 种员工模型路由验证 |
| G3 | Phase 3 完成后 | 事件接入是否可靠？ | SSE 延迟 < 200ms + 审计日志完整 |
| G4 | Phase 4 完成后 | 是否具备生产部署条件？ | 压测 10 并发 1 小时稳定 + 安全审计通过 |

---

### 8.2 短期行动项（Next 2 周）

#### 8.2.1 立即启动：Phase 0 + Phase 1

| 任务 | 负责人 | 预估工时 | 产出 | 截止日期 |
|------|--------|---------|------|---------|
| 安装 Claude CLI v0.2.13 + free-claude-code-main | 后端开发 | 2h | 可运行环境 | Day 1 |
| 配置 `application-local.yml` 的 `claude-cli` 属性 | 后端开发 | 1h | 配置文件 | Day 1 |
| 实现 `ClaudeCliProperties.java` | 后端开发 | 2h | 配置类 + 单元测试 | Day 2 |
| 部署 `free-claude-code-main` 到 localhost:8082 | 后端开发 | 4h | Docker 容器运行 | Day 2 |
| 改造 `ClaudeExecutionGateway` 注入代理环境变量 | 后端开发 | 4h | 环境变量注入代码 | Day 3 |
| 改造 `SandboxService` 支持 Claude sandbox | 后端开发 | 4h | 隔离工作区 | Day 3 |
| 实现 `StreamJsonParser.java` 基础版 | 后端开发 | 6h | JSON 逐行解析器 | Day 4-5 |
| 端到端验证：Claude CLI → Proxy → 模型 | 后端开发 | 4h | Demo 视频/截图 | Day 5 |

#### 8.2.2 代码审查清单

对 `free-claude-code-main` 需要审查的关键文件：

| 文件 | 审查重点 | 负责人 |
|------|---------|--------|
| `message_converter.py` | Anthropic→OpenAI 字段映射、工具调用格式、system prompt 处理 | 架构师 |
| `sse_builder.py` | SSE 格式对齐、事件类型映射、边界情况 | 架构师 |
| `proxy_server.py` | 端口绑定、并发模型、健康检查端点 | 后端开发 |
| `sandbox_config.py` | 工作区隔离、权限控制、白名单机制 | 安全工程师 |

---

### 8.3 中期行动项（Week 4-7）

#### 8.3.1 `AnthropicOpenAiConverter` 测试用例矩阵

| 测试场景 | 输入类型 | 预期输出 | 优先级 |
|---------|---------|---------|--------|
| 简单文本请求 | `{"role":"user","content":"hello"}` | 等价 OpenAI 格式 | P0 |
| 多轮对话 | 3 轮 messages | 正确拼接 history | P0 |
| 系统消息 | 含 `system` role | Anthropic system → OpenAI system | P0 |
| 单工具调用 | 含 `tools` + `tool_use` | 正确转换 `input_schema` | P0 |
| 嵌套工具调用 | 2 个工具同时调用 | 顺序处理，不丢失顺序 | P1 |
| 空内容块 | `content: []` | 返回默认空字符串 | P1 |
| 超长内容 | > 32K tokens | 截断或分段处理 | P1 |
| 特殊字符 | Unicode、emoji、HTML | 正确编码，不转义丢失 | P2 |
| 流式 vs 非流式 | `stream: true/false` | 分别处理 SSE / JSON | P0 |
| 错误状态码 | 4xx/5xx | 转换为 Anthropic 错误格式 | P1 |

#### 8.3.2 AnthropicSseBuilder 测试用例

| 测试场景 | 输入 | 输出 SSE 格式验证 |
|---------|------|-----------------|
| message_start | 新消息开始 | `data: {"type":"message_start","message":{...}}` |
| content_block_start | 文本块开始 | `data: {"type":"content_block_start","index":0,...}` |
| content_block_delta | 文本增量 | `data: {"type":"content_block_delta","delta":{"text":"..."}}` |
| content_block_stop | 块结束 | `data: {"type":"content_block_stop","index":0}` |
| message_delta | 消息结束 | `data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},...}` |
| message_stop | 消息完全结束 | `data: {"type":"message_stop"}` |
| error | 错误事件 | `data: {"type":"error","error":{"message":"...",...}}` |
| ping | 心跳 | `data: {"type":"ping"}` |

---

### 8.4 资源需求与团队分工

| 角色 | 人数 | 负责阶段 | 职责 |
|------|------|---------|------|
| 后端开发（Java） | 2 | Phase 1-4 | Claude CLI 接入、代理核心、事件处理 |
| 安全工程师 | 1 | Phase 1, 4 | 沙箱隔离、权限控制、安全审计 |
| 测试工程师 | 1 | Phase 1, 2 | 测试用例编写、自动化对比测试 |
| 架构师 | 1 | Phase 2, 4 | 模型路由、架构评审、技术决策 |
| DevOps | 1 | Phase 0, 4 | Docker 环境、CI/CD、监控告警 |

---

### 8.5 成功指标与验收标准

| 指标 | 目标值 | 测量方式 | 测量频率 |
|------|--------|---------|---------|
| Claude CLI 端到端成功率 | ≥ 95% | `ClaudeCliJob.status = COMPLETED` 比例 | 每日 |
| 模型路由准确率 | 100%（规则匹配） | `AuditLog.selection_reason` 验证 | 每周 |
| SSE 事件推送延迟（p95） | < 200ms | Prometheus `claude_sse_push_latency_seconds` | 每日 |
| 工具调用成功率 | ≥ 90% | `TRACE_TOOL_COMPLETE` / `TRACE_TOOL_START` | 每日 |
| 平均 Claude CLI 会话时长 | < 5 分钟 | `COMPLETED_AT - STARTED_AT` | 每日 |
| 模型故障自动切换成功率 | 100% | `claude_model_failover_count` | 实时 |
| 安全事件数 | 0 | AuditLog `security_violation` 计数 | 每日 |
| 磁盘使用增长 | < 10GB/月 | Prometheus node_disk_used_bytes | 每周 |

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
