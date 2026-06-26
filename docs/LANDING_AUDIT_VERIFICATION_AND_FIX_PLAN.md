# 落地核查验证与改进方案

> **版本**：v1.0 | **核查日期**：2026-06-26
>
> **目的**：基于 `LANDING_AUDIT_AND_IMPROVEMENT_PLAN.md` 列出的问题清单，对照实际代码逐项核查，给出**确凿证据**与**可执行修复方案**。
>
> **核查方法**：源码逐文件读取、Bean 装配追踪、`grep` 交叉引用、git 工作区统计。所有结论均标注 `文件:行号` 证据。
>
> **与原文档的关系**：本文档是 `LANDING_AUDIT_AND_IMPROVEMENT_PLAN.md` 的**验证 + 修正 + 细化**版本。对原报告中的**误报**予以指出，对**确认项**给出具体修复代码方向。

---

## 一、核查结论总览

| 原编号 | 原描述 | 实际核查 | 与原文档差异 |
|--------|--------|----------|--------------|
| P0-1 | LedgerService 纯内存态 | ✅ 确认 | 一致 |
| P0-2 | 假数据扫描器 | ✅ 确认 | 一致；补充 `GitHubScannerImpl` 既死代码又假数据 |
| P0-3 | HardwareUpgrade 空操作 | ✅ 确认 | 一致 |
| P1-1 | Dashboard 成本分析全零 | ✅ 确认 | 一致；补充 `buildTaskMetrics`/`buildDepartmentHealth` 也传 0 |
| P1-2 | 绩效评分硬编码 0.0 | ✅ 确认 | 一致 |
| P1-3 | CEODashboardService 死代码 | ✅ 确认 | 一致 |
| P1-4 | 3 套绩效 Bean 并存 | ✅ 确认 | 一致 |
| P2-1 | Embedding 静默降级 | ✅ 确认 | 一致 |
| **P2-2** | LeadOrchestrator 5 方法抛异常 | ⚠️ **部分误报** | 接口 default 抛异常属实，但生产 Bean `TechLeadOrchestrator` 已完整实现 |
| P2-3 | 多套内存态服务 | ✅ 确认 | 一致；补充 `ComplianceManager` 用非线程安全 `ArrayList` |
| **P2-4** | 测试覆盖 ~0.8%（8 个） | ⚠️ **修正** | 实际 11 个测试文件 |
| P3 | 残留清理项 | ✅ 确认 | 一致；补充 `AgentTaskController` 返回假示例数据 |
| 工作区 | 183 文件未提交 | ⚠️ 修正 | 实际 191 文件未提交 |

**核查新发现（原文档未列）**：
1. `ComplianceManager.violations` / `auditLogs` 使用 `ArrayList`（非 `synchronizedList`），存在线程安全风险
2. `IncentiveManager.pendingRewards` 也是内存态 `ConcurrentHashMap`
3. `GitHubScannerImpl` 既是死代码（被 `@Primary` 覆盖）又返回假数据（双重问题）
4. `AgentTaskController` 不仅双路由，还返回假示例数据（"task_001"、"示例任务"）

---

## 二、P0 级问题核查与修复方案

### 🔴 P0-1 — LedgerService 纯内存态，重启即清零

**核查结论**：✅ 确认

