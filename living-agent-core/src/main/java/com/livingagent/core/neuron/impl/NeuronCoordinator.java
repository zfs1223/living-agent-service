package com.livingagent.core.neuron.impl;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.Channel.ChannelType;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronRegistry;

public class NeuronCoordinator {
    
    private static final Logger log = LoggerFactory.getLogger(NeuronCoordinator.class);
    
    private static final String PERCEPTION_CHANNEL_PREFIX = "channel://perception/";
    private static final String DISPATCH_CHANNEL_PREFIX = "channel://dispatch/";
    private static final String TOOL_INTENT_CHANNEL_PREFIX = "channel://tool-intent/";
    private static final String RESPONSE_CHANNEL_PREFIX = "channel://response/";
    
    private final NeuronRegistry neuronRegistry;
    private final ChannelManager channelManager;
    private final ExecutorService coordinatorExecutor;
    private final Map<String, SessionState> sessionStates;
    
    public NeuronCoordinator(NeuronRegistry neuronRegistry, ChannelManager channelManager) {
        this.neuronRegistry = neuronRegistry;
        this.channelManager = channelManager;
        this.coordinatorExecutor = Executors.newFixedThreadPool(4);
        this.sessionStates = new ConcurrentHashMap<>();
    }
    
    public String createSession() {
        String sessionId = "session-" + UUID.randomUUID().toString().substring(0, 8);
        SessionState state = new SessionState(sessionId);
        
        Channel perceptionChannel = channelManager.create(
            PERCEPTION_CHANNEL_PREFIX + sessionId, ChannelType.BROADCAST);
        Channel dispatchChannel = channelManager.create(
            DISPATCH_CHANNEL_PREFIX + sessionId, ChannelType.ROUND_ROBIN);
        Channel toolIntentChannel = channelManager.create(
            TOOL_INTENT_CHANNEL_PREFIX + sessionId, ChannelType.UNICAST);
        Channel responseChannel = channelManager.create(
            RESPONSE_CHANNEL_PREFIX + sessionId, ChannelType.BROADCAST);
        
        state.setPerceptionChannel(perceptionChannel);
        state.setDispatchChannel(dispatchChannel);
        state.setToolIntentChannel(toolIntentChannel);
        state.setResponseChannel(responseChannel);
        
        sessionStates.put(sessionId, state);
        log.info("Session created: {}", sessionId);
        
        return sessionId;
    }
    
    public void bindNeuronToSession(String sessionId, String neuronId) {
        SessionState state = sessionStates.get(sessionId);
        if (state == null) {
            log.warn("No session state found for: {}", sessionId);
            return;
        }
        
        neuronRegistry.get(neuronId).ifPresent(neuron -> {
            neuron.subscribe(state.getPerceptionChannel());
            neuron.publishTo(state.getResponseChannel());
            log.info("Neuron {} bound to session {}", neuronId, sessionId);
        });
    }
    
    public void publishUserInput(String sessionId, String userInput, Map<String, Object> userContext) {
        SessionState state = sessionStates.get(sessionId);
        if (state == null) {
            log.warn("No session state found for session: {}", sessionId);
            return;
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("userContext", userContext);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("userInput", userInput);
        payload.put("userContext", userContext);
        
        ChannelMessage message = new ChannelMessage(
            state.getPerceptionChannel().getId(),
            "coordinator",
            "perception",
            sessionId,
            ChannelMessage.MessageType.TEXT,
            payload
        );
        
        state.getPerceptionChannel().publish(message);
        log.debug("Published user input to perception channel for session: {}", sessionId);
    }
    
    public SessionState getSessionState(String sessionId) {
        return sessionStates.get(sessionId);
    }
    
    public void destroySession(String sessionId) {
        SessionState state = sessionStates.remove(sessionId);
        if (state != null) {
            channelManager.destroy(PERCEPTION_CHANNEL_PREFIX + sessionId);
            channelManager.destroy(DISPATCH_CHANNEL_PREFIX + sessionId);
            channelManager.destroy(TOOL_INTENT_CHANNEL_PREFIX + sessionId);
            channelManager.destroy(RESPONSE_CHANNEL_PREFIX + sessionId);
            
            state.setPerceptionChannel(null);
            state.setDispatchChannel(null);
            state.setToolIntentChannel(null);
            state.setResponseChannel(null);
            state.getUserContext().clear();
            
            log.info("Session destroyed and resources cleaned: {}", sessionId);
        }
    }
    
    public void shutdown() {
        for (String sessionId : sessionStates.keySet()) {
            destroySession(sessionId);
        }
        coordinatorExecutor.shutdown();
        log.info("NeuronCoordinator shutdown complete");
    }
    
    public static class SessionState {
        private final String sessionId;
        private Channel perceptionChannel;
        private Channel dispatchChannel;
        private Channel toolIntentChannel;
        private Channel responseChannel;
        private Map<String, Object> userContext;
        
        public SessionState(String sessionId) {
            this.sessionId = sessionId;
            this.userContext = new HashMap<>();
        }
        
        public String getSessionId() { return sessionId; }
        public Channel getPerceptionChannel() { return perceptionChannel; }
        public void setPerceptionChannel(Channel channel) { this.perceptionChannel = channel; }
        public Channel getDispatchChannel() { return dispatchChannel; }
        public void setDispatchChannel(Channel channel) { this.dispatchChannel = channel; }
        public Channel getToolIntentChannel() { return toolIntentChannel; }
        public void setToolIntentChannel(Channel channel) { this.toolIntentChannel = channel; }
        public Channel getResponseChannel() { return responseChannel; }
        public void setResponseChannel(Channel channel) { this.responseChannel = channel; }
        public Map<String, Object> getUserContext() { return userContext; }
        public void setUserContext(Map<String, Object> context) { this.userContext = context; }
    }
}
