# 工具技能模块

> 版本：2026-05-27 | 路径：living-agent-core/tool/, skill/

## 工具分类

| 类别 | 工具 | 说明 |
|------|------|------|
| 源码编辑 | **FileEditTool** | 工作区文件读写、目录列表、代码搜索 |
| 构建部署 | **BuildTool** | 编译、打包、重启服务 |
| 搜索 | TavilySearchTool, SearXNGTool, WebCrawlerTool | 网络搜索爬取 |
| 文档 | PdfTool, OfficeTool | PDF/Office 处理 |
| 浏览器 | BrowserAutomationTool, PlaywrightCrawlerTool | 浏览器自动化 |
| 代码代理 | ClaudeCliTool, TraeTool | CLI 集成代码生成/审查/调试 |
| 飞书 | FeishuTool, EnterpriseFeishuTool, EmployeeFeishuTool, HrFeishuTool | 飞书集成（按部门/角色拆分） |
| 钉钉 | DingTalkTool | 钉钉集成 |
| GitLab | GitLabTool | 代码托管 |
| 容器 | DockerTool | Docker 容器管理 |
| 桌面自动化 | WindowsAppTool | Windows 桌面应用自动化 |

## Tool 接口

```java
// Tool.java
public interface Tool {
    String getName();
    String getDescription();
    String getVersion();
    String getDepartment();
    ToolSchema getSchema();
    List<String> getCapabilities();
    ToolResult execute(ToolParams params, ToolContext context);
    void validate(ToolParams params);
    boolean isAllowed(SecurityPolicy policy);
    boolean requiresApproval();
    ToolStats getStats();

    record ToolParams(Map<String, Object> args) {
        <T> T get(String key);
        String getString(String key);
        Integer getInteger(String key);
        Boolean getBoolean(String key);
        static ToolParams of(Map<String, Object> args);
    }
}
```

## ToolSchema 结构

```java
// ToolSchema.java
public record ToolSchema(
    String name,
    String description,
    Map<String, Property> properties,    // 属性映射（非 List）
    List<String> required                // 必填参数名列表
) {
    public record Property(
        String type,
        String description,
        Object defaultValue,
        List<String> enumValues
    ) {
        static Property string(String desc);
        static Property integer(String desc);
        static Property bool(String desc);
        static Property object(String desc);
        static Property array(String desc);
    }

    // Builder 模式
    static class Builder { ... }
}
```

## ToolExecutor 执行流程

```java
// ToolExecutor.java — 基础接口
interface ToolExecutor {
    String getName();
    String getDescription();
    ToolResult execute(Map<String, Object> parameters, String userId);
}

// 实际安全链在 ToolBackedEmployeeTaskExecutor 中实现：
// 1. 查找工具 ToolRegistry.find(toolName)
// 2. 验证权限 Tool.isAllowed(securityPolicy)
// 3. 验证参数 Tool.validate(params)
// 4. 构建上下文 ToolContext
// 5. 调用 tool.execute(params, context)
// 6. 记录执行统计 Tool.getStats()
// 7. 返回结果
```

## Skill 技能

技能是基于工具的业务封装，一个技能可以组合多个工具。

```java
// Skill.java
public interface Skill {
    default String getId() { return getName(); }
    String getName();
    String getDescription();
    String getCategory();
    default void setCategory(String category);
    String getTargetBrain();
    default void setTargetBrain(String targetBrain);
    String getContent();
    void setContent(String content);
    String getSkillPath();
    Map<String, Object> getMetadata();
    String getMetadataSummary();
    default List<String> getRequiredCapabilities() { return List.of(); }
    default SkillResult execute(SkillContext context) { ... }
}
```

## 内置技能（71个）

| 类别 | 数量 | 示例 |
|------|------|------|
| 技术 | 25 | code_review, ci_cd, architecture_design |
| 行政 | 15 | document_processing, copywriting |
| 运营 | 9 | data_analysis, operation_strategy |
| 财务 | 4 | reimbursement, budget |
| 销售 | 4 | sales_support, marketing |
| 招聘 | 3 | recruitment, performance |
| 客服 | 3 | ticket_processing, faq |
| 法务 | 3 | contract_review, compliance |
| 核心技能 | 10 | weather, find_skills 等 |

> **注意**：SKILL_INDEX.json 统计为 71 个，部分技能可能标记为"待开发"状态。SkillRegistry 运行时动态加载，实际注册数取决于文件系统中的 SKILL.md 文件数量。

## 代码路径

```
tool/
├── Tool.java                     # 工具接口（含 ToolParams 内部 record）
├── ToolRegistry.java            # 工具注册表
├── ToolExecutor.java            # 工具执行器（基础接口）
├── ToolContext.java            # 执行上下文
├── ToolResult.java             # 执行结果
├── ToolSchema.java            # 工具 Schema（含 Property 内部 record + Builder）
└── impl/
    ├── TavilySearchTool.java
    ├── WebCrawlerTool.java
    ├── PdfTool.java
    ├── OfficeTool.java
    ├── BrowserAutomationTool.java
    ├── PlaywrightCrawlerTool.java
    ├── DingTalkTool.java
    ├── GitLabTool.java
    └── enterprise/
        ├── FeishuTool.java
        ├── EnterpriseFeishuTool.java
        ├── EmployeeFeishuTool.java
        └── HrFeishuTool.java

skill/
├── Skill.java                   # 技能接口
├── SkillRegistry.java         # 技能注册表
├── SkillContext.java          # 技能上下文
└── resources/
    └── skills/                # 技能定义文件（SKILL.md）
```

## 代码产物保存约定

### ClaudeCliTool + WorktreeManager 集成

