# Crawl4AI - 智能网页爬取技能

> 基于 Crawl4AI 的高性能网页爬取和内容提取技能

## 技能概述

Crawl4AI 是一个 LLM 友好的网页爬取工具，支持 JavaScript 渲染、智能内容提取和反爬虫措施。作为 Living Agent Service 的辅助技能，提供强大的网络信息获取能力。

## 核心能力

| 能力 | 说明 |
|------|------|
| **JavaScript 渲染** | 支持 SPA、动态加载页面的完整渲染 |
| **智能内容提取** | Markdown、结构化数据、链接提取 |
| **反爬虫措施** | Stealth 模式、代理轮换、指纹随机化 |
| **深度爬取** | BFS/DFS 策略，支持过滤器和评分器 |
| **LLM 集成** | 直接输出适合 LLM 处理的 Markdown 格式 |

## 服务信息

| 配置项 | 值 |
|--------|-----|
| **服务名称** | crawl4ai |
| **默认端口** | 11235 |
| **健康检查** | `/health` |
| **API 端点** | `/crawl`, `/crawl/stream` |

## 使用方式

### 1. 通过 WebCrawlerTool 调用

```java
// Java 代码调用
ToolCall call = new ToolCall("web_crawler", Map.of(
    "url", "https://example.com/article",
    "extract_type", "markdown",
    "wait_time", 2000
));
ToolResult result = webCrawlerTool.execute(call);
```

### 2. 直接 HTTP API 调用

```bash
# 基础爬取
curl -X POST http://localhost:11235/crawl \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com",
    "extraction_strategy": {"type": "markdown"}
  }'

# 带 JavaScript 执行
curl -X POST http://localhost:11235/crawl \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/dynamic",
    "js_code": "window.scrollTo(0, document.body.scrollHeight)",
    "wait_for": 2000
  }'

# 深度爬取
curl -X POST http://localhost:11235/crawl \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com",
    "deep_crawl": {
      "max_pages": 10,
      "strategy": "bfs"
    }
  }'
```

## 参数说明

### 基础参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `url` | string | 必填 | 要爬取的 URL |
| `extraction_strategy` | object | markdown | 内容提取策略 |
| `js_code` | string | - | JavaScript 代码 |
| `wait_for` | int | 0 | 等待时间 (毫秒) |
| `user_agent` | string | 自动 | User-Agent |

### 提取策略

| 类型 | 说明 |
|------|------|
| `markdown` | 转换为 Markdown 格式 |
| `text` | 纯文本提取 |
| `json` | 结构化 JSON 提取 |
| `llm` | LLM 智能提取 |

### 深度爬取参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `max_pages` | int | 10 | 最大页面数 |
| `strategy` | string | bfs | 策略: bfs/dfs |
| `filter` | object | - | URL 过滤器 |
| `scorer` | object | - | 页面评分器 |

## 反爬虫配置

### Stealth 模式

```json
{
  "url": "https://example.com",
  "browser_config": {
    "headless": true,
    "proxy": "http://proxy:8080",
    "user_agent_mode": "random"
  }
}
```

### 代理配置

```json
{
  "url": "https://example.com",
  "proxy_config": {
    "server": "http://proxy:8080",
    "username": "user",
    "password": "pass"
  }
}
```

## 使用场景

### 场景 1: 企业新闻监控

```json
{
  "url": "https://news.example.com/enterprise",
  "extraction_strategy": {"type": "markdown"},
  "deep_crawl": {
    "max_pages": 5,
    "strategy": "bfs",
    "filter": {"pattern": "/article/"}
  }
}
```

### 场景 2: 竞品分析

```json
{
  "url": "https://competitor.example.com/products",
  "extraction_strategy": {
    "type": "json",
    "schema": {
      "products": [{"name": "", "price": "", "description": ""}]
    }
  }
}
```

### 场景 3: 技术文档抓取

```json
{
  "url": "https://docs.example.com",
  "extraction_strategy": {"type": "markdown"},
  "deep_crawl": {
    "max_pages": 50,
    "strategy": "bfs",
    "filter": {"pattern": "/docs/"}
  }
}
```

## 与 PlaywrightCrawlerTool 对比

| 特性 | PlaywrightCrawlerTool | Crawl4AI |
|------|----------------------|----------|
| **部署方式** | 内置 | 独立服务 |
| **资源占用** | 低 | 中 |
| **批量爬取** | 单页面 | 支持批量 |
| **深度爬取** | 不支持 | 支持 |
| **LLM 提取** | 不支持 | 支持 |
| **适用场景** | 简单爬取 | 复杂爬取 |

## 错误处理

| 错误码 | 说明 | 解决方案 |
|--------|------|---------|
| 400 | 参数错误 | 检查请求参数 |
| 429 | 请求频率超限 | 降低请求频率 |
| 500 | 服务内部错误 | 检查服务日志 |
| 503 | 服务不可用 | 等待服务恢复 |

## 健康检查

```bash
# 检查服务状态
curl http://localhost:11235/health

# 预期响应
{
  "status": "healthy",
  "version": "0.7.0"
}
```

## 部署配置

在 `docker-compose.yml` 中已配置：

```yaml
crawl4ai:
  image: unclecode/crawl4ai:latest
  ports:
    - "11235:11235"
  environment:
    - OPENAI_API_KEY=${OPENAI_API_KEY:-}
    - DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY:-}
  volumes:
    - /dev/shm:/dev/shm
  deploy:
    resources:
      limits:
        memory: 4G
```

## 注意事项

1. **资源消耗**: Crawl4AI 使用 Chromium 渲染，建议分配至少 2GB 内存
2. **并发限制**: 默认支持 10 个并发请求，可通过环境变量调整
3. **代理使用**: 高反爬虫网站建议配置代理
4. **数据安全**: 所有数据在本地处理，不会上传到外部服务

## 相关文件

- [WebCrawlerTool.java](../../living-agent-core/src/main/java/com/livingagent/core/tool/impl/WebCrawlerTool.java)
- [Crawl4aiClient.java](../../living-agent-core/src/main/java/com/livingagent/core/tool/impl/Crawl4aiClient.java)
- [docker-compose.yml](../../docker-compose.yml)
