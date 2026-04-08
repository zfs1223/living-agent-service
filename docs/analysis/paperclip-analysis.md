# 各部门能力增强计划

> 基于生命智能体现有架构，增强各部门核心能力
> 
> **核心原则: 保持原有业务大脑架构，在各部门基础上增强能力**

---

## 一、架构原则

### 1.1 保持原有架构

本项目采用**标准企业部门架构**，业务大脑设计如下：

| 大脑 | 部门 | 核心能力 |
|------|------|---------|
| **TechBrain** | 技术部 | 代码审查、CI/CD、架构设计 |
| **HrBrain** | 人力资源 | 招聘管理、考勤、绩效 |
| **FinanceBrain** | 财务部 | 报销审批、发票、预算 |
| **SalesBrain** | 销售部 | 销售支持、市场营销 |
| **CsBrain** | 客服部 | 工单处理、问题解答 |
| **AdminBrain** | 行政部 | 文档处理、文案创作 |
| **LegalBrain** | 法务部 | 合同审查、合规检查 |
| **OpsBrain** | 运营部 | 数据分析、运营策略 |
| **MainBrain** | 跨部门 | 协调多部门协作 |

### 1.2 能力增强映射

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    能力增强 → 部门归属                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  技术部 增强:                                                                │
│  ├── 心跳服务 - 系统运维能力                                                 │
│  ├── 适配器注册 - AI模型管理能力                                             │
│  ├── 会话管理 - 状态管理能力                                                 │
│  ├── 沙箱执行 - 安全执行能力                                                 │
│  └── 配置版本 - 配置管理能力                                                 │
│                                                                             │
│  财务部 增强:                                                                │
│  ├── 成本追踪 - Token成本估算、项目核算                                       │
│  └── 预算控制 - 月度预算管理、超支预警                                        │
│                                                                             │
│  运营部 增强:                                                                │
│  ├── 任务检出 - 原子任务分配                                                 │
│  └── 运行队列 - 并发控制、优先级调度                                          │
│                                                                             │
│  销售部 增强:                                                                │
│  ├── 平台集成 - GitHub/Upwork等平台对接                                      │
│  └── 赚钱驱动 - BountyHunter自主赚钱                                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、技术部能力增强

### 2.1 心跳服务

**目标**: 降低资源消耗，支持多数字员工并发执行

**实现内容**:

```java
@Service
public class HeartbeatService {
    
    public enum WakeSource {
        TIMER,              // 定时唤醒
        TASK_ASSIGNED,      // 任务分配
        ON_DEMAND,          // 手动触发
        EVOLUTION,          // 进化触发
        PROFIT_OPPORTUNITY  // 赚钱机会
    }
    
    public void enqueueWakeup(String employeeId, WakeSource source, WakeOptions options);
    public void executeRun(HeartbeatRun run);
    public void cancelRun(String runId);
}
```

**文件位置**: `living-agent-core/src/main/java/com/livingagent/core/tech/heartbeat/`

---

### 2.2 适配器注册

**目标**: 解耦 AI 模型执行与核心逻辑，支持多种 AI 后端

**实现内容**:

```java
public interface AgentAdapter {
    String getType();
    CompletableFuture<ExecutionResult> execute(ExecutionContext ctx);
    EnvironmentTestResult testEnvironment();
    List<ModelInfo> getSupportedModels();
}

@Component
public class AdapterRegistry {
    private final Map<String, AgentAdapter> adapters = new ConcurrentHashMap<>();
    
    public AgentAdapter getAdapter(String type);
    public void registerAdapter(AgentAdapter adapter);
}
```

**文件位置**: `living-agent-core/src/main/java/com/livingagent/core/tech/adapter/`

---

### 2.3 会话管理

**目标**: 支持任务中断恢复，长期记忆保存

**实现内容**:

```java
@Entity
@Table(name = "agent_sessions")
public class AgentSession {
    private String sessionId;
    private String employeeId;
    private String taskKey;
    private Map<String, Object> sessionParams;
    private String workspacePath;
    private Instant lastActiveAt;
}

@Service
public class SessionManager {
    public SessionState restoreSession(String employeeId, String taskKey);
    public void saveSession(String employeeId, String taskKey, SessionState state);
}
```

**文件位置**: `living-agent-core/src/main/java/com/livingagent/core/tech/session/`

---

### 2.4 沙箱执行

**目标**: 安全隔离执行，资源限制

**实现内容** (已完成):

```java
@Component
public class SandboxExecutorImpl implements SandboxExecutor {
    // 进程隔离方案
    // - 独立 JVM 进程执行
    // - 内存限制 (-Xmx 参数)
    // - 超时控制
    // - 进程强制终止
}
```

