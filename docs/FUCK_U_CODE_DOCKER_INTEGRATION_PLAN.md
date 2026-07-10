# fuck-u-code Docker 部署与模型守护进程集成改进方案

> **生成日期**: 2026-07-08
> **修订日期**: 2026-07-09（v1.3 — Phase 4 Java层集成完成）
> **目的**: 将 fuck-u-code 作为独立Docker服务部署，对接 Living Agent 模型守护进程，通过MCP协议供数字员工调用代码审查能力
> **关联闭环**: 49（代码审查工作流闭环）P49-A/P49-B/P49-C、33（Claude Code工具闭环）
> **配套文档**: [IMPROVEMENT_PLAN_INDEX.md](IMPROVEMENT_PLAN_INDEX.md)、[IMPROVEMENT_PLAN_L4_BUSINESS_LOOPS.md](IMPROVEMENT_PLAN_L4_BUSINESS_LOOPS.md)
> **实施进度**:
> - Phase 0 ✅ 模型守护进程扩展（model_daemon.py 8392端口 LLM HTTP 端点已添加）
> - Phase 1 ✅ Docker 部署（docker-compose.yml 已添加 fuck-u-code 服务，镜像已构建）
> - Phase 2 ⬜ 模型守护进程对接（需 Docker 环境运行后验证）
> - Phase 3 ✅ MCP 集成（mcp.json 已注册 fuck-u-code MCP Server）
> - Phase 4 ✅ 闭环49打通（FuckUCodeClient + CodeReviewMetricsService基线评分 + CodeReviewQualityOptimizer阈值优化）

---

## 一、背景与动机

### 1.1 为什么需要部署 fuck-u-code

Living Agent 项目已有闭环49（代码审查工作流闭环），包含 P49-A（CodeReviewMetricsService 审查度量）和 P49-B（CodeReviewQualityOptimizer 质量优化），但**缺少自动化的静态分析+AI审查执行层**。

| 环节 | 当前实现 | 缺失能力 |
|------|---------|---------|
| 提交前质量门禁 | 无 | 自动拦截低质量代码 |
| 结构化静态分析 | 无 | 7维度11项指标量化 |
| AI辅助审查 | MainBrain裸LLM调用 | 无结构化指标上下文的泛化建议 |
| 审查度量输入 | CodeReviewMetricsService | 缺少自动化数据采集源 |

### 1.2 fuck-u-code 能力匹配

| fuck-u-code 能力 | 对应闭环49环节 | 价值 |
|-----------------|---------------|------|
| `analyze` — 7维11项指标扫描 | 提交前质量门禁 + 度量数据源 | 自动化量化检测 |
| `ai-review` — 基于指标的AI审查 | AI辅助审查 | 比裸LLM调用更有针对性 |
| MCP Server — stdio协议 | 数字员工工具接入 | 与闭环33（Claude Code工具闭环）对齐 |
| 多格式输出 | 报告归档 | Markdown/JSON可入库 |

### 1.3 核心思路：模型守护进程复用（非 Ollama）

fuck-u-code 的 AI 审查需要 LLM Provider。项目中**已有保证可用的本地模型源**——**模型守护进程**（`model_daemon.py`），它运行在 living-agent-service 容器内，通过 llama.cpp CLI 加载 GGUF 量化模型：

| 模型 | 用途 | 文件路径 | 守护进程角色 |
|------|------|---------|-------------|
| **Qwen3.5-2B-GGUF** | 任务转达、工具调用、部门引导（Layer 3 工具神经元） | `/app/ai-models/Qwen3.5-2B-GGUF/Qwen3.5-2B-Q4_K_M.gguf` | 守护进程常驻，保证可用 |
| Qwen3-0.6B-GGUF | 沟通、表达、高效回复（Layer 2 闲聊神经元） | `/app/ai-models/Qwen3-0.6B-GGUF/Qwen3-0.6B-Q8_0.gguf` | 守护进程常驻，保证可用 |

**为什么不用 Ollama？**

| 问题 | 说明 |
|------|------|
| Ollama 可能未部署 | docker-compose 中 ollama 服务是可选的，非必须启动 |
| 模型不保证存在 | Ollama 需手动 `ollama pull`，模型可能未拉取 |
| 守护进程保证可用 | `model_daemon.py` 随 living-agent-service 启动，模型文件已内置在镜像中 |
| 统一管理 | 守护进程已有 Qwen3.5-2B，无需另拉一份相同模型到 Ollama |

**方案核心**：为模型守护进程新增 OpenAI 兼容 HTTP 端点（`/v1/chat/completions`），fuck-u-code 直接指向该端点使用 Qwen3.5-2B，实现**零外部依赖融合**。

