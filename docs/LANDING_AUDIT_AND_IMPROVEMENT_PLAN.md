# 落地核查与改进计划

> **版本**：v1.0 | **核查日期**：2026-06-25
>
> **目的**：基于**实际代码核查**（非文档自述状态）整理 Living Agent Service 的落地情况与遗留问题，建立一份可执行的改进清单。
>
> **核查方法**：编译验证（`mvn compile` 通过）、源码逐文件排查、Bean 装配追踪、git 工作区状态核查。所有问题均标注 `文件:行号` 证据。
>
> **重要说明**：本文档与既有计划文档（如 `MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md`、`pending/INDEX.md` 等）存在差异——那些文档普遍自述"✅ 已完成"，但代码核查发现多处**假数据、内存态账本、死代码和未实现接口**。本文以代码实际状态为准。

---

## 一、核查结论总览

| 维度 | 状态 | 说明 |
|------|------|------|
| 编译 | ✅ 通过 | `mvn compile`（core/gateway/app）成功，无编译错误 |
| 工作区 | ⚠️ 未固化 | **183 个文件改动未提交**（Java 97、前端 10、文档 17、其余数据/配置 59），任何重启/误操作都会丢失大量工作 |
| 骨架完整度 | ✅ 高 | 自治链路、大脑/员工/模型池/工具/沙箱主链路齐全且接入 |
| 持久化 | 🔴 不足 | 财务/积分账本、DAG、审批、待办池均为纯内存态，重启即清零 |
| 数据真实性 | 🔴 有隐患 | Dashboard 成本分析、绩效指标、Bug赏金/外包扫描器返回硬编码假数据 |
| 测试覆盖 | 🔴 极低 | 全项目 953 个源文件，仅 8 个测试文件（~0.8%） |
| 文档与实现一致性 | 🔴 脱节 | 多份计划文档自述"全部完成"，实际存在未落地的持久化层与假数据 |

**一句话结论**：编译通过、骨架完整，但**距离生产可用还有明显差距**——核心财务体系无持久化、多处假数据伪装成真实指标、测试几乎为零。

---

## 二、已确认真实落地的能力

以下能力经代码核查确认**真实接入**，非文档空话：

| 能力 | 关键证据 |
|------|----------|
| 自治编排主链路 | `ConversationOrchestrator`（意图→规划→路由→分派→执行→回执→主脑收口），LLM 主实现 + 规则降级闭环完整 |
| 模型池/熔断/降级 | `ModelHealthRegistry`（5 种状态）、`BrainModelResolver`（熔断过滤）、`BrainAutoAssigner`（启动自动分配） |
| 产物持久化 | `JpaArtifactRecordService` + `ArtifactController` + `V8__artifact_records.sql` |
| 执行回执持久化 | `FileBasedEmployeeExecutionReceiptService`（生产文件）+ `JpaEmployeeExecutionReceiptService`（DB） |
| 自进化基础设施 | `ErrorCodeMapper`、`CodebaseAccessService`、`PatchProposalService`、`ArchitectureKnowledgeSeeder`、`SourceTreeIndexer` 均在 `MemoryConfig` 注册并经 `ApplicationRunner` 启动执行 |
| Docker 沙箱隔离 | `HybridSandboxService` + docker-socket-proxy 白名单（CONTAINERS/EXEC/IMAGES/NETWORKS/VOLUMES） |
| Admin 管理工具桥接 | `tool/impl/admin/` 下 GitLab/OpenProject/Jenkins AdminTool + `DefaultServiceAdminBootstrap`（2026-06-25 新建） |
| Windows 自动化 clientId | `AgentWebSocketHandler:170-175`、`DepartmentWebSocketHandler:187-228` 已解析、绑定并注册设备（文档列的"❌ 缺口"实际已修复，文档过时） |

---

## 三、遗留问题清单（按严重度）

### 🔴 P0-1 — 财务/积分账本纯内存态，重启即清零

**证据**：`living-agent-core/.../autonomous/config/AutonomousOperationConfig.java:63-67`

```java
@Bean
public LedgerService ledgerService() {
    log.info("Initializing LedgerService as unified balance source");
    return new InMemoryLedgerService();   // 内部是 ConcurrentHashMap
}
```

