package com.livingagent.core.skill.feedback;

import com.livingagent.core.autonomy.EmployeeExecutionReceipt;
import com.livingagent.core.autonomy.EmployeeExecutionReceiptService;
import com.livingagent.core.autonomy.ReceiptStatus;
import com.livingagent.core.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 技能增量优化服务（64-G-2）
 * 借鉴 CLI-Anything 的 /refine 机制：
 * 分析员工近 N 次执行回执 → 识别高频失败/低效模式 →
 * 对照技能定义检查覆盖但执行差的技能 → 生成改进建议。
 */
public class SkillRefineService {

    private static final Logger log = LoggerFactory.getLogger(SkillRefineService.class);

    private final SkillRegistry skillRegistry;
    private final EmployeeExecutionReceiptService receiptService;
    private final SkillCoverageEvaluator coverageEvaluator;

    public SkillRefineService(SkillRegistry skillRegistry,
                               EmployeeExecutionReceiptService receiptService,
                               SkillCoverageEvaluator coverageEvaluator) {
        this.skillRegistry = skillRegistry;
        this.receiptService = receiptService;
        this.coverageEvaluator = coverageEvaluator;
    }

    public record RefineResult(
        String employeeCode,
        int analyzedExecutions,
        List<PerformanceIssue> performanceIssues,
        List<SkillImprovement> improvements,
        List<String> recommendations
    ) {}

    public record PerformanceIssue(
        String taskType,
        IssueType type,
        String description,
        int occurrenceCount
    ) {}

    public enum IssueType {
        HIGH_FAILURE_RATE,
        LOW_VALIDATION_RATE,
        RECURRING_BLOCKED,
        SLOW_EXECUTION,
        EMPTY_OUTPUT
    }

    public record SkillImprovement(
        String skillName,
        String currentDeficiency,
        String suggestedAction,
        ImprovementType type
    ) {}

    public enum ImprovementType {
        ADD_TRIGGER_WORD,
        ADD_CHECK_DIMENSION,
        ADJUST_PARAMETERS,
        CREATE_NEW_SKILL,
        BIND_EXISTING_SKILL
    }

    /**
     * 增量优化流程
     *
     * @param employeeCode   员工代码
     * @param recentExecutions 分析最近 N 次执行回执
     */
    public RefineResult refine(String employeeCode, int recentExecutions) {
        if (receiptService == null) {
            return new RefineResult(employeeCode, 0, List.of(), List.of(),
                List.of("回执服务不可用，无法分析"));
        }

        // 1. 获取近 N 次执行回执
        List<EmployeeExecutionReceipt> receipts =
            receiptService.getReceiptsByEmployee(employeeCode, recentExecutions);

        if (receipts.isEmpty()) {
            return new RefineResult(employeeCode, 0, List.of(), List.of(),
                List.of("无执行回执可供分析"));
        }

        // 2. 识别高频失败/低效的行动模式
        List<PerformanceIssue> issues = analyzePerformancePatterns(receipts);

        // 3. 对照技能定义检查覆盖但执行差的技能
        List<SkillImprovement> improvements = identifySkillGaps(employeeCode, issues, receipts);

        // 4. 生成改进建议
        List<String> recommendations = generateRecommendations(issues, improvements);

        return new RefineResult(employeeCode, receipts.size(), issues, improvements, recommendations);
    }

    private List<PerformanceIssue> analyzePerformancePatterns(List<EmployeeExecutionReceipt> receipts) {
        List<PerformanceIssue> issues = new ArrayList<>();

        // 按任务类型分组统计
        Map<String, List<EmployeeExecutionReceipt>> byTaskType = new java.util.LinkedHashMap<>();
        for (EmployeeExecutionReceipt r : receipts) {
            String taskType = extractTaskType(r);
            byTaskType.computeIfAbsent(taskType, k -> new ArrayList<>()).add(r);
        }

        for (var entry : byTaskType.entrySet()) {
            String taskType = entry.getKey();
            List<EmployeeExecutionReceipt> group = entry.getValue();

            long failedCount = group.stream()
                .filter(r -> r.status() == ReceiptStatus.FAILED || r.status() == ReceiptStatus.NEEDS_REWORK)
                .count();
            long blockedCount = group.stream()
                .filter(r -> r.status() == ReceiptStatus.NEEDS_APPROVAL || r.status() == ReceiptStatus.NEEDS_HUMAN_REVIEW)
                .count();

            // 高失败率
            if (failedCount > 2 && (double) failedCount / group.size() > 0.3) {
                issues.add(new PerformanceIssue(taskType, IssueType.HIGH_FAILURE_RATE,
                    String.format("任务 '%s' 失败率 %.0f%% (%d/%d)",
                        taskType, failedCount * 100.0 / group.size(), failedCount, group.size()),
                    (int) failedCount));
            }

            // 低验证通过率
            long validationFailed = group.stream()
                .filter(r -> r.validation() != null && !r.validation().valid())
                .count();
            if (validationFailed > 1 && (double) validationFailed / group.size() > 0.2) {
                issues.add(new PerformanceIssue(taskType, IssueType.LOW_VALIDATION_RATE,
                    String.format("任务 '%s' 验证失败率 %.0f%%",
                        taskType, validationFailed * 100.0 / group.size()),
                    (int) validationFailed));
            }

            // 重复阻塞
            if (blockedCount > 3) {
                issues.add(new PerformanceIssue(taskType, IssueType.RECURRING_BLOCKED,
                    String.format("任务 '%s' 多次被阻塞 (%d 次)", taskType, blockedCount),
                    (int) blockedCount));
            }

            // 空输出
            long emptyOutput = group.stream()
                .filter(r -> r.summary() == null || r.summary().isBlank())
                .count();
            if (emptyOutput > 1) {
                issues.add(new PerformanceIssue(taskType, IssueType.EMPTY_OUTPUT,
                    String.format("任务 '%s' 存在空输出 (%d 次)", taskType, emptyOutput),
                    (int) emptyOutput));
            }
        }

        return issues;
    }

