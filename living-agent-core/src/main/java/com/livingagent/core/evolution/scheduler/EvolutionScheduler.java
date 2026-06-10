package com.livingagent.core.evolution.scheduler;

public interface EvolutionScheduler {
    
    void runHourlyAdjustment();
    
    void cleanupExpiredFeedback();
    
    void retryFailedTasks();
}
