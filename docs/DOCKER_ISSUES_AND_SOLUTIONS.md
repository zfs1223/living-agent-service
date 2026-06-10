# Docker 后端服务问题清单与解决方案

> 生成时间: 2026-05-03
> 分析范围: living-agent-service 容器日志、Dockerfile、依赖配置

---

## 问题总览

| # | 严重程度 | 问题 | 影响范围 |
|---|----------|------|----------|
| 1 | 🔴 严重 | PlaywrightCrawlerTool 启动失败 — 缺少浏览器系统依赖库 | 网页爬虫工具完全不可用 |
| 2 | 🔴 严重 | 所有大脑模型解析失败 — `qwen` provider 未注册 | 所有9个部门大脑回退到 fallback 模型 |
| 3 | 🟡 中等 | python-wheels.tar 缺少核心 Python 依赖 | Office 技能(docx/pptx/xlsx)运行失败 |
| 4 | 🟡 中等 | Docker Sandbox 不可用 | 代码沙箱功能失效 |
| 5 | 🟡 中等 | 9个工具未注册 | jira/gitlab/jenkins/claude_cli 工具不可用 |
| 6 | 🟡 中等 | Memos 容器不健康 — LLM 配置缺失 | 记忆系统无法启动 |
| 7 | 🟢 低 | 飞书工具未配置 | 飞书集成不可用 |
| 8 | 🟢 低 | Hibernate 配置警告 | 日志噪音 |

---

## 问题 1：PlaywrightCrawlerTool 启动失败（🔴 严重）

### 现象

```
PlaywrightCrawlerTool not available: Error {
  Host system is missing dependencies to run browsers.
  Please install them with the following command:
    sudo apt-get install libnss3 libnspr4 libatk1.0-0 libatk-bridge2.0-0
      libcups2 libatspi2.0-0 libxcomposite1 libxdamage1
}
```

### 根因分析

项目中存在**两套不同的 Playwright**，但构建逻辑只处理了 Python 版本：

| 维度 | Python Playwright | Java Playwright |
|------|-------------------|-----------------|
| 包来源 | `pip install playwright==1.49.1` | `com.microsoft.playwright:playwright:1.40.0` (Maven) |
| 使用者 | Python 技能 (webapp-testing 等) | `PlaywrightCrawlerTool.java` |
| 浏览器 | Dockerfile.system-deps 安装了 Firefox + FFmpeg | 运行时自动下载 Chromium + Firefox + Webkit + FFmpeg |
| 系统依赖 | ❌ 未安装 | ❌ 未安装 |

**构建流程问题：**

1. `Dockerfile.system-deps` 第 46-51 行：
   - `pip download --dest /wheels playwright==1.49.1` → 下载 wheel 到 `/wheels`，但**从未被使用**
   - `pip install playwright && playwright install firefox ffmpeg && pip uninstall -y playwright` → 临时安装 Python Playwright，下载 Firefox 浏览器二进制，然后**卸载 Python 包**
   - Python Playwright 被卸载后，Python 技能也无法使用 `from playwright.sync_api import sync_playwright`

2. `PlaywrightCrawlerTool.java` 使用 Java Playwright 1.40.0，启动时：
   - 自己从网络下载 Chromium、Firefox、Webkit、FFmpeg（**离线部署时不可用**）
   - 尝试启动 Chromium 时失败，因为缺少系统依赖库

3. `Dockerfile.local` 基于 `living-agent-system-deps:1.0`，**没有任何 Playwright 相关安装步骤**

### 解决方案

#### 步骤 1：在 Dockerfile.system-deps 中添加浏览器系统依赖库

修改 `image/download_images.py` 中 `build_system_deps_image()` 函数的 `dockerfile_content`（约第 490 行）：

```dockerfile
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl bash git python3.11 python3.11-venv python3.11-dev python3-pip \
    docker.io nodejs npm openssh-client ca-certificates \
    libgcc-s1 libopus-dev opus-tools ffmpeg libsqlite3-dev \
    build-essential cmake \
    # Playwright 浏览器依赖 (Chromium + Firefox)
    libnss3 libnspr4 libatk1.0-0 libatk-bridge2.0-0 \
    libcups2 libatspi2.0-0 libxcomposite1 libxdamage1 \
    libxrandr2 libgbm1 libpango-1.0-0 libcairo2 \
    libasound2 libxshmfence1 libxfixes3 libxkbcommon0 \
    libdrm2 \
    && rm -rf /var/lib/apt/lists/*
```

