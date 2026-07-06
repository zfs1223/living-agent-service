package com.livingagent.core.employee.neuron;

import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainContext;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.employee.claim.TaskClaimService;
import com.livingagent.core.employee.impl.DigitalEmployee;
import com.livingagent.core.evolution.engine.EvolutionDecisionEngine;
import com.livingagent.core.evolution.personality.BrainPersonality;
import com.livingagent.core.knowledge.KnowledgeBase;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.NeuronState;
import com.livingagent.core.neuron.impl.AbstractNeuron;
import com.livingagent.core.planner.dag.DagTask;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.provider.impl.ProviderFactory;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EmployeeNeuron extends AbstractNeuron {

    private static final Logger log = LoggerFactory.getLogger(EmployeeNeuron.class);

    private final DigitalEmployee employee;
    private volatile Brain delegateBrain;
    private BrainContext brainContext;
    private volatile Instant lastActiveAt;
    private volatile Instant lastClaimScanAt;
    private volatile Instant lastClaimSuccessAt;
    private KnowledgeBase knowledgeBase;
    private EvolutionDecisionEngine evolutionEngine;
    private TaskClaimService taskClaimService;

    private volatile ScheduledExecutorService claimScanScheduler;
    private ProviderFactory providerFactory;
    private ToolRegistry toolRegistry;

    private static final Duration CLAIM_SCAN_COOLDOWN = Duration.ofSeconds(30);
    private static final Duration IDLE_SCAN_THRESHOLD = Duration.ofSeconds(45);
    private static final long CLAIM_SCAN_INTERVAL_SECONDS = 15;

    public EmployeeNeuron(DigitalEmployee employee, Brain delegateBrain, List<Tool> tools) {
        super(
            employee.getDigitalConfig().getNeuronId(),
            employee.getName(),
            employee.getTitle(),
            employee.getDigitalConfig().getSubscribeChannels(),
            employee.getDigitalConfig().getPublishChannels(),
            tools != null ? tools : (delegateBrain != null ? delegateBrain.getTools() : List.of())
        );
        this.employee = employee;
        this.delegateBrain = delegateBrain;
        this.lastActiveAt = Instant.now();
        this.lastClaimScanAt = Instant.EPOCH;
        this.lastClaimSuccessAt = Instant.EPOCH;
        
        log.info("Created EmployeeNeuron: {} for employee {}", id, employee.getEmployeeId());
    }

    public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public void setEvolutionEngine(EvolutionDecisionEngine evolutionEngine) {
        this.evolutionEngine = evolutionEngine;
    }

    public void setTaskClaimService(TaskClaimService taskClaimService) {
        this.taskClaimService = taskClaimService;
    }

    public void setProviderFactory(ProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    public void setToolRegistry(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    protected void doStart(NeuronContext context) {
        log.info("Starting EmployeeNeuron: {} ({})", id, employee.getTitle());
        
        employee.setStatus(EmployeeStatus.ACTIVE);
        lastActiveAt = Instant.now();
        
        if (delegateBrain != null && context != null) {
            try {
                brainContext = createBrainContext(context);
                delegateBrain.start(brainContext);
                log.info("Delegate brain started for neuron: {}", id);
            } catch (Exception e) {
                log.error("Failed to start delegate brain for neuron: {}", id, e);
            }
        }
        
        for (String skill : employee.getSkills()) {
            addSkill(skill);
        }

        startClaimScanner();
    }

    @Override
    protected void doStop() {
        log.info("Stopping EmployeeNeuron: {}", id);
        
        employee.setStatus(EmployeeStatus.OFFLINE);
        
        stopClaimScanner();

        if (delegateBrain != null) {
            try {
                delegateBrain.stop();
            } catch (Exception e) {
                log.warn("Error stopping delegate brain for neuron: {}", id, e);
            }
        }
    }

    @Override
    protected void doProcessMessage(ChannelMessage message) {
        log.debug("EmployeeNeuron {} processing message from {}", id, message.getSourceChannelId());
        
        if (employee.getStatus() == EmployeeStatus.OFFLINE) {
            employee.setStatus(EmployeeStatus.ACTIVE);
            setState(NeuronState.RUNNING);
        }
        
        lastActiveAt = Instant.now();
        
        if (delegateBrain != null) {
            try {
                processWithBrain(message);
                employee.recordTask(true);
            } catch (Exception e) {
                log.error("Error processing message with brain for neuron: {}", id, e);
                employee.recordTask(false);
                setState(NeuronState.ERROR);
            }
        } else {
            log.warn("No delegate brain for neuron: {}, message will be ignored", id);
        }

        // 在每次消息处理后尝试自动认领，补齐 idle->scan 闭环（带冷却窗口避免高频扫描）
        maybeScanAndClaim();
    }

    private void processWithBrain(ChannelMessage message) {
        if (brainContext == null) {
            log.warn("BrainContext not initialized for neuron: {}", id);
            return;
        }

        ensureDelegateBrainProvider();
        
        delegateBrain.process(message);
        
        log.debug("EmployeeNeuron {} processed message", id);
    }

    private void ensureDelegateBrainProvider() {
        if (delegateBrain == null) {
            return;
        }

        if (delegateBrain instanceof com.livingagent.core.brain.impl.AbstractBrain ab) {
            if (ab.hasProvider()) {
                return;
            }

            if (providerFactory != null) {
                String brainId = delegateBrain.getId();
                try {
                    Provider provider = providerFactory.createForEmployee(
                        employee.getEmployeeId(), employee.getDepartmentId(), brainId);
                    if (provider != null) {
                        ab.updateProvider(provider);
                        log.info("EmployeeNeuron {} runtime兜底注入 Provider 成功: employeeId={}, departmentId={}, departmentBrainId={}, provider={}",
                            id, employee.getEmployeeId(), employee.getDepartmentId(), brainId, provider.name());
                    } else {
                        log.warn("EmployeeNeuron {} runtime兜底注入 Provider 失败: employeeId={}, departmentId={}, departmentBrainId={} 无法创建 (可能原因: 模型池配置缺失/协议不支持)",
                            id, employee.getEmployeeId(), employee.getDepartmentId(), brainId);
                    }
                } catch (Exception e) {
                    log.error("EmployeeNeuron {} runtime Provider 创建异常: brainId={}, error={}", id, brainId, e.getMessage());
                }
            } else {
                log.warn("EmployeeNeuron {} ProviderFactory 未配置，无法兜底注入 Provider", id);
            }
        }
    }

    private BrainContext createBrainContext(NeuronContext neuronContext) {
        BrainContext.Builder builder = BrainContext.builder()
            .brainId(delegateBrain != null ? delegateBrain.getId() : id)
            .department(delegateBrain != null ? delegateBrain.getDepartment() : employee.getDepartmentId())
            .sessionId(neuronContext.getSessionId())
            .channelManager(neuronContext.getChannelManager())
            .skillRegistry(neuronContext.getSkillRegistry())
            .employeeId(employee.getEmployeeId())
            .employeeCode(employee.getAuthId());

        if (providerFactory != null && delegateBrain instanceof com.livingagent.core.brain.impl.AbstractBrain) {
            String brainId = delegateBrain.getId();
            Provider provider = providerFactory.createForEmployee(
                employee.getEmployeeId(), employee.getDepartmentId(), brainId);
            if (provider != null) {
                builder.provider(provider);
                log.info("EmployeeNeuron {} 为 employeeId={}, departmentId={}, departmentBrainId={} 注入了 Provider: {}",
                    id, employee.getEmployeeId(), employee.getDepartmentId(), brainId, provider.name());
            } else {
                log.warn("EmployeeNeuron {} 无法为 employeeId={}, departmentId={}, departmentBrainId={} 创建 Provider",
                    id, employee.getEmployeeId(), employee.getDepartmentId(), brainId);
            }
        }

        if (toolRegistry != null) {
            builder.toolRegistry(toolRegistry);
        }

        if (knowledgeBase != null) {
            builder.knowledgeBase(knowledgeBase);
        }

        if (evolutionEngine != null) {
            builder.evolutionEngine(evolutionEngine);
        }

        if (delegateBrain instanceof com.livingagent.core.brain.impl.AbstractBrain ab) {
            BrainPersonality personality = ab.getPersonality();
            if (personality != null) {
                builder.personality(personality);
            }
        }

        return builder.build();
    }

    private void maybeScanAndClaim() {
        if (taskClaimService == null) {
            return;
        }

        if (employee.getStatus() == EmployeeStatus.BUSY || getState() == NeuronState.PROCESSING) {
            return;
        }

        Instant now = Instant.now();
        if (lastClaimScanAt != null && now.isBefore(lastClaimScanAt.plus(CLAIM_SCAN_COOLDOWN))) {
            return;
        }
        lastClaimScanAt = now;

        String role = normalizeRole(employee.getTitle());
        Optional<DagTask> claimed = taskClaimService.scanAndClaim(id, role);
        if (claimed.isPresent()) {
            DagTask task = claimed.get();
            employee.setStatus(EmployeeStatus.BUSY);
            setState(NeuronState.PROCESSING);
            lastClaimSuccessAt = now;
            log.info("EmployeeNeuron {} auto-claimed DAG task #{} ({})", id, task.id(), task.subject());
        } else {
            if (employee.getStatus() == EmployeeStatus.BUSY || employee.getStatus() == EmployeeStatus.WORKING) {
                employee.setStatus(EmployeeStatus.IDLE);  // ✅ 任务完成后进入休息区
            }
            if (getState() == NeuronState.PROCESSING) {
                setState(NeuronState.RUNNING);
            }
        }
    }

    private String normalizeRole(String title) {
        if (title == null) {
            return null;
        }
        String normalized = title.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toLowerCase(java.util.Locale.ROOT);
    }

    private void startClaimScanner() {
        if (taskClaimService == null) {
            return;
        }
        if (claimScanScheduler != null && !claimScanScheduler.isShutdown()) {
            return;
        }

        claimScanScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "employee-claim-scan-" + id.replace("://", "-").replace('/', '-'));
            t.setDaemon(true);
            return t;
        });

        claimScanScheduler.scheduleAtFixedRate(() -> {
            try {
                if (!isIdleForClaimScan()) {
                    return;
                }
                maybeScanAndClaim();
            } catch (Exception e) {
                log.warn("EmployeeNeuron {} claim scanner error: {}", id, e.getMessage());
            }
        }, CLAIM_SCAN_INTERVAL_SECONDS, CLAIM_SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);

        log.info("EmployeeNeuron {} started idle->scan claim loop (interval={}s)", id, CLAIM_SCAN_INTERVAL_SECONDS);
    }

    private void stopClaimScanner() {
        if (claimScanScheduler == null) {
            return;
        }
        claimScanScheduler.shutdown();
        try {
            if (!claimScanScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                claimScanScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            claimScanScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            claimScanScheduler = null;
        }
        log.info("EmployeeNeuron {} stopped idle->scan claim loop", id);
    }

    private boolean isIdleForClaimScan() {
        if (employee.getStatus() == EmployeeStatus.BUSY || getState() == NeuronState.PROCESSING) {
            return false;
        }

        Instant now = Instant.now();
        Instant activeAt = lastActiveAt == null ? Instant.EPOCH : lastActiveAt;
        Instant successAt = lastClaimSuccessAt == null ? Instant.EPOCH : lastClaimSuccessAt;

        boolean inactiveLongEnough = Duration.between(activeAt, now).compareTo(IDLE_SCAN_THRESHOLD) >= 0;
        boolean noRecentClaimSuccess = Duration.between(successAt, now).compareTo(CLAIM_SCAN_COOLDOWN) >= 0;

        return inactiveLongEnough && noRecentClaimSuccess;
    }

    public DigitalEmployee getEmployee() {
        return employee;
    }

    public Brain getDelegateBrain() {
        return delegateBrain;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void syncStateFromEmployee() {
        EmployeeStatus empStatus = employee.getStatus();
        NeuronState targetState = mapEmployeeStatusToNeuronState(empStatus);
        setState(targetState);
        log.debug("Synced neuron {} state from employee: {} -> {}", id, empStatus, targetState);
    }

    public void syncStateToEmployee() {
        NeuronState neuronState = getState();
        EmployeeStatus targetStatus = mapNeuronStateToEmployeeStatus(neuronState);
        employee.setStatus(targetStatus);
        log.debug("Synced employee {} state from neuron: {} -> {}", employee.getEmployeeId(), neuronState, targetStatus);
    }

    private NeuronState mapEmployeeStatusToNeuronState(EmployeeStatus status) {
        return switch (status) {
            case ACTIVE -> NeuronState.ACTIVE;              // ✅ 刚启动
            case WORKING, BUSY -> NeuronState.PROCESSING;   // ✅ 工作中/协作中
            case IDLE -> NeuronState.IDLE;                  // ✅ 空闲休息
            case OFFLINE, DORMANT -> NeuronState.SUSPENDED;  // 离线/休眠
            case DISABLED, TERMINATED -> NeuronState.STOPPED;
            case ARCHIVED -> NeuronState.STOPPED;
            case LEARNING -> NeuronState.LEARNING;
            case EVOLVING -> NeuronState.EVOLVING;
        };
    }

    private EmployeeStatus mapNeuronStateToEmployeeStatus(NeuronState state) {
        return switch (state) {
            case RUNNING -> EmployeeStatus.IDLE;           // ✅ RUNNING = 空闲运行
            case ACTIVE -> EmployeeStatus.ACTIVE;          // ✅ ACTIVE = 刚启动
            case PROCESSING -> EmployeeStatus.WORKING;     // ✅ WORKING = 执行任务
            case IDLE, SUSPENDED -> EmployeeStatus.IDLE;   // ✅ IDLE = 休息中
            case STOPPED -> EmployeeStatus.OFFLINE;
            case ERROR -> EmployeeStatus.DISABLED;
            case INITIALIZING, CREATED -> EmployeeStatus.ACTIVE;
            case LEARNING -> EmployeeStatus.LEARNING;
            case EVOLVING -> EmployeeStatus.EVOLVING;
        };
    }

    public static EmployeeNeuron create(DigitalEmployee employee, BrainRegistry brainRegistry, List<Tool> tools) {
        Brain brain = null;

        if (brainRegistry != null) {
            String department = employee.getDepartmentId();
            if (department == null || department.isEmpty()) {
                department = DEPARTMENT_NAME_TO_CODE.getOrDefault(employee.getDepartment(), employee.getDepartment());
            }
            Optional<Brain> brainOpt = brainRegistry.getByDepartment(department);
            if (brainOpt.isPresent()) {
                brain = brainOpt.get();
            } else {
                log.debug("No brain found for department '{}' of employee '{}' at creation time, will bind later",
                    department, employee.getEmployeeId());
            }
        }

        return new EmployeeNeuron(employee, brain, tools);
    }

    /**
     * 延迟绑定部门大脑。
     * 用于 Brain 注册完成后，为已创建但未绑定大脑的员工补充绑定。
     * 适用于固定数字员工（FIXED origin）和 LLM 动态创建员工（EVOLVED origin）。
     * 个人新建员工（PERSONAL origin）由用户手动指定大脑，不自动绑定。
     */
    private static final Map<String, String> DEPARTMENT_NAME_TO_CODE = Map.ofEntries(
        Map.entry("技术部", "tech"),
        Map.entry("财务部", "finance"),
        Map.entry("运营部", "ops"),
        Map.entry("销售部", "sales"),
        Map.entry("人力资源", "hr"),
        Map.entry("人力资源部", "hr"),
        Map.entry("客服部", "cs"),
        Map.entry("行政部", "admin"),
        Map.entry("法务部", "legal"),
        Map.entry("跨部门协调", "core"),
        Map.entry("跨部门", "core"),
        Map.entry("综合管理", "core")  // dept_main 对应 MainBrain (department=core)
    );

    // departmentId 后缀到 Brain department 的特殊映射
    private static final Map<String, String> DEPARTMENT_ID_TO_BRAIN_DEPT = Map.of(
        "main", "core"  // dept_main -> MainBrain (department=core)
    );

    public boolean bindBrain(BrainRegistry brainRegistry) {
        if (this.delegateBrain != null) {
            return false; // already bound
        }
        if (brainRegistry == null) {
            return false;
        }
        String department = employee.getDepartmentId();
        if (department == null || department.isEmpty()) {
            // departmentId 为空时，从 department 中文名映射到 code
            department = DEPARTMENT_NAME_TO_CODE.getOrDefault(employee.getDepartment(), employee.getDepartment());
            log.info("bindBrain: departmentId is null, mapped department='{}' -> code='{}'", employee.getDepartment(), department);
        } else {
            // 去掉 dept_ 前缀（数据库使用 dept_tech 格式，Brain 注册使用 tech 格式）
            if (department.startsWith("dept_")) {
                department = department.substring(5);
                log.debug("bindBrain: normalized departmentId '{}' -> '{}'", employee.getDepartmentId(), department);
            }
            // 特殊映射：main -> core（dept_main 对应 MainBrain）
            department = DEPARTMENT_ID_TO_BRAIN_DEPT.getOrDefault(department, department);
        }
        Optional<Brain> brainOpt = brainRegistry.getByDepartment(department);
        if (brainOpt.isPresent()) {
            this.delegateBrain = brainOpt.get();
            log.info("Bound brain '{}' to employee '{}' (department={})",
                delegateBrain.getName(), employee.getEmployeeId(), department);
            return true;
        }
        log.warn("bindBrain: no brain found for employeeId={}, departmentId='{}', department='{}', available: {}",
            employee.getEmployeeId(), employee.getDepartmentId(), employee.getDepartment(),
            brainRegistry.getAll().stream().map(b -> b.getDepartment() + "=" + b.getName()).toList());
        return false;
    }
}
