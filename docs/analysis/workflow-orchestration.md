# 项目开发流程编排设计

## 一、整体架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    项目开发流程编排架构                                        │
├─────────────────────────────────────────────────────────────────────────────┤

│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    WorkflowMonitor (监督层)                          │   │
│  │                    - 超时检测                                        │   │
│  │                    - 心跳监控                                        │   │
│  │                    - 自动恢复                                        │   │
│  │                    - 告警通知                                        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│                                    ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    ProjectDevelopmentNeuron                          │   │
│  │                    (项目开发流程神经元)                                │   │
│  │  - 接收项目操作指令                                                   │   │
│  │  - 协调 WorkflowOrchestrator                                         │   │
│  │  - 发布流程事件                                                       │   │
│  │  - 定期发送心跳                                                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│                                    ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    WorkflowOrchestrator                              │   │
│  │                    (流程编排器)                                        │   │
│  │  - 管理工作流生命周期                                                 │   │
│  │  - 调度阶段处理器                                                     │   │
│  │  - 发布阶段事件                                                       │   │
│  │  - 注册执行到 Monitor                                                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│         ┌──────────────────────────┼──────────────────────────┐            │
│         ▼                          ▼                          ▼            │
│  ┌─────────────┐           ┌─────────────┐           ┌─────────────┐      │
│  │PhaseHandler │           │PhaseHandler │           │PhaseHandler │      │
│  │ 市场分析     │           │ 需求分析     │           │ 方案设计     │      │
│  └─────────────┘           └─────────────┘           └─────────────┘      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 二、监督机制 (防止"摸鱼")

### 2.1 问题场景

| 场景 | 风险 | 解决方案 |
|------|------|---------|
| 数字员工卡住不动 | 流程阻塞 | 超时检测 + 自动重试 |
| 数字员工崩溃 | 心跳丢失 | 心跳监控 + 自动恢复 |
| 阶段执行失败 | 流程中断 | 失败重试 + 告警 |
| 长时间无响应 | 死锁 | 死锁检测 + 取消流程 |

### 2.2 WorkflowMonitor (工作流监督器)

**核心职责**:
- 监控所有活跃的工作流执行
- 检测超时和心跳丢失
- 自动重试失败的任务
- 发送告警通知

**超时配置**:
```java
// 各阶段默认超时时间
MARKET_ANALYSIS: 24小时
REQUIREMENT: 8小时
DESIGN: 16小时
DEVELOPMENT: 72小时
TESTING: 24小时
DEPLOYMENT: 4小时
OPERATION: 8小时
AFTER_SALES: 48小时
```

### 2.3 心跳机制

**HeartbeatProvider 接口**:
```java
public interface HeartbeatProvider {
    String getProviderId();
    void startHeartbeat(HeartbeatCallback callback);
    void stopHeartbeat();
}
```

**AbstractNeuron 实现**:
- 每 60 秒发送一次心跳
- 心跳丢失超过 5 分钟触发告警
- 自动尝试恢复执行

### 2.4 告警类型

| 告警类型 | 说明 | 处理方式 |
|---------|------|---------|
| TIMEOUT | 阶段执行超时 | 自动重试 (最多3次) |
| HEARTBEAT_MISSED | 心跳丢失 | 重新执行阶段 |
| DEADLOCK | 死锁检测 | 取消工作流 |
| RETRY | 重试通知 | 记录日志 |
| MANUAL_INTERVENTION | 需要人工介入 | 发送通知 |

### 2.5 自动恢复流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    自动恢复流程                                              │
├─────────────────────────────────────────────────────────────────────────────┤

│                                                                             │
│  执行开始 ──▶ 注册到 Monitor ──▶ 定时检查 (每分钟)                           │
│                                     │                                       │
│                    ┌────────────────┼────────────────┐                      │
│                    ▼                ▼                ▼                      │
│               [超时检测]      [心跳检测]      [失败检测]                      │
│                    │                │                │                      │
│                    ▼                ▼                ▼                      │
│              超时? ──▶ 重试次数 < 3?                                         │
│                    │           │                                              │
│                   是          是 ──▶ 自动重试                                 │
│                    │           │                                              │
│                   否          否 ──▶ 标记死锁                                 │
│                    │                                                          │
│                    ▼                                                          │
│              取消工作流 ──▶ 发送告警                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 二、核心组件

### 2.1 WorkflowOrchestrator (流程编排器)

**职责**:
- 管理项目工作流的生命周期
- 调度各阶段处理器
- 发布阶段事件到 Channel
- 处理阶段完成回调

**核心方法**:
```java
// 启动工作流
WorkflowExecution startWorkflow(String projectId)

// 推进到下一阶段
void advancePhase(String projectId)

// 执行指定阶段
void executePhase(String projectId, ProjectPhase phase)

// 阶段完成回调
void onPhaseComplete(String projectId, ProjectPhase phase, Map<String, Object> result)

// 暂停/恢复/取消工作流
void pauseWorkflow(String projectId)
void resumeWorkflow(String projectId)
void cancelWorkflow(String projectId)
```

