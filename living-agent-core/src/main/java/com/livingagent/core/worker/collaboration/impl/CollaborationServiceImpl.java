package com.livingagent.core.worker.collaboration.impl;

import com.livingagent.core.autonomy.CodeReviewWorkflowService;
import com.livingagent.core.autonomy.CodeReviewWorkflowService.ReviewStage;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import com.livingagent.core.worker.collaboration.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CollaborationServiceImpl implements CollaborationService {

    private static final Logger log = LoggerFactory.getLogger(CollaborationServiceImpl.class);

    private final FixedEmployeeRegistry fixedEmployeeRegistry;
    private final CodeReviewWorkflowService codeReviewWorkflowService;

    private final Map<String, CollaborationSessionImpl> sessions = new ConcurrentHashMap<>();

    public CollaborationServiceImpl(FixedEmployeeRegistry fixedEmployeeRegistry,
                                    CodeReviewWorkflowService codeReviewWorkflowService) {
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.codeReviewWorkflowService = codeReviewWorkflowService;
    }

    @Override
    public CollaborationSession createSession(CollaborationRequest request) {
        String sessionId = "collab-" + UUID.randomUUID().toString().substring(0, 8);
        
        List<CollaborationSession.CollaborationTask> tasks = new ArrayList<>();
        for (int i = 0; i < request.tasks().size(); i++) {
            TaskDefinition td = request.tasks().get(i);
            tasks.add(new CollaborationSession.CollaborationTask(
                sessionId + "-task-" + i,
                td.name(),
                td.description(),
                td.assigneeId(),
                CollaborationSession.CollaborationTask.TaskStatus.PENDING,
                td.order(),
                td.dependencies() != null ? td.dependencies() : List.of(),
                td.input() != null ? td.input() : Map.of(),
                Map.of(),
                null,
                null
            ));
        }
        
        CollaborationSessionImpl session = new CollaborationSessionImpl(
            sessionId,
            request.title(),
            request.description(),
            request.type(),
            CollaborationSession.CollaborationStatus.CREATED,
            request.initiatorId(),
            new ArrayList<>(request.participantIds()),
            tasks,
            new ConcurrentHashMap<>(request.context() != null ? request.context() : Map.of())
        );
        
        sessions.put(sessionId, session);
        log.info("Created collaboration session: {} (type: {}, initiator: {})", 
            sessionId, request.type(), request.initiatorId());
        
        return session;
    }

    @Override
    public Optional<CollaborationSession> getSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public List<CollaborationSession> getActiveSessions() {
        return sessions.values().stream()
            .filter(s -> s.getStatus() == CollaborationSession.CollaborationStatus.IN_PROGRESS ||
                        s.getStatus() == CollaborationSession.CollaborationStatus.RECRUITING)
            .map(s -> (CollaborationSession) s)
            .collect(Collectors.toList());
    }

    @Override
    public List<CollaborationSession> getSessionsByParticipant(String employeeId) {
        return sessions.values().stream()
            .filter(s -> s.getParticipantIds().contains(employeeId))
            .map(s -> (CollaborationSession) s)
            .collect(Collectors.toList());
    }

    @Override
    public List<CollaborationSession> getSessionsByInitiator(String employeeId) {
        return sessions.values().stream()
            .filter(s -> s.getInitiatorId().equals(employeeId))
            .map(s -> (CollaborationSession) s)
            .collect(Collectors.toList());
    }

    @Override
    public void joinSession(String sessionId, String employeeId) {
        CollaborationSessionImpl session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        
        if (!session.getParticipantIds().contains(employeeId)) {
            session.addParticipant(employeeId);
            log.info("Employee {} joined session {}", employeeId, sessionId);
        }
    }

    @Override
    public void leaveSession(String sessionId, String employeeId) {
        CollaborationSessionImpl session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        
        session.removeParticipant(employeeId);
        log.info("Employee {} left session {}", employeeId, sessionId);
    }

    @Override
    public void startSession(String sessionId) {
        CollaborationSessionImpl session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        
        session.start();
        log.info("Started collaboration session: {}", sessionId);
    }

    @Override
    public void completeTask(String sessionId, String taskId, Map<String, Object> output) {
        CollaborationSessionImpl session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        
        session.completeTask(taskId, output);
        log.info("Completed task {} in session {}", taskId, sessionId);

        // PEER_REVIEW 类型：代码任务完成后自动推进审查状态机
        if (session.getType() == CollaborationSession.CollaborationType.PEER_REVIEW) {
            advancePeerReviewState(session, taskId, output);
        }

        checkAndCompleteSession(session);
    }

    /**
     * PEER_REVIEW 协作类型：任务完成后自动推进审查状态机。
     * - 开发任务完成 → CODE_SUBMITTED
     * - 审查任务完成 → REVIEW_APPROVED 或 REVIEW_CHANGES_REQUESTED
     */
    private void advancePeerReviewState(CollaborationSessionImpl session, String taskId,
                                         Map<String, Object> output) {
        String reviewTaskId = (String) session.getContext().getOrDefault("reviewTaskId", taskId);
        codeReviewWorkflowService.getByTaskId(reviewTaskId).ifPresent(state -> {
            ReviewStage currentStage = state.stage();
            String taskName = session.getTasks().stream()
                .filter(t -> t.taskId().equals(taskId))
                .map(CollaborationSession.CollaborationTask::name)
                .findFirst().orElse("");

            try {
                if (taskName.contains("开发") || taskName.contains("develop") || taskName.contains("write")) {
                    // 开发任务完成 → CODE_SUBMITTED
                    if (currentStage == ReviewStage.DEVELOPER_WRITING) {
                        codeReviewWorkflowService.advanceStage(reviewTaskId, ReviewStage.CODE_SUBMITTED, Map.of());
                        log.info("PEER_REVIEW: task {} developer completed, advanced to CODE_SUBMITTED", taskId);
                    }
                } else if (taskName.contains("审查") || taskName.contains("review")) {
                    // 审查任务完成 → 根据输出决定 APPROVED 或 CHANGES_REQUESTED
                    String reviewVerdict = output != null ? (String) output.getOrDefault("verdict", "") : "";
                    if ("APPROVED".equalsIgnoreCase(reviewVerdict)) {
                        codeReviewWorkflowService.approve(reviewTaskId, Map.of());
                        log.info("PEER_REVIEW: task {} reviewer approved", taskId);
                    } else {
                        @SuppressWarnings("unchecked")
                        List<String> findings = output != null && output.containsKey("findings")
                            ? (List<String>) output.get("findings") : List.of("审查未通过，需要修改");
                        codeReviewWorkflowService.requestChanges(reviewTaskId, findings, Map.of());
                        log.info("PEER_REVIEW: task {} reviewer requested changes: {}", taskId, findings);
                    }
                }
            } catch (Exception e) {
                log.warn("PEER_REVIEW: failed to advance review state for task {}: {}", taskId, e.getMessage());
            }
        });
    }

    private void checkAndCompleteSession(CollaborationSessionImpl session) {
        boolean allCompleted = session.getTasks().stream()
            .allMatch(t -> t.status() == CollaborationSession.CollaborationTask.TaskStatus.COMPLETED ||
                          t.status() == CollaborationSession.CollaborationTask.TaskStatus.SKIPPED);
        
        if (allCompleted) {
            session.complete();
            log.info("All tasks completed, session {} finished", session.getSessionId());
        }
    }

    @Override
    public void cancelSession(String sessionId, String reason) {
        CollaborationSessionImpl session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        
        session.cancel(reason);
        log.info("Cancelled session {}: {}", sessionId, reason);
    }

    @Override
    public CollaborationSession.CollaborationStatus getSessionStatus(String sessionId) {
        CollaborationSessionImpl session = sessions.get(sessionId);
        return session != null ? session.getStatus() : null;
    }

    @Override
    public List<CollaborationSession.CollaborationTask> getPendingTasks(String sessionId, String employeeId) {
        CollaborationSessionImpl session = sessions.get(sessionId);
        if (session == null) {
            return List.of();
        }
        
        return session.getTasks().stream()
            .filter(t -> employeeId.equals(t.assigneeId()))
            .filter(t -> t.status() == CollaborationSession.CollaborationTask.TaskStatus.PENDING ||
                        t.status() == CollaborationSession.CollaborationTask.TaskStatus.READY)
            .collect(Collectors.toList());
    }

    @Override
    public void updateContext(String sessionId, Map<String, Object> context) {
        CollaborationSessionImpl session = sessions.get(sessionId);
        if (session != null) {
            session.getContext().putAll(context);
        }
    }

    @Override
    public CollaborationSession.CollaborationResult waitForCompletion(String sessionId, long timeoutMs) {
        CollaborationSessionImpl session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        
        try {
            session.getCompletionLatch().await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return session.getResult();
    }

    @Override
    public List<CollaborationRecommendation> recommendCollaborators(String sessionId, String taskDescription) {
        List<FixedEmployeeRegistry.FixedEmployeeDefinition> allDefs = fixedEmployeeRegistry.getAllDefinitions();
        if (allDefs.isEmpty()) {
            log.warn("No fixed employee definitions found, returning empty recommendations");
            return List.of();
        }

        String descLower = taskDescription != null ? taskDescription.toLowerCase() : "";
        List<CollaborationRecommendation> recommendations = new ArrayList<>();

        for (FixedEmployeeRegistry.FixedEmployeeDefinition def : allDefs) {
            double score = computeMatchScore(def, descLower);
            if (score > 0.3) {
                recommendations.add(new CollaborationRecommendation(
                    def.neuronId(),
                    def.name(),
                    score,
                    String.format("%s (%s)，具备能力: %s",
                        def.title(), def.departmentName(),
                        def.capabilities() != null ? String.join(", ", def.capabilities()) : "无"),
                    def.capabilities() != null ? def.capabilities() : List.of()
                ));
            }
        }

        recommendations.sort((a, b) -> Double.compare(b.matchScore(), a.matchScore()));
        return recommendations.stream().limit(5).collect(Collectors.toList());
    }

    private double computeMatchScore(FixedEmployeeRegistry.FixedEmployeeDefinition def, String descLower) {
        double score = 0.0;
        int matched = 0;

        if (def.capabilities() != null) {
            for (String cap : def.capabilities()) {
                if (descLower.contains(cap.toLowerCase())) {
                    matched++;
                }
            }
            if (!def.capabilities().isEmpty()) {
                score += 0.5 * ((double) matched / def.capabilities().size());
            }
        }

        if (def.roles() != null) {
            for (String role : def.roles()) {
                if (descLower.contains(role.toLowerCase())) {
                    matched++;
                }
            }
            if (!def.roles().isEmpty()) {
                score += 0.3 * ((double) matched / def.roles().size());
            }
        }

        if (def.department() != null && descLower.contains(def.department().toLowerCase())) {
            score += 0.2;
        }

        return Math.min(1.0, score);
    }

    private static class CollaborationSessionImpl implements CollaborationSession {
        private final String sessionId;
        private final String title;
        private final String description;
        private final CollaborationType type;
        private volatile CollaborationStatus status;
        private final String initiatorId;
        private final List<String> participantIds;
        private final List<CollaborationTask> tasks;
        private final Map<String, Object> context;
        
        private final Instant createdAt;
        private volatile Instant startedAt;
        private volatile Instant completedAt;
        private volatile CollaborationResult result;
        private volatile String cancellationReason;
        
        private final CountDownLatch completionLatch = new CountDownLatch(1);

        CollaborationSessionImpl(
                String sessionId,
                String title,
                String description,
                CollaborationType type,
                CollaborationStatus status,
                String initiatorId,
                List<String> participantIds,
                List<CollaborationTask> tasks,
                Map<String, Object> context) {
            this.sessionId = sessionId;
            this.title = title;
            this.description = description;
            this.type = type;
            this.status = status;
            this.initiatorId = initiatorId;
            this.participantIds = participantIds;
            this.tasks = new ArrayList<>(tasks);
            this.context = context;
            this.createdAt = Instant.now();
        }

        @Override
        public String getSessionId() { return sessionId; }

        @Override
        public String getTitle() { return title; }

        @Override
        public String getDescription() { return description; }

        @Override
        public CollaborationType getType() { return type; }

        @Override
        public CollaborationStatus getStatus() { return status; }

        @Override
        public String getInitiatorId() { return initiatorId; }

        @Override
        public List<String> getParticipantIds() { return new ArrayList<>(participantIds); }

        @Override
        public List<CollaborationTask> getTasks() { return new ArrayList<>(tasks); }

        @Override
        public Map<String, Object> getContext() { return context; }

        @Override
        public Instant getCreatedAt() { return createdAt; }

        @Override
        public Instant getStartedAt() { return startedAt; }

        @Override
        public Instant getCompletedAt() { return completedAt; }

        @Override
        public CollaborationResult getResult() { return result; }

        @Override
        public void addParticipant(String employeeId) {
            if (!participantIds.contains(employeeId)) {
                participantIds.add(employeeId);
            }
        }

        @Override
        public void removeParticipant(String employeeId) {
            participantIds.remove(employeeId);
        }

        @Override
        public void assignTask(String taskId, String employeeId) {
            for (int i = 0; i < tasks.size(); i++) {
                CollaborationTask t = tasks.get(i);
                if (t.taskId().equals(taskId)) {
                    tasks.set(i, new CollaborationTask(
                        t.taskId(),
                        t.name(),
                        t.description(),
                        employeeId,
                        t.status(),
                        t.order(),
                        t.dependencies(),
                        t.input(),
                        t.output(),
                        t.startedAt(),
                        t.completedAt()
                    ));
                    break;
                }
            }
        }

        @Override
        public void completeTask(String taskId, Map<String, Object> output) {
            for (int i = 0; i < tasks.size(); i++) {
                CollaborationTask t = tasks.get(i);
                if (t.taskId().equals(taskId)) {
                    tasks.set(i, new CollaborationTask(
                        t.taskId(),
                        t.name(),
                        t.description(),
                        t.assigneeId(),
                        CollaborationTask.TaskStatus.COMPLETED,
                        t.order(),
                        t.dependencies(),
                        t.input(),
                        output,
                        t.startedAt(),
                        Instant.now()
                    ));
                    break;
                }
            }
            
            updateTaskDependencies();
        }

        private void updateTaskDependencies() {
            Set<String> completedTaskIds = tasks.stream()
                .filter(t -> t.status() == CollaborationTask.TaskStatus.COMPLETED)
                .map(CollaborationTask::taskId)
                .collect(Collectors.toSet());
            
            for (int i = 0; i < tasks.size(); i++) {
                CollaborationTask t = tasks.get(i);
                if (t.status() == CollaborationTask.TaskStatus.PENDING) {
                    boolean allDepsCompleted = t.dependencies().isEmpty() ||
                        completedTaskIds.containsAll(t.dependencies());
                    
                    if (allDepsCompleted) {
                        tasks.set(i, new CollaborationTask(
                            t.taskId(),
                            t.name(),
                            t.description(),
                            t.assigneeId(),
                            CollaborationTask.TaskStatus.READY,
                            t.order(),
                            t.dependencies(),
                            t.input(),
                            t.output(),
                            Instant.now(),
                            null
                        ));
                    }
                }
            }
        }

        @Override
        public void start() {
            this.status = CollaborationStatus.IN_PROGRESS;
            this.startedAt = Instant.now();
            
            for (int i = 0; i < tasks.size(); i++) {
                CollaborationTask t = tasks.get(i);
                if (t.dependencies().isEmpty()) {
                    tasks.set(i, new CollaborationTask(
                        t.taskId(),
                        t.name(),
                        t.description(),
                        t.assigneeId(),
                        CollaborationTask.TaskStatus.READY,
                        t.order(),
                        t.dependencies(),
                        t.input(),
                        t.output(),
                        Instant.now(),
                        null
                    ));
                }
            }
        }

        @Override
        public void complete() {
            this.status = CollaborationStatus.COMPLETED;
            this.completedAt = Instant.now();
            
            Map<String, Object> deliverables = new HashMap<>();
            List<String> contributions = new ArrayList<>();
            Map<String, Double> participantScores = new HashMap<>();
            
            for (CollaborationTask t : tasks) {
                if (t.status() == CollaborationTask.TaskStatus.COMPLETED) {
                    deliverables.put(t.name(), t.output());
                    contributions.add(t.assigneeId() + ": " + t.name());
                    participantScores.merge(t.assigneeId(), 1.0, Double::sum);
                }
            }
            
            int totalTasks = tasks.size();
            long completedTasks = tasks.stream()
                .filter(t -> t.status() == CollaborationTask.TaskStatus.COMPLETED)
                .count();
            
            this.result = new CollaborationResult(
                completedTasks == totalTasks,
                "协作完成，共完成 " + completedTasks + "/" + totalTasks + " 个任务",
                deliverables,
                contributions,
                (double) completedTasks / totalTasks,
                participantScores
            );
            
            completionLatch.countDown();
        }

        @Override
        public void cancel(String reason) {
            this.status = CollaborationStatus.CANCELLED;
            this.cancellationReason = reason;
            this.completedAt = Instant.now();
            
            this.result = new CollaborationResult(
                false,
                "协作已取消: " + reason,
                Map.of(),
                List.of(),
                0.0,
                Map.of()
            );
            
            completionLatch.countDown();
        }

        CountDownLatch getCompletionLatch() {
            return completionLatch;
        }
    }
}