### 1.4 模型守护进程现状分析

`model_daemon.py` 当前的通信方式：

| 通道 | 协议 | 端口/路径 | 用途 |
|------|------|----------|------|
| 命名管道（控制） | FIFO | `/tmp/dialogue_daemon_control_request` + `_response` | Java 服务创建/销毁会话、查询状态 |
| 命名管道（会话） | FIFO | `/tmp/dialogue_daemon_request_{sid}` + `_response_{sid}` | 逐会话的 LLM/TTS/ASR 请求 |
| HTTP（声纹） | REST | `:8391` | 声纹注册/验证/识别 |
| **缺失** | **HTTP OpenAI-Compatible** | **无** | **fuck-u-code 等外部工具需要的标准接口** |

**关键差距**：守护进程已有 HTTP 服务器（8391端口），但仅用于声纹服务，**没有暴露 LLM 的 OpenAI 兼容端点**。

---

## 二、架构设计

### 2.1 整体架构

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Docker Compose 编排                             │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  ┌──────────────────┐    MCP(stdio)    ┌────────────────────────────┐  │
│  │  fuck-u-code     │◄────────────────│  living-agent-service      │  │
│  │  (Node.js 容器)  │                 │  (Java + Python 守护进程)   │  │
│  │                  │                 │                            │  │
│  │  analyze 工具     │                 │  ┌──────────────────────┐ │  │
│  │  ai-review 工具   │                 │  │  model_daemon.py     │ │  │
│  │                  │                 │  │  ┌─ Qwen3-0.6B (闲聊) │ │  │
│  │                  │   HTTP          │  │  ├─ Qwen3.5-2B (工具) │ │  │
│  │                  │   OpenAI兼容    │  │  ├─ ASR (Sherpa)      │ │  │
│  │                  │◄───────────────│  │  ├─ TTS (MeloTTS)     │ │  │
│  │                  │ :8391/v1/      │  │  └─ CAM++ (声纹)      │ │  │
│  └──────────────────┘                 │  │                      │ │  │
│                                       │  │  新增端点:            │ │  │
│                                       │  │  /v1/chat/completions│ │  │
│                                       │  │  /v1/models          │ │  │
│                                       │  └──────────────────────┘ │  │
│                                       │                            │  │
│                                       │  ├─ ClaudeExecutionGateway │  │
│                                       │  ├─ TechBrain              │  │
│                                       │  └─ 闭环49 CodeReview*     │  │
│                                       └────────────────────────────┘  │
│                                                                        │
│  ┌──────────────┐    可选备选                                          │
│  │  Ollama      │    (仅当已部署时使用)                                 │
│  │  :11434/v1   │◄─── fuck-u-code 备选模型源                           │
│  └──────────────┘                                                     │
│                                                                        │
│  数据流:                                                               │
│  1. TechBrain → MCP调用 fuck-u-code → analyze(项目路径)                │
│  2. fuck-u-code tree-sitter解析 → 返回11项指标+评分                     │
│  3. TechBrain → MCP调用 fuck-u-code → ai-review(最差N个文件)           │
│  4. fuck-u-code → HTTP POST model_daemon:8391/v1/chat/completions     │
│     → 守护进程调用 llama.cpp CLI + Qwen3.5-2B → 返回审查报告           │
│  5. 审查结果 → CodeReviewMetricsService(P49-A)记录度量                  │
│  6. 度量趋势 → CodeReviewQualityOptimizer(P49-B)优化规则                │
└────────────────────────────────────────────────────────────────────────┘
```

### 2.2 服务间通信

| 连接 | 协议 | 目标 | 说明 |
|------|------|------|------|
| fuck-u-code → model_daemon | **HTTP OpenAI-Compatible** | `http://living-agent-service:8391/v1` | AI审查**首选**，守护进程保证可用 |
| fuck-u-code → Ollama | HTTP OpenAI-Compatible | `http://ollama:11434/v1` | AI审查备选（仅当 Ollama 已部署） |
| living-agent-service → fuck-u-code | MCP Stdio | `fuck-u-code-mcp` 命令 | 数字员工调用入口 |
| living-agent-service → model_daemon | FIFO 命名管道 | `/tmp/dialogue_daemon_*` | Java服务原有通道，不受影响 |

### 2.3 MCP 配置

在 living-agent-service 的 `.mcp.json`（或容器内 `/app/config/claude/mcp.json`）中注册：

```json
{
  "mcpServers": {
    "fuck-u-code": {
      "command": "npx",
      "args": ["-y", "eff-u-code-mcp"],
      "env": {
        "FUCKUCODE_AI_PROVIDER": "openai",
        "FUCKUCODE_AI_MODEL": "qwen3.5-2b",
        "FUCKUCODE_AI_BASE_URL": "http://living-agent-service:8391/v1",
        "FUCKUCODE_AI_API_KEY": "",
        "FUCKUCODE_LOCALE": "zh"
      }
    }
  }
}
```

