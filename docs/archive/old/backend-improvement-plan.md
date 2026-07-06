# 后端改造计划文档

> 基于 `brain-model-selector-improvement.md` 规划文档第 7 节，对照实际代码状态制定。
> 最后更新时间：2026-04-24
> 更新说明：第一至第四阶段已完成，待测试验证

## 改造概览

| 模块 | 改造类型 | 工作量 | 优先级 | 状态 |
|------|---------|--------|--------|------|
| EvolutionOrchestrator | 核心逻辑实现 | 大 | P0 | ✅ 已完成 (score, selectStrategy, runAutoAdjust, rollbackBrain) |
| EvolutionFeedbackService | 统计聚合实现 | 中 | P0 | ✅ 已完成 (statistics 补强，连续失败检测) |
| EvolutionScheduler | 新建定时任务模块 | 大 | P1 | ✅ 已完成 (接口/实现/配置) |
| DepartmentController | 接真实数据源 | 中 | P1 | ✅ 已完成 (部门列表/大脑配置/成员查询) |
| EvolutionAdminController | API 扩展 | 小 | P1 | ✅ 已存在，trigger-auto-adjust/history 已有 |
| BrainModelChangeHistory | 变更历史实体 | 小 | P0 | ✅ 已完成 (实体/Repository/迁移脚本) |

> 说明：
> - **已存在**：仓库中已有类或接口，逻辑基本可用；
> - **待补强**：类已存在，需要补核心实现；
> - **待新增**：当前仓库未见对应实现，需要新建；
> - 本表反映 2026-04-24 审查后的实际状态。

---

## 0. 代码状态校准

### ✅ 已完整实现 (确认无缺口)

- `ModelPoolController`：模型池 CRUD 完整，支持 provider/model 管理、测试、发现
- `BrainModelConfigController`：大脑模型绑定完整，支持 GET/PUT/DELETE/available
- `BrainConfig.tsx`：前端大脑配置页完整，含自动进化开关和手动回滚
- `AgentCreate.tsx`：个人数字助理创建页完整，模型选择已接入 modelPoolApi.models.list()
- `ModelPoolProviders.tsx`：前端模型池管理页完整，含17个供应商支持
- `BrainModelAssigner`：大脑模型绑定器已实现
- `BrainModelSelectorManager`：大脑模型选择器管理器已实现

### ⚠️ 部分实现 (需要补强)

- `EvolutionAdminController`：已有基础 API 入口 (POST /feedback, GET /feedback/recent, POST /rollback/{brainId})，但缺少 trigger-auto-adjust 和 history 接口
- `EvolutionOrchestrator`：已有类定义，但 score/selectStrategy/runAutoAdjust 核心方法需要补强
- `EvolutionFeedbackService`：已有类定义，但 statistics/record 统计聚合逻辑需要补强
- `DepartmentController` (gateway)：部门信息返回硬编码数据，getDepartmentBrains 返回假数据
- `DepartmentWebSocketHandler`：已有 processWithBrain 逻辑，但依赖 agentService.processTextAsync 间接调用，未直接查询 brain-models 配置

### ❌ 待新增 (当前不存在)

- `EvolutionScheduler` / `EvolutionSchedulerImpl`：定时任务模块不存在
- `EvolutionJobConfig`：Spring 定时任务配置不存在
- `BrainModelChangeHistory` 实体：大脑模型变更历史表不存在
- `BrainModelChangeHistoryRepository`：变更历史仓库不存在
- `LlmClientFactory`：LLM 客户端工厂不存在（如部门对话需要直接调用）

### 📝 关键发现

1. **DepartmentController 命名不一致**：实际文件名为 `DepartmentController.java` (非 `DepartmentApiController.java`)
2. **部门大脑配置返回硬编码**：`getDepartmentBrain()` 方法直接返回 `BrainInfo(id + "_brain", brainName, "running", Instant.now(), 100)`，不查询真实 brain-models 配置
3. **EvolutionOrchestrator.rollbackBrain() 已有实现**：在 EvolutionAdminController 中已调用 `orchestrator.rollbackBrain(brainId)`，说明 Orchestrator 中已有基础方法
4. **前端自动进化仅用 localStorage**：BrainConfig 的 `autoEvolutionEnabled` 状态仅存储在 localStorage，未与后端进化系统联动

