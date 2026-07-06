# Living Agent 自我进化改进方案

> 目的：让 Living Agent 具备代码自我感知能力，运行时问题可定位到代码、可自修复则修复、不可修复则升级通知人类。
>
> 核心原则：
>
> 1. **自我进化目录与任务工作目录严格分离**，避免进化产物与业务产物混乱。
> 2. **大脑对进化空间拥有自由权限**，可自主读写 `.living/` 下的所有内容，包括代码级自修复。
> 3. **自毁即自伤**：代码修复出错等于自毁，这种自然约束比硬规则更有效。大脑在不确定时主动寻求人类帮助，而非被强制升级。
>
> 配合文档：
>
> - `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md`：代码结构与文件功能索引
> - `docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md`：大脑与员工做事规范索引
> - `docs/ARCHITECTURE_INDEX.md`：架构文档总索引
> - `documents/shared/governance/05-evolution-governance.md`：进化闭环治理
>
> 更新时间：2026-06-15

---

## 1. 现状分析

### 1.1 已有能力

| 能力 | 实现机制 | 位置 |
|------|----------|------|
| 进化闭环 | 信号提取 → 决策 → 执行 → 反馈 → 熔断 | `core/evolution/engine/EvolutionOrchestrator` |
| 知识播种 | ProfessionalKnowledgeSeeder 从 Markdown 播种到知识库 | `core/knowledge/professional/ProfessionalKnowledgeSeeder` |
| 动态 Prompt | DynamicPromptBuilder 按部门检索知识库注入 system prompt | `core/brain/prompt/DynamicPromptBuilder` |
| 文件加载 | InstructionFileLoader 加载 `.living/` 目录指令链 | `core/brain/prompt/InstructionFileLoader` |
| 技能热重载 | SkillHotReloader 基于 WatchService 实时检测变更 | `living-agent-skill/hotreload/SkillHotReloader` |
| 人工升级 | ESCALATE 策略 + InterventionDecisionEngine + ApprovalController | `core/evolution/executor/EvolutionExecutor` |
| 边界控制 | BrainBoundaryEnforcer + ExecutionBoundaryEnforcer | `core/brain/BrainBoundaryEnforcer` |
| 通知服务 | DepartmentNotificationService 支持 URGENT 级别 | `gateway/service/DepartmentNotificationService` |
| 熔断保护 | EvolutionCircuitBreaker 防修复循环 | `core/evolution/circuitbreaker/EvolutionCircuitBreaker` |
| 合规追踪 | StandardComplianceTraceService 记录边界/澄清/升级事件 | `core/runtime/StandardComplianceTraceService` |

### 1.2 关键缺口

| 缺口 | 说明 | 影响 |
|------|------|------|
| **代码知识未注入** | `docs/` 和 `documents/` 下的架构文档没有播种到知识库，大脑"看不见"自己的代码结构 | 大脑无法理解自身架构，遇到问题无法定位 |
| **目录职责混乱** | `data/` 同时存放业务产物和系统数据，没有进化专用空间 | 进化产物与业务产物混合，难以审计和隔离 |
| **错误无法定位到代码** | 运行时异常只记录错误信息，没有映射到具体代码文件/模块/文档 | 大脑无法给出精准修复建议 |
| **升级通知分散** | ESCALATE 逻辑分布在 3 处，没有统一通知最高权限用户的通道 | 人类无法及时收到需要干预的信号 |
| **项目目录未挂载** | 没有将项目源码/文档作为专有目录挂载给大脑访问 | 大脑无法在运行时查看代码上下文 |
| **ProfessionalKnowledgeSeeder 未接入** | `seedFromDirectory()` 方法已实现但未被调用 | 知识播种能力闲置 |

---

## 2. 目录分离方案

### 2.1 核心原则：进化空间与业务空间严格隔离