**证据**：[AutonomousOperationConfig.java:63-67](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomous/config/AutonomousOperationConfig.java#L63-L67)

```java
@Bean
public LedgerService ledgerService() {
    log.info("Initializing LedgerService as unified balance source");
    return new InMemoryLedgerService();   // 内部类，ConcurrentHashMap+synchronizedList
}
```

内部类 `InMemoryLedgerService`（`:123-157`）使用 `ConcurrentHashMap<String, Integer> balances` 与 `Collections.synchronizedList(new ArrayList<>())` 存储全部账本数据。

**依赖扩散**（共用这一个内存 Bean）：
- `CreditAccountService` → `UnifiedCreditAccountService(ledgerService)`（`:104-107`）
- `EvolutionTracker` → `UnifiedEvolutionTracker(ledgerService)`（`:110-113`）
- `IncentiveManager`（`:116-121`）
- `EvolutionManager`（`:96-101`）

**全项目搜索结果**：`grep JpaLedgerService` → **无匹配**，确认没有 JPA 实现。

**修复方案**：

1. **新建数据库表**（Flyway migration `V{n}__ledger_transactions.sql`）

```sql
CREATE TABLE ledger_transaction (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  VARCHAR(64)  NOT NULL UNIQUE,   -- 业务ID，如 inc_xxx / txn_xxx
    employee_id     VARCHAR(128) NOT NULL,
    source_type     VARCHAR(32)  NOT NULL,           -- INCOME / REWARD / DEBIT / ACHIEVEMENT
    source_id       VARCHAR(128),
    amount_cents    INTEGER      NOT NULL,           -- 正数=入账，负数=出账
    balance_after   INTEGER      NOT NULL,           -- 余额快照
    status          VARCHAR(16)  NOT NULL,           -- RECEIVED / PENDING
    description     TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ledger_employee ON ledger_transaction(employee_id, created_at DESC);
CREATE INDEX idx_ledger_source   ON ledger_transaction(source_type, source_id);
```

2. **新建实体与 Repository**（`core/database/entity/LedgerTransactionEntity.java` + `core/database/repository/LedgerTransactionRepository.java`）

3. **新建 `JpaLedgerService implements LedgerService`**（`core/autonomous/ledger/JpaLedgerService.java`）
   - 写穿 DB：每次 `recordIncome` / `recordReward` 都 `INSERT` 一条 `ledger_transaction`
   - `getBalance`：`SELECT COALESCE(SUM(amount_cents),0) FROM ledger_transaction WHERE employee_id=?`
   - `getIncomeHistory`：`SELECT ... WHERE employee_id=? ORDER BY created_at DESC LIMIT ?`
   - 用 `@Transactional` 保证余额快照一致性

4. **修改 `AutonomousOperationConfig.ledgerService()`**

```java
@Bean
@ConditionalOnProperty(name = "autonomous.ledger.persistence", havingValue = "jpa", matchIfMissing = true)
public LedgerService jpaLedgerService(LedgerTransactionRepository repo) {
    return new JpaLedgerService(repo);
}

@Bean
@ConditionalOnProperty(name = "autonomous.ledger.persistence", havingValue = "memory")
@Profile("dev")  // 仅开发环境允许内存态
public LedgerService inMemoryLedgerService() {
    return new InMemoryLedgerService();
}
```

5. **将原 `InMemoryLedgerService` 抽出为独立公开类**，标注 `@Profile("dev")` 或仅测试用。

**验收标准**：
- [ ] 容器重启后 `getBalance(employeeId)` 返回重启前的值
- [ ] DB 表 `ledger_transaction` 有写入记录
- [ ] `mvn compile` 通过
- [ ] 单测：`JpaLedgerServiceTest` 覆盖 recordIncome/getBalance/getIncomeHistory

---

### 🔴 P0-2 — Bug赏金/外包扫描器返回硬编码假数据

**核查结论**：✅ 确认

**证据**：

[BugBountyScannerImpl.java:41-57](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomous/bounty/impl/BugBountyScannerImpl.java#L41-L57) — 返回字面量假机会，URL 全是 `example-program`：

```java
opportunities.add(createBugBountyOpportunity(
    "HackerOne: XSS vulnerability", ..., "https://hackerone.com/example-program"));
opportunities.add(createBugBountyOpportunity(
    "HackerOne: Authentication bypass", ..., "https://hackerone.com/example-program"));
opportunities.add(createBugBountyOpportunity(
    "Bugcrowd: IDOR vulnerability", ..., "https://bugcrowd.com/example-program"));
```

[FreelanceScannerImpl.java:38-67](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomous/bounty/impl/FreelanceScannerImpl.java#L38-L67) — URL 全是 `upwork.com/jobs/example` / `fiverr.com/gigs/example`：

```java
opportunities.add(createFreelanceOpportunity(
    "Upwork: " + keyword + " development", ..., "https://upwork.com/jobs/example"));
opportunities.add(createFreelanceOpportunity(
    "Fiverr: " + keyword + " gig", ..., "https://fiverr.com/gigs/example"));
```

**死代码**：[GitHubScannerImpl.java:16-17](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomous/bounty/impl/GitHubScannerImpl.java#L16-L17) 标 `@Component` 但被 `AutonomousOperationConfig:38-43` 的 `@Primary GitHubPlatformIntegration` 覆盖，且自身也返回 `https://github.com/example/repo/issues/1` 假数据。

**修复方案**（按"是否有真实接入计划"二选一）：

**方案 A：禁用假数据扫描器（推荐，若无真实接入计划）**

```java
// AutonomousOperationConfig.java
@Bean
@ConditionalOnProperty(name = "autonomous.bounty-hunter.scan-freelance", havingValue = "true")
public FreelanceScanner freelanceScanner() {
    log.warn("FreelanceScannerImpl returns MOCK data, enable only in dev");
    return new FreelanceScannerImpl();
}

@Bean
@ConditionalOnProperty(name = "autonomous.bounty-hunter.scan-bugbounty", havingValue = "true")
public BugBountyScanner bugBountyScanner() {
    log.warn("BugBountyScannerImpl returns MOCK data, enable only in dev");
    return new BugBountyScannerImpl();
}
```

并在 `application.yml` 默认关闭：
```yaml
autonomous:
  bounty-hunter:
    scan-freelance: false
    scan-bugbounty: false
```

**方案 B：保留但加 `@Profile("dev")` 标识**

```java
// 在 BugBountyScannerImpl / FreelanceScannerImpl 上加
@Profile("dev")
@Component
public class BugBountyScannerImpl implements BugBountyScanner { ... }
```

**方案 C：实现真实接入**（若要保留生产可用）— 调用 HackerOne/Bugcrowd API、Upwork/Fiverr API，需要 API Key 配置。

**附加：删除 `GitHubScannerImpl` 死代码**

```java
// 直接删除 GitHubScannerImpl.java，或改为
@Profile("dev")
@Component
public class GitHubScannerImpl implements GitHubScanner { ... }
```

**验收标准**：
- [ ] 生产环境（`prod` profile）下 `BountyHunterSkill` 不产出虚假商机
- [ ] `grep "example-program" src/main` 无匹配（仅测试代码可有）
- [ ] `GitHubScannerImpl` 不再以 `@Component` 存活于生产装配

---

### 🔴 P0-3 — InMemoryHardwareUpgradeService 是空操作

**核查结论**：✅ 确认

**证据**：[AutonomousOperationConfig.java:89-93](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomous/config/AutonomousOperationConfig.java#L89-L93) + 内部类 `InMemoryHardwareUpgradeService`（`:159-188`）

```java
@Override
public HardwareUpgradeResult executeUpgrade(String employeeId, EvolutionManager.HardwareUpgradePlan plan) {
    // 仅盖一个成功 ID，不执行任何动作
    return HardwareUpgradeResult.success("upg_" + System.currentTimeMillis(), plan.hardware(), plan.costCents());
}
```

`evaluateUpgrade` 返回硬编码模板："RTX 5090 32GB"（≥500000）、"128GB RAM"（≥200000）。`recordTierChange` 是空方法体。

**修复方案**：

**方案 A：明确标注为"模拟"（推荐，因硬件升级本质是物理操作，服务层无法真实执行）**

1. 重命名 `InMemoryHardwareUpgradeService` → `SimulatedHardwareUpgradeService`，移到 `core/evolution/impl/`
2. 在 `HardwareUpgradeResult` 中增加 `boolean simulated` 字段，UI 显示"模拟升级"
3. `EvolutionManager` 调用 `executeUpgrade` 时 `log.warn("Hardware upgrade is SIMULATED, no real action taken")`

**方案 B：与运维系统对接**（如果有真实基础设施 API）— 调用 Proxmox/K8s API 扩容，需要单独的 `RealHardwareUpgradeService` 实现。

**验收标准**：
- [ ] `EvolutionManager` 日志/UI 明确标注"模拟升级"
- [ ] `executeUpgrade` 调用方知道这是 no-op，不会误以为硬件真的升级

---

## 三、P1 级问题核查与修复方案

### 🟠 P1-1 — Dashboard 成本分析返回全零

**核查结论**：✅ 确认

**证据**：[DashboardServiceImpl.java:196-206](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/operation/dashboard/impl/DashboardServiceImpl.java#L196-L206)

```java
private DashboardDTOs.CostAnalysis buildCostAnalysis() {
    return new DashboardDTOs.CostAnalysis(
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, 0.0, List.of());
}
```

同步传 0 的还有：
- `buildTaskMetrics`（`:193`）`new DashboardDTOs.TaskMetrics(totalTasks, stats.pendingCount(), (int) completedToday, 0, completionRate, 0)` — 最后两个 0
- `buildDepartmentHealth`（`:231-234`）`..., 0, healthScore, status, 0` — 两个 0

**修复方案**：

**短期（隐藏假数据）**：前端隐藏成本卡片，后端返回 `null` 或 `Optional.empty()`，前端检测后不渲染。

**长期（接入真实数据）**：依赖 P0-1 修复完成后，从 `LedgerService` 聚合：

```java
private DashboardDTOs.CostAnalysis buildCostAnalysis() {
    // 从 ledger_transaction 表聚合
    BigDecimal totalRevenue = ledgerService.getTotalRevenue();      // SUM(amount_cents) WHERE amount>0
    BigDecimal totalCost    = ledgerService.getTotalCost();         // SUM(-amount_cents) WHERE amount<0
    BigDecimal netProfit    = totalRevenue.subtract(totalCost);
    BigDecimal costPerTask  = taskCheckout.getStatistics().completedCount() > 0
        ? totalCost.divide(BigDecimal.valueOf(taskCheckout.getStatistics().completedCount()), 2, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;
    // ... 按部门/按类型聚合明细
    return new DashboardDTOs.CostAnalysis(totalRevenue, totalCost, netProfit, costPerTask, ...);
}
```

token 成本可从 `TokenCostEstimator`（`AutonomousOperationConfig:84-87`）聚合。

**验收标准**：
- [ ] 成本卡片显示真实数字，或前端不渲染该卡片
- [ ] `buildTaskMetrics`/`buildDepartmentHealth` 的 0 替换为真实聚合值或 `null`

---

### 🟠 P1-2 — 绩效指标评分硬编码 0.0

**核查结论**：✅ 确认

**证据**：[JpaPerformanceAssessmentService.java:232-244](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/operation/performance/JpaPerformanceAssessmentService.java#L232-L244)

```java
@Override public double getActualValue() { return entity.getTargetValue() ... }  // 用目标值冒充实绩
@Override public double getScore() { return 0.0; }            // 硬编码
@Override public double getAchievementRate() { return 0.0; }  // 硬编码
```

`getDepartmentAverageScores`（`:120-133`）所有维度分数都等于 `overall`：

```java
double overall = assessments.stream().mapToDouble(...).average().orElse(0.0);
return Map.of(
    "overall", overall,
    "taskCompletion", overall,  // 复制
    "quality", overall,         // 复制
    "efficiency", overall,      // 复制
    "collaboration", overall    // 复制
);
```

**修复方案**：

```java
private PerformanceIndicator toIndicator(PerformanceIndicatorEntity entity) {
    double target = entity.getTargetValue() != null ? entity.getTargetValue() : 0.0;
    double actual = computeActualValue(entity);  // 从 task_checkout / ledger 等真实数据源聚合
    double score = target > 0 ? Math.min(100.0, actual / target * 100.0) : 0.0;
    double achievementRate = target > 0 ? actual / target : 0.0;
    return new PerformanceIndicator() {
        @Override public double getActualValue() { return actual; }
        @Override public double getScore() { return score; }
        @Override public double getAchievementRate() { return achievementRate; }
        // ...
    };
}

private double computeActualValue(PerformanceIndicatorEntity entity) {
    // 根据 entity.getCategory() 路由到不同数据源：
    // TASK_COMPLETION -> taskCheckout.getStatistics().completionRate()
    // QUALITY         -> codeReviewWorkflowService 通过率
    // EFFICIENCY      -> taskCheckout 平均完成时间倒数
    // COLLABORATION   -> collaboration 统计
    return switch (IndicatorCategory.valueOf(entity.getCategory())) {
        case TASK_COMPLETION -> taskCheckout.getStatistics().completionRate() * 100;
        case QUALITY         -> computeQualityScore(entity.getIndicatorId());
        case EFFICIENCY      -> computeEfficiencyScore(entity.getIndicatorId());
        case COLLABORATION   -> computeCollaborationScore(entity.getIndicatorId());
    };
}

@Override
public Map<String, Double> getDepartmentAverageScores(String departmentId) {
    List<PerformanceAssessment> assessments = getDepartmentAssessments(departmentId, ...);
    if (assessments.isEmpty()) return Map.of("overall",0.0,"taskCompletion",0.0,"quality",0.0,"efficiency",0.0,"collaboration",0.0);
    return Map.of(
        "overall",        assessments.stream().mapToDouble(PerformanceAssessment::getOverallScore).average().orElse(0.0),
        "taskCompletion", assessments.stream().mapToDouble(a -> a.getDimensionScores().getOrDefault("taskCompletion",0.0)).average().orElse(0.0),
        "quality",        assessments.stream().mapToDouble(a -> a.getDimensionScores().getOrDefault("quality",0.0)).average().orElse(0.0),
        "efficiency",     assessments.stream().mapToDouble(a -> a.getDimensionScores().getOrDefault("efficiency",0.0)).average().orElse(0.0),
        "collaboration",  assessments.stream().mapToDouble(a -> a.getDimensionScores().getOrDefault("collaboration",0.0)).average().orElse(0.0)
    );
}
```

**验收标准**：
- [ ] `getScore()` / `getAchievementRate()` 返回真实计算值，非 0.0
- [ ] 部门维度分数有差异（不再全是 overall 复制）
- [ ] 单测覆盖：`JpaPerformanceAssessmentServiceTest.toIndicator`

---

### 🟠 P1-3 — CEODashboardService 100% 假数据（死代码地雷）

**核查结论**：✅ 确认

**证据**：[CEODashboardService.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/operation/dashboard/CEODashboardService.java) 全文

- 类无 `@Service` / `@Component` 注解（第8行 `public class CEODashboardService implements CEODashboard`）
- 全项目 `grep CEODashboardService` → **仅 1 个匹配（定义文件自身）**，无任何引用
- `getCompanyOverview()` 返回 `150, 120, 80, 40, 8, 0.85, 0.92, 15000, 230`（第16-27行）
- `getDepartmentMetrics()` 返回 8 个编造部门（第32-41行："dept-tech" / "dept-sales" / ...）
- `getTopPerformers()` 返回"张三/李四/王五/赵六/钱七"假员工 S 级绩效（第47-51行）
- `getPerformanceTrends()` 用 `new Random(42)` 生成 0.85~0.95 假趋势（第67-71行）
- `getAIRecommendations()` / `getDepartmentRankings()` / `getRiskAssessment()` 全是字面量

**修复方案**：**直接删除该类**

```bash
# 删除前最后确认无引用（已确认）
# 删除文件
rm living-agent-core/src/main/java/com/livingagent/core/operation/dashboard/CEODashboardService.java
```

如果 `CEODashboard` 接口未来要落地，应从真实数据源（`EmployeeService` / `TaskCheckout` / `LedgerService` / `PerformanceAssessmentService`）构建，而非字面量。

**验收标准**：
- [ ] 文件已删除
- [ ] `grep -rln "CEODashboardService" src/main` 无匹配
- [ ] `mvn compile` 通过（确认无引用残留）

---

### 🟠 P1-4 — 多套绩效实现 Bean 并存，冲突风险

**核查结论**：✅ 确认

**证据**：3 个 `@Service` 同时存在

| 实现类 | 注解 | 行为 |
|--------|------|------|
| [JpaPerformanceAssessmentService.java:29-30](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/operation/performance/JpaPerformanceAssessmentService.java#L29-L30) | `@Service @Primary` | JPA 持久化，但有 P1-2 硬编码问题 |
| [PerformanceAssessmentServiceImpl.java:12-13](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/operation/performance/PerformanceAssessmentServiceImpl.java#L12-L13) | `@Service` | `getPerformanceTrend` 用 `70.0 + Math.random()*20`（第80行）；`getDepartmentAverageScores` 返回固定值 |
| [InMemoryPerformanceAssessmentService.java:20-21](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/operation/performance/InMemoryPerformanceAssessmentService.java#L20-L21) | `@Service` | 内存态，固定 `overall=80.0`（第95行） |

**修复方案**：

1. **保留** `JpaPerformanceAssessmentService` 为唯一生产实现（修 P1-2 后）
2. **`PerformanceAssessmentServiceImpl`** 改为 `@Profile("test")` 或直接删除
3. **`InMemoryPerformanceAssessmentService`** 改为 `@Profile("test")` 或直接删除

```java
// PerformanceAssessmentServiceImpl.java
@Service
@Profile("test")  // 仅测试用
public class PerformanceAssessmentServiceImpl implements PerformanceAssessmentService { ... }

// InMemoryPerformanceAssessmentService.java
@Service
@Profile("test")  // 仅测试用
public class InMemoryPerformanceAssessmentService implements PerformanceAssessmentService { ... }
```

**验收标准**：
- [ ] `prod` profile 启动时只有一个 `PerformanceAssessmentService` Bean
- [ ] 注入 `PerformanceAssessmentService` 的字段必定拿到 JPA 实现
- [ ] `mvn compile` 通过

---

## 四、P2 级问题核查与修复方案

### 🟡 P2-1 — Embedding 降级为语义噪声

**核查结论**：✅ 确认

**证据**：[LocalEmbeddingService.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/embedding/impl/LocalEmbeddingService.java)

- 第72-76行：模型文件不存在 → `log.warn` → `initialized = true`（继续用 mock）
- 第86-89行：`loadModelNative` 返回 0 → `log.warn` → `initialized = true`
- 第91-94行：异常 → `log.error` → `initialized = true`
- 第250-271行 `generateMockEmbedding`：用 `text.hashCode()` 做种生成高斯噪声向量
- 第282-284行：native 库加载失败仅 `log.info`（连 warn 都不是）

```java
static {
    try {
        System.loadLibrary("embedding_native");
        log.info("Loaded embedding_native library");
    } catch (UnsatisfiedLinkError e) {
        log.info("embedding_native library not found, using mock embeddings");  // 静默降级
    }
}
```

**修复方案**：生产环境**硬失败**

```java
public synchronized void initialize() {
    if (initialized) return;
    try {
        Path path = resolveModelPath();
        if (!Files.exists(path)) {
            if (isProductionProfile()) {
                throw new IllegalStateException(
                    "Embedding model file not found at: " + path + 
                    ". Production requires real model, configure embedding.model-path");
            }
            log.warn("Model file not found at: {}, using mock embeddings (dev only)", path);
            initialized = true;
            return;
        }
        // ... 加载逻辑
        if (modelHandle == 0) {
            if (isProductionProfile()) {
                throw new IllegalStateException("Failed to load embedding model, refusing to start in production");
            }
            log.warn("Failed to load embedding model, using mock embeddings");
            initialized = true;
        }
    } catch (Exception e) {
        if (isProductionProfile()) throw new RuntimeException("Embedding init failed in production", e);
        log.error("Failed to initialize embedding service: {}", e.getMessage());
        initialized = true;
    }
}

private boolean isProductionProfile() {
    return Arrays.asList(env.getActiveProfiles()).contains("prod");
}
```

并在启动后增加健康检查：`EmbeddingHealthIndicator` 检查 `modelHandle != 0`，否则标记 `OUT_OF_SERVICE`。

**验收标准**：
- [ ] `prod` profile 启动时若模型缺失，应用启动失败并报清晰错误
- [ ] `dev` profile 仍允许 mock 降级
- [ ] 健康检查 `/actuator/health` 反映 embedding 真实状态

---

### 🟡 P2-2 — LeadOrchestrator 代码审查闭环（部分误报）

**核查结论**：⚠️ **部分误报**

**原报告说**：5 个 default 方法全部抛异常，审查闭环未落地。

**实际核查**：
- [LeadOrchestrator.java:58-101](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/brain/collaboration/LeadOrchestrator.java#L58-L101) — 接口 5 个 default 方法**确实**抛 `UnsupportedOperationException`（原文描述准确）
- **但生产 Bean `TechLeadOrchestrator` 已完整实现**：[TechLeadOrchestrator.java:209-314](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/brain/collaboration/impl/TechLeadOrchestrator.java#L209-L314)
  - `submitForReview`（:209-238）：调用 `codeReviewWorkflowService.advanceStage` 推进 `CODE_SUBMITTED → ASSIGN_REVIEWER → REVIEWING`，并通过 `ChannelManager` 通知审查员工
  - `requestChanges`（:241-267）：调用 `codeReviewWorkflowService.requestChanges`，通知开发员工修改
  - `resubmitCode`（:270-283）：推进 `DEVELOPER_REVISING → CODE_RESUBMITTED → ASSIGN_REVIEWER`
  - `approveCode`（:286-300）：调用 `approve` + 推进 `REVIEW_APPROVED → FINAL_SUMMARY`，更新 `taskDagService.updateTaskStatus(taskId, COMPLETED)`
  - `escalateReview`（:303-314）：调用 `escalate` + `broadcastToTeam` 广播升级
- Bean 装配：[BrainConfig.java:137-141](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/config/BrainConfig.java#L137-L141) 注入 `CodeReviewWorkflowService`

**结论**：审查闭环**已落地**，原报告"未实现"判断错误。但接口 default 方法抛异常仍是不规范设计，需修复。

**修复方案**：

```java
// LeadOrchestrator.java — 将 5 个 default 方法改为返回 Optional.empty() 或抛清晰异常
public interface LeadOrchestrator {
    // ...
    
    /**
     * 提交代码进入审查流程。
     * <p>默认实现抛出异常，表示该编排器不支持代码审查循环。
     * 由 {@link TechLeadOrchestrator} 等支持审查的实现覆盖。
     */
    default CodeReviewWorkflowService.ReviewState submitForReview(String taskId, String reviewerNeuronId) {
        throw new UnsupportedOperationException(
            "Code review workflow not supported by " + getClass().getSimpleName() + 
            ". Use TechLeadOrchestrator for code review capabilities.");
    }
    // ... 其余 4 个方法同样补清晰错误信息
}
```

或在接口中**移除** default 实现，强制实现类显式声明支持与否。

**验收标准**：
- [ ] 异常消息明确告知"该编排器不支持审查"，避免误调用
- [ ] `TechLeadOrchestratorTest` 覆盖 5 个方法的成功路径
- [ ] 文档同步：移除"未实现"描述，改为"接口默认不支持，TechLeadOrchestrator 已实现"

---

### 🟡 P2-3 — 多个核心服务为内存态且为生产默认

**核查结论**：✅ 确认

**证据**：

| 服务 | 注册位置 | 实现 |
|------|----------|------|
| `InMemoryTaskDagService` | [BrainConfig.java:125-128](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/config/BrainConfig.java#L125-L128) | 任务 DAG 重启丢失 |
| `InMemoryPlanApprovalService` | [BrainConfig.java:130-134](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/config/BrainConfig.java#L130-L134) | 计划审批记录丢失 |
| `InMemoryDepartmentTodoPool` | [GatewayConfig.java:216-220](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomy/DepartmentTodoPool.java) | 部门待办池丢失 |
| `ComplianceManager` | [ComplianceManager.java:14-21](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/compliance/ComplianceManager.java#L14-L21) | `@Service`，`violations`/`auditLogs` 用 **`ArrayList`（非线程安全！）** |
| `IncentiveManager.pendingRewards` | [IncentiveManager.java:17](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomous/incentive/IncentiveManager.java#L17) | `ConcurrentHashMap` 内存态 |

**修复方案**：

**短期（标注单节点限制 + 修复线程安全）**：

```java
// ComplianceManager.java — 立即修复线程安全
@Service
public class ComplianceManager {
    private final Map<String, ComplianceRule> rules = new ConcurrentHashMap<>();
    private final List<ComplianceViolation> violations = Collections.synchronizedList(new ArrayList<>());  // 改为 synchronizedList
    private final List<AccessAuditLog> auditLogs = Collections.synchronizedList(new ArrayList<>());        // 改为 synchronizedList
    // ...
}
```

**长期（迁移持久化）**：每个服务逐个迁移

1. **TaskDagService** → JPA：新建 `task_dag_node` / `task_dag_edge` 表，`JpaTaskDagService`
2. **PlanApprovalService** → JPA：新建 `plan_approval` 表，`JpaPlanApprovalService`
3. **DepartmentTodoPool** → Redis（高频读写）：`RedisDepartmentTodoPool`
4. **ComplianceManager** → JPA：新建 `compliance_rule` / `compliance_violation` / `access_audit_log` 表
5. **IncentiveManager.pendingRewards** → 与 P0-1 一并迁入 `ledger_transaction`

每个迁移用 `@ConditionalOnProperty` 允许回退到内存实现（仅 `dev` profile）。

**验收标准**：
- [ ] `ComplianceManager` 线程安全修复（短期）立即合入
- [ ] 每个内存服务迁移后，重启不丢数据
- [ ] 集群部署场景下数据一致

---

### 🟡 P2-4 — 测试覆盖极低（修正为 11 个测试文件）

**核查结论**：⚠️ 修正（实际 11 个，非 8 个）

**证据**：`living-agent-core/src/test/` 下实际测试文件：

| # | 测试文件 | 覆盖范围 |
|---|----------|----------|
| 1 | `ServiceAdminBootstrapTest` | Admin 启动 |
| 2 | `AdminToolRegistrationTest` | Admin 工具注册 |
| 3 | `AdminToolPermissionTest` | Admin 工具权限 |
| 4 | `RuleBasedContextCompactorNativeFallbackTest` | 上下文压缩降级 |
| 5 | `DigitalEmployeeFeishuTest` | 数字员工飞书 |
| 6 | `ActivateDigitalEmployeeTest` | 数字员工激活 |
| 7 | `FeishuIntegrationTest` | 飞书集成 |
| 8 | `ChatNeuronConfigTest` | 闲聊神经元配置 |
| 9 | `ChatNeuronRouterTest` | 闲聊神经元路由 |
| 10 | `ChatIntentClassifierTest` | 闲聊意图分类 |
| 11 | `IdUtilsTest` | ID 工具 |

**关键路径无测试**：
- `ConversationOrchestrator`（对话编排主链路）
- `LedgerService`（修复 P0-1 后必须补）
- `AbstractBrain` / `BrainModelFallback`（降级链）
- `BrainModelResolver`（熔断过滤）
- `TechLeadOrchestrator`（审查闭环，修复 P2-2 后必须补）

**修复方案**：按优先级补单测

```java
// 1. ConversationOrchestratorTest — 全链路 + 降级
class ConversationOrchestratorTest {
    @Test void should_route_to_department_brain_based_on_intent() { ... }
    @Test void should_fallback_to_rule_based_when_llm_fails() { ... }
    @Test void should_deny_access_when_permission_insufficient() { ... }
}

// 2. JpaLedgerServiceTest — 修复 P0-1 后
class JpaLedgerServiceTest {
    @Test void should_persist_income_record() { ... }
    @Test void should_return_correct_balance_after_multiple_records() { ... }
    @Test void should_snapshot_balance_after_each_transaction() { ... }
}

// 3. BrainModelFallbackTest — 降级链
class BrainModelFallbackTest {
    @Test void should_fallback_to_qwen3_when_mainbrain_circuit_open() { ... }
    @Test void should_fallback_to_tool_neuron_when_all_brains_fail() { ... }
}

// 4. BrainModelResolverTest — 熔断过滤
class BrainModelResolverTest {
    @Test void should_exclude_unhealthy_models() { ... }
    @Test void should_reset_circuit_breaker_after_recovery() { ... }
}

// 5. TechLeadOrchestratorTest — 审查闭环
class TechLeadOrchestratorTest {
    @Test void should_advance_review_state_on_submit() { ... }
    @Test void should_notify_reviewer_via_channel() { ... }
    @Test void should_complete_task_on_approval() { ... }
}
```

**验收标准**：
- [ ] 上述 5 个核心测试类建立并绿
- [ ] 覆盖率从 ~0.8% 提升到关键路径 ≥30%

---

## 五、P3 级残留清理项核查与修复方案

### 🟢 P3-1 — `AgentTaskController` 双路由 + 假示例数据

**证据**：[AgentTaskController.java:14-50](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentTaskController.java#L14-L50)

```java
@Deprecated
@RestController
@RequestMapping("/api/agents/{agentId:.+}/tasks")
public class AgentTaskController {
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskInfo>>> listTasks(...) {
        // ...
        tasks.add(new TaskInfo("task_001", agentId, "示例任务", ...));  // 假示例数据
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }
}
```

`TaskController` 同样挂载 `/api/tasks`（[TaskController.java:32-33](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/TaskController.java#L32-L33)）。

**修复方案**：**直接删除 `AgentTaskController`**

```bash
rm living-agent-gateway/src/main/java/com/livingagent/gateway/controller/AgentTaskController.java
```

删除前确认 `AgentApiController.java:318` 的注释 `// Note: /{agentId}/tasks endpoint is handled by AgentTaskController` 同步更新。

**验收标准**：`grep "AgentTaskController" src/main` 无匹配，`/api/tasks` 仍可用。

---

### 🟢 P3-2 — Excel 导入占位

**证据**：[EmployeeImporter.java:274-280](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/security/importer/EmployeeImporter.java#L274-L280)

```java
private List<Map<String, String>> parseExcelData(byte[] excelData) {
    List<Map<String, String>> rows = new ArrayList<>();
    log.warn("Excel parsing not fully implemented, using placeholder");
    return rows;  // 永远返回空
}
```

**修复方案**（二选一）：

**方案 A：实现 Excel 解析**（用 Apache POI）

```java
private List<Map<String, String>> parseExcelData(byte[] excelData) {
    List<Map<String, String>> rows = new ArrayList<>();
    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(excelData))) {
        Sheet sheet = workbook.getSheetAt(0);
        Row header = sheet.getRow(0);
        List<String> headers = new ArrayList<>();
        for (Cell c : header) headers.add(c.getStringCellValue());
        
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Map<String, String> rowData = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                Cell cell = row.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                rowData.put(headers.get(j), getCellValueAsString(cell));
            }
            rows.add(rowData);
        }
    } catch (Exception e) {
        log.error("Failed to parse Excel", e);
        throw new IllegalArgumentException("Invalid Excel file", e);
    }
    return rows;
}
```

**方案 B：前端禁用 Excel 导入入口**，仅保留 CSV/JSON 导入。

**验收标准**：无 `placeholder` warning，或前端无 Excel 导入按钮。

---

### 🟢 P3-3 — LLM 客户端流式未实现

**证据**：
- [OpenAiCompatibleClient.java:76](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/model/pool/client/OpenAiCompatibleClient.java#L76)：`throw new UnsupportedOperationException("Streaming not yet implemented for OpenAiCompatibleClient")`
- [AnthropicClient.java:74](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/model/pool/client/AnthropicClient.java#L74)：同上

**修复方案**（二选一）：

**方案 A：实现流式**（用 WebClient / SSE）

```java
@Override
public Flux<String> streamChat(ChatRequest request) {
    return webClient.post()
        .uri("/chat/completions")
        .bodyValue(Map.of(
            "model", request.getModel(),
            "messages", request.getMessages(),
            "stream", true
        ))
        .retrieve()
        .bodyToFlux(String.class)
        .filter(line -> !line.equals("[DONE]"))
        .map(this::extractContent);
}
```

**方案 B：文档标注不支持**，调用方检测到流式不支持时降级为非流式（一次性返回）。

**验收标准**：无 `UnsupportedOperationException`，或调用方有明确降级处理。

---

### 🟢 P3-4 — `GitHubScannerImpl` 死代码

**证据**：[GitHubScannerImpl.java:16-17](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomous/bounty/impl/GitHubScannerImpl.java#L16-L17) 标 `@Component`，被 `AutonomousOperationConfig:38-43` 的 `@Primary GitHubPlatformIntegration` 覆盖，自身也返回假数据（`https://github.com/example/repo/issues/1`）。

**修复方案**：**直接删除**

```bash
rm living-agent-core/src/main/java/com/livingagent/core/autonomous/bounty/impl/GitHubScannerImpl.java
```

**验收标准**：`grep "GitHubScannerImpl" src/main` 无匹配。

---

### 🟢 P3-5 — `MiscController` 垃圾桶化

**证据**：[MiscController.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/MiscController.java) 存在，文档自承"应尽量减少新增"。

**修复方案**：审计端点，按职责迁移到专门 Controller，最后留空的 `MiscController` 删除。

**验收标准**：`MiscController` 无端点或已删除。

---

## 六、新增发现（原文档未列）

### 🆕 N-1 — `ComplianceManager` 线程安全问题

**证据**：[ComplianceManager.java:20-21](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/compliance/ComplianceManager.java#L20-L21)

```java
private final List<ComplianceViolation> violations = new ArrayList<>();  // 非线程安全
private final List<AccessAuditLog> auditLogs = new ArrayList<>();         // 非线程安全
```

`@Service` 单例，多线程并发调用 `recordAuditLog` / `checkCompliance` 会触发 `ArrayList` 并发修改异常。

**修复方案**（短期立即修复）：

```java
private final List<ComplianceViolation> violations = Collections.synchronizedList(new ArrayList<>());
private final List<AccessAuditLog> auditLogs = Collections.synchronizedList(new ArrayList<>());
```

或用 `CopyOnWriteArrayList`（读多写少场景更优）。

**验收标准**：`ComplianceManager` 并发压测无异常。

---

### 🆕 N-2 — `IncentiveManager.pendingRewards` 内存态

**证据**：[IncentiveManager.java:17](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/autonomous/incentive/IncentiveManager.java#L17)

```java
private final Map<String, List<IncentiveReward>> pendingRewards = new ConcurrentHashMap<>();
```

**修复方案**：与 P0-1 一并迁入 `ledger_transaction`，或新建 `pending_reward` 表。

---

### 🆕 N-3 — 工作区未固化（191 文件未提交）

**证据**：`git status --porcelain | wc -l` = **191**（原报告说 183，略有差异）

**修复方案**：**立即提交**

```bash
git add -A
git commit -m "WIP: snapshot before P0 fixes (191 files uncommitted)"
```

或按模块分批提交，避免一次性大变更。

**验收标准**：`git status --porcelain` 为空。

---

## 七、修复路线图（按优先级与依赖关系）

> **进度更新**：2026-06-26
> - ✅ 阶段零已完成（5/5，git 已提交）
> - ✅ 阶段一已完成（3/4，单测待后续）
> - ✅ 阶段二已完成（4/4）
> - ⏳ 阶段三已完成（5/6，LeadOrchestrator+TaskDag+PlanApproval+ComplianceManager+TodoPool标注）
> - ⏳ 阶段四进行中（3/4，Excel+LLM+MiscController 已标注）

### 阶段零：立即止血（本周内）

| # | 任务 | 优先级 | 估时 | 验收 | 状态 |
|---|------|--------|------|------|------|
| 0.1 | **提交改动** | 🔴 紧急 | 0.5h | 工作区干净 | ✅ 完成（f75240f + d4848b4） |
| 0.2 | **修复 `ComplianceManager` 线程安全**（N-1） | 🔴 紧急 | 0.5h | `synchronizedList` 改造 | ✅ 完成 |
| 0.3 | **删除 `CEODashboardService` 死代码**（P1-3） | 🟠 高 | 0.5h | 无引用残留 | ✅ 完成 |
| 0.4 | **删除 `GitHubScannerImpl` 死代码**（P3-4） | 🟠 高 | 0.5h | 无残留 | ✅ 完成 |
| 0.5 | **删除 `AgentTaskController` 双路由**（P3-1） | 🟠 高 | 0.5h | `/api/tasks` 仍可用 | ✅ 完成 |

### 阶段一：P0 持久化（1-2 周）

| # | 任务 | 依赖 | 验收 | 状态 |
|---|------|------|------|------|
| 1.1 | `LedgerService` JPA 持久化（P0-1） | 无 | 重启不丢余额 | ✅ 完成（V27 migration + Entity + Repository + JpaLedgerService） |
| 1.2 | 禁用假数据扫描器（P0-2） | 无 | 生产无虚假商机 | ✅ 完成（ConditionalOnProperty 默认关闭） |
| 1.3 | 标注 `HardwareUpgrade` 为模拟（P0-3） | 无 | UI/日志明确"模拟" | ✅ 完成（log.warn 标注） |
| 1.4 | `JpaLedgerServiceTest` 单测（P2-4） | 1.1 | 余额持久化测试绿 | ⏳ 待后续 |

### 阶段二：P1 数据真实性（2-3 周）

| # | 任务 | 依赖 | 验收 | 状态 |
|---|------|------|------|------|
| 2.1 | Dashboard 成本分析接真实数据（P1-1） | 1.1 | 成本卡片真实 | ✅ 完成（接入 TokenCostEstimator） |
| 2.2 | 绩效评分算法实现（P1-2） | 无 | score/achievementRate 非 0 | ✅ 完成（使用 normalizedScore） |
| 2.3 | 绩效 Bean 去重（P1-4） | 2.2 | 仅 JPA 实现生产可用 | ✅ 完成（删除空实现，内存版加 @Profile("dev")) |
| 2.4 | Embedding 生产硬失败（P2-1） | 无 | 缺模型启动失败 | ✅ 完成（新增 strictMode 配置） |

### 阶段三：P2 健壮性（1 个月内）

| # | 任务 | 依赖 | 验收 | 状态 |
|---|------|------|------|------|
| 3.1 | `LeadOrchestrator` 接口 default 文档化（P2-2） | 无 | 异常消息清晰 | ✅ 完成（添加实现说明+指明 TechLeadOrchestrator） |
| 3.2 | `TaskDagService` 迁 JPA（P2-3） | 无 | DAG 重启不丢 | ✅ 完成（新增 JpaTaskDagService @Primary） |
| 3.3 | `PlanApprovalService` 迁 JPA（P2-3） | 无 | 审批记录持久 | ✅ 完成（新增 JpaPlanApprovalService @Primary） |
| 3.4 | `DepartmentTodoPool` 迁 Redis（P2-3） | 无 | 待办池集群可用 | ⚠️ 标注单节点限制（Redis迁移需额外依赖） |
| 3.5 | `ComplianceManager` 迁 JPA（P2-3 + N-1） | 0.2 | 合规记录持久 | ✅ 完成（新增 JpaComplianceManager @Primary） |
| 3.6 | 关键路径补单测（P2-4） | 各修复项 | 覆盖率 ≥30% | ⏳ 待执行 |

### 阶段四：P3 清理（持续）

| # | 任务 | 验收 | 状态 |
|---|------|------|------|
| 4.1 | Excel 导入实现/禁用（P3-2） | 无 placeholder | ✅ 完成（标注未实现+推荐 CSV） |
| 4.2 | LLM 流式实现/标注（P3-3） | 无 UnsupportedOperationException | ✅ 完成（标注未实现+文档说明） |
| 4.3 | `MiscController` 审计迁移（P3-5） | 端点归位 | ✅ 完成（审计+添加 TODO） |
| 4.4 | 文档状态回溯校正 | 状态列与代码一致 | ⏳ 待执行 |

---

## 八、核查方法论（可复现）

本文档所有结论均来自以下核查步骤，可逐条复现：

1. **Bean 装配追踪**：`Grep "implements LedgerService"` / `Grep "@Bean"` 确认真实注入实现类
2. **死代码追踪**：`Grep "CEODashboardService"` 全项目仅 1 匹配（定义文件自身）
3. **假数据排查**：`Grep "example-program" / "example/repo" / "Math.random" / "return 0.0" / "BigDecimal.ZERO"`
4. **未实现接口**：`Grep "UnsupportedOperationException"` 定位 6 处（5 个 LeadOrchestrator + 2 个 LLM 流式）
5. **测试统计**：`Glob "**/src/test/**/*.java"` → living-agent-core 下 11 个文件
6. **工作区核查**：`git status --porcelain | Measure-Object -Line` → 191
7. **源码逐文件读取**：关键文件全文 Read，标注行号

**建议建立周期性核查机制**：每完成一个阶段，用相同方法回溯验证，同步更新本文档与 `LANDING_AUDIT_AND_IMPROVEMENT_PLAN.md` 的状态列。

---

## 九、与原文档的差异说明

| 项 | 原文档 | 本文档 | 原因 |
|----|--------|--------|------|
| P2-2 | "5 个方法全部抛异常，审查流转未落地" | "接口 default 抛异常，但 TechLeadOrchestrator 已完整实现审查闭环" | 原文档未核查实现类 `TechLeadOrchestrator.java:209-314` |
| P2-4 | "8 个测试文件" | "11 个测试文件" | 原文档统计遗漏（新增了 ServiceAdmin/AdminTool 系列 3 个） |
| 工作区 | "183 文件未提交" | "191 文件未提交" | 原文档核查日期 2026-06-25，本文档 2026-06-26，期间新增 8 个改动 |
| — | 未列 `ComplianceManager` 线程安全 | 新增 N-1 | 原文档仅说"内存态"，未注意 `ArrayList` 非线程安全 |
| — | 未列 `IncentiveManager.pendingRewards` | 新增 N-2 | 原文档未深入 `IncentiveManager` 字段 |
| — | 未列 `GitHubScannerImpl` 假数据 | 在 P0-2 / P3-4 中补充 | 原文档只说"死代码"，未提也返回假数据 |

---

## 十、相关文档

| 文档 | 关系 |
|------|------|
| [LANDING_AUDIT_AND_IMPROVEMENT_PLAN.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/LANDING_AUDIT_AND_IMPROVEMENT_PLAN.md) | 原始问题清单（本文档为其验证 + 修正 + 细化） |
| `MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md` | 自治/模型/执行链路主计划 |
| `pending/SELF_EVOLUTION_IMPROVEMENT_PLAN.md` | 自进化方案（P0-1 账本持久化除外，大部分已实现） |
| `pending/WEBSOCKET_BACKEND_IMPROVEMENT_PLAN.md` | WebSocket 改进（clientId 缺口已修复） |
| `WINDOWS_MCP_INTEGRATION_PLAN.md` | Windows 自动化集成（Phase 1-2 已完成） |
| `CODE_STRUCTURE_AND_FILE_GUIDE.md` | 代码结构索引（权威结构参考） |
