# Memory 系统配置文档

> Living Agent Service 记忆系统集成 MemOS-2.0.7

---

## 一、系统概述

### 1.1 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                    Living Agent Service                          │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    MemoryService                         │    │
│  │  ┌─────────────────┐    ┌──────────────────────────┐   │    │
│  │  │ SQLiteBackend   │    │   MemosMemoryBackend     │   │    │
│  │  │ (本地缓存/兜底)  │    │   (MemOS 远程调用)        │   │    │
│  │  └─────────────────┘    └──────────────────────────┘   │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼ HTTP REST API
┌─────────────────────────────────────────────────────────────────┐
│                    MemOS-2.0.7 Service                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Neo4j      │  │   Qdrant     │  │   Redis      │          │
│  │  (图数据库)   │  │ (向量存储)    │  │  (调度队列)   │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 端口分配

| 服务 | 端口 | 说明 |
|------|------|------|
| living-agent-service | 8380 | 主服务 |
| MemOS API | **8381** | 记忆系统 API |
| Neo4j HTTP | 7475 | 图数据库管理界面 |
| Neo4j Bolt | 7688 | 图数据库连接 |
| Qdrant HTTP | 6333 | 向量数据库 (复用) |
| Qdrant gRPC | 6334 | 向量数据库 (复用) |

---

## 二、配置说明

### 2.1 application.yml 配置

```yaml
memory:
  enabled: true
  provider: memos  # 可选: sqlite, memos
  
  # SQLite 配置 (本地缓存/兜底)
  sqlite:
    path: ${LIVING_AGENT_DATA_PATH:/app/data}/memory.db
  
  # MemOS 配置
  memos:
    base-url: ${MEMOS_BASE_URL:http://memos:8381}
    timeout: 30000
    default-cube-id: ${MEMOS_DEFAULT_CUBE_ID:living-agent}
    
  # 查询配置
  query:
    max-results: 20
    min-score: 0.3
```

### 2.2 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `MEMOS_BASE_URL` | `http://memos:8381` | MemOS 服务地址 |
| `MEMOS_DEFAULT_CUBE_ID` | `living-agent` | 默认记忆立方 ID |
| `NEO4J_URI` | `bolt://memos-neo4j:7687` | Neo4j 连接地址 |
| `NEO4J_USER` | `neo4j` | Neo4j 用户名 |
| `NEO4J_PASSWORD` | `memos123456` | Neo4j 密码 |

---

## 三、API 映射

### 3.1 Memory 接口与 MemOS API 对应

| Memory 方法 | MemOS API | HTTP 方法 |
|-------------|-----------|-----------|
| `store()` | `/product/add` | POST |
| `recall()` | `/product/search` | POST |
| `get()` | `/product/get_all` | POST |
| `list()` | `/product/get_all` | POST |
| `forget()` | `/product/memories/{id}` | DELETE |

### 3.2 请求示例

#### 存储记忆

```json
POST /product/add
{
  "user_id": "user-001",
  "mem_cube_id": "living-agent",
  "messages": [
    {"role": "user", "content": "用户偏好设置"},
    {"role": "assistant", "content": "已记录用户偏好"}
  ],
  "async_mode": "sync"
}
```

#### 搜索记忆

```json
POST /product/search
{
  "query": "用户偏好",
  "user_id": "user-001",
  "mem_cube_id": "living-agent",
  "top_k": 10
}
```

---

## 四、部署模式

### 4.1 快速模式 (默认)

仅启动核心服务，使用 SQLite 作为记忆后端：

```bash
docker compose up -d
```

### 4.2 完整模式 (推荐)

启动所有服务，包括 MemOS、Neo4j、Qdrant：

```bash
docker compose --profile full up -d
```

---

## 五、MemOS 功能特性

### 5.1 核心能力

| 功能 | 说明 |
|------|------|
| **知识库管理** | 文档/URL 解析，跨项目共享 |
| **多模态记忆** | 文本、图像、图表支持 |
| **记忆反馈** | 自然语言纠正记忆 |
| **工具记忆** | Agent 规划历史存储 |
| **异步调度** | Redis Streams 毫秒级延迟 |

### 5.2 性能指标

- **准确率**: +43.70% vs OpenAI Memory
- **Token 节省**: 35.24%
- **基准测试**: LOCOMO 75.80, LongMemEval +40.43%

---

## 六、故障排查

### 6.1 常见问题

| 问题 | 解决方案 |
|------|----------|
| MemOS 连接失败 | 检查 `MEMOS_BASE_URL` 配置 |
| Neo4j 连接超时 | 确认 Neo4j 服务已启动 |
| 向量搜索无结果 | 检查 Qdrant 数据是否已初始化 |

### 6.2 健康检查

```bash
# 检查 MemOS 服务
curl http://localhost:8381/product/users

# 检查 Neo4j
curl http://localhost:7475

# 检查 Qdrant
curl http://localhost:6333/health
```

---

## 七、参考链接

- [MemOS 官网](https://memos.openmem.net/)
- [MemOS 文档](https://memos-docs.openmem.net/home/overview/)
- [MemOS GitHub](https://github.com/MemTensor/MemOS)