---

## 1. EvolutionOrchestrator 决策主流程

### 文件路径
`living-agent-core/src/main/java/com/livingagent/core/evolution/engine/EvolutionOrchestrator.java`

### 当前状态 (2026-04-24 审查)
- 类已存在，但核心方法 (score, selectStrategy, runAutoAdjust) 需要补强
- `rollbackBrain()` 方法已被 EvolutionAdminController 调用，说明已有基础实现，但需要确认是否完整
- 目标职责：只负责"编排"，不直接承担统计、选择器注册、数据落库

### 职责边界
- **负责**：拉起反馈聚合、选择自动调整策略、调用 selector、协调 assigner、记录调整结果
- **不负责**：统计聚合细节（交给 `EvolutionFeedbackService`）、具体打分规则（交给 selector）、定时调度（交给 `EvolutionScheduler`）、WebSocket 路由（交给 `DepartmentWebSocketHandler`）

### 与入口路由的关系
- 本模块只处理"brain-models 自动调整"链路，不直接处理 `/chat?id=...` 或 `/chat?brain=...` 的入口分发
- 与 `Chat.tsx` / `DepartmentController` 的入口路由逻辑无直接耦合，只通过 `brain-models` 结果间接影响部门脑绑定

### 需要实现的方法

#### 1.1 `score(EvolutionResult result)` - 反馈评分

**输入**：进化反馈结果（包含用户评分、响应时间、成功率等）

**输出**：归一化分数（0.0 - 1.0）

**逻辑**：
```java
public double score(EvolutionResult result) {
    double userRating = extractUserRating(result);
    double responseTime = extractResponseTime(result);
    boolean success = result.isSuccess();
    
    double normalizedRating = userRating / 5.0;
    double timeScore = Math.max(0, 1.0 - (responseTime / 30000.0));
    
    double finalScore = (normalizedRating * 0.6) + (timeScore * 0.3) + (success ? 0.1 : 0.0);
    
    return Math.min(1.0, Math.max(0.0, finalScore));
}
```

#### 1.2 `selectStrategy(EvolutionSignal signal)` - 策略选择

**输入**：进化信号（包含失败类型、性能指标等）

**输出**：进化策略枚举

**策略矩阵**：
| 信号类型 | 条件 | 策略 |
|---------|------|------|
| 连续 3 次失败 | 同一 brain | `REPLACE_MODEL` |
| 用户评分 < 2.0 | 连续 5 次 | `DOWNGRADE_MODEL` |
| 响应时间 > 15s | 连续 10 次 | `UPGRADE_MODEL` |
| 部门访问拒绝 | 权限问题 | `ESCALATE_TO_ADMIN` |
| 其他 | 默认 | `DEFER` |

#### 1.3 `runAutoAdjust(String brainId)` - 自动调整主流程

**输入**：brainId（可选，为 null 时调整所有大脑）

**输出**：调整结果 Map

**流程**：
```
1. 获取最近反馈（最近 50 条）
2. 按 brainId 分组统计
3. 对每个 brain：
   a. 计算平均分数
   b. 如果分数 < 阈值（0.4）：触发模型替换
   c. 从候选模型中选择最佳
   d. 调用 BrainModelAssigner 更新绑定
   e. 记录调整历史到 BrainModelChangeHistory
4. 返回调整结果
```

#### 1.4 `rollbackBrain(String brainId)` - 回滚大脑配置

**输入**：brainId

**输出**：是否成功回滚

**逻辑**：
```java
public boolean rollbackBrain(String brainId) {
    List<BrainModelChangeHistory> history = changeHistoryRepository.findByBrainIdOrderByCreatedAtDesc(brainId);
    BrainModelChangeHistory lastManual = history.stream()
        .filter(c -> "manual".equals(c.getSource()))
        .findFirst()
        .orElse(null);
    
    if (lastManual == null) return false;
    
    brainModelAssigner.assignModel(
        brainId,
        lastManual.getBrainName(),
        lastManual.getBrainType(),
        lastManual.getModelId(),
        "system_rollback"
    );
    
    changeHistoryRepository.save(new BrainModelChangeHistory(..., "rollback"));
    
    return true;
}
```

### 新增依赖

