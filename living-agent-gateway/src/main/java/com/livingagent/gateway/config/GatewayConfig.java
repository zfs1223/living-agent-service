package com.livingagent.gateway.config;

import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.BrainRegistryImpl;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.knowledge.KnowledgeManager;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.proactive.suggestion.ProactiveSuggestionService;
import com.livingagent.core.security.auth.OAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.voiceprint.VoicePrintService;
import com.livingagent.core.security.auth.PhoneVerificationService;
import com.livingagent.core.database.repository.SessionContextRepository;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.autonomy.PerformanceStatsService;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.autonomy.*;
import com.livingagent.core.autonomy.impl.ChannelBackedDepartmentExecutionCoordinator;
import com.livingagent.core.autonomy.impl.DefaultAssignmentPreparationService;
import com.livingagent.core.autonomy.impl.DefaultExecutionCapabilityResolver;
import com.livingagent.core.autonomy.impl.DefaultExecutionResultAggregator;
import com.livingagent.core.autonomy.impl.DefaultKnowledgeCaptureService;
import com.livingagent.core.autonomy.impl.DefaultMainBrainRequirementClarifier;
import com.livingagent.core.autonomy.impl.DefaultMainBrainResponseComposer;
import com.livingagent.core.autonomy.impl.DefaultPerformanceCaptureService;
import com.livingagent.core.autonomy.impl.DefaultFinalResponseCoordinator;
import com.livingagent.core.autonomy.impl.DefaultTaskRouteClassifier;
import com.livingagent.core.autonomy.impl.InMemoryDepartmentTodoPool;
import com.livingagent.core.autonomy.impl.DefaultEmployeeSelfClaimService;
import com.livingagent.core.autonomy.impl.DefaultCrossDepartmentCoordinator;
import com.livingagent.core.autonomy.impl.DefaultDepartmentAggregationService;
import com.livingagent.core.autonomy.impl.JpaDepartmentAggregationService;
import com.livingagent.core.autonomy.impl.LlmDepartmentAggregationService;
import com.livingagent.core.brain.impl.MainBrain;
import com.livingagent.core.autonomy.review.InternalReviewService;
import com.livingagent.core.autonomy.review.impl.DefaultInternalReviewService;
import com.livingagent.core.autonomy.review.impl.JpaInternalReviewService;
import com.livingagent.core.database.repository.InternalReviewRepository;
import com.livingagent.core.autonomy.impl.LlmRequirementReadinessEvaluator;
import com.livingagent.core.autonomy.impl.DynamicEmployeeTaskConsumerRegistry;
import com.livingagent.core.autonomy.LLMEmployeeCreationService;
import com.livingagent.core.autonomy.impl.LLMEmployeeCreationServiceImpl;
import com.livingagent.core.autonomy.AutonomyTraceService;
import com.livingagent.core.autonomy.impl.JpaEmployeeExecutionReceiptService;
import com.livingagent.core.database.repository.EmployeeExecutionReceiptRepository;
import com.livingagent.core.autonomy.context.DecisionContextBuilder;
import com.livingagent.core.autonomy.impl.LlmBasedDialogueAnalyzer;
import com.livingagent.core.autonomy.impl.LlmBasedMainBrainTaskDirector;
import com.livingagent.core.autonomy.impl.LlmBasedFixedEmployeeDispatcher;
import com.livingagent.core.autonomy.impl.LlmBasedFinalResponseCoordinator;
import com.livingagent.core.autonomy.impl.LlmBasedMainBrainResponseComposer;
import com.livingagent.core.autonomy.impl.LlmBasedExecutionResultAggregator;
import com.livingagent.core.autonomy.impl.LlmExecutionReceiptReviewer;
import com.livingagent.core.autonomy.llm.LlmDecisionClient;
import com.livingagent.core.autonomy.llm.impl.DefaultLlmDecisionClient;
import com.livingagent.core.autonomy.impl.LlmAssignmentReadinessEvaluator;
import com.livingagent.core.autonomy.ExecutionReceiptReviewer;
import com.livingagent.core.autonomy.AssignmentReadinessEvaluator;
import com.livingagent.core.proactive.llm.LlmProactiveAdvisor;
import com.livingagent.core.proactive.llm.LlmRiskAssessor;
import com.livingagent.core.proactive.llm.impl.LlmProactiveAdvisorImpl;
import com.livingagent.core.proactive.llm.impl.LlmRiskAssessorImpl;
import com.livingagent.core.proactive.predictor.PatternPredictor;
import com.livingagent.core.proactive.predictor.RiskPredictor;
import com.livingagent.core.autonomy.impl.RegistryBackedFixedEmployeeDispatcher;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import com.livingagent.core.autonomous.bounty.LedgerService;
import com.livingagent.core.model.pool.BrainModelResolver;
import com.livingagent.core.model.pool.ModelHealthRegistry;
import com.livingagent.core.model.pool.ModelPoolManager;
import com.livingagent.gateway.executor.ToolExecutor;
import com.livingagent.gateway.executor.ToolExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Configuration
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true)  // 强制启用 CGLIB 代理，解决 @EventListener 方法不在接口中的问题
public class GatewayConfig {
    
