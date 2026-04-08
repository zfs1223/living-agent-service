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
import com.livingagent.core.security.AccessLevel;

public class ChatNeuronRouter {
    
    private static final Logger log = LoggerFactory.getLogger(ChatNeuronRouter.class);
    
    private final NeuronRegistry neuronRegistry;
    private final ChatIntentClassifier intentClassifier;
    private final ChatNeuronConfig config;
    
    private Neuron chatNeuron;
    private Neuron toolNeuron;
    private Neuron mainBrain;
    private final Map<String, Neuron> departmentBrains = new HashMap<>();
    
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
        
        neuronRegistry.get("neuron://brain/tech/001").ifPresent(n -> departmentBrains.put("tech", n));
        neuronRegistry.get("neuron://brain/hr/001").ifPresent(n -> departmentBrains.put("hr", n));
        neuronRegistry.get("neuron://brain/finance/001").ifPresent(n -> departmentBrains.put("finance", n));
        neuronRegistry.get("neuron://brain/admin/001").ifPresent(n -> departmentBrains.put("admin", n));
        neuronRegistry.get("neuron://brain/ops/001").ifPresent(n -> departmentBrains.put("ops", n));
        neuronRegistry.get("neuron://brain/sales/001").ifPresent(n -> departmentBrains.put("sales", n));
        neuronRegistry.get("neuron://brain/cs/001").ifPresent(n -> departmentBrains.put("cs", n));
        neuronRegistry.get("neuron://brain/legal/001").ifPresent(n -> departmentBrains.put("legal", n));
        
