package com.livingagent.core.tool.worktree;

import java.util.List;
import java.util.Optional;

public interface WorktreeManager {

    WorktreeEntry create(String name, String taskId, String baseRef);

    Optional<WorktreeEntry> get(String name);

    List<WorktreeEntry> getAll();

    List<WorktreeEntry> getActive();

    WorktreeEntry bindTask(String name, String taskId);

    ExecutionResult run(String name, String command);

    CloseoutResult closeout(String name, CloseoutAction action, String reason, boolean completeTask);

    boolean exists(String name);

    void cleanAll();

    enum CloseoutAction {
        KEEP,
        REMOVE
    }

    record ExecutionResult(boolean success, String output, int exitCode) {
        public static ExecutionResult ok(String output) {
            return new ExecutionResult(true, output, 0);
        }
        public static ExecutionResult fail(String output, int exitCode) {
            return new ExecutionResult(false, output, exitCode);
        }
    }

    record CloseoutResult(boolean success, String message, CloseoutAction action) {}
}