#### 步骤 2：预安装 Java Playwright 的 Chromium 浏览器

在 Dockerfile.system-deps 中，将 Python Playwright 安装步骤替换为 Java Playwright 的浏览器预安装：

```dockerfile
# 预安装 Playwright 浏览器 (Chromium，用于 Java PlaywrightCrawlerTool)
# Java Playwright 1.40.0 对应 Chromium 119
# 使用 Python playwright CLI 下载浏览器二进制，然后卸载 Python 包
RUN pip install --no-cache-dir playwright==1.40.0 && \
    playwright install chromium && \
    pip uninstall -y playwright && \
    rm -rf ~/.cache/pip
```

> **注意：** 版本号改为 `1.40.0`，与 Java Playwright 版本一致，确保下载的浏览器版本兼容。

#### 步骤 3：设置 Playwright 浏览器缓存目录的环境变量

在 Dockerfile.system-deps 中添加：

```dockerfile
ENV PLAYWRIGHT_BROWSERS_PATH=/home/livingagent/.cache/ms-playwright
```

#### 步骤 4：删除无效的 `pip download` 步骤

删除 Dockerfile.system-deps 中第 46-48 行：

```dockerfile
# 删除以下无效步骤：
# RUN pip download --dest /wheels \
#     --index-url https://pypi.tuna.tsinghua.edu.cn/simple/ \
#     playwright==1.49.1
```

这个 `pip download` 下载的 wheel 文件从未被任何后续步骤使用，是浪费构建时间和空间。

#### 步骤 5（可选）：如需 Python Playwright 技能支持

如果 `tech/webapp-testing` 技能需要在容器内使用 Python Playwright，需要在 `Dockerfile.local` 的 `pip3 install` 中添加 `playwright`：

```dockerfile
pip3 install --no-cache-dir --break-system-packages --no-index --find-links=/tmp/wheels \
    ... (已有的包) \
    playwright \
    ...
```

同时在 `download_images.py` 的 `build_python_deps()` 中添加 `playwright==1.49.1` 到下载列表。

---

## 问题 2：大脑模型解析失败 — 部分大脑未分配模型（🔴 严重）

### 现象

```
Selector model provider qwen not found, cannot build resolved model for brain neuron://admin/admin-brain/001
No default model found for brain neuron://admin/admin-brain/001, returning fallback
```

影响除 main 和 tech 之外的7个部门大脑：admin, cs, finance, hr, legal, ops, sales。

### 根因分析

`BrainModelResolver` 的解析流程采用**三级降级策略**：

```
resolve(brainId)
  ├── 1. resolveFromAssignment() → 从 brain_model_assignments 表查找显式分配
  ├── 2. resolveFromSelector()   → 从 BrainModelSelectorManager 查找选择器推荐
  └── 3. resolveDefault()        → 硬编码兜底，查找 "qwen" provider
```

**实际数据库状态（已验证）：**

通过 API 查询确认，数据库中**已有**模型池数据：

- **Provider**：`qwen`(DashScope)、`ollama`(本地)、`siliconflow`(硅基流动)、`openrouter` 等，均已存在且启用
- **Model**：已有大量模型（qwen3.5-27b、qwen3.5-14b、siliconflow 上的各种模型、openrouter 上的免费模型等）
- **大脑分配**：只有2个大脑有显式分配：
  - `neuron://core/main-brain/001` → `qwen2.5:3b`（ollama provider）
  - `neuron://tech/tech-brain/001` → `qwen2.5-coder:3b`（ollama provider）
  - **其余7个大脑没有分配**

**错误原因分析：**

1. **第1级 `resolveFromAssignment()`**：7个大脑没有显式分配 → 降级
2. **第2级 `resolveFromSelector()`**：选择器默认推荐 `qwen` provider 的模型，但 `qwen` provider 的 baseUrl 是 `https://dashscope.aliyuncs.com/compatible-mode/v1`（云端），**没有配置 API Key** → 无法调用
3. **第3级 `resolveDefault()`**：同样查找 `qwen` provider，同样因 API Key 为空而无法调用