        log.info("ChatNeuronRouter initialized: chatNeuron={}, toolNeuron={}, mainBrain={}, departmentBrains={}",
            chatNeuron != null, toolNeuron != null, mainBrain != null, departmentBrains.size());
    }
    
    public RoutingResult route(String sessionId, String userInput, Map<String, Object> context) {
        long startTime = System.currentTimeMillis();
        
        AccessLevel accessLevel = extractAccessLevel(context);
        String departmentId = (String) context.getOrDefault("departmentId", "unknown");
        
        ChatIntentClassifier.ClassificationResult classification = intentClassifier.classify(userInput);
        
        RoutingResult result = new RoutingResult();
        result.setSessionId(sessionId);
        result.setOriginalInput(userInput);
        result.setIntent(classification.getIntent().name());
        result.setConfidence(classification.getConfidence());
        result.setReason(classification.getReason());
        result.setAccessLevel(accessLevel);
        result.setDepartmentId(departmentId);
        
        Neuron targetNeuron = selectTargetNeuronWithPermission(classification, accessLevel, departmentId, context);
        
        if (targetNeuron != null) {
            result.setTargetNeuron(targetNeuron.getId());
            result.setNeuron(targetNeuron);
            result.setPermissionGranted(true);
        } else {
            result.setTargetNeuron("chat-neuron");
            result.setNeuron(chatNeuron);
            result.setPermissionGranted(false);
            result.setPermissionDeniedReason(buildPermissionDeniedMessage(classification, accessLevel));
            log.warn("Permission denied: intent={}, accessLevel={}, user needs higher permission", 
                classification.getIntent(), accessLevel);
        }
        
        long latency = System.currentTimeMillis() - startTime;
        result.setRoutingLatencyMs(latency);
        
        updateStats(sessionId, classification.getIntent().name(), latency);
        
        log.debug("Routed input to {} (intent={}, confidence={}, accessLevel={}, latency={}ms)",
            result.getTargetNeuron(), result.getIntent(), result.getConfidence(), accessLevel, latency);
        
        return result;
    }
    
    private AccessLevel extractAccessLevel(Map<String, Object> context) {
        if (context == null) {
            return AccessLevel.CHAT_ONLY;
        }
        
        Object levelObj = context.get("accessLevel");
        if (levelObj instanceof AccessLevel) {
            return (AccessLevel) levelObj;
        }
        
        if (levelObj instanceof String) {
            try {
                return AccessLevel.valueOf((String) levelObj);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid access level string: {}", levelObj);
            }
        }
        
        Object identityObj = context.get("userIdentity");
        if (identityObj != null) {
            String identity = identityObj.toString();
            if (identity.contains("CHAIRMAN")) return AccessLevel.FULL;
            if (identity.contains("ACTIVE")) return AccessLevel.DEPARTMENT;
            if (identity.contains("PROBATION")) return AccessLevel.LIMITED;
            if (identity.contains("DEPARTED") || identity.contains("VISITOR")) return AccessLevel.CHAT_ONLY;
            if (identity.contains("CUSTOMER") || identity.contains("PARTNER")) return AccessLevel.LIMITED;
        }
        
        return AccessLevel.CHAT_ONLY;
    }
    
    private Neuron selectTargetNeuronWithPermission(ChatIntentClassifier.ClassificationResult classification,
                                                      AccessLevel accessLevel,
                                                      String departmentId,
                                                      Map<String, Object> context) {
        if (!config.isEnableIntentClassification()) {
            return chatNeuron;
        }
        
        return switch (classification.getIntent()) {
            case GREETING, CASUAL_CHAT, SIMPLE_QUESTION -> {
                yield chatNeuron;
            }
            
            case TOOL_CALL -> {
                if (accessLevel.getLevel() >= AccessLevel.DEPARTMENT.getLevel()) {
                    yield toolNeuron != null ? toolNeuron : chatNeuron;
                }
                log.info("TOOL_CALL denied for accessLevel={}, downgrading to chatNeuron", accessLevel);
                yield chatNeuron;
            }
            
            case COMPLEX_TASK -> {
                if (accessLevel == AccessLevel.FULL && mainBrain != null) {
                    log.debug("COMPLEX_TASK routed to MainBrain for FULL access");
                    yield mainBrain;
                }
                
                if (accessLevel == AccessLevel.DEPARTMENT) {
                    Neuron deptBrain = getDepartmentBrain(departmentId, classification, context);
                    if (deptBrain != null) {
                        log.debug("COMPLEX_TASK routed to department brain: {}", departmentId);
                        yield deptBrain;
                    }
                    yield chatNeuron;
                }
                
                if (accessLevel == AccessLevel.LIMITED) {
                    if (isAdminOrCsTask(classification, context)) {
                        Neuron limitedBrain = departmentBrains.get("admin");
                        if (limitedBrain != null) {
                            log.debug("COMPLEX_TASK routed to AdminBrain for LIMITED access");
                            yield limitedBrain;
                        }
                    }
                    log.info("COMPLEX_TASK denied for LIMITED access, downgrading to chatNeuron");
                    yield chatNeuron;
                }
                
                log.info("COMPLEX_TASK denied for CHAT_ONLY access, downgrading to chatNeuron");
                yield chatNeuron;
            }
            
            case UNKNOWN -> chatNeuron;
        };
    }
    
    private Neuron getDepartmentBrain(String departmentId, 
                                        ChatIntentClassifier.ClassificationResult classification,
                                        Map<String, Object> context) {
        if (departmentId != null && !departmentId.equals("unknown")) {
            Neuron brain = departmentBrains.get(departmentId.toLowerCase());
            if (brain != null) {
                return brain;
            }
        }
        
        String input = classification.getOriginalInput();
        if (input != null) {
            String lowerInput = input.toLowerCase();
            if (containsAny(lowerInput, "代码", "开发", "git", "部署", "bug")) {
                return departmentBrains.get("tech");
            }
            if (containsAny(lowerInput, "招聘", "考勤", "绩效", "员工")) {
                return departmentBrains.get("hr");
            }
            if (containsAny(lowerInput, "报销", "发票", "预算", "财务")) {
                return departmentBrains.get("finance");
            }
            if (containsAny(lowerInput, "文档", "文案", "行政")) {
                return departmentBrains.get("admin");
            }
            if (containsAny(lowerInput, "运营", "数据", "分析")) {
                return departmentBrains.get("ops");
            }
            if (containsAny(lowerInput, "销售", "客户", "营销")) {
                return departmentBrains.get("sales");
            }
            if (containsAny(lowerInput, "工单", "客服", "问题")) {
                return departmentBrains.get("cs");
            }
            if (containsAny(lowerInput, "合同", "法务", "合规")) {
                return departmentBrains.get("legal");
            }
        }
        
        return departmentBrains.get("admin");
    }
    
    private boolean isAdminOrCsTask(ChatIntentClassifier.ClassificationResult classification,
                                     Map<String, Object> context) {
        String input = classification.getOriginalInput();
        if (input == null) return false;
        
        String lowerInput = input.toLowerCase();
        return containsAny(lowerInput, "文档", "文案", "工单", "客服", "问题");
    }
    
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    private String buildPermissionDeniedMessage(ChatIntentClassifier.ClassificationResult classification,
                                                  AccessLevel accessLevel) {
        return switch (classification.getIntent()) {
            case TOOL_CALL -> "您的权限不足以使用工具调用功能。需要 DEPARTMENT 或更高权限。";
            case COMPLEX_TASK -> "您的权限不足以执行复杂任务。需要 DEPARTMENT 或更高权限。";
            default -> "您的权限不足以执行此操作。";
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
        private AccessLevel accessLevel;
        private String departmentId;
        private boolean permissionGranted = true;
        private String permissionDeniedReason;
        
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
        
        public AccessLevel getAccessLevel() { return accessLevel; }
        public void setAccessLevel(AccessLevel accessLevel) { this.accessLevel = accessLevel; }
        
        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        
        public boolean isPermissionGranted() { return permissionGranted; }
        public void setPermissionGranted(boolean permissionGranted) { this.permissionGranted = permissionGranted; }
        
        public String getPermissionDeniedReason() { return permissionDeniedReason; }
        public void setPermissionDeniedReason(String permissionDeniedReason) { this.permissionDeniedReason = permissionDeniedReason; }
        
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
