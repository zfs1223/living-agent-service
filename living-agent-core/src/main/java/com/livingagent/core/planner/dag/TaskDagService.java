package com.livingagent.core.planner.dag;

import java.util.List;
import java.util.Optional;

public interface TaskDagService {

    DagTask createTask(String subject, String description, List<String> blockedBy, String role);

    Optional<DagTask> getTask(String taskId);

    List<DagTask> getAllTasks();

    List<DagTask> getTasksByStatus(DagTaskStatus status);

    List<DagTask> getUnclaimedTasks();

    List<DagTask> getUnclaimedTasks(String role);

    DagTask claimTask(String taskId, String assignee);

    DagTask updateTaskStatus(String taskId, DagTaskStatus status);

    DagTask bindWorktree(String taskId, String worktreeName);

    DagTask addDependency(String taskId, String blockedById);

    DagTask removeDependency(String taskId, String blockedById);

    List<String> getReadyTaskIds();

    boolean hasCyclicDependency();

    void clearAll();
}
