# 音频处理流程优化文档

## 概述

本文档描述 Living Agent Service 的音频处理完整数据流，以及已完成的优化工作。

## 当前架构

### 三层 LLM 架构

```
┌─────────────────────────────────────────────────────────────────┐
│ Layer 1: MainBrain (Qwen3.5-27B 或其他大模型)                    │
│ - 复杂推理、跨部门协调、战略决策                                    │
│ - 通过 API 调用                                                  │
└─────────────────────────────────────────────────────────────────┘
                              ▲
                              │ 复杂问题路由
┌─────────────────────────────────────────────────────────────────┐
│ Layer 2: 闲聊神经元 (Qwen3Neuron) - Qwen3-0.6B                   │
│ - 日常对话、快速响应、简单任务                                    │
│ - 快速响应，低延迟                                                │
└─────────────────────────────────────────────────────────────────┘
                              ▲
                              │ 任务转达
┌─────────────────────────────────────────────────────────────────┐
│ Layer 3: 工具神经元 (ToolNeuron) - Qwen3.5-2B (默认) / BitNet-1.58-3B (备选) │
│ - 工具检测、兜底处理、部门引导                                     │
│ - 神经网络路由到部门/MainBrain                                    │
└─────────────────────────────────────────────────────────────────┘
```

### 公司前台双模型分工

| 模型 | 职责 | 触发条件 |
|------|------|----------|
| Qwen3-0.6B | 沟通、表达、高效回复 | 问候、闲聊、简单问题 |
| Qwen3.5-2B | 任务转达、部门引导 | 部门关键词、任务关键词、工具调用 |

## 完整数据流

### 已实现的数据流

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              前端 (living-agent-frontend)                    │
│                                                                              │
│  useVoiceDialogue.ts:                                                        │
│  - Opus 编码: 16kHz, 1ch, frameSize=960                                      │
│  - VAD 检测: EnhancedVAD (能量+过零率+频谱特征)                                │
│  - WebSocket 发送: { type: "audio_full", audio: base64(opusData) }           │
│  - WebSocket 接收: { type: "audio_response", audio: base64(opusData) }       │
│  - Opus 解码播放                                                              │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      │ WebSocket (Opus Base64)
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Gateway (AgentWebSocketHandler.java)                      │
│                                                                              │
│  handleAudioFullChainMessage():                                              │
│  1. 解析 Base64 Opus 数据                                                    │
│  2. 调用 Rust Native decode_opus() → PCM                                     │
│  3. 保存 PCM 到临时文件                                                       │
│  4. 调用 model_daemon ASR → 文本                                             │
│  5. 调用 model_daemon Chat → 文本响应 + 路由信息                              │
│  6. 调用 model_daemon TTS → PCM 音频                                         │
│  7. 调用 Rust Native encode_pcm() → Opus                                     │
│  8. 返回 Base64 Opus 数据                                                    │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      │ JNI 调用
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     living-agent-native (Rust)                               │
│                                                                              │
│  AudioProcessor: sample_rate=16000, frame_size=960                           │
│  - createProcessor(sampleRate, channels, frameSize, enableVad) → handle      │
│  - decode_opus(handle, opus_data) → PCM (i16)                                │
│  - encode_pcm(handle, pcm_data) → Opus                                       │
│  - detect_voice_activity(handle, pcm_data) → bool                            │
│  - getStats(handle) → JSON string                                            │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      │ 命名管道
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     model_daemon.py (Python 守护进程)                         │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  ASR: Sherpa-NCNN SenseVoice                                         │    │
│  │  - 输入: WAV/PCM 音频文件路径                                         │    │
│  │  - 输出: 文本                                                         │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                         │                                                    │
│                         ▼                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  意图分类: DualModelIntentClassifier                                  │    │
│  │  - 问候 → 快速响应 (无LLM)                                            │    │
│  │  - 部门/任务关键词 → Qwen3.5-2B (任务转达)                             │    │
│  │  - 复杂问题 → MainBrain 路由                                          │    │
│  │  - 闲聊 → Qwen3-0.6B (沟通)                                          │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                         │                                                    │
│           ┌─────────────┴─────────────┐                                     │
│           ▼                           ▼                                     │
│  ┌──────────────────┐      ┌──────────────────┐                            │
│  │ Qwen3-0.6B       │      │ Qwen3.5-2B       │                            │
│  │ (沟通/表达)       │      │ (任务转达/工具)   │                            │
│  └──────────────────┘      └────────┬─────────┘                            │
│                                     │                                       │
│                                     ▼                                       │
│                    ┌────────────────────────────────┐                       │
│                    │ 神经网络路由器 (NeuronRouter)    │                       │
│                    │ - 路由到部门神经元               │                       │
│                    │ - 路由到 MainBrain              │                       │
│                    └────────────────────────────────┘                       │
│                         │                                                    │
│                         ▼                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  TTS: MeloTTS (带 MD5 缓存)                                          │    │
│  │  - 输入: 文本                                                         │    │
│  │  - 输出: PCM 音频 (int16, 16kHz, mono)                               │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 已完成的优化

