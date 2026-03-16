package com.livingagent.core.worker.collaboration;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CollaborationService {

    CollaborationSession createSession(CollaborationRequest request);
    
    Optional<CollaborationSession> getSession(String sessionId);
    
    List<CollaborationSession> getActiveSessions();
    
    List<CollaborationSession> getSessionsByParticipant(String employeeId);
    
    List<CollaborationSession> getSessionsByInitiator(String employeeId);
    
    void joinSession(String sessionId, String employeeId);
    
    void leaveSession(String sessionId, String employeeId);
    
    void startSession(String sessionId);
    
    void completeTask(String sessionId, String taskId, Map<String, Object> output);
    
    void cancelSession(String sessionId, String reason);
    
    CollaborationSession.CollaborationStatus getSessionStatus(String sessionId);
    
    List<CollaborationSession.CollaborationTask> getPendingTasks(String sessionId, String employeeId);
    
    void updateContext(String sessionId, Map<String, Object> context);
    
    CollaborationSession.CollaborationResult waitForCompletion(String sessionId, long timeoutMs);
    
    List<CollaborationRecommendation> recommendCollaborators(String sessionId, String taskDescription);
    
    record CollaborationRequest(
        String title,
        String description,
        CollaborationSession.CollaborationType type,
        String initiatorId,
        List<String> participantIds,
        List<TaskDefinition> tasks,
        Map<String, Object> context
    ) {}
    
    record TaskDefinition(
        String name,
        String description,
        String assigneeId,
        int order,
        List<String> dependencies,
        Map<String, Object> input
    ) {}
    
    record CollaborationRecommendation(
        String employeeId,
        String employeeName,
        double matchScore,
        String reason,
        List<String> relevantSkills
    ) {}
}
