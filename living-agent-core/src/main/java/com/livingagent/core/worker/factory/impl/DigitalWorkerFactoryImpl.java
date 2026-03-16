package com.livingagent.core.worker.factory.impl;

import com.livingagent.core.employee.*;
import com.livingagent.core.employee.EmployeePersonality;
import com.livingagent.core.employee.EmployeeStatus;
import com.livingagent.core.employee.impl.DigitalEmployee;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.util.IdUtils;
import com.livingagent.core.worker.*;
import com.livingagent.core.worker.WorkerMetrics;
import com.livingagent.core.worker.factory.DigitalWorkerFactory;
import com.livingagent.core.worker.template.WorkerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DigitalWorkerFactoryImpl implements DigitalWorkerFactory {

    private static final Logger log = LoggerFactory.getLogger(DigitalWorkerFactoryImpl.class);

    private final Map<String, WorkerTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, DigitalWorker> workers = new ConcurrentHashMap<>();

    @Override
    public DigitalWorker createWorker(WorkerTemplate template) {
        return createWorker(template, Map.of());
    }

    @Override
    public DigitalWorker createWorker(WorkerTemplate template, Map<String, Object> overrides) {
        WorkerCreationResult validation = validateCreation(template, overrides);
        if (!validation.success()) {
            throw new IllegalArgumentException(validation.errorMessage());
        }

        String instanceId = UUID.randomUUID().toString().substring(0, 8);
        String department = (String) overrides.getOrDefault("department", template.getDefaultDepartment());
        String role = (String) overrides.getOrDefault("role", template.getDefaultRole());
        String workerId = IdUtils.generateNeuronId(department, role, instanceId);

        DigitalWorkerImpl worker = new DigitalWorkerImpl(
            workerId,
            template.getTemplateId(),
            template.getName(),
            template.getDescription(),
            template.getWorkerType(),
            department,
            role,
            instanceId,
            createPersonality(template.getPersonalityTemplate(), overrides),
            new HashSet<>(template.getDefaultSkills()),
            new ArrayList<>(template.getSubscribedChannels()),
            new ArrayList<>(template.getPublishChannels()),
            new HashMap<>(template.getDefaultConfig())
        );

        workers.put(workerId, worker);
        log.info("Created digital worker: {} from template: {}", workerId, template.getTemplateId());

        return worker;
    }

    @Override
    public DigitalWorker createWorker(String templateId) {
        return createWorker(templateId, Map.of());
    }

    @Override
    public DigitalWorker createWorker(String templateId, Map<String, Object> overrides) {
        WorkerTemplate template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }
        return createWorker(template, overrides);
    }

    @Override
    public Optional<DigitalWorker> getWorker(String workerId) {
        return Optional.ofNullable(workers.get(workerId));
    }

    @Override
    public void destroyWorker(String workerId) {
        DigitalWorker worker = workers.remove(workerId);
        if (worker != null) {
            log.info("Destroyed digital worker: {}", workerId);
        }
    }

    @Override
    public boolean workerExists(String workerId) {
        return workers.containsKey(workerId);
    }

    @Override
    public void registerTemplate(WorkerTemplate template) {
        templates.put(template.getTemplateId(), template);
        log.info("Registered worker template: {}", template.getTemplateId());
    }

    @Override
    public void unregisterTemplate(String templateId) {
        templates.remove(templateId);
        log.info("Unregistered worker template: {}", templateId);
    }

    @Override
    public Optional<WorkerTemplate> getTemplate(String templateId) {
        return Optional.ofNullable(templates.get(templateId));
    }

    @Override
    public Map<String, WorkerTemplate> getAvailableTemplates() {
        return new HashMap<>(templates);
    }

    @Override
    public WorkerCreationResult validateCreation(WorkerTemplate template, Map<String, Object> overrides) {
        Map<String, String> errors = new HashMap<>();

        if (template == null) {
            return WorkerCreationResult.failure("Template is required");
        }

        if (template.getName() == null || template.getName().isBlank()) {
            errors.put("name", "Template name is required");
        }

        if (template.getDefaultDepartment() == null || template.getDefaultDepartment().isBlank()) {
            errors.put("department", "Default department is required");
        }

        if (template.getDefaultRole() == null || template.getDefaultRole().isBlank()) {
            errors.put("role", "Default role is required");
        }

        if (!errors.isEmpty()) {
            return WorkerCreationResult.validationFailed(errors);
        }

        return WorkerCreationResult.success("validation-passed");
    }

    private EmployeePersonality createPersonality(
            WorkerTemplate.PersonalityTemplate template,
            Map<String, Object> overrides) {
        
        return new EmployeePersonality(
            (Double) overrides.getOrDefault("rigor", template.openness()),
            (Double) overrides.getOrDefault("creativity", template.creativity()),
            (Double) overrides.getOrDefault("riskTolerance", template.extraversion()),
            (Double) overrides.getOrDefault("obedience", template.agreeableness()),
            EmployeePersonality.PersonalitySource.TEMPLATE,
            Instant.now()
        );
    }

    private static class DigitalWorkerImpl implements DigitalWorker {

        private final String id;
        private final String templateId;
        private final String name;
        private final String description;
        private final WorkerType workerType;
        private final String department;
        private final String role;
        private final String instance;
        private final EmployeePersonality personality;
        private final Set<String> skills;
        private final List<String> subscribedChannels;
        private final List<String> publishChannels;
        private final Map<String, Object> config;
        private final WorkerMetrics metrics;
        private final Instant createdAt;
        
        private volatile Instant lastActiveAt;
        private volatile EmployeeStatus status = EmployeeStatus.ACTIVE;
        private volatile int experienceLevel = 1;

        DigitalWorkerImpl(
                String id,
                String templateId,
                String name,
                String description,
                WorkerType workerType,
                String department,
                String role,
                String instance,
                EmployeePersonality personality,
                Set<String> skills,
                List<String> subscribedChannels,
                List<String> publishChannels,
                Map<String, Object> config) {
            
            this.id = id;
            this.templateId = templateId;
            this.name = name;
            this.description = description;
            this.workerType = workerType;
            this.department = department;
            this.role = role;
            this.instance = instance;
            this.personality = personality;
            this.skills = ConcurrentHashMap.newKeySet();
            this.skills.addAll(skills);
            this.subscribedChannels = Collections.unmodifiableList(subscribedChannels);
            this.publishChannels = Collections.unmodifiableList(publishChannels);
            this.config = new ConcurrentHashMap<>(config);
            this.metrics = new WorkerMetrics();
            this.createdAt = Instant.now();
            this.lastActiveAt = this.createdAt;
        }

        public String getId() { return id; }

        @Override
        public String getEmployeeId() { return id; }

        @Override
        public String getTemplateId() { return templateId; }

        @Override
        public String getName() { return name; }

        public String getDescription() { return description; }

        @Override
        public WorkerType getWorkerType() { return workerType; }

        @Override
        public String getDepartment() { return department; }

        public String getRole() { return role; }

        @Override
        public EmployeeStatus getStatus() { return status; }

        public void setStatus(EmployeeStatus status) { this.status = status; }

        @Override
        public EmployeePersonality getPersonality() { return personality; }

        @Override
        public int getExperienceLevel() { return experienceLevel; }

        @Override
        public long getTotalTasksCompleted() { return metrics.getTasksCompleted(); }

        @Override
        public double getSuccessRate() { return metrics.getSuccessRate(); }

        @Override
        public List<String> getCapabilities() {
            return List.copyOf(skills);
        }

        @Override
        public List<String> getSkills() {
            return List.copyOf(skills);
        }

        @Override
        public List<String> getTools() {
            return List.of();
        }

        @Override
        public AccessLevel getAccessLevel() {
            return AccessLevel.DEPARTMENT;
        }

        @Override
        public UserIdentity getIdentity() {
            return UserIdentity.INTERNAL_ACTIVE;
        }

        @Override
        public Set<String> getLearnedSkills() { return skills; }

        @Override
        public void learnSkill(String skillId) {
            skills.add(skillId);
        }

        @Override
        public void forgetSkill(String skillId) {
            skills.remove(skillId);
        }

        @Override
        public boolean hasCapability(String capability) {
            return skills.contains(capability);
        }

        @Override
        public WorkerMetrics getMetrics() { return metrics; }

        @Override
        public void recordTaskCompletion(boolean success, long durationMs) {
            metrics.recordTask(success, durationMs, null, 0);
            lastActiveAt = Instant.now();
            
            if (metrics.getTasksCompleted() % 100 == 0 && metrics.getSuccessRate() > 0.8) {
                experienceLevel = Math.min(10, experienceLevel + 1);
            }
        }

        @Override
        public void updateMetrics(WorkerMetrics newMetrics) {
        }

        @Override
        public Instant getCreatedAt() { return createdAt; }

        @Override
        public Instant getLastActiveAt() { return lastActiveAt; }

        @Override
        public void touch() {
            lastActiveAt = Instant.now();
        }

        @Override
        public EmployeeType getEmployeeType() { return EmployeeType.DIGITAL; }

        @Override
        public String getAuthId() { return "digital-" + id; }

        @Override
        public String getAuthProvider() { return "internal"; }

        @Override
        public String getTitle() { return role; }

        @Override
        public String getIcon() { return "robot"; }

        @Override
        public Optional<String> getEmail() { return Optional.empty(); }

        @Override
        public Optional<String> getPhone() { return Optional.empty(); }

        @Override
        public String getDepartmentId() { return department != null ? department.toLowerCase().replace(" ", "-") : "unknown"; }

        @Override
        public List<String> getRoles() { return List.of(role); }

        @Override
        public Optional<String> getManagerId() { return Optional.empty(); }

        @Override
        public Optional<Instant> getExpiresAt() { return Optional.empty(); }

        @Override
        public int getTaskCount() { return (int) metrics.getTasksCompleted(); }

        @Override
        public int getSuccessCount() { return (int) (metrics.getTasksCompleted() * metrics.getSuccessRate()); }

        @Override
        public boolean isHuman() { return false; }

        @Override
        public boolean isDigital() { return true; }

        @Override
        public HumanConfig getHumanConfig() { return null; }

        @Override
        public DigitalConfig getDigitalConfig() {
            return new DigitalConfig() {
                @Override
                public String getNeuronId() { return id; }
                
                @Override
                public List<String> getSubscribeChannels() { return subscribedChannels; }
                
                @Override
                public List<String> getPublishChannels() { return publishChannels; }
                
                @Override
                public List<WorkflowBinding> getWorkflowBindings() { return List.of(); }
                
                @Override
                public LearningConfig getLearningConfig() {
                    return new LearningConfig() {
                        @Override
                        public boolean isEnabled() { return true; }
                        
                        @Override
                        public List<String> getSources() { return List.of("experience", "feedback"); }
                    };
                }
                
                @Override
                public boolean isAutoDormant() { return true; }
                
                @Override
                public java.time.Duration getMaxIdleTime() { return java.time.Duration.ofHours(1); }
            };
        }

        public boolean isWorkingTime(Instant time) { return true; }
    }
}
