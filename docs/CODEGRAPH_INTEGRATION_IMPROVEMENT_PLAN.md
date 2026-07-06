# CodeGraph 集成改进方案

> 目标：评估 [colbymchenry/codegraph](https://github.com/colbymchenry/codegraph) 对 living-agent-service 项目的优化价值，明确集成路径、兼容性风险和代码修改范围。
>
> 配合文档：
> - `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md`：代码结构与文件功能
> - `docs/MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md`：模型职责与执行闭环
> - `docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md`：大脑与固定数字员工做事规范
>
> 更新时间：2026-06-22

---

## 1. CodeGraph 概述

CodeGraph 是一个 **100% 本地运行** 的代码语义知识图谱工具，通过 MCP（Model Context Protocol）服务器为 AI 编程助手提供：

| 能力 | 说明 |
|---|---|
| 符号关系图谱 | 调用图、引用关系、影响半径 |
| 全文搜索 | 基于 FTS5 的跨代码库即时搜索 |
| 智能上下文构建 | 一次工具调用返回入口点 + 相关符号 + 代码片段 |
| 影响分析 | 追踪任意符号的 callers / callees |
| 文件监视自动同步 | 原生 FSEvents/inotify/ReadDirectoryChangesW + 防抖 |
| 20+ 语言支持 | Java、TypeScript、Python、Rust（与本项目技术栈完全匹配） |
| 框架感知路由 | 识别 17 种 Web 框架路由文件（含 Spring Boot） |

基准测试（Claude Opus 4.8，7 个真实开源项目）：

```
平均：16% 更便宜 / 47% 更少 tokens / 22% 更快 / 58% 更少工具调用
Java 项目（OkHttp）：25% 更便宜 / 54% 更少 tokens / 31% 更快
```

---

## 2. 本项目现有代码理解与修改链路

### 2.1 代码库存储与流转架构

```text
┌─────────────────────────────────────────────────────────────────┐
│  服务器端（living-agent-service 容器内）                          │
│                                                                   │
│  .living/codebase/          ← 代码库镜像（EvolutionNamespaceService）│
│  ├── docs/                  ← 文档镜像                            │
│  ├── documents/             ← 企业知识源镜像                      │
│  ├── source-tree/           ← 源码树索引                          │
│  └── ...                                                          │
│                                                                   │
│  .living/evolution/         ← 进化工作区                          │
│  ├── patches/               ← 补丁提案与应用                      │
│  ├── signals/               ← 进化信号                            │
│  ├── knowledge/             ← 大脑专属进化知识                    │
│  └── rollback/              ← 回滚基线                            │
│                                                                   │
│  agent_workspace/           ← Claude CLI 工作目录                 │
│  （ClaudeCliProperties.workspace）                                │
│                                                                   │
│  CodebaseAccessService      ← 大脑受控访问代码库镜像              │
│  PatchProposalService       ← 补丁提案创建/保存/查询              │
│  PatchApplicationService    ← 补丁应用/回滚                       │
└─────────────────────────────────────────────────────────────────┘
         │
         │ 代码库从服务器端复制到客户端本地工作目录
         ↓
┌─────────────────────────────────────────────────────────────────┐
│  客户端本地（Docker 沙箱内）                                      │
│                                                                   │
│  agent_workspace/           ← Claude CLI 实际操作目录             │
│  （从 .living/codebase/ 复制而来）                                │
│                                                                   │
│  Claude CLI 执行流程：                                            │
│  1. 大脑/员工 → ClaudeCliTool → ClaudeExecutionGateway            │
│  2. ClaudeExecutionGateway 创建 SandboxSession                    │
│  3. 注入环境变量：                                                │
│     - ANTHROPIC_BASE_URL=http://living-agent-service:8382/...    │
│     - ANTHROPIC_API_KEY=sk-living-agent-claude-proxy             │
│  4. 执行 claude -p "..." --add-dir ./agent_workspace             │
│  5. Claude CLI 通过代理调用模型池（qwen3.5:9b 等）               │
│  6. Claude CLI 读取/修改 agent_workspace/ 中的文件               │
│  7. 修改结果通过 ExecutionResult 返回                             │
│  8. 大脑通过 PatchApplicationService 应用/回滚补丁                │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 关键代码位置

| 组件 | 文件 | 作用 |
|---|---|---|
| 代码库镜像路径 | `core/runtime/EvolutionNamespaceService.java` | `.living/codebase/` 路径管理 |
| 代码库访问配置 | `core/evolution/codebase/CodebaseAccessConfig.java` | 挂载点、敏感文件过滤、速率限制 |
| 代码库受控访问 | `core/evolution/codebase/CodebaseAccessService.java` | 大脑读写代码库镜像 |
| 源码结构索引 | `core/knowledge/professional/SourceTreeIndexer.java` | 生成 source-tree.json |
| 架构知识播种 | `core/knowledge/professional/ArchitectureKnowledgeSeeder.java` | docs/documents → 知识库 |
| 错误代码映射 | `core/evolution/codemapper/ErrorCodeMapper.java` | 异常→代码文件→文档 |
| 补丁提案 | `core/evolution/patch/PatchProposalService.java` | 创建/保存/查询补丁 |
| 补丁应用 | `core/evolution/patch/PatchApplicationService.java` | 应用/回滚补丁 |
| Claude CLI 配置 | `core/sandbox/ClaudeCliProperties.java` | 命令/工作目录/代理/超时 |
| Claude 执行网关 | `core/sandbox/ClaudeExecutionGateway.java` | CLI 参数构造/环境注入/会话管理 |
| Claude CLI 工具 | `core/tool/impl/ClaudeCliTool.java` | Tool 接口适配 |
| 工具注册 | `core/config/ToolConfig.java` | 注册 ClaudeCliTool |
| MCP 客户端技能 | `skills/core/mcp-client/SKILL.md` | MCP 客户端连接能力 |
| 应用配置 | `living-agent-app/src/main/resources/application.yml` | claude-cli 配置段 |

### 2.3 代码修改的完整生命周期

```text
1. 服务器端代码库镜像（.living/codebase/）
   ↓ CodebaseAccessService.readFile() / listDirectory()
2. 大脑分析代码，识别问题
   ↓ PatchProposalService.createProposal()
3. 补丁提案（.living/evolution/patches/）
   ↓ PatchApplicationService.applyPatch()
4. 补丁应用到代码库镜像
   ↓ 复制到 agent_workspace/
5. Claude CLI 在沙箱内执行修改
   ↓ ClaudeExecutionGateway.execute()
6. 修改结果返回
   ↓ ExecutionResult
7. 大脑验收 + 回执
   ↓ EmployeeExecutionReceipt
8. 修改同步回服务器端代码库
```

---

## 3. 兼容性评估

### 3.1 兼容性矩阵

| 维度 | 兼容性 | 说明 |
|---|---|---|
| MCP 协议 | ✅ 完全兼容 | CodeGraph 是 stdio MCP server，Claude CLI 是 MCP client；与 ANTHROPIC_BASE_URL 代理无关，互不干扰 |
| 模型代理 | ✅ 无冲突 | CodeGraph 不接触模型层，只提供代码索引；Claude CLI 仍走本项目代理 |
| 语言支持 | ✅ 完全匹配 | Java（Spring Boot）+ TypeScript + Rust + Python 全覆盖 |
| 容器内运行 | ⚠️ 需适配 | CodeGraph 标准流程假设开发者本地环境，容器内需解决安装、索引位置、文件监视器可靠性 |
| 与现有代码理解链路 | ⚠️ 需划界 | 与 SourceTreeIndexer / ArchitectureKnowledgeSeeder 服务对象不同，但需明确边界 |
| 运行时自主进化 | ⚠️ 需验证 | 大脑批量应用补丁时的高频文件变更可能触发频繁重索引 |
| License | ✅ MIT 可商用 | 无障碍 |

### 3.2 核心兼容性问题：代码库在服务器端，修改在客户端本地

本项目的代码修改链路有一个关键特征：

- **代码库权威来源在服务器端**：`.living/codebase/` 是代码库镜像，由 `CodebaseAccessService` 管理
- **修改发生在客户端本地**：Claude CLI 在 `agent_workspace/`（Docker 沙箱）中操作，这是从服务器端复制过去的副本
- **修改结果需同步回服务器端**：通过 `PatchApplicationService` 应用补丁

CodeGraph 的索引应该建在 **客户端本地的工作目录**（`agent_workspace/`），因为：
1. Claude CLI 直接操作的是这个目录
2. CodeGraph 的文件监视器需要监视 Claude CLI 实际修改的文件
3. 服务器端的代码库镜像是权威来源，不需要 CodeGraph 索引

但有一个问题：**每次从服务器端复制代码到客户端时，CodeGraph 索引需要重建或增量更新**。

---

## 4. 集成方案

### 4.1 分层原则

| 层级 | 服务对象 | 工具 | 用途 |
|---|---|---|---|
| **L1 大脑层** | 大脑/员工（Java 代码直接调用） | SourceTreeIndexer、ArchitectureKnowledgeSeeder、ErrorCodeMapper、CodebaseAccessService | 大脑决策时的代码结构理解、异常定位、受控访问 |
| **L2 Claude CLI 层** | Claude CLI（通过 MCP） | **CodeGraph** | Claude CLI 执行代码修改时的符号查询、影响分析 |
| **L3 文档层** | 大脑 + 员工 | documents/、docs/ | 职责卡、runbook、制度 |

**原则**：CodeGraph 不替代 L1，只增强 L2。大脑不直接调用 CodeGraph，Claude CLI 自己调用。

### 4.2 集成架构

```text
┌─────────────────────────────────────────────────────────────────┐
│  服务器端（living-agent-service 容器内）                          │
│                                                                   │
│  .living/codebase/          ← 代码库镜像（权威来源）              │
│                                                                   │
│  ClaudeExecutionGateway                                          │
│  ├── buildClaudeArgs()       ← 新增 --mcp-config 参数            │
│  └── buildEnvironment()      ← 新增 CODEGRAPH 相关环境变量       │
│                                                                   │
│  ClaudeCliProperties                                              │
│  └── codegraph 段            ← 新增 CodeGraph 配置项             │
└─────────────────────────────────────────────────────────────────┘
         │
         │ 代码库复制 + CodeGraph 索引
         ↓
┌─────────────────────────────────────────────────────────────────┐
│  客户端本地（Docker 沙箱内）                                      │
│                                                                   │
│  agent_workspace/           ← Claude CLI 工作目录                 │
│  ├── .codegraph/            ← CodeGraph 索引（本地 SQLite）      │
│  └── ...（从 .living/codebase/ 复制而来）                        │
│                                                                   │
│  Claude CLI 执行流程（增强后）：                                   │
│  1. claude -p "..." --add-dir ./agent_workspace                  │
│     --mcp-config /path/to/codegraph-mcp.json                     │
│  2. Claude CLI 自动连接 CodeGraph MCP server                     │
│  3. Claude CLI 通过 codegraph_explore 查询符号关系               │
│  4. Claude CLI 通过 codegraph_search 全文搜索                    │
│  5. Claude CLI 基于影响分析进行代码修改                           │
│  6. 修改结果返回服务器端                                          │
│                                                                   │
│  CodeGraph MCP Server（沙箱内进程）                               │
│  ├── codegraph serve --mcp    ← stdio MCP 服务器                 │
│  ├── 索引：agent_workspace/.codegraph/                           │
│  └── 文件监视：自动同步                                          │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 集成路径：CodeGraph → Claude CLI（推荐路径）

**仅走此路径**：CodeGraph 作为 Claude CLI 的 MCP 工具，不侵入本项目 Java 业务代码。

```text
员工任务 → ClaudeCliTool → ClaudeExecutionGateway → 沙箱内 claude -p
                                                          ↓
                                                    --mcp-config 注入
                                                          ↓
                                                    Claude CLI 自动调用
                                                          ↓
                                                    CodeGraph MCP server
                                                          ↓
                                                    代码符号查询/影响分析
```

**优点**：
- 零侵入本项目 Java 业务逻辑
- CodeGraph 的价值（减少 token / 工具调用）直接惠及 Claude CLI
- 与现有 SourceTreeIndexer 等无冲突

**缺点**：
- 需要解决容器内 CodeGraph 安装和配置持久化
- 大脑无法直接利用 CodeGraph 的符号图（只能通过 Claude CLI 间接利用）

---

## 5. 修改文件清单

### 5.1 服务器端修改（需持久化到代码库）

#### 5.1.1 `ClaudeCliProperties.java` — 新增 CodeGraph 配置段

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/sandbox/ClaudeCliProperties.java
// 修改类型：新增内部类 + 字段

public class ClaudeCliProperties {
    // ... 现有字段 ...
    private Codegraph codegraph = new Codegraph();  // 新增

    public Codegraph getCodegraph() { return codegraph; }
    public void setCodegraph(Codegraph codegraph) { this.codegraph = codegraph; }

    /**
     * CodeGraph 语义代码索引配置
     * CodeGraph 为 Claude CLI 提供符号关系图谱、影响分析和全文搜索能力
     * 索引建在客户端本地工作目录，不修改服务器端代码库镜像
     */
    public static class Codegraph {
        private boolean enabled = false;
        private String command = "codegraph";          // CodeGraph CLI 命令
        private String mcpConfigPath = "";             // MCP 配置文件路径（空则自动生成）
        private int watchDebounceMs = 5000;            // 文件监视防抖（适应批量补丁场景）
        private boolean autoSync = true;               // 是否自动同步索引
        private boolean indexOnCopy = true;            // 从服务器端复制代码后是否自动建索引
        private List<String> excludePatterns = List.of(  // 排除索引的模式
            ".env", "credentials", "secret", "*.key", "*.pem", "*.p12",
            "node_modules", ".git", "target", "build", "dist"
        );

        // getter/setter ...
    }
}
```

#### 5.1.2 `ClaudeExecutionGateway.java` — 注入 CodeGraph MCP 配置

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/sandbox/ClaudeExecutionGateway.java
// 修改类型：修改 buildClaudeArgs() 和 buildEnvironment()

// buildClaudeArgs() 中新增：
private List<String> buildClaudeArgs(String action, Map<String, Object> params) {
    List<String> args = new ArrayList<>();
    // ... 现有参数构造 ...

    // 新增：CodeGraph MCP 配置注入
    if (claudeCliProperties.getCodegraph().isEnabled()) {
        String mcpConfigPath = resolveCodegraphMcpConfig();
        if (mcpConfigPath != null && !mcpConfigPath.isBlank()) {
            args.add("--mcp-config");
            args.add(mcpConfigPath);
        }
    }

    return args;
}

// buildEnvironment() 中新增：
private Map<String, String> buildEnvironment(Map<String, Object> params) {
    Map<String, String> env = new HashMap<>();
    // ... 现有环境变量 ...

    // 新增：CodeGraph 环境变量
    if (claudeCliProperties.getCodegraph().isEnabled()) {
        ClaudeCliProperties.Codegraph cg = claudeCliProperties.getCodegraph();
        env.put("CODEGRAPH_WATCH_DEBOUNCE_MS", String.valueOf(cg.getWatchDebounceMs()));
        if (!cg.isAutoSync()) {
            env.put("CODEGRAPH_NO_DAEMON", "1");
        }
    }

    return env;
}

// 新增方法：解析 CodeGraph MCP 配置路径
private String resolveCodegraphMcpConfig() {
    ClaudeCliProperties.Codegraph cg = claudeCliProperties.getCodegraph();
    if (cg.getMcpConfigPath() != null && !cg.getMcpConfigPath().isBlank()) {
        return cg.getMcpConfigPath();
    }
    // 默认在工作目录下生成
    return claudeCliProperties.getWorkspace() + "/.codegraph/mcp-config.json";
}
```

#### 5.1.3 `application.yml` — 新增 CodeGraph 配置段

```yaml
# 文件：living-agent-app/src/main/resources/application.yml
# 修改类型：在 living-agent.claude-cli 段下新增

living-agent:
  claude-cli:
    # ... 现有配置 ...
    codegraph:
      enabled: ${CODEGRAPH_ENABLED:false}
      command: ${CODEGRAPH_COMMAND:codegraph}
      mcp-config-path: ${CODEGRAPH_MCP_CONFIG_PATH:}
      watch-debounce-ms: ${CODEGRAPH_WATCH_DEBOUNCE_MS:5000}
      auto-sync: ${CODEGRAPH_AUTO_SYNC:true}
      index-on-copy: ${CODEGRAPH_INDEX_ON_COPY:true}
      exclude-patterns:
        - .env
        - credentials
        - secret
        - "*.key"
        - "*.pem"
        - "*.p12"
        - node_modules
        - .git
        - target
        - build
        - dist
```

#### 5.1.4 `docker-compose.yml` — 新增 CodeGraph 环境变量

```yaml
# 文件：docker-compose.yml
# 修改类型：在 living-agent-service 环境变量中新增

services:
  living-agent-service:
    environment:
      # ... 现有环境变量 ...
      CODEGRAPH_ENABLED: ${CODEGRAPH_ENABLED:-false}
      CODEGRAPH_COMMAND: ${CODEGRAPH_COMMAND:-codegraph}
      CODEGRAPH_WATCH_DEBOUNCE_MS: ${CODEGRAPH_WATCH_DEBOUNCE_MS:-5000}
```

### 5.2 客户端本地修改（从服务器端复制过去，不修改服务器端代码库）

以下文件是在 Docker 沙箱内由 `ClaudeExecutionGateway` 自动生成的，不需要手动创建：

#### 5.2.1 `agent_workspace/.codegraph/mcp-config.json` — MCP 配置文件

```json
{
  "mcpServers": {
    "codegraph": {
      "command": "codegraph",
      "args": ["serve", "--mcp"],
      "cwd": "./agent_workspace"
    }
  }
}
```

> 此文件由 `ClaudeExecutionGateway.resolveCodegraphMcpConfig()` 在首次使用时自动生成到工作目录。
> 不需要保存在服务器端代码库中，因为它是运行时动态生成的。

#### 5.2.2 `agent_workspace/.codegraph/` — CodeGraph 索引目录

> 由 `codegraph init` 在工作目录中自动创建，包含 SQLite 数据库。
> 不需要保存在服务器端代码库中。
> 每次从服务器端复制新代码到 agent_workspace/ 后，需要 `codegraph sync` 增量更新索引。

### 5.3 Dockerfile 修改（需持久化到代码库）

#### 5.3.1 `living-agent-app/Dockerfile` — 预装 CodeGraph

```dockerfile
# 文件：living-agent-app/Dockerfile
# 修改类型：新增 CodeGraph 安装步骤

# 在现有构建步骤后新增：
ARG CODEGRAPH_ENABLED=false
RUN if [ "$CODEGRAPH_ENABLED" = "true" ]; then \
      curl -fsSL https://raw.githubusercontent.com/colbymchenry/codegraph/main/install.sh | sh; \
    fi
```

### 5.4 修改文件汇总

| 文件 | 修改类型 | 保存位置 | 说明 |
|---|---|---|---|
| `ClaudeCliProperties.java` | 新增 `Codegraph` 内部类 | 服务器端代码库 | CodeGraph 配置定义 |
| `ClaudeExecutionGateway.java` | 修改 `buildClaudeArgs()` + `buildEnvironment()` | 服务器端代码库 | 注入 MCP 配置和环境变量 |
| `application.yml` | 新增 `codegraph` 配置段 | 服务器端代码库 | 运行时配置 |
| `docker-compose.yml` | 新增环境变量 | 服务器端代码库 | 容器编排配置 |
| `Dockerfile` | 新增 CodeGraph 安装步骤 | 服务器端代码库 | 构建镜像 |
| `agent_workspace/.codegraph/mcp-config.json` | 运行时自动生成 | 客户端本地（不保存到服务器） | MCP 连接配置 |
| `agent_workspace/.codegraph/` 索引数据 | 运行时自动生成 | 客户端本地（不保存到服务器） | SQLite 索引 |

---

## 6. 关键风险与应对

### 6.1 容器内 CodeGraph 安装与配置持久化

**问题**：Docker 容器重建后 CodeGraph 安装和索引丢失。

**应对**：
- Dockerfile 中预装 CodeGraph（条件安装，`CODEGRAPH_ENABLED=true` 时才安装）
- 索引数据不持久化，每次容器启动时从服务器端代码库镜像重建
- MCP 配置文件由 `ClaudeExecutionGateway` 运行时自动生成

### 6.2 代码库从服务器端复制到客户端后的索引时机

**问题**：代码库从 `.living/codebase/` 复制到 `agent_workspace/` 后，CodeGraph 索引需要更新。

**应对**：
- `ClaudeCliProperties.Codegraph.indexOnCopy = true`：复制后自动执行 `codegraph sync`
- 在 `ClaudeExecutionGateway` 创建 SandboxSession 时，检查并触发索引更新
- 文件监视器（inotify）在沙箱内运行，实时捕获 Claude CLI 的修改

### 6.3 文件监视器在容器内的可靠性

**问题**：如果 `agent_workspace/` 是 bind mount（宿主机目录挂载），宿主机修改 → 容器内 inotify 可能不触发。

**应对**：
- 优先使用 named volume 而非 bind mount
- 在 `PatchApplicationService` 应用补丁后，主动调用 `codegraph sync`
- 设置 `CODEGRAPH_WATCH_DEBOUNCE_MS=5000` 适应批量补丁场景

### 6.4 与现有代码理解链路的功能边界

**问题**：本项目已有 `SourceTreeIndexer`、`ArchitectureKnowledgeSeeder`、`ErrorCodeMapper`，CodeGraph 与它们的边界需明确。

**应对**（分层清晰）：

| 层级 | 服务对象 | 工具 | 用途 |
|---|---|---|---|
| L1 大脑层 | 大脑/员工（Java 代码直接调用） | SourceTreeIndexer、ArchitectureKnowledgeSeeder、ErrorCodeMapper | 大脑决策时的代码结构理解 |
| L2 Claude CLI 层 | Claude CLI（通过 MCP） | **CodeGraph** | Claude CLI 执行代码修改时的符号查询 |
| L3 文档层 | 大脑 + 员工 | documents/、docs/ | 职责卡、runbook、制度 |

**原则**：CodeGraph 不替代 L1，只增强 L2。

### 6.5 运行时自主进化的高频变更

**问题**：大脑通过 `PatchApplicationService` 批量应用补丁时，可能短时间内产生大量文件变更，触发 CodeGraph 频繁重索引。

**应对**：
- `CODEGRAPH_WATCH_DEBOUNCE_MS=5000`（默认 2s 太激进，5s 适应批量补丁）
- 补丁批量应用前后，通过 `codegraph sync` 手动控制索引时机
- 监控 CodeGraph 索引耗时，必要时禁用文件监视器（`CODEGRAPH_NO_DAEMON=1`），改为显式触发

### 6.6 不索引 living-agent-service 自身源码

**问题**：如果 CodeGraph 索引了 living-agent-service 自身源码，大脑可能通过 Claude CLI 修改自身代码，造成循环依赖。

**应对**：
- CodeGraph 只索引 `agent_workspace/`（从服务器端代码库镜像复制过来的外部项目代码）
- 不索引 living-agent-service 自身源码
- `excludePatterns` 中排除 `.living/`、`target/`、`node_modules/` 等

---

## 7. 分阶段实施计划

### P0 — 开发期工具（立即可用，零风险）

**目标**：开发者在本地使用 CodeGraph 维护本项目代码。

**操作**：
1. 开发者本地安装 CodeGraph：`irm https://raw.githubusercontent.com/colbymchenry/codegraph/main/install.ps1 | iex`
2. 在项目根目录执行：`codegraph init`
3. 连接 Claude Code/Cursor：`codegraph install`

**效果**：
- 开发者维护本项目代码时，直接享受 16% 成本下降 / 58% 工具调用减少
- 不涉及容器化，不涉及运行时集成
- 零代码修改

**验证标准**：
- `codegraph status` 显示索引正常
- Claude Code 回答架构问题时，工具调用次数明显减少

### P1 — 容器内 Claude CLI 增强（中等成本，需验证）

**目标**：让容器内的 Claude CLI 自动使用 CodeGraph 进行代码修改。

**操作**：
1. 修改 `ClaudeCliProperties.java`：新增 `Codegraph` 内部类
2. 修改 `ClaudeExecutionGateway.java`：注入 `--mcp-config` 和环境变量
3. 修改 `application.yml`：新增 `codegraph` 配置段
4. 修改 `docker-compose.yml`：新增环境变量
5. 修改 `Dockerfile`：条件安装 CodeGraph
6. 在测试环境验证：容器内 inotify 可靠性、索引性能、与代理的兼容性

**验证标准**：
- 容器内 `claude -p "分析 TechBrain 的调用链" --mcp-config ...` 能正确调用 CodeGraph
- Claude CLI 执行代码修改时，工具调用次数减少 40%+
- 补丁应用后 `codegraph sync` 正确更新索引
- 不影响现有 Claude CLI 代理链路

### P2 — 长期演进（可选，非必需）

**目标**：评估大脑直接调用 CodeGraph 的价值。

**可能方向**：
- 用 CodeGraph 符号图增强 `ErrorCodeMapper`（异常→调用链→根因）
- 用 CodeGraph 实现职责卡与代码一致性自动校验（解决 BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md 的 P2 待办）
- 用 CodeGraph 影响分析增强 `PatchProposalService`（补丁提案前评估影响半径）

**前提**：P1 验证通过，且明确大脑直接调用 CodeGraph 有超出 Claude CLI 间接调用的额外收益。

---

## 8. 明确不做的事

- ❌ 不要用 CodeGraph 替代 `SourceTreeIndexer`（服务对象不同：L1 vs L2）
- ❌ 不要让大脑直接调用 CodeGraph（除非 P2 验证有明确收益）
- ❌ 不要在主对话链路同步调用 CodeGraph（Claude CLI 自己异步调用即可）
- ❌ 不要索引 living-agent-service 自身源码（避免大脑自我修改时的循环依赖）
- ❌ 不要把 CodeGraph 索引数据保存到服务器端代码库（索引是客户端本地的运行时产物）
- ❌ 不要修改 `CodebaseAccessService` / `PatchApplicationService` 等服务器端代码理解链路（L1 层不变）

---

## 9. 验证清单

### P0 验证

- [ ] 开发者本地 `codegraph init` 成功
- [ ] `codegraph status` 显示索引正常
- [ ] Claude Code 回答架构问题时工具调用减少

### P1 验证

- [ ] Dockerfile 条件安装 CodeGraph 成功
- [ ] 容器内 `codegraph init agent_workspace/` 成功
- [ ] `ClaudeExecutionGateway` 自动生成 `mcp-config.json`
- [ ] Claude CLI 通过 `--mcp-config` 加载 CodeGraph 后正常工作
- [ ] Claude CLI 执行代码修改时使用 `codegraph_explore` 查询
- [ ] 补丁应用后 `codegraph sync` 正确更新索引
- [ ] 批量补丁场景下 CodeGraph 不阻塞主流程
- [ ] `CODEGRAPH_ENABLED=false` 时完全不影响现有行为

### P2 验证（如实施）

- [ ] 大脑直接调用 CodeGraph 有超出 Claude CLI 间接调用的额外收益
- [ ] `ErrorCodeMapper` 增强后异常定位准确率提升
- [ ] 职责卡与代码一致性自动校验可行

---

## 10. 与其他文档的关系

| 文档 | 关系 |
|---|---|
| `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md` | 代码结构与文件功能索引，CodeGraph 修改的文件均在此文档中有记录 |
| `docs/MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md` | 模型职责与执行闭环，CodeGraph 优化的是 Claude CLI 的代码理解效率 |
| `docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md` | 大脑与员工规范，CodeGraph 不改变 L1 层的规范链 |
| `docs/WINDOWS_MCP_INTEGRATION_PLAN.md` | MCP 集成计划，CodeGraph 是 MCP server 的一种具体实现 |
| `documents/shared/company/fixed-employee-*.md` | 固定员工 Prompt/runbook，CodeGraph 不修改员工规范 |