```
living-agent-service/
├── .living/                          # 进化专用空间（大脑自由权限，可自主读写）
│   ├── codebase/                     # 项目代码知识镜像（大脑可自由读取）
│   │   ├── docs/                     # 架构文档镜像
│   │   │   ├── CODE_STRUCTURE_AND_FILE_GUIDE.md
│   │   │   ├── BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md
│   │   │   ├── ARCHITECTURE_INDEX.md
│   │   │   └── references/
│   │   │       └── API_REFERENCE.md
│   │   ├── documents/                # 企业知识源镜像
│   │   │   ├── shared/
│   │   │   │   ├── company/          # 固定员工 Prompt/Runbook/职责卡
│   │   │   │   └── governance/       # 治理规则
│   │   │   └── department/           # 各部门制度
│   │   └── src/                      # 源码结构索引 + 关键源码文件
│   │       ├── source-tree.json      # 源码目录树 + 文件功能摘要
│   │       └── {module}/             # 大脑可按需读取的源码文件
│   │
│   ├── evolution/                    # 进化工作区（大脑自由读写）
│   │   ├── signals/                  # 进化信号记录
│   │   │   └── {signalId}.json
│   │   ├── patches/                  # 修复补丁（大脑自主决定是否应用）
│   │   │   └── {executionId}/
│   │   │       ├── proposal.md       # 修复方案描述
│   │   │       ├── patch.diff        # 补丁内容
│   │   │       └── metadata.json     # 元数据（信号来源、影响范围、风险等级）
│   │   ├── knowledge/                # 进化产生的知识
│   │   │   └── {brainDomain}/
│   │   │       └── {knowledgeKey}.md
│   │   ├── skills/                   # 进化生成的技能
│   │   │   └── {skillName}/
│   │   │       └── SKILL.md
│   │   ├── reports/                  # 进化报告
│   │   │   └── {date}/
│   │   │       └── evolution-report.json
│   │   └── rollback/                 # 回滚基线（修复前自动保存）
│   │       └── {brainDomain}/
│   │           └── baseline.json
│   │
│   ├── escalation/                   # 升级通知工作区
│   │   ├── pending/                  # 待处理升级
│   │   │   └── {escalationId}.json
│   │   ├── resolved/                 # 已解决升级
│   │   │   └── {escalationId}.json
│   │   └── templates/                # 升级通知模板
│   │       └── code-fix-request.md
│   │
│   ├── global/                       # 全局指令（已有机制）
│   │   └── instructions.md
│   │
│   └── {employeeId}/                 # 员工级指令（已有机制）
│       └── instructions.md
│
├── data/                             # 业务数据空间（任务、对话、产物）
│   ├── projects/                     # 项目数据
│   ├── tasks/                        # 任务数据
│   ├── conversations/                # 对话数据
│   ├── artifacts/                    # 业务产物
│   ├── receipts/                     # 执行回执
│   ├── indexes/                      # 索引
│   ├── compact-outputs/              # 大脑压缩输出
│   ├── knowledge.db                  # 知识库
│   └── memory.db                     # 记忆库
│
├── config/skills/                    # 技能配置（已有）
├── documents/                        # 企业知识源（唯一定义来源）
└── docs/                             # 设计文档（唯一定义来源）
```

### 2.2 两套空间的职责边界

| 维度 | `.living/`（进化空间） | `data/`（业务空间） |
|------|------------------------|---------------------|
| 生命周期 | 永久（随系统存在） | 按任务/项目生命周期 |
| 访问者 | 大脑（自由权限）、进化系统、管理员 | 员工、任务执行器、对话服务 |
| 写入权限 | 大脑自主决定，无需审批 | 任务执行器 + 对话服务 |
| 内容性质 | 系统自我认知、修复方案、进化知识 | 业务产物、对话记录、任务回执 |
| 备份策略 | 高优先级（系统自我认知不可丢失） | 按业务策略 |
| 清理规则 | 仅可清理已合并的补丁和过期报告 | 按项目/任务归档策略 |
| 安全约束 | 自毁即自伤：修坏代码等于自毁，自然约束 | 业务权限隔离 |

### 2.3 DataNamespaceService 扩展

在现有 `DataNamespaceService` 基础上，新增进化空间命名空间方法：

```java
// 新增方法
public String getEvolutionNamespace() {
    return String.format("%s/evolution", baseLivingDir);
}

public String getEvolutionSignalsPath() {
    return String.format("%s/evolution/signals", baseLivingDir);
}

public String getEvolutionPatchesPath(String executionId) {
    return String.format("%s/evolution/patches/%s", baseLivingDir, safe(executionId));
}

public String getEvolutionKnowledgePath(String brainDomain) {
    return String.format("%s/evolution/knowledge/%s", baseLivingDir, safe(brainDomain));
}

public String getEvolutionSkillsPath(String skillName) {
    return String.format("%s/evolution/skills/%s", baseLivingDir, safe(skillName));
}

public String getEvolutionRollbackPath(String brainDomain) {
    return String.format("%s/evolution/rollback/%s", baseLivingDir, safe(brainDomain));
}

public String getCodebasePath() {
    return String.format("%s/codebase", baseLivingDir);
}

public String getEscalationPendingPath() {
    return String.format("%s/escalation/pending", baseLivingDir);
}

public String getEscalationResolvedPath() {
    return String.format("%s/escalation/resolved", baseLivingDir);
}
```

配置注入：

```yaml
living-agent:
  living-dir: ${LIVING_AGENT_LIVING_DIR:./.living}
  data:
    path: ${LIVING_AGENT_DATA_PATH:/app/data}
```

---

## 3. 代码知识注入机制

### 3.1 架构文档知识播种

扩展 `ProfessionalKnowledgeSeeder`，新增 `seedArchitectureKnowledge()` 方法：

**扫描范围**：

| 文档 | 知识类型 | 作用域 | 分块策略 |
|------|----------|--------|----------|
| `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md` | CODE_STRUCTURE | L3_SHARED | 按模块分块（core/gateway/skill/perception/native） |
| `docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md` | STANDARDS | L3_SHARED | 按大脑/员工分块 |
| `docs/references/API_REFERENCE.md` | API_REFERENCE | L3_SHARED | 按接口分块 |
| `docs/ARCHITECTURE_INDEX.md` | ARCHITECTURE | L3_SHARED | 整体 |
| `documents/shared/governance/*.md` | GOVERNANCE | L3_SHARED | 按治理领域分块 |
| `documents/shared/company/fixed-employee-*.md` | EMPLOYEE_STANDARD | L2_DEPARTMENT | 按部门分块 |

