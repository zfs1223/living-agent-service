package com.livingagent.core.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class UsageTracker {

    private static final Logger log = LoggerFactory.getLogger(UsageTracker.class);

    private final Map<String, Queue<TokenUsage>> sessionUsages = new ConcurrentHashMap<>();
    private final Map<String, Queue<TokenUsage>> brainUsages = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> totalTokensByBrain = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> totalCostByBrain = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> requestCountByBrain = new ConcurrentHashMap<>();

    private volatile long sessionStartTime = System.currentTimeMillis();
    private volatile Instant trackingStartTime = Instant.now();

    public void recordUsage(TokenUsage usage) {
        if (usage == null) {
            return;
        }

        String sessionId = usage.getSessionId();
        String brainId = usage.getBrainId();

        sessionUsages.computeIfAbsent(sessionId, k -> new ConcurrentLinkedQueue<>()).add(usage);
        brainUsages.computeIfAbsent(brainId, k -> new ConcurrentLinkedQueue<>()).add(usage);

        totalTokensByBrain.computeIfAbsent(brainId, k -> new AtomicLong(0))
                .addAndGet(usage.getTotalTokens());
        totalCostByBrain.computeIfAbsent(brainId, k -> new AtomicLong(0))
                .addAndGet((long) (usage.getCost() * 10000));
        requestCountByBrain.computeIfAbsent(brainId, k -> new AtomicLong(0))
                .incrementAndGet();

        log.debug("Recorded usage: {} total={} cost={:.4f}",
                brainId, usage.getTotalTokens(), usage.getCost());
    }

    public void recordRequest(String sessionId, String brainId, String model,
                             int promptTokens, int completionTokens, double costPer1K) {
        TokenUsage usage = TokenUsage.of(sessionId, brainId, model,
                promptTokens, completionTokens, costPer1K);
        recordUsage(usage);
    }

    public List<TokenUsage> getSessionUsage(String sessionId) {
        Queue<TokenUsage> usages = sessionUsages.get(sessionId);
        if (usages == null) {
            return List.of();
        }
        return new ArrayList<>(usages);
    }

    public List<TokenUsage> getBrainUsage(String brainId) {
        Queue<TokenUsage> usages = brainUsages.get(brainId);
        if (usages == null) {
            return List.of();
        }
        return new ArrayList<>(usages);
    }

    public List<TokenUsage> getRecentUsage(String brainId, int limit) {
        Queue<TokenUsage> usages = brainUsages.get(brainId);
        if (usages == null) {
            return List.of();
        }
        return usages.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public TokenUsage.UsageSummary getSummary(String brainId) {
        List<TokenUsage> usages = getBrainUsage(brainId);
        return TokenUsage.UsageSummary.from(usages, brainId);
    }

    public Map<String, TokenUsage.UsageSummary> getAllSummaries() {
        Map<String, TokenUsage.UsageSummary> summaries = new HashMap<>();
        for (String brainId : brainUsages.keySet()) {
            summaries.put(brainId, getSummary(brainId));
        }
        return summaries;
    }

    public long getTotalTokens(String brainId) {
        AtomicLong total = totalTokensByBrain.get(brainId);
        return total != null ? total.get() : 0;
    }

    public double getTotalCost(String brainId) {
        AtomicLong total = totalCostByBrain.get(brainId);
        return total != null ? total.get() / 10000.0 : 0.0;
    }

    public long getRequestCount(String brainId) {
        AtomicLong count = requestCountByBrain.get(brainId);
        return count != null ? count.get() : 0;
    }

    public Map<String, Long> getTokenCountByModel(String brainId) {
        List<TokenUsage> usages = getBrainUsage(brainId);
        Map<String, Long> counts = new HashMap<>();
        for (TokenUsage usage : usages) {
            counts.merge(usage.getModel(), (long) usage.getTotalTokens(), Long::sum);
        }
        return counts;
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalTokensAll = totalTokensByBrain.values().stream()
                .mapToLong(AtomicLong::get).sum();
        long totalCostAll = totalCostByBrain.values().stream()
                .mapToLong(AtomicLong::get).sum();
        long totalRequests = requestCountByBrain.values().stream()
                .mapToLong(AtomicLong::get).sum();

        stats.put("totalTokens", totalTokensAll);
        stats.put("totalCost", totalCostAll / 10000.0);
        stats.put("totalRequests", totalRequests);
        stats.put("trackingDurationSeconds",
                Duration.between(trackingStartTime, Instant.now()).getSeconds());
        stats.put("brainCount", brainUsages.size());
        stats.put("sessionCount", sessionUsages.size());

        return stats;
    }

    public void resetBrainUsage(String brainId) {
        brainUsages.remove(brainId);
        totalTokensByBrain.remove(brainId);
        totalCostByBrain.remove(brainId);
        requestCountByBrain.remove(brainId);
        log.info("Reset usage tracking for brain: {}", brainId);
    }

    public void resetSessionUsage(String sessionId) {
        sessionUsages.remove(sessionId);
        log.info("Reset usage tracking for session: {}", sessionId);
    }

    public void resetAll() {
        sessionUsages.clear();
        brainUsages.clear();
        totalTokensByBrain.clear();
        totalCostByBrain.clear();
        requestCountByBrain.clear();
        trackingStartTime = Instant.now();
        log.info("Reset all usage tracking");
    }

    public Map<String, Object> getCostReport(String brainId, Instant since) {
        List<TokenUsage> usages = getBrainUsage(brainId).stream()
                .filter(u -> u.getTimestamp().isAfter(since))
                .collect(Collectors.toList());

        Map<String, Object> report = new HashMap<>();
        report.put("brainId", brainId);
        report.put("since", since.toString());
        report.put("requestCount", usages.size());

        int totalPrompt = usages.stream().mapToInt(TokenUsage::getPromptTokens).sum();
        int totalCompletion = usages.stream().mapToInt(TokenUsage::getCompletionTokens).sum();
        double totalCost = usages.stream().mapToDouble(TokenUsage::getCost).sum();

        report.put("promptTokens", totalPrompt);
        report.put("completionTokens", totalCompletion);
        report.put("totalTokens", totalPrompt + totalCompletion);
        report.put("totalCost", totalCost);

        Map<String, Integer> byModel = new HashMap<>();
        Map<String, Integer> byOperation = new HashMap<>();

        for (TokenUsage usage : usages) {
            byModel.merge(usage.getModel(), usage.getTotalTokens(), Integer::sum);
            byOperation.merge(usage.getOperationType(), usage.getTotalTokens(), Integer::sum);
        }

        report.put("byModel", byModel);
        report.put("byOperation", byOperation);

        return report;
    }
}
