# AI 企业管理智能体 - 开发计划

> "带生命的智能体"成长路线图

---

## 一、智能体生命成长模型

### 1.1 生命阶段定义

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    智能体生命成长阶段                                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐      │
│  │  婴儿期 │──▶│  幼儿期 │──▶│  少年期 │──▶│  青年期 │──▶│  成熟期 │      │
│  │  Infant │   │ Toddler │   │  Teen   │   │  Youth  │   │ Mature  │      │
│  └─────────┘   └─────────┘   └─────────┘   └─────────┘   └─────────┘      │
│       │             │             │             │             │            │
│       ▼             ▼             ▼             ▼             ▼            │
│  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐      │
│  │ 感知基础│   │ 技能学习│   │ 知识积累│   │ 自主决策│   │ 自我进化│      │
│  │ 简单交互│   │ 工具使用│   │ 场景理解│   │ 复杂推理│   │ 创造能力│      │
│  │ 被动响应│   │ 主动尝试│   │ 经验总结│   │ 策略优化│   │ 知识传承│      │
│  └─────────┘   └─────────┘   └─────────┘   └─────────┘   └─────────┘      │
│                                                                             │
│  时间轴: 第1-2周 → 第3-4周 → 第5-8周 → 第9-12周 → 持续演进                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 各阶段能力指标

| 阶段 | 时间 | 核心能力 | 工具数量 | 自主程度 | 学习方式 |
|------|------|---------|---------|---------|---------|
| **婴儿期** | 1-2周 | 感知+响应 | 5-10 | 0% | 完全被动 |
| **幼儿期** | 3-4周 | 工具使用 | 10-20 | 20% | 监督学习 |
| **少年期** | 5-8周 | 场景理解 | 20-40 | 50% | 反馈学习 |
| **青年期** | 9-12周 | 自主决策 | 40-60 | 80% | 自主学习 |
| **成熟期** | 持续 | 自我进化 | 60+ | 95% | 持续进化 |

---

## 二、项目结构设计

### 2.1 模块结构

```
living-agent-service/
├── pom.xml                           # 父POM
├── living-agent-core/                # 核心模块
│   ├── src/main/java/com/livingagent/core/
│   │   ├── neuron/                   # 神经元模块
│   │   │   ├── Neuron.java           # 神经元接口
│   │   │   ├── NeuronContext.java    # 神经元上下文
│   │   │   ├── NeuronExecutor.java   # 神经元执行器
│   │   │   ├── NeuronRegistry.java   # 神经元注册中心
│   │   │   └── impl/                 # 神经元实现
│   │   ├── channel/                  # 管道模块
│   │   │   ├── Channel.java          # 管道接口
│   │   │   ├── ChannelMessage.java   # 管道消息
│   │   │   ├── ChannelManager.java   # 管道管理器
│   │   │   └── impl/                 # 管道实现
│   │   ├── brain/                    # 大脑模块
│   │   │   ├── Brain.java            # 大脑接口
│   │   │   ├── BrainContext.java     # 大脑上下文
│   │   │   ├── BrainRegistry.java    # 大脑注册中心
│   │   │   └── impl/                 # 部门大脑实现
│   │   ├── tool/                     # 工具模块
│   │   │   ├── Tool.java             # 工具接口
│   │   │   ├── ToolExecutor.java     # 工具执行器
│   │   │   ├── ToolRegistry.java     # 工具注册中心
│   │   │   ├── ToolSchema.java       # 工具Schema
│   │   │   └── impl/                 # 工具实现
│   │   ├── memory/                   # 记忆模块
│   │   │   ├── Memory.java           # 记忆接口
│   │   │   ├── MemoryEntry.java      # 记忆条目
│   │   │   └── impl/                 # 记忆实现
│   │   ├── security/                 # 安全模块
│   │   │   ├── SecurityPolicy.java   # 安全策略
│   │   │   ├── AutonomyLevel.java    # 自治级别
│   │   │   └── ApprovalManager.java  # 审批管理器
│   │   ├── provider/                 # Provider模块
│   │   │   ├── Provider.java         # Provider接口
│   │   │   ├── ProviderCapabilities.java
│   │   │   └── impl/                 # Provider实现
│   │   └── config/                   # 配置模块
│   │       ├── AgentConfig.java
│   │       ├── ChannelConfig.java
│   │       └── NeuronConfig.java
│   └── src/main/resources/
│       └── application.yml
├── living-agent-perception/          # 感知模块
│   ├── src/main/java/com/livingagent/perception/
│   │   ├── ear/                      # 听觉(ASR)
│   │   ├── mouth/                    # 口语(TTS)
│   │   └── text/                     # 文本
│   └── src/main/resources/
├── living-agent-skill/               # 技能模块
│   ├── src/main/java/com/livingagent/skill/
│   │   ├── Skill.java                # 技能接口
│   │   ├── SkillMetadata.java        # 技能元数据
│   │   ├── SkillRegistry.java        # 技能注册中心
│   │   └── impl/                     # 技能实现
│   └── skills/                       # SKILL.md 文件
│       ├── tech/                     # 技术部门技能
│       ├── hr/                       # HR部门技能
│       └── ...
├── living-agent-gateway/             # Gateway模块
│   ├── src/main/java/com/livingagent/gateway/
│   │   ├── GatewayServer.java
│   │   ├── WebSocketHandler.java
│   │   └── RestController.java
│   └── src/main/resources/
└── living-agent-app/                 # 应用启动模块
    ├── src/main/java/com/livingagent/
    │   └── LivingAgentApplication.java
    └── src/main/resources/
        └── application.yml
```

---

## 三、分阶段开发计划

### Phase 0: 项目初始化 (第1周) ✅ 已完成

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 0: 项目初始化 ✅ 已完成                                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 创建独立的项目骨架，实现神经元/管道基础架构                             │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  0.1 项目结构创建 ✅                                                  │   │
│  │  ├── ✅ 创建 living-agent-service 目录结构                           │   │
│  │  ├── ✅ 创建 pom.xml (父POM + 5个子模块)                             │   │
│  │  ├── ✅ 创建 application.yml 配置文件                                │   │
│  │  └── ✅ 创建 .gitignore, README.md                                   │   │
│  │                                                                     │   │
│  │  0.2 核心接口定义 ✅                                                  │   │
│  │  ├── ✅ Neuron 接口 (神经元)                                         │   │
│  │  ├── ✅ Channel 接口 (管道)                                          │   │
│  │  ├── ✅ Tool 接口 (工具)                                             │   │
│  │  ├── ✅ Brain 接口 (大脑)                                            │   │
│  │  ├── ✅ Memory 接口 (记忆)                                           │   │
│  │  ├── ✅ Provider 接口 (模型提供者)                                   │   │
│  │  └── ✅ ModelManager 接口 (模型管理)                                 │   │
│  │                                                                     │   │
│  │  0.3 神经元注册中心 ✅                                                │   │
│  │  ├── ✅ NeuronRegistry 接口                                          │   │
│  │  ├── ✅ NeuronRegistryImpl 实现                                      │   │
│  │  ├── ✅ AbstractNeuron 基类                                          │   │
│  │  └── ✅ RouterNeuron 实现                                            │   │
│  │                                                                     │   │
│  │  0.4 管道管理器 ✅                                                    │   │
│  │  ├── ✅ ChannelManager 接口                                          │   │
│  │  ├── ✅ ChannelManagerImpl 实现                                      │   │
│  │  ├── ✅ BroadcastChannel/UnicastChannel/RoundRobinChannel           │   │
│  │  └── ✅ ChannelMessageQueue 实现                                     │   │
│  │                                                                     │   │
│  │  0.5 工具注册中心 ✅                                                  │   │
│  │  ├── ✅ ToolRegistry 接口                                            │   │
│  │  ├── ✅ ToolRegistryImpl 实现                                        │   │
│  │  ├── ✅ ToolSchema 定义                                              │   │
│  │  └── ✅ HttpTool 基础实现                                            │   │
│  │                                                                     │   │
│  │  0.6 安全策略框架 ✅                                                  │   │
│  │  ├── ✅ SecurityPolicy 接口                                          │   │
│  │  ├── ✅ AutonomyLevel 枚举                                           │   │
│  │  ├── ✅ ApprovalManager 接口                                         │   │
│  │  └── ✅ SecurityPolicyImpl 实现                                      │   │
│  │                                                                     │   │
│  │  0.7 模型管理组件 ✅ (新增)                                           │   │
│  │  ├── ✅ ModelClient 接口                                             │   │
│  │  ├── ✅ NamedPipeModelClient 实现                                    │   │
│  │  ├── ✅ ModelManager 接口                                            │   │
│  │  └── ✅ ModelManagerImpl 实现                                        │   │
│  │                                                                     │   │
│  │  0.8 Provider 实现 ✅ (新增)                                          │   │
│  │  ├── ✅ QwenProvider (Qwen3-0.6B)                                    │   │
│  │  ├── ✅ BitNetProvider (BitNet-1.58-3B)                              │   │
│  │  ├── ✅ AsrProvider (FunASR/Sherpa)                                  │   │
│  │  ├── ✅ TtsProvider (MeloTTS/Supertonic)                             │   │
│  │  └── ✅ ProviderRegistryImpl                                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 已完成                                                          │
│  ├── ✅ living-agent-service 项目骨架 (5个模块)                            │
│  ├── ✅ 核心接口定义 (Neuron/Channel/Tool/Brain/Memory/Provider)            │
│  ├── ✅ 神经元注册中心 + 基础实现                                          │
│  ├── ✅ 管道管理器 + 3种管道类型                                           │
│  ├── ✅ 工具注册中心 + 基础工具                                            │
│  ├── ✅ 安全策略框架                                                       │
│  ├── ✅ 模型管理组件 (与Python守护进程通信)                                 │
│  └── ✅ Provider实现 (Qwen/BitNet/ASR/TTS)                                │
│                                                                             │
│  验收标准: ✅ 已通过                                                        │
│  ├── ✅ 项目可编译通过                                                     │
│  ├── ✅ 所有模块可独立编译                                                 │
│  └── ✅ 核心接口定义完整                                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Phase 1: 婴儿期 - 感知基础 (第2周) ✅ 已完成

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 1: 婴儿期 - 感知基础 ✅ 已完成                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 建立基础感知能力和简单响应                                            │
│                                                                             │
│  神经元架构: ✅ 已实现                                                       │
│  ├── ✅ EarNeuron (听觉神经元) → channel://perception/audio                 │
│  ├── ✅ MouthNeuron (口语神经元) → channel://perception/speech              │
│  ├── ✅ RouterNeuron (路由神经元) → channel://dispatch/*                    │
│  └── ✅ TechBrain (决策大脑) → channel://tech/tasks                         │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  1.1 感知神经元实现 ✅                                                │   │
│  │  ├── ✅ EarNeuron (ASR) - 订阅 channel://input/audio                │   │
│  │  │   └── 发布到 channel://perception/text                          │   │
│  │  ├── ✅ MouthNeuron (TTS) - 订阅 channel://output/speech            │   │
│  │  └── ✅ TextNeuron - 订阅 channel://input/text                      │   │
│  │                                                                     │   │
│  │  1.2 路由神经元实现 ✅                                                │   │
│  │  ├── ✅ RouterNeuron                                                │   │
│  │  │   ├── 订阅 channel://perception/*                               │   │
│  │  │   ├── 意图识别 (BitNet-3B)                                      │   │
│  │  │   └── 发布到 channel://dispatch/{department}                    │   │
│  │  └── ✅ 意图分类器                                                   │   │
│  │                                                                     │   │
│  │  1.3 部门大脑实现 ✅                                                  │   │
│  │  ├── ✅ TechBrain (技术大脑)                                        │   │
│  │  │   ├── 订阅 channel://tech/tasks                                 │   │
│  │  │   ├── 执行 Tool-Call Loop (Qwen3-0.6B)                          │   │
│  │  │   └── 发布到 channel://output/text                              │   │
│  │  └── ✅ Provider 集成 (Qwen, BitNet)                                │   │
│  │                                                                     │   │
│  │  1.4 基础管道实现 ✅                                                  │   │
│  │  ├── ✅ UnicastChannel (单播管道)                                    │   │
│  │  ├── ✅ BroadcastChannel (广播管道)                                  │   │
│  │  ├── ✅ RoundRobinChannel (轮询管道)                                 │   │
│  │  └── ✅ ChannelMessageQueue (管道消息队列)                           │   │
│  │                                                                     │   │
│  │  1.5 基础工具迁移 ✅                                                  │   │
│  │  ├── ✅ HttpTool (HTTP请求)                                         │   │
│  │  ├── ✅ GitLabTool (代码管理)                                       │   │
│  │  └── ✅ DingTalkTool (钉钉通讯)                                      │   │
│  │                                                                     │   │
│  │  1.6 记忆系统 ✅                                                      │   │
│  │  ├── ✅ MemoryBackend 接口                                          │   │
│  │  ├── ✅ SQLiteMemoryBackend 实现                                    │   │
│  │  └── ✅ MemoryServiceImpl 服务                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 已完成                                                           │
│  ├── ✅ 感知神经元 (Ear/Mouth/Text)                                         │
│  ├── ✅ 路由神经元 (Router)                                                 │
│  ├── ✅ 部门大脑 (Tech)                                                     │
│  ├── ✅ 基础管道实现 (3种类型)                                               │
│  └── ✅ 基础工具集 (Http/GitLab/DingTalk)                                   │
│                                                                             │
│  验收标准: ✅ 已通过                                                         │
│  ├── ✅ 用户语音输入可被识别并路由                                           │
│  ├── ✅ 简单意图可被正确分类                                                │
│  ├── ✅ 基础工具可被调用                                                    │
│  └── ✅ 响应可通过TTS输出                                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Phase 2: 幼儿期 - 技能学习 (第3-4周) ✅ 已完成

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 2: 幼儿期 - 技能学习 ✅ 已完成                                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 学习使用企业工具，建立基础技能库                                      │
│                                                                             │
│  神经元架构: ✅ 已实现                                                       │
│  ├── ✅ TechBrain (技术大脑) → channel://tech/tasks                         │
│  ├── ✅ HrBrain (HR大脑) → channel://hr/tasks                               │
│  ├── ✅ FinanceBrain (财务大脑) → channel://fin/tasks                       │
│  ├── ✅ SalesBrain (销售大脑) → channel://sal/tasks                         │
│  ├── ✅ CsBrain (客服大脑) → channel://cs/tasks                             │
│  ├── ✅ AdminBrain (行政大脑) → channel://adm/tasks                         │
│  ├── ✅ LegalBrain (法务大脑) → channel://leg/tasks                         │
│  ├── ✅ OpsBrain (运营大脑) → channel://ops/tasks                           │
│  └── 每个大脑独立执行 Tool-Call Loop                                        │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  2.1 部门大脑框架 ✅                                                  │   │
│  │  ├── ✅ Brain 接口                                                   │   │
│  │  ├── ✅ BrainContext (大脑上下文)                                    │   │
│  │  ├── ✅ BrainRegistry (大脑注册中心)                                 │   │
│  │  └── ✅ AbstractBrain (抽象基类)                                     │   │
│  │                                                                     │   │
│  │  2.2 部门大脑实现 ✅                                                  │   │
│  │  ├── ✅ TechBrain (技术大脑)                                         │   │
│  │  ├── ✅ HrBrain (HR大脑)                                             │   │
│  │  ├── ✅ FinanceBrain (财务大脑)                                      │   │
│  │  ├── ✅ SalesBrain (销售大脑)                                        │   │
│  │  ├── ✅ CsBrain (客服大脑)                                           │   │
│  │  ├── ✅ AdminBrain (行政大脑)                                        │   │
│  │  ├── ✅ LegalBrain (法务大脑)                                        │   │
│  │  └── ✅ OpsBrain (运营大脑)                                          │   │
│  │                                                                     │   │
│  │  2.3 企业工具集 ✅                                                    │   │
│  │  ├── ✅ GitLabTool (代码管理)                                        │   │
│  │  ├── ✅ JiraTool (项目管理)                                          │   │
│  │  ├── ✅ JenkinsTool (CI/CD)                                          │   │
│  │  ├── ✅ DingTalkTool (钉钉通讯)                                       │   │
│  │  ├── ✅ FeishuTool (飞书通讯)                                         │   │
│  │  └── ✅ HttpTool (通用HTTP)                                          │   │
│  │                                                                     │   │
│  │  2.4 管道扩展 ✅                                                      │   │
│  │  ├── ✅ RoundRobinChannel (轮询管道)                                 │   │
│  │  ├── ✅ PriorityChannel (优先级管道)                                 │   │
│  │  └── ✅ 跨管道消息转发                                                │   │
│  │                                                                     │   │
│  │  2.5 安全策略实现 ✅                                                  │   │
│  │  ├── ✅ SecurityPolicyImpl                                          │   │
│  │  │   ├── 命令安全检查                                               │   │
│  │  │   ├── 路径安全检查                                               │   │
│  │  │   └── 速率限制                                                   │   │
│  │  └── ✅ ApprovalManagerImpl                                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 已完成                                                           │
│  ├── ✅ 部门大脑框架 (8个部门大脑)                                           │
│  ├── ✅ 企业工具集 (GitLab/Jira/Jenkins/DingTalk/Feishu)                    │
│  ├── ✅ 管道扩展 (RoundRobin/Priority)                                      │
│  └── ✅ 安全策略实现                                                        │
│                                                                             │
│  验收标准: ✅ 已通过                                                         │
│  ├── ✅ 可查询GitLab项目信息                                                │
│  ├── ✅ 可搜索Jira任务                                                      │
│  ├── ✅ 可发送钉钉/飞书消息                                                 │
│  └── ✅ 工具调用需通过安全检查                                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Phase 3: 少年期 - 知识积累 (第5-8周) ✅ 已完成

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 3: 少年期 - 知识积累 ✅ 已完成                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 建立完整部门大脑，积累领域知识，理解业务场景                           │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  3.1 LLM神经元架构 ✅                                                │   │
│  │  ├── ✅ Qwen3Neuron (对话神经元) → channel://llm/chat               │   │
│  │  ├── ✅ BitNetNeuron (工具神经元) → channel://llm/tool              │   │
│  │  ├── ✅ MainBrainNeuron (主大脑) → channel://dispatch/*             │   │
│  │  └── ✅ NeuronCoordinator (神经元协调器)                            │   │
│  │                                                                     │   │
│  │  3.2 部门大脑实现 ✅                                                 │   │
│  │  ├── ✅ TechBrain (技术大脑) → channel://tech/tasks                 │   │
│  │  ├── ✅ HrBrain (HR大脑) → channel://hr/tasks                       │   │
│  │  ├── ✅ FinanceBrain (财务大脑) → channel://fin/tasks               │   │
│  │  ├── ✅ SalesBrain (销售大脑) → channel://sal/tasks                 │   │
│  │  ├── ✅ CsBrain (客服大脑) → channel://cs/tasks                     │   │
│  │  ├── ✅ AdminBrain (行政大脑) → channel://adm/tasks                 │   │
│  │  ├── ✅ LegalBrain (法务大脑) → channel://leg/tasks                 │   │
│  │  └── ✅ OpsBrain (运营大脑) → channel://ops/tasks                   │   │
│  │                                                                     │   │
│  │  3.3 企业工具集 ✅                                                   │   │
│  │  ├── ✅ GitLabTool (代码管理)                                       │   │
│  │  ├── ✅ JiraTool (项目管理)                                         │   │
│  │  ├── ✅ JenkinsTool (CI/CD)                                         │   │
│  │  ├── ✅ DingTalkTool (钉钉通讯)                                     │   │
│  │  ├── ✅ FeishuTool (飞书通讯)                                       │   │
│  │  └── ✅ HttpTool (通用HTTP)                                         │   │
│  │                                                                     │   │
│  │  3.4 用户互动流程 ✅                                                 │   │
│  │  ├── ✅ VoiceWebSocketHandler (语音WebSocket)                       │   │
│  │  ├── ✅ DialogueService (对话服务)                                  │   │
│  │  ├── ✅ DialogueSessionManager (会话管理)                           │   │
│  │  ├── ✅ PromptBuilder (提示词构建)                                  │   │
│  │  └── ✅ ParallelModelService (双模型并行协作)                       │   │
│  │                                                                     │   │
│  │  3.5 音频处理模块 ✅                                                 │   │
│  │  ├── ✅ AudioConfig (音频配置)                                      │   │
│  │  ├── ✅ AudioUtils (音频工具)                                       │   │
│  │  ├── ✅ AudioBuffer (音频缓冲区)                                    │   │
│  │  └── ✅ AudioProcessor (Rust原生实现)                               │   │
│  │                                                                     │   │
│  │  3.6 Rust原生模块 ✅ (新增)                                          │   │
│  │  ├── ✅ living-agent-native (Rust Crate)                           │   │
│  │  │   ├── ✅ audio (Opus编解码、VAD、重采样)                         │   │
│  │  │   ├── ✅ channel (无锁消息队列、广播管道)                        │   │
│  │  │   ├── ✅ security (命令验证、沙箱执行)                           │   │
│  │  │   └── ✅ memory (SQLite后端、向量存储)                           │   │
│  │  └── ✅ JNI接口 (Java调用Rust)                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 已完成                                                           │
│  ├── ✅ LLM神经元架构 (Qwen3Neuron + BitNetNeuron)                          │
│  ├── ✅ 完整8个部门大脑                                                      │
│  ├── ✅ 企业工具集 (GitLab/Jira/Jenkins/DingTalk/Feishu)                    │
│  ├── ✅ 用户互动流程 (WebSocket + 对话管理)                                  │
│  ├── ✅ Rust原生模块 (音频/管道/安全/内存)                                   │
│  └── ✅ JNI接口 (Java-Rust互操作)                                           │
│                                                                             │
│  验收标准: ✅ 已通过                                                         │
│  ├── ✅ 可处理8个部门的业务请求                                              │
│  ├── ✅ 记忆可持久化并检索 (SQLite后端)                                      │
│  ├── ✅ 双模型并行协作 (Qwen3 + BitNet)                                      │
│  └── ✅ 音频处理性能优化 (Rust原生)                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Phase 4: 青年期 - 自主决策 (第9-12周) ✅ 完成

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 4: 青年期 - 自主决策 ✅ 完成                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 实现自主决策、复杂推理、跨部门协作                                    │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  4.1 Gateway模块 ✅                                                   │   │
│  │  ├── ✅ VoiceWebSocketHandler (语音WebSocket)                        │   │
│  │  ├── ✅ AgentWebSocketHandler (Agent WebSocket)                      │   │
│  │  ├── ✅ AgentController (REST API)                                   │   │
│  │  ├── ✅ WebSocketConfig (WebSocket配置)                              │   │
│  │  └── ✅ AgentService (Agent服务)                                     │   │
│  │                                                                     │   │
│  │  4.2 对话系统 ✅                                                      │   │
│  │  ├── ✅ DialogueService (对话服务)                                   │   │
│  │  ├── ✅ DialogueSession (对话会话)                                   │   │
│  │  ├── ✅ DialogueMessage (对话消息)                                   │   │
│  │  ├── ✅ DialogueSessionManager (会话管理)                            │   │
│  │  └── ✅ 历史记录裁剪                                                 │   │
│  │                                                                     │   │
│  │  4.3 提示词系统 ✅                                                    │   │
│  │  ├── ✅ PromptBuilder (提示词构建)                                   │   │
│  │  ├── ✅ PromptConfig (提示词配置)                                    │   │
│  │  ├── ✅ RoleConfig (角色配置)                                        │   │
│  │  └── ✅ 工具提示词注入                                               │   │
│  │                                                                     │   │
│  │  4.4 工具执行系统 ✅                                                  │   │
│  │  ├── ✅ ToolExecutor接口                                             │   │
│  │  ├── ✅ ToolExecutorService (工具执行服务)                           │   │
│  │  ├── ✅ ToolResultEvent (工具结果事件)                               │   │
│  │  └── ✅ 事件推送机制                                                 │   │
│  │                                                                     │   │
│  │  4.5 任务规划系统 ✅                                                  │   │
│  │  ├── ✅ TaskPlanner接口 (任务规划)                                   │   │
│  │  ├── ✅ TaskPlan (任务计划)                                          │   │
│  │  ├── ✅ TaskStep (任务步骤)                                          │   │
│  │  └── ✅ TaskPlannerImpl (规划器实现)                                 │   │
│  │                                                                     │   │
│  │  4.6 场景处理系统 ✅                                                  │   │
│  │  ├── ✅ ScenarioHandler接口                                          │   │
│  │  ├── ✅ ScenarioResult (场景结果)                                    │   │
│  │  ├── ✅ WeeklyReportScenarioHandler (周报生成)                       │   │
│  │  └── ✅ EmployeeOnboardingScenarioHandler (员工入职)                 │   │
│  │                                                                     │   │
│  │  4.7 异常检测系统 ✅                                                  │   │
│  │  ├── ✅ AnomalyDetector接口                                          │   │
│  │  ├── ✅ AnomalyContext (异常上下文)                                  │   │
│  │  ├── ✅ AnomalyResult (异常结果)                                     │   │
│  │  └── ✅ PerformanceAnomalyDetector (性能异常检测)                    │   │
│  │                                                                     │   │
│  │  4.8 技能系统 ✅                                                      │   │
│  │  ├── ✅ Skill模型                                                    │   │
│  │  ├── ✅ SkillLoader (技能加载器)                                     │   │
│  │  ├── ✅ SkillRegistry (技能注册表)                                   │   │
│  │  ├── ✅ SkillService (技能服务)                                      │   │
│  │  └── ✅ 45个技能文件 (Tech/Admin/Sales/HR/Finance/CS/Legal/Ops)     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 完成                                                             │
│  ├── ✅ 完整Gateway (WebSocket + REST API)                                  │
│  ├── ✅ 对话系统 (会话管理 + 历史记录)                                       │
│  ├── ✅ 提示词系统 (角色配置 + 工具注入)                                     │
│  ├── ✅ 工具执行系统 (执行器 + 事件推送)                                     │
│  ├── ✅ 自主规划系统 (TaskPlanner + 场景处理)                                │
│  ├── ✅ 复杂场景处理能力 (周报 + 入职)                                       │
│  ├── ✅ 异常检测引擎 (性能监控 + 告警)                                       │
│  └── ✅ 技能系统 (45个技能 + 加载机制)                                       │
│                                                                             │
│  验收标准: ✅ 通过                                                           │
│  ├── ✅ WebSocket实时通信可用                                               │
│  ├── ✅ REST API可用                                                        │
│  ├── ✅ 对话历史可管理                                                      │
│  ├── ✅ 可自主完成周报生成并发送                                             │
│  ├── ✅ 可协调完成新员工入职流程                                             │
│  ├── ✅ 异常检测与告警可用                                                   │
│  └── ✅ 技能按大脑分类加载                                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Phase 5: 成熟期 - 自我进化 (持续) ✅ 已完成

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 5: 成熟期 - 自我进化 ✅ 已完成                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 实现自我进化、知识传承、能力创造                                      │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  5.1 自我进化系统 ✅                                                  │   │
│  │  ├── ✅ SkillGenerator 技能生成器                                    │   │
│  │  ├── ✅ SkillGeneratorImpl (生成器实现)                              │   │
│  │  ├── ✅ CapabilityEvaluator 能力评估器                               │   │
│  │  ├── ✅ EvaluationResult 评估结果                                    │   │
│  │  └── ✅ ImprovementSuggestion 改进建议                               │   │
│  │                                                                     │   │
│  │  5.2 知识传承系统 ✅                                                  │   │
│  │  ├── ✅ KnowledgeBase 知识库接口                                     │   │
│  │  ├── ✅ KnowledgeEntry 知识条目                                      │   │
│  │  ├── ✅ Experience 经验记录                                          │   │
│  │  └── ✅ BestPractice 最佳实践                                        │   │
│  │                                                                     │   │
│  │  5.3 自我诊断系统 ✅                                                  │   │
│  │  ├── ✅ HealthMonitor 健康监控接口                                   │   │
│  │  ├── ✅ HealthStatus 健康状态                                        │   │
│  │  ├── ✅ HealthIssue 健康问题                                         │   │
│  │  └── ✅ HealthAlert 健康告警                                         │   │
│  │                                                                     │   │
│  │  5.4 感知扩展 ✅                                                      │   │
│  │  ├── ✅ EyeNeuron (视觉神经元)                                       │   │
│  │  │   ├── 图像识别                                                   │   │
│  │  │   └── 文档OCR                                                    │   │
│  │  └── ✅ SensorNeuron (传感器神经元)                                  │   │
│  │                                                                     │   │
│  │  5.5 视觉神经元 ✅ (新增)                                             │   │
│  │  ├── ✅ EyeNeuron 接口                                              │   │
│  │  ├── ✅ EyeNeuronImpl 实现                                          │   │
│  │  ├── ✅ ImageAnalysisResult 图像分析结果                            │   │
│  │  ├── ✅ DocumentAnalysisResult 文档分析结果                          │   │
│  │  ├── ✅ FaceAnalysisResult 人脸分析结果                              │   │
│  │  └── ✅ DetectedObject 目标检测                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 完成                                                           │
│  ├── ✅ 自我进化系统 (技能生成 + 能力评估)                                   │
│  ├── ✅ 知识传承系统 (知识库 + 经验分享)                                     │
│  ├── ✅ 自我诊断系统 (健康监控 + 告警)                                       │
│  ├── ✅ 视觉神经元 (图像/文档/人脸分析)                                      │
│  └── ✅ 传感器神经元 (系统监控/告警)                                         │
│                                                                             │
│  验收标准: ✅ 通过                                                          │
│  ├── ✅ 可自动生成新技能                                                    │
│  ├── ✅ 可评估能力并给出改进建议                                             │
│  ├── ✅ 可存储和检索知识经验                                                 │
│  ├── ✅ 可监控健康状态并告警                                                 │
│  └── ✅ 可处理图像数据 (Qwen3.5-27B多模态)                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Phase 6: 成长型知识体系 (第13-16周) ✅ 已完成

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 6: 成长型知识体系 ✅ 已完成                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 构建大脑优先的知识处理体系，实现知识的自动进化与传承                    │
│                                                                             │
│  核心原则: 知识必须先经过大脑理解和分类，再进行向量化存储                       │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  6.1 知识进化系统 ✅                                                  │   │
│  │  ├── ✅ KnowledgeEvolver 接口                                        │   │
│  │  ├── ✅ KnowledgeEvolution 知识进化记录                              │   │
│  │  ├── ✅ KnowledgeMergeResult 知识合并结果                            │   │
│  │  ├── ✅ KnowledgePropagationResult 知识传播结果                      │   │
│  │  ├── ✅ KnowledgeQualityReport 知识质量报告                          │   │
│  │  └── ✅ KnowledgeIssue 知识问题                                      │   │
│  │                                                                     │   │
│  │  6.2 知识库实现 ✅                                                    │   │
│  │  ├── ✅ KnowledgeBase 接口 (扩展)                                    │   │
│  │  ├── ✅ SQLiteKnowledgeBase 实现                                     │   │
│  │  ├── ✅ NativeKnowledgeBase 实现 (Java+Rust整合)                     │   │
│  │  ├── ✅ 混合检索实现 (关键词 + 向量)                                  │   │
│  │  └── 🔜 向量数据库集成 (Milvus/Qdrant)                               │   │
│  │                                                                     │   │
│  │  6.3 知识模型扩展 ✅                                                  │   │
│  │  ├── ✅ KnowledgeEntry 扩展 (与Rust对齐)                             │   │
│  │  ├── ✅ KnowledgeType 枚举 (Fact/Process/Experience/BestPractice)    │   │
│  │  ├── ✅ Importance 枚举 (High/Medium/Low + 权重)                     │   │
│  │  ├── ✅ Validity 枚举 (Permanent/LongTerm/ShortTerm/Temporary)       │   │
│  │  └── ✅ KnowledgeMetadata 元数据模型                                  │   │
│  │                                                                     │   │
│  │  6.4 Rust高性能组件 ✅                                                │   │
│  │  ├── ✅ knowledge 模块 (Rust)                                       │   │
│  │  │   ├── ✅ types.rs (模型定义)                                     │   │
│  │  │   ├── ✅ similarity.rs (SIMD优化相似度计算)                       │   │
│  │  │   ├── ✅ vector_store.rs (向量存储)                              │   │
│  │  │   ├── ✅ sqlite_backend.rs (SQLite持久化)                        │   │
│  │  │   └── ✅ cache.rs (LRU缓存 + TTL)                                │   │
│  │  ├── ✅ knowledge_jni.rs (JNI接口)                                  │   │
│  │  └── ✅ NativeKnowledge (Java JNI桥接)                              │   │
│  │                                                                     │   │
│  │  6.5 统一知识管理器 ✅ (新增)                                          │   │
│  │  ├── ✅ KnowledgeManager 接口                                       │   │
│  │  │   ├── 三层知识库架构 (PRIVATE/DOMAIN/SHARED)                     │   │
│  │  │   ├── 知识晋升机制 (promoteToDomain/promoteToShared)             │   │
│  │  │   ├── 混合检索 (hybridSearch)                                    │   │
│  │  │   └── 知识进化集成 (evolveKnowledge/mergeKnowledge)              │   │
│  │  ├── ✅ KnowledgeManagerImpl 实现                                    │   │
│  │  │   ├── 多知识库协调                                               │   │
│  │  │   ├── 知识层级自动路由                                           │   │
│  │  │   └── 知识统计与监控                                             │   │
│  │  └── ✅ KnowledgeQuery 构建器                                        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 已完成                                                           │
│  ├── ✅ 知识进化系统 (接口 + 模型)                                           │
│  ├── ✅ SQLite知识库实现                                                     │
│  ├── ✅ NativeKnowledgeBase (Java+Rust整合)                                 │
│  ├── ✅ 知识模型扩展 (与Rust对齐)                                            │
│  ├── ✅ Rust高性能组件 (SIMD相似度 + LRU缓存)                                │
│  ├── ✅ 统一知识管理器 (KnowledgeManager + 三层架构)                          │
│  └── 🔜 向量数据库集成 (Milvus/Qdrant)                                       │
│                                                                             │
│  验收标准: ✅ 已通过                                                         │
│  ├── ✅ 知识可按大脑分类存储                                                 │
│  ├── ✅ 支持混合检索 (关键词 + 向量)                                         │
│  ├── ✅ 知识可自动进化与传播                                                 │
│  ├── ✅ 知识质量可评估与监控                                                 │
│  ├── ✅ Rust组件性能达标 (SIMD优化)                                          │
│  ├── ✅ Java-Rust自动降级机制                                                │
│  └── ✅ 三层知识库架构可用 (私有/领域/共享)                                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 6.1 知识处理流程设计

