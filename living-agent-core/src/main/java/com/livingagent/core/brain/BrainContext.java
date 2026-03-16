package com.livingagent.core.brain;

import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.evolution.engine.EvolutionDecisionEngine;
import com.livingagent.core.evolution.personality.BrainPersonality;
import com.livingagent.core.knowledge.KnowledgeBase;
import com.livingagent.core.memory.Memory;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.skill.SkillRegistry;
import com.livingagent.core.tool.ToolRegistry;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BrainContext {

    private final String brainId;
    private final String department;
    private final String sessionId;
    private final Provider provider;
    private final Memory memory;
    private final ToolRegistry toolRegistry;
    private final KnowledgeBase knowledgeBase;
    private final EvolutionDecisionEngine evolutionEngine;
    private final BrainPersonality personality;
    private final Map<String, Object> state;
    private final ChannelManager channelManager;
    private final SkillRegistry skillRegistry;
    private List<ChatMessage> history;

    public record ChatMessage(String role, String content) {
        public static ChatMessage system(String content) { return new ChatMessage("system", content); }
        public static ChatMessage user(String content) { return new ChatMessage("user", content); }
        public static ChatMessage assistant(String content) { return new ChatMessage("assistant", content); }
    }

    public BrainContext(String brainId, String department, String sessionId,
                        Provider provider, Memory memory, ToolRegistry toolRegistry) {
        this(brainId, department, sessionId, provider, memory, toolRegistry, null, null, null, null, null);
    }

    public BrainContext(String brainId, String department, String sessionId,
                        Provider provider, Memory memory, ToolRegistry toolRegistry,
                        KnowledgeBase knowledgeBase, EvolutionDecisionEngine evolutionEngine,
                        BrainPersonality personality) {
        this(brainId, department, sessionId, provider, memory, toolRegistry, 
            knowledgeBase, evolutionEngine, personality, null, null);
    }

    public BrainContext(String brainId, String department, String sessionId,
                        Provider provider, Memory memory, ToolRegistry toolRegistry,
                        KnowledgeBase knowledgeBase, EvolutionDecisionEngine evolutionEngine,
                        BrainPersonality personality, ChannelManager channelManager,
                        SkillRegistry skillRegistry) {
        this.brainId = brainId;
        this.department = department;
        this.sessionId = sessionId;
        this.provider = provider;
        this.memory = memory;
        this.toolRegistry = toolRegistry;
        this.knowledgeBase = knowledgeBase;
        this.evolutionEngine = evolutionEngine;
        this.personality = personality != null ? personality : BrainPersonality.getDefaultForBrain(brainId);
        this.channelManager = channelManager;
        this.skillRegistry = skillRegistry;
        this.state = new ConcurrentHashMap<>();
        this.history = new java.util.ArrayList<>();
    }

    public String getBrainId() { return brainId; }
    public String getDepartment() { return department; }
    public String getSessionId() { return sessionId; }
    public Provider getProvider() { return provider; }
    public Memory getMemory() { return memory; }
    public ToolRegistry getToolRegistry() { return toolRegistry; }
    public KnowledgeBase getKnowledgeBase() { return knowledgeBase; }
    public EvolutionDecisionEngine getEvolutionEngine() { return evolutionEngine; }
    public BrainPersonality getPersonality() { return personality; }
    public ChannelManager getChannelManager() { return channelManager; }
    public SkillRegistry getSkillRegistry() { return skillRegistry; }
    public Map<String, Object> getState() { return state; }
    public List<ChatMessage> getHistory() { return history; }
    
    public void setHistory(List<ChatMessage> history) { this.history = history; }
    public void addToHistory(ChatMessage message) { this.history.add(message); }

    public void setState(String key, Object value) { state.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> T getState(String key) { return (T) state.get(key); }

    public void publish(String channelId, Object message) {
        setState("_publish_channel", channelId);
        setState("_publish_message", message);
        
        if (channelManager != null && channelId != null && !channelId.isEmpty()) {
            try {
                ChannelMessage channelMessage;
                if (message instanceof ChannelMessage cm) {
                    channelMessage = cm;
                } else if (message instanceof String text) {
                    channelMessage = ChannelMessage.text(
                        channelId,
                        brainId,
                        channelId,
                        sessionId != null ? sessionId : UUID.randomUUID().toString(),
                        text
                    );
                } else {
                    channelMessage = ChannelMessage.text(
                        channelId,
                        brainId,
                        channelId,
                        sessionId != null ? sessionId : UUID.randomUUID().toString(),
                        message != null ? message.toString() : ""
                    );
                    if (message != null) {
                        channelMessage.addMetadata("dataType", message.getClass().getSimpleName());
                    }
                }
                
                channelManager.publish(channelId, channelMessage);
            } catch (Exception e) {
                state.put("_publish_error", e.getMessage());
            }
        }
    }

    public void publishText(String channelId, String content) {
        publish(channelId, content);
    }

    public void publishToBrain(String targetBrainId, String content) {
        String channelId = "channel://" + targetBrainId + "/inbox";
        publish(channelId, content);
    }

    public void broadcast(String content) {
        String broadcastChannel = "channel://broadcast/all";
        publish(broadcastChannel, content);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String brainId;
        private String department;
        private String sessionId;
        private Provider provider;
        private Memory memory;
        private ToolRegistry toolRegistry;
        private KnowledgeBase knowledgeBase;
        private EvolutionDecisionEngine evolutionEngine;
        private BrainPersonality personality;
        private ChannelManager channelManager;
        private SkillRegistry skillRegistry;

        public Builder brainId(String brainId) { this.brainId = brainId; return this; }
        public Builder department(String department) { this.department = department; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder provider(Provider provider) { this.provider = provider; return this; }
        public Builder memory(Memory memory) { this.memory = memory; return this; }
        public Builder toolRegistry(ToolRegistry toolRegistry) { this.toolRegistry = toolRegistry; return this; }
        public Builder knowledgeBase(KnowledgeBase knowledgeBase) { this.knowledgeBase = knowledgeBase; return this; }
        public Builder evolutionEngine(EvolutionDecisionEngine evolutionEngine) { this.evolutionEngine = evolutionEngine; return this; }
        public Builder personality(BrainPersonality personality) { this.personality = personality; return this; }
        public Builder channelManager(ChannelManager channelManager) { this.channelManager = channelManager; return this; }
        public Builder skillRegistry(SkillRegistry skillRegistry) { this.skillRegistry = skillRegistry; return this; }

        public BrainContext build() {
            return new BrainContext(
                brainId, department, sessionId,
                provider, memory, toolRegistry,
                knowledgeBase, evolutionEngine, personality,
                channelManager, skillRegistry
            );
        }
    }
}
