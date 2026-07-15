package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.AutonomyTraceService;
import com.livingagent.core.autonomy.AutonomyTraceEvent;
import com.livingagent.core.autonomy.LLMEmployeeCreationService;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import com.livingagent.core.employee.*;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import com.livingagent.core.util.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class LLMEmployeeCreationServiceImpl implements LLMEmployeeCreationService {

    private static final Logger log = LoggerFactory.getLogger(LLMEmployeeCreationServiceImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String EMPLOYEE_CREATION_SYSTEM_PROMPT = """
        你是企业主大脑，负责判断是否需要创建新的数字员工。
        
        当前企业已有32名固定数字员工，覆盖技术、财务、运营、销售、人力资源、客服、行政、法务、跨部门协调等职能。
        
        你需要根据当前任务需求和工作负载，判断是否需要创建新的数字员工。只有当现有员工无法覆盖的职能缺口时，才应创建新员工。
        
        新员工必须具备：
        1. 专属的中文名称（以"真"字开头，如"真测"表示测试工程师）
        2. 明确的能力列表
        3. 明确的技能列表
        4. 明确的工具列表
        5. 明确的岗位职责
        
        注意：员工编号代码（code）将由系统自动生成，格式为"部门前缀+两位数字"，如T11、F05等。
        你只需要提供neuronRoleSegment（纯英文小写加连字符，如test-engineer）用于生成员工ID。
        
        你必须只输出一个合法的JSON对象，不要包含任何其他文字。
        如果不需要创建新员工，输出：{"needed": false}
        如果需要创建新员工，输出：
        {
          "needed": true,
          "name": "真测",
          "title": "测试工程师",
          "department": "tech",
          "capabilities": ["自动化测试", "性能测试", "接口测试"],
          "skills": ["selenium", "jmeter", "pytest"],
          "tools": ["testing_tool"],
          "roles": ["tester"],
          "justification": "当前技术部缺少专职测试人员，代码审查员无法覆盖自动化测试需求",
          "neuronRoleSegment": "test-engineer"
        }
        
        neuronRoleSegment 必须是纯英文小写加连字符的形式，用于生成员工ID。
        
        部门代码：tech / finance / legal / hr / sales / cs / ops / admin / main
        """;

    private final BrainRegistry brainRegistry;
    private final EmployeeService employeeService;
    private final FixedEmployeeRegistry fixedEmployeeRegistry;
    private final AutonomyTraceService traceService;
    private final EmployeeCreationImprovementTracker improvementTracker;

    public LLMEmployeeCreationServiceImpl(
            BrainRegistry brainRegistry,
            EmployeeService employeeService,
            FixedEmployeeRegistry fixedEmployeeRegistry,
            AutonomyTraceService traceService) {
        this.brainRegistry = brainRegistry;
        this.employeeService = employeeService;
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.traceService = traceService;
        this.improvementTracker = null;
    }

    public LLMEmployeeCreationServiceImpl(
            BrainRegistry brainRegistry,
            EmployeeService employeeService,
            FixedEmployeeRegistry fixedEmployeeRegistry,
            AutonomyTraceService traceService,
            EmployeeCreationImprovementTracker improvementTracker) {
        this.brainRegistry = brainRegistry;
        this.employeeService = employeeService;
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.traceService = traceService;
        this.improvementTracker = improvementTracker;
    }

    @Override
    public Optional<EmployeeCreationProposal> evaluateCreationNeed(
            String department, String taskDescription, String workloadContext) {

        String requestId = UUID.randomUUID().toString();

        MainBrain mainBrain = brainRegistry.get(MainBrain.ID)
            .filter(b -> b instanceof MainBrain)
            .map(b -> (MainBrain) b)
            .orElse(null);

        if (mainBrain == null) {
            log.warn("MainBrain not available, cannot evaluate employee creation need");
            return Optional.empty();
        }

        try {
            String userPrompt = buildUserPrompt(department, taskDescription, workloadContext);
            String llmResponse = mainBrain.callLlm(EMPLOYEE_CREATION_SYSTEM_PROMPT, userPrompt);

            if (llmResponse == null || llmResponse.isBlank()) {
                log.info("LLM returned empty response for employee creation evaluation");
                return Optional.empty();
            }

            Map<String, Object> parsed = parseJson(llmResponse);
            if (parsed == null) {
                log.warn("Failed to parse LLM response for employee creation");
                return Optional.empty();
            }

            boolean needed = Boolean.TRUE.equals(parsed.get("needed"));
            if (!needed) {
                log.info("LLM determined no new employee needed for department={}", department);
                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "employee_creation_evaluated", "LLMEmployeeCreationService",
                    "LLM decided no new employee needed",
                    Map.of("department", department, "needed", false)
                ));
                return Optional.empty();
            }

            EmployeeCreationProposal proposal = buildProposal(parsed, department);
            if (proposal == null) {
                return Optional.empty();
            }

            if (isDuplicate(proposal)) {
                log.info("Proposed employee {} ({}) duplicates existing employee, skipping",
                    proposal.name(), proposal.code());
                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "employee_creation_evaluated", "LLMEmployeeCreationService",
                    "Proposed employee duplicates existing",
                    Map.of("code", proposal.code(), "name", proposal.name())
                ));
                return Optional.empty();
            }

            log.info("LLM proposed new employee: {} ({}) for department {}",
                proposal.name(), proposal.code(), proposal.department());
            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "employee_creation_evaluated", "LLMEmployeeCreationService",
                "LLM proposed new employee",
                Map.of("code", proposal.code(), "name", proposal.name(), "department", proposal.department())
            ));

            return Optional.of(proposal);

        } catch (Exception e) {
            log.error("Failed to evaluate employee creation need: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Employee createFromProposal(EmployeeCreationProposal proposal) {
        String instance = findNextInstanceNumber(proposal.department(), proposal.neuronRoleSegment());
        String neuronId = IdUtils.generateNeuronId(
            proposal.department(),
            proposal.neuronRoleSegment(),
            instance
        );
        String employeeId = IdUtils.neuronToEmployeeId(neuronId);

        if (employeeService.getEmployee(employeeId).isPresent()) {
            throw new IllegalStateException("Employee already exists with ID: " + employeeId);
        }

        String uniqueCode = generateUniqueCode(proposal.department(), proposal.code());

        EmployeeService.EmployeeCreationRequest request = new EmployeeService.EmployeeCreationRequest(
            IdUtils.EmployeeType.DIGITAL,
            "system",
            uniqueCode,
            proposal.name(),
            proposal.title(),
            proposal.icon(),
            proposal.department(),
            proposal.departmentId() != null ? proposal.departmentId() : proposal.department(),
            proposal.roles(),
            null,
            proposal.capabilities(),
            proposal.skills(),
            proposal.tools(),
            null,
            null,
            List.of("channel://employee/" + neuronId + "/tasks"),
            List.of(),
            List.of(),
            null,
            null,
            EmployeeOrigin.EVOLVED,
            employeeId,
            null,
            null,
            null,
            null,
            "DEPARTMENT",
            null,
            null
        );

        Employee employee = employeeService.createEmployee(request);
        log.info("Created LLM-proposed employee: {} ({}) -> {}",
            proposal.name(), uniqueCode, employeeId);

        // 闭环35 improvement: 记录创建员工供后续绩效评估
        if (improvementTracker != null) {
            improvementTracker.recordCreatedEmployee(employeeId, proposal.department(), proposal.justification());
        }

        return employee;
    }

    private String findNextInstanceNumber(String department, String role) {
        int maxInstance = employeeService.listEmployees(
            new EmployeeService.EmployeeQuery(null, null, null, null, 10000, 0)
        ).stream()
            .filter(Employee::isDigital)
            .mapToInt(e -> {
                try {
                    IdUtils.ParsedEmployeeId parsed = IdUtils.parseEmployeeId(e.getEmployeeId());
                    if (department.equals(parsed.getDepartment()) && role.equals(parsed.getRole())) {
                        String inst = parsed.getInstance();
                        return inst != null ? Integer.parseInt(inst) : 0;
                    }
                } catch (Exception ex) {
                    // skip non-standard IDs
                }
                return 0;
            })
            .max()
            .orElse(0);
        return String.format("%03d", maxInstance + 1);
    }

    private String generateUniqueCode(String department, String proposedCode) {
        String deptPrefix = getDepartmentPrefix(department);
        int maxNumber = findMaxCodeNumber(deptPrefix);
        return String.format("%s%02d", deptPrefix, maxNumber + 1);
    }

    private String getDepartmentPrefix(String department) {
        if (department == null) return "X";
        return switch (department.toLowerCase()) {
            case "tech" -> "T";
            case "finance" -> "F";
            case "ops" -> "O";
            case "sales" -> "S";
            case "hr" -> "H";
            case "cs" -> "C";
            case "admin" -> "A";
            case "legal" -> "L";
            case "main" -> "M";
            default -> department.substring(0, 1).toUpperCase();
        };
    }

    private int findMaxCodeNumber(String prefix) {
        int maxInFixed = fixedEmployeeRegistry.getAllDefinitions().stream()
            .filter(d -> d.code().startsWith(prefix))
            .mapToInt(d -> {
                try {
                    String numPart = d.code().substring(prefix.length());
                    return Integer.parseInt(numPart);
                } catch (Exception e) {
                    return 0;
                }
            })
            .max()
            .orElse(0);

        int maxInDynamic = employeeService.listEmployees(
            new EmployeeService.EmployeeQuery(null, null, null, null, 10000, 0)
        ).stream()
            .filter(Employee::isDigital)
            .filter(e -> e.getOrigin() == EmployeeOrigin.EVOLVED)
            .mapToInt(e -> {
                String code = e.getAuthId();
                if (code != null && code.startsWith(prefix)) {
                    try {
                        String numPart = code.substring(prefix.length());
                        return Integer.parseInt(numPart);
                    } catch (Exception ex) {
                        return 0;
                    }
                }
                return 0;
            })
            .max()
            .orElse(0);

        return Math.max(maxInFixed, maxInDynamic);
    }

    private String buildUserPrompt(String department, String taskDescription, String workloadContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前部门: ").append(department != null ? department : "未知").append("\n");

        String existingEmployees = fixedEmployeeRegistry.getAllDefinitions().stream()
            .map(d -> d.code() + ":" + d.name() + "(" + d.title() + ")")
            .collect(Collectors.joining(", "));
        sb.append("现有固定员工: ").append(existingEmployees).append("\n");

        long dynamicCount = employeeService.listEmployees(
            new EmployeeService.EmployeeQuery(null, null, null, null, 10000, 0)
        ).stream()
            .filter(Employee::isDigital)
            .filter(e -> e.getOrigin() == EmployeeOrigin.EVOLVED)
            .count();
        sb.append("现有动态员工数量: ").append(dynamicCount).append("\n");

        sb.append("任务描述: ").append(taskDescription != null ? taskDescription : "无").append("\n");
        sb.append("工作负载上下文: ").append(workloadContext != null ? workloadContext : "无").append("\n");
        sb.append("\n请判断是否需要创建新的数字员工。");
        return sb.toString();
    }

    private boolean isDuplicate(EmployeeCreationProposal proposal) {
        if (fixedEmployeeRegistry.getAllDefinitions().stream()
                .anyMatch(d -> d.code().equalsIgnoreCase(proposal.code()) || d.name().equals(proposal.name()))) {
            return true;
        }

        String expectedPrefix = "employee://digital/" + proposal.department() + "/" + proposal.neuronRoleSegment() + "/";
        
        return employeeService.listEmployees(
            new EmployeeService.EmployeeQuery(null, null, null, null, 10000, 0)
        ).stream()
            .filter(Employee::isDigital)
            .anyMatch(e -> e.getName().equals(proposal.name()) ||
                           e.getEmployeeId().startsWith(expectedPrefix));
    }

    private Map<String, Object> parseJson(String response) {
        if (response == null) return null;
        String trimmed = response.trim();

        if (trimmed.startsWith("```json")) {
            int start = trimmed.indexOf("\n") + 1;
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) trimmed = trimmed.substring(start, end).trim();
        } else if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("\n") + 1;
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) trimmed = trimmed.substring(start, end).trim();
        }

        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(
                    trimmed.substring(braceStart, braceEnd + 1), Map.class);
                return parsed;
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse JSON from LLM response: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private EmployeeCreationProposal buildProposal(Map<String, Object> parsed, String fallbackDepartment) {
        try {
            String name = (String) parsed.getOrDefault("name", "");
            String code = (String) parsed.getOrDefault("code", "");
            String title = (String) parsed.getOrDefault("title", "");
            String department = (String) parsed.getOrDefault("department", fallbackDepartment);
            String justification = (String) parsed.getOrDefault("justification", "");
            String neuronRoleSegment = (String) parsed.getOrDefault("neuronRoleSegment", "");

            if (name.isBlank() || neuronRoleSegment.isBlank()) {
                log.warn("LLM proposal missing required fields (name/neuronRoleSegment)");
                return null;
            }

            List<String> capabilities = parsed.get("capabilities") instanceof List<?> list
                ? list.stream().map(Object::toString).toList() : List.of();
            List<String> skills = parsed.get("skills") instanceof List<?> list
                ? list.stream().map(Object::toString).toList() : List.of();
            List<String> tools = parsed.get("tools") instanceof List<?> list
                ? list.stream().map(Object::toString).toList() : List.of();
            List<String> roles = parsed.get("roles") instanceof List<?> list
                ? list.stream().map(Object::toString).toList() : List.of();

            return new EmployeeCreationProposal(
                department, department, name, code, title, "🤖",
                capabilities, skills, tools, roles, justification, neuronRoleSegment
            );
        } catch (Exception e) {
            log.warn("Failed to build employee creation proposal: {}", e.getMessage());
            return null;
        }
    }
}
