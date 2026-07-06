# Claude Code 完整集成改进方案（MCP Server + 插件 + 技能）

> **目标**：让 Docker 内安装的 Claude Code CLI（`@anthropic-ai/claude-code@2.1.140`）完整对接项目服务、MCP Server、插件和技能体系，实现数字员工通过 Claude Code 执行编码任务的完整闭环。
>
> **核心设计**：Claude Code 是真正的执行引擎，`proxy/anthropic` 是 API 代理拦截层，通过环境变量 `ANTHROPIC_BASE_URL` 让 Claude Code 的 API 请求路由到模型池。
>
> **配合文档**：
> - `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md` — 代码结构与文件功能（权威源）
> - `docs/CODEGRAPH_INTEGRATION_IMPROVEMENT_PLAN.md` — CodeGraph 集成方案
> - `living-agent-skill/SKILL_MAPPING.md` — 技能分类与部门大脑映射
> - `living-agent-skill/target/classes/skills/SKILL_INDEX.json` — 技能索引（v1.8.0, 71个技能）
>
> **更新时间**：2026-07-02

---

## 一、现状分析

### 1.1 当前已实现

| 能力 | 代码位置 | 状态 |
|------|---------|------|
| Claude Code CLI 安装 | `Dockerfile.local#L58-62` (`npm install -g @anthropic-ai/claude-code@2.1.140`) | ✅ |
| API 代理拦截 | `proxy/anthropic/ClaudeProxyController.java` (`/api/v1/proxy/anthropic/v1/messages`) | ✅ |
| 模型池路由 | `proxy/anthropic/ClaudeProxyModelRouter.java` (employeeId → brainId → Provider) | ✅ |
| 虚拟模型映射 | `ClaudeCliProperties.Proxy.virtualModelMapping` (claude-sonnet-4 → balanced) | ✅ |
| 格式转换 | `proxy/anthropic/converter/AnthropicToOpenAiConverter.java` | ✅ |
| SSE 流式转换 | `proxy/anthropic/sse/OpenAiStreamChunkParser.java` + `AnthropicSseBuilder.java` | ✅ |
| 审计日志 | `proxy/anthropic/ClaudeProxyAuditService.java` | ✅ |
| 沙箱执行 | `sandbox/ClaudeExecutionGateway.java` (buildClaudeArgs + buildEnvironment) | ✅ |
| 环境变量注入 | `buildEnvironment()`: ANTHROPIC_API_KEY/BASE_URL/API_URL | ✅ |
| 命令行参数 | `buildClaudeArgs()`: -p/--model/--output-format/--add-dir/--append-system-prompt/--max-turns/--settings | ✅ |
| 数字员工调用 | `tool/impl/ClaudeCliTool.java` (作为 Brain 的外部工具) | ✅ |
| Tech 提示词 | `brain/impl/TechClaudeCliPromptTemplates.java` (代码审查/开发专用提示词) | ✅ |

### 1.2 当前缺失

| 能力 | 缺失内容 | 影响 |
|------|---------|------|
| **MCP Server 配置** | 无 `.mcp.json`，`buildClaudeArgs()` 缺少 `--mcp-config` 参数 | Claude Code 无法使用 MCP 工具 |
| **插件安装** | 无 `.claude/plugins/` 目录 | 无法使用 /commit, /code-review 等命令 |
| **技能映射** | SKILL_INDEX.json 71个技能未映射到 Claude Code | 技能只能通过 Java Tool 使用 |
| **CodeGraph** | 仅在文档中规划，未编译安装 | 无法提供代码语义图谱（减少 47% tokens） |
| **MemPalace MCP** | 代码已有但默认关闭，缺 @PreDestroy | 跨会话记忆不可用 |
| **Docker 服务对接** | Claude Code 无法直接发现 docker-compose 服务 | 需通过 Shell + curl 间接访问 |

### 1.3 现有 Docker Compose 服务清单