### 优化 1: Rust JNI 接口参数匹配 ✅

**位置**: `living-agent-native/src/jni/audio_jni.rs`

**修复内容**:
- 添加 `frameSize` 和 `enableVad` 参数到 `createProcessor`
- 修复 `jboolean` 类型问题 (Rust 中 `jboolean` 是 `bool` 类型)
- 添加 `getStats()` 方法

### 优化 2: AgentWebSocketHandler 完整音频处理链路 ✅

**位置**: `living-agent-gateway/.../AgentWebSocketHandler.java`

**新增内容**:
- 添加 `handleAudioFullChainMessage()` 方法
- 支持 `audio_full` 消息类型
- 实现完整链路: Opus解码 → ASR → LLM → TTS → Opus编码

### 优化 3: TTS 输出 PCM 数据 ✅

**位置**: `model_daemon.py` 的 `synthesize_speech()`

**修复内容**:
- 始终返回 `int16` PCM 数据 (`audio_data` 字段)
- 支持 TTS 缓存命中返回缓存数据

### 优化 4: 神经网络路由器 ✅

**位置**: `model_daemon.py` 的 `NeuronRouter` 类

**实现内容**:
- 支持 7 个部门路由: tech/hr/fin/admin/legal/sales/cs
- 支持 MainBrain 路由判断
- 集成到 `generate_chat_response()` 方法

### 优化 5: 快速问候响应 ✅

**位置**: `model_daemon.py` 的 `QuickGreetingGenerator` 类

**实现内容**:
- 无需 LLM 调用的即时响应
- 时间敏感问候 (早上好/下午好/晚上好)
- "在吗"检测快速响应

### 优化 6: TTS 缓存机制 ✅

**位置**: `model_daemon.py` 的 `TTSCache` 类

**实现内容**:
- MD5 哈希缓存
- LRU 淘汰策略
- 命中率统计

## 音频格式规范

### 前端 Opus 配置

```typescript
const DEFAULT_OPUS_CONFIG = {
  sampleRate: 16000,    // 16kHz
  channels: 1,          // 单声道
  frameSize: 960,       // 20ms @ 16kHz
  bitrate: 24000,       // 24kbps
  application: 2049,    // VOIP
  complexity: 10,
}
```

### Rust Native 配置

```rust
const DEFAULT_SAMPLE_RATE: u32 = 16000;
const DEFAULT_FRAME_SIZE: usize = 960;  // 20ms @ 16kHz
```

### TTS 输出配置

```python
target_sample_rate = 16000  # 重采样到 16kHz
channels = 1                # 单声道
audio_format = 'int16'      # PCM 格式
```

## WebSocket 消息协议

### 客户端 → 服务端

```json
{
  "type": "audio_full",
  "audio": "<base64-opus-data>"
}
```

### 服务端 → 客户端

```json
{
  "type": "audio_response",
  "text": "<识别的文本>",
  "response": "<LLM响应文本>",
  "audio": "<base64-opus-data>",
  "model": "qwen3-0.6b",
  "intent": "casual_chat",
  "latency_ms": 1234,
  "routing": {
    "success": true,
    "action": "route_to_department",
    "department": "tech",
    "channel": "channel://tech/tasks"
  }
}
```

### 错误响应

```json
{
  "type": "error",
  "message": "<错误信息>"
}
```

## 性能优化要点

1. **TTS 缓存**: 使用 MD5 哈希缓存常用响应
2. **快速问候**: 无需 LLM 调用的即时响应
3. **并行处理**: ASR 和 LLM 预加载
4. **流式响应**: 支持 TTS 流式输出

## 编译脚本

使用 `image/build_rust_native.bat` 一键编译 Rust Native 库:

```batch
cd f:\SoarCloudAI\docker\living-agent-service\image
build_rust_native.bat
```

编译产物: `image/libliving_agent_native.so`

## 测试验证

1. 单元测试: 各组件独立测试
2. 集成测试: 完整链路测试
3. 性能测试: 延迟、吞吐量测试
4. 端到端测试: 前端到后端完整测试
