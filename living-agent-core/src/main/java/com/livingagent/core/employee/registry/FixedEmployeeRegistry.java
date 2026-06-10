package com.livingagent.core.employee.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.database.entity.FixedEmployeeDefinitionEntity;
import com.livingagent.core.database.repository.FixedEmployeeDefinitionRepository;
import com.livingagent.core.employee.*;
import com.livingagent.core.employee.claim.TaskClaimService;
import com.livingagent.core.employee.impl.DigitalEmployee;
import com.livingagent.core.employee.neuron.EmployeeNeuron;
import com.livingagent.core.evolution.engine.EvolutionDecisionEngine;
import com.livingagent.core.knowledge.KnowledgeBase;
import com.livingagent.core.model.pool.BrainModelResolver;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.provider.impl.ProviderFactory;
import com.livingagent.core.skill.SkillRegistry;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.util.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class FixedEmployeeRegistry {

    private static final Logger log = LoggerFactory.getLogger(FixedEmployeeRegistry.class);

    private final EmployeeService employeeService;
    private final NeuronRegistry neuronRegistry;
    private final BrainRegistry brainRegistry;
    private final ChannelManager channelManager;
    private final SkillRegistry skillRegistry;
    private final ToolRegistry toolRegistry;
    private final KnowledgeBase knowledgeBase;
    private final EvolutionDecisionEngine evolutionEngine;
    private final TaskClaimService taskClaimService;
    private final FixedEmployeeDefinitionRepository fixedEmployeeDefinitionRepository;
    private final BrainModelResolver brainModelResolver;
    private final ProviderFactory providerFactory;
    private final ObjectMapper objectMapper;

    @Value("${living-agent.fixed-employee.tools.strict:false}")
    private boolean strictToolValidation;

    private volatile boolean employeesInitialized = false;
    private volatile boolean brainsRegistered = false;

    private final Map<String, FixedEmployeeDefinition> definitionsByCode = new ConcurrentHashMap<>();
    private final Map<String, String> neuronIdToCode = new ConcurrentHashMap<>();
    private final Map<String, String> codeToNeuronId = new ConcurrentHashMap<>();
    private final Map<String, Neuron> employeeNeurons = new ConcurrentHashMap<>();

    public FixedEmployeeRegistry(
            EmployeeService employeeService, 
            NeuronRegistry neuronRegistry,
            BrainRegistry brainRegistry,
            ChannelManager channelManager,
            SkillRegistry skillRegistry,
            ToolRegistry toolRegistry,
            KnowledgeBase knowledgeBase,
            EvolutionDecisionEngine evolutionEngine,
            TaskClaimService taskClaimService,
            FixedEmployeeDefinitionRepository fixedEmployeeDefinitionRepository,
            BrainModelResolver brainModelResolver,
            ObjectMapper objectMapper) {
        this.employeeService = employeeService;
        this.neuronRegistry = neuronRegistry;
        this.brainRegistry = brainRegistry;
        this.channelManager = channelManager;
        this.skillRegistry = skillRegistry;
        this.toolRegistry = toolRegistry;
        this.knowledgeBase = knowledgeBase;
        this.evolutionEngine = evolutionEngine;
        this.taskClaimService = taskClaimService;
        this.fixedEmployeeDefinitionRepository = fixedEmployeeDefinitionRepository;
        this.brainModelResolver = brainModelResolver;
        this.providerFactory = new ProviderFactory(brainModelResolver);
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        registerAllFixedEmployees();
        validateConfiguredTools();
        log.info("FixedEmployeeRegistry definitions loaded: {} employees registered, waiting for brains to register", definitionsByCode.size());
    }

    @Order(1)
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        log.info("ApplicationReadyEvent received - starting fixed employee neuron creation after all brains are registered");
        brainsRegistered = true;
        createAndStartAllEmployees();
        validateDelegateBrainBindings();
        log.info("FixedEmployeeRegistry neurons created: {} active neurons", employeeNeurons.size());
        employeesInitialized = true;
    }

    public void registerAllFixedEmployees() {
        if (registerDefinitionsFromDatabase()) {
            log.info("Loaded fixed employee definitions from database: {}", definitionsByCode.size());
            return;
        }

        log.warn("No active fixed employee definitions found in database, falling back to static registry definitions");
        registerTechEmployees();
        registerFinanceEmployees();
        registerOpsEmployees();
        registerSalesEmployees();
        registerHrEmployees();
        registerCsEmployees();
        registerAdminEmployees();
        registerLegalEmployees();
        registerMainEmployees();
    }

    private boolean registerDefinitionsFromDatabase() {
        try {
            List<FixedEmployeeDefinitionEntity> entities = fixedEmployeeDefinitionRepository.findByActiveTrueOrderByCodeAsc();
            if (entities.isEmpty()) {
                return false;
            }

            definitionsByCode.clear();
            neuronIdToCode.clear();
            codeToNeuronId.clear();

            for (FixedEmployeeDefinitionEntity entity : entities) {
                FixedEmployeeDefinition definition = toDefinition(entity);
                definitionsByCode.put(definition.code(), definition);
                neuronIdToCode.put(definition.neuronId(), definition.code());
                codeToNeuronId.put(definition.code(), definition.neuronId());
            }
            return true;
        } catch (Exception e) {
            log.warn("Failed to load fixed employee definitions from database, fallback to static definitions", e);
            definitionsByCode.clear();
            neuronIdToCode.clear();
            codeToNeuronId.clear();
            return false;
        }
    }

    private void createAndStartAllEmployees() {
        int successCount = 0;
        int unboundCount = 0;
        List<String> unboundEmployees = new ArrayList<>();

        for (String code : definitionsByCode.keySet()) {
            try {
                Optional<Employee> existing = getEmployeeByCode(code);
                if (existing.isEmpty()) {
                    Employee employee = createFixedEmployee(code);
                    createAndStartNeuron(employee);
                    successCount++;
                    log.info("Created and started fixed employee: {} -> {}", code, employee.getEmployeeId());
                } else {
                    Employee employee = existing.get();
                    createAndStartNeuron(employee);
                    log.debug("Fixed employee {} already exists, ensured neuron started", code);
                }
            } catch (Exception e) {
                log.error("Failed to create fixed employee: {}", code, e);
                unboundCount++;
                unboundEmployees.add(code);
            }
        }

        log.info("Fixed employee creation summary: {} succeeded, {} failed", successCount, unboundCount);
        if (!unboundEmployees.isEmpty()) {
            log.warn("Unbound employees: {}", unboundEmployees);
        }
    }

    private void createAndStartNeuron(Employee employee) {
        if (!employee.isDigital()) {
            return;
        }
        
        DigitalEmployee de = (DigitalEmployee) employee;
        String neuronId = de.getDigitalConfig().getNeuronId();
        
        if (neuronRegistry.exists(neuronId)) {
            log.debug("Neuron already exists: {}", neuronId);
            return;
        }
        
        List<Tool> tools = loadToolsForEmployee(de);
        EmployeeNeuron neuron = EmployeeNeuron.create(de, brainRegistry, tools);
        
        if (knowledgeBase != null) {
            neuron.setKnowledgeBase(knowledgeBase);
        }
        if (evolutionEngine != null) {
            neuron.setEvolutionEngine(evolutionEngine);
        }
        if (taskClaimService != null) {
            neuron.setTaskClaimService(taskClaimService);
        }
        if (providerFactory != null) {
            neuron.setProviderFactory(providerFactory);
        }
        if (toolRegistry != null) {
            neuron.setToolRegistry(toolRegistry);
        }
        
        neuronRegistry.register(neuron);
        
        subscribeNeuronToChannels(neuron, de.getDigitalConfig().getSubscribeChannels());
        
        NeuronContext context = new NeuronContext(
            neuronId,
            de.getDigitalConfig().getSubscribeChannels().isEmpty() ? 
                null : de.getDigitalConfig().getSubscribeChannels().get(0),
            channelManager,
            skillRegistry
        );
        
        neuron.start(context);
        
        employeeNeurons.put(employee.getEmployeeId(), neuron);
        
        log.info("Created and started neuron for employee: {} ({})", 
            employee.getEmployeeId(), neuronId);
    }

    private void subscribeNeuronToChannels(Neuron neuron, List<String> channels) {
        for (String channelId : channels) {
            if (channelId != null && !channelId.isEmpty()) {
                try {
                    var channel = channelManager.getOrCreateChannel(channelId);
                    neuron.subscribe(channel);
                } catch (Exception e) {
                    log.warn("Failed to subscribe neuron {} to channel {}: {}", 
                        neuron.getId(), channelId, e.getMessage());
                }
            }
        }
    }

    private static final Set<String> SHARED_TOOL_NAMES = Set.of(
        "playwright_crawler",
        "rss_reader",
        "proactive_agent",
        "find_skills",
        "searxng",
        "weather",
        "summarize",
        "pdf",
        "office",
        "notion",
        "slack"
    );

    private static final Map<String, Set<String>> DEPARTMENT_TOOL_ALLOWLIST = Map.of(
        "tech", Set.of("browser_automation", "docker", "github", "gitlab", "jenkins", "huggingface", "trae", "claude_cli", "knowledge_graph", "self_improving", "jira", "file_edit"),
        "finance", Set.of("budget_management", "invoice_processing", "summarize", "browser_automation"),
        "ops", Set.of("summarize", "proactive_agent", "notion"),
        "sales", Set.of("github", "browser_automation", "notion", "slack", "summarize"),
        "hr", Set.of("notion", "slack", "summarize"),
        "cs", Set.of("notion", "slack", "jira"),
        "admin", Set.of("notion", "office", "slack", "summarize"),
        "legal", Set.of("office", "summarize"),
        "main", Set.of("slack", "proactive_agent", "summarize")
    );

    private static final Map<String, Set<String>> DEPARTMENT_DEFAULT_TOOL_WHITELIST = Map.of(
        "tech", Set.of("claude_cli", "github", "gitlab", "jenkins", "docker", "browser_automation", "knowledge_graph", "self_improving", "jira", "huggingface", "trae", "file_edit"),
        "finance", Set.of("budget_management", "invoice_processing", "browser_automation", "summarize"),
        "ops", Set.of("proactive_agent", "summarize", "notion"),
        "sales", Set.of("github", "browser_automation", "notion", "slack", "summarize"),
        "hr", Set.of("notion", "slack", "summarize"),
        "cs", Set.of("notion", "slack", "jira"),
        "admin", Set.of("notion", "office", "slack", "summarize"),
        "legal", Set.of("office", "summarize"),
        "main", Set.of("slack", "proactive_agent", "summarize")
    );

    private static final Map<String, String> TOOL_ALIAS = Map.ofEntries(
        Map.entry("docker_tool", "docker"),
        Map.entry("github_tool", "github"),
        Map.entry("gitlab_tool", "gitlab"),
        Map.entry("npm_tool", "browser_automation"),
        Map.entry("jenkins_tool", "jenkins"),
        Map.entry("model_tool", "huggingface"),
        Map.entry("database_tool", "knowledge_graph"),
        Map.entry("security_tool", "self_improving"),
        Map.entry("config_tool", "notion"),
        Map.entry("budget_tool", "budget_management"),
        Map.entry("finance_tool", "invoice_processing"),
        Map.entry("erp_tool", "invoice_processing"),
        Map.entry("ocr_tool", "browser_automation"),
        Map.entry("analytics_tool", "summarize"),
        Map.entry("bi_tool", "summarize"),
        Map.entry("scheduler_tool", "proactive_agent"),
        Map.entry("queue_tool", "proactive_agent"),
        Map.entry("crm_tool", "notion"),
        Map.entry("marketing_tool", "summarize"),
        Map.entry("communication_tool", "slack"),
        Map.entry("jira_tool", "jira"),
        Map.entry("ticket_tool", "notion"),
        Map.entry("calendar_tool", "notion"),
        Map.entry("document_tool", "office"),
        Map.entry("storage_tool", "notion"),
        Map.entry("legal_tool", "summarize"),
        Map.entry("audit_tool", "summarize"),
        Map.entry("workflow_tool", "proactive_agent"),
        Map.entry("api_tool", "browser_automation"),
        Map.entry("monitoring_tool", "proactive_agent"),
        Map.entry("cost_tool", "summarize"),
        Map.entry("hr_tool", "notion")
    );

    private void validateConfiguredTools() {
        if (toolRegistry == null) {
            return;
        }

        Set<String> registered = toolRegistry.getAll().stream()
            .map(Tool::getName)
            .collect(Collectors.toSet());

        int violationCount = 0;

        for (FixedEmployeeDefinition def : definitionsByCode.values()) {
            Set<String> departmentAllowed = DEPARTMENT_DEFAULT_TOOL_WHITELIST.getOrDefault(def.department(), Set.of());
            for (String configured : def.tools()) {
                String normalized = TOOL_ALIAS.getOrDefault(configured, configured);

                if (!registered.contains(normalized)) {
                    violationCount++;
                    log.warn("Fixed employee {} ({}) configured tool {} -> {} but target tool is not registered",
                        def.code(), def.name(), configured, normalized);
                }

                boolean allowed = SHARED_TOOL_NAMES.contains(normalized) || departmentAllowed.contains(normalized);
                if (!allowed) {
                    violationCount++;
                    log.warn("Fixed employee {} ({}) configured tool {} -> {} is outside department allowlist {}",
                        def.code(), def.name(), configured, normalized, def.department());
                }
            }
        }

        if (strictToolValidation && violationCount > 0) {
            throw new IllegalStateException("Fixed employee tool validation failed, violation count=" + violationCount);
        }

        if (!strictToolValidation && violationCount > 0) {
            log.warn("Fixed employee tool validation completed with {} violations (strict mode disabled)", violationCount);
        }
    }

    private List<Tool> loadToolsForEmployee(DigitalEmployee employee) {
        if (toolRegistry == null) {
            return List.of();
        }

        String department = employee.getDepartmentId() == null || employee.getDepartmentId().isBlank()
            ? employee.getDepartment()
            : employee.getDepartmentId();

        Set<String> departmentAllowed = DEPARTMENT_DEFAULT_TOOL_WHITELIST.getOrDefault(department, Set.of());

        // 优先使用 DigitalEmployee 自身的 tools 配置
        List<String> toolSources = employee.getTools();

        // 回退：如果 DigitalEmployee.tools 为空（可能 enterprise_employee 表与
        // fixed_employee_definition 表数据不同步），从 definitionsByCode 获取原始定义
        boolean usedFallback = false;
        if ((toolSources == null || toolSources.isEmpty()) && !definitionsByCode.isEmpty()) {
            // 通过 employeeId 反查 code → 获取 definition 的 tools
            String code = neuronIdToCode.get(employee.getNeuronId());
            if (code != null) {
                FixedEmployeeDefinition def = definitionsByCode.get(code);
                if (def != null && def.tools() != null && !def.tools().isEmpty()) {
                    toolSources = def.tools();
                    usedFallback = true;
                    log.info("loadToolsForEmployee: employee {} ({}) using fallback tools from definition ({} tools), "
                        + "DigitalEmployee.tools was empty. department={}",
                        employee.getEmployeeId(), employee.getName(), toolSources.size(), department);
                }
            }
        }

        Set<String> resolvedNames = new LinkedHashSet<>();
        for (String configured : toolSources) {
            String normalized = TOOL_ALIAS.getOrDefault(configured, configured);

            if (TOOL_ALIAS.containsKey(configured) && !configured.equals(normalized)) {
                log.info("Tool alias mapping applied for employee {} ({}): {} -> {}",
                    employee.getEmployeeId(), employee.getName(), configured, normalized);
            }

            boolean allowed = SHARED_TOOL_NAMES.contains(normalized) || departmentAllowed.contains(normalized);
            if (allowed) {
                resolvedNames.add(normalized);
            } else {
                log.warn("Employee {} ({}) configured tool {} is not allowed for department {}",
                    employee.getEmployeeId(), employee.getName(), configured, department);
            }
        }

        List<Tool> loaded = new ArrayList<>();
        for (String toolName : resolvedNames) {
            toolRegistry.get(toolName).ifPresent(loaded::add);
        }

        if (loaded.isEmpty() && !resolvedNames.isEmpty()) {
            // 有工具名但 ToolRegistry 中找不到（工具未注册）
            log.warn("Employee {} ({}) has {} resolved tool names but none found in ToolRegistry (tools not registered?): {}",
                employee.getEmployeeId(), employee.getName(), resolvedNames.size(), resolvedNames);
        } else if (loaded.isEmpty()) {
            log.warn("Employee {} ({}) has no resolved tools after authorization filtering (dept={}, allowed={}, sources={}, fallback={})",
                employee.getEmployeeId(), employee.getName(),
                department, departmentAllowed.size(), employee.getTools().size(), usedFallback);
        } else if (usedFallback) {
            log.info("Employee {} ({}) loaded {} tools via fallback from definition",
                employee.getEmployeeId(), employee.getName(), loaded.size());
        }

        return loaded;
    }

    private void registerTechEmployees() {
        String dept = "tech";
        String deptName = "技术部";
        
        registerDefinition("T01", "真砺", "代码审查员", dept, deptName,
            "neuron://tech/code-reviewer/001",
            List.of("代码质量审查", "PR审核", "代码规范检查"),
            List.of("code-review", "security-audit", "best-practices", "GitLab仓库管理", "GitLab MR审查"),
            List.of("gitlab", "github"),
            "channel://tech/code-review",
            EmployeePersonality.of(0.85, 0.5, 0.4, 0.8));

        registerDefinition("T02", "真构", "架构师", dept, deptName,
            "neuron://tech/architect/001",
            List.of("系统架构设计", "技术选型", "架构评审"),
            List.of("architecture", "system-design", "tech-selection", "GitLab仓库管理", "Jira项目管理"),
            List.of("gitlab", "jira"),
            "channel://tech/architecture",
            EmployeePersonality.of(0.9, 0.7, 0.5, 0.75));

        registerDefinition("T03", "真捷", "DevOps工程师", dept, deptName,
            "neuron://tech/devops/001",
            List.of("CI/CD流水线", "部署自动化", "环境管理", "Claude CLI 任务协调", "仓库级变更审查", "项目管理任务同步"),
            List.of("ci-cd", "deployment", "infrastructure", "cli-orchestration", "repo-review", "Jira项目管理"),
            List.of("jenkins", "docker", "gitlab", "claude_cli", "browser_automation", "jira"),
            "channel://tech/devops",
            EmployeePersonality.of(0.78, 0.65, 0.55, 0.72));
            
        registerDefinition("T04", "真稳", "运维工程师", dept, deptName,
            "neuron://tech/ops/001",
            List.of("心跳服务", "资源调度", "并发控制", "系统监控", "Claude CLI 运行协调", "运维事件关联项目管理"),
            List.of("heartbeat", "resource-scheduling", "concurrency-control", "ops-automation", "Jira项目管理"),
            List.of("proactive_agent", "docker", "claude_cli", "jira"),
            "channel://tech/ops",
            EmployeePersonality.of(0.82, 0.42, 0.52, 0.88));
            
        registerDefinition("T05", "真模", "AI模型管理员", dept, deptName,
            "neuron://tech/model-admin/001",
            List.of("适配器注册", "模型切换", "性能监控", "Claude CLI 模型评测", "提示词实验"),
            List.of("model-management", "adapter-registry", "performance-monitoring", "prompt-evaluation"),
            List.of("huggingface", "claude_cli"),
            "channel://tech/model",
            EmployeePersonality.of(0.88, 0.55, 0.45, 0.82));
            
        registerDefinition("T06", "真续", "状态管理员", dept, deptName,
            "neuron://tech/state-admin/001",
            List.of("会话管理", "状态持久化", "中断恢复"),
            List.of("session-management", "state-persistence", "recovery"),
            List.of("knowledge_graph"),
            "channel://tech/state",
            EmployeePersonality.of(0.9, 0.3, 0.3, 0.9));
            
        registerDefinition("T07", "真盾", "安全工程师", dept, deptName,
            "neuron://tech/security/001",
            List.of("沙箱执行", "资源限制", "安全隔离"),
            List.of("sandbox", "security", "isolation"),
            List.of("self_improving"),
            "channel://tech/security",
            EmployeePersonality.of(0.95, 0.3, 0.2, 0.95));
            
        registerDefinition("T08", "真策", "配置管理员", dept, deptName,
            "neuron://tech/config-admin/001",
            List.of("配置版本", "变更审计", "回滚支持"),
            List.of("config-management", "version-control", "audit"),
            List.of("notion"),
            "channel://tech/config",
            EmployeePersonality.of(0.9, 0.3, 0.3, 0.9));
            
        registerDefinition("T09", "真绘", "前端工程师", dept, deptName,
            "neuron://tech/frontend/001",
            List.of("前端开发", "UI交互", "用户体验", "前端任务状态同步"),
            List.of("frontend", "ui", "ux", "GitLab仓库管理", "GitLab MR审查", "Jira项目管理"),
            List.of("gitlab", "browser_automation", "jira"),
            "channel://tech/frontend",
            EmployeePersonality.of(0.7, 0.7, 0.5, 0.7));

        registerDefinition("T10", "真栈", "后端工程师", dept, deptName,
            "neuron://tech/backend/001",
            List.of("后端开发", "API设计", "数据库优化", "后端任务状态同步"),
            List.of("backend", "api", "database", "GitLab仓库管理", "GitLab MR审查", "Jira项目管理"),
            List.of("gitlab", "knowledge_graph", "jira"),
            "channel://tech/backend",
            EmployeePersonality.of(0.8, 0.6, 0.5, 0.75));
    }

    private void registerFinanceEmployees() {
        String dept = "finance";
        String deptName = "财务部";
        
        registerDefinition("F01", "真账", "财务会计", dept, deptName,
            "neuron://finance/accountant/001",
            List.of("账务处理", "财务报表", "税务申报"),
            List.of("accounting", "financial-reports", "tax"),
            List.of("invoice_processing"),
            "channel://finance/accounting",
            EmployeePersonality.of(0.95, 0.2, 0.2, 0.98));
            
        registerDefinition("F02", "真审", "报销审核员", dept, deptName,
            "neuron://finance/auditor/001",
            List.of("报销审批", "发票核验", "合规检查"),
            List.of("expense-audit", "invoice-verification", "compliance"),
            List.of("invoice_processing", "browser_automation"),
            "channel://finance/audit",
            EmployeePersonality.of(0.95, 0.2, 0.2, 0.98));
            
        registerDefinition("F03", "真算", "成本核算员", dept, deptName,
            "neuron://finance/cost-accountant/001",
            List.of("Token成本估算", "项目独立核算", "成本分析"),
            List.of("cost-estimation", "project-accounting", "cost-analysis"),
            List.of("summarize"),
            "channel://finance/cost",
            EmployeePersonality.of(0.9, 0.3, 0.2, 0.95));
            
        registerDefinition("F04", "真预", "预算管理员", dept, deptName,
            "neuron://finance/budget-admin/001",
            List.of("月度预算管理", "超支预警", "预算报告"),
            List.of("budget-management", "alert", "reporting"),
            List.of("budget_management"),
            "channel://finance/budget",
            EmployeePersonality.of(0.9, 0.3, 0.2, 0.95));
    }

    private void registerOpsEmployees() {
        String dept = "ops";
        String deptName = "运营部";
        
        registerDefinition("O01", "真析", "数据分析师", dept, deptName,
            "neuron://ops/analyst/001",
            List.of("数据分析", "报表生成", "趋势预测"),
            List.of("data-analysis", "reporting", "forecasting"),
            List.of("summarize"),
            "channel://ops/analysis",
            EmployeePersonality.of(0.75, 0.6, 0.5, 0.7));
            
        registerDefinition("O02", "真营", "运营专员", dept, deptName,
            "neuron://ops/operator/001",
            List.of("日常运营", "活动策划", "用户运营"),
            List.of("operations", "campaign", "user-engagement"),
            List.of("notion", "summarize"),
            "channel://ops/daily",
            EmployeePersonality.of(0.6, 0.7, 0.5, 0.7));
            
        registerDefinition("O03", "真度", "任务调度员", dept, deptName,
            "neuron://ops/scheduler/001",
            List.of("任务检出", "原子分配", "冲突避免"),
            List.of("task-scheduling", "assignment", "conflict-resolution"),
            List.of("proactive_agent"),
            "channel://ops/schedule",
            EmployeePersonality.of(0.8, 0.4, 0.4, 0.85));
            
        registerDefinition("O04", "真流", "流程管理员", dept, deptName,
            "neuron://ops/process-admin/001",
            List.of("运行队列", "并发控制", "优先级调度"),
            List.of("process-management", "queue-management", "priority-scheduling"),
            List.of("proactive_agent"),
            "channel://ops/process",
            EmployeePersonality.of(0.85, 0.4, 0.4, 0.85));
    }

    private void registerSalesEmployees() {
        String dept = "sales";
        String deptName = "销售部";
        
        registerDefinition("S01", "真拓", "销售代表", dept, deptName,
            "neuron://sales/representative/001",
            List.of("客户开发", "销售跟进", "合同签订"),
            List.of("sales", "customer-development", "contract"),
            List.of("notion", "slack"),
            "channel://sales/reps",
            EmployeePersonality.of(0.5, 0.7, 0.6, 0.6));
            
        registerDefinition("S02", "真宣", "市场专员", dept, deptName,
            "neuron://sales/marketer/001",
            List.of("市场调研", "营销推广", "品牌建设"),
            List.of("marketing", "research", "branding"),
            List.of("summarize", "searxng"),
            "channel://sales/market",
            EmployeePersonality.of(0.5, 0.8, 0.6, 0.6));
            
        registerDefinition("S03", "真联", "渠道经理", dept, deptName,
            "neuron://sales/channel-manager/001",
            List.of("平台集成", "GitHub/Upwork对接", "渠道管理"),
            List.of("platform-integration", "channel-management", "github", "upwork"),
            List.of("github", "browser_automation"),
            "channel://sales/channel",
            EmployeePersonality.of(0.6, 0.7, 0.6, 0.7));
    }

    private void registerHrEmployees() {
        String dept = "hr";
        String deptName = "人力资源";
        
        registerDefinition("H01", "真才", "招聘专员", dept, deptName,
            "neuron://hr/recruiter/001",
            List.of("招聘管理", "人才筛选", "面试安排"),
            List.of("recruitment", "candidate-screening", "interview"),
            List.of("notion", "slack"),
            "channel://hr/recruit",
            EmployeePersonality.of(0.6, 0.5, 0.4, 0.8));
            
        registerDefinition("H02", "真绩", "绩效管理员", dept, deptName,
            "neuron://hr/performance/001",
            List.of("绩效考核", "培训管理", "员工发展"),
            List.of("performance", "training", "development"),
            List.of("notion", "summarize"),
            "channel://hr/performance",
            EmployeePersonality.of(0.7, 0.4, 0.3, 0.85));
    }

    private void registerCsEmployees() {
        String dept = "cs";
        String deptName = "客服部";
        
        registerDefinition("C01", "真晴", "客服专员", dept, deptName,
            "neuron://cs/agent/001",
            List.of("客户咨询", "问题解答", "投诉处理"),
            List.of("customer-service", "inquiry", "complaint"),
            List.of("notion", "slack"),
            "channel://cs/support",
            EmployeePersonality.of(0.6, 0.5, 0.4, 0.7));
            
        registerDefinition("C02", "真修", "工单处理员", dept, deptName,
            "neuron://cs/ticket-handler/001",
            List.of("工单处理", "问题跟踪", "服务升级"),
            List.of("ticket-management", "issue-tracking", "escalation", "Jira工单管理"),
            List.of("notion", "jira"),
            "channel://cs/ticket",
            EmployeePersonality.of(0.7, 0.4, 0.4, 0.8));
    }

    private void registerAdminEmployees() {
        String dept = "admin";
        String deptName = "行政部";
        
        registerDefinition("A01", "真序", "行政助理", dept, deptName,
            "neuron://admin/assistant/001",
            List.of("行政事务", "日程管理", "会议安排"),
            List.of("administration", "scheduling", "meeting"),
            List.of("notion", "slack"),
            "channel://admin/affairs",
            EmployeePersonality.of(0.7, 0.4, 0.3, 0.9));
            
        registerDefinition("A02", "真典", "文档管理员", dept, deptName,
            "neuron://admin/doc-manager/001",
            List.of("文档管理", "档案维护", "知识归档"),
            List.of("document-management", "archiving", "knowledge-base"),
            List.of("office", "notion"),
            "channel://admin/docs",
            EmployeePersonality.of(0.8, 0.3, 0.3, 0.9));
            
        registerDefinition("A03", "真笔", "文案策划", dept, deptName,
            "neuron://admin/copywriter/001",
            List.of("文案创作", "内容策划", "品牌传播"),
            List.of("copywriting", "content-planning", "branding"),
            List.of("office", "summarize"),
            "channel://admin/content",
            EmployeePersonality.of(0.5, 0.8, 0.5, 0.7));
    }

    private void registerLegalEmployees() {
        String dept = "legal";
        String deptName = "法务部";
        
        registerDefinition("L01", "真律", "合同审查员", dept, deptName,
            "neuron://legal/contract-reviewer/001",
            List.of("合同审查", "风险识别", "条款建议"),
            List.of("contract-review", "risk-assessment", "legal-advice"),
            List.of("office", "summarize"),
            "channel://legal/contract",
            EmployeePersonality.of(0.95, 0.2, 0.1, 0.98));
            
        registerDefinition("L02", "真规", "合规专员", dept, deptName,
            "neuron://legal/compliance/001",
            List.of("合规检查", "政策解读", "风险预警"),
            List.of("compliance", "policy", "risk-management"),
            List.of("summarize"),
            "channel://legal/compliance",
            EmployeePersonality.of(0.95, 0.2, 0.1, 0.98));
    }

    private void registerMainEmployees() {
        String dept = "main";
        String deptName = "跨部门协调";
        
        registerDefinition("M01", "真合", "协调员", dept, deptName,
            "neuron://main/coordinator/001",
            List.of("跨部门协调", "资源调配", "冲突解决"),
            List.of("coordination", "resource-allocation", "conflict-resolution"),
            List.of("slack", "proactive_agent"),
            "channel://main/coord",
            EmployeePersonality.of(0.7, 0.5, 0.4, 0.85));
            
        registerDefinition("M02", "真略", "战略规划师", dept, deptName,
            "neuron://main/strategist/001",
            List.of("战略规划", "决策支持", "目标管理"),
            List.of("strategy", "decision-support", "goal-management"),
            List.of("summarize"),
            "channel://main/strategy",
            EmployeePersonality.of(0.7, 0.5, 0.4, 0.85));
    }

    private void registerDefinition(String code, String name, String title, 
                                   String dept, String deptName,
                                   String neuronId, 
                                   List<String> roles, List<String> capabilities, List<String> tools,
                                   String channel,
                                   EmployeePersonality personality) {
        registerDefinition(code, name, title, dept, deptName, neuronId,
            roles, capabilities, tools, channel, personality, List.of());
    }

    private void registerDefinition(String code, String name, String title, 
                                   String dept, String deptName,
                                   String neuronId, 
                                   List<String> roles, List<String> capabilities, List<String> tools,
                                   String channel,
                                   EmployeePersonality personality,
                                   List<String> requiredSkills) {
        
        List<String> allSkills = new java.util.ArrayList<>(List.of(
            "tavily-search", "find-skills", "proactive-agent", "weather"
        ));
        if (requiredSkills != null) {
            allSkills.addAll(requiredSkills);
        }
        
        FixedEmployeeDefinition definition = new FixedEmployeeDefinition(
            code, name, title, dept, deptName, neuronId,
            roles, capabilities, tools, channel, personality,
            getIconForDepartment(dept),
            allSkills
        );
        
        definitionsByCode.put(code, definition);
        neuronIdToCode.put(neuronId, code);
        codeToNeuronId.put(code, neuronId);
        
        log.debug("Registered fixed employee definition: {} ({}) - {} with {} skills", 
            code, name, neuronId, allSkills.size());
    }

    private FixedEmployeeDefinition toDefinition(FixedEmployeeDefinitionEntity entity) {
        String department = defaultString(entity.getDepartmentCode(), "main");
        String name = defaultString(entity.getNameZh(), entity.getNameEn(), entity.getCode());
        String title = defaultString(entity.getTitleZh(), entity.getTitleEn(), name);
        String neuronId = defaultString(entity.getNeuronId(), "neuron://" + department + "/" + entity.getCode().toLowerCase(Locale.ROOT) + "/001");
        String channel = defaultString(entity.getChannel(), "channel://" + department + "/" + entity.getCode().toLowerCase(Locale.ROOT));
        EmployeePersonality personality = parsePersonality(entity.getPersonality(), department);
        List<String> requiredSkills = new ArrayList<>(List.of("tavily-search", "find-skills", "proactive-agent", "weather"));
        requiredSkills.addAll(parseStringList(entity.getRequiredSkills()));

        return new FixedEmployeeDefinition(
            entity.getCode(),
            name,
            title,
            department,
            defaultString(entity.getDepartmentName(), department),
            neuronId,
            parseStringList(entity.getRoles()),
            parseStringList(entity.getCapabilities()),
            parseStringList(entity.getTools()),
            channel,
            personality,
            getIconForDepartment(department),
            requiredSkills.stream().distinct().toList()
        );
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse fixed employee JSON list: {}", json, e);
            return List.of();
        }
    }

    private EmployeePersonality parsePersonality(String json, String department) {
        if (json == null || json.isBlank()) {
            return EmployeePersonality.defaultForDepartment(department);
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            double rigor = readDouble(node, "rigor", "conscientiousness", EmployeePersonality.defaultForDepartment(department).rigor());
            double creativity = readDouble(node, "creativity", "openness", EmployeePersonality.defaultForDepartment(department).creativity());
            double riskTolerance = readDouble(node, "riskTolerance", "risk_tolerance", EmployeePersonality.defaultForDepartment(department).riskTolerance());
            double obedience = readDouble(node, "obedience", "agreeableness", EmployeePersonality.defaultForDepartment(department).obedience());
            return EmployeePersonality.of(rigor, creativity, riskTolerance, obedience, EmployeePersonality.PersonalitySource.MANUAL);
        } catch (Exception e) {
            log.warn("Failed to parse fixed employee personality JSON: {}", json, e);
            return EmployeePersonality.defaultForDepartment(department);
        }
    }

    private double readDouble(JsonNode node, String primary, String fallback, double defaultValue) {
        if (node.has(primary) && node.get(primary).isNumber()) {
            return node.get(primary).asDouble();
        }
        if (node.has(fallback) && node.get(fallback).isNumber()) {
            return node.get(fallback).asDouble();
        }
        return defaultValue;
    }

    private String defaultString(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String getIconForDepartment(String dept) {
        return switch (dept) {
            case "tech" -> "💻";
            case "finance" -> "💰";
            case "ops" -> "📊";
            case "sales" -> "📈";
            case "hr" -> "👥";
            case "cs" -> "🎧";
            case "admin" -> "📋";
            case "legal" -> "⚖️";
            case "main" -> "🎯";
            default -> "🤖";
        };
    }

    public Optional<Employee> getEmployeeByNeuronId(String neuronId) {
        String employeeId = IdUtils.neuronToEmployeeId(neuronId);
        return employeeService.getEmployee(employeeId);
    }

    public Optional<Employee> getEmployeeByCode(String code) {
        String neuronId = codeToNeuronId.get(code);
        if (neuronId == null) {
            return Optional.empty();
        }
        return getEmployeeByNeuronId(neuronId);
    }

    public Optional<Neuron> getNeuronByEmployeeId(String employeeId) {
        return Optional.ofNullable(employeeNeurons.get(employeeId));
    }

    public Optional<Neuron> getNeuronByNeuronId(String neuronId) {
        String employeeId = IdUtils.neuronToEmployeeId(neuronId);
        return getNeuronByEmployeeId(employeeId);
    }

    public List<Employee> getEmployeesByDepartment(String department) {
        return employeeService.listByDepartment(department);
    }

    public List<FixedEmployeeDefinition> getAllDefinitions() {
        return new ArrayList<>(definitionsByCode.values());
    }

    public List<FixedEmployeeDefinition> getDefinitionsByDepartment(String department) {
        return definitionsByCode.values().stream()
            .filter(d -> d.department().equals(department))
            .toList();
    }

    public Optional<FixedEmployeeDefinition> getDefinitionByCode(String code) {
        return Optional.ofNullable(definitionsByCode.get(code));
    }

    public int getDefinitionCount() {
        return definitionsByCode.size();
    }

    public int getActiveEmployeeCount() {
        return employeeNeurons.size();
    }

    public Employee createFixedEmployee(String code) {
        FixedEmployeeDefinition def = definitionsByCode.get(code);
        if (def == null) {
            throw new IllegalArgumentException("Unknown fixed employee code: " + code);
        }
        
        String expectedEmployeeId = IdUtils.neuronToEmployeeId(def.neuronId());

        EmployeeService.EmployeeCreationRequest request = new EmployeeService.EmployeeCreationRequest(
            IdUtils.EmployeeType.DIGITAL,
            "system",
            def.code(),
            def.name(),
            def.title(),
            def.icon(),
            def.departmentName(),
            def.department(),
            def.roles(),
            null,
            def.capabilities(),
            List.of(),
            def.tools(),
            def.personality(),
            null,
            List.of(def.channel()),
            List.of(),
            List.of(),
            null,
            null,
            EmployeeOrigin.FIXED,
            expectedEmployeeId
        );
        
        Employee employee = employeeService.createEmployee(request);
        log.info("Created fixed employee from definition: {} -> {}", code, employee.getEmployeeId());
        
        return employee;
    }

    private void validateDelegateBrainBindings() {
        int totalNeurons = employeeNeurons.size();
        int boundCount = 0;
        int newlyBoundCount = 0;
        int unboundCount = 0;
        int mismatchCount = 0;
        List<String> unboundList = new ArrayList<>();
        List<String> mismatchList = new ArrayList<>();

        for (Map.Entry<String, Neuron> entry : employeeNeurons.entrySet()) {
            String employeeId = entry.getKey();
            Neuron neuron = entry.getValue();

            if (neuron instanceof EmployeeNeuron en) {
                // 尝试延迟绑定：如果 delegateBrain 为 null，从 brainRegistry 查找并绑定
                boolean bound = en.bindBrain(brainRegistry);
                if (bound) {
                    newlyBoundCount++;
                    boundCount++;
                } else if (en.getDelegateBrain() != null) {
                    boundCount++;
                } else {
                    unboundCount++;
                    unboundList.add(employeeId);
                    log.warn("EmployeeNeuron {} delegateBrain is null, no brain found for its department", employeeId);
                }

                // 校验员工部门与绑定大脑的部门是否匹配
                if (en.getDelegateBrain() != null) {
                    String employeeDept = en.getEmployee() != null ? en.getEmployee().getDepartmentId() : null;
                    if (employeeDept == null && en.getEmployee() != null) {
                        employeeDept = en.getEmployee().getDepartment();
                    }
                    String brainDept = en.getDelegateBrain().getDepartment();
                    if (employeeDept != null && brainDept != null && !employeeDept.equals(brainDept)) {
                        mismatchCount++;
                        mismatchList.add(employeeId + "(employeeDept=" + employeeDept + ", brainDept=" + brainDept + ")");
                        log.warn("delegateBrain mismatch: employee={} dept='{}' but bound to brain dept='{}'",
                            employeeId, employeeDept, brainDept);
                    }
                }
            }
        }

        log.info("==== delegateBrain binding report ====");
        log.info("Total neurons: {}", totalNeurons);
        log.info("Already bound: {}", boundCount - newlyBoundCount);
        log.info("Newly bound (deferred): {}", newlyBoundCount);
        log.info("Unbound: {}", unboundCount);
        log.info("Dept mismatch: {}", mismatchCount);

        if (unboundCount > 0) {
            log.warn("Unbound neurons (no brain for department): {}", unboundList);
        }
        if (mismatchCount > 0) {
            log.warn("Dept mismatch neurons (employee dept != brain dept): {}", mismatchList);
        }
        if (unboundCount == 0 && mismatchCount == 0) {
            log.info("All fixed employee neurons successfully bound to correct brains");
        }
    }

    public Map<String, List<FixedEmployeeDefinition>> getDefinitionsGroupedByDepartment() {
        return definitionsByCode.values().stream()
            .collect(Collectors.groupingBy(FixedEmployeeDefinition::department));
    }

    public FixedEmployeeSummary getSummary() {
        Map<String, Integer> countByDept = definitionsByCode.values().stream()
            .collect(Collectors.groupingBy(
                FixedEmployeeDefinition::department,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
            ));
        
        int activeCount = (int) employeeNeurons.values().stream()
            .filter(n -> n.getState() == com.livingagent.core.neuron.NeuronState.RUNNING)
            .count();
        
        return new FixedEmployeeSummary(
            definitionsByCode.size(),
            activeCount,
            (int) definitionsByCode.values().stream().map(FixedEmployeeDefinition::department).distinct().count(),
            countByDept
        );
    }

    public record FixedEmployeeDefinition(
        String code,
        String name,
        String title,
        String department,
        String departmentName,
        String neuronId,
        List<String> roles,
        List<String> capabilities,
        List<String> tools,
        String channel,
        EmployeePersonality personality,
        String icon,
        List<String> requiredSkills
    ) {
        public boolean hasCapability(String capability) {
            return capabilities != null && capabilities.contains(capability);
        }

        public boolean hasTool(String toolId) {
            return tools != null && tools.contains(toolId);
        }

        public boolean hasSkill(String skillId) {
            return requiredSkills != null && requiredSkills.contains(skillId);
        }

        public boolean canPerformTask(String requiredCapability) {
            return hasCapability(requiredCapability);
        }

        public void validateCapability(String capability) {
            if (!hasCapability(capability)) {
                throw new IllegalStateException(
                    String.format("编制 '%s' (%s) 不具备能力 '%s'，无法执行此操作", 
                        name, code, capability)
                );
            }
        }

        public void validateTool(String toolId) {
            if (!hasTool(toolId)) {
                throw new IllegalStateException(
                    String.format("编制 '%s' (%s) 未授权使用工具 '%s'", 
                        name, code, toolId)
                );
            }
        }

        public void validateSkill(String skillId) {
            if (!hasSkill(skillId)) {
                throw new IllegalStateException(
                    String.format("编制 '%s' (%s) 未配置技能 '%s'", 
                        name, code, skillId)
                );
            }
        }

        public static FixedEmployeeDefinition withDefaultSkills(
            String code, String name, String title,
            String department, String departmentName, String neuronId,
            List<String> roles, List<String> capabilities, List<String> tools,
            String channel, EmployeePersonality personality, String icon
        ) {
            List<String> defaultSkills = List.of(
                "tavily-search", "find-skills", "proactive-agent", "weather"
            );
            return new FixedEmployeeDefinition(
                code, name, title, department, departmentName, neuronId,
                roles, capabilities, tools, channel, personality, icon, defaultSkills
            );
        }
    }

    public record FixedEmployeeSummary(
        int totalDefinitions,
        int activeEmployees,
        int departmentCount,
        Map<String, Integer> countByDepartment
    ) {}
}
