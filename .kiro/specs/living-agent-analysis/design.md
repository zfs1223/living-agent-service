# Living Agent Service 项目分析与测试验证设计文档

## 概述

本设计文档基于Living Agent Service的核心框架，详细设计了项目分析方法和董事长场景测试方案。设计重点关注三层LLM架构、神经元群聊机制、权限隔离体系和自主进化能力的验证。

## 架构设计

### 测试架构概览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Living Agent Service 测试验证架构                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  测试场景层 (Test Scenario Layer)                                    │   │
│  │  ├── 董事长项目需求场景 (Enterprise Project Requirement)                │   │
│  │  ├── 权限隔离验证场景 (Permission Isolation Verification)            │   │
│  │  ├── 神经元协作场景 (Neuron Collaboration)                           │   │
│  │  └── 自主进化触发场景 (Autonomous Evolution Trigger)                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  验证执行层 (Verification Execution Layer)                            │   │
│  │  ├── 前端WebSocket连接验证                                            │   │
│  │  ├── 后端路由逻辑验证                                                  │   │
│  │  ├── 神经元Channel通信验证                                            │   │
│  │  └── 数据持久化验证                                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  监控分析层 (Monitoring Analysis Layer)                               │   │
│  │  ├── 性能指标收集                                                     │   │
│  │  ├── 错误日志分析                                                     │   │
│  │  ├── 流程完整性检查                                                   │   │
│  │  └── 结果报告生成                                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 核心组件设计

#### 1. 项目规则分析器 (Project Rules Analyzer)

**职责**: 分析Living Agent Service的实际代码实现，提取项目规则

**核心功能**:
- 三层LLM架构分析
- 权限控制机制梳理
- 神经元路由逻辑解析
- WebSocket通信流程分析
- Docker部署配置解析

**实现方式**:
```java
public class ProjectRulesAnalyzer {
    // 分析ChatNeuronRouter的路由规则
    public RoutingRules analyzeRoutingRules();
    
    // 分析AccessLevel权限体系
    public PermissionMatrix analyzePermissionMatrix();
    
    // 分析三层LLM架构配置
    public LLMArchitecture analyzeLLMArchitecture();
    
    // 分析神经元注册和发现机制
    public NeuronTopology analyzeNeuronTopology();
}
```

#### 2. 董事长场景测试器 (Enterprise Scenario Tester)

**职责**: 模拟董事长(账号:18970718886)给出项目需求的完整流程

**测试场景设计**:
```
董事长登录 → 权限验证(FULL) → 输入项目需求 → 意图分类(COMPLEX_TASK) 
→ 路由到MainBrain → 协调部门大脑 → 调用相关技能 → 生成项目规划 → 返回结果
```

**核心测试用例**:
- 基于"F:\SoarCloudAI\docker\AI企业管理智能体系统 软件概要设计文档 (1).docx"的项目需求
- 验证从前端输入到后端处理的完整数据流
- 检查MainBrain的跨部门协调能力
- 验证技能系统的调用和执行

#### 3. 权限隔离验证器 (Permission Isolation Verifier)

**职责**: 验证不同权限级别用户的访问控制是否正确实现

**权限级别测试矩阵**:
```
┌─────────────┬─────────────┬─────────────┬─────────────┬─────────────┐
│ 权限级别     │ CHAT_ONLY   │ LIMITED     │ DEPARTMENT  │ FULL        │
├─────────────┼─────────────┼─────────────┼─────────────┼─────────────┤
│ Qwen3Neuron │ ✅ 允许      │ ✅ 允许      │ ✅ 允许      │ ✅ 允许      │
│ ToolNeuron  │ ❌ 拒绝      │ ❌ 拒绝      │ ✅ 允许      │ ✅ 允许      │
│ 部门大脑     │ ❌ 拒绝      │ 🔒 限制      │ 🏢 本部门    │ ✅ 全部      │
│ MainBrain   │ ❌ 拒绝      │ ❌ 拒绝      │ ❌ 拒绝      │ ✅ 允许      │
└─────────────┴─────────────┴─────────────┴─────────────┴─────────────┘
```

#### 4. 神经元通信验证器 (Neuron Communication Verifier)

**职责**: 验证神经元之间的Channel通信机制

**通信模式验证**:
- 单播通信: 点对点消息传递
- 广播通信: 一对多消息分发
- 优先级队列: 重要消息优先处理
- 轮询分发: 负载均衡消息分配

**Channel通信流程**:
```
发布者 → ChannelManager.publish() → Channel → ChannelSubscriber.onMessage() → 订阅者
```

## 数据模型设计

### 测试结果数据模型

```java
public class TestResult {
    private String testId;
    private String scenarioName;
    private TestStatus status;
    private long executionTime;
    private Map<String, Object> metrics;
    private List<String> errors;
    private String detailReport;
}

public class PerformanceMetrics {
    private long responseLatency;        // 响应延迟
    private int concurrentSessions;      // 并发会话数
    private double cpuUsage;            // CPU使用率
    private double memoryUsage;         // 内存使用率
    private int messagesThroughput;     // 消息吞吐量
}

public class PermissionTestCase {
    private AccessLevel userLevel;
    private String targetResource;
    private boolean expectedAccess;
    private boolean actualAccess;
    private String denialReason;
}
```

### 项目需求分析模型

