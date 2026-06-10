# RequirementStatus 状态机定义

> 本文档定义了需求状态机的完整枚举和状态转移规则。
> 版本：2026-05-20

---

## 状态枚举

```java
public enum RequirementStatus {
    /** 初始状态：用户首次表达需求，尚未确认 */
    DRAFT,

    /** 主脑判断需求缺关键要素，必须追问 */
    NEEDS_CLARIFICATION,

    /** 已发出澄清问题，等待用户回答 */
    CLARIFICATION_PENDING,

    /** 需求已明确，可进入正式规划与分派 */
    REQUIREMENT_CONFIRMED,

    /** 主脑生成正式任务计划中 */
    PLANNING,

    /** 计划已生成，等待分派 */
    PLANNED,

    /** 员工任务单已生成并分派 */
    ASSIGNED,

    /** 员工正在执行 */
    EXECUTING,

    /** 验收通过并完成 */
    COMPLETED,

    /** 执行失败或验收失败 */
    FAILED;
}
```

---

## 状态说明

| 状态 | 说明 | 允许的下一步 |
| --- | --- | --- |
| `DRAFT` | 用户首次表达需求，尚未确认 | `NEEDS_CLARIFICATION`, `REQUIREMENT_CONFIRMED` |
| `NEEDS_CLARIFICATION` | 主脑判断需求缺关键要素，必须追问 | `CLARIFICATION_PENDING` |
| `CLARIFICATION_PENDING` | 已发出澄清问题，等待用户回答 | `REQUIREMENT_CONFIRMED`, `NEEDS_CLARIFICATION` |
| `REQUIREMENT_CONFIRMED` | 需求已明确，可进入正式规划与分派 | `PLANNING` |
| `PLANNING` | 主脑生成正式任务计划中 | `PLANNED` |
| `PLANNED` | 计划已生成，等待分派 | `ASSIGNED` |
| `ASSIGNED` | 员工任务单已生成并分派 | `EXECUTING` |
| `EXECUTING` | 员工正在执行 | `COMPLETED`, `FAILED` |
| `COMPLETED` | 验收通过并完成 | 终态，不可转移 |
| `FAILED` | 执行失败或验收失败 | 终态，不可转移 |

---

## 状态转移规则

```java
public static boolean canTransition(RequirementStatus from, RequirementStatus to) {
    return switch (from) {
        case DRAFT -> to == NEEDS_CLARIFICATION || to == REQUIREMENT_CONFIRMED;
        case NEEDS_CLARIFICATION -> to == CLARIFICATION_PENDING;
        case CLARIFICATION_PENDING -> to == REQUIREMENT_CONFIRMED || to == NEEDS_CLARIFICATION;
        case REQUIREMENT_CONFIRMED -> to == PLANNING;
        case PLANNING -> to == PLANNED;
        case PLANNED -> to == ASSIGNED;
        case ASSIGNED -> to == EXECUTING;
        case EXECUTING -> to == COMPLETED || to == FAILED;
        case COMPLETED, FAILED -> false; // 终态，不可转移
    };
}

public boolean allowsAssignment() {
    return this == PLANNED;
}

public boolean allowsExecution() {
    return this == ASSIGNED || this == EXECUTING;
}

public boolean needsClarification() {
    return this == DRAFT || this == NEEDS_CLARIFICATION || this == CLARIFICATION_PENDING;
}
```

---

## 禁止转移规则

```
DRAFT / NEEDS_CLARIFICATION / CLARIFICATION_PENDING 不能直接进入 ASSIGNED
未 REQUIREMENT_CONFIRMED 不能进入 PLANNING
ASSIGNED 之前不能生成最终员工执行产物
```

---

## 相关文件

| 文件 | 说明 |
| --- | --- |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/RequirementStatus.java` | RequirementStatus 枚举实现 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/RequirementReadinessEvaluator.java` | 需求就绪评估器 |
| `docs/core/REQUIREMENT_READINESS_EVALUATOR_RULES.md` | 评估器判断规则 |
