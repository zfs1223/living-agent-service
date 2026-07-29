package com.livingagent.core.employee.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 统一使用 EnterpriseEmployeeEntity 的 EmployeeService 实现。
 * 替代旧的 JpaEmployeeServiceImpl（使用 V1 employees 表）。
 */
@Transactional
public class JpaEmployeeServiceImpl implements EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(JpaEmployeeServiceImpl.class);

    private final EnterpriseEmployeeRepository enterpriseEmployeeRepository;
    private final DepartmentRepository departmentRepository;
    private final NeuronRegistry neuronRegistry;
    private final ObjectMapper objectMapper;

    private final Map<String, Employee> cache = new ConcurrentHashMap<>();
    private final Map<String, String> authIndex = new ConcurrentHashMap<>();

    public JpaEmployeeServiceImpl(EnterpriseEmployeeRepository enterpriseEmployeeRepository,
                                   DepartmentRepository departmentRepository,
                                   NeuronRegistry neuronRegistry) {
        this.enterpriseEmployeeRepository = enterpriseEmployeeRepository;
        this.departmentRepository = departmentRepository;
        this.neuronRegistry = neuronRegistry;
        this.objectMapper = new ObjectMapper();
        loadCache();
    }

    private void loadCache() {
        log.info("Loading employees from enterprise_employees table into cache...");
        List<EnterpriseEmployeeEntity> entities = enterpriseEmployeeRepository.findAll();
        for (EnterpriseEmployeeEntity entity : entities) {
            Employee employee = toDomain(entity);
            cache.put(entity.getEmployeeId(), employee);
            if (entity.getOauthProvider() != null && entity.getOauthUserId() != null) {
                String authKey = entity.getOauthProvider() + ":" + entity.getOauthUserId();
                authIndex.put(authKey, entity.getEmployeeId());
            }
        }
        log.info("Loaded {} employees from enterprise_employees table", cache.size());
    }

    @Override
    public Employee createEmployee(EmployeeCreationRequest request) {
        String employeeId = generateEmployeeId(request);

        if (cache.containsKey(employeeId)) {
            throw new IllegalStateException("Employee already exists: " + employeeId);
        }

        EnterpriseEmployeeEntity entity = new EnterpriseEmployeeEntity();
        entity.setEmployeeId(employeeId);
        entity.setName(request.name());
        entity.setDepartmentId(request.departmentId());
        entity.setDepartmentName(request.department());
        entity.setPosition(request.title());
        entity.setStatus(EmployeeStatus.ACTIVE.name());
        entity.setActive(true);
        entity.setHireDate(LocalDate.now());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        if (request.type() == IdUtils.EmployeeType.DIGITAL) {
            entity.setEmployeeType("DIGITAL");
            entity.setIdentity("digital_employee");
            entity.setAccessLevel(
                request.permissionAccessLevel() != null ? request.permissionAccessLevel() : "DEPARTMENT"
            );
            entity.setBrainDomain(request.department());
            entity.setMaxConcurrentTasks(5);
            entity.setOrigin(request.origin() != null ? request.origin().name() : EmployeeOrigin.PERSONAL.name());
            entity.setPermissionScopeType(request.permissionScopeType() != null ? request.permissionScopeType() : "company");
            entity.setOwnerId(request.ownerId());
            if (request.primaryModelId() != null && !request.primaryModelId().isBlank()) {
                entity.setModel(request.primaryModelId());
            }
            try {
                entity.setSkills(objectMapper.writeValueAsString(request.skills()));
                entity.setCapabilities(objectMapper.writeValueAsString(request.capabilities()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize skills/capabilities", e);
            }
        } else {
            entity.setEmployeeType("HUMAN");
            entity.setIdentity("human_employee");
            entity.setOrigin(EmployeeOrigin.HUMAN.name());
            entity.setOauthProvider(request.authProvider());
            entity.setOauthUserId(request.authId());
            entity.setEmail(request.email());
            entity.setPhone(request.phone());
            entity.setAccessLevel("CHAT_ONLY");
        }

        enterpriseEmployeeRepository.save(entity);

        Employee employee;
        if (request.type() == IdUtils.EmployeeType.DIGITAL) {
            employee = createDigitalEmployee(employeeId, request);
        } else {
            employee = createHumanEmployee(employeeId, request);
        }

        cache.put(employeeId, employee);
        if (request.authProvider() != null && request.authId() != null) {
            String authKey = request.authProvider() + ":" + request.authId();
            authIndex.put(authKey, employeeId);
        }

        log.info("Created {} employee: {} ({})", request.type(), employeeId, request.name());

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
        int maxInstance = cache.values().stream()
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
            .roles(request.roles())
            .managerId(request.managerId())
            .capabilities(request.capabilities())
            .skills(request.skills())
            .tools(request.tools())
            .personality(request.personality())
            .subscribeChannels(request.subscribeChannels())
            .publishChannels(request.publishChannels())
            .workflowBindings(request.workflowBindings())
            .origin(request.origin())
            .permissionScopeType(request.permissionScopeType())
            .ownerId(request.ownerId());

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
            .roles(request.roles())
            .managerId(request.managerId())
            .capabilities(request.capabilities())
            .skills(request.skills())
            .tools(request.tools())
            .personality(request.personality())
            .build();
    }

    @Override
    public Optional<Employee> getEmployee(String employeeId) {
        return Optional.ofNullable(cache.get(employeeId));
    }

    @Override
    public Employee updateEmployee(String employeeId, EmployeeUpdateRequest request) {
        Employee existing = cache.get(employeeId);
        if (existing == null) {
            throw new NoSuchElementException("Employee not found: " + employeeId);
        }

        Employee updated;
        if (existing.isDigital()) {
            updated = updateDigitalEmployee((DigitalEmployee) existing, request);
        } else {
            updated = updateHumanEmployee((HumanEmployee) existing, request);
        }

        enterpriseEmployeeRepository.findByEmployeeId(employeeId).ifPresent(entity -> {
            updateEntityFromDomain(entity, updated);
            enterpriseEmployeeRepository.save(entity);
        });

        cache.put(employeeId, updated);
        log.info("Updated employee: {}", employeeId);

        return updated;
    }

    private void updateEntityFromDomain(EnterpriseEmployeeEntity entity, Employee employee) {
        entity.setName(employee.getName());
        entity.setDepartmentId(employee.getDepartmentId());
        entity.setDepartmentName(employee.getDepartment());
        entity.setStatus(employee.getStatus().name());
        entity.setPosition(employee.getTitle());
        entity.setUpdatedAt(Instant.now());
    }

    private DigitalEmployee updateDigitalEmployee(DigitalEmployee existing, EmployeeUpdateRequest request) {
        return DigitalEmployee.builder()
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
            .createdAt(existing.getCreatedAt())
            .build();
    }

    private HumanEmployee updateHumanEmployee(HumanEmployee existing, EmployeeUpdateRequest request) {
        return HumanEmployee.builder()
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
            .createdAt(existing.getCreatedAt())
            .build();
    }

    @Override
    public void updateStatus(String employeeId, EmployeeStatus status) {
        Employee employee = cache.get(employeeId);
        if (employee == null) {
            throw new NoSuchElementException("Employee not found: " + employeeId);
        }

        if (employee.isDigital()) {
            ((DigitalEmployee) employee).setStatus(status);
        } else {
            ((HumanEmployee) employee).setStatus(status);
        }

        enterpriseEmployeeRepository.findByEmployeeId(employeeId).ifPresent(entity -> {
            entity.setStatus(status.name());
            // ✅ 基于新状态模型：工作动作状态+学习状态均为"在线"
            entity.setActive(status.isOnline());
            entity.setUpdatedAt(Instant.now());
            enterpriseEmployeeRepository.save(entity);
        });

        log.info("Updated employee {} status to {}", employeeId, status);
    }

    @Override
    public void deleteEmployee(String employeeId) {
        Employee employee = cache.remove(employeeId);
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

        enterpriseEmployeeRepository.deleteById(employeeId);

        log.info("Deleted employee: {}", employeeId);
    }

    @Override
    public List<Employee> listEmployees(EmployeeQuery query) {
        return cache.values().stream()
            .filter(e -> query.type() == null ||
                (query.type() == IdUtils.EmployeeType.DIGITAL && e.isDigital()) ||
                (query.type() == IdUtils.EmployeeType.HUMAN && e.isHuman()))
            // 按 origin 过滤：query.origin() 为 null（未传 origin 参数）时不过滤，保持原有全量行为；
            // 传入 origin（如 personal）时只返回对应来源的员工，避免 /api/agents?origin=personal 泄露 fixed 等其它来源（AGENTS.md §5.3 / 聊天对象选择区规则）
            .filter(e -> query.origin() == null ||
                query.origin().equals(e.getOrigin()))
            .filter(e -> query.departmentId() == null ||
                query.departmentId().equals(e.getDepartmentId()))
            .filter(e -> query.status() == null ||
                query.status().equals(e.getStatus()))
            .filter(e -> query.nameKeyword() == null ||
                e.getName().toLowerCase().contains(query.nameKeyword().toLowerCase()))
            .skip(query.offset())
            .limit(query.limit())
            .collect(Collectors.toList());
    }

    @Override
    public List<Employee> listByDepartment(String departmentId) {
        return cache.values().stream()
            .filter(e -> departmentId.equals(e.getDepartmentId()))
            .collect(Collectors.toList());
    }

    @Override
    public List<Employee> listByStatus(EmployeeStatus status) {
        return cache.values().stream()
            .filter(e -> status.equals(e.getStatus()))
            .collect(Collectors.toList());
    }

    @Override
    public List<Employee> listDigitalEmployees() {
        return cache.values().stream()
            .filter(Employee::isDigital)
            .collect(Collectors.toList());
    }

    @Override
    public List<Employee> listHumanEmployees() {
        return cache.values().stream()
            .filter(Employee::isHuman)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Employee> findByAuthId(String authProvider, String authId) {
        String authKey = authProvider + ":" + authId;
        String employeeId = authIndex.get(authKey);
        if (employeeId != null) {
            return Optional.ofNullable(cache.get(employeeId));
        }

        // 尝试从数据库查找
        Optional<EnterpriseEmployeeEntity> entityOpt =
            enterpriseEmployeeRepository.findByOauthProviderAndOauthUserId(authProvider, authId);
        if (entityOpt.isPresent()) {
            Employee employee = toDomain(entityOpt.get());
            cache.put(employee.getEmployeeId(), employee);
            authIndex.put(authKey, employee.getEmployeeId());
            return Optional.of(employee);
        }

        return cache.values().stream()
            .filter(e -> authProvider.equals(e.getAuthProvider()) &&
                        authId.equals(e.getAuthId()))
            .findFirst();
    }

    @Override
    public void bindSkill(String employeeId, String skillName) {
        Employee employee = cache.get(employeeId);
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
        Employee employee = cache.get(employeeId);
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
        Employee employee = cache.get(employeeId);
        if (employee == null) {
            throw new NoSuchElementException("Employee not found: " + employeeId);
        }
        return employee.getSkills();
    }

    @Override
    public void addCapability(String employeeId, String capability) {
        Employee employee = cache.get(employeeId);
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
        Employee employee = cache.get(employeeId);
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

        for (Employee employee : cache.values()) {
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
        Employee employee = cache.get(employeeId);
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
        Employee employee = cache.get(employeeId);
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

        enterpriseEmployeeRepository.findByEmployeeId(employeeId).ifPresent(entity -> {
            entity.setStatus(EmployeeStatus.TERMINATED.name());
            entity.setActive(false);
            entity.setUpdatedAt(Instant.now());
            enterpriseEmployeeRepository.save(entity);
        });
    }

    private Employee toDomain(EnterpriseEmployeeEntity entity) {
        if ("DIGITAL".equals(entity.getEmployeeType())) {
            return toDigitalEmployee(entity);
        } else {
            return toHumanEmployee(entity);
        }
    }

    private DigitalEmployee toDigitalEmployee(EnterpriseEmployeeEntity entity) {
        List<String> skills = parseJsonList(entity.getSkills());
        List<String> capabilities = parseJsonList(entity.getCapabilities());

        return DigitalEmployee.builder()
            .employeeId(entity.getEmployeeId())
            .name(entity.getName())
            .title(entity.getPosition())
            .department(entity.getDepartmentName())
            .departmentId(entity.getDepartmentId())
            .skills(skills)
            .capabilities(capabilities)
            .status(parseStatus(entity.getStatus()))
            .origin(parseOrigin(entity.getOrigin()))
            .permissionScopeType(entity.getPermissionScopeType())
            .ownerId(entity.getOwnerId())
            .createdAt(entity.getCreatedAt())
            .build();
    }

    private HumanEmployee toHumanEmployee(EnterpriseEmployeeEntity entity) {
        return HumanEmployee.builder()
            .employeeId(entity.getEmployeeId())
            .name(entity.getName())
            .title(entity.getPosition())
            .department(entity.getDepartmentName() != null ? entity.getDepartmentName() : "unassigned")
            .departmentId(entity.getDepartmentId() != null ? entity.getDepartmentId() : "unassigned")
            .authProvider(entity.getOauthProvider())
            .authId(entity.getOauthUserId())
            .status(parseStatus(entity.getStatus()))
            .createdAt(entity.getCreatedAt())
            .build();
    }

    private EmployeeStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return EmployeeStatus.ACTIVE;
        try {
            return EmployeeStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return EmployeeStatus.ACTIVE;
        }
    }

    private EmployeeOrigin parseOrigin(String origin) {
        if (origin == null || origin.isBlank()) return EmployeeOrigin.PERSONAL;
        try {
            return EmployeeOrigin.valueOf(origin);
        } catch (IllegalArgumentException e) {
            return EmployeeOrigin.PERSONAL;
        }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON list: {}", json, e);
            return new ArrayList<>();
        }
    }

    public int getEmployeeCount() {
        return cache.size();
    }

    public int getDigitalEmployeeCount() {
        return (int) cache.values().stream()
            .filter(Employee::isDigital)
            .count();
    }

    public int getHumanEmployeeCount() {
        return (int) cache.values().stream()
            .filter(Employee::isHuman)
            .count();
    }

    @Override
    public List<MemberSummary> getDepartmentMembersByCode(String departmentCode) {
        if (departmentCode == null || departmentCode.isBlank()) {
            return List.of();
        }

        try {
            Optional<DepartmentEntity> deptOpt = departmentRepository.findByCode(departmentCode);
            if (deptOpt.isEmpty()) {
                return List.of();
            }

            String departmentId = deptOpt.get().getDepartmentId();

            List<EnterpriseEmployeeEntity> employees = enterpriseEmployeeRepository
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
            return enterpriseEmployeeRepository.findByEmployeeId(employeeId)
                    .map(this::toMemberSummary);
        } catch (Exception e) {
            log.error("Failed to get member summary for employee: {}", employeeId, e);
            return Optional.empty();
        }
    }

    private MemberSummary toMemberSummary(EnterpriseEmployeeEntity entity) {
        String status = entity.isActive() ? "在线" : "离线";
        String origin = "DIGITAL".equals(entity.getEmployeeType()) ? "digital" : "human";

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
        if (departmentId != null) {
            Optional<DepartmentEntity> deptOpt = departmentRepository.findById(departmentId);
            if (deptOpt.isPresent()) {
                return deptOpt.get().getCode();
            }
        }

        if (departmentName != null && !departmentName.isBlank()) {
            return Department.mapDepartmentToBrain(departmentName).toLowerCase().replace("brain", "");
        }

        return null;
    }
}