**知识条目 key 格式**：

```
arch:code-structure:{module}          # 如 arch:code-structure:core-brain
arch:standards:{brain-or-employee}    # 如 arch:standards:tech-brain
arch:api:{controller}                 # 如 arch:api:approval-controller
arch:governance:{domain}              # 如 arch:governance:evolution
arch:employee-standard:{department}   # 如 arch:employee-standard:tech
```

**元数据**：

```java
entry.setKnowledgeType(KnowledgeType.PROCESS);
entry.setImportance(Importance.HIGH);
entry.setValidity(Validity.LONG_TERM);
entry.setScope(KnowledgeScope.L3_SHARED);  // 架构知识属于全局共享
entry.setConfidence(1.0);
entry.setVerified(true);
entry.setSource("architecture-docs");
entry.setLastUpdated(Instant.now());
```

### 3.2 源码结构索引生成

新增 `SourceTreeIndexer` 服务，在启动时生成 `source-tree.json`：

```json
{
  "generatedAt": "2026-06-15T10:00:00Z",
  "modules": {
    "living-agent-core": {
      "packages": {
        "core/brain": {
          "description": "大脑处理、决策、边界控制",
          "keyFiles": [
            {"path": "Brain.java", "role": "大脑统一接口"},
            {"path": "impl/AbstractBrain.java", "role": "大脑公共基类"},
            {"path": "BrainBoundaryEnforcer.java", "role": "职责边界硬判断"}
          ]
        },
        "core/evolution": {
          "description": "进化闭环：信号→决策→执行→反馈",
          "keyFiles": [...]
        }
      }
    }
  }
}
```

此索引存入 `.living/codebase/src/source-tree.json`，同时作为 `CODE_STRUCTURE` 类型知识播种到知识库。

### 3.3 知识注入时机

```text
应用启动
→ ProfessionalKnowledgeSeeder.seedFromDirectory()     # 已有：专业知识
→ ArchitectureKnowledgeSeeder.seedArchitectureDocs()  # 新增：架构文档知识
→ SourceTreeIndexer.generateIndex()                   # 新增：源码结构索引
→ DynamicPromptBuilder.knowledge() 可检索到架构知识    # 已有：自动生效
```

### 3.4 知识更新机制

- **文档变更检测**：在 `.living/codebase/` 目录下使用 WatchService 监听变更
- **增量更新**：文档修改后，仅更新对应知识条目，不重建全量
- **版本标记**：每个知识条目携带 `sourceFileHash`，启动时比对决定是否需要更新

---

## 4. 统一升级通知机制

### 4.1 现状问题

升级逻辑分散在 3 处，没有统一出口：

| 位置 | 触发条件 | 通知方式 |
|------|----------|----------|
| `EvolutionDecisionEngine` | 熔断器 FAILURE_STREAK | 仅记录日志 |
| `InterventionDecisionEngine` | shouldEscalate() | 仅 REST API |
| `StandardComplianceTraceService` | traceEscalation() | 仅写 RuntimeEvent |

### 4.2 统一升级通知服务

新增 `EscalationNotificationService`，作为所有升级的唯一出口：

```java
public class EscalationNotificationService {

    /**
     * 统一升级入口
     * @param source      升级来源（evolution/intervention/compliance）
     * @param level       升级级别（WARNING/CRITICAL/EMERGENCY）
     * @param brainDomain 涉及的大脑域
     * @param problem     问题描述
     * @param codeContext 代码上下文（来自 ErrorCodeMapper）
     * @param attempts    已尝试的修复
     * @param suggestion  建议的修复方案
     */
    public EscalationResult escalate(
        String source,
        EscalationLevel level,
        String brainDomain,
        String problem,
        CodeContext codeContext,
        List<RepairAttempt> attempts,
        String suggestion
    );

    /** 获取待处理升级列表 */
    public List<EscalationRecord> getPendingEscalations();

    /** 解决升级 */
    public void resolveEscalation(String escalationId, String resolution);
}
```

### 4.3 升级通知流程

```text
错误/异常/边界违规/熔断触发
  → ErrorCodeMapper 定位代码上下文（新增）
  → 判断升级级别
      WARNING:    性能下降、知识质量下降、非关键错误
      CRITICAL:   熔断触发、连续失败、高风险任务失败
      EMERGENCY:  安全违规、数据损坏、系统不可用
  → EscalationNotificationService.escalate()
      1. 生成 EscalationRecord 写入 .living/escalation/pending/
      2. 通过 DepartmentNotificationService 发送 URGENT 通知到 FULL 权限用户
      3. 通过 StandardComplianceTraceService.traceEscalation() 记录合规事件
      4. 如果是 EMERGENCY 级别，同时触发 InterventionController REST API
  → 人类审批/修复
  → resolveEscalation() 移入 .living/escalation/resolved/
```