当 `ClaudeCliTool` 以 `worktree=true` 模式执行时，自动完成以下流程：

1. 调用 `WorktreeManager.create()` 创建隔离工作区 `<repoRoot>/.worktrees/<taskId>/`
2. 将 worktree 路径注入 CLI 执行参数（`--add-dir`）
3. 执行成功后自动注册 `CODE_WORKTREE` 和 `CODE_DIFF` artifact
4. 在 worktree 中生成 `.living-agent/changes.diff`

### 产物物理路径

```
<repoRoot>/.worktrees/<taskId>/
  source files...
  .living-agent/
    changes.diff
    review-report.json
    final-summary.md
```

- `<repoRoot>` 默认 `./data/repo`，可通过 `living-agent.worktree.repo-root` 配置
- 产物元数据存储在 PostgreSQL `artifact_records` 表（`ArtifactType` 枚举：`CODE_WORKTREE`/`CODE_DIFF`/`CODE_REVIEW_REPORT`/`CODE_FINAL_SUMMARY`）

### ArtifactType 代码产物枚举

| 枚举值 | 说明 |
|--------|------|
| `CODE_WORKTREE` | 代码工作树 |
| `CODE_DIFF` | 代码差异补丁 |
| `CODE_REVIEW_REPORT` | 代码审查报告 |
| `CODE_FINAL_SUMMARY` | 最终交付摘要 |

## 快速定位

| 需求 | 文件 |
|------|------|
| 添加新工具 | `tool/impl/` + `ToolRegistry.register()` |
| 修改工具执行逻辑 | `ToolBackedEmployeeTaskExecutor.java` |
| 添加工具到大脑 | 各 `*Brain.java` 构造函数 |
| 添加新技能 | `skill/resources/skills/` + SKILL.md |
| 注册技能 | `SkillRegistry.register()` |
| 修改工具 Schema | `ToolSchema.Builder` |
| 修改工具安全策略 | `Tool.isAllowed()` |
| 修改代码产物保存逻辑 | `ClaudeCliTool.java`（worktree 模式） |
| 修改 worktree 管理 | `GitWorktreeManager.java` |
| 修改产物元数据绑定 | `CodeArtifactMetadataBinder.java` |

---

## FileEditTool — 源码文件编辑工具

> 路径：`tool/impl/FileEditTool.java` | 注册名：`file_edit` | 部门：tech

允许 LLM 通过 function calling 读写工作区内的源码文件。

### 工作区挂载

```yaml
# docker-compose.yml
volumes:
  - ${WORKSPACE_PATH:-../..}:/app/workspace  # 挂载项目根目录
```

- 容器内路径：`/app/workspace`
- 宿主机路径：`F:\SoarCloudAI`（通过 `WORKSPACE_PATH` 环境变量配置）
- 可通过 JVM 参数 `-Dlivingagent.workspace.root=/app/workspace` 覆盖

### 支持的操作

| 操作 | action 参数 | 必填参数 | 说明 |
|------|-------------|----------|------|
| 读取文件 | `read_file` | path | 返回文件内容、大小、行数 |
| 写入文件 | `write_file` | path, content | 创建或覆盖文件（需审批） |
| 列出目录 | `list_dir` | path | 返回目录下的文件和子目录列表 |
| 搜索代码 | `search_code` | path, pattern | 在目录下递归搜索文本模式 |

### 安全约束

1. **路径沙箱**：所有路径必须在 `/app/workspace` 内，禁止 `..` 路径穿越
2. **敏感文件保护**：禁止读写 `.env`、`.credentials`、`.secret`、`.key`、`.pem` 等文件
3. **搜索过滤**：自动跳过 `.class`、`.jar`、`node_modules`、`target`、二进制文件
4. **审批要求**：`write_file` 操作需要审批（`requiresApproval = true`）

### 使用示例

```json
// LLM function calling 请求
{
  "name": "file_edit",
  "arguments": {
    "action": "read_file",
    "path": "docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/tool/impl/FileEditTool.java"
  }
}

// 返回
{
  "path": "docker/living-agent-service/.../FileEditTool.java",
  "content": "package com.livingagent.core.tool.impl;...",
  "size": 8234,
  "lines": 245
}
```

---

## BuildTool — 构建触发工具

> 路径：`tool/impl/BuildTool.java` | 注册名：`build` | 部门：tech

允许 LLM 在修改源码后触发编译、打包和服务重启，形成完整的"修改→编译→部署"闭环。

### 支持的操作

| 操作 | action 参数 | 可选参数 | 说明 |
|------|-------------|----------|------|
| 编译 | `compile` | module | 执行 `mvn compile`，可指定模块 |
| 打包 | `build` | module | 执行 `mvn package -DskipTests`，可指定模块 |
| 重启服务 | `restart` | service | 执行 `docker restart <service>` |
| 检查状态 | `status` | service | 检查 Docker 容器运行状态 |

### 安全约束

1. **所有操作需要审批**（`requiresApproval = true`）
2. **超时限制**：编译/打包默认 300 秒超时
3. **工作区限制**：仅在 `/app/workspace` 目录内执行构建命令

### 典型工作流

```
LLM 修改源码 (FileEditTool.write_file)
  → 触发编译 (BuildTool.compile)
  → 编译成功 → 触发打包 (BuildTool.build)
  → 打包成功 → 重启服务 (BuildTool.restart)
  → 检查状态 (BuildTool.status)
```

### 模块名称

| 模块 | module 参数值 |
|------|---------------|
| 核心层 | `living-agent-core` |
| 网关层 | `living-agent-gateway` |
| 感知层 | `living-agent-perception` |
| 技能层 | `living-agent-skill` |
| 启动模块 | `living-agent-app` |
| 全部 | 留空 |