**关键发现**：问题不是"数据库中没有 provider"，而是：
1. **7个大脑没有分配模型** — 需要通过前端"大脑配置"页面分配
2. **`qwen` provider 的 API Key 未配置** — DashScope 云端 API 需要 API Key
3. **`resolveDefault()` 硬编码了 `qwen` provider** — 即使有其他可用 provider（如 ollama），兜底逻辑也不会使用它们

### 解决方案

前端已有完整的模型池配置页面（`ModelPoolProviders.tsx`）和大脑配置页面（`BrainConfig.tsx`），不需要手动调用 API。

#### 方案 A：通过前端"大脑配置"页面为7个大脑分配模型（推荐，立即可用）

1. 打开前端 → 企业设置 → "大脑配置" Tab
2. 为每个未分配的大脑选择合适的模型：
   - `AdminBrain` → 选择 `ollama/qwen3.5:9b` 或其他可用模型
   - `HrBrain` → 选择 `ollama/qwen3.5:9b` 或其他可用模型
   - `FinanceBrain` → 选择 `ollama/qwen3.5:9b` 或其他可用模型
   - `SalesBrain` → 选择 `ollama/qwen3.5:9b` 或其他可用模型
   - `CsBrain` → 选择 `ollama/qwen3.5:9b` 或其他可用模型
   - `OpsBrain` → 选择 `ollama/qwen3.5:9b` 或其他可用模型
   - `LegalBrain` → 选择 `ollama/qwen3.5:9b` 或其他可用模型

> **注意**：当前 main-brain 和 tech-brain 已分配了 `qwen2.5:3b` 和 `qwen2.5-coder:3b`，这些是 Ollama 上的小模型。如果有更大模型可用（如 `qwen3.5:27b`），建议替换。

#### 方案 B：修改 `resolveDefault()` 兜底逻辑，使用已配置的 ollama provider

当前 `resolveDefault()` 硬编码查找 `qwen` provider，但 `qwen` 是云端 DashScope（需要 API Key）。修改为优先使用本地 `ollama` provider：

```java
// BrainModelResolver.java 修改
public ResolvedBrainModel resolveDefault(String brainId) {
    String brainType = getBrainType(brainId);
    String defaultModelName = getDefaultModelName(brainType);

    // 优先查找 ollama provider（本地部署，保证可用）
    Optional<ResolvedBrainModel> ollamaModel = resolveFromProvider("ollama", "qwen3.5:9b");
    if (ollamaModel.isPresent()) {
        return ollamaModel.get();
    }

    // 其次查找 qwen provider（云端 DashScope）
    Optional<ResolvedBrainModel> qwenModel = resolveFromProvider("qwen", defaultModelName);
    if (qwenModel.isPresent()) {
        return qwenModel.get();
    }

    // 最终 fallback：硬编码本地 ollama
    return new ResolvedBrainModel(null, "ollama", "qwen3.5:9b", "Qwen3.5-9B (本地兜底)",
        "http://host.docker.internal:11434/v1", "",
        Protocol.OPENAI_COMPATIBLE, 32768, 4096, 0.7, true);
}

private Optional<ResolvedBrainModel> resolveFromProvider(String providerId, String modelName) {
    return modelRepo.findByProviderIdAndModelName(providerId, modelName)
        .map(model -> providerRepo.findById(providerId)
            .filter(ProviderConfig::isEnabled)
            .map(provider -> buildResolvedModel(model, provider))
            .orElse(null));
}
```

#### 方案 C：为 `qwen` provider 配置 API Key

如果需要使用 DashScope 云端 API，在前端"模型池"页面为 `qwen` provider 配置 API Key：

1. 打开前端 → 企业设置 → "模型池" Tab
2. 找到 `qwen` provider → 编辑 → 填入 DashScope API Key
3. 测试连接确认可用

#### 方案 D：使用 model_daemon.py 的 Qwen3.5-2B 作为最终兜底

参见下方"方案 E"的详细说明。

### 方案对比

| 方案 | 改动量 | 需要网络 | 工具调用支持 | 推荐场景 |
|------|--------|---------|-------------|---------|
| A: 前端分配模型 | 无代码改动 | 取决于选择的 provider | ✅ | **立即可用，推荐首选** |
| B: 修改 resolveDefault() | 小（改1个Java文件） | ❌ 本地 ollama | ✅ | 保证兜底可用 |
| C: 配置 qwen API Key | 无代码改动 | ✅ 云端 | ✅ | 需要云端大模型 |
| D: model_daemon.py 兜底 | 中 | ❌ 本地 | ❌ Prompt引导 | 保证兜底可用 |