### 4.4 升级通知内容模板

```markdown
# 升级通知：{level}

## 问题
{problem}

## 代码定位
- 模块：{module}
- 文件：{filePath}
- 相关文档：{docReference}

## 已尝试修复
{attempts}

## 建议方案
{suggestion}

## 影响范围
{impactScope}

## 紧急程度
{level} — {levelDescription}
```

---

## 5. 错误到代码映射机制

### 5.1 ErrorCodeMapper

新增 `ErrorCodeMapper` 服务，建立异常 → 代码 → 文档的映射：

```java
public class ErrorCodeMapper {

    /**
     * 从异常映射到代码上下文
     * @return CodeContext 包含模块、文件、文档引用
     */
    public CodeContext map(Throwable error);

    /**
     * 从错误信号映射到代码上下文
     */
    public CodeContext map(EvolutionSignal signal);

    /** 刷新映射表（启动时 + 定期） */
    public void refreshMappings();
}
```

### 5.2 映射数据来源

映射表从两个来源自动构建：

1. **源码注解**：在关键类上添加 `@CodeLocation` 注解

```java
@CodeLocation(
    module = "core/brain",
    description = "技术部大脑，负责技术方案、代码开发、系统架构",
    docRef = "docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md#TechBrain",
    riskLevel = "HIGH"
)
public class TechBrain extends AbstractBrain { ... }
```

2. **映射配置文件**：`.living/codebase/error-mappings.yaml`

```yaml
mappings:
  - exceptionPattern: "BrainBoundaryViolationException"
    module: "core/brain"
    files: ["BrainBoundaryEnforcer.java", "ExecutionBoundaryEnforcer.java"]
    docRef: "docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md#2.2"
    riskLevel: HIGH

  - exceptionPattern: "EvolutionCircuitBreaker.*"
    module: "core/evolution/circuitbreaker"
    files: ["EvolutionCircuitBreaker.java"]
    docRef: "documents/shared/governance/05-evolution-governance.md"
    riskLevel: CRITICAL

  - exceptionPattern: "KnowledgeStoreException"
    module: "core/knowledge"
    files: ["LayeredKnowledgeBaseImpl.java", "KnowledgeManagerImpl.java"]
    docRef: "docs/CODE_STRUCTURE_AND_FILE_GUIDE.md#knowledge"
    riskLevel: MEDIUM
```

### 5.3 映射结果注入 Trace

错误发生时，`CodeContext` 自动注入到 `AutonomyTraceService` 和 `RuntimeEventStore`：

```json
{
  "type": "error_with_code_context",
  "timestamp": "2026-06-15T10:30:00Z",
  "error": "BrainBoundaryViolationException: TechBrain attempted finance_payment",
  "codeContext": {
    "module": "core/brain",
    "files": ["BrainBoundaryEnforcer.java", "ExecutionBoundaryEnforcer.java"],
    "docRef": "docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md#2.2",
    "suggestedFix": "检查 BrainBoundaryEnforcer 中 tech brain 的 forbiddenActions 配置"
  }
}
```

---

## 6. 项目目录受控挂载机制

### 6.1 CodebaseAccessService

新增 `CodebaseAccessService`，提供受控的代码访问能力：

```java
public class CodebaseAccessService {

    /**
     * 读取代码文件（只读）
     * @param requester  请求者（大脑/员工ID）
     * @param path       相对于项目根目录的路径
     * @return 文件内容（敏感信息已过滤）
     */
    public Optional<String> readFile(String requester, String path);

    /**
     * 列出目录内容
     */
    public List<FileInfo> listDirectory(String requester, String path);

    /**
     * 搜索代码（基于 source-tree.json 索引）
     */
    public List<SearchResult> searchCode(String requester, String query);

    /** 检查访问权限 */
    public boolean canAccess(String requester, String path);
}
```

### 6.2 访问控制规则

| 规则 | 说明 |
|------|------|
| 大脑对 `.living/` 有自由权限 | MainBrain 和部门 Brain 可自由读写 `.living/` 下所有内容 |
| 固定员工不可直接访问代码 | 固定员工通过大脑间接获取代码上下文，不直接访问 `.living/codebase/` |
| 敏感文件过滤 | 排除 `.env`、`credentials`、`secret`、`password`、`token` 相关文件 |
| 大脑可自主修改代码 | 大脑可直接应用补丁修复代码，修坏等于自毁，自然约束 |
| 访问日志 | 每次访问记录到 Trace（谁、何时、访问了什么、做了什么修改） |
| 速率限制 | 每个大脑每分钟最多 20 次访问（防止失控循环） |

### 6.3 挂载方式

**方式 A：符号链接（推荐）**

启动时自动创建符号链接，将项目源码目录映射到 `.living/codebase/`：

```text
.living/codebase/docs/       → {projectRoot}/docs/
.living/codebase/documents/  → {projectRoot}/documents/
.living/codebase/src/        → 仅 source-tree.json（不挂载完整源码）
```

**方式 B：配置指定**