> **注意**：`baseUrl` 指向 `living-agent-service:8391`（模型守护进程 HTTP 端口），而非 Ollama。

---

## 三、Docker 部署方案

### 3.1 方案A：轻量 npx 模式（推荐）

**无需构建镜像，利用 npx 直接运行 MCP Server**，适合快速集成。

**优点**：无需维护额外镜像，版本跟随 npm 更新
**缺点**：首次启动需下载 npm 包（约50MB）

#### docker-compose.yml 新增

```yaml
  # ===========================================
  # fuck-u-code - 代码质量分析与AI审查服务
  # 通过MCP协议供living-agent-service调用
  # AI模型使用模型守护进程(model_daemon.py)的 Qwen3.5-2B
  # 无需依赖Ollama，守护进程保证模型可用
  # ===========================================
  fuck-u-code:
    image: node:22-alpine
    container_name: living-agent-fuck-u-code
    hostname: fuck-u-code
    restart: unless-stopped
    environment:
      # AI模型配置 — 使用模型守护进程的 OpenAI 兼容端点（端口8391）
      FUCKUCODE_AI_PROVIDER: openai
      FUCKUCODE_AI_MODEL: qwen3.5-2b
      FUCKUCODE_AI_BASE_URL: http://living-agent-service:8391/v1
      FUCKUCODE_AI_API_KEY: ""
      # 输出语言
      FUCKUCODE_LOCALE: zh
      # 并发数（根据宿主机CPU调整）
      FUCKUCODE_CONCURRENCY: 4
      NODE_ENV: production
    volumes:
      # 挂载项目源码（只读，用于分析）
      - ${WORKSPACE_PATH:-./..}:/workspace:ro
      # 可选：持久化fuck-u-code配置缓存
      - fuck-u-code-cache:/home/node/.cache/fuck-u-code
    networks:
      - backend
    depends_on:
      living-agent-service:
        condition: service_healthy
    command:
      - sh
      - -c
      - |
        echo "Installing eff-u-code..."
        npm install -g eff-u-code
        echo "fuck-u-code ready. MCP server available at fuck-u-code-mcp"
        tail -f /dev/null  # Keep container running for exec
    deploy:
      resources:
        limits:
          memory: 512M
        reservations:
          memory: 128M
```

> **关键变更**：
> - `depends_on` 改为 `living-agent-service`（含模型守护进程），不再依赖 ollama
> - `FUCKUCODE_AI_BASE_URL` 指向 `living-agent-service:8391/v1`（守护进程 HTTP 端口）

#### 使用方式

```bash
# 测试 analyze（静态分析，不需要AI模型）
docker exec living-agent-fuck-u-code fuck-u-code analyze /workspace/docker/living-agent-service/living-agent-core/src/main/java -f json

# 测试 ai-review（需要守护进程HTTP端点就绪）
docker exec living-agent-fuck-u-code fuck-u-code ai-review /workspace/docker/living-agent-service/living-agent-core/src/main/java --top 1
```

### 3.2 方案B：预构建镜像模式（符合项目风格，推荐）

**构建专用 Docker 镜像，预装 fuck-u-code 和 tree-sitter 语法库，保存到 `image/` 目录**。

这种方式符合项目的镜像管理风格（参见 `download_images.py`），避免每次部署时重复下载。

#### 步骤1：创建 Dockerfile

在 `image/Dockerfile.fuck-u-code` 创建：

```dockerfile
# Living Agent Service - fuck-u-code 代码质量分析镜像
# 预装 eff-u-code + tree-sitter 语法库，用于 MCP 调用代码审查
FROM node:22-alpine

LABEL maintainer="Living Agent Team"
LABEL description="fuck-u-code - 代码质量分析+AI审查MCP Server (使用项目模型守护进程)"
LABEL version="2.2.1"

# 设置工作目录
WORKDIR /app

# 安装 fuck-u-code (eff-u-code npm 包)
RUN npm install -g eff-u-code && \
    ln -s /usr/local/lib/node_modules/eff-u-code/bin/fuck-u-code.js /usr/local/bin/fuck-u-code && \
    ln -s /usr/local/lib/node_modules/eff-u-code/bin/fuck-u-code-mcp.js /usr/local/bin/fuck-u-code-mcp

# 验证安装
RUN fuck-u-code --version

# 创建缓存目录
RUN mkdir -p /home/node/.cache/fuck-u-code

# 挂载点（用于分析项目源码）
VOLUME ["/workspace"]

# 默认入口
ENTRYPOINT ["fuck-u-code"]
CMD ["--help"]
```

