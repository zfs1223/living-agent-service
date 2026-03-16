package com.livingagent.core.proactive.digest;

import com.livingagent.core.proactive.alert.AlertNotifier;
import com.livingagent.core.proactive.alert.AlertNotifier.Alert;
import com.livingagent.core.proactive.scheduler.ProactiveTaskScheduler;
import com.livingagent.core.proactive.scheduler.ProactiveTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DailyDigestGenerator {

    private static final Logger log = LoggerFactory.getLogger(DailyDigestGenerator.class);

    private final ProactiveTaskScheduler taskScheduler;
    private final List<AlertNotifier> notifiers;
    private final ZoneId timezone;
    
    private final Map<String, DigestConfig> userConfigs = new ConcurrentHashMap<>();
    private final Map<String, DigestSource> digestSources = new ConcurrentHashMap<>();

    public DailyDigestGenerator(ProactiveTaskScheduler taskScheduler, List<AlertNotifier> notifiers) {
        this(taskScheduler, notifiers, ZoneId.of("Asia/Shanghai"));
    }

    public DailyDigestGenerator(ProactiveTaskScheduler taskScheduler, List<AlertNotifier> notifiers, ZoneId timezone) {
        this.taskScheduler = taskScheduler;
        this.notifiers = notifiers != null ? new ArrayList<>(notifiers) : new ArrayList<>();
        this.timezone = timezone;
        
        registerDefaultSources();
    }

    private void registerDefaultSources() {
        registerSource(new DigestSource(
                "news",
                "新闻摘要",
                SourceType.EXTERNAL,
                true,
                Map.of("maxItems", 10, "categories", List.of("tech", "business"))
        ));
        
        registerSource(new DigestSource(
                "tasks",
                "待办事项",
                SourceType.INTERNAL,
                true,
                Map.of("includeCompleted", false)
        ));
        
        registerSource(new DigestSource(
                "calendar",
                "日程安排",
                SourceType.INTERNAL,
                true,
                Map.of("lookAheadHours", 24)
        ));
        
        registerSource(new DigestSource(
                "emails",
                "邮件摘要",
                SourceType.EXTERNAL,
                false,
                Map.of("maxItems", 5, "priorityOnly", true)
        ));
        
        registerSource(new DigestSource(
                "reports",
                "报告提醒",
                SourceType.INTERNAL,
                true,
                Map.of("includeOverdue", true)
        ));
    }

    public void registerSource(DigestSource source) {
        digestSources.put(source.sourceId(), source);
        log.info("Registered digest source: {}", source.name());
    }

    public void configureUserDigest(String userId, DigestConfig config) {
        userConfigs.put(userId, config);
        
        scheduleDigestTask(userId, config);
        
        log.info("Configured daily digest for user: {} at {}", userId, config.scheduledTime());
    }

    private void scheduleDigestTask(String userId, DigestConfig config) {
        if (!config.enabled()) {
            return;
        }

        LocalTime scheduledTime = config.scheduledTime();
        int hour = scheduledTime.getHour();
        int minute = scheduledTime.getMinute();
        
        String cronExpr = String.format("%d %d * * 1-5", minute, hour);

        ProactiveTask task = ProactiveTask.create(
                "每日摘要 - " + userId,
                ProactiveTask.TaskType.REPORT,
                ProactiveTask.TaskTrigger.SCHEDULED
        )
        .withParameters(Map.of(
                "digestType", "daily",
                "userId", userId,
                "cronExpression", cronExpr,
                "channels", config.channels()
        ));

        taskScheduler.scheduleTask(task);
    }

    public DigestContent generateDigest(String userId) {
        log.info("Generating daily digest for user: {}", userId);

        DigestConfig config = userConfigs.getOrDefault(userId, DigestConfig.defaultConfig());
        
        Map<String, DigestSection> sections = new LinkedHashMap<>();

        for (DigestSource source : digestSources.values()) {
            if (config.enabledSources().contains(source.sourceId()) || 
                    (config.enabledSources().isEmpty() && source.enabledByDefault())) {
                
                DigestSection section = collectSection(userId, source);
                if (section != null && !section.items().isEmpty()) {
                    sections.put(source.sourceId(), section);
                }
            }
        }

        String summary = generateSummary(sections);
        
        DigestContent content = new DigestContent(
                "digest_" + System.currentTimeMillis(),
                userId,
                LocalDate.now(timezone),
                sections,
                summary,
                Instant.now()
        );

        if (config.autoSend()) {
            sendDigest(userId, content, config.channels());
        }

        return content;
    }

    private DigestSection collectSection(String userId, DigestSource source) {
        return switch (source.sourceId()) {
            case "news" -> collectNewsSection(source);
            case "tasks" -> collectTasksSection(userId, source);
            case "calendar" -> collectCalendarSection(userId, source);
            case "emails" -> collectEmailsSection(source);
            case "reports" -> collectReportsSection(userId, source);
            default -> null;
        };
    }

    private DigestSection collectNewsSection(DigestSource source) {
        List<DigestItem> items = new ArrayList<>();
        
        items.add(new DigestItem(
                "news_1",
                "AI领域重大突破：新模型性能提升50%",
                "https://example.com/news/1",
                Instant.now().minus(2, ChronoUnit.HOURS),
                Map.of("category", "tech", "priority", "high")
        ));
        
        items.add(new DigestItem(
                "news_2",
                "企业数字化转型趋势报告发布",
                "https://example.com/news/2",
                Instant.now().minus(5, ChronoUnit.HOURS),
                Map.of("category", "business", "priority", "medium")
        ));
        
        items.add(new DigestItem(
                "news_3",
                "开源社区新项目：高效数据处理框架",
                "https://example.com/news/3",
                Instant.now().minus(8, ChronoUnit.HOURS),
                Map.of("category", "tech", "priority", "medium")
        ));

        return new DigestSection("新闻摘要", items, Instant.now());
    }

    private DigestSection collectTasksSection(String userId, DigestSource source) {
        List<DigestItem> items = new ArrayList<>();
        
        items.add(new DigestItem(
                "task_1",
                "完成项目周报",
                null,
                Instant.now().plus(4, ChronoUnit.HOURS),
                Map.of("priority", "high", "due", "今天 17:00")
        ));
        
        items.add(new DigestItem(
                "task_2",
                "代码审查 - PR #123",
                null,
                Instant.now().plus(2, ChronoUnit.HOURS),
                Map.of("priority", "medium", "due", "今天 15:00")
        ));
        
        items.add(new DigestItem(
                "task_3",
                "团队会议准备",
                null,
                Instant.now().plus(6, ChronoUnit.HOURS),
                Map.of("priority", "low", "due", "今天 19:00")
        ));

        return new DigestSection("今日待办", items, Instant.now());
    }

    private DigestSection collectCalendarSection(String userId, DigestSource source) {
        List<DigestItem> items = new ArrayList<>();
        
        items.add(new DigestItem(
                "event_1",
                "团队周会",
                null,
                Instant.now().plus(3, ChronoUnit.HOURS),
                Map.of("time", "14:00-15:00", "location", "会议室A")
        ));
        
        items.add(new DigestItem(
                "event_2",
                "产品评审会",
                null,
                Instant.now().plus(7, ChronoUnit.HOURS),
                Map.of("time", "18:00-19:00", "location", "线上会议")
        ));

        return new DigestSection("今日日程", items, Instant.now());
    }

    private DigestSection collectEmailsSection(DigestSource source) {
        List<DigestItem> items = new ArrayList<>();
        
        items.add(new DigestItem(
                "email_1",
                "[重要] 项目进度更新 - 需要确认",
                null,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Map.of("from", "张三", "priority", "high")
        ));
        
        items.add(new DigestItem(
                "email_2",
                "本周技术分享会议题征集",
                null,
                Instant.now().minus(3, ChronoUnit.HOURS),
                Map.of("from", "李四", "priority", "medium")
        ));

        return new DigestSection("重要邮件", items, Instant.now());
    }

    private DigestSection collectReportsSection(String userId, DigestSource source) {
        List<DigestItem> items = new ArrayList<>();
        
        items.add(new DigestItem(
                "report_1",
                "技术部周报 - 本周五截止",
                null,
                Instant.now().plus(2, ChronoUnit.DAYS),
                Map.of("type", "weekly", "deadline", "周五 17:00")
        ));

        return new DigestSection("报告提醒", items, Instant.now());
    }

    private String generateSummary(Map<String, DigestSection> sections) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("## 📋 每日摘要\n");
        summary.append("**日期**: ").append(LocalDate.now(timezone).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE"))).append("\n\n");
        
        for (Map.Entry<String, DigestSection> entry : sections.entrySet()) {
            DigestSection section = entry.getValue();
            summary.append("### ").append(section.title()).append("\n");
            
            for (DigestItem item : section.items()) {
                summary.append("- ").append(item.title());
                if (item.metadata() != null && item.metadata().containsKey("priority")) {
                    String priority = (String) item.metadata().get("priority");
                    summary.append(" [").append(priority.toUpperCase()).append("]");
                }
                summary.append("\n");
            }
            summary.append("\n");
        }
        
        summary.append("---\n");
        summary.append("*祝您今天工作顺利！*");
        
        return summary.toString();
    }

    private void sendDigest(String userId, DigestContent content, List<String> channels) {
        Alert alert = Alert.info(
                "每日摘要 - " + content.date().format(DateTimeFormatter.ofPattern("MM月dd日")),
                content.summary()
        ).withTargetUsers(List.of(userId));

        for (AlertNotifier notifier : notifiers) {
            if (notifier.isAvailable() && 
                    (channels.isEmpty() || channels.contains(notifier.getChannelName()))) {
                try {
                    notifier.send(alert);
                    log.info("Digest sent to user {} via {}", userId, notifier.getChannelName());
                } catch (Exception e) {
                    log.warn("Failed to send digest via {}: {}", notifier.getChannelName(), e.getMessage());
                }
            }
        }
    }

    public DigestConfig getUserConfig(String userId) {
        return userConfigs.getOrDefault(userId, DigestConfig.defaultConfig());
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("configuredUsers", userConfigs.size());
        stats.put("availableSources", digestSources.keySet());
        
        Map<String, Long> sourceUsage = new HashMap<>();
        userConfigs.values().forEach(config -> {
            for (String sourceId : config.enabledSources()) {
                sourceUsage.merge(sourceId, 1L, Long::sum);
            }
        });
        stats.put("sourceUsage", sourceUsage);
        
        return stats;
    }

    public record DigestConfig(
            String userId,
            LocalTime scheduledTime,
            boolean enabled,
            boolean autoSend,
            List<String> enabledSources,
            List<String> channels
    ) {
        public static DigestConfig defaultConfig() {
            return new DigestConfig(
                    "default",
                    LocalTime.of(8, 0),
                    true,
                    true,
                    List.of(),
                    List.of("dingtalk", "feishu")
            );
        }
        
        public static DigestConfig create(String userId, int hour, int minute) {
            return new DigestConfig(
                    userId,
                    LocalTime.of(hour, minute),
                    true,
                    true,
                    List.of(),
                    List.of("dingtalk")
            );
        }
        
        public DigestConfig withSources(String... sources) {
            return new DigestConfig(userId, scheduledTime, enabled, autoSend, List.of(sources), channels);
        }
        
        public DigestConfig withChannels(String... channels) {
            return new DigestConfig(userId, scheduledTime, enabled, autoSend, enabledSources, List.of(channels));
        }
    }

    public record DigestSource(
            String sourceId,
            String name,
            SourceType type,
            boolean enabledByDefault,
            Map<String, Object> config
    ) {}

    public record DigestSection(
            String title,
            List<DigestItem> items,
            Instant collectedAt
    ) {}

    public record DigestItem(
            String itemId,
            String title,
            String url,
            Instant timestamp,
            Map<String, Object> metadata
    ) {}

    public record DigestContent(
            String digestId,
            String userId,
            LocalDate date,
            Map<String, DigestSection> sections,
            String summary,
            Instant generatedAt
    ) {}

    public enum SourceType {
        INTERNAL,
        EXTERNAL,
        HYBRID
    }
}