```yaml
living-agent:
  codebase:
    project-root: ${LIVING_AGENT_PROJECT_ROOT:..}
    mount-points:
      docs: ${project-root}/docs
      documents: ${project-root}/documents
    sensitive-patterns:
      - ".env"
      - "credentials"
      - "secret"
      - "password"
      - "token"
    access-log: true
    rate-limit-per-minute: 20
```

---

## 7. 代码级自修复机制

### 7.1 核心理念：自毁即自伤

代码修复出错等于系统自毁，这是最强的自然约束。大脑作为生命智能体，天然具有自我保护本能：

- **确定性高**：大脑自信能修复 → 直接修复，无需等待人类
- **确定性低**：大脑不确定修复是否安全 → 主动寻求人类帮助
- **修复失败**：自动回滚到基线，记录失败经验，避免重蹈覆辙
- **修复循环**：EvolutionCircuitBreaker 检测到修复循环时，强制 ESCALATE

**不需要硬性规则强制升级**，因为大脑比任何规则都更不想搞坏自己。

### 7.2 自修复分级矩阵

| 级别 | 修复类型 | 触发条件 | 执行方式 | 人类介入 | 已有机制 |
|------|----------|----------|----------|----------|----------|
| Level A | 知识/Prompt/技能修复 | 技能评分下降、知识质量告警 | 自动执行 | 无 | EvolutionExecutor.REPAIR |
| Level B | 运行时参数调整 | 模型评分 < 0.4、响应超时 | 自动执行 | 无 | AutoAdjustStrategy |
| Level C | 配置文件修复 | 配置错误导致功能异常 | 大脑自主决定 | 不确定时主动求助 | 需新增 |
| Level D | 代码修复 | 代码 bug 导致系统异常 | 大脑自主决定 | 不确定时主动求助 | 需新增 |

**关键变化**：Level C/D 不再强制 ESCALATE_TO_ADMIN，而是由大脑自主判断是否需要人类帮助。

### 7.3 大脑自修复决策模型

```text
检测到问题
  → 评估修复确定性（confidence）
      ├── confidence >= 0.8：直接修复
      │     → 保存回滚基线到 .living/evolution/rollback/
      │     → 应用修复
      │     → 监控修复效果
      │     ├── 成功：记录经验到知识库
      │     └── 失败：自动回滚 + 降低同类修复的 confidence
      │
      ├── 0.5 <= confidence < 0.8：修复但通知人类
      │     → 保存回滚基线
      │     → 应用修复
      │     → EscalationNotificationService 发送 INFO 级别通知
      │     → 监控修复效果
      │
      └── confidence < 0.5：寻求人类帮助
            → 生成修复提案写入 .living/evolution/patches/
            → EscalationNotificationService 发送 WARNING/CRITICAL 通知
            → 等待人类决策
```

### 7.4 Level C：配置文件修复流程

```text
大脑检测到配置问题
  → 通过知识库理解配置结构
  → 评估修复确定性
      ├── 高确定性：直接修复
      │     → 保存基线到 .living/evolution/rollback/
      │     → 写入修复到 .living/evolution/patches/{executionId}/
      │     → 应用修复
      │     → EvolutionCircuitBreaker 监控效果
      │     └── 失败时自动回滚
      └── 低确定性：生成提案，通知人类
            → 写入提案到 .living/evolution/patches/{executionId}/
            → EscalationNotificationService 发送通知
            └── 等待人类决策
```

### 7.5 Level D：代码修复流程

```text
大脑检测到代码级问题
  → ErrorCodeMapper 定位到具体代码文件
  → CodebaseAccessService 读取相关代码上下文
  → 通过知识库理解代码架构和依赖关系
  → 评估修复确定性
      ├── 高确定性：直接修复
      │     → 保存基线到 .living/evolution/rollback/
      │     → 生成补丁写入 .living/evolution/patches/{executionId}/
      │         ├── proposal.md      # 修复方案描述
      │         ├── patch.diff       # 补丁内容
      │         └── metadata.json    # 元数据
      │     → 应用补丁
      │     → 监控修复效果
      │     ├── 成功：记录经验，通知人类已修复
      │     └── 失败：自动回滚，降低 confidence，可能寻求人类帮助
      │
      └── 低确定性：寻求人类帮助
            → 生成修复提案
            → EscalationNotificationService 发送通知
            → 人类审查并决策
```

### 7.6 自我保护机制

大脑的"自毁即自伤"约束通过以下机制保障：

| 机制 | 说明 |
|------|------|
| **回滚基线** | 每次修复前自动保存当前状态到 `.living/evolution/rollback/`，失败时自动回滚 |
| **修复循环检测** | EvolutionCircuitBreaker 检测到连续修复 >= 3 次，强制 ESCALATE（防止大脑"执迷不悟"） |
| **连续失败检测** | 连续失败 >= 5 次，强制 ESCALATE（大脑"受伤太重"需要人类救治） |
| **经验沉淀** | 每次修复结果记录到知识库，成功经验提升 confidence，失败经验降低 confidence |
| **速率限制** | 每分钟最多 20 次代码访问，防止失控循环 |
| **Trace 全记录** | 所有修复操作记录到 Trace，人类可随时审查 |

