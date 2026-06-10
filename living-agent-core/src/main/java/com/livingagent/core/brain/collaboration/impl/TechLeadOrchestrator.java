package com.livingagent.core.brain.collaboration.impl;

import com.livingagent.core.autonomy.CodeReviewWorkflowService;
import com.livingagent.core.autonomy.CodeReviewWorkflowService.ReviewStage;
import com.livingagent.core.autonomy.TaskMetadataKeys;
import com.livingagent.core.brain.collaboration.LeadOrchestrator;
import com.livingagent.core.brain.collaboration.TeammateAssignment;
import com.livingagent.core.brain.collaboration.TeammateRole;
import com.livingagent.core.brain.impl.TechBrain;
import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.planner.dag.DagTask;
import com.livingagent.core.planner.dag.DagTaskStatus;
import com.livingagent.core.planner.dag.TaskDagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TechLeadOrchestrator implements LeadOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TechLeadOrchestrator.class);

    private final TaskDagService taskDagService;
    private final ChannelManager channelManager;
    private final CodeReviewWorkflowService codeReviewWorkflowService;
    private final Map<String, TeammateRole> teammates = new ConcurrentHashMap<>();
    private final Map<String, TeammateAssignment> assignments = new ConcurrentHashMap<>();

    private static final List<TeammateRole> DEFAULT_TEAMMATES = List.of(
        TeammateRole.of("code-reviewer", "代码审查员", "channel://tech/code-review",
            "employee://digital/tech/code-reviewer/001"),
        TeammateRole.of("architect", "架构师", "channel://tech/architecture",
            "employee://digital/tech/architect/001"),
        TeammateRole.of("frontend-dev", "前端工程师", "channel://tech/frontend",
            "employee://digital/tech/frontend-dev/001"),
        TeammateRole.of("backend-dev", "后端工程师", "channel://tech/backend",
            "employee://digital/tech/backend-dev/001")
    );

    public TechLeadOrchestrator(TaskDagService taskDagService, ChannelManager channelManager) {
        this(taskDagService, channelManager, null);
    }

    public TechLeadOrchestrator(TaskDagService taskDagService, ChannelManager channelManager,
                                CodeReviewWorkflowService codeReviewWorkflowService) {
        this.taskDagService = taskDagService;
        this.channelManager = channelManager;
        this.codeReviewWorkflowService = codeReviewWorkflowService;
        for (TeammateRole role : DEFAULT_TEAMMATES) {
            teammates.put(role.name(), role);
        }
        ensureChannels();
    }

    private void ensureChannels() {
        for (TeammateRole role : teammates.values()) {
            if (!channelManager.exists(role.channelId())) {
                channelManager.create(role.channelId(), Channel.ChannelType.UNICAST);
                log.info("Created channel for teammate: {} -> {}", role.name(), role.channelId());
            }
        }
        String leadInbox = "channel://tech/lead/inbox";
        if (!channelManager.exists(leadInbox)) {
            channelManager.create(leadInbox, Channel.ChannelType.BROADCAST);
            log.info("Created lead inbox channel: {}", leadInbox);
        }
    }

    @Override
    public String getLeadBrainId() {
        return TechBrain.ID;
    }

    @Override
    public List<TeammateRole> getAvailableTeammates() {
        return new ArrayList<>(teammates.values());
    }

    @Override
    public TeammateAssignment assignTask(String taskId, String teammateNeuronId) {
        Optional<DagTask> taskOpt = taskDagService.getTask(taskId);
        if (taskOpt.isEmpty()) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }

        DagTask task = taskOpt.get();
        TeammateRole role = teammates.values().stream()
            .filter(r -> r.neuronId().equals(teammateNeuronId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Teammate not found: " + teammateNeuronId));

        return assignTask(task, role);
    }

    @Override
    public TeammateAssignment assignTask(DagTask task, TeammateRole teammate) {
        TeammateAssignment assignment = TeammateAssignment.assign(
            teammate.neuronId(), task.id(), teammate.channelId());
        assignments.put(task.id(), assignment);

        taskDagService.claimTask(task.id(), teammate.neuronId());

        ChannelMessage assignMsg = ChannelMessage.text(
            teammate.channelId(),
            getLeadBrainId(),
            teammate.channelId(),
            "task_assign_" + task.id(),
            "任务分配: " + task.subject() + "\n描述: " + task.description()
        );
        assignMsg.addMetadata("task_id", task.id());
        assignMsg.addMetadata("assignment_id", assignment.assignmentId());
        assignMsg.addMetadata("type", "task_assignment");
        channelManager.publish(teammate.channelId(), assignMsg);

        log.info("Assigned task #{} to teammate {} ({})", task.id(), teammate.name(), teammate.neuronId());
        return assignment;
    }

    @Override
    public List<TeammateAssignment> getActiveAssignments() {
        return assignments.values().stream()
            .filter(a -> a.status() == TeammateAssignment.AssignmentStatus.ASSIGNED
                || a.status() == TeammateAssignment.AssignmentStatus.IN_PROGRESS)
            .toList();
    }

    @Override
    public Optional<TeammateAssignment> getAssignment(String taskId) {
        return Optional.ofNullable(assignments.get(taskId));
    }

    @Override
    public void completeTask(String taskId, String result) {
        TeammateAssignment assignment = assignments.get(taskId);
        if (assignment != null) {
            assignments.put(taskId, assignment.withStatus(TeammateAssignment.AssignmentStatus.COMPLETED));
        }
        taskDagService.updateTaskStatus(taskId, DagTaskStatus.COMPLETED);
        log.info("Task #{} completed by {}", taskId, assignment != null ? assignment.teammateNeuronId() : "unknown");
    }

    @Override
    public void failTask(String taskId, String error) {
        TeammateAssignment assignment = assignments.get(taskId);
        if (assignment != null) {
            assignments.put(taskId, assignment.withStatus(TeammateAssignment.AssignmentStatus.FAILED));
        }
        taskDagService.updateTaskStatus(taskId, DagTaskStatus.FAILED);
        log.warn("Task #{} failed: {}", taskId, error);
    }

    @Override
    public void sendToTeammate(String teammateNeuronId, String message) {
        TeammateRole role = teammates.values().stream()
            .filter(r -> r.neuronId().equals(teammateNeuronId))
            .findFirst()
            .orElse(null);
        if (role == null) {
            log.warn("Teammate not found: {}", teammateNeuronId);
            return;
        }

        ChannelMessage msg = ChannelMessage.text(
            role.channelId(),
            getLeadBrainId(),
            role.channelId(),
            "msg_" + System.currentTimeMillis(),
            message
        );
        msg.addMetadata("type", "lead_message");
        channelManager.publish(role.channelId(), msg);
    }

    @Override
    public void broadcastToTeam(String message) {
        ChannelMessage broadcast = ChannelMessage.text(
            "channel://tech/team",
            getLeadBrainId(),
            "channel://tech/team",
            "broadcast_" + System.currentTimeMillis(),
            message
        );
        broadcast.addMetadata("type", "team_broadcast");

        for (TeammateRole role : teammates.values()) {
            channelManager.publish(role.channelId(), broadcast);
        }
        log.debug("Broadcast to team: {}", message.substring(0, Math.min(50, message.length())));
    }

    @Override
    public List<ChannelMessage> readFromTeam(String leadBrainId) {
        List<ChannelMessage> messages = new ArrayList<>();
        String leadInbox = "channel://tech/lead/inbox";
        Channel inbox = channelManager.get(leadInbox).orElse(null);
        if (inbox != null) {
            // Channel 系统会通过 onMessage 回调投递，此处返回空列表
            // 实际消息通过 Brain.process() 接收
        }
        return messages;
    }

    // ========== 代码审查循环驱动实现 ==========

    @Override
    public CodeReviewWorkflowService.ReviewState submitForReview(String taskId, String reviewerNeuronId) {
        if (codeReviewWorkflowService == null) {
            throw new IllegalStateException("CodeReviewWorkflowService not available");
        }
        var state = codeReviewWorkflowService.getByTaskId(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Review state not found for taskId=" + taskId));

        // CODE_SUBMITTED → ASSIGN_REVIEWER → REVIEWING
        state = codeReviewWorkflowService.advanceStage(taskId, ReviewStage.ASSIGN_REVIEWER, Map.of(
            TaskMetadataKeys.REVIEWER_EMPLOYEE_CODE, reviewerNeuronId));
        state = codeReviewWorkflowService.advanceStage(taskId, ReviewStage.REVIEWING, Map.of());

        // 通过 Channel 通知审查员工
        TeammateRole reviewer = teammates.values().stream()
            .filter(r -> r.neuronId().equals(reviewerNeuronId))
            .findFirst().orElse(null);
        if (reviewer != null) {
            ChannelMessage reviewMsg = ChannelMessage.text(
                reviewer.channelId(), getLeadBrainId(), reviewer.channelId(),
                "review_request_" + taskId,
                "代码审查请求: 任务 " + taskId + " 已提交代码，请审查"
            );
            reviewMsg.addMetadata("task_id", taskId);
            reviewMsg.addMetadata("type", "review_request");
            channelManager.publish(reviewer.channelId(), reviewMsg);
        }

        log.info("Task {} submitted for review by {}", taskId, reviewerNeuronId);
        return state;
    }

    @Override
    public CodeReviewWorkflowService.ReviewState requestChanges(String taskId, List<String> findings) {
        if (codeReviewWorkflowService == null) {
            throw new IllegalStateException("CodeReviewWorkflowService not available");
        }
        var state = codeReviewWorkflowService.requestChanges(taskId, findings, Map.of());

        // 通知开发员工修改代码
        String developerNeuronId = state.developerEmployeeCode();
        if (developerNeuronId != null) {
            TeammateRole developer = teammates.values().stream()
                .filter(r -> r.neuronId().equals(developerNeuronId))
                .findFirst().orElse(null);
            if (developer != null) {
                ChannelMessage reviseMsg = ChannelMessage.text(
                    developer.channelId(), getLeadBrainId(), developer.channelId(),
                    "revise_request_" + taskId,
                    "代码需要修改: 任务 " + taskId + "\n问题: " + String.join("; ", findings)
                );
                reviseMsg.addMetadata("task_id", taskId);
                reviseMsg.addMetadata("type", "revise_request");
                channelManager.publish(developer.channelId(), reviseMsg);
            }
        }

        log.info("Changes requested for task {} (round {}): {} findings", taskId, state.reviewRound(), findings.size());
        return state;
    }

    @Override
    public CodeReviewWorkflowService.ReviewState resubmitCode(String taskId) {
        if (codeReviewWorkflowService == null) {
            throw new IllegalStateException("CodeReviewWorkflowService not available");
        }
        var state = codeReviewWorkflowService.getByTaskId(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Review state not found for taskId=" + taskId));

        // DEVELOPER_REVISING → CODE_RESUBMITTED → ASSIGN_REVIEWER
        state = codeReviewWorkflowService.advanceStage(taskId, ReviewStage.CODE_RESUBMITTED, Map.of());
        state = codeReviewWorkflowService.advanceStage(taskId, ReviewStage.ASSIGN_REVIEWER, Map.of());

        log.info("Task {} code resubmitted for review (round {})", taskId, state.reviewRound());
        return state;
    }

    @Override
    public CodeReviewWorkflowService.ReviewState approveCode(String taskId) {
        if (codeReviewWorkflowService == null) {
            throw new IllegalStateException("CodeReviewWorkflowService not available");
        }
        var state = codeReviewWorkflowService.approve(taskId, Map.of());

        // REVIEW_APPROVED → FINAL_SUMMARY
        state = codeReviewWorkflowService.advanceStage(taskId, ReviewStage.FINAL_SUMMARY, Map.of());

        // 更新任务状态为完成
        taskDagService.updateTaskStatus(taskId, DagTaskStatus.COMPLETED);

        log.info("Task {} code approved after {} review rounds", taskId, state.reviewRound());
        return state;
    }

    @Override
    public CodeReviewWorkflowService.ReviewState escalateReview(String taskId, String reason) {
        if (codeReviewWorkflowService == null) {
            throw new IllegalStateException("CodeReviewWorkflowService not available");
        }
        var state = codeReviewWorkflowService.escalate(taskId, reason, Map.of());

        // 广播升级消息给整个团队
        broadcastToTeam("代码审查升级: 任务 " + taskId + " 原因: " + reason);

        log.warn("Task {} review escalated: {}", taskId, reason);
        return state;
    }

    @Override
    public Optional<CodeReviewWorkflowService.ReviewState> getReviewState(String taskId) {
        if (codeReviewWorkflowService == null) {
            return Optional.empty();
        }
        return codeReviewWorkflowService.getByTaskId(taskId);
    }
}