**文件位置**: `living-agent-core/src/main/java/com/livingagent/core/security/impl/`

---

### 2.5 配置版本

**目标**: 配置回滚，变更审计

**实现内容**:

```java
@Entity
@Table(name = "employee_config_revisions")
public class EmployeeConfigRevision {
    private String revisionId;
    private String employeeId;
    private int revisionNumber;
    private Map<String, Object> config;
    private String changedBy;
    private String changeReason;
    private Instant changedAt;
}

@Service
public class ConfigVersionService {
    public void saveRevision(String employeeId, Map<String, Object> config, String changedBy, String reason);
    public void rollback(String employeeId, int revisionNumber);
}
```

**文件位置**: `living-agent-core/src/main/java/com/livingagent/core/tech/config/`

---

## 三、财务部能力增强

### 3.1 成本追踪

**目标**: Token成本估算，项目独立核算

**实现内容** (已完成):

```java
@Component
public class TokenCostEstimator {
    // 云端模型价格
    // - GPT-4o: $2.50/M input, $10.00/M output
    // - Claude 3.5: $3.00/M input, $15.00/M output
    // - DeepSeek: $0.14/M input, $0.28/M output
    
    // 本地模型成本
    // - 电费 = GPU功耗(kW) × 时间(h) × 电费(元/kWh)
    
    public CostEstimate estimateCloudCost(String model, int inputTokens, int outputTokens);
    public CostEstimate estimateLocalCost(String model, int inputTokens, int outputTokens, double timeSeconds);
    
    // 项目独立核算
    public ProjectAccounting createProjectAccount(String projectId, String projectName);
    public void recordTaskExecution(String projectId, TaskCostRecord record);
    public ProjectSummary getProjectSummary(String projectId);
}
```

**文件位置**: `living-agent-core/src/main/java/com/livingagent/core/autonomous/bounty/TokenCostEstimator.java`

---

### 3.2 预算控制

**目标**: 月度预算管理，超支预警

**实现内容**:

```java
@Service
public class BudgetService {
    
    public MonthlyBudget getMonthlyBudget(String employeeId);
    public void setMonthlyBudget(String employeeId, BigDecimal limit);
    public boolean checkBudget(String employeeId, BigDecimal estimatedCost);
    public BudgetStatus getBudgetStatus(String employeeId);
    public void alertIfNearLimit(String employeeId);
}

public record BudgetStatus(
    BigDecimal limit,
    BigDecimal spent,
    BigDecimal remaining,
    double usagePercent,
    BudgetAlertLevel alertLevel
) {}
```

**文件位置**: `living-agent-core/src/main/java/com/livingagent/core/finance/budget/`

---

## 四、运营部能力增强

### 4.1 任务检出

**目标**: 原子任务分配，防止冲突

**实现内容**:

```java
@Service
public class TaskCheckoutService {
    
    @Transactional
    public Optional<Task> checkout(String taskId, String employeeId) {
        // 使用乐观锁保证原子性
        int updated = taskRepository.checkout(taskId, employeeId, 
            List.of("PENDING", "AVAILABLE"));
        if (updated > 0) {
            return taskRepository.findById(taskId);
        }
        return Optional.empty();
    }
    
    public void release(String taskId, String employeeId);
    public boolean renewCheckout(String taskId, String employeeId);
}
```

**文件位置**: `living-agent-core/src/main/java/com/livingagent/core/ops/checkout/`

---

### 4.2 运行队列

**目标**: 并发控制，优先级调度

**实现内容**:

```java
@Service
public class RunQueueService {
    
    public void enqueue(String employeeId, WakeupRequest request);
    public WakeupRequest dequeue(String employeeId);
    public int getQueueSize(String employeeId);
    public void prioritize(String employeeId, String requestId, int priority);
    public void cancel(String employeeId, String requestId);
}
```

**文件位置**: `living-agent-core/src/main/java/com/livingagent/core/ops/queue/`

---

## 五、销售部能力增强

### 5.1 平台集成

**目标**: GitHub/Upwork等赚钱平台对接

**实现内容**:

```java
public interface PlatformIntegration {
    List<Opportunity> scanOpportunities(ScanCriteria criteria);
    boolean claimTask(String opportunityId);
    DeliveryResult submitWork(String opportunityId, WorkResult result);
    PaymentStatus checkPayment(String opportunityId);
}

@Component
public class GitHubPlatformIntegration implements PlatformIntegration {
    // GitHub Issues / Pull Requests 扫描
    // 任务认领和提交
    // 支付状态跟踪
}
```

