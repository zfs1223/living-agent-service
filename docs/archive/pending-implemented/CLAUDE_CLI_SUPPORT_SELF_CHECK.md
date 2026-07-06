# Claude CLI 参数支持性自检方案

## 目标

验证容器内实际运行的 `claude` 命令是否真正支持研发员工需要的关键参数，以及环境变量是否正确生效。

## 自检项

### 1. CLI 版本与帮助信息

- `claude --version`
- `claude --help`

确认以下参数是否在帮助中可见：

- `--model`
- `--worktree`
- `--append-system-prompt`
- `--max-turns`
- `--add-dir`
- `--resume`
- `--fork-session`
- `--dangerously-skip-permissions`
- `--output-format`

### 2. 环境变量检查

确认执行环境中是否包含：

- `ANTHROPIC_API_KEY`
- `ANTHROPIC_BASE_URL`
- `ANTHROPIC_API_URL`
- `CLAUDE_CODE_SHELL`
- `CLAUDE_BASH_NO_LOGIN`
- `TERM`
- `PYTHONIOENCODING`

### 3. 最小执行验证

建议分三组执行：

#### A. 只读验证

```bash
claude -p "echo hello" --output-format text --model sonnet
```

验证：

- 是否能正常启动
- 是否接受 `--model`
- 是否能返回输出

#### B. 系统提示词验证

```bash
claude -p "review this repo" --append-system-prompt "You are a tech reviewer." --output-format text
```

验证：

- `--append-system-prompt` 是否被接受
- 输出是否体现提示词约束

#### C. 工作树与目录验证

```bash
claude -p "analyze current repo" --worktree --add-dir /workspace --output-format stream-json
```

验证：

- `--worktree` 是否被接受
- `--add-dir` 是否生效
- `stream-json` 是否可用

### 4. 失败时的判定规则

如果出现以下任一情况，说明容器内 CLI 支持不完整或参数链路有问题：

- `unknown option`
- `unrecognized flag`
- `command not found`
- 输出不符合预期格式
- 参数被吞掉但无报错

### 5. 与代码链路的对应关系

当前代码链路为：

`TechBrain` -> `ClaudeCliTool` -> `ClaudeExecutionGateway` -> `SandboxSessionImpl` -> 容器内 `claude`

若某个参数在代码中已拼接，但容器内不支持，则最终会在 `SandboxSessionImpl` 的执行结果中体现为：

- exit code 非 0
- stderr 出现参数解析错误
- stdout 没有任何有效事件

## 建议的执行顺序

1. 先跑 `claude --help` 和 `claude --version`
2. 再跑只读 prompt
3. 再跑带 system prompt 的 prompt
4. 再跑 `worktree` / `add-dir` 场景
5. 最后结合后端日志确认参数是否真的透传

## 备注

如果容器里 `claude` 的版本较旧，建议将研发固定员工的 Claude CLI 策略降级为：

- 仅使用 `prompt` / `resume` / `output-format` / `verbose`
- 暂不启用 `worktree` / `max-turns` / `append-system-prompt`

直到确认容器支持为止。