### 方案 E：使用 model_daemon.py 的 Qwen3.5-2B 作为兜底（推荐兜底方案）

**核心思路**：model_daemon.py 已预加载 Qwen3.5-2B 模型，且随容器启动，**保证一定可用**。但它的调用通道（NamedPipe）与大脑的调用通道（HTTP）不同，需要桥接。

#### 架构差异说明

```
当前大脑调用链路（需要 HTTP 端点）:
  Brain → ProviderFactory → ResolvedBrainModelProvider → HTTP POST /v1/chat/completions
                                                                      ↑ 需要 HTTP 服务

model_daemon.py 的调用链路（NamedPipe，无 HTTP）:
  QwenProvider → ModelManager → NamedPipe → model_daemon.py → llama.cpp CLI
                                                                      ↑ 已加载 Qwen3.5-2B
```

#### 实现方案 E-1：修改 ProviderFactory，兜底时回退到 QwenProvider（最简单）

修改 `ProviderFactory.java`，当 `BrainModelResolver` 解析失败时，回退到 `ProviderRegistry` 中的 `QwenProvider` Spring Bean：

```java
// ProviderFactory.java 修改
public class ProviderFactory {

    private final BrainModelResolver brainModelResolver;
    private final ProviderRegistry providerRegistry;  // 新增

    public ProviderFactory(BrainModelResolver brainModelResolver,
                           ProviderRegistry providerRegistry) {  // 新增参数
        this.brainModelResolver = brainModelResolver;
        this.providerRegistry = providerRegistry;
    }

    public Provider create(String brainId) {
        // 1. 尝试通过 BrainModelResolver 解析
        ResolvedBrainModel resolvedModel = brainModelResolver.resolve(brainId);
        if (resolvedModel != null) {
            Provider provider = createFromResolvedModel(resolvedModel);
            if (provider != null) {
                return provider;
            }
        }

        // 2. 解析失败，回退到 ProviderRegistry 中的本地 Provider
        log.warn("BrainModelResolver 解析失败，回退到本地 Provider: brainId={}", brainId);

        // 优先使用 OllamaProvider（支持原生工具调用）
        Provider ollamaProvider = providerRegistry.get("ollama").orElse(null);
        if (ollamaProvider != null) {
            log.info("使用 OllamaProvider 作为兜底 Provider: brainId={}", brainId);
            return ollamaProvider;
        }

        // 最后兜底：使用 QwenProvider（model_daemon.py，保证可用）
        Provider qwenProvider = providerRegistry.get("qwen").orElse(null);
        if (qwenProvider != null) {
            log.info("使用 QwenProvider (model_daemon.py) 作为最终兜底: brainId={}", brainId);
            return qwenProvider;
        }

        log.error("没有可用的 Provider: brainId={}", brainId);
        return null;
    }
}
```

**优点**：
- 代码改动最小（只改 ProviderFactory）
- model_daemon.py 随容器启动，**Qwen3.5-2B 保证可用**
- 不需要额外的 HTTP 服务
- 回退优先级：数据库配置 → Ollama → QwenProvider（model_daemon.py）

**缺点**：
- Qwen3.5-2B 是 2B 参数小模型，能力远弱于 27B 模型
- 使用 Prompt 引导工具调用（非原生 function calling），工具调用准确率较低
- 但作为**兜底方案**，比完全没有 Provider 好得多

#### 实现方案 E-2：为 model_daemon.py 添加 HTTP 端点（更完整）

在 `model_daemon.py` 中添加一个轻量 HTTP 服务器，实现 OpenAI `/v1/chat/completions` 接口，复用已加载的 Qwen3.5-2B 模型：