**影响范围**（全部依赖这一个内存 Bean）：
- `CreditAccountService` → `UnifiedCreditAccountService(ledgerService)`（`:104-107`）
- `EvolutionTracker` → `UnifiedEvolutionTracker(ledgerService)`（`:110-113`）
- `IncentiveManager`（`:116-121`）
- `EvolutionManager`（`:96-101`）

**后果**：整个"自主赚钱 / 积分 / 收益 / 进化基金"体系是自治赚钱叙事的核心，但**容器重启后所有员工余额、收入历史、进化基金全部丢失**，没有任何数据库表支撑。

**修复方向**：
1. 新建 `ledger_transaction` 表（账户、变动类型、金额、余额快照、时间、关联 ID）
2. 新增 `JpaLedgerService implements LedgerService`，写穿 DB
3. `AutonomousOperationConfig` 改为注入 JPA 实现，`InMemoryLedgerService` 降级为测试用

---

### 🔴 P0-2 — Bug赏金/外包扫描器返回硬编码假数据（激活 Bean）

**证据**：`AutonomousOperationConfig.java:45-55` 注册了三个扫描器 Bean：

| 扫描器 | 注册行 | 实现文件 | 是否有真实 API |
|--------|--------|----------|----------------|
| `bugBountyScanner` | `:51` | `BugBountyScannerImpl` | ❌ 无，返回字面量假机会 |
| `freelanceScanner` | `:45` | `FreelanceScannerImpl` | ❌ 无，返回字面量假机会 |
| `gitHubScanner` | `:38-43` | `GitHubPlatformIntegration`（`@Primary`） | ✅ 有真实 GitHub API |

假数据样例：
```
"HackerOne: XSS vulnerability"  url = "hackerone.com/example-program"
"Upwork: {keyword} development" url = "upwork.com/jobs/example"
"Fiverr: {keyword} gig"
```

**后果**：`BountyHunterSkill`（`:70-81`）会持续产出**虚假"商机"**进入赚钱流程，用户/大脑误以为系统在真实接单。`GitHubScannerImpl`（`@Component`）虽被 `@Primary` 覆盖，但仍存活，配置一改就可能被选中。

**修复方向**：
1. Bug赏金/外包若无真实接入计划 → 改为 `@ConditionalOnProperty` 默认禁用，或删除实现
2. 若要保留 → 标注 `@Profile("dev")` 或增加 `enabled` 开关，避免生产环境产出假数据
3. 删除或隔离 `GitHubScannerImpl`（被 `@Primary` 覆盖的死代码）

---

### 🔴 P0-3 — `InMemoryHardwareUpgradeService` 是空操作

**证据**：`AutonomousOperationConfig.java:89-93`，`InMemoryHardwareUpgradeService.evaluateUpgrade` 返回硬编码升级模板（"RTX 5090 32GB" if 余额≥500000），`executeUpgrade` 仅盖一个成功 ID 不执行任何动作。

**后果**：`EvolutionManager` 调用的"硬件升级"是假的，进化资金花出去了但什么都没发生。

**修复方向**：与 P0-1 一并处理，或在 UI 明确标注"模拟/未启用"。

---

### 🟠 P1-1 — Dashboard 成本分析返回全零

**证据**：`DashboardServiceImpl.java:196-206`

```java
private DashboardDTOs.CostAnalysis buildCostAnalysis() {
    return new DashboardDTOs.CostAnalysis(
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, ...
        List.of()   // 空成本明细
    );
}
```

**后果**：Dashboard 成本分析卡片永远显示 0，前端看似有数据实则空。`buildTaskMetrics`（`:193`）、`buildDepartmentHealth`/`toDepartmentSummary` 也对若干指标传 `0`。

**修复方向**：接入真实的 `LedgerService`（修复 P0-1 后）或 token 成本统计；未完成前前端隐藏该卡片。

---

### 🟠 P1-2 — 绩效指标评分硬编码 0.0

**证据**：`JpaPerformanceAssessmentService.java:232-243`

```java
private PerformanceIndicator toIndicator(PerformanceIndicatorEntity entity) {
    return new PerformanceIndicator() {
        ...
        @Override public double getActualValue() { return entity.getTargetValue() ... }  // 用目标值冒充实绩
        @Override public double getScore() { return 0.0; }            // 硬编码
        @Override public double getAchievementRate() { return 0.0; }  // 硬编码
    };
}
```

