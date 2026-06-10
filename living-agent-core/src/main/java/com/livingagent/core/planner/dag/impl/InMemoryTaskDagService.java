package com.livingagent.core.planner.dag.impl;

import com.livingagent.core.planner.dag.DagTask;
import com.livingagent.core.planner.dag.DagTaskStatus;
import com.livingagent.core.planner.dag.TaskDagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class InMemoryTaskDagService implements TaskDagService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTaskDagService.class);

    private final Map<String, DagTask> tasks = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private final ReentrantLock claimLock = new ReentrantLock();

    @Override
    public DagTask createTask(String subject, String description, List<String> blockedBy, String role) {
        String taskId = String.valueOf(idCounter.incrementAndGet());
        List<String> validBlockedBy = blockedBy != null
            ? blockedBy.stream().filter(id -> tasks.containsKey(id)).toList()
            : List.of();

        DagTask task = DagTask.create(taskId, subject, description)
            .withBlockedBy(validBlockedBy)
            .withRole(role);

        tasks.put(taskId, task);

        for (String blockerId : validBlockedBy) {
            DagTask blocker = tasks.get(blockerId);
            if (blocker != null) {
                List<String> newBlocks = new ArrayList<>(blocker.blocks());
                if (!newBlocks.contains(taskId)) {
                    newBlocks.add(taskId);
                    tasks.put(blockerId, blocker.withBlocks(newBlocks));
                }
            }
        }

        log.info("Created DAG task #{}: {} (blockedBy={})", taskId, subject, validBlockedBy);
        return task;
    }

    @Override
    public Optional<DagTask> getTask(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public List<DagTask> getAllTasks() {
        return new ArrayList<>(tasks.values());
    }

    @Override
    public List<DagTask> getTasksByStatus(DagTaskStatus status) {
        return tasks.values().stream()
            .filter(t -> t.status() == status)
            .collect(Collectors.toList());
    }

    @Override
    public List<DagTask> getUnclaimedTasks() {
        return tasks.values().stream()
            .filter(t -> t.isClaimable(null))
            .collect(Collectors.toList());
    }

    @Override
    public List<DagTask> getUnclaimedTasks(String role) {
        return tasks.values().stream()
            .filter(t -> t.isClaimable(role))
            .collect(Collectors.toList());
    }

    @Override
    public DagTask claimTask(String taskId, String assignee) {
        claimLock.lock();
        try {
            DagTask task = tasks.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("Task not found: " + taskId);
            }
            if (task.status() != DagTaskStatus.PENDING) {
                throw new IllegalStateException("Task " + taskId + " is not PENDING (status=" + task.status() + ")");
            }
            if (task.isBlocked()) {
                throw new IllegalStateException("Task " + taskId + " is blocked by: " + task.blockedBy());
            }
            if (task.assignee() != null && !task.assignee().isEmpty()) {
                throw new IllegalStateException("Task " + taskId + " already claimed by: " + task.assignee());
            }

            DagTask claimed = task.withAssignee(assignee).withStatus(DagTaskStatus.IN_PROGRESS);
            tasks.put(taskId, claimed);
            log.info("Task #{} claimed by {}", taskId, assignee);
            return claimed;
        } finally {
            claimLock.unlock();
        }
    }

    @Override
    public DagTask updateTaskStatus(String taskId, DagTaskStatus status) {
        DagTask task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }

        DagTask updated = task.withStatus(status);
        tasks.put(taskId, updated);

        if (status == DagTaskStatus.COMPLETED) {
            clearDependency(taskId);
            log.info("Task #{} completed, unblocking dependent tasks", taskId);
        }

        return updated;
    }

    @Override
    public DagTask bindWorktree(String taskId, String worktreeName) {
        DagTask task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        DagTask updated = task.withWorktree(worktreeName);
        tasks.put(taskId, updated);
        log.info("Task #{} bound to worktree {}", taskId, worktreeName);
        return updated;
    }

    @Override
    public DagTask addDependency(String taskId, String blockedById) {
        DagTask task = tasks.get(taskId);
        DagTask blocker = tasks.get(blockedById);
        if (task == null || blocker == null) {
            throw new IllegalArgumentException("Task not found");
        }

        List<String> newBlockedBy = new ArrayList<>(task.blockedBy());
        if (!newBlockedBy.contains(blockedById)) {
            newBlockedBy.add(blockedById);
        }

        List<String> newBlocks = new ArrayList<>(blocker.blocks());
        if (!newBlocks.contains(taskId)) {
            newBlocks.add(taskId);
        }

        tasks.put(taskId, task.withBlockedBy(newBlockedBy));
        tasks.put(blockedById, blocker.withBlocks(newBlocks));

        return tasks.get(taskId);
    }

    @Override
    public DagTask removeDependency(String taskId, String blockedById) {
        DagTask task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }

        List<String> newBlockedBy = new ArrayList<>(task.blockedBy());
        newBlockedBy.remove(blockedById);
        tasks.put(taskId, task.withBlockedBy(newBlockedBy));

        DagTask blocker = tasks.get(blockedById);
        if (blocker != null) {
            List<String> newBlocks = new ArrayList<>(blocker.blocks());
            newBlocks.remove(taskId);
            tasks.put(blockedById, blocker.withBlocks(newBlocks));
        }

        return tasks.get(taskId);
    }

    @Override
    public List<String> getReadyTaskIds() {
        return tasks.values().stream()
            .filter(t -> t.status() == DagTaskStatus.PENDING)
            .filter(t -> !t.isBlocked())
            .map(DagTask::id)
            .collect(Collectors.toList());
    }

    @Override
    public boolean hasCyclicDependency() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String taskId : tasks.keySet()) {
            if (hasCycleDFS(taskId, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void clearAll() {
        tasks.clear();
        idCounter.set(0);
    }

    private void clearDependency(String completedId) {
        for (Map.Entry<String, DagTask> entry : tasks.entrySet()) {
            DagTask task = entry.getValue();
            if (task.blockedBy().contains(completedId)) {
                List<String> newBlockedBy = new ArrayList<>(task.blockedBy());
                newBlockedBy.remove(completedId);
                tasks.put(entry.getKey(), task.withBlockedBy(newBlockedBy));
            }
        }
    }

    private boolean hasCycleDFS(String taskId, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(taskId)) {
            return true;
        }
        if (visited.contains(taskId)) {
            return false;
        }

        visited.add(taskId);
        recursionStack.add(taskId);

        DagTask task = tasks.get(taskId);
        if (task != null) {
            for (String blockedById : task.blockedBy()) {
                if (hasCycleDFS(blockedById, visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(taskId);
        return false;
    }
}
