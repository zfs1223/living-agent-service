# Tavily Search - AI 搜索引擎技能

> 基于 Tavily AI 的实时网络搜索和信息检索技能

## 技能概述

Tavily Search 是一个专为 AI 设计的搜索引擎 API，提供实时、准确的网络搜索结果。作为神经元的基础技能，支持信息检索、知识获取和实时数据查询。

## 核心能力

| 能力 | 说明 |
|------|------|
| **实时搜索** | 获取最新的网络信息 |
| **AI 优化结果** | 返回结构化、相关性高的结果 |
| **深度搜索** | 支持搜索深度控制 |
| **多类型搜索** | 网页、新闻、学术等 |
| **结果摘要** | 自动生成搜索结果摘要 |

## 技能参数

### 基础参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `query` | string | 是 | - | 搜索查询语句 |
| `search_depth` | string | 否 | basic | 搜索深度: basic/advanced |
| `max_results` | int | 否 | 5 | 最大结果数 (1-10) |
| `include_domains` | array | 否 | - | 限定搜索域名 |
| `exclude_domains` | array | 否 | - | 排除搜索域名 |
| `include_answer` | bool | 否 | true | 是否包含 AI 答案 |
| `include_raw_content` | bool | 否 | false | 是否包含原始内容 |

## 使用示例

### 基础搜索

```json
{
  "query": "2024年人工智能发展趋势",
  "max_results": 5
}
```

### 深度搜索

```json
{
  "query": "Java 21 新特性详解",
  "search_depth": "advanced",
  "max_results": 10,
  "include_answer": true,
  "include_raw_content": true
}
```

### 限定域名搜索

```json
{
  "query": "Spring Boot 最佳实践",
  "include_domains": ["spring.io", "baeldung.com"],
  "max_results": 5
}
```

## 返回结果结构

```json
{
  "answer": "AI 生成的答案摘要",
  "results": [
    {
      "title": "结果标题",
      "url": "https://example.com",
      "content": "内容摘要",
      "score": 0.95,
      "published_date": "2024-01-15"
    }
  ],
  "query": "原始查询",
  "response_time_ms": 1500
}
```

## 触发词

- 搜索、查找、查询
- search、find、lookup
- 最新信息、实时数据
- 网络搜索、互联网查询

## 神经元集成

每个神经元都应具备此技能，用于：

1. **Qwen3Neuron**: 闲聊中需要实时信息时使用
2. **BitNetNeuron**: 工具检测和信息补充
3. **RouterNeuron**: 意图识别时获取上下文
4. **部门大脑**: 专业领域信息检索

## API 配置

### 环境变量

```env
TAVILY_API_KEY=tvly-your-api-key
```

### 配置文件

```yaml
living-agent:
  skills:
    tavily-search:
      enabled: true
      api-key: ${TAVILY_API_KEY}
      default-max-results: 5
      default-depth: basic
      timeout-ms: 30000
```

## 错误处理

| 错误码 | 说明 | 处理方式 |
|--------|------|---------|
| 401 | API Key 无效 | 检查配置 |
| 429 | 请求频率超限 | 降低频率或升级套餐 |
| 500 | 服务错误 | 重试或使用备用搜索 |

## 成本估算

| 搜索深度 | 每次请求成本 |
|---------|-------------|
| basic | ~0.001 USD |
| advanced | ~0.005 USD |

## 相关技能

- [find-skills](../find-skills/SKILL.md) - 技能发现
- [proactive-agent](../proactive-agent/SKILL.md) - 主动代理
- [crawl4ai](../crawl4ai/SKILL.md) - 网页爬取
