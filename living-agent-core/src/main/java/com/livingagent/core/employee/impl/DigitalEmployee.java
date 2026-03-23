package com.livingagent.core.employee.impl;

import com.livingagent.core.employee.*;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.util.IdUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class DigitalEmployee implements Employee {

    private final String employeeId;
    private final String neuronId;
    private final String name;
    private final String title;
    private final String icon;
    private final String department;
    private final String departmentId;
    private final List<String> roles;
    private final String managerId;
    
    private final List<String> capabilities;
    private final List<String> skills;
    private final List<String> tools;
    private final AccessLevel accessLevel;
    private final UserIdentity identity;
    private final AccessType accessType;
    private final boolean isPublic;
    
    private EmployeePersonality personality;
    private EmployeeStatus status;
    
    private final List<String> subscribeChannels;
    private final List<String> publishChannels;
    private final List<WorkflowBinding> workflowBindings;
    private final LearningConfig learningConfig;
    private final boolean autoDormant;
    private final Duration maxIdleTime;
    
    private int taskCount;
    private int successCount;
    private final Instant createdAt;
    private Instant lastActiveAt;
    private Instant expiresAt;

    private DigitalEmployee(Builder builder) {
        this.employeeId = builder.employeeId;
        this.neuronId = IdUtils.employeeToNeuronId(builder.employeeId);
        this.name = builder.name;
        this.title = builder.title;
        this.icon = builder.icon;
        this.department = builder.department;
        this.departmentId = builder.departmentId;
        this.roles = Collections.unmodifiableList(builder.roles);
        this.managerId = builder.managerId;
        this.capabilities = Collections.unmodifiableList(builder.capabilities);
        this.skills = Collections.unmodifiableList(builder.skills);
        this.tools = Collections.unmodifiableList(builder.tools);
        this.accessLevel = builder.accessLevel;
        this.identity = builder.identity;
        this.accessType = builder.accessType;
        this.isPublic = builder.isPublic;
        this.personality = builder.personality;
        this.status = builder.status;
        this.subscribeChannels = Collections.unmodifiableList(builder.subscribeChannels);
        this.publishChannels = Collections.unmodifiableList(builder.publishChannels);
        this.workflowBindings = Collections.unmodifiableList(builder.workflowBindings);
        this.learningConfig = builder.learningConfig;
        this.autoDormant = builder.autoDormant;
        this.maxIdleTime = builder.maxIdleTime;
        this.createdAt = builder.createdAt;
        this.lastActiveAt = builder.lastActiveAt;
        this.expiresAt = builder.expiresAt;
    }

    @Override
    public String getEmployeeId() { return employeeId; }
    
    @Override
    public EmployeeType getEmployeeType() { return EmployeeType.DIGITAL; }
    
    @Override
    public String getAuthId() { return employeeId; }
    
    @Override
    public String getAuthProvider() { return "SYSTEM"; }
    
    @Override
    public String getName() { return name; }
    
    @Override
    public String getTitle() { return title; }
    
    @Override
    public String getIcon() { return icon; }
    
    @Override
    public Optional<String> getEmail() { return Optional.empty(); }
    
    @Override
    public Optional<String> getPhone() { return Optional.empty(); }
    
    @Override
    public String getDepartment() { return department; }
    
    @Override
    public String getDepartmentId() { return departmentId; }
    
    @Override
    public List<String> getRoles() { return roles; }
    
    @Override
    public Optional<String> getManagerId() { return Optional.ofNullable(managerId); }
    
    @Override
    public List<String> getCapabilities() { return capabilities; }
    
    @Override
    public List<String> getSkills() { return skills; }
    
    @Override
    public List<String> getTools() { return tools; }
    
    @Override
    public AccessLevel getAccessLevel() { return accessLevel; }
    
    @Override
    public UserIdentity getIdentity() { return identity; }
    
    @Override
    public EmployeePersonality getPersonality() { return personality; }
    
    @Override
    public EmployeeStatus getStatus() { return status; }
    
    @Override
    public int getTaskCount() { return taskCount; }
    
    @Override
    public int getSuccessCount() { return successCount; }
    
    @Override
    public double getSuccessRate() {
        return taskCount > 0 ? (double) successCount / taskCount : 0.0;
    }
    
    @Override
    public Instant getCreatedAt() { return createdAt; }
    
    @Override
    public Instant getLastActiveAt() { return lastActiveAt; }
    
    @Override
    public Optional<Instant> getExpiresAt() { return Optional.ofNullable(expiresAt); }
    
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
            public String getNeuronId() { return neuronId; }
            
            @Override
            public List<String> getSubscribeChannels() { return subscribeChannels; }
            
            @Override
            public List<String> getPublishChannels() { return publishChannels; }
            
            @Override
            public List<WorkflowBinding> getWorkflowBindings() { return workflowBindings; }
            
            @Override
            public LearningConfig getLearningConfig() { return learningConfig; }
            
            @Override
            public boolean isAutoDormant() { return autoDormant; }
            
            @Override
            public Duration getMaxIdleTime() { return maxIdleTime; }
        };
    }

    public String getNeuronId() { return neuronId; }
    
    public AccessType getAccessType() { return accessType; }
    
    public boolean isPublic() { return isPublic; }
    
    public void setPersonality(EmployeePersonality personality) {
        this.personality = personality;
    }
    
    public void setStatus(EmployeeStatus status) {
        if (this.status != null && this.status != status && !this.status.canTransitionTo(status)) {
            throw new IllegalStateException(
                "Cannot transition from " + this.status + " to " + status);
        }
        this.status = status;
    }
    
    public void recordTask(boolean success) {
        this.taskCount++;
        if (success) this.successCount++;
        this.lastActiveAt = Instant.now();
    }
    
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String employeeId;
        private String name;
        private String title;
        private String icon = "🤖";
        private String department;
        private String departmentId;
        private List<String> roles = new ArrayList<>();
        private String managerId;
        
        private List<String> capabilities = new ArrayList<>();
        private List<String> skills = new ArrayList<>();
        private List<String> tools = new ArrayList<>();
        private AccessLevel accessLevel = AccessLevel.DEPARTMENT;
        private UserIdentity identity = UserIdentity.INTERNAL_ACTIVE;
        private AccessType accessType = AccessType.DEPARTMENT;
        private boolean isPublic = false;
        
        private EmployeePersonality personality;
        private EmployeeStatus status = EmployeeStatus.ACTIVE;
        
        private List<String> subscribeChannels = new ArrayList<>();
        private List<String> publishChannels = new ArrayList<>();
        private List<WorkflowBinding> workflowBindings = new ArrayList<>();
        private LearningConfig learningConfig = new LearningConfig() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public List<String> getSources() {
                return List.of();
            }
        };
        private boolean autoDormant = true;
        private Duration maxIdleTime = Duration.ofDays(7);
        
        private Instant createdAt = Instant.now();
        private Instant lastActiveAt = Instant.now();
        private Instant expiresAt;

        public Builder employeeId(String employeeId) {
            this.employeeId = employeeId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder department(String department) {
            this.department = department;
            return this;
        }

        public Builder departmentId(String departmentId) {
            this.departmentId = departmentId;
            return this;
        }

        public Builder roles(List<String> roles) {
            this.roles = new ArrayList<>(roles);
            return this;
        }

        public Builder addRole(String role) {
            this.roles.add(role);
            return this;
        }

        public Builder managerId(String managerId) {
            this.managerId = managerId;
            return this;
        }

        public Builder capabilities(List<String> capabilities) {
            this.capabilities = new ArrayList<>(capabilities);
            return this;
        }

        public Builder addCapability(String capability) {
            this.capabilities.add(capability);
            return this;
        }

        public Builder skills(List<String> skills) {
            this.skills = new ArrayList<>(skills);
            return this;
        }

        public Builder addSkill(String skill) {
            this.skills.add(skill);
            return this;
        }

        public Builder tools(List<String> tools) {
            this.tools = new ArrayList<>(tools);
            return this;
        }

        public Builder addTool(String tool) {
            this.tools.add(tool);
            return this;
        }

        public Builder accessLevel(AccessLevel accessLevel) {
            this.accessLevel = accessLevel;
            return this;
        }

        public Builder identity(UserIdentity identity) {
            this.identity = identity;
            return this;
        }

        public Builder accessType(AccessType accessType) {
            this.accessType = accessType;
            this.isPublic = accessType.isPublicAccess();
            return this;
        }

        public Builder isPublic(boolean isPublic) {
            this.isPublic = isPublic;
            return this;
        }

        public Builder personality(EmployeePersonality personality) {
            this.personality = personality;
            return this;
        }

        public Builder status(EmployeeStatus status) {
            this.status = status;
            return this;
        }

        public Builder subscribeChannels(List<String> channels) {
            this.subscribeChannels = new ArrayList<>(channels);
            return this;
        }

        public Builder addSubscribeChannel(String channel) {
            this.subscribeChannels.add(channel);
            return this;
        }

        public Builder publishChannels(List<String> channels) {
            this.publishChannels = new ArrayList<>(channels);
            return this;
        }

        public Builder addPublishChannel(String channel) {
            this.publishChannels.add(channel);
            return this;
        }

        public Builder workflowBindings(List<WorkflowBinding> bindings) {
            this.workflowBindings = new ArrayList<>(bindings);
            return this;
        }

        public Builder addWorkflowBinding(WorkflowBinding binding) {
            this.workflowBindings.add(binding);
            return this;
        }

        public Builder learningConfig(LearningConfig config) {
            this.learningConfig = config;
            return this;
        }

        public Builder autoDormant(boolean autoDormant) {
            this.autoDormant = autoDormant;
            return this;
        }

        public Builder maxIdleTime(Duration maxIdleTime) {
            this.maxIdleTime = maxIdleTime;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder lastActiveAt(Instant lastActiveAt) {
            this.lastActiveAt = lastActiveAt;
            return this;
        }

        public DigitalEmployee build() {
            Objects.requireNonNull(employeeId, "employeeId is required");
            Objects.requireNonNull(name, "name is required");
            Objects.requireNonNull(department, "department is required");
            
            if (personality == null) {
                personality = EmployeePersonality.defaultForDepartment(department);
            }
            
            return new DigitalEmployee(this);
        }
    }
}
