# RequirementReadinessEvaluator 判断规则

> 本文档定义了需求就绪评估器的判断规则和实现指南。
> 版本：2026-05-20

---

## 评估器接口

```java
public interface RequirementReadinessEvaluator {
    RequirementReadinessResult evaluate(String userMessage, String department, String sessionId);
}
```

---

## 评估结果结构

```java
public record RequirementReadinessResult(
    ReadinessLevel level,
    double confidence,
    List<String> missingElements,
    List<String> clarificationQuestions,
    String reason
) {
    public static RequirementReadinessResult sufficient(double confidence, String reason) {
        return new RequirementReadinessResult(ReadinessLevel.SUFFICIENT, confidence, List.of(), List.of(), reason);
    }

    public static RequirementReadinessResult partiallySufficient(double confidence,
            List<String> missingElements, List<String> clarificationQuestions, String reason) {
        return new RequirementReadinessResult(ReadinessLevel.PARTIALLY_SUFFICIENT, confidence,
            missingElements, clarificationQuestions, reason);
    }

    public static RequirementReadinessResult insufficient(List<String> clarificationQuestions, String reason) {
        return new RequirementReadinessResult(ReadinessLevel.INSUFFICIENT, 0.0, List.of(), clarificationQuestions, reason);
    }

    public boolean isReady() {
        return level == ReadinessLevel.SUFFICIENT;
    }

    public boolean needsClarification() {
        return level != ReadinessLevel.SUFFICIENT;
    }
}

enum ReadinessLevel {
    SUFFICIENT,       // 需求明确，可以进入规划
    PARTIALLY_SUFFICIENT,  // 需求部分明确，可规划但建议先澄清
    INSUFFICIENT     // 需求不明确，必须先澄清
}
```

---

## 推荐判断维度

- 目标是否明确
- 范围是否明确
- 产物是否明确
- 验收标准是否明确
- 时间/里程碑是否明确
- 风险和约束是否明确
- 是否需要跨部门协作
- 是否存在明显冲突或歧义

---

## 判定结果

```text
READY
PARTIALLY_READY
NEEDS_CLARIFICATION
```

---

## 判定阈值

```text
confidence >= 0.85 && missingElements 为空 -> READY
0.65 <= confidence < 0.85 -> PARTIALLY_READY
confidence < 0.65 或关键字段缺失 -> NEEDS_CLARIFICATION
```

---

## 返回规则

- `READY`：允许主脑进入正式规划。
- `PARTIALLY_READY`：允许主脑补充少量澄清，但不能分派员工。
- `NEEDS_CLARIFICATION`：必须先澄清，不允许正式规划和分派。

---

## 建议输出字段

```text
readinessLevel
confidence
missingElements
clarificationQuestions
blockingReasons
recommendation
```

---

## 默认实现评估维度权重

| 维度 | 权重 | 关键词示例 |
| --- | --- | --- |
| 动作明确性 | 0.25 | 开发、生成、创建、制作、编写、设计、分析、修改、修复 |
| 目标产物 | 0.25 | 网页、页面、文档、报告、方案、代码、接口、系统 |
| 上下文充分性 | 0.15 | 长度 >= 20 字符 |
| 范围明确性 | 0.15 | 范围、包括、包含、只要、限定 |
| 验收标准隐含 | 0.10 | 标准、要求、必须、满足、验证、通过 |
| 完整性 | 0.10 | 消息长度 |

---

## 相关文件

| 文件 | 说明 |
| --- | --- |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/RequirementReadinessEvaluator.java` | 评估器接口定义 |
| `living-agent-core/src/main/java/com/livingagent/core/autonomy/impl/DefaultRequirementReadinessEvaluator.java` | 默认规则实现 |
| `docs/core/REQUIREMENT_STATUS_STATE_MACHINE.md` | 需求状态机定义 |
