package com.livingagent.core.employee.registry;

import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.employee.*;
import com.livingagent.core.employee.impl.DigitalEmployee;
import com.livingagent.core.employee.neuron.EmployeeNeuron;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.skill.SkillRegistry;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.util.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    
    private final Map<String, FixedEmployeeDefinition> definitionsByCode = new ConcurrentHashMap<>();
    private final Map<String, String> neuronIdToCode = new ConcurrentHashMap<>();
    private final Map<String, String> codeToNeuronId = new ConcurrentHashMap<>();
    private final Map<String, Neuron> employeeNeurons = new ConcurrentHashMap<>();

    public FixedEmployeeRegistry(
            EmployeeService employeeService, 
            NeuronRegistry neuronRegistry,
            BrainRegistry brainRegistry,
            ChannelManager channelManager,
            SkillRegistry skillRegistry) {
        this.employeeService = employeeService;
        this.neuronRegistry = neuronRegistry;
        this.brainRegistry = brainRegistry;
        this.channelManager = channelManager;
        this.skillRegistry = skillRegistry;
    }

    @PostConstruct
    public void init() {
        registerAllFixedEmployees();
        createAndStartAllEmployees();
        log.info("FixedEmployeeRegistry initialized with {} fixed employees", employeeNeurons.size());
    }

    public void registerAllFixedEmployees() {
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

    private void createAndStartAllEmployees() {
        for (String code : definitionsByCode.keySet()) {
            try {
                Optional<Employee> existing = getEmployeeByCode(code);
                if (existing.isEmpty()) {
                    Employee employee = createFixedEmployee(code);
                    createAndStartNeuron(employee);
                    log.info("Created and started fixed employee: {} -> {}", code, employee.getEmployeeId());
                } else {
                    Employee employee = existing.get();
                    createAndStartNeuron(employee);
                    log.debug("Fixed employee {} already exists, ensured neuron started", code);
                }
            } catch (Exception e) {
                log.error("Failed to create fixed employee: {}", code, e);
            }
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

    private List<Tool> loadToolsForEmployee(DigitalEmployee employee) {
        return List.of();
    }

    private void registerTechEmployees() {
        String dept = "tech";
        String deptName = "技术部";
        
        registerDefinition("T01", "CodeReviewer", "代码审查员", dept, deptName,
            "neuron://tech/code-reviewer/001",
            List.of("代码质量审查", "PR审核", "代码规范检查"),
            List.of("code-review", "security-audit", "best-practices"),
            List.of("gitlab_tool", "github_tool"),
            "channel://tech/code-review",
            EmployeePersonality.of(0.85, 0.5, 0.4, 0.8));
            
        registerDefinition("T02", "Architect", "架构师", dept, deptName,
            "neuron://tech/architect/001",
            List.of("系统架构设计", "技术选型", "架构评审"),
            List.of("architecture", "system-design", "tech-selection"),
            List.of("gitlab_tool", "jira_tool"),
            "channel://tech/architecture",
            EmployeePersonality.of(0.9, 0.7, 0.5, 0.75));
            
        registerDefinition("T03", "DevOpsEngineer", "DevOps工程师", dept, deptName,
            "neuron://tech/devops/001",
            List.of("CI/CD流水线", "部署自动化", "环境管理"),
            List.of("ci-cd", "deployment", "infrastructure"),
            List.of("jenkins_tool", "docker_tool", "gitlab_tool"),
            "channel://tech/devops",
            EmployeePersonality.of(0.75, 0.6, 0.5, 0.7));
            
        registerDefinition("T04", "OpsEngineer", "运维工程师", dept, deptName,
            "neuron://tech/ops/001",
            List.of("心跳服务", "资源调度", "并发控制", "系统监控"),
            List.of("heartbeat", "resource-scheduling", "concurrency-control"),
            List.of("monitoring_tool", "docker_tool"),
            "channel://tech/ops",
            EmployeePersonality.of(0.8, 0.4, 0.5, 0.85));
            
        registerDefinition("T05", "ModelAdmin", "AI模型管理员", dept, deptName,
            "neuron://tech/model-admin/001",
            List.of("适配器注册", "模型切换", "性能监控"),
            List.of("model-management", "adapter-registry", "performance-monitoring"),
            List.of("model_tool"),
            "channel://tech/model",
            EmployeePersonality.of(0.85, 0.5, 0.4, 0.8));
            
        registerDefinition("T06", "StateAdmin", "状态管理员", dept, deptName,
            "neuron://tech/state-admin/001",
            List.of("会话管理", "状态持久化", "中断恢复"),
            List.of("session-management", "state-persistence", "recovery"),
            List.of("database_tool"),
            "channel://tech/state",
            EmployeePersonality.of(0.9, 0.3, 0.3, 0.9));
            
        registerDefinition("T07", "SecurityEngineer", "安全工程师", dept, deptName,
            "neuron://tech/security/001",
            List.of("沙箱执行", "资源限制", "安全隔离"),
            List.of("sandbox", "security", "isolation"),
            List.of("security_tool"),
            "channel://tech/security",
            EmployeePersonality.of(0.95, 0.3, 0.2, 0.95));
            
        registerDefinition("T08", "ConfigAdmin", "配置管理员", dept, deptName,
            "neuron://tech/config-admin/001",
            List.of("配置版本", "变更审计", "回滚支持"),
            List.of("config-management", "version-control", "audit"),
            List.of("config_tool"),
            "channel://tech/config",
            EmployeePersonality.of(0.9, 0.3, 0.3, 0.9));
            
        registerDefinition("T09", "FrontendEngineer", "前端工程师", dept, deptName,
            "neuron://tech/frontend/001",
            List.of("前端开发", "UI交互", "用户体验"),
            List.of("frontend", "ui", "ux"),
            List.of("gitlab_tool", "npm_tool"),
            "channel://tech/frontend",
            EmployeePersonality.of(0.7, 0.7, 0.5, 0.7));
            
        registerDefinition("T10", "BackendEngineer", "后端工程师", dept, deptName,
            "neuron://tech/backend/001",
            List.of("后端开发", "API设计", "数据库优化"),
            List.of("backend", "api", "database"),
            List.of("gitlab_tool", "database_tool"),
            "channel://tech/backend",
            EmployeePersonality.of(0.8, 0.6, 0.5, 0.75));
    }

    private void registerFinanceEmployees() {
        String dept = "finance";
        String deptName = "财务部";
        
        registerDefinition("F01", "Accountant", "财务会计", dept, deptName,
            "neuron://finance/accountant/001",
            List.of("账务处理", "财务报表", "税务申报"),
            List.of("accounting", "financial-reports", "tax"),
            List.of("erp_tool", "finance_tool"),
            "channel://finance/accounting",
            EmployeePersonality.of(0.95, 0.2, 0.2, 0.98));
            
        registerDefinition("F02", "Auditor", "报销审核员", dept, deptName,
            "neuron://finance/auditor/001",
            List.of("报销审批", "发票核验", "合规检查"),
            List.of("expense-audit", "invoice-verification", "compliance"),
            List.of("erp_tool", "ocr_tool"),
            "channel://finance/audit",
            EmployeePersonality.of(0.95, 0.2, 0.2, 0.98));
            
        registerDefinition("F03", "CostAccountant", "成本核算员", dept, deptName,
            "neuron://finance/cost-accountant/001",
            List.of("Token成本估算", "项目独立核算", "成本分析"),
            List.of("cost-estimation", "project-accounting", "cost-analysis"),
            List.of("cost_tool", "analytics_tool"),
            "channel://finance/cost",
            EmployeePersonality.of(0.9, 0.3, 0.2, 0.95));
            
        registerDefinition("F04", "BudgetAdmin", "预算管理员", dept, deptName,
            "neuron://finance/budget-admin/001",
            List.of("月度预算管理", "超支预警", "预算报告"),
            List.of("budget-management", "alert", "reporting"),
            List.of("budget_tool"),
            "channel://finance/budget",
            EmployeePersonality.of(0.9, 0.3, 0.2, 0.95));
    }

    private void registerOpsEmployees() {
        String dept = "ops";
        String deptName = "运营部";
        
        registerDefinition("O01", "DataAnalyst", "数据分析师", dept, deptName,
            "neuron://ops/analyst/001",
            List.of("数据分析", "报表生成", "趋势预测"),
            List.of("data-analysis", "reporting", "forecasting"),
            List.of("analytics_tool", "bi_tool"),
            "channel://ops/analysis",
            EmployeePersonality.of(0.75, 0.6, 0.5, 0.7));
            
        registerDefinition("O02", "Operator", "运营专员", dept, deptName,
            "neuron://ops/operator/001",
            List.of("日常运营", "活动策划", "用户运营"),
            List.of("operations", "campaign", "user-engagement"),
            List.of("crm_tool", "marketing_tool"),
            "channel://ops/daily",
            EmployeePersonality.of(0.6, 0.7, 0.5, 0.7));
            
        registerDefinition("O03", "TaskScheduler", "任务调度员", dept, deptName,
            "neuron://ops/scheduler/001",
            List.of("任务检出", "原子分配", "冲突避免"),
            List.of("task-scheduling", "assignment", "conflict-resolution"),
            List.of("scheduler_tool"),
            "channel://ops/schedule",
            EmployeePersonality.of(0.8, 0.4, 0.4, 0.85));
            
        registerDefinition("O04", "ProcessAdmin", "流程管理员", dept, deptName,
            "neuron://ops/process-admin/001",
            List.of("运行队列", "并发控制", "优先级调度"),
            List.of("process-management", "queue-management", "priority-scheduling"),
            List.of("queue_tool"),
            "channel://ops/process",
            EmployeePersonality.of(0.85, 0.4, 0.4, 0.85));
    }

    private void registerSalesEmployees() {
        String dept = "sales";
        String deptName = "销售部";
        
        registerDefinition("S01", "SalesRepresentative", "销售代表", dept, deptName,
            "neuron://sales/representative/001",
            List.of("客户开发", "销售跟进", "合同签订"),
            List.of("sales", "customer-development", "contract"),
            List.of("crm_tool", "communication_tool"),
            "channel://sales/reps",
            EmployeePersonality.of(0.5, 0.7, 0.6, 0.6));
            
        registerDefinition("S02", "Marketer", "市场专员", dept, deptName,
            "neuron://sales/marketer/001",
            List.of("市场调研", "营销推广", "品牌建设"),
            List.of("marketing", "research", "branding"),
            List.of("marketing_tool", "analytics_tool"),
            "channel://sales/market",
            EmployeePersonality.of(0.5, 0.8, 0.6, 0.6));
            
        registerDefinition("S03", "ChannelManager", "渠道经理", dept, deptName,
            "neuron://sales/channel-manager/001",
            List.of("平台集成", "GitHub/Upwork对接", "渠道管理"),
            List.of("platform-integration", "channel-management", "github", "upwork"),
            List.of("github_tool", "api_tool"),
            "channel://sales/channel",
            EmployeePersonality.of(0.6, 0.7, 0.6, 0.7));
    }

    private void registerHrEmployees() {
        String dept = "hr";
        String deptName = "人力资源";
        
        registerDefinition("H01", "Recruiter", "招聘专员", dept, deptName,
            "neuron://hr/recruiter/001",
            List.of("招聘管理", "人才筛选", "面试安排"),
            List.of("recruitment", "candidate-screening", "interview"),
            List.of("hr_tool", "communication_tool"),
            "channel://hr/recruit",
            EmployeePersonality.of(0.6, 0.5, 0.4, 0.8));
            
        registerDefinition("H02", "PerformanceAdmin", "绩效管理员", dept, deptName,
            "neuron://hr/performance/001",
            List.of("绩效考核", "培训管理", "员工发展"),
            List.of("performance", "training", "development"),
            List.of("hr_tool", "analytics_tool"),
            "channel://hr/performance",
            EmployeePersonality.of(0.7, 0.4, 0.3, 0.85));
    }

    private void registerCsEmployees() {
        String dept = "cs";
        String deptName = "客服部";
        
        registerDefinition("C01", "CustomerServiceAgent", "客服专员", dept, deptName,
            "neuron://cs/agent/001",
            List.of("客户咨询", "问题解答", "投诉处理"),
            List.of("customer-service", "inquiry", "complaint"),
            List.of("crm_tool", "communication_tool"),
            "channel://cs/support",
            EmployeePersonality.of(0.6, 0.5, 0.4, 0.7));
            
        registerDefinition("C02", "TicketHandler", "工单处理员", dept, deptName,
            "neuron://cs/ticket-handler/001",
            List.of("工单处理", "问题跟踪", "服务升级"),
            List.of("ticket-management", "issue-tracking", "escalation"),
            List.of("ticket_tool", "jira_tool"),
            "channel://cs/ticket",
            EmployeePersonality.of(0.7, 0.4, 0.4, 0.8));
    }

    private void registerAdminEmployees() {
        String dept = "admin";
        String deptName = "行政部";
        
        registerDefinition("A01", "AdminAssistant", "行政助理", dept, deptName,
            "neuron://admin/assistant/001",
            List.of("行政事务", "日程管理", "会议安排"),
            List.of("administration", "scheduling", "meeting"),
            List.of("calendar_tool", "communication_tool"),
            "channel://admin/affairs",
            EmployeePersonality.of(0.7, 0.4, 0.3, 0.9));
            
        registerDefinition("A02", "DocumentManager", "文档管理员", dept, deptName,
            "neuron://admin/doc-manager/001",
            List.of("文档管理", "档案维护", "知识归档"),
            List.of("document-management", "archiving", "knowledge-base"),
            List.of("document_tool", "storage_tool"),
            "channel://admin/docs",
            EmployeePersonality.of(0.8, 0.3, 0.3, 0.9));
            
        registerDefinition("A03", "Copywriter", "文案策划", dept, deptName,
            "neuron://admin/copywriter/001",
            List.of("文案创作", "内容策划", "品牌传播"),
            List.of("copywriting", "content-planning", "branding"),
            List.of("document_tool", "marketing_tool"),
            "channel://admin/content",
            EmployeePersonality.of(0.5, 0.8, 0.5, 0.7));
    }

    private void registerLegalEmployees() {
        String dept = "legal";
        String deptName = "法务部";
        
        registerDefinition("L01", "ContractReviewer", "合同审查员", dept, deptName,
            "neuron://legal/contract-reviewer/001",
            List.of("合同审查", "风险识别", "条款建议"),
            List.of("contract-review", "risk-assessment", "legal-advice"),
            List.of("document_tool", "legal_tool"),
            "channel://legal/contract",
            EmployeePersonality.of(0.95, 0.2, 0.1, 0.98));
            
        registerDefinition("L02", "ComplianceOfficer", "合规专员", dept, deptName,
            "neuron://legal/compliance/001",
            List.of("合规检查", "政策解读", "风险预警"),
            List.of("compliance", "policy", "risk-management"),
            List.of("legal_tool", "audit_tool"),
            "channel://legal/compliance",
            EmployeePersonality.of(0.95, 0.2, 0.1, 0.98));
    }

    private void registerMainEmployees() {
        String dept = "main";
        String deptName = "跨部门协调";
        
        registerDefinition("M01", "Coordinator", "协调员", dept, deptName,
            "neuron://main/coordinator/001",
            List.of("跨部门协调", "资源调配", "冲突解决"),
            List.of("coordination", "resource-allocation", "conflict-resolution"),
            List.of("communication_tool", "workflow_tool"),
            "channel://main/coord",
            EmployeePersonality.of(0.7, 0.5, 0.4, 0.85));
            
        registerDefinition("M02", "Strategist", "战略规划师", dept, deptName,
            "neuron://main/strategist/001",
            List.of("战略规划", "决策支持", "目标管理"),
            List.of("strategy", "decision-support", "goal-management"),
            List.of("analytics_tool", "bi_tool"),
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
            null
        );
        
        Employee employee = employeeService.createEmployee(request);
        log.info("Created fixed employee from definition: {} -> {}", code, employee.getEmployeeId());
        
        return employee;
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
