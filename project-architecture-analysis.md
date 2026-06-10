# Living Agent Service 项目架构分析报告

## 执行摘要

基于对Living Agent Service项目代码的深入分析，该系统确实实现了文档中描述的核心架构设计。系统采用三层LLM架构，具备完善的权限控制机制、神经元路由逻辑和Channel通信系统。

## 1. 三层LLM架构实现分析

### 1.1 架构配置验证

**配置文件**: `living-agent-app/src/main/resources/application.yml`

系统在配置中明确定义了三层架构：

```yaml
# Layer 1: MainBrain (决策层)
ollama:
  default-model: qwen3.5:9b  # 当前使用9B，预留27B
  models:
    - name: qwen3.5:27b
      enabled: false  # 待部署
      description: "Qwen3.5 27B - 主大脑模型"

# Layer 2: ChatNeuron (执行层)  
llm:
  default-model: qwen3-0.6b
  models:
    - name: qwen3-0.6b
      type: local
      model-path: /app/ai-models/Qwen3-0.6B-GGUF/Qwen3-0.6B-Q8_0.gguf

# Layer 3: ToolNeuron (工具层)
tool-neuron:
  model:
    default: qwen3.5-2b
    models:
      - id: qwen3.5-2b
        memory-mb: 4096
        context-length: 262144
        multimodal: true
      - id: bitnet-1.58-3b
        memory-mb: 1024
        context-length: 4096
        multimodal: false
```

**发现问题**: MainBrain当前使用qwen3.5:9b而非设计的27B模型，但系统已预留27B配置。

## 2. 权限控制机制分析

### 2.1 AccessLevel枚举实现

**文件**: `living-agent-core/src/main/java/com/livingagent/core/security/AccessLevel.java`

系统实现了四级权限控制：

```java
public enum AccessLevel {
    CHAT_ONLY(0, "仅闲聊", Set.of("Qwen3-0.6B"), Set.of(), false),
    LIMITED(1, "受限访问", Set.of("Qwen3.5-27B", "Qwen3-0.6B"), Set.of("AdminBrain", "CsBrain"), true),
    DEPARTMENT(2, "部门访问", Set.of("Qwen3.5-27B", "Qwen3-0.6B", "BitNet-1.58-3B"), Set.of("TechBrain", "HrBrain", ...), true),
    FULL(3, "完全访问", Set.of("Qwen3.5-27B", "Qwen3-0.6B", "BitNet-1.58-3B"), Set.of("...", "MainBrain"), true)
}
```

**权限隔离矩阵**:
- CHAT_ONLY: 只能访问Qwen3-0.6B，无法访问任何大脑
- LIMITED: 可访问AdminBrain和CsBrain
- DEPARTMENT: 可访问本部门大脑和工具
- FULL: 可访问所有资源包括MainBrain

## 3. 神经元路由逻辑分析

### 3.1 ChatIntentClassifier实现

**文件**: `living-agent-core/src/main/java/com/livingagent/core/neuron/chat/ChatIntentClassifier.java`

意图分类器实现了5种意图识别：

```java
public enum ChatIntent {
    GREETING,        // 问候 -> Qwen3Neuron
    CASUAL_CHAT,     // 闲聊 -> Qwen3Neuron  
    SIMPLE_QUESTION, // 简单问题 -> Qwen3Neuron
    TOOL_CALL,       // 工具调用 -> ToolNeuron
    COMPLEX_TASK,    // 复杂任务 -> MainBrain
    UNKNOWN          // 未知 -> Qwen3Neuron (默认)
}
```

**关键词匹配规则**:
- 工具关键词: "查询", "搜索", "执行", "git", "docker", "天气"等
- 复杂任务关键词: "分析", "设计", "规划", "架构", "方案"等
- 问候词: "你好", "hi", "hello", "早上好"等

### 3.2 ChatNeuronRouter路由决策

**文件**: `living-agent-core/src/main/java/com/livingagent/core/neuron/chat/ChatNeuronRouter.java`

路由器实现了权限感知的路由决策：

```java
private Neuron selectTargetNeuronWithPermission(ClassificationResult classification,
                                                AccessLevel accessLevel,
                                                String departmentId,
                                                Map<String, Object> context) {
    return switch (classification.getIntent()) {
        case GREETING, CASUAL_CHAT, SIMPLE_QUESTION -> chatNeuron;
        case TOOL_CALL -> {
            if (accessLevel.getLevel() >= AccessLevel.DEPARTMENT.getLevel()) {
                yield toolNeuron != null ? toolNeuron : chatNeuron;
            }
            yield chatNeuron; // 权限不足降级
        }
        case COMPLEX_TASK -> {
            if (accessLevel == AccessLevel.FULL && mainBrain != null) {
                yield mainBrain;
            }
            if (accessLevel == AccessLevel.DEPARTMENT) {
                yield getDepartmentBrain(departmentId, classification, context);
            }
            yield chatNeuron; // 权限不足降级
        }
        case UNKNOWN -> chatNeuron;
    };
}
```

