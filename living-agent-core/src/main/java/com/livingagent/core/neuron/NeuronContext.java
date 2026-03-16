package com.livingagent.core.neuron;

import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.channel.ChannelMessageQueue;
import com.livingagent.core.skill.SkillRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NeuronContext {

    private final String neuronId;
    private final String channelId;
    private final String sessionId;
    private final String brainDomain;
    private final Map<String, Object> state;
    private final ChannelMessageQueue queue;
    private final SkillRegistry skillRegistry;
    private ChannelManager channelManager;

    public NeuronContext(String neuronId, String channelId, String sessionId, ChannelMessageQueue queue) {
        this.neuronId = neuronId;
        this.channelId = channelId;
        this.sessionId = sessionId;
        this.queue = queue;
        this.skillRegistry = null;
        this.brainDomain = null;
        this.state = new ConcurrentHashMap<>();
    }

    public NeuronContext(String neuronId, String channelId, String sessionId, 
                         ChannelMessageQueue queue, SkillRegistry skillRegistry) {
        this.neuronId = neuronId;
        this.channelId = channelId;
        this.sessionId = sessionId;
        this.queue = queue;
        this.skillRegistry = skillRegistry;
        this.brainDomain = null;
        this.state = new ConcurrentHashMap<>();
    }

    public NeuronContext(String neuronId, String channelId, String sessionId, 
                         ChannelMessageQueue queue, SkillRegistry skillRegistry,
                         ChannelManager channelManager) {
        this.neuronId = neuronId;
        this.channelId = channelId;
        this.sessionId = sessionId;
        this.queue = queue;
        this.skillRegistry = skillRegistry;
        this.channelManager = channelManager;
        this.brainDomain = null;
        this.state = new ConcurrentHashMap<>();
    }

    public NeuronContext(String neuronId, String channelId, 
                         ChannelManager channelManager, SkillRegistry skillRegistry) {
        this.neuronId = neuronId;
        this.channelId = channelId;
        this.sessionId = null;
        this.queue = null;
        this.skillRegistry = skillRegistry;
        this.channelManager = channelManager;
        this.brainDomain = null;
        this.state = new ConcurrentHashMap<>();
    }

    public NeuronContext(String neuronId, String channelId, String sessionId,
                         ChannelMessageQueue queue, SkillRegistry skillRegistry,
                         ChannelManager channelManager, String brainDomain) {
        this.neuronId = neuronId;
        this.channelId = channelId;
        this.sessionId = sessionId;
        this.queue = queue;
        this.skillRegistry = skillRegistry;
        this.channelManager = channelManager;
        this.brainDomain = brainDomain;
        this.state = new ConcurrentHashMap<>();
    }

    public String getNeuronId() {
        return neuronId;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getBrainDomain() {
        return brainDomain;
    }

    public Map<String, Object> getState() {
        return state;
    }

    public ChannelMessageQueue getQueue() {
        return queue;
    }

    public SkillRegistry getSkillRegistry() {
        return skillRegistry;
    }

    public ChannelManager getChannelManager() {
        return channelManager;
    }

    public void setChannelManager(ChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    public void setState(String key, Object value) {
        state.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getState(String key) {
        return (T) state.get(key);
    }

    public void clearState() {
        state.clear();
    }
    
    public void publish(String channelId, ChannelMessage message) {
        if (channelManager != null) {
            channelManager.publish(channelId, message);
        }
        setState("_last_publish_channel", channelId);
        setState("_last_publish_time", System.currentTimeMillis());
    }
}
