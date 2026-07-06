# 闭环33：数字员工使用 Claude CLI 工具优化建议报告

> **生成日期**: 2026-07-02
> **分析对象**: Living Agent Service 闭环33 (数字员工使用 Claude CLI 工具流程)
> **参考文档**: Claude Code 流程闭环文档 (FLOW_CLOSED_LOOP_WITH_SUBFLOWS.md)
> **重要说明**: 本报告聚焦于**数字员工如何用好 Claude CLI 工具完成工作**（闭环33业务流程），而非单纯的技术调用流程优化（闭环22技术组件）
> **目的**: 为不同类型的数字员工提供 Claude CLI 工具使用最佳实践，优化工具调用策略，提升工作效率和任务完成质量

---

## 零、闭环33与闭环22的关系说明

### 0.1 闭环定位区分

**闭环22（Claude CLI代理闭环）** - **技术视角**：
- 定位：技术组件，提供 Claude CLI 的底层调用能力
- 关注点：CLI可用性检查、执行验证、输出解析、错误处理
- 代码位置：`ClaudeCliTool.java` + `ClaudeExecutionGateway.java`
- 独立性：可被任何需要调用 Claude CLI 的场景使用

**闭环33（数字员工使用 Claude CLI 工具闭环）** - **业务视角**：
- 定位：业务流程，描述数字员工如何使用 Claude CLI 工具完成任务
- 关注点：员工权限验证、工具选择决策、参数定制、结果质量评估、回执提交
- 代码位置：各业务大脑 + 员工执行服务 + 回执服务
- 依赖性：依赖业务上下文，是闭环11-A（员工任务处理闭环）的子流程

### 0.2 调用关系架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Living Agent Service (Java应用)                             │
│                                                                                │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    闭环22：Claude CLI代理闭环（技术组件）                 │  │
│  │                                                                        │  │
│  │  ClaudeCliTool.java ──> ClaudeExecutionGateway.java                   │  │
│  │         │                      │                                       │  │
│  │         │                      │                                       │  │
│  │         ▼                      ▼                                       │  │
│  │  构建调用参数          创建 SandboxSession                              │  │
│  │         │                      │                                       │  │
│  │         │                      │                                       │  │
│  │         └──────────────────────┼───────────────────────────────────┐  │  │
│  │                                │                                   │  │  │
│  │                                ▼                                   │  │  │
│  │                    executeCommand("claude ...")                   │  │  │
│  │                                │                                   │  │  │
│  └────────────────────────────────┼───────────────────────────────────┘  │
│                                   │                                        │
│                                   │ 外部进程调用                            │
│                                   │                                        │
└───────────────────────────────────┼────────────────────────────────────────┘
                                    │
                                    │ subprocess
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│              Claude Code CLI (外部工具, Node.js应用)                           │
│                @anthropic-ai/claude-code@2.1.140                              │
│                                                                                │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    Claude Code 内部流程                                  │  │
│  │                                                                        │  │
│  │  cli.tsx ──> main.tsx ──> launchRepl ──> 工具执行 ──> 结果返回          │  │
│  │                                                                        │  │
│  │  输出格式: stream-json (JSON事件流)                                     │  │
│  │                                                                        │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                                │
│  返回: JSON事件流 (通过stdout)                                                 │
│                                                                                │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ JSON事件流
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    闭环33：数字员工使用 Claude CLI 工具（业务流程）            │
│                                                                                │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    数字员工业务流程                                       │  │
│  │                                                                        │  │
│  │  员工任务 → 工具选择 → 参数定制 → 结果解析 → 任务完成 → 回执提交        │  │
│  │                                                                        │  │
│  │  ├─ 员工权限验证（查询职责卡）                                          │  │
│  │  ├─ 工具选择决策（是否适合使用 Claude CLI）                             │  │
│  │  ├─ 参数定制（不同员工类型不同参数）                                    │  │
│  │  ├─ 执行状态监控（心跳、超时）                                          │  │
│  │  ├─ 结果质量评估（任务完成度）                                          │  │
│  │  ├─ 回执提交（绩效记录）                                                │  │
│  │                                                                        │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                                │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 0.3 调用关系说明

| 角色 | 组件 | 闭环归属 | 说明 |
|------|------|----------|------|
| **技术组件** | ClaudeCliTool + ClaudeExecutionGateway | **闭环22** | 提供 Claude CLI 的底层代理调用能力 |
| **业务流程** | 各业务大脑 + 员工执行服务 | **闭环33** | 数字员工使用 Claude CLI 工具完成任务的业务流程 |
| **调用方式** | 业务大脑调用 ClaudeCliTool | 闭环33调用闭环22 | 业务流程使用技术组件的能力 |
| **输出格式** | stream-json | 闭环22处理 | 技术组件负责解析输出格式 |
| **结果评估** | 任务完成度、质量评估 | **闭环33处理** | 业务流程负责评估结果质量 |

### 0.4 优化建议焦点区分

**闭环22优化焦点（技术视角）**：
1. **如何更好地调用 Claude Code CLI**
   - 参数传递优化
   - 调用方式优化
   - CLI可用性检查

2. **如何更好地解析 Claude Code 输出**
   - stream-json 格式解析
   - 事件类型识别
   - 错误信息提取

3. **如何更好地处理 Claude Code 错误**
   - 错误分类
   - 错误恢复
   - 重试机制

**闭环33优化焦点（业务视角）**：
1. **如何更好地选择 Claude CLI 工具**
   - 任务适用性判断
   - 员工权限验证
   - 工具能力边界确认

2. **如何更好地定制调用参数**
   - 不同员工类型的参数定制
   - 任务上下文参数注入
   - 超时和权限控制

3. **如何更好地评估执行结果**
   - 任务完成度检查
   - 结果质量评估
   - 回执提交和绩效记录

**注意**: 本报告聚焦于**闭环33（业务流程）**的优化建议，闭环22（技术组件）的优化建议参见技术文档。

---

## 一、可借鉴流程闭环清单

### 1.1 Claude Code 流程与 Living Agent Service 闭环33对应关系

| Claude Code 流程名称 | 可借鉴的调用方式 | Living Agent Service 对应流程 | 借鉴价值评估 |
|---------------------|-----------------|------------------------------|------------|
| **CLI启动流程** | 快速路径参数（--version/--bridge等）、性能剖析参数、初始化参数传递 | ClaudeExecutionGateway.buildClaudeArgs() | **高** - 优化参数传递，增强调用效率 |
| **工具执行流程** | 工具调用参数格式、权限参数传递、结果格式约定 | ClaudeCliTool.execute() | **高** - 标准化调用参数，增强结果解析 |
| **工具结果处理** | stream-json输出格式、事件类型定义、元数据字段约定 | ExecutionResult.metrics() | **中** - 改进结果解析，增强追踪能力 |
| **工具错误处理** | 错误输出格式、错误类型定义、恢复策略约定 | ClaudeCliTool异常处理 | **高** - 标准化错误处理，提升鲁棒性 |
| **MCP服务流程** | MCP服务器配置参数、认证参数传递、工具列表参数 | ClaudeExecutionGateway.sessions管理 | **中** - 优化配置传递，增强连接可靠性 |
| **Bridge远程控制** | Bridge模式参数（--bridge）、会话恢复参数、心跳参数约定 | ClaudeAsyncJob管理 | **高** - 补充心跳机制，增强异步任务监控 |
| **状态管理流程** | 状态输出格式、状态字段约定、状态持久化约定 | ClaudeSessionState | **中** - 标准化状态管理，增强会话追踪 |

---

### 1.2 高价值借鉴项详细分析

#### 1.2.1 CLI快速路径检查（借鉴价值：高）

**Claude Code 实现**：
```typescript
// Claude Code 快速路径检查
async function bootstrap(args: string[]) {
  // 1. 版本检查 (--version) ← 闭环:快速退出
  if (args.includes('--version')) {
    console.log(VERSION);
    process.exit(0);
    return;
  }
  
  // 2. 系统提示转储 (--dump-system-prompt) ← 闭环:快速退出
  if (args.includes('--dump-system-prompt')) {
    process.exit(0);
    return;
  }
  
  // 3. Chrome MCP模式 ← 闭环:进入Chrome MCP
  if (args.includes('--claude-in-chrome-mcp')) {
    await import('./chromeMcp.js');
    return;
  }
  
  // 4. Bridge远程控制模式 ← 闭环:进入Bridge主循环
  if (bridgeMode) {
    await import('./bridge/bridgeMain.js');
    return;
  }
  
  // 5. 无快速路径 → 动态加载完整CLI ← 闭环:进入主CLI循环
  const mainModule = await import('./main.js');
  await mainModule.main();
}
```

**闭环验证表格**：
| 阶段 | 入口 | 出口 | 闭环检查 |
|------|------|------|----------|
| 版本检查 | `cli.tsx --version` | `process.exit(0)` | ✅ 快速退出 |
| 系统提示转储 | `cli.tsx --dump-system-prompt` | `process.exit(0)` | ✅ 快速退出 |
| Chrome MCP模式 | `cli.tsx --claude-in-chrome-mcp` | `chromeMcp.js` | ✅ 进入Chrome MCP |
| Bridge模式 | `cli.tsx --bridge` | `bridgeMain.ts` | ✅ 进入Bridge循环 |
| 完整CLI | `cli.tsx` | `main.tsx → launchRepl` | ✅ 进入REPL循环 |

