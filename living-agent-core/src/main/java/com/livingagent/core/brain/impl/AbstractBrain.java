package com.livingagent.core.brain.impl;

import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainContext;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.evolution.engine.EvolutionDecisionEngine;
import com.livingagent.core.evolution.personality.BrainPersonality;
import com.livingagent.core.evolution.signal.EvolutionSignal;
import com.livingagent.core.knowledge.KnowledgeBase;
import com.livingagent.core.memory.Memory;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractBrain implements Brain {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String id;
    protected final String name;
    protected final String department;
    protected final List<String> subscribedChannels;
    protected final List<String> publishChannels;
    protected final List<Tool> tools;

    protected final AtomicReference<BrainState> state = new AtomicReference<>(BrainState.INITIALIZING);
    protected volatile BrainContext context;
    protected volatile boolean running = false;
    protected final Map<String, Object> stateData = new ConcurrentHashMap<>();
    
    protected BrainPersonality personality;
    protected int evolutionSuccessCount = 0;
    protected int evolutionFailureCount = 0;
    protected long lastEvolutionTime = 0;

    protected AbstractBrain(String id, String name, String department,
                            List<String> subscribedChannels, List<String> publishChannels,
                            List<Tool> tools) {
        this.id = id;
        this.name = name;
        this.department = department;
        this.subscribedChannels = Collections.unmodifiableList(new ArrayList<>(subscribedChannels));
        this.publishChannels = Collections.unmodifiableList(new ArrayList<>(publishChannels));
        this.tools = Collections.unmodifiableList(new ArrayList<>(tools));
        this.personality = BrainPersonality.getDefaultForBrain(name);
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getName() { return name; }

    @Override
    public String getDepartment() { return department; }

    @Override
    public BrainState getState() { return state.get(); }

    @Override
    public List<Tool> getTools() { return tools; }

    @Override
    public List<String> getSubscribedChannels() { return subscribedChannels; }

    @Override
    public List<String> getPublishChannels() { return publishChannels; }
    
    public BrainPersonality getPersonality() { return personality; }
    
    public int getEvolutionSuccessCount() { return evolutionSuccessCount; }
    
    public int getEvolutionFailureCount() { return evolutionFailureCount; }

    @Override
    public void start(BrainContext context) {
        if (running) {
            log.warn("Brain {} already running", id);
            return;
        }

        this.context = context;
        
        if (context.getPersonality() != null) {
            this.personality = context.getPersonality();
        }
        
        state.set(BrainState.INITIALIZING);

        try {
            doStart(context);
            running = true;
            state.set(BrainState.RUNNING);
            log.info("Brain {} started for department {} with personality {}", id, department, personality.toKey());
        } catch (Exception e) {
            state.set(BrainState.ERROR);
            log.error("Failed to start brain: {}", id, e);
            throw new RuntimeException("Failed to start brain: " + id, e);
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        state.set(BrainState.STOPPED);

        try {
            doStop();
            log.info("Brain {} stopped", id);
        } catch (Exception e) {
            log.error("Failed to stop brain: {}", id, e);
        }
    }

    @Override
    public void process(ChannelMessage message) {
        if (!running) {
            log.warn("Brain {} received message but not running", id);
            return;
        }

        try {
            state.set(BrainState.RUNNING);
            doProcess(message);
        } catch (Exception e) {
            log.error("Error processing message in brain: {}", id, e);
            state.set(BrainState.ERROR);
            handleProcessingError(message, e);
        }
    }

    protected abstract void doStart(BrainContext context);

    protected abstract void doStop();

    protected abstract void doProcess(ChannelMessage message);
    
    protected abstract String buildPrompt(BrainContext context, String userInput);
    
    public abstract List<com.livingagent.core.tool.ToolSchema> getToolSchemas();
    
    protected void handleProcessingError(ChannelMessage message, Exception error) {
        log.warn("Brain {} handling processing error: {}", id, error.getMessage());
        
        EvolutionDecisionEngine engine = getEvolutionEngine();
        if (engine != null) {
            EvolutionSignal signal = EvolutionSignal.error(
                "brain_error_" + id,
                error.getClass().getSimpleName(),
                error.getMessage(),
                name
            );
            
            EvolutionDecisionEngine.EvolutionDecision decision = engine.decide(signal);
            log.info("Evolution decision for error: {} with confidence {}", 
                decision.getStrategy(), decision.getConfidence());
            
            if (decision.shouldExecute()) {
                executeEvolutionDecision(decision);
            }
        }
    }
    
    protected void executeEvolutionDecision(EvolutionDecisionEngine.EvolutionDecision decision) {
        log.info("Brain {} executing evolution decision: {}", id, decision.getStrategy());
        
        try {
            switch (decision.getStrategy()) {
                case REPAIR:
                    executeRepair(decision);
                    break;
                case OPTIMIZE:
                    executeOptimize(decision);
                    break;
                case INNOVATE:
                    executeInnovate(decision);
                    break;
                case ESCALATE:
                    escalateToMainBrain(decision);
                    break;
                default:
                    log.debug("Evolution strategy {} not executed", decision.getStrategy());
            }
            
            evolutionSuccessCount++;
            lastEvolutionTime = System.currentTimeMillis();
            
        } catch (Exception e) {
            evolutionFailureCount++;
            log.error("Failed to execute evolution decision: {}", e.getMessage());
        }
    }
    
    protected void executeRepair(EvolutionDecisionEngine.EvolutionDecision decision) {
        log.info("Brain {} executing repair for skill: {}", id, decision.getTargetSkillId());
    }
    
    protected void executeOptimize(EvolutionDecisionEngine.EvolutionDecision decision) {
        log.info("Brain {} executing optimization for skill: {}", id, decision.getTargetSkillId());
    }
    
    protected void executeInnovate(EvolutionDecisionEngine.EvolutionDecision decision) {
        log.info("Brain {} executing innovation: {}", id, decision.getParameters());
    }
    
    protected void escalateToMainBrain(EvolutionDecisionEngine.EvolutionDecision decision) {
        log.warn("Brain {} escalating to MainBrain: {}", id, decision.getReasons());
        
        if (context != null) {
            Map<String, Object> escalationData = new HashMap<>();
            escalationData.put("sourceBrain", id);
            escalationData.put("decision", decision);
            escalationData.put("timestamp", System.currentTimeMillis());
            
            context.setState("_escalation", escalationData);
        }
    }
    
    public void updatePersonality(String param, double delta) {
        if (personality != null) {
            com.livingagent.core.evolution.personality.PersonalityMutation mutation = 
                new com.livingagent.core.evolution.personality.PersonalityMutation(
                    param, delta, "manual_update", System.currentTimeMillis()
                );
            personality.applyMutation(mutation);
            log.info("Brain {} personality updated: {} -> {}", id, param, delta);
        }
    }
    
    public Map<String, Object> getEvolutionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("brainId", id);
        stats.put("brainName", name);
        stats.put("successCount", evolutionSuccessCount);
        stats.put("failureCount", evolutionFailureCount);
        stats.put("lastEvolutionTime", lastEvolutionTime);
        stats.put("personality", personality != null ? personality.toKey() : "null");
        return stats;
    }

    protected void publish(String channelId, ChannelMessage message) {
        if (context != null) {
            context.publish(channelId, message);
        }
    }

    protected Memory getMemory() {
        return context != null ? context.getMemory() : null;
    }

    protected Provider getProvider() {
        return context != null ? context.getProvider() : null;
    }

    protected ToolRegistry getToolRegistry() {
        return context != null ? context.getToolRegistry() : null;
    }
    
    protected KnowledgeBase getKnowledgeBase() {
        return context != null ? context.getKnowledgeBase() : null;
    }
    
    protected EvolutionDecisionEngine getEvolutionEngine() {
        return context != null ? context.getEvolutionEngine() : null;
    }
}
