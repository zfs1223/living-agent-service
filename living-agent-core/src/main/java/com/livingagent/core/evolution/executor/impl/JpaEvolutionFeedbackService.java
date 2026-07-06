package com.livingagent.core.evolution.executor.impl;

import com.livingagent.core.database.entity.EvolutionFeedbackEntity;
import com.livingagent.core.database.entity.EvolutionResultEntity;
import com.livingagent.core.evolution.executor.EvolutionFeedbackService;
import com.livingagent.core.evolution.executor.EvolutionResult;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Primary
public class JpaEvolutionFeedbackService implements EvolutionFeedbackService {

    private final com.livingagent.core.database.repository.EvolutionFeedbackRepository feedbackRepository;
    private final com.livingagent.core.database.repository.EvolutionResultRepository resultRepository;
    private final Map<String, Integer> consecutiveFailureCount = new HashMap<>();

    public JpaEvolutionFeedbackService(com.livingagent.core.database.repository.EvolutionFeedbackRepository feedbackRepository,
                                       com.livingagent.core.database.repository.EvolutionResultRepository resultRepository) {
        this.feedbackRepository = feedbackRepository;
        this.resultRepository = resultRepository;
    }

    @Override
    @Transactional
    public void record(EvolutionResult result) {
        if (result == null) {
            return;
        }

        EvolutionResultEntity savedResult = resultRepository.findByResultId(result.getResultId())
                .orElseGet(() -> resultRepository.save(EvolutionResultEntity.fromDomain(result)));

        EvolutionFeedbackEntity feedback = new EvolutionFeedbackEntity();
        feedback.setResultId(savedResult.getResultId());
        feedback.setFeedbackType(result.getStatus() != null ? result.getStatus().name() : "UNKNOWN");
        feedback.setScore(result.isSuccess() ? 1.0 : 0.0);
        feedback.setComment(result.getErrorMessage());
        feedback.setSource(result.getAction() != null ? result.getAction() : "evolution");
        feedback.setMetadataJson(result.toMap().toString());
        feedbackRepository.save(feedback);
        
        checkConsecutiveFailures(result);
    }

    private void checkConsecutiveFailures(EvolutionResult result) {
        String brainDomain = extractBrainDomain(result);
        if (result.getStatus() == EvolutionResult.Status.FAILED) {
            consecutiveFailureCount.merge(brainDomain, 1, Integer::sum);
        } else {
            consecutiveFailureCount.put(brainDomain, 0);
        }
    }
    
    private String extractBrainDomain(EvolutionResult result) {
        if (result.getSignal() != null && result.getSignal().getBrainDomain() != null) {
            return result.getSignal().getBrainDomain();
        }
        if (result.getMetadata() != null && result.getMetadata().containsKey("brainId")) {
            Object brainId = result.getMetadata().get("brainId");
            if (brainId instanceof String && !((String) brainId).isBlank()) {
                return (String) brainId;
            }
        }
        return "unknown";
    }

    @Override
    public List<EvolutionResult> recent(int limit) {
        return resultRepository.findTop50ByOrderByTimestampDesc().stream()
                .limit(limit)
                .map(EvolutionResultEntity::toDomain)
                .toList();
    }

