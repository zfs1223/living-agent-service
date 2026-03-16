package com.livingagent.core.employee.impl;

import com.livingagent.core.employee.*;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.util.IdUtils;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

public class HumanEmployee implements Employee {

    private final String employeeId;
    private final String authId;
    private final String authProvider;
    
    private final String name;
    private final String title;
    private final String icon;
    private final String email;
    private final String phone;
    
    private final String department;
    private final String departmentId;
    private final List<String> roles;
    private final String managerId;
    
    private final List<String> capabilities;
    private final List<String> skills;
    private final List<String> tools;
    private final AccessLevel accessLevel;
    private final UserIdentity identity;
    
    private EmployeePersonality personality;
    private EmployeeStatus status;
    
    private final String dingTalkId;
    private final String feishuId;
    private final String wecomId;
    private final String oaAccountId;
    private final NotificationPreference notificationPreference;
    private final WorkSchedule workSchedule;
    
    private int taskCount;
    private int successCount;
    private final Instant createdAt;
    private Instant lastActiveAt;

    private HumanEmployee(Builder builder) {
        this.employeeId = builder.employeeId;
        this.authId = builder.authId;
        this.authProvider = builder.authProvider;
        this.name = builder.name;
        this.title = builder.title;
        this.icon = builder.icon;
        this.email = builder.email;
        this.phone = builder.phone;
        this.department = builder.department;
        this.departmentId = builder.departmentId;
        this.roles = Collections.unmodifiableList(builder.roles);
        this.managerId = builder.managerId;
        this.capabilities = Collections.unmodifiableList(builder.capabilities);
        this.skills = Collections.unmodifiableList(builder.skills);
        this.tools = Collections.unmodifiableList(builder.tools);
        this.accessLevel = builder.accessLevel;
        this.identity = builder.identity;
        this.personality = builder.personality;
        this.status = builder.status;
        this.dingTalkId = builder.dingTalkId;
        this.feishuId = builder.feishuId;
        this.wecomId = builder.wecomId;
        this.oaAccountId = builder.oaAccountId;
        this.notificationPreference = builder.notificationPreference;
        this.workSchedule = builder.workSchedule;
        this.createdAt = builder.createdAt;
        this.lastActiveAt = builder.lastActiveAt;
    }

    @Override
    public String getEmployeeId() { return employeeId; }
    
    @Override
    public EmployeeType getEmployeeType() { return EmployeeType.HUMAN; }
    
    @Override
    public String getAuthId() { return authId; }
    
    @Override
    public String getAuthProvider() { return authProvider; }
    
    @Override
    public String getName() { return name; }
    
    @Override
    public String getTitle() { return title; }
    
    @Override
    public String getIcon() { return icon; }
    
    @Override
    public Optional<String> getEmail() { return Optional.ofNullable(email); }
    
    @Override
    public Optional<String> getPhone() { return Optional.ofNullable(phone); }
    
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
    public Optional<Instant> getExpiresAt() { return Optional.empty(); }
    
    @Override
    public boolean isHuman() { return true; }
    
    @Override
    public boolean isDigital() { return false; }
    
    @Override
    public HumanConfig getHumanConfig() {
        return new HumanConfig() {
            @Override
            public String getDingTalkId() { return dingTalkId; }
            
            @Override
            public String getFeishuId() { return feishuId; }
            
            @Override
            public String getWecomId() { return wecomId; }
            
            @Override
            public String getOaAccountId() { return oaAccountId; }
            
            @Override
            public NotificationPreference getNotificationPreference() { return notificationPreference; }
            
            @Override
            public WorkSchedule getWorkSchedule() { return workSchedule; }
        };
    }
    
    @Override
    public DigitalConfig getDigitalConfig() { return null; }

    public void setPersonality(EmployeePersonality personality) {
        this.personality = personality;
    }
    
    public void setStatus(EmployeeStatus status) {
        if (this.status.canTransitionTo(status)) {
            this.status = status;
        } else {
            throw new IllegalStateException(
                "Cannot transition from " + this.status + " to " + status);
        }
    }
    