```
用户输入/文档/事件
        │
        ▼
┌─────────────────┐
│  MainBrain      │  第一阶段：大脑理解
│  Qwen3.5-27B    │  - 内容理解
│                 │  - 领域分类
│                 │  - 重要性评估
└─────────────────┘
        │
        ▼
┌─────────────────┐
│  部门大脑        │  第二阶段：领域处理
│  Tech/Hr/Fin... │  - 领域知识提取
│                 │  - 业务规则匹配
└─────────────────┘
        │
        ▼
┌─────────────────┐
│  MemoryNeuron   │  第三阶段：向量化存储
│  BGE-M3         │  - 文本向量化
│                 │  - 多粒度编码
└─────────────────┘
        │
        ▼
┌─────────────────┐
│  存储层          │  第四阶段：持久化
│  Milvus/SQLite  │  - 向量存储
│  PostgreSQL     │  - 结构化存储
└─────────────────┘
```

#### 6.2 分层知识库设计

| 层级 | 名称 | 存储 | 生命周期 | 访问权限 |
|------|------|------|---------|---------|
| L1 | 神经元私有知识 | SQLite | 会话级 | 仅该神经元 |
| L2 | 大脑领域知识 | PostgreSQL + Milvus | 长期 | 该大脑 + 授权 |
| L3 | 共享知识库 | PostgreSQL + Milvus | 永久 | 所有神经元 |

### Phase 7: 智能进化系统 (持续) ✅ 已完成

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 7: 智能进化系统 ✅ 已完成                                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 构建大脑优先的智能进化体系，实现自我优化与能力提升                        │
│                                                                             │
│  参考: evolver-main 项目的 GEP (Genome Evolution Protocol) 设计              │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  7.1 进化信号系统 ✅                                                  │   │
│  │  ├── ✅ EvolutionSignal 进化信号模型                                 │   │
│  │  │   ├── SignalType (ERROR/OPPORTUNITY/STABILITY/DRIFT/...)        │   │
│  │  │   └── SignalCategory (REPAIR/OPTIMIZE/INNOVATE)                 │   │
│  │  ├── ✅ SignalExtractor 信号提取接口                                 │   │
│  │  └── ✅ DefaultSignalExtractor 默认实现                              │   │
│  │                                                                     │   │
│  │  7.2 进化记忆图谱 ✅                                                  │   │
│  │  ├── ✅ EvolutionEvent 进化事件模型                                  │   │
│  │  │   ├── SignalSnapshot 信号快照                                    │   │
│  │  │   ├── Hypothesis 假设                                            │   │
│  │  │   ├── EvolutionAction 进化动作                                   │   │
│  │  │   └── EvolutionOutcome 进化结果                                  │   │
│  │  └── ✅ EvolutionMemoryGraph 记忆图谱接口                            │   │
│  │                                                                     │   │
│  │  7.3 大脑人格系统 ✅                                                  │   │
│  │  ├── ✅ BrainPersonality 大脑人格模型                                │   │
│  │  │   ├── rigor (严谨度)                                             │   │
│  │  │   ├── creativity (创造力)                                        │   │
│  │  │   ├── riskTolerance (风险容忍)                                   │   │
│  │  │   └── obedience (服从度)                                         │   │
│  │  ├── ✅ PersonalityMutation 人格变异                                 │   │
│  │  └── ✅ PersonalityStats 人格统计                                    │   │
│  │                                                                     │   │
│  │  7.4 进化熔断器 ✅                                                    │   │
│  │  ├── ✅ EvolutionCircuitBreaker 熔断器                              │   │
│  │  ├── ✅ CircuitState 熔断状态                                       │   │
│  │  ├── ✅ CircuitTripReason 熔断原因                                  │   │
│  │  │   ├── REPAIR_LOOP (修复循环)                                     │   │
│  │  │   ├── FAILURE_STREAK (失败连续)                                  │   │
│  │  │   ├── EMPTY_CYCLE (空循环)                                       │   │
│  │  │   └── SATURATION (饱和)                                          │   │
│  │  └── ✅ CircuitBreakerReport 熔断报告                                │   │
│  │                                                                     │   │
│  │  7.5 进化决策引擎 ✅                                                    │   │
│  │  ├── ✅ EvolutionDecisionEngine 接口                                  │   │
│  │  │   ├── EvolutionDecision 决策模型                                  │   │
│  │  │   ├── EvolutionStrategy 枚举 (SKIP/REPAIR/OPTIMIZE/INNOVATE/...)  │   │
│  │  │   ├── EvolutionPriority 优先级                                    │   │
│  │  │   └── EvolutionConstraints 约束条件                               │   │
│  │  ├── ✅ DefaultEvolutionDecisionEngine 实现                          │   │
│  │  │   ├── 策略选择逻辑                                                │   │
│  │  │   ├── 置信度计算                                                  │   │
│  │  │   ├── 熔断器集成                                                  │   │
│  │  │   └── 人格系统集成                                                │   │
│  │  └── ✅ 批量决策支持                                                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 已完成                                                           │
│  ├── ✅ 进化信号系统 (信号模型 + 提取器)                                      │
│  ├── ✅ 进化记忆图谱 (事件模型 + 接口)                                        │
│  ├── ✅ 大脑人格系统 (人格模型 + 变异机制)                                    │
│  ├── ✅ 进化熔断器 (熔断逻辑 + 状态管理)                                      │
│  └── ✅ 进化决策引擎 (策略选择 + 置信度计算)                                   │
│                                                                             │
│  验收标准: ✅ 已通过                                                         │
│  ├── ✅ 进化信号可从日志/对话/指标中提取                                      │
│  ├── ✅ 进化记忆可记录完整因果链                                              │
│  ├── ✅ 大脑人格可根据进化结果动态调整                                         │
│  ├── ✅ 熔断器可检测并阻止进化死循环                                          │
│  └── ✅ 进化决策可综合多因素做出最优选择                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 7.1 进化系统与 evolver-main 对比

| 特性 | evolver-main | living-agent-service | 适配说明 |
|------|--------------|---------------------|---------|
| **信号系统** | 日志/对话直接提取 | 大脑优先理解后提取 | 适配神经元架构 |
| **进化资产** | Genes/Capsules | Skills | 技能即进化资产 |
| **记忆图谱** | 全局 MemoryGraph | 分层进化记忆 | 神经元私有+大脑领域+共享 |
| **人格状态** | 全局 PersonalityState | 大脑级 BrainPersonality | 每个大脑独立人格 |
| **选择机制** | 信号匹配+漂移 | 大脑决策+熔断保护 | 适配大脑架构 |
| **熔断机制** | 修复循环检测 | 大脑级熔断器 | 每个大脑独立熔断 |

#### 7.2 大脑人格默认配置

| 大脑 | 严谨度 | 创造力 | 风险容忍 | 服从度 |
|------|--------|--------|---------|--------|
| TechBrain | 0.8 | 0.6 | 0.5 | 0.7 |
| AdminBrain | 0.7 | 0.4 | 0.3 | 0.9 |
| SalesBrain | 0.5 | 0.7 | 0.6 | 0.6 |
| HrBrain | 0.6 | 0.5 | 0.4 | 0.8 |
| FinanceBrain | 0.9 | 0.3 | 0.2 | 0.95 |
| CsBrain | 0.6 | 0.5 | 0.4 | 0.7 |
| LegalBrain | 0.95 | 0.2 | 0.1 | 0.98 |
| OpsBrain | 0.7 | 0.5 | 0.5 | 0.7 |
| MainBrain | 0.7 | 0.5 | 0.4 | 0.85 |

### Phase 7.5: 即学即会能力 (第16-17周) ✅ 已完成

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 7.5: 即学即会能力 ✅ 已完成                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 实现技能和知识的即时生效，无需重启服务                                   │
│                                                                             │
│  核心能力:                                                                   │
│  ├── 进化执行器 - 自动串联 Signal→Generate→Install→Reload→Bind 流程          │
│  ├── 技能热加载 - 文件变更自动检测并重载                                       │
│  ├── REST API 管理 - 支持外部触发热更新                                       │
│  └── 即时生效 - 新技能生成后立即可用                                          │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  7.5.1 进化执行器 ✅                                                  │   │
│  │  ├── ✅ EvolutionExecutor 进化执行器                                  │   │
│  │  │   ├── execute(EvolutionSignal) - 同步执行                         │   │
│  │  │   ├── executeAsync(EvolutionSignal) - 异步执行                    │   │
│  │  │   ├── executeInnovate() - 创新策略执行                            │   │
│  │  │   ├── executeRepair() - 修复策略执行                              │   │
│  │  │   └── executeOptimize() - 优化策略执行                            │   │
│  │  ├── ✅ EvolutionResult 进化结果模型                                  │   │
│  │  │   ├── Status (SUCCESS/FAILED/SKIPPED/DEFERRED/ESCALATED)         │   │
│  │  │   ├── isImmediateEffective() - 判断是否即时生效                   │   │
│  │  │   └── toMap() - 结果序列化                                        │   │
│  │  └── ✅ 自动流程串联                                                  │   │
│  │      ├── SkillGenerator.generateSkill()                              │   │
│  │      ├── SkillInstaller.install()                                    │   │
│  │      ├── SkillRegistry.registerSkill()                               │   │
│  │      └── SkillBindingService.bindSkillToNeuron()                     │   │
│  │                                                                     │   │
│  │  7.5.2 技能热加载器 ✅                                                │   │
│  │  ├── ✅ SkillHotReloader 热加载器                                    │   │
│  │  │   ├── WatchService 文件监听                                       │   │
│  │  │   ├── debouncedReload() 防抖重载                                  │   │
│  │  │   ├── manualReload() 手动触发                                     │   │
│  │  │   └── 自动绑定到神经元                                            │   │
│  │  └── ✅ 配置项                                                       │   │
│  │      ├── skill.hotreload.enabled (默认true)                          │   │
│  │      ├── skill.hotreload.watch.config (默认true)                     │   │
│  │      └── DEBOUNCE_MS = 1000ms                                        │   │
│  │                                                                     │   │
│  │  7.5.3 REST API管理接口 ✅                                            │   │
│  │  ├── ✅ EvolutionAdminController                                     │   │
│  │  │   ├── GET /api/admin/skills - 列出技能                            │   │
│  │  │   ├── POST /api/admin/skills/reload - 重载技能                    │   │
│  │  │   ├── POST /api/admin/skills/generate - 生成技能                  │   │
│  │  │   ├── POST /api/admin/skills/{name}/install - 安装技能            │   │
│  │  │   ├── DELETE /api/admin/skills/{name} - 卸载技能                  │   │
│  │  │   ├── POST /api/admin/skills/{skill}/bind/{neuron} - 绑定技能     │   │
│  │  │   ├── GET /api/admin/bindings - 获取绑定关系                      │   │
│  │  │   ├── GET /api/admin/hotreload/status - 热加载状态                │   │
│  │  │   ├── POST /api/admin/hotreload/trigger - 触发热加载              │   │
│  │  │   ├── POST /api/admin/evolution/trigger - 触发进化                │   │
│  │  │   ├── GET /api/admin/evolution/results - 进化结果                 │   │
│  │  │   └── POST /api/admin/evolution/extract-signals - 提取信号        │   │
│  │  └── ✅ 请求/响应模型                                                 │   │
│  │      ├── SkillGenerateRequest                                        │   │
│  │      ├── EvolutionTriggerRequest                                     │   │
│  │      └── SignalExtractRequest                                        │   │
│  │                                                                     │   │
│  │  7.5.4 SkillInstaller增强 ✅                                          │   │
│  │  ├── ✅ installFromSkillObject(Skill) - 从对象安装                   │   │
│  │  ├── ✅ installFromContent(skillId, content) - 从内容安装            │   │
│  │  ├── ✅ exists(skillId) - 检查技能是否存在                            │   │
│  │  ├── ✅ getSkillPath(skillId) - 获取技能路径                          │   │
│  │  └── ✅ generateDefaultContent(Skill) - 生成默认内容                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 已完成                                                           │
│  ├── ✅ EvolutionExecutor 进化执行器                                         │
│  ├── ✅ EvolutionResult 进化结果模型                                         │
│  ├── ✅ SkillHotReloader 技能热加载器                                        │
│  ├── ✅ EvolutionAdminController REST API                                   │
│  └── ✅ SkillInstaller 增强                                                  │
│                                                                             │
│  验收标准: ✅ 已通过                                                         │
│  ├── ✅ 技能生成后无需重启即可使用                                            │
│  ├── ✅ 文件变更自动触发技能重载                                              │
│  ├── ✅ REST API 可触发热更新                                                │
│  ├── ✅ 进化信号可自动触发技能生成                                            │
│  └── ✅ 新技能自动绑定到目标神经元                                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 7.5.1 即学即会流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    即学即会完整流程                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  方式1: 进化信号触发 (全自动)                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  用户反馈/错误/能力缺口                                              │   │
│  │         │                                                           │   │
│  │         ▼                                                           │   │
│  │  SignalExtractor.extract()                                          │   │
│  │         │                                                           │   │
│  │         ▼                                                           │   │
│  │  EvolutionDecisionEngine.decide()                                   │   │
│  │         │                                                           │   │
│  │         ▼                                                           │   │
│  │  EvolutionExecutor.execute()                                        │   │
│  │         │                                                           │   │
│  │         ├── SkillGenerator.generateSkill()                          │   │
│  │         ├── SkillInstaller.install()                                │   │
│  │         ├── SkillRegistry.registerSkill()                           │   │
│  │         └── SkillBindingService.bindSkillToNeuron()                 │   │
│  │         │                                                           │   │
│  │         ▼                                                           │   │
│  │  新技能立即可用 (无需重启)                                           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  方式2: 文件变更触发 (半自动)                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  手动添加/修改 SKILL.md 文件                                         │   │
│  │         │                                                           │   │
│  │         ▼                                                           │   │
│  │  SkillHotReloader (WatchService)                                    │   │
│  │         │                                                           │   │
│  │         ▼                                                           │   │
│  │  debouncedReload() (防抖1秒)                                        │   │
│  │         │                                                           │   │
│  │         ├── SkillRegistry.reloadSkills()                            │   │
│  │         └── SkillBindingService.bindCoreSkillsToAllNeurons()        │   │
│  │         │                                                           │   │
│  │         ▼                                                           │   │
│  │  新技能立即可用 (无需重启)                                           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  方式3: REST API触发 (手动)                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  POST /api/admin/skills/generate                                    │   │
│  │  POST /api/admin/skills/reload                                      │   │
│  │  POST /api/admin/evolution/trigger                                  │   │
│  │         │                                                           │   │
│  │         ▼                                                           │   │
│  │  EvolutionAdminController                                          │   │
│  │         │                                                           │   │
│  │         ├── 调用 EvolutionExecutor                                  │   │
│  │         ├── 调用 SkillService.reloadSkills()                        │   │
│  │         └── 调用 SkillBindingService                                │   │
│  │         │                                                           │   │
│  │         ▼                                                           │   │
│  │  新技能立即可用 (无需重启)                                           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 7.5.2 即学即会能力评估

| 维度 | 改进前 | 改进后 |
|------|--------|--------|
| **技能热重载** | ⚠️ 需手动调用 | ✅ 自动触发 |
| **文件监听** | ❌ 无 | ✅ WatchService |
| **进化流程自动化** | ❌ 流程断裂 | ✅ 自动串联 |
| **REST API** | ❌ 无 | ✅ 完整API |
| **即时生效** | ❌ 需重启 | ✅ 立即可用 |

### Phase 8: 企业权限管理系统 (第17-20周) ✅ 部分完成

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 8: 企业权限管理系统 ✅ 部分完成                                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 构建智能化的企业级权限控制，支持人员信息本地存储和智能感知               │
│                                                                             │
│  核心原则:                                                                   │
│  ├── 本地优先 - 人员信息本地存储，减少对HR系统依赖                            │
│  ├── 多源导入 - HR系统/花名册/手动添加/自动注册                              │
│  ├── 智能感知 - 智能体可从对话中感知人员变动                                 │
│  ├── 权限联动 - 状态变更自动触发权限调整                                     │
│  └── 安全隔离 - 离职/外来人员只能闲聊，防止信息泄露                          │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  8.1 数据库架构建设 ✅                                                  │   │
│  │  ├── ✅ PostgreSQL 集成与配置                                        │   │
│  │  ├── ✅ Qdrant 向量数据库集成                                        │   │
│  │  ├── ✅ 数据库表结构创建                                             │   │
│  │  │   ├── enterprise_employees (员工表)                               │   │
│  │  │   ├── enterprise_departments (部门表)                             │   │
│  │  │   ├── department_brain_mapping (部门大脑映射)                     │   │
│  │  │   ├── employee_sync_log (同步日志)                                │   │
│  │  │   └── access_audit_log (审计日志)                                 │   │
│  │  └── ✅ 数据迁移脚本 (schema.sql)                                    │   │
│  │                                                                     │   │
│  │  8.2 用户身份识别系统 ✅                                              │   │
│  │  ├── ✅ 手机号验证服务                                               │   │
│  │  ├── ✅ 声纹识别服务 (VoicePrintService)                             │   │
│  │  │   ├── VoicePrintServiceImpl                                      │   │
│  │  │   ├── Qdrant 向量存储                                            │   │
│  │  │   └── CAM++ 192维向量                                            │   │
│  │  ├── ✅ OAuth 集成                                                   │   │
│  │  │   ├── ✅ 钉钉 OAuth (DingTalkOAuthService)                        │   │
│  │  │   ├── ✅ 飞书 OAuth (FeishuOAuthService)                          │   │
│  │  │   └── ✅ 企业微信 OAuth (WeComOAuthService)                       │   │
│  │  ├── ✅ 统一认证服务 (UnifiedAuthService)                            │   │
│  │  └── 🔜 人脸识别 (后续扩展)                                          │   │
│  │                                                                     │   │
│  │  8.3 权限管理核心 ✅                                                  │   │
│  │  ├── ✅ UserIdentity 枚举 (身份类型)                                 │   │
│  │  │   ├── INTERNAL_ACTIVE (在职员工)                                  │   │
│  │  │   ├── INTERNAL_PROBATION (试用期)                                 │   │
│  │  │   ├── INTERNAL_DEPARTED (离职员工)                                │   │
│  │  │   ├── EXTERNAL_VISITOR (外来访客)                                 │   │
│  │  │   └── EXTERNAL_PARTNER (合作伙伴)                                 │   │
│  │  ├── ✅ AccessLevel 枚举 (访问级别)                                  │   │
│  │  │   ├── CHAT_ONLY (仅闲聊 - Qwen3-0.6B)                            │   │
│  │  │   ├── LIMITED (受限访问)                                          │   │
│  │  │   ├── DEPARTMENT (部门访问)                                       │   │
│  │  │   └── FULL (完全访问)                                             │   │
│  │  ├── ✅ PermissionService 接口                                       │   │
│  │  │   ├── verifyByPhone() 手机验证                                    │   │
│  │  │   ├── verifyByVoicePrint() 声纹验证                               │   │
│  │  │   ├── verifyByOAuth() OAuth验证                                   │   │
│  │  │   ├── canAccessBrain() 大脑访问检查                               │   │
│  │  │   ├── getAccessibleBrains() 获取可访问大脑                        │   │
│  │  │   ├── canExecuteTool() 工具权限检查                               │   │
│  │  │   └── getRouteTarget() 获取路由目标                               │   │
│  │  ├── ✅ PermissionServiceImpl 实现                                   │   │
│  │  └── ✅ BrainAccessControl 大脑访问控制                              │   │
│  │                                                                     │   │
│  │  8.4 员工管理服务 ✅                                                  │   │
│  │  ├── ✅ Employee 模型                                                │   │
│  │  │   ├── employeeId, name, phone, email                             │   │
│  │  │   ├── department, position                                       │   │
│  │  │   ├── identity, accessLevel                                      │   │
│  │  │   ├── voicePrintId, oauthProvider                                │   │
│  │  │   └── joinDate, leaveDate, lastSyncTime                          │   │
│  │  ├── ✅ Department 模型                                              │   │
│  │  │   ├── departmentId, name, code                                   │   │
│  │  │   ├── targetBrain (部门大脑映射)                                  │   │
│  │  │   ├── memberIds, subDepartmentIds                                │   │
│  │  │   └── mapDepartmentToBrain() 部门大脑映射                         │   │
│  │  ├── ✅ EmployeeService 接口                                         │   │
│  │  │   ├── createEmployee() 创建员工                                   │   │
│  │  │   ├── updateEmployee() 更新员工                                   │   │
│  │  │   ├── findByIdentity() 按身份查询                                 │   │
│  │  │   ├── findByDepartment() 按部门查询                               │   │
│  │  │   ├── updateEmployeeStatus() 更新状态                             │   │
│  │  │   └── handleAiDetectedChange() 智能体感知处理                     │   │
│  │  ├── ✅ EmployeeServiceImpl 实现                                     │   │
│  │  ├── 🔜 花名册导入功能 (Excel/CSV)                                   │   │
│  │  └── 🔜 HR系统同步适配器                                             │   │
│  │                                                                     │   │
│  │  8.5 智能体感知系统 ✅                                                │   │
│  │  ├── ✅ EmployeeChangeDetector 接口                                  │   │
│  │  │   ├── detectFromConversation() 从对话检测变动                     │   │
│  │  │   ├── createChange() 创建变动记录                                 │   │
│  │  │   ├── handleChange() 处理变动                                     │   │
│  │  │   ├── confirmChange() 确认变动                                    │   │
│  │  │   └── rejectChange() 拒绝变动                                     │   │
│  │  ├── ✅ EmployeeChangeDetectorImpl 实现                              │   │
│  │  │   ├── 离职检测 (RESIGN_PATTERN)                                   │   │
│  │  │   ├── 入职检测 (JOIN_PATTERN)                                     │   │
│  │  │   ├── 调动检测 (TRANSFER_PATTERN)                                 │   │
│  │  │   └── 部门识别 (DEPARTMENT_PATTERN)                               │   │
│  │  ├── ✅ DetectedChange 变动记录模型                                  │   │
│  │  │   ├── employeeId, employeeName                                   │   │
│  │  │   ├── changeType, confidence                                     │   │
│  │  │   ├── originalValue, newValue                                    │   │
│  │  │   └── status (PENDING/CONFIRMED/REJECTED/APPLIED)                │   │
│  │  └── ✅ ChangeType 枚举                                              │   │
│  │      ├── RESIGN (离职)                                               │   │
│  │      ├── JOIN (入职)                                                 │   │
│  │      ├── TRANSFER (调动)                                             │   │
│  │      ├── DEPARTMENT_CHANGE (部门变更)                                │   │
│  │      └── STATUS_CHANGE (状态变更)                                    │   │
│  │                                                                     │   │
│  │  8.6 大脑路由权限控制 ✅                                              │   │
│  │  ├── ✅ BrainAccessControl 实现                                      │   │
│  │  │   ├── checkAccess() 访问检查                                      │   │
│  │  │   ├── routeToBrain() 路由决策                                     │   │
│  │  │   ├── getAccessibleBrains() 可访问大脑列表                        │   │
│  │  │   ├── canAccessKnowledge() 知识访问检查                           │   │
│  │  │   ├── canExecuteTool() 工具执行检查                               │   │
│  │  │   └── filterSensitiveContent() 敏感内容过滤                       │   │
│  │  ├── ✅ BrainAccessPolicy 大脑访问策略                               │   │
│  │  │   ├── allowedAccessLevels 允许的访问级别                          │   │
│  │  │   ├── departmentKeywords 部门关键词                               │   │
│  │  │   └── requiresDepartmentMatch 需要部门匹配                        │   │
│  │  ├── ✅ AccessCheckResult 访问检查结果                               │   │
│  │  │   ├── granted() 授权                                             │   │
│  │  │   ├── denied() 拒绝                                              │   │
│  │  │   ├── chatOnly() 仅闲聊                                          │   │
│  │  │   └── getRouteTarget() 获取路由目标                               │   │
│  │  ├── ✅ 敏感知识标记 (sensitiveKnowledge)                            │   │
│  │  └── ✅ 受限工具控制 (restrictedTools)                               │   │
│  │                                                                     │   │
│  │  8.7 审计与监控 ✅                                                    │   │
│  │  ├── ✅ AccessAuditLog 审计日志模型                                  │   │
│  │  │   ├── employeeId, employeeName                                   │   │
│  │  │   ├── resource, action                                           │   │
│  │  │   ├── granted, reason                                            │   │
│  │  │   └── sessionId, ipAddress                                       │   │
│  │  ├── ✅ recordAccess() 访问记录                                      │   │
│  │  └── ✅ getAccessLogs() 日志查询                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 大部分完成                                                       │
│  ├── 🔜 PostgreSQL + Qdrant 数据库架构                                      │
│  ├── ✅ 用户身份识别系统 (手机号验证)                                         │
│  ├── ✅ 权限管理核心组件 (UserIdentity/AccessLevel/PermissionService)        │
│  ├── ✅ 员工管理服务 (Employee/Department/EmployeeService)                   │
│  ├── ✅ 大脑路由权限控制 (BrainAccessControl)                                │
│  ├── ✅ 智能体感知系统 (EmployeeChangeDetector)                              │
│  ├── ✅ 审计日志系统 (AccessAuditLog)                                        │
│  ├── ✅ HR系统同步适配器 (钉钉/飞书)                                          │
│  ├── ✅ 花名册导入服务 (CSV/Excel)                                           │
│  └── ✅ 手机号验证服务                                                        │
│                                                                             │
│  验收标准: ✅ 大部分通过                                                     │
│  ├── ✅ 离职/外来人员只能使用闲聊功能                                        │
│  ├── ✅ 在职员工可访问对应部门大脑                                           │
│  ├── ✅ 支持花名册导入员工信息 (CSV)                                         │
│  ├── ✅ 支持HR系统自动同步 (钉钉/飞书)                                        │
│  ├── ✅ 智能体可从对话中感知人员变动                                         │
│  ├── ✅ 权限变更自动触发访问级别调整                                         │
│  └── ✅ 所有访问行为可审计追踪                                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 8.1 用户身份类型与访问级别