```java
private final EvolutionFeedbackService feedbackService;
private final BrainModelSelectorManager selectorManager;
private final BrainModelAssigner brainModelAssigner;
private final BrainModelChangeHistoryRepository changeHistoryRepository;
```

### 文件内实现优先级
1. 优先补 `score(...)` / `selectStrategy(...)` 这类纯流程方法
2. 再补 `runAutoAdjust(...)` 的编排
3. 最后补 `rollbackBrain(...)` 与历史回写

---

## 2. EvolutionFeedbackService 统计聚合

### 文件路径
`living-agent-core/src/main/java/com/livingagent/core/evolution/executor/EvolutionFeedbackService.java`

### 当前状态 (2026-04-24 审查)
- 类已存在，但统计聚合逻辑需要补强
- 目标职责：负责反馈记录、最近反馈查询、统计聚合与缓存失效

### 职责边界
- **负责**：写入反馈、查询最近反馈、汇总统计、提供自动调整输入
- **不负责**：模型选择策略、自动调度、回滚、WebSocket 路由

### 需要实现的方法

#### 2.1 `record(EvolutionResult result)` - 反馈记录

**逻辑**：
```java
@Transactional
public void record(EvolutionResult result) {
    evolutionResultRepository.save(result);
    statsCache.invalidate("global_stats");
    checkConsecutiveFailures(result);
}
```

#### 2.2 `recent(int limit)` - 最近反馈查询

**逻辑**：
```java
public List<EvolutionResult> recent(int limit) {
    return evolutionResultRepository.findTopByOrderByTimestampDescLimit(limit);
}
```

#### 2.3 `statistics()` - 统计聚合

**返回结构**：
```json
{
  "total": 1234,
  "success_rate": 0.85,
  "avg_response_time_ms": 2500,
  "avg_user_rating": 4.2,
  "by_brain": {
    "main": { "count": 500, "success_rate": 0.9 },
    "tech": { "count": 300, "success_rate": 0.8 }
  },
  "consecutive_failures": {
    "main": 0,
    "tech": 2
  }
}
```

### 新增依赖

```java
private final EvolutionResultRepository resultRepository;
private final LoadingCache<String, Map<String, Object>> statsCache;
```

---

## 3. EvolutionScheduler 定时任务

### 文件路径
`living-agent-core/src/main/java/com/livingagent/core/evolution/scheduler/EvolutionScheduler.java`（新建）

### 需要创建的文件

| 文件 | 职责 | 状态 |
|------|------|------|
| `EvolutionScheduler.java` | 接口定义 | ❌ 待新增 |
| `EvolutionSchedulerImpl.java` | 实现类 | ❌ 待新增 |
| `EvolutionJobConfig.java` | Spring 定时任务配置 | ❌ 待新增 |

### 职责边界
- **负责**：定时触发自动调整、清理过期反馈、重试失败任务
- **不负责**：评分、策略选择、数据库聚合、部门对话路由

### EvolutionScheduler 接口

```java
public interface EvolutionScheduler {
    @Scheduled(cron = "${evolution.scheduler.cron:0 0 * * * ?}")
    void runHourlyAdjustment();
    
    @Scheduled(cron = "${evolution.scheduler.cleanup-cron:0 0 2 * * ?}")
    void cleanupExpiredFeedback();
    
    @Scheduled(cron = "${evolution.scheduler.retry-cron:0 */30 * * * ?}")
    void retryFailedTasks();
}
```

### EvolutionSchedulerImpl 实现

```java
@Component
@Slf4j
public class EvolutionSchedulerImpl implements EvolutionScheduler {
    
    private final EvolutionOrchestrator orchestrator;
    private final EvolutionResultRepository resultRepository;
    private final BrainModelChangeHistoryRepository changeHistoryRepository;
    
    @Override
    public void runHourlyAdjustment() {
        log.info("Starting hourly evolution adjustment...");
        try {
            Map<String, Object> adjustments = orchestrator.runAutoAdjust(null);
            log.info("Hourly adjustment completed: {}", adjustments);
        } catch (Exception e) {
            log.error("Hourly adjustment failed", e);
        }
    }
    
    @Override
    public void cleanupExpiredFeedback() {
        log.info("Starting feedback cleanup...");
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        int deleted = resultRepository.deleteByTimestampBefore(cutoff);
        log.info("Deleted {} expired feedback records", deleted);
    }
    
    @Override
    public void retryFailedTasks() {
        log.info("Starting failed task retry...");
        List<EvolutionResult> failed = resultRepository.findByStatusAndRetryCountLessThan(
            EvolutionResult.Status.FAILED, 3);
        
        for (EvolutionResult result : failed) {
            try {
                result.setRetryCount(result.getRetryCount() + 1);
                resultRepository.save(result);
                orchestrator.runAutoAdjust(null);
            } catch (Exception e) {
                log.warn("Retry failed for result {}: {}", result.getResultId(), e.getMessage());
            }
        }
    }
}
```