| 服务 | 容器名 | 端口 | Claude Code 可对接方式 |
|------|-------|------|----------------------|
| living-agent-service | living-agent-service | 8382 | ✅ ANTHROPIC_BASE_URL 已对接 |
| postgres | living-agent-postgres | 5432 | ✅ Shell: `psql -h postgres -U livingagent` |
| redis | living-agent-redis | 6379 | ✅ Shell: `redis-cli -h redis` |
| qdrant | living-agent-qdrant | 6333 | ✅ Shell: `curl http://qdrant:6333/collections` |
| kafka | living-agent-kafka | 9092 | ⚠️ 间接（通过 Java 服务中转） |
| memos (MemOS) | living-agent-memos | 8381 | ✅ Shell: `curl http://memos:8381/openapi.json` |
| memos-neo4j | living-agent-memos-neo4j | 7687 | ⚠️ 间接（通过 memos 服务） |
| jenkins | living-agent-jenkins | 8080 | ✅ Shell: `curl http://jenkins:8080/api/json` |
| gitlab | living-agent-gitlab | 8929 | ✅ Shell: `git remote add origin http://gitlab:8929/...` |
| openproject | living-agent-openproject | 8080 | ✅ Shell: `curl http://openproject:8080/api/v3/projects` |
| ruview-sensing | living-agent-ruview-sensing | 3000 | ✅ Shell: `curl http://ruview-sensing:3000/health` |

---

## 二、改进方案

### 2.1 Phase 1: MCP Server 配置（P0）

#### 2.1.1 新建 `.mcp.json` 配置文件

**文件位置**：`living-agent-app/src/main/resources/claude/mcp.json`

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/app/workspace", "/app/agent_workspace"],
      "env": {}
    },
    "memory": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-memory"],
      "env": {}
    },
    "sequential-thinking": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-sequential-thinking"],
      "env": {}
    }
  }
}
```

**说明**：
- `filesystem`：受限文件系统访问，限制在 `/app/workspace` 和 `/app/agent_workspace`
- `memory`：简单键值记忆，跨工具调用保持上下文
- `sequential-thinking`：复杂推理链，支持多步思考

**暂不纳入的 MCP Server**（需额外工作）：

| MCP Server | 原因 | 计划 |
|-----------|------|------|
| CodeGraph | 需 Rust 编译 | Phase 2 |
| MemPalace | 缺少 @PreDestroy，默认关闭 | Phase 2（先修复生命周期） |
| git | Claude Code 内置 Git 能力已足够 | 不需要 |

#### 2.1.2 修改 `ClaudeCliProperties.java`

在 `ClaudeCliProperties` 中新增 MCP 配置属性：

```java
// 新增字段
private String mcpConfigPath = "classpath:claude/mcp.json";
private boolean mcpEnabled = true;

// getter/setter
public String getMcpConfigPath() { return mcpConfigPath; }
public void setMcpConfigPath(String mcpConfigPath) { this.mcpConfigPath = mcpConfigPath; }
public boolean isMcpEnabled() { return mcpEnabled; }
public void setMcpEnabled(boolean mcpEnabled) { this.mcpEnabled = mcpEnabled; }
```

#### 2.1.3 修改 `ClaudeExecutionGateway.buildClaudeArgs()`

在现有 `buildClaudeArgs()` 方法末尾（`return args;` 之前）新增 `--mcp-config` 参数注入：

```java
// MCP Server 配置
if (claudeCliProperties.isMcpEnabled()) {
    String mcpConfigPath = stringValue(params.get("mcp_config_path"), 
        claudeCliProperties.getMcpConfigPath());
    if (mcpConfigPath != null && !mcpConfigPath.isBlank()) {
        // classpath: 前缀需要解析为实际文件路径
        if (mcpConfigPath.startsWith("classpath:")) {
            String resourcePath = mcpConfigPath.substring("classpath:".length());
            var resource = getClass().getClassLoader().getResource(resourcePath);
            if (resource != null) {
                args.add("--mcp-config");
                args.add(resource.getPath());
            } else {
                log.warn("MCP config resource not found: {}", mcpConfigPath);
            }
        } else {
            args.add("--mcp-config");
            args.add(mcpConfigPath);
        }
    }
}
```

#### 2.1.4 修改 `application.yml` 配置

```yaml
living-agent:
  claude-cli:
    # ... 现有配置 ...
    mcp-enabled: true
    mcp-config-path: classpath:claude/mcp.json
```

#### 2.1.5 修改 `docker-compose.yml`

新增环境变量：

```yaml
environment:
  # ... 现有配置 ...
  - CLAUDE_CLI_MCP_ENABLED=${CLAUDE_CLI_MCP_ENABLED:-true}
  - CLAUDE_CLI_MCP_CONFIG_PATH=/app/config/claude/mcp.json
```

新增 volume 挂载：

```yaml
volumes:
  # ... 现有配置 ...
  - ./living-agent-app/src/main/resources/claude/mcp.json:/app/config/claude/mcp.json:ro
