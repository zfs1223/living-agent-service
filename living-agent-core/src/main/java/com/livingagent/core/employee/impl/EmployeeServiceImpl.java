package com.livingagent.core.employee.impl;

import com.livingagent.core.employee.*;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.neuron.NeuronState;
import com.livingagent.core.util.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeServiceImpl.class);

    private final Map<String, Employee> employeeStore = new ConcurrentHashMap<>();
    private final Map<String, String> authIndex = new ConcurrentHashMap<>();
    private final NeuronRegistry neuronRegistry;

    public EmployeeServiceImpl(NeuronRegistry neuronRegistry) {
        this.neuronRegistry = neuronRegistry;
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
            String instance = String.format("%03d", 
                employeeStore.values().stream()
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
        return employeeStore.values().stream()
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
}