    private static final Logger log = LoggerFactory.getLogger(GatewayConfig.class);

    @Bean(initMethod = "loadSessionsFromDb")
    public UnifiedAuthService unifiedAuthService(
            List<OAuthService> oauthServices,
            VoicePrintService voicePrintService,
            PhoneVerificationService phoneVerificationService,
            SessionContextRepository sessionContextRepository
    ) {
        log.info("Initializing UnifiedAuthService with {} OAuth providers and session persistence",
                oauthServices != null ? oauthServices.size() : 0);
        return new UnifiedAuthService(oauthServices, voicePrintService, phoneVerificationService,
                sessionContextRepository);
    }

    @Bean
    public ProactiveSuggestionService proactiveSuggestionService(
            PatternPredictor patternPredictor,
            RiskPredictor riskPredictor,
            LlmProactiveAdvisor llmProactiveAdvisor,
            LlmRiskAssessor llmRiskAssessor,
            List<com.livingagent.core.proactive.alert.AlertNotifier> notifiers) {
        log.info("Initializing ProactiveSuggestionService with LLM proactive advisor and risk assessor");
        return new ProactiveSuggestionService(patternPredictor, riskPredictor, llmProactiveAdvisor, llmRiskAssessor, notifiers);
    }
    
    @Bean
    public ToolExecutorService toolExecutorService(
            ApplicationEventPublisher eventPublisher,
            ToolRegistry toolRegistry) {
        log.info("Initializing ToolExecutorService");
        ToolExecutorService service = new ToolExecutorService(eventPublisher);
        
        for (Tool tool : toolRegistry.getAll()) {
            ToolExecutor executor = createExecutorFromTool(tool);
            if (executor != null) {
                service.register(executor);
                log.debug("Registered tool executor: {}", tool.getName());
            }
        }
        
        log.info("ToolExecutorService initialized with {} executors", service.getExecutorCount());
        return service;
    }
    
    private ToolExecutor createExecutorFromTool(Tool tool) {
        return new ToolExecutor() {
            @Override
            public String getName() {
                return tool.getName();
            }
            
            @Override
            public String getDescription() {
                return tool.getDescription();
            }
            
            @Override
            public com.livingagent.core.tool.ToolResult execute(Map<String, Object> parameters, String userId) {
                com.livingagent.core.tool.Tool.ToolParams params = com.livingagent.core.tool.Tool.ToolParams.of(parameters);
                com.livingagent.core.tool.ToolContext context = com.livingagent.core.tool.ToolContext.of(
                    userId,
                    "gateway-session"
                );
                return tool.execute(params, context);
            }

            @Override
            public String[] getRequiredParameters() {
                List<String> required = tool.getSchema().required();
                return required != null ? required.toArray(new String[0]) : new String[0];
            }
        };
    }

