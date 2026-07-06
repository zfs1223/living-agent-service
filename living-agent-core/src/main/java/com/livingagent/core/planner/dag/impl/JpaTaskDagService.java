package com.livingagent.core.planner.dag.impl;

import com.livingagent.core.database.entity.DagTaskEntity;
import com.livingagent.core.database.entity.DagTaskEntity.DagTaskStatus;
import com.livingagent.core.database.repository.DagTaskRepository;
import com.livingagent.core.planner.dag.DagTask;
import com.livingagent.core.planner.dag.TaskDagService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * JPA 持久化版 TaskDagService - P2-3 修复。
 * 
 * <p>DAG 任务存储在数据库，重启后不丢失。
 * 任务依赖关系通过 JSON 数组存储（blockedBy/blocks）。</p>
 */
@Service
@Primary
public class JpaTaskDagService implements TaskDagService {

    private static final Logger log = LoggerFactory.getLogger(JpaTaskDagService.class);

    private final DagTaskRepository repository;
    private final AtomicInteger idCounter;

    public JpaTaskDagService(DagTaskRepository repository) {
        this.repository = repository;
        this.idCounter = new AtomicInteger(0);
    }

    @PostConstruct
    public void init() {
        try {
            Integer maxId = repository.findMaxTaskId();
            this.idCounter.set(maxId != null ? maxId : 0);
            log.info("Initialized JpaTaskDagService with max taskId={}", idCounter.get());
        } catch (Exception e) {
            log.warn("Failed to initialize taskId counter during startup (dag_tasks table may not exist yet): {}", e.getMessage());
            this.idCounter.set(0);
        }
    }

    @Override
    @Transactional
    public DagTask createTask(String subject, String description, List<String> blockedBy, String role) {
        String taskId = String.valueOf(idCounter.incrementAndGet());
        
        // 验证依赖是否存在
        List<String> validBlockedBy = blockedBy != null
            ? blockedBy.stream().filter(id -> repository.findByTaskId(id).isPresent()).toList()
            : List.of();

        DagTaskEntity entity = new DagTaskEntity();
        entity.setTaskId(taskId);
        entity.setSubject(subject);
        entity.setDescription(description);
        entity.setBlockedByList(validBlockedBy);
        entity.setRole(role);
        entity.setStatus(DagTaskStatus.PENDING);

        repository.save(entity);

        // 更新 blocker 的 blocks 列表
        for (String blockerId : validBlockedBy) {
            repository.findByTaskId(blockerId).ifPresent(blocker -> {
                List<String> blocksList = blocker.getBlocksList();
                if (!blocksList.contains(taskId)) {
                    blocksList.add(taskId);
                    blocker.setBlocksList(blocksList);
                    repository.save(blocker);
                }
            });
        }

        log.info("Created DAG task #{}: {} (blockedBy={}) [JPA]", taskId, subject, validBlockedBy);
        return toDagTask(entity);
    }

    @Override
    public Optional<DagTask> getTask(String taskId) {
        return repository.findByTaskId(taskId).map(this::toDagTask);
    }

    @Override
    public List<DagTask> getAllTasks() {
        return repository.findAll().stream()
            .map(this::toDagTask)
            .collect(Collectors.toList());
    }

    @Override
    public List<DagTask> getTasksByStatus(com.livingagent.core.planner.dag.DagTaskStatus status) {
        DagTaskStatus entityStatus = mapStatus(status);
        return repository.findByStatusOrderByCreatedAtAsc(entityStatus).stream()
            .map(this::toDagTask)
            .collect(Collectors.toList());
    }

    @Override
    public List<DagTask> getUnclaimedTasks() {
        return repository.findUnclaimedTasks().stream()
            .map(this::toDagTask)
            .collect(Collectors.toList());
    }