**Living Agent Service 当前实现缺失**：
- ❌ 无快速路径检查（直接进入完整初始化）
- ❌ 无CLI可用性预检查（--version检查）
- ❌ 无性能剖析检查点
- ❌ 无快速失败机制

---

#### 1.2.2 工具执行流程闭环（借鉴价值：高）

**Claude Code 实现**：
```typescript
// Claude Code 工具执行流程
async function executeToolCall(toolName: string, input: unknown) {
  // 1. 从工具池查找工具
  const tool = toolPool.find(t => t.name === toolName)
  
  if (!tool) {
    return {
      type: 'error',
      error: `Tool ${toolName} not found`
    } // ← 闭环:错误处理
  }
  
  // 2. 检查工具是否启用
  if (tool.isEnabled && !tool.isEnabled(context)) {
    return {
      type: 'error',
      error: `Tool ${toolName} is disabled`
    } // ← 闭环:禁用处理
  }
  
  // 3. 准备执行上下文
  const toolContext: ToolUseContext = {
    getAppState: () => appState,
    toolPermissionContext: permissionContext,
    abortSignal: abortController.signal
  }
  
  // 4. 执行工具
  try {
    const result = await tool.invoke(input, toolContext)
    
    // 5. 处理结果
    if (result.type === 'error') {
      logError(result.error)
    }
    
    return result // ← 闭环:返回结果
  } catch (error) {
    return {
      type: 'error',
      error: errorMessage(error)
    } // ← 闭环:异常处理
  }
}
```

**闭环验证表格**：
| 阶段 | 入口 | 出口 | 闭环检查 |
|------|------|------|----------|
| 工具池组装 | `assembleToolPool()` | Tools 数组 | ✅ 包含所有可用工具 |
| 工具查找 | `toolPool.find()` | Tool 或 undefined | ✅ 数组查找返回工具定义 |
| 权限检查 | `filterToolsByDenyRules()` | Tools 数组 | ✅ 明确权限状态 |
| 工具执行 | `tool.invoke()` | ToolResult | ✅ 返回统一结果 |
| 错误处理 | catch块 | error结果 | ✅ 优雅降级 |

**Living Agent Service 当前实现**：
- ✅ 有工具查找（ClaudeExecutionGateway.getOrCreateSession）
- ✅ 有参数验证（ClaudeCliTool.validate）
- ⚠️ 部分有权限检查（ClaudeCliTool.isAllowed）
- ❌ 无工具启用状态检查（isEnabled机制）
- ⚠️ 部分有错误处理（catch块，但缺少分类）

---

#### 1.2.3 工具错误处理闭环（借鉴价值：高）

**Claude Code 实现**：
```typescript
// Claude Code 错误分类与恢复策略
async function handleToolError(
  error: Error,
  toolName: string,
  context: ToolUseContext
): Promise<ErrorHandlingResult> {
  // 1. 错误分类 ← 闭环:类型识别
  const errorType = classifyError(error);
  
  // 2. 获取处理策略 ← 闭环:策略映射
  const strategy = getErrorHandlingStrategy(errorType, toolName);
  
  switch (errorType) {
    case 'permission_error':
      // 权限错误处理 ← 闭环:权限建议
      return {
        type: 'permission_error',
        message: `Permission denied for tool ${toolName}`,
        recoverable: true,
        suggestion: 'prompt_permission'
      };
      
    case 'timeout_error':
      // 超时错误重试 ← 闭环:重试逻辑
      if (strategy.retryable && strategy.maxRetries > 0) {
        return {
          type: 'retry_suggestion',
          message: `Timeout error, retrying...`,
          recoverable: true,
          retryAction: { toolName, timeout: newTimeout }
        };
      }
      break;
      
    case 'network_error':
      // 网络错误恢复 ← 闭环:网络恢复
      return {
        type: 'network_error',
        message: 'Network connectivity issue',
        recoverable: true,
        suggestion: 'retry_with_network'
      };
      
    case 'system_error':
      // 系统错误处理 ← 闭环:系统日志
      logSystemError(error, toolName, context);
      return {
        type: 'system_error',
        message: `System error in tool ${toolName}`,
        recoverable: false
      };
  }
}

function classifyError(error: Error): ErrorType {
  if (error instanceof PermissionError) return 'permission_error';
  if (error instanceof TimeoutError) return 'timeout_error';
  if (error instanceof NetworkError) return 'network_error';
  if (error instanceof ValidationError) return 'validation_error';
  return 'unknown_error';
}
```

**闭环验证表格**：
| 阶段 | 入口 | 出口 | 检查点 |
|------|------|------|--------|
| 错误捕获 | `catch(error)` | 错误对象 | ✅ 错误捕获 |
| 错误分类 | `classifyError()` | 错误类型 | ✅ 类型识别 |
| 策略获取 | `getErrorHandlingStrategy()` | 处理策略 | ✅ 策略映射 |
| 权限错误处理 | `prompt_permission` | 权限提示 | ✅ 权限建议 |
| 超时错误处理 | `retry_with_timeout` | 重试建议 | ✅ 重试机制 |
| 网络错误处理 | `retry_with_network` | 恢复建议 | ✅ 网络恢复 |

**Living Agent Service 当前实现**：
- ✅ 有错误捕获（ClaudeCliTool.execute catch块）
- ❌ 无错误分类机制（统一作为Exception处理）
- ❌ 无恢复策略决策
- ❌ 无重试机制
- ❌ 无超时调整逻辑

---

#### 1.2.4 Bridge心跳检测机制（借鉴价值：高）

**Claude Code 实现**：
```typescript
// Claude Code Bridge 心跳检测
async function heartbeatActiveWorkItems(
  api: BridgeAPI,
  sessionWorkIds: Map<string, string>
): Promise<void> {
  // 并行发送心跳 ← 闭环:批量心跳
  await Promise.all(
    Array.from(sessionWorkIds.entries()).map(async ([workId, sessionId]) => {
      try {
        await api.heartbeat(workId);
      } catch (error) {
        // 心跳失败，移除会话 ← 闭环:失效检测
        sessionWorkIds.delete(workId);
      }
    })
  );
}

// Bridge主循环
export async function runBridgeLoop(
  config: BridgeConfig,
  signal: AbortSignal
): Promise<void> {
  // 启动心跳定时器 ← 闭环:持续心跳
  const heartbeatTimer = setInterval(
    () => heartbeatActiveWorkItems(api, sessionWorkIds),
    config.heartbeatInterval
  );
  
  // Poll循环 ← 闭环:持续轮询
  while (!signal.aborted) {
    try {
      const workItems = await api.poll(environmentId, environmentSecret);
      
      for (const workItem of workItems) {
        await handleWorkItem(workItem, activeSessions, sessionWorkIds);
      }
      
      await sleep(config.pollInterval);
    } catch (error) {
      logger.error('Poll loop error:', error);
      await sleep(config.pollInterval * 2); // 错误时延长等待
    }
  }
  
  // 清理资源 ← 闭环:优雅退出
  clearInterval(heartbeatTimer);
  await cleanupSessions(activeSessions);
}
```

**闭环验证表格**：
| 阶段 | 入口 | 出口 | 闭环检查 |
|------|------|------|----------|
| Bridge启动 | `bridgeMain()` | Poll循环 | ✅ 进入循环 |
| Poll请求 | `api.poll()` | 工作项列表 | ✅ 获取工作项 |
| 心跳检测 | `heartbeat()` | 心跳确认 | ✅ 活跃状态 |
| 心跳失败处理 | 删除sessionWorkIds | 移除失效会话 | ✅ 失效检测 |
| 错误处理 | catch块 | 继续循环 | ✅ 优雅降级 |
| 资源清理 | `cleanupSessions()` | 清理完成 | ✅ 退出清理 |

**Living Agent Service 当前实现**：
- ✅ 有异步任务管理（ClaudeAsyncJob）
- ❌ 无心跳检测机制
- ❌ 无Poll循环监控
- ❌ 无失效会话自动清理
- ⚠️ 部分有资源清理（closeSession/closeAllSessions）

---

#### 1.2.5 MCP工具动态加载（借鉴价值：中）

**Claude Code 实现**：
```typescript
// Claude Code MCP工具动态包装器
export const fetchToolsForClient = memoizeWithLRU(
  async (client: MCPServerConnection): Promise<Tool[]> => {
    // 从MCP服务器获取工具列表
    const toolsToProcess = await client.listTools();
    
    // 动态创建MCPTool包装器 ← 闭环：动态创建
    return toolsToProcess.map((tool): Tool => {
      const fullyQualifiedName = buildMcpToolName(client.name, tool.name);
      return {
        ...MCPTool,  // 使用 MCPTool 作为模板
        name: skipPrefix ? tool.name : fullyQualifiedName,
        mcpInfo: { serverName: client.name, toolName: tool.name },
        isMcp: true,
        description: tool.description,
        inputSchema: tool.inputSchema
      };
    });
  }
);
```

**闭环验证表格**：
| 阶段 | 入口 | 出口 | 闭环检查 |
|------|------|------|----------|
| 配置解析 | `getAllMcpConfigs()` | 配置Map | ✅ 完整配置 |
| 传输创建 | `createTransport()` | Transport实例 | ✅ 成功创建 |
| 客户端连接 | `client.connect()` | 连接成功 | ✅ 建立连接 |
| 工具列表 | `fetchToolsForClient()` | 工具列表 | ✅ 完整列表 |
| 工具调用 | `callMcpTool()` | CallToolResult | ✅ 返回结果 |
| 错误处理 | `McpAuthError/McpToolCallError` | 错误对象 | ✅ 明确错误 |
| 缓存管理 | `clearServerCache()` | 缓存清除 | ✅ 资源释放 |

