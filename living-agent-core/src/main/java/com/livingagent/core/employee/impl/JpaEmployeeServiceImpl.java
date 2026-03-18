package com.livingagent.core.employee.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.employee.*;
import com.livingagent.core.employee.entity.DigitalEmployeeEntity;
import com.livingagent.core.employee.entity.EmployeeEntity;
import com.livingagent.core.employee.entity.HumanEmployeeEntity;
import com.livingagent.core.employee.repository.EmployeeRepository;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.neuron.NeuronState;
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

@Transactional
public class JpaEmployeeServiceImpl implements EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(JpaEmployeeServiceImpl.class);

    private final EmployeeRepository employeeRepository;
    private final NeuronRegistry neuronRegistry;
    private final ObjectMapper objectMapper;

    private final Map<String, Employee> cache = new ConcurrentHashMap<>();
    private final Map<String, String> authIndex = new ConcurrentHashMap<>();

    public JpaEmployeeServiceImpl(EmployeeRepository employeeRepository, NeuronRegistry neuronRegistry) {
        this.employeeRepository = employeeRepository;
        this.neuronRegistry = neuronRegistry;
        this.objectMapper = new ObjectMapper();
        loadCache();
    }

    private void loadCache() {
        log.info("Loading employees from database into cache...");
        List<EmployeeEntity> entities = employeeRepository.findAll();
        for (EmployeeEntity entity : entities) {
            Employee employee = toDomain(entity);
            cache.put(entity.getId(), employee);
            String authKey = employee.getAuthProvider() + ":" + employee.getAuthId();
            authIndex.put(authKey, entity.getId());
        }
        log.info("Loaded {} employees from database", cache.size());
    }

    @Override
    public Employee createEmployee(EmployeeCreationRequest request) {
        String employeeId = generateEmployeeId(request);

        if (cache.containsKey(employeeId)) {
            throw new IllegalStateException("Employee already exists: " + employeeId);
        }

        EmployeeEntity entity;
        Employee employee;

        if (request.type() == IdUtils.EmployeeType.DIGITAL) {
            entity = createDigitalEntity(employeeId, request);
            employee = createDigitalEmployee(employeeId, request);
        } else {
            entity = createHumanEntity(employeeId, request);
            employee = createHumanEmployee(employeeId, request);
        }

        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        employeeRepository.save(entity);

        cache.put(employeeId, employee);
        String authKey = request.authProvider() + ":" + request.authId();
        authIndex.put(authKey, employeeId);

        log.info("Created {} employee: {} ({})", request.type(), employeeId, request.name());

        return employee;
    }

    private String generateEmployeeId(EmployeeCreationRequest request) {
        if (request.type() == IdUtils.EmployeeType.DIGITAL) {
            String instance = String.format("%03d",
                cache.values().stream()
                    .filter(Employee::isDigital)
                    .mapToInt(e -> 1)
                    .sum() + 1);
            return IdUtils.generateDigitalEmployeeId(
                request.department(),
                request.roles().isEmpty() ? "worker" : request.roles().get(0),
                instance
            );
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

    private DigitalEmployeeEntity createDigitalEntity(String employeeId, EmployeeCreationRequest request) {
        DigitalEmployeeEntity entity = new DigitalEmployeeEntity();
        entity.setId(employeeId);
        entity.setName(request.name());
        entity.setDepartment(request.department());
        entity.setStatus(EmployeeStatus.ACTIVE.name());
        entity.setPosition(request.title());
        entity.setHireDate(LocalDate.now());
        entity.setBrainDomain(request.department());
        entity.setMaxConcurrentTasks(5);
        try {
            entity.setSkills(objectMapper.writeValueAsString(request.skills()));
            entity.setCapabilities(objectMapper.writeValueAsString(request.capabilities()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize skills/capabilities", e);
        }
        return entity;
    }

    private HumanEmployeeEntity createHumanEntity(String employeeId, EmployeeCreationRequest request) {
        HumanEmployeeEntity entity = new HumanEmployeeEntity();
        entity.setId(employeeId);
        entity.setName(request.name());
        entity.setDepartment(request.department());
        entity.setStatus(EmployeeStatus.ACTIVE.name());
        entity.setPosition(request.title());
        entity.setHireDate(LocalDate.now());
        return entity;
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
            .workflowBindings(request.workflowBindings());

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

        Optional<EmployeeEntity> entityOpt = employeeRepository.findById(employeeId);
        if (entityOpt.isPresent()) {
            EmployeeEntity entity = entityOpt.get();
            updateEntityFromDomain(entity, updated);
            employeeRepository.save(entity);
        }

        cache.put(employeeId, updated);
        log.info("Updated employee: {}", employeeId);

        return updated;
    }

    private void updateEntityFromDomain(EmployeeEntity entity, Employee employee) {
        entity.setName(employee.getName());
        entity.setDepartment(employee.getDepartment());
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

        employeeRepository.findById(employeeId).ifPresent(entity -> {
            entity.setStatus(status.name());
            entity.setUpdatedAt(Instant.now());
            employeeRepository.save(entity);
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

        employeeRepository.deleteById(employeeId);

        log.info("Deleted employee: {}", employeeId);
    }

    @Override
    public List<Employee> listEmployees(EmployeeQuery query) {
        return cache.values().stream()
            .filter(e -> query.type() == null ||
                (query.type() == IdUtils.EmployeeType.DIGITAL && e.isDigital()) ||
                (query.type() == IdUtils.EmployeeType.HUMAN && e.isHuman()))
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

        employeeRepository.findById(employeeId).ifPresent(entity -> {
            entity.setStatus(EmployeeStatus.TERMINATED.name());
            entity.setUpdatedAt(Instant.now());
            employeeRepository.save(entity);
        });
    }

    private Employee toDomain(EmployeeEntity entity) {
        if (entity instanceof DigitalEmployeeEntity digitalEntity) {
            return toDigitalEmployee(digitalEntity);
        } else if (entity instanceof HumanEmployeeEntity humanEntity) {
            return toHumanEmployee(humanEntity);
        }
        throw new IllegalArgumentException("Unknown entity type: " + entity.getClass());
    }

    private DigitalEmployee toDigitalEmployee(DigitalEmployeeEntity entity) {
        List<String> skills = parseJsonList(entity.getSkills());
        List<String> capabilities = parseJsonList(entity.getCapabilities());

        return DigitalEmployee.builder()
            .employeeId(entity.getId())
            .name(entity.getName())
            .title(entity.getPosition())
            .department(entity.getDepartment())
            .skills(skills)
            .capabilities(capabilities)
            .status(EmployeeStatus.valueOf(entity.getStatus()))
            .createdAt(entity.getCreatedAt())
            .build();
    }

    private HumanEmployee toHumanEmployee(HumanEmployeeEntity entity) {
        return HumanEmployee.builder()
            .employeeId(entity.getId())
            .name(entity.getName())
            .title(entity.getPosition())
            .department(entity.getDepartment())
            .status(EmployeeStatus.valueOf(entity.getStatus()))
            .createdAt(entity.getCreatedAt())
            .build();
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
}
