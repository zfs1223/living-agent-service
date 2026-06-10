package com.livingagent.core.evolution.scheduler;

import com.livingagent.core.database.entity.EvolutionResultEntity;
import com.livingagent.core.database.repository.EvolutionResultRepository;
import com.livingagent.core.evolution.engine.EvolutionOrchestrator;
import com.livingagent.core.model.pool.BrainModelChangeHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Component
public class EvolutionSchedulerImpl implements EvolutionScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(EvolutionSchedulerImpl.class);
    private static final int FEEDBACK_RETENTION_DAYS = 30;
    private static final int MAX_RETRY_COUNT = 3;
    
    private final EvolutionOrchestrator orchestrator;
    private final EvolutionResultRepository resultRepository;
    private final BrainModelChangeHistoryRepository changeHistoryRepository;
    
    public EvolutionSchedulerImpl(EvolutionOrchestrator orchestrator,
                                  EvolutionResultRepository resultRepository,
                                  BrainModelChangeHistoryRepository changeHistoryRepository) {
        this.orchestrator = orchestrator;
        this.resultRepository = resultRepository;
        this.changeHistoryRepository = changeHistoryRepository;
    }
    
    @Scheduled(cron = "${evolution.scheduler.cron:0 0 * * * ?}")
    @Override
    public void runHourlyAdjustment() {
        log.info("Starting hourly evolution adjustment...");
        try {
            Map<String, Object> adjustments = orchestrator.runAutoAdjust(null);
            
            int adjustedCount = 0;
            int skippedCount = 0;
            int errorCount = 0;
            
            for (Map.Entry<String, Object> entry : adjustments.entrySet()) {
                String brainId = entry.getKey();
                Object resultObj = entry.getValue();
                
                if (resultObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = (Map<String, Object>) resultObj;
                    String status = (String) result.getOrDefault("status", "unknown");
                    
                    switch (status) {
                        case "adjusted":
                            adjustedCount++;
                            log.info("Brain {} adjusted: old={} -> new={}", brainId, 
                                    result.get("oldScore"), result.get("newModelId"));
                            break;
                        case "skipped":
                            skippedCount++;
                            log.debug("Brain {} skipped: {}", brainId, result.get("reason"));
                            break;
                        case "score_ok":
                            skippedCount++;
                            log.debug("Brain {} score OK: score={}", brainId, result.get("score"));
                            break;
                        case "no_candidate_model":
                            skippedCount++;
                            log.warn("Brain {} has no candidate model, score={}", brainId, result.get("score"));
                            break;
                        case "error":
                            errorCount++;
                            log.error("Brain {} adjustment failed: {}", brainId, result.get("error"));
                            break;
                        default:
                            log.warn("Brain {} unknown status: {}", brainId, status);
                    }
                }
            }
            
            log.info("Hourly adjustment completed: adjusted={}, skipped={}, errors={}, total={}",
                    adjustedCount, skippedCount, errorCount, adjustments.size());
        } catch (Exception e) {
            log.error("Hourly adjustment failed", e);
        }
    }
    
    @Scheduled(cron = "${evolution.scheduler.cleanup-cron:0 0 2 * * ?}")
    @Override
    public void cleanupExpiredFeedback() {
        log.info("Starting feedback cleanup...");
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(FEEDBACK_RETENTION_DAYS);
            Long cutoffTimestamp = cutoff.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            
            List<EvolutionResultEntity> expiredResults = resultRepository
                    .findByTimestampBeforeOrderByTimestampDesc(cutoffTimestamp);
            
            int deletedCount = expiredResults.size();
            for (EvolutionResultEntity entity : expiredResults) {
                resultRepository.delete(entity);
            }
            
            log.info("Deleted {} expired feedback records (older than {} days)", 
                    deletedCount, FEEDBACK_RETENTION_DAYS);
        } catch (Exception e) {
            log.error("Feedback cleanup failed", e);
        }
    }
    
    @Scheduled(cron = "${evolution.scheduler.retry-cron:0 */30 * * * ?}")
    @Override
    public void retryFailedTasks() {
        log.info("Starting failed task retry...");
        try {
            List<EvolutionResultEntity> failedResults = resultRepository
                    .findByStatusOrderByTimestampDesc("FAILED");
            
            int retriedCount = 0;
            for (EvolutionResultEntity entity : failedResults) {
                try {
                    var domainResult = entity.toDomain();
                    int currentRetryCount = domainResult.getRetryCount();
                    
                    if (currentRetryCount >= MAX_RETRY_COUNT) {
                        log.warn("Result {} exceeded max retry count ({}), skipping", 
                                entity.getResultId(), MAX_RETRY_COUNT);
                        continue;
                    }
                    
                    domainResult.setRetryCount(currentRetryCount + 1);
                    entity.setTimestamp(domainResult.getTimestamp());
                    resultRepository.save(entity);
                    
                    retriedCount++;
                    log.info("Retried result {} (attempt {}/{})", 
                            entity.getResultId(), currentRetryCount + 1, MAX_RETRY_COUNT);
                    
                } catch (Exception e) {
                    log.warn("Retry failed for result {}: {}", entity.getResultId(), e.getMessage());
                }
            }
            
            log.info("Failed task retry completed: {} tasks retried", retriedCount);
        } catch (Exception e) {
            log.error("Failed task retry failed", e);
        }
    }
}