#### 步骤2：手动构建（首次）

```powershell
cd f:\SoarCloudAI\docker\living-agent-service

# 构建镜像
docker build -f image/Dockerfile.fuck-u-code -t living-agent-fuck-u-code:latest image/

# 保存镜像到 image 目录
docker save living-agent-fuck-u-code:latest -o image/living-agent-fuck-u-code.tar

# 验证镜像大小
dir image\living-agent-fuck-u-code.tar
```

#### 步骤3：添加构建脚本（可选，自动化）

在 `image/download_images.py` 的 `main()` 函数中调用新函数，或在 `pull_service_images()` 中添加：

```python
# 在 images 列表中添加
{"name": "living-agent-fuck-u-code:latest", "file": "living-agent-fuck-u-code.tar",
 "desc": "fuck-u-code 代码质量分析 (~150MB)", "build_local": True},
```

#### 步骤4：更新 load_images.ps1 添加加载命令

在 `image/load_images.ps1` 中添加：

```powershell
# 加载 fuck-u-code 镜像（代码质量分析）
if (Test-Path "living-agent-fuck-u-code.tar") {
    Write-Host "Loading fuck-u-code image..."
    docker load -i living-agent-fuck-u-code.tar
    Write-Host "  [OK] fuck-u-code loaded" -ForegroundColor Green
}
```

#### docker-compose.yml（方案B版）

```yaml
  # ===========================================
  # fuck-u-code - 代码质量分析与AI审查服务
  # 预构建镜像：image/living-agent-fuck-u-code.tar
  # AI模型使用模型守护进程(model_daemon.py)的 Qwen3.5-2B (端口8392)
  # ===========================================
  fuck-u-code:
    image: living-agent-fuck-u-code:latest
    container_name: living-agent-fuck-u-code
    hostname: fuck-u-code
    restart: unless-stopped
    environment:
      FUCKUCODE_AI_PROVIDER: openai
      FUCKUCODE_AI_MODEL: qwen3.5-2b
      FUCKUCODE_AI_BASE_URL: http://living-agent-service:8392/v1
      FUCKUCODE_AI_API_KEY: ""
      FUCKUCODE_LOCALE: zh
      FUCKUCODE_CONCURRENCY: 4
    volumes:
      - ${WORKSPACE_PATH:-./..}:/workspace:ro
      - fuck-u-code-cache:/home/node/.cache/fuck-u-code
    networks:
      - backend
    depends_on:
      living-agent-service:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 512M
        reservations:
          memory: 128M
```

> **关键点**：`baseUrl` 使用 `8392` 端口（模型守护进程的 LLM HTTP 端点），非声纹端口 `8391`。

---

## 四、模型守护进程对接细节（核心）

### 4.1 前提：为守护进程新增 OpenAI 兼容端点

当前 `model_daemon.py` 仅通过命名管道（FIFO）和 HTTP 8391端口（声纹服务）通信。**必须先扩展**，增加 OpenAI 兼容的 LLM HTTP 端点。

**扩展方案**：在 `model_daemon.py` 的 `main()` 函数中，与声纹HTTP服务并行，启动第二个 HTTP Server 监听在 **8392 端口**（或复用8391），暴露以下端点：

| 端点 | 方法 | 说明 | 对应内部逻辑 |
|------|------|------|-------------|
| `/v1/models` | GET | 列出可用模型 | 返回 qwen3-0.6b + qwen3.5-2b 信息 |
| `/v1/chat/completions` | POST | 聊天补全（OpenAI格式） | 内部调用 `manager.generate_text()` → llama.cpp CLI |

#### 扩展代码（添加到 model_daemon.py）

在 `start_speaker_http_server()` 之后，新增 `start_llm_http_server()` 函数：

