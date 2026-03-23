package com.livingagent.core.neuron.chat;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronRegistry;

public class ChatNeuronRouter {
    
    private static final Logger log = LoggerFactory.getLogger(ChatNeuronRouter.class);
    
    private final NeuronRegistry neuronRegistry;
    private final ChatIntentClassifier intentClassifier;
    private final ChatNeuronConfig config;
    
    private Neuron chatNeuron;
    private Neuron toolNeuron;
    private Neuron mainBrain;
    
    private final Map<String, RoutingStats> sessionStats = new HashMap<>();
    
    public ChatNeuronRouter(NeuronRegistry neuronRegistry) {
        this(neuronRegistry, ChatNeuronConfig.defaultConfig());
    }
    
    public ChatNeuronRouter(NeuronRegistry neuronRegistry, ChatNeuronConfig config) {
        this.neuronRegistry = neuronRegistry;
        this.config = config;
        this.intentClassifier = new ChatIntentClassifier();
    }
    
    public void initialize() {
        neuronRegistry.get("neuron://chat/qwen3/001").ifPresent(n -> this.chatNeuron = n);
        neuronRegistry.get("neuron://tool/bitnet/001").ifPresent(n -> this.toolNeuron = n);
        neuronRegistry.get("neuron://main/brain/001").ifPresent(n -> this.mainBrain = n);
        
        log.info("ChatNeuronRouter initialized: chatNeuron={}, toolNeuron={}, mainBrain={}",
            chatNeuron != null, toolNeuron != null, mainBrain != null);
    }
    
    public RoutingResult route(String sessionId, String userInput, Map<String, Object> context) {
        long startTime = System.currentTimeMillis();
        
        ChatIntentClassifier.ClassificationResult classification = intentClassifier.classify(userInput);
        
        RoutingResult result = new RoutingResult();
        result.setSessionId(sessionId);
        result.setOriginalInput(userInput);
        result.setIntent(classification.getIntent().name());
        result.setConfidence(classification.getConfidence());
        result.setReason(classification.getReason());
        
        Neuron targetNeuron = selectTargetNeuron(classification);
        result.setTargetNeuron(targetNeuron != null ? targetNeuron.getId() : "unknown");
        
        if (targetNeuron != null) {
            result.setNeuron(targetNeuron);
        }
        
        long latency = System.currentTimeMillis() - startTime;
        result.setRoutingLatencyMs(latency);
        
        updateStats(sessionId, classification.getIntent().name(), latency);
        
        log.debug("Routed input to {} (intent={}, confidence={}, latency={}ms)",
            result.getTargetNeuron(), result.getIntent(), result.getConfidence(), latency);
        
        return result;
    }
    
    private Neuron selectTargetNeuron(ChatIntentClassifier.ClassificationResult classification) {
        if (!config.isEnableIntentClassification()) {
            return chatNeuron != null ? chatNeuron : 
                   (toolNeuron != null ? toolNeuron : mainBrain);
        }
        
        return switch (classification.getIntent()) {
            case GREETING, CASUAL_CHAT, SIMPLE_QUESTION -> {
                if (chatNeuron != null) {
                    yield chatNeuron;
                }
                yield mainBrain != null ? mainBrain : toolNeuron;
            }
            case TOOL_CALL -> {
                if (toolNeuron != null) {
                    yield toolNeuron;
                }
                yield mainBrain != null ? mainBrain : chatNeuron;
            }
            case COMPLEX_TASK -> {
                if (mainBrain != null) {
                    yield mainBrain;
                }
                yield chatNeuron != null ? chatNeuron : toolNeuron;
            }
            case UNKNOWN -> {
                yield chatNeuron != null ? chatNeuron : 
                       (toolNeuron != null ? toolNeuron : mainBrain);
            }
        };
    }
    
    public CompletableFuture<RoutingResult> routeAsync(String sessionId, String userInput, Map<String, Object> context) {
        return CompletableFuture.supplyAsync(() -> route(sessionId, userInput, context));
    }
    
    public boolean shouldUseChatNeuron(String userInput) {
        if (!config.isEnableIntentClassification()) {
            return true;
        }
        return intentClassifier.isQuickResponseCandidate(userInput);
    }
    
    public boolean requiresToolCall(String userInput) {
        if (!config.isEnableIntentClassification()) {
            return false;
        }
        return intentClassifier.requiresToolCall(userInput);
    }
    
