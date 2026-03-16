package com.livingagent.core.skill.bounty;

import java.util.List;
import java.util.Optional;

public interface BountyHunterService {

    List<BountyTask> findAvailableTasks(String workerId);
    
    List<BountyTask> findMatchingTasks(String workerId, List<String> skills);
    
    List<BountyTask> findTasksByType(BountyTask.BountyType type);
    
    List<BountyTask> findTasksByDifficulty(BountyTask.DifficultyLevel maxDifficulty);
    
    List<BountyTask> findHighRewardTasks(double minReward);
    
    Optional<BountyTask> getTask(String taskId);
    
    boolean acceptTask(String taskId, String workerId);
    
    boolean reserveTask(String taskId, String workerId, java.time.Duration holdDuration);
    
    boolean releaseTask(String taskId, String workerId);
    
    boolean submitTask(String taskId, String workerId, BountySubmission submission);
    
    Optional<BountyReview> getReview(String taskId);
    
    List<BountyTask> getWorkerTasks(String workerId);
    
    List<BountyTask> getWorkerActiveTasks(String workerId);
    
    List<BountyTask> getWorkerCompletedTasks(String workerId);
    
    WorkerEarnings getWorkerEarnings(String workerId);
    
    void registerTaskProvider(TaskProvider provider);
    
    void unregisterTaskProvider(String providerId);
    
    record BountySubmission(
        String taskId,
        String workerId,
        java.util.Map<String, Object> deliverables,
        String notes,
        java.time.Instant submittedAt
    ) {}
    
    record BountyReview(
        String reviewId,
        String taskId,
        String reviewerId,
        boolean approved,
        double actualReward,
        String feedback,
        java.time.Instant reviewedAt
    ) {}
    
    record WorkerEarnings(
        String workerId,
        double totalEarned,
        double pendingEarnings,
        int tasksCompleted,
        int tasksRejected,
        double averageRating,
        double successRate
    ) {}
    
    interface TaskProvider {
        String getProviderId();
        String getProviderName();
        List<BountyTask> fetchAvailableTasks();
        Optional<BountyTask> fetchTask(String taskId);
        void notifyTaskCompletion(String taskId, BountySubmission submission);
    }
}