同时 `getDepartmentAverageScores()`（`:120-133`）所有维度分数都等于 `overall` 平均值的复制，不是分维计算。

**后果**：绩效看板的指标得分和达成率永远为 0，部门维度分数无差异。

**修复方向**：实现真实评分算法（actualValue 从任务数据聚合，score = actual/target 加权）。

---

### 🟠 P1-3 — `CEODashboardService` 100% 假数据（死代码地雷）

**证据**：`operation/dashboard/CEODashboardService.java`（全文）

返回编造的高管数据：`getCompanyOverview()` 返回 `150, 120, 80, 40, 8, 0.85, 0.92, 15000, 230`；`getDepartmentMetrics()` 返回 8 个编造部门行；`getTopPerformers()` 返回"张三/李四/王五"等假员工 S 级绩效。

**当前状态**：未注入（无 `@Service`，gateway 未引用）= 死代码。

**风险**：定时炸弹——一旦有人注册成 Bean，CEO 看到的全是假数字。

**修复方向**：**直接删除该类**，或如果 CEO Dashboard 是规划中能力，则建立独立任务从真实数据源构建。

---

### 🟠 P1-4 — 多套绩效实现 Bean 并存，冲突风险

**证据**：
- `PerformanceAssessmentServiceImpl`（`@Service`，`getPerformanceTrend` 用 `70.0 + Math.random()*20`）
- `JpaPerformanceAssessmentService`（`@Primary`、`@Service`）
- `InMemoryPerformanceAssessmentService`（`@Service`，返回固定 `overall=80.0`）

**后果**：三个 `@Service` 同时存在，依赖注入行为依赖 `@Primary`，配置变化或新增模块注入时可能注入到假数据实现。

**修复方向**：保留 JPA 实现为唯一生产实现，其余改为 `@Profile("test")` 或删除。

---

### 🟡 P2-1 — Embedding 降级为语义噪声

**证据**：`LocalEmbeddingService.java`（`generateMockEmbedding` 约 `:250-265`）

模型文件缺失或加载失败时回退到 `generateMockEmbedding`：用 `text.hashCode()` 做种生成高斯噪声向量。仅打 `log.warn`（`:73,87,283`）。

**后果**：RAG 检索质量会**静默坍塌成哈希噪声**——相似度计算无意义，但系统不报错，生产中很难发现知识库检索已失效。

**修复方向**：生产环境配置为**硬失败**（启动时校验模型文件存在，缺失则拒绝启动），仅开发环境允许 mock 降级。

---

### 🟡 P2-2 — `LeadOrchestrator` 代码审查闭环未实现

**证据**：`brain/collaboration/LeadOrchestrator.java:58-101`

5 个 default 方法全部抛异常：
```java
default ... submitForReview(...) { throw new UnsupportedOperationException("submitForReview not implemented"); }
default ... requestChanges(...)  { throw new UnsupportedOperationException("requestChanges not implemented"); }
default ... resubmitCode(...)    { throw new UnsupportedOperationException("resubmitCode not implemented"); }
default ... approveCode(...)     { throw new UnsupportedOperationException("approveCode not implemented"); }
default ... escalateReview(...)  { throw new UnsupportedOperationException("escalateReview not implemented"); }
```

**后果**：代码任务审查循环（提交→评审→改→再提交→通过/升级）仅 `TechLeadOrchestrator` 实现了基础分工，审查流转未落地。任何调用这些方法的路径会直接抛异常。

**修复方向**：在 `TechLeadOrchestrator` 或新建实现类中落地审查状态机，或暂时移除这些接口方法避免误调用。

---

### 🟡 P2-3 — 多个核心服务为内存态且为生产默认

| 服务 | 注册位置 | 风险 |
|------|----------|------|
| `InMemoryTaskDagService` | `BrainConfig.java:127` | 任务 DAG 重启丢失，不支持集群 |
| `InMemoryPlanApprovalService` | `BrainConfig.java:133` | 计划审批记录丢失 |
| `InMemoryDepartmentTodoPool` | `GatewayConfig.java:219` | 部门待办池丢失（文档自承"后续可替换为 Redis"） |
| `ComplianceManager`（内存 ArrayList） | — | 合规记录无持久化 |