    @Override
    public List<DagTask> getUnclaimedTasks(String role) {
        return repository.findUnclaimedTasksByRole(role).stream()
            .map(this::toDagTask)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DagTask claimTask(String taskId, String assignee) {
        DagTaskEntity entity = repository.findByTaskId(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (entity.getStatus() != DagTaskStatus.PENDING) {
            throw new IllegalStateException("Task " + taskId + " is not PENDING (status=" + entity.getStatus() + ")");
        }
        if (entity.isBlocked()) {
            throw new IllegalStateException("Task " + taskId + " is blocked by: " + entity.getBlockedByList());
        }
        if (entity.getAssignee() != null && !entity.getAssignee().isEmpty()) {
            throw new IllegalStateException("Task " + taskId + " already claimed by: " + entity.getAssignee());
        }

        entity.setAssignee(assignee);
        entity.setStatus(DagTaskStatus.IN_PROGRESS);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        log.info("Task #{} claimed by {} [JPA]", taskId, assignee);
        return toDagTask(entity);
    }

    @Override
    @Transactional
    public DagTask updateTaskStatus(String taskId, com.livingagent.core.planner.dag.DagTaskStatus status) {
        DagTaskEntity entity = repository.findByTaskId(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        DagTaskStatus entityStatus = mapStatus(status);
        entity.setStatus(entityStatus);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        if (status == com.livingagent.core.planner.dag.DagTaskStatus.COMPLETED) {
            clearDependency(taskId);
            log.info("Task #{} completed, unblocking dependent tasks [JPA]", taskId);
        }

        return toDagTask(entity);
    }

    @Override
    @Transactional
    public DagTask bindWorktree(String taskId, String worktreeName) {
        DagTaskEntity entity = repository.findByTaskId(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        entity.setWorktree(worktreeName);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        log.info("Task #{} bound to worktree {} [JPA]", taskId, worktreeName);
        return toDagTask(entity);
    }

    @Override
    @Transactional
    public DagTask addDependency(String taskId, String blockedById) {
        DagTaskEntity task = repository.findByTaskId(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        DagTaskEntity blocker = repository.findByTaskId(blockedById)
            .orElseThrow(() -> new IllegalArgumentException("Blocker task not found: " + blockedById));

        List<String> blockedByList = task.getBlockedByList();
        if (!blockedByList.contains(blockedById)) {
            blockedByList.add(blockedById);
            task.setBlockedByList(blockedByList);
        }

        List<String> blocksList = blocker.getBlocksList();
        if (!blocksList.contains(taskId)) {
            blocksList.add(taskId);
            blocker.setBlocksList(blocksList);
        }

        repository.save(task);
        repository.save(blocker);

        return toDagTask(repository.findByTaskId(taskId).orElse(task));
    }

    @Override
    @Transactional
    public DagTask removeDependency(String taskId, String blockedById) {
        DagTaskEntity task = repository.findByTaskId(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        List<String> blockedByList = task.getBlockedByList();
        blockedByList.remove(blockedById);
        task.setBlockedByList(blockedByList);
        repository.save(task);

        repository.findByTaskId(blockedById).ifPresent(blocker -> {
            List<String> blocksList = blocker.getBlocksList();
            blocksList.remove(taskId);
            blocker.setBlocksList(blocksList);
            repository.save(blocker);
        });

        return toDagTask(repository.findByTaskId(taskId).orElse(task));
    }

    @Override
    public List<String> getReadyTaskIds() {
        return repository.findReadyTaskIds(DagTaskStatus.PENDING);
    }

    @Override
    public boolean hasCyclicDependency() {
        List<DagTaskEntity> allTasks = repository.findAll();
        Map<String, List<String>> dependencies = new HashMap<>();

        for (DagTaskEntity task : allTasks) {
            dependencies.put(task.getTaskId(), task.getBlockedByList());
        }

        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String taskId : dependencies.keySet()) {
            if (hasCycleDFS(taskId, dependencies, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional
    public void clearAll() {
        repository.deleteAll();
        idCounter.set(0);
        log.info("Cleared all DAG tasks [JPA]");
    }

    private void clearDependency(String completedId) {
        List<DagTaskEntity> dependentTasks = repository.findAll().stream()
            .filter(t -> t.getBlockedByList().contains(completedId))
            .toList();

        for (DagTaskEntity task : dependentTasks) {
            List<String> blockedBy = task.getBlockedByList();
            blockedBy.remove(completedId);
            task.setBlockedByList(blockedBy);
            task.setUpdatedAt(Instant.now());
            repository.save(task);
        }
    }

    private boolean hasCycleDFS(String taskId, Map<String, List<String>> dependencies,
            Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(taskId)) {
            return true;
        }
        if (visited.contains(taskId)) {
            return false;
        }

        visited.add(taskId);
        recursionStack.add(taskId);

        List<String> blockedBy = dependencies.get(taskId);
        if (blockedBy != null) {
            for (String blockerId : blockedBy) {
                if (hasCycleDFS(blockerId, dependencies, visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(taskId);
        return false;
    }

    private DagTask toDagTask(DagTaskEntity entity) {
        return new DagTask(
            entity.getTaskId(),
            entity.getSubject(),
            entity.getDescription(),
            mapStatusFromEntity(entity.getStatus()),
            entity.getBlockedByList(),
            entity.getBlocksList(),
            entity.getAssignee(),
            entity.getWorktree(),
            entity.getRole(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private DagTaskStatus mapStatus(com.livingagent.core.planner.dag.DagTaskStatus status) {
        return switch (status) {
            case PENDING -> DagTaskStatus.PENDING;
            case IN_PROGRESS -> DagTaskStatus.IN_PROGRESS;
            case COMPLETED -> DagTaskStatus.COMPLETED;
            case FAILED -> DagTaskStatus.FAILED;
            case CANCELLED -> DagTaskStatus.CANCELLED;
        };
    }

    private com.livingagent.core.planner.dag.DagTaskStatus mapStatusFromEntity(DagTaskStatus status) {
        return switch (status) {
            case PENDING -> com.livingagent.core.planner.dag.DagTaskStatus.PENDING;
            case IN_PROGRESS -> com.livingagent.core.planner.dag.DagTaskStatus.IN_PROGRESS;
            case COMPLETED -> com.livingagent.core.planner.dag.DagTaskStatus.COMPLETED;
            case FAILED -> com.livingagent.core.planner.dag.DagTaskStatus.FAILED;
            case CANCELLED -> com.livingagent.core.planner.dag.DagTaskStatus.CANCELLED;
        };
    }
}