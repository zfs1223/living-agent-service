package com.livingagent.core.employee.claim;

import com.livingagent.core.planner.dag.DagTask;
import com.livingagent.core.planner.dag.TaskDagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class TaskClaimService {

    private static final Logger log = LoggerFactory.getLogger(TaskClaimService.class);

    private final TaskDagService taskDagService;

    public TaskClaimService(TaskDagService taskDagService) {
        this.taskDagService = taskDagService;
    }

    public Optional<DagTask> scanAndClaim(String neuronId, String role) {
        List<DagTask> unclaimed = taskDagService.getUnclaimedTasks(role);
        if (unclaimed.isEmpty()) {
            return Optional.empty();
        }

        DagTask task = unclaimed.get(0);
        try {
            DagTask claimed = taskDagService.claimTask(task.id(), neuronId);
            log.info("Neuron {} auto-claimed task #{}: {}", neuronId, task.id(), task.subject());
            return Optional.of(claimed);
        } catch (Exception e) {
            log.warn("Neuron {} failed to claim task #{}: {}", neuronId, task.id(), e.getMessage());
            return Optional.empty();
        }
    }

    public List<DagTask> scanAvailable(String role) {
        return taskDagService.getUnclaimedTasks(role);
    }

    public boolean hasClaimableTasks(String role) {
        return !taskDagService.getUnclaimedTasks(role).isEmpty();
    }
}