```python
# model_daemon.py 新增 HTTP 服务器
from http.server import HTTPServer, BaseHTTPRequestHandler
import threading

class OpenAICompatHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/v1/chat/completions':
            content_length = int(self.headers['Content-Length'])
            body = json.loads(self.rfile.read(content_length))
            
            # 复用已有的 Qwen3.5-2B 会话生成文本
            messages = body.get('messages', [])
            prompt = build_prompt_from_messages(messages)
            response_text = generate_with_qwen35(prompt)
            
            # 返回 OpenAI 格式响应
            response = {
                "choices": [{"message": {"content": response_text, "role": "assistant"}}],
                "model": body.get("model", "qwen3.5-2b"),
                "usage": {"prompt_tokens": 0, "completion_tokens": 0}
            }
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(response).encode())

# 在 model_daemon.py 主函数中启动 HTTP 服务器
http_server = HTTPServer(('0.0.0.0', 8390), OpenAICompatHandler)
threading.Thread(target=http_server.serve_forever, daemon=True).start()
```

然后在 `resolveDefault()` 的硬编码兜底中指向这个 HTTP 端点：

```java
// BrainModelResolver.resolveDefault() 修改
return new ResolvedBrainModel(null, "local", "qwen3.5-2b", "Qwen3.5-2B (本地兜底)",
    "http://localhost:8390/v1", "",
    Protocol.OPENAI_COMPATIBLE, 16384, 1024, 0.7, false);
```

**优点**：
- 走标准 HTTP 通道，兼容 ResolvedBrainModelProvider
- model_daemon.py 已加载模型，无需额外加载
- 保证可用（随容器启动）

**缺点**：
- 需要修改 model_daemon.py（添加 HTTP 服务器）
- Qwen3.5-2B 工具调用能力有限
- 需要正确处理并发请求（model_daemon.py 当前是单线程处理）

#### 推荐组合方案

**短期**：方案 E-1（修改 ProviderFactory 回退到 QwenProvider），保证系统可用
**中期**：方案 A（配置 Ollama + qwen3.5:27b），获得完整的工具调用支持
**长期**：方案 D（配置化默认 Provider），使架构更灵活

---

## 问题 3：python-wheels.tar 缺少核心 Python 依赖（🟡 中等）

### 现象

Office 技能（docx/pptx/xlsx）的 Python 脚本 import 了未预装的包，运行时会报 `ModuleNotFoundError`。

### 缺失的核心依赖

以下包在容器本地运行的核心技能脚本中被直接 import，缺失会导致运行时错误：

| 缺失包名 | 严重程度 | 影响范围 | 来源文件 |
|-----------|----------|----------|----------|
| `defusedxml` | 高 | docx/pptx/xlsx 三个 Office 技能 | `admin/docx-official/scripts/document.py` 等 7 个文件 |
| `Pillow` | 高 | pptx-official 缩略图生成 | `admin/pptx-official/scripts/inventory.py` |
| `six` | 中 | pptx-official 幻灯片重排 | `admin/pptx-official/scripts/rearrange.py` |
| `markitdown` | 中 | pptx-official 文本提取 | `admin/pptx-official/SKILL.md` |
| `pyyaml` | 中 | 全局依赖、evaluation_manager | `SKILL_DEPENDENCIES.yml` |
| `python-dotenv` | 中 | hugging-face-evaluation / paper-publisher | `tech/hugging-face-evaluation/requirements.txt` |
| `markdown-it-py` | 中 | hugging-face-evaluation 表格解析 | `tech/hugging-face-evaluation/scripts/evaluation_manager.py` |
| `markdown` | 中 | 全局依赖声明 | `SKILL_DEPENDENCIES.yml` |
| `llama-cpp-python` | 低 | LLM 回退方案 | `scripts/python/llm/run_qwen3.py` |

### 可选依赖（远程执行/特定场景）

以下包主要用于 HuggingFace Jobs 远程执行（通过 `uv run` + PEP 723 声明），不在容器本地直接 import：

`anthropic`, `mcp`, `duckdb`, `pandas`, `datasets`, `trl`, `peft`, `trackio`, `accelerate`, `unsloth`, `vllm`, `lighteval`, `inspect-ai`, `openai`, `gguf`, `sentencepiece`, `protobuf`, `feedparser`, `playwright`, `polars`, `tqdm`, `scikit-learn`

### 解决方案

#### 步骤 1：在 download_images.py 的 build_python_deps() 中添加缺失包

在 `Dockerfile.python-deps` 的 `pip download` 命令中追加：

