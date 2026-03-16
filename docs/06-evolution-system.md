# 进化系统设计

> 智能进化系统架构

---

## 一、进化系统概述

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    进化系统架构                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  核心组件:                                                                   │
│  ├── EvolutionSignal - 进化信号                                             │
│  ├── SignalExtractor - 信号提取器                                           │
│  ├── EvolutionDecisionEngine - 决策引擎                                     │
│  ├── EvolutionExecutor - 执行器                                             │
│  ├── EvolutionCircuitBreaker - 熔断器                                       │
│  ├── EmployeePersonality - 人格系统 (引用 07-unified-employee-model.md)     │
│  └── EvolutionMemoryGraph - 进化记忆                                        │
│                                                                             │
│  进化流程:                                                                   │
│  信号检测 → 信号提取 → 决策引擎 → 策略执行 → 结果记录 → 熔断保护              │
│                                                                             │
│  关联系统:                                                                   │
│  ├── 统一员工模型 (07) - 数字员工进化影响人格配置                            │
│  ├── 知识体系 (05) - 进化产生的知识存入知识库                                │
│  └── 运营评判 (10) - 进化效果影响绩效考核                                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、信号检测机制

### 2.1 信号来源

| 来源 | 说明 | 示例 |
|------|------|------|
| **对话分析** | 从对话中提取 | 用户反馈"这个回答不对" |
| **日志分析** | 从执行日志提取 | 工具调用失败 |
| **指标分析** | 从性能指标提取 | 响应时间过长 |
| **用户反馈** | 直接用户反馈 | 点赞/点踩 |

### 2.2 信号提取器

```java
public interface SignalExtractor {
    List<EvolutionSignal> extractFromConversation(Conversation conv);
    List<EvolutionSignal> extractFromLogs(LogEntry[] logs);
    List<EvolutionSignal> extractFromMetrics(MetricData[] metrics);
    List<EvolutionSignal> extractFromUserFeedback(Feedback feedback);
}
```

---

## 三、决策引擎

### 3.1 决策流程

```
信号输入
    │
    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  EvolutionDecisionEngine                                                     │
│                                                                             │
│  1. 信号评估                                                                 │
│     ├── 信号类型分析                                                        │
│     ├── 信号强度计算                                                        │
│     └── 信号优先级排序                                                      │
│                                                                             │
│  2. 策略选择                                                                 │
│     ├── ERROR → REPAIR (修复)                                               │
│     ├── OPPORTUNITY → OPTIMIZE (优化)                                       │
│     ├── CAPABILITY_GAP → INNOVATE (创新)                                    │
│     ├── STABILITY → DEFER (延迟)                                            │
│     └── 复杂情况 → ESCALATE (上报)                                          │
│                                                                             │
│  3. 人格影响                                                                 │
│     ├── 严谨度高 → 倾向保守策略                                              │
│     ├── 创造力高 → 倾向创新策略                                              │
│     └── 风险容忍低 → 倾向延迟策略                                            │
│                                                                             │
│  4. 熔断检查                                                                 │
│     ├── 检查修复循环                                                        │
│     ├── 检查连续失败                                                        │
│     └── 决定是否熔断                                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
    │
    ▼
策略执行
```

### 3.2 策略映射

```java
public class DefaultEvolutionDecisionEngine implements EvolutionDecisionEngine {
    
    @Override
    public EvolutionStrategy decide(EvolutionSignal signal, BrainPersonality personality) {
        return switch (signal.type()) {
            case ERROR -> selectErrorStrategy(signal, personality);
            case OPPORTUNITY -> selectOpportunityStrategy(signal, personality);
            case CAPABILITY_GAP -> EvolutionStrategy.INNOVATE;
            case STABILITY -> EvolutionStrategy.DEFER;
            case USER_REQUEST -> EvolutionStrategy.INNOVATE;
            default -> EvolutionStrategy.DEFER;
        };
    }
    
    private EvolutionStrategy selectErrorStrategy(EvolutionSignal signal, BrainPersonality p) {
        if (p.rigor() > 0.8) return EvolutionStrategy.REPAIR;
        if (p.creativity() > 0.7) return EvolutionStrategy.INNOVATE;
        return EvolutionStrategy.REPAIR;
    }
}
```

