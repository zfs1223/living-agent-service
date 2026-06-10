package com.livingagent.skill.registry;

import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.skill.Skill;
import com.livingagent.core.skill.SkillRegistry;
import com.livingagent.skill.loader.SkillLoader;
import com.livingagent.skill.model.SkillImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class SkillRegistryImpl implements SkillRegistry {
    private static final Logger log = LoggerFactory.getLogger(SkillRegistryImpl.class);

    private final SkillLoader skillLoader;
    
    @Value("${skill.built-in.path:classpath:skills}")
    private String builtInPath;
    
    @Value("${skill.config.path:./config/skills}")
    private String configPath;
    
    @Value("${skill.data.path:./data/skills}")
    private String dataPath;

    private final Map<String, Skill> skillsByName = new ConcurrentHashMap<>();
    private final Map<String, List<Skill>> skillsByBrain = new ConcurrentHashMap<>();
    private final Map<String, List<Skill>> skillsByCategory = new ConcurrentHashMap<>();
    
    private volatile boolean isReloading = false;
    private final Object reloadLock = new Object();

    @Autowired
    public SkillRegistryImpl(SkillLoader skillLoader) {
        this.skillLoader = skillLoader;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing SkillRegistry...");
        loadBuiltInSkills();
        loadConfigSkills();
        loadDataSkills();
        log.info("SkillRegistry initialized with {} skills", skillsByName.size());
    }

    private void loadBuiltInSkills() {
        try {
            Path builtInSkillsPath = Path.of("src/main/resources/skills");
            if (builtInSkillsPath.toFile().exists()) {
                List<Skill> skills = skillLoader.loadSkillsFromDirectory(builtInSkillsPath);
                registerSkills(skills);
                log.info("Loaded {} built-in skills", skills.size());
            }
        } catch (Exception e) {
            log.error("Failed to load built-in skills: {}", e.getMessage());
        }
    }

    private void loadConfigSkills() {
        try {
            Path configSkillsPath = Path.of(configPath);
            if (configSkillsPath.toFile().exists()) {
                List<Skill> skills = skillLoader.loadSkillsFromDirectory(configSkillsPath);
                registerSkills(skills);
                log.info("Loaded {} config skills", skills.size());
            }
        } catch (Exception e) {
            log.error("Failed to load config skills: {}", e.getMessage());
        }
    }

    private void loadDataSkills() {
        try {
            Path dataSkillsPath = Path.of(dataPath);
            if (dataSkillsPath.toFile().exists()) {
                List<Skill> skills = skillLoader.loadSkillsFromDirectory(dataSkillsPath);
                registerSkills(skills);
                log.info("Loaded {} data skills", skills.size());
            }
        } catch (Exception e) {
            log.error("Failed to load data skills: {}", e.getMessage());
        }
    }

    @Override
    public void registerSkill(Skill skill) {
        if (skill == null || skill.getName() == null) {
            return;
        }

        // P1-5.1: 强制设置 scope 默认值
        if (skill.getScope() == null || skill.getScope().isBlank()) {
            skill.setScope("global");
        }

        skillsByName.put(skill.getName(), skill);

        String brain = skill.getTargetBrain();
        if (brain != null) {
            skillsByBrain.computeIfAbsent(brain, k -> new ArrayList<>()).add(skill);
        }

        String category = skill.getCategory();
        if (category != null) {
            skillsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(skill);
        }
    }

    @Override
    public void registerSkills(List<Skill> skills) {
        skills.forEach(this::registerSkill);
    }

    @Override
    public Optional<Skill> getSkill(String name) {
        return Optional.ofNullable(skillsByName.get(name));
    }

    @Override
    public List<Skill> getSkillsByBrain(String brain) {
        return skillsByBrain.getOrDefault(brain, Collections.emptyList());
    }

    @Override
    public List<Skill> getSkillsByCategory(String category) {
        return skillsByCategory.getOrDefault(category, Collections.emptyList());
    }

    @Override
    public List<Skill> getAllSkills() {
        return new ArrayList<>(skillsByName.values());
    }

    @Override
    public List<Skill> getSkillsByScope(String scope) {
        return skillsByName.values().stream()
                .filter(skill -> scope.equals(skill.getScope()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Skill> getSkillsByOwnerId(String ownerId) {
        return skillsByName.values().stream()
                .filter(skill -> ownerId.equals(skill.getOwnerId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Skill> getVisibleSkills(String userId, AccessLevel accessLevel, String departmentId) {
        if (accessLevel == AccessLevel.CHAT_ONLY) {
            return Collections.emptyList();
        }

        return skillsByName.values().stream()
                .filter(skill -> {
                    String scope = skill.getScope();
                    String owner = skill.getOwnerId();
                    String skillDept = skill.getDepartmentId();

                    // global 技能对所有非 CHAT_ONLY 用户可见
                    if ("global".equals(scope)) return true;

                    // personal 技能仅自己可见
                    if ("personal".equals(scope)) return userId != null && userId.equals(owner);

                    // private:{employeeId} 格式的个人技能
                    if (scope != null && scope.startsWith("private:")) {
                        String scopeOwnerId = scope.substring("private:".length());
                        return userId != null && userId.equals(scopeOwnerId);
                    }

                    // evolved 技能：FULL 可见全部，DEPARTMENT 仅见本部门
                    if ("evolved".equals(scope)) {
                        if (accessLevel == AccessLevel.FULL) return true;
                        if (accessLevel == AccessLevel.DEPARTMENT && departmentId != null && departmentId.equals(skillDept)) return true;
                        return false;
                    }

                    // department:{departmentName} 格式的部门技能
                    if (scope != null && scope.startsWith("department:")) {
                        String scopeDept = scope.substring("department:".length());
                        if (accessLevel == AccessLevel.FULL) return true;
                        if (accessLevel == AccessLevel.DEPARTMENT && departmentId != null && departmentId.equals(scopeDept)) return true;
                        return false;
                    }

                    // LIMITED 用户可见 global 和自己的 personal
                    if (accessLevel == AccessLevel.LIMITED) {
                        return "global".equals(scope);
                    }

                    return true;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getSkillMetadataForBrain(String brain) {
        return getSkillsByBrain(brain).stream()
                .map(Skill::getMetadataSummary)
                .collect(Collectors.toList());
    }

    @Override
    public List<Skill> searchSkills(String query) {
        String lowerQuery = query.toLowerCase();
        return skillsByName.values().stream()
                .filter(skill -> 
                        skill.getName().toLowerCase().contains(lowerQuery) ||
                        (skill.getDescription() != null && 
                         skill.getDescription().toLowerCase().contains(lowerQuery)))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Integer> getSkillCountsByBrain() {
        Map<String, Integer> counts = new HashMap<>();
        skillsByBrain.forEach((brain, skills) -> counts.put(brain, skills.size()));
        return counts;
    }

    @Override
    public void reloadSkills() {
        synchronized (reloadLock) {
            if (isReloading) {
                log.warn("Skill reload already in progress, skipping...");
                return;
            }
            isReloading = true;
        }
        
        try {
            log.info("Starting atomic skill reload...");
            
            // 清除 SkillLoader 扫描缓存，确保重新扫描最新文件
            skillLoader.clearAllCache();
            
            Map<String, Skill> newSkillsByName = new ConcurrentHashMap<>();
            Map<String, List<Skill>> newSkillsByBrain = new ConcurrentHashMap<>();
            Map<String, List<Skill>> newSkillsByCategory = new ConcurrentHashMap<>();
            
            loadSkillsToMaps(newSkillsByName, newSkillsByBrain, newSkillsByCategory);
            
            synchronized (reloadLock) {
                skillsByName.clear();
                skillsByName.putAll(newSkillsByName);
                
                skillsByBrain.clear();
                skillsByBrain.putAll(newSkillsByBrain);
                
                skillsByCategory.clear();
                skillsByCategory.putAll(newSkillsByCategory);
                
                isReloading = false;
            }
            
            log.info("Skill reload completed. Total skills: {}", skillsByName.size());
            
        } catch (Exception e) {
            log.error("Failed to reload skills: {}", e.getMessage());
            synchronized (reloadLock) {
                isReloading = false;
            }
        }
    }
    
    private void loadSkillsToMaps(
            Map<String, Skill> nameMap,
            Map<String, List<Skill>> brainMap,
            Map<String, List<Skill>> categoryMap) {
        
        Path builtInSkillsPath = Path.of("src/main/resources/skills");
        if (builtInSkillsPath.toFile().exists()) {
            List<Skill> skills = skillLoader.loadSkillsFromDirectory(builtInSkillsPath);
            addSkillsToMaps(skills, nameMap, brainMap, categoryMap);
            log.info("Loaded {} built-in skills", skills.size());
        }
        
        try {
            Path configSkillsPath = Path.of(configPath);
            if (configSkillsPath.toFile().exists()) {
                List<Skill> skills = skillLoader.loadSkillsFromDirectory(configSkillsPath);
                addSkillsToMaps(skills, nameMap, brainMap, categoryMap);
                log.info("Loaded {} config skills", skills.size());
            }
        } catch (Exception e) {
            log.warn("Failed to load config skills: {}", e.getMessage());
        }
        
        try {
            Path dataSkillsPath = Path.of(dataPath);
            if (dataSkillsPath.toFile().exists()) {
                List<Skill> skills = skillLoader.loadSkillsFromDirectory(dataSkillsPath);
                addSkillsToMaps(skills, nameMap, brainMap, categoryMap);
                log.info("Loaded {} data skills", skills.size());
            }
        } catch (Exception e) {
            log.warn("Failed to load data skills: {}", e.getMessage());
        }
    }
    
    private void addSkillsToMaps(
            List<Skill> skills,
            Map<String, Skill> nameMap,
            Map<String, List<Skill>> brainMap,
            Map<String, List<Skill>> categoryMap) {
        
        for (Skill skill : skills) {
            if (skill.getName() == null) continue;
            
            nameMap.put(skill.getName(), skill);
            
            String brain = skill.getTargetBrain();
            if (brain != null) {
                brainMap.computeIfAbsent(brain, k -> new ArrayList<>()).add(skill);
            }
            
            String category = skill.getCategory();
            if (category != null) {
                categoryMap.computeIfAbsent(category, k -> new ArrayList<>()).add(skill);
            }
        }
    }
}
