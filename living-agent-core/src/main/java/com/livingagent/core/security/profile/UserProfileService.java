package com.livingagent.core.security.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.UserIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    private final UserProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    public UserProfileService(UserProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
        this.objectMapper = new ObjectMapper();
    }

    public UserProfileEntity createProfileFromAuthContext(AuthContext authContext) {
        String profileId = "profile_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        UserProfileEntity profile = new UserProfileEntity();
        profile.setProfileId(profileId);
        profile.setEmployeeId(authContext.getEmployeeId());
        profile.setCreatedAt(Instant.now());
        profile.setUpdatedAt(Instant.now());
        profile.setLastActiveAt(Instant.now());
        
        initDefaultPersonality(profile);
        initDefaultBehaviorPreferences(profile);
        initDefaultKnowledgeAssociation(profile);
        initDefaultUsageStatistics(profile);
        
        UserProfileEntity saved = profileRepository.save(profile);
        log.info("Created user profile for employee: {} ({})", authContext.getEmployeeId(), profileId);
        return saved;
    }

    public Optional<UserProfileEntity> findById(String profileId) {
        return profileRepository.findById(profileId);
    }

    public Optional<UserProfileEntity> findByEmployeeId(String employeeId) {
        return profileRepository.findByEmployeeId(employeeId);
    }

    public Optional<UserProfileEntity> findBySpeakerId(String speakerId) {
        return profileRepository.findBySpeakerId(speakerId);
    }

    public Optional<UserProfileEntity> findByDigitalId(String digitalId) {
        return profileRepository.findByDigitalId(digitalId);
    }

    public UserProfileEntity updatePersonality(String profileId, Map<String, Object> personality) {
        Optional<UserProfileEntity> optProfile = profileRepository.findById(profileId);
        if (optProfile.isEmpty()) {
            throw new IllegalArgumentException("Profile not found: " + profileId);
        }
        
        UserProfileEntity profile = optProfile.get();
        try {
            profile.setPersonalityConfig(objectMapper.writeValueAsString(personality));
            profile.setUpdatedAt(Instant.now());
            return profileRepository.save(profile);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize personality config", e);
        }
    }

    public UserProfileEntity updateBehaviorPreferences(String profileId, Map<String, Object> preferences) {
        Optional<UserProfileEntity> optProfile = profileRepository.findById(profileId);
        if (optProfile.isEmpty()) {
            throw new IllegalArgumentException("Profile not found: " + profileId);
        }
        
        UserProfileEntity profile = optProfile.get();
        try {
            profile.setBehaviorPreferences(objectMapper.writeValueAsString(preferences));
            profile.setUpdatedAt(Instant.now());
            return profileRepository.save(profile);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize behavior preferences", e);
        }
    }

    public UserProfileEntity updateUsageStatistics(String profileId, Map<String, Object> statistics) {
        Optional<UserProfileEntity> optProfile = profileRepository.findById(profileId);
        if (optProfile.isEmpty()) {
            throw new IllegalArgumentException("Profile not found: " + profileId);
        }
        
        UserProfileEntity profile = optProfile.get();
        try {
            profile.setUsageStatistics(objectMapper.writeValueAsString(statistics));
            profile.setLastActiveAt(Instant.now());
            return profileRepository.save(profile);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize usage statistics", e);
        }
    }

    public UserProfileEntity linkVoicePrint(String profileId, String speakerId) {
        Optional<UserProfileEntity> optProfile = profileRepository.findById(profileId);
        if (optProfile.isEmpty()) {
            throw new IllegalArgumentException("Profile not found: " + profileId);
        }
        
        UserProfileEntity profile = optProfile.get();
        profile.setSpeakerId(speakerId);
        profile.setUpdatedAt(Instant.now());
        
        return profileRepository.save(profile);
    }

    public UserProfileEntity linkDigitalEmployee(String profileId, String digitalId) {
        Optional<UserProfileEntity> optProfile = profileRepository.findById(profileId);
        if (optProfile.isEmpty()) {
            throw new IllegalArgumentException("Profile not found: " + profileId);
        }
        
        UserProfileEntity profile = optProfile.get();
        profile.setDigitalId(digitalId);
        profile.setUpdatedAt(Instant.now());
        
        return profileRepository.save(profile);
    }

    public void recordSession(String profileId, long durationMs) {
        findByEmployeeId(profileId).ifPresent(profile -> {
            Map<String, Object> stats = parseJson(profile.getUsageStatistics());
            long totalSessions = getLong(stats, "totalSessions", 0) + 1;
            long totalTime = getLong(stats, "totalInteractionTime", 0) + durationMs;
            stats.put("totalSessions", totalSessions);
            stats.put("totalInteractionTime", totalTime);
            stats.put("lastSessionAt", Instant.now().toString());
            updateUsageStatistics(profileId, stats);
        });
    }

    public void recordTaskCompletion(String profileId, String skillId, boolean success) {
        findByEmployeeId(profileId).ifPresent(profile -> {
            Map<String, Object> stats = parseJson(profile.getUsageStatistics());
            long totalTasks = getLong(stats, "totalTasksCompleted", 0) + 1;
            stats.put("totalTasksCompleted", totalTasks);
            
            @SuppressWarnings("unchecked")
            Map<String, Integer> skillUsage = (Map<String, Integer>) stats.getOrDefault("skillUsageCount", new HashMap<>());
            skillUsage.put(skillId, skillUsage.getOrDefault(skillId, 0) + 1);
            stats.put("skillUsageCount", skillUsage);
            
            updateUsageStatistics(profileId, stats);
        });
    }

    public boolean hasAnyProfile() {
        return profileRepository.count() > 0;
    }

    public UserProfileEntity createVisitorProfile() {
        String profileId = "visitor_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        
        UserProfileEntity profile = new UserProfileEntity();
        profile.setProfileId(profileId);
        profile.setCreatedAt(Instant.now());
        profile.setUpdatedAt(Instant.now());
        profile.setLastActiveAt(Instant.now());
        
        initDefaultPersonality(profile);
        initDefaultBehaviorPreferences(profile);
        initDefaultKnowledgeAssociation(profile);
        initDefaultUsageStatistics(profile);
        
        return profileRepository.save(profile);
    }

    public UserProfileEntity createDigitalEmployeeProfile(String digitalId) {
        String profileId = "digital_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        
        UserProfileEntity profile = new UserProfileEntity();
        profile.setProfileId(profileId);
        profile.setDigitalId(digitalId);
        profile.setCreatedAt(Instant.now());
        profile.setUpdatedAt(Instant.now());
        profile.setLastActiveAt(Instant.now());
        
        initDefaultPersonality(profile);
        initDefaultBehaviorPreferences(profile);
        initDefaultKnowledgeAssociation(profile);
        initDefaultUsageStatistics(profile);
        
        return profileRepository.save(profile);
    }

    private void initDefaultPersonality(UserProfileEntity profile) {
        Map<String, Object> personality = new HashMap<>();
        personality.put("rigor", 0.5);
        personality.put("creativity", 0.5);
        personality.put("riskTolerance", 0.3);
        personality.put("obedience", 0.7);
        personality.put("source", "TEMPLATE");
        personality.put("updatedAt", Instant.now().toString());
        
        try {
            profile.setPersonalityConfig(objectMapper.writeValueAsString(personality));
        } catch (JsonProcessingException e) {
            log.error("Failed to init default personality", e);
        }
    }

    private void initDefaultBehaviorPreferences(UserProfileEntity profile) {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("preferredLanguage", "zh-CN");
        preferences.put("communicationStyle", "casual");
        preferences.put("prefersVoice", false);
        preferences.put("prefersText", true);
        preferences.put("interactionPattern", "EXPLORATORY");
        
        try {
            profile.setBehaviorPreferences(objectMapper.writeValueAsString(preferences));
        } catch (JsonProcessingException e) {
            log.error("Failed to init default behavior preferences", e);
        }
    }

    private void initDefaultKnowledgeAssociation(UserProfileEntity profile) {
        Map<String, Object> knowledge = new HashMap<>();
        knowledge.put("privateKnowledgeCount", 0);
        knowledge.put("privateKnowledgeSize", 0);
        knowledge.put("totalKnowledgeCreated", 0);
        knowledge.put("totalKnowledgeAccessed", 0);
        
        try {
            profile.setKnowledgeAssociation(objectMapper.writeValueAsString(knowledge));
        } catch (JsonProcessingException e) {
            log.error("Failed to init default knowledge association", e);
        }
    }

    private void initDefaultUsageStatistics(UserProfileEntity profile) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSessions", 0);
        stats.put("totalInteractionTime", 0);
        stats.put("totalTasksCompleted", 0);
        stats.put("taskSuccessRate", 0.0);
        stats.put("skillUsageCount", new HashMap<String, Integer>());
        
        try {
            profile.setUsageStatistics(objectMapper.writeValueAsString(stats));
        } catch (JsonProcessingException e) {
            log.error("Failed to init default usage statistics", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    private long getLong(Map<String, Object> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).longValue();
        return defaultValue;
    }
}
