# Claude CLI Tool

> Claude CLI 集成技能，用于代码生成、审查、测试、调试和仓库协作

## 一、技能描述

Claude CLI 技能使技术部数字员工能够通过 Claude CLI 执行：
- 代码生成与重构
- 代码审查与质量分析
- 测试生成与验证
- Bug 定位与修复
- 发布准备与风险检查
- 架构重构规划

## 二、适用编制

| 编制代码 | 职位 | 主要用途 |
|---------|------|---------|
| T01 | 代码审查员 | 代码审查、质量分析 |
| T02 | Bug修复专员 | Bug定位与修复 |
| T03 | 测试工程师 | 测试生成与验证 |
| T04 | 架构师 | 架构重构规划 |
| T09 | 前端工程师 | 前端代码生成 |
| T10 | 后端工程师 | 后端代码生成 |

## 三、触发词

- Claude CLI、claude_cli
- 代码生成、代码审查、代码修复
- 测试生成、bug修复
- 重构规划、发布准备

## 四、任务分流规则

### 4.1 任务类型映射

| 任务类型 | 触发条件 | CLI 参数 |
|---------|---------|---------|
| code_review | 审查、差异分析、风险识别 | `--output-format stream-json --model sonnet --max-turns 8 --add-dir <repo>` |
| bug_fix | 报错、异常、回归、NPE、构建失败 | `--output-format stream-json --model opus --worktree --max-turns 12` |
| test_generate | 补测试、验证边界、生成回归用例 | `--output-format json --model sonnet --max-turns 6 --add-dir <tests>` |
| release_prep | 发布、上线、部署、回滚、变更窗口 | `--output-format json --model sonnet --worktree --max-turns 10` |
| refactor_plan | 大规模重构、分层拆分、迁移规划 | `--output-format stream-json --model opus --worktree --max-turns 15` |

### 4.2 模型选择策略

| 任务复杂度 | 推荐模型 | 说明 |
|-----------|---------|------|
| 简单任务 | sonnet | 快速响应，低延迟 |
| 复杂任务 | opus | 深度推理，高准确率 |

## 五、使用示例

### 5.1 代码审查

```
用户: 审查 living-agent-core 目录的代码

技能执行:
1. 收集五元上下文：任务类型、仓库路径、允许目录、输出格式、工作树
2. 调用 Claude CLI: --output-format stream-json --model sonnet --max-turns 8
3. 执行代码审查
4. 返回问题清单、风险等级、建议修复点
```

### 5.2 Bug修复

```
用户: 修复 NullPointerException in DepartmentChatService.java

技能执行:
1. 定位错误位置和根因
2. 启用 worktree 隔离工作树
3. 调用 Claude CLI: --output-format stream-json --model opus --worktree --max-turns 12
4. 执行最小修复
5. 返回修复方案和回归测试建议
```

### 5.3 测试生成

```
用户: 为 FuckUCodeClient 生成单元测试

技能执行:
1. 读取接口和现有测试
2. 调用 Claude CLI: --output-format json --model sonnet --max-turns 6
3. 生成最小测试集
4. 返回测试列表和覆盖点
```

## 六、操作类型

### 6.1 支持的操作（action）

| action | 说明 | 必需参数 |
|--------|------|---------|
| prompt | 发送提示词 | prompt |
| resume | 恢复会话 | prompt, resume_session_id |
| status | 查询会话状态 | - |
| start | 启动异步任务 | prompt |
| poll | 轮询异步任务结果 | job_id |
| cancel | 取消异步任务 | job_id |

### 6.2 核心参数

| 参数 | 类型 | 说明 | 默认值 |
|------|------|------|--------|
| prompt | string | 用户提示词/任务描述 | - |
| model | string | 模型名称（sonnet/opus） | sonnet |
| output_format | string | 输出格式（stream-json/json/text） | stream-json |
| max_turns | number | 最大思考轮数 | 8 |
| worktree | boolean | 是否使用隔离工作树 | false |
| add_dir | array | 额外可访问目录 | [] |
| allowed_dirs | array | 允许访问的目录白名单 | [] |
| system_prompt | string | 自定义系统提示词 | - |
| resume_session_id | string | 恢复会话ID | - |

## 七、调用方式

### 7.1 Java API 调用

```java
// 获取 ClaudeCliTool
Tool claudeCliTool = toolRegistry.get("claude_cli").orElseThrow();

// 构建参数
ToolParams params = ToolParams.of(
    "action", "prompt",
    "prompt", "审查 living-agent-core 目录的代码",
    "model", "sonnet",
    "output_format", "stream-json",
    "max_turns", 8,
    "add_dir", List.of("/workspace/docker/living-agent-service")
);

// 执行
ToolResult result = claudeCliTool.execute(params, context);
```

### 7.2 会话恢复

```java
ToolParams params = ToolParams.of(
    "action", "resume",
    "prompt", "继续修复上一个问题",
    "resume_session_id", "session_abc123"
);
```

### 7.3 异步任务

```java
// 启动异步任务
ToolParams startParams = ToolParams.of(
    "action", "start",
    "prompt", "大规模重构任务"
);
ToolResult startResult = claudeCliTool.execute(startParams, context);
String jobId = (String) startResult.getData().get("job_id");

// 轮询结果
ToolParams pollParams = ToolParams.of(
    "action", "poll",
    "job_id", jobId
);
ToolResult pollResult = claudeCliTool.execute(pollParams, context);
```

## 八、工作原则

根据 `TechClaudeCliPromptTemplates.SHARED_POLICY`：

| 原则 | 说明 |
|------|------|
| 1. 优先使用 Claude CLI | 处理代码生成、重构、修复、测试、排查、总结与审查任务 |
| 2. 保护敏感信息 | 不要把敏感代码、密钥、配置或日志发送到外网 |
| 3. 多步骤先规划 | 先给出执行计划，再逐步执行 |
| 4. 目录约束 | 优先在当前仓库或允许目录内操作 |
| 5. 输出规范 | 必须包含：结论、修改建议、执行摘要 |
| 6. 澄清优先 | 当任务不清晰时，先澄清目标、输入、约束、期望输出 |

## 九、输出格式

### 9.1 stream-json 输出

```json
{"type":"assistant","content":"正在分析代码..."}
{"type":"result","content":"发现3个问题"}
{"type":"session_id","content":"session_abc123"}
```

### 9.2 json 输出

```json
{
  "output": "完整的输出内容",
  "duration_ms": 12345,
  "execution_id": "exec_xyz",
  "action": "prompt",
  "events": [...],
  "parsed_session_id": "session_abc123"
}
```

## 十、配置要求

```yaml
# application.yml
living-agent:
  claude-cli:
    enabled: true
    default-model: sonnet
    default-output-format: stream-json
    default-max-turns: 8
    worktree-enabled: true
    danger-skip-permissions: true
```

## 十一、依赖关系

- **ClaudeExecutionGateway**: CLI 会话管理与参数映射
- **ToolRegistry**: 工具注册与发现
- **SecurityPolicy**: 权限检查

## 十二、健康检查

```java
// ClaudeCliHealthChecker.java
HealthStatus status = healthChecker.check();
// 返回: healthy / degraded / unhealthy
```

## 十三、关联闭环

- **闭环33**: Claude Code工具闭环
- **闭环49**: 代码审查工作流闭环

---

*来源: 基于 free-claude-code-main 集成*
*更新时间: 2026-07-09*