**Living Agent Service 当前实现**：
- ✅ 有会话管理（ClaudeExecutionGateway.sessions）
- ⚠️ 部分有连接状态管理（ClaudeSessionState）
- ❌ 无工具动态加载机制
- ❌ 无LRU缓存机制
- ❌ 无认证处理闭环

---

## 二、Claude CLI 优化建议

### 2.1 当前实现分析

**Living Agent Service 闭环22 当前状态**：

| 环节 | 当前实现 | 闭环完整性 | 主要缺口 |
|------|---------|----------|---------|
| **CLI可用性检查** | ❌ 无预检查 | 不完整 | 缺少--version快速检查、CLI安装验证 |
| **会话创建** | ✅ getOrCreateSession | 完整 | - |
| **参数构建** | ✅ buildClaudeArgs | 完整 | - |
| **环境配置** | ✅ buildEnvironment | 完整 | - |
| **命令执行** | ✅ executeCommand | 完整 | - |
| **结果解析** | ⚠️ extractJsonEventLines | 部分完整 | 缺少格式验证、类型识别 |
| **错误处理** | ⚠️ catch块 | 部分完整 | 缺少错误分类、恢复策略 |
| **会话管理** | ⚠️ ClaudeSessionState | 部分完整 | 缺少状态机转换、持久化 |
| **异步任务** | ✅ ClaudeAsyncJob | 完整 | 缺少心跳监控、Poll机制 |
| **资源清理** | ✅ closeSession | 完整 | - |

---

### 2.2 优化方案详细描述

#### 优化方案1：增强CLI可用性检查（借鉴快速路径）

**优化目标**：借鉴Claude Code的快速路径检查，在执行前验证CLI可用性，避免无效启动。

**当前问题**：
```java
// 当前实现：直接创建会话并执行，无预检查
public CompletableFuture<ExecutionResult> execute(String sessionId, Map<String, Object> params) {
    SandboxSession session = getOrCreateSession(sessionId);
    if (session == null) {
        return CompletableFuture.completedFuture(
            ExecutionResult.error("claude-no-session", "Failed to create sandbox session")
        );
    }
    // 直接执行，未检查CLI是否可用
    String action = stringValue(params.get("action"), "prompt").toLowerCase();
    List<String> args = buildClaudeArgs(action, params);
    return session.executeCommand(claudeCliProperties.getCommand(), args, env);
}
```

**优化方案**：
```java
// 新增：CLI可用性检查闭环
public class ClaudeCliHealthChecker {
    private static final Logger log = LoggerFactory.getLogger(ClaudeCliHealthChecker.class);
    
    private final ClaudeCliProperties properties;
    private volatile boolean lastCheckResult = false;
    private volatile Instant lastCheckTime = null;
    private static final Duration CHECK_INTERVAL = Duration.ofMinutes(5);
    
    /**
     * 快速路径检查：验证CLI是否可用
     * ← 闭环：快速失败机制
     */
    public CompletableFuture<Boolean> checkCliAvailable() {
        // 缓存检查结果，避免频繁检查
        if (lastCheckTime != null 
            && Instant.now().isBefore(lastCheckTime.plus(CHECK_INTERVAL))) {
            return CompletableFuture.completedFuture(lastCheckResult);
        }
        
        return performQuickCheck()
            .thenApply(result -> {
                lastCheckResult = result;
                lastCheckTime = Instant.now();
                log.info("Claude CLI availability check: {}", result ? "available" : "unavailable");
                return result;
            });
    }
    
    /**
     * 执行快速检查：--version参数
     * ← 闭环：版本验证
     */
    private CompletableFuture<Boolean> performQuickCheck() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                properties.getCommand(), "--version"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return CompletableFuture.completedFuture(false);
            }
            
            int exitCode = process.exitValue();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            // ← 闭环：输出验证
            boolean isValid = exitCode == 0 && output.contains("claude");
            return CompletableFuture.completedFuture(isValid);
            
        } catch (Exception e) {
            log.warn("Claude CLI quick check failed", e);
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * 检查CLI安装状态
     * ← 闭环：安装验证
     */
    public boolean isCliInstalled() {
        String command = properties.getCommand();
        if (command == null || command.isBlank()) {
            return false;
        }
        
        // Windows: where命令
        // Linux/Mac: which命令
        String checkCommand = System.getProperty("os.name").toLowerCase().contains("windows")
            ? "where"
            : "which";
        
        try {
            ProcessBuilder pb = new ProcessBuilder(checkCommand, command);
            Process process = pb.start();
            boolean completed = process.waitFor(2, TimeUnit.SECONDS);
            return completed && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}

// ClaudeExecutionGateway 改造
public CompletableFuture<ExecutionResult> execute(String sessionId, Map<String, Object> params) {
    // 1. 快速路径检查 ← 闭环：快速失败
    if ("status".equals(stringValue(params.get("action"), "prompt").toLowerCase())) {
        return healthChecker.checkCliAvailable()
            .thenApply(available -> available 
                ? ExecutionResult.success("Claude CLI is available")
                : ExecutionResult.error("claude-unavailable", "Claude CLI is not installed or unavailable"));
    }
    
    // 2. CLI可用性预检查 ← 闭环：执行前验证
    return healthChecker.checkCliAvailable()
        .thenCompose(available -> {
            if (!available) {
                return CompletableFuture.completedFuture(
                    ExecutionResult.error("claude-unavailable", "Claude CLI is not available")
                );
            }
            
            // 3. 创建会话并执行
            SandboxSession session = getOrCreateSession(sessionId);
            if (session == null) {
                return CompletableFuture.completedFuture(
                    ExecutionResult.error("claude-no-session", "Failed to create sandbox session")
                );
            }
            
            String action = stringValue(params.get("action"), "prompt").toLowerCase();
            List<String> args = buildClaudeArgs(action, params);
            Map<String, String> env = buildEnvironment(params);
            
            return session.executeCommand(properties.getCommand(), args, env)
                .thenApply(result -> enrichResult(sessionId, action, params, result, System.currentTimeMillis()));
        });
}
```

**闭环验证表格**：
| 阶段 | 入口 | 出口 | 闭环检查 |
|------|------|------|----------|
| CLI安装检查 | `isCliInstalled()` | boolean | ✅ 安装验证 |
| 版本检查 | `--version` | 输出解析 | ✅ 版本验证 |
| 可用性检查 | `checkCliAvailable()` | boolean | ✅ 快速失败 |
| 缓存管理 | `lastCheckResult` | 缓存结果 | ✅ 避免频繁检查 |
| 执行前验证 | `checkCliAvailable().thenCompose` | 执行或失败 | ✅ 执行前闭环 |

**实现步骤**：
1. 新增 `ClaudeCliHealthChecker` 类
2. 在 `ClaudeExecutionGateway` 中注入 `ClaudeCliHealthChecker`
3. 在 `execute()` 方法开头添加快速路径检查
4. 在 `execute()` 方法中添加CLI可用性预检查
5. 添加配置项 `claude.cli.health-check-interval` 控制检查频率
6. 在 `ClaudeCliTool` 的 `validate()` 方法中调用 `healthChecker.isCliInstalled()`

---

#### 优化方案2：改进执行结果验证（借鉴结果处理闭环）

**优化目标**：借鉴Claude Code的工具结果处理流程，增强结果类型识别、格式验证和元数据注入。

**当前问题**：
```java
// 当前实现：简单的JSON行提取，缺少类型识别和验证
private List<String> extractJsonEventLines(String stdout) {
    if (stdout == null || stdout.isBlank()) {
        return List.of();
    }
    List<String> lines = new ArrayList<>();
    for (String line : stdout.split("\\R")) {
        String trimmed = line.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            lines.add(trimmed);
        }
    }
    return lines;
}
```

