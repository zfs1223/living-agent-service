package com.livingagent.core.evolution.engine;

import com.livingagent.core.evolution.executor.EvolutionExecutor;
import com.livingagent.core.evolution.executor.EvolutionFeedbackService;
import com.livingagent.core.evolution.executor.EvolutionResult;
import com.livingagent.core.evolution.executor.EvolutionResultRepository;
import com.livingagent.core.evolution.executor.impl.JpaEvolutionFeedbackService;
import com.livingagent.core.evolution.signal.EvolutionSignal;
import com.livingagent.core.evolution.signal.SignalExtractor;
import com.livingagent.core.model.pool.BrainModelAssigner;
import com.livingagent.core.model.pool.BrainModelChangeHistory;
import com.livingagent.core.model.pool.BrainModelChangeHistoryRepository;
import com.livingagent.core.model.pool.LlmModel;
import com.livingagent.core.model.pool.ModelPoolManager;
import com.livingagent.core.model.pool.ProviderConfig;
import com.livingagent.core.model.selector.BrainModelSelectorManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class EvolutionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(EvolutionOrchestrator.class);
    private static final double AUTO_ADJUST_THRESHOLD = 0.4;
    private static final int FEEDBACK_SAMPLE_SIZE = 50;
    private static final int CONSECUTIVE_FAILURES_REPLACE = 3;
    private static final int CONSECUTIVE_LOW_RATING_DOWNGRADE = 5;
    private static final int CONSECUTIVE_SLOW_RESPONSE_UPGRADE = 10;

    private final SignalExtractor signalExtractor;
    private final EvolutionDecisionEngine decisionEngine;
    private final EvolutionExecutor evolutionExecutor;
    private final EvolutionFeedbackService feedbackService;
    private final BrainModelSelectorManager selectorManager;
    private final BrainModelAssigner brainModelAssigner;
    private final BrainModelChangeHistoryRepository changeHistoryRepository;
    private final ModelPoolManager modelPoolManager;

    public EvolutionOrchestrator(
            SignalExtractor signalExtractor,
            EvolutionDecisionEngine decisionEngine,
            EvolutionExecutor evolutionExecutor,
            EvolutionFeedbackService feedbackService,
            BrainModelSelectorManager selectorManager,
            BrainModelAssigner brainModelAssigner,
            BrainModelChangeHistoryRepository changeHistoryRepository,
            ModelPoolManager modelPoolManager) {
        this.signalExtractor = signalExtractor;
        this.decisionEngine = decisionEngine;
        this.evolutionExecutor = evolutionExecutor;
        this.feedbackService = feedbackService;
        this.selectorManager = selectorManager;
        this.brainModelAssigner = brainModelAssigner;
        this.changeHistoryRepository = changeHistoryRepository;
        this.modelPoolManager = modelPoolManager;
    }

    public OrchestrationReport run(Map<String, Object> sourceContext) {
        List<EvolutionSignal> signals = signalExtractor.extractFromMetrics(sourceContext);
        List<EvolutionResult> results = new ArrayList<>();

        for (EvolutionSignal signal : signals) {
            if (!decisionEngine.shouldTriggerEvolution(signal.getBrainDomain())) {
                continue;
            }
            EvolutionResult result = evolutionExecutor.execute(signal);
            feedbackService.record(result);
            results.add(result);
        }

        long success = results.stream().filter(r -> r.getStatus() == EvolutionResult.Status.SUCCESS).count();
        long failed = results.stream().filter(r -> r.getStatus() == EvolutionResult.Status.FAILED).count();

        log.info("Evolution orchestration completed: signals={}, success={}, failed={}",
                signals.size(), success, failed);

        return new OrchestrationReport(signals.size(), results.size(), success, failed, results);
    }

    public double score(EvolutionResult result) {
        double userRating = extractUserRating(result);
        double responseTime = extractResponseTime(result);
        boolean success = result.isSuccess();

        double normalizedRating = userRating / 5.0;
        double timeScore = Math.max(0, 1.0 - (responseTime / 30000.0));

        double finalScore = (normalizedRating * 0.6) + (timeScore * 0.3) + (success ? 0.1 : 0.0);

        return Math.min(1.0, Math.max(0.0, finalScore));
    }

    private double extractUserRating(EvolutionResult result) {
        if (result.getMetadata() != null && result.getMetadata().containsKey("userRating")) {
            Object rating = result.getMetadata().get("userRating");
            if (rating instanceof Number) {
                return ((Number) rating).doubleValue();
            }
        }
        return result.isSuccess() ? 4.0 : 1.5;
    }

    private double extractResponseTime(EvolutionResult result) {
        if (result.getExecutionTimeMs() > 0) {
            return (double) result.getExecutionTimeMs();
        }
        if (result.getMetadata() != null && result.getMetadata().containsKey("responseTimeMs")) {
            Object time = result.getMetadata().get("responseTimeMs");
            if (time instanceof Number) {
                return ((Number) time).doubleValue();
            }
        }
        return 5000.0;
    }

    public AutoAdjustStrategy selectStrategy(EvolutionSignal signal) {
        String brainDomain = signal.getBrainDomain();
        int consecutiveFailures = feedbackService instanceof JpaEvolutionFeedbackService
                ? ((JpaEvolutionFeedbackService) feedbackService).getConsecutiveFailures(brainDomain)
                : 0;

        if (consecutiveFailures >= CONSECUTIVE_FAILURES_REPLACE) {
            return AutoAdjustStrategy.REPLACE_MODEL;
        }

        double avgRating = getAverageRating(brainDomain);
        if (avgRating < 2.0 && consecutiveFailures >= CONSECUTIVE_LOW_RATING_DOWNGRADE) {
            return AutoAdjustStrategy.DOWNGRADE_MODEL;
        }

        double avgResponseTime = getAverageResponseTime(brainDomain);
        if (avgResponseTime > 15000 && consecutiveFailures >= CONSECUTIVE_SLOW_RESPONSE_UPGRADE) {
            return AutoAdjustStrategy.UPGRADE_MODEL;
        }

        if (signal.getType() == EvolutionSignal.SignalType.ERROR && signal.getConfidence() > 0.8) {
            return AutoAdjustStrategy.ESCALATE_TO_ADMIN;
        }

        return AutoAdjustStrategy.DEFER;
    }

    private double getAverageRating(String brainDomain) {
        Map<String, Object> stats = feedbackService.statistics();
        Object byBrainObj = stats.get("by_brain");
        if (byBrainObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> byBrain = (Map<String, Map<String, Object>>) byBrainObj;
            Map<String, Object> brainStats = byBrain.get(brainDomain);
            if (brainStats != null && brainStats.containsKey("avgScore")) {
                Object avgScore = brainStats.get("avgScore");
                if (avgScore instanceof Number) {
                    return ((Number) avgScore).doubleValue();
                }
            }
        }
        return 3.0;
    }

    private double getAverageResponseTime(String brainDomain) {
        Map<String, Object> stats = feedbackService.statistics();
        Object byBrainObj = stats.get("by_brain");
        if (byBrainObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> byBrain = (Map<String, Map<String, Object>>) byBrainObj;
            Map<String, Object> brainStats = byBrain.get(brainDomain);
            if (brainStats != null && brainStats.containsKey("avgResponseTimeMs")) {
                Object avgTime = brainStats.get("avgResponseTimeMs");
                if (avgTime instanceof Number) {
                    return ((Number) avgTime).doubleValue();
                }
            }
        }
        return 5000.0;
    }

    public Map<String, Object> runAutoAdjust(String brainId) {
        log.info("Starting auto-adjust for brain: {}", brainId);
        Map<String, Object> adjustmentResults = new HashMap<>();

        List<EvolutionResult> recentFeedback = feedbackService.recent(FEEDBACK_SAMPLE_SIZE);
        Map<String, List<EvolutionResult>> groupedByBrain = groupByBrainDomain(recentFeedback);

        List<String> targetBrains = brainId != null
                ? Collections.singletonList(brainId)
                : new ArrayList<>(groupedByBrain.keySet());

        for (String targetBrain : targetBrains) {
            try {
                List<EvolutionResult> brainResults = groupedByBrain.getOrDefault(targetBrain, Collections.emptyList());
                double avgScore = calculateAverageScore(brainResults);

                if (avgScore < AUTO_ADJUST_THRESHOLD) {
                    log.info("Brain {} score {:.3f} below threshold {}, triggering model replacement",
                            targetBrain, avgScore, AUTO_ADJUST_THRESHOLD);

                    LlmModel currentModel = brainModelAssigner.getModelForBrain(targetBrain);
                    UUID currentModelId = currentModel != null ? currentModel.getId() : null;
                    String brainType = getBrainType(targetBrain);

                    Optional<LlmModel> candidateOpt = selectorManager.selectBestCandidateModel(targetBrain, brainType, currentModelId);
                    if (candidateOpt.isPresent()) {
                        LlmModel newModel = candidateOpt.get();
                        brainModelAssigner.assignModel(
                                targetBrain,
                                newModel.getDisplayName(),
                                brainType,
                                newModel.getId(),
                                "auto_adjust"
                        );

                        recordChangeHistory(targetBrain, newModel.getDisplayName(),
                                brainType, newModel.getId(), newModel.getModelName(),
                                "auto", "system", "Auto-adjust: score " + String.format("%.3f", avgScore));

                        Map<String, Object> result = new HashMap<>();
                        result.put("status", "adjusted");
                        result.put("oldScore", avgScore);
                        result.put("oldModelId", currentModelId != null ? currentModelId.toString() : null);
                        result.put("newModelId", newModel.getId().toString());
                        result.put("newModelName", newModel.getModelName());
                        result.put("reason", "low_score");
                        result.put("strategy", "REPLACE_MODEL");
                        adjustmentResults.put(targetBrain, result);
                        log.info("Brain {} adjusted to model {}", targetBrain, newModel.getModelName());
                    } else {
                        Map<String, Object> result = new HashMap<>();
                        result.put("status", "skipped");
                        result.put("score", avgScore);
                        result.put("reason", "no_suitable_candidate");
                        adjustmentResults.put(targetBrain, result);
                    }
                } else {
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "score_ok");
                    result.put("score", avgScore);
                    adjustmentResults.put(targetBrain, result);
                }
            } catch (Exception e) {
                log.error("Auto-adjust failed for brain {}: {}", targetBrain, e.getMessage(), e);
                adjustmentResults.put(targetBrain, Map.of(
                        "status", "error",
                        "error", e.getMessage()
                ));
            }
        }

        log.info("Auto-adjust completed: {} brains processed", adjustmentResults.size());
        return adjustmentResults;
    }

    private Map<String, List<EvolutionResult>> groupByBrainDomain(List<EvolutionResult> results) {
        Map<String, List<EvolutionResult>> grouped = new HashMap<>();
        for (EvolutionResult result : results) {
            String brainDomain = result.getSignal() != null
                    ? result.getSignal().getBrainDomain()
                    : "unknown";
            grouped.computeIfAbsent(brainDomain, k -> new ArrayList<>()).add(result);
        }
        return grouped;
    }

    private double calculateAverageScore(List<EvolutionResult> results) {
        if (results.isEmpty()) {
            return 1.0;
        }
        double totalScore = 0.0;
        for (EvolutionResult result : results) {
            totalScore += score(result);
        }
        return totalScore / results.size();
    }

    private String getBrainType(String brainId) {
        if (brainId == null) return "default";
        String lower = brainId.toLowerCase();
        if (lower.contains("main")) return "main";
        if (lower.contains("tech")) return "tech";
        if (lower.contains("admin")) return "admin";
        if (lower.contains("hr")) return "hr";
        if (lower.contains("finance")) return "finance";
        if (lower.contains("sales")) return "sales";
        if (lower.contains("cs")) return "cs";
        if (lower.contains("ops")) return "ops";
        if (lower.contains("legal")) return "legal";
        return "default";
    }

    public Map<String, Object> rollbackBrain(String brainId) {
        Map<String, Object> result = new HashMap<>();
        result.put("brainId", brainId);

        List<BrainModelChangeHistory> history = changeHistoryRepository.findByBrainIdOrderByCreatedAtDesc(brainId);
        if (history.isEmpty()) {
            log.warn("No change history found for brain: {}", brainId);
            result.put("status", "failed");
            result.put("reason", "no_history_found");
            result.put("message", "未找到 " + brainId + " 的变更记录");
            return result;
        }

        BrainModelChangeHistory lastManual = history.stream()
                .filter(c -> "manual".equals(c.getSource()))
                .findFirst()
                .orElse(null);

        if (lastManual == null) {
            log.warn("No manual configuration found for brain: {}", brainId);
            result.put("status", "failed");
            result.put("reason", "no_manual_baseline");
            result.put("message", "未找到 " + brainId + " 的手工配置基线");
            return result;
        }

        try {
            brainModelAssigner.assignModel(
                    brainId,
                    lastManual.getBrainName(),
                    lastManual.getBrainType(),
                    lastManual.getModelId(),
                    "system_rollback"
            );

            LlmModel model = modelPoolManager.getModelById(lastManual.getModelId());
            recordChangeHistory(brainId, lastManual.getBrainName(),
                    lastManual.getBrainType(), lastManual.getModelId(),
                    model != null ? model.getModelName() : null,
                    "rollback", "system", "Rolled back to previous manual configuration");

            log.info("Brain {} rolled back to model {}", brainId, lastManual.getModelId());
            result.put("status", "success");
            result.put("restoredModelId", lastManual.getModelId().toString());
            result.put("restoredModelName", model != null ? model.getModelName() : "unknown");
            result.put("message", "已回滚 " + brainId + " 到最近一次手工配置");
            return result;
        } catch (Exception e) {
            log.error("Rollback failed for brain {}: {}", brainId, e.getMessage(), e);
            result.put("status", "failed");
            result.put("reason", "assign_failed");
            result.put("message", "回滚失败: " + e.getMessage());
            return result;
        }
    }

    private void recordChangeHistory(String brainId, String brainName, String brainType,
                                     UUID modelId, String modelName, String source,
                                     String changedBy, String reason) {
        BrainModelChangeHistory history = new BrainModelChangeHistory(
                brainId, brainName, brainType, modelId, modelName, source, changedBy, reason
        );
        changeHistoryRepository.save(history);
    }

    public record OrchestrationReport(
            int extractedSignals,
            int executedSignals,
            long successCount,
            long failedCount,
            List<EvolutionResult> results
    ) {}
}
