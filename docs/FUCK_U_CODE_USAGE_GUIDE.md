# fuck-u-code 使用指南 - 数字人调用手册

> **生成日期**: 2026-07-09
> **适用对象**: TechBrain、MainBrain、Claude Code、固定数字人
> **关联闭环**: 49（代码审查工作流闭环）P49-C

---

## 一、工具概述

fuck-u-code 是一款代码质量分析工具，用于检测"屎山代码"。它可以从**七个维度**评估代码质量：

| 维度 | 指标说明 |
|------|---------|
| 🔄 复杂度 | 循环复杂度、认知复杂度、嵌套深度 |
| 📏 代码量 | 函数长度、文件长度、参数数量 |
| 📝 注释 | 注释比例 |
| ❌ 错误处理 | try-catch 覆盖率 |
| 🏷️ 命名 | 命名规范遵循度 |
| 📋 重复 | 代码重复率 |
| 🏗️ 结构 | 结构分析 |

**评分规则**：0-100 分，**越高越烂**。建议将质量阈值设为 60 分以下。

---

## 二、部署方式

### 2.1 外部使用（独立部署）

**适用场景**：开发人员本地使用、CI/CD 流水线、外部项目审查

#### Go 版本（官方推荐）

```bash
# 方法一：Go 安装
go install github.com/Done-0/fuck-u-code/cmd/fuck-u-code@latest

# 方法二：Docker 运行
docker run --rm -v "/path/to/project:/build" fuck-u-code analyze

# 方法三：源码构建
git clone https://github.com/Done-0/fuck-u-code.git
cd fuck-u-code && go build -o fuck-u-code ./cmd/fuck-u-code
```

#### 常用命令

```bash
# 基本分析
fuck-u-code analyze /path/to/project

# 显示详细报告
fuck-u-code analyze --verbose

# 最烂的前 N 个文件
fuck-u-code analyze --top 10

# Markdown 格式输出（适合 AI 分析）
fuck-u-code analyze --markdown > report.md

# 指定报告语言
fuck-u-code analyze --lang zh-CN
fuck-u-code analyze --lang en-US

# 排除指定目录
fuck-u-code analyze --exclude "**/test/**"
```

### 2.2 内部使用（Living Agent 集成）

**适用场景**：数字人调用、自动代码审查、闭环 49 集成

#### Node.js 版本（已部署）

Living Agent 已部署 Node.js 版本的 fuck-u-code，通过 Docker 容器运行。

```yaml
# docker-compose.yml 配置
fuck-u-code:
  image: living-agent-fuck-u-code:latest
  container_name: living-agent-fuck-u-code
  volumes:
    - ${WORKSPACE_PATH:-./..}:/workspace:ro  # 挂载项目源码
  environment:
    FUCKUCODE_AI_PROVIDER: openai
    FUCKUCODE_AI_MODEL: qwen3.5-2b
    FUCKUCODE_AI_BASE_URL: http://living-agent-service:8392/v1
    FUCKUCODE_LOCALE: zh
```

---

## 三、数字人调用方式

### 3.1 通过 MCP 调用（推荐）

**适用对象**：Claude Code、TechBrain、MainBrain

Claude Code 可通过 MCP 协议直接调用 fuck-u-code：

```json
// mcp.json 配置
{
  "mcpServers": {
    "fuck-u-code": {
      "command": "docker",
      "args": ["exec", "-i", "living-agent-fuck-u-code", "node", "/app/bin/fuck-u-code-mcp.js"],
      "env": {
        "FUCKUCODE_AI_PROVIDER": "openai",
        "FUCKUCODE_AI_MODEL": "qwen3.5-2b",
        "FUCKUCODE_AI_BASE_URL": "http://living-agent-service:8392/v1",
        "FUCKUCODE_LOCALE": "zh"
      }
    }
  }
}
```

**MCP 工具列表**：

| 工具名 | 功能 | 参数 |
|--------|------|------|
| `analyze` | 代码质量分析 | `path`, `topN`, `format` |
| `ai-review` | AI 代码审查 | `path`, `topN`, `model` |

### 3.2 通过命令行调用

**适用对象**：TechBrain、任意数字人

数字人可通过 `docker exec` 命令调用：

```bash
# 分析指定目录
docker exec living-agent-fuck-u-code node /app/bin/fuck-u-code.js analyze /workspace/docker/living-agent-service/living-agent-core --top 10 --locale zh

# AI 审查（需要模型守护进程）
docker exec living-agent-fuck-u-code node /app/bin/fuck-u-code.js ai-review /workspace/docker/living-agent-service/living-agent-core --top 5
```

### 3.3 通过 Java API 调用

**适用对象**：TechBrain、CodeReviewWorkflowService

Java 服务层可通过 `FuckUCodeClient` 调用：