**优化方案**：
```java
// 新增：结果类型识别与验证
public class ClaudeResultProcessor {
    private static final Logger log = LoggerFactory.getLogger(ClaudeResultProcessor.class);
    
    /**
     * 处理Claude CLI输出结果
     * ← 闭环：完整结果处理
     */
    public ProcessedResult processResult(
        ExecutionResult rawResult,
        String action,
        Map<String, Object> params
    ) {
        // 1. 结果类型识别 ← 闭环：类型识别
        ResultType resultType = detectResultType(rawResult.stdout(), params);
        
        // 2. 根据类型处理 ← 闭环：类型适配
        ProcessedContent content;
        switch (resultType) {
            case STREAM_JSON:
                content = processStreamJsonResult(rawResult.stdout());
                break;
            case TEXT:
                content = processTextResult(rawResult.stdout());
                break;
            case ERROR:
                content = processErrorResult(rawResult.stderr());
                break;
            case EMPTY:
                content = ProcessedContent.empty();
                break;
            default:
                content = ProcessedContent.unknown(rawResult.stdout());
        }
        
        // 3. 添加执行元数据 ← 闭环：元数据注入
        ResultMetadata metadata = buildMetadata(rawResult, action, resultType);
        
        // 4. 记录结果日志 ← 闭环：日志追踪
        logResult(metadata, content);
        
        return new ProcessedResult(content, metadata);
    }
    
    /**
     * 检测结果类型
     * ← 闭环：类型识别
     */
    private ResultType detectResultType(String stdout, Map<String, Object> params) {
        if (stdout == null || stdout.isBlank()) {
            return ResultType.EMPTY;
        }
        
        String format = stringValue(params.get("output_format"), "stream-json");
        
        // stream-json格式检测
        if ("stream-json".equals(format)) {
            List<String> jsonLines = extractJsonEventLines(stdout);
            if (!jsonLines.isEmpty()) {
                // ← 闭环：格式验证
                boolean allValid = jsonLines.stream()
                    .allMatch(line -> isValidJsonEvent(line));
                return allValid ? ResultType.STREAM_JSON : ResultType.TEXT;
            }
        }
        
        // JSON格式检测
        if ("json".equals(format)) {
            try {
                JsonParser.parseObject(stdout);
                return ResultType.JSON;
            } catch (Exception e) {
                return ResultType.TEXT;
            }
        }
        
        return ResultType.TEXT;
    }
    
    /**
     * 处理stream-json结果
     * ← 闭环：流式结果处理
     */
    private ProcessedContent processStreamJsonResult(String stdout) {
        List<String> eventLines = extractJsonEventLines(stdout);
        List<ClaudeEvent> events = new ArrayList<>();
        
        for (String line : eventLines) {
            try {
                ClaudeEvent event = parseClaudeEvent(line);
                if (event != null) {
                    events.add(event);
                }
            } catch (Exception e) {
                log.warn("Failed to parse stream-json event: {}", line, e);
            }
        }
        
        // ← 闭环：事件分类
        Map<String, List<ClaudeEvent>> eventsByType = events.stream()
            .collect(Collectors.groupingBy(ClaudeEvent::getType));
        
        return ProcessedContent.streamJson(events, eventsByType);
    }
    
    /**
     * 解析Claude事件
     * ← 闭环：事件解析
     */
    private ClaudeEvent parseClaudeEvent(String jsonLine) {
        try {
            JsonObject obj = JsonParser.parseObject(jsonLine);
            String type = obj.getString("type");
            
            return new ClaudeEvent(
                type,
                obj.getString("subtype"),
                obj.getMap("data"),
                jsonLine,
                Instant.now()
            );
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 验证JSON事件格式
     * ← 闭环：格式验证
     */
    private boolean isValidJsonEvent(String line) {
        try {
            JsonObject obj = JsonParser.parseObject(line);
            return obj.containsKey("type");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 构建执行元数据
     * ← 闭环：元数据注入
     */
    private ResultMetadata buildMetadata(
        ExecutionResult result,
        String action,
        ResultType resultType
    ) {
        return new ResultMetadata(
            result.executionId(),
            action,
            resultType,
            result.exitCode(),
            result.durationMs(),
            result.success(),
            Instant.now(),
            calculateResultSize(result.stdout())
        );
    }
    
    /**
     * 记录结果日志
     * ← 闭环：日志追踪
     */
    private void logResult(ResultMetadata metadata, ProcessedContent content) {
        log.info("Claude CLI result: executionId={}, action={}, type={}, success={}, duration={}ms, size={}",
            metadata.executionId(),
            metadata.action(),
            metadata.resultType(),
            metadata.success(),
            metadata.durationMs(),
            metadata.size());
        
        if (!metadata.success()) {
            log.warn("Claude CLI execution failed: exitCode={}, error={}",
                metadata.exitCode(),
                content.getError());
        }
    }
    
    // 枚举类型
    public enum ResultType {
        STREAM_JSON, JSON, TEXT, ERROR, EMPTY, UNKNOWN
    }
    
    // 数据类
    public record ClaudeEvent(String type, String subtype, Map<String, Object> data, String raw, Instant timestamp) {}
    public record ProcessedContent(Object content, ResultType type, String error, Map<String, List<ClaudeEvent>> eventsByType) {
        public static ProcessedContent empty() { return new ProcessedContent(null, ResultType.EMPTY, null, Map.of()); }
        public static ProcessedContent unknown(String raw) { return new ProcessedContent(raw, ResultType.UNKNOWN, null, Map.of()); }
        public static ProcessedContent streamJson(List<ClaudeEvent> events, Map<String, List<ClaudeEvent>> eventsByType) {
            return new ProcessedContent(events, ResultType.STREAM_JSON, null, eventsByType);
        }
    }
    public record ResultMetadata(String executionId, String action, ResultType resultType, int exitCode, long durationMs, boolean success, Instant timestamp, long size) {}
    public record ProcessedResult(ProcessedContent content, ResultMetadata metadata) {}
}

// ClaudeExecutionGateway 改造
private ExecutionResult enrichResult(String sessionId,
                                     String action,
                                     Map<String, Object> params,
                                     ExecutionResult result,
                                     long startTime) {
    String sid = normalizeSessionId(sessionId);
    
    // 使用新的结果处理器 ← 闭环：完整处理
    ProcessedResult processed = resultProcessor.processResult(result, action, params);
    
    Map<String, Object> metrics = new HashMap<>();
    metrics.put("provider", "claude-cli");
    metrics.put("action", action);
    metrics.put("result_type", processed.metadata().resultType());
    metrics.put("stream_event_count", processed.content().eventsByType().values().stream()
        .mapToInt(List::size).sum());
    metrics.put("events_by_type", processed.content().eventsByType());
    metrics.put("gateway_duration_ms", System.currentTimeMillis() - startTime);
    
    // 从事件中提取session_id
    List<ClaudeEvent> initEvents = processed.content().eventsByType().getOrDefault("init", List.of());
    String parsedClaudeSessionId = initEvents.stream()
        .filter(e -> e.data() != null && e.data().containsKey("session_id"))
        .map(e -> stringValue(e.data().get("session_id"), null))
        .filter(id -> id != null)
        .findFirst()
        .orElse(null);
    
    if (parsedClaudeSessionId != null) {
        metrics.put("parsed_session_id", parsedClaudeSessionId);
    }
    
    // 更新会话状态
    updateSessionState(sid, action, result, processed);
    
    return new ExecutionResult(
        result.executionId(),
        result.success(),
        result.exitCode(),
        result.stdout(),
        result.stderr(),
        result.durationMs(),
        metrics,
        result.executedAt()
    );
}
```

**闭环验证表格**：
| 阶段 | 入口 | 出口 | 检查点 |
|------|------|------|--------|
| 类型识别 | `detectResultType()` | 结果类型 | ✅ 类型明确 |
| 格式验证 | `isValidJsonEvent()` | boolean | ✅ 格式正确 |
| 事件解析 | `parseClaudeEvent()` | ClaudeEvent | ✅ 解析成功 |
| 事件分类 | `eventsByType` | 分类Map | ✅ 分类完成 |
| 元数据注入 | `buildMetadata()` | 元数据对象 | ✅ 元数据完整 |
| 日志记录 | `logResult()` | 日志记录 | ✅ 追踪完成 |

**实现步骤**：
1. 新增 `ClaudeResultProcessor` 类及其数据类（ResultType, ClaudeEvent, ProcessedContent等）
2. 在 `ClaudeExecutionGateway` 中注入 `ClaudeResultProcessor`
3. 在 `enrichResult()` 方法中使用新的结果处理器
4. 更新 metrics 结构，包含 `result_type` 和 `events_by_type`
5. 在 `ClaudeCliTool` 中使用 `ProcessedResult` 返回更详细的结果

---

#### 优化方案3：添加心跳检测机制（借鉴Bridge流程）

**优化目标**：借鉴Claude Code的Bridge心跳检测机制，增强异步任务监控和会话健康检查。

**当前问题**：
```java
// 当前实现：异步任务启动后无监控
public Map<String, Object> startAsyncJob(String sessionId, Map<String, Object> params) {
    String action = stringValue(params.get("action"), "prompt").toLowerCase();
    String sid = normalizeSessionId(sessionId);
    String jobId = "claude-job-" + UUID.randomUUID().toString().substring(0, 8);
    
    CompletableFuture<ExecutionResult> future = execute(sid, params);
    ClaudeAsyncJob job = new ClaudeAsyncJob(jobId, sid, action, Instant.now(), future);
    asyncJobs.put(jobId, job);
    
    // 启动后无心跳监控
    return Map.of(
        "job_id", jobId,
        "session_id", sid,
        "action", action,
        "state", "running",
        "started_at", job.startedAt
    );
}
```

