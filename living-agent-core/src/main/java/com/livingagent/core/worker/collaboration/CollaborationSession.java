package com.livingagent.core.worker.collaboration;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface CollaborationSession {

    String getSessionId();
    
    String getTitle();
    
    String getDescription();
    
    CollaborationType getType();
    
    CollaborationStatus getStatus();
    
    String getInitiatorId();
    
    List<String> getParticipantIds();
    
    List<CollaborationTask> getTasks();
    
    Map<String, Object> getContext();
    
    Instant getCreatedAt();
    
    Instant getStartedAt();
    
    Instant getCompletedAt();
    
    CollaborationResult getResult();
    
    void addParticipant(String employeeId);
    
    void removeParticipant(String employeeId);
    
    void assignTask(String taskId, String employeeId);
    
    void completeTask(String taskId, Map<String, Object> output);
    
    void start();
    
    void complete();
    
    void cancel(String reason);
    
    enum CollaborationType {
        TASK_CHAIN,
        PARALLEL,
        ROUND_ROBIN,
        DEBATE,
        CONSENSUS,
        HIERARCHICAL,
        PEER_REVIEW
    }
    
    enum CollaborationStatus {
        CREATED,
        RECRUITING,
        IN_PROGRESS,
        COMPLETING,
        COMPLETED,
        CANCELLED,
        FAILED
    }
    
    record CollaborationTask(
        String taskId,
        String name,
        String description,
        String assigneeId,
        TaskStatus status,
        int order,
        List<String> dependencies,
        Map<String, Object> input,
        Map<String, Object> output,
        Instant startedAt,
        Instant completedAt
    ) {
        public enum TaskStatus {
            PENDING,
            READY,
            IN_PROGRESS,
            COMPLETED,
            FAILED,
            SKIPPED
        }
    }
    
    record CollaborationResult(
        boolean success,
        String summary,
        Map<String, Object> deliverables,
        List<String> contributions,
        double overallScore,
        Map<String, Double> participantScores
    ) {}
}