    private List<SkillImprovement> identifySkillGaps(String employeeCode,
                                                      List<PerformanceIssue> issues,
                                                      List<EmployeeExecutionReceipt> receipts) {
        List<SkillImprovement> improvements = new ArrayList<>();

        if (coverageEvaluator != null) {
            var coverage = coverageEvaluator.evaluate(employeeCode);
            // 缺失领域 → 建议创建/绑定技能
            for (SkillCoverageEvaluator.SkillSuggestion suggestion : coverage.suggestions()) {
                improvements.add(new SkillImprovement(
                    suggestion.skillName(),
                    suggestion.reason(),
                    switch (suggestion.priority()) {
                        case HIGH -> "立即绑定已有技能或创建新技能";
                        case MEDIUM -> "近期规划技能创建";
                        case LOW -> "观察是否需要创建技能";
                    },
                    suggestion.priority() == SkillCoverageEvaluator.Priority.HIGH
                        ? ImprovementType.BIND_EXISTING_SKILL
                        : ImprovementType.CREATE_NEW_SKILL
                ));
            }
        }

        // 基于性能问题生成技能改进
        for (PerformanceIssue issue : issues) {
            switch (issue.type()) {
                case HIGH_FAILURE_RATE -> improvements.add(new SkillImprovement(
                    "skill_" + issue.taskType().toLowerCase(),
                    issue.description(),
                    "增加错误恢复策略和重试逻辑到技能定义",
                    ImprovementType.ADD_CHECK_DIMENSION
                ));
                case LOW_VALIDATION_RATE -> improvements.add(new SkillImprovement(
                    "skill_" + issue.taskType().toLowerCase(),
                    issue.description(),
                    "增加输出质量检查维度到技能定义",
                    ImprovementType.ADD_CHECK_DIMENSION
                ));
                case EMPTY_OUTPUT -> improvements.add(new SkillImprovement(
                    "skill_" + issue.taskType().toLowerCase(),
                    issue.description(),
                    "增加非空输出触发词和最小输出要求",
                    ImprovementType.ADD_TRIGGER_WORD
                ));
                default -> {}
            }
        }

        return improvements;
    }

    private List<String> generateRecommendations(List<PerformanceIssue> issues,
                                                  List<SkillImprovement> improvements) {
        List<String> recommendations = new ArrayList<>();

        if (!issues.isEmpty()) {
            recommendations.add("发现 " + issues.size() + " 个性能问题需关注：");
            for (PerformanceIssue issue : issues) {
                recommendations.add("  - " + issue.description());
            }
        }

        if (!improvements.isEmpty()) {
            recommendations.add("建议 " + improvements.size() + " 项技能改进：");
            long bindCount = improvements.stream()
                .filter(i -> i.type() == ImprovementType.BIND_EXISTING_SKILL).count();
            long createCount = improvements.stream()
                .filter(i -> i.type() == ImprovementType.CREATE_NEW_SKILL).count();
            long enhanceCount = improvements.size() - bindCount - createCount;

            if (bindCount > 0) recommendations.add("  - 绑定已有技能: " + bindCount + " 项");
            if (createCount > 0) recommendations.add("  - 创建新技能: " + createCount + " 项");
            if (enhanceCount > 0) recommendations.add("  - 增强现有技能: " + enhanceCount + " 项");
        }

        if (issues.isEmpty() && improvements.isEmpty()) {
            recommendations.add("表现良好，暂无改进建议");
        }

        return recommendations;
    }

    private String extractTaskType(EmployeeExecutionReceipt receipt) {
        if (receipt.metadata() != null) {
            Object taskType = receipt.metadata().get("taskType");
            if (taskType instanceof String s) return s;
        }
        return "unknown";
    }
}
