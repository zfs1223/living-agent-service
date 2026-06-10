package com.livingagent.core.proactive.suggestion;

import com.livingagent.core.proactive.alert.AlertNotifier;
import com.livingagent.core.proactive.alert.AlertNotifier.Alert;
import com.livingagent.core.proactive.llm.LlmProactiveAdvisor;
import com.livingagent.core.proactive.llm.LlmRiskAssessor;
import com.livingagent.core.proactive.predictor.PatternPredictor;
import com.livingagent.core.proactive.predictor.RiskPredictor;
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
    private final Map<String, List<Suggestion>> userSuggestions = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSuggestionTime = new ConcurrentHashMap<>();

    private static final long SUGGESTION_COOLDOWN_MS = 300000;
    private static final double CONFIDENCE_THRESHOLD = 0.7;

    public ProactiveSuggestionService(
            PatternPredictor patternPredictor,
            RiskPredictor riskPredictor,
            List<AlertNotifier> notifiers
    ) {
        this(patternPredictor, riskPredictor, null, null, notifiers);
    }

    public ProactiveSuggestionService(
            PatternPredictor patternPredictor,
            RiskPredictor riskPredictor,
            LlmProactiveAdvisor llmProactiveAdvisor,
            LlmRiskAssessor llmRiskAssessor,
            List<AlertNotifier> notifiers
    ) {
        this.patternPredictor = patternPredictor;
        this.riskPredictor = riskPredictor;
        this.llmProactiveAdvisor = llmProactiveAdvisor;
        this.llmRiskAssessor = llmRiskAssessor;
        this.notifiers = notifiers != null ? new ArrayList<>(notifiers) : new ArrayList<>();
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
