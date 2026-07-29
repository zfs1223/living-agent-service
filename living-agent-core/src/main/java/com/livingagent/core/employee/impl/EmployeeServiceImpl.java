package com.livingagent.core.employee.impl;

import com.livingagent.core.database.entity.DepartmentEntity;
import com.livingagent.core.database.entity.EnterpriseEmployeeEntity;
import com.livingagent.core.database.repository.DepartmentRepository;
import com.livingagent.core.database.repository.EnterpriseEmployeeRepository;
import com.livingagent.core.employee.*;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.neuron.NeuronState;
import com.livingagent.core.security.Department;
import com.livingagent.core.util.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EmployeeServiceImpl implements EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeServiceImpl.class);

    private final Map<String, Employee> employeeStore = new ConcurrentHashMap<>();
    private final Map<String, String> authIndex = new ConcurrentHashMap<>();
    private final NeuronRegistry neuronRegistry;
    private final EnterpriseEmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    public EmployeeServiceImpl(
            NeuronRegistry neuronRegistry,
            EnterpriseEmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository) {
        this.neuronRegistry = neuronRegistry;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
    }

    @Override
    public Employee createEmployee(EmployeeCreationRequest request) {
        String employeeId = generateEmployeeId(request);
        
        if (employeeStore.containsKey(employeeId)) {
            throw new IllegalStateException("Employee already exists: " + employeeId);
        }
        
        Employee employee;
        if (request.type() == IdUtils.EmployeeType.DIGITAL) {
            employee = createDigitalEmployee(employeeId, request);
        } else {
            employee = createHumanEmployee(employeeId, request);
        }
        
        employeeStore.put(employeeId, employee);
        
        String authKey = request.authProvider() + ":" + request.authId();
        authIndex.put(authKey, employeeId);
        
        log.info("Created {} employee: {} ({})", 
            request.type(), employeeId, request.name());
        
        return employee;
    }

    private String generateEmployeeId(EmployeeCreationRequest request) {
        if (request.type() == IdUtils.EmployeeType.DIGITAL) {
            if (request.suggestedEmployeeId() != null && !request.suggestedEmployeeId().isBlank()) {
                return request.suggestedEmployeeId();
            }
            String domain = request.departmentId() != null ? request.departmentId() : toIdSegment(request.department());
            String role = toIdSegment(request.name());
            String instance = generateInstanceNumber(domain, role);
            return IdUtils.generateDigitalEmployeeId(domain, role, instance);
        } else {
            IdUtils.AuthProvider provider;
            try {
                provider = IdUtils.AuthProvider.valueOf(request.authProvider().toUpperCase());
            } catch (IllegalArgumentException e) {
                provider = IdUtils.AuthProvider.SYSTEM;
            }
            return IdUtils.generateHumanEmployeeId(
                provider,
                request.authId()
            );
        }
    }

    private String generateInstanceNumber(String domain, String role) {
        int maxInstance = employeeStore.values().stream()
            .filter(Employee::isDigital)
            .mapToInt(e -> {
                try {
                    IdUtils.ParsedEmployeeId parsed = IdUtils.parseEmployeeId(e.getEmployeeId());
                    if (domain.equals(parsed.getDepartment()) && role.equals(parsed.getRole())) {
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

    private String toIdSegment(String text) {
        if (text == null) return "unknown";
        return text.replaceAll("([a-z])([A-Z])", "$1-$2")
                   .toLowerCase()
                   .replaceAll("[^a-z0-9\\u4e00-\\u9fff-]", "-")
                   .replaceAll("-+", "-")
                   .replaceAll("^-|-$", "");
    }

    private Employee createDigitalEmployee(String employeeId, EmployeeCreationRequest request) {
        DigitalEmployee.Builder builder = DigitalEmployee.builder()
            .employeeId(employeeId)
            .name(request.name())
            .title(request.title())
            .icon(request.icon() != null ? request.icon() : "🤖")
            .department(request.department())
            .departmentId(request.departmentId())
            .roles(request.roles() != null ? request.roles() : List.of())
            .managerId(request.managerId())
            .capabilities(request.capabilities() != null ? request.capabilities() : List.of())
            .skills(request.skills() != null ? request.skills() : List.of())
            .tools(request.tools() != null ? request.tools() : List.of())
            .personality(request.personality())
            .subscribeChannels(request.subscribeChannels() != null ? request.subscribeChannels() : List.of())
            .publishChannels(request.publishChannels() != null ? request.publishChannels() : List.of())
            .workflowBindings(request.workflowBindings() != null ? request.workflowBindings() : List.of())
            .origin(request.origin());
        
        if (request.ttl() != null) {
            builder.expiresAt(Instant.now().plus(request.ttl()));
        }
        
        return builder.build();
    }

    private Employee createHumanEmployee(String employeeId, EmployeeCreationRequest request) {
        return HumanEmployee.builder()
            .employeeId(employeeId)
            .authId(request.authId())
            .authProvider(request.authProvider())
            .name(request.name())
            .title(request.title())
            .icon(request.icon() != null ? request.icon() : "👤")
            .department(request.department())
            .departmentId(request.departmentId())
            .roles(request.roles() != null ? request.roles() : List.of())
            .managerId(request.managerId())
            .capabilities(request.capabilities() != null ? request.capabilities() : List.of())
            .skills(request.skills() != null ? request.skills() : List.of())
            .tools(request.tools() != null ? request.tools() : List.of())
            .personality(request.personality())
            .build();
    }

    @Override
    public Optional<Employee> getEmployee(String employeeId) {
        return Optional.ofNullable(employeeStore.get(employeeId));
    }

    @Override
    public Employee updateEmployee(String employeeId, EmployeeUpdateRequest request) {
        Employee existing = employeeStore.get(employeeId);
        if (existing == null) {
            throw new NoSuchElementException("Employee not found: " + employeeId);
        }
        
        Employee updated;
        if (existing.isDigital()) {
            updated = updateDigitalEmployee((DigitalEmployee) existing, request);
        } else {
            updated = updateHumanEmployee((HumanEmployee) existing, request);
        }
        
        employeeStore.put(employeeId, updated);
        log.info("Updated employee: {}", employeeId);
        
        return updated;
    }

    private DigitalEmployee updateDigitalEmployee(DigitalEmployee existing, EmployeeUpdateRequest request) {
        DigitalEmployee.Builder builder = DigitalEmployee.builder()
            .employeeId(existing.getEmployeeId())
            .name(request.name() != null ? request.name() : existing.getName())
            .title(request.title() != null ? request.title() : existing.getTitle())
            .icon(request.icon() != null ? request.icon() : existing.getIcon())
            .department(request.department() != null ? request.department() : existing.getDepartment())
            .departmentId(request.departmentId() != null ? request.departmentId() : existing.getDepartmentId())
            .roles(request.roles() != null ? request.roles() : existing.getRoles())
            .managerId(request.managerId() != null ? request.managerId() : existing.getManagerId().orElse(null))
            .capabilities(request.capabilities() != null ? request.capabilities() : existing.getCapabilities())
            .skills(request.skills() != null ? request.skills() : existing.getSkills())
            .tools(request.tools() != null ? request.tools() : existing.getTools())
            .personality(request.personality() != null ? request.personality() : existing.getPersonality())
            .subscribeChannels(existing.getDigitalConfig().getSubscribeChannels())
            .publishChannels(existing.getDigitalConfig().getPublishChannels())
            .workflowBindings(existing.getDigitalConfig().getWorkflowBindings())
            .status(existing.getStatus())
            .createdAt(existing.getCreatedAt());
        
        return builder.build();
    }

    private HumanEmployee updateHumanEmployee(HumanEmployee existing, EmployeeUpdateRequest request) {
        HumanEmployee.Builder builder = HumanEmployee.builder()
            .employeeId(existing.getEmployeeId())
            .authId(existing.getAuthId())
            .authProvider(existing.getAuthProvider())
            .name(request.name() != null ? request.name() : existing.getName())
            .title(request.title() != null ? request.title() : existing.getTitle())
            .icon(request.icon() != null ? request.icon() : existing.getIcon())
            .department(request.department() != null ? request.department() : existing.getDepartment())
            .departmentId(request.departmentId() != null ? request.departmentId() : existing.getDepartmentId())
            .roles(request.roles() != null ? request.roles() : existing.getRoles())
            .managerId(request.managerId() != null ? request.managerId() : existing.getManagerId().orElse(null))
            .capabilities(request.capabilities() != null ? request.capabilities() : existing.getCapabilities())
            .skills(request.skills() != null ? request.skills() : existing.getSkills())
            .tools(request.tools() != null ? request.tools() : existing.getTools())
            .personality(request.personality() != null ? request.personality() : existing.getPersonality())
            .status(existing.getStatus())
            .dingTalkId(existing.getHumanConfig().getDingTalkId())
            .feishuId(existing.getHumanConfig().getFeishuId())
            .wecomId(existing.getHumanConfig().getWecomId())
            .oaAccountId(existing.getHumanConfig().getOaAccountId())
            .createdAt(existing.getCreatedAt());
        
        return builder.build();
    }

    @Override
    public void updateStatus(String employeeId, EmployeeStatus status) {
        Employee employee = employeeStore.get(employeeId);
        if (employee == null) {
            throw new NoSuchElementException("Employee not found: " + employeeId);
        }
        
        if (employee.isDigital()) {
            ((DigitalEmployee) employee).setStatus(status);
        } else {
            ((HumanEmployee) employee).setStatus(status);
        }
        
        log.info("Updated employee {} status to {}", employeeId, status);
    }

    @Override
    public void deleteEmployee(String employeeId) {
        Employee employee = employeeStore.remove(employeeId);
        if (employee == null) {
            return;
        }
        
        if (employee.isDigital()) {
            String neuronId = IdUtils.employeeToNeuronId(employeeId);
            neuronRegistry.unregister(neuronId);
            log.info("Unregistered neuron for deleted employee: {}", neuronId);
        }
        
        String authKey = employee.getAuthProvider() + ":" + employee.getAuthId();
        authIndex.remove(authKey);
        
        log.info("Deleted employee: {}", employeeId);
    }

    @Override
    public List<Employee> listEmployees(EmployeeQuery query) {
        List<Employee> result = new java.util.ArrayList<>();

        // 1. 从内存获取数字员工（FIXED, PERSONAL, EVOLVED）
        employeeStore.values().stream()
            .filter(e -> query.type() == null ||
                (query.type() == IdUtils.EmployeeType.DIGITAL && e.isDigital()) ||
                (query.type() == IdUtils.EmployeeType.HUMAN && e.isHuman()))
            .filter(e -> query.departmentId() == null ||
                query.departmentId().equals(e.getDepartmentId()))
            .filter(e -> query.status() == null ||
                query.status().equals(e.getStatus()))
            .filter(e -> query.origin() == null ||
                query.origin().equals(e.getOrigin()))
            .filter(e -> query.nameKeyword() == null ||
                e.getName().toLowerCase().contains(query.nameKeyword().toLowerCase()))
            .forEach(result::add);

        // 2. 从数据库获取人类员工（HUMAN），如果查询条件允许
        boolean includeHuman = query.origin() == null || query.origin() == EmployeeOrigin.HUMAN;
        if (includeHuman && (query.type() == null || query.type() == IdUtils.EmployeeType.HUMAN)) {
            try {
                List<EnterpriseEmployeeEntity> humanEntities;
                if (query.departmentId() != null) {
                    humanEntities = employeeRepository.findActiveByDepartmentId(query.departmentId());
                } else {
                    humanEntities = employeeRepository.findByActiveTrue();
                }

                humanEntities.stream()
                    .filter(e -> query.status() == null ||
                        query.status().name().equalsIgnoreCase(e.getStatus()))
                    .filter(e -> query.nameKeyword() == null ||
                        e.getName().toLowerCase().contains(query.nameKeyword().toLowerCase()))
                    .map(this::convertToHumanEmployee)
                    .forEach(result::add);
            } catch (Exception e) {
                log.warn("Failed to load human employees from database", e);
            }
        }

        // 3. 应用分页
        return result.stream()
            .skip(query.offset())
            .limit(query.limit())
            .collect(Collectors.toList());
    }

    private HumanEmployee convertToHumanEmployee(EnterpriseEmployeeEntity entity) {
        return HumanEmployee.builder()
            .employeeId(entity.getEmployeeId())
            .authId(entity.getEmployeeId())
            .authProvider("system")
            .name(entity.getName())
            .title(entity.getPosition())
            .icon(entity.getAvatarUrl() != null ? "👤" : "👤")
            .department(entity.getDepartmentName())
            .departmentId(entity.getDepartmentId())
            .roles(List.of())
            .capabilities(List.of())
            .skills(List.of())
            .tools(List.of())
            .personality(null)
            .build();
    }

    @Override
    public List<Employee> listByDepartment(String departmentId) {
        return employeeStore.values().stream()
            .filter(e -> departmentId.equals(e.getDepartmentId()))
            .collect(Collectors.toList());
    }

    @Override
    public List<Employee> listByStatus(EmployeeStatus status) {
        return employeeStore.values().stream()
            .filter(e -> status.equals(e.getStatus()))
            .collect(Collectors.toList());
    }

    @Override
    public List<Employee> listDigitalEmployees() {
        return employeeStore.values().stream()
            .filter(Employee::isDigital)
            .collect(Collectors.toList());
    }

    @Override
    public List<Employee> listHumanEmployees() {
        return employeeStore.values().stream()
            .filter(Employee::isHuman)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Employee> findByAuthId(String authProvider, String authId) {
        String authKey = authProvider + ":" + authId;
        String employeeId = authIndex.get(authKey);
        if (employeeId != null) {
            return Optional.ofNullable(employeeStore.get(employeeId));
        }
        
        return employeeStore.values().stream()
            .filter(e -> authProvider.equals(e.getAuthProvider()) && 
                        authId.equals(e.getAuthId()))
            .findFirst();
    }

    @Override
    public void bindSkill(String employeeId, String skillName) {
        Employee employee = employeeStore.get(employeeId);
        if (employee == null) {
            throw new NoSuchElementException("Employee not found: " + employeeId);
        }
        
        List<String> skills = new ArrayList<>(employee.getSkills());
        if (!skills.contains(skillName)) {
            skills.add(skillName);
            EmployeeUpdateRequest request = new EmployeeUpdateRequest(
                null, null, null, null, null, null, null, null, skills, null, null
            );
            updateEmployee(employeeId, request);
            log.info("Bound skill {} to employee {}", skillName, employeeId);
        }
    }

    @Override
    public void unbindSkill(String employeeId, String skillName) {
        Employee employee = employeeStore.get(employeeId);
        if (employee == null) {
            throw new NoSuchElementException("Employee not found: " + employeeId);
        }
        
        List<String> skills = new ArrayList<>(employee.getSkills());
        if (skills.remove(skillName)) {
            EmployeeUpdateRequest request = new EmployeeUpdateRequest(
                null, null, null, null, null, null, null, null, skills, null, null
            );
            updateEmployee(employeeId, request);
            log.info("Unbound skill {} from employee {}", skillName, employeeId);
        }
    }

    @Override
    public List<String> getSkills(String employeeId) {
        Employee employee = employeeStore.get(employeeId);
        if (employee == null) {
            throw new NoSuchElementException("Employee not found: " + employeeId);
        }
        return employee.getSkills();
    }

    @Override
    public void addCapability(String employeeId, String capability) {
        Employee employee = employeeStore.get(employeeId);
        if (employee == null) {
            throw new NoSuchElementException("Employee not found: " + employeeId);
        }
        
        List<String> capabilities = new ArrayList<>(employee.getCapabilities());
        if (!capabilities.contains(capability)) {
            capabilities.add(capability);
            EmployeeUpdateRequest request = new EmployeeUpdateRequest(
                null, null, null, null, null, null, null, capabilities, null, null, null
            );
            updateEmployee(employeeId, request);
            log.info("Added capability {} to employee {}", capability, employeeId);
        }
    }

    @Override
    public void recordTask(String employeeId, boolean success) {
        Employee employee = employeeStore.get(employeeId);
        if (employee == null) {
            throw new NoSuchElementException("Employee not found: " + employeeId);
        }
        
        if (employee.isDigital()) {
            ((DigitalEmployee) employee).recordTask(success);
        } else {
            ((HumanEmployee) employee).recordTask(success);
        }
    }

    @Override
    public void checkAndDormantIdleEmployees() {
        Instant now = Instant.now();
        
        for (Employee employee : employeeStore.values()) {
            if (!employee.isDigital()) continue;
            
            DigitalEmployee de = (DigitalEmployee) employee;
            Duration idleTime = Duration.between(de.getLastActiveAt(), now);
            Duration maxIdle = de.getDigitalConfig().getMaxIdleTime();
            
            if (de.getDigitalConfig().isAutoDormant() && 
                idleTime.compareTo(maxIdle) > 0 &&
                de.getStatus() == EmployeeStatus.ACTIVE) {
                
                de.setStatus(EmployeeStatus.OFFLINE);
                log.info("Auto dormant digital employee: {} (idle: {})", 
                    de.getEmployeeId(), idleTime);
            }
        }
    }

    @Override
    public void wakeupEmployee(String employeeId) {
        Employee employee = employeeStore.get(employeeId);
        if (employee == null) {
            throw new NoSuchElementException("Employee not found: " + employeeId);
        }
        
        if (employee.isDigital()) {
            DigitalEmployee de = (DigitalEmployee) employee;
            de.setStatus(EmployeeStatus.ACTIVE);
            
            String neuronId = IdUtils.employeeToNeuronId(employeeId);
            neuronRegistry.get(neuronId).ifPresent(neuron -> {
                neuron.setState(NeuronState.ACTIVE);
            });
            
            log.info("Woke up digital employee: {}", employeeId);
        }
    }

    @Override
    public void terminateEmployee(String employeeId, String reason) {
        Employee employee = employeeStore.get(employeeId);
        if (employee == null) {
            throw new NoSuchElementException("Employee not found: " + employeeId);
        }
        
        if (employee.isDigital()) {
            ((DigitalEmployee) employee).setStatus(EmployeeStatus.TERMINATED);
            String neuronId = IdUtils.employeeToNeuronId(employeeId);
            neuronRegistry.unregister(neuronId);
            log.info("Terminated digital employee: {} (reason: {})", employeeId, reason);
        } else {
            ((HumanEmployee) employee).setStatus(EmployeeStatus.TERMINATED);
            log.info("Terminated human employee: {} (reason: {})", employeeId, reason);
        }
    }

    public int getEmployeeCount() {
        return employeeStore.size();
    }

    public int getDigitalEmployeeCount() {
        return (int) employeeStore.values().stream()
            .filter(Employee::isDigital)
            .count();
    }

    public int getHumanEmployeeCount() {
        return (int) employeeStore.values().stream()
            .filter(Employee::isHuman)
            .count();
    }

    @Override
    public List<MemberSummary> getDepartmentMembersByCode(String departmentCode) {
        if (departmentCode == null || departmentCode.isBlank()) {
            return List.of();
        }

        try {
            // 先通过部门 code 查询 DepartmentEntity 获取 departmentId
            Optional<DepartmentEntity> deptOpt = departmentRepository.findByCode(departmentCode);
            if (deptOpt.isEmpty()) {
                return List.of();
            }
            
            String departmentId = deptOpt.get().getDepartmentId();
            
            // 再用 departmentId 查询员工
            List<EnterpriseEmployeeEntity> employees = employeeRepository
                    .findActiveByDepartmentId(departmentId);
            
            return employees.stream()
                    .map(this::toMemberSummary)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get department members by code: {}", departmentCode, e);
            return List.of();
        }
    }

    @Override
    public Optional<MemberSummary> getMemberSummary(String employeeId) {
        if (employeeId == null || employeeId.isBlank()) {
            return Optional.empty();
        }

        try {
            return employeeRepository.findByEmployeeId(employeeId)
                    .map(this::toMemberSummary);
        } catch (Exception e) {
            log.error("Failed to get member summary for employee: {}", employeeId, e);
            return Optional.empty();
        }
    }

    private MemberSummary toMemberSummary(EnterpriseEmployeeEntity entity) {
        String status = entity.isActive() ? "在线" : "离线";
        String origin = "human";
        
        String departmentCode = resolveDepartmentCode(entity.getDepartmentId(), entity.getDepartmentName());

        return new MemberSummary(
            entity.getEmployeeId(),
            entity.getName(),
            entity.getDepartmentName(),
            departmentCode,
            status,
            origin,
            entity.getPosition(),
            entity.getAvatarUrl(),
            entity.getAccessLevel() != null ? entity.getAccessLevel() : "UNKNOWN"
        );
    }
    
    private String resolveDepartmentCode(String departmentId, String departmentName) {
        if (departmentId == null) {
            return null;
        }
        
        Optional<DepartmentEntity> deptOpt = departmentRepository.findById(departmentId);
        if (deptOpt.isPresent()) {
            return deptOpt.get().getCode();
        }
        
        if (departmentName != null && !departmentName.isBlank()) {
            return Department.mapDepartmentToBrain(departmentName).toLowerCase().replace("brain", "");
        }
        
        return null;
    }
}