```

#### 2.1.6 修改 `Dockerfile.local`

在 `npm install -g @anthropic-ai/claude-code@2.1.140` 之后新增 MCP Server npm 包：

```dockerfile
# 安装 MCP Server npm 包
RUN npm install -g \
    @modelcontextprotocol/server-filesystem \
    @modelcontextprotocol/server-memory \
    @modelcontextprotocol/server-sequential-thinking
```

---

### 2.2 Phase 2: 增强能力（P1）

#### 2.2.1 CodeGraph MCP Server 安装

**前置条件**：需在宿主机编译 Rust 二进制，然后 COPY 到 Docker 镜像。

```dockerfile
# Dockerfile.local 中新增
# CodeGraph MCP Server（预编译二进制）
COPY image/codegraph /usr/local/bin/codegraph
RUN chmod +x /usr/local/bin/codegraph
```

**`.mcp.json` 新增**：

```json
{
  "codegraph": {
    "command": "codegraph",
    "args": ["serve", "--mcp"],
    "cwd": "/app/workspace"
  }
}
```

**效果**：代码语义图谱，平均减少 47% tokens / 22% 更快 / 58% 更少工具调用。

#### 2.2.2 MemPalace MCP 修复

**修复 `MemPalaceBackend.java`**：补充 `@PreDestroy` 生命周期管理

```java
@PreDestroy
public void shutdown() {
    if (mcpProcess != null && mcpProcess.isAlive()) {
        mcpProcess.destroyForcibly();
        log.info("MemPalace MCP process forcibly destroyed");
    }
    if (mcpWatcher != null) {
        mcpWatcher.interrupt();
    }
}
```

**`.mcp.json` 新增**：

```json
{
  "mempalace": {
    "command": "python3",
    "args": ["-m", "mempalace.mcp_server"],
    "env": {
      "MEMPALACE_PALACE_PATH": "/app/data/palace"
    }
  }
}
```

#### 2.2.3 插件安装

**文件位置**：`living-agent-app/src/main/resources/claude/plugins/`

从 `f:\SoarCloudAI\docker\claude\claude-code-v2.1.140\plugins\` 复制关键插件：

| 插件 | 来源路径 | 目标路径 | 用途 |
|------|---------|---------|------|
| commit-commands | `plugins/commit-commands/` | `claude/plugins/commit-commands/` | Git 工作流（/commit, /commit-push-pr） |
| code-review | `plugins/code-review/` | `claude/plugins/code-review/` | 自动化代码审查（/code-review） |
| feature-dev | `plugins/feature-dev/` | `claude/plugins/feature-dev/` | 结构化特性开发 |
| security-guidance | `plugins/security-guidance/` | `claude/plugins/security-guidance/` | 安全提醒钩子 |

**Dockerfile.local 新增**：

```dockerfile
# Claude Code 插件
COPY src/main/resources/claude/plugins /home/livingagent/.claude/plugins
```

**ClaudeExecutionGateway.buildEnvironment() 新增**：

```java
env.put("CLAUDE_PLUGINS_DIR", "/home/livingagent/.claude/plugins");
```

---

### 2.3 Phase 3: 技能对接（P2）

#### 2.3.1 技能映射策略

项目 SKILL_INDEX.json 有 71 个技能，不可能全部映射到 Claude Code。采用**按部门动态映射**策略：

```
Claude Code 使用的技能 = 核心技能(共享) + 当前部门技能(动态)
```

#### 2.3.2 技能格式转换

项目技能格式（`SKILL.md`）与 Claude Code 技能格式不同：

| 项目 SKILL.md | Claude Code SKILL.md |
|--------------|---------------------|
| 无 frontmatter | 需要 `---` frontmatter（name/description/triggers） |
| 自由格式 Markdown | 结构化 Markdown |
| 通过 Java Tool 执行 | 通过 Claude Code 内置工具执行 |

**转换规则**：

```markdown
---
name: {skill_name}
description: {skill_description}
triggers: [{trigger_list}]
---

{原 SKILL.md 内容}
```

#### 2.3.3 动态技能目录映射

在 `ClaudeExecutionGateway.buildClaudeArgs()` 中根据当前部门动态注入技能目录：

```java
// 按部门注入技能目录
String department = stringValue(params.get("department"), 
    claudeCliProperties.getProxy().getDefaultDepartmentId());