    public boolean requiresComplexProcessing(String userInput) {
        if (!config.isEnableIntentClassification()) {
            return false;
        }
        return intentClassifier.requiresComplexProcessing(userInput);
    }
    
    private void updateStats(String sessionId, String intent, long latency) {
        sessionStats.computeIfAbsent(sessionId, k -> new RoutingStats())
            .recordRouting(intent, latency);
    }
    
    public RoutingStats getStats(String sessionId) {
        return sessionStats.get(sessionId);
    }
    
    public Map<String, Object> getAggregatedStats() {
        Map<String, Object> stats = new HashMap<>();
        
        int totalRoutings = 0;
        long totalLatency = 0;
        Map<String, Integer> intentCounts = new HashMap<>();
        
        for (RoutingStats sessionStat : sessionStats.values()) {
            totalRoutings += sessionStat.getTotalRoutings();
            totalLatency += sessionStat.getTotalLatencyMs();
            
            sessionStat.getIntentCounts().forEach((intent, count) -> 
                intentCounts.merge(intent, count, Integer::sum));
        }
        
        stats.put("totalRoutings", totalRoutings);
        stats.put("avgLatencyMs", totalRoutings > 0 ? totalLatency / totalRoutings : 0);
        stats.put("intentDistribution", intentCounts);
        stats.put("activeSessions", sessionStats.size());
        
        return stats;
    }
    
    public void clearSessionStats(String sessionId) {
        sessionStats.remove(sessionId);
    }
    
    public void setChatNeuron(Neuron neuron) {
        this.chatNeuron = neuron;
    }
    
    public void setToolNeuron(Neuron neuron) {
        this.toolNeuron = neuron;
    }
    
    public void setMainBrain(Neuron neuron) {
        this.mainBrain = neuron;
    }
    
    public static class RoutingResult {
        private String sessionId;
        private String originalInput;
        private String intent;
        private double confidence;
        private String reason;
        private String targetNeuron;
        private Neuron neuron;
        private long routingLatencyMs;
        private boolean shouldRoute = true;
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public String getOriginalInput() { return originalInput; }
        public void setOriginalInput(String originalInput) { this.originalInput = originalInput; }
        
        public String getIntent() { return intent; }
        public void setIntent(String intent) { this.intent = intent; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public String getTargetNeuron() { return targetNeuron; }
        public void setTargetNeuron(String targetNeuron) { this.targetNeuron = targetNeuron; }
        
        public Neuron getNeuron() { return neuron; }
        public void setNeuron(Neuron neuron) { this.neuron = neuron; }
        
        public long getRoutingLatencyMs() { return routingLatencyMs; }
        public void setRoutingLatencyMs(long routingLatencyMs) { this.routingLatencyMs = routingLatencyMs; }
        
        public boolean shouldRoute() { return shouldRoute; }
        public void setShouldRoute(boolean shouldRoute) { this.shouldRoute = shouldRoute; }
        
        public boolean isQuickResponse() {
            return "GREETING".equals(intent) || "CASUAL_CHAT".equals(intent) || "SIMPLE_QUESTION".equals(intent);
        }
        
        public boolean requiresToolNeuron() {
            return "TOOL_CALL".equals(intent);
        }
        
        public boolean requiresMainBrain() {
            return "COMPLEX_TASK".equals(intent);
        }
        
        public boolean shouldUseChatNeuron() {
            return "GREETING".equals(intent) || "CASUAL_CHAT".equals(intent) || 
                   "SIMPLE_QUESTION".equals(intent) || "UNKNOWN".equals(intent);
        }
    }
    
    public static class RoutingStats {
        private int totalRoutings = 0;
        private long totalLatencyMs = 0;
        private final Map<String, Integer> intentCounts = new HashMap<>();
        
        public synchronized void recordRouting(String intent, long latencyMs) {
            totalRoutings++;
            totalLatencyMs += latencyMs;
            intentCounts.merge(intent, 1, Integer::sum);
        }
        
        public int getTotalRoutings() { return totalRoutings; }
        public long getTotalLatencyMs() { return totalLatencyMs; }
        public Map<String, Integer> getIntentCounts() { return new HashMap<>(intentCounts); }
        
        public double getAvgLatencyMs() {
            return totalRoutings > 0 ? (double) totalLatencyMs / totalRoutings : 0;
        }
    }
}