```python
def start_llm_http_server(model_manager, port=8392):
    """启动LLM的OpenAI兼容HTTP服务 — 供 fuck-u-code 等外部工具调用"""
    from http.server import HTTPServer, BaseHTTPRequestHandler
    
    class LLMOpenAIHandler(BaseHTTPRequestHandler):
        def log_message(self, format, *args): pass
        
        def send_json_response(self, data, status=200):
            self.send_response(status)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps(data, ensure_ascii=False).encode('utf-8'))
        
        def do_OPTIONS(self):
            self.send_response(200)
            self.send_header('Access-Control-Allow-Origin', '*')
            self.send_header('Access-Control-Allow-Methods', 'GET,POST')
            self.send_header('Access-Control-Allow-Headers', 'Content-Type,Authorization')
            self.end_headers()
        
        def do_GET(self):
            if self.path == '/v1/models' or self.path == '/models':
                models = []
                if model_manager.models_loaded.get('qwen35'):
                    models.append({
                        "id": "qwen3.5-2b",
                        "object": "model",
                        "owned_by": "local",
                        "created": int(time.time())
                    })
                if model_manager.models_loaded.get('qwen3'):
                    models.append({
                        "id": "qwen3-0.6b",
                        "object": "model",
                        "owned_by": "local",
                        "created": int(time.time())
                    })
                self.send_json_response({
                    "object": "list",
                    "data": models
                })
            elif self.path in ('/health', '/v1/health'):
                self.send_json_response({
                    "status": "healthy",
                    "qwen35_loaded": model_manager.models_loaded.get('qwen35', False),
                    "qwen3_loaded": model_manager.models_loaded.get('qwen3', False),
                    "llama_cli_available": model_manager.llama_cli_path is not None
                })
            else:
                self.send_json_response({"error": {"message": "Not found"}}, 404)
        
        def do_POST(self):
            if self.path not in ('/v1/chat/completions', '/chat/completions'):
                self.send_json_response({"error": {"message": "Not found"}}, 404)
                return
            
            content_length = int(self.headers.get('Content-Length', 0))
            body = json.loads(self.rfile.read(content_length))
            
            # 解析OpenAI标准请求格式
            model = body.get('model', 'qwen3.5-2b')
            messages = body.get('messages', [])
            max_tokens = body.get('max_tokens', CHAT_CONFIG['max_tokens_tool'])
            temperature = body.get('temperature', CHAT_CONFIG['temperature_tool'])
            
            # 将 messages 数组拼接为 prompt
            prompt_parts = []
            for msg in messages:
                role = msg.get('role', '')
                content = msg.get('content', '')
                prompt_parts.append(f"{role}: {content}")
            prompt = "\n".join(prompt_parts)
            
            # 选择模型
            internal_model = 'qwen35'
            if '0.6' in model or 'qwen3-' in model or model == 'qwen3-0.6b':
                internal_model = 'qwen3'
            
            result = model_manager.generate_text(prompt, model=internal_model,
                                                   max_tokens=max_tokens, temperature=temperature)
            
            if result.get('success'):
                self.send_json_response({
                    "id": f"chatcmpl-{int(time.time())}",
                    "object": "chat.completion",
                    "model": model,
                    "choices": [{
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": result.get('text', '')
                        },
                        "finish_reason": "stop"
                    }],
                    "usage": {
                        "prompt_tokens": len(prompt),
                        "completion_tokens": max_tokens,
                        "total_tokens": len(prompt) + max_tokens
                    },
                    "backend": "model-daemon-llamacpp",
                    "latency_ms": result.get('latency_ms', 0)
                })
            else:
                error_msg = result.get('error', 'Unknown LLM error')
                self.send_json_response({
                    "error": {"message": error_msg, "type": "server_error"}
                }, 500)
    
    server = HTTPServer(('0.0.0.0', port), LLMOpenAIHandler)
    print(f"[ModelDaemon] 🤖 LLM OpenAI兼容HTTP服务启动于端口 {port}", file=sys.stderr, flush=True)
    server.serve_forever()
```

然后在 `main()` 中启动：

```python
# 在 speaker_http_thread 启动后添加：
llm_http_port = int(os.environ.get('LLM_HTTP_PORT', '8392'))
llm_http_thread = threading.Thread(
    target=start_llm_http_server,
    args=(manager, llm_http_port),
    name="LLMOpenAIHttpServer",
    daemon=True
)
llm_http_thread.start()
print(f"[ModelDaemon] 🤖 LLM OpenAI兼容HTTP服务启动于端口 {llm_http_port}", file=sys.stderr, flush=True)
```

### 4.2 模型选择策略

fuck-u-code 的 AI 审查场景特点：**单次请求、中短文本、需要结构化输出**。按项目现有资源排序：

| 优先级 | 模型来源 | 模型名 | 适用原因 | 配置值 |
|--------|---------|--------|---------|--------|
| **P0（首选）** | **模型守护进程** | **`qwen3.5-2b`** | **保证可用、零外部依赖、中文优秀** | `provider=openai, model=qwen3.5-2b, baseUrl=http://living-agent-service:8392/v1` |
| P1（备选） | Ollama本地 | `qwen3.5:2b` | 仅当Ollama已部署且拉取了模型时使用 | `baseUrl=http://ollama:11434/v1` |
| P2（云端备选） | DeepSeek | `deepseek-chat` | 云端备选、性价比高 | `provider=deepseek, baseUrl=https://api.deepseek.com/v1` |