---

## 四、执行器

### 4.1 执行流程

```java
public interface EvolutionExecutor {
    EvolutionResult execute(EvolutionSignal signal);
    EvolutionResult executeRepair(EvolutionSignal signal);
    EvolutionResult executeOptimize(EvolutionSignal signal);
    EvolutionResult executeInnovate(EvolutionSignal signal);
}
```

### 4.2 执行结果

```java
public class EvolutionResult {
    private Status status;           // SUCCESS / FAILED / SKIPPED / DEFERRED / ESCALATED
    private String strategy;         // 使用的策略
    private String description;      // 结果描述
    private List<String> changes;    // 变更列表
    private boolean immediateEffective; // 是否即时生效
}
```

---

## 五、熔断器

### 5.1 熔断状态

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  熔断器状态机                                                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  CLOSED (关闭) ──失败连续5次──→ OPEN (打开)                                  │
│       ↑                           │                                        │
│       │                           │ 30分钟冷却                              │
│       │                           ▼                                        │
│  成功执行 ←── HALF_OPEN (半开) ←──┘                                         │
│                                                                             │
│  熔断触发条件:                                                               │
│  ├── 修复循环超过3次                                                         │
│  ├── 连续失败超过5次                                                         │
│  └── 冷却期内重复触发                                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 熔断器实现

```java
public class EvolutionCircuitBreaker {
    private CircuitState state = CircuitState.CLOSED;
    private int repairLoopCount = 0;
    private int failureStreak = 0;
    private Instant lastTripTime;
    
    public boolean shouldTrip(EvolutionSignal signal) {
        if (repairLoopCount >= REPAIR_LOOP_THRESHOLD) return true;
        if (failureStreak >= FAILURE_STREAK_THRESHOLD) return true;
        return false;
    }
    
    public void recordSuccess() {
        this.failureStreak = 0;
        this.repairLoopCount = 0;
        this.state = CircuitState.CLOSED;
    }
    
    public void recordFailure() {
        this.failureStreak++;
        if (shouldTrip(null)) {
            this.state = CircuitState.OPEN;
            this.lastTripTime = Instant.now();
        }
    }
}
```

---

## 六、进化记忆

### 6.1 记忆图谱

```java
public class EvolutionMemoryGraph {
    private Map<String, EvolutionEvent> events;
    private Map<String, List<String>> dependencies;
    
    public void recordEvent(EvolutionEvent event);
    public List<EvolutionEvent> getHistory(String skillId);
    public Optional<EvolutionEvent> findSimilar(String description);
}
```

### 6.2 进化事件

```java
public class EvolutionEvent {
    private String eventId;
    private String skillId;
    private EvolutionStrategy strategy;
    private EvolutionSignal signal;
    private EvolutionResult result;
    private Instant timestamp;
    private Map<String, Object> context;
}
```

---

## 七、即学即会能力

### 7.1 热加载机制

```java
@Service
public class SkillHotReloader {
    private WatchService watchService;
    private static final long DEBOUNCE_MS = 1000;
    
    @PostConstruct
    public void startWatching() {
        // 监听技能目录变化
        // 自动重载变更的技能
    }
    
    public void manualReload(String skillId);
}
```

### 7.2 REST API

| 接口 | 说明 |
|------|------|
| `GET /api/admin/skills` | 列出技能 |
| `POST /api/admin/skills/reload` | 重载技能 |
| `POST /api/admin/skills/generate` | 生成技能 |
| `POST /api/admin/skills/{name}/install` | 安装技能 |
| `DELETE /api/admin/skills/{name}` | 卸载技能 |
| `POST /api/admin/skills/{skill}/bind/{neuron}` | 绑定技能 |
| `POST /api/admin/evolution/trigger` | 触发进化 |

---

## 八、相关文档

- [05-knowledge-system.md](./05-knowledge-system.md) - 知识体系
- [07-unified-employee-model.md](./07-unified-employee-model.md) - 统一员工模型 (人格系统)
- [08-database-design.md](./08-database-design.md) - 数据库设计
- [09-proactive-prediction.md](./09-proactive-prediction.md) - 主动预判
- [10-operation-assessment.md](./10-operation-assessment.md) - 运营评判系统
