package com.livingagent.core.proactive.habit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserHabitAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(UserHabitAnalyzer.class);

    private static final int MIN_SAMPLES = 3;
    private static final double HABIT_CONFIDENCE_THRESHOLD = 0.6;
    private static final long ANALYSIS_WINDOW_DAYS = 30;

    private final Map<String, UserHabitProfile> habitProfiles = new ConcurrentHashMap<>();
    private final ZoneId timezone;

    public UserHabitAnalyzer() {
        this(ZoneId.of("Asia/Shanghai"));
    }

    public UserHabitAnalyzer(ZoneId timezone) {
        this.timezone = timezone;
    }

    public void recordActivity(String userId, UserActivity activity) {
        UserHabitProfile profile = habitProfiles.computeIfAbsent(
                userId, 
                k -> new UserHabitProfile(userId)
        );
        
        profile.addActivity(activity);
        
        analyzeHabitPatterns(userId, profile);
        
        log.debug("Recorded activity for user {}: {}", userId, activity.activityType());
    }

    private void analyzeHabitPatterns(String userId, UserHabitProfile profile) {
        List<HabitPattern> patterns = new ArrayList<>();

        analyzeTimePatterns(profile, patterns);
        analyzeFrequencyPatterns(profile, patterns);
        analyzeSequencePatterns(profile, patterns);
        analyzePreferencePatterns(profile, patterns);

        profile.updatePatterns(patterns);
    }

    private void analyzeTimePatterns(UserHabitProfile profile, List<HabitPattern> patterns) {
        Map<Integer, Integer> hourlyDistribution = profile.getHourlyDistribution();
        Map<Integer, Integer> dailyDistribution = profile.getDailyDistribution();

        for (Map.Entry<Integer, Integer> entry : hourlyDistribution.entrySet()) {
            if (entry.getValue() >= MIN_SAMPLES) {
                double confidence = Math.min(1.0, entry.getValue() / 10.0);
                
                patterns.add(new HabitPattern(
                        "habit_hour_" + entry.getKey(),
                        HabitType.TIME_BASED,
                        "活跃时段",
                        Map.of("hour", entry.getKey()),
                        confidence,
                        Instant.now()
                ));
            }
        }

        for (Map.Entry<Integer, Integer> entry : dailyDistribution.entrySet()) {
            if (entry.getValue() >= MIN_SAMPLES) {
                double confidence = Math.min(1.0, entry.getValue() / 5.0);
                String dayName = getDayName(entry.getKey());
                
                patterns.add(new HabitPattern(
                        "habit_day_" + entry.getKey(),
                        HabitType.TIME_BASED,
                        "活跃日期",
                        Map.of("dayOfWeek", entry.getKey(), "dayName", dayName),
                        confidence,
                        Instant.now()
                ));
            }
        }
    }

    private void analyzeFrequencyPatterns(UserHabitProfile profile, List<HabitPattern> patterns) {
        Map<String, Integer> activityFrequency = profile.getActivityFrequency();

        for (Map.Entry<String, Integer> entry : activityFrequency.entrySet()) {
            if (entry.getValue() >= MIN_SAMPLES) {
                double confidence = Math.min(1.0, entry.getValue() / 20.0);
                
                patterns.add(new HabitPattern(
                        "habit_freq_" + entry.getKey(),
                        HabitType.FREQUENCY_BASED,
                        "常用操作",
                        Map.of("activity", entry.getKey(), "count", entry.getValue()),
                        confidence,
                        Instant.now()
                ));
            }
        }
    }

    private void analyzeSequencePatterns(UserHabitProfile profile, List<HabitPattern> patterns) {
        List<String> recentActivities = profile.getRecentActivityTypes(20);

        if (recentActivities.size() < 5) {
            return;
        }

        Map<String, Integer> sequences = new HashMap<>();
        for (int i = 0; i < recentActivities.size() - 1; i++) {
            String sequence = recentActivities.get(i) + " -> " + recentActivities.get(i + 1);
            sequences.merge(sequence, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : sequences.entrySet()) {
            if (entry.getValue() >= 2) {
                double confidence = Math.min(1.0, entry.getValue() / 5.0);
                String[] parts = entry.getKey().split(" -> ");
                
                patterns.add(new HabitPattern(
                        "habit_seq_" + System.nanoTime(),
                        HabitType.SEQUENCE_BASED,
                        "操作序列",
                        Map.of("from", parts[0], "to", parts[1], "count", entry.getValue()),
                        confidence,
                        Instant.now()
                ));
            }
        }
    }

    private void analyzePreferencePatterns(UserHabitProfile profile, List<HabitPattern> patterns) {
        Map<String, Integer> resourcePreference = profile.getResourcePreference();
        Map<String, Integer> toolPreference = profile.getToolPreference();

        for (Map.Entry<String, Integer> entry : resourcePreference.entrySet()) {
            if (entry.getValue() >= MIN_SAMPLES) {
                double confidence = Math.min(1.0, entry.getValue() / 10.0);
                
                patterns.add(new HabitPattern(
                        "habit_resource_" + entry.getKey().hashCode(),
                        HabitType.PREFERENCE_BASED,
                        "资源偏好",
                        Map.of("resource", entry.getKey(), "count", entry.getValue()),
                        confidence,
                        Instant.now()
                ));
            }
        }

        for (Map.Entry<String, Integer> entry : toolPreference.entrySet()) {
            if (entry.getValue() >= MIN_SAMPLES) {
                double confidence = Math.min(1.0, entry.getValue() / 8.0);
                
                patterns.add(new HabitPattern(
                        "habit_tool_" + entry.getKey().hashCode(),
                        HabitType.PREFERENCE_BASED,
                        "工具偏好",
                        Map.of("tool", entry.getKey(), "count", entry.getValue()),
                        confidence,
                        Instant.now()
                ));
            }
        }
    }

    public List<HabitPattern> getUserHabits(String userId) {
        UserHabitProfile profile = habitProfiles.get(userId);
        return profile != null ? profile.getPatterns() : List.of();
    }

    public Optional<HabitInsight> getHabitInsight(String userId) {
        UserHabitProfile profile = habitProfiles.get(userId);
        if (profile == null || profile.getActivityCount() < MIN_SAMPLES) {
            return Optional.empty();
        }

        List<HabitPattern> patterns = profile.getPatterns();
        if (patterns.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> insights = new HashMap<>();

        patterns.stream()
                .filter(p -> p.type() == HabitType.TIME_BASED)
                .filter(p -> p.parameters().containsKey("hour"))
                .max(Comparator.comparingDouble(HabitPattern::confidence))
                .ifPresent(p -> insights.put("peakHour", p.parameters().get("hour")));

        patterns.stream()
                .filter(p -> p.type() == HabitType.FREQUENCY_BASED)
                .max(Comparator.comparingDouble(HabitPattern::confidence))
                .ifPresent(p -> insights.put("mostFrequentActivity", p.parameters().get("activity")));

        patterns.stream()
                .filter(p -> p.type() == HabitType.PREFERENCE_BASED)
                .filter(p -> p.parameters().containsKey("tool"))
                .max(Comparator.comparingDouble(HabitPattern::confidence))
                .ifPresent(p -> insights.put("preferredTool", p.parameters().get("tool")));

        double overallConfidence = patterns.stream()
                .mapToDouble(HabitPattern::confidence)
                .average()
                .orElse(0);

        return Optional.of(new HabitInsight(
                userId,
                insights,
                overallConfidence,
                profile.getActivityCount(),
                Instant.now()
        ));
    }

    public Optional<HabitPrediction> predictNextActivity(String userId) {
        UserHabitProfile profile = habitProfiles.get(userId);
        if (profile == null) {
            return Optional.empty();
        }

        ZonedDateTime now = ZonedDateTime.now(timezone);
        int currentHour = now.getHour();

        List<HabitPattern> timePatterns = profile.getPatterns().stream()
                .filter(p -> p.type() == HabitType.TIME_BASED)
                .filter(p -> p.parameters().containsKey("hour"))
                .filter(p -> Math.abs((Integer) p.parameters().get("hour") - currentHour) <= 2)
                .toList();

        if (!timePatterns.isEmpty()) {
            List<HabitPattern> sequencePatterns = profile.getPatterns().stream()
                    .filter(p -> p.type() == HabitType.SEQUENCE_BASED)
                    .toList();

            String lastActivity = profile.getLastActivityType();
            
            for (HabitPattern seq : sequencePatterns) {
                if (seq.parameters().get("from").equals(lastActivity)) {
                    double confidence = seq.confidence() * 0.8;
                    
                    return Optional.of(new HabitPrediction(
                            "pred_" + System.currentTimeMillis(),
                            userId,
                            (String) seq.parameters().get("to"),
                            confidence,
                            "sequence_pattern",
                            Instant.now()
                    ));
                }
            }
        }

        List<HabitPattern> freqPatterns = profile.getPatterns().stream()
                .filter(p -> p.type() == HabitType.FREQUENCY_BASED)
                .sorted(Comparator.comparingDouble(HabitPattern::confidence).reversed())
                .toList();

        if (!freqPatterns.isEmpty()) {
            HabitPattern top = freqPatterns.get(0);
            return Optional.of(new HabitPrediction(
                    "pred_" + System.currentTimeMillis(),
                    userId,
                    (String) top.parameters().get("activity"),
                    top.confidence() * 0.6,
                    "frequency_pattern",
                    Instant.now()
            ));
        }

        return Optional.empty();
    }

    public void clearUserData(String userId) {
        habitProfiles.remove(userId);
        log.info("Cleared habit data for user: {}", userId);
    }

    public void cleanupOldData() {
        Instant cutoff = Instant.now().minus(ANALYSIS_WINDOW_DAYS, ChronoUnit.DAYS);
        
        habitProfiles.values().forEach(profile -> profile.removeOldActivities(cutoff));
        
        log.info("Cleaned up habit data older than {} days", ANALYSIS_WINDOW_DAYS);
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", habitProfiles.size());
        
        int totalPatterns = habitProfiles.values().stream()
                .mapToInt(p -> p.getPatterns().size())
                .sum();
        stats.put("totalPatterns", totalPatterns);
        
        Map<String, Long> byType = new HashMap<>();
        for (HabitType type : HabitType.values()) {
            byType.put(type.name(), habitProfiles.values().stream()
                    .flatMap(p -> p.getPatterns().stream())
                    .filter(pattern -> pattern.type() == type)
                    .count());
        }
        stats.put("patternsByType", byType);
        
        return stats;
    }

    private String getDayName(int dayOfWeek) {
        return switch (dayOfWeek) {
            case 1 -> "周一";
            case 2 -> "周二";
            case 3 -> "周三";
            case 4 -> "周四";
            case 5 -> "周五";
            case 6 -> "周六";
            case 7 -> "周日";
            default -> "未知";
        };
    }

    public record UserActivity(
            String activityId,
            String userId,
            String activityType,
            String resource,
            String tool,
            Map<String, Object> metadata,
            Instant timestamp
    ) {
        public static UserActivity of(String userId, String activityType) {
            return new UserActivity(
                    "act_" + System.currentTimeMillis(),
                    userId,
                    activityType,
                    null,
                    null,
                    Map.of(),
                    Instant.now()
            );
        }
        
        public static UserActivity of(String userId, String activityType, String resource) {
            return new UserActivity(
                    "act_" + System.currentTimeMillis(),
                    userId,
                    activityType,
                    resource,
                    null,
                    Map.of(),
                    Instant.now()
            );
        }
        
        public static UserActivity of(String userId, String activityType, String resource, String tool) {
            return new UserActivity(
                    "act_" + System.currentTimeMillis(),
                    userId,
                    activityType,
                    resource,
                    tool,
                    Map.of(),
                    Instant.now()
            );
        }
    }

    public record HabitPattern(
            String patternId,
            HabitType type,
            String name,
            Map<String, Object> parameters,
            double confidence,
            Instant detectedAt
    ) {}

    public record HabitInsight(
            String userId,
            Map<String, Object> insights,
            double confidence,
            int sampleCount,
            Instant analyzedAt
    ) {}

    public record HabitPrediction(
            String predictionId,
            String userId,
            String predictedActivity,
            double confidence,
            String basedOn,
            Instant predictedAt
    ) {}

    public enum HabitType {
        TIME_BASED,
        FREQUENCY_BASED,
        SEQUENCE_BASED,
        PREFERENCE_BASED
    }

    private static class UserHabitProfile {
        private final String userId;
        private final List<UserActivity> activities = Collections.synchronizedList(new ArrayList<>());
        private final Map<Integer, Integer> hourlyDistribution = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> dailyDistribution = new ConcurrentHashMap<>();
        private final Map<String, Integer> activityFrequency = new ConcurrentHashMap<>();
        private final Map<String, Integer> resourcePreference = new ConcurrentHashMap<>();
        private final Map<String, Integer> toolPreference = new ConcurrentHashMap<>();
        private List<HabitPattern> patterns = new ArrayList<>();

        UserHabitProfile(String userId) {
            this.userId = userId;
        }

        void addActivity(UserActivity activity) {
            activities.add(activity);

            ZonedDateTime time = activity.timestamp().atZone(ZoneId.systemDefault());
            hourlyDistribution.merge(time.getHour(), 1, Integer::sum);
            dailyDistribution.merge(time.getDayOfWeek().getValue(), 1, Integer::sum);

            activityFrequency.merge(activity.activityType(), 1, Integer::sum);

            if (activity.resource() != null) {
                resourcePreference.merge(activity.resource(), 1, Integer::sum);
            }

            if (activity.tool() != null) {
                toolPreference.merge(activity.tool(), 1, Integer::sum);
            }
        }

        void removeOldActivities(Instant cutoff) {
            activities.removeIf(a -> a.timestamp().isBefore(cutoff));
        }

        void updatePatterns(List<HabitPattern> newPatterns) {
            this.patterns = newPatterns;
        }

        int getActivityCount() {
            return activities.size();
        }

        Map<Integer, Integer> getHourlyDistribution() {
            return new HashMap<>(hourlyDistribution);
        }

        Map<Integer, Integer> getDailyDistribution() {
            return new HashMap<>(dailyDistribution);
        }

        Map<String, Integer> getActivityFrequency() {
            return new HashMap<>(activityFrequency);
        }

        Map<String, Integer> getResourcePreference() {
            return new HashMap<>(resourcePreference);
        }

        Map<String, Integer> getToolPreference() {
            return new HashMap<>(toolPreference);
        }

        List<HabitPattern> getPatterns() {
            return new ArrayList<>(patterns);
        }

        List<String> getRecentActivityTypes(int limit) {
            List<String> types = new ArrayList<>();
            for (int i = activities.size() - 1; i >= 0 && types.size() < limit; i--) {
                types.add(activities.get(i).activityType());
            }
            return types;
        }

        String getLastActivityType() {
            if (activities.isEmpty()) {
                return null;
            }
            return activities.get(activities.size() - 1).activityType();
        }
    }
}