> **P0 为唯一保证可用的选项**。Qwen3.5-2B 已随镜像内置 GGUF 文件，由 `model_daemon.py` 在启动时加载。

### 4.3 推荐默认配置

```json
{
  "ai": {
    "enabled": true,
    "provider": "openai",
    "model": "qwen3.5-2b",
    "baseUrl": "http://living-agent-service:8392/v1",
    "apiKey": ""
  },
  "i18n": {
    "locale": "zh"
  },
  "metrics": {
    "weights": {
      "complexity": 0.32,
      "duplication": 0.20,
      "size": 0.18,
      "structure": 0.12,
      "error": 0.08,
      "documentation": 0.05,
      "naming": 0.05
    }
  }
}
```

### 4.4 动态模型切换

可通过环境变量动态切换模型源，无需重建容器：

```bash
# 默认：使用模型守护进程（保证可用）
FUCKUCODE_AI_BASE_URL=http://living-agent-service:8392/v1
FUCKUCODE_AI_MODEL=qwen3.5-2b

# 备选1：切到Ollama（如果已部署）
FUCKUCODE_AI_BASE_URL=http://ollama:11434/v1
FUCKUCODE_AI_MODEL=qwen3.5:2b

# 备选2：切到云端DeepSeek
FUCKUCODE_AI_PROVIDER=deepseek
FUCKUCODE_AI_MODEL=deepseek-chat
FUCKUCODE_AI_BASE_URL=https://api.deepseek.com/v1
DEEPSEEK_API_KEY=sk-xxx
```

### 4.5 模型健康检查

fuck-u-code 容器启动后应验证守护进程 HTTP 端点可达性：

```bash
# 在 fuck-u-code 容器内执行
# 检查模型守护进程 LLM HTTP 服务是否就绪
curl -sf http://living-agent-service:8392/v1/health > /dev/null && echo "Model Daemon OK" || echo "Model Daemon UNREACHABLE"

# 测试聊天补全接口
curl -sf http://living-agent-service:8392/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen3.5-2b","messages":[{"role":"user","content":"回复OK"}],"max_tokens":4}' \
  > /dev/null && echo "Chat completions OK" || echo "Chat completions FAILED"

# 检查可用模型列表
curl -s http://living-agent-service:8392/v1/models
```

---

## 五、闭环49集成方案

### 5.1 与现有闭环49的关系

```
闭环49: 代码审查工作流闭环（提交→审查→返工→通过→质量度量）

现有实现:
  P49-A CodeReviewMetricsService     ← 审查通过率/返工次数/耗时追踪（已有Java类）
  P49-B CodeReviewQualityOptimizer   ← 基于指标优化规则和预检清单（已有Java类）

新增 fuck-u-code 补强:
  ┌──────────────────────────────────────────────────────┐
  │  提交前预检层 (新增)                                  │
  │  fuck-u-code analyze → 评分 < 60 分文件标记为需返工     │
  │  ↓                                                   │
  │  AI审查层 (新增)                                       │
  │  fuck-u-code ai-review → 最差N个文件的结构化审查报告     │
  │  ↓                                                   │
  │  P49-A 度量层 (已有)                                   │
  │  分析结果 → CodeReviewMetricsService.record()          │
  │  ↓                                                   │
  │  P49-B 优化层 (已有)                                   │
  │  度量趋势 → CodeReviewQualityOptimizer.suggest()        │
  └──────────────────────────────────────────────────────┘
```

### 5.2 Java 层集成点

需要在以下位置添加 fuck-u-code 调用逻辑：

| 文件 | 集成内容 | 说明 |
|------|---------|------|
| `CodeReviewWorkflowService.java` | 审查阶段注入analyze预检 | 在 REVIEWER_REVIEWING 阶段前先跑 analyze |
| `CodeReviewMetricsService.java` | 记录fuck-u-code评分作为基线 | `recordBaselineScore(file, score)` |
| `CodeReviewQualityOptimizer.java` | 用fuck-u-code指标优化阈值 | 权重/阈值基于历史评分分布调整 |
| `TechBrain.java` / `ClaudeExecutionGateway.java` | MCP调用入口 | 数字员工通过MCP调用 fuck-u-code |

### 5.3 调用示例

**场景1：提交前自动质量检查**

```typescript
// 数字员工(TechBrain)通过MCP调用
const analysis = await mcp.call('analyze', {
  path: '/app/workspace/docker/living-agent-service/living-agent-core',
  format: 'json',
  top: 10,
  locale: 'zh'
});

// 过滤低分文件
const poorFiles = analysis.fileResults.filter(f => f.score < 60);
if (poorFiles.length > 0) {
  return {
    status: 'BLOCKED',
    reason: `${poorFiles.length} files below quality threshold`,
    details: poorFiles.map(f => `${f.filePath}: ${f.score}/100`)
  };
}
```

