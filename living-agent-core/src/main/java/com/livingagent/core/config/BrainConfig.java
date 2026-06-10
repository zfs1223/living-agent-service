package com.livingagent.core.config;

import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.impl.*;
import com.livingagent.core.brain.compact.ContextCompactor;
import com.livingagent.core.brain.compact.impl.HybridContextCompactor;
import com.livingagent.core.brain.collaboration.LeadOrchestrator;
import com.livingagent.core.brain.collaboration.impl.TechLeadOrchestrator;
import com.livingagent.core.autonomy.CodeReviewWorkflowService;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.evolution.engine.DefaultEvolutionDecisionEngine;
import com.livingagent.core.evolution.engine.EvolutionDecisionEngine;
import com.livingagent.core.evolution.memory.EvolutionMemoryGraph;
import com.livingagent.core.evolution.circuitbreaker.EvolutionCircuitBreaker;
import com.livingagent.core.model.pool.BrainModelResolver;
import com.livingagent.core.model.pool.BrainModelAssigner;
import com.livingagent.core.model.pool.ModelPoolManager;
import com.livingagent.core.model.selector.*;
import com.livingagent.core.planner.dag.TaskDagService;
import com.livingagent.core.planner.dag.impl.InMemoryTaskDagService;
import com.livingagent.core.approval.plan.PlanApprovalService;
import com.livingagent.core.approval.plan.impl.InMemoryPlanApprovalService;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.tool.worktree.WorktreeManager;
import com.livingagent.core.tool.worktree.impl.GitWorktreeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class BrainConfig {

    private static final Logger log = LoggerFactory.getLogger(BrainConfig.class);

    // P2-1: 部门-工具部门映射，定义每个大脑可以访问哪些部门的工具
    // key = 大脑部门, value = 允许访问的工具部门集合
    private static final Map<String, Set<String>> BRAIN_TOOL_DEPARTMENT_MAPPING = Map.of(
        "tech", Set.of("tech", "devops", "project", "core", "comm", "cross_dept", "search", "data", "productivity", "information"),
        "hr", Set.of("hr", "human_resources", "core", "comm", "cross_dept", "search", "data", "productivity", "information"),
        "finance", Set.of("finance", "core", "comm", "cross_dept", "search", "data", "productivity", "information"),
        "sales", Set.of("sales", "core", "comm", "cross_dept", "search", "data", "productivity", "information"),
        "cs", Set.of("cs", "core", "comm", "cross_dept", "search", "data", "productivity", "information"),
        "admin", Set.of("admin", "enterprise_management", "core", "comm", "cross_dept", "search", "data", "productivity", "information"),
        "legal", Set.of("legal", "core", "comm", "cross_dept", "search", "data", "productivity", "information"),
        "ops", Set.of("ops", "devops", "core", "comm", "cross_dept", "search", "data", "productivity", "information")
    );

    /**
     * P2-1: 按部门过滤工具，实现大脑工具隔离
     * MainBrain 获取全部工具，其他大脑仅获取映射表中允许的部门工具
     */
    private List<Tool> filterToolsByBrainDepartment(ToolRegistry toolRegistry, String brainDepartment) {
        Set<String> allowedDepartments = BRAIN_TOOL_DEPARTMENT_MAPPING.get(brainDepartment);
        if (allowedDepartments == null) {
            log.warn("[P2-1] No tool department mapping for brain department '{}', falling back to getAll()", brainDepartment);
            return new ArrayList<>(toolRegistry.getAll());
        }

        List<Tool> allTools = toolRegistry.getAll();
        List<Tool> filtered = new ArrayList<>();
        for (Tool tool : allTools) {
            String toolDept = tool.getDepartment();
            // 无部门标识的工具视为共享工具，允许所有大脑访问
            if (toolDept == null || toolDept.isEmpty() || allowedDepartments.contains(toolDept)) {
                filtered.add(tool);
            }
        }

        log.info("[P2-1] Brain '{}' tool isolation: {}/{} tools allowed (departments: {})",
            brainDepartment, filtered.size(), allTools.size(), allowedDepartments);
        return filtered;
    }

    @Value("${living-agent.brain.compact.enabled:true}")
    private boolean compactEnabled;

    @Value("${living-agent.brain.compact.context-limit:50000}")
    private int compactContextLimit;

    @Value("${living-agent.brain.compact.persist-dir:./data/compact-outputs}")
    private String compactPersistDir;

    @Value("${living-agent.brain.compact.native-enabled:false}")
    private boolean nativeCompactEnabled;

    @Value("${living-agent.worktree.repo-root:./data/repo}")
    private String repoRoot;

    @Bean
    public BrainRegistryImpl brainRegistry() {
        log.info("Initializing BrainRegistry");
        return new BrainRegistryImpl();
    }

    @Bean
    public ContextCompactor contextCompactor(BrainRegistryImpl brainRegistry) {
        log.info("Initializing ContextCompactor (enabled={}, nativeEnabled={}, contextLimit={}, mode=hybrid)",
            compactEnabled, nativeCompactEnabled, compactContextLimit);
        if (!compactEnabled) {
            return null;
        }
        return new HybridContextCompactor(
            brainRegistry,
            java.nio.file.Path.of(compactPersistDir),
            compactContextLimit,
            nativeCompactEnabled
        );
    }

    @Bean
    public TaskDagService taskDagService() {
        log.info("Initializing TaskDagService");
        return new InMemoryTaskDagService();
    }

    @Bean
    public PlanApprovalService planApprovalService() {
        log.info("Initializing PlanApprovalService");
        return new InMemoryPlanApprovalService();
    }

    @Bean
    public LeadOrchestrator techLeadOrchestrator(TaskDagService taskDagService, ChannelManager channelManager,
                                                  CodeReviewWorkflowService codeReviewWorkflowService) {
        log.info("Initializing TechLeadOrchestrator with CodeReviewWorkflowService");
        return new TechLeadOrchestrator(taskDagService, channelManager, codeReviewWorkflowService);
    }

    @Bean
    public WorktreeManager worktreeManager(TaskDagService taskDagService) {
        log.info("Initializing WorktreeManager with repo root: {}", repoRoot);
        return new GitWorktreeManager(java.nio.file.Path.of(repoRoot), taskDagService);
    }

    @Bean
    public EvolutionMemoryGraph evolutionMemoryGraph() {
        log.info("Initializing EvolutionMemoryGraph");
        return new com.livingagent.core.evolution.memory.impl.InMemoryEvolutionMemoryGraph();
    }

    @Bean
    public EvolutionCircuitBreaker evolutionCircuitBreaker(EvolutionMemoryGraph memoryGraph) {
        log.info("Initializing EvolutionCircuitBreaker");
        return new EvolutionCircuitBreaker(memoryGraph);
    }

    @Bean
    public EvolutionDecisionEngine evolutionDecisionEngine(
            EvolutionMemoryGraph memoryGraph,
            EvolutionCircuitBreaker circuitBreaker) {
        log.info("Initializing EvolutionDecisionEngine");
        return new DefaultEvolutionDecisionEngine(memoryGraph, circuitBreaker);
    }

    @Bean
    public MainBrain mainBrain(
            ToolRegistry toolRegistry,
            BrainRegistryImpl brainRegistry,
            com.livingagent.core.security.PermissionService permissionService,
            BrainModelResolver brainModelResolver,
            BrainModelAssigner brainModelAssigner,
            ModelPoolManager modelPoolManager) {
        log.info("Initializing MainBrain with BrainModelResolver");
        List<Tool> tools = new ArrayList<>(toolRegistry.getAll());
        MainBrain brain = new MainBrain(tools, brainRegistry, permissionService);
        brain.setBrainModelResolver(brainModelResolver);
        brain.setBrainModelAssigner(brainModelAssigner);
        brain.setModelPoolManager(modelPoolManager);
        return brain;
    }

    @Bean
    public TechBrain techBrain(ToolRegistry toolRegistry,
                               ContextCompactor contextCompactor,
                               LeadOrchestrator techLeadOrchestrator,
                               TechBrainModelSelector techBrainModelSelector,
                               BrainModelResolver brainModelResolver,
                               BrainModelAssigner brainModelAssigner,
                               ModelPoolManager modelPoolManager) {
        log.info("Initializing TechBrain with ModelSelector and BrainModelResolver");
        // P2-1: 按部门过滤工具，实现工具隔离
        List<Tool> tools = filterToolsByBrainDepartment(toolRegistry, "tech");
        TechBrain brain = new TechBrain(tools);
        if (contextCompactor != null) {
            brain.setContextCompactor(contextCompactor);
        }
        if (techLeadOrchestrator != null) {
            brain.setLeadOrchestrator(techLeadOrchestrator);
        }
        brain.setModelSelector(techBrainModelSelector);
        brain.setBrainModelResolver(brainModelResolver);
        brain.setBrainModelAssigner(brainModelAssigner);
        brain.setModelPoolManager(modelPoolManager);
        return brain;
    }

    @Bean
    public HrBrain hrBrain(ToolRegistry toolRegistry, ContextCompactor contextCompactor,
                           HrBrainModelSelector hrBrainModelSelector,
                           BrainModelResolver brainModelResolver,
                           BrainModelAssigner brainModelAssigner,
                           ModelPoolManager modelPoolManager) {
        log.info("Initializing HrBrain with ModelSelector and BrainModelResolver");
        List<Tool> tools = filterToolsByBrainDepartment(toolRegistry, "hr");
        HrBrain brain = new HrBrain(tools);
        if (contextCompactor != null) {
            brain.setContextCompactor(contextCompactor);
        }
        brain.setModelSelector(hrBrainModelSelector);
        brain.setBrainModelResolver(brainModelResolver);
        brain.setBrainModelAssigner(brainModelAssigner);
        brain.setModelPoolManager(modelPoolManager);
        return brain;
    }

    @Bean
    public FinanceBrain financeBrain(ToolRegistry toolRegistry,
                                     FinanceBrainModelSelector financeBrainModelSelector,
                                     BrainModelResolver brainModelResolver,
                                     BrainModelAssigner brainModelAssigner,
                                     ModelPoolManager modelPoolManager) {
        log.info("Initializing FinanceBrain with ModelSelector and BrainModelResolver");
        List<Tool> tools = filterToolsByBrainDepartment(toolRegistry, "finance");
        FinanceBrain brain = new FinanceBrain(tools);
        brain.setModelSelector(financeBrainModelSelector);
        brain.setBrainModelResolver(brainModelResolver);
        brain.setBrainModelAssigner(brainModelAssigner);
        brain.setModelPoolManager(modelPoolManager);
        return brain;
    }

    @Bean
    public SalesBrain salesBrain(ToolRegistry toolRegistry,
                                 SalesBrainModelSelector salesBrainModelSelector,
                                 BrainModelResolver brainModelResolver,
                                 BrainModelAssigner brainModelAssigner,
                                 ModelPoolManager modelPoolManager) {
        log.info("Initializing SalesBrain with ModelSelector and BrainModelResolver");
        List<Tool> tools = filterToolsByBrainDepartment(toolRegistry, "sales");
        SalesBrain brain = new SalesBrain(tools);
        brain.setModelSelector(salesBrainModelSelector);
        brain.setBrainModelResolver(brainModelResolver);
        brain.setBrainModelAssigner(brainModelAssigner);
        brain.setModelPoolManager(modelPoolManager);
        return brain;
    }

    @Bean
    public CsBrain csBrain(ToolRegistry toolRegistry,
                           CsBrainModelSelector csBrainModelSelector,
                           BrainModelResolver brainModelResolver,
                           BrainModelAssigner brainModelAssigner,
                           ModelPoolManager modelPoolManager) {
        log.info("Initializing CsBrain with ModelSelector and BrainModelResolver");
        List<Tool> tools = filterToolsByBrainDepartment(toolRegistry, "cs");
        CsBrain brain = new CsBrain(tools);
        brain.setModelSelector(csBrainModelSelector);
        brain.setBrainModelResolver(brainModelResolver);
        brain.setBrainModelAssigner(brainModelAssigner);
        brain.setModelPoolManager(modelPoolManager);
        return brain;
    }

    @Bean
    public AdminBrain adminBrain(ToolRegistry toolRegistry,
                                 AdminBrainModelSelector adminBrainModelSelector,
                                 BrainModelResolver brainModelResolver,
                                 BrainModelAssigner brainModelAssigner,
                                 ModelPoolManager modelPoolManager) {
        log.info("Initializing AdminBrain with ModelSelector and BrainModelResolver");
        List<Tool> tools = filterToolsByBrainDepartment(toolRegistry, "admin");
        AdminBrain brain = new AdminBrain(tools);
        brain.setModelSelector(adminBrainModelSelector);
        brain.setBrainModelResolver(brainModelResolver);
        brain.setBrainModelAssigner(brainModelAssigner);
        brain.setModelPoolManager(modelPoolManager);
        return brain;
    }

    @Bean
    public LegalBrain legalBrain(ToolRegistry toolRegistry,
                                 LegalBrainModelSelector legalBrainModelSelector,
                                 BrainModelResolver brainModelResolver,
                                 BrainModelAssigner brainModelAssigner,
                                 ModelPoolManager modelPoolManager) {
        log.info("Initializing LegalBrain with ModelSelector and BrainModelResolver");
        List<Tool> tools = filterToolsByBrainDepartment(toolRegistry, "legal");
        LegalBrain brain = new LegalBrain(tools);
        brain.setModelSelector(legalBrainModelSelector);
        brain.setBrainModelResolver(brainModelResolver);
        brain.setBrainModelAssigner(brainModelAssigner);
        brain.setModelPoolManager(modelPoolManager);
        return brain;
    }

    @Bean
    public OpsBrain opsBrain(ToolRegistry toolRegistry,
                             OpsBrainModelSelector opsBrainModelSelector,
                             BrainModelResolver brainModelResolver,
                             BrainModelAssigner brainModelAssigner,
                             ModelPoolManager modelPoolManager) {
        log.info("Initializing OpsBrain with ModelSelector and BrainModelResolver");
        List<Tool> tools = filterToolsByBrainDepartment(toolRegistry, "ops");
        OpsBrain brain = new OpsBrain(tools);
        brain.setModelSelector(opsBrainModelSelector);
        brain.setBrainModelResolver(brainModelResolver);
        brain.setBrainModelAssigner(brainModelAssigner);
        brain.setModelPoolManager(modelPoolManager);
        return brain;
    }

    @Bean
    public LivingAgentInitializer livingAgentInitializer(
            BrainRegistryImpl brainRegistry,
            com.livingagent.core.neuron.NeuronRegistry neuronRegistry,
            ChannelManager channelManager,
            List<Brain> brains,
            ModelPoolManager modelPoolManager) {
        log.info("Initializing LivingAgentInitializer with ModelPoolManager");
        return new LivingAgentInitializer(brainRegistry, neuronRegistry, channelManager, brains, modelPoolManager);
    }

    public static class LivingAgentInitializer {

        private static final Logger initLog = LoggerFactory.getLogger(LivingAgentInitializer.class);

        private final BrainRegistryImpl brainRegistry;
        private final com.livingagent.core.neuron.NeuronRegistry neuronRegistry;
        private final ChannelManager channelManager;
        private final List<Brain> brains;
        private final ModelPoolManager modelPoolManager;

        public LivingAgentInitializer(
                BrainRegistryImpl brainRegistry,
                com.livingagent.core.neuron.NeuronRegistry neuronRegistry,
                ChannelManager channelManager,
                List<Brain> brains,
                ModelPoolManager modelPoolManager) {
            this.brainRegistry = brainRegistry;
            this.neuronRegistry = neuronRegistry;
            this.channelManager = channelManager;
            this.brains = brains;
            this.modelPoolManager = modelPoolManager;
        }

        @jakarta.annotation.PostConstruct
        public void initialize() {
            initLog.info("Starting LivingAgent initialization...");

            modelPoolManager.seedDefaults();
            initLog.info("Model pool defaults seeded");

            for (Brain brain : brains) {
                brainRegistry.register(brain);
                initLog.info("Registered brain: {}", brain.getName());
            }

            brainRegistry.startAll();
            initLog.info("All brains started. Total brains: {}", brainRegistry.count());

            neuronRegistry.startAll();
            initLog.info("All neurons started. Total neurons: {}", neuronRegistry.count());
        }

        public void shutdown() {
            initLog.info("Shutting down LivingAgent...");
            brainRegistry.stopAll();
            initLog.info("LivingAgent shutdown completed");
        }
    }
}
