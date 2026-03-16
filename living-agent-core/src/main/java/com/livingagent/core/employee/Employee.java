package com.livingagent.core.employee;

import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.UserIdentity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface Employee {

    String getEmployeeId();
    EmployeeType getEmployeeType();
    
    String getAuthId();
    String getAuthProvider();
    
    String getName();
    String getTitle();
    String getIcon();
    Optional<String> getEmail();
    Optional<String> getPhone();
    
    String getDepartment();
    String getDepartmentId();
    List<String> getRoles();
    Optional<String> getManagerId();
    
    List<String> getCapabilities();
    List<String> getSkills();
    List<String> getTools();
    AccessLevel getAccessLevel();
    UserIdentity getIdentity();
    
    EmployeePersonality getPersonality();
    EmployeeStatus getStatus();
    
    int getTaskCount();
    int getSuccessCount();
    double getSuccessRate();
    
    Instant getCreatedAt();
    Instant getLastActiveAt();
    Optional<Instant> getExpiresAt();

    enum EmployeeType {
        HUMAN,
        DIGITAL
    }

    boolean isHuman();
    boolean isDigital();
    
    HumanConfig getHumanConfig();
    DigitalConfig getDigitalConfig();

    interface HumanConfig {
        String getDingTalkId();
        String getFeishuId();
        String getWecomId();
        String getOaAccountId();
        NotificationPreference getNotificationPreference();
        WorkSchedule getWorkSchedule();
    }

    interface DigitalConfig {
        String getNeuronId();
        List<String> getSubscribeChannels();
        List<String> getPublishChannels();
        List<WorkflowBinding> getWorkflowBindings();
        LearningConfig getLearningConfig();
        boolean isAutoDormant();
        java.time.Duration getMaxIdleTime();
    }

    interface WorkflowBinding {
        String getTrigger();
        String getWorkflowId();
        String getDescription();
        int getPriority();
    }

    interface LearningConfig {
        boolean isEnabled();
        List<String> getSources();
    }

    interface NotificationPreference {
        boolean isDingTalkEnabled();
        boolean isFeishuEnabled();
        boolean isEmailEnabled();
        boolean isSmsEnabled();
    }

    interface WorkSchedule {
        List<String> getWorkDays();
        String getWorkHours();
        boolean isWorkingTime(Instant time);
    }
}