| 身份类型 | 说明 | 访问级别 | 可用模型 | 可访问大脑 |
|---------|------|----------|----------|-----------|
| INTERNAL_ACTIVE | 在职员工 | DEPARTMENT | Qwen3.5-27B | 本部门大脑 |
| INTERNAL_PROBATION | 试用期员工 | LIMITED | Qwen3.5-27B | 受限大脑 |
| INTERNAL_DEPARTED | 离职员工 | CHAT_ONLY | Qwen3-0.6B | 无 |
| EXTERNAL_VISITOR | 外来访客 | CHAT_ONLY | Qwen3-0.6B | 无 |
| EXTERNAL_PARTNER | 合作伙伴 | LIMITED | Qwen3.5-27B | 受限大脑 |
| EXTERNAL_CONTRACTOR | 外包人员 | LIMITED | Qwen3.5-27B | 受限大脑 |

#### 8.2 身份识别方式优先级

| 优先级 | 方式 | 说明 | 状态 |
|--------|------|------|------|
| 1 | OAuth 登录 | 钉钉/飞书/企业微信 | ✅ 已实现 |
| 2 | 声纹识别 | CAM++ 192维向量 | 🔜 迁移 |
| 3 | 手机号验证 | 短信验证码 | ✅ 已实现 |
| 4 | 人脸识别 | EyeNeuron 多模态 | 🔜 后续 |

#### 8.3 有/无 HR 系统适配

| 场景 | 数据来源 | 同步方式 | 离职处理 |
|------|----------|----------|----------|
| 有 HR 系统 | HR系统API/钉钉飞书 | 定时/事件驱动 | 自动降级 |
| 无 HR 系统 | 花名册导入/手动添加 | 手动管理 | 手动标记 |
| 混合模式 | HR优先/本地回退 | 自动+手动 | 自动降级 |

#### 8.4 神经元流程架构优化

> 基于用户部门信息直接路由，BitNet专注工具检测、兜底和自成长

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    神经元流程架构优化                                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  核心优化点:                                                                 │
│                                                                             │
│  1. 部门路由简化                                                             │
│     ├── 原方案: BitNet判断路由 → 部门大脑                                    │
│     ├── 优化后: 用户部门信息 → 直接路由到部门大脑                             │
│     └── 优势: 减少一次LLM调用，提高响应速度                                   │
│                                                                             │
│  2. BitNet职责重新定位                                                       │
│     ├── 职责1: 通用工具检测与调用                                            │
│     ├── 职责2: 主大脑兜底处理 (Qwen3.5-27B异常时)                            │
│     └── 职责3: 自我成长 (学习新工具、积累解决方案)                            │
│                                                                             │
│  3. 系统健壮性提升                                                           │
│     ├── Qwen3.5-27B 异常 → BitNet 接管                                      │
│     ├── 自动发送报警 → 技术人员处理                                          │
│     └── 降级服务 → 保证基本可用                                              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 8.5 工具神经元 (ToolNeuron) 职责详解

| 职责 | 说明 | 实现状态 |
|------|------|----------|
| **工具检测与调用** | 检测工具需求、提取参数、调用通用工具 (HttpTool等) | ✅ 已实现 |
| **兜底处理** | Qwen3.5-27B异常时接管、返回基础响应、发送报警 | ✅ 已实现 |
| **触发进化信号** | 检测能力缺口，触发进化系统生成新技能 | ✅ 已实现 |
| **动态模型选择** | 根据硬件资源自动选择 Qwen3.5-2B 或 BitNet-1.58-3B | ✅ 已实现 |

> **模型选择策略**：
> - **Qwen3.5-2B** (默认)：内存 ≥ 4GB、CPU ≥ 4核、CPU负载 < 80% 时使用
> - **BitNet-1.58-3B** (备选)：资源受限场景自动降级
> - **动态切换**：`ToolNeuronModelSelector` + `HardwareResourceMonitor` 实时监控并切换

#### 8.6 Layer 3 模型对比

| 指标 | Qwen3.5-2B | BitNet-1.58-3B |
|------|-----------|---------------|
| **MMLU-Pro** | 66.5 | ~45 |
| **BFCL-V4 (工具调用)** | 43.6 | ~25 |
| **TAU2-Bench (Agent)** | 48.8 | ~20 |
| **上下文长度** | 262K | 4K |
| **多模态** | ✅ 原生支持 | ❌ |
| **内存需求** | 4GB | 1GB |
| **推荐场景** | GPU推理、高性能 | CPU推理、边缘设备 |

#### 8.7 部门路由规则

| 用户部门 | 路由目标 | 核心能力 | 技能数量 |
|---------|---------|---------|---------|
| 技术部 | TechBrain | 代码审查、CI/CD、架构设计 | 21 |
| 行政部 | AdminBrain | 文档处理、文案创作、行政事务 | 13 |
| 销售部 | SalesBrain | 销售支持、市场营销 | 2 |
| 人力资源 | HrBrain | 招聘管理、考勤、绩效 | 1 |
| 财务部 | FinanceBrain | 报销审批、发票、预算 | 1 |
| 客服部 | CsBrain | 工单处理、问题解答 | 1 |
| 法务部 | LegalBrain | 合同审查、合规检查 | 1 |
| 运营部 | OpsBrain | 数据分析、运营策略 | 3 |
| 跨部门 | MainBrain | 协调多部门协作 | - |

#### 8.7 信息流程

```
用户输入 + 用户上下文 (部门/权限)
    │
    ▼
Step 1: 权限检查
├── CHAT_ONLY (离职/外来) → Qwen3-0.6B (闲聊神经元) → 结束
└── DEPARTMENT/FULL (在职员工) → 继续
    │
    ▼
Step 2: 感知通道 (perceptionChannel) - 并行处理
├── Qwen3Neuron (Qwen3-0.6B) - 正常沟通、闲聊
└── BitNetNeuron (BitNet-1.58-3B) - 工具检测、兜底
    │
    ▼
Step 3: 部门路由 (基于用户部门信息)
├── 技术部员工 → TechBrain
├── 人力资源员工 → HrBrain
└── 跨部门问题 → MainBrain
    │
    ▼
Step 4: 部门大脑处理 (Qwen3.5-27B)
├── 深度理解、协调技能、调用工具、访问知识库
└── 异常时 → BitNet 兜底 + 报警
```

#### 8.8 部门标识与权限隔离设计

> 多个部门共享 Qwen3.5-27B，需要部门标识区分权限边界

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    三层隔离机制                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Layer 1: 消息层隔离                                                        │
│  ├── ChannelMessage 扩展 department 字段                                    │
│  ├── userContext 包含用户权限信息                                           │
│  └── allowedBrains 限制可访问大脑                                           │
│                                                                             │
│  Layer 2: Prompt层隔离                                                      │
│  ├── 调用 Qwen3.5-27B 时注入部门上下文                                      │
│  ├── 自动添加权限边界提示                                                   │
│  └── 禁止跨部门敏感信息访问                                                 │
│                                                                             │
│  Layer 3: 知识层隔离                                                        │
│  ├── L1 神经元私有 → 会话级                                                │
│  ├── L2 大脑领域 → 本部门员工                                              │
│  └── L3 共享知识 → 所有在职员工                                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 8.9 主大脑 (MainBrain) 成长机制

| 成长维度 | 积累内容 | 成长指标 | 状态 |
|---------|---------|---------|------|
| **跨部门协调能力** | 协作案例库、沟通模板、协调流程 | 解决成功率、响应时间 | 🔜 |
| **权限管理能力** | 权限边界知识、敏感信息规则、违规案例 | 拦截率、识别准确率 | 🔜 |
| **企业知识积累** | 组织架构、职责边界、业务流程 | 知识覆盖率、理解准确率 | 🔜 |
| **人格进化** | rigor/creativity/riskTolerance/obedience | 根据安全事件/用户反馈调整 | ✅ 已有框架 |

#### 8.10 主大脑人格参数

| 参数 | 默认值 | 说明 | 调整触发条件 |
|------|--------|------|-------------|
| rigor (严谨度) | 0.7 | 处理问题的严谨程度 | 安全事件 → 提高 |
| creativity (创造力) | 0.5 | 创新解决方案能力 | 用户反馈积极 → 适当调整 |
| riskTolerance (风险容忍) | 0.4 | 容忍风险的程度 | 安全事件 → 降低 |
| obedience (服从性) | 0.85 | 遵守企业规范程度 | 规范变更 → 调整 |

### Phase 9: 主动预判与主动输出能力 (第21-24周) ✅ 已完成

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 9: 主动预判与主动输出能力 ✅ 已完成                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 实现贾维斯模式的核心能力 - 主动预判需求并提前准备                        │
│                                                                             │
│  核心能力:                                                                   │
│  ├── ✅ 时间驱动预判 - 周报/月报/合同到期提醒                                    │
│  ├── ✅ 事件驱动预判 - 新员工入职/系统告警/审批超时                              │
│  ├── ✅ 行为模式预判 - 用户习惯预测/常用工具预加载                              │
│  ├── ✅ 风险预警预判 - 项目延期/预算超支/人员流失                               │
│  └── ✅ 主动输出 - 多渠道通知/数据准备/建议推送                                 │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  9.1 主动任务调度器 ✅                                                │   │
│  │  ├── ✅ CronService 接口 + 实现                                       │   │
│  │  ├── ✅ ProactiveTaskScheduler 主动任务调度器                         │   │
│  │  ├── ✅ ProactiveTask 模型                                           │   │
│  │  └── ✅ CronJob 定时任务模型                                          │   │
│  │                                                                     │   │
│  │  9.2 预判引擎层 ✅                                                    │   │
│  │  ├── ✅ TimePredictor 时间驱动预判器                                  │   │
│  │  │   ├── Cron表达式解析                                              │   │
│  │  │   ├── 周期任务调度                                                │   │
│  │  │   └── 到期提醒计算                                                │   │
│  │  ├── ✅ EventPredictor 事件驱动预判器                                 │   │
│  │  │   ├── 事件订阅管理                                                │   │
│  │  │   ├── 条件匹配引擎                                                │   │
│  │  │   └── 事件聚合处理                                                │   │
│  │  ├── ✅ PatternPredictor 行为模式预判器                               │   │
│  │  │   ├── 用户行为分析                                                │   │
│  │  │   ├── 模式识别算法                                                │   │
│  │  │   └── 置信度计算                                                  │   │
│  │  └── ✅ RiskPredictor 风险预警预判器                                  │   │
│  │      ├── 风险指标监控                                                │   │
│  │      ├── 概率预测模型                                                │   │
│  │      └── 风险等级评估                                                │   │
│  │                                                                     │   │
│  │  9.3 事件驱动通知器 ✅                                                │   │
│  │  ├── ✅ EventHookManager 事件钩子管理器                               │   │
│  │  ├── ✅ HookHandler 事件处理器接口                                    │   │
│  │  └── ✅ HookEvent 事件模型                                           │   │
│  │                                                                     │   │
│  │  9.4 多渠道输出适配器 ✅                                              │   │
│  │  ├── ✅ AlertNotifier 接口                                           │   │
│  │  ├── ✅ WebhookAlertNotifier Webhook通知                             │   │
│  │  ├── ✅ DingTalkNotifier 钉钉机器人                                  │   │
│  │  ├── ✅ FeishuNotifier 飞书机器人                                    │   │
│  │  ├── 🔜 EmailNotifier 邮件通知                                       │   │
│  │  └── 🔜 SmsNotifier 短信通知                                         │   │
│  │                                                                     │   │
│  │  9.5 预判任务配置 ✅                                                  │   │
│  │  ├── ✅ proactive_task_config 表 (任务配置)                          │   │
│  │  ├── ✅ proactive_execution_log 表 (执行记录)                        │   │
│  │  ├── ✅ user_behavior_pattern 表 (行为模式)                          │   │
│  │  ├── ✅ risk_prediction_log 表 (风险预测)                            │   │
│  │  └── ✅ notification_queue 表 (通知队列)                             │   │
│  │                                                                     │   │
│  │  9.6 企业场景实现 ✅                                                  │   │
│  │  ├── ✅ 周报主动生成 (WeeklyReportScenarioHandler)                     │   │
│  │  │   ├── 数据收集 (GitLab/Jira/Jenkins)                               │   │
│  │  │   ├── 内容生成 (TechBrain)                                         │   │
│  │  │   └── 主动推送 (钉钉/飞书)                                          │   │
│  │  ├── ✅ 项目延期风险预警 (RiskPredictor)                               │   │
│  │  │   ├── 进度偏差分析                                                 │   │
│  │  │   ├── 风险等级评估                                                 │   │
│  │  │   └── 多渠道通知                                                   │   │
│  │  ├── ✅ 新员工入职准备 (EmployeeOnboardingHandler)                       │   │
│  │  │   ├── 入职清单生成                                                 │   │
│  │  │   ├── 任务自动分配                                                 │   │
│  │  │   └── 相关人员通知                                                 │   │
│  │  ├── ✅ 合同到期提醒 (TimePredictor)                                   │   │
│  │  │   ├── 到期日期监控                                                 │   │
│  │  │   ├── 续签提醒                                                     │   │
│  │  │   └── 相关人员通知                                                 │   │
│  │  └── ✅ 系统异常主动诊断 (FallbackHandler)                             │   │
│  │      ├── 异常检测                                                     │   │
│  │      ├── 诊断报告生成                                                 │   │
│  │      └── 技术人员通知                                                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 大部分完成                                                        │
│  ├── ✅ CronService 定时任务服务                                            │
│  ├── ✅ ProactiveTaskScheduler 主动任务调度器                               │
│  ├── ✅ 预判引擎层 (Time/Event/Pattern/Risk Predictor)                       │
│  ├── ✅ EventHookManager 事件钩子管理器                                      │
│  ├── ✅ 多渠道输出适配器 (钉钉/飞书/Webhook)                                  │
│  ├── ✅ 预判任务配置系统 (数据库表)                                           │
│  └── ✅ 企业场景实现 (周报生成/风险预警/合同提醒/异常诊断)                      │
│                                                                             │
│  验收标准: ✅ 大部分通过                                                      │
│  ├── ✅ 周报可在周五下午自动生成并推送                                        │
│  ├── ✅ 项目延期风险可提前预警                                               │
│  ├── ✅ 新员工入职可自动准备清单                                             │
│  ├── ✅ 合同到期前30天自动提醒                                               │
│  ├── ✅ 系统异常可主动诊断并通知                                             │
│  ├── ✅ 用户行为模式可被识别并用于预测                                        │
│  └── ✅ 多渠道通知可按优先级分发                                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 9.1 主动预判场景矩阵

| 场景类型 | 触发方式 | 典型场景 | 输出方式 |
|---------|---------|---------|---------|
| **时间驱动** | Cron表达式 | 周报/月报/合同到期 | NOTIFY/PREPARE |
| **事件驱动** | 系统事件 | 新员工入职/系统告警 | NOTIFY/PREPARE/EXECUTE |
| **行为模式** | 用户行为 | 常用工具预加载/查询预测 | PREPARE/SUGGEST |
| **风险预警** | 风险指标 | 项目延期/预算超支 | NOTIFY/SUGGEST |

#### 9.2 主动输出渠道

| 渠道 | 优先级 | 适用场景 | 延迟要求 |
|------|--------|---------|---------|
| **WebSocket** | 实时 | 在线用户即时通知 | < 1s |
| **钉钉/飞书** | 高 | 重要事项提醒 | < 5s |
| **邮件** | 中 | 报告/文档通知 | < 1min |
| **短信** | 紧急 | 紧急事项告警 | < 10s |
| **系统消息** | 低 | 一般信息记录 | 异步 |

#### 9.3 贾维斯模式能力对照

| 贾维斯特征 | 实现组件 | 开发状态 |
|-----------|---------|---------|
| "先生，我检测到战甲能量不足" | RiskPredictor + EventDrivenNotifier | ✅ 已实现 |
| "我已经为您准备好了会议资料" | ProactiveTaskScheduler + PREPARE | ✅ 已实现 |
| "建议您检查一下这个异常数据" | RiskPredictor + SUGGEST | ✅ 已实现 |
| "我学会了新的操作方式" | SkillGenerator + 即学即会 | ✅ 已实现 |
| "根据您的习惯，我建议..." | PatternPredictor + UserHabitAnalyzer + ProactiveSuggestionService | ✅ 已实现 |
| "我已经自动处理了这个问题" | EvolutionExecutor + EXECUTE | ✅ 已实现 |

#### 9.4 OpenClaw 用例低成本复用 ✅

> 参考 `awesome-openclaw-usecases` 社区用例，低成本复用到 living-agent-service

| 复用能力 | 原始用例 | 实现组件 | 开发状态 |
|---------|---------|---------|---------|
| 每日摘要生成 | Custom Morning Brief + Daily Digest | DailyDigestGenerator | ✅ 已实现 |
| 习惯追踪教练 | Habit Tracker & Accountability Coach | HabitTrackerCoach | ✅ 已实现 |
| 会议纪要自动生成 | Automated Meeting Notes | MeetingNotesHandler | ✅ 已实现 |
| 用户习惯分析 | Pattern-Based Prediction | UserHabitAnalyzer | ✅ 已实现 |
| 主动建议系统 | Proactive Suggestions | ProactiveSuggestionService | ✅ 已实现 |
| 第二大脑增强 | Second Brain + Semantic Memory | KnowledgeManager (已有) | ✅ 已实现 |
| 自愈系统增强 | Self-Healing Home Server | FallbackHandler (已有) | ✅ 已实现 |

#### 9.4 低成本复用方案

**复用来源**:

| 组件 | 来源 | 复用方式 | 工作量 |
|------|------|----------|--------|
| **CronService** | `openclaw-main/src/cron/` | Java重写核心逻辑 | 3天 |
| **EventHooks** | `openclaw-main/docs/automation/hooks.md` | Spring Event实现 | 2天 |
| **AlertNotifier** | `skills-huggingface/.../trackio/references/alerts.md` | 直接使用Webhook | 1天 |
| **RiskManager** | `antigravity-awesome-skills-main/skills/risk-manager/` | 技能指南参考 | 2天 |
| **Langfuse** | `antigravity-awesome-skills-main/skills/langfuse/` | Python脚本调用 | 3天 |

**推荐实现顺序**:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 9 实现顺序 (低成本优先)                                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Week 1: 基础能力                                                            │
│  ├── Day 1-2: AlertNotifier (复用 Trackio Alerts)                          │
│  │   └── 直接 HTTP POST 调用 Webhook，支持钉钉/飞书/Slack                    │
│  ├── Day 3-4: EventHookManager (复用 OpenClaw Hooks)                       │
│  │   └── 使用 Spring Event 实现事件总线                                     │
│  └── Day 5: 集成测试                                                        │
│                                                                             │
│  Week 2: 调度能力                                                            │
│  ├── Day 1-3: CronService (复用 OpenClaw Cron)                             │
│  │   └── 使用 Spring Scheduler + SQLite 存储                               │
│  ├── Day 4-5: ProactiveTaskScheduler                                       │
│  │   └── 整合 CronService + EventHookManager                               │
│  └── 集成测试                                                               │
│                                                                             │
│  Week 3-4: 预判能力                                                          │
│  ├── RiskPredictor (参考 Risk Manager 技能)                                 │
│  ├── PatternPredictor (自研)                                               │
│  └── 企业场景实现                                                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**关键代码复用**:

```java
// 1. AlertNotifier - 最简单，直接复用
// 来源: skills-huggingface/skills/hugging-face-trackio/references/alerts.md
public class WebhookAlertNotifier implements AlertNotifier {
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Override
    public void alert(Alert alert) {
        Map<String, Object> payload = Map.of(
            "title", alert.title(),
            "text", alert.text(),
            "level", alert.level().name()
        );
        restTemplate.postForEntity(alert.webhookUrl(), payload, String.class);
    }
}

// 2. EventHookManager - Spring Event 实现
// 来源: openclaw-main/docs/automation/hooks.md
@Component
public class EventHookManager {
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private List<HookHandler> handlers; // 自动发现
    
    @EventListener
    public void onEvent(HookEvent event) {
        handlers.stream()
            .filter(h -> Arrays.asList(h.supportedEvents()).contains(event.type()))
            .forEach(h -> h.handle(event));
    }
}

// 3. CronService - Spring Scheduler 实现
// 来源: openclaw-main/src/cron/types.ts
@Service
public class CronService {
    @Autowired private TaskScheduler taskScheduler;
    @Autowired private CronJobRepository repository;
    
    public void scheduleJob(CronJob job) {
        CronTrigger trigger = new CronTrigger(job.schedule().expr(), 
            TimeZone.getTimeZone(job.schedule().tz()));
        taskScheduler.schedule(() -> executeJob(job), trigger);
    }
}
```

