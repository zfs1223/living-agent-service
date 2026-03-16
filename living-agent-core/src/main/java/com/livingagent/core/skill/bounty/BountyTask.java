package com.livingagent.core.skill.bounty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface BountyTask {

    String getTaskId();
    
    String getTitle();
    
    String getDescription();
    
    BountyType getType();
    
    BountyStatus getStatus();
    
    double getReward();
    
    String getCurrency();
    
    DifficultyLevel getDifficulty();
    
    Instant getDeadline();
    
    Instant getCreatedAt();
    
    Instant getAcceptedAt();
    
    Instant getCompletedAt();
    
    String getAcceptedBy();
    
    Map<String, Object> getRequirements();
    
    Map<String, Object> getDeliverables();
    
    List<String> getRequiredSkills();
    
    boolean isEligible(String workerId, List<String> skills);
    
    enum BountyType {
        DEVELOPMENT,
        TESTING,
        DOCUMENTATION,
        DESIGN,
        ANALYSIS,
        CONSULTATION,
        TRANSLATION,
        DATA_ENTRY,
        RESEARCH,
        OTHER
    }
    
    enum BountyStatus {
        OPEN,
        RESERVED,
        IN_PROGRESS,
        UNDER_REVIEW,
        COMPLETED,
        CANCELLED,
        EXPIRED
    }
    
    enum DifficultyLevel {
        BEGINNER(1),
        INTERMEDIATE(2),
        ADVANCED(3),
        EXPERT(4),
        MASTER(5);
        
        private final int level;
        
        DifficultyLevel(int level) {
            this.level = level;
        }
        
        public int getLevel() { return level; }
    }
}