```dockerfile
# Office 技能核心依赖
RUN pip download --dest /wheels \
    --index-url https://pypi.tuna.tsinghua.edu.cn/simple/ \
    defusedxml==0.7.1 \
    Pillow==11.3.0 \
    six==1.17.0 \
    markitdown==0.1.1

# 全局/评估依赖
RUN pip download --dest /wheels \
    --index-url https://pypi.tuna.tsinghua.edu.cn/simple/ \
    pyyaml==6.0.2 \
    python-dotenv==1.2.1 \
    markdown-it-py==3.0.0 \
    markdown==3.8
```

#### 步骤 2：在 Dockerfile.local 的 pip3 install 中追加包名

修改 `Dockerfile.local` 第 82-90 行的 `pip3 install` 命令：

```dockerfile
pip3 install --no-cache-dir --break-system-packages --no-index --find-links=/tmp/wheels \
    pypdf pdfplumber python-docx openpyxl python-pptx huggingface-hub requests beautifulsoup4 lxml \
    sherpa-ncnn soundfile scipy numpy \
    torch torchaudio transformers \
    librosa Unidecode phonemizer \
    cn2an eng-to-ipa jieba pypinyin \
    mecab-python3 fugashi jaconv \
    nltk unidic-lite num2words pykakasi g2p-en anyascii jamo gruut g2pkk pydub inflect langid loguru cached-path python-mecab-ko python-mecab-ko-dic \
    funasr modelscope \
    defusedxml Pillow six markitdown pyyaml python-dotenv markdown-it-py markdown \
    ...
```

---

## 问题 4：Docker Sandbox 不可用（🟡 中等）

### 现象

```
Docker is not available: dockerCmdExecFactory was not specified
DockerSandboxService initialized but Docker is not available
```

### 根因分析

`docker-compose.yml` 中 Docker socket 以**只读模式**挂载：

```yaml
- /var/run/docker.sock:/var/run/docker.sock:ro
```

同时，Java 的 `docker-java` 库需要正确配置才能连接 Docker daemon。当前配置缺少 `dockerCmdExecFactory` 设置。

### 解决方案

#### 方案 A：如果需要完整的 Docker 沙箱功能

1. 修改 `docker-compose.yml`，去掉 `:ro` 限制：

```yaml
- /var/run/docker.sock:/var/run/docker.sock
```

2. 在 Java 配置中添加 docker-java 连接配置：

```yaml
# application.yml
docker:
  host: unix:///var/run/docker.sock
  tls-verify: false
```

#### 方案 B：如果不需要 Docker 沙箱功能

保持现状，但将日志级别从 ERROR 降为 INFO，避免启动时产生误导性错误日志。

---

## 问题 5：9个工具未注册（🟡 中等）

### 现象

```
Fixed employee C02 configured tool jira -> jira but target tool is not registered
Fixed employee T01 configured tool gitlab -> gitlab but target tool is not registered
Fixed employee T03 configured tool jenkins -> jenkins but target tool is not registered
Fixed employee T03 configured tool gitlab -> gitlab but target tool is not registered
Fixed employee T03 configured tool claude_cli -> claude_cli but target tool is not registered
Fixed employee T02 configured tool gitlab -> gitlab but target tool is not registered
Fixed employee T02 configured tool jira -> jira but target tool is not registered
Fixed employee T09 configured tool gitlab -> gitlab but target tool is not registered
Fixed employee T10 configured tool gitlab -> gitlab but target tool is not registered
```

### 未注册工具汇总

| 工具名 | 引用员工 | 说明 |
|--------|----------|------|
| `jira` | C02, T02 | Jira 项目管理 |
| `gitlab` | T01, T03, T02, T09, T10 | GitLab 代码管理 |
| `jenkins` | T03 | Jenkins CI/CD |
| `claude_cli` | T03 | Claude CLI 工具 |

### 解决方案

#### 已实施：注册 JiraTool / GitLabTool / JenkinsTool / OpenProjectTool

**代码修改：**

1. **`LivingAgentCoreConfig.java`** — 添加条件注册逻辑：
   - `OpenProjectTool`（优先）：当 `tool.openproject.base-url` 配置时注册，工具名为 `"jira"`
   - `JiraTool`（备选）：当 `tool.jira.base-url` 配置时注册，工具名为 `"jira"`
   - `GitLabTool`：当 `tool.gitlab.base-url` 配置时注册，支持无 Token 匿名访问
   - `JenkinsTool`：当 `tool.jenkins.base-url` 配置时注册，支持无 Token 匿名访问