**修复方向**：逐个迁移到 JPA/Redis；至少标注当前为单节点内存态，不支持水平扩展。

---

### 🟡 P2-4 — 测试覆盖极低（~0.8%）

**证据**：全项目 953 个 Java 源文件，仅 `living-agent-core/src/test/` 下 8 个测试文件：
```
RuleBasedContextCompactorNativeFallbackTest
ChatIntentClassifierTest / ChatNeuronConfigTest / ChatNeuronRouterTest
FeishuIntegrationTest
ActivateDigitalEmployeeTest / DigitalEmployeeFeishuTest
IdUtilsTest
```

**后果**：核心的 `ConversationOrchestrator`、`LedgerService`、`AbstractBrain` 降级链、`BrainModelResolver` 等关键路径**无任何测试**，重构风险极高。

**修复方向**：优先为以下路径补单测：
1. `ConversationOrchestrator` 全链路（含降级）
2. `LedgerService`（修复 P0-1 后）
3. `AbstractBrain` / `BrainModelFallback` 降级链
4. `BrainModelResolver` 熔断过滤

---

### 🟢 P3 — 残留清理项

| 项 | 证据 | 建议 |
|----|------|------|
| `AgentTaskController` 整类 `@Deprecated` 仍挂路由 | `/api/agents/{id}/tasks` 与 `TaskController` 的 `/tasks` 双路由并存 | 删除该 Controller，统一到 `/tasks` |
| Excel 导入占位 | `EmployeeImporter.java:277` `log.warn("Excel parsing not fully implemented, using placeholder")` | 实现或从前端禁用入口 |
| LLM 客户端流式未实现 | `AnthropicClient:74`、`OpenAiCompatibleClient:76` 抛 `Streaming not yet implemented` | 实现流式或文档标注不支持 |
| `GitHubScannerImpl` 死代码 | 被 `@Primary` 覆盖但仍 `@Component` 存活 | 删除 |
| `MiscController` 垃圾桶化 | 文档自承"应尽量减少新增" | 审计并迁移端点 |

---

## 四、文档与实现脱节清单

以下文档的"状态"列与代码实际不符，需更新：

| 文档 | 文档自述 | 代码实际 |
|------|----------|----------|
| `ACTIVE_FIXES_TODO.md` | "无待处理项" | 183 文件未提交；存在 P0 级持久化缺口 |
| `pending/INDEX.md` | P1-P3 全部 ✅ | Windows MCP 标 🔲 待测试；自进化部分缺 P0-1 账本持久化 |
| `WEBSOCKET_BACKEND_IMPROVEMENT_PLAN.md` §7 | clientId 链路多处 ❌ | 代码已实现（`AgentWebSocketHandler:170`、`DepartmentWebSocketHandler:187`），文档未更新 |
| `SELF_EVOLUTION_IMPROVEMENT_PLAN.md` §1.2 | 列 6 个"关键缺口"待实施 | MemoryConfig 显示 ErrorCodeMapper/CodebaseAccessService/PatchProposalService/知识播种均已实现 |
| `MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md` §2 | 全部 ✅ | 阶段5/7 的 LLM 实现自述"需按禁硬编码原则复核" |

**根因**：项目缺乏"实际落地核查"机制，依赖各计划文档的状态列，而状态列往往在规划时即标 ✅，落地后未回溯验证。

---

## 五、修复优先级路线图

> **进度更新**：2026-06-26 | 参考 [LANDING_AUDIT_VERIFICATION_AND_FIX_PLAN.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/LANDING_AUDIT_VERIFICATION_AND_FIX_PLAN.md) 获取详细进度

### 阶段一：止血（P0，建议本周）
| # | 任务 | 文件 | 验收标准 | 状态 |
|---|------|------|----------|------|
| 1 | **提交未保存改动** | git 全部 183 文件 | 工作区干净，避免丢失 | ✅ 完成 |
| 2 | `LedgerService` 改 JPA 持久化 | 新建实体/Repository/JpaLedgerService + 迁移脚本 | 重启后余额不丢失 | ✅ 完成 |
| 3 | 禁用/隔离假数据扫描器 | `AutonomousOperationConfig` + BugBounty/Freelance/GitHubScannerImpl | 生产环境不产出假机会 | ✅ 完成 |
| 4 | 删除 `CEODashboardService` 死代码 | `CEODashboardService.java` 全文 | 无残留引用 | ✅ 完成 |