    public void recordTask(boolean success) {
        this.taskCount++;
        if (success) this.successCount++;
        this.lastActiveAt = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String employeeId;
        private String authId;
        private String authProvider = "DINGTALK";
        
        private String name;
        private String title;
        private String icon = "👤";
        private String email;
        private String phone;
        
        private String department;
        private String departmentId;
        private List<String> roles = new ArrayList<>();
        private String managerId;
        
        private List<String> capabilities = new ArrayList<>();
        private List<String> skills = new ArrayList<>();
        private List<String> tools = new ArrayList<>();
        private AccessLevel accessLevel = AccessLevel.DEPARTMENT;
        private UserIdentity identity = UserIdentity.INTERNAL_ACTIVE;
        
        private EmployeePersonality personality;
        private EmployeeStatus status = EmployeeStatus.ACTIVE;
        
        private String dingTalkId;
        private String feishuId;
        private String wecomId;
        private String oaAccountId;
        private NotificationPreference notificationPreference = new DefaultNotificationPreference();
        private WorkSchedule workSchedule = new DefaultWorkSchedule();
        
        private Instant createdAt = Instant.now();
        private Instant lastActiveAt = Instant.now();

        public Builder employeeId(String employeeId) {
            this.employeeId = employeeId;
            return this;
        }

        public Builder authId(String authId) {
            this.authId = authId;
            return this;
        }

        public Builder authProvider(String authProvider) {
            this.authProvider = authProvider;
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

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder phone(String phone) {
            this.phone = phone;
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

        public Builder personality(EmployeePersonality personality) {
            this.personality = personality;
            return this;
        }

        public Builder status(EmployeeStatus status) {
            this.status = status;
            return this;
        }

        public Builder dingTalkId(String dingTalkId) {
            this.dingTalkId = dingTalkId;
            return this;
        }

        public Builder feishuId(String feishuId) {
            this.feishuId = feishuId;
            return this;
        }

        public Builder wecomId(String wecomId) {
            this.wecomId = wecomId;
            return this;
        }

        public Builder oaAccountId(String oaAccountId) {
            this.oaAccountId = oaAccountId;
            return this;
        }

        public Builder notificationPreference(NotificationPreference preference) {
            this.notificationPreference = preference;
            return this;
        }

        public Builder workSchedule(WorkSchedule schedule) {
            this.workSchedule = schedule;
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

        public HumanEmployee build() {
            Objects.requireNonNull(name, "name is required");
            Objects.requireNonNull(department, "department is required");
            
            if (employeeId == null && dingTalkId != null) {
                employeeId = IdUtils.generateHumanEmployeeId(IdUtils.AuthProvider.DINGTALK, dingTalkId);
            }
            
            if (authId == null) {
                authId = dingTalkId != null ? dingTalkId : 
                         feishuId != null ? feishuId :
                         wecomId != null ? wecomId : name;
            }
            
            if (personality == null) {
                personality = EmployeePersonality.defaultForDepartment(department);
            }
            
            return new HumanEmployee(this);
        }
    }

    private static class DefaultNotificationPreference implements NotificationPreference {
        @Override
        public boolean isDingTalkEnabled() { return true; }
        
        @Override
        public boolean isFeishuEnabled() { return false; }
        
        @Override
        public boolean isEmailEnabled() { return true; }
        
        @Override
        public boolean isSmsEnabled() { return false; }
    }

    private static class DefaultWorkSchedule implements WorkSchedule {
        private static final List<String> DEFAULT_WORK_DAYS = List.of(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"
        );
        private static final String DEFAULT_WORK_HOURS = "09:00-18:00";
        
        @Override
        public List<String> getWorkDays() { return DEFAULT_WORK_DAYS; }
        
        @Override
        public String getWorkHours() { return DEFAULT_WORK_HOURS; }
        
        @Override
        public boolean isWorkingTime(Instant time) {
            ZonedDateTime zdt = time.atZone(ZoneId.of("Asia/Shanghai"));
            DayOfWeek dayOfWeek = zdt.getDayOfWeek();
            LocalTime localTime = zdt.toLocalTime();
            
            boolean isWorkDay = DEFAULT_WORK_DAYS.contains(dayOfWeek.name());
            if (!isWorkDay) return false;
            
            LocalTime workStart = LocalTime.of(9, 0);
            LocalTime workEnd = LocalTime.of(18, 0);
            
            return !localTime.isBefore(workStart) && localTime.isBefore(workEnd);
        }
    }
}
