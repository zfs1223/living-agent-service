package com.livingagent.core.neuron.impl;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronRegistry;

/**
 * NeuronCoordinator - 神经元协调器
 * 
 * 职责边界：
 * 1. 会话生命周期管理 - 创建、维护、销毁会话
 * 2. 通道编排 - 创建和管理会话相关的通道
 * 3. 神经元绑定 - 将神经元绑定到会话通道
 * 
 * 不负责：
 * - 意图识别和路由决策 (由 RouterNeuron 负责)
 * - 消息内容处理 (由各神经元负责)
 * 
 * 与 RouterNeuron 的关系：
 * - NeuronCoordinator 创建和管理通道基础设施
 * - RouterNeuron 基于用户部门信息进行路由决策
 * - 两者协作完成消息从用户到目标大脑的传递
 */
public class NeuronCoordinator {
    
    private static final Logger log = LoggerFactory.getLogger(NeuronCoordinator.class);
    
    public static final String PERCEPTION_CHANNEL = "channel://perception";
    public static final String DISPATCH_CHANNEL = "channel://dispatch";
    public static final String TOOL_INTENT_CHANNEL = "channel://tool-intent";
    public static final String RESPONSE_CHANNEL = "channel://response";
    
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
    
    /**
     * 创建会话 - 创建会话相关的通道和状态
     */
    public String createSession() {
        String sessionId = "session-" + System.currentTimeMillis();
        
        Channel perceptionChannel = channelManager.getOrCreateChannel(PERCEPTION_CHANNEL);
        Channel dispatchChannel = channelManager.getOrCreateChannel(DISPATCH_CHANNEL);
        Channel toolIntentChannel = channelManager.getOrCreateChannel(TOOL_INTENT_CHANNEL);
        Channel responseChannel = channelManager.getOrCreateChannel(RESPONSE_CHANNEL);
        
        SessionState state = new SessionState(sessionId);
        state.setPerceptionChannel(perceptionChannel);
        state.setDispatchChannel(dispatchChannel);
        state.setToolIntentChannel(toolIntentChannel);
        state.setResponseChannel(responseChannel);
        sessionStates.put(sessionId, state);
        
        log.info("Session created: {}", sessionId);
        return sessionId;
    }
    
    /**
     * 绑定神经元到会话 - 将神经元订阅到会话通道
     */
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
    
    /**
     * 发布用户输入 - 将用户输入发布到感知通道
     * 
     * 注意：此方法只负责消息发布，不负责路由决策
     * 路由决策由 RouterNeuron 基于用户部门信息完成
     */
    public void processUserInput(String sessionId, String userInput, Map<String, Object> userContext) {
        SessionState state = sessionStates.get(sessionId);
        if (state == null) {
            log.warn("No session state found for: {}", sessionId);
            return;
        }
        
        Channel perceptionChannel = state.getPerceptionChannel();
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("userContext", userContext);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("userInput", userInput);
        
        ChannelMessage message = ChannelMessage.text(
            perceptionChannel.getId(),
            "coordinator",
            perceptionChannel.getId(),
            sessionId,
            userInput
        );
        perceptionChannel.publish(message);
        
        log.debug("Published user input to perception channel for session: {}", sessionId);
    }
    
    /**
     * 获取会话状态
     */
    public SessionState getSessionState(String sessionId) {
        return sessionStates.get(sessionId);
    }
    
    /**
     * 销毁会话 - 清理会话资源
     */
    public void destroySession(String sessionId) {
        SessionState state = sessionStates.remove(sessionId);
        if (state != null) {
            log.info("Session destroyed: {}", sessionId);
        }
    }
    
    public void shutdown() {
        coordinatorExecutor.shutdown();
        sessionStates.clear();
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