### 阶段二：数据真实性（P1，建议 2 周内）
| # | 任务 | 文件 | 验收标准 | 状态 |
|---|------|------|----------|------|
| 5 | Dashboard 成本分析接真实数据 | `DashboardServiceImpl.buildCostAnalysis` | 成本卡片显示真实数字或隐藏 | ✅ 完成 |
| 6 | 绩效指标评分算法实现 | `JpaPerformanceAssessmentService.toIndicator` | score/achievementRate 非全 0 | ✅ 完成 |
| 7 | 绩效实现 Bean 去重 | 3 套 `@Service` | 仅保留 JPA 为生产实现 | ✅ 完成 |
| 8 | Embedding 生产硬失败 | `LocalEmbeddingService` | 缺模型时启动失败而非静默降级 | ✅ 完成 |

### 阶段三：健壮性（P2，建议 1 个月内）
| # | 任务 | 文件 | 验收标准 | 状态 |
|---|------|------|----------|------|
| 9 | 内存态服务迁移持久化 | TaskDag/PlanApproval/TodoPool/Compliance | 重启不丢、支持集群 | ✅ 完成（TaskDag/PlanApproval/Compliance JPA，TodoPool标注限制） |
| 10 | 代码审查闭环实现 | `LeadOrchestrator` 5 方法 | 提交→评审→通过 状态机可用 | ✅ 完成（接口文档化+指明 TechLeadOrchestrator） |
| 11 | 关键路径补单测 | 8 项核心服务 | 覆盖率提升至可重构水平 | ⏳ 待执行 |

### 阶段四：清理（P3，持续）
| # | 任务 | 文件 | 验收标准 | 状态 |
|---|------|------|----------|------|
| 12 | 删除 `@Deprecated` 双路由 Controller | `AgentTaskController` | 任务接口统一 `/tasks` | ✅ 完成 |
| 13 | 实现/禁用 Excel 导入 | `EmployeeImporter` | 不留 placeholder | ✅ 完成（标注未实现+推荐 CSV） |
| 14 | LLM 流式实现或文档标注 | `AnthropicClient`/`OpenAiCompatibleClient` | 无 UnsupportedOperationException | ✅ 完成（标注未实现+文档说明） |
| 15 | 文档状态回溯校正 | 各计划文档 | 状态列与代码一致 | ✅ 完成（本更新） |

---

## 六、核查方法论说明

本文档的所有结论均来自以下**可复现**的核查步骤：

1. **编译验证**：`mvn -o compile -pl core,gateway,app -am` → exit 0
2. **工作区核查**：`git status --porcelain` → 183 文件未提交
3. **死代码追踪**：`grep -rln "CEODashboardService"` → 仅定义文件，无引用
4. **Bean 装配追踪**：逐个 `@Bean` 方法确认注入的真实实现类
5. **假数据排查**：搜索 `return 0.0`、`Math.random`、`List.of(...)` 字面量、`UnsupportedOperationException`
6. **测试统计**：`find -path "*/src/test/*" -name "*.java"` → 8 个

**建议建立周期性核查机制**：每完成一个阶段，用相同方法回溯验证代码状态，并同步更新本文档与各计划文档的状态列，避免"文档乐观、代码藏雷"的脱节再次发生。

---

## 七、相关文档

| 文档 | 关系 |
|------|------|
| `MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md` | 自治/模型/执行链路主计划（本文档发现其状态列需回溯） |
| `pending/SELF_EVOLUTION_IMPROVEMENT_PLAN.md` | 自进化方案（本文档确认其大部分已实现，P0-1 账本持久化除外） |
| `pending/WEBSOCKET_BACKEND_IMPROVEMENT_PLAN.md` | WebSocket 改进（本文档确认 clientId 缺口已修复） |
| `WINDOWS_MCP_INTEGRATION_PLAN.md` | Windows 自动化集成（本文档确认 Phase 1-2 已完成，Phase 3 待测试） |
| `CODE_STRUCTURE_AND_FILE_GUIDE.md` | 代码结构索引（权威结构参考） |