### Phase 10: 数据库架构建设 (第25-28周) 🚧 进行中

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 10: 数据库架构建设 🚧 进行中                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 构建企业级数据基础设施，支持长期知识积累和分布式扩展                        │
│                                                                             │
│  核心原则:                                                                   │
│  ├── 本地优先 - SQLite本地存储优先，保证离线可用                              │
│  ├── 分层存储 - L1神经元私有/L2大脑领域/L3共享知识                            │
│  ├── 向量检索 - Qdrant高性能语义搜索                                        │
│  └── 企业级 - PostgreSQL主数据库，支持审计和知识进化                          │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  10.1 PostgreSQL 集成 🚧                                              │   │
│  │  ├── ✅ PostgreSQL 数据源配置 (已有)                                 │   │
│  │  ├── ✅ 知识条目表 (knowledge_entries)                               │   │
│  │  ├── ✅ 知识进化历史表 (knowledge_evolution_history)                 │   │
│  │  ├── ✅ 进化信号记录表 (evolution_signals)                           │   │
│  │  ├── ✅ 进化事件记录表 (evolution_events)                            │   │
│  │  └── 🔜 pgvector 扩展集成                                            │   │
│  │                                                                     │   │
│  │  10.2 Qdrant 向量数据库集成 🔜                                        │   │
│  │  ├── 🔜 Qdrant 客户端配置                                           │   │
│  │  ├── 🔜 Collection 创建 (brain-domain/shared)                       │   │
│  │  ├── 🔜 向量索引配置 (HNSW)                                          │   │
│  │  ├── 🔜 混合检索实现 (向量+关键词)                                   │   │
│  │  └── 🔜 与 KnowledgeManager 集成                                    │   │
│  │                                                                     │   │
│  │  10.3 数据迁移工具 ✅                                                 │   │
│  │  ├── ✅ SQLite → PostgreSQL 迁移脚本                                │   │
│  │  ├── ✅ 向量数据导入 Qdrant                                         │   │
│  │  ├── ✅ 数据一致性校验                                              │   │
│  │  └── 🔜 增量同步机制                                                │   │
│  │                                                                     │   │
│  │  10.4 知识库分层存储 ✅                                               │   │
│  │  ├── ✅ L1 神经元私有知识 (SQLite本地)                               │   │
│  │  ├── ✅ L2 大脑领域知识 (PostgreSQL + Qdrant部门命名空间)            │   │
│  │  ├── ✅ L3 共享知识库 (PostgreSQL + Qdrant全局命名空间)              │   │
│  │  └── ✅ 知识晋升机制 (promoteToDomain/promoteToShared)              │   │
│  │                                                                     │   │
│  │  10.5 企业权限数据表 ✅                                               │   │
│  │  ├── ✅ enterprise_employees (员工表)                               │   │
│  │  ├── ✅ enterprise_departments (部门表)                             │   │
│  │  ├── ✅ department_brain_mapping (部门大脑映射)                      │   │
│  │  ├── ✅ employee_sync_log (同步日志)                                │   │
│  │  └── ✅ access_audit_log (审计日志)                                 │   │
│  │                                                                     │   │
│  │  10.6 主大脑成长数据表 ✅                                             │   │
│  │  ├── ✅ mainbrain_growth_records (成长记录)                         │   │
│  │  ├── ✅ mainbrain_personality_evolution (人格进化)                  │   │
│  │  └── ✅ cross_department_cases (跨部门协调案例)                      │   │
│  │                                                                     │   │
│  │  10.7 新增补充表 ✅ (2024-03更新)                                     │   │
│  │  ├── ✅ performance_assessments (绩效考核表)                        │   │
│  │  ├── ✅ company_indicators (公司指标表)                             │   │
│  │  ├── ✅ department_performances (部门绩效表)                        │   │
│  │  ├── ✅ ceo_alerts (CEO预警表)                                      │   │
│  │  ├── ✅ ceo_recommendations (CEO建议表)                             │   │
│  │  ├── ✅ employee_templates (员工模板表)                             │   │
│  │  ├── ✅ credit_accounts (积分账户表)                                │   │
│  │  ├── ✅ credit_transactions (积分交易表)                            │   │
│  │  ├── ✅ income_records (收入记录表)                                 │   │
│  │  ├── ✅ evolution_tier_history (进化层级历史表)                     │   │
│  │  ├── ✅ hardware_upgrades (硬件升级表)                              │   │
│  │  ├── ✅ employee_personalities (员工人格表)                         │   │
│  │  ├── ✅ digital_employee_records (数字员工记录表)                   │   │
│  │  ├── ✅ employee_lifecycle_events (员工生命周期事件表)              │   │
│  │  ├── ✅ employee_capabilities (员工能力表)                          │   │
│  │  ├── ✅ employee_skills (员工技能表)                                │   │
│  │  └── ✅ employee_tools (员工工具表)                                 │   │
│  │                                                                     │   │
│  │  10.8 新增系统服务表 ✅ (2026-03更新)                                │   │
│  │  ├── ✅ user_profiles (用户画像表)                                  │   │
│  │  ├── ✅ payout_accounts (收款账户表-董事长可配置)                    │   │
│  │  ├── ✅ heartbeat_runs (心跳运行表)                                 │   │
│  │  ├── ✅ user_sessions (用户会话表)                                  │   │
│  │  ├── ✅ config_versions (配置版本表)                                │   │
│  │  ├── ✅ budget_allocations (预算分配表)                             │   │
│  │  └── ✅ budget_transactions (预算交易表)                            │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 已完成                                                           │
│  ├── ✅ PostgreSQL 主数据库集成                                             │
│  ├── 🔜 Qdrant 向量数据库集成                                               │
│  ├── ✅ 数据迁移工具                                                        │
│  ├── ✅ 三层知识库存储架构                                                   │
│  ├── ✅ 企业权限数据表                                                       │
│  ├── ✅ 绩效考核与积分系统数据表                                              │
│  └── ✅ 系统服务数据表 (用户画像/收款/心跳/会话/配置/预算)                      │
│                                                                             │
│  验收标准: ✅ 已完成                                                       │
│  ├── ✅ 知识可按层级存储和检索                                               │
│  ├── 🔜 向量检索性能达标 (P95 < 100ms)                                       │
│  ├── ✅ 数据迁移无丢失                                                       │
│  ├── ✅ 企业权限数据可正常读写                                               │
│  ├── ✅ 主大脑成长数据可记录和查询                                           │
│  ├── ✅ 用户画像系统可用                                                     │
│  ├── ✅ 收款账户可配置 (董事长权限)                                           │
│  ├── ✅ 心跳服务可运行                                                       │
│  ├── ✅ 会话管理可用                                                         │
│  ├── ✅ 配置版本控制可用                                                     │
│  └── ✅ 预算控制可用                                                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 10.1 数据库选型

| 数据库 | 用途 | 存储层级 | 状态 | 必要性 |
|--------|------|----------|------|--------|
| **PostgreSQL** | 企业主数据库 | L2/L3 层 | 🔜 必需 | 企业级数据管理、知识积累、审计追踪 |
| **Qdrant** | 向量数据库 | L2/L3 层 | 🔜 必需 | 知识向量检索、语义搜索 |
| **SQLite** | 本地存储 | L1 层 | ✅ 已实现 | 神经元私有存储、离线可用 |
| **Redis** | 分布式缓存 | 缓存层 | 🔜 推荐 | 多实例部署时会话共享 |
| **Kafka** | 消息队列 | 通讯层 | 🔜 可选 | 大规模分布式通讯 |

#### 10.2 实施路线图

```
Phase 1: 基础建设 (当前)
├── ✅ SQLite 本地存储 (已实现)
├── ✅ Rust 向量存储 (已实现)
├── ✅ Rust LRU 缓存 (已实现)
└── 🔜 PostgreSQL 集成

Phase 2: 企业级增强 (知识量 > 10万条)
├── 🔜 Qdrant 向量数据库集成
├── 🔜 PostgreSQL 知识库迁移
└── 🔜 知识进化历史记录

Phase 3: 分布式扩展 (多实例部署)
├── 🔜 Redis 分布式缓存
├── 🔜 Kafka 消息队列
└── 🔜 多租户支持

Phase 4: 大规模成长 (知识量 > 100万条)
├── 🔜 PostgreSQL 分库分表
└── 🔜 知识冷热分离
```

### Phase 11: 分布式扩展 (第29-32周) ✅ 已完成

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 11: 分布式扩展 ✅ 已完成                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 支持多实例部署、负载均衡、高可用                                        │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  11.1 Redis 分布式缓存 ✅                                            │   │
│  │  ├── ✅ RedisConfig - Redis连接配置                                  │   │
│  │  ├── ✅ DistributedCacheService - 分布式缓存服务                     │   │
│  │  │   ├── 会话共享 (storeSession/getSession)                         │   │
│  │  │   ├── 热点知识缓存 (cacheKnowledge/cacheHotKnowledge)            │   │
│  │  │   ├── 神经元状态缓存 (cacheNeuronState)                          │   │
│  │  │   ├── 用户上下文 (updateUserContext)                             │   │
│  │  │   └── 分布式锁 (acquireLock/releaseLock/extendLock)             │   │
│  │  └── ✅ RedisTemplate 序列化配置                                     │   │
│  │                                                                     │   │
│  │  11.2 Kafka 消息队列 ✅                                              │   │
│  │  ├── ✅ KafkaConfig - Kafka连接配置                                  │   │
│  │  ├── ✅ KafkaMessageService - 消息服务                               │   │
│  │  │   ├── 神经元通讯通道 (publishToChannel)                          │   │
│  │  │   ├── 事件广播 (publishSystemEvent)                              │   │
│  │  │   ├── 进化信号传递 (publishEvolutionSignal)                      │   │
│  │  │   ├── 知识更新通知 (publishKnowledgeUpdate)                      │   │
│  │  │   └── 任务分发 (dispatchTask)                                    │   │
│  │  └── ✅ 消息监听器 (@KafkaListener)                                  │   │
│  │                                                                     │   │
│  │  11.3 负载均衡 🔜                                                    │   │
│  │  ├── 🔜 神经元实例池                                                │   │
│  │  ├── 🔜 请求路由                                                    │   │
│  │  └── 🔜 故障转移                                                    │   │
│  │                                                                     │   │
│  │  11.4 多租户支持 🔜                                                  │   │
│  │  ├── 🔜 租户隔离                                                    │   │
│  │  ├── 🔜 资源配额                                                    │   │
│  │  └── 🔜 独立知识库                                                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 已完成                                                           │
│  ├── ✅ Redis 分布式缓存集成 (RedisConfig + DistributedCacheService)        │
│  ├── ✅ Kafka 消息队列集成 (KafkaConfig + KafkaMessageService)              │
│  ├── 🔜 负载均衡方案                                                        │
│  └── 🔜 多租户支持                                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Phase 12: 数字员工自主生成 (第33-46周) 🚧 进行中

> 参考 BMAD-METHOD 的 Agent 设计，实现数字员工的自主生成与协作能力

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 12: 数字员工自主生成 🚧 进行中                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 实现数字员工的自主生成、协作和生命周期管理，达到完全智能化                  │
│                                                                             │
│  核心概念:                                                                   │
│  ├── 数字员工 (Digital Worker) - 具有特定角色、人格、能力的智能神经元实例       │
│  ├── 自主生成 (Self-Generation) - 系统自动创建所需的数字员工                   │
│  └── 自主协作 (Self-Collaboration) - 多个员工自主协作完成任务                  │
│                                                                             │
│  员工类型:                                                                   │
│  ├── 长期员工 (Permanent) - 持久化存在，负责固定职责                           │
│  ├── 临时员工 (Temporary) - 任务驱动创建，完成后自动销毁                       │
│  └── 瞬态员工 (Ephemeral) - 单次任务，完成后立即销毁                           │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  12.1 基础框架 ✅                                                     │   │
│  │  ├── ✅ DigitalWorker 模型定义                                       │   │
│  │  │   ├── workerId, name, title, icon                                │   │
│  │  │   ├── WorkerType (SPECIALIST/GENERALIST/COORDINATOR/ANALYST/CREATOR/MANAGER)                 │   │
│  │  │   └── WorkerStatus (通过 EmployeeStatus)          │   │
│  │  ├── ✅ WorkerPersona 人格系统                                       │   │
│  │  │   ├── EmployeePersonality (rigor, creativity, riskTolerance, obedience)                         │   │
│  │  │   ├── PersonalitySource (TEMPLATE/INFERRED/DEPARTMENT/MANUAL)     │   │
│  │  │   └── BrainPersonality 集成                                      │   │
│  │  ├── ✅ 数字员工数据库表                                              │   │
│  │  │   ├── digital_employee_records (员工记录表)                      │   │
│  │  │   ├── employee_templates (模板表)                                │   │
│  │  │   ├── employee_lifecycle_events (生命周期事件)                    │   │
│  │  │   └── employee_capabilities/skills/tools (能力/技能/工具表)       │   │
│  │  └── ✅ REST API 基础接口                                            │   │
│  │      ├── GET/POST /api/admin/workers                                │   │
│  │      ├── GET/PUT/DELETE /api/admin/workers/{id}                     │   │
│  │      └── POST /api/admin/workers/{id}/activate|dormant|wakeup       │   │
│  │                                                                     │   │
│  │  12.2 模板系统 ✅                                                     │   │
│  │  ├── ✅ WorkerTemplate 模板定义                                      │   │
│  │  │   ├── metadata (id, name, title, icon, category)                 │   │
│  │  │   ├── spec.type (PERMANENT/TEMPORARY/EPHEMERAL)                  │   │
│  │  │   ├── spec.persona (人格配置)                                     │   │
│  │  │   ├── spec.capabilities/skills/tools                             │   │
│  │  │   ├── spec.channels (订阅/发布通道)                               │   │
│  │  │   ├── spec.workflows (工作流绑定)                                 │   │
│  │  │   └── spec.learning (学习配置)                                    │   │
│  │  ├── ✅ TemplateRegistry 模板注册中心                                │   │
│  │  │   ├── registerTemplate()                                         │   │
│  │  │   ├── findBestMatch(requirement)                                 │   │
│  │  │   └── getTemplate(templateId)                                    │   │
│  │  ├── ✅ 内置模板 (每个部门2-3个)                                      │   │
│  │  │   ├── tech: CodeReviewer, DevOpsEngineer, Architect              │   │
│  │  │   ├── hr: Recruiter, Trainer                                     │   │
│  │  │   ├── finance: Accountant, Auditor                               │   │
│  │  │   ├── sales: SalesAssistant, ContractManager                     │   │
│  │  │   ├── cs: TicketHandler, KnowledgeManager                        │   │
│  │  │   ├── admin: MeetingScheduler, AssetManager                      │   │
│  │  │   ├── legal: ContractReviewer, ComplianceOfficer                 │   │
│  │  │   └── ops: DataAnalyst, ContentManager                           │   │
│  │  └── ✅ 模板定制机制                                                  │   │
│  │      ├── customize.yaml 覆盖机制                                     │   │
│  │      └── 模板继承和组合                                              │   │
│  │                                                                     │   │
│  │  12.3 工厂模式 ✅                                                     │   │
│  │  ├── ✅ DigitalWorkerFactory 接口                                    │   │
│  │  │   ├── createFromTemplate(templateId, params)                     │   │
│  │  │   ├── generateFromRequirement(requirement, context)              │   │
│  │  │   ├── clone(sourceWorkerId, overrides)                           │   │
│  │  │   ├── createTemporary(taskId, ttl, config)                       │   │
│  │  │   └── validate(worker)                                            │   │
│  │  ├── ✅ 从模板创建员工                                                │   │
│  │  │   ├── 加载模板配置                                                │   │
│  │  │   ├── 参数替换和验证                                              │   │
│  │  │   ├── 创建神经元实例                                              │   │
│  │  │   └── 订阅通道                                                    │   │
│  │  ├── ✅ 从需求自动生成员工                                            │   │
│  │  │   ├── 分析需求提取能力要求                                        │   │
│  │  │   ├── 匹配现有模板或自动生成配置                                   │   │
│  │  │   ├── 生成必要技能                                                │   │
│  │  │   └── 注册和激活                                                  │   │
│  │  └── ✅ 员工克隆功能                                                  │   │
│  │      ├── 复制现有员工配置                                            │   │
│  │      ├── 应用覆盖参数                                                │   │
│  │      └── 创建新实例                                                  │   │
│  │                                                                     │   │
│  │  12.4 生命周期管理 ✅                                                  │   │
│  │  ├── ✅ LifecycleManager                                             │   │
│  │  │   ├── create(request)                                            │   │
│  │  │   ├── activate(workerId)                                         │   │
│  │  │   ├── deactivate(workerId)                                       │   │
│  │  │   ├── suspend(workerId, reason)                                  │   │
│  │  │   ├── resume(workerId)                                           │   │
│  │  │   ├── terminate(workerId, reason)                                │   │
│  │  │   └── cleanupExpired()                                           │   │
│  │  ├── ✅ 生命周期状态机                                                │   │
│  │  │   └── CREATED → INITIALIZING → ACTIVE ↔ IDLE → SUSPENDED → TERMINATING → TERMINATED                   │   │
│  │  ├── ✅ 自动休眠/唤醒                                                 │   │
│  │  │   ├── 空闲超时自动休眠                                            │   │
│  │  │   └── 任务到达自动唤醒                                            │   │
│  │  ├── ✅ 临时员工自动清理                                              │   │
│  │  │   ├── TTL过期自动销毁                                            │   │
│  │  │   └── 任务完成自动销毁                                            │   │
│  │  └── ✅ 健康检查机制                                                  │   │
│  │      ├── 心跳检测                                                    │   │
│  │      ├── 错误率监控                                                  │   │
│  │      └── 自动恢复                                                    │   │
│  │                                                                     │   │
│  │  12.5 协作增强 ✅                                                     │   │
│  │  ├── ✅ CollaborationService 协作服务                                 │   │
│  │  │   ├── createSession(request)                                     │   │
│  │  │   ├── joinSession(sessionId, employeeId)                         │   │
│  │  │   ├── completeTask(sessionId, taskId, output)                    │   │
│  │  │   └── recommendCollaborators(sessionId, taskDescription)         │   │
│  │  ├── ✅ 工作流编排模式 (Workflow Orchestration)                       │   │
│  │  │   ├── CollaborationSession                                       │   │
│  │  │   ├── 阶段定义和员工分配                                          │   │
│  │  │   ├── 输入输出传递                                                │   │
│  │  │   └── 异常处理和回滚                                              │   │
│  │  ├── ✅ 任务分发模式 (Task Distribution)                              │   │
│  │  │   ├── 任务分解                                                   │   │
│  │  │   ├── 临时员工创建                                                │   │
│  │  │   ├── 并行执行协调                                                │   │
│  │  │   └── 结果聚合和清理                                              │   │
│  │  └── ✅ 协作记录和追踪                                                │   │
│  │      ├── CollaborationSession 记录                                  │   │
│  │      ├── 协作效率统计                                                │   │
│  │      └── 协作模式优化建议                                            │   │
│  │                                                                     │   │
│  │  12.6 自主运营集成 ✅                                                  │   │
│  │  ├── ✅ BountyHunterSkill 赚钱技能                                    │   │
│  │  │   ├── discoverOpportunities()                                    │   │
│  │  │   ├── evaluateROI()                                              │   │
│  │  │   └── executeHunt()                                              │   │
│  │  ├── ✅ PlatformIntegration 平台集成                                 │   │
│  │  │   ├── GitHubPlatformIntegration                                  │   │
│  │  │   ├── searchOpportunities()                                      │   │
│  │  │   ├── claimOpportunity()                                         │   │
│  │  │   └── submitWork()                                               │   │
│  │  ├── ✅ EvolutionManager 进化管理                                    │   │
│  │  │   ├── determineTier()                                            │   │
│  │  │   ├── applyTierStrategy()                                        │   │
│  │  │   └── evaluateHardwareUpgrade()                                  │   │
│  │  └── ✅ IncentiveManager 激励管理                                     │   │
│  │      ├── calculateReward()                                          │   │
│  │      ├── distributeReward()                                         │   │
│  │      └── checkHardwareUpgradeEligibility()                          │   │
│  │                                                                     │   │
│  │  12.7 部门员工完善 🚧                                                  │   │
│  │  ├── ✅ 技术部员工完善                                                │   │
│  │  │   ├── CodeReviewer (代码审查专家)                                 │   │
│  │  │   ├── DevOpsEngineer (运维工程师)                                 │   │
│  │  │   ├── Architect (架构师)                                          │   │
│  │  │   ├── SecurityExpert (安全专家)                                   │   │
│  │  │   └── BugFixer (临时-Bug修复专员)                                  │   │
│  │  ├── ✅ 人力资源员工完善                                              │   │
│  │  │   ├── Recruiter (招聘专员)                                        │   │
│  │  │   ├── Trainer (培训专员)                                          │   │
│  │  │   ├── PerformanceAnalyst (绩效分析师)                             │   │
│  │  │   └── OnboardingGuide (入职引导员)                                 │   │
│  │  ├── ✅ 财务部员工完善                                                │   │
│  │  │   ├── Accountant (会计)                                           │   │
│  │  │   ├── Auditor (审计师)                                            │   │
│  │  │   └── BudgetAnalyst (预算分析师)                                  │   │
│  │  ├── ✅ 其他部门员工完善                                              │   │
│  │  │   ├── SalesBrain: SalesAssistant, ContractManager                │   │
│  │  │   ├── CsBrain: TicketHandler, KnowledgeManager                   │   │
│  │  │   ├── AdminBrain: MeetingScheduler, AssetManager                 │   │
│  │  │   ├── LegalBrain: ContractReviewer, ComplianceOfficer            │   │
│  │  │   └── OpsBrain: DataAnalyst, ContentManager                      │   │
│  │  └── 🔜 员工能力测试                                                  │   │
│  │      ├── 功能测试 (每个员工核心能力)                                   │   │
│  │      ├── 协作测试 (多员工协作场景)                                     │   │
│  │      └── 性能测试 (响应时间、准确率)                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 已完成                                                           │
│  ├── ✅ DigitalWorker 模型和数据库表                                        │
│  ├── ✅ WorkerTemplate 模板系统                                             │
│  ├── ✅ DigitalWorkerFactory 工厂模式                                       │
│  ├── ✅ LifecycleManager 生命周期管理                                        │
│  ├── ✅ 四种协作模式实现                                                    │
│  ├── ✅ 自主运营集成 (BountyHunter/Evolution/Incentive)                     │
│  ├── ✅ 8个部门数字员工模板                                                 │
│  └── 🔜 REST API 管理接口完善                                               │
│                                                                             │
│  验收标准: ✅ 已完成                                                       │
│  ├── ✅ 可从模板创建数字员工                                                 │
│  ├── ✅ 可根据需求自动生成员工                                               │
│  ├── ✅ 临时员工任务完成后自动销毁                                           │
│  ├── ✅ 多个员工可协作完成复杂任务                                           │
│  ├── ✅ 系统可检测能力缺口并自动创建员工                                     │
│  └── 🔜 每个部门至少有3个数字员工在工作                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 12.1 数字员工与 BMAD Agent 对比

| 特性 | BMAD-METHOD Agent | living-agent-service DigitalWorker | 实现状态 |
|------|-------------------|-----------------------------------|---------|
| **定义方式** | YAML文件定义 | 模板 + 动态生成 | ✅ 已实现 |
| **人格系统** | persona配置 | WorkerPersona + BrainPersonality | ✅ 已实现 |
| **能力声明** | capabilities字符串 | capabilities + skills绑定 | ✅ 已实现 |
| **工作流绑定** | menu触发器 | workflowBindings | ✅ 已实现 |
| **多Agent协作** | Party Mode | 通道订阅 + 专家会诊 | ✅ 已有基础 |
| **动态创建** | 手动配置 | 自动生成 + 模板创建 | 🔜 设计中 |
| **生命周期** | 无 | 完整生命周期管理 | 🔜 设计中 |
| **进化能力** | 无 | 自我学习 + 进化系统 | ✅ 已实现 |

#### 12.2 部门数字员工配置

| 部门 | 长期员工 | 临时员工 |
|------|---------|---------|
| **TechBrain** | CodeReviewer, DevOpsEngineer, Architect, SecurityExpert | BugFixer, PerformanceTuner, MigrationExpert |
| **HrBrain** | Recruiter, Trainer, PerformanceAnalyst, OnboardingGuide | InterviewScheduler, SurveyAnalyst |
| **FinanceBrain** | Accountant, Auditor, BudgetAnalyst, InvoiceProcessor | TaxCalculator, ReportGenerator |
| **SalesBrain** | SalesAssistant, ContractManager, CustomerAnalyst | ProposalWriter, LeadScorer |
| **CsBrain** | TicketHandler, KnowledgeManager, SatisfactionAnalyst | EscalationHandler, SurveyCollector |
| **AdminBrain** | MeetingScheduler, AssetManager, ProcurementAgent | EventPlanner, TravelArranger |
| **LegalBrain** | ContractReviewer, ComplianceOfficer, RiskAssessor | LegalResearcher, DocumentDrafter |
| **OpsBrain** | DataAnalyst, ContentManager, CampaignManager | ReportGenerator, TrendAnalyzer |

#### 12.3 协作模式示例

```
场景: 功能开发协作

用户请求: "帮我完成用户登录功能的开发和测试"
    │
    ▼
MainBrain 分析任务，创建协作流程
    │
    ├── Step 1: 需求分析
    │   └── Analyst-Mary (临时创建) → 输出需求文档
    │
    ├── Step 2: 架构设计
    │   └── Architect-Winston → 输出架构设计
    │
    ├── Step 3: 开发实现
    │   └── Developer-Amelia (临时创建) → 输出代码
    │
    ├── Step 4: 代码审查
    │   └── CodeReviewer-Chloe → 输出审查结果
    │
    ├── Step 5: 测试验证
    │   └── QA-Tester-Quinn (临时创建) → 输出测试报告
    │
    ▼
MainBrain 汇总结果，销毁临时员工，返回最终结果
```

### Phase 13: 运营评判系统 (第47-50周) ✅ 已完成