    @Bean
    public DialogueAnalyzer dialogueAnalyzer(BrainRegistry brainRegistry, FixedEmployeeRegistry fixedEmployeeRegistry, LlmDecisionClient llmDecisionClient, DecisionContextBuilder decisionContextBuilder) {
        log.info("Initializing LlmBasedDialogueAnalyzer with DecisionContext");
        return new LlmBasedDialogueAnalyzer(brainRegistry, fixedEmployeeRegistry, llmDecisionClient, decisionContextBuilder);
    }

    @Bean
    public AutonomyTraceService autonomyTraceService(com.livingagent.core.database.repository.TraceEventRepository traceEventRepository) {
        log.info("Initializing AutonomyTraceService with database persistence (single-thread executor)");
        return new AutonomyTraceService(1000, traceEventRepository, java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "trace-persist");
            t.setDaemon(true);
            return t;
        }));
    }

    @Bean
    public MainBrainTaskDirector mainBrainTaskDirector(BrainRegistry brainRegistry, FixedEmployeeRegistry fixedEmployeeRegistry, AutonomyTraceService autonomyTraceService, LlmDecisionClient llmDecisionClient) {
        log.info("Initializing LlmBasedMainBrainTaskDirector");
        return new LlmBasedMainBrainTaskDirector(brainRegistry, fixedEmployeeRegistry, autonomyTraceService, llmDecisionClient);
    }

    @Bean
    public FixedEmployeeDispatcher fixedEmployeeDispatcher(
            FixedEmployeeRegistry fixedEmployeeRegistry,
            BrainRegistry brainRegistry,
            AutonomyTraceService autonomyTraceService,
            PerformanceStatsService performanceStatsService) {
        log.info("Initializing LlmBasedFixedEmployeeDispatcher with PerformanceStatsService");
        return new LlmBasedFixedEmployeeDispatcher(fixedEmployeeRegistry, brainRegistry, autonomyTraceService, performanceStatsService);
    }

    @Bean
    public AssignmentPreparationService assignmentPreparationService(
            com.livingagent.core.autonomy.ExecutionCapabilityResolver capabilityResolver,
            FixedEmployeeRegistry fixedEmployeeRegistry) {
        log.info("Initializing DefaultAssignmentPreparationService with ExecutionCapabilityResolver and FixedEmployeeRegistry");
        return new DefaultAssignmentPreparationService(capabilityResolver, fixedEmployeeRegistry);
    }

    @Bean
    public DepartmentExecutionCoordinator departmentExecutionCoordinator(
            ChannelManager channelManager,
            EmployeeExecutionReceiptService employeeExecutionReceiptService) {
        log.info("Initializing ChannelBackedDepartmentExecutionCoordinator");
        return new ChannelBackedDepartmentExecutionCoordinator(channelManager, employeeExecutionReceiptService);
    }

    @Bean
    public InternalReviewService internalReviewService(InternalReviewRepository internalReviewRepository) {
        log.info("Initializing JpaInternalReviewService (persistent)");
        return new JpaInternalReviewService(internalReviewRepository);
    }

    @Bean
    public com.livingagent.core.autonomy.DepartmentTodoPool departmentTodoPool() {
        log.info("Initializing InMemoryDepartmentTodoPool");
        return new InMemoryDepartmentTodoPool();
    }

    @Bean
    public com.livingagent.core.autonomy.EmployeeSelfClaimService employeeSelfClaimService(
            com.livingagent.core.autonomy.DepartmentTodoPool departmentTodoPool,
            FixedEmployeeRegistry fixedEmployeeRegistry) {
        log.info("Initializing DefaultEmployeeSelfClaimService with maxLoad=3");
        return new DefaultEmployeeSelfClaimService(departmentTodoPool, fixedEmployeeRegistry, 3);
    }

    /**
     * #7: 部门聚合服务 Bean。
     *
     * <p>默认使用 LLM 增强版（LlmDepartmentAggregationService），LLM 调用失败时自动降级到规则版。
     * 如果 MainBrain 不可用，则直接使用规则版（DefaultDepartmentAggregationService）。
     */
    @Bean
    @org.springframework.context.annotation.Primary
    public com.livingagent.core.autonomy.DepartmentAggregationService departmentAggregationService(
            EmployeeExecutionReceiptService employeeExecutionReceiptService,
            InternalReviewService internalReviewService,
            com.livingagent.core.autonomy.DepartmentTodoPool departmentTodoPool,
            MainBrain mainBrain,
            com.livingagent.core.database.repository.DepartmentDeliverableRepository deliverableRepository) {
        // M-DA: 使用 JPA 持久化版作为 fallback
        JpaDepartmentAggregationService fallback = new JpaDepartmentAggregationService(
            employeeExecutionReceiptService, internalReviewService, departmentTodoPool, deliverableRepository);

        if (mainBrain != null) {
            log.info("Initializing LlmDepartmentAggregationService with JPA fallback");
            return new LlmDepartmentAggregationService(fallback, mainBrain);
        } else {
            log.info("MainBrain not available, using JpaDepartmentAggregationService");
            return fallback;
        }
    }

    @Bean(initMethod = "registerAll")
    public DynamicEmployeeTaskConsumerRegistry dynamicEmployeeTaskConsumerRegistry(
            ChannelManager channelManager,
            FixedEmployeeRegistry fixedEmployeeRegistry,
            EmployeeExecutionReceiptService employeeExecutionReceiptService,
            BrainModelResolver brainModelResolver,
            ModelPoolManager modelPoolManager,
            com.livingagent.core.autonomy.EmployeeTaskExecutor employeeTaskExecutor,
            EmployeeService employeeService,
            InternalReviewService internalReviewService) {
        log.info("Initializing DynamicEmployeeTaskConsumerRegistry with ToolBackedEmployeeTaskExecutor, EmployeeService and InternalReviewService");
        return new DynamicEmployeeTaskConsumerRegistry(channelManager, fixedEmployeeRegistry, employeeExecutionReceiptService, brainModelResolver, modelPoolManager, employeeTaskExecutor, employeeService, internalReviewService);
    }

    @Bean
    public LLMEmployeeCreationService llmEmployeeCreationService(
            BrainRegistry brainRegistry,
            EmployeeService employeeService,
            FixedEmployeeRegistry fixedEmployeeRegistry,
            AutonomyTraceService traceService) {
        log.info("Initializing LLMEmployeeCreationService");
        return new LLMEmployeeCreationServiceImpl(brainRegistry, employeeService, fixedEmployeeRegistry, traceService);
    }

    @Bean
    public MainBrainResponseComposer mainBrainResponseComposer(BrainRegistry brainRegistry) {
        log.info("Initializing LlmBasedMainBrainResponseComposer");
        return new LlmBasedMainBrainResponseComposer(brainRegistry);
    }

    @Bean
    public ExecutionResultAggregator executionResultAggregator(BrainRegistry brainRegistry, PerformanceStatsService performanceStatsService) {
        log.info("Initializing LlmBasedExecutionResultAggregator");
        ExecutionReceiptReviewer reviewer = new LlmExecutionReceiptReviewer(brainRegistry, performanceStatsService);
        return new LlmBasedExecutionResultAggregator(brainRegistry, reviewer);
    }

    @Bean
    public ExecutionReceiptReviewer executionReceiptReviewer(BrainRegistry brainRegistry, PerformanceStatsService performanceStatsService) {
        log.info("Initializing LlmExecutionReceiptReviewer with PerformanceStatsService");
        return new LlmExecutionReceiptReviewer(brainRegistry, performanceStatsService);
    }

    @Bean
    public AssignmentReadinessEvaluator assignmentReadinessEvaluator(BrainRegistry brainRegistry) {
        log.info("Initializing LlmAssignmentReadinessEvaluator");
        return new LlmAssignmentReadinessEvaluator(brainRegistry);
    }

    @Bean
    public FinalResponseCoordinator finalResponseCoordinator(
            BrainRegistry brainRegistry,
            AutonomyTraceService autonomyTraceService) {
        log.info("Initializing LlmBasedFinalResponseCoordinator");
        return new LlmBasedFinalResponseCoordinator(brainRegistry, autonomyTraceService);
    }

    @Bean
    public TaskRouteClassifier taskRouteClassifier(BrainRegistry brainRegistry) {
        log.info("Initializing DefaultTaskRouteClassifier");
        return new DefaultTaskRouteClassifier(brainRegistry);
    }

    @Bean
    public ConversationOrchestrator conversationOrchestrator(
            DialogueAnalyzer dialogueAnalyzer,
            MainBrainTaskDirector mainBrainTaskDirector,
            BrainRegistry brainRegistry,
            AutonomyTraceService autonomyTraceService,
            RequirementReadinessEvaluator readinessEvaluator,
            MainBrainRequirementClarifier requirementClarifier,
            FixedEmployeeDispatcher fixedEmployeeDispatcher,
            TaskRouteClassifier taskRouteClassifier) {
        log.info("Initializing ConversationOrchestrator with TaskRouteClassifier");
        return new ConversationOrchestrator(dialogueAnalyzer, mainBrainTaskDirector, brainRegistry,
            autonomyTraceService, readinessEvaluator, requirementClarifier, fixedEmployeeDispatcher, null, taskRouteClassifier);
    }

    @Bean
    public RequirementReadinessEvaluator requirementReadinessEvaluator(BrainModelResolver brainModelResolver) {
        log.info("Initializing LlmRequirementReadinessEvaluator");
        return new LlmRequirementReadinessEvaluator(brainModelResolver, "neuron://core/main-brain/001");
    }

    @Bean
    public MainBrainRequirementClarifier mainBrainRequirementClarifier() {
        log.info("Initializing DefaultMainBrainRequirementClarifier");
        return new DefaultMainBrainRequirementClarifier();
    }

    @Bean
    public KnowledgeCaptureService knowledgeCaptureService(KnowledgeManager knowledgeManager) {
        log.info("Initializing DefaultKnowledgeCaptureService");
        return new DefaultKnowledgeCaptureService(knowledgeManager);
    }

    @Bean
    public PerformanceCaptureService performanceCaptureService(LedgerService ledgerService) {
        log.info("Initializing DefaultPerformanceCaptureService");
        return new DefaultPerformanceCaptureService(ledgerService);
    }

    @Bean
    public LlmProactiveAdvisor llmProactiveAdvisor(
            BrainRegistry brainRegistry,
            PatternPredictor patternPredictor) {
        log.info("Initializing LlmProactiveAdvisorImpl");
        return new LlmProactiveAdvisorImpl(brainRegistry, patternPredictor);
    }

    @Bean
    public LlmRiskAssessor llmRiskAssessor(
            BrainRegistry brainRegistry,
            RiskPredictor riskPredictor) {
        log.info("Initializing LlmRiskAssessorImpl");
        return new LlmRiskAssessorImpl(brainRegistry, riskPredictor);
    }

    @Bean
    public com.livingagent.core.autonomy.context.DecisionContextBuilder decisionContextBuilder(
            UnifiedAuthService unifiedAuthService,
            BrainRegistry brainRegistry,
            FixedEmployeeRegistry fixedEmployeeRegistry,
            ToolRegistry toolRegistry,
            KnowledgeManager knowledgeManager,
            BrainModelResolver brainModelResolver) {
        log.info("Initializing DefaultDecisionContextBuilder");
        return new com.livingagent.core.autonomy.context.impl.DefaultDecisionContextBuilder(
            unifiedAuthService, brainRegistry, fixedEmployeeRegistry,
            toolRegistry, knowledgeManager, brainModelResolver);
    }

    @Bean
    public com.livingagent.core.autonomy.llm.LlmDecisionClient llmDecisionClient(
            BrainRegistry brainRegistry,
            BrainModelResolver brainModelResolver) {
        log.info("Initializing DefaultLlmDecisionClient");
        return new com.livingagent.core.autonomy.llm.impl.DefaultLlmDecisionClient(
            brainRegistry, brainModelResolver);
    }

    @Bean
    public ModelHealthRegistry modelHealthRegistry() {
        log.info("Initializing ModelHealthRegistry with cooldown=5min, failureThreshold=3");
        return new ModelHealthRegistry(3, java.time.Duration.ofMinutes(5), 2);
    }

    @Bean
    public BrainModelResolver brainModelResolver(
            com.livingagent.core.model.pool.BrainModelAssignmentRepository assignmentRepo,
            com.livingagent.core.model.pool.LlmModelRepository modelRepo,
            com.livingagent.core.model.pool.ProviderConfigRepository providerRepo,
            com.livingagent.core.model.selector.BrainModelSelectorManager selectorManager,
            ModelHealthRegistry modelHealthRegistry,
            com.livingagent.core.model.pool.ModelCapabilityAssessor modelCapabilityAssessor) {
        log.info("Initializing BrainModelResolver with ModelHealthRegistry and ModelCapabilityAssessor");
        return new BrainModelResolver(assignmentRepo, modelRepo, providerRepo, selectorManager, modelHealthRegistry, modelCapabilityAssessor);
    }

    @Bean
    public com.livingagent.core.autonomy.MainBrainFinalSummaryService mainBrainFinalSummaryService(
            com.livingagent.core.brain.impl.MainBrain mainBrain) {
        log.info("Initializing LlmMainBrainFinalSummaryService with DefaultMainBrainFinalSummaryService fallback");
        com.livingagent.core.autonomy.MainBrainFinalSummaryService fallback = 
            new com.livingagent.core.autonomy.impl.DefaultMainBrainFinalSummaryService();
        return new com.livingagent.core.autonomy.impl.LlmMainBrainFinalSummaryService(mainBrain, fallback);
    }

    @Bean
    public com.livingagent.core.autonomy.ExecutionCapabilityResolver executionCapabilityResolver() {
        log.info("Initializing DefaultExecutionCapabilityResolver");
        return new com.livingagent.core.autonomy.impl.DefaultExecutionCapabilityResolver();
    }

    @Bean
    public com.livingagent.core.autonomy.EmployeeTaskExecutor employeeTaskExecutor(
            Optional<com.livingagent.core.sandbox.SandboxService> sandboxServiceOpt,
            BrainModelResolver brainModelResolver,
            com.livingagent.core.autonomy.ExecutionCapabilityResolver capabilityResolver) {
        log.info("Initializing ToolBackedEmployeeTaskExecutor for real tool-backed task execution");
        return sandboxServiceOpt
            .filter(com.livingagent.core.sandbox.SandboxService::isAvailable)
            .map(sandbox -> {
                log.info("ToolBackedEmployeeTaskExecutor initialized with Docker sandbox + ExecutionCapabilityResolver");
                return new com.livingagent.core.autonomy.impl.ToolBackedEmployeeTaskExecutor(sandbox, brainModelResolver, capabilityResolver);
            })
            .orElseGet(() -> {
                log.info("ToolBackedEmployeeTaskExecutor initialized without sandbox (artifact-only mode) + ExecutionCapabilityResolver");
                return new com.livingagent.core.autonomy.impl.ToolBackedEmployeeTaskExecutor(null, brainModelResolver, capabilityResolver);
            });
    }

    // ===== 周期性健康摘要 =====
    // @Lazy 打破自引用循环：ModelHealthRegistry 是本类 @Bean 方法定义的，直接注入会形成 GatewayConfig → ModelHealthRegistry → GatewayConfig 循环

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private ModelHealthRegistry _modelHealthRegistry;

    /** 每5分钟输出模型健康摘要 */
    @Scheduled(fixedRate = 300000, initialDelay = 60000)
    public void logModelHealthSummary() {
        if (_modelHealthRegistry == null) return;
        String summary = _modelHealthRegistry.getHealthSummary();
        if (!summary.isEmpty()) {
            log.info("[ModelHealth] Periodic summary: {}", summary);
        }
    }

    @Bean
    public CrossDepartmentCoordinator crossDepartmentCoordinator(
            DepartmentExecutionCoordinator departmentExecutionCoordinator,
            AutonomyTraceService traceService) {
        log.info("Initializing DefaultCrossDepartmentCoordinator");
        return new DefaultCrossDepartmentCoordinator(departmentExecutionCoordinator, traceService);
    }

    @Bean
    public com.livingagent.core.diagnosis.impl.StartupDependencyChecker startupDependencyChecker(
            com.livingagent.core.diagnosis.HealthMonitor healthMonitor,
            Optional<com.livingagent.core.model.ModelClient> modelClient) {
        log.info("Initializing StartupDependencyChecker with P20-A process monitor");
        return modelClient
            .map(mc -> new com.livingagent.core.diagnosis.impl.StartupDependencyChecker(healthMonitor, mc))
            .orElseGet(() -> new com.livingagent.core.diagnosis.impl.StartupDependencyChecker(healthMonitor));
    }

    @Bean
    public com.livingagent.core.nativelib.NativeLibraryHealthCheck nativeLibraryHealthCheck(
            com.livingagent.core.nativelib.NativePerformanceMonitor performanceMonitor,
            com.livingagent.core.diagnosis.HealthMonitor healthMonitor) {
        com.livingagent.core.nativelib.NativeLibraryHealthCheck check =
            new com.livingagent.core.nativelib.NativeLibraryHealthCheck(performanceMonitor);
        healthMonitor.registerCheck("native_library", check);
        log.info("P19-C: Registered NativeLibraryHealthCheck with HealthMonitor");
        return check;
    }

    @Bean
    public com.livingagent.gateway.websocket.ConnectionHealthCheck connectionHealthCheck(
            com.livingagent.gateway.websocket.InMemoryConnectionRegistry connectionRegistry,
            com.livingagent.core.diagnosis.HealthMonitor healthMonitor,
            org.springframework.context.ApplicationEventPublisher eventPublisher) {
        com.livingagent.gateway.websocket.ConnectionHealthCheck check =
            new com.livingagent.gateway.websocket.ConnectionHealthCheck(connectionRegistry, eventPublisher);
        healthMonitor.registerCheck("websocket_connections", check);
        log.info("P24-C: Registered ConnectionHealthCheck with HealthMonitor");
        return check;
    }

    @Bean
    public com.livingagent.core.diagnosis.VitalSignsService vitalSignsService(
            com.livingagent.core.diagnosis.HealthMonitor healthMonitor,
            com.livingagent.gateway.websocket.InMemoryConnectionRegistry connectionRegistry,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            com.livingagent.core.evolution.orchestrator.CrossLoopEventBus crossLoopEventBus) {
        return new com.livingagent.core.diagnosis.VitalSignsService(
            healthMonitor,
            connectionRegistry::getActiveConnectionCount,
            com.livingagent.core.diagnosis.AppModeUtil::isDegraded,
            eventPublisher,
            crossLoopEventBus
        );
    }
}
