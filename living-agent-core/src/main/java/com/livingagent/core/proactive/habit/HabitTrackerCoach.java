package com.livingagent.core.proactive.habit;

import com.livingagent.core.proactive.alert.AlertNotifier;
import com.livingagent.core.proactive.alert.AlertNotifier.Alert;
import com.livingagent.core.proactive.cron.CronJob;
import com.livingagent.core.proactive.cron.CronService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HabitTrackerCoach {

    private static final Logger log = LoggerFactory.getLogger(HabitTrackerCoach.class);

    private final CronService cronService;
    private final List<AlertNotifier> notifiers;
    private final ZoneId timezone;
    
    private final Map<String, UserHabits> userHabits = new ConcurrentHashMap<>();
    private final Map<String, HabitCheckIn> pendingCheckIns = new ConcurrentHashMap<>();

    public HabitTrackerCoach(CronService cronService, List<AlertNotifier> notifiers) {
        this(cronService, notifiers, ZoneId.of("Asia/Shanghai"));
    }

    public HabitTrackerCoach(CronService cronService, List<AlertNotifier> notifiers, ZoneId timezone) {
        this.cronService = cronService;
        this.notifiers = notifiers != null ? new ArrayList<>(notifiers) : new ArrayList<>();
        this.timezone = timezone;
    }

    public HabitDefinition defineHabit(String userId, String habitName, HabitFrequency frequency, 
                                        LocalTime reminderTime, List<String> channels) {
        UserHabits habits = userHabits.computeIfAbsent(userId, k -> new UserHabits(userId));
        
        HabitDefinition habit = new HabitDefinition(
                "habit_" + System.currentTimeMillis(),
                userId,
                habitName,
                frequency,
                reminderTime,
                channels != null ? channels : List.of("dingtalk"),
                true,
                Instant.now()
        );
        
        habits.addHabit(habit);
        
        scheduleHabitReminder(habit);
        
        log.info("Defined habit for user {}: {} ({})", userId, habitName, frequency);
        return habit;
    }

    private void scheduleHabitReminder(HabitDefinition habit) {
        String cronExpr = buildCronExpression(habit.frequency(), habit.reminderTime());
        
        CronJob cronJob = CronJob.create(
                "Habit: " + habit.habitName(),
                cronExpr,
                "habit_check"
        ).withTaskParams(Map.of(
                "habitId", habit.habitId(),
                "userId", habit.userId(),
                "habitName", habit.habitName()
        ));
        
        cronService.scheduleJob(cronJob);
    }

    private String buildCronExpression(HabitFrequency frequency, LocalTime time) {
        int hour = time.getHour();
        int minute = time.getMinute();
        
        return switch (frequency) {
            case HOURLY -> String.format("%d * * * *", minute);
            case DAILY -> String.format("%d %d * * *", minute, hour);
            case WEEKDAYS -> String.format("%d %d * * 1-5", minute, hour);
            case WEEKLY -> String.format("%d %d * * 1", minute, hour);
            case CUSTOM -> String.format("%d %d * * *", minute, hour);
        };
    }

    public void recordProgress(String userId, String habitId, boolean completed, String notes) {
        UserHabits habits = userHabits.get(userId);
        if (habits == null) {
            log.warn("No habits found for user: {}", userId);
            return;
        }

        HabitDefinition habit = habits.getHabit(habitId);
        if (habit == null) {
            log.warn("Habit not found: {}", habitId);
            return;
        }

        HabitRecord record = new HabitRecord(
                "rec_" + System.currentTimeMillis(),
                habitId,
                userId,
                LocalDate.now(timezone),
                completed,
                notes,
                Instant.now()
        );
        
        habits.addRecord(record);
        
        int streak = habits.getCurrentStreak(habitId);
        
        if (completed) {
            sendEncouragement(userId, habit, streak);
        } else {
            sendMotivation(userId, habit, streak);
        }
        
        log.info("Recorded progress for habit {}: completed={}, streak={}", habitId, completed, streak);
    }

    public void triggerCheckIn(String userId, String habitId) {
        UserHabits habits = userHabits.get(userId);
        if (habits == null) {
            return;
        }

        HabitDefinition habit = habits.getHabit(habitId);
        if (habit == null || !habit.enabled()) {
            return;
        }

        int streak = habits.getCurrentStreak(habitId);
        String tone = determineTone(streak);
        
        HabitCheckIn checkIn = new HabitCheckIn(
                "checkin_" + System.currentTimeMillis(),
                habitId,
                userId,
                habit.habitName(),
                streak,
                tone,
                Instant.now(),
                CheckInStatus.PENDING
        );
        
        pendingCheckIns.put(checkIn.checkInId(), checkIn);
        
        sendCheckInNotification(userId, habit, checkIn);
    }

    private String determineTone(int streak) {
        if (streak >= 30) {
            return "celebratory";
        } else if (streak >= 14) {
            return "encouraging";
        } else if (streak >= 7) {
            return "supportive";
        } else if (streak >= 3) {
            return "gentle";
        } else if (streak > 0) {
            return "motivating";
        } else {
            return "friendly";
        }
    }

    private void sendEncouragement(String userId, HabitDefinition habit, int streak) {
        String message = switch (streak) {
            case 7 -> String.format("🎉 太棒了！您已连续完成「%s」一周！继续保持！", habit.habitName());
            case 14 -> String.format("🏆 恭喜！您已连续完成「%s」两周！您正在养成一个好习惯！", habit.habitName());
            case 30 -> String.format("🌟 了不起！您已连续完成「%s」一个月！这已经成为您的习惯了！", habit.habitName());
            default -> {
                if (streak > 0) {
                    yield String.format("✅ 做得好！「%s」连续 %d 天完成！", habit.habitName(), streak);
                } else {
                    yield String.format("✅ 很好！您完成了今天的「%s」！", habit.habitName());
                }
            }
        };
        
        sendNotification(userId, "习惯追踪 - 进度更新", message, habit.channels());
    }

    private void sendMotivation(String userId, HabitDefinition habit, int streak) {
        String message = String.format(
                "💪 别灰心！「%s」今天没完成，但明天是新的一天。您之前已经连续 %d 天了，重新开始吧！",
                habit.habitName(),
                streak
        );
        
        sendNotification(userId, "习惯追踪 - 加油", message, habit.channels());
    }

    private void sendCheckInNotification(String userId, HabitDefinition habit, HabitCheckIn checkIn) {
        String message = String.format(
                "📋 习惯检查提醒\n\n" +
                "「%s」\n\n" +
                "当前连续: %d 天\n\n" +
                "请回复「完成」或「跳过」来记录今天的进度。",
                habit.habitName(),
                checkIn.streak()
        );
        
        sendNotification(userId, "习惯检查 - " + habit.habitName(), message, habit.channels());
    }

    private void sendNotification(String userId, String title, String content, List<String> channels) {
        Alert alert = Alert.info(title, content).withTargetUsers(List.of(userId));

        for (AlertNotifier notifier : notifiers) {
            if (notifier.isAvailable() && 
                    (channels.isEmpty() || channels.contains(notifier.getChannelName()))) {
                try {
                    notifier.send(alert);
                } catch (Exception e) {
                    log.warn("Failed to send notification via {}: {}", notifier.getChannelName(), e.getMessage());
                }
            }
        }
    }

    public void respondToCheckIn(String checkInId, boolean completed, String notes) {
        HabitCheckIn checkIn = pendingCheckIns.remove(checkInId);
        if (checkIn == null) {
            log.warn("Check-in not found: {}", checkInId);
            return;
        }

        recordProgress(checkIn.userId(), checkIn.habitId(), completed, notes);
    }

    public List<HabitDefinition> getUserHabits(String userId) {
        UserHabits habits = userHabits.get(userId);
        return habits != null ? habits.getAllHabits() : List.of();
    }

    public HabitStats getHabitStats(String userId, String habitId) {
        UserHabits habits = userHabits.get(userId);
        if (habits == null) {
            return null;
        }

        HabitDefinition habit = habits.getHabit(habitId);
        if (habit == null) {
            return null;
        }

        int currentStreak = habits.getCurrentStreak(habitId);
        int longestStreak = habits.getLongestStreak(habitId);
        int totalCompletions = habits.getTotalCompletions(habitId);
        double completionRate = habits.getCompletionRate(habitId);

        return new HabitStats(
                habitId,
                habit.habitName(),
                currentStreak,
                longestStreak,
                totalCompletions,
                completionRate,
                Instant.now()
        );
    }

    public boolean deleteHabit(String userId, String habitId) {
        UserHabits habits = userHabits.get(userId);
        if (habits == null) {
            return false;
        }

        boolean removed = habits.removeHabit(habitId);
        if (removed) {
            log.info("Deleted habit: {} for user: {}", habitId, userId);
        }
        return removed;
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userHabits.size());
        
        int totalHabits = userHabits.values().stream()
                .mapToInt(h -> h.getAllHabits().size())
                .sum();
        stats.put("totalHabits", totalHabits);
        
        int totalRecords = userHabits.values().stream()
                .mapToInt(UserHabits::getTotalRecords)
                .sum();
        stats.put("totalRecords", totalRecords);
        
        int activeStreaks = (int) userHabits.values().stream()
                .flatMap(h -> h.getAllHabits().stream())
                .filter(h -> {
                    UserHabits uh = userHabits.get(h.userId());
                    return uh != null && uh.getCurrentStreak(h.habitId()) > 0;
                })
                .count();
        stats.put("activeStreaks", activeStreaks);
        
        return stats;
    }

    public record HabitDefinition(
            String habitId,
            String userId,
            String habitName,
            HabitFrequency frequency,
            LocalTime reminderTime,
            List<String> channels,
            boolean enabled,
            Instant createdAt
    ) {}

    public record HabitRecord(
            String recordId,
            String habitId,
            String userId,
            LocalDate date,
            boolean completed,
            String notes,
            Instant recordedAt
    ) {}

    public record HabitCheckIn(
            String checkInId,
            String habitId,
            String userId,
            String habitName,
            int streak,
            String tone,
            Instant triggeredAt,
            CheckInStatus status
    ) {}

    public record HabitStats(
            String habitId,
            String habitName,
            int currentStreak,
            int longestStreak,
            int totalCompletions,
            double completionRate,
            Instant calculatedAt
    ) {}

    public enum HabitFrequency {
        HOURLY,
        DAILY,
        WEEKDAYS,
        WEEKLY,
        CUSTOM
    }

    public enum CheckInStatus {
        PENDING,
        COMPLETED,
        SKIPPED,
        EXPIRED
    }

    private static class UserHabits {
        private final String userId;
        private final Map<String, HabitDefinition> habits = new ConcurrentHashMap<>();
        private final Map<String, List<HabitRecord>> records = new ConcurrentHashMap<>();

        UserHabits(String userId) {
            this.userId = userId;
        }

        void addHabit(HabitDefinition habit) {
            habits.put(habit.habitId(), habit);
            records.putIfAbsent(habit.habitId(), new ArrayList<>());
        }

        void addRecord(HabitRecord record) {
            records.computeIfAbsent(record.habitId(), k -> new ArrayList<>()).add(record);
        }

        boolean removeHabit(String habitId) {
            return habits.remove(habitId) != null;
        }

        HabitDefinition getHabit(String habitId) {
            return habits.get(habitId);
        }

        List<HabitDefinition> getAllHabits() {
            return new ArrayList<>(habits.values());
        }

        int getCurrentStreak(String habitId) {
            List<HabitRecord> habitRecords = records.get(habitId);
            if (habitRecords == null || habitRecords.isEmpty()) {
                return 0;
            }

            habitRecords.sort(Comparator.comparing(HabitRecord::date).reversed());
            
            int streak = 0;
            LocalDate expectedDate = LocalDate.now();
            
            for (HabitRecord record : habitRecords) {
                if (record.completed() && record.date().equals(expectedDate)) {
                    streak++;
                    expectedDate = expectedDate.minusDays(1);
                } else if (!record.date().equals(expectedDate)) {
                    break;
                }
            }
            
            return streak;
        }

        int getLongestStreak(String habitId) {
            List<HabitRecord> habitRecords = records.get(habitId);
            if (habitRecords == null || habitRecords.isEmpty()) {
                return 0;
            }

            habitRecords.sort(Comparator.comparing(HabitRecord::date));
            
            int maxStreak = 0;
            int currentStreak = 0;
            LocalDate lastDate = null;
            
            for (HabitRecord record : habitRecords) {
                if (record.completed()) {
                    if (lastDate == null || record.date().equals(lastDate.plusDays(1))) {
                        currentStreak++;
                    } else {
                        currentStreak = 1;
                    }
                    maxStreak = Math.max(maxStreak, currentStreak);
                    lastDate = record.date();
                } else {
                    currentStreak = 0;
                    lastDate = null;
                }
            }
            
            return maxStreak;
        }

        int getTotalCompletions(String habitId) {
            List<HabitRecord> habitRecords = records.get(habitId);
            if (habitRecords == null) {
                return 0;
            }
            return (int) habitRecords.stream().filter(HabitRecord::completed).count();
        }

        double getCompletionRate(String habitId) {
            List<HabitRecord> habitRecords = records.get(habitId);
            if (habitRecords == null || habitRecords.isEmpty()) {
                return 0;
            }
            return (double) habitRecords.stream().filter(HabitRecord::completed).count() / habitRecords.size();
        }

        int getTotalRecords() {
            return records.values().stream().mapToInt(List::size).sum();
        }
    }
}