**优化方案**：
```java
// 新增：心跳检测机制
public class ClaudeHeartbeatMonitor {
    private static final Logger log = LoggerFactory.getLogger(ClaudeHeartbeatMonitor.class);
    
    private final ConcurrentMap<String, ClaudeAsyncJob> asyncJobs;
    private final ConcurrentMap<String, ClaudeSessionState> sessionStates;
    private final ScheduledExecutorService scheduler;
    private final Duration heartbeatInterval;
    private final Duration maxIdleTime;
    
    /**
     * 启动心跳监控
     * ← 闭环：持续监控
     */
    public void startMonitoring() {
        scheduler.scheduleAtFixedRate(
            this::heartbeatCheck,
            heartbeatInterval.toMillis(),
            heartbeatInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );
        
        log.info("Claude heartbeat monitor started: interval={}ms", heartbeatInterval.toMillis());
    }
    
    /**
     * 心跳检查：检查所有活跃会话和任务
     * ← 闭环：批量检查
     */
    private void heartbeatCheck() {
        Instant now = Instant.now();
        
        // 1. 检查异步任务状态 ← 闭环：任务监控
        checkAsyncJobs(now);
        
        // 2. 检查会话健康状态 ← 闭环：会话监控
        checkSessionHealth(now);
        
        // 3. 清理失效资源 ← 闭环：资源清理
        cleanupStaleResources(now);
    }
    
    /**
     * 检查异步任务状态
     * ← 闭环：任务状态检测
     */
    private void checkAsyncJobs(Instant now) {
        asyncJobs.forEach((jobId, job) -> {
            try {
                // 检查任务是否完成
                if (job.future.isDone()) {
                    log.debug("Async job completed: jobId={}, sessionId={}", jobId, job.sessionId);
                    return;
                }
                
                // 检查任务是否超时 ← 闭环：超时检测
                Duration elapsed = Duration.between(job.startedAt, now);
                if (elapsed.compareTo(maxIdleTime) > 0) {
                    log.warn("Async job timeout: jobId={}, sessionId={}, elapsed={}s",
                        jobId, job.sessionId, elapsed.getSeconds());
                    
                    // 标记任务超时 ← 闭环：超时处理
                    job.future.cancel(true);
                    job.state = ClaudeAsyncJob.JobState.TIMEOUT;
                }
                
                // 发送心跳信号 ← 闭环：心跳发送
                job.lastHeartbeat = now;
                
            } catch (Exception e) {
                log.warn("Heartbeat check failed for job: {}", jobId, e);
            }
        });
    }
    
    /**
     * 检查会话健康状态
     * ← 闭环：会话健康检测
     */
    private void checkSessionHealth(Instant now) {
        sessionStates.forEach((sessionId, state) -> {
            try {
                // 检查会话是否超时 ← 闭环：会话超时检测
                Duration idleDuration = Duration.between(state.lastUpdatedAt, now);
                if (idleDuration.compareTo(maxIdleTime) > 0) {
                    log.warn("Session timeout: sessionId={}, idle={}s",
                        sessionId, idleDuration.getSeconds());
                    
                    // 标记会话失效 ← 闭环：失效处理
                    state.state = ClaudeSessionState.SessionState.STALE;
                }
                
            } catch (Exception e) {
                log.warn("Session health check failed: {}", sessionId, e);
            }
        });
    }
    
    /**
     * 清理失效资源
     * ← 闭环：资源清理
     */
    private void cleanupStaleResources(Instant now) {
        // 清理超时的异步任务
        asyncJobs.entrySet().removeIf(entry -> {
            ClaudeAsyncJob job = entry.getValue();
            if (job.state == ClaudeAsyncJob.JobState.TIMEOUT 
                || job.state == ClaudeAsyncJob.JobState.CANCELLED) {
                log.info("Cleaning up stale job: jobId={}", entry.getKey());
                return true;
            }
            return false;
        });
        
        // 清理失效的会话
        sessionStates.entrySet().removeIf(entry -> {
            ClaudeSessionState state = entry.getValue();
            if (state.state == ClaudeSessionState.SessionState.STALE) {
                log.info("Cleaning up stale session: sessionId={}", entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * 停止监控
     * ← 闭环：优雅退出
     */
    public void stopMonitoring() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        log.info("Claude heartbeat monitor stopped");
    }
    
    /**
     * 获取监控状态
     * ← 闭环：状态暴露
     */
    public Map<String, Object> getMonitoringStatus() {
        return Map.of(
            "active_jobs", asyncJobs.size(),
            "active_sessions", sessionStates.size(),
            "heartbeat_interval_ms", heartbeatInterval.toMillis(),
            "max_idle_time_ms", maxIdleTime.toMillis(),
            "monitor_active", !scheduler.isShutdown()
        );
    }
}

// ClaudeAsyncJob 改造
private static final class ClaudeAsyncJob {
    private final String jobId;
    private final String sessionId;
    private final String action;
    private final Instant startedAt;
    private final CompletableFuture<ExecutionResult> future;
    private volatile JobState state = JobState.RUNNING;
    private volatile Instant lastHeartbeat;
    
    private ClaudeAsyncJob(String jobId, String sessionId, String action, 
                           Instant startedAt, CompletableFuture<ExecutionResult> future) {
        this.jobId = jobId;
        this.sessionId = sessionId;
        this.action = action;
        this.startedAt = startedAt;
        this.future = future;
        this.lastHeartbeat = startedAt;
    }
    
    public enum JobState {
        RUNNING, COMPLETED, FAILED, TIMEOUT, CANCELLED
    }
}

// ClaudeSessionState 改造
private static final class ClaudeSessionState {
    private final String sessionId;
    private String claudeSessionId;
    private String lastAction;
    private boolean lastSuccess;
    private int lastExitCode;
    private String lastError;
    private int lastEventCount;
    private Instant lastUpdatedAt;
    private volatile SessionState state = SessionState.ACTIVE;
    
    private ClaudeSessionState(String sessionId) {
        this.sessionId = sessionId;
        this.lastUpdatedAt = Instant.now();
    }
    
    public enum SessionState {
        ACTIVE, IDLE, STALE, CLOSED
    }
}

// ClaudeExecutionGateway 改造
public class ClaudeExecutionGateway {
    private final ClaudeHeartbeatMonitor heartbeatMonitor;
    
    public ClaudeExecutionGateway(SandboxService sandboxService, 
                                   ClaudeCliProperties claudeCliProperties,
                                   ClaudeHeartbeatMonitor heartbeatMonitor) {
        this.sandboxService = sandboxService;
        this.claudeCliProperties = claudeCliProperties;
        this.heartbeatMonitor = heartbeatMonitor;
        
        // 启动心跳监控 ← 闭环：启动监控
        heartbeatMonitor.startMonitoring();
    }
    
    public Map<String, Object> startAsyncJob(String sessionId, Map<String, Object> params) {
        String action = stringValue(params.get("action"), "prompt").toLowerCase();
        String sid = normalizeSessionId(sessionId);
        String jobId = "claude-job-" + UUID.randomUUID().toString().substring(0, 8);
        
        CompletableFuture<ExecutionResult> future = execute(sid, params);
        ClaudeAsyncJob job = new ClaudeAsyncJob(jobId, sid, action, Instant.now(), future);
        asyncJobs.put(jobId, job);
        
        // 心跳监控已启动，任务会自动监控 ← 闭环：自动监控
        return Map.of(
            "job_id", jobId,
            "session_id", sid,
            "action", action,
            "state", "running",
            "started_at", job.startedAt,
            "heartbeat_enabled", true
        );
    }
    
    public void close() {
        // 停止心跳监控 ← 闭环：优雅退出
        heartbeatMonitor.stopMonitoring();
        closeAllSessions();
    }
}
```

**闭环验证表格**：
| 阶段 | 入口 | 出口 | 闭环检查 |
|------|------|------|----------|
| 监控启动 | `startMonitoring()` | scheduler启动 | ✅ 持续监控 |
| 任务检查 | `checkAsyncJobs()` | 任务状态更新 | ✅ 任务监控 |
| 超时检测 | `elapsed > maxIdleTime` | TIMEOUT标记 | ✅ 超时处理 |
| 心跳发送 | `lastHeartbeat = now` | 心跳更新 | ✅ 心跳发送 |
| 会话检查 | `checkSessionHealth()` | 会话状态更新 | ✅ 会话监控 |
| 失效检测 | `idleDuration > maxIdleTime` | STALE标记 | ✅ 失效处理 |
| 资源清理 | `cleanupStaleResources()` | 移除失效资源 | ✅ 资源清理 |
| 监控停止 | `stopMonitoring()` | scheduler关闭 | ✅ 优雅退出 |

**实现步骤**：
1. 新增 `ClaudeHeartbeatMonitor` 类及其枚举（JobState, SessionState）
2. 改造 `ClaudeAsyncJob` 和 `ClaudeSessionState`，添加 `state` 和 `lastHeartbeat` 字段
3. 在 `ClaudeExecutionGateway` 构造函数中注入并启动 `ClaudeHeartbeatMonitor`
4. 在 `ClaudeExecutionGateway.close()` 方法中停止心跳监控
5. 添加配置项：
   - `claude.cli.heartbeat-interval`（默认30秒）
   - `claude.cli.max-idle-time`（默认5分钟）
6. 在 `ClaudeCliTool` 中添加心跳状态查询接口（通过 `getSessionSnapshot()`）

---

#### 优化方案4：完善错误处理闭环（借鉴错误处理流程）

**优化目标**：借鉴Claude Code的错误分类和恢复策略机制，增强错误处理的闭环完整性。

**当前问题**：
```java
// 当前实现：统一的异常处理，缺少分类和恢复策略
@Override
public ToolResult execute(ToolParams params, ToolContext context) {
    long start = System.currentTimeMillis();
    
    try {
        // ... 执行逻辑
        stats = stats.recordCall(true, duration);
        return ToolResult.success(data);
    } catch (Exception e) {
        long duration = System.currentTimeMillis() - start;
        stats = stats.recordCall(false, duration);
        log.error("Failed to execute Claude CLI action: {}", normalizedAction, e);
        return ToolResult.failure("Execution error: " + e.getMessage());
        // ❌ 无错误分类
        // ❌ 无恢复策略
        // ❌ 无重试机制
    }
}
```

