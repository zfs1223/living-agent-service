package com.livingagent.core.worker.template;

import com.livingagent.core.worker.DigitalWorker.WorkerType;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface WorkerTemplate {

    String getTemplateId();
    
    String getName();
    
    String getDescription();
    
    WorkerType getWorkerType();
    
    String getDefaultDepartment();
    
    String getDefaultRole();
    
    Set<String> getRequiredCapabilities();
    
    Set<String> getDefaultSkills();
    
    List<String> getSubscribedChannels();
    
    List<String> getPublishChannels();
    
    Map<String, Object> getDefaultConfig();
    
    PersonalityTemplate getPersonalityTemplate();
    
    int getMinExperienceLevel();
    
    int getMaxExperienceLevel();
    
    boolean isAutoDiscoverable();
    
    TemplateMetadata getMetadata();
    
    record PersonalityTemplate(
        double openness,
        double conscientiousness,
        double extraversion,
        double agreeableness,
        double neuroticism,
        double creativity,
        double analyticalThinking,
        double communicationStyle
    ) {
        public static PersonalityTemplate DEFAULT = new PersonalityTemplate(
            0.7, 0.8, 0.6, 0.7, 0.3, 0.6, 0.7, 0.6
        );
    }
    
    record TemplateMetadata(
        String author,
        String version,
        String category,
        List<String> tags,
        long createdAt,
        long updatedAt
    ) {}
}
