# Living Agent Service 项目分析与测试验证需求文档

## 项目概述

Living Agent Service 是一个企业级生命智能体自治系统，核心设计理念包括：
- **神经元群聊模式**: 每个智能体作为"神经元"，通过通讯管路协作
- **仿脑神经中枢架构**: 按大脑功能分区设计智能体职责  
- **带生命的智能体**: 具备感知、决策、执行、学习、进化、赚钱能力
- **自主进化驱动**: 通过赚钱实现经济独立，收益用于硬件升级和技能进化

本需求文档基于项目核心框架，分析实际代码实现，整理项目规则，并设计董事长项目需求场景来验证整个系统流程的完整性。

## 需求

### 需求 1: 项目架构分析与规则整理

**用户故事:** 作为系统架构师，我需要深入分析 Living Agent Service 的实际代码实现，以便理解系统的真实架构和运行机制。

#### 验收标准

1. WHEN 分析项目代码结构 THEN 系统 SHALL 识别出三层LLM架构的实际实现
2. WHEN 检查权限控制机制 THEN 系统 SHALL 梳理出完整的访问级别和权限隔离规则
3. WHEN 分析神经元路由逻辑 THEN 系统 SHALL 理解ChatNeuronRouter的意图分类和路由决策机制
4. WHEN 检查WebSocket通信流程 THEN 系统 SHALL 掌握前后端数据交互的完整链路
5. WHEN 分析Docker部署配置 THEN 系统 SHALL 了解服务依赖关系和环境配置要求

### 需求 2: 董事长场景测试设计

**用户故事:** 作为董事长，我需要能够通过系统给出项目开发需求，并验证整个流程是否能够正常运行。

#### 验收标准

1. WHEN 董事长登录系统 THEN 系统 SHALL 识别其FULL权限级别并允许访问所有功能
2. WHEN 董事长输入项目需求 THEN 系统 SHALL 通过ChatNeuronRouter正确路由到MainBrain
3. WHEN MainBrain处理复杂任务 THEN 系统 SHALL 能够协调多个部门大脑进行协作
4. WHEN 系统处理项目需求 THEN 系统 SHALL 调用相关技能并生成合理的项目规划
5. WHEN 整个流程完成 THEN 系统 SHALL 返回完整的项目分析和建议

### 需求 3: 权限隔离验证

**用户故事:** 作为系统管理员，我需要验证不同权限级别的用户只能访问其授权范围内的功能。

#### 验收标准

1. WHEN CHAT_ONLY用户发送消息 THEN 系统 SHALL 只路由到Qwen3Neuron闲聊神经元
2. WHEN LIMITED用户尝试工具调用 THEN 系统 SHALL 拒绝访问并返回权限不足提示
3. WHEN DEPARTMENT用户访问其他部门功能 THEN 系统 SHALL 限制其只能访问本部门资源
4. WHEN FULL权限用户操作 THEN 系统 SHALL 允许访问所有神经元和大脑
5. IF 权限验证失败 THEN 系统 SHALL 记录安全日志并降级到安全的默认处理

### 需求 4: 模型加载与配置验证

**用户故事:** 作为运维工程师，我需要验证本地AI模型是否正确加载，以及Ollama集成是否正常工作。

#### 验收标准