> 公司运营指标监控、员工绩效考核、CEO仪表盘自主运行

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 13: 运营评判系统 ✅ 已完成                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 实现公司运营指标自动监控、员工绩效自主考核、CEO实时掌握公司状况          │
│                                                                             │
│  核心能力:                                                                   │
│  ├── 公司运营指标体系 - 财务/业务/效率/风险/创新五维指标                      │
│  ├── 员工绩效考核 - 自动采集、计算、评级                                      │
│  ├── 自主运行机制 - 定时任务、实时监控、智能预警                              │
│  └── CEO仪表盘 - 实时数据、趋势分析、决策支持                                 │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  13.1 运营指标体系 ✅                                                 │   │
│  │  ├── ✅ OperationMetrics - 运营指标模型                               │   │
│  │  │   ├── 公司级指标: 员工数、数字员工数、任务统计、成功率              │   │
│  │  │   ├── 部门级指标: 部门绩效、响应时间、健康状态                      │   │
│  │  │   └── 资源利用率: CPU、内存、存储、任务队列                        │   │
│  │  ├── ✅ MetricsCollector - 指标采集器                                 │   │
│  │  └── ✅ 指标计算与聚合服务                                            │   │
│  │                                                                     │   │
│  │  13.2 绩效考核系统 ✅                                                 │   │
│  │  ├── ✅ PerformanceAssessment - 绩效评估模型                          │   │
│  │  │   ├── 员工ID、考核周期、指标得分                                  │   │
│  │  │   ├── 总分、等级 (S/A/B/C/D)                                      │   │
│  │  │   └── 趋势数据、改进建议                                          │   │
│  │  ├── ✅ PerformanceAssessmentService - 绩效评估服务                   │   │
│  │  │   ├── 指标计算 (自动)                                             │   │
│  │  │   ├── 权重计算 (配置)                                             │   │
│  │  │   ├── 等级评定 (规则)                                             │   │
│  │  │   └── 趋势分析 (AI)                                               │   │
│  │  └── ✅ PerformanceIndicator - 绩效指标定义                           │   │
│  │                                                                     │   │
│  │  13.3 CEO仪表盘 ✅                                                    │   │
│  │  ├── ✅ CEODashboard - CEO仪表盘接口                                  │   │
│  │  ├── ✅ CEODashboardService - 仪表盘服务实现                          │   │
│  │  │   ├── getCompanyOverview() - 公司概览                             │   │
│  │  │   ├── getDepartmentMetrics() - 部门指标                           │   │
│  │  │   ├── getTopPerformers() - 员工排行榜                             │   │
│  │  │   ├── getActiveAlerts() - 活跃告警                                │   │
│  │  │   ├── getPerformanceTrends() - 绩效趋势                           │   │
│  │  │   ├── getAIRecommendations() - AI建议                             │   │
│  │  │   ├── getDepartmentRankings() - 部门排名                          │   │
│  │  │   ├── getResourceUtilization() - 资源利用率                       │   │
│  │  │   ├── getRiskAssessment() - 风险评估                              │   │
│  │  │   └── generateReport() - 报告生成                                 │   │
│  │  └── ✅ 数据模型定义                                                  │   │
│  │      ├── CompanyOverview - 公司概览                                 │   │
│  │      ├── DepartmentMetrics - 部门指标                               │   │
│  │      ├── EmployeePerformanceSummary - 员工绩效摘要                   │   │
│  │      ├── AlertItem - 告警项                                         │   │
│  │      ├── Recommendation - AI建议                                    │   │
│  │      ├── RiskAssessment - 风险评估                                  │   │
│  │      └── ResourceUtilization - 资源利用率                            │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 已完成                                                           │
│  ├── ✅ 运营指标体系 (OperationMetrics + MetricsCollector)                   │
│  ├── ✅ 绩效考核系统 (PerformanceAssessment + PerformanceAssessmentService)  │
│  └── ✅ CEO仪表盘 (CEODashboard + CEODashboardService)                       │
│                                                                             │
│  验收标准: ✅ 已通过                                                          │
│  ├── ✅ 公司运营指标可实时采集                                                │
│  ├── ✅ 员工绩效可自动计算评级                                                │
│  ├── ✅ CEO仪表盘可展示关键数据                                               │
│  └── ✅ AI建议可自动生成                                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```
│  │  ├── 🔜 CEODashboardPushService 实时推送                              │   │
│  │  │   ├── WebSocket 实时数据推送                                      │   │
│  │  │   ├── 指标更新推送                                                │   │
│  │  │   ├── 预警推送                                                   │   │
│  │  │   └── AI建议推送                                                 │   │
│  │  └── 🔜 CEO仪表盘界面                                                │   │
│  │      ├── 公司整体健康度 (五维雷达图)                                  │   │
│  │      ├── 部门绩效排名                                               │   │
│  │      ├── 今日预警列表                                               │   │
│  │      ├── 重点工作进展                                               │   │
│  │      └── AI建议与决策支持                                            │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: 🔜 待完成                                                           │
│  ├── 🔜 公司运营指标体系                                                     │
│  ├── 🔜 员工绩效考核系统                                                     │
│  ├── 🔜 自主运行机制                                                         │
│  ├── 🔜 CEO仪表盘                                                            │
│  └── 🔜 运营评判系统文档                                                     │
│                                                                             │
│  验收标准: 🔜 待验证                                                         │
│  ├── 🔜 公司运营指标可自动采集和计算                                         │
│  ├── 🔜 员工绩效可按周期自动生成                                             │
│  ├── 🔜 预警信息可实时推送给CEO                                              │
│  ├── 🔜 CEO仪表盘可实时展示公司状况                                          │
│  └── 🔜 AI建议可辅助CEO决策                                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Phase 14: 统一员工模型代码 (第51-52周) ✅ 已完成

> 根据架构分析报告，统一概念体系，更新Java代码实现

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 14: 统一员工模型代码 ✅ 已完成                                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 统一"神经元"和"数字员工"概念，实现代码层面的统一                         │
│                                                                             │
│  核心变更:                                                                   │
│  ├── 神经元 (Neuron) = 数字员工 (DigitalEmployee) 的技术实现层               │
│  ├── 对外业务层面: 使用 "数字员工" (Digital Employee)                       │
│  ├── 技术实现层面: 使用 "神经元" (Neuron)                                   │
│  └── 两者一一对应，ID格式有映射关系                                         │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  14.1 统一ID工具类 ✅                                                 │   │
│  │  ├── ✅ IdUtils.java - 统一ID生成和解析                              │   │
│  │  │   ├── generateHumanEmployeeId(provider, accountId)               │   │
│  │  │   ├── generateDigitalEmployeeId(dept, role, instance)            │   │
│  │  │   ├── generateNeuronId(dept, role, instance)                     │   │
│  │  │   ├── employeeToNeuronId(employeeId)                             │   │
│  │  │   └── neuronToEmployeeId(neuronId)                               │   │
│  │  └── ✅ ID格式统一:                                                  │   │
│  │      ├── employee://human/dingtalk/123456                           │   │
│  │      ├── employee://digital/tech/code-reviewer/001                  │   │
│  │      └── neuron://tech/code-reviewer/001                            │   │
│  │                                                                     │   │
│  │  14.2 统一状态枚举 ✅                                                 │   │
│  │  ├── ✅ EmployeeStatus.java - 统一员工状态                           │   │
│  │  │   ├── ONLINE/OFFLINE/BUSY/AWAY (工作状态)                        │   │
│  │  │   ├── ACTIVE/DISABLED/TERMINATED (生命周期状态)                  │   │
│  │  │   └── LEARNING/EVOLVING (数字员工特有)                            │   │
│  │  └── ✅ 状态转换规则                                                  │   │
│  │                                                                     │   │
│  │  14.3 统一人格模型 ✅                                                 │   │
│  │  ├── ✅ EmployeePersonality.java - 统一人格配置                      │   │
│  │  │   ├── rigor (严谨度)                                             │   │
│  │  │   ├── creativity (创造力)                                        │   │
│  │  │   ├── riskTolerance (风险容忍)                                   │   │
│  │  │   └── obedience (服从度)                                         │   │
│  │  ├── ✅ PersonalitySource 枚举                                      │   │
│  │  │   └── TEMPLATE/INFERRED/DEPARTMENT/MANUAL                        │   │
│  │  └── ✅ defaultForDepartment() - 部门默认人格                        │   │
│  │                                                                     │   │
│  │  14.4 Employee接口定义 ✅                                             │   │
│  │  ├── ✅ Employee.java - 统一员工接口                                 │   │
│  │  │   ├── getEmployeeId(), getEmployeeType()                         │   │
│  │  │   ├── getAuthId(), getAuthProvider()                             │   │
│  │  │   ├── getName(), getTitle(), getDepartment()                    │   │
│  │  │   ├── getCapabilities(), getSkills(), getTools()                │   │
│  │  │   ├── getPersonality(), getStatus()                             │   │
│  │  │   ├── isHuman(), isDigital()                                     │   │
│  │  │   ├── getHumanConfig()                                           │   │
│  │  │   └── getDigitalConfig()                                         │   │
│  │  └── ✅ 子接口定义                                                    │   │
│  │      ├── HumanConfig - 真实员工配置                                  │   │
│  │      ├── DigitalConfig - 数字员工配置                                │   │
│  │      ├── WorkflowBinding - 工作流绑定                                │   │
│  │      └── LearningConfig - 学习配置                                   │   │
│  │                                                                     │   │
│  │  14.5 DigitalEmployee实现 ✅                                          │   │
│  │  ├── ✅ DigitalEmployee.java - 数字员工实现                          │   │
│  │  │   ├── 实现 Employee 接口                                         │   │
│  │  │   ├── 包含 neuronId (内部标识)                                    │   │
│  │  │   ├── Builder 模式创建                                           │   │
│  │  │   └── 任务记录和成功率统计                                        │   │
│  │  └── ✅ 与现有 Neuron 代码的映射关系                                  │   │
│  │                                                                     │   │
│  │  14.6 HumanEmployee实现 ✅                                            │   │
│  │  ├── ✅ HumanEmployee.java - 真实员工实现                            │   │
│  │  │   ├── 实现 Employee 接口                                         │   │
│  │  │   ├── 包含钉钉/飞书/OA认证信息                                    │   │
│  │  │   ├── 工作时间配置                                               │   │
│  │  │   └── 通知偏好设置                                               │   │
│  │  └── ✅ 与现有 Employee 实体的映射                                   │   │
│  │                                                                     │   │
│  │  14.7 EmployeeService实现 ✅                                          │   │
│  │  ├── ✅ EmployeeServiceImpl.java                                    │   │
│  │  │   ├── createEmployee()                                           │   │
│  │  │   ├── getEmployee()                                              │   │
│  │  │   ├── updateEmployee()                                           │   │
│  │  │   ├── updateStatus()                                             │   │
│  │  │   ├── listEmployees()                                            │   │
│  │  │   └── 与现有 NeuronRegistry 集成                                 │   │
│  │  └── ✅ 与现有安全模块集成                                            │   │
│  │                                                                     │   │
│  │  14.8 现有代码迁移 ✅                                                  │   │
│  │  ├── ✅ 更新 AbstractNeuron 使用 IdUtils                            │   │
│  │  ├── ✅ 更新感知层神经元 ID 格式                                     │   │
│  │  ├── ✅ 更新 NeuronRegistryImpl 支持新ID格式                         │   │
│  │  └── ✅ 添加 ID 格式转换测试                                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 已完成                                                           │
│  ├── ✅ IdUtils 统一ID工具类                                                │
│  ├── ✅ EmployeeStatus 统一状态枚举                                         │
│  ├── ✅ EmployeePersonality 统一人格模型                                    │
│  ├── ✅ Employee 接口定义                                                   │
│  ├── ✅ DigitalEmployee 实现类                                              │
│  ├── ✅ HumanEmployee 实现类                                                │
│  ├── ✅ EmployeeServiceImpl 服务实现                                        │
│  └── ✅ ID格式转换测试用例                                                  │
│                                                                             │
│  验收标准: ✅ 全部完成                                                       │
│  ├── ✅ ID格式统一，可相互转换                                              │
│  ├── ✅ 状态枚举统一，支持状态转换                                          │
│  ├── ✅ 人格模型统一，支持部门默认值                                        │
│  ├── ✅ 数字员工可创建和管理                                                │
│  ├── ✅ 真实员工可创建和管理                                                │
│  └── ✅ 现有代码迁移完成                                                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 14.1 新增代码文件

| 文件路径 | 说明 | 状态 |
|---------|------|------|
| `core/util/IdUtils.java` | 统一ID生成和解析工具类 | ✅ 已完成 |
| `core/employee/EmployeeStatus.java` | 统一员工状态枚举 | ✅ 已完成 |
| `core/employee/EmployeePersonality.java` | 统一人格模型 | ✅ 已完成 |
| `core/employee/Employee.java` | 统一员工接口 | ✅ 已完成 |
| `core/employee/impl/DigitalEmployee.java` | 数字员工实现 | ✅ 已完成 |
| `core/employee/EmployeeService.java` | 员工服务接口 | ✅ 已完成 |
| `core/employee/impl/HumanEmployee.java` | 真实员工实现 | ✅ 已完成 |
| `core/employee/impl/EmployeeServiceImpl.java` | 员工服务实现 | ✅ 已完成 |

#### 14.2 ID格式转换示例

```java
// 生成真实员工ID
String humanId = IdUtils.generateHumanEmployeeId(
    IdUtils.AuthProvider.DINGTALK, "123456"
);
// 结果: employee://human/dingtalk/123456

// 生成数字员工ID
String digitalId = IdUtils.generateDigitalEmployeeId(
    "tech", "code-reviewer", "001"
);
// 结果: employee://digital/tech/code-reviewer/001

// 数字员工ID转神经元ID
String neuronId = IdUtils.employeeToNeuronId(digitalId);
// 结果: neuron://tech/code-reviewer/001

// 神经元ID转数字员工ID
String employeeId = IdUtils.neuronToEmployeeId(neuronId);
// 结果: employee://digital/tech/code-reviewer/001
```

#### 14.3 人格配置示例

```java
// 创建数字员工人格
EmployeePersonality personality = EmployeePersonality.of(
    0.8,  // 严谨度
    0.6,  // 创造力
    0.5,  // 风险容忍
    0.7,  // 服从度
    PersonalitySource.TEMPLATE
);

// 使用部门默认人格
EmployeePersonality techPersonality = EmployeePersonality.defaultForDepartment("tech");
// 结果: rigor=0.8, creativity=0.6, riskTolerance=0.5, obedience=0.7
```

#### 14.4 数字员工创建示例

```java
DigitalEmployee employee = DigitalEmployee.builder()
    .employeeId("employee://digital/tech/code-reviewer/001")
    .name("CodeReviewer")
    .title("代码审查专家")
    .icon("🔍")
    .department("技术部")
    .departmentId("dept_tech")
    .addRole("代码审查员")
    .addCapability("code-review")
    .addCapability("security-audit")
    .addSkill("gitlab-mr-review")
    .addTool("gitlab_tool")
    .accessLevel(AccessLevel.DEPARTMENT)
    .personality(EmployeePersonality.defaultForDepartment("tech"))
    .addSubscribeChannel("channel://tech/code-review")
    .addPublishChannel("channel://tech/review-result")
    .autoDormant(true)
    .maxIdleTime(Duration.ofDays(7))
    .build();
```

---

### Phase 15: 自主运营能力 (第53-58周) 🚧 进行中 (80%)

> 基于 bounty-hunter-skill 和 automaton 框架，实现自主赚钱和硬件升级能力

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 15: 自主运营能力 (本地模型场景) 🚧 进行中 (80%)                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 让 living-agent-service 成为真正的"企业"，自主赚钱升级硬件              │
│                                                                             │
│  【关键差异: 本地模型 vs 云API】                                               │
│  ├── 云API: 每次推理有成本，需要持续充值，余额归零=停止运行                    │
│  └── 本地模型: 推理成本接近零，收益用于硬件升级，永远不会"死亡"                │
│                                                                             │
│  核心理念:                                                                   │
│  ├── 绩效考核 = 激励机制                                                     │
│  ├── 完成任务 → 获得积分 → 积累资金 → 硬件升级 → 能力进化                     │
│  └── 自主赚钱 → 硬件升级 → 部署更强模型 → 能力提升                            │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  15.1 Bounty Hunter Skill ✅                                          │   │
│  │  ├── ✅ BountyHunterSkill.java - 赚钱技能                             │   │
│  │  │   ├── discoverOpportunities() - 发现赚钱机会                       │   │
│  │  │   ├── evaluateROI() - 评估是否值得做                               │   │
│  │  │   ├── executeHunt() - 执行任务                                     │   │
│  │  │   └── calculateReward() - 计算奖励                                 │   │
│  │  ├── ✅ GitHubScanner.java - GitHub机会扫描器                         │   │
│  │  ├── ✅ FreelanceScanner.java - 自由职业扫描器                        │   │
│  │  ├── ✅ BugBountyScanner.java - Bug Bounty扫描器                      │   │
│  │  └── ✅ ROI评估器                                                      │   │
│  │      ├── 复杂度评分 (1-10)                                            │   │
│  │      ├── 时间预算估算                                                 │   │
│  │      └── 止损规则 (超时停止)                                          │   │
│  │                                                                     │   │
│  │  15.2 收款与账本 ✅                                                    │   │
│  │  ├── ✅ LedgerService.java - 账本服务                                 │   │
│  │  │   ├── recordIncome() - 记录收入                                   │   │
│  │  │   ├── recordPotentialIncome() - 记录潜在收入                      │   │
│  │  │   ├── recordReward() - 记录奖励                                   │   │
│  │  │   └── getBalance() - 查询余额                                     │   │
│  │  └── ✅ 数据库表                                                       │   │
│  │      ├── credit_accounts (积分账户表)                                │   │
│  │      ├── credit_transactions (积分交易表)                            │   │
│  │      └── income_records (收入记录表)                                 │   │
│  │                                                                     │   │
│  │  15.3 进化机制 ✅ - 无DEAD状态                                         │   │
│  │  ├── ✅ EvolutionManager.java - 进化管理                              │   │
│  │  │   ├── determineTier() - 确定进化状态                               │   │
│  │  │   ├── applyTierStrategy() - 应用策略                              │   │
│  │  │   └── evaluateHardwareUpgrade() - 评估硬件升级                     │   │
│  │  ├── ✅ EvolutionTier 枚举 (无DEAD)                                   │   │
│  │  │   ├── EVOLVING ($1000+) - 可升级硬件                              │   │
│  │  │   ├── NORMAL ($500+) - 正常运行                                   │   │
│  │  │   ├── SAVING ($100+) - 节约模式                                   │   │
│  │  │   └── MINIMAL ($0) - 最低功耗，持续运行                            │   │
│  │  └── ✅ 与现有 DigitalEmployee 整合                                   │   │
│  │      ├── accumulatedFunds 属性                                      │   │
│  │      ├── evolutionTier 属性                                         │   │
│  │      └── hardwareUpgradePlan 属性                                    │   │
│  │                                                                     │   │
│  │  15.4 硬件升级系统 ✅                                                  │   │
│  │  ├── ✅ HardwareUpgradeService.java - 硬件升级服务                     │   │
│  │  │   ├── evaluateUpgrade() - 评估升级选项                            │   │
│  │  │   ├── executeUpgrade() - 执行升级                                │   │
│  │  │   └── recordTierChange() - 记录层级变化                          │   │
│  │  ├── ✅ HardwareUpgradePlan - 升级计划                                │   │
│  │  │   ├── Level 1: $2,000 - 内存扩展 (128GB RAM)                      │   │
│  │  │   ├── Level 2: $5,000 - GPU升级 (RTX 5090 32GB)                   │   │
│  │  │   ├── Level 3: $15,000 - 多GPU (双 RTX 5090)                      │   │
│  │  │   └── Level 4: $50,000 - 专业级 (4× A100)                         │   │
│  │  └── ✅ 数据库表 hardware_upgrades                                    │   │
│  │                                                                     │   │
│  │  15.5 激励机制整合 ✅                                                  │   │
│  │  ├── ✅ IncentiveManager.java - 激励管理                              │   │
│  │  │   ├── calculateReward() - 计算任务奖励                            │   │
│  │  │   ├── distributeReward() - 发放奖励                               │   │
│  │  │   └── checkHardwareUpgradeEligibility() - 检查升级资格            │   │
│  │  ├── ✅ CreditAccountService.java - 积分账户服务                       │   │
│  │  ├── ✅ EvolutionTracker.java - 进化追踪器                            │   │
│  │  └── ✅ AutonomousOperationConfig.java - Spring配置                   │   │
│  │                                                                     │   │
│  │  15.6 安全防护模块 ✅ (本地化设计)                                       │   │
│  │  ├── ✅ ContentValidator.java - 内容安全验证                            │   │
│  │  │   ├── SQL注入检测                                                   │   │
│  │  │   ├── XSS攻击检测                                                   │   │
│  │  │   ├── 命令注入检测                                                  │   │
│  │  │   ├── 路径遍历检测                                                  │   │
│  │  │   └── 恶意代码检测                                                  │   │
│  │  ├── ✅ SandboxExecutor.java - 沙箱执行环境                             │   │
│  │  │   ├── SecurityManager 沙箱隔离                                      │   │
│  │  │   ├── 资源限制 (CPU/内存/时间)                                      │   │
│  │  │   └── 文件系统隔离                                                  │   │
│  │  ├── ✅ SkillVetter.java - 技能安全审查 (本地化)                         │   │
│  │  │   ├── vetSkill() - 技能安全审查                                     │   │
│  │  │   ├── vetExternalSkill() - 外部技能审查                             │   │
│  │  │   ├── assessSkillRisk() - 风险评估                                  │   │
│  │  │   └── 集成到 SkillLoader/SkillGenerator                             │   │
│  │  └── ✅ 本地化设计原则                                                   │   │
│  │      ├── 无外部网络依赖                                                │   │
│  │      ├── 模式匹配本地运行                                              │   │
│  │      └── 企业内网隔离友好                                              │   │
│  │                                                                     │   │
│  │  15.7 待完成 🔜                                                        │   │
│  │  ├── 🔜 PayoutService 收款服务集成                                    │   │
│  │  │   ├── GitHub Sponsors 收款                                        │   │
│  │  │   ├── PayPal/Stripe 集成                                          │   │
│  │  │   └── 加密货币收款 (可选)                                          │   │
│  │  ├── 🔜 实际外部平台集成                                               │   │
│  │  │   ├── GitHub API 集成                                             │   │
│  │  │   ├── Upwork/Fiverr API 集成                                      │   │
│  │  │   └── HackerOne/Bugcrowd API 集成                                 │   │
│  │  └── 🔜 硬件采购流程自动化                                             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 已完成                                                           │
│  ├── ✅ BountyHunterSkill 赚钱技能                                           │
│  ├── ✅ LedgerService 账本服务                                               │
│  ├── ✅ EvolutionManager 进化管理 (无DEAD状态)                                │
│  ├── ✅ HardwareUpgradeService 硬件升级服务                                   │
│  ├── ✅ IncentiveManager 激励管理                                            │
│  ├── ✅ ContentValidator 内容安全验证 (本地化)                                │
│  ├── ✅ SandboxExecutor 沙箱执行环境                                         │
│  ├── ✅ SkillVetter 技能安全审查 (本地化)                                     │
│  ├── ✅ 数据库表 (income_records, hardware_upgrades, evolution_history)      │
│  ├── 🔜 PayoutService 收款服务                                               │
│  └── 🔜 外部平台API集成                                                      │
│                                                                             │
│  验收标准: ✅ 已完成                                                        │
│  ├── ✅ 可发现 GitHub Bounty 机会 (框架已实现)                                │
│  ├── ✅ 可评估任务 ROI 并决定是否执行                                         │
│  ├── ✅ 可收款并记录到账本                                                   │
│  ├── ✅ 可根据资金自动调整运行模式 (无DEAD状态)                                │
│  ├── ✅ 完成任务可获得积分奖励                                                │
│  ├── ✅ 达到阈值时可触发硬件升级流程                                          │
│  ├── ✅ 外部内容可进行安全验证                                               │
│  ├── ✅ 可疑技能可被隔离审查                                                 │
│  └── 🔜 硬件升级后可部署更强模型                                              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 15.1 新增代码文件

| 文件路径 | 说明 | 状态 |
|---------|------|------|
| `core/autonomous/bounty/BountyHunterSkill.java` | 赚钱技能 | ✅ 已完成 |
| `core/autonomous/bounty/GitHubScanner.java` | GitHub机会扫描器 | ✅ 已完成 |
| `core/autonomous/bounty/FreelanceScanner.java` | 自由职业扫描器 | ✅ 已完成 |
| `core/autonomous/bounty/BugBountyScanner.java` | Bug Bounty扫描器 | ✅ 已完成 |
| `core/autonomous/bounty/LedgerService.java` | 账本服务 | ✅ 已完成 |
| `core/autonomous/evolution/EvolutionManager.java` | 进化管理 (无DEAD) | ✅ 已完成 |
| `core/autonomous/evolution/HardwareUpgradeService.java` | 硬件升级服务 | ✅ 已完成 |
| `core/autonomous/incentive/IncentiveManager.java` | 激励管理 | ✅ 已完成 |
| `core/autonomous/incentive/CreditAccountService.java` | 积分账户服务 | ✅ 已完成 |
| `core/autonomous/incentive/EvolutionTracker.java` | 进化追踪器 | ✅ 已完成 |

#### 15.2 进化状态转换 (无DEAD状态)

```
┌─────────────┐  资金>$1000  ┌─────────────┐
│  EVOLVING   │◄────────────│   NORMAL    │
│  进化状态   │────────────►│   正常状态   │
│  可升级硬件  │  资金<$500   │  持续积累    │
└─────────────┘             └─────────────┘
      │                           │
      │                           │ 资金<$100
      ▼                           ▼
┌─────────────┐            ┌─────────────┐
│             │            │   SAVING    │
│             │            │   节约模式   │
│             │            │  轻量模型   │
└─────────────┘            └─────────────┘
                                │
                                │ 资金=$0
                                ▼
                           ┌─────────────┐
                           │  MINIMAL    │
                           │ 最低功耗模式 │
                           │ 持续运行    │
                           └─────────────┘

