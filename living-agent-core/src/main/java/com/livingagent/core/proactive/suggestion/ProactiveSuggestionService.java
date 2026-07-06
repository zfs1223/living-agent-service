package com.livingagent.core.proactive.suggestion;

import com.livingagent.core.proactive.alert.AlertNotifier;
import com.livingagent.core.proactive.alert.AlertNotifier.Alert;
import com.livingagent.core.proactive.llm.LlmProactiveAdvisor;
import com.livingagent.core.proactive.llm.LlmRiskAssessor;
import com.livingagent.core.proactive.predictor.PatternPredictor;
import com.livingagent.core.proactive.predictor.RiskPredictor;
import com.livingagent.core.ops.scheduler.TaskCheckout;
import com.livingagent.core.ops.scheduler.TaskCheckout.TaskStatistics;
import com.livingagent.core.skill.bounty.BountyHunterService;
import com.livingagent.core.employee.claim.TaskClaimService;
import com.livingagent.core.memory.Memory;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.proactive.duty.DutyCardParser;
import com.livingagent.core.proactive.duty.DutyCardParser.DutyCard;
import com.livingagent.core.proactive.duty.DutyCardParser.ChairmanReportSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProactiveSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(ProactiveSuggestionService.class);

    private final PatternPredictor patternPredictor;
    private final RiskPredictor riskPredictor;
    private final LlmProactiveAdvisor llmProactiveAdvisor;
    private final LlmRiskAssessor llmRiskAssessor;
    private final List<AlertNotifier> notifiers;
    // PR-2: 新增任务相关组件
    private final TaskCheckout taskCheckout;
    private final BountyHunterService bountyHunterService;
    private final TaskClaimService taskClaimService;
    private final Memory memory;
    private final AccessLevel accessLevel;
    // PR-3: 新增职责卡解析组件
    private final DutyCardParser dutyCardParser;
    private final Map<String, List<Suggestion>> userSuggestions = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSuggestionTime = new ConcurrentHashMap<>();

    private static final long SUGGESTION_COOLDOWN_MS = 300000;
    private static final double CONFIDENCE_THRESHOLD = 0.7;

    public ProactiveSuggestionService(
            PatternPredictor patternPredictor,
            RiskPredictor riskPredictor,
            List<AlertNotifier> notifiers
    ) {
        this(patternPredictor, riskPredictor, null, null, notifiers, null, null, null, null, null, null);
    }

    public ProactiveSuggestionService(
            PatternPredictor patternPredictor,
            RiskPredictor riskPredictor,
            LlmProactiveAdvisor llmProactiveAdvisor,
            LlmRiskAssessor llmRiskAssessor,
            List<AlertNotifier> notifiers
    ) {
        this(patternPredictor, riskPredictor, llmProactiveAdvisor, llmRiskAssessor, notifiers, null, null, null, null, null, null);
    }

    // PR-2/PR-3: 新增完整构造函数（包含任务相关组件和职责卡解析）
    public ProactiveSuggestionService(
            PatternPredictor patternPredictor,
            RiskPredictor riskPredictor,
            LlmProactiveAdvisor llmProactiveAdvisor,
            LlmRiskAssessor llmRiskAssessor,
            List<AlertNotifier> notifiers,
            TaskCheckout taskCheckout,
            BountyHunterService bountyHunterService,
            TaskClaimService taskClaimService,
            Memory memory,
            AccessLevel defaultAccessLevel,
            DutyCardParser dutyCardParser
    ) {
        this.patternPredictor = patternPredictor;
        this.riskPredictor = riskPredictor;
        this.llmProactiveAdvisor = llmProactiveAdvisor;
        this.llmRiskAssessor = llmRiskAssessor;
        this.notifiers = notifiers != null ? new ArrayList<>(notifiers) : new ArrayList<>();
        this.taskCheckout = taskCheckout;
        this.bountyHunterService = bountyHunterService;
        this.taskClaimService = taskClaimService;
        this.memory = memory;
        this.accessLevel = defaultAccessLevel;
        this.dutyCardParser = dutyCardParser;
    }

    public List<Suggestion> generateSuggestions(String userId) {
        Map<String, Object> context = buildSuggestionContext(userId);
        List<Suggestion> llmSuggestions = generateLlmSuggestions(userId, context);
        if (!llmSuggestions.isEmpty()) {
            return llmSuggestions.stream()
                .filter(s -> s.confidence() >= CONFIDENCE_THRESHOLD)
                .sorted(Comparator.comparingDouble(s -> -s.confidence()))
                .limit(5)
                .toList();
        }

        List<Suggestion> suggestions = new ArrayList<>();

        suggestions.addAll(generateTimeBasedSuggestions(userId));
        suggestions.addAll(generatePatternBasedSuggestions(userId));
        suggestions.addAll(generateRiskBasedSuggestions(userId));
        // PR-2: 添加任务相关建议
        suggestions.addAll(generateTaskBasedSuggestions(userId));

        suggestions.sort(Comparator.comparingDouble(s -> -s.confidence()));

        return suggestions.stream()
                .filter(s -> s.confidence() >= CONFIDENCE_THRESHOLD)
                .limit(5)
                .toList();
    }

    private Map<String, Object> buildSuggestionContext(String userId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("userId", userId);
        context.put("generatedAt", Instant.now().toString());
        context.put("timezone", "Asia/Shanghai");

        if (patternPredictor != null) {
            patternPredictor.predictNextAction(userId).ifPresent(action -> context.put("predictedAction", Map.of(
                "action", action.predictedAction(),
                "confidence", action.confidence(),
                "patternId", action.basedOnPattern()
            )));
            context.put("behaviorInsights", patternPredictor.getUserInsights(userId).stream()
                .map(insight -> Map.of(
                    "type", insight.insightType(),
                    "name", insight.insightName(),
                    "description", insight.description(),
                    "confidence", insight.confidence()
                ))
                .toList());
        }

        if (riskPredictor != null) {
            context.put("riskAlerts", riskPredictor.getActiveAlerts().stream()
                .map(alert -> Map.of(
                    "alertId", alert.alertId(),
                    "indicatorId", alert.indicatorId(),
                    "indicatorName", alert.indicatorName(),
                    "level", alert.level().name(),
                    "severity", alert.level().getSeverity(),
                    "probability", alert.probability(),
                    "recommendation", alert.recommendation()
                ))
                .toList());
        }

        // PR-2: 添加任务相关上下文数据
        if (taskCheckout != null) {
            TaskStatistics stats = taskCheckout.getStatistics();
            context.put("taskStatistics", Map.of(
                "pendingCount", stats.pendingCount(),
                "checkedOutCount", stats.checkedOutCount(),
                "completedCount", stats.completedCount()
            ));
        }

        if (bountyHunterService != null) {
            try {
                BountyHunterService.WorkerEarnings earnings = bountyHunterService.getWorkerEarnings(userId);
                if (earnings != null) {
                    context.put("workerEarnings", Map.of(
                        "totalEarned", earnings.totalEarned(),
                        "pendingEarnings", earnings.pendingEarnings(),
                        "tasksCompleted", earnings.tasksCompleted(),
                        "averageRating", earnings.averageRating(),
                        "successRate", earnings.successRate()
                    ));
                }
                int availableTasks = bountyHunterService.findAvailableTasks(userId).size();
                context.put("availableBountyTasks", availableTasks);
            } catch (Exception e) {
                log.debug("Failed to get bounty hunter data for user {}: {}", userId, e.getMessage());
            }
        }

        if (taskClaimService != null) {
            try {
                int claimableTasks = taskClaimService.scanAvailable("default").size();
                context.put("claimableTasks", claimableTasks);
            } catch (Exception e) {
                log.debug("Failed to get claimable tasks: {}", e.getMessage());
            }
        }

        if (this.accessLevel != null) {
            context.put("permissionLevel", this.accessLevel.name());
        }

        return context;
    }

    private List<Suggestion> generateLlmSuggestions(String userId, Map<String, Object> context) {
        if (llmProactiveAdvisor == null) {
            return List.of();
        }
        try {
            List<Suggestion> suggestions = llmProactiveAdvisor.generateSuggestions(userId, context, 5).stream()
                .filter(suggestion -> suggestion != null && suggestion.confidence() >= CONFIDENCE_THRESHOLD)
                .map(this::fromLlmSuggestion)
                .toList();
            if (!suggestions.isEmpty()) {
                log.debug("Generated {} LLM proactive suggestions for user {}", suggestions.size(), userId);
            }
            return suggestions;
        } catch (Exception e) {
            log.warn("LLM proactive suggestion failed for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    private Suggestion fromLlmSuggestion(LlmProactiveAdvisor.ProactiveSuggestion suggestion) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", suggestion.source());
        metadata.put("category", suggestion.category());
        metadata.put("triggerReason", suggestion.triggerReason());
        metadata.put("expectedBenefit", suggestion.expectedBenefit());
        metadata.put("riskNote", suggestion.riskNote());
        metadata.put("recommendedActions", suggestion.recommendedActions());
        metadata.put("requiresUserConfirmation", suggestion.requiresUserConfirmation());
        if (suggestion.recommendedActions() != null && !suggestion.recommendedActions().isEmpty()) {
            metadata.put("action", suggestion.recommendedActions().get(0));
        }
        return new Suggestion(
            suggestion.suggestionId(),
            suggestion.userId(),
            suggestion.title(),
            suggestion.description(),
            mapSuggestionType(suggestion.category()),
            suggestion.confidence(),
            metadata,
            Instant.now()
        );
    }

    private SuggestionType mapSuggestionType(String category) {
        if (category == null) {
            return SuggestionType.INSIGHT;
        }
        String normalized = category.toLowerCase(Locale.ROOT);
        if (normalized.contains("risk") || normalized.contains("warning") || normalized.contains("风险")) {
            return SuggestionType.WARNING;
        }
        if (normalized.contains("report") || normalized.contains("周报") || normalized.contains("报告")) {
            return SuggestionType.REPORT;
        }
        if (normalized.contains("workflow") || normalized.contains("流程")) {
            return SuggestionType.WORKFLOW;
        }
        if (normalized.contains("learn") || normalized.contains("学习")) {
            return SuggestionType.LEARNING;
        }
        if (normalized.contains("remind") || normalized.contains("提醒")) {
            return SuggestionType.REMINDER;
        }
        if (normalized.contains("action") || normalized.contains("操作")) {
            return SuggestionType.ACTION;
        }
        return SuggestionType.INSIGHT;
    }

    private List<Suggestion> generateTimeBasedSuggestions(String userId) {
        List<Suggestion> suggestions = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        int hour = now.getHour();
        int dayOfWeek = now.getDayOfWeek().getValue();

        if (hour >= 9 && hour < 10 && dayOfWeek >= 1 && dayOfWeek <= 5) {
            suggestions.add(new Suggestion(
                    "sugg_" + System.currentTimeMillis() + "_morning",
                    userId,
                    "早安工作准备",
                    "早上好！根据您的习惯，我建议您先查看今日待办事项和邮件。",
                    SuggestionType.WORKFLOW,
                    0.85,
                    Map.of("action", "view_todos", "priority", "high"),
                    Instant.now()
            ));
        }

        if (hour >= 17 && hour < 18 && dayOfWeek == 5) {
            suggestions.add(new Suggestion(
                    "sugg_" + System.currentTimeMillis() + "_weekly",
                    userId,
                    "周报准备提醒",
                    "今天是周五，我建议您开始准备本周的工作周报。",
                    SuggestionType.REPORT,
                    0.9,
                    Map.of("action", "prepare_report", "type", "weekly"),
                    Instant.now()
            ));
        }

        if (hour >= 14 && hour < 15 && dayOfWeek >= 1 && dayOfWeek <= 5) {
            suggestions.add(new Suggestion(
                    "sugg_" + System.currentTimeMillis() + "_afternoon",
                    userId,
                    "下午工作效率建议",
                    "下午时段，建议您处理需要集中注意力的任务。",
                    SuggestionType.WORKFLOW,
                    0.7,
                    Map.of("action", "focus_mode"),
                    Instant.now()
            ));
        }

        return suggestions;
    }

    private List<Suggestion> generatePatternBasedSuggestions(String userId) {
        List<Suggestion> suggestions = new ArrayList<>();

        if (patternPredictor != null) {
            var predictedAction = patternPredictor.predictNextAction(userId);
            if (predictedAction.isPresent()) {
                var action = predictedAction.get();
                
                suggestions.add(new Suggestion(
                        "sugg_" + System.currentTimeMillis() + "_pattern",
                        userId,
                        "智能操作建议",
                        String.format("根据您的习惯，我建议您执行: %s", action.predictedAction()),
                        SuggestionType.ACTION,
                        action.confidence(),
                        Map.of(
                                "action", action.predictedAction(),
                                "patternId", action.basedOnPattern()
                        ),
                        Instant.now()
                ));
            }

            var insights = patternPredictor.getUserInsights(userId);
            for (var insight : insights) {
                if (insight.confidence() >= CONFIDENCE_THRESHOLD) {
                    suggestions.add(new Suggestion(
                            "sugg_" + System.currentTimeMillis() + "_insight_" + insight.insightType(),
                            userId,
                            insight.insightName(),
                            insight.description(),
                            SuggestionType.INSIGHT,
                            insight.confidence(),
                            Map.of("insightType", insight.insightType()),
                            Instant.now()
                    ));
                }
            }
        }

        return suggestions;
    }

    private List<Suggestion> generateRiskBasedSuggestions(String userId) {
        List<Suggestion> suggestions = new ArrayList<>();

        if (riskPredictor != null) {
            var alerts = riskPredictor.getActiveAlerts();
            
            for (var alert : alerts) {
                if (alert.level().getSeverity() >= 3) {
                    Optional<LlmRiskAssessor.RiskAssessmentResult> llmAssessment = assessRiskWithLlm(alert);
                    if (llmAssessment.isPresent()) {
                        LlmRiskAssessor.RiskAssessmentResult assessment = llmAssessment.get();
                        suggestions.add(new Suggestion(
                                "sugg_" + System.currentTimeMillis() + "_risk_" + alert.indicatorId(),
                                userId,
                                "风险预警建议",
                                String.format("%s。影响范围：%s。建议：%s", assessment.evidence(), assessment.impactScope(), assessment.recommendedAction()),
                                SuggestionType.WARNING,
                                Math.max(alert.probability(), assessment.confidence()),
                                Map.of(
                                        "indicatorId", alert.indicatorId(),
                                        "level", assessment.level().name(),
                                        "alertId", alert.alertId(),
                                        "assessmentId", assessment.assessmentId(),
                                        "requiresApproval", assessment.requiresApproval(),
                                        "requiresHumanIntervention", assessment.requiresHumanIntervention(),
                                        "source", assessment.source()
                                ),
                                Instant.now()
                        ));
                    } else {
                        suggestions.add(new Suggestion(
                                "sugg_" + System.currentTimeMillis() + "_risk_" + alert.indicatorId(),
                                userId,
                                "风险预警建议",
                                String.format("检测到风险: %s。%s", alert.indicatorName(), alert.recommendation()),
                                SuggestionType.WARNING,
                                alert.probability(),
                                Map.of(
                                        "indicatorId", alert.indicatorId(),
                                        "level", alert.level().name(),
                                        "alertId", alert.alertId(),
                                        "source", "rule_based_fallback"
                                ),
                                Instant.now()
                        ));
                    }
                }
            }
        }

        return suggestions;
    }

    // PR-2/PR-3: 基于任务的建议生成（身份驱动 + 职责卡）
    private List<Suggestion> generateTaskBasedSuggestions(String userId) {
        List<Suggestion> suggestions = new ArrayList<>();

        // 根据用户身份生成不同的建议（使用默认身份或从上下文获取）
        AccessLevel level = this.accessLevel != null ? this.accessLevel : AccessLevel.CHAT_ONLY;

        // 董事长/管理员：关注全局任务状态 + 所有部门职责卡汇总
        if (level == AccessLevel.FULL) {
            // PR-3: 使用职责卡解析生成董事长专属汇报
            if (dutyCardParser != null) {
                ChairmanReportSummary summary = dutyCardParser.getChairmanReportSummary();
                if (summary.getTotalDepartmentCount() > 0) {
                    suggestions.add(new Suggestion(
                        "sugg_" + System.currentTimeMillis() + "_dept_overview",
                        userId,
                        "数字员工体系概览",
                        String.format("当前已部署 %d 个部门数字员工体系，涵盖运营、技术、销售等核心职能。", summary.getTotalDepartmentCount()),
                        SuggestionType.INSIGHT,
                        0.90,
                        Map.of("action", "view_dept_overview", "deptCount", summary.getTotalDepartmentCount()),
                        Instant.now()
                    ));
                }
                // 显示各部门核心使命
                List<String> topMissions = summary.allMissions().stream().limit(3).toList();
                if (!topMissions.isEmpty()) {
                    suggestions.add(new Suggestion(
                        "sugg_" + System.currentTimeMillis() + "_missions",
                        userId,
                        "部门核心使命",
                        "各部门核心使命：" + String.join("；", topMissions),
                        SuggestionType.INSIGHT,
                        0.85,
                        Map.of("action", "view_missions"),
                        Instant.now()
                    ));
                }
            }
            if (taskCheckout != null) {
                TaskStatistics stats = taskCheckout.getStatistics();
                if (stats.pendingCount() > 10) {
                    suggestions.add(new Suggestion(
                        "sugg_" + System.currentTimeMillis() + "_pending_tasks",
                        userId,
                        "待处理任务提醒",
                        String.format("当前有 %d 个待处理任务，建议分配给合适的员工执行。", stats.pendingCount()),
                        SuggestionType.WORKFLOW,
                        0.88,
                        Map.of("action", "assign_tasks", "pendingCount", stats.pendingCount()),
                        Instant.now()
                    ));
                }
                if (stats.completedCount() > 50) {
                    suggestions.add(new Suggestion(
                        "sugg_" + System.currentTimeMillis() + "_completed_tasks",
                        userId,
                        "任务完成里程碑",
                        String.format("本周已完成 %d 个任务，系统运行良好。", stats.completedCount()),
                        SuggestionType.INSIGHT,
                        0.85,
                        Map.of("action", "view_report", "completedCount", stats.completedCount()),
                        Instant.now()
                    ));
                }
            }
        }

        // 部门经理：关注部门任务分配 + 部门职责卡
        if (level == AccessLevel.DEPARTMENT) {
            // PR-3: 使用部门职责卡生成专属汇报
            if (dutyCardParser != null) {
                // 尝试获取部门职责卡（假设用户关联某个部门）
                Optional<DutyCard> cardOpt = dutyCardParser.getDutyCardByDepartment("tech"); // 默认技术部示例
                if (cardOpt.isPresent()) {
                    DutyCard card = cardOpt.get();
                    suggestions.add(new Suggestion(
                        "sugg_" + System.currentTimeMillis() + "_dept_duty",
                        userId,
                        "部门职责提醒",
                        String.format("您的部门核心使命：%s", card.coreMission()),
                        SuggestionType.INSIGHT,
                        0.82,
                        Map.of("action", "view_dept_duty", "mission", card.coreMission()),
                        Instant.now()
                    ));
                    // 显示部门成功标准
                    List<String> criteria = card.successCriteria().stream().limit(2).toList();
                    if (!criteria.isEmpty()) {
                        suggestions.add(new Suggestion(
                            "sugg_" + System.currentTimeMillis() + "_success_criteria",
                            userId,
                            "部门成功标准",
                            "部门成功标准：" + String.join("；", criteria),
                            SuggestionType.INSIGHT,
                            0.80,
                            Map.of("action", "view_success_criteria"),
                            Instant.now()
                        ));
                    }
                }
            }
            if (taskClaimService != null) {
                try {
                    int claimableTasks = taskClaimService.scanAvailable("default").size();
                    if (claimableTasks > 5) {
                        suggestions.add(new Suggestion(
                            "sugg_" + System.currentTimeMillis() + "_claimable",
                            userId,
                            "部门任务待认领",
                            String.format("部门有 %d 个任务待认领，建议安排员工接取。", claimableTasks),
                            SuggestionType.WORKFLOW,
                            0.82,
                            Map.of("action", "assign_to_employee", "claimableCount", claimableTasks),
                            Instant.now()
                        ));
                    }
                } catch (Exception e) {
                    log.debug("Failed to get claimable tasks for department manager: {}", e.getMessage());
                }
            }
        }

        // 普通员工：关注个人任务和收益
        if (level == AccessLevel.CHAT_ONLY || level == AccessLevel.LIMITED) {
            if (bountyHunterService != null) {
                try {
                    BountyHunterService.WorkerEarnings earnings = bountyHunterService.getWorkerEarnings(userId);
                    if (earnings != null) {
                        if (earnings.pendingEarnings() > 0) {
                            suggestions.add(new Suggestion(
                                "sugg_" + System.currentTimeMillis() + "_pending_earnings",
                                userId,
                                "收益待结算",
                                String.format("您有 %.2f 元待结算收益，已完成 %d 个任务。", earnings.pendingEarnings(), earnings.tasksCompleted()),
                                SuggestionType.INSIGHT,
                                0.75,
                                Map.of("action", "view_earnings", "pendingEarnings", earnings.pendingEarnings()),
                                Instant.now()
                            ));
                        }
                        if (earnings.successRate() < 0.8) {
                            suggestions.add(new Suggestion(
                                "sugg_" + System.currentTimeMillis() + "_skill_improve",
                                userId,
                                "技能提升建议",
                                String.format("您的任务成功率 %.0f%%，建议查看失败任务原因并改进。", earnings.successRate() * 100),
                                SuggestionType.LEARNING,
                                0.78,
                                Map.of("action", "review_failed_tasks", "successRate", earnings.successRate()),
                                Instant.now()
                            ));
                        }
                    }
                    int availableTasks = bountyHunterService.findAvailableTasks(userId).size();
                    if (availableTasks > 0) {
                        suggestions.add(new Suggestion(
                            "sugg_" + System.currentTimeMillis() + "_available_tasks",
                            userId,
                            "可接取任务",
                            String.format("有 %d 个任务可接取，建议查看并选择合适的任务。", availableTasks),
                            SuggestionType.ACTION,
                            0.80,
                            Map.of("action", "view_available_tasks", "availableCount", availableTasks),
                            Instant.now()
                        ));
                    }
                } catch (Exception e) {
                    log.debug("Failed to get bounty hunter data for employee: {}", e.getMessage());
                }
            }
        }

        return suggestions;
    }

    private Optional<LlmRiskAssessor.RiskAssessmentResult> assessRiskWithLlm(RiskPredictor.RiskAlert alert) {
        if (llmRiskAssessor == null || alert == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> indicators = new LinkedHashMap<>();
            indicators.put("alertId", alert.alertId());
            indicators.put("indicatorId", alert.indicatorId());
            indicators.put("indicatorName", alert.indicatorName());
            indicators.put("level", alert.level().name());
            indicators.put("severity", alert.level().getSeverity());
            indicators.put("probability", alert.probability());
            indicators.put("ruleRecommendation", alert.recommendation());
            return llmRiskAssessor.assessRisk("proactive_suggestion", indicators);
        } catch (Exception e) {
            log.warn("LLM risk assessment failed for alert {}: {}", alert.alertId(), e.getMessage());
            return Optional.empty();
        }
    }

    public void pushSuggestion(String userId, Suggestion suggestion) {
        Instant lastTime = lastSuggestionTime.get(userId);
        if (lastTime != null && 
                System.currentTimeMillis() - lastTime.toEpochMilli() < SUGGESTION_COOLDOWN_MS) {
            log.debug("Suggestion cooldown active for user: {}", userId);
            return;
        }

        userSuggestions.computeIfAbsent(userId, k -> new ArrayList<>()).add(suggestion);
        lastSuggestionTime.put(userId, Instant.now());

        if (suggestion.confidence() >= 0.85) {
            pushToNotifiers(userId, suggestion);
        }

        log.info("Pushed suggestion to user {}: {} (confidence: {})", 
                userId, suggestion.title(), suggestion.confidence());
    }

    private void pushToNotifiers(String userId, Suggestion suggestion) {
        String content = formatSuggestionContent(suggestion);

        for (AlertNotifier notifier : notifiers) {
            if (notifier.isAvailable()) {
                try {
                    Alert alert = Alert.info(
                            "智能建议: " + suggestion.title(),
                            content
                    ).withTargetUsers(List.of(userId));

                    notifier.send(alert);
                    log.debug("Suggestion pushed via {} to user {}", notifier.getChannelName(), userId);
                } catch (Exception e) {
                    log.warn("Failed to push suggestion via {}: {}", notifier.getChannelName(), e.getMessage());
                }
            }
        }
    }

    private String formatSuggestionContent(Suggestion suggestion) {
        StringBuilder content = new StringBuilder();
        
        content.append(suggestion.description()).append("\n\n");
        
        if (suggestion.metadata() != null && !suggestion.metadata().isEmpty()) {
            content.append("**相关信息**\n");
            for (Map.Entry<String, Object> entry : suggestion.metadata().entrySet()) {
                content.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        
        content.append("\n*置信度: ").append(String.format("%.0f%%", suggestion.confidence() * 100)).append("*");
        
        return content.toString();
    }

    public List<Suggestion> getUserSuggestions(String userId) {
        return userSuggestions.getOrDefault(userId, List.of());
    }

    public void clearUserSuggestions(String userId) {
        userSuggestions.remove(userId);
        lastSuggestionTime.remove(userId);
    }

    public void acknowledgeSuggestion(String userId, String suggestionId) {
        List<Suggestion> suggestions = userSuggestions.get(userId);
        if (suggestions != null) {
            suggestions.removeIf(s -> s.suggestionId().equals(suggestionId));
        }
        log.debug("Suggestion acknowledged: {} for user: {}", suggestionId, userId);
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userSuggestions.size());
        stats.put("totalSuggestions", userSuggestions.values().stream()
                .mapToInt(List::size)
                .sum());
        
        Map<String, Long> byType = new HashMap<>();
        for (SuggestionType type : SuggestionType.values()) {
            byType.put(type.name(), userSuggestions.values().stream()
                    .flatMap(List::stream)
                    .filter(s -> s.type() == type)
                    .count());
        }
        stats.put("byType", byType);
        
        return stats;
    }

    public record Suggestion(
            String suggestionId,
            String userId,
            String title,
            String description,
            SuggestionType type,
            double confidence,
            Map<String, Object> metadata,
            Instant createdAt
    ) {
        public boolean isHighConfidence() {
            return confidence >= 0.85;
        }
        
        public boolean isActionable() {
            return metadata != null && metadata.containsKey("action");
        }
        
        public String getAction() {
            return metadata != null ? (String) metadata.get("action") : null;
        }
    }

    public enum SuggestionType {
        WORKFLOW,
        ACTION,
        REPORT,
        WARNING,
        INSIGHT,
        REMINDER,
        LEARNING
    }
}
