package com.livingagent.core.proactive.predictor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PatternPredictor {

    private static final Logger log = LoggerFactory.getLogger(PatternPredictor.class);

    private static final int MIN_SAMPLES = 5;
    private static final double CONFIDENCE_THRESHOLD = 0.7;
    private static final long PATTERN_WINDOW_DAYS = 30;

    private final Map<String, UserBehaviorProfile> userProfiles = new ConcurrentHashMap<>();
    private final Map<String, List<BehaviorPattern>> detectedPatterns = new ConcurrentHashMap<>();

    public void recordBehavior(String userId, BehaviorEvent event) {
        UserBehaviorProfile profile = userProfiles.computeIfAbsent(userId, k -> new UserBehaviorProfile(userId));
        profile.addEvent(event);
        
        analyzePatterns(userId, profile);
        
        log.debug("Recorded behavior for user {}: {}", userId, event.eventType());
    }

    public List<BehaviorPattern> getUserPatterns(String userId) {
        return detectedPatterns.getOrDefault(userId, List.of());
    }

    public Optional<PredictedAction> predictNextAction(String userId) {
        UserBehaviorProfile profile = userProfiles.get(userId);
        if (profile == null || profile.getEventCount() < MIN_SAMPLES) {
            return Optional.empty();
        }

        List<BehaviorPattern> patterns = detectedPatterns.getOrDefault(userId, List.of());
        if (patterns.isEmpty()) {
            return Optional.empty();
        }

        BehaviorPattern strongestPattern = patterns.stream()
                .max(Comparator.comparingDouble(BehaviorPattern::confidence))
                .orElse(null);

        if (strongestPattern == null || strongestPattern.confidence() < CONFIDENCE_THRESHOLD) {
            return Optional.empty();
        }

        Instant predictedTime = predictNextOccurrence(strongestPattern);
        String predictedAction = strongestPattern.actionType();

        return Optional.of(new PredictedAction(
                "pred_" + System.currentTimeMillis(),
                userId,
                predictedAction,
                predictedTime,
                strongestPattern.confidence(),
                strongestPattern.patternId()
        ));
    }

    public List<UserInsight> getUserInsights(String userId) {
        List<UserInsight> insights = new ArrayList<>();
        
        UserBehaviorProfile profile = userProfiles.get(userId);
        if (profile == null) {
            return insights;
        }

        Map<String, Integer> hourlyDistribution = profile.getHourlyDistribution();
        int peakHour = findPeakHour(hourlyDistribution);
        if (peakHour >= 0) {
            insights.add(new UserInsight(
                    "peak_activity_hour",
                    "用户活跃时段",
                    String.format("用户通常在 %d:00 最活跃", peakHour),
                    0.8
            ));
        }

        Map<String, Integer> actionFrequency = profile.getActionFrequency();
        String mostFrequentAction = findMostFrequent(actionFrequency);
        if (mostFrequentAction != null) {
            insights.add(new UserInsight(
                    "frequent_action",
                    "常用操作",
                    String.format("用户最常执行: %s", mostFrequentAction),
                    0.9
            ));
        }

        Map<String, Integer> resourceAccess = profile.getResourceAccess();
        String mostAccessedResource = findMostFrequent(resourceAccess);
        if (mostAccessedResource != null) {
            insights.add(new UserInsight(
                    "preferred_resource",
                    "偏好资源",
                    String.format("用户最常访问: %s", mostAccessedResource),
                    0.7
            ));
        }

        return insights;
    }

    private void analyzePatterns(String userId, UserBehaviorProfile profile) {
        List<BehaviorPattern> patterns = new ArrayList<>();

        analyzeTimePatterns(profile, patterns);
        analyzeSequencePatterns(profile, patterns);
        analyzeResourcePatterns(profile, patterns);

        detectedPatterns.put(userId, patterns);
    }

    private void analyzeTimePatterns(UserBehaviorProfile profile, List<BehaviorPattern> patterns) {
        Map<String, Integer> hourlyDistribution = profile.getHourlyDistribution();
        
        for (Map.Entry<String, Integer> entry : hourlyDistribution.entrySet()) {
            if (entry.getValue() >= MIN_SAMPLES) {
                double confidence = Math.min(1.0, entry.getValue() / 10.0);
                
                try {
                    int hour = Integer.parseInt(entry.getKey());
                    patterns.add(new BehaviorPattern(
                            "pattern_time_" + entry.getKey(),
                            "time_based",
                            "hourly_action",
                            Map.of("hour", hour),
                            confidence,
                            Instant.now()
                    ));
                } catch (NumberFormatException e) {
                    log.warn("Invalid hour key: {}", entry.getKey());
                }
            }
        }
    }

    private void analyzeSequencePatterns(UserBehaviorProfile profile, List<BehaviorPattern> patterns) {
        List<String> recentActions = profile.getRecentActionTypes(10);
        
        if (recentActions.size() < 3) {
            return;
        }

        Map<String, Integer> sequences = new HashMap<>();
        for (int i = 0; i < recentActions.size() - 1; i++) {
            String sequence = recentActions.get(i) + " -> " + recentActions.get(i + 1);
            sequences.merge(sequence, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : sequences.entrySet()) {
            if (entry.getValue() >= 2) {
                double confidence = Math.min(1.0, entry.getValue() / 5.0);
                
                String[] parts = entry.getKey().split(" -> ");
                patterns.add(new BehaviorPattern(
                        "pattern_seq_" + System.nanoTime(),
                        "sequence",
                        parts[1],
                        Map.of("previousAction", parts[0]),
                        confidence,
                        Instant.now()
                ));
            }
        }
    }

    private void analyzeResourcePatterns(UserBehaviorProfile profile, List<BehaviorPattern> patterns) {
        Map<String, Integer> resourceAccess = profile.getResourceAccess();
        
        for (Map.Entry<String, Integer> entry : resourceAccess.entrySet()) {
            if (entry.getValue() >= MIN_SAMPLES) {
                double confidence = Math.min(1.0, entry.getValue() / 10.0);
                
                patterns.add(new BehaviorPattern(
                        "pattern_resource_" + entry.getKey().hashCode(),
                        "resource_based",
                        "access_resource",
                        Map.of("resource", entry.getKey()),
                        confidence,
                        Instant.now()
                ));
            }
        }
    }

    private Instant predictNextOccurrence(BehaviorPattern pattern) {
        Instant baseTime = Instant.now();
        
        return switch (pattern.patternType()) {
            case "time_based" -> {
                Integer hour = (Integer) pattern.parameters().get("hour");
                if (hour != null) {
                    Instant nextHour = baseTime.truncatedTo(ChronoUnit.HOURS).plus(hour - baseTime.atZone(java.time.ZoneId.systemDefault()).getHour(), ChronoUnit.HOURS);
                    if (nextHour.isBefore(baseTime)) {
                        nextHour = nextHour.plus(24, ChronoUnit.HOURS);
                    }
                    yield nextHour;
                }
                yield baseTime.plus(1, ChronoUnit.HOURS);
            }
            case "sequence" -> baseTime.plus(5, ChronoUnit.MINUTES);
            case "resource_based" -> baseTime.plus(30, ChronoUnit.MINUTES);
            default -> baseTime.plus(1, ChronoUnit.HOURS);
        };
    }

    private int findPeakHour(Map<String, Integer> hourlyDistribution) {
        return hourlyDistribution.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(entry -> {
                    try {
                        return Integer.parseInt(entry.getKey());
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                })
                .orElse(-1);
    }

    private String findMostFrequent(Map<String, Integer> frequency) {
        return frequency.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userProfiles.size());
        stats.put("usersWithPatterns", detectedPatterns.size());
        
        int totalPatterns = detectedPatterns.values().stream()
                .mapToInt(List::size)
                .sum();
        stats.put("totalPatterns", totalPatterns);
        
        return stats;
    }

    public void clearUserData(String userId) {
        userProfiles.remove(userId);
        detectedPatterns.remove(userId);
        log.info("Cleared behavior data for user: {}", userId);
    }

    public void cleanupOldData() {
        Instant cutoff = Instant.now().minus(PATTERN_WINDOW_DAYS, ChronoUnit.DAYS);
        
        userProfiles.values().forEach(profile -> profile.removeOldEvents(cutoff));
        
        log.info("Cleaned up behavior data older than {} days", PATTERN_WINDOW_DAYS);
    }

    public record BehaviorEvent(
            String eventId,
            String userId,
            String eventType,
            String resource,
            Map<String, Object> metadata,
            Instant timestamp
    ) {
        public static BehaviorEvent of(String userId, String eventType) {
            return new BehaviorEvent(
                    "evt_" + System.currentTimeMillis(),
                    userId,
                    eventType,
                    null,
                    Map.of(),
                    Instant.now()
            );
        }
        
        public static BehaviorEvent of(String userId, String eventType, String resource) {
            return new BehaviorEvent(
                    "evt_" + System.currentTimeMillis(),
                    userId,
                    eventType,
                    resource,
                    Map.of(),
                    Instant.now()
            );
        }
        
        public BehaviorEvent withMetadata(Map<String, Object> metadata) {
            return new BehaviorEvent(eventId, userId, eventType, resource, metadata, timestamp);
        }
    }

    public record BehaviorPattern(
            String patternId,
            String patternType,
            String actionType,
            Map<String, Object> parameters,
            double confidence,
            Instant detectedAt
    ) {}

    public record PredictedAction(
            String predictionId,
            String userId,
            String predictedAction,
            Instant predictedTime,
            double confidence,
            String basedOnPattern
    ) {}

    public record UserInsight(
            String insightType,
            String insightName,
            String description,
            double confidence
    ) {}

    private static class UserBehaviorProfile {
        private final String userId;
        private final List<BehaviorEvent> events = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, Integer> hourlyDistribution = new ConcurrentHashMap<>();
        private final Map<String, Integer> actionFrequency = new ConcurrentHashMap<>();
        private final Map<String, Integer> resourceAccess = new ConcurrentHashMap<>();

        UserBehaviorProfile(String userId) {
            this.userId = userId;
        }

        void addEvent(BehaviorEvent event) {
            events.add(event);
            
            int hour = event.timestamp().atZone(java.time.ZoneId.systemDefault()).getHour();
            hourlyDistribution.merge(String.valueOf(hour), 1, Integer::sum);
            
            actionFrequency.merge(event.eventType(), 1, Integer::sum);
            
            if (event.resource() != null) {
                resourceAccess.merge(event.resource(), 1, Integer::sum);
            }
        }

        void removeOldEvents(Instant cutoff) {
            events.removeIf(e -> e.timestamp().isBefore(cutoff));
        }

        int getEventCount() {
            return events.size();
        }

        Map<String, Integer> getHourlyDistribution() {
            return new HashMap<>(hourlyDistribution);
        }

        Map<String, Integer> getActionFrequency() {
            return new HashMap<>(actionFrequency);
        }

        Map<String, Integer> getResourceAccess() {
            return new HashMap<>(resourceAccess);
        }

        List<String> getRecentActionTypes(int limit) {
            List<String> types = new ArrayList<>();
            for (int i = events.size() - 1; i >= 0 && types.size() < limit; i--) {
                types.add(events.get(i).eventType());
            }
            return types;
        }
    }
}