**场景2：AI深度审查最差文件**

```typescript
const review = await mcp.call('ai-review', {
  path: '/app/workspace/docker/living-agent-service/living-agent-core',
  provider: 'openai',
  model: 'qwen3.5-2b',
  baseUrl: 'http://living-agent-service:8392/v1',  // 模型守护进程
  apiKey: '',
  top: 3,
  locale: 'zh'
});

// 结果写入闭环49度量系统
await api.post('/api/code-review/metrics/record', {
  source: 'fuck-u-code-ai-review',
  filesReviewed: review.reviews.length,
  averageScore: avg(review.reviews.map(r => r.score)),
  timestamp: new Date().toISOString()
});
```

---

## 六、MCP Server 注册配置

### 6.1 Claude Code 配置

更新 `living-agent-app/src/main/resources/claude/mcp.json`：

```json
{
  "mcpServers": {
    "memory": { ... },
    "filesystem": { ... },
    "sequential-thinking": { ... },
    "fuck-u-code": {
      "command": "docker",
      "args": [
        "exec", "-i", "living-agent-fuck-u-code",
        "node", "/app/eff-u-code/bin/fuck-u-code-mcp.js"
      ],
      "env": {
        "FUCKUCODE_AI_PROVIDER": "openai",
        "FUCKUCODE_AI_MODEL": "qwen3.5-2b",
        "FUCKUCODE_AI_BASE_URL": "http://living-agent-service:8392/v1",
        "FUCKUCODE_AI_API_KEY": ""
      }
    }
  }
}
```

> 注意：`baseUrl` 指向模型守护进程的 LLM HTTP 端口（8392），非 Ollama。

### 6.2 Cursor / Windsurf 配置

`.cursor/mcp.json` 或类似配置：

```json
{
  "mcpServers": {
    "fuck-u-code": {
      "command": "npx",
      "args": ["-y", "eff-u-code-mcp"],
      "env": {
        "FUCKUCODE_AI_PROVIDER": "openai",
        "FUCKUCODE_AI_MODEL": "qwen3.5-2b",
        "FUCKUCODE_AI_BASE_URL": "http://host.docker.internal:8392/v1"
      }
    }
  }
}
```

---

## 七、安全考量

### 7.1 代码不外泄

- fuck-u-code 的静态分析**全程本地运行**，不上传任何代码到外部服务器
- AI 审查使用**模型守护进程的本地 Qwen3.5-2B**，请求在 Docker 内网中完成，不经过外部网络
- 即使切换到云端模型，也仅发送**指标摘要+最差N个文件的函数签名**，非完整源码

### 7.2 容器隔离

- fuck-u-code 容器以最小权限运行（node:22-alpine，无root）
- 项目源码以 **`:ro`（只读）** 挂载，防止误修改
- 仅加入 `backend` 内部网络，无外部端口暴露

### 7.3 API Key 管理

- 模型守护进程不需要 API Key（本地 GGUF 模型）
- 云端备选模型的 Key 通过环境变量注入，不写死在镜像中
- 建议使用项目的统一密钥管理机制

---

## 八、实施步骤

### Phase 0：模型守护进程扩展（前置条件，1小时）

| 步骤 | 操作 | 验证 |
|------|------|------|
| 0.1 | 在 `model_daemon.py` 中添加 `start_llm_http_server()` 函数 | 函数定义存在 |
| 0.2 | 在 `main()` 中启动 LLM HTTP 线程（8392端口） | 日志出现 `LLM OpenAI兼容HTTP服务启动于端口 8392` |
| 0.3 | 重启 living-agent-service | `curl http://localhost:8392/v1/health` 返回 healthy |
| 0.4 | 测试 chat completions | `curl -X POST http://localhost:8392/v1/chat/completions` 返回正确格式 |
| 0.5 | 确保 8392 端口在 Docker 网络中可访问 | 从其他容器 `curl http://living-agent-service:8392/v1/models` |

### Phase 1：基础部署（1小时）

| 步骤 | 操作 | 验证 |
|------|------|------|
| 1.1 | 在 docker-compose.yml 中添加 fuck-u-code 服务定义 | `docker compose config` 验证语法 |
| 1.2 | 启动容器 `docker compose up -d fuck-u-code` | 容器 running 状态 |
| 1.3 | 执行 `docker exec fuck-u-code fuck-u-code --version` | 版本号输出正确 |
| 1.4 | 测试 analyze：对 living-agent-core 目录扫描 | JSON输出含 overallScore |

### Phase 2：模型守护进程对接（30分钟）

