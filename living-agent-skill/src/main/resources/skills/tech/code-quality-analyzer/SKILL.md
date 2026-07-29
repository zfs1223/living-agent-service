---
name: code-quality-analyzer
description: "代码质量分析(fuck-u-code)，七维度质量评估"
personalSafe: false
risk: low
source: internal
date_added: '2026-07-09'
---

# Code Quality Analyzer (fuck-u-code)

> 代码质量分析技能，检测"屎山代码"，提供七维度代码质量评估

## 一、技能描述

代码质量分析技能使技术部数字员工能够执行自动化代码质量检测，包括：
- 七维度代码质量评估（复杂度/代码量/注释/错误处理/命名/重复/结构）
- AI 代码审查（通过本地模型）
- 代码质量评分（0-100分，越高越烂）
- 最差代码排行榜

## 二、适用编制

| 编制代码 | 职位 | 主要用途 |
|---------|------|---------|
| T01 | 代码审查员 | PR审查前质量预检 |
| T04 | 架构师 | 架构重构质量基线 |
| T09 | 前端工程师 | 前端代码质量检测 |
| T10 | 后端工程师 | 后端代码质量检测 |

## 三、触发词

- 代码质量、质量检测、屎山代码
- 代码审查、质量评分
- analyze、code quality、fuck-u-code
- 复杂度、代码重复、错误处理

## 四、检查维度

### 4.1 七维度指标

| 维度 | 指标 | 说明 |
|------|------|------|
| 🔄 复杂度 | 循环复杂度、认知复杂度、嵌套深度 | 评估代码可维护性 |
| 📏 代码量 | 函数长度、文件长度、参数数量 | 检测过大函数/文件 |
| 📝 注释 | 注释比例 | 检测文档缺失 |
| ❌ 错误处理 | try-catch 覆盖率 | 检测异常处理不足 |
| 🏷️ 命名 | 命名规范遵循度 | 检测命名问题 |
| 📋 重复 | 代码重复率 | 检测重复代码 |
| 🏗️ 结构 | 结构分析 | 检测结构问题 |

### 4.2 支持语言

| 语言 | 解析方式 |
|------|---------|
| Java | tree-sitter |
| TypeScript/JavaScript | tree-sitter |
| Python | tree-sitter |
| Rust | tree-sitter |
| Go | tree-sitter |
| C/C++ | tree-sitter |
| Ruby | tree-sitter |
| PHP | tree-sitter |
| Swift | tree-sitter |
| Kotlin | tree-sitter |
| Scala | tree-sitter |
| Lua | tree-sitter |
| C# | tree-sitter |
| JSON/YAML | tree-sitter |

## 五、使用示例

### 5.1 代码质量检测

```
用户: 检查 living-agent-core 目录的代码质量

技能执行:
1. 调用 fuck-u-code analyze 命令
2. 扫描目标目录所有源文件
3. 计算七维度指标
4. 生成质量评分报告
5. 返回最差代码排行榜
```

### 5.2 AI 代码审查

```
用户: AI 审查 DepartmentChatService.java

技能执行:
1. 调用 fuck-u-code ai-review 命令
2. 连接本地模型守护进程（8392端口）
3. 使用 Qwen3.5-2B 模型进行审查
4. 生成详细审查报告
5. 返回改进建议
```

### 5.3 提交前预检

```
用户: 提交前检查代码质量

技能执行:
1. 扫描待提交文件
2. 运行 analyze 获取评分
3. 评分 < 60 标记为需返工
4. 返回预检结果
```

## 六、质量报告模板

```markdown
# 代码质量分析报告

## 概览
- 分析目录: living-agent-core/src/main/java
- 文件数量: 932
- 总体评分: 79.22 / 100

## 质量等级
- 屎山等级: 微臭青年 - 略有异味，建议适量通风

## 最差代码排行榜
| 排名 | 文件 | 糟糕指数 | 主要问题 |
|------|------|---------|---------|
| 1 | DepartmentChatService.java | 60.68 | 复杂度(26)、错误处理(131) |
| 2 | model_daemon.py | 54.57 | 复杂度(39)、错误处理(49) |
| 3 | AgentWebSocketHandler.java | 36.11 | 错误处理(57)、结构(8) |

## 维度分析
| 维度 | 问题数 | 建议操作 |
|------|--------|---------|
| 错误处理 | 200+ | 添加 try-catch |
| 复杂度 | 50+ | 拆分大函数 |
| 注释缺失 | 70%+ | 补充关键注释 |

## 建议
1. 重构 DepartmentChatService.processBrainResponse()（复杂度66）
2. 拆分 model_daemon.py 的 classify() 和 generate() 函数
3. 补充错误处理和关键注释
```

## 七、调用方式

### 7.1 命令行调用

```bash
# 基本分析
docker exec living-agent-fuck-u-code node /app/bin/fuck-u-code.js analyze /workspace/path --top 10 --locale zh

# AI 审查
docker exec living-agent-fuck-u-code node /app/bin/fuck-u-code.js ai-review /workspace/path --top 5

# JSON 输出
docker exec living-agent-fuck-u-code node /app/bin/fuck-u-code.js analyze /workspace/path --format json
```

### 7.2 Java API 调用

```java
FuckUCodeClient client = new FuckUCodeClient();
if (!client.isAvailable()) {
    return "工具不可用";
}

AnalyzeResult result = client.analyze("/workspace/path", 10);
for (FileScore file : result.fileScores()) {
    if (file.score() < 60) {
        log.warn("代码质量不达标: {}", file.filePath());
    }
}
```

### 7.3 MCP 调用

```
mcp.fuck-u-code.analyze(path, topN, format)
mcp.fuck-u-code.ai-review(path, topN)
```

## 八、参数说明

| 参数 | 类型 | 必选 | 说明 | 默认值 |
|------|------|------|------|--------|
| path | string | ✅ | 分析路径（容器内路径） | - |
| topN | int | ❌ | 返回评分最低的 N 个文件 | 5 |
| locale | string | ❌ | 报告语言（zh/en） | zh |
| format | string | ❌ | 输出格式（terminal/json/markdown） | terminal |

## 九、质量阈值

| 评分范围 | 等级 | 建议操作 |
|---------|------|---------|
| 0-40 | 清新可人 | ✅ 通过 |
| 40-60 | 微臭青年 | ⚠️ 建议优化 |
| 60-80 | 中度屎山 | ❌ 需要重构 |
| 80-100 | 重度屎山 | 🚫 必须重构 |

## 十、配置要求

```yaml
# docker-compose.yml
fuck-u-code:
  image: living-agent-fuck-u-code:latest
  container_name: living-agent-fuck-u-code
  volumes:
    - ${WORKSPACE_PATH}:/workspace:ro
  environment:
    FUCKUCODE_AI_PROVIDER: openai
    FUCKUCODE_AI_MODEL: qwen3.5-2b
    FUCKUCODE_AI_BASE_URL: http://living-agent-service:8392/v1
    FUCKUCODE_LOCALE: zh
```

## 十一、依赖关系

- **模型守护进程**: 8392 端口提供 OpenAI 兼容 API
- **Docker 容器**: living-agent-fuck-u-code
- **MCP 配置**: mcp.json

## 十二、关联闭环

- **闭环49**: 代码审查工作流闭环（P49-C）
- **闭环42**: 技能管理闭环

---

*来源: 基于 fuck-u-code-2.2.1 集成*
*更新时间: 2026-07-09*