**路由规则验证**:
- 所有用户都可以访问ChatNeuron (Qwen3-0.6B)
- DEPARTMENT及以上权限可访问ToolNeuron
- 只有FULL权限可访问MainBrain
- 权限不足时自动降级到ChatNeuron

## 4. WebSocket通信流程分析

### 4.1 前端WebSocket实现

**文件**: `frontend/src/pages/Chat.tsx`

前端根据用户身份和对话目标选择WebSocket端点：

```typescript
let wsUrl: string;
if (id) {
    // 与特定数字员工对话
    wsUrl = `${protocol}//${window.location.host}/ws/agent?token=${token}&agentId=${encodeURIComponent(id)}`;
} else if (brainDept) {
    // 与部门大脑对话
    wsUrl = `${protocol}//${window.location.host}/ws/dept/${encodeURIComponent(brainDept)}?token=${token}`;
} else {
    // 根据用户身份选择通道
    if (user?.identity === 'INTERNAL_ENTERPRISE' || user?.access_level === 'FULL') {
        wsUrl = `${protocol}//${window.location.host}/ws/enterprise?token=${token}`;
    } else if (user?.department_id) {
        wsUrl = `${protocol}//${window.location.host}/ws/dept/${encodeURIComponent(user.department_id)}?token=${token}`;
    } else {
        wsUrl = `${protocol}//${window.location.host}/ws/public?token=${token}`;
    }
}
```

### 4.2 后端WebSocket处理

**文件**: `living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/AgentWebSocketHandler.java`

后端WebSocket处理器实现了：
- 连接建立时的权限验证
- 消息类型路由 (text, audio, audio_full, control)
- 会话管理和状态跟踪

```java
@Override
protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    Map<String, Object> request = objectMapper.readValue(payload, Map.class);
    String type = (String) request.getOrDefault("type", "unknown");
    
    switch (type) {
        case "text" -> handleTextMessage(session, request);
        case "audio" -> handleAudioMessage(session, request);
        case "audio_full" -> handleAudioFullChainMessage(session, request);
        case "control" -> handleControlMessage(session, request);
        default -> sendError(session, "Unknown message type: " + type);
    }
}
```

## 5. Channel通信机制分析

### 5.1 Channel配置

**配置文件**: `application.yml`

系统定义了多种Channel类型：

```yaml
channels:
  perception:
    audio:
      id: channel://perception/audio
      type: UNICAST
    text:
      id: channel://perception/text
      type: UNICAST
  dispatch:
    tech:
      id: channel://tech/tasks
      type: ROUND_ROBIN
      max-concurrency: 5
  output:
    text:
      id: channel://output/text
      type: BROADCAST
```

### 5.2 ChannelManager接口

**文件**: `living-agent-core/src/main/java/com/livingagent/core/channel/ChannelManager.java`

Channel管理器提供了完整的发布订阅机制：

```java
public interface ChannelManager {
    void publish(String channelId, ChannelMessage message);
    void broadcast(String pattern, ChannelMessage message);
    void subscribe(String channelId, ChannelSubscriber subscriber);
    void unsubscribe(String channelId, String subscriberId);
}
```

## 6. 发现的问题和建议

### 6.1 配置问题

1. **MainBrain模型配置**: 当前使用qwen3.5:9b，但设计要求27B
2. **MemOS服务不健康**: 配置验证错误导致服务启动失败
3. **Qdrant健康检查**: 健康检查端点配置可能有误

### 6.2 架构完整性

**优点**:
- 三层LLM架构设计清晰且已实现
- 权限控制机制完善，支持四级权限
- 神经元路由逻辑合理，支持意图分类和权限验证
- WebSocket通信流程完整，支持多种消息类型
- Channel通信机制设计良好，支持多种分发模式

**待完善**:
- NeuronRegistry具体实现类未找到，可能在其他模块
- ChannelManager具体实现需要进一步验证
- 自主进化系统的具体实现需要深入分析

## 7. 测试建议

基于架构分析，建议重点测试：

1. **权限隔离**: 验证不同AccessLevel用户的访问控制
2. **路由逻辑**: 测试ChatIntentClassifier的意图识别准确性
3. **模型切换**: 验证三层架构中的模型选择和切换
4. **Channel通信**: 测试神经元间的消息传递机制
5. **WebSocket稳定性**: 验证长连接的稳定性和错误恢复

## 8. 结论

Living Agent Service项目的核心架构实现与设计文档高度一致，具备了企业级智能体系统的基础能力。系统采用了先进的三层LLM架构，实现了完善的权限控制和神经元通信机制。虽然存在一些配置问题，但整体架构设计合理，具备良好的扩展性和可维护性。