# 知识体系

> 成长型知识体系架构
>
> 本文档补充说明：
> - 知识、记忆、文档三者边界
> - L1/L2/L3 与 PRIVATE/DOMAIN/SHARED 的映射
> - 晋升与复制的区别
> - 知识治理与部门知识沉淀落点

## 一，三层知识库架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    三层知识库架构                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  L1: 神经元私有知识 (Neuron Private Knowledge)                               │
│  ├── 存储: SQLite本地 + PostgreSQL持久化                                        │
│  ├── 范围: 单个神经元私有                                                    │
│  ├── 内容: 个人经验、对话历史、学习记录                                      │
│  └── 特点: 离线可用、快速访问、隐私隔离、PostgreSQL持久化备份                        │
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

## 二，知识条目模型

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

## 三，知识进化机制

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

## 四，进化信号类型

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

## 五，进化策略

| 策略 | 说明 | 使用场景 |
|------|------|---------|
| **REPAIR** | 修复现有技能 | 错误信号触发 |
| **OPTIMIZE** | 优化技能性能 | 机会信号触发 |
| **INNOVATE** | 创造新技能 | 能力缺口触发 |
| **DEFER** | 延迟处理 | 稳定信号触发 |
| **ESCALATE** | 上报人工 | 无法自动处理 |

## 六，向量数据库集成

| 组件 | 状态 | 说明 |
|------|------|------|
| **QdrantConfig** | ✅ 已完成 | Qdrant客户端配置、连接管理 |
| **QdrantVectorService** | ✅ 已完成 | Spring Service，完整向量CRUD操作 |
| **QdrantVectorStore** | ✅ 已完成 | 向量存储类，搜索、过滤、集合管理 |
| **VoicePrintServiceImpl** | ✅ 已完成 | 声纹识别服务，已集成Qdrant (192维向量) |

## 七，Kafka 消息中间件

**核心依赖** - Kafka 是神经元通讯的基础设施，支持进化信号的传递

```yaml
# docker-compose.yml
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_NUM_PARTITIONS: 3
    depends_on:
      - zookeeper
```

**Kafka 在进化系统中的作用:**
- 进化信号 (EvolutionSignal) 的异步传递
- 知识更新事件的发布/订阅
- 系统事件的广播通知
- 任务分发到不同大脑

## 八，知识、记忆、文档的边界

- **文档**：原始输入，制度文本、模板、流程说明、版本保留
- **记忆**：运行态上下文、会话历史、短中期经验
- **知识**：可复用语义、规则，最佳实践，制度性内容

这三者可以相互转化，但不能混为一谈。文档可以生成知识，知识可以沉淀为制度，记忆可以作为知识提取的来源，但检索与写入路径必须区分作用域。

## 九，知识晋升与复制的区别

- **复制**：只是把内容复制到另一个位置
- **晋升**：经过验证、评分、引用与治理规则确认后，进入更高层级

知识体系里，L1/L2/L3 的移动必须是"晋升"语义，而不是简单复制。

## 十，结合补充治理资料后的澄清

- `documents/shared/governance/04-knowledge-governance.md`、`documents/shared/governance/00-index.md` 已经明确了知识治理是企业治理的一部分，而不是孤立的数据存储。
- `documents/shared/company/fixed-employee-document-workflow.md`、`documents/shared/company/requirement-routing-state-machine.md` 说明文档与需求流可以作为知识来源，但它们不是知识本体。
- `documents/department/**` 的部门目录可以作为 L2 领域知识的沉淀落点，但晋升仍需经过验证与治理。

## 十一，建议修补点

- 固定 L1/L2/L3 与 PRIVATE/DOMAIN/SHARED 的映射
- 增补"晋升 != 复制"的明确说明
- 增补知识、记忆、文档边界说明
- 增补部门知识落点与治理规则的关系

## 十二，结论

知识体系已经具备完整骨架，但要真正支撑企业生命体，必须把知识来源、知识边界、知识晋升和知识治理明确成可执行规则，否则知识库会退化成"文本仓库"，无法承担组织记忆与能力传承。