### 7.7 补丁提案格式

**proposal.md**：

```markdown
# 代码修复提案

## 信号来源
- 信号ID：{signalId}
- 信号类型：{signalType}
- 触发时间：{timestamp}

## 问题描述
{problemDescription}

## 根因分析
{rootCauseAnalysis}

## 修复方案
{fixDescription}

## 影响范围
- 涉及文件：{files}
- 涉及模块：{modules}
- 可能副作用：{sideEffects}

## 测试建议
{testSuggestions}

## 风险等级
{riskLevel}
```

**metadata.json**：

```json
{
  "proposalId": "patch_20260615_001",
  "signalId": "sig_001",
  "brainDomain": "tech",
  "riskLevel": "HIGH",
  "confidence": 0.85,
  "affectedModules": ["core/brain"],
  "affectedFiles": ["BrainBoundaryEnforcer.java"],
  "autoApplied": true,
  "rollbackAvailable": true,
  "createdAt": "2026-06-15T10:30:00Z",
  "status": "APPLIED"
}
```

---

## 8. 实施优先级与依赖关系

### 8.1 实施路线图

```text
Phase 1 (P0) ─ 基础感知能力
  ├── 1.1 目录分离：创建 .living/ 进化空间结构
  ├── 1.2 DataNamespaceService 扩展：新增进化空间命名空间
  ├── 1.3 架构知识播种：ArchitectureKnowledgeSeeder
  ├── 1.4 ProfessionalKnowledgeSeeder 接入：启动时调用 seedFromDirectory()
  └── 1.5 配置注入：living-agent.living-dir 配置项

Phase 2 (P0) ─ 统一升级通道
  ├── 2.1 EscalationNotificationService 实现
  ├── 2.2 升级通知模板
  ├── 2.3 整合现有 3 处升级逻辑
  └── 2.4 DepartmentNotificationService 联动

Phase 3 (P1) ─ 错误定位能力
  ├── 3.1 ErrorCodeMapper 实现
  ├── 3.2 @CodeLocation 注解定义
  ├── 3.3 error-mappings.yaml 配置
  └── 3.4 Trace 注入 CodeContext

Phase 4 (P1) ─ 代码访问能力
  ├── 4.1 CodebaseAccessService 实现
  ├── 4.2 敏感文件过滤
  ├── 4.3 访问日志和速率限制
  └── 4.4 符号链接挂载

Phase 5 (P2) ─ 代码自修复能力
  ├── 5.1 Level C 配置修复流程
  ├── 5.2 Level D 代码修复流程
  ├── 5.3 补丁提案格式和存储
  ├── 5.4 回滚基线管理
  └── 5.5 EvolutionCircuitBreaker 扩展覆盖自修复
```

### 8.2 依赖关系

```text
1.1 目录分离 ─────┐
1.2 命名空间扩展 ──┤
                  ├──→ 1.3 架构知识播种 ──→ 3.1 ErrorCodeMapper
1.4 Seeder 接入 ──┘                         │
1.5 配置注入 ───────────────────────────────→ 4.1 CodebaseAccessService
                                            │
2.1 统一升级服务 ──→ 2.3 整合升级逻辑 ──→ 5.2 代码修复流程
                                            │
3.1 ErrorCodeMapper ─────────────────────→ 5.1 补丁提案
```

---

## 9. 安全约束

### 9.1 安全边界（基于自然约束而非硬规则）

| 约束 | 说明 |
|------|------|
| 进化空间与业务空间隔离 | `.living/` 下的进化操作不能影响 `data/` 下的业务数据 |
| 大脑对 `.living/` 有自由权限 | 大脑可自主读写 `.living/` 下所有内容，包括代码修复 |
| 自毁即自伤 | 代码修复出错等于自毁，大脑天然不想搞坏自己，这是最强的约束 |
| 修复循环强制升级 | EvolutionCircuitBreaker 检测到修复循环时，强制 ESCALATE（大脑"执迷不悟"时人类介入） |
| 连续失败强制升级 | 连续失败 >= 5 次时，强制 ESCALATE（大脑"受伤太重"需要人类救治） |
| 敏感文件不可访问 | CodebaseAccessService 必须过滤 `.env`、`credentials` 等敏感文件 |
| 访问可审计 | 所有代码访问和进化操作必须记录 Trace |
| 回滚基线自动保存 | 每次修复前自动保存基线到 `.living/evolution/rollback/`，失败时自动回滚 |
| 回滚基线不可删除 | `.living/evolution/rollback/` 中的基线只能追加，不能删除 |

### 9.2 BrainBoundaryEnforcer 扩展

在现有边界规则中，为每个大脑新增进化相关边界：

