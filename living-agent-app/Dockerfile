# Living Agent Service Dockerfile
# Multi-stage build for optimized image size
#
# 构建方式:
#   1. 在线构建 (需要网络): docker build -f living-agent-app/Dockerfile -t living-agent-service .
#   2. 离线构建 (使用本地依赖): docker build -f living-agent-app/Dockerfile.local -t living-agent-service .

# ===========================================
# Stage 1: Build stage - Java/Maven
# ===========================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# 安装 Maven (项目没有 Maven Wrapper)
RUN apk add --no-cache maven

# 复制 pom 文件 (利用 Docker 缓存层)
COPY pom.xml ./
COPY living-agent-app/pom.xml living-agent-app/pom.xml
COPY living-agent-core/pom.xml living-agent-core/pom.xml
COPY living-agent-skill/pom.xml living-agent-skill/pom.xml
COPY living-agent-gateway/pom.xml living-agent-gateway/pom.xml
COPY living-agent-perception/pom.xml living-agent-perception/pom.xml

# 下载依赖 (利用缓存)
RUN mvn dependency:go-offline -B || true

# 复制源代码
COPY living-agent-app living-agent-app
COPY living-agent-core living-agent-core
COPY living-agent-skill living-agent-skill
COPY living-agent-gateway living-agent-gateway
COPY living-agent-perception living-agent-perception

# 构建 Rust native 库 (如果有 Rust 源码)
RUN apk add --no-cache rust cargo opus-dev sqlite-dev openssl-dev
COPY living-agent-native living-agent-native
RUN cd living-agent-native && cargo build --release || true

# 构建应用
RUN mvn clean package -DskipTests -pl living-agent-app -am

# ===========================================
# Stage 2: Runtime stage
# ===========================================
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Living Agent Team"
LABEL description="AI Enterprise Management Agent Service with Skills Support"
LABEL version="1.0.0"

# 安装系统依赖
RUN apk add --no-cache \
    curl \
    bash \
    git \
    python3 \
    py3-pip \
    docker-cli \
    nodejs \
    npm \
    openssh-client \
    ca-certificates \
    # Rust 运行时依赖
    libgcc \
    # 音频处理依赖
    opus \
    opus-tools \
    ffmpeg \
    # SQLite 依赖
    sqlite-libs \
    && rm -rf /var/cache/apk/*

# 安装 Python 包 (文档处理工具)
RUN pip3 install --no-cache-dir --break-system-packages \
    pypdf \
    pdfplumber \
    python-docx \
    openpyxl \
    python-pptx \
    huggingface-hub \
    requests \
    beautifulsoup4 \
    lxml

# 创建非 root 用户
RUN addgroup -S livingagent && adduser -S livingagent -G livingagent

# 添加 livingagent 到 docker 组 (用于 Docker socket 访问)
RUN addgroup -S docker 2>/dev/null || true
RUN adduser livingagent docker 2>/dev/null || true

# 设置工作目录
WORKDIR /app

# 复制应用 JAR
COPY --from=builder /build/living-agent-app/target/*.jar app.jar

# 创建 lib 目录
RUN mkdir -p /app/lib

# 复制 Rust native 库 (如果存在)
RUN --mount=type=bind,from=builder,source=/build/living-agent-native/target/release,target=/tmp/native \
    if [ -f /tmp/native/libliving_agent_native.so ]; then \
        cp /tmp/native/libliving_agent_native.so /app/lib/; \
    fi

# 复制技能目录
COPY living-agent-skill/src/main/resources/skills /app/skills

# 创建必要目录
RUN mkdir -p /app/data /app/logs /app/.cache /app/.config && \
    chown -R livingagent:livingagent /app

# 设置环境变量
ENV LIVING_AGENT_DATA_PATH=/app/data \
    LIVING_AGENT_SKILLS_PATH=/app/skills \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    LD_LIBRARY_PATH=/app/lib:$LD_LIBRARY_PATH

# 切换到非 root 用户
USER livingagent

# 暴露端口
EXPOSE 8380

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8380/actuator/health || exit 1

# JVM 选项
ENV JAVA_OPTS="-Xms512m -Xmx1536m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
