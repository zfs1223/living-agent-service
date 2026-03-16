# 知识体系设计

> 成长型知识体系架构

---

## 一、三层知识库架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    三层知识库架构                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  L1: 神经元私有知识 (Neuron Private Knowledge)                               │
│  ├── 存储: SQLite本地                                                        │
│  ├── 范围: 单个神经元私有                                                    │
│  ├── 内容: 个人经验、对话历史、学习记录                                      │
│  └── 特点: 离线可用、快速访问、隐私隔离                                      │
│                                                                             │
│  L2: 大脑领域知识 (Brain Domain Knowledge)                                   │
│  ├── 存储: PostgreSQL + Qdrant (部门命名空间)                                │
│  ├── 范围: 部门内共享                                                       │
│  ├── 内容: 部门最佳实践、业务规则、专业知识                                  │
│  └── 特点: 部门隔离、权限控制、知识晋升                                      │
│                                                                             │
│  L3: 共享知识库 (Shared Knowledge Base)                                      │
│  ├── 存储: PostgreSQL + Qdrant (全局命名空间)                               │
│  ├── 范围: 全企业共享                                                       │
│  ├── 内容: 通用知识、公司制度、跨部门经验                                    │
│  └── 特点: 全局可用、知识传承、版本管理                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、知识条目模型

```java
public class KnowledgeEntry {
    private String id;
    private String title;
    private String content;
    private KnowledgeType type;        // BEST_PRACTICE / EXPERIENCE / RULE / FACT
    private String domain;             // 所属领域/部门
    private int layer;                 // 层级 (1/2/3)
    private double importance;         // 重要性 (0-1)
    private double validity;           // 有效性 (0-1)
    private List<String> tags;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;
    private int accessCount;
    private int usefulnessScore;
}
```

---

## 三、知识进化机制

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    知识进化流程                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. 知识获取                                                                 │
│  ├── 从对话中提取                                                           │
│  ├── 从文档中导入                                                           │
│  ├── 从工具使用中学习                                                       │
│  └── 从用户反馈中积累                                                       │
│                                                                             │
│  2. 知识验证                                                                 │
│  ├── 人工审核                                                               │
│  ├── 自动验证 (与其他知识对比)                                              │
│  └── 实践验证 (使用效果评估)                                                │
│                                                                             │
│  3. 知识晋升                                                                 │
│  ├── L1 → L2: 个人知识被部门验证后晋升                                       │
│  ├── L2 → L3: 部门知识被多部门验证后晋升                                     │
│  └── 晋升条件: 使用次数、有效性评分、跨部门引用                              │
│                                                                             │
│  4. 知识衰退                                                                 │
│  ├── 长期未使用: 有效性降低                                                 │
│  ├── 与新知识冲突: 标记待审核                                               │
│  └── 用户反馈差: 降低重要性                                                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 四、进化信号类型

| 信号类型 | 说明 | 触发条件 |
|----------|------|---------|
| **ERROR** | 错误信号 | 执行失败、结果错误 |
| **OPPORTUNITY** | 机会信号 | 发现优化机会 |
| **STABILITY** | 稳定信号 | 连续成功执行 |
| **DRIFT** | 漂移信号 | 行为偏离预期 |
| **CAPABILITY_GAP** | 能力缺口 | 缺少必要能力 |
| **PERFORMANCE** | 性能信号 | 性能指标变化 |
| **USER_REQUEST** | 用户请求 | 用户明确要求 |
| **SYSTEM_EVENT** | 系统事件 | 系统状态变化 |

---

## 五、进化策略

| 策略 | 说明 | 使用场景 |
|------|------|---------|
| **REPAIR** | 修复现有技能 | 错误信号触发 |
| **OPTIMIZE** | 优化技能性能 | 机会信号触发 |
| **INNOVATE** | 创造新技能 | 能力缺口触发 |
| **DEFER** | 延迟处理 | 稳定信号触发 |
| **ESCALATE** | 上报人工 | 无法自动处理 |

---

## 六、人格系统

> 人格系统采用层级设计：
> - **BrainPersonality** - 部门大脑人格，定义部门整体风格（领导层）
> - **EmployeePersonality** - 员工人格，可继承或覆盖部门默认（员工层）

```java
// 人格参数 - 基础四维参数
// 详见: docs/07-unified-employee-model.md

// 业务大脑使用部门默认人格
// 数字员工使用模板配置人格（可从部门大脑继承）
// 真实员工使用行为推断人格

public class PersonalityParams {
    private double rigor;           // 严谨度 (0-1)
    private double creativity;      // 创造力 (0-1)
    private double riskTolerance;   // 风险容忍 (0-1)
    private double obedience;       // 服从度 (0-1)
}

// 员工人格可从大脑人格创建
EmployeePersonality empPersonality = EmployeePersonality.fromBrainPersonality(brainPersonality);
```

### 大脑人格默认配置

> 这些配置用于业务大脑的默认人格，数字员工创建时可覆盖

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

---

## 七、熔断器设计

```java
public class EvolutionCircuitBreaker {
    private CircuitState state;              // CLOSED / OPEN / HALF_OPEN
    private int repairLoopCount;             // 修复循环计数
    private int failureStreak;               // 连续失败计数
    private Instant lastTripTime;            // 上次熔断时间
    
    // 熔断阈值
    private static final int REPAIR_LOOP_THRESHOLD = 3;
    private static final int FAILURE_STREAK_THRESHOLD = 5;
    private static final Duration COOLDOWN_PERIOD = Duration.ofMinutes(30);
}
```

---

## 八、相关文档

- [02-architecture.md](./02-architecture.md) - 架构设计
- [06-evolution-system.md](./06-evolution-system.md) - 进化系统
- [07-unified-employee-model.md](./07-unified-employee-model.md) - 统一员工模型 (人格系统)
- [08-database-design.md](./08-database-design.md) - 数据库设计
- [10-operation-assessment.md](./10-operation-assessment.md) - 运营评判系统