**优化方案**：
```java
// 新增：错误分类与恢复策略
public class ClaudeErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ClaudeErrorHandler.class);
    
    /**
     * 错误分类
     * ← 闭环：类型识别
     */
    public ClaudeErrorType classifyError(Exception error) {
        if (error instanceof TimeoutException) {
            return ClaudeErrorType.TIMEOUT_ERROR;
        }
        if (error instanceof ProcessException) {
            ProcessException pe = (ProcessException) error;
            if (pe.exitCode() == 127) {
                return ClaudeErrorType.CLI_NOT_FOUND;
            }
            if (pe.exitCode() == 1) {
                return ClaudeErrorType.CLI_ERROR;
            }
        }
        if (error instanceof SecurityException) {
            return ClaudeErrorType.PERMISSION_ERROR;
        }
        if (error instanceof NetworkException) {
            return ClaudeErrorType.NETWORK_ERROR;
        }
        if (error instanceof IllegalArgumentException) {
            return ClaudeErrorType.PARAMETER_ERROR;
        }
        if (error instanceof IOException) {
            return ClaudeErrorType.IO_ERROR;
        }
        return ClaudeErrorType.UNKNOWN_ERROR;
    }
    
    /**
     * 获取错误处理策略
     * ← 闭环：策略映射
     */
    public ClaudeErrorStrategy getErrorStrategy(ClaudeErrorType errorType, String action) {
        switch (errorType) {
            case TIMEOUT_ERROR:
                return ClaudeErrorStrategy.retryWithExtendedTimeout(3, 2.0);
            case CLI_NOT_FOUND:
                return ClaudeErrorStrategy.abort("Claude CLI not installed");
            case PERMISSION_ERROR:
                return ClaudeErrorStrategy.requestPermission();
            case NETWORK_ERROR:
                return ClaudeErrorStrategy.retryWithNetworkCheck(2);
            case PARAMETER_ERROR:
                return ClaudeErrorStrategy.requestCorrection();
            case CLI_ERROR:
                return ClaudeErrorStrategy.abort("Claude CLI execution error");
            case IO_ERROR:
                return ClaudeErrorStrategy.retry(1);
            default:
                return ClaudeErrorStrategy.abort("Unknown error");
        }
    }
    
    /**
     * 处理错误
     * ← 闭环：错误处理
     */
    public ClaudeErrorResult handleError(
        Exception error,
        String action,
        Map<String, Object> params,
        int retryCount
    ) {
        // 1. 错误分类 ← 闭环：类型识别
        ClaudeErrorType errorType = classifyError(error);
        
        // 2. 获取处理策略 ← 闭环：策略映射
        ClaudeErrorStrategy strategy = getErrorStrategy(errorType, action);
        
        // 3. 根据策略处理 ← 闭环：策略执行
        switch (strategy.action) {
            case RETRY:
                if (retryCount < strategy.maxRetries) {
                    log.warn("Retrying Claude CLI action: action={}, retryCount={}, maxRetries={}",
                        action, retryCount, strategy.maxRetries);
                    return ClaudeErrorResult.retry(
                        errorType,
                        strategy.nextTimeout,
                        retryCount + 1
                    );
                }
                break;
                
            case RETRY_WITH_EXTENDED_TIMEOUT:
                if (retryCount < strategy.maxRetries) {
                    long newTimeout = (long) (strategy.timeoutMultiplier * strategy.nextTimeout);
                    log.warn("Retrying with extended timeout: action={}, retryCount={}, newTimeout={}ms",
                        action, retryCount, newTimeout);
                    return ClaudeErrorResult.retryWithTimeout(
                        errorType,
                        newTimeout,
                        retryCount + 1
                    );
                }
                break;
                
            case REQUEST_PERMISSION:
                log.warn("Permission error, requesting user permission: action={}", action);
                return ClaudeErrorResult.requestPermission(errorType);
                
            case REQUEST_CORRECTION:
                log.warn("Parameter error, requesting correction: action={}", action);
                return ClaudeErrorResult.requestCorrection(errorType);
                
            case ABORT:
                log.error("Aborting Claude CLI execution: action={}, errorType={}, message={}",
                    action, errorType, strategy.message);
                return ClaudeErrorResult.abort(errorType, strategy.message);
        }
        
        // 超过最大重试次数 ← 闭环：终止重试
        return ClaudeErrorResult.abort(errorType, "Max retries exceeded");
    }
    
    // 枚举和数据类
    public enum ClaudeErrorType {
        TIMEOUT_ERROR, CLI_NOT_FOUND, CLI_ERROR, PERMISSION_ERROR,
        NETWORK_ERROR, PARAMETER_ERROR, IO_ERROR, UNKNOWN_ERROR
    }
    
    public enum ClaudeErrorAction {
        RETRY, RETRY_WITH_EXTENDED_TIMEOUT, REQUEST_PERMISSION, REQUEST_CORRECTION, ABORT
    }
    
    public static class ClaudeErrorStrategy {
        public final ClaudeErrorAction action;
        public final int maxRetries;
        public final long nextTimeout;
        public final double timeoutMultiplier;
        public final String message;
        
        public static ClaudeErrorStrategy retry(int maxRetries) {
            return new ClaudeErrorStrategy(ClaudeErrorAction.RETRY, maxRetries, 30000, 1.0, null);
        }
        
        public static ClaudeErrorStrategy retryWithExtendedTimeout(int maxRetries, double multiplier) {
            return new ClaudeErrorStrategy(ClaudeErrorAction.RETRY_WITH_EXTENDED_TIMEOUT, 
                maxRetries, 30000, multiplier, null);
        }
        
        public static ClaudeErrorStrategy retryWithNetworkCheck(int maxRetries) {
            return new ClaudeErrorStrategy(ClaudeErrorAction.RETRY, maxRetries, 5000, 1.0, null);
        }
        
        public static ClaudeErrorStrategy requestPermission() {
            return new ClaudeErrorStrategy(ClaudeErrorAction.REQUEST_PERMISSION, 0, 0, 1.0, null);
        }
        
        public static ClaudeErrorStrategy requestCorrection() {
            return new ClaudeErrorStrategy(ClaudeErrorAction.REQUEST_CORRECTION, 0, 0, 1.0, null);
        }
        
        public static ClaudeErrorStrategy abort(String message) {
            return new ClaudeErrorStrategy(ClaudeErrorAction.ABORT, 0, 0, 1.0, message);
        }
        
        private ClaudeErrorStrategy(ClaudeErrorAction action, int maxRetries, 
                                    long nextTimeout, double timeoutMultiplier, String message) {
            this.action = action;
            this.maxRetries = maxRetries;
            this.nextTimeout = nextTimeout;
            this.timeoutMultiplier = timeoutMultiplier;
            this.message = message;
        }
    }
    
    public static class ClaudeErrorResult {
        public final ClaudeErrorType errorType;
        public final ClaudeErrorAction action;
        public final boolean recoverable;
        public final String message;
        public final long suggestedTimeout;
        public final int nextRetryCount;
        
        // 工厂方法...
    }
}

// ClaudeCliTool 改造
@Override
public ToolResult execute(ToolParams params, ToolContext context) {
    long start = System.currentTimeMillis();
    String action = params.getString("action");
    String normalizedAction = action.toLowerCase();
    String sessionId = context != null ? context.sessionId() : null;
    
    // 重试循环 ← 闭环：重试机制
    int retryCount = 0;
    int maxRetries = 3;
    long timeout = 30000; // 默认30秒
    
    while (retryCount <= maxRetries) {
        try {
            // 执行逻辑...
            ExecutionResult result = executionGateway.execute(sessionId, gatewayParams)
                .orTimeout(timeout, TimeUnit.MILLISECONDS)
                .join();
            
            if (result.success()) {
                stats = stats.recordCall(true, System.currentTimeMillis() - start);
                return ToolResult.success(buildSuccessData(result));
            }
            
            // 执行失败，检查是否需要重试
            ClaudeErrorResult errorResult = errorHandler.handleError(
                new ProcessException(result.stderr(), result.exitCode()),
                normalizedAction,
                gatewayParams,
                retryCount
            );
            
            if (!errorResult.recoverable) {
                stats = stats.recordCall(false, System.currentTimeMillis() - start);
                return ToolResult.failure(errorResult.message);
            }
            
            if (errorResult.action == ClaudeErrorAction.RETRY 
                || errorResult.action == ClaudeErrorAction.RETRY_WITH_EXTENDED_TIMEOUT) {
                retryCount = errorResult.nextRetryCount;
                timeout = errorResult.suggestedTimeout;
                log.warn("Retrying execution: retryCount={}, timeout={}ms", retryCount, timeout);
                continue;
            }
            
            // 其他处理（权限请求、参数修正等）
            stats = stats.recordCall(false, System.currentTimeMillis() - start);
            return ToolResult.failure(errorResult.message);
            
        } catch (TimeoutException e) {
            // 超时错误处理 ← 闭环：超时处理
            ClaudeErrorResult errorResult = errorHandler.handleError(e, normalizedAction, gatewayParams, retryCount);
            
            if (errorResult.recoverable && errorResult.action == ClaudeErrorAction.RETRY_WITH_EXTENDED_TIMEOUT) {
                retryCount = errorResult.nextRetryCount;
                timeout = errorResult.suggestedTimeout;
                log.warn("Timeout, retrying with extended timeout: retryCount={}, timeout={}ms", 
                    retryCount, timeout);
                continue;
            }
            
            stats = stats.recordCall(false, System.currentTimeMillis() - start);
            return ToolResult.failure("Timeout after " + retryCount + " retries");
            
        } catch (Exception e) {
            // 其他错误处理 ← 闭环：错误分类
            ClaudeErrorResult errorResult = errorHandler.handleError(e, normalizedAction, gatewayParams, retryCount);
            
            if (errorResult.recoverable && retryCount < maxRetries) {
                retryCount++;
                log.warn("Error, retrying: retryCount={}, errorType={}", retryCount, errorResult.errorType);
                continue;
            }
            
            stats = stats.recordCall(false, System.currentTimeMillis() - start);
            log.error("Failed to execute Claude CLI action: {}", normalizedAction, e);
            return ToolResult.failure(errorResult.message);
        }
    }
    
    // 超过最大重试次数 ← 闭环：终止重试
    stats = stats.recordCall(false, System.currentTimeMillis() - start);
    return ToolResult.failure("Max retries exceeded");
}
```

