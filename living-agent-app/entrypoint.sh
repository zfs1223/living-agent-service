#!/bin/bash
# Living Agent Service 启动脚本
# 先启动模型守护进程，再启动 Java 应用

set -e

echo "=========================================="
echo "Living Agent Service - 启动中..."
echo "=========================================="

# 设置环境变量
export PYTHONUNBUFFERED=1
export PYTHONDONTWRITEBYTECODE=1

# 模型路径
SHERPA_MODEL_DIR=${SHERPA_MODEL_DIR:-/app/ai-models/sherpa-ncnn/sherpa-ncnn-sense-voice-zh-en-ja-ko-yue-2025-09-09}
QWEN3_MODEL_FILE=${QWEN3_MODEL_FILE:-/app/ai-models/Qwen3-0.6B-GGUF/Qwen3-0.6B-Q8_0.gguf}
QWEN35_MODEL_FILE=${QWEN35_MODEL_FILE:-/app/ai-models/Qwen3.5-2B-GGUF/Qwen3.5-2B-Q4_K_M.gguf}
MELOTTS_MODEL_DIR=${MELOTTS_MODEL_DIR:-/app/ai-models/MeloTTS}

# 检查模型守护进程脚本是否存在
DAEMON_SCRIPT="/opt/python_scripts/model_daemon.py"
if [ -f "$DAEMON_SCRIPT" ]; then
    echo "[Startup] 启动模型守护进程..."
    
    # 导出环境变量供守护进程使用
    export SHERPA_MODEL_DIR
    export QWEN3_MODEL_FILE
    export QWEN35_MODEL_FILE
    export MELOTTS_MODEL_DIR
    
    # 后台启动守护进程
    python3 "$DAEMON_SCRIPT" &
    DAEMON_PID=$!
    echo "[Startup] 模型守护进程 PID: $DAEMON_PID"
    
    # 等待守护进程初始化 (最多等待60秒)
    echo "[Startup] 等待模型加载..."
    WAIT_COUNT=0
    MAX_WAIT=60
    
    while [ ! -p /tmp/dialogue_daemon_control_request ]; do
        sleep 1
        WAIT_COUNT=$((WAIT_COUNT + 1))
        if [ $WAIT_COUNT -ge $MAX_WAIT ]; then
            echo "[Startup] ⚠️ 等待守护进程超时，继续启动 Java 应用..."
            break
        fi
    done
    
    if [ -p /tmp/dialogue_daemon_control_request ]; then
        echo "[Startup] ✅ 模型守护进程就绪"
    fi
else
    echo "[Startup] ⚠️ 模型守护进程脚本不存在: $DAEMON_SCRIPT"
    echo "[Startup] 将使用远程模型服务 (Ollama)"
fi

echo "=========================================="
echo "[Startup] 启动 Java 应用..."
echo "=========================================="

# 启动 Java 应用
exec java $JAVA_OPTS -jar app.jar
