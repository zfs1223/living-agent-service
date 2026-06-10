package com.livingagent.core.brain.collaboration;

import com.livingagent.core.planner.dag.DagTask;

public record TeammateAssignment(
    String assignmentId,
    String teammateNeuronId,
    String taskId,
    String channelId,
    AssignmentStatus status,
    long assignedAt
) {
    public enum AssignmentStatus {
        ASSIGNED,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public static TeammateAssignment assign(String teammateNeuronId, String taskId, String channelId) {
        return new TeammateAssignment(
            "assign_" + System.currentTimeMillis(),
            teammateNeuronId,
            taskId,
            channelId,
            AssignmentStatus.ASSIGNED,
            System.currentTimeMillis()
        );
    }

    public TeammateAssignment withStatus(AssignmentStatus status) {
        return new TeammateAssignment(assignmentId, teammateNeuronId, taskId, channelId, status, assignedAt);
    }
}