**闭环验证表格**：
| 阶段 | 入口 | 出口 | 检查点 |
|------|------|------|--------|
| 错误捕获 | `catch(error)` | 错误对象 | ✅ 错误捕获 |
| 错误分类 | `classifyError()` | ClaudeErrorType | ✅ 类型识别 |
| 策略获取 | `getErrorStrategy()` | ClaudeErrorStrategy | ✅ 策略映射 |
| 超时错误处理 | RETRY_WITH_EXTENDED_TIMEOUT | 重试建议 | ✅ 重试机制 |
| 权限错误处理 | REQUEST_PERMISSION | 权限提示 | ✅ 权限建议 |
| 参数错误处理 | REQUEST_CORRECTION | 参数建议 | ✅ 参数建议 |
| CLI错误处理 | ABORT | 错误报告 | ✅ 终止处理 |
| 重试循环 | `retryCount <= maxRetries` | 重试执行 | ✅ 重试机制 |
| 超时调整 | `timeout = suggestedTimeout` | 新超时值 | ✅ 超时调整 |

**实现步骤**：
1. 新增 `ClaudeErrorHandler` 类及其枚举和数据类
2. 在 `ClaudeCliTool` 中注入 `ClaudeErrorHandler`
3. 改造 `execute()` 方法，添加重试循环和错误分类处理
4. 使用 `CompletableFuture.orTimeout()` 实现超时控制
5. 在 `ToolResult` 中添加错误类型和建议字段
6. 在 `ToolStats` 中添加错误分类统计

---

## 三、闭环完整性对比

### 3.1 Living Agent Service 闭环33 当前状态

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│            Living Agent Service 闭环33 当前状态                                                │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  流程定义：                                                                                   │
│  员工任务 → 工具选择 → 参数定制 → Claude CLI 执行 → 结果解析 → 任务完成 → 回执提交             │
│                                                                                              │
│  当前闭环完整性（业务视角）：                                                                   │
│  ├─ 员工任务接收 ✅ 完整                                                                       │
│  │     ├─ 任务解析 ✅                                                                         │
│  │     ├─ 任务分派 ✅                                                                         │
│  │     └─ 任务状态管理 ✅                                                                     │
│  ├─ 工具选择决策 ⚠️ 部分实现                                                                   │
│  │     ├─ 任务适用性判断 ⚠️ 部分实现                                                           │
│  │     ├─ 员工权限验证 ⚠️ 部分实现                                                             │
│  │     ├─ 工具能力边界确认 ❌ 缺失                                                             │
│  │     └─ Claude CLI vs 其他工具选择策略 ❌ 缺失                                                │
│  ├─ 参数定制 ⚠️ 部分实现                                                                       │
│  │     ├─ 不同员工类型参数定制 ❌ 缺失                                                          │
│  │     ├─ 任务上下文参数注入 ⚠️ 部分实现                                                        │
│  │     ├─ 超时和权限控制 ⚠️ 部分实现                                                            │
│  │     └─ Claude CLI 工具权限配置 ❌ 缺失                                                       │
│  ├─ Claude CLI 执行（调用闭环22） ✅ 完整                                                      │
│  │     ├─ 执行状态监控 ⚠️ 部分实现                                                              │
│  │     ├─ 心跳检测 ❌ 缺失                                                                     │
│  │     ├─ 超时处理 ⚠️ 部分实现                                                                 │
│  │     └─ 执行进度追踪 ❌ 缺失                                                                 │
│  ├─ 结果解析（闭环22提供） ✅ 完整                                                             │
│  │     ├─ stream-json解析 ✅                                                                  │
│  │     ├─ 事件类型识别 ⚠️ 部分实现                                                              │
│  │     ├─ 结果格式验证 ❌ 缺失                                                                 │
│  │     └─ 结果数据提取 ✅                                                                     │
│  ├─ 任务完成度检查 ⚠️ 部分实现                                                                 │
│  │     ├─ 完成标准定义 ❌ 缺失                                                                 │
│  │     ├─ 完成度评估算法 ❌ 缺失                                                                │
│  │     ├─ 质量评分机制 ❌ 缺失                                                                 │
│  │     └─ 人工审核触发 ⚠️ 部分实现                                                              │
│  ├─ 回执提交 ⚠️ 部分实现                                                                       │
│  │     ├─ 回执格式定义 ✅                                                                     │
│  │     ├─ 回执提交验证 ⚠️ 部分实现                                                              │
│  │     ├─ 绩效数据收集 ⚠️ 部分实现                                                              │
│  │     ├─ 经验沉淀机制 ❌ 缺失                                                                 │
│  │     └─ 工具使用效果记录 ❌ 缺失                                                              │
│                                                                                              │
│  主要缺口（业务视角）：                                                                         │
│  ├─ ❌ 工具选择决策闭环（任务适用性、员工权限、能力边界）                                         │
│  ├─ ❌ 不同员工类型参数定制机制                                                                 │
│  ├─ ❌ Claude CLI 工具权限配置体系                                                              │
│  ├─ ❌ 执行进度追踪和心跳检测                                                                   │
│  ├─ ❌ 任务完成度检查和评估算法                                                                 │
│  ├─ ❌ 结果质量评分机制                                                                         │
│  ├─ ❌ 经验沉淀和工具使用效果记录                                                               │
│  ├─ ❌ 工具使用绩效数据收集                                                                     │
│                                                                                              │
│  与闭环22的关系：                                                                              │
│  ├─ 闭环33 使用闭环22提供的 Claude CLI 执行能力                                                 │
│  ├─ 闭环22 的技术缺口（如CLI可用性检查、错误处理）会影响闭环33的业务执行                          │
│  ├─ 闭环33 需要在闭环22的基础上补充业务层面的闭环验证                                            │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 3.2 Claude Code 对应流程的闭环完整性

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│            Claude Code 对应流程的闭环完整性                                                     │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  CLI启动流程闭环完整性：✅ 完整                                                                │
│  ├─ ✅ 快速路径检查（--version/--bridge等）                                                    │
│  ├─ ✅ 完整初始化流程（MDM/Keychain/MCP预取）                                                   │
│  ├─ ✅ 性能剖析检查点                                                                          │
│  ├─ ✅ 迁移系统闭环                                                                            │
│  ├─ ✅ REPL循环闭环                                                                            │
│                                                                                              │
│  工具执行流程闭环完整性：✅ 完整                                                                │
│  ├─ ✅ 工具池组装闭环（assembleToolPool）                                                      │
│  ├─ ✅ 权限检查闭环（filterToolsByDenyRules）                                                  │
│  ├─ ✅ 工具查找闭环（toolPool.find）                                                           │
│  ├─ ✅ 工具启用状态检查（isEnabled）                                                           │
│  ├─ ✅ 执行上下文准备                                                                          │
│  ├─ ✅ 工具执行闭环（tool.invoke）                                                             │
│  ├─ ✅ 结果处理闭环（processToolResult）                                                       │
│  ├─ ✅ 错误处理闭环（handleToolError）                                                         │
│                                                                                              │
│  MCP服务流程闭环完整性：✅ 完整                                                                │
│  ├─ ✅ 配置解析闭环（getAllMcpConfigs）                                                        │
│  ├─ ✅ 传输创建闭环（createTransport）                                                         │
│  ├─ ✅ 客户端连接闭环（client.connect）                                                        │
│  ├─ ✅ 工具列表获取闭环（fetchToolsForClient）                                                 │
│  ├─ ✅ 工具调用闭环（callMcpTool）                                                             │
│  ├─ ✅ 错误处理闭环（McpAuthError/McpToolCallError）                                           │
│  ├─ ✅ 缓存管理闭环（clearServerCache）                                                        │
│                                                                                              │
│  Bridge远程控制流程闭环完整性：✅ 完整                                                          │
│  ├─ ✅ Bridge启动闭环（bridgeMain）                                                            │
│  ├─ ✅ Poll循环闭环（api.poll）                                                                │
│  ├─ ✅ 会话创建闭环（createNewSession）                                                        │
│  ├─ ✅ 会话恢复闭环（resumeSession）                                                           │
│  ├─ ✅ 会话取消闭环（cancelSession）                                                           │
│  ├─ ✅ 心跳检测闭环（heartbeat）                                                               │
│  ├─ ✅ 失效检测闭环（sessionWorkIds.delete）                                                   │
│  ├─ ✅ 错误处理闭环（优雅降级）                                                                 │
│  ├─ ✅ 资源清理闭环（cleanupSessions）                                                         │
│                                                                                              │
│  工具结果处理闭环完整性：✅ 完整                                                                │
│  ├─ ✅ 类型识别闭环（detectResultType）                                                        │
│  ├─ ✅ 文本处理闭环（processTextResult）                                                       │
│  ├─ ✅ 错误处理闭环（processErrorResult）                                                      │
│  ├─ ✅ 图片处理闭环（processImageResult）                                                      │
│  ├─ ✅ MCP处理闭环（processMcpResult）                                                         │
│  ├─ ✅ 元数据注入闭环（ResultMetadata）                                                        │
│  ├─ ✅ 日志记录闭环（logToolResult）                                                           │
│                                                                                              │
│  工具错误处理闭环完整性：✅ 完整                                                                │
│  ├─ ✅ 错误捕获闭环（catch块）                                                                 │
│  ├─ ✅ 错误分类闭环（classifyError）                                                           │
│  ├─ ✅ 策略获取闭环（getErrorHandlingStrategy）                                                │
│  ├─ ✅ 权限错误处理闭环（prompt_permission）                                                   │
│  ├─ ✅ 超时错误处理闭环（retry_with_timeout）                                                  │
│  ├─ ✅ 网络错误处理闭环（retry_with_network）                                                  │
│  ├─ ✅ 参数错误处理闭环（prompt_correction）                                                   │
│  ├─ ✅ 系统错误处理闭环（log_and_abort）                                                       │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 3.3 需要补充的闭环检查点

