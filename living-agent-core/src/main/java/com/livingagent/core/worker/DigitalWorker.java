package com.livingagent.core.worker;

import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeePersonality;
import com.livingagent.core.employee.EmployeeStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface DigitalWorker extends Employee {

    String getTemplateId();
    
    WorkerType getWorkerType();
    
    int getExperienceLevel();
    
    long getTotalTasksCompleted();
    
    double getSuccessRate();
    
    List<String> getCapabilities();
    
    Set<String> getLearnedSkills();
    
    void learnSkill(String skillId);
    
    void forgetSkill(String skillId);
    
    boolean hasCapability(String capability);
    
    WorkerMetrics getMetrics();
    
    void recordTaskCompletion(boolean success, long durationMs);
    
    void updateMetrics(WorkerMetrics metrics);
    
    Instant getCreatedAt();
    
    Instant getLastActiveAt();
    
    void touch();
    
    enum WorkerType {
        SPECIALIST,
        GENERALIST,
        COORDINATOR,
        ANALYST,
        CREATOR,
        MANAGER
    }
}
