# Living Agent Service 部署指南

## 快速开始

### 1. 基础部署 (仅核心服务)

```bash
cd f:\SoarCloudAI\docker\living-agent-service

# 启动服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f living-agent-service
```

### 2. 完整部署 (包含数据库)

```bash
# 使用 full profile 启动所有服务
docker-compose --profile full up -d

# 服务列表:
# - living-agent-service (主服务)
# - crawl4ai (网页爬取)
# - postgres (数据库)
# - qdrant (向量数据库)
# - redis (缓存)
```

## 服务架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    Docker Network                               │
│                 (living-agent-network)                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌───────────────────┐     ┌───────────────────┐               │
│  │ living-agent-     │     │ crawl4ai          │               │
│  │ service           │────▶│ (辅助技能)         │               │
│  │ :8380             │     │ :11235            │               │
│  │                   │     │                   │               │
│  │ - REST API        │     │ - 网页爬取        │               │
│  │ - WebSocket       │     │ - JS渲染          │               │
│  │ - 神经元系统      │     │ - 反爬虫          │               │
│  └───────────────────┘     └───────────────────┘               │
│           │                                                     │
│           │ (可选)                                               │
│           ▼                                                     │
│  ┌───────────────────┐     ┌───────────────────┐               │
│  │ postgres          │     │ qdrant            │               │
│  │ :5432             │     │ :6333             │               │
│  │                   │     │                   │               │
│  │ - 企业知识存储    │     │ - 向量检索        │               │
│  └───────────────────┘     └───────────────────┘               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 环境变量配置

创建 `.env` 文件：

```env
# LLM API Keys
QWEN_API_KEY=your_qwen_api_key
DEEPSEEK_API_KEY=your_deepseek_api_key
OPENAI_API_KEY=your_openai_api_key

# Database
POSTGRES_PASSWORD=your_secure_password

# Optional
LLM_PROVIDER=deepseek
```

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| living-agent-service | 8380 | 主服务 HTTP/WebSocket |
| crawl4ai | 11235 | 网页爬取 API |
| postgres | 5432 | PostgreSQL 数据库 |
| qdrant | 6333 | Qdrant 向量数据库 |
| qdrant-grpc | 6334 | Qdrant gRPC |
| redis | 6379 | Redis 缓存 |

## 健康检查

```bash
# 检查主服务
curl http://localhost:8380/actuator/health

# 检查 crawl4ai
curl http://localhost:11235/health

# 检查 PostgreSQL
docker exec living-agent-postgres pg_isready -U livingagent

# 检查 Qdrant
curl http://localhost:6333/health

# 检查 Redis
docker exec living-agent-redis redis-cli ping
```

## 数据持久化

| 卷名 | 说明 |
|------|------|
| living-agent-data | 主服务数据 |
| living-agent-logs | 日志文件 |
| crawl4ai-cache | Crawl4AI 缓存 |
| living-agent-postgres-data | PostgreSQL 数据 |
| living-agent-qdrant-data | Qdrant 数据 |
| living-agent-redis-data | Redis 数据 |

## 常用命令

```bash
# 启动服务
docker-compose up -d

# 停止服务
docker-compose down

# 重启服务
docker-compose restart

# 查看日志
docker-compose logs -f [service_name]

# 进入容器
docker exec -it living-agent-service /bin/sh

# 清理数据 (谨慎使用)
docker-compose down -v
```

## 资源配置

### 最小配置

| 服务 | CPU | 内存 |
|------|-----|------|
| living-agent-service | 1 | 1GB |
| crawl4ai | 1 | 2GB |

### 推荐配置

| 服务 | CPU | 内存 |
|------|-----|------|
| living-agent-service | 2 | 2GB |
| crawl4ai | 2 | 4GB |
| postgres | 1 | 1GB |
| qdrant | 1 | 2GB |
| redis | 0.5 | 512MB |

## 网络爬取能力

### 内置工具 (独立运行)

- **PlaywrightCrawlerTool** - Java 原生，支持 JS 渲染
- **RssReaderTool** - RSS/Atom 订阅
- **HttpTool** - 基础 HTTP 请求

### 辅助服务 (crawl4ai)

- **WebCrawlerTool** - 高级爬取，批量处理
- 深度爬取 (BFS/DFS)
- LLM 智能提取
- 高级反爬虫

## 故障排查

### 服务无法启动

```bash
# 检查日志
docker-compose logs living-agent-service

# 检查资源
docker stats

# 检查网络
docker network ls
docker network inspect living-agent-network
```

### crawl4ai 连接失败

```bash
# 检查服务状态
docker-compose ps crawl4ai

# 检查健康状态
curl http://localhost:11235/health

# 检查网络连接
docker exec living-agent-service curl http://crawl4ai:11235/health
```

### 内存不足

```bash
# 调整 docker-compose.yml 中的资源限制
deploy:
  resources:
    limits:
      memory: 4G
```

## 安全建议

1. **修改默认密码** - 修改 PostgreSQL、Redis 密码
2. **限制端口暴露** - 仅暴露必要端口
3. **使用 HTTPS** - 配置反向代理
4. **定期备份** - 备份数据卷
5. **日志监控** - 监控异常访问

## 升级指南

```bash
# 拉取最新镜像
docker-compose pull

# 重新构建
docker-compose build

# 重启服务
docker-compose up -d
```