| 闭环检查点 | 当前状态 | Claude Code对应实现 | 补充优先级 |
|----------|---------|---------------------|----------|
| **CLI可用性预检查** | ❌ 缺失 | bootstrap快速路径检查 | **P0 - 必须** |
| **CLI安装验证** | ❌ 缺失 | where/which检查 | **P0 - 必须** |
| **版本检查闭环** | ❌ 缺失 | --version快速检查 | **P1 - 重要** |
| **工具启用状态检查** | ❌ 缺失 | isEnabled机制 | **P1 - 重要** |
| **结果类型识别** | ❌ 缺失 | detectResultType | **P1 - 重要** |
| **结果格式验证** | ❌ 缺失 | isValidJsonEvent | **P1 - 重要** |
| **结果元数据注入** | ⚠️ 部分 | ResultMetadata完整实现 | **P2 - 改进** |
| **错误分类机制** | ❌ 缺失 | classifyError | **P0 - 必须** |
| **错误恢复策略** | ❌ 缺失 | getErrorHandlingStrategy | **P0 - 必须** |
| **重试机制** | ❌ 缺失 | retry循环 | **P0 - 必须** |
| **超时调整机制** | ❌ 缺失 | calculateNewTimeout | **P1 - 重要** |
| **心跳检测机制** | ❌ 缺失 | heartbeatActiveWorkItems | **P1 - 重要** |
| **会话健康监控** | ❌ 缺失 | checkSessionHealth | **P1 - 重要** |
| **失效资源自动清理** | ⚠️ 手动 | cleanupStaleResources自动 | **P2 - 改进** |
| **性能剖析检查点** | ❌ 缺失 | profileCheckpoint | **P2 - 可选** |
| **会话状态机** | ⚠️ 部分 | SessionState完整枚举 | **P2 - 改进** |

---

## 四、实施建议

### 4.1 优先级排序

**P0 - 必须实现（影响系统稳定性）**：
1. CLI可用性预检查（避免无效启动）
2. 错误分类机制（明确错误类型）
3. 错误恢复策略（自动恢复能力）
4. 重试机制（提升鲁棒性）

**P1 - 重要实现（提升用户体验）**：
5. CLI安装验证（用户友好提示）
6. 版本检查闭环（快速失败）
7. 工具启用状态检查（工具管理）
8. 结果类型识别（结果处理）
9. 结果格式验证（数据质量）
10. 错误超时调整机制（动态超时）
11. 心跳检测机制（任务监控）
12. 会话健康监控（会话管理）

**P2 - 改进实现（增强系统能力）**：
13. 结果元数据注入完整性
14. 失效资源自动清理
15. 性能剖析检查点
16. 会话状态机完善

---

### 4.2 实施步骤建议

**阶段一：基础闭环补全（P0）**
- 时间：1-2周
- 内容：
  1. 实现CLI可用性预检查（ClaudeCliHealthChecker）
  2. 实现错误分类机制（ClaudeErrorHandler）
  3. 实现错误恢复策略（ClaudeErrorStrategy）
  4. 实现重试机制（retry循环）

**阶段二：监控闭环补全（P1）**
- 时间：2-3周
- 内容：
  5. 实现心跳检测机制（ClaudeHeartbeatMonitor）
  6. 实现会话健康监控（checkSessionHealth）
  7. 实现结果类型识别（ClaudeResultProcessor）
  8. 实现结果格式验证（isValidJsonEvent）

**阶段三：完善性改进（P2）**
- 时间：1周
- 内容：
  9. 完善结果元数据注入
  10. 实现失效资源自动清理
  11. 添加性能剖析检查点
  12. 完善会话状态机

---

### 4.3 配置项建议

新增配置项（在 `ClaudeCliProperties` 中）：
```yaml
claude:
  cli:
    # 基础配置
    command: claude
    bash-no-login: true
    
    # 新增：健康检查配置
    health-check:
      enabled: true
      interval: 5m
      quick-check-timeout: 5s
    
    # 新增：心跳监控配置
    heartbeat:
      enabled: true
      interval: 30s
      max-idle-time: 5m
    
    # 新增：错误处理配置
    error-handling:
      max-retries: 3
      timeout-multiplier: 2.0
      default-timeout: 30s
    
    # 新增：结果处理配置
    result-processing:
      validate-format: true
      extract-events: true
      metadata-injection: full
```

---

## 五、总结

### 5.1 对比分析结论

Living Agent Service 闭环33（数字员工使用 Claude CLI 工具流程）与 Claude Code 流程对比，发现以下关键差异：

**优势方面**：
- ✅ 员工任务接收和分派机制完整
- ✅ 回执提交机制存在（可在此基础上补充业务闭环）
- ✅ 基础执行调用能力具备（通过闭环22）
- ✅ 参数定制有一定灵活性

**主要缺口**：
- ❌ 缺少工具选择决策闭环（任务适用性、员工权限、能力边界）
- ❌ 缺少不同员工类型的参数定制机制
- ❌ 缺少 Claude CLI 工具权限配置体系
- ❌ 缺少执行进度追踪和心跳检测
- ❌ 缺少任务完成度检查和评估算法
- ❌ 缺少结果质量评分机制
- ❌ 缺少经验沉淀和工具使用效果记录

---

### 5.2 借鉴价值评估

**高价值借鉴项（业务视角）**：
1. **工具选择决策闭环** - 任务适用性判断、员工权限验证、能力边界确认
2. **参数定制机制** - 不同员工类型的参数定制、超时和权限控制
3. **结果质量评估闭环** - 任务完成度检查、质量评分、回执提交

**中价值借鉴项（业务视角）**：
4. **执行进度追踪** - 心跳检测、执行状态监控
5. **经验沉淀机制** - 工具使用效果记录、绩效数据收集

**低价值借鉴项（业务视角）**：
6. **人工审核触发** - 可选的质量保障机制

---

### 5.3 最终建议

Living Agent Service 闭环33当前实现存在业务层面的系统性缺口，建议从以下四个方面补充闭环验证：

1. **工具选择决策闭环** - 建议实现 `ClaudeToolSelectionService`
2. **参数定制闭环** - 建议实现 `ClaudeParameterCustomizationService`
3. **结果质量评估闭环** - 建议实现 `ClaudeResultQualityEvaluator`
4. **经验沉淀闭环** - 建议实现 `ClaudeExperienceAccumulationService`

同时，闭环33依赖闭环22（技术组件）的稳定性，建议闭环22优先实现：
- CLI可用性预检查（`ClaudeCliHealthChecker`）
- 错误处理闭环（`ClaudeErrorHandler`）
- 心跳监控闭环（`ClaudeHeartbeatMonitor`）

通过实施这些优化方案，可以显著提升闭环33的业务完整性，使其达到 Claude Code 流程闭环的业务标准。

---

### 5.4 闭环22与闭环33协同优化建议

```
┌─────────────────────────────────────────────────────────────────────────────┐
│           闭环22与闭环33协同优化路径                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【阶段一：闭环22技术组件优化】                                               │
│  ├─ P0：CLI可用性预检查                                                      │
│  ├─ P0：错误分类和恢复策略                                                   │
│  ├─ P0：重试机制                                                             │
│  ├─ P1：心跳检测机制                                                         │
│  └─ P1：结果类型识别和验证                                                   │
│                                                                             │
│  【阶段二：闭环33业务流程优化】                                               │
│  ├─ P0：工具选择决策闭环                                                     │
│  │     ├─ 任务适用性判断                                                     │
│  │     ├─ 员工权限验证                                                       │
│  │     ├─ 工具能力边界确认                                                   │
│  ├─ P0：参数定制机制                                                         │
│  │     ├─ 不同员工类型参数定制                                               │
│  │     ├─ 超时和权限控制                                                     │
│  ├─ P1：结果质量评估闭环                                                     │
│  │     ├─ 任务完成度检查                                                     │
│  │     ├─ 结果质量评分                                                       │
│  ├─ P1：经验沉淀机制                                                         │
│  │     ├─ 工具使用效果记录                                                   │
│  │     ├─ 绩效数据收集                                                       │
│                                                                             │
│  【协同验证】                                                                 │
│  ├─ 闭环22优化后，闭环33可以稳定调用 Claude CLI                               │
│  ├─ 闭环33优化后，业务层面的闭环验证得以补全                                   │
│  ├─ 两者协同形成完整的"数字员工使用 Claude CLI 工具"能力                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

> **文档版本**: v2.0  
> **生成日期**: 2026-07-02  
> **分析对象**: 闭环33（数字员工使用 Claude CLI 工具流程）  
> **相关闭环**: 闭环22（Claude CLI代理闭环 - 技术组件）  
> **分析人员**: AI Assistant  
> **下一步**: 根据优先级排序实施闭环33和闭环22的协同优化方案