### EvolutionJobConfig 配置

```java
@Configuration
@EnableScheduling
public class EvolutionJobConfig {
}
```

---

## 4. DepartmentController 接真实数据源

### 文件路径
`living-agent-gateway/src/main/java/com/livingagent/gateway/controller/DepartmentController.java`

### 当前状态 (2026-04-24 审查)
- ✅ 权限验证完整
- ✅ 路由逻辑完整
- ❌ `getDepartmentBrain()` 返回硬编码数据：`BrainInfo(id + "_brain", brainName, "running", Instant.now(), 100)`
- ❌ 部门列表为硬编码示例数据
- ❌ 未查询真实 brain-models 配置

### 需要修改的方法

#### 4.1 `getDepartmentBrain()` - 接真实大脑配置

**当前**：返回硬编码数据
```java
BrainInfo brain = new BrainInfo(
    id + "_brain",
    brainName,
    "running",
    Instant.now(),
    100
);
```

**修改后**：
```java
@GetMapping("/{id}/brain")
public ResponseEntity<ApiResponse<BrainInfo>> getDepartmentBrain(
        @PathVariable String id,
        @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
    
    String brainId = mapDepartmentToBrainId(id);
    BrainModelAssignment assignment = brainModelAssigner.getAssignment(brainId);
    
    if (assignment != null) {
        LlmModel model = modelPoolManager.getModelById(assignment.getModelId());
        BrainInfo brain = new BrainInfo(
            brainId,
            model != null ? model.getDisplayName() : brainName,
            "running",
            assignment.getAssignedAt(),
            model != null && model.isEnabled() ? 100 : 0
        );
        return ResponseEntity.ok(ApiResponse.success(brain));
    }
    
    // 未配置时返回默认
    BrainInfo brain = new BrainInfo(
        brainId,
        brainName + " (默认)",
        "running",
        null,
        100
    );
    return ResponseEntity.ok(ApiResponse.success(brain));
}
```

#### 4.2 `getDepartmentInfo()` - 接真实部门数据

**修改后**：
```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<DepartmentInfo>> getDepartmentInfo(
        @PathVariable String id,
        @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
    
    Department department = departmentService.findByCode(id);
    if (department == null) {
        return ResponseEntity.status(404)
            .body(ApiResponse.error("not_found", "Department not found: " + id));
    }
    
    BrainModelAssignment assignment = brainModelAssigner.getAssignment(
        mapDepartmentToBrainId(id));
    int memberCount = employeeService.countByDepartment(id);
    
    DepartmentInfo info = new DepartmentInfo(
        department.getCode(),
        department.getName(),
        department.getNameEn(),
        department.getDescription(),
        department.getIcon(),
        memberCount,
        assignment != null ? 1 : 0
    );
    
    return ResponseEntity.ok(ApiResponse.success(info));
}
```

#### 4.3 新增 `getDepartmentBrains()` - 查询部门所有大脑配置

```java
@GetMapping("/{id}/brains")
public ResponseEntity<ApiResponse<List<BrainInfo>>> getDepartmentBrains(
        @PathVariable String id,
        @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
    
    String brainId = mapDepartmentToBrainId(id);
    List<BrainModelAssignment> assignments = brainModelAssigner.getAllAssignments();
    
    List<BrainInfo> brains = assignments.stream()
        .filter(a -> a.getBrainId().equals(brainId) || isRelatedBrain(a.getBrainId(), id))
        .map(assignment -> {
            LlmModel model = modelPoolManager.getModelById(assignment.getModelId());
            return new BrainInfo(
                assignment.getBrainId(),
                model != null ? model.getDisplayName() : assignment.getBrainName(),
                "running",
                assignment.getAssignedAt(),
                model != null && model.isEnabled() ? 100 : 0
            );
        })
        .toList();
    
    return ResponseEntity.ok(ApiResponse.success(brains));
}
```