```java
// 1. 创建客户端
FuckUCodeClient client = new FuckUCodeClient();

// 2. 检查可用性
if (!client.isAvailable()) {
    log.warn("fuck-u-code 容器不可用");
    return;
}

// 3. 执行分析
AnalyzeResult result = client.analyze("/workspace/docker/living-agent-service/living-agent-core", 10);

// 4. 获取评分最低的文件
for (FileScore file : result.fileScores()) {
    if (file.score() < 60) {
        log.warn("代码质量不达标: {} (score={})", file.filePath(), file.score());
    }
}

// 5. 记录基线评分（闭环49）
metricsService.recordBaselineScores(
    result.fileScores().stream()
        .map(f -> new BaselineScore(f.filePath(), f.score(), f.metrics(), Instant.now()))
        .toList()
);
```

---

## 四、数字人使用场景

### 4.1 TechBrain - 代码审查助手

**触发场景**：审查 Merge Request、代码提交预检

```java
// 1. 提交前预检
AnalyzeResult result = client.analyze(worktreePath, 5);
if (result.overallScore() < 60) {
    return "代码质量不合格，请先修复以下问题：" + result.fileScores().get(0).filePath();
}

// 2. 审查通过后记录基线
metricsService.recordBaselineScores(...);
optimizer.adjustThresholdBasedOnBaseline();
```

### 4.2 MainBrain - 跨部门代码质量监控

**触发场景**：定期扫描核心模块、生成质量报告

```bash
# 定期执行
0 2 * * * docker exec living-agent-fuck-u-code node /app/bin/fuck-u-code.js analyze /workspace/docker/living-agent-service --markdown > /reports/code-quality-$(date +%Y%m%d).md
```

### 4.3 Claude Code - 开发辅助

**触发场景**：开发过程中实时反馈

用户可通过 Claude Code 的 MCP 接口直接请求代码质量分析：

```
用户: 请帮我检查 living-agent-core/codereview 目录的代码质量

Claude Code: 
→ 调用 MCP: fuck-u-code.analyze
→ 返回报告：总分 95.44，主要问题是错误处理不足
```

---

## 五、输出格式说明

### 5.1 终端输出（默认）

```
🌸 屎山代码分析报告 🌸

总体评分: 79.22 / 100 - 略带清香，偶尔飘过一丝酸爽
屎山等级: 微臭青年 - 略有异味，建议适量通风

◆ 最屎代码排行榜
  1. DepartmentChatService.java (糟糕指数: 60.68)
     🔄 processBrainResponse() L1230: 复杂度: 66
     ❌ L95: 未处理的易出错调用
```

### 5.2 JSON 输出（--format json）

```json
{
  "overallScore": 79.22,
  "level": "微臭青年",
  "fileResults": [
    {
      "filePath": "DepartmentChatService.java",
      "score": 60,
      "metrics": {
        "complexity": 26,
        "error": 131,
        "structure": 19
      }
    }
  ]
}
```

### 5.3 Markdown 输出（--markdown）

适合 AI 分析、文档集成、CI/CD 报告。

---

## 六、常见问题

### Q1: 容器一直重启怎么办？

**原因**：Dockerfile 的 CMD 执行了命令后退出

**解决**：修改 Dockerfile，使用 `tail -f /dev/null` 保持运行

```dockerfile
# 错误方式
CMD ["node", "bin/fuck-u-code.js", "--help"]

# 正确方式（已修复）
CMD ["tail", "-f", "/dev/null"]
```

### Q2: AI 审查失败怎么办？

**原因**：模型守护进程未启动或端口配置错误

**检查**：
```bash
# 检查模型守护进程
curl http://localhost:8392/v1/health

# 检查容器网络
docker network inspect living-agent_backend
```

### Q3: 如何只检查特定文件类型？

```bash
# 只检查 Java 文件
docker exec living-agent-fuck-u-code node /app/bin/fuck-u-code.js analyze /workspace/docker/living-agent-service --include "**/*.java"
```

---

## 七、维护信息

| 字段 | 值 |
|------|---|
| 文档版本 | v1.0 |
| 生成日期 | 2026-07-09 |
| 关联闭环 | 49（代码审查工作流闭环）P49-C |
| 维护责任人 | TechBrain |
| 镜像位置 | `image/living-agent-fuck-u-code.tar` |
| MCP 配置 | `living-agent-app/src/main/resources/claude/mcp.json` |

---

## 八、快速参考卡片

### 数字人调用速查表

| 数字人 | 调用方式 | 命令/代码 |
|--------|---------|----------|
| Claude Code | MCP | `mcp.fuck-u-code.analyze(path, topN)` |
| TechBrain | Java API | `client.analyze(path, topN)` |
| MainBrain | 命令行 | `docker exec ... analyze /workspace/...` |

### 常用参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--top N` | 最烂的 N 个文件 | 5 |
| `--locale zh` | 中文报告 | zh |
| `--format json` | JSON 输出 | terminal |
| `--markdown` | Markdown 输出 | - |

### 质量阈值参考

| 评分范围 | 等级 | 建议操作 |
|---------|------|---------|
| 0-40 | 清新可人 | ✅ 通过 |
| 40-60 | 微臭青年 | ⚠️ 建议优化 |
| 60-80 | 中度屎山 | ❌ 需要重构 |
| 80-100 | 重度屎山 | 🚫 必须重构 |