    @Override
    public Map<String, Object> statistics() {
        List<EvolutionResultEntity> allResults = resultRepository.findTop500ByOrderByTimestampDesc();
        List<EvolutionFeedbackEntity> allFeedback = feedbackRepository.findTop100ByOrderByCreatedAtDesc();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", allResults.size());
        
        long successCount = allResults.stream()
                .filter(r -> Objects.equals(r.getStatus(), EvolutionResult.Status.SUCCESS.name()))
                .count();
        stats.put("success_rate", allResults.isEmpty() ? 0.0 : (double) successCount / allResults.size());
        
        double avgExecutionTime = allResults.stream()
                .filter(r -> r.getExecutionTimeMs() != null)
                .mapToLong(EvolutionResultEntity::getExecutionTimeMs)
                .average()
                .orElse(0.0);
        stats.put("avg_response_time_ms", avgExecutionTime);
        
        double avgScore = allFeedback.stream()
                .filter(f -> f.getScore() != null)
                .mapToDouble(EvolutionFeedbackEntity::getScore)
                .average()
                .orElse(0.0);
        stats.put("avg_user_rating", avgScore * 5.0);
        
        Map<String, Map<String, Object>> byBrain = new HashMap<>();
        Map<String, List<EvolutionResultEntity>> groupedByBrain = allResults.stream()
                .collect(Collectors.groupingBy(this::extractStableBrainId));
        
        for (Map.Entry<String, List<EvolutionResultEntity>> entry : groupedByBrain.entrySet()) {
            String brainId = entry.getKey();
            List<EvolutionResultEntity> brainResults = entry.getValue();
            
            Map<String, Object> brainStats = new HashMap<>();
            brainStats.put("brainId", brainId);
            brainStats.put("sampleSize", brainResults.size());
            
            long brainSuccess = brainResults.stream()
                    .filter(r -> Objects.equals(r.getStatus(), EvolutionResult.Status.SUCCESS.name()))
                    .count();
            brainStats.put("success_rate", brainResults.isEmpty() ? 0.0 : (double) brainSuccess / brainResults.size());
            brainStats.put("failureRate", brainResults.isEmpty() ? 0.0 : (double) (brainResults.size() - brainSuccess) / brainResults.size());
            
            brainStats.put("avgScore", calculateScoreForResults(brainResults));
            
            double brainAvgResponseTime = brainResults.stream()
                    .filter(r -> r.getExecutionTimeMs() != null)
                    .mapToLong(EvolutionResultEntity::getExecutionTimeMs)
                    .average()
                    .orElse(0.0);
            brainStats.put("avgResponseTimeMs", brainAvgResponseTime);
            
            brainStats.put("consecutiveFailures", getConsecutiveFailures(brainId));
            
            String brainType = brainResults.stream()
                    .map(EvolutionResultEntity::getBrainType)
                    .filter(Objects::nonNull)
                    .filter(t -> !t.isBlank())
                    .findFirst()
                    .orElse(extractBrainTypeFromId(brainId));
            brainStats.put("brainType", brainType);
            
            String department = brainResults.stream()
                    .map(EvolutionResultEntity::getDepartment)
                    .filter(Objects::nonNull)
                    .filter(t -> !t.isBlank())
                    .findFirst()
                    .orElse(extractDepartmentFromId(brainId));
            brainStats.put("department", department);
            
            byBrain.put(brainId, brainStats);
        }
        stats.put("by_brain", byBrain);
        
        Map<String, Integer> failures = new HashMap<>(consecutiveFailureCount);
        stats.put("consecutive_failures", failures);
        
        stats.put("immediateEffective", allResults.stream()
                .filter(r -> Boolean.TRUE.equals(r.getImmediateEffective()))
                .count());
        
        return stats;
    }
    
    private String extractStableBrainId(EvolutionResultEntity entity) {
        if (entity.getBrainId() != null && !entity.getBrainId().isBlank()) {
            return entity.getBrainId();
        }
        if (entity.getSignalId() != null && !entity.getSignalId().isBlank()) {
            return entity.getSignalId();
        }
        return "unknown";
    }
    
    private String extractBrainTypeFromId(String brainId) {
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
    
    private String extractDepartmentFromId(String brainId) {
        if (brainId == null) return "unknown";
        if (brainId.startsWith("neuron://")) {
            String[] parts = brainId.split("/");
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return extractBrainTypeFromId(brainId);
    }
    
    private double calculateScoreForResults(List<EvolutionResultEntity> results) {
        if (results.isEmpty()) return 0.0;
        
        double totalScore = 0.0;
        for (EvolutionResultEntity entity : results) {
            boolean success = Objects.equals(entity.getStatus(), EvolutionResult.Status.SUCCESS.name());
            double responseTime = entity.getExecutionTimeMs() != null ? entity.getExecutionTimeMs() : 5000.0;
            double timeScore = Math.max(0, 1.0 - (responseTime / 30000.0));
            totalScore += (success ? 1.0 : 0.0) * 0.6 + timeScore * 0.4;
        }
        return totalScore / results.size();
    }
    
    public int getConsecutiveFailures(String brainDomain) {
        return consecutiveFailureCount.getOrDefault(brainDomain, 0);
    }
}