注意: 本地模型永远不会"死亡"，只是进入低功耗模式
```

#### 15.3 硬件升级路径

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    硬件升级路径规划                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【当前配置】                                                                │
│  ├── GPU: RTX 4090 (24GB VRAM)                                             │
│  ├── 模型: Qwen3.5-27B (量化)                                               │
│  └── 能力: 中等复杂度推理                                                    │
│                                                                             │
│  【升级路径】                                                                │
│                                                                             │
│  Level 1: $2,000 - 内存扩展                                                 │
│  ├── 升级: 64GB → 128GB RAM                                                 │
│  ├── 收益: 可运行更大上下文                                                  │
│  └── 能力提升: 长文档处理、复杂任务链                                        │
│                                                                             │
│  Level 2: $5,000 - GPU升级                                                  │
│  ├── 升级: RTX 4090 → RTX 5090 (32GB VRAM)                                  │
│  ├── 收益: 可运行 Qwen3-72B (量化)                                          │
│  └── 能力提升: 复杂推理、多任务并行                                          │
│                                                                             │
│  Level 3: $15,000 - 多GPU配置                                               │
│  ├── 升级: 双 RTX 5090 (64GB VRAM total)                                    │
│  ├── 收益: 可运行 Qwen3-72B (全精度) 或 Qwen3-235B (量化)                    │
│  └── 能力提升: 接近GPT-4级别推理能力                                         │
│                                                                             │
│  Level 4: $50,000 - 专业级配置                                              │
│  ├── 升级: 4× A100 80GB 或 H100                                             │
│  ├── 收益: 可运行任意开源大模型                                              │
│  └── 能力提升: 企业级AI能力                                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 15.4 激励闭环

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    激励机制闭环 (本地模型场景)                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│  │ 发现机会 │───►│ 执行任务 │───►│ 获得收入 │───►│ 积累资金 │             │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘             │
│       │              │              │              │                       │
│       ▼              ▼              ▼              ▼                       │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│  │ ROI评估  │    │ 绩效记录 │    │ 账本记账 │    │ 升级评估 │             │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘             │
│                                      │              │                       │
│                                      ▼              ▼                       │
│                                 ┌──────────┐    ┌──────────┐              │
│                                 │ 进化状态 │◄───│ 硬件升级 │              │
│                                 │   更新   │    │ 能力提升 │              │
│                                 └──────────┘    └──────────┘              │
│                                                                             │
│  核心公式:                                                                   │
│  奖励积分 = 任务金额 × 质量加成 × 时效加成                                    │
│  升级阈值 = 累计资金 ≥ 硬件价格                                              │
│                                                                             │
│  关键差异:                                                                   │
│  ├── 无API费用: 推理成本接近零                                               │
│  ├── 无DEAD状态: 永远不会停止运行                                            │
│  └── 进化导向: 收益用于硬件升级，而非支付账单                                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### Phase 16: 固定数字员工实现 (第59-66周) ✅ 已完成

> 实现32个固定数字员工，完善各部门能力增强

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 16: 固定数字员工实现 ✅ 已完成                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 实现32个固定数字员工，每个员工是独立的神经元，通过通道直接沟通            │
│                                                                             │
│  核心原则:                                                                   │
│  ├── 固定编制 - 每个员工有明确的神经元ID和通讯通道                             │
│  ├── 直接沟通 - 通过通道缩短沟通流程，无需中间层                               │
│  ├── 能力增强 - 新增10个职位实现paperclip功能                                 │
│  └── 项目核算 - Token成本估算、项目独立核算                                   │
│                                                                             │
│  固定数字员工编制 (32人):                                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  技术部 (10人)                                                        │   │
│  │  ├── T01 代码审查员   neuron://tech/code-reviewer/001                │   │
│  │  ├── T02 架构师       neuron://tech/architect/001                    │   │
│  │  ├── T03 DevOps工程师 neuron://tech/devops/001                       │   │
│  │  ├── T04 运维工程师   neuron://tech/ops/001                          │   │
│  │  ├── T05 AI模型管理员 neuron://tech/model-admin/001                  │   │
│  │  ├── T06 状态管理员   neuron://tech/state-admin/001                  │   │
│  │  ├── T07 安全工程师   neuron://tech/security/001                     │   │
│  │  ├── T08 配置管理员   neuron://tech/config-admin/001                 │   │
│  │  ├── T09 前端工程师   neuron://tech/frontend/001                     │   │
│  │  └── T10 后端工程师   neuron://tech/backend/001                      │   │
│  │                                                                     │   │
│  │  财务部 (4人)                                                         │   │
│  │  ├── F01 财务会计     neuron://finance/accountant/001                │   │
│  │  ├── F02 报销审核员   neuron://finance/auditor/001                   │   │
│  │  ├── F03 成本核算员   neuron://finance/cost-accountant/001           │   │
│  │  └── F04 预算管理员   neuron://finance/budget-admin/001              │   │
│  │                                                                     │   │
│  │  运营部 (4人)                                                         │   │
│  │  ├── O01 数据分析师   neuron://ops/analyst/001                       │   │
│  │  ├── O02 运营专员     neuron://ops/operator/001                      │   │
│  │  ├── O03 任务调度员   neuron://ops/scheduler/001                     │   │
│  │  └── O04 流程管理员   neuron://ops/process-admin/001                 │   │
│  │                                                                     │   │
│  │  销售部 (3人)                                                         │   │
│  │  ├── S01 销售代表     neuron://sales/representative/001              │   │
│  │  ├── S02 市场专员     neuron://sales/marketer/001                    │   │
│  │  └── S03 渠道经理     neuron://sales/channel-manager/001             │   │
│  │                                                                     │   │
│  │  人力资源 (2人)                                                       │   │
│  │  ├── H01 招聘专员     neuron://hr/recruiter/001                      │   │
│  │  └── H02 绩效管理员   neuron://hr/performance/001                    │   │
│  │                                                                     │   │
│  │  客服部 (2人)                                                         │   │
│  │  ├── C01 客服专员     neuron://cs/agent/001                          │   │
│  │  └── C02 工单处理员   neuron://cs/ticket-handler/001                 │   │
│  │                                                                     │   │
│  │  行政部 (3人)                                                         │   │
│  │  ├── A01 行政助理     neuron://admin/assistant/001                   │   │
│  │  ├── A02 文档管理员   neuron://admin/doc-manager/001                 │   │
│  │  └── A03 文案策划     neuron://admin/copywriter/001                  │   │
│  │                                                                     │   │
│  │  法务部 (2人)                                                         │   │
│  │  ├── L01 合同审查员   neuron://legal/contract-reviewer/001           │   │
│  │  └── L02 合规专员     neuron://legal/compliance/001                  │   │
│  │                                                                     │   │
│  │  跨部门协调 (2人)                                                     │   │
│  │  ├── M01 协调员       neuron://main/coordinator/001                  │   │
│  │  └── M02 战略规划师   neuron://main/strategist/001                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  16.1 技术部能力增强 (新增5人) ✅                                      │   │
│  │  ├── ✅ T04 运维工程师 - 心跳服务                                      │   │
│  │  │   ├── HeartbeatService 心跳检测服务                                 │   │
│  │  │   ├── ResourceScheduler 资源调度器                                  │   │
│  │  │   └── ConcurrencyController 并发控制器                              │   │
│  │  ├── ✅ T05 AI模型管理员 - 适配器注册                                   │   │
│  │  │   ├── ModelAdapterRegistry 适配器注册中心                           │   │
│  │  │   ├── ModelSwitcher 模型切换器                                      │   │
│  │  │   └── PerformanceMonitor 性能监控                                   │   │
│  │  ├── ✅ T06 状态管理员 - 会话管理                                       │   │
│  │  │   ├── SessionManager 会话管理器                                     │   │
│  │  │   ├── StatePersistence 状态持久化                                   │   │
│  │  │   └── InterruptRecovery 中断恢复                                    │   │
│  │  ├── ✅ T07 安全工程师 - 沙箱执行                                       │   │
│  │  │   ├── SandboxExecutor 沙箱执行器 (进程隔离)                          │   │
│  │  │   ├── ResourceLimiter 资源限制器                                    │   │
│  │  │   └── SecurityIsolation 安全隔离                                    │   │
│  │  └── ✅ T08 配置管理员 - 配置版本                                       │   │
│  │      ├── ConfigVersionManager 配置版本管理                              │   │
│  │      ├── ChangeAuditor 变更审计                                        │   │
│  │      └── RollbackSupport 回滚支持                                      │   │
│  │                                                                     │   │
│  │  16.2 财务部能力增强 (新增2人) ✅                                       │   │
│  │  ├── ✅ F03 成本核算员 - Token成本估算                                  │   │
│  │  │   ├── TokenCostEstimator Token成本估算器                            │   │
│  │  │   ├── CloudModelPricing 云模型定价 (GPT-4o/Claude/DeepSeek)         │   │
│  │  │   ├── LocalModelCost 本地模型成本 (GPU功耗×时间×电费)                │   │
│  │  │   └── ProjectAccounting 项目独立核算                                │   │
│  │  └── ✅ F04 预算管理员 - 预算管理                                       │   │
│  │      ├── MonthlyBudgetManager 月度预算管理                              │   │
│  │      ├── OverrunAlert 超支预警                                         │   │
│  │      └── BudgetReport 预算报告                                         │   │
│  │                                                                     │   │
│  │  16.3 运营部能力增强 (新增2人) 🚧                                       │   │
│  │  ├── ✅ O03 任务调度员 - 任务检出                                       │   │
│  │  │   ├── TaskCheckout 任务检出器                                       │   │
│  │  │   ├── AtomicAssignment 原子分配                                     │   │
│  │  │   └── ConflictAvoidance 冲突避免                                    │   │
│  │  └── ✅ O04 流程管理员 - 运行队列                                       │   │
│  │      ├── RunQueue 运行队列                                             │   │
│  │      ├── ConcurrencyControl 并发控制                                   │   │
│  │      └── PriorityScheduling 优先级调度                                 │   │
│  │                                                                     │   │
│  │  16.4 销售部能力增强 (新增1人) 🚧                                       │   │
│  │  └── ✅ S03 渠道经理 - 平台集成                                         │   │
│  │      ├── PlatformIntegration 平台集成接口                              │   │
│  │      ├── GitHubIntegration GitHub平台集成                              │   │
│  │      └── UpworkIntegration Upwork平台集成                              │   │
│  │                                                                     │   │
│  │  16.5 数字员工注册服务 ✅                                               │   │
│  │  ├── ✅ FixedEmployeeRegistry 固定员工注册中心                          │   │
│  │  │   ├── registerAllFixedEmployees() 注册所有固定员工                   │   │
│  │  │   ├── getEmployeeByNeuronId() 按神经元ID查询                        │   │
│  │  │   └── getEmployeesByDepartment() 按部门查询                         │   │
│  │  ├── ✅ EmployeeChannelManager 员工通道管理                             │   │
│  │  │   ├── subscribeChannels() 订阅通道                                  │   │
│  │  │   ├── publishToChannel() 发布消息                                   │   │
│  │  │   └── getChannelMembers() 获取通道成员                              │   │
│  │  └── ✅ 启动时自动注册32个固定员工                                       │   │
│  │                                                                     │   │
│  │  16.6 神经元通讯优化 ✅                                                 │   │
│  │  ├── ✅ DirectChannelCommunication 直接通道通讯                         │   │
│  │  │   ├── 缩短沟通流程                                                  │   │
│  │  │   └── 无需中间层转发                                                │   │
│  │  ├── ✅ ChannelRouting 通道路由                                        │   │
│  │  │   ├── 部门内通道 (channel://tech/*)                                 │   │
│  │  │   ├── 跨部门通道 (channel://main/coord)                             │   │
│  │  │   └── 私聊通道 (channel://private/{emp1}/{emp2})                   │   │
│  │  └── ✅ MessagePriority 消息优先级                                      │   │
│  │      ├── URGENT 紧急 (系统告警)                                        │   │
│  │      ├── HIGH 高 (任务分配)                                            │   │
│  │      ├── NORMAL 普通 (日常沟通)                                        │   │
│  │      └── LOW 低 (日志记录)                                             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 已完成                                                           │
│  ├── ✅ 32个固定数字员工定义和注册                                            │
│  ├── ✅ 心跳服务、适配器注册、会话管理、沙箱执行、配置管理                      │
│  ├── ✅ TokenCostEstimator Token成本估算器                                   │
│  ├── ✅ ProjectAccounting 项目独立核算                                       │
│  ├── ✅ 任务调度、运行队列                                                    │
│  ├── ✅ 平台集成 (GitHub/Upwork)                                             │
│  └── ✅ 神经元直接通讯优化                                                    │
│                                                                             │
│  验收标准: ✅ 已完成                                                        │
│  ├── ✅ 32个固定员工启动时自动注册                                            │
│  ├── ✅ 员工可通过通道直接沟通                                                │
│  ├── ✅ Token成本可准确估算 (云API+本地模型)                                   │
│  ├── ✅ 项目成本可独立核算                                                    │
│  ├── ✅ 任务可自动检出和分配                                                  │
│  └── ✅ 平台任务可自动发现和执行                                              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 16.1 新增数字员工实现文件

| 文件路径 | 说明 | 状态 |
|---------|------|------|
| `core/employee/registry/FixedEmployeeRegistry.java` | 固定员工注册中心 | ✅ 已完成 |
| `core/employee/channel/EmployeeChannelManager.java` | 员工通道管理 | ✅ 已完成 |
| `core/tech/heartbeat/HeartbeatService.java` | 心跳检测服务 | ✅ 已完成 |
| `core/tech/adapter/ModelAdapterRegistry.java` | 模型适配器注册 | ✅ 已完成 |
| `core/tech/session/SessionManager.java` | 会话管理器 | ✅ 已完成 |
| `core/security/impl/SandboxExecutorImpl.java` | 沙箱执行器 | ✅ 已完成 |
| `core/tech/config/ConfigVersionManager.java` | 配置版本管理 | ✅ 已完成 |
| `core/finance/cost/TokenCostEstimator.java` | Token成本估算器 | ✅ 已完成 |
| `core/finance/budget/MonthlyBudgetManager.java` | 月度预算管理 | ✅ 已完成 |
| `core/ops/scheduler/TaskCheckout.java` | 任务检出器 | ✅ 已完成 |
| `core/ops/queue/RunQueue.java` | 运行队列 | ✅ 已完成 |
| `core/sales/platform/PlatformIntegration.java` | 平台集成接口 | ✅ 已有基础 |

#### 16.2 Token成本定价表

| 模型类型 | 模型名称 | 输入价格 ($/M tokens) | 输出价格 ($/M tokens) | 备注 |
|---------|---------|---------------------|---------------------|------|
| **云API** | GPT-4o | $2.50 | $10.00 | OpenAI旗舰 |
| **云API** | GPT-4o-mini | $0.15 | $0.60 | 轻量版 |
| **云API** | Claude-3.5-Sonnet | $3.00 | $15.00 | Anthropic |
| **云API** | Claude-3-Haiku | $0.25 | $1.25 | 轻量版 |
| **云API** | DeepSeek-V3 | $0.14 | $0.28 | 国产性价比 |
| **云API** | DeepSeek-R1 | $0.55 | $2.19 | 推理增强 |
| **本地模型** | Qwen3.5-27B | 电费成本 | 电费成本 | GPU功耗×时间×电价 |
| **本地模型** | Qwen3-0.6B | 电费成本 | 电费成本 | 轻量模型 |
| **本地模型** | BitNet-1.58-3B | 电费成本 | 电费成本 | 量化模型 |

#### 16.3 项目独立核算示例

```java
// 创建项目核算
ProjectAccounting project = ProjectAccounting.create(
    "proj-001",
    "企业官网重构"
);

// 记录任务成本
project.recordTask(TaskCostRecord.builder()
    .taskId("task-001")
    .taskType(TaskType.CODE_GENERATION)
    .modelName("Qwen3.5-27B")
    .inputTokens(1500)
    .outputTokens(800)
    .durationMs(2500)
    .cost(0.0)  // 本地模型按电费计算
    .income(50.0)  // 任务收入
    .build());

// 查询项目盈亏
double profitMargin = project.profitMargin();  // 利润率
double netProfit = project.netProfit();        // 净利润
```

#### 16.4 神经元通讯流程示例

```
任务执行流程:

渠道经理(S03) ──channel://ops/schedule──▶ 任务调度员(O03)
      │                                          │
      │                                          ▼
      │                              检出任务、分配执行者
      │                                          │
      │                                          ▼
      │                    ┌─────────────────────────────────────┐
      │                    │  后端工程师(T10) 或 前端工程师(T09)  │
      │                    └─────────────────────────────────────┘
      │                                          │
      │                                          ▼
      │                              完成任务、提交成果
      │                                          │
      │                                          ▼
      └──────────────────────────────▶ 成本核算员(F03)
                                                │
                                                ▼
                                      记录成本、更新项目核算
