package com.livingagent.core.employee.sync;

import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.employee.impl.DigitalEmployee;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.neuron.NeuronState;
import com.livingagent.core.util.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EmployeeStateSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(EmployeeStateSynchronizer.class);

    private final EmployeeService employeeService;
    private final NeuronRegistry neuronRegistry;

    /** ✅ 空闲超时阈值：员工超过此时间无任务活动则自动转为 IDLE（休息区） */
    @Value("${employee.idle-timeout-minutes:5}")
    private int idleTimeoutMinutes;

    /** ✅ 空闲检测间隔（毫秒） */
    private static final long IDLE_CHECK_INTERVAL_MS = 60_000; // 每分钟检查一次

    private final Map<String, EmployeeStatus> lastKnownEmployeeStatus = new ConcurrentHashMap<>();
    private final Map<String, NeuronState> lastKnownNeuronState = new ConcurrentHashMap<>();

    private static final Map<EmployeeStatus, NeuronState> EMPLOYEE_TO_NEURON_STATE = Map.ofEntries(
        Map.entry(EmployeeStatus.ACTIVE, NeuronState.RUNNING),
        Map.entry(EmployeeStatus.WORKING, NeuronState.PROCESSING),
        Map.entry(EmployeeStatus.IDLE, NeuronState.IDLE),
        Map.entry(EmployeeStatus.BUSY, NeuronState.PROCESSING),
        Map.entry(EmployeeStatus.OFFLINE, NeuronState.SUSPENDED),
        Map.entry(EmployeeStatus.DISABLED, NeuronState.STOPPED),
        Map.entry(EmployeeStatus.TERMINATED, NeuronState.STOPPED),
        Map.entry(EmployeeStatus.LEARNING, NeuronState.LEARNING),
        Map.entry(EmployeeStatus.EVOLVING, NeuronState.EVOLVING),
        Map.entry(EmployeeStatus.DORMANT, NeuronState.SUSPENDED),
        Map.entry(EmployeeStatus.ARCHIVED, NeuronState.STOPPED)
    );

    private static final Map<NeuronState, EmployeeStatus> NEURON_TO_EMPLOYEE_STATE = Map.ofEntries(
        Map.entry(NeuronState.RUNNING, EmployeeStatus.IDLE),      // ✅ RUNNING 状态对应空闲
        Map.entry(NeuronState.ACTIVE, EmployeeStatus.ACTIVE),
        Map.entry(NeuronState.PROCESSING, EmployeeStatus.WORKING), // ✅ PROCESSING 对应工作中
        Map.entry(NeuronState.IDLE, EmployeeStatus.IDLE),          // ✅ IDLE 对应休息中
        Map.entry(NeuronState.LEARNING, EmployeeStatus.LEARNING),
        Map.entry(NeuronState.EVOLVING, EmployeeStatus.EVOLVING),
        Map.entry(NeuronState.SUSPENDED, EmployeeStatus.OFFLINE),
        Map.entry(NeuronState.STOPPED, EmployeeStatus.OFFLINE),
        Map.entry(NeuronState.ERROR, EmployeeStatus.DISABLED),
        Map.entry(NeuronState.INITIALIZING, EmployeeStatus.ACTIVE),
        Map.entry(NeuronState.CREATED, EmployeeStatus.ACTIVE)
    );

    public EmployeeStateSynchronizer(EmployeeService employeeService, NeuronRegistry neuronRegistry) {
        this.employeeService = employeeService;
        this.neuronRegistry = neuronRegistry;
    }

    public void syncEmployeeToNeuron(String employeeId) {
        employeeService.getEmployee(employeeId).ifPresent(emp -> {
            if (!emp.isDigital()) {
                return;
            }
            
            DigitalEmployee de = (DigitalEmployee) emp;
            String neuronId = de.getDigitalConfig().getNeuronId();
            
            neuronRegistry.get(neuronId).ifPresent(neuron -> {
                EmployeeStatus currentStatus = emp.getStatus();
                EmployeeStatus lastStatus = lastKnownEmployeeStatus.get(employeeId);
                
                if (currentStatus != lastStatus) {
                    NeuronState targetState = EMPLOYEE_TO_NEURON_STATE.getOrDefault(currentStatus, NeuronState.ACTIVE);
                    neuron.setState(targetState);
                    lastKnownEmployeeStatus.put(employeeId, currentStatus);
                    lastKnownNeuronState.put(neuronId, targetState);
                    
                    log.info("Synced employee {} status {} to neuron state {}", employeeId, currentStatus, targetState);
                }
            });
        });
    }

    public void syncNeuronToEmployee(String neuronId) {
        String employeeId = IdUtils.neuronToEmployeeId(neuronId);

        neuronRegistry.get(neuronId).ifPresent(neuron -> {
            employeeService.getEmployee(employeeId).ifPresent(emp -> {
                if (!emp.isDigital()) {
                    return;
                }

                DigitalEmployee de = (DigitalEmployee) emp;
                NeuronState currentState = neuron.getState();
                NeuronState lastState = lastKnownNeuronState.get(neuronId);

                if (currentState != lastState) {
                    EmployeeStatus targetStatus = NEURON_TO_EMPLOYEE_STATE.getOrDefault(currentState, EmployeeStatus.ACTIVE);
                    EmployeeStatus oldStatus = de.getStatus();

                    // ✅ 通过 updateStatus 持久化到数据库
                    employeeService.updateStatus(employeeId, targetStatus);

                    lastKnownNeuronState.put(neuronId, currentState);
                    lastKnownEmployeeStatus.put(employeeId, targetStatus);

                    log.info("Synced neuron {} state {} to employee status {} (persisted)", neuronId, currentState, targetStatus);
                }
            });
        });
    }

    public void syncAllEmployeesToNeurons() {
        employeeService.listDigitalEmployees().forEach(emp -> {
            if (emp.isDigital()) {
                syncEmployeeToNeuron(emp.getEmployeeId());
            }
        });
    }

    public void syncAllNeuronsToEmployees() {
        neuronRegistry.getAll().forEach(neuron -> {
            syncNeuronToEmployee(neuron.getId());
        });
    }

    @Scheduled(fixedRate = 30000)
    public void periodicSync() {
        log.debug("Running periodic state sync...");
        syncAllNeuronsToEmployees();
    }

    /**
     * ✅ 自动空闲检测：将长时间无任务活动的 ACTIVE/WORKING 员工转为 IDLE（休息区）
     * <p>
     * 检测逻辑：
     * - 状态为 ACTIVE 或 WORKING 的数字员工
     * - lastActiveAt 距今超过 idleTimeoutMinutes
     * - 自动转换为 IDLE，并通过神经元同步推送
     * </p>
     */
    @Scheduled(fixedRateString = "${employee.idle-check-interval-ms:60000}", initialDelay = 120_000)
    public void autoIdleDetection() {
        Duration idleThreshold = Duration.ofMinutes(idleTimeoutMinutes);
        Instant now = Instant.now();
        int transitionedCount = 0;

        try {
            var digitalEmployees = employeeService.listDigitalEmployees();
            for (Employee emp : digitalEmployees) {
                if (!emp.isDigital()) continue;

                DigitalEmployee de = (DigitalEmployee) emp;
                EmployeeStatus currentStatus = de.getStatus();

                // 只检测需要自动空闲的状态
                if (currentStatus != EmployeeStatus.ACTIVE && currentStatus != EmployeeStatus.WORKING) {
                    continue;
                }

                // 检查最后活动时间
                Instant lastActive = de.getLastActiveAt();
                if (lastActive == null) {
                    lastActive = de.getCreatedAt() != null ? de.getCreatedAt() : Instant.EPOCH;
                }

                Duration idleDuration = Duration.between(lastActive, now);
                if (idleDuration.compareTo(idleThreshold) >= 0) {
                    String employeeId = de.getEmployeeId();
                    EmployeeStatus oldStatus = currentStatus;

                    // ✅ 通过 updateStatus 持久化到数据库
                    employeeService.updateStatus(employeeId, EmployeeStatus.IDLE);
                    lastKnownEmployeeStatus.put(employeeId, EmployeeStatus.IDLE);

                    log.info("[AutoIdle] 员工 {} 空闲超过 {} 分钟 ({}), {} -> IDLE (休息区, 已持久化)",
                        employeeId, idleTimeoutMinutes, idleDuration, oldStatus);
                    transitionedCount++;

                    // 同步到神经元状态
                    syncEmployeeToNeuron(employeeId);
                }
            }

            if (transitionedCount > 0) {
                log.info("[AutoIdle] 本轮检测共将 {} 名员工转入休息区", transitionedCount);
            }
        } catch (Exception e) {
            log.warn("[AutoIdle] 空闲检测执行异常", e);
        }
    }

    public void forceSync(String employeeId) {
        syncEmployeeToNeuron(employeeId);
        
        employeeService.getEmployee(employeeId).ifPresent(emp -> {
            if (emp.isDigital()) {
                DigitalEmployee de = (DigitalEmployee) emp;
                String neuronId = de.getDigitalConfig().getNeuronId();
                syncNeuronToEmployee(neuronId);
            }
        });
    }

    public StateSyncStatus getSyncStatus(String employeeId) {
        Optional<Employee> empOpt = employeeService.getEmployee(employeeId);
        if (empOpt.isEmpty() || !empOpt.get().isDigital()) {
            return new StateSyncStatus(employeeId, null, null, false, "Not a digital employee");
        }
        
        DigitalEmployee de = (DigitalEmployee) empOpt.get();
        String neuronId = de.getDigitalConfig().getNeuronId();
        
        Optional<Neuron> neuronOpt = neuronRegistry.get(neuronId);
        if (neuronOpt.isEmpty()) {
            return new StateSyncStatus(employeeId, de.getStatus(), null, false, "Neuron not found");
        }
        
        Neuron neuron = neuronOpt.get();
        EmployeeStatus empStatus = de.getStatus();
        NeuronState neuronState = neuron.getState();
        
        boolean inSync = isStatusInSync(empStatus, neuronState);
        String message = inSync ? "States are synchronized" : "States are out of sync";
        
        return new StateSyncStatus(employeeId, empStatus, neuronState, inSync, message);
    }

    private boolean isStatusInSync(EmployeeStatus empStatus, NeuronState neuronState) {
        NeuronState expectedNeuronState = EMPLOYEE_TO_NEURON_STATE.get(empStatus);
        return expectedNeuronState == neuronState;
    }

    public record StateSyncStatus(
        String employeeId,
        EmployeeStatus employeeStatus,
        NeuronState neuronState,
        boolean inSync,
        String message
    ) {}
}