### 新增依赖

```java
private final BrainModelAssigner brainModelAssigner;
private final ModelPoolManager modelPoolManager;
private final EmployeeService employeeService;
private final DepartmentService departmentService;
```

### 辅助方法

```java
private String mapDepartmentToBrainId(String departmentCode) {
    return switch (departmentCode.toLowerCase()) {
        case "tech" -> "neuron://tech/tech-brain/001";
        case "admin" -> "neuron://admin/admin-brain/001";
        case "hr" -> "neuron://hr/hr-brain/001";
        case "finance" -> "neuron://finance/finance-brain/001";
        case "sales" -> "neuron://sales/sales-brain/001";
        case "cs" -> "neuron://cs/cs-brain/001";
        case "ops" -> "neuron://ops/ops-brain/001";
        case "legal" -> "neuron://legal/legal-brain/001";
        case "core", "cross_dept" -> "neuron://core/main-brain/001";
        default -> "neuron://core/main-brain/001";
    };
}

private boolean isRelatedBrain(String brainId, String departmentCode) {
    // 判断 brainId 是否与部门相关（用于跨部门大脑）
    return brainId.contains("main") || brainId.contains(departmentCode);
}
```

---

## 5. 新增实体和 Repository

### 5.1 BrainModelChangeHistory（大脑模型变更历史）

**文件路径**：`living-agent-core/src/main/java/com/livingagent/core/model/pool/BrainModelChangeHistory.java`

```java
@Entity
@Table(name = "brain_model_change_history")
public class BrainModelChangeHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String brainId;
    
    @Column(nullable = false)
    private String brainName;
    
    @Column(nullable = false)
    private String brainType;
    
    @Column(nullable = false)
    private UUID modelId;
    
    private String modelName;
    
    @Column(nullable = false)
    private String source; // "manual" | "auto" | "rollback"
    
    private String changedBy;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private String reason;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### 5.2 BrainModelChangeHistoryRepository

**文件路径**：`living-agent-core/src/main/java/com/livingagent/core/model/pool/BrainModelChangeHistoryRepository.java`

```java
public interface BrainModelChangeHistoryRepository extends JpaRepository<BrainModelChangeHistory, UUID> {
    List<BrainModelChangeHistory> findByBrainIdOrderByCreatedAtDesc(String brainId);
    List<BrainModelChangeHistory> findByBrainIdAndSource(String brainId, String source);
    List<BrainModelChangeHistory> findByBrainIdAndSourceOrderByCreatedAtDesc(String brainId, String source);
}
```

### 5.3 EvolutionResult 字段扩展

**需要新增的字段**：
```java
@Column
private int retryCount = 0;

@Column
private LocalDateTime timestamp;

public LocalDateTime getTimestamp() {
    return timestamp != null ? timestamp : LocalDateTime.ofInstant(
        Instant.ofEpochMilli(getCreatedAt()), ZoneId.systemDefault());
}
```

### 5.4 数据库迁移脚本 (Flyway)

**文件路径**：`living-agent-core/src/main/resources/db/migration/Vxxx__create_brain_model_change_history.sql`

```sql
CREATE TABLE IF NOT EXISTS brain_model_change_history (
    id UUID PRIMARY KEY,
    brain_id VARCHAR(255) NOT NULL,
    brain_name VARCHAR(255) NOT NULL,
    brain_type VARCHAR(50) NOT NULL,
    model_id UUID NOT NULL,
    model_name VARCHAR(255),
    source VARCHAR(50) NOT NULL,
    changed_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason TEXT
);