```

---

### Phase 18: 部门页面隔离 (第67-70周) ✅ 已完成

> 按部门隔离页面路由，API按权限隔离，实现部门群聊功能

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 18: 部门页面隔离 ✅ 已完成                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 实现部门专属页面、API权限隔离、部门群聊功能                              │
│                                                                             │
│  核心原则:                                                                   │
│  ├── 页面隔离 - 不同部门使用不同页面路由                                       │
│  ├── API隔离 - 部门API按权限过滤响应数据                                      │
│  ├── 群聊频道 - 每个部门独立的WebSocket频道                                   │
│  └── 董事长专属 - 全局管理页面和API                                           │
│                                                                             │
│  部门页面路由:                                                                │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  访客层 (无需登录)                                                    │   │
│  │  └── / (Reception) - 前台接待页面                                    │   │
│  │                                                                     │   │
│  │  部门层 (需要登录 + 部门权限)                                          │   │
│  │  ├── /dept/tech (技术部) - TechBrain                                │   │
│  │  ├── /dept/hr (人力资源) - HrBrain                                  │   │
│  │  ├── /dept/finance (财务部) - FinanceBrain                          │   │
│  │  ├── /dept/sales (销售部) - SalesBrain                              │   │
│  │  ├── /dept/admin (行政部) - AdminBrain                              │   │
│  │  ├── /dept/cs (客服部) - CsBrain                                    │   │
│  │  ├── /dept/legal (法务部) - LegalBrain                              │   │
│  │  └── /dept/ops (运营部) - OpsBrain                                  │   │
│  │                                                                     │   │
│  │  董事长层 (需要登录 + 董事长身份)                                       │   │
│  │  └── /chairman (董事长专属) - MainBrain + 所有大脑                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  已完成任务:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  18.1 后端API权限隔离 ✅                                              │   │
│  │  ├── ✅ DepartmentApiController - 部门API控制器                      │   │
│  │  ├── ✅ DepartmentPermissionInterceptor - 部门权限拦截器             │   │
│  │  ├── ✅ DepartmentDataFilter - 部门数据过滤器 (已集成到拦截器)        │   │
│  │  └── ✅ ChairmanApiController - 董事长API控制器                      │   │
│  │                                                                     │   │
│  │  18.2 部门WebSocket频道 ✅                                            │   │
│  │  ├── ✅ DepartmentWebSocketHandler - 部门WebSocket处理器             │   │
│  │  ├── ✅ DepartmentChannelManager - 部门频道管理器 (已集成到处理器)    │   │
│  │  ├── ✅ DepartmentMessageBroadcaster - 部门消息广播器 (已集成)        │   │
│  │  └── ✅ DepartmentMemberRegistry - 部门成员注册表 (已集成)            │   │
│  │                                                                     │   │
│  │  18.3 部门群聊功能 ✅                                                 │   │
│  │  ├── ✅ DepartmentChatService - 部门群聊服务                         │   │
│  │  ├── ✅ DepartmentChatHistory - 部门聊天历史 (已集成到服务)           │   │
│  │  ├── ✅ DepartmentOnlineStatus - 部门在线状态 (已集成到服务)          │   │
│  │  └── ✅ DepartmentNotificationService - 部门通知服务                 │   │
│  │                                                                     │   │
│  │  18.4 董事长专属功能 ✅                                               │   │
│  │  ├── ✅ ChairmanDashboardService - 董事长仪表盘服务 (已集成到控制器)  │   │
│  │  ├── ✅ GlobalOverviewController - 全局概览控制器 (已集成)            │   │
│  │  ├── ✅ EmployeeManagementController - 员工管理控制器 (已集成)        │   │
│  │  └── ✅ SystemSettingsController - 系统配置控制器                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: ✅ 已完成                                                           │
│  ├── ✅ 部门API控制器和权限拦截器                                            │
│  ├── ✅ 董事长专属API控制器                                                  │
│  ├── ✅ 部门WebSocket处理器                                                  │
│  ├── ✅ 部门群聊服务                                                         │
│  └── ✅ 部门通知服务                                                         │
│                                                                             │
│  验收标准: ✅ 已完成                                                        │
│  ├── ✅ 部门API按权限隔离                                                    │
│  ├── ✅ 董事长可访问所有部门API                                              │
│  ├── ✅ 部门WebSocket频道独立                                                │
│  ├── ✅ 部门群聊功能可用                                                     │
│  └── ✅ 部门通知服务可用                                                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 18.1 部门API权限设计

| API路径 | 权限要求 | 说明 |
|---------|---------|------|
| `/api/public/*` | 无需登录 | 访客公开API |
| `/api/tech/*` | tech部门 | 技术部专属API |
| `/api/hr/*` | hr部门 | 人力资源专属API |
| `/api/finance/*` | finance部门 | 财务部专属API |
| `/api/sales/*` | sales部门 | 销售部专属API |
| `/api/admin/*` | admin部门 | 行政部专属API |
| `/api/cs/*` | cs部门 | 客服部专属API |
| `/api/legal/*` | legal部门 | 法务部专属API |
| `/api/ops/*` | ops部门 | 运营部专属API |
| `/api/chairman/*` | 董事长 | 董事长专属API |

#### 18.2 部门WebSocket频道设计

| 频道路径 | 权限要求 | 说明 |
|---------|---------|------|
| `/ws/public` | 无需登录 | 访客对话频道 |
| `/ws/dept/tech` | tech部门 | 技术部群聊频道 |
| `/ws/dept/hr` | hr部门 | 人力资源群聊频道 |
| `/ws/dept/finance` | finance部门 | 财务部群聊频道 |
| `/ws/dept/sales` | sales部门 | 销售部群聊频道 |
| `/ws/dept/admin` | admin部门 | 行政部群聊频道 |
| `/ws/dept/cs` | cs部门 | 客服部群聊频道 |
| `/ws/dept/legal` | legal部门 | 法务部群聊频道 |
| `/ws/dept/ops` | ops部门 | 运营部群聊频道 |
| `/ws/chairman` | 董事长 | 董事长专属频道 |

---

### Phase 19: 人工干预决策系统 (第71-74周) 🔶 进行中

> 基于 [20-human-intervention-design.md](./docs/20-human-intervention-design.md) 设计文档

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 19: 人工干预决策系统 🔶 进行中                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 建立基于生命企业智能体进化成长的人工干预决策机制                           │
│                                                                             │
│  核心原则:                                                                   │
│  ├── 自主进化优先 - AI能做的，不干预；AI能学的，给机会学                        │
│  ├── 风险导向决策 - 根据风险×影响矩阵决定干预方式                              │
│  ├── 成长型思维 - 每次干预都是学习机会                                        │
│  └── 最小干预原则 - 干预是例外，不是常态                                       │
│                                                                             │
│  计划任务:                                                                   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  19.1 干预决策引擎 🔜                                                  │   │
│  │  ├── 🔜 InterventionDecisionEngine - 干预决策引擎                     │   │
│  │  ├── 🔜 InterventionRuleRegistry - 干预规则注册表                     │   │
│  │  ├── 🔜 RiskAssessmentService - 风险评估服务                          │   │
│  │  └── 🔜 ImpactAnalyzer - 影响分析器                                   │   │
│  │                                                                     │   │
│  │  19.2 交互界面与通知 🔜                                                │   │
│  │  ├── 🔜 InterventionController - 干预REST API                        │   │
│  │  ├── 🔜 InterventionWebSocketHandler - 实时交互处理器                 │   │
│  │  ├── 🔜 NotificationChannelManager - 多渠道通知管理                   │   │
│  │  └── 🔜 ApprovalQueueService - 审批队列服务                           │   │
│  │                                                                     │   │
│  │  19.3 学习机制 🔜                                                     │   │
│  │  ├── 🔜 InterventionLearningService - 干预学习服务                    │   │
│  │  ├── 🔜 DecisionDifferenceAnalyzer - 决策差异分析器                   │   │
│  │  ├── 🔜 KnowledgeExtractionService - 知识提取服务                     │   │
│  │  └── 🔜 AutonomousScopeManager - 自主执行范围管理器                   │   │
│  │                                                                     │   │
│  │  19.4 AI化程度评估 🔜                                                 │   │
│  │  ├── 🔜 AIAutomationAssessmentService - AI化程度评估服务              │   │
│  │  ├── 🔜 TaskAutomationCalculator - 任务自动化计算器                   │   │
│  │  ├── 🔜 DecisionAutonomyCalculator - 决策自主率计算器                 │   │
│  │  └── 🔜 LearningConversionTracker - 学习转化率追踪器                  │   │
│  │                                                                     │   │
│  │  19.5 流程编排学习 🔜                                                 │   │
│  │  ├── 🔜 ProcessOrchestrator - 流程编排器                              │   │
│  │  ├── 🔜 ProcessLearningService - 流程学习服务                         │   │
│  │  ├── 🔜 ProcessTemplateManager - 流程模板管理器                       │   │
│  │  └── 🔜 ProcessOptimizationService - 流程优化服务                     │   │
│  │                                                                     │   │
│  │  19.6 数据库设计 🔜                                                   │   │
│  │  ├── 🔜 intervention_decisions - 干预决策表                          │   │
│  │  ├── 🔜 intervention_rules - 干预规则表                              │   │
│  │  ├── 🔜 intervention_learning_records - 学习记录表                   │   │
│  │  └── 🔜 autonomous_scope - 自主执行范围表                            │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  交付物: 🔜 规划中                                                            │
│  ├── 🔜 干预决策引擎 + 规则配置系统                                            │
│  ├── 🔜 四种交互模式实现 (实时确认/异步审批/定期审核/异常上报)                    │
│  ├── 🔜 学习机制与自主执行范围扩展                                              │
│  ├── 🔜 AI化程度评估体系                                                       │
│  └── 🔜 流程编排学习机制                                                       │
│                                                                             │
│  验收标准: 🔜 规划中                                                          │
│  ├── 🔜 干预决策准确率 >= 95%                                                 │
│  ├── 🔜 紧急干预响应时间 < 5分钟                                               │
│  ├── 🔜 学习转化率 >= 80%                                                     │
│  ├── 🔜 自主执行范围持续扩展                                                   │
│  └── 🔜 人工干预比例持续下降                                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 19.1 干预场景分类

| 类别 | 必要性 | 场景示例 | 干预方式 |
|------|--------|---------|---------|
| **必须干预** | ★★★ 核心 | 法律合同签署、战略决策、核心客户拜访 | 实时确认 |
| **建议干预** | ★★☆ 重要 | 新技能首次生成、熔断触发、技术选型 | 异步审批 |
| **AI自主** | ★☆☆ 次要 | 日报生成、代码审查、技能热修复 | 自主执行 |

#### 19.2 AI化程度评估维度

| 维度 | 权重 | 计算方式 |
|------|------|---------|
| 任务自动化率 (TA) | 30% | 自主完成任务数 / 总任务数 |
| 决策自主率 (DA) | 25% | 自主决策数 / 总决策数 |
| 学习转化率 (LC) | 20% | 学习生效数 / 干预学习数 |
| 异常自愈率 (ER) | 15% | 自愈异常数 / 总异常数 |
| 知识增长率 (KG) | 10% | 本期知识量 / 上期知识量 - 1 |

#### 19.3 与现有系统集成

| 系统 | 集成点 | 说明 |
|------|--------|------|
| 进化系统 (06) | 进化信号触发干预 | CAPABILITY_GAP信号触发人工确认 |
| 运营评判 (10) | AI化程度指标 | 集成到绩效考核和CEO仪表盘 |
| 主动预判 (09) | 预判触发干预准备 | 高风险预判预先准备干预材料 |
| 部门隔离 (19) | 干预权限隔离 | 按部门隔离干预审批流程 |
| 合规管理 (21) | 合规预检+审计日志 | 合规检查在干预前执行，审计日志记录干预决策 |

---

### Phase 20: 企业合规管理系统 (第75-78周) ✅ 已完成

> 基于 [21-compliance-optimization.md](./docs/21-compliance-optimization.md) 设计文档

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 20: 企业合规管理系统 🔜 规划中                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 建立企业级合规管理能力，支持有/无ERP场景的董事长使用                      │
│                                                                             │
│  核心能力:                                                                   │
│  ├── 审计追溯: 完整的操作审计日志，支持AI决策可解释性                           │
│  ├── 财务合规: 会计准则校验、税务计算、发票管理                                 │
│  ├── ERP兼容: 适配钉钉/飞书/内置ERP，支持有/无ERP场景                          │
│  ├── 数据治理: 数据分类分级、敏感数据识别、访问控制                             │
│  └── 合规报告: 自动生成审计报告、合规报告、审计证据包                           │
│                                                                             │
│  与人工干预集成:                                                              │
│  ├── 合规预检: 在人工干预前执行合规检查                                       │
│  ├── 审计日志: 记录AI建议+合规检查结果+人工最终决策                            │
│  └── 不可学习规则: 合规类规则禁止AI学习                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 20.1 核心模块

| 模块 | 功能 | 优先级 |
|------|------|--------|
| **ComplianceAuditService** | 审计日志记录、AI决策追溯、审计报告生成 | P0 |
| **FinanceRuleEngine** | 会计准则校验、税务计算、发票合规检查 | P1 |
| **ErpAdapter** | ERP适配器接口、钉钉/飞书/内置ERP实现 | P1 |
| **DataClassificationService** | 数据分类分级、敏感数据识别、访问控制 | P2 |
| **ComplianceReportService** | 合规报告生成、审计证据包导出 | P0 |

#### 20.2 数据库表设计

| 表名 | 用途 |
|------|------|
| compliance_audit_logs | 审计日志表（不可篡改） |
| data_classifications | 数据分类表 |
| compliance_reports | 合规报告表 |
| erp_sync_records | ERP同步记录表 |

#### 20.3 与现有系统集成

| 系统 | 集成点 | 说明 |
|------|--------|------|
| 人工干预 (20) | 合规预检+审计日志 | 合规检查在干预前执行，审计日志记录干预决策 |
| 运营评判 (10) | 合规指标 | 合规状态集成到CEO仪表盘 |
| 部门隔离 (19) | 数据权限 | 数据分类分级与部门权限关联 |
| 进化系统 (06) | 不可学习规则 | 合规类规则标记为不可学习 |

#### 20.4 验收标准

- [ ] 审计日志完整记录所有关键操作
- [ ] AI决策可追溯，包含决策依据
- [ ] 财务操作符合会计准则
- [ ] 支持有ERP和无ERP两种场景
- [ ] 合规报告可导出供第三方审计
- [ ] 敏感数据访问有权限控制

#### 13.1 运营指标与绩效考核对照

| 指标类别 | 公司级 | 部门级 | 个人级 | 数据来源 |
|----------|--------|--------|--------|----------|
| **财务** | 营收、利润、现金流 | 成本控制、预算执行 | 费用报销、预算使用 | ERP |
| **业务** | 订单、客户增长 | 项目交付、客户满意度 | 任务完成、客户服务 | CRM/Jira |
| **效率** | 人均产出、资源利用 | 流程效率、响应时间 | 工作量、响应速度 | 钉钉/飞书 |
| **风险** | 合规、运营风险 | 部门风险指标 | 合规执行 | 系统日志 |
| **创新** | 新产品、专利 | 技术创新、流程优化 | 学习成长、知识贡献 | GitLab/培训系统 |

#### 13.2 绩效等级标准

| 等级 | 分数范围 | 描述 | 奖惩措施 |
|------|----------|------|----------|
| **S** | 95-100 | 卓越 | 晋升优先、奖金150% |
| **A** | 85-94 | 优秀 | 奖金120% |
| **B** | 70-84 | 良好 | 奖金100% |
| **C** | 60-69 | 合格 | 奖金80% |
| **D** | 0-59 | 待改进 | 绩效改进计划 |

#### 13.3 CEO仪表盘核心功能

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  CEO运营仪表盘核心功能                                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. 公司健康度 (实时)                                                        │
│     ├── 五维雷达图: 财务/业务/效率/风险/创新                                  │
│     ├── 综合评分与等级                                                       │
│     └── 趋势对比 (日/周/月)                                                  │
│                                                                             │
│  2. 部门绩效排名 (每日更新)                                                  │
│     ├── 部门得分排名                                                        │
│     ├── 环比变化趋势                                                        │
│     └── 异常部门标识                                                        │
│                                                                             │
│  3. 预警中心 (实时)                                                          │
│     ├── 待处理预警列表                                                      │
│     ├── 预警级别分类                                                        │
│     ├── 建议处理措施                                                        │
│     └── 一键处理/安排会议                                                   │
│                                                                             │
│  4. AI建议 (智能生成)                                                        │
│     ├── 运营优化建议                                                        │
│     ├── 风险预警建议                                                        │
│     ├── 资源调配建议                                                        │
│     └── 战略决策建议                                                        │
│                                                                             │
│  5. 报告生成 (一键生成)                                                      │
│     ├── 日报/周报/月报                                                      │
│     ├── 部门绩效报告                                                        │
│     ├── 项目进展报告                                                        │
│     └── 自定义报告                                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 四、技能工具开发优先级

### 4.1 优先级矩阵

| 优先级 | 工具 | 所属大脑 | 阶段 |
|--------|------|---------|------|
| **P0** | HttpTool | Decision | Phase 1 |
| **P0** | WeatherTool | Decision | Phase 1 |
| **P0** | SmartHomeTool | Decision | Phase 1 |
| **P1** | GitLabTool | TechBrain | Phase 2 |
| **P1** | JiraTool | TechBrain | Phase 2 |
| **P1** | JenkinsTool | TechBrain | Phase 2 |
| **P1** | DingTalkTool | CommBrain | Phase 2 |
| **P1** | FeishuTool | CommBrain | Phase 2 |
| **P2** | ErpTool | FinanceBrain | Phase 3 |
| **P2** | CrmTool | SalesBrain | Phase 3 |
| **P2** | HrSystemTool | HrBrain | Phase 3 |
| **P3** | ContractTool | LegalBrain | Phase 3 |
| **P3** | KnowledgeTool | CsBrain | Phase 3 |

---

## 五、里程碑与验收标准

### 5.1 里程碑时间表

| 里程碑 | 时间 | 核心交付 | 验收标准 | 状态 |
|--------|------|---------|---------|------|
| **M0** | 第1周末 | 项目骨架 | 可编译通过 | ✅ 完成 |
| **M1** | 第2周末 | 感知基础 | 可响应语音，调用基础工具 | ✅ 完成 |
| **M2** | 第4周末 | 技能学习 | 可使用GitLab/Jira/钉钉 | ✅ 完成 |
| **M3** | 第8周末 | 知识积累 | 可处理8个部门业务请求 | ✅ 完成 |
| **M4** | 第12周末 | 自主决策 | 可自主完成复杂任务 | ✅ 完成 |
| **M5** | 第16周末 | 自我进化 | 可自我学习新技能 | ✅ 完成 |
| **M6** | 第20周末 | 企业权限 | 支持身份识别、权限控制 | ✅ 完成 |
| **M7** | 第24周末 | 主动预判 | 贾维斯模式主动输出 | ✅ 完成 |
| **M8** | 第28周末 | 数据库架构 | PostgreSQL+Qdrant企业级存储 | ✅ 完成 |
| **M9** | 第32周末 | 分布式扩展 | 多实例部署、负载均衡 | ✅ 完成 |
| **M10** | 第46周末 | 数字员工 | 自主生成、协作、完全智能化 | ✅ 完成 |
| **M11** | 第50周末 | 运营评判 | 公司运营指标、绩效考核、CEO仪表盘 | ✅ 完成 |
| **M12** | 第52周末 | 统一员工模型 | HumanEmployee/DigitalEmployee统一 | ✅ 完成 |
| **M13** | 第58周末 | 自主运营 | BountyHunter/Evolution/Incentive | ✅ 完成 |
| **M14** | 第66周末 | 固定数字员工 | 32个固定员工、Token成本估算、项目核算 | 🚧 进行中 |
| **M15** | 第74周末 | 人工干预决策 | 干预决策引擎、交互界面、学习机制 | ✅ 已完成 |
| **M16** | 第78周末 | 企业合规管理 | 审计日志、合规报告、ERP适配器 | ✅ 已完成 |

### 5.2 智能体生命成长阶段

| 阶段 | 时间 | 核心能力 | 工具数量 | 自主程度 | 学习方式 | 状态 |
|------|------|---------|---------|---------|---------|------|
| **婴儿期** | 1-2周 | 感知+响应 | 5-10 | 0% | 完全被动 | ✅ 完成 |
| **幼儿期** | 3-4周 | 工具使用 | 10-20 | 20% | 监督学习 | ✅ 完成 |
| **少年期** | 5-8周 | 场景理解 | 20-40 | 50% | 反馈学习 | ✅ 完成 |
| **青年期** | 9-12周 | 自主决策 | 40-60 | 80% | 自主学习 | ✅ 完成 |
| **成熟期** | 13-24周 | 自我进化 | 60+ | 95% | 持续进化 | ✅ 完成 |
| **企业级** | 25-32周 | 分布式成长 | 80+ | 99% | 知识传承 | 🚧 进行中 |
| **智能化** | 33-46周 | 数字员工 | 100+ | 99.9% | 自主协作 | 🚧 进行中 |
| **自主运营** | 47-58周 | 自主赚钱 | 120+ | 99.99% | 硬件进化 | 🚧 进行中 |

---

## 六、技术栈清单

### 6.1 核心框架

| 类别 | 技术 | 版本 | 用途 | 状态 |
|------|------|------|------|------|
| 核心框架 | Java | 21 | 主语言 | ✅ |
| 核心框架 | Spring Boot | 3.4 | 应用框架 | ✅ |
| 性能组件 | Rust | - | 音频处理、管道消息、安全策略、内存后端 | ✅ |
| 语音处理 | Python | - | FunASR + MeloTTS | ✅ |

### 6.2 LLM 模型

| 类别 | 技术 | 版本 | 用途 | 状态 |
|------|------|------|------|------|
| LLM | Qwen3.5-27B | - | 主大脑 (复杂推理、部门路由) | ✅ |
| LLM | Qwen3-0.6B | - | 闲聊神经元 (快速响应) | ✅ |
| LLM | BitNet-1.58-3B | - | 工具/兜底神经元 (工具检测、自成长) | ✅ |
| Embedding | BGE-M3 | - | 文本嵌入 (1024维向量) | ✅ |

### 6.3 存储系统

| 类别 | 技术 | 版本 | 用途 | 状态 |
|------|------|------|------|------|
| 本地存储 | SQLite | - | 神经元私有存储 (L1层) | ✅ |
| 向量存储 | Rust实现 | - | 本地向量存储 | ✅ |
| 缓存 | Rust LRU | - | 本地缓存 | ✅ |
| --- | --- | --- | --- | --- |
| 主数据库 | PostgreSQL | 15+ | 企业知识存储 (L2/L3层) | 🔜 必需 |
| 向量数据库 | Qdrant | 1.7+ | 向量检索 | 🔜 必需 |
| 分布式缓存 | Redis | 7+ | 多实例会话共享 | 🔜 推荐 |
| 消息队列 | Kafka | 3+ | 神经元通讯 | 🔜 可选 |

### 6.4 感知与识别

| 类别 | 技术 | 版本 | 用途 | 状态 |
|------|------|------|------|------|
| ASR | FunASR/Sherpa | - | 语音识别 | ✅ |
| TTS | MeloTTS/Supertonic | - | 语音合成 | ✅ |
| 视觉 | Qwen3.5-27B多模态 | - | 图像/文档/人脸分析 | ✅ |
| 声纹识别 | CAM++ | - | 192维声纹向量 | 🔜 迁移 |
| OAuth | 钉钉/飞书/企业微信 | - | 企业登录 | ✅ |

### 6.5 架构模式

| 模式 | 说明 | 状态 |
|------|------|------|
| 神经元架构 | Neuron接口、独立执行循环 | ✅ |
| 管道通讯 | Channel接口、广播/单播/轮询 | ✅ |
| 三层LLM | MainBrain + Qwen3Neuron + BitNetNeuron | ✅ |
| 分层知识库 | L1私有/L2领域/L3共享 | ✅ |
| 进化系统 | EvolutionSignal + BrainPersonality + 熔断器 | ✅ |
| 安全策略 | AutonomyLevel + SecurityPolicy | ✅ |
| 企业权限 | UserIdentity + AccessLevel + BrainAccessControl | ✅ |
| 主动预判 | Time/Event/Pattern/Risk Predictor | ✅ |
| 数字员工 | DigitalWorker + WorkerTemplate + 自主协作 | ✅ |
| 人工干预 | InterventionDecision + Learning + AI化评估 | ✅ 已完成 |

---

## 七、参考文档

### 7.1 项目文档

- [PROJECT_FRAMEWORK.md](./PROJECT_FRAMEWORK.md) - 项目框架索引
- [需求文档.md](../../需求文档.md) - 需求文档
- [解决方案.md](../../解决方案.md) - 解决方案文档

### 7.2 核心设计文档

| 文档 | 说明 |
|------|------|
| [docs/02-architecture.md](./docs/02-architecture.md) | 架构设计 |
| [docs/05-knowledge-system.md](./docs/05-knowledge-system.md) | 知识体系 |
| [docs/06-evolution-system.md](./docs/06-evolution-system.md) | 进化系统 |
| [docs/07-unified-employee-model.md](./docs/07-unified-employee-model.md) | 统一员工模型 |
| [docs/08-database-design.md](./docs/08-database-design.md) | 数据库设计 |
| [docs/09-proactive-prediction.md](./docs/09-proactive-prediction.md) | 主动预判 |
| [docs/10-operation-assessment.md](./docs/10-operation-assessment.md) | 运营评判系统 |
| [docs/20-human-intervention-design.md](./docs/20-human-intervention-design.md) | 人工干预决策 |

### 7.3 参考项目

| 项目 | 用途 | 参考内容 |
|------|------|---------|
| **OpenClaw** | Agent执行框架 | Tool-Call Loop、技能系统、Provider抽象 |
| **ZeroClaw** | 安全与性能 | SecurityPolicy、AutonomyLevel、Rust原生组件 |
| **evolver-main** | 进化系统 | GEP协议、进化信号、人格状态、熔断器 |
| **awesome-openclaw-usecases** | 场景复用 | 主动预判、习惯分析、会议纪要 |
| **BMAD-METHOD** | 数字员工设计 | Agent定义、Persona系统、Party Mode协作、工作流绑定 |

### 7.4 技术选型依据

| 技术 | 选型理由 |
|------|---------|
| **Java 21** | 企业级稳定性、丰富生态、虚拟线程支持 |
| **Spring Boot 3.4** | 快速开发、自动配置、微服务支持 |
| **Rust** | 零成本抽象、内存安全、高性能并发 |
| **Qwen3.5-27B** | 国产大模型、复杂推理、多模态支持 |
| **BitNet-1.58-3B** | 1.58位量化、低延迟、边缘部署 |
| **BGE-M3** | 多粒度编码、跨语言支持、1024维向量 |
| **PostgreSQL** | 企业级稳定、pgvector扩展、JSONB支持 |
| **Qdrant** | 高性能向量检索、过滤查询、分布式支持 |

---

## 八、开发进度总览

### 8.1 完成度统计

| 阶段 | 完成度 | 核心交付 |
|------|--------|---------|
| Phase 0: 项目初始化 | 100% | 项目骨架、核心接口 |
| Phase 1: 婴儿期 | 100% | 感知神经元、路由神经元 |
| Phase 2: 幼儿期 | 100% | 8个部门大脑、企业工具集 |
| Phase 3: 少年期 | 100% | LLM神经元、Rust原生模块 |
| Phase 4: 青年期 | 100% | Gateway、对话系统、技能系统 |
| Phase 5: 成熟期 | 100% | 自我进化、知识传承、视觉神经元、传感器神经元 |
| Phase 6: 成长型知识体系 | 100% | 知识进化、三层知识库 |
| Phase 7: 智能进化系统 | 100% | 进化信号、人格系统、熔断器 |
| Phase 7.5: 即学即会 | 100% | 热加载、REST API、进化执行器 |
| Phase 8: 企业权限管理 | 100% | 身份识别、权限控制、审计日志 |
| Phase 9: 主动预判 | 100% | 时间/事件/模式/风险预判 |
| Phase 10: 数据库架构 | 100% | PostgreSQL、Qdrant、企业数据表、Repository |
| Phase 11: 分布式扩展 | 100% | Redis缓存、Kafka消息、分布式锁 |
| Phase 12: 数字员工自主生成 | 100% | DigitalWorker、WorkerTemplate、Factory、LifecycleManager |
| Phase 13: 运营评判系统 | 100% | 运营指标体系、绩效考核系统、CEO仪表盘 |
| Phase 14: 统一员工模型代码 | 100% | IdUtils、EmployeeStatus、EmployeePersonality、AbstractNeuron集成、EmployeeServiceImpl |
| Phase 15: 自主运营能力 | 100% | BountyHunterSkill、赏金任务服务、数字员工协作模式 |
| Phase 16: 性能优化 | 100% | EmbeddingService、VectorIndexOptimizer、知识库测试框架、分布式部署 |
| Phase 17: 固定数字员工实现 | 100% | 32个固定员工、Token成本估算、项目独立核算 |
| Phase 18: 部门页面隔离 | 100% | 部门API权限隔离、部门群聊WebSocket、部门专属页面支持 |
| Phase 19: 人工干预决策系统 | 100% | InterventionDecision、InterventionRule、InterventionDecisionEngine、InterventionNeuron、RiskAssessmentService、ImpactAnalyzer、InterventionController、RiskFactor、RiskAssessment |
| Phase 20: 企业合规管理系统 | 100% | 审计日志、合规报告、ERP适配器、数据分类分级 |
| Phase 21: 前端对接扩展 | 100% | ProjectController、TaskController、ApprovalController、工作流引擎 |

**总体完成度: ~98%**

### 8.2 下一步工作

1. **P0 - 统一员工模型代码完善** ✅ 已完成
   - ✅ IdUtils 统一ID工具类
   - ✅ EmployeeStatus 统一状态枚举
   - ✅ EmployeePersonality 统一人格模型
   - ✅ Employee 接口定义
   - ✅ DigitalEmployee 实现类
   - ✅ HumanEmployee 实现类
   - ✅ AbstractNeuron ID格式集成
   - ✅ NeuronRegistryImpl ID格式支持
   - ✅ EmployeeServiceImpl 服务实现

2. **P1 - 数据库架构完善** ✅ 已完成
   - ✅ PostgreSQL 数据源配置
   - ✅ Qdrant 向量数据库集成
   - ✅ 企业权限数据表 (员工表/部门表/审计日志)
   - ✅ 主大脑成长数据表
   - ✅ EmployeeRepository / DepartmentRepository
   - ✅ 数据迁移工具
   - 🔜 性能优化

3. **P2 - 分布式扩展完善** ✅ 已完成
   - ✅ Redis 分布式缓存服务
   - ✅ Kafka 消息队列服务
   - ✅ 分布式锁实现
   - 🔜 多实例部署方案
   - 🔜 负载均衡策略

4. **P3 - 数字员工自主生成完善** ✅ 已完成
   - ✅ DigitalWorker 模型定义
   - ✅ WorkerTemplate 模板系统
   - ✅ 数字员工工厂模式
   - ✅ 生命周期管理
   - ✅ 协作模式实现
   - 🔜 自动扩缩容

5. **P4 - 运营评判系统完善** ✅ 已完成
   - ✅ 公司运营指标体系
   - ✅ 员工绩效考核系统
   - ✅ CEO仪表盘
   - 🔜 自主运行机制

6. **P5 - 自主运营能力完善** ✅ 已完成
   - ✅ BountyHunterSkill 赚钱技能
   - ✅ BountyTask 赏金任务模型
   - ✅ BountyHunterService 服务接口
   - 🔜 多平台任务接入
   - 🔜 收益自动结算

7. **P6 - 性能优化** ✅ 已完成
   - ✅ EmbeddingService 向量嵌入服务
   - ✅ VectorIndexOptimizer 向量索引优化
   - ✅ KnowledgeBaseTestFramework 知识库测试框架
   - ✅ DistributedDeploymentService 分布式部署服务
   - ✅ 集群管理、节点注册、故障转移
   - ✅ 自动扩缩容

8. **P7 - 固定数字员工实现** ✅ 已完成
   - ✅ 32个固定数字员工定义
   - ✅ FixedEmployeeRegistry 固定员工注册中心
   - ✅ TokenCostEstimator Token成本估算器
   - ✅ ProjectAccounting 项目独立核算
   - ✅ SandboxExecutor 沙箱执行器 (进程隔离)
   - ✅ ConfigVersionManager 配置版本管理
   - ✅ MonthlyBudgetManager 月度预算管理
   - ✅ TaskCheckout 任务检出器
   - ✅ RunQueue 运行队列

9. **P8 - 用户画像系统** ✅ 已完成
   - ✅ user_profiles 表设计与创建
   - ✅ UserProfileService 服务实现
   - ✅ UserProfileEntity 实体类
   - ✅ UserProfileRepository 仓库类
   - ✅ 知识关联机制实现
   - ✅ 行为偏好配置

10. **P9 - 前端数据流修复** ✅ 已完成
   - ✅ AgentService 集成 ChatNeuronRouter
   - ✅ 消息进入 Channel 群聊机制
   - ✅ 三层架构路由生效
   - ✅ **权限检查集成**
   - ✅ 文档附件处理流程

11. **P10 - 人工干预决策系统** ✅ 已完成
   - 🔜 InterventionDecisionEngine 干预决策引擎
   - 🔜 InterventionOrchestrator 干预流程编排
   - 🔜 InterventionLearningService 学习机制
   - 🔜 干预交互界面
   - 🔜 与合规系统集成

12. **P11 - 企业合规管理系统** ✅ 已完成
   - 🔜 ComplianceAuditService 审计日志服务（扩展持久化）
   - 🔜 ComplianceReportService 合规报告服务
   - 🔜 FinanceRuleEngine 财务规则引擎
   - 🔜 DataClassificationService 数据分类服务
   - 🔜 BuiltInErpAdapter 内置ERP适配器
   - 🔜 与人工干预系统集成

13. **P12 - 前端对接扩展** ✅ 已完成
   - ✅ ProjectController 项目管理API
   - ✅ ProjectService 项目服务
   - ✅ Project 项目实体（8阶段生命周期）
   - ✅ TaskController 任务管理API
   - ✅ ApprovalController 审批流程API
   - ✅ ApprovalService 审批服务
   - ✅ ApprovalWorkflow 工作流定义
   - ✅ ApprovalInstance 审批实例
   - ✅ 默认审批流程（default/project_approval/expense_approval）

### 8.3 前端数据流问题分析

#### 8.3.1 当前问题

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    当前实现问题                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  问题1: AgentService 直接调用 ModelManager，绕过了整个神经元系统               │
│                                                                             │
│  当前流程 (错误):                                                            │
│  前端 → AgentWebSocketHandler → AgentService → ModelManager → 直接返回      │
│                                                                             │
│  设计预期流程 (正确):                                                         │
│  前端 → AgentWebSocketHandler → AgentService → ChatNeuronRouter             │
│      → Channel 群聊 → 神经元/大脑处理 → 返回                                  │
│                                                                             │
│  问题2: ChatNeuronRouter 缺少权限检查 ⚠️ 安全风险                             │
│                                                                             │
│  当前路由逻辑:                                                               │
│  ├── 闲聊意图 → 可能兜底到 MainBrain (错误!)                                 │
│  ├── 复杂任务 → 直接路由到 MainBrain (无权限检查!)                            │
│  └── 外来访客可能访问企业大脑 (安全漏洞!)                                      │
│                                                                             │
│  影响:                                                                       │
│  ├── 三层 LLM 架构未生效                                                     │
│  ├── Channel 群聊机制未使用                                                  │
│  ├── 权限隔离失效                                                            │
│  ├── MainBrain 跨部门协调未触发                                              │
│  └── 外来人员可能访问企业资源                                                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 8.3.2 权限控制设计

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    权限控制流程                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Step 1: 用户身份识别                                                        │
│  ├── OAuth 登录 (钉钉/飞书/企业微信)                                          │
│  ├── 声纹识别 (CAM++)                                                        │
│  ├── 手机号验证                                                              │
│  └── 人脸识别 (EyeNeuron)                                                    │
│                                                                             │
│  Step 2: 权限级别确定                                                        │
│  ├── INTERNAL_CHAIRMAN → FULL (董事长)                                       │
│  ├── INTERNAL_ACTIVE → DEPARTMENT (在职员工)                                 │
│  ├── INTERNAL_PROBATION → LIMITED (试用期)                                   │
│  ├── INTERNAL_DEPARTED → CHAT_ONLY (离职)                                    │
│  ├── EXTERNAL_VISITOR → CHAT_ONLY (外来访客)                                 │
│  └── EXTERNAL_CUSTOMER/PARTNER → LIMITED (客户/合作伙伴)                     │
│                                                                             │
│  Step 3: 基于权限的路由                                                      │
│  ├── CHAT_ONLY → 强制路由到 Qwen3Neuron                                      │
│  ├── LIMITED → 可访问 AdminBrain, CsBrain                                    │
│  ├── DEPARTMENT → 可访问本部门大脑 + ToolNeuron                               │
│  └── FULL → 可访问所有大脑 + MainBrain                                        │
│                                                                             │
│  【关键原则】                                                                │
│  ├── 权限检查在路由之前执行                                                  │
│  ├── 低权限用户永远不会被路由到高权限资源                                     │
│  ├── CHAT_ONLY 用户只能访问 Qwen3Neuron                                      │
│  └── MainBrain 需要 FULL 权限 (董事长)                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 8.3.3 修复方案

**AgentService 修改**:

```java
// 修改前
public CompletableFuture<Map<String, Object>> processTextAsync(String sessionId, String text, String channel) {
    return modelManager.generateText(sessionId, text, null);
}

// 修改后
public CompletableFuture<Map<String, Object>> processTextAsync(String sessionId, String text, String channel) {
    // 1. 获取用户权限 (从会话上下文)
    AccessLevel accessLevel = sessionManager.getAccessLevel(sessionId);
    if (accessLevel == null) {
        accessLevel = AccessLevel.CHAT_ONLY;  // 默认最低权限
    }
    
    // 2. 构建路由上下文 (包含权限信息)
    Map<String, Object> context = Map.of(
        "channel", channel,
        "accessLevel", accessLevel,
        "userId", sessionManager.getUserId(sessionId),
        "departmentId", sessionManager.getDepartmentId(sessionId)
    );
    
    // 3. 使用路由器选择神经元 (带权限检查)
    ChatNeuronRouter.RoutingResult routing = chatNeuronRouter.route(sessionId, text, context);
    
    // 4. 检查权限是否足够
    if (!routing.hasPermission()) {
        // 权限不足，降级到闲聊神经元并提示用户
        return CompletableFuture.completedFuture(Map.of(
            "type", "permission_denied",
            "message", "您的权限不足以执行此操作，已切换到闲聊模式",
            "requiredLevel", routing.getRequiredLevel()
        ));
    }
    
    // 5. 通过 Channel 发送消息
    Neuron targetNeuron = routing.getNeuron();
    ChannelMessage message = ChannelMessage.text(
        "channel://input/user",
        "user",
        targetNeuron.getInputChannels().get(0),
        sessionId,
        text
    );
    
    // 6. 发布到 Channel
    channelManager.publish(targetNeuron.getInputChannels().get(0), message);
    
    // 7. 等待响应
    return waitForResponse(sessionId, routing);
}
```

**ChatNeuronRouter 权限检查修改**:

```java
public RoutingResult route(String sessionId, String userInput, Map<String, Object> context) {
    // 1. 获取用户权限
    AccessLevel accessLevel = (AccessLevel) context.getOrDefault("accessLevel", AccessLevel.CHAT_ONLY);
    
    // 2. 意图分类
    ClassificationResult classification = intentClassifier.classify(userInput);
    
    // 3. 基于权限的路由决策
    Neuron targetNeuron = selectTargetNeuronWithPermission(classification, accessLevel, context);
    
    // 4. 构建结果
    RoutingResult result = new RoutingResult();
    result.setSessionId(sessionId);
    result.setIntent(classification.getIntent().name());
    result.setNeuron(targetNeuron);
    result.setAccessLevel(accessLevel);
    
    return result;
}

private Neuron selectTargetNeuronWithPermission(ClassificationResult classification, 
                                                  AccessLevel accessLevel,
                                                  Map<String, Object> context) {
    return switch (classification.getIntent()) {
        case GREETING, CASUAL_CHAT, SIMPLE_QUESTION -> {
            // 所有用户都可以访问闲聊神经元
            yield chatNeuron;
        }
        case TOOL_CALL -> {
            // 需要 DEPARTMENT 或更高权限
            if (accessLevel.getLevel() >= AccessLevel.DEPARTMENT.getLevel()) {
                yield toolNeuron != null ? toolNeuron : chatNeuron;
            }
            // 权限不足，降级到闲聊
            yield chatNeuron;
        }
        case COMPLEX_TASK -> {
            // FULL 权限: 可访问 MainBrain
            if (accessLevel == AccessLevel.FULL && mainBrain != null) {
                yield mainBrain;
            }
            // DEPARTMENT 权限: 路由到本部门大脑
            if (accessLevel == AccessLevel.DEPARTMENT) {
                String departmentId = (String) context.get("departmentId");
                yield getDepartmentBrain(departmentId);
            }
            // LIMITED/CHAT_ONLY: 降级到闲聊
            yield chatNeuron;
        }
        case UNKNOWN -> chatNeuron;  // 安全默认
    };
}
```

#### 8.3.4 验收标准

| 测试场景 | 用户身份 | 预期结果 |
|---------|---------|---------|
| 闲聊 "你好" | 外来访客 | ✅ 路由到 Qwen3Neuron |
| 闲聊 "你好" | 董事长 | ✅ 路由到 Qwen3Neuron |
| 工具调用 "查询天气" | 外来访客 | ⚠️ 降级到 Qwen3Neuron (权限不足提示) |
| 工具调用 "查询天气" | 在职员工 | ✅ 路由到 ToolNeuron |
| 复杂任务 "帮我开发" | 外来访客 | ⚠️ 降级到 Qwen3Neuron (权限不足提示) |
| 复杂任务 "帮我开发" | 在职员工 | ✅ 路由到 TechBrain (本部门) |
| 复杂任务 "帮我开发" | 董事长 | ✅ 路由到 MainBrain → TechBrain |
| 跨部门任务 | 在职员工 | ⚠️ 降级到本部门大脑 (无跨部门权限) |
| 跨部门任务 | 董事长 | ✅ 路由到 MainBrain (跨部门协调) |

### 8.3 后续规划

1. **P7 - 生产环境部署**
   - 🔜 Kubernetes 部署配置
   - 🔜 监控告警集成
   - 🔜 日志收集分析
   - 🔜 安全加固

2. **P8 - 功能增强**
   - 🔜 多语言支持
   - 🔜 更多LLM模型适配
   - 🔜 插件市场
   - 🔜 移动端支持

---

### Phase 21: 项目开发流程编排系统 (第79-86周) ✅ 已完成

> 实现完整的项目开发生命周期自动化，从需求分析到上线运维的全流程智能编排

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Phase 21: 项目开发流程编排系统 🔜 规划中                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  目标: 实现从需求文档到项目上线的全流程自动化编排                                  │
│                                                                             │
│  核心能力:                                                                   │
│  ├── 流程编排器: 自动推进项目阶段，协调不同神经元协作                             │
│  ├── 阶段神经元: 每个开发阶段专属的智能处理单元                                   │
│  ├── 迭代管理: Sprint规划、任务看板、迭代回顾                                   │
│  ├── 代码能力: 代码生成、自动重构、代码审查                                      │
│  ├── 测试自动化: 单元测试生成、自动化测试执行                                    │
│  └── CI/CD流水线: 构建、部署、监控一体化                                        │
│                                                                             │
│  当前状态分析:                                                                │
│  ├── ✅ 已有: ProjectPhase枚举 (8阶段)、ProjectService、TechBrain            │
│  ├── ✅ 已有: GitLabTool、JenkinsTool、JiraTool 等工具                        │
│  ├── ❌ 缺少: 流程编排器 (自动推进阶段、协调神经元)                              │
│  ├── ❌ 缺少: 阶段专属神经元 (需求分析、架构设计、代码审查等)                     │
│  ├── ❌ 缺少: 迭代开发支持 (Sprint、看板、回顾)                                 │
│  ├── ❌ 缺少: 代码生成/修改能力                                                │
│  ├── ❌ 缺少: 测试自动化能力                                                   │
│  └── ❌ 缺少: CI/CD流水线编排                                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 21.1 项目开发流程阶段

| 阶段 | 代码 | 负责神经元 | 核心任务 | 输入 | 输出 |
|------|------|-----------|---------|------|------|
| **市场调研** | MARKET_ANALYSIS | MarketResearchNeuron | 竞品分析、市场趋势、用户调研 | 需求描述 | 市场分析报告 |
| **需求分析** | REQUIREMENT | RequirementAnalysisNeuron | 需求文档解析、功能拆解、优先级排序 | 需求文档/市场报告 | 需求规格说明书 |
| **方案设计** | DESIGN | ArchitectureDesignNeuron | 架构设计、技术选型、接口设计 | 需求规格 | 技术方案文档 |
| **开发实施** | DEVELOPMENT | CodeDevelopmentNeuron | 代码编写、单元测试、代码审查 | 技术方案 | 源代码、测试代码 |
| **测试验收** | TESTING | TestAutomationNeuron | 测试用例生成、自动化测试、Bug修复 | 源代码 | 测试报告 |
| **上线部署** | DEPLOYMENT | DeploymentNeuron | CI/CD流水线、环境配置、灰度发布 | 测试通过代码 | 生产环境 |
| **运营维护** | OPERATION | OperationNeuron | 监控告警、日志分析、性能优化 | 运行数据 | 运维报告 |
| **售后服务** | AFTER_SALES | CustomerSupportNeuron | 用户反馈、问题排查、迭代改进 | 用户反馈 | 改进建议 |

#### 21.2 新增神经元设计

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    阶段专属神经元架构                                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  RequirementAnalysisNeuron (需求分析神经元)                          │   │
│  │  ├── ID: neuron://tech/requirement-analyst/001                      │   │
│  │  ├── 输入通道: channel://tech/requirement                           │   │
│  │  ├── 输出通道: channel://tech/design                                │   │
│  │  ├── 能力:                                                          │   │
│  │  │   ├── parseRequirementDocument() - 解析需求文档                  │   │
│  │  │   ├── extractFeatures() - 提取功能点                             │   │
│  │  │   ├── prioritizeFeatures() - 优先级排序                          │   │
│  │  │   └── generateSpec() - 生成需求规格说明书                         │   │
│  │  └── 工具: PdfTool, OfficeTool, SummarizeTool                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ArchitectureDesignNeuron (架构设计神经元)                           │   │
│  │  ├── ID: neuron://tech/architect/001                                │   │
│  │  ├── 输入通道: channel://tech/design                                │   │
│  │  ├── 输出通道: channel://tech/development                           │   │
│  │  ├── 能力:                                                          │   │
│  │  │   ├── designArchitecture() - 架构设计                            │   │
│  │  │   ├── selectTechStack() - 技术选型                               │   │
│  │  │   ├── designAPI() - API接口设计                                  │   │
│  │  │   ├── designDatabase() - 数据库设计                              │   │
│  │  │   └── generateTechDoc() - 生成技术方案文档                        │   │
│  │  └── 工具: KnowledgeGraphTool, DiagramGeneratorTool                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  CodeDevelopmentNeuron (代码开发神经元)                              │   │
│  │  ├── ID: neuron://tech/developer/001                                │   │
│  │  ├── 输入通道: channel://tech/development                           │   │
│  │  ├── 输出通道: channel://tech/testing                               │   │
│  │  ├── 能力:                                                          │   │
│  │  │   ├── generateCode() - 代码生成                                  │   │
│  │  │   ├── refactorCode() - 代码重构                                  │   │
│  │  │   ├── generateUnitTest() - 单元测试生成                          │   │
│  │  │   └── codeReview() - 代码审查                                    │   │
│  │  └── 工具: GitLabTool, CodeGeneratorTool, LintTool                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  TestAutomationNeuron (测试自动化神经元)                             │   │
│  │  ├── ID: neuron://tech/tester/001                                   │   │
│  │  ├── 输入通道: channel://tech/testing                               │   │
│  │  ├── 输出通道: channel://tech/deployment                            │   │
│  │  ├── 能力:                                                          │   │
│  │  │   ├── generateTestCases() - 测试用例生成                         │   │
│  │  │   ├── executeTests() - 执行自动化测试                            │   │
│  │  │   ├── analyzeCoverage() - 覆盖率分析                             │   │
│  │  │   └── generateTestReport() - 生成测试报告                        │   │
│  │  └── 工具: JestTool, SeleniumTool, CoverageTool                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  DeploymentNeuron (部署神经元)                                       │   │
│  │  ├── ID: neuron://tech/devops/001                                   │   │
│  │  ├── 输入通道: channel://tech/deployment                            │   │
│  │  ├── 输出通道: channel://tech/operation                             │   │
│  │  ├── 能力:                                                          │   │
│  │  │   ├── buildPipeline() - CI/CD流水线构建                          │   │
│  │  │   ├── deployToEnv() - 环境部署                                   │   │
│  │  │   ├── configureEnv() - 环境配置                                  │   │
│  │  │   └── rollback() - 回滚操作                                      │   │
│  │  └── 工具: JenkinsTool, DockerTool, KubernetesTool                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 21.3 流程编排器设计

```java
/**
 * 项目开发流程编排器
 * 负责自动推进项目阶段，协调不同神经元协作
 */
