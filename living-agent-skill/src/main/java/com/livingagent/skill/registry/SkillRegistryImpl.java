package com.livingagent.skill.registry;

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
        skillsByName.clear();
        skillsByBrain.clear();
        skillsByCategory.clear();
        init();
    }
}
