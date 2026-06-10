package com.livingagent.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TokenUsage {

    private final String sessionId;
    private final String brainId;
    private final String model;
    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;
    private final double cost;
    private final Instant timestamp;
    private final String operationType;
    private final Map<String, Object> metadata;

    public TokenUsage(String sessionId, String brainId, String model,
                      int promptTokens, int completionTokens, int totalTokens,
                      double cost, String operationType) {
        this.sessionId = sessionId;
        this.brainId = brainId;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.cost = cost;
        this.timestamp = Instant.now();
        this.operationType = operationType;
        this.metadata = new ConcurrentHashMap<>();
    }

    public static TokenUsage of(String sessionId, String brainId, String model,
                                int promptTokens, int completionTokens, double costPer1K) {
        int total = promptTokens + completionTokens;
        double cost = (total / 1000.0) * costPer1K;
        return new TokenUsage(sessionId, brainId, model, promptTokens, completionTokens,
                total, cost, "default");
    }

    public static TokenUsage of(String sessionId, String brainId, String model,
                                int promptTokens, int completionTokens, double costPer1K,
                                String operationType) {
        int total = promptTokens + completionTokens;
        double cost = (total / 1000.0) * costPer1K;
        return new TokenUsage(sessionId, brainId, model, promptTokens, completionTokens,
                total, cost, operationType);
    }

    public TokenUsage withMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    public String getSessionId() { return sessionId; }
    public String getBrainId() { return brainId; }
    public String getModel() { return model; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public double getCost() { return cost; }
    public Instant getTimestamp() { return timestamp; }
    public String getOperationType() { return operationType; }
    public Map<String, Object> getMetadata() { return metadata; }

    @Override
    public String toString() {
        return String.format("TokenUsage{brain=%s, model=%s, prompt=%d, completion=%d, total=%d, cost=%.4f, op=%s}",
                brainId, model, promptTokens, completionTokens, totalTokens, cost, operationType);
    }

    public record UsageSummary(
            String brainId,
            int totalRequests,
            int totalPromptTokens,
            int totalCompletionTokens,
            int totalTokens,
            double totalCost,
            double avgPromptTokensPerRequest,
            double avgCompletionTokensPerRequest
    ) {
        public static UsageSummary from(java.util.List<TokenUsage> usages, String brainId) {
            if (usages == null || usages.isEmpty()) {
                return new UsageSummary(brainId, 0, 0, 0, 0, 0, 0, 0);
            }

            int totalPrompt = usages.stream().mapToInt(TokenUsage::getPromptTokens).sum();
            int totalCompletion = usages.stream().mapToInt(TokenUsage::getCompletionTokens).sum();
            int total = usages.stream().mapToInt(TokenUsage::getTotalTokens).sum();
            double totalCost = usages.stream().mapToDouble(TokenUsage::getCost).sum();

            return new UsageSummary(
                    brainId,
                    usages.size(),
                    totalPrompt,
                    totalCompletion,
                    total,
                    totalCost,
                    (double) totalPrompt / usages.size(),
                    (double) totalCompletion / usages.size()
            );
        }
    }
}