CREATE INDEX idx_brain_history_brain_id ON brain_model_change_history(brain_id);
CREATE INDEX idx_brain_history_source ON brain_model_change_history(source);
CREATE INDEX idx_brain_history_created_at ON brain_model_change_history(created_at DESC);
```

---

## 6. 前端联动缺口

### 6.1 自动进化开关未联动后端

**问题**：BrainConfig.tsx 的 `autoEvolutionEnabled` 仅存储在 localStorage，未与后端进化系统联动

**解决方案**：
```typescript
// 添加 API 调用
const toggleAutoEvolutionMutation = useMutation({
  mutationFn: (enabled: boolean) =>
    fetchJson('/evolution/auto-adjust', {
      method: 'POST',
      body: JSON.stringify({ enabled }),
    }),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['evolution', 'config'] });
  },
});
```

### 6.2 进化反馈展示区缺失

**问题**：BrainConfig 有自动进化配置区，但缺少最近反馈记录和进化结果展示

**建议新增**：
- 最近反馈记录列表（调用 `GET /api/evolution/feedback/recent`）
- 进化调整历史记录
- 手动回滚按钮（已有，但依赖 lastAction 状态）

---

## 实施顺序

### 第一阶段：基础设施（预计 2-3 小时）

1. [ ] 创建 `BrainModelChangeHistory` 实体
2. [ ] 创建 `BrainModelChangeHistoryRepository` 接口
3. [ ] 扩展 `EvolutionResult` 字段
4. [ ] 添加数据库迁移脚本（Flyway）

### 第二阶段：核心逻辑（预计 4-5 小时）

5. [ ] 实现 `EvolutionFeedbackService` 统计聚合
6. [ ] 实现 `EvolutionOrchestrator.score()`
7. [ ] 实现 `EvolutionOrchestrator.selectStrategy()`
8. [ ] 实现 `EvolutionOrchestrator.runAutoAdjust()`
9. [ ] 实现 `EvolutionOrchestrator.rollbackBrain()` 完整逻辑（含历史记录）

### 第三阶段：定时任务（预计 2 小时）

10. [ ] 创建 `EvolutionScheduler` 接口
11. [ ] 创建 `EvolutionSchedulerImpl` 实现
12. [ ] 创建 `EvolutionJobConfig` 配置

### 第四阶段：部门对话（预计 3-4 小时）

13. [ ] 修改 `DepartmentController.getDepartmentBrain()` 接真实配置
14. [ ] 修改 `DepartmentController.getDepartmentInfo()` 接真实数据
15. [ ] 新增 `DepartmentController.getDepartmentBrains()` 接口

### 第五阶段：前端联动（预计 1-2 小时）

16. [ ] 添加自动进化开关后端 API
17. [ ] 添加进化反馈展示区
18. [ ] 完善手动回滚功能

### 第六阶段：测试和验证（预计 2 小时）

19. [ ] 单元测试
20. [ ] 集成测试
21. [ ] API 文档更新

---

## 风险和依赖

### 外部依赖
- `EmployeeService` - 需要确认员工库服务是否已存在
- `DepartmentService` - 需要确认部门服务是否已存在
- `Flyway` - 数据库迁移工具配置

### 风险点
1. **EvolutionOrchestrator 逻辑复杂** - 需要仔细测试评分和策略选择算法
2. **定时任务并发** - 需要确保定时任务不会并发执行导致数据冲突
3. **部门对话性能** - 接入真实推理后需要监控响应时间
4. **localStorage 与后端状态不同步** - 前端自动进化开关需要后端 API 支持

---

## 验收标准

| 功能 | 验收标准 |
|------|---------|
| 自动调整 | 每小时自动执行，成功率 > 80% |
| 回滚功能 | 能正确回滚到最近一次手动配置 |
| 历史记录 | 能查询所有变更历史 |
| 部门成员 | 返回真实员工数据 |
| 部门大脑 | 返回真实大脑配置状态 |
| 部门对话 | 能返回 LLM 推理结果 |
| 前端联动 | 自动进化开关与后端同步 |

---

## 附录：API 变更清单

### 新增 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/evolution/trigger-auto-adjust` | 手动触发自动调整 |
| POST | `/api/evolution/rollback/{brainId}` | 回滚大脑配置（已存在） |
| GET | `/api/evolution/history` | 查询进化历史 |
| POST | `/api/evolution/auto-adjust` | 启用/禁用自动进化 |
| GET | `/api/dept/{department}/brains` | 查询部门大脑配置 |

### 修改 API

| 方法 | 路径 | 变更说明 |
|------|------|---------|
| GET | `/api/dept/{id}/brain` | 从硬编码改为真实大脑配置 |
| GET | `/api/dept/{id}` | 从示例数据改为真实部门信息 |
| GET | `/api/evolution/feedback/recent` | 补充统计聚合数据 |
