package com.livingagent.core.brain.impl;

import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.Brain.BrainState;
import com.livingagent.core.brain.BrainContext;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.evolution.personality.BrainPersonality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BrainRegistryImpl implements BrainRegistry {

    private static final Logger log = LoggerFactory.getLogger(BrainRegistryImpl.class);

    private final Map<String, Brain> brainById = new ConcurrentHashMap<>();
    private final Map<String, Brain> brainByDepartment = new ConcurrentHashMap<>();
    private final Map<String, BrainPersonality> personalityByBrain = new ConcurrentHashMap<>();

    public BrainRegistryImpl() {
        initializeDefaultPersonalities();
    }

    private void initializeDefaultPersonalities() {
        for (Map.Entry<String, BrainPersonality> entry : BrainPersonality.DEFAULT_PERSONALITIES.entrySet()) {
            personalityByBrain.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void register(Brain brain) {
        if (brain == null) {
            throw new IllegalArgumentException("Brain cannot be null");
        }

        String brainId = brain.getId();
        if (brainId == null || brainId.isEmpty()) {
            throw new IllegalArgumentException("Brain ID cannot be null or empty");
        }

        if (brainById.containsKey(brainId)) {
            log.warn("Brain {} already registered, replacing", brainId);
        }

        brainById.put(brainId, brain);

        String department = brain.getDepartment();
        if (department != null && !department.isEmpty()) {
            brainByDepartment.put(department, brain);
        }

        if (!personalityByBrain.containsKey(brain.getName())) {
            personalityByBrain.put(brain.getName(), BrainPersonality.getDefaultForBrain(brain.getName()));
        }

        log.info("Registered brain: {} (department: {}, state: {})", 
            brainId, department, brain.getState());
    }

    @Override
    public void unregister(String brainId) {
        if (brainId == null) {
            return;
        }

        Brain brain = brainById.remove(brainId);
        if (brain != null) {
            String department = brain.getDepartment();
            if (department != null) {
                brainByDepartment.remove(department, brain);
            }
            log.info("Unregistered brain: {}", brainId);
        }
    }

    @Override
    public Optional<Brain> get(String brainId) {
        if (brainId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(brainById.get(brainId));
    }

    @Override
    public List<Brain> getAll() {
        return new ArrayList<>(brainById.values());
    }

    @Override
    public Optional<Brain> getByDepartment(String department) {
        if (department == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(brainByDepartment.get(department));
    }

    @Override
    public List<Brain> getByState(BrainState state) {
        if (state == null) {
            return Collections.emptyList();
        }

        return brainById.values().stream()
            .filter(brain -> brain.getState() == state)
            .toList();
    }

    @Override
    public boolean exists(String brainId) {
        return brainId != null && brainById.containsKey(brainId);
    }

    @Override
    public int count() {
        return brainById.size();
    }

    @Override
    public void startAll() {
        log.info("Starting all brains (count: {})", brainById.size());
        
        List<Brain> failedBrains = new ArrayList<>();
        
        for (Brain brain : brainById.values()) {
            try {
                if (brain.getState() != BrainState.RUNNING) {
                    BrainContext context = createBrainContext(brain);
                    brain.start(context);
                    log.info("Started brain: {}", brain.getId());
                }
            } catch (Exception e) {
                log.error("Failed to start brain: {}", brain.getId(), e);
                failedBrains.add(brain);
            }
        }

        if (!failedBrains.isEmpty()) {
            log.warn("Failed to start {} brains: {}", 
                failedBrains.size(), 
                failedBrains.stream().map(Brain::getId).toList());
        }
    }

    @Override
    public void stopAll() {
        log.info("Stopping all brains (count: {})", brainById.size());
        
        for (Brain brain : brainById.values()) {
            try {
                if (brain.getState() == BrainState.RUNNING) {
                    brain.stop();
                    log.info("Stopped brain: {}", brain.getId());
                }
            } catch (Exception e) {
                log.error("Failed to stop brain: {}", brain.getId(), e);
            }
        }
    }

    public Optional<BrainPersonality> getPersonality(String brainName) {
        return Optional.ofNullable(personalityByBrain.get(brainName));
    }

    public void updatePersonality(String brainName, BrainPersonality personality) {
        if (brainName != null && personality != null) {
            personalityByBrain.put(brainName, personality);
            log.info("Updated personality for brain: {}", brainName);
        }
    }

    public List<String> getRunningBrainIds() {
        return brainById.values().stream()
            .filter(brain -> brain.getState() == BrainState.RUNNING)
            .map(Brain::getId)
            .toList();
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBrains", brainById.size());
        stats.put("departments", brainByDepartment.size());
        
        Map<String, Long> stateCounts = new HashMap<>();
        for (Brain brain : brainById.values()) {
            String stateName = brain.getState().name();
            stateCounts.merge(stateName, 1L, Long::sum);
        }
        stats.put("stateCounts", stateCounts);
        
        return stats;
    }

    private BrainContext createBrainContext(Brain brain) {
        BrainPersonality personality = personalityByBrain.get(brain.getName());
        return new BrainContext(
            brain.getId(),
            brain.getDepartment(),
            "session_" + System.currentTimeMillis(),
            null,
            null,
            null,
            null,
            null,
            personality
        );
    }
}
