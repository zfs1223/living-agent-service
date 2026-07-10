package com.livingagent.core.employee.claim;

import com.livingagent.core.planner.dag.DagTask;
import com.livingagent.core.planner.dag.TaskDagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class TaskClaimService {

    private static final Logger log = LoggerFactory.getLogger(TaskClaimService.class);

    private final TaskDagService taskDagService;

    /** O3: 领取冷却记录（neuronId → 上次领取时间），用于动态窗口期 */
    private final Map<String, Instant> lastClaimTime = new ConcurrentHashMap<>();

    /** O3: 基础冷却期（毫秒） */
    private long baseCooldownMs = 5000;

    /** O3: 最大冷却期上限（毫秒） */
    private long maxCooldownMs = 60_000;

    public TaskClaimService(TaskDagService taskDagService) {
        this.taskDagService = taskDagService;
    }

    /**
     * O3: 设置动态窗口期参数。
     * @param baseCooldownMs  基础冷却期（待办少时使用，毫秒）
     * @param maxCooldownMs   最大冷却期上限（毫秒）
     */
    public void setCooldownParams(long baseCooldownMs, long maxCooldownMs) {
        this.baseCooldownMs = baseCooldownMs;
        this.maxCooldownMs = maxCooldownMs;
    }

    /**
     * O3: 动态计算当前冷却期。
     * 待办越多 → 冷却期越短（鼓励快速领取）；待办越少 → 冷却期越长（避免频繁空轮询）。
     * 公式：cooldown = maxCooldownMs - (maxCooldownMs - baseCooldownMs) * min(1.0, pendingCount / 50)
     */
    public long getDynamicCooldownMs(String role) {
        int pendingCount = taskDagService.getUnclaimedTasks(role).size();
        double factor = Math.min(1.0, (double) pendingCount / 50.0);
        long cooldown = (long) (maxCooldownMs - (maxCooldownMs - baseCooldownMs) * factor);
        log.debug("Dynamic cooldown for role '{}': {}ms (pending={}tasks)", role, cooldown, pendingCount);
        return cooldown;
    }

    /**
     * O3: 检查是否在冷却窗口内。
     */
    public boolean isInCooldown(String neuronId, String role) {
        Instant lastTime = lastClaimTime.get(neuronId);
        if (lastTime == null) return false;
        long elapsed = Instant.now().toEpochMilli() - lastTime.toEpochMilli();
        return elapsed < getDynamicCooldownMs(role);
    }

    public Optional<DagTask> scanAndClaim(String neuronId, String role) {
        // O3: 动态窗口期检查
        if (isInCooldown(neuronId, role)) {
            log.debug("Neuron {} in cooldown window for role {}", neuronId, role);
            return Optional.empty();
        }

        List<DagTask> unclaimed = taskDagService.getUnclaimedTasks(role);
        if (unclaimed.isEmpty()) {
            return Optional.empty();
        }

        DagTask task = unclaimed.get(0);
        try {
            DagTask claimed = taskDagService.claimTask(task.id(), neuronId);
            lastClaimTime.put(neuronId, Instant.now());
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