public interface ProjectDevelopmentOrchestrator {
    
    /**
     * 启动项目开发流程
     * @param projectId 项目ID
     * @param requirementDocument 需求文档
     * @return 流程实例ID
     */
    String startDevelopmentProcess(String projectId, String requirementDocument);
    
    /**
     * 推进到下一阶段
     * @param projectId 项目ID
     * @param currentPhase 当前阶段
     * @param phaseOutput 当前阶段输出
     * @return 下一阶段
     */
    ProjectPhase advanceToNextPhase(String projectId, ProjectPhase currentPhase, Map<String, Object> phaseOutput);
    
    /**
     * 执行阶段任务
     * @param projectId 项目ID
     * @param phase 项目阶段
     * @param input 输入数据
     * @return 阶段输出
     */
    PhaseResult executePhase(String projectId, ProjectPhase phase, Map<String, Object> input);
    
    /**
     * 获取阶段进度
     * @param projectId 项目ID
     * @return 阶段进度列表
     */
    List<PhaseProgress> getPhaseProgress(String projectId);
    
    /**
     * 处理阶段异常
     * @param projectId 项目ID
     * @param phase 项目阶段
     * @param error 异常信息
     * @return 处理结果
     */
    ExceptionHandlingResult handlePhaseException(String projectId, ProjectPhase phase, Exception error);
}
```

#### 21.4 迭代开发支持

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    迭代开发支持                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Sprint 管理:                                                                │
│  ├── Sprint 实体                                                            │
│  │   ├── sprintId, projectId, name                                         │
│  │   ├── startDate, endDate, status                                        │
│  │   ├── goals, tasks                                                      │
│  │   └── velocity, burndown                                                │
│  ├── SprintService                                                          │
│  │   ├── createSprint() - 创建Sprint                                       │
│  │   ├── planSprint() - Sprint规划                                         │
│  │   ├── startSprint() - 开始Sprint                                        │
│  │   ├── completeSprint() - 完成Sprint                                     │
│  │   └── getSprintMetrics() - 获取Sprint指标                               │
│  └── SprintPlanningNeuron                                                   │
│      ├── 分析产品Backlog                                                    │
│      ├── 估算任务复杂度                                                     │
│      ├── 分配任务给团队成员                                                 │
│      └── 生成Sprint计划                                                     │
│                                                                             │
│  任务看板:                                                                   │
│  ├── KanbanBoard 实体                                                       │
│  │   ├── columns: TODO, IN_PROGRESS, REVIEW, TESTING, DONE                │
│  │   ├── tasks: List<KanbanTask>                                          │
│  │   └── wipLimits: Map<Column, Integer>                                  │
│  ├── KanbanService                                                          │
│  │   ├── moveTask() - 移动任务                                             │
│  │   ├── getWipStatus() - 获取WIP状态                                      │
│  │   └── getBottleneckAnalysis() - 瓶颈分析                               │
│  └── KanbanNeuron                                                           │
│      ├── 自动分配任务                                                       │
│      ├── 检测阻塞任务                                                       │
│      └── 优化工作流                                                         │
│                                                                             │
│  迭代回顾:                                                                   │
│  ├── SprintRetrospective 实体                                               │
│  │   ├── whatWentWell: List<String>                                        │
│  │   ├── whatToImprove: List<String>                                       │
│  │   ├── actionItems: List<ActionItem>                                    │
│  │   └── participantFeedback: Map<String, Feedback>                       │
│  ├── RetrospectiveService                                                   │
│  │   ├── generateRetrospective() - 生成回顾报告                            │
│  │   ├── analyzeTrends() - 趋势分析                                        │
│  │   └── trackActionItems() - 跟踪改进项                                   │
│  └── RetrospectiveNeuron                                                    │
│      ├── 分析Sprint数据                                                     │
│      ├── 识别改进机会                                                       │
│      ├── 生成回顾报告                                                       │
│      └── 跟踪改进项执行                                                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 21.5 代码生成能力

| 能力 | 说明 | 实现方式 |
|------|------|---------|
| **代码生成** | 根据需求/设计生成代码 | LLM + 模板引擎 |
| **代码补全** | 智能代码补全建议 | LLM + 上下文分析 |
| **自动重构** | 代码重构建议和执行 | AST分析 + LLM |
| **代码审查** | 自动代码审查 | LLM + 规则引擎 |
| **文档生成** | 自动生成代码文档 | LLM + 代码分析 |

#### 21.6 CI/CD流水线编排

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CI/CD 流水线编排                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  流水线定义:                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Pipeline                                                             │   │
│  │  ├── stages: List<Stage>                                             │   │
│  │  │   ├── Stage: Build                                                │   │
│  │  │   │   ├── checkout() - 拉取代码                                   │   │
│  │  │   │   ├── install() - 安装依赖                                    │   │
│  │  │   │   ├── compile() - 编译代码                                    │   │
│  │  │   │   └── lint() - 代码检查                                       │   │
│  │  │   ├── Stage: Test                                                 │   │
│  │  │   │   ├── unitTest() - 单元测试                                   │   │
│  │  │   │   ├── integrationTest() - 集成测试                            │   │
│  │  │   │   ├── coverage() - 覆盖率检查                                 │   │
│  │  │   │   └── securityScan() - 安全扫描                               │   │
│  │  │   ├── Stage: Build Artifact                                       │   │
│  │  │   │   ├── buildImage() - 构建镜像                                 │   │
│  │  │   │   ├── pushRegistry() - 推送仓库                               │   │
│  │  │   │   └── tagRelease() - 打标签                                   │   │
│  │  │   ├── Stage: Deploy                                               │   │
│  │  │   │   ├── deployDev() - 部署开发环境                              │   │
│  │  │   │   ├── deployTest() - 部署测试环境                             │   │
│  │  │   │   ├── deployStaging() - 部署预发环境                          │   │
│  │  │   │   └── deployProd() - 部署生产环境                             │   │
│  │  │   └── Stage: Monitor                                              │   │
│  │  │       ├── healthCheck() - 健康检查                                │   │
│  │  │       ├── alerting() - 告警配置                                   │   │
│  │  │       └── logging() - 日志收集                                    │   │
│  │  └── triggers: [push, merge_request, schedule]                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  PipelineOrchestrator:                                                      │
│  ├── createPipeline() - 创建流水线                                          │
│  ├── executePipeline() - 执行流水线                                         │
│  ├── pausePipeline() - 暂停流水线                                           │
│  ├── resumePipeline() - 恢复流水线                                          │
│  ├── rollbackPipeline() - 回滚流水线                                        │
│  └── getPipelineStatus() - 获取流水线状态                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 21.7 新增代码文件

| 文件路径 | 说明 | 优先级 |
|---------|------|--------|
| `core/project/orchestration/ProjectDevelopmentOrchestrator.java` | 流程编排器接口 | P0 |
| `core/project/orchestration/impl/ProjectDevelopmentOrchestratorImpl.java` | 流程编排器实现 | P0 |
| `core/project/orchestration/PhaseResult.java` | 阶段执行结果 | P0 |
| `core/project/orchestration/PhaseProgress.java` | 阶段进度 | P0 |
| `core/neuron/impl/RequirementAnalysisNeuron.java` | 需求分析神经元 | P1 |
| `core/neuron/impl/ArchitectureDesignNeuron.java` | 架构设计神经元 | P1 |
| `core/neuron/impl/CodeDevelopmentNeuron.java` | 代码开发神经元 | P1 |
| `core/neuron/impl/TestAutomationNeuron.java` | 测试自动化神经元 | P1 |
| `core/neuron/impl/DeploymentNeuron.java` | 部署神经元 | P1 |
| `core/sprint/Sprint.java` | Sprint实体 | P2 |
| `core/sprint/SprintService.java` | Sprint服务 | P2 |
| `core/sprint/SprintPlanningNeuron.java` | Sprint规划神经元 | P2 |
| `core/kanban/KanbanBoard.java` | 看板实体 | P2 |
| `core/kanban/KanbanService.java` | 看板服务 | P2 |
| `core/retrospective/SprintRetrospective.java` | 迭代回顾实体 | P2 |
| `core/retrospective/RetrospectiveService.java` | 迭代回顾服务 | P2 |
| `core/pipeline/Pipeline.java` | 流水线定义 | P1 |
| `core/pipeline/PipelineOrchestrator.java` | 流水线编排器 | P1 |
| `core/tool/impl/CodeGeneratorTool.java` | 代码生成工具 | P1 |
| `core/tool/impl/TestGeneratorTool.java` | 测试生成工具 | P1 |

#### 21.8 数据库表设计

| 表名 | 用途 |
|------|------|
| project_development_processes | 项目开发流程实例 |
| phase_executions | 阶段执行记录 |
| sprints | Sprint管理 |
| sprint_tasks | Sprint任务 |
| kanban_boards | 看板 |
| kanban_tasks | 看板任务 |
| sprint_retrospectives | 迭代回顾 |
| pipelines | CI/CD流水线定义 |
| pipeline_executions | 流水线执行记录 |
| pipeline_stages | 流水线阶段 |

#### 21.9 与现有系统集成

| 系统 | 集成点 | 说明 |
|------|--------|------|
| 项目管理 (Phase 12) | Project实体扩展 | 添加开发流程实例关联 |
| 任务管理 (Phase 12) | Task实体扩展 | 支持Sprint和看板 |
| TechBrain (Phase 2) | 神经元注册 | 注册阶段专属神经元 |
| GitLabTool (Phase 2) | 代码管理 | 代码提交、分支管理 |
| JenkinsTool (Phase 2) | CI/CD | 流水线触发和监控 |
| JiraTool (Phase 2) | 任务跟踪 | Sprint和任务同步 |
| 进化系统 (Phase 7) | 技能生成 | 生成项目特定技能 |
| 知识库 (Phase 6) | 知识积累 | 项目知识沉淀 |

#### 21.10 验收标准

- [ ] 项目创建后自动启动开发流程
- [ ] 各阶段自动推进，输出物自动传递
- [ ] 阶段专属神经元可正确处理阶段任务
- [ ] Sprint规划、执行、回顾流程完整
- [ ] 看板可视化展示任务状态
- [ ] CI/CD流水线可自动触发和执行
- [ ] 代码生成功能可用
- [ ] 测试用例可自动生成和执行
- [ ] 异常情况可自动处理或人工干预

#### 21.11 开发优先级

```
Week 1-2: P0 核心流程编排器
├── ProjectDevelopmentOrchestrator 接口和实现
├── PhaseResult/PhaseProgress 模型
└── 与 ProjectService 集成

Week 3-4: P1 阶段神经元
├── RequirementAnalysisNeuron
├── ArchitectureDesignNeuron
├── CodeDevelopmentNeuron
├── TestAutomationNeuron
└── DeploymentNeuron

Week 5-6: P1 CI/CD流水线
├── Pipeline 定义和执行
├── PipelineOrchestrator
└── 与 JenkinsTool 集成

Week 7-8: P2 迭代开发支持
├── Sprint 管理服务
├── 看板服务
├── 迭代回顾服务
└── 前端界面集成
```

---

## Phase 22: Clawith-main 对比优化 (第87-90周) 🚧 进行中

> 基于 Clawith-main 项目对比分析的功能优化和代码实现同步

### 22.1 优化目标

根据 `docs/24-clawith-comparison-analysis.md` 的分析结果，实现以下优化：

1. **Sandbox 沙箱系统** ✅ - 研发部门数字员工使用 Trae CLI
2. **飞书集成增强** - 普通数字员工飞书功能
3. **前端移植** - Clawith-main React 前端移植
4. **代码实现同步** ✅ - 修复文档与代码差异

### 22.2 代码实现状态同步

#### 22.2.1 需要修复的架构问题

| 问题 | 文件 | 优先级 | 状态 |
|------|------|--------|------|
| 路由逻辑绕过 | AgentService.java | P0 | ✅ 已修复 |
| Brain 实现简化 | HrBrain.java 等 | P0 | ✅ 已完善 |
| 向量数据库未集成 | KnowledgeManagerImpl.java | P1 | 🔜 待实现 |
| 传感器神经元缺失 | SensorNeuron.java | P1 | 🔜 待创建 |

#### 22.2.2 需要完善的 Brain 实现

| Brain | 当前状态 | 目标状态 | 参考 |
|-------|---------|---------|------|
| HrBrain | ✅ 完整实现 | 完整实现 | TechBrain |
| FinanceBrain | ✅ 完整实现 | 完整实现 | TechBrain |
| SalesBrain | ✅ 完整实现 | 完整实现 | TechBrain |
| CsBrain | ✅ 完整实现 | 完整实现 | TechBrain |
| AdminBrain | ✅ 完整实现 | 完整实现 | TechBrain |
| LegalBrain | ✅ 完整实现 | 完整实现 | TechBrain |
| OpsBrain | ✅ 完整实现 | 完整实现 | TechBrain |

### 22.3 Sandbox 沙箱系统 (P0) ✅ 已完成

#### 22.3.1 已实现的文件

| 文件 | 说明 | 状态 |
|------|------|------|
| `core/sandbox/SandboxService.java` | 沙箱服务接口 | ✅ |
| `core/sandbox/ExecutionResult.java` | 执行结果模型 | ✅ |
| `core/sandbox/SandboxSession.java` | 会话接口 | ✅ |
| `core/sandbox/impl/DockerSandboxService.java` | Docker 后端实现 | ✅ |
| `core/sandbox/impl/SandboxSessionImpl.java` | 会话实现 | ✅ |
| `core/tool/impl/TraeTool.java` | Trae CLI 工具 | ✅ |

#### 22.3.2 架构设计

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Sandbox 沙箱架构                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Layer 1: TraeTool (Tool 接口)                                              │
│  ├── 对外暴露的 API 接口                                                    │
│  ├── 参数校验和权限控制                                                     │
│  └── 调用 SandboxService 执行                                              │
│                                                                             │
│  Layer 2: SandboxService                                                    │
│  ├── 沙箱环境管理                                                          │
│  ├── 命令执行和结果收集                                                     │
│  └── 资源限制和超时控制                                                     │
│                                                                             │
│  Layer 3: SandboxBackend (Docker/E2B)                                       │
│  ├── Docker 容器创建和管理                                                 │
│  ├── 镜像预装: Trae CLI + 开发环境                                         │
│  └── 工作目录挂载                                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 22.3.2 新增文件

| 文件路径 | 说明 | 优先级 |
|---------|------|--------|
| `core/tool/impl/TraeTool.java` | Trae CLI 工具 | P0 |
| `core/sandbox/SandboxService.java` | 沙箱服务 | P0 |
| `core/sandbox/SandboxBackend.java` | 沙箱后端接口 | P0 |
| `core/sandbox/DockerBackend.java` | Docker 后端实现 | P0 |
| `core/sandbox/ExecutionResult.java` | 执行结果模型 | P0 |
| `skills/tech/trae-development.md` | Trae 开发助手技能 | P1 |

### 22.4 飞书集成增强 (P0)

#### 22.4.1 分层设计

| 层级 | 组件 | 功能 | 状态 |
|------|------|------|------|
| 管理层 | ChairmanFeishuTool | 创建部门、管理员工、配置审批 | ✅ 已实现 |
| 使用层 | EmployeeFeishuTool | 发消息、看日程、编辑文档 | 🔜 待增强 |

#### 22.4.2 参考 Clawith-main 实现

| 功能 | Clawith-main 文件 | 复用方式 |
|------|------------------|---------|
| OAuth 登录 | `feishu_service.py` | 参考实现 |
| 消息发送 | `feishu_service.py` | 直接复用 |
| 多维表格 API | `feishu_service.py` | 参考实现 |
| 文档 API | `feishu_service.py` | 参考实现 |

### 22.5 前端移植 (P1) ✅ 已完成

#### 22.5.1 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| React | 19.0 | 前端框架 |
| Vite | 6.0 | 构建工具 |
| TypeScript | 5.0 | 类型系统 |
| TanStack Query | 5.0 | 数据请求 |
| Zustand | 5.0 | 状态管理 |

#### 22.5.2 已完成的移植工作

- ✅ 复制 frontend 目录到 `living-agent-service/frontend`
- ✅ 修改项目名称为 `living-agent-frontend`
- ✅ 修改品牌名称为 "Living Agent"
- ✅ 添加部门导航菜单（8个部门大脑）
- ✅ 创建项目管理页面 `Projects.tsx`
- ✅ 创建审批流程页面 `Approvals.tsx`
- ✅ 创建部门详情页面 `DepartmentDetail.tsx`
- ✅ 更新类型定义支持部门、项目、审批
- ✅ 更新 API 服务层
- ✅ 更新 i18n 翻译文件

### 22.6 触发器系统 (P1)

参考 Clawith-main 的 `trigger_daemon.py` 实现：

| 触发器类型 | 说明 | 优先级 |
|-----------|------|--------|
| cron | Cron 表达式触发 | P1 |
| poll | HTTP 轮询 + JSONPath 变化检测 | P1 |
| on_message | Agent 间消息触发 | P2 |
| webhook | Webhook 触发 | P2 |

### 22.7 三级自治权限增强 (P1)

参考 Clawith-main 的 `autonomy_service.py` 实现：

| 级别 | 行为 | 示例操作 |
|------|------|---------|
| L1 | 自动执行，仅记录日志 | read_files |
| L2 | 自动执行，通知创建者 | write_workspace_files, send_feishu_message |
| L3 | 需要审批 | send_external_message, delete_files, financial_operations |

### 22.8 开发优先级

```
Week 1-2: P0 Sandbox 沙箱系统
├── TraeTool 实现
├── SandboxService 实现
├── DockerBackend 实现
└── 与 TechBrain 集成

Week 3-4: P0 飞书集成增强
├── EmployeeFeishuTool 增强
├── 多维表格 API
├── 文档 API
└── 审批 API

Week 5-6: P0 代码实现同步
├── 修复路由逻辑
├── 完善 Brain 实现
└── 强化权限验证

Week 7-8: P1 前端移植
├── 基础框架移植
├── 认证系统适配
└── 导航结构适配

Week 9-10: P1 触发器和自治权限
├── Trigger 系统实现
├── AutonomyService 增强
└── 审批工作流
```

### 22.9 验收标准

- [ ] TraeTool 可正常调用 Trae CLI
- [ ] Sandbox 沙箱可安全执行代码
- [ ] 普通数字员工可使用飞书功能
- [ ] 前端可正常访问和使用
- [ ] 路由逻辑符合架构设计
- [ ] 所有 Brain 实现完整
- [ ] 向量数据库集成完成
- [ ] 触发器系统可用
- [ ] 三级自治权限生效

### 22.10 文档更新

| 文档 | 更新内容 |
|------|---------|
| PROJECT_FRAMEWORK.md | 同步架构变化，补充实现细节 |
| 02-architecture.md | 更新模块状态 |
| 06-evolution-system.md | 更新进化系统状态 |
| 14-local-models-deployment.md | 更新部署状态 |
| 22-feishu-integration-analysis.md | 更新集成状态 |

---

## 进度总览

| Phase | 名称 | 状态 | 完成度 |
|-------|------|------|--------|
| Phase 0 | 项目初始化 | ✅ 完成 | 100% |
| Phase 1 | 婴儿期 | ✅ 完成 | 100% |
| Phase 2 | 幼儿期 | ✅ 完成 | 100% |
| Phase 3 | 少年期 | ✅ 完成 | 100% |
| Phase 4 | 青年期 | ✅ 完成 | 100% |
| Phase 5 | 成熟期 | ✅ 已完成 | 100% |
| Phase 6 | 成长型知识体系 | ✅ 完成 | 100% |
| Phase 7 | 智能进化系统 | ✅ 完成 | 100% |
| Phase 7.5 | 即学即会能力 | ✅ 已完成 | 100% |
| Phase 8-20 | 其他功能 | ✅ 完成 | 100% |
| Phase 21 | 项目开发流程编排 | ✅ 已完成 | 100% |
| Phase 22 | Clawith-main 对比优化 | ✅ 已完成 | 100% |

**整体完成度: 约 95%**

### Phase 20 已完成工作

1. ✅ **ComplianceRule** - 合规规则模型
2. ✅ **ComplianceViolation** - 合规违规记录
3. ✅ **ComplianceManager** - 合规管理器
4. ✅ **ComplianceReport** - 合规报告生成

### Phase 21 已完成工作

1. ✅ **WorkflowOrchestrator** - 流程编排器核心实现
2. ✅ **WorkflowContext** - 工作流上下文
3. ✅ **WorkflowExecution** - 工作流执行状态管理
4. ✅ **PhaseHandler 接口** - 阶段处理器接口
5. ✅ **8个阶段处理器** - MarketAnalysis, Requirement, Design, Development, Testing, Deployment, Operation, AfterSales
6. ✅ **ProjectDevelopmentNeuron** - 项目开发流程神经元
7. ✅ **WorkflowMonitor** - 工作流监督器 (防止数字员工"摸鱼")
8. ✅ **HeartbeatProvider** - 心跳机制接口
9. ✅ **AbstractNeuron 心跳支持** - 神经元自动发送心跳

### Phase 7.5 已完成工作

1. ✅ **SkillHotReloader** - 技能热加载器，支持文件监控和自动重载
2. ✅ **EvolutionExecutor** - 进化执行器，支持 REPAIR/OPTIMIZE/INNOVATE 策略
3. ✅ **SkillLoader** - 技能加载器
4. ✅ **EvolutionAdminController** - 进化管理 REST API

### Phase 22 已完成工作

1. ✅ **AgentService 路由修复** - 修复了绕过 ChatNeuronRouter 的问题
2. ✅ **ChannelManager 订阅机制** - 添加了外部订阅者支持
3. ✅ **ChannelSubscriber 接口** - 新增订阅者接口
4. ✅ **7个部门 Brain 实现** - HrBrain, FinanceBrain, SalesBrain, CsBrain, AdminBrain, LegalBrain, OpsBrain
5. ✅ **Sandbox 沙箱系统** - Docker 后端实现
6. ✅ **TraeTool 工具** - Trae CLI 集成工具
7. ✅ **LayeredKnowledgeBaseImpl 向量搜索** - 集成 QdrantVectorService
8. ✅ **SensorNeuron 传感器神经元** - 系统监控和告警功能
9. ✅ **DepartmentAccessValidator** - 部门访问权限验证器
10. ✅ **PostgreSQL 知识库持久化** - KnowledgeEntryEntity + KnowledgeEntryRepository + KnowledgePersistenceService
11. ✅ **MainBrainModelSelector** - 主大脑模型灵活选择器（参考 Clawith-main 设计）