```java
// MainBrain 新增
allowedActions.add("codebase_full_access");    // 自由读写代码库
allowedActions.add("evolution_full_access");   // 自由读写进化空间
allowedActions.add("apply_code_fix");          // 可自主应用代码修复
allowedActions.add("propose_code_fix");        // 可提议代码修复
mustEscalateScenarios.add("repair_loop_detected");       // 修复循环时强制升级
mustEscalateScenarios.add("consecutive_failures_5");     // 连续失败5次强制升级

// 部门 Brain 新增
allowedActions.add("codebase_full_access");    // 自由读写代码库
allowedActions.add("evolution_full_access");   // 自由读写进化空间
allowedActions.add("apply_code_fix");          // 可自主应用代码修复（限本部门相关代码）
allowedActions.add("propose_code_fix");        // 可提议代码修复
mustEscalateScenarios.add("repair_loop_detected");       // 修复循环时强制升级
mustEscalateScenarios.add("consecutive_failures_5");     // 连续失败5次强制升级

// 固定员工
forbiddenActions.add("codebase_access");       // 不可直接访问代码库
forbiddenActions.add("apply_code_fix");        // 不可应用代码修复
forbiddenActions.add("evolution_write");       // 不可写入进化空间
```

---

## 10. 与现有文档的关系

| 文档 | 关系 |
|------|------|
| `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md` | 代码知识注入的数据源 |
| `docs/BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md` | 大脑/员工边界扩展的依据 |
| `docs/ARCHITECTURE_INDEX.md` | 架构文档知识注入的数据源 |
| `documents/shared/governance/05-evolution-governance.md` | 进化闭环治理规则，需同步更新 |
| `documents/shared/governance/06-security-governance.md` | 安全权限治理，需同步更新 CodebaseAccess 规则 |
| `docs/CODE_STRUCTURE_AND_FILE_GUIDE.md` | 新增文件需同步更新到此索引 |

---

## 11. 新增文件清单

| 优先级 | 文件 | 作用 |
|--------|------|------|
| P0 | `core/runtime/EvolutionNamespaceService.java` | 进化空间命名空间（扩展 DataNamespaceService 或独立） |
| P0 | `core/knowledge/professional/ArchitectureKnowledgeSeeder.java` | 架构文档知识播种器 |
| P0 | `core/knowledge/professional/SourceTreeIndexer.java` | 源码结构索引生成器 |
| P0 | `core/evolution/escalation/EscalationNotificationService.java` | 统一升级通知服务 |
| P0 | `core/evolution/escalation/EscalationRecord.java` | 升级记录实体 |
| P0 | `core/evolution/escalation/EscalationLevel.java` | 升级级别枚举 |
| P1 | `core/evolution/codemapper/ErrorCodeMapper.java` | 错误到代码映射器 |
| P1 | `core/evolution/codemapper/CodeContext.java` | 代码上下文实体 |
| P1 | `core/evolution/codemapper/CodeLocation.java` | 代码位置注解 |
| P1 | `core/evolution/codebase/CodebaseAccessService.java` | 代码目录受控访问服务 |
| P1 | `core/evolution/codebase/CodebaseAccessConfig.java` | 代码访问配置 |
| P2 | `core/evolution/patch/PatchProposalService.java` | 补丁提案服务 |
| P2 | `core/evolution/patch/PatchProposal.java` | 补丁提案实体 |
| P2 | `core/evolution/patch/PatchApplicationService.java` | 补丁应用服务（大脑自主决定，含回滚基线保存） |

---

## 12. 配置变更清单

### application.yml 新增配置

```yaml
living-agent:
  # 进化空间根目录（与 data/ 分离）
  living-dir: ${LIVING_AGENT_LIVING_DIR:./.living}

  # 代码库访问配置
  codebase:
    project-root: ${LIVING_AGENT_PROJECT_ROOT:..}
    mount-points:
      docs: ${living-agent.codebase.project-root}/docs
      documents: ${living-agent.codebase.project-root}/documents
    sensitive-patterns:
      - ".env"
      - "credentials"
      - "secret"
      - "password"
      - "token"
      - ".key"
      - ".pem"
      - ".p12"
    access-log: true
    rate-limit-per-minute: 20

  # 架构知识播种配置
  architecture-knowledge:
    enabled: true
    docs-path: ${living-agent.codebase.project-root}/docs
    documents-path: ${living-agent.codebase.project-root}/documents
    rescan-on-startup: true
    chunk-size: 2000

  # 升级通知配置
  escalation:
    notification-enabled: true
    admin-roles:
      - "FOUNDER"
      - "ADMIN"
    urgent-channel: "department-notification"
    max-pending: 100
    auto-resolve-timeout-hours: 72
```

---

## 13. 审查清单

实施每个 Phase 时，必须检查：

- [x] 进化空间与业务空间是否严格隔离？
- [x] 大脑对 `.living/` 是否有自由权限？
- [x] 新增文件是否更新到 `CODE_STRUCTURE_AND_FILE_GUIDE.md`？
- [x] 新增边界规则是否更新到 `BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md`？
- [x] 进化治理规则是否同步更新 `05-evolution-governance.md`？
- [x] 安全规则是否同步更新 `06-security-governance.md`？
- [x] 代码修复是否由大脑自主决定（而非强制升级）？
- [x] 修复循环和连续失败是否触发强制升级？
- [x] 所有进化操作是否有 Trace 记录？
- [x] 回滚基线是否在修复前自动保存？
- [x] 敏感文件是否在 CodebaseAccessService 中过滤？

