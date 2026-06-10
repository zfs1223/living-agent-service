# RuView WiFi 感知微服务融合方案

> **版本**: v1.0 | **日期**: 2026-05-21 | **状态**: 待实施
>
> 本文档详细说明如何将 [RuView (WiFi DensePose)](https://github.com/ruvnet/RuView) 作为独立微服务集成到 Living Agent Service，实现物理世界感知能力。

---

## 📋 目录

1. [背景与目标](#一背景与目标)
2. [架构分析](#二架构分析)
3. [集成方案设计](#三集成方案设计)
4. [Docker 部署配置](#四docker-部署配置)
5. [代码实现指南](#五代码实现指南)
6. [离线部署支持](#六离线部署支持)
7. [业务场景应用](#七业务场景应用)
8. [实施路线图](#八实施路线图)
9. [风险与缓解](#九风险与缓解)
10. [成功指标](#十成功指标)

---

## 一、背景与目标

### 1.1 现状分析

#### Living Agent Service 当前能力

根据 [`02-core-architecture.md`](../core/02-core-architecture.md) 文档，Living Agent 采用**三层 LLM 架构**：

```
┌─────────────────────────────────────────┐
│           感知层 (Perception Layer)       │
│                                         │
│  ✅ EarNeuron   (ASR - 语音识别)        │
│  ✅ MouthNeuron  (TTS - 语音合成)        │
│  ✅ TextNeuron   (文本处理)              │
│  ✅ VisionNeuron (视觉识别)              │
│  🔴 SensorNeuron (物理传感器) ← 缺失！    │
└─────────────────────────────────────────┘
```

**关键发现**：
- 感知层中 **Sensor (触觉/传感器)** 模块标记为 `🔜规划` 状态
- 代码路径 `living-agent-perception/` 下仅有 ear/mouth/text 三个神经元
- **缺少物理世界感知能力**（人员检测、环境监测、生命体征等）

#### RuView 能力矩阵

| 能力维度 | 具体功能 | 技术实现 | 商业价值 |
|---------|---------|---------|---------|
| 👥 **人员感知** | 存在检测、计数、追踪、姿态估计 | WiFi CSI + AI 模型 | 智慧办公、安全管理 |
| 🫁 **生命体征** | 呼吸率、心率监测 | 相位信号分析 | 健康监护、疲劳检测 |
| 🏃 **行为识别** | 行走、跌倒、手势、活动分类 | 时序模式匹配 | 安全预警、行为分析 |
| 🗺️ **环境感知** | 房间指纹、家具移动、入侵检测 | RF 特征提取 | 智能家居、资产管理 |
| 😴 **健康监测** | 睡眠质量、呼吸暂停筛查 | 长时序分析 | 员工福利、医疗辅助 |

### 1.2 融合目标

**核心目标**：通过独立微服务方式将 RuView 集成到 Living Agent，填补 SensorNeuron 空白

**具体目标**：
1. ✅ 在感知层新增 **SensorNeuron**（WiFi 物理感知）
2. ✅ 实现实时人员存在检测和占用统计
3. ✅ 支持生命体征监测（呼吸/心率）
4. ✅ 提供安全预警能力（跌倒检测、异常闯入）
5. ✅ 与现有大脑系统（TechBrain/OpsBrain 等）无缝协作
6. ✅ 支持离线部署（符合 Living Agent 的离线构建体系）

---

## 二、架构分析

### 2.1 Living Agent 核心架构

#### 三层 LLM 架构

```
Layer 1: MainBrain (主大脑)
├── 职责: 复杂推理、跨部门协调、战略决策
├── 模型: 动态选择（Qwen3.5-2B / 云端模型）
└── 场景: 需要感知数据的复杂决策

Layer 2: Qwen3Neuron (闲聊神经元)
├── 职责: 日常对话、快速响应
├── 模型: Qwen3-0.6B (本地)
└── 场景: 用户查询感知状态

Layer 3: ToolNeuron (工具神经元)
├── 职责: 工具检测、兜底处理
├── 模型: 固定模型
└── 场景: 触发感知相关工具调用
```

#### 感知层 Neuron 模式

以 [`EarNeuron.java`](../../living-agent-perception/src/main/java/com/livingagent/perception/ear/EarNeuron.java) 为例：

```java
public class EarNeuron extends AbstractNeuron {
    public static final String ID = "neuron://perception/ear/001";
    public static final String INPUT_CHANNEL = "channel://input/audio";
    public static final String OUTPUT_CHANNEL = "channel://perception/text";

    @Override
    protected void doProcessMessage(ChannelMessage message) {
        // 1. 接收原始音频数据
        // 2. 调用 ASR 服务转换
        // 3. 发布文本消息到输出通道
    }
}
```

**关键设计模式**：
- 每个 Neuron 有唯一的 ID 和通道
- 输入/输出通过 ChannelMessage 异步传递
- 支持错误处理和元数据附加

#### 现有基础设施

根据 [`docker-compose.yml`](../../docker-compose.yml)，Living Agent 已具备：

| 组件 | 端口 | 用途 |
|------|------|------|
| PostgreSQL (pgvector) | 5432 | 主数据库 + 向量搜索 |
| Redis | 6379 | 缓存 + 会话 |
| Qdrant | 6333/6334 | 向量数据库 |
| Kafka | 9092 | 消息队列（神经元通讯） |
| Zookeeper | 2181 | Kafka 协调 |
| MemOS | 8381 | 记忆系统 |

### 2.2 RuView 技术架构

#### 核心组件

```
RuView System
├── sensing-server (Rust/Axum)     :8387  # REST API
├── python-sensing (Python/FastAPI) :8388  # Python 服务
├── ESP32 Sensor Nodes              :5005/udp  # WiFi 数据采集
└── WebSocket Stream                :8389  # 实时数据推送
```

#### API 接口（基于 [`pose.py`](../../../../RuView/archive/v1/src/api/routers/pose.py)）

| 端点 | 方法 | 功能 | 数据类型 |
|------|------|------|---------|
| `/api/v1/pose/current` | GET | 当前姿态估计 | PoseEstimationResponse |
| `/api/v1/pose/zones/{id}/occupancy` | GET | 区域占用 | ZoneOccupancy |
| `/api/v1/pose/zones/summary` | GET | 区域汇总 | ZonesSummary |
| `/api/v1/pose/activities` | GET | 活动列表 | ActivityList |
| `/api/v1/pose/historical` | POST | 历史数据 | HistoricalData |
| `/api/v1/pose/calibrate` | POST | 校准系统 | CalibrationStatus |

#### 数据模型

```typescript
// 人员姿态数据
interface PersonPose {
  person_id: string;
  confidence: number;        // 0-1
  bounding_box: {x, y, w, h};
  keypoints?: KeyPoint[];    // 17个关键点
  zone_id?: string;
  activity?: string;        // walking/sitting/falling/...
  timestamp: Date;
}

// 区域占用数据
interface ZoneOccupancy {
  zone_id: string;
  current_occupancy: number;
  max_occupancy?: number;
  persons: PersonPose[];
  timestamp: Date;
}
```

### 2.3 集成契合度分析

#### ✅ 高度契合点

| 维度 | Living Agent | RuView | 匹配度 |
|------|-------------|--------|-------|
| **架构风格** | 微服务 + Docker Compose | Docker 化部署 | ⭐⭐⭐⭐⭐ |
| **通信协议** | REST + WebSocket + Kafka | REST + WebSocket | ⭐⭐⭐⭐⭐ |
| **数据格式** | JSON (ApiResponse<T>) | JSON (Pydantic models) | ⭐⭐⭐⭐⭐ |
| **扩展机制** | Neuron 抽象 + Channel | Plugin/Cog 系统 | ⭐⭐⭐⭐ |
| **离线支持** | download_images.py 机制 | 支持模拟数据模式 | ⭐⭐⭐⭐ |

#### 🔧 需要适配的点

| 问题 | 解决方案 | 工作量 |
|------|---------|-------|
| 技术栈差异 (Java vs Python/Rust) | HTTP 客户端调用 + WebSocket | 低 |
| 认证体系不同 | RuView API Gateway 代理 | 中 |
| 数据模型映射 | SensorNeuron 内部转换 | 低 |
| 实时性要求 | WebSocket 双向通信 | 中 |

---

## 三、集成方案设计

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                  Living Agent Service (现有)                         │
│                                                                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │
│  │  EarNeuron   │  │ MouthNeuron │  │ TextNeuron  │                 │
│  │   (ASR)      │  │   (TTS)     │  │   (Text)    │                 │
│  └──────┬───────┘  └──────┬──────┘  └──────┬───────┘                 │
│         │                 │                 │                          │
│         └─────────────────┼─────────────────┘                          │
│                           ▼                                            │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │                    神经元层 (Neuron Layer)                      │    │
│  │                                                               │    │
│  │  ┌─────────────────────────────────────────────────────┐     │    │
│  │  │  ⭐ 新增: SensorNeuron (WiFi 物理感知)               │     │    │
│  │  │                                                     │     │    │
│  │  │  ID: neuron://perception/sensor/001                 │     │    │
│  │  │  Input: channel://input/sensor                      │     │    │
│  │  │  Output: channel://perception/sensor-data           │     │    │
│  │  └──────────────────────┬──────────────────────────────┘     │    │
│  └─────────────────────────┼───────────────────────────────────┘    │
│                            │ HTTP/WebSocket                        │
│                            ▼                                       │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │              RuView Sensing Service (新增微服务)              │    │
│  │                                                               │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐    │    │
│  │  │sensing-server│  │python-sensing│  │ ESP32 Sensor Nodes│    │    │
│  │  │  :8387 (Rust)│  │  :8388 (Py)  │  │   :5005/udp      │    │    │
│  │  └──────────────┘  └──────────────┘  └──────────────────┘    │    │
│  └──────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 方案对比

#### 方案 A：独立微服务（推荐 ✅）

**架构**：RuView 作为独立 Docker 服务，SensorNeuron 通过 HTTP/WebSocket 调用

**优势**：
- ✅ 符合 Living Agent 微服务架构
- ✅ 故障隔离（RuView 宕机不影响核心功能）
- ✅ 独立扩容（可根据传感器数量调整资源）
- ✅ 技术栈解耦（Java ↔ Python/Rust 无冲突）
- ✅ 支持离线部署（可打包到 image/ 目录）

**劣势**：
- ⚠️ 网络延迟增加 ~10-50ms（可接受）
- ⚠️ 需要额外的容器资源（~512MB-2GB）

#### 方案 B：嵌入式集成（不推荐 ❌）

**架构**：将 RuView Python 库直接嵌入 living-agent-service 容器

**劣势**：
- ❌ 技术栈冲突（PyTorch/SciPy 与 Java 环境不兼容）
- ❌ GPU 资源竞争（AI 服务已占用 GPU）
- ❌ 维护困难（需同时维护 Java + Python + Rust）
- ❌ 无法独立升级

**结论**：采用**方案 A（独立微服务）**

### 3.3 数据流设计

#### 实时感知数据流

```
ESP32 Sensor → UDP:5005 → RuView sensing-server
                                ↓
                           WebSocket:8389 (实时流)
                                ↓
                    SensorNeuron (订阅 WebSocket)
                                ↓
                    channel://perception/sensor-data
                                ↓
                    ┌───────────┼───────────┐
                    ▼           ▼           ▼
              TechBrain     OpsBrain    AdminBrain
              (技术部)      (运营部)    (行政部)
                    │           │           │
                    ▼           ▼           ▼
              "会议室A有3人"  "工位占用率75%"  "检测到异常闯入"
```

#### 请求-响应模式

```
用户提问 → TextNeuron → Qwen3Neuron(意图识别)
                              ↓
                    [需要感知数据?]
                     /          \
                   Yes          No
                   ↓             ↓
            SensorNeuron    直接回答
           (调用RuView API)    ↓
                   ↓             ↓
            返回感知上下文    返回结果
                   ↓             ↓
            组合最终响应 ──────┘
                   ↓
              MouthNeuron(TTS)
                   ↓
              用户收到答案
```

---

## 四、Docker 部署配置

### 4.1 docker-compose.yml 新增配置

在 [`docker-compose.yml`](../../docker-compose.yml) 中添加 RuView 服务：

```yaml
# ===========================================
# RuView Sensing Service - WiFi 物理感知服务
# ===========================================
ruview-sensing:
  build:
    context: ./ruview-integration
    dockerfile: Dockerfile.ruview
  image: living-agent-ruview-sensing
  container_name: living-agent-ruview-sensing
  hostname: ruview-sensing
  ports:
    - "8387:3000"    # REST API (外部 8387 → 容器内部 3000)
    - "8389:3001"    # WebSocket (外部 8389 → 容器内部 3001)
    - "5005:5005/udp" # ESP32 数据接收 (可选，硬件模式)
  environment:
    # 数据源配置
    - CSI_SOURCE=${CSI_SOURCE:-simulated}  # simulated/esp32/wifi/auto
    - MODELS_DIR=/app/data/models
    
    # API 配置
    - RUST_LOG=info
    - RUVIEW_API_HOST=0.0.0.0
    - RUVIEW_API_PORT=3000
    
    # 连接 Living Agent 基础设施 (可选，用于存储感知数据)
    - POSTGRES_URL=jdbc:postgresql://postgres:5432/livingagent
    - POSTGRES_USERNAME=livingagent
    - POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-livingagent123}
    
    # Redis 缓存 (可选，用于实时数据缓存)
    - REDIS_HOST=redis
    - REDIS_PORT=6379
    
    # Kafka 事件发布 (可选，用于神经元间通信)
    - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
    - KAFKA_TOPIC_SENSOR_DATA=living-agent.sensor.data
  volumes:
    # 感知数据持久化
    - ruview-data:/app/data
    - ruview-models:/app/data/models
    # 日志
    - ./logs/ruview:/app/logs
    # 配置文件
    - ./config/ruview:/app/config:ro
  networks:
    - living-agent-network
  depends_on:
    postgres:
      condition: service_healthy
    redis:
      condition: service_healthy
    kafka:
      condition: service_healthy  # 可选
  healthcheck:
    test: ["CMD-SHELL", "curl -f http://localhost:3000/api/v1/health || exit 1"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 30s
  restart: unless-stopped
  deploy:
    resources:
      limits:
        memory: 2G
        cpus: '2.0'
      reservations:
        memory: 512M
        cpus: '0.5'
```

### 4.2 Dockerfile 构建

创建 `docker/living-agent-service/ruview-integration/Dockerfile.ruview`：

```dockerfile
# ===========================================
# RuView Sensing Service - 多阶段构建
# 支持 Rust (sensing-server) + Python (API层)
# ===========================================

# Stage 1: 构建 Rust sensing-server
FROM rust:1.85-slim AS rust-builder

WORKDIR /build

# 安装构建依赖
RUN apt-get update && apt-get install -y \
    pkg-config \
    libssl-dev \
    && rm -rf /var/lib/apt/lists/*

# 配置国内镜像
RUN mkdir -p /usr/local/cargo && \
    echo '[source.crates-io]' > /usr/local/cargo/config.toml && \
    echo 'replace-with = "tuna"' >> /usr/local/cargo/config.toml && \
    echo '[source.tuna]' >> /usr/local/cargo/config.toml && \
    echo 'registry = "sparse+https://mirrors.tuna.tsinghua.edu.cn/crates.io-index/"' >> /usr/local/cargo/config.toml

# 复制 RuView Rust 源码 (从 vendor 或 submodule)
COPY vendor/RuView/v2/crates/wifi-densepose-sensing-server /build/sensing-server
COPY vendor/RuView/v2/Cargo.toml /build/Cargo.toml
COPY vendor/RuView/v2/Cargo.lock /build/Cargo.lock

# 构建 sensing-server
WORKDIR /build
RUN cargo build --release -p wifi-densepose-sensing-server

# Stage 2: 运行时镜像
FROM python:3.11-slim

LABEL maintainer="Living Agent Team"
LABEL description="RuView WiFi Sensing Service - Integrated with Living Agent"
LABEL version="1.0.0"

# 安装系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    libssl3 \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# 创建非 root 用户
RUN groupadd -r ruview && useradd -r -g ruview ruview

# 复制 Rust 二进制
COPY --from=rust-builder /build/target/release/wifi-densepose-sensing-server /usr/local/bin/

# 安装 Python 依赖 (轻量级 API 包装层)
COPY requirements.txt /tmp/
RUN pip install --no-cache-dir -r /tmp/requirements.txt && \
    rm /tmp/requirements.txt

# 创建目录结构
WORKDIR /app
RUN mkdir -p /app/data /app/data/models /app/logs /app/config && \
    chown -R ruview:ruview /app

# 复制 Python API 代码
COPY python-api/ /app/python-api/
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# 切换到非 root 用户
USER ruview

# 暴露端口
EXPOSE 8387 8389 8388 5005/udp

# 环境变量
ENV RUST_LOG=info \
    PYTHONUNBUFFERED=1 \
    RUVIEW_HOME=/app

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8387/api/v1/health || exit 1

# 启动脚本 (同时启动 Rust server 和 Python API)
ENTRYPOINT ["/app/entrypoint.sh"]
```

### 4.3 启动脚本

创建 `ruview-integration/entrypoint.sh`：

```bash
#!/bin/bash
set -e

echo "========================================="
echo " Starting RuView Sensing Service"
echo "========================================="

# 启动 Rust sensing-server (后台)
echo "[*] Starting Rust sensing-server on port 8387..."
wifi-densepose-sensing-server \
    --host 0.0.0.0 \
    --port 8387 \
    --source ${CSI_SOURCE:-simulated} \
    --models-dir ${MODELS_DIR:-/app/data/models} &
RUST_PID=$!

# 等待 Rust 服务就绪
echo "[*] Waiting for sensing-server to be ready..."
until curl -sf http://localhost:8387/api/v1/health > /dev/null 2>&1; do
    sleep 1
done
echo "[✓] Sensing-server is ready"

# 启动 Python API 服务 (后台，可选)
if [ "$ENABLE_PYTHON_API" = "true" ]; then
    echo "[*] Starting Python API on port 8388..."
    cd /app/python-api
    python -m uvicorn main:app --host 0.0.0.0 --port 8388 &
    PYTHON_PID=$!
fi

# 保持容器运行
echo "[✓] All services started"
echo "    - Rust sensing-server: http://localhost:8387 (PID: $RUST_PID)"
echo "    - WebSocket stream: ws://localhost:8389"
[ "$ENABLE_PYTHON_API" = "true" ] && echo "    - Python API: http://localhost:8388 (PID: $PYTHON_PID)"
echo ""
echo "========================================="

# 等待任意子进程退出
wait -n

echo "[!] A service exited, stopping..."
kill $RUST_PID 2>/dev/null
[ -n "$PYTHON_PID" ] && kill $PYTHON_PID 2>/dev/null
exit 1
```

---

## 五、代码实现指南

### 5.1 SensorNeuron 实现

创建 `living-agent-perception/src/main/java/com/livingagent/perception/sensor/SensorNeuron.java`：

```java
package com.livingagent.perception.sensor;

import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.impl.AbstractNeuron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SensorNeuron - WiFi 物理感知神经元
 * 
 * 通过 RuView Sensing Service 获取 WiFi 感知数据，
 * 包括人员存在检测、占用统计、生命体征等。
 * 
 * 对接 RuView API:
 * - GET /api/v1/pose/current - 当前姿态
 * - GET /api/v1/pose/zones/{id}/occupancy - 区域占用
 * - WS /ws/sensing - 实时数据流
 */
public class SensorNeuron extends AbstractNeuron {

    private static final Logger log = LoggerFactory.getLogger(SensorNeuron.class);

    public static final String ID = "neuron://perception/sensor/001";
    public static final String INPUT_CHANNEL = "channel://input/sensor";
    public static final String OUTPUT_CHANNEL = "channel://perception/sensor-data";
    public static final String ALERT_CHANNEL = "channel://perception/sensor-alert";

    @Value("${ruview.api.base-url:http://ruview-sensing:3000}")
    private String ruviewApiBaseUrl;

    @Value("${ruview.api.timeout:5000}")
    private int apiTimeout;

    @Value("${ruview.polling.interval-seconds:10}")
    private int pollingIntervalSeconds;

    @Value("${ruview.ws.enabled:true}")
    private boolean websocketEnabled;

    private RestTemplate restTemplate;
    private ScheduledExecutorService scheduler;
    private RuViewWebSocketClient wsClient;

    public SensorNeuron() {
        super(
            ID,
            "SensorNeuron",
            "WiFi 物理感知神经元 - 人员检测、环境监测、生命体征",
            List.of(INPUT_CHANNEL),
            List.of(OUTPUT_CHANNEL, ALERT_CHANNEL),
            List.of()
        );
    }

    @Override
    protected void doStart(NeuronContext context) {
        log.info("SensorNeuron starting, connecting to RuView at {}", ruviewApiBaseUrl);
        
        // 初始化 REST 客户端
        restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(createRequestFactory());
        
        // 启动定时轮询任务
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
            this::pollSensorData,
            0,
            pollingIntervalSeconds,
            TimeUnit.SECONDS
        );

        // 启动 WebSocket 客户端 (实时数据)
        if (websocketEnabled) {
            startWebSocketClient();
        }

        log.info("SensorNeuron started successfully");
        log.info("  - Polling interval: {}s", pollingIntervalSeconds);
        log.info("  - WebSocket: {}", websocketEnabled ? "enabled" : "disabled");
    }

    @Override
    protected void doStop() {
        log.info("SensorNeuron stopping...");
        
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }

        if (wsClient != null) {
            wsClient.disconnect();
        }

        log.info("SensorNeuron stopped");
    }

    @Override
    protected void doProcessMessage(ChannelMessage message) {
        log.debug("SensorNeuron processing message: {}", message.getId());

        switch (message.getType()) {
            case SENSOR_QUERY:
                handleSensorQuery(message);
                break;
            case COMMAND:
                handleCommand(message);
                break;
            default:
                log.warn("Unsupported message type: {}", message.getType());
        }
    }

    /**
     * 定时轮询 RuView API 获取感知数据
     */
    private void pollSensorData() {
        try {
            log.debug("Polling sensor data from RuView...");
            
            // 获取当前姿态数据
            PoseEstimationResponse poseData = callRuViewApi(
                "/api/v1/pose/current",
                PoseEstimationResponse.class
            );

            if (poseData != null && poseData.getPersons() != null) {
                // 发布感知数据到输出通道
                ChannelMessage sensorMessage = ChannelMessage.builder()
                    .type(ChannelMessage.MessageType.SENSOR_DATA)
                    .sourceChannelId(INPUT_CHANNEL)
                    .sourceNeuronId(ID)
                    .targetChannelId(OUTPUT_CHANNEL)
                    .sessionId("system-polling")
                    .payload(poseData)
                    .timestamp(Instant.now())
                    .build();

                sensorMessage.addMetadata("source", "ruview-polling");
                sensorMessage.addMetadata("total_persons", poseData.getPersons().size());
                
                publish(OUTPUT_CHANNEL, sensorMessage);

                // 检查告警条件
                checkAlertConditions(poseData);
            }

        } catch (Exception e) {
            log.error("Failed to poll sensor data from RuView", e);
            publishError(null, "RuView polling failed: " + e.getMessage());
        }
    }

    /**
     * 处理传感器查询请求
     */
    private void handleSensorQuery(ChannelMessage queryMessage) {
        try {
            String queryType = queryMessage.getMetadata("query_type");
            Object result;

            switch (queryType != null ? queryType : "current") {
                case "zone_occupancy":
                    String zoneId = queryMessage.getMetadata("zone_id");
                    result = callRuViewApi(
                        "/api/v1/pose/zones/" + zoneId + "/occupancy",
                        ZoneOccupancy.class
                    );
                    break;
                    
                case "zones_summary":
                    result = callRuViewApi(
                        "/api/v1/pose/zones/summary",
                        ZonesSummary.class
                    );
                    break;
                    
                case "activities":
                    result = callRuViewApi(
                        "/api/v1/activities",
                        ActivityList.class
                    );
                    break;
                    
                default: // "current"
                    result = callRuViewApi(
                        "/api/v1/pose/current",
                        PoseEstimationResponse.class
                    );
            }

            // 发送查询结果
            ChannelMessage response = ChannelMessage.builder()
                .type(ChannelMessage.MessageType.SENSOR_DATA)
                .sourceChannelId(INPUT_CHANNEL)
                .sourceNeuronId(ID)
                .targetChannelId(queryMessage.getSourceChannelId())
                .sessionId(queryMessage.getSessionId())
                .payload(result)
                .timestamp(Instant.now())
                .build();

            response.addMetadata("response_to", queryMessage.getId());
            response.addMetadata("query_type", queryType);

            publish(response.getSourceChannelId(), response);

        } catch (Exception e) {
            log.error("Failed to handle sensor query", e);
            publishError(queryMessage, "Query failed: " + e.getMessage());
        }
    }

    /**
     * 处理命令请求 (如校准、配置变更)
     */
    private void handleCommand(ChannelMessage commandMessage) {
        String command = commandMessage.getMetadata("command");

        if ("calibrate".equals(command)) {
            try {
                CalibrationResult result = callRuViewApi(
                    "/api/v1/pose/calibrate",
                    HttpMethod.POST,
                    null,
                    CalibrationResult.class
                );

                ChannelMessage response = ChannelMessage.builder()
                    .type(ChannelMessage.MessageType.COMMAND_RESPONSE)
                    .sourceNeuronId(ID)
                    .sessionId(commandMessage.getSessionId())
                    .payload(result)
                    .timestamp(Instant.now())
                    .build();

                publish(commandMessage.getSourceChannelId(), response);
                log.info("Calibration initiated: {}", result.getCalibrationId());

            } catch (Exception e) {
                log.error("Calibration failed", e);
                publishError(commandMessage, "Calibration failed: " + e.getMessage());
            }
        } else {
            log.warn("Unknown command: {}", command);
            publishError(commandMessage, "Unknown command: " + command);
        }
    }

    /**
     * 检查告警条件并发布告警
     */
    private void checkAlertConditions(PoseEstimationResponse poseData) {
        if (poseData == null || poseData.getPersons() == null) return;

        List<String> alerts = new ArrayList<>();

        // 检测跌倒
        poseData.getPersons().forEach(person -> {
            if ("falling".equals(person.getActivity())) {
                alerts.add(String.format(
                    "FALL_DETECTED: Person %s in zone %s at %s",
                    person.getPersonId(),
                    person.getZoneId(),
                    person.getTimestamp()
                ));
            }
        });

        // 检测区域超员
        if (poseData.getZoneSummary() != null) {
            poseData.getZoneSummary().forEach((zoneId, count) -> {
                if (count > getMaxOccupancyForZone(zoneId)) {
                    alerts.add(String.format(
                        "OVER_CAPACITY: Zone %s has %d persons (max: %d)",
                        zoneId, count,
                        getMaxOccupancyForZone(zoneId)
                    ));
                }
            });
        }

        // 发布告警
        alerts.forEach(alert -> {
            ChannelMessage alertMessage = ChannelMessage.builder()
                .type(ChannelMessage.MessageType.ALERT)
                .sourceNeuronId(ID)
                .targetChannelId(ALERT_CHANNEL)
                .sessionId("system-alert")
                .payload(alert)
                .timestamp(Instant.now())
                .priority(ChannelMessage.Priority.HIGH)
                .build();

            alertMessage.addMetadata("alert_type", extractAlertType(alert));
            publish(ALERT_CHANNEL, alertMessage);
            log.warn("Sensor alert: {}", alert);
        });
    }

    /**
     * 调用 RuView REST API
     */
    private <T> T callRuViewApi(String path, Class<T> responseType) {
        return callRuViewApi(path, HttpMethod.GET, null, responseType);
    }

    private <T> T callRuViewApi(String path, HttpMethod method, Object body, Class<T> responseType) {
        try {
            String url = ruviewApiBaseUrl + path;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<?> entity = (body != null) 
                ? new HttpEntity<>(body, headers)
                : new HttpEntity<>(headers);

            ResponseEntity<T> response = restTemplate.exchange(
                url,
                method,
                entity,
                responseType
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                log.warn("RuView API returned status {}: {}", 
                    response.getStatusCode(), url);
                return null;
            }

        } catch (Exception e) {
            log.error("Failed to call RuView API: {}", path, e);
            return null;
        }
    }

    /**
     * 启动 WebSocket 客户端 (实时数据流)
     */
    private void startWebSocketClient() {
        try {
            String wsUrl = ruviewApiBaseUrl.replace("http:", "ws:") + "/ws/sensing";
            wsClient = new RuViewWebSocketClient(wsUrl, this::handleWebSocketMessage);
            wsClient.connect();
            log.info("WebSocket client connected to {}", wsUrl);
        } catch (Exception e) {
            log.error("Failed to start WebSocket client", e);
        }
    }

    /**
     * 处理 WebSocket 消息
     */
    private void handleWebSocketMessage(Object message) {
        try {
            // 直接发布实时数据，无需等待轮询
            ChannelMessage realtimeMessage = ChannelMessage.builder()
                .type(ChannelMessage.MessageType.SENSOR_DATA)
                .sourceChannelId(INPUT_CHANNEL)
                .sourceNeuronId(ID)
                .targetChannelId(OUTPUT_CHANNEL)
                .sessionId("realtime-ws")
                .payload(message)
                .timestamp(Instant.now())
                .build();

            realtimeMessage.addMetadata("source", "ruview-websocket-realtime");
            publish(OUTPUT_CHANNEL, realtimeMessage);

        } catch (Exception e) {
            log.error("Failed to handle WebSocket message", e);
        }
    }

    // ==================== 辅助方法 ====================

    private org.springframework.http.client.ClientHttpRequestFactory createRequestFactory() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = 
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(apiTimeout);
        factory.setReadTimeout(apiTimeout);
        return factory;
    }

    private int getMaxOccupancyForZone(String zoneId) {
        // TODO: 从配置或数据库读取各区域最大容量
        return 10; // 默认值
    }

    private String extractAlertType(String alert) {
        if (alert.startsWith("FALL_DETECTED")) return "FALL";
        if (alert.startsWith("OVER_CAPACITY")) return "OVER_CAPACITY";
        return "UNKNOWN";
    }

    private void publishError(ChannelMessage original, String error) {
        ChannelMessage errorMessage = ChannelMessage.error(
            OUTPUT_CHANNEL,
            ID,
            original != null ? original.getTargetChannelId() : OUTPUT_CHANNEL,
            original != null ? original.getSessionId() : "system-error",
            error
        );
        publish(OUTPUT_CHANNEL, errorMessage);
    }

    // ==================== 数据传输对象 (DTO) ====================
    
    /**
     * 姿态估计响应
     */
    public static class PoseEstimationResponse {
        private Instant timestamp;
        private String frameId;
        private List<PersonPose> persons;
        private Map<String, Integer> zoneSummary;
        private double processingTimeMs;
        
        // getters & setters
        public List<PersonPose> getPersons() { return persons; }
        public Map<String, Integer> getZoneSummary() { return zoneSummary; }
        // ... 其他 getter/setter
    }

    /**
     * 人员姿态
     */
    public static class PersonPose {
        private String personId;
        private double confidence;
        private Map<String, Double> boundingBox;
        private String zoneId;
        private String activity;
        private Instant timestamp;
        
        // getters & setters
        public String getPersonId() { return personId; }
        public String getZoneId() { return zoneId; }
        public String getActivity() { return activity; }
        public Instant getTimestamp() { return timestamp; }
    }

    /**
     * 区域占用
     */
    public static class ZoneOccupancy {
        private String zoneId;
        private int currentOccupancy;
        private Integer maxOccupancy;
        private List<PersonPose> persons;
        private Instant timestamp;
        
        // getters & setters
    }

    /**
     * 区域汇总
     */
    public static class ZonesSummary {
        private Instant timestamp;
        private int totalPersons;
        private Map<String, Object> zones;
        private List<String> activeZones;
        
        // getters & setters
    }

    /**
     * 校准结果
     */
    public static class CalibrationResult {
        private String calibrationId;
        private String status;
        private int estimatedDurationMinutes;
        
        // getters & setters
        public String getCalibrationId() { return calibrationId; }
    }

    /**
     * 活动列表
     */
    public static class ActivityList {
        private List<Object> activities;
        private int totalCount;
        
        // getters & setters
    }
}
```

### 5.2 Spring Boot 配置

在 `application.yml` 中添加 RuView 配置：

```yaml
# RuView Sensing Service Configuration
ruview:
  api:
    base-url: ${RUVIEW_API_BASE_URL:http://ruview-sensing:3000}
    timeout: ${RUVIEW_API_TIMEOUT:5000}
    auth-token: ${RUVIEW_API_AUTH_TOKEN:}
  
  polling:
    enabled: ${RUVIEW_POLLING_ENABLED:true}
    interval-seconds: ${RUVIEW_POLLING_INTERVAL:10}
  
  websocket:
    enabled: ${RUVIEW_WS_ENABLED:true}
    reconnect-interval-seconds: ${RUVIEW_WS_RECONNECT_INTERVAL:30}
  
  zones:
    # 区域配置 (可根据实际环境调整)
    meeting-room-a:
      id: "zone-meeting-a"
      name: "会议室A"
      max-occupancy: 10
      type: MEETING
    meeting-room-b:
      id: "zone-meeting-b"
      name: "会议室B"
      max-occupancy: 8
      type: MEETING
    open-office:
      id: "zone-open-office"
      name: "开放办公区"
      max-occupancy: 30
      type: WORKSPACE
    entrance:
      id: "zone-entrance"
      name: "入口大厅"
      max-occupancy: 20
      type: ENTRANCE
  
  alerts:
    fall-detection:
      enabled: true
      cooldown-seconds: 300  # 5分钟冷却期
    over-capacity:
      enabled: true
    intrusion:
      enabled: true
      allowed-hours: "08:00-20:00"  # 允许的时间段
  
  # 数据保留策略
  retention:
    raw-data-days: 7
    aggregated-data-days: 90
    alert-history-days: 365
```

### 5.3 注册 SensorNeuron

在 Perception 模块的配置类中注册 SensorNeuron：

```java
@Configuration
public class PerceptionConfig {

    @Bean
    public SensorNeuron sensorNeuron(
        @Value("${ruview.api.base-url}") String apiUrl
    ) {
        SensorNeuron neuron = new SensorNeuron();
        neuron.setRuviewApiBaseUrl(apiUrl);
        return neuron;
    }

    @Bean
    public RuViewRestClient ruViewRestClient(
        @Value("${ruview.api.base-url}") String baseUrl,
        @Value("${ruview.api.timeout}") int timeout
    ) {
        return new RuViewRestClient(baseUrl, timeout);
    }
}
```

---

## 六、离线部署支持

### 6.1 扩展 download_images.py

修改 [`image/download_images.py`](../../image/download_images.py)，添加 RuView 镜像下载逻辑：

```python
def pull_ruview_images(image_dir):
    """下载 RuView Sensing Service 相关镜像"""
    print("步骤 3c: 下载 RuView 感知服务镜像...")
    print("  包含: Rust sensing-server + Python API 层")
    print()
    
    images = [
        {"name": "rust:1.85-slim", "file": "rust-1.85-slim.tar",
         "desc": "Rust 编译环境"},
        {"name": "python:3.11-slim", "file": "python-3.11-slim.tar",
         "desc": "Python 3.11 运行时 (RuView API)"},
        # 可选: 如果使用预构建的 RuView 镜像
        # {"name": "ruvnet/wifi-densepose:latest", "file": "wifi-densepose-latest.tar",
        #  "desc": "RuView WiFi DensePose (官方镜像 ~2GB)"},
    ]
    
    failed = []
    for img in images:
        output_path = image_dir / img["file"]
        
        if output_path.exists():
            size_mb = output_path.stat().st_size / (1024 * 1024)
            print(f"  [OK] 镜像已存在: {img['file']} ({size_mb:.2f} MB)")
            continue
        
        print(f"  拉取镜像: {img['name']} ({img['desc']})...")
        # ... (拉取逻辑同上)
    
    # 下载预训练模型 (可选，~48KB)
    download_ruview_models(image_dir)

def download_ruview_models(image_dir):
    """下载 RuView 预训练模型"""
    print("\n  下载 RuView 预训练模型...")
    
    model_file = image_dir / "wifi-densepose-pretrained.q4.bin"
    if model_file.exists():
        size_kb = model_file.stat().st_size / 1024
        print(f"  [OK] 模型已存在: {model_file.name} ({size_kb:.2f} KB)")
        return
    
    print(f"  从 HuggingFace 下载模型 (约 8 KB)...")
    try:
        import subprocess
        subprocess.run([
            "huggingface-cli", "download",
            "ruvnet/wifi-densepose-pretrained",
            "--local-dir", str(image_dir / "ruview-models"),
            "--include", "model-q4.bin"
        ], check=True, capture_output=True)
        
        # 重命名
        src = image_dir / "ruview-models" / "model-q4.bin"
        if src.exists():
            src.rename(model_file)
            print(f"  [OK] 模型保存成功: {model_file.name}")
    except Exception as e:
        print(f"  [WARN] 模型下载失败: {e}")
        print(f"  提示: 可稍后手动下载或使用模拟数据模式")
```

### 6.2 离线构建流程

完整的离线构建步骤：

```bash
# Step 1: 在联网环境下载所有依赖
cd f:\SoarCloudAI\docker\living-agent-service
python image\download_images.py

# Step 2: 加载镜像到离线 Docker
powershell -File image\load_images.ps1

# Step 3: 离线构建 (包含 RuView)
docker-compose up --build -d ruview-sensing living-agent-service

# Step 4: 验证 RuView 服务
curl http://localhost:8387/api/v1/health

# Step 5: 查看 Living Agent 日志中的 SensorNeuron 启动信息
docker-compose logs -f living-agent-service | grep SensorNeuron
```

### 6.3 模拟数据模式

对于没有硬件的场景，RuView 支持**完全模拟模式**：

```yaml
# docker-compose.yml 环境变量
environment:
  - CSI_SOURCE=simulated  # 使用合成数据，无需 ESP32
  - SIMULATION_MODE=full  # 全功能模拟 (人员+姿态+生命体征)
  - SIMULATION_PERSONS=5  # 模拟 5 个人
  - SIMULATION_ZONES=3    # 模拟 3 个区域
```

**优势**：
- ✅ 无需购买 ESP32 硬件即可开发和测试
- ✅ 可演示完整功能（人员检测、占用统计、跌倒预警等）
- ✅ 用于前端开发和集成测试
- ⚠️ 生产环境需切换为真实硬件或混合模式

---

## 七、业务场景应用

### 7.1 场景 1：智慧会议室管理

**需求**：自动检测会议室占用情况，优化资源分配

**实现**：

```
用户: "会议室A现在有人吗？"
    ↓
TextNeuron → Qwen3Neuron(意图: 查询占用)
    ↓
SensorNeuron → RuView API: /zones/meeting-room-a/occupancy
    ↓
返回: {"zone_id": "meeting-room-a", "current_occupancy": 3, ...}
    ↓
MainBrain/TechBrain: "会议室A目前有3人在使用"
    ↓
MouthNeuron(TTS): 语音播报
```

**增值功能**：
- 自动释放超时预订（空闲 > 30 分钟无人）
- 占用率报表（日/周/月维度）
- 会议开始前 5 分钟提醒参会者

### 7.2 场景 2：安全监控与告警

**需求**：检测跌倒、异常闯入、区域超员等安全事件

**实现**：

```java
// SensorNeuron.checkAlertConditions() 已实现

// 告警事件 → Kafka → 各部门 Brain 处理
// 1. FALL_DETECTED → OpsBrain(运营) → 通知安保 + HR
// 2. INTRUSION → SecurityBrain(法务) → 记录日志 + 告警
// 3. OVER_CAPACITY → AdminBrain(行政) → 疏导提示
```

**告警处理流程**：

```
检测到跌倒事件
    ↓
SensorNeuron → ALERT_CHANNEL
    ↓
Kafka: topic=living-agent.sensor.alert
    ↓
┌─────────────┬─────────────┬─────────────┐
│  OpsBrain   │  HrBrain    │ AdminBrain  │
│  (运营)     │  (人力资源) │  (行政)     │
│             │             │             │
│ 通知安保    │ 启动应急预案│ 更新考勤    │
│ 记录事故    │ 联系家属    │ 统计分析    │
└─────────────┴─────────────┴─────────────┘
```

### 7.3 场景 3：员工健康监护

**需求**：非接触式监测员工呼吸/心率，预防过劳

**实现**：

```
定时任务 (每小时)
    ↓
SensorNeuron → RuView API: /api/v1/vitals/current
    ↓
返回: [
  {"employee_id": "emp_001", "bpm": 72, "breathing_rate": 16},
  {"employee_id": "emp_002", "bpm": 95, "breathing_rate": 22}  ⚠️ 异常
]
    ↓
HrBrain 分析:
  - emp_002 心率和呼吸频率偏高
  - 可能处于压力/疲劳状态
    ↓
自动触发:
  - 发送关怀消息: "您看起来有些疲惫，建议休息一下 ☕"
  - 记录到健康档案
  - 如持续异常 → 通知主管
```

### 7.4 场景 4：智能办公环境

**需求**：根据人员分布自动调节空调、灯光

**实现**：

```
SensorNeuron (实时数据)
    ↓
WebSocket: 每 5 秒推送一次区域占用数据
    ↓
AdminBrain (规则引擎):
  IF zone_occupancy == 0 AND working_hours:
    → 关闭该区域空调/灯光
  IF zone_occupancy > threshold:
    → 调整空调温度至舒适范围
    ↓
执行动作 (通过 Skill 调用智能家居 API)
```

---

## 八、实施路线图

### Phase 1：基础集成（2 周）

**目标**：实现基本的 WiFi 感知数据接入

**任务清单**：

- [ ] **Week 1: 环境准备**
  - [ ] 1.1 下载 RuView Docker 镜像和依赖
      ```bash
      cd docker/living-agent-service
      python image/download_images.py  # 已包含 RuView 镜像
      ```
  - [ ] 1.2 创建 `ruview-integration/` 目录结构
  - [ ] 1.3 编写 `Dockerfile.ruview` 和 `docker-compose.yml` 配置
  - [ ] 1.4 使用模拟数据启动 RuView 服务
      ```bash
      docker-compose up -d ruview-sensing
      curl http://localhost:8387/api/v1/health  # 验证
      ```

- [ ] **Week 2: SensorNeuron 开发**
  - [ ] 2.1 创建 `SensorNeuron.java` 基础框架
  - [ ] 2.2 实现 REST API 客户端（调用 RuView）
  - [ ] 2.3 实现定时轮询逻辑（每 10 秒获取数据）
  - [ ] 2.4 注册到 Spring 容器并测试
  - [ ] 2.5 编写单元测试（Mock RuView API）

**交付物**：
- ✅ RuView 服务可在 Docker 中运行（模拟模式）
- ✅ SensorNeuron 可接收并转发感知数据
- ✅ 基础日志输出正常

### Phase 2：业务对接（3 周）

**目标**：将感知能力应用到具体业务场景

**任务清单**：

- [ ] **Week 3: 数据模型完善**
  - [ ] 3.1 定义完整的 DTO 类（PoseEstimation, ZoneOccupancy, VitalSigns）
  - [ ] 3.2 实现数据校验和异常处理
  - [ ] 3.3 添加 Redis 缓存层（减少 API 调用）
  - [ ] 3.4 实现历史数据查询接口

- [ ] **Week 4: 大脑集成**
  - [ ] 4.1 TechBrain: 会议室占用查询技能
  - [ ] 4.2 OpsBrain: 人员统计和分析技能
  - [ ] 4.3 AdminBrain: 环境监控和自动化技能
  - [ ] 4.4 对话系统集成（用户可通过对话查询感知状态）

- [ ] **Week 5: 告警系统**
  - [ ] 5.1 实现告警规则引擎（跌倒/超员/闯入）
  - [ ] 5.2 Kafka 事件发布（跨大脑通知）
  - [ ] 5.3 告警去重和冷却机制
  - [ ] 5.4 前端告警展示组件

**交付物**：
- ✅ 用户可通过对话查询："会议室A有几人？"
- ✅ 自动生成占用报表
- ✅ 跌倒/闯入告警可推送到前端

### Phase 3：高级特性（4 周）

**目标**：深度融合与智能化

**任务清单**：

- [ ] **Week 6: 实时优化**
  - [ ] 6.1 WebSocket 实时数据流替代轮询
  - [ ] 6.2 数据压缩和增量更新
  - [ ] 6.3 断线重连和心跳检测
  - [ ] 6.4 性能优化（延迟 < 100ms P99）

- [ ] **Week 7: 多模态融合**
  - [ ] 7.1 WiFi + 摄像头数据融合（如有）
  - [ ] 7.2 传感器数据置信度加权
  - [ ] 7.3 异常检测算法（基于历史基线）
  - [ ] 7.4 预测性分析（人员流动预测）

- [ ] **Week 8: 自定义与扩展**
  - [ ] 8.1 区域配置 UI（动态定义监控区域）
  - [ ] 8.2 告警规则可视化配置
  - [ ] 8.3 第三方系统集成（钉钉/飞书/企业微信通知）
  - [ ] 8.4 数据导出和报表定制

- [ ] **Week 9: 硬件对接（可选）**
  - [ ] 9.1 ESP32-S3 固件烧录
  - [ ] 9.2 WiFi 凭据配置
  - [ ] 9.3 传感器节点部署
  - [ ] 9.4 从模拟模式切换到真实数据

**交付物**：
- ✅ 实时感知仪表盘（< 1 秒延迟）
- ✅ 智能告警系统（准确率 > 95%）
- ✅ 可选的真实硬件部署文档

### Phase 4：生产化（2 周）

**目标**：生产环境部署和运维准备

**任务清单**：

- [ ] **Week 10: 性能与稳定性**
  - [ ] 10.1 压力测试（并发 100+ 请求）
  - [ ] 10.2 故障恢复测试（RuView 宕机影响评估）
  - [ ] 10.3 资源优化（内存 < 2GB, CPU < 2 核）
  - [ ] 10.4 监控指标接入（Prometheus + Grafana）

- [ ] **Week 11: 文档与培训**
  - [ ] 11.1 运维手册编写
  - [ ] 11.2 故障排查指南
  - [ ] 11.3 用户使用手册
  - [ ] 11.4 团队培训和知识转移

**交付物**：
- ✅ 生产级部署方案
- ✅ 完整的运维文档
- ✅ SLA 保障（可用性 > 99.9%）

---

## 九、风险与缓解

### 9.1 技术风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| **WiFi 信号不稳定** | 高 | 中 | ① 多节点冗余<br>② 信号强度阈值过滤<br>③ 降级为 RSSI-only 模式 |
| **多人识别准确率下降** | 中 | 高 | ① 结合摄像头融合（可选）<br>② 调整 confidence_threshold<br>③ 定期重新校准 |
| **网络延迟影响实时性** | 低 | 中 | ① WebSocket 替代轮询<br>② 边缘预处理<br>③ 本地缓存策略 |
| **ESP32 硬件兼容性** | 中 | 中 | ① 提供认证硬件列表<br>② 固件版本锁定<br>③ 模拟模式兜底 |
| **RuView API 变更** | 低 | 中 | ① 版本锁定策略<br>② 适配层抽象<br>③ 兼容性测试 |

### 9.2 业务风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| **隐私担忧** | 高 | 高 | ① 强调无摄像头、数据加密<br>② 仅存储元数据（不留存生物特征）<br>③ GDPR/个人信息保护合规 |
| **误报导致警报疲劳** | 中 | 中 | ① 智能过滤算法<br>② 冷却期机制<br>③ 人工确认流程 |
| **员工抵触情绪** | 中 | 中 | ① 透明告知用途<br>② 提供 opt-out 选项<br>③ 强调安全和健康价值 |
| **法规合规问题** | 中 | 高 | ① 法务团队审核<br>② 数据本地化存储<br>③ 最小化数据收集原则 |

### 9.3 运维风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| **资源争抢（GPU/CPU）** | 中 | 中 | ① RuView 使用 CPU 模式<br>② 资源限制（Docker limits）<br>③ 独立部署避免干扰 |
| **依赖服务故障** | 低 | 高 | ① 降级策略（缓存历史数据）<br>② 熔断机制<br>③ 快速恢复 SOP |
| **升级兼容性** | 中 | 中 | ① 版本矩阵测试<br>② 灰度发布<br>③ 回滚方案 |

---

## 十、成功指标

### 10.1 技术指标

| 指标 | 目标值 | 测量方法 |
|------|--------|---------|
| **API 响应时间 (P99)** | < 100ms | Prometheus histogram |
| **WebSocket 延迟** | < 50ms | 端到端 ping 测量 |
| **服务可用性** | > 99.9% | Uptime 监控 |
| **人员检测准确率** | > 95% | 人工标注对比 |
| **跌倒检测召回率** | > 90% | 模拟测试 + 真实案例 |
| **误报率** | < 5次/天 | 告警日志统计 |

### 10.2 业务指标

| 指标 | 目标值 | 测量方法 |
|------|--------|---------|
| **会议室利用率提升** | > 20% | 前后对比分析 |
| **安全事件响应时间** | 缩短 50% | 工单系统统计 |
| **用户满意度** | > 4.5/5 | 问卷调查 |
| **能耗降低** | > 15% | 电费账单对比 |
| **员工健康干预次数** | 可量化 | 系统日志统计 |

### 10.3 项目里程碑

| 里程碑 | 时间点 | 验收标准 |
|--------|--------|---------|
| **M1: 基础集成完成** | Phase 1 结束 | RuView + SensorNeuron 可运行 |
| **M2: 业务场景上线** | Phase 2 结束 | 3 个以上场景可用 |
| **M3: 高级特性交付** | Phase 3 结束 | 实时 + 智能 + 可扩展 |
| **M4: 生产化部署** | Phase 4 结束 | SLA 达标 + 文档齐全 |

---

## 附录

### A. 参考资源

- **RuView GitHub**: https://github.com/ruvnet/RuView
- **RuView 文档**: https://ruvnet.github.io/RuView/
- **WiFi DensePose 模型**: https://huggingface.co/ruvnet/wifi-densepose-pretrained
- **ESP32-S3 文档**: https://docs.espressif.com/projects/esp-idf/en/latest/esp32s3/
- **Living Agent 架构文档**: ../core/02-core-architecture.md

### B. 术语表

| 术语 | 解释 |
|------|------|
| **CSI (Channel State Information)** | 信道状态信息，描述 WiFi 信号在传播过程中的变化 |
| **DensePose** | 密集人体姿态估计，将人体建模为 3D 表面 |
| **ESP32-S3** | 乐鑫科技的 WiFi/蓝牙 MCU，支持 CSI 采集 |
| **Neuron** | Living Agent 中的神经元抽象，负责感知/处理/输出 |
| **Channel** | Living Agent 中的消息通道，用于 Neuron 间异步通信 |
| **Brain** | Living Agent 中的业务大脑，负责特定领域的推理和决策 |

### C. 常见问题

**Q1: 是否必须购买 ESP32 硬件？**

A: 不必。RuView 支持模拟数据模式（`CSI_SOURCE=simulated`），可用于开发、测试和演示。生产环境建议使用真实硬件以获得最佳效果。

**Q2: 隐私如何保障？**

A: RuView 基于 WiFi 信号工作，**不采集视频或图像**。系统仅存储元数据（人数、区域、活动类型），不留存生物特征信息。所有数据加密传输和存储。

**Q3: 与现有 AI 服务（ASR/TTS）会冲突吗？**

A: 不会。RuView 作为独立微服务部署，资源隔离。SensorNeuron 仅在需要感知数据时才调用 RuView API，不影响 ASR/TTS 的性能。

**Q4: 可以同时支持多少个传感器节点？**

A: 理论上无限制。实际受限于网络带宽和处理能力。建议从 3-6 个节点开始，根据需求逐步扩展。

**Q5: 如何从 SoarCloudAI 旧版迁移？**

A: 本方案为增量集成，不影响现有功能。只需在 `docker-compose.yml` 中添加 RuView 服务，并在 Living Agent 中启用 SensorNeuron 即可。

---

## 文档修订记录

| 版本 | 日期 | 作者 | 变更内容 |
|------|------|------|---------|
| v1.0 | 2026-05-21 | AI Assistant | 初稿，完整方案设计 |

---

**状态**: 🟡 待评审 → 🟢 待实施 → ✅ 已完成