| 步骤 | 操作 | 验证 |
|------|------|------|
| 2.1 | 验证守护进程 LLM HTTP 端口可达 | `docker exec fuck-u-code curl -s http://living-agent-service:8392/v1/health` |
| 2.2 | 确认环境变量指向守护进程 | `docker exec fuck-u-code env \| grep FUCKUCODE` |
| 2.3 | 测试 ai-review（单个小文件） | 返回Markdown格式的审查报告 |

### Phase 3：MCP集成（30分钟）

| 步骤 | 操作 | 验证 |
|------|------|------|
| 3.1 | 更新 claude/mcp.json 添加 fuck-u-code | JSON格式校验通过 |
| 3.2 | 重启 living-agent-service | MCP Server列表包含 fuck-u-code |
| 3.3 | 从 Claude Code 调用 `analyze` 工具 | 正常返回结果 |

### Phase 4：闭环49打通（2小时）

| 步骤 | 操作 | 验证 |
|------|------|------|
| 4.1 | CodeReviewWorkflowService 注入 analyze 预检 | 低分文件被拦截 |
| 4.2 | CodeReviewMetricsService 记录基线分数 | 数据写入 metrics 表 |
| 4.3 | CodeReviewQualityOptimizer 消费评分数据 | 阈值建议生成 |

---

## 九、验收标准

| 编号 | 验收项 | 方法 | 通过条件 |
|------|--------|------|---------|
| V0 | **守护进程 LLM HTTP 端点可用** | `curl http://living-agent-service:8392/v1/health` | 返回 healthy + qwen35_loaded=true |
| V0b | **OpenAI chat/completions 格式正确** | `curl -X POST :8392/v1/chat/completions` | 返回标准 OpenAI choices 格式 |
| V1 | 容器正常运行 | `docker ps \| grep fuck-u-code` | UP状态，无restart |
| V2 | analyze可用 | `docker exec fuck-u-code fuck-u-code analyze /workspace -f json` | 返回overallScore 0-100 |
| V3 | ai-review可用（模型守护进程） | `docker exec fuck-u-code fuck-u-code ai-review /workspace --top 1` | 返回审查报告 |
| V4 | MCP工具注册 | Claude Code `/mcp` 命令 | 显示 fuck-u-code 的 analyze + ai-review |
| V5 | 模型守护进程复用确认 | 检查网络流量 | ai-review 请求走 `living-agent-service:8392` 非外网 |
| V6 | 闭环49度量集成 | CodeReviewMetricsService 日志 | 出现 `fuck-u-code-baseline` 来源标记 |
| V7 | 中文输出正常 | analyze 输出 | 中文提示词和说明 |

---

## 十、后续扩展方向

### 10.1 GitLab MR 集成（增强）

- Webhook触发：MR创建时自动运行 analyze
- 评论反馈：将低分文件评论自动发布到MR
- 状态检查：GitLab Merge Request Quality Gate

### 10.2 定制化规则（增强）

- 基于 CODE_STRUCTURE_AND_FILE_GUIDE.md 的项目规则定制权重
- Java Spring Boot 特定规则（Bean命名规范、权限隔离检查等）
- 自定义 .fuckucoderc.json 放入项目根目录

### 10.3 历史趋势（增强）

- 每次分析结果持久化到 PostgreSQL
- 跨版本对比图表（前端Dashboard展示）
- 与闭环40（项目管理闭环）的健康度联动

### 10.4 增量分析（远期）

- 目前仅支持全量扫描，未来可增加 git diff 范围分析
- 仅审查变更文件，适配大型项目

---

## 十一、文档维护信息

| 字段 | 值 |
|------|---|
| 文档版本 | v1.3 |
| 生成日期 | 2026-07-08 |
| 修订说明 | v1.3 — Phase 4 Java层集成完成（FuckUCodeClient+CodeReviewMetricsService+CodeReviewQualityOptimizer）；v1.2 — Phase 0/1/3 已实施（model_daemon.py 8392端口+docker-compose+MCP配置）；v1.1 — 修正模型源为模型守护进程(model_daemon.py) |
| 关联闭环 | 49（代码审查工作流闭环）、33（Claude Code工具闭环） |
| 依赖组件 | fuck-u-code >= 2.2.1、模型守护进程(model_daemon.py + Qwen3.5-2B-GGUF)、Node.js 22 |
| 关键文件 | `scripts/python/model_daemon.py`（已扩展8392端口LLM HTTP端点）、`docker-compose.yml`（已添加fuck-u-code服务）、`mcp.json`（已注册fuck-u-code MCP）、`codereview/client/FuckUCodeClient.java`（P49-C Java集成） |
| 维护责任人 | 待分配 |