```java
public class ProjectRequirement {
    private String documentPath;        // 文档路径
    private String content;            // 需求内容
    private RequirementType type;      // 需求类型
    private Priority priority;         // 优先级
    private List<String> departments;  // 涉及部门
    private List<String> skills;       // 需要技能
}

public class ProjectAnalysisResult {
    private String projectName;
    private String description;
    private List<String> phases;       // 项目阶段
    private List<String> deliverables; // 交付物
    private Map<String, String> departmentTasks; // 部门任务分配
    private EstimatedEffort effort;    // 工作量估算
}
```

## 错误处理设计

### 错误分类体系

```java
public enum TestErrorType {
    CONNECTION_ERROR,      // 连接错误
    PERMISSION_ERROR,      // 权限错误
    ROUTING_ERROR,         // 路由错误
    MODEL_ERROR,          // 模型错误
    TIMEOUT_ERROR,        // 超时错误
    DATA_ERROR,           // 数据错误
    SYSTEM_ERROR          // 系统错误
}
```

### 错误恢复策略

1. **连接错误**: 自动重连机制，最多重试3次
2. **权限错误**: 降级到安全默认处理，记录安全日志
3. **路由错误**: 回退到ChatNeuron，提供基础服务
4. **模型错误**: 切换到备用模型或云端API
5. **超时错误**: 返回部分结果，异步继续处理
6. **数据错误**: 数据校验和清洗，提供错误详情
7. **系统错误**: 熔断保护，记录详细错误信息

## 测试策略设计

### 董事长项目需求测试场景

**场景描述**: 董事长(18970718886)基于"AI企业管理智能体系统 软件概要设计文档"提出项目开发需求

**测试步骤**:
1. **前端连接验证**
   - 访问 http://localhost:8383
   - 使用董事长账号登录
   - 验证WebSocket连接建立

2. **权限验证**
   - 验证用户身份识别为INTERNAL_ENTERPRISE
   - 验证AccessLevel设置为FULL
   - 验证可访问所有功能模块

3. **项目需求输入**
   - 上传或引用文档: "F:\SoarCloudAI\docker\AI企业管理智能体系统 软件概要设计文档 (1).docx"
   - 输入需求: "请基于这个文档，帮我制定一个完整的AI企业管理智能体系统开发计划"

4. **路由验证**
   - 验证ChatIntentClassifier识别为COMPLEX_TASK
   - 验证ChatNeuronRouter路由到MainBrain
   - 验证MainBrain接收并处理请求

5. **协作验证**
   - 验证MainBrain协调TechBrain(技术规划)
   - 验证MainBrain协调AdminBrain(文档管理)
   - 验证MainBrain协调FinanceBrain(预算评估)
   - 验证MainBrain协调HrBrain(人员配置)

6. **技能调用验证**
   - 验证调用项目管理相关技能
   - 验证调用文档分析技能
   - 验证调用架构设计技能
   - 验证调用成本估算技能

7. **结果验证**
   - 验证返回完整的项目开发计划
   - 验证包含技术架构建议
   - 验证包含人员配置方案
   - 验证包含时间进度安排
   - 验证包含预算估算

### 性能基准测试

**响应时间基准**:
- 简单对话(Qwen3Neuron): < 500ms
- 工具调用(ToolNeuron): < 2s
- 复杂任务(MainBrain): < 3s
- 跨部门协作: < 5s

**并发性能基准**:
- 单机并发会话: 1000+
- WebSocket连接数: 10000+
- Channel消息吞吐: 1M msg/s
- 数据库查询QPS: 10K+

### 集成测试设计

**Ollama集成测试**:
```bash
# 验证Ollama服务连接
curl http://host.docker.internal:11434/api/tags

# 验证模型可用性
curl http://host.docker.internal:11434/api/generate \
  -d '{"model": "qwen2.5:latest", "prompt": "Hello"}'
```

**数据库集成测试**:
- PostgreSQL连接和查询测试
- Qdrant向量存储和检索测试
- Redis缓存读写测试
- Neo4j图数据库操作测试

**Docker服务健康检查**:
```yaml
healthcheck:
  test: ["CMD-SHELL", "wget -qO- http://localhost:8382/api/system/status"]
  interval: 30s
  timeout: 10s
  retries: 5
```

## 监控和分析设计

### 实时监控指标

1. **系统性能指标**
   - CPU使用率
   - 内存使用率
   - GPU使用率(如果可用)
   - 磁盘I/O
   - 网络I/O

2. **业务性能指标**
   - 活跃会话数
   - 消息处理速度
   - 神经元响应时间
   - 技能调用成功率
   - 错误率统计

3. **模型性能指标**
   - 模型加载状态
   - 推理延迟
   - Token消耗统计
   - 模型切换频率

### 日志分析设计

**日志级别分类**:
- ERROR: 系统错误和异常
- WARN: 警告信息和降级处理
- INFO: 关键业务流程信息
- DEBUG: 详细调试信息

**关键日志点**:
- WebSocket连接建立/断开
- 用户权限验证
- 神经元路由决策
- Channel消息传递
- 技能调用执行
- 模型推理过程
- 数据库操作

### 测试报告设计

**报告结构**:
1. **执行摘要**
   - 测试概述
   - 总体结果
   - 关键发现

2. **详细结果**
   - 各测试用例结果
   - 性能指标统计
   - 错误分析

3. **问题和建议**
   - 发现的问题
   - 改进建议
   - 风险评估

4. **附录**
   - 详细日志
   - 配置信息
   - 环境信息