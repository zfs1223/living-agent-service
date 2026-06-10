package com.livingagent.core.worker.collaboration;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工人层协作会话服务 —— 数字员工之间的多向协作管理。
 *
 * <p><b>层次边界</b>：本接口属于 worker 层，负责"员工之间如何协作"。
 * 与 {@code brain.collaboration.LeadOrchestrator}（大脑层，负责"大脑如何指挥员工"）职责不同：
 * <ul>
 *   <li>worker/collaboration：员工 ↔ 员工，多向协作，基于内存会话和7种协作类型</li>
 *   <li>brain/collaboration：大脑 → 员工，单向指挥，基于 Channel 通道系统和 TaskDagService</li>
 * </ul>
 * 工人层的协作会话管理使用本接口，大脑层的任务编排使用 LeadOrchestrator。</p>
 */
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