---

## 14. 实施状态（2026-06-15 更新）

### 14.1 已完成项

| Phase | 项目 | 状态 | 实现文件 |
|-------|------|------|----------|
| P1 | 进化空间命名空间 | ✅ 已完成 | `core/runtime/EvolutionNamespaceService.java` |
| P1 | 架构文档知识播种 | ✅ 已完成 | `core/knowledge/professional/ArchitectureKnowledgeSeeder.java` |
| P1 | 源码结构索引生成 | ✅ 已完成 | `core/knowledge/professional/SourceTreeIndexer.java` |
| P1 | 知识播种启动接入 | ✅ 已完成 | `MemoryConfig.knowledgeSeedingRunner()` (ApplicationRunner) |
| P1 | 配置注入 | ✅ 已完成 | `application.yml` 新增 living-dir + architecture-knowledge |
| P2 | 统一升级通知 | ✅ 已完成 | `core/evolution/escalation/EscalationNotificationService.java` |
| P2 | 升级记录实体 | ✅ 已完成 | `EscalationLevel.java` + `EscalationRecord.java` |
| P3 | 错误到代码映射 | ✅ 已完成 | `core/evolution/codemapper/ErrorCodeMapper.java` |
| P3 | 代码位置注解 | ✅ 已完成 | `CodeLocation.java` + `CodeContext.java` |
| P4 | 代码库受控访问 | ✅ 已完成 | `core/evolution/codebase/CodebaseAccessService.java` + `CodebaseAccessConfig.java` |
| P5 | 补丁提案服务 | ✅ 已完成 | `core/evolution/patch/PatchProposalService.java` + `PatchProposal.java` |
| P5 | 补丁应用服务 | ✅ 已完成 | `core/evolution/patch/PatchApplicationService.java` |

### 14.2 集成修复项

| 优先级 | 修复项 | 状态 | 说明 |
|--------|--------|------|------|
| P0 | MemoryConfig PostConstruct → ApplicationRunner | ✅ 已修复 | 避免初始化时序风险和实例不一致 |
| P0 | EvolutionExecutor 集成 EscalationNotificationService | ✅ 已修复 | executeEscalate() 现在发送升级通知 |
| P0 | EvolutionExecutor 集成 ErrorCodeMapper | ✅ 已修复 | executeRepair() 获取代码上下文 |
| P1 | EvolutionNamespaceService 路径与 InstructionFileLoader 兼容 | ✅ 已修复 | getEmployeeInstructionsPath() 对齐 |
| P1 | BrainBoundaryEnforcer 添加进化动作边界 | ✅ 已修复 | 大脑添加 codebase_full_access 等，固定员工禁止 |
| P1 | ArchitectureKnowledgeSeeder 使用 KnowledgeEntry 强类型 | ✅ 已修复 | storeKnowledge() 使用 KnowledgeEntry 设置强类型属性 |
| P2 | StandardComplianceTraceService 对接 EscalationNotificationService | ✅ 已修复 | traceEscalation() 现在发送升级通知 |
| P2 | EvolutionCircuitBreaker 补充 EMPTY_CYCLE 检查 | ✅ 已修复 | checkCircuit() 新增空循环检测 |

### 14.3 待完成项

| 优先级 | 待完成项 | 状态 | 说明 |
|--------|----------|------|------|
| P2 | 更新 BRAIN_AND_EMPLOYEE_STANDARDS_INDEX.md | ✅ 已完成 | 新增进化权限列、8个条目、3条越权规则 |
| P2 | 更新 05-evolution-governance.md | ✅ 已完成 | 新增第9节"自我进化与代码感知" |
| P2 | 更新 06-security-governance.md | ✅ 已完成 | 新增第11节"进化空间安全治理" |
| P3 | DynamicPromptBuilder 增加架构知识检索 | ✅ 已完成 | knowledge() 增加 "arch" 关键词额外搜索 |
| P3 | ErrorCodeMapper 支持 YAML 配置加载 | ✅ 已完成 | 新增 loadFromYaml() + loadFromYamlString() |
| P3 | Docker 环境路径配置 | ✅ 已完成 | 独立环境变量 + docker-compose.yml 适配 |

### 14.4 闭环验证

**核心闭环链路**：

```text
代码知识注入 ✅ → DynamicPromptBuilder 检索 ✅ → 大脑理解代码结构 ✅
→ 运行时错误 → ErrorCodeMapper 定位 ✅ → 大脑评估修复确定性 ✅
→ 高确定性：PatchApplicationService 直接修复 ✅ → 回滚基线保存 ✅
→ 低确定性：EscalationNotificationService 通知人类 ✅
→ 修复循环：EvolutionCircuitBreaker 强制升级 ✅
→ 升级通知：EscalationNotificationService 统一出口 ✅
→ 人类解决：resolveEscalation() ✅
→ 合规追踪：StandardComplianceTraceService 记录 ✅
```

**闭环状态：可落地运行**。核心链路已全部打通，编译通过。
