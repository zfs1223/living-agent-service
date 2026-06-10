# 知识记忆模块

> 版本：2026-05-18 | 路径：living-agent-core/knowledge/, memory/

---

## 边界说明

> **重要**：Knowledge 和 Memory 是两个独立的层面，不应混为一谈。

| 层面 | 职责 | 存储后端 | 使用场景 |
| --- | --- | --- | --- |
| **Knowledge** | 结构化治理后的知识资产 | PostgreSQL + Qdrant | L1/L2/L3 知识库、知识晋升、知识治理 |
| **Memory** | 底层记忆存储和检索 | SQLite / MemOS / MemPalace | 会话记忆、短期记忆、长期记忆存储 |

**关系**：
- Memory 是知识提取的来源（从记忆中提取 → 验证 → 晋升为知识）
- Knowledge 是治理后的结果（结构化、可检索、有生命周期）
- 两者可以同步、桥接、互相导入，但**不应混为同一层**

**详细说明**：
- 知识层详见本文档"三层知识库"部分
- 记忆层详见本文档"Memory 记忆模块"部分
- 记忆后端（SQLite/MemOS/MemPalace）详见 `docs/old/memory.md`

---

## 三层知识库

| 层级 | KnowledgeScope | 可见性 | 说明 |
|------|------|--------|------|
| L1 | `L1_PRIVATE` | 仅创建者 | 个人经验积累 |
| L2 | `L2_DEPARTMENT` | 本部门 | 部门最佳实践 |
| L3 | `L3_SHARED` | 全企业 | 制度规范 |

## KnowledgeEntry 结构

```java
// KnowledgeEntry.java — class（非 record），20+ 字段
public class KnowledgeEntry {
    String entryId;                // 条目 ID
    String key;                    // 知识键
    String content;                // 知识内容
    String category;               // 分类
    String knowledgeType;          // 知识类型
    double importance;             // 重要性 0.0-1.0
    double validity;               // 有效性 0.0-1.0
    KnowledgeScope scope;          // 作用域（L1/L2/L3）
    String scopeIdentifier;        // 作用域标识
    String brainDomain;            // 所属大脑域
    String neuronId;               // 神经元 ID
    float[] vector;                // 向量嵌入
    double confidence;             // 置信度
    boolean verified;              // 是否已验证
    String source;                 // 来源
    String promotedFrom;           // 晋升来源层级
    Map<String, String> tags;      // 标签
    Map<String, Object> metadata;  // 元数据
    LocalDateTime expiresAt;       // 过期时间
    int accessCount;               // 访问次数
    double relevanceScore;         // 相关度评分
    double relevance;              // 相关性
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    LocalDateTime lastAccessedAt;
}

enum KnowledgeScope {
    L1_PRIVATE("profile", "私有知识 - 仅所有者可访问"),
    L2_DEPARTMENT("department", "部门知识 - 部门成员可访问"),
    L3_SHARED("shared", "共享知识 - 所有正式员工可访问")
}
```

> **待实现**：`KnowledgeStatus` 枚举（DRAFT/PUBLISHED/ARCHIVED）尚未在代码中实现，当前知识条目缺少生命周期状态管理。

## KnowledgeManager 核心逻辑

```java
// KnowledgeManager.java — 方法按功能分组

// 存储层
storePrivate(key, content, neuronId, brainDomain)   // L1 私有存储
storeDomain(key, content, department, brainDomain)   // L2 部门存储
storeShared(key, content, brainDomain)               // L3 共享存储

// 检索层
retrieve(key)                     → KnowledgeEntry
retrieveFromLayer(key, scope)     → KnowledgeEntry
search(query, limit)              → List<KnowledgeEntry>
searchInLayer(query, scope, limit)→ List<KnowledgeEntry>
searchSimilar(queryVector, limit) → List<KnowledgeEntry>  // 向量相似搜索
hybridSearch(query, limit)        → List<KnowledgeEntry>  // 混合搜索

// 晋升层
promoteToDomain(key)              // L1 → L2（无条件移动）
promoteToShared(key)              // L2 → L3（无条件移动）
moveToLayer(key, targetLayer)     // 通用层级移动

// 进化层
addExperience(key, content)       // 添加经验
recordBestPractice(key, content)  // 记录最佳实践
shareKnowledge(key)               // 共享知识
evolveKnowledge(key, newContent)  // 知识进化
mergeKnowledge(sourceKey, targetKey) // 知识合并
propagateKnowledge(key, targetScope) // 知识传播

// 评估层
assessQuality(key)                // 评估知识质量
```

> **注意**：当前 `promoteToDomain()` 和 `promoteToShared()` 为无条件层级移动，文档原设计的晋升条件（验证≥3次、专家审核≥3人）尚未实现。

## 晋升机制（当前实现）

```
L1 (私有)
    │
    ├── promoteToDomain()：无条件移动
    │
    ▼
L2 (部门共享)
    │
    ├── promoteToShared()：无条件移动
    │
    ▼
L3 (企业共享)
```

## Memory 记忆模块

```java
// Memory.java — 全异步接口
interface Memory {
    CompletableFuture<Void> store(String key, String content, MemoryCategory category, String sessionId);
    CompletableFuture<List<MemoryEntry>> recall(String query, int limit, String sessionId);
    CompletableFuture<Optional<MemoryEntry>> get(String key);
    CompletableFuture<List<MemoryEntry>> list(MemoryCategory category, String sessionId);
    CompletableFuture<Boolean> forget(String key);
    CompletableFuture<Integer> count();
    CompletableFuture<Boolean> healthCheck();
}

enum MemoryCategory {
    CORE,           // 核心长期记忆
    DAILY,          // 日常记忆
    CONVERSATION,   // 会话记忆
    CUSTOM          // 自定义分类
}
```

> **注意**：所有 Memory 方法返回 `CompletableFuture`，支持异步 I/O 操作。`sessionId` 参数支持会话隔离。

## 代码路径

```
knowledge/
├── KnowledgeManager.java          # 知识管理接口
├── KnowledgeEntry.java            # 知识条目（class）
├── KnowledgeBase.java             # 知识库接口（含 searchSimilar/hybridSearch）
├── KnowledgeScope.java            # 作用域枚举（L1_PRIVATE/L2_DEPARTMENT/L3_SHARED）
└── impl/
    ├── KnowledgeManagerImpl.java  # 知识管理实现
    ├── LayeredKnowledgeBaseImpl.java # 分层知识库实现
    └── NativeKnowledgeBase.java   # Native 知识库实现

memory/
├── Memory.java                    # 记忆接口（全异步）
├── MemoryCategory.java            # 记忆分类枚举
├── MemoryEntry.java               # 记忆条目
└── impl/
    └── MemoryServiceImpl.java     # 记忆服务实现

database/entity/
├── KnowledgeEntryEntity.java      # 知识条目 JPA 实体
└── (KnowledgeScope 在 core 包中)

database/repository/
└── KnowledgeEntryRepository.java  # 知识条目 JPA Repository
```

## 快速定位

| 需求 | 文件 |
|------|------|
| 添加知识 | `KnowledgeManager.storePrivate/storeDomain/storeShared()` |
| 修改晋升逻辑 | `KnowledgeManagerImpl.promoteToDomain/promoteToShared()` |
| 查询知识 | `KnowledgeManager.search/hybridSearch()` |
| 向量搜索 | `KnowledgeBase.searchSimilar()` |
| 修改记忆存储 | `MemoryServiceImpl.java` |
| 修改知识实体 | `KnowledgeEntryEntity.java` |