2. **`OpenProjectTool.java`** — 新建文件，实现与 JiraTool 相同的操作接口：
   - 工具名：`"jira"`（与 JiraTool 相同，无缝替换）
   - 操作：`search_issue`, `get_issue`, `create_issue`, `update_issue`, `add_comment`, `search_user`
   - API：OpenProject REST API v3（`/api/v3/work_packages`）
   - 认证：`apikey:{token}` Basic Auth（Token 可选，本地部署可匿名）

3. **`JiraTool.java` / `JenkinsTool.java`** — 修改认证为可选：
   - `addAuth()` 方法：当 Token 为空时不添加 Authorization 头
   - 支持本地部署的无认证模式

4. **`GitLabTool.java`** — 修改认证为可选：
   - `doGet()` / `doPost()`：当 accessToken 为空时不添加 PRIVATE-TOKEN 头

5. **`application.yml`** — 添加工具配置：
   ```yaml
   tool:
     openproject:
       base-url: ${OPENPROJECT_BASE_URL:}
       api-token: ${OPENPROJECT_API_TOKEN:}
     jira:
       base-url: ${JIRA_BASE_URL:}
       email: ${JIRA_EMAIL:}
       api-token: ${JIRA_API_TOKEN:}
     gitlab:
       base-url: ${GITLAB_BASE_URL:}
       access-token: ${GITLAB_ACCESS_TOKEN:}
     jenkins:
       base-url: ${JENKINS_BASE_URL:}
       username: ${JENKINS_USERNAME:}
       api-token: ${JENKINS_API_TOKEN:}
   ```

6. **`docker-compose.yml`** — 添加 Docker 服务：
   - Jenkins：`jenkins/jenkins:lts`，端口 8384
   - GitLab CE：`gitlab/gitlab-ce:latest`，端口 8385
   - OpenProject：`openproject/openproject:15`，端口 8386（替代 Jira）

7. **`FixedEmployeeRegistry.java`** — 更新 TOOL_ALIAS：
   - `gitlab_tool → gitlab`（原为 `trae`）
   - `jenkins_tool → jenkins`（原为 `browser_automation`）
   - `jira_tool → jira`（原为 `trae`）

#### OpenProject 替代 Jira 的设计说明

| 对比项 | Jira | OpenProject |
|--------|------|-------------|
| 许可证 | 需要付费许可证 | ✅ 完全免费开源 |
| Docker 部署 | `atlassian/jira-software` (需许可证) | `openproject/openproject:15` (免费) |
| REST API | Jira API v3 | OpenProject API v3 |
| 认证方式 | email + API Token | `apikey:{token}` Basic Auth |
| 工具名 | `"jira"` | `"jira"`（相同，无缝替换） |
| 固定员工分配 | T02真构、C02真修 | 无需修改，自动使用 OpenProjectTool |

**注册优先级**：OpenProject（优先）> Jira（备选），二选一：
- 当 `OPENPROJECT_BASE_URL` 配置时 → 注册 OpenProjectTool
- 否则当 `JIRA_BASE_URL` 配置时 → 注册 JiraTool
- 都未配置 → 不注册 jira 工具

**固定员工无需修改**：OpenProjectTool 使用与 JiraTool 相同的工具名 `"jira"`，所有引用 `jira` 工具的固定员工（T02真构、C02真修）会自动使用 OpenProjectTool 实现。

---

## 问题 6：Memos 容器不健康（🟡 中等）

### 现象

```
pydantic_core._pydantic_core.ValidationError: 2 validation errors for InternetRetrieverConfigFactory
reader.llm.model_name_or_path - Input should be a valid string
reader.llm.api_base - Input should be a valid string [input_value=None]
```

容器状态：`Up 10 minutes (unhealthy)`

### 根因分析

`docker-compose.yml` 中 Memos 配置了 Ollama 作为 LLM 后端：

```yaml
- OLLAMA_BASE_URL=http://host.docker.internal:11434
- MOS_LLM_BACKEND=ollama
- MOS_LLM_MODEL=qwen3.5:9b
```

但 MemOS 2.0.7 的 `InternetRetrieverConfigFactory` 需要 `reader.llm.model_name_or_path` 和 `reader.llm.api_base` 两个配置项，当前这两个值为 `None`。

可能原因：
1. 宿主机上 Ollama 未运行，或 `qwen3.5:9b` 模型未拉取
2. MemOS 2.0.7 的配置格式与 docker-compose.yml 中的环境变量不匹配
3. `.env` 文件缺失（只有 `.env.example`，没有 `.env`）