1. WHEN 系统启动 THEN 系统 SHALL 成功加载Qwen3-0.6B闲聊模型
2. WHEN 系统启动 THEN 系统 SHALL 成功连接到本地Ollama服务(http://host.docker.internal:11434)
3. WHEN 检查模型状态 THEN 系统 SHALL 报告ASR、LLM、TTS模型的可用性
4. WHEN 模型选择器工作 THEN 系统 SHALL 根据硬件资源自动选择合适的模型
5. IF 模型加载失败 THEN 系统 SHALL 提供降级方案并记录错误信息

### 需求 5: 端到端流程测试

**用户故事:** 作为质量保证工程师，我需要验证从前端输入到后端处理的完整数据流是否正常工作。

#### 验收标准

1. WHEN 前端发送WebSocket消息 THEN 后端 SHALL 正确接收并解析消息内容
2. WHEN AgentService处理文本消息 THEN 系统 SHALL 通过Channel机制正确分发到目标神经元
3. WHEN 神经元处理完成 THEN 系统 SHALL 通过WebSocket将结果返回前端
4. WHEN 处理音频消息 THEN 系统 SHALL 完成ASR→LLM→TTS的完整链路
5. WHEN 系统出现异常 THEN 系统 SHALL 提供有意义的错误信息并保持连接稳定

### 需求 6: 技能系统集成测试

**用户故事:** 作为业务用户，我需要验证76个技能是否能够正确调用并产生预期结果。

#### 验收标准

1. WHEN 用户请求技术相关任务 THEN 系统 SHALL 调用TechBrain的25个技能
2. WHEN 用户请求行政事务 THEN 系统 SHALL 调用AdminBrain的15个技能
3. WHEN 技能执行过程中 THEN 系统 SHALL 正确处理工具调用和结果返回
4. WHEN 技能需要外部API THEN 系统 SHALL 正确配置和调用第三方服务
5. IF 技能执行失败 THEN 系统 SHALL 提供错误诊断并尝试替代方案

### 需求 7: 数据持久化验证

**用户故事:** 作为数据管理员，我需要验证对话历史、用户信息、系统状态是否正确持久化。

#### 验收标准

1. WHEN 用户进行对话 THEN 系统 SHALL 将对话历史保存到PostgreSQL数据库
2. WHEN 系统重启 THEN 系统 SHALL 能够恢复用户的对话上下文
3. WHEN 向量数据生成 THEN 系统 SHALL 正确存储到Qdrant向量数据库
4. WHEN 知识图谱更新 THEN 系统 SHALL 同步更新Neo4j图数据库
5. WHEN 缓存数据访问 THEN 系统 SHALL 优先使用Redis缓存提高性能

### 需求 8: 神经元群聊机制验证

**用户故事:** 作为系统架构师，我需要验证神经元之间的Channel通信机制是否按照设计正常工作。

#### 验收标准

1. WHEN 消息发布到Channel THEN 系统 SHALL 正确分发给所有订阅的神经元
2. WHEN 神经元处理消息 THEN 系统 SHALL 通过Channel返回处理结果
3. WHEN 多个神经元协作 THEN 系统 SHALL 支持单播、广播、优先级队列、轮询分发
4. WHEN Channel消息传递 THEN 系统 SHALL 保证消息的可靠性和顺序性
5. IF Channel通信失败 THEN 系统 SHALL 启用熔断机制并记录错误

### 需求 9: 自主进化能力验证

**用户故事:** 作为产品经理，我需要验证系统的自主进化能力是否能够检测信号并执行进化策略。

#### 验收标准

1. WHEN 系统检测到ERROR信号 THEN 系统 SHALL 执行REPAIR进化策略
2. WHEN 系统检测到OPPORTUNITY信号 THEN 系统 SHALL 执行INNOVATE进化策略
3. WHEN 系统检测到PERFORMANCE信号 THEN 系统 SHALL 执行OPTIMIZE进化策略
4. WHEN 进化执行完成 THEN 系统 SHALL 验证效果并沉淀知识到三层知识库
5. IF 进化失败 THEN 系统 SHALL 启用熔断保护并回滚到稳定状态

### 需求 10: 统一员工模型验证

**用户故事:** 作为HR管理员，我需要验证真实员工与数字员工的统一建模是否正确实现。

#### 验收标准

1. WHEN 真实员工登录 THEN 系统 SHALL 通过企业账号(钉钉/飞书)进行认证
2. WHEN 数字员工创建 THEN 系统 SHALL 分配唯一ID(employee://digital/{domain}/{name}/{instance})
3. WHEN 员工交互 THEN 系统 SHALL 区分互动式(真实员工)和自主式(数字员工)信息传递
4. WHEN 编制管理 THEN 系统 SHALL 确保所有实例遵循编制的能力边界和工具授权
5. WHEN 员工状态变更 THEN 系统 SHALL 同步更新权限和访问控制

### 需求 11: 贾维斯模式主动预判验证

**用户故事:** 作为业务用户，我需要验证系统的主动预判能力是否能在我开口之前就做好准备。

#### 验收标准

1. WHEN 时间规律触发 THEN 系统 SHALL 主动生成周报、会议提醒等时间预判任务
2. WHEN 系统事件发生 THEN 系统 SHALL 自动触发新员工入职流程、项目里程碑等事件预判
3. WHEN 用户行为模式识别 THEN 系统 SHALL 基于历史行为预测并推荐相关内容
4. WHEN 风险指标异常 THEN 系统 SHALL 主动预警项目延期、预算超支等风险预判
5. WHEN 预判执行 THEN 系统 SHALL 通过多渠道(WebSocket、邮件、钉钉)进行通知

### 需求 12: 性能与监控验证

**用户故事:** 作为系统监控员，我需要验证系统的性能指标是否达到预期标准。

#### 验收标准

1. WHEN 对话响应时间测试 THEN 执行层响应 SHALL 小于500ms
2. WHEN 复杂推理任务测试 THEN 决策层响应 SHALL 小于3秒
3. WHEN 并发会话测试 THEN 系统 SHALL 支持1000+并发会话
4. WHEN Rust原生组件性能测试 THEN 音频处理 SHALL 小于5ms，消息吞吐 SHALL 大于1M msg/s
5. WHEN 健康检查执行 THEN 系统 SHALL 返回各组件的健康状态和性能指标