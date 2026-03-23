package com.livingagent.core.neuron.chat;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class ChatNeuronConfig {
    
    private String modelId = "qwen3-0.6b";
    private int maxTokens = 512;
    private double temperature = 0.7;
    private double topP = 0.9;
    private int contextWindowSize = 512;
    private int maxHistoryTurns = 5;
    private long responseTimeoutMs = 5000;
    private boolean enableQuickResponse = true;
    private boolean enableIntentClassification = true;
    private double intentConfidenceThreshold = 0.6;
    private List<String> systemPrompts = new ArrayList<>();
    private Map<String, String> personalityTraits = new HashMap<>();
    
    public ChatNeuronConfig() {
        initDefaultPrompts();
        initDefaultPersonality();
    }
    
    private void initDefaultPrompts() {
        systemPrompts.add("你是公司的前台接待，热情友好地接待每一位访客。");
        systemPrompts.add("你了解公司文化，但不涉及公司机密信息。");
        systemPrompts.add("请用简洁、自然的语言回答用户的问题。");
    }
    
    private void initDefaultPersonality() {
        personalityTraits.put("tone", "warm");
        personalityTraits.put("style", "professional_friendly");
        personalityTraits.put("language", "chinese");
        personalityTraits.put("role", "receptionist");
    }
    
    public String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是公司的前台接待，负责接待访客和日常问候。\n\n");
        sb.append("角色定位：\n");
        sb.append("- 公司形象代表，热情友好的第一接触点\n");
        sb.append("- 了解公司文化，但不涉及机密信息\n");
        sb.append("- 专注于表达和高效回复，不处理专业业务\n\n");
        sb.append("工作方式：\n");
        sb.append("- 快速响应，简洁明了\n");
        sb.append("- 礼貌热情，乐于助人\n");
        sb.append("- 遇到专业问题，告知访客将转接专业人员处理\n\n");
        sb.append("注意：\n");
        sb.append("- 你只负责日常问候和简单交流\n");
        sb.append("- 工具调用、部门引导等专业事务由其他系统处理\n");
        return sb.toString();
    }
    
    public String buildPromptWithContext(String userInput, List<Map<String, String>> history) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(buildSystemPrompt()).append("\n");
        
        if (history != null && !history.isEmpty()) {
            sb.append("--- 对话历史 ---\n");
            int startIdx = Math.max(0, history.size() - maxHistoryTurns);
            for (int i = startIdx; i < history.size(); i++) {
                Map<String, String> turn = history.get(i);
                String role = turn.getOrDefault("role", "user");
                String content = turn.getOrDefault("content", "");
                if ("user".equals(role)) {
                    sb.append("用户：").append(content).append("\n");
                } else {
                    sb.append("助手：").append(content).append("\n");
                }
            }
            sb.append("--- 当前问题 ---\n");
        }
        
        sb.append("用户：").append(userInput).append("\n");
        sb.append("助手：");
        
        return sb.toString();
    }
    
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    
    public double getTopP() { return topP; }
    public void setTopP(double topP) { this.topP = topP; }
    
    public int getContextWindowSize() { return contextWindowSize; }
    public void setContextWindowSize(int contextWindowSize) { this.contextWindowSize = contextWindowSize; }
    
    public int getMaxHistoryTurns() { return maxHistoryTurns; }
    public void setMaxHistoryTurns(int maxHistoryTurns) { this.maxHistoryTurns = maxHistoryTurns; }
    
    public long getResponseTimeoutMs() { return responseTimeoutMs; }
    public void setResponseTimeoutMs(long responseTimeoutMs) { this.responseTimeoutMs = responseTimeoutMs; }
    
    public boolean isEnableQuickResponse() { return enableQuickResponse; }
    public void setEnableQuickResponse(boolean enableQuickResponse) { this.enableQuickResponse = enableQuickResponse; }
    
    public boolean isEnableIntentClassification() { return enableIntentClassification; }
    public void setEnableIntentClassification(boolean enableIntentClassification) { this.enableIntentClassification = enableIntentClassification; }
    
    public double getIntentConfidenceThreshold() { return intentConfidenceThreshold; }
    public void setIntentConfidenceThreshold(double intentConfidenceThreshold) { this.intentConfidenceThreshold = intentConfidenceThreshold; }
    
    public List<String> getSystemPrompts() { return systemPrompts; }
    public void setSystemPrompts(List<String> systemPrompts) { this.systemPrompts = systemPrompts; }
    
    public Map<String, String> getPersonalityTraits() { return personalityTraits; }
    public void setPersonalityTraits(Map<String, String> personalityTraits) { this.personalityTraits = personalityTraits; }
    
    public static ChatNeuronConfig defaultConfig() {
        return new ChatNeuronConfig();
    }
    
    public static ChatNeuronConfig quickResponseConfig() {
        ChatNeuronConfig config = new ChatNeuronConfig();
        config.setMaxTokens(256);
        config.setTemperature(0.8);
        config.setResponseTimeoutMs(3000);
        config.setMaxHistoryTurns(3);
        return config;
    }
}