### 解决方案

#### 步骤 1：创建 .env 文件

```bash
cd f:\SoarCloudAI\docker\MemOS-2.0.7
cp .env.example .env
```

#### 步骤 2：在 .env 中配置 LLM

```env
# 使用阿里云百炼 API（推荐，稳定）
MOS_LLM_MODEL=qwen-plus
MOS_LLM_API_KEY=your-dashscope-api-key
MOS_LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1

# 或使用 Ollama（需要宿主机运行 Ollama）
# OLLAMA_BASE_URL=http://host.docker.internal:11434
# MOS_LLM_BACKEND=ollama
# MOS_LLM_MODEL=qwen3.5:9b
```

#### 步骤 3：在 docker-compose.yml 中补充 Internet Retriever 配置

```yaml
environment:
  # ... 已有配置 ...
  # Internet Retriever LLM 配置
  - READER_LLM_MODEL_NAME_OR_PATH=qwen-plus
  - READER_LLM_API_BASE=https://dashscope.aliyuncs.com/compatible-mode/v1
  - READER_LLM_API_KEY=${DASHSCOPE_API_KEY}
```

---

## 问题 7：飞书工具未配置（🟢 低）

### 现象

```
HR Feishu App ID not configured, HrFeishuTool will not be available
Employee Feishu App ID not configured, EmployeeFeishuTool will not be available
```

### 解决方案

在 `docker-compose.yml` 或 `.env` 中配置飞书应用凭据：

```yaml
environment:
  - HR_FEISHU_APP_ID=your_hr_feishu_app_id
  - HR_FEISHU_APP_SECRET=your_hr_feishu_app_secret
  - EMPLOYEE_FEISHU_APP_ID=your_employee_feishu_app_id
  - EMPLOYEE_FEISHU_APP_SECRET=your_employee_feishu_app_secret
```

---

## 问题 8：Hibernate 配置警告（🟢 低）

### 现象

```
HHH90000025: PostgreSQLDialect does not need to be specified explicitly using 'hibernate.dialect'
spring.jpa.open-in-view is enabled by default
```

### 解决方案

在 `application.yml` 中添加：

```yaml
spring:
  jpa:
    open-in-view: false
    # 删除 hibernate.dialect 显式配置，让 Hibernate 自动检测
```

---

## 构建依赖关系图

```
download_images.py
  ├── build_python_deps()          → image/python-wheels.tar
  │     └── Dockerfile.python-deps  (pip download → tar 打包)
  │
  ├── build_system_deps_image()    → image/living-agent-system-deps-1.0.tar
  │     └── Dockerfile.system-deps  (apt-get + Playwright 浏览器)
  │
  └── build_rust_native()          → image/libliving_agent_native.so

Dockerfile.local (离线构建)
  ├── FROM living-agent-system-deps:1.0    ← 来自 living-agent-system-deps-1.0.tar
  ├── COPY image/python-wheels.tar         ← 来自 build_python_deps()
  ├── COPY image/llama-cpp-built.tar       ← 来自 build_llama_cpp.py
  ├── COPY image/nltk_data                 ← NLTK 数据
  └── COPY image/libliving_agent_native.so ← 来自 build_rust_native()
```

### 修改影响范围

| 修改文件 | 影响的产物 | 是否需要重建镜像 |
|----------|-----------|-----------------|
| `download_images.py` (build_python_deps) | `python-wheels.tar` | ✅ 需要重新下载 + 重建 |
| `download_images.py` (build_system_deps_image) | `living-agent-system-deps-1.0.tar` | ✅ 需要重建基础镜像 |
| `Dockerfile.local` | `living-agent-service` 镜像 | ✅ 需要重建应用镜像 |
| `docker-compose.yml` | 运行时配置 | ❌ 重启即可 |
| `.env` | 运行时配置 | ❌ 重启即可 |

### 重建步骤

```bash
# 1. 删除旧的产物（强制重新下载/构建）
del image\python-wheels.tar
del image\living-agent-system-deps-1.0.tar

# 2. 重新下载依赖
python image\download_images.py

# 3. 加载基础镜像
powershell -File image\load_images.ps1

# 4. 重建并启动
docker-compose up --build -d
```