**文件位置**: `living-agent-core/src/main/java/com/livingagent/core/sales/platform/`

---

### 5.2 赚钱驱动

**目标**: BountyHunter自主赚钱

**实现内容** (已完成):

```java
public class BountyHunterSkill {
    // ROI 评估
    public ROIResult evaluateROI(Opportunity opportunity);
    
    // 任务执行
    public HuntResult hunt(Opportunity opportunity);
    
    // 收入记录
    // 通过 LedgerService 记录收入
}
```

**文件位置**: `living-agent-core/src/main/java/com/livingagent/core/autonomous/bounty/BountyHunterSkill.java`

---

## 六、数据库表设计

### 6.1 新增表

```sql
-- 心跳运行记录 (技术部)
CREATE TABLE heartbeat_runs (
    run_id VARCHAR(36) PRIMARY KEY,
    employee_id VARCHAR(36) NOT NULL,
    wake_source VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    context JSONB,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 会话存储 (技术部)
CREATE TABLE agent_sessions (
    session_id VARCHAR(36) PRIMARY KEY,
    employee_id VARCHAR(36) NOT NULL,
    task_key VARCHAR(128) NOT NULL,
    session_params JSONB,
    workspace_path VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP,
    UNIQUE(employee_id, task_key)
);

-- 成本事件 (财务部)
CREATE TABLE cost_events (
    event_id VARCHAR(36) PRIMARY KEY,
    employee_id VARCHAR(36) NOT NULL,
    project_id VARCHAR(36),
    run_id VARCHAR(36),
    input_tokens INTEGER DEFAULT 0,
    output_tokens INTEGER DEFAULT 0,
    cost_usd DECIMAL(10, 6),
    cost_cny DECIMAL(10, 6),
    model VARCHAR(64),
    provider VARCHAR(32),
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 配置版本 (技术部)
CREATE TABLE employee_config_revisions (
    revision_id VARCHAR(36) PRIMARY KEY,
    employee_id VARCHAR(36) NOT NULL,
    revision_number INTEGER NOT NULL,
    config JSONB NOT NULL,
    changed_by VARCHAR(32) NOT NULL,
    change_reason TEXT,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(employee_id, revision_number)
);

-- 月度预算 (财务部)
CREATE TABLE monthly_budgets (
    budget_id VARCHAR(36) PRIMARY KEY,
    employee_id VARCHAR(36) NOT NULL,
    year_month VARCHAR(7) NOT NULL,
    limit_cents INTEGER NOT NULL,
    spent_cents INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(employee_id, year_month)
);

-- 唤醒队列 (运营部)
CREATE TABLE wakeup_requests (
    request_id VARCHAR(36) PRIMARY KEY,
    employee_id VARCHAR(36) NOT NULL,
    wake_source VARCHAR(32) NOT NULL,
    priority INTEGER DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    context JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    scheduled_at TIMESTAMP
);
```

---

## 七、实施计划

### 7.1 第一阶段 (本周)

| 部门 | 任务 | 状态 |
|------|------|------|
| 技术部 | 适配器接口和注册表 | 待实现 |
| 技术部 | QwenAdapter/OllamaAdapter | 待实现 |
| 财务部 | 成本追踪增强 | ✅ 已完成 |
| 销售部 | TokenCostEstimator 项目核算 | ✅ 已完成 |

### 7.2 第二阶段 (下周)

| 部门 | 任务 | 状态 |
|------|------|------|
| 技术部 | 心跳服务基础实现 | 待实现 |
| 技术部 | 会话管理器 | 待实现 |
| 运营部 | 任务检出服务 | 待实现 |
| 财务部 | 预算控制 | 待实现 |

### 7.3 第三阶段 (下下周)

| 部门 | 任务 | 状态 |
|------|------|------|
| 技术部 | 配置版本控制 | 待实现 |
| 运营部 | 运行队列管理 | 待实现 |
| 销售部 | 平台集成增强 | 待实现 |

---

## 八、总结

### 8.1 架构保持不变

- **业务大脑**: 保持原有的9个业务大脑设计
- **部门结构**: 保持标准企业部门架构
- **核心能力**: 保持各部门原有核心能力

### 8.2 能力增强分布

| 部门 | 增强数量 | 主要增强 |
|------|---------|---------|
| 技术部 | 5 | 心跳、适配器、会话、沙箱、配置 |
| 财务部 | 2 | 成本追踪、预算控制 |
| 运营部 | 2 | 任务检出、运行队列 |
| 销售部 | 2 | 平台集成、赚钱驱动 |

---

*文档更新时间: 2026-03-12*
*目标项目: living-agent-service*
