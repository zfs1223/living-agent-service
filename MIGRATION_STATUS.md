# 服务架构检查报告

## 一、架构对比总结

### dialogue-service 服务清单
| 服务 | 功能 | 移植状态 |
|------|------|----------|
| AsrService | ASR语音识别 | ✅ 已有Provider架构 |
| TtsService | TTS语音合成 | ✅ 已有Provider架构 |
| LlmService | LLM调用 | ✅ 已有Neuron架构 |
| DialogueSystemService | 对话系统核心 | ✅ 已有Gateway模块 |
| ToolCallManager | 工具调用管理 | ✅ 已有Tool架构 |
| UserProfileService | 用户画像 | ✅ 已整合到Employee |
| SpeakerVerificationService | 声纹验证 | ✅ 已有实现 |
| SpeakerLibraryService | 声纹库管理 | ✅ 已整合到EmployeeService |
| WeatherService | 天气服务 | ✅ 已有WeatherTool |
| McpClientService | MCP客户端 | ✅ 已移到技能目录 (core/mcp-client) |
| HomeAssistantClient | 智能家居 | ✅ 已移到技能目录 (ops/smart-home) |

### living-agent-service 架构优势

| 特性 | dialogue-service | living-agent-service |
|------|------------------|----------------------|
| 架构模式 | 单体服务 | **微服务+模块化** |
| 高性能模块 | 无 | **Rust Native** |
| 大脑系统 | 无 | **8个部门大脑** |
| 神经元系统 | 无 | **Qwen3/BitNet/Router** |
| 进化系统 | 无 | **信号/人格/熔断器** |
| 权限系统 | 简单 | **UserIdentity + AccessLevel** |

## 二、用户身份体系

### UserIdentity (已更新)
```java
INTERNAL_ACTIVE      // 在职员工
INTERNAL_PROBATION   // 试用期员工
INTERNAL_DEPARTED    // 离职员工
EXTERNAL_VISITOR     // 外来访客
EXTERNAL_CUSTOMER    // 客户 (新增)
EXTERNAL_PARTNER     // 合作伙伴
EXTERNAL_CONTRACTOR  // 外包人员
```

### AccessLevel
```java
CHAT_ONLY    // 仅闲聊
LIMITED      // 受限访问
DEPARTMENT   // 部门访问
FULL         // 完全访问
```

## 三、EmployeeService (员工+声纹)

Employee类已包含：
- employeeId, name, phone, email
- department, position
- identity (UserIdentity)
- accessLevel (AccessLevel)
- **voicePrintId** (声纹ID)
- oauthProvider, oauthUserId
- allowedBrains (可访问的大脑)

EmployeeService支持：
- findByVoicePrintId() - 通过声纹查找员工
- setVoicePrintId() - 设置声纹ID
- linkOAuthAccount() - 关联OAuth账号

## 四、新增技能

### smart-home (智能家居技能)
位置: `living-agent-skill/src/main/resources/skills/ops/smart-home/`

功能:
- 设备控制 (灯光、空调、窗帘)
- 场景切换
- 状态查询

### mcp-client (MCP客户端技能)
位置: `living-agent-skill/src/main/resources/skills/core/mcp-client/`

功能:
- 连接MCP服务器
- 调用工具
- 访问资源
- 获取提示词

## 五、配置路径

### application.yml
```yaml
ai-models:
  base-path: ${AI_MODELS_PATH:/app/ai-models}

# 技能相关配置在技能的 SKILL.md 中说明
```

### docker-compose.yml
```yaml
volumes:
  - ${AI_MODELS_PATH:-../../ai-models}:/app/ai-models:ro
  - ./scripts/python:/opt/python_scripts:ro
  - ./living-agent-skill/src/main/resources/skills:/app/skills:ro
```

## 六、启动说明

```bash
cd f:\SoarCloudAI\docker\living-agent-service

# Windows
set AI_MODELS_PATH=f:\SoarCloudAI\ai-models
docker-compose up -d

# 访问
# 主服务: http://localhost:8380
# WebSocket: ws://localhost:8380/ws/voice
```

## 七、架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Living Agent Service 架构                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐         │
│  │   Gateway       │    │   Core          │    │   Native (Rust) │         │
│  │   (Java)        │    │   (Java)        │    │   (高性能)       │         │
│  ├─────────────────┤    ├─────────────────┤    ├─────────────────┤         │
│  │ - WebSocket     │    │ - Neuron       │    │ - Audio处理     │         │
│  │ - REST API      │    │ - Brain        │    │ - Opus编解码    │         │
│  │ - Controller    │    │ - Channel      │    │ - VAD检测       │         │
│  │                 │    │ - Tool         │    │ - 向量存储      │         │
│  │                 │    │ - Security     │    │ - 安全验证      │         │
│  │                 │    │ - Evolution    │    │                 │         │
│  └─────────────────┘    └─────────────────┘    └─────────────────┘         │
│           │                     │                     │                    │
│           └─────────────────────┼─────────────────────┘                    │
│                                 │                                          │
│  ┌──────────────────────────────▼──────────────────────────┐              │
│  │                    用户身份体系                           │              │
│  ├──────────────────────────────────────────────────────────┤              │
│  │  INTERNAL_ACTIVE → DEPARTMENT → 8个部门大脑              │              │
│  │  INTERNAL_PROBATION → LIMITED → 部分大脑                  │              │
│  │  EXTERNAL_CUSTOMER → LIMITED → 部分大脑                   │              │
│  │  EXTERNAL_VISITOR → CHAT_ONLY → 仅闲聊                    │              │
│  └──────────────────────────────────────────────────────────┘              │
│                                                                             │
│  ┌──────────────────────────────▼──────────────────────────┐              │
│  │                    AI Models (本地)                       │              │
│  ├──────────────────────────────────────────────────────────┤              │
│  │  - Qwen3-0.6B (LLM)                                      │              │
│  │  - BitNet-1.58-3B (工具检测)                              │              │
│  │  - Sherpa-NCNN (ASR)                                     │              │
│  │  - MeloTTS (TTS)                                         │              │
│  │  - CAM++ (声纹)                                          │              │
│  └──────────────────────────────────────────────────────────┘              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```