String skillsDir = "/app/skills/core";  // 核心技能（必注入）
args.add("--add-dir");
args.add(skillsDir);
String deptSkillsDir = "/app/skills/" + department;  // 部门技能
args.add("--add-dir");
args.add(deptSkillsDir);
```

**映射关系**：

| 部门 | 技能目录 | 技能数 | 关键技能 |
|------|---------|-------|---------|
| 核心 | `/app/skills/core/` | 10 | tavily-search, find-skills, mcp-client, knowledge-graph |
| tech | `/app/skills/tech/` | 25 | coding-agent, code-review, architecture, docker-expert, mcp-builder |
| admin | `/app/skills/admin/` | 15 | docx-official, xlsx-official, copywriting, summarize |
| ops | `/app/skills/ops/` | 9 | cron, inngest, data-aggregator, system-health-diagnoser |
| finance | `/app/skills/finance/` | 4 | billing-automation, finance-api-gateway, budget-management |
| sales | `/app/skills/sales/` | 4 | crm-integration, sales-automation, seo-audit |
| hr | `/app/skills/hr/` | 3 | hr-pro, recruitment-automation, performance-management |
| cs | `/app/skills/cs/` | 3 | customer-support, ticket-system-integration |
| legal | `/app/skills/legal/` | 3 | legal-advisor, contract-management, compliance-check |

**注意**：技能目录已通过 `docker-compose.yml` 挂载到 `/app/skills`：
```yaml
- ./living-agent-skill/src/main/resources/skills:/app/skills:ro
```

#### 2.3.4 系统提示词注入

在 `buildClaudeArgs()` 的 `--append-system-prompt` 中注入服务发现信息：

```java
StringBuilder systemPrompt = new StringBuilder();
systemPrompt.append("你是 Living Agent Service 的数字员工，通过 Claude Code CLI 执行任务。\n\n");
systemPrompt.append("## 可用服务\n");
systemPrompt.append("- PostgreSQL: psql -h postgres -U livingagent -d livingagent\n");
systemPrompt.append("- Redis: redis-cli -h redis\n");
systemPrompt.append("- Qdrant: curl http://qdrant:6333/collections\n");
systemPrompt.append("- Jenkins: curl http://jenkins:8080/api/json\n");
systemPrompt.append("- GitLab: git remote add origin http://gitlab:8929/...\n");
systemPrompt.append("- OpenProject: curl http://openproject:8080/api/v3/projects\n");
systemPrompt.append("- MemOS: curl http://memos:8381/openapi.json\n");
systemPrompt.append("- RuView: curl http://ruview-sensing:3000/health\n\n");
systemPrompt.append("## 当前部门技能\n");
systemPrompt.append("技能目录: /app/skills/ 和 /app/skills/{department}/\n");
systemPrompt.append("参考 SKILL.md 文件获取技能详细说明。\n");
```

---

### 2.4 Phase 4: 服务对接增强（P2）

#### 2.4.1 GitLab 集成

Claude Code 内置 Git 能力，可直接对接 docker-compose 中的 GitLab：

```java
// buildEnvironment() 中注入 GitLab 配置
env.put("GITLAB_URL", "http://gitlab:8929");
env.put("GITLAB_TOKEN", gitlabAccessToken);  // 从配置读取
```

Claude Code 可直接使用：
```bash
git clone http://gitlab:8929/group/project.git
git add . && git commit -m "feat: ..." && git push
```

#### 2.4.2 Jenkins CI/CD

通过 Shell 命令触发 Jenkins 构建：

```java
env.put("JENKINS_URL", "http://jenkins:8080");
env.put("JENKINS_TOKEN", jenkinsApiToken);
```

#### 2.4.3 项目管理（OpenProject）

通过 REST API 操作项目：

```java
env.put("OPENPROJECT_URL", "http://openproject:8080");
env.put("OPENPROJECT_TOKEN", openprojectApiToken);
```

---

## 三、代码修改清单

### 3.1 新增文件

| 文件 | 模块 | 说明 |
|------|------|------|
| `living-agent-app/src/main/resources/claude/mcp.json` | app | MCP Server 配置文件 |
| `living-agent-app/src/main/resources/claude/plugins/commit-commands/` | app | Git 工作流插件 |
| `living-agent-app/src/main/resources/claude/plugins/code-review/` | app | 代码审查插件 |
| `living-agent-app/src/main/resources/claude/plugins/feature-dev/` | app | 特性开发插件 |
| `living-agent-app/src/main/resources/claude/plugins/security-guidance/` | app | 安全提醒插件 |

### 3.2 修改文件

| 文件 | 模块 | 修改内容 |
|------|------|---------|
| `ClaudeCliProperties.java` | core/sandbox | 新增 mcpConfigPath/mcpEnabled 字段 |
| `ClaudeExecutionGateway.java` | core/sandbox | buildClaudeArgs() 新增 --mcp-config 注入；buildEnvironment() 新增 CLAUDE_PLUGINS_DIR/GITLAB_URL 等 |
| `Dockerfile.local` | app | 新增 MCP Server npm 包安装 + 插件 COPY |
| `docker-compose.yml` | infra | 新增 CLAUDE_CLI_MCP_ENABLED/MCP_CONFIG_PATH 环境变量 + mcp.json volume 挂载 |
| `application.yml` | app | 新增 mcp-enabled/mcp-config-path 配置项 |
| `MemPalaceBackend.java` | core/memory | 补充 @PreDestroy 生命周期管理 |

### 3.3 不修改的文件

| 文件 | 原因 |
|------|------|
| `ClaudeProxyService.java` | 代理主流程不变，MCP 在 Claude Code 端使用 |
| `ClaudeProxyModelRouter.java` | 模型路由逻辑不变 |
| `AnthropicToOpenAiConverter.java` | 格式转换逻辑不变 |
| `ClaudeProxyController.java` | API 端点不变 |
| `ClaudeCliTool.java` | 工具接口不变 |
| `TechClaudeCliPromptTemplates.java` | 提示词模板不变 |

---

## 四、冲突分析

### 4.1 与现有代码的兼容性

| 检查项 | 结论 | 说明 |
|--------|------|------|
| ClaudeCliProperties 新增字段 | ✅ 无冲突 | 新增字段有默认值，不影响现有配置 |
| buildClaudeArgs() 新增参数 | ✅ 无冲突 | `--mcp-config` 是追加参数，不影响现有参数 |
| buildEnvironment() 新增变量 | ✅ 无冲突 | 新增环境变量不影响现有变量 |
| Dockerfile.local 新增安装 | ✅ 无冲突 | npm install 追加包，不影响现有包 |
| docker-compose.yml 新增变量 | ✅ 无冲突 | 新增环境变量有默认值 |
| SKILL.md 格式 | ✅ 无冲突 | 只需在现有文件头部添加 frontmatter |
| MemPalaceBackend @PreDestroy | ✅ 无冲突 | 补充缺失的生命周期管理 |

### 4.2 与 CODE_STRUCTURE_AND_FILE_GUIDE.md 的一致性

| 检查项 | 结论 | 说明 |
|--------|------|------|
| sandbox 包结构 | ✅ 一致 | 新增修改在 ClaudeCliProperties 和 ClaudeExecutionGateway |
| proxy/anthropic 包结构 | ✅ 不变 | Phase 1-3 不修改代理层代码 |
| tool 包结构 | ✅ 不变 | ClaudeCliTool 接口不变 |
| memory 包结构 | ✅ 一致 | 仅补充 @PreDestroy |

---

## 五、实施路线图

### 5.1 Phase 1: MCP Server 配置（P0）

**步骤**：

```
1. 新建 claude/mcp.json 配置文件
2. 修改 ClaudeCliProperties.java（新增 mcpConfigPath/mcpEnabled）
3. 修改 ClaudeExecutionGateway.java（buildClaudeArgs 新增 --mcp-config）
4. 修改 application.yml（新增配置项）
5. 修改 Dockerfile.local（新增 MCP Server npm 包）
6. 修改 docker-compose.yml（新增环境变量 + volume）
7. 编译验证
8. 更新 CODE_STRUCTURE_AND_FILE_GUIDE.md
```

### 5.2 Phase 2: 增强能力（P1）

**步骤**：

```
1. 编译 CodeGraph Rust 二进制
2. Dockerfile.local 新增 CodeGraph COPY
3. mcp.json 新增 codegraph 配置
4. 修复 MemPalaceBackend @PreDestroy
5. mcp.json 新增 mempalace 配置（可选）
6. 复制 Claude Code 插件到 claude/plugins/
7. Dockerfile.local 新增插件 COPY
8. buildEnvironment() 新增 CLAUDE_PLUGINS_DIR
9. 编译验证
```

### 5.3 Phase 3: 技能对接（P2）

**步骤**：

```
1. 为 SKILL.md 文件添加 frontmatter（批量脚本）
2. buildClaudeArgs() 新增动态技能目录注入
3. --append-system-prompt 新增服务发现信息
4. 验证各部门技能可被 Claude Code 发现和使用
```

### 5.4 Phase 4: 服务对接增强（P2）

**步骤**：

```
1. buildEnvironment() 新增 GITLAB_URL/JENKINS_URL/OPENPROJECT_URL
2. 验证 Claude Code 可通过 Shell 命令操作各服务
3. 端到端测试：数字员工 → Claude Code → GitLab commit → Jenkins build
```

---

## 六、验收标准

### 6.1 Phase 1 验收

| 序号 | 验收项 | 验证方法 |
|------|-------|---------|
| 1 | Claude Code 启动时加载 MCP 配置 | 日志中可见 "Loading MCP servers: filesystem, memory, sequential-thinking" |
| 2 | filesystem MCP 可用 | Claude Code 可读写 /app/workspace 和 /app/agent_workspace |
| 3 | memory MCP 可用 | Claude Code 可存储和检索键值对 |
| 4 | sequential-thinking MCP 可用 | Claude Code 可使用多步推理 |
| 5 | mcp-enabled=false 时跳过 MCP | 不注入 --mcp-config 参数 |

### 6.2 Phase 2 验收

| 序号 | 验收项 | 验证方法 |
|------|-------|---------|
| 1 | CodeGraph MCP 可用 | `codegraph serve --mcp` 启动成功 |
| 2 | MemPalace 优雅关闭 | 停止服务时日志可见 "MemPalace MCP process forcibly destroyed" |
| 3 | /commit 命令可用 | Claude Code 中输入 `/commit` 可执行 Git 提交 |
| 4 | /code-review 命令可用 | Claude Code 中输入 `/code-review` 可执行代码审查 |

### 6.3 Phase 3 验收

| 序号 | 验收项 | 验证方法 |
|------|-------|---------|
| 1 | 核心技能可发现 | Claude Code 可读取 /app/skills/core/ 下的 SKILL.md |
| 2 | 部门技能动态注入 | 不同部门的数字员工看到不同的技能目录 |
| 3 | 服务发现信息在系统提示词中 | Claude Code 知道 GitLab/Jenkins 等服务地址 |

### 6.4 Phase 4 验收

| 序号 | 验收项 | 验证方法 |
|------|-------|---------|
| 1 | GitLab 操作 | Claude Code 可 clone/commit/push 到 GitLab |
| 2 | Jenkins 触发 | Claude Code 可通过 curl 触发 Jenkins 构建 |
| 3 | 端到端 | 数字员工 → Claude Code → 代码修改 → GitLab commit → Jenkins build |

---

## 七、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| MCP Server npm 包在国内镜像不可用 | Phase 1 阻塞 | 已配置 npmmirror.com 镜像源 |
| CodeGraph Rust 编译失败 | Phase 2 延迟 | 可在宿主机预编译二进制，直接 COPY |
| MemPalace MCP 子进程泄漏 | 内存泄漏 | 修复 @PreDestroy + 添加守护线程 |
| Claude Code 插件与代理模型不兼容 | 功能降级 | 逐步测试每个插件，不兼容的标记为可选 |
| SKILL.md frontmatter 批量修改 | 工作量大 | 编写 Python 脚本自动转换 |
| Docker 镜像体积增大 | 构建变慢 | MCP Server npm 包约 50MB，可接受 |

---

## 八、闭环关联

本改进方案与 IMPROVEMENT_PLAN 体系的关联：

| 改进方案编号 | 关联闭环 | 关系 |
|------------|---------|------|
| P22-A | 闭环22（Claude CLI代理闭环） | CLI可用性检查增强 → Phase 1 |
| P22-B | 闭环22 | 调用结果验证增强 → Phase 1 |
| P22-C | 闭环22 | 输出解析验证增强 → Phase 2 |
| 16.3 | InterventionDecisionEngine | Claude Code 操作经过干预决策 → 已完成 |
| 16.4 | BrainBoundaryEnforcer | Claude Code 操作受边界约束 → 已完成 |
| 闭环33 | 数字员工使用Claude CLI工具闭环 | 员工任务→Claude Code→工具调用→结果解析→回执提交 → Phase 3 |

---

*文档版本: v1.0*
*生成日期: 2026-07-02*
*下次审查: Phase 1 完成后*
