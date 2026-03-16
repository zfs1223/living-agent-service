package com.livingagent.skill.service;

import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.skill.Skill;
import com.livingagent.core.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SkillBindingService {
    private static final Logger log = LoggerFactory.getLogger(SkillBindingService.class);

    private static final Set<String> CORE_SKILLS = Set.of(
            "tavily-search",
            "find-skills",
            "proactive-agent",
            "weather"
    );

    private final SkillRegistry skillRegistry;
    private final NeuronRegistry neuronRegistry;
    
    private final Map<String, Set<String>> neuronSkillBindings = new ConcurrentHashMap<>();
    private final Map<String, Long> lastBindingTime = new ConcurrentHashMap<>();

    @Autowired
    public SkillBindingService(SkillRegistry skillRegistry, NeuronRegistry neuronRegistry) {
        this.skillRegistry = skillRegistry;
        this.neuronRegistry = neuronRegistry;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing SkillBindingService...");
        log.info("SkillBindingService initialized");
    }

    public void bindCoreSkillsToNeuron(String neuronId) {
        for (String skillName : CORE_SKILLS) {
                    Optional<Skill> skill = skillRegistry.getSkill(skillName);
                    if (skill.isPresent()) {
                        log.debug("Core skill {} is available", skillName);
                    }
                }
    }

    public void bindSkillToNeuron(String skillName, String neuronId) {
        Optional<Skill> skill = skillRegistry.getSkill(skillName);
        if (skill.isEmpty()) {
            log.warn("Skill not found: {}", skillName);
            return;
        }
        
        lastBindingTime.put(neuronId, System.currentTimeMillis());
        neuronSkillBindings.computeIfAbsent(neuronId, k -> new HashSet<>()).add(skillName);
        log.info("Bound skill {} to neuron {}", skillName, neuronId);
    }

    public void unbindSkillFromNeuron(String skillName, String neuronId) {
        if (CORE_SKILLS.contains(skillName)) {
            log.warn("Cannot unbind core skill {} from neuron {}", skillName, neuronId);
            return;
        }

        Set<String> skills = neuronSkillBindings.get(neuronId);
        if (skills != null) {
            skills.remove(skillName);
        }
        log.info("Unbound skill {} from neuron {}", skillName, neuronId);
        
        lastBindingTime.put(neuronId, System.currentTimeMillis());
    }

    public Set<String> getNeuronSkills(String neuronId) {
        return neuronSkillBindings.getOrDefault(neuronId, Collections.emptySet());
    }

    public Map<String, Set<String>> getAllBindings() {
        return new HashMap<>(neuronSkillBindings);
    }

    public List<String> recommendSkillsForNeuron(String neuronId) {
        Set<String> currentSkills = neuronSkillBindings.getOrDefault(neuronId, Collections.emptySet());
        List<String> recommendations = new ArrayList<>();

        for (Skill skill : skillRegistry.getAllSkills()) {
            if (currentSkills.contains(skill.getName())) {
                continue;
            }

            String targetBrain = skill.getTargetBrain();
            if (targetBrain != null) {
                recommendations.add(skill.getName());
            }
        }

        return recommendations;
    }

    @Scheduled(fixedRate = 300000)
    public void refreshBindings() {
        log.debug("Refreshing skill bindings...");
    }

    public void installAndBindSkill(String skillName, String source, String neuronId) {
        log.info("Installing skill {} from {} for neuron {}", skillName, source, neuronId);
        
        bindSkillToNeuron(skillName, neuronId);
    }

    public boolean hasCoreSkills(String neuronId) {
        Set<String> skills = neuronSkillBindings.get(neuronId);
        if (skills == null) {
            return false;
        }

        for (String skillName : CORE_SKILLS) {
            if (!skills.contains(skillName)) {
                return false;
            }
        }
        return true;
    }

    public Map<String, Object> getBindingStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSkills", skillRegistry.getAllSkills().size());
        stats.put("coreSkills", CORE_SKILLS);
        stats.put("bindings", neuronSkillBindings.size());
        
        return stats;
    }
}
