package com.livingagent.core.proactive.predictor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class TimePredictor {

    private static final Logger log = LoggerFactory.getLogger(TimePredictor.class);

    private final ZoneId defaultTimezone;

    public TimePredictor() {
        this(ZoneId.of("Asia/Shanghai"));
    }

    public TimePredictor(ZoneId timezone) {
        this.defaultTimezone = timezone;
    }

    public List<TimeBasedTask> predictUpcomingTasks(TimePredictConfig config) {
        List<TimeBasedTask> tasks = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now(defaultTimezone);

        for (ScheduledItem item : config.scheduledItems()) {
            ZonedDateTime nextExecution = calculateNextExecution(item, now);
            
            if (nextExecution != null && shouldTrigger(item, nextExecution, now)) {
                TimeBasedTask task = createTask(item, nextExecution);
                tasks.add(task);
            }
        }

        tasks.sort(Comparator.comparing(TimeBasedTask::scheduledTime));
        return tasks;
    }

    public List<ExpiryReminder> checkExpiringItems(ExpiryCheckConfig config) {
        List<ExpiryReminder> reminders = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now(defaultTimezone);

        for (ExpirableItem item : config.items()) {
            ZonedDateTime expiryDate = item.expiryDate();
            
            if (expiryDate == null) {
                continue;
            }

            long daysUntilExpiry = ChronoUnit.DAYS.between(now, expiryDate);
            
            for (int threshold : config.reminderDays()) {
                if (daysUntilExpiry == threshold) {
                    ExpiryReminder reminder = new ExpiryReminder(
                            item.itemId(),
                            item.itemType(),
                            item.name(),
                            expiryDate,
                            (int) daysUntilExpiry,
                            item.responsibleUsers()
                    );
                    reminders.add(reminder);
                    log.info("Expiry reminder: {} expires in {} days", item.name(), daysUntilExpiry);
                }
            }
        }

        return reminders;
    }

    public List<PeriodicReport> generatePeriodicReports(ReportConfig config) {
        List<PeriodicReport> reports = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now(defaultTimezone);

        for (ReportSchedule schedule : config.schedules()) {
            if (shouldGenerateReport(schedule, now)) {
                PeriodicReport report = new PeriodicReport(
                        schedule.reportId(),
                        schedule.reportName(),
                        schedule.reportType(),
                        calculateReportPeriod(schedule, now),
                        schedule.targetUsers(),
                        schedule.dataSources()
                );
                reports.add(report);
                log.info("Periodic report scheduled: {} ({})", schedule.reportName(), schedule.reportType());
            }
        }

        return reports;
    }

    private ZonedDateTime calculateNextExecution(ScheduledItem item, ZonedDateTime now) {
        String cronExpr = item.cronExpression();
        if (cronExpr == null || cronExpr.isEmpty()) {
            return null;
        }

        return parseCronExpression(cronExpr, now);
    }

    private ZonedDateTime parseCronExpression(String cronExpr, ZonedDateTime now) {
        String[] parts = cronExpr.split("\\s+");
        if (parts.length < 5) {
            return null;
        }

        int minute = parseCronPart(parts[0], now.getMinute(), 0, 59);
        int hour = parseCronPart(parts[1], now.getHour(), 0, 23);
        int dayOfMonth = parseCronPart(parts[2], now.getDayOfMonth(), 1, 31);
        int month = parseCronPart(parts[3], now.getMonthValue(), 1, 12);
        
        ZonedDateTime next = now.withMinute(minute).withHour(hour);
        
        if (next.isBefore(now) || next.isEqual(now)) {
            next = next.plusDays(1);
        }
        
        return next;
    }

    private int parseCronPart(String part, int currentValue, int min, int max) {
        if (part.equals("*")) {
            return currentValue;
        }
        
        if (part.startsWith("*/")) {
            int interval = Integer.parseInt(part.substring(2));
            return currentValue;
        }
        
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return currentValue;
        }
    }

    private boolean shouldTrigger(ScheduledItem item, ZonedDateTime nextExecution, ZonedDateTime now) {
        long minutesUntil = ChronoUnit.MINUTES.between(now, nextExecution);
        return minutesUntil <= item.advanceMinutes() && minutesUntil >= 0;
    }

    private boolean shouldGenerateReport(ReportSchedule schedule, ZonedDateTime now) {
        return switch (schedule.frequency()) {
            case DAILY -> now.getHour() == schedule.generateHour() && now.getMinute() == schedule.generateMinute();
            case WEEKLY -> now.getDayOfWeek() == schedule.generateDayOfWeek() 
                    && now.getHour() == schedule.generateHour();
            case MONTHLY -> now.getDayOfMonth() == schedule.generateDayOfMonth() 
                    && now.getHour() == schedule.generateHour();
            case QUARTERLY -> isQuarterEnd(now) && now.getHour() == schedule.generateHour();
        };
    }

    private boolean isQuarterEnd(ZonedDateTime now) {
        int month = now.getMonthValue();
        return (month == 3 || month == 6 || month == 9 || month == 12) && now.getDayOfMonth() >= 28;
    }

    private ReportPeriod calculateReportPeriod(ReportSchedule schedule, ZonedDateTime now) {
        return switch (schedule.frequency()) {
            case DAILY -> new ReportPeriod(
                    now.truncatedTo(ChronoUnit.DAYS).minusDays(1),
                    now.truncatedTo(ChronoUnit.DAYS).minusSeconds(1)
            );
            case WEEKLY -> new ReportPeriod(
                    now.truncatedTo(ChronoUnit.DAYS).minusWeeks(1),
                    now.truncatedTo(ChronoUnit.DAYS).minusSeconds(1)
            );
            case MONTHLY -> new ReportPeriod(
                    now.truncatedTo(ChronoUnit.DAYS).minusMonths(1).withDayOfMonth(1),
                    now.truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1).minusSeconds(1)
            );
            case QUARTERLY -> calculateQuarterPeriod(now);
        };
    }

    private ReportPeriod calculateQuarterPeriod(ZonedDateTime now) {
        int quarter = (now.getMonthValue() - 1) / 3;
        int startMonth = quarter * 3 + 1;
        ZonedDateTime start = now.withMonth(startMonth).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime end = start.plusMonths(3).minusSeconds(1);
        return new ReportPeriod(start, end);
    }

    private TimeBasedTask createTask(ScheduledItem item, ZonedDateTime executionTime) {
        return new TimeBasedTask(
                "task_" + System.currentTimeMillis(),
                item.taskName(),
                item.taskType(),
                executionTime,
                item.parameters(),
                item.targetUsers(),
                item.priority()
        );
    }

    public record TimeBasedTask(
            String taskId,
            String name,
            String taskType,
            ZonedDateTime scheduledTime,
            Map<String, Object> parameters,
            List<String> targetUsers,
            int priority
    ) {}

    public record ExpiryReminder(
            String itemId,
            String itemType,
            String itemName,
            ZonedDateTime expiryDate,
            int daysUntilExpiry,
            List<String> responsibleUsers
    ) {}

    public record PeriodicReport(
            String reportId,
            String reportName,
            String reportType,
            ReportPeriod period,
            List<String> targetUsers,
            List<String> dataSources
    ) {}

    public record ReportPeriod(ZonedDateTime start, ZonedDateTime end) {}

    public record ScheduledItem(
            String itemId,
            String taskName,
            String taskType,
            String cronExpression,
            int advanceMinutes,
            Map<String, Object> parameters,
            List<String> targetUsers,
            int priority
    ) {}

    public record ExpirableItem(
            String itemId,
            String itemType,
            String name,
            ZonedDateTime expiryDate,
            List<String> responsibleUsers
    ) {}

    public record TimePredictConfig(
            List<ScheduledItem> scheduledItems
    ) {
        public static TimePredictConfig empty() {
            return new TimePredictConfig(List.of());
        }
    }

    public record ExpiryCheckConfig(
            List<ExpirableItem> items,
            int[] reminderDays
    ) {
        public static ExpiryCheckConfig empty() {
            return new ExpiryCheckConfig(List.of(), new int[]{7, 3, 1});
        }
    }

    public record ReportConfig(
            List<ReportSchedule> schedules
    ) {
        public static ReportConfig empty() {
            return new ReportConfig(List.of());
        }
    }

    public record ReportSchedule(
            String reportId,
            String reportName,
            String reportType,
            ReportFrequency frequency,
            int generateHour,
            int generateMinute,
            DayOfWeek generateDayOfWeek,
            int generateDayOfMonth,
            List<String> targetUsers,
            List<String> dataSources
    ) {}

    public enum ReportFrequency {
        DAILY, WEEKLY, MONTHLY, QUARTERLY
    }
}
