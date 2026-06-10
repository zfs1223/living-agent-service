# 需求明确性前置判断 - RequirementReadinessEvaluator

> 本文档记录了需求明确性前置判断方案。
> 对应原文章节：2.3.3.2 P0-6
> 状态：✅ 已完成

---

## 设计原则

```text
Requirement-confirmed-before-assignment.
No assignment before requirement confirmation.
```

---

## 新增组件

### RequirementReadinessEvaluator

职责：在主脑规划和员工分派之前，判断用户需求是否足够明确。

```java
public interface RequirementReadinessEvaluator {
    RequirementReadinessResult evaluate(String userMessage, String department, String sessionId);
}
```

### MainBrainRequirementClarifier

职责：负责主脑统一澄清问题。

```java
public interface MainBrainRequirementClarifier {
    ClarificationResult clarify(String userMessage, RequirementReadinessResult readiness, String conversationId);
}
```

---

## 推荐流程顺序

```text
DialogueAnalyzer
-> MainBrainRequirementClarifier / RequirementReadinessEvaluator
-> 若需求不清楚：主脑发起澄清，状态 = NEEDS_CLARIFICATION
-> 用户补充信息，继续同一个 conversationId / draft task
-> MainBrain 合并历史上下文，确认 requirementStatus = REQUIREMENT_CONFIRMED
-> MainBrainTaskDirector 生成正式任务计划
-> ExecutionCapabilityResolver 归一化 executionCapability / artifactType / executionMode
-> FixedEmployeeDispatcher 分派员工
-> AssignmentPreparationService 准备任务单
-> ToolBackedEmployeeTaskExecutor 执行
-> ExecutionReceiptReviewer 验收
-> MainBrainFinalSummaryService 收口
```

---

## 评估阈值

```text
confidence >= 0.85 && missingElements 为空 -> SUFFICIENT (可进入规划)
0.65 <= confidence < 0.85 -> PARTIALLY_SUFFICIENT (可规划但建议澄清)
confidence < 0.65 -> INSUFFICIENT (必须先澄清)
```

---

## 相关文件

| 文件 | 说明 |
| --- | --- |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/RequirementReadinessEvaluator.java` | 需求就绪评估器接口 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/DefaultRequirementReadinessEvaluator.java` | 需求就绪评估器实现 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/MainBrainRequirementClarifier.java` | 主脑澄清器接口 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/DefaultMainBrainRequirementClarifier.java` | 主脑澄清器实现 |