### 2.2 PhaseHandler (阶段处理器接口)

```java
public interface PhaseHandler {
    void execute(WorkflowContext context);
    String getName();
    String getDescription();
}
```

### 2.3 WorkflowContext (工作流上下文)

**包含信息**:
- 项目 ID 和名称
- 当前阶段
- 项目元数据
- 上下文数据
- 错误信息

**核心方法**:
```java
void setData(String key, Object value)
<T> T getData(String key)
void completePhase(Map<String, Object> result)
void requestAdvance()
```

### 2.4 WorkflowExecution (工作流执行状态)

**状态枚举**:
- RUNNING - 运行中
- PAUSED - 已暂停
- COMPLETED - 已完成
- CANCELLED - 已取消

**包含信息**:
- 项目 ID
- 当前阶段
- 各阶段结果
- 开始/结束时间

## 三、8个开发阶段

| 阶段 | 代码 | 处理器 | 核心任务 |
|------|------|--------|---------|
| 市场分析 | market_analysis | MarketAnalysisHandler | 竞品分析、市场调研、需求收集 |
| 需求分析 | requirement | RequirementAnalysisHandler | 需求文档解析、功能点拆解 |
| 方案设计 | design | DesignHandler | 架构设计、技术选型、接口设计 |
| 开发实施 | development | DevelopmentHandler | 代码编写、单元测试、代码审查 |
| 测试验收 | testing | TestingHandler | 测试用例生成、自动化测试 |
| 上线部署 | deployment | DeploymentHandler | CI/CD配置、环境部署 |
| 运营维护 | operation | OperationHandler | 监控配置、性能优化 |
| 售后服务 | after_sales | AfterSalesHandler | 用户反馈、迭代改进 |

## 四、Channel 通信

### 4.1 订阅的 Channel

| Channel | 用途 |
|---------|------|
| channel://tech/projects | 接收项目操作指令 |
| channel://workflow/events | 接收工作流事件 |

### 4.2 发布的 Channel

| Channel | 用途 |
|---------|------|
| channel://tech/tasks | 发布任务指令 |
| channel://workflow/commands | 发布工作流命令 |

### 4.3 消息格式

**项目创建**:
```json
{
  "action": "create_project",
  "name": "项目名称",
  "description": "项目描述",
  "department": "tech",
  "managerId": "user_001"
}
```

**启动工作流**:
```json
{
  "action": "start_workflow",
  "projectId": "proj_001"
}
```

**阶段完成事件**:
```json
{
  "eventType": "phase_complete",
  "projectId": "proj_001",
  "phase": "development",
  "result": { ... }
}
```

## 五、自动推进机制

工作流支持自动推进到下一阶段，条件：
1. 当前阶段进度达到 100%
2. 当前阶段不是最后一个阶段 (AFTER_SALES)

```java
private boolean shouldAutoAdvance(Project project, ProjectPhase completedPhase) {
    ProjectPhaseRecord record = project.getPhases().stream()
        .filter(p -> p.getPhase() == completedPhase)
        .findFirst()
        .orElse(null);
    
    if (record == null) return false;
    
    return record.getProgress() >= 100.0 
        && !completedPhase.equals(ProjectPhase.AFTER_SALES);
}
```

## 六、扩展机制

### 6.1 自定义阶段处理器

```java
public class CustomPhaseHandler implements PhaseHandler {
    @Override
    public void execute(WorkflowContext context) {
        // 自定义处理逻辑
        Map<String, Object> result = new HashMap<>();
        result.put("status", "completed");
        context.completePhase(result);
    }
    
    @Override
    public String getName() { return "自定义处理器"; }
    
    @Override
    public String getDescription() { return "自定义处理逻辑描述"; }
}

// 注册处理器
orchestrator.registerHandler(ProjectPhase.CUSTOM, new CustomPhaseHandler());
```

### 6.2 事件监听

通过订阅 `channel://workflow/events` 可以监听所有工作流事件：
- PHASE_START - 阶段开始
- PHASE_COMPLETE - 阶段完成
- WORKFLOW_STARTED - 工作流启动
- WORKFLOW_COMPLETED - 工作流完成

## 七、文件结构

```
living-agent-core/src/main/java/com/livingagent/core/
├── workflow/
│   ├── WorkflowOrchestrator.java      # 流程编排器
│   ├── WorkflowContext.java           # 工作流上下文
│   ├── WorkflowExecution.java         # 执行状态
│   ├── PhaseHandler.java              # 阶段处理器接口
│   └── handlers/
│       ├── MarketAnalysisHandler.java
│       ├── RequirementAnalysisHandler.java
│       ├── DesignHandler.java
│       ├── DevelopmentHandler.java
│       ├── TestingHandler.java
│       ├── DeploymentHandler.java
│       ├── OperationHandler.java
│       └── AfterSalesHandler.java
├── neuron/impl/
│   └── ProjectDevelopmentNeuron.java  # 项目开发神经元
└── project/
    ├── Project.java
    ├── ProjectPhase.java
    ├── ProjectService.java
    └── impl/
        └── ProjectServiceImpl.java
```
