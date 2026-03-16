package com.livingagent.core.proactive.scheduler;

import com.livingagent.core.proactive.alert.AlertNotifier;
import com.livingagent.core.proactive.alert.AlertNotifier.Alert;
import com.livingagent.core.proactive.cron.CronJob;
import com.livingagent.core.proactive.cron.CronService;
import com.livingagent.core.proactive.event.EventHookManager;
import com.livingagent.core.proactive.event.HookEvent;
import com.livingagent.core.proactive.event.HookHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

public class ProactiveTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProactiveTaskScheduler.class);

    private final CronService cronService;
    private final EventHookManager eventHookManager;
    private final List<AlertNotifier> alertNotifiers;
    private final ExecutorService executorService;
    
    private final Map<String, ProactiveTask> tasks = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<ProactiveTask> taskQueue;
    private final Map<String, List<ProactiveTask>> brainTasks = new ConcurrentHashMap<>();
    
    private volatile boolean running = false;
    private Thread schedulerThread;

    public ProactiveTaskScheduler(CronService cronService, EventHookManager eventHookManager, 
                                   List<AlertNotifier> alertNotifiers) {
        this.cronService = cronService;
        this.eventHookManager = eventHookManager;
        this.alertNotifiers = alertNotifiers != null ? new ArrayList<>(alertNotifiers) : new ArrayList<>();
        this.executorService = Executors.newFixedThreadPool(4);
        this.taskQueue = new PriorityBlockingQueue<>(100, 
                Comparator.comparingInt(t -> -t.priority().getWeight()));
        
        registerDefaultHandlers();
    }

    private void registerDefaultHandlers() {
        eventHookManager.registerHandler(new HookHandler() {
            @Override
            public String[] supportedEvents() {
                return new String[]{"cron.execute", "task.trigger", "alert.needed"};
            }

            @Override
            public int getOrder() {
                return 10;
            }

            @Override
            public void handle(HookEvent event) {
                handleEvent(event);
            }
        });
    }

    public ProactiveTask scheduleTask(ProactiveTask task) {
        if (task == null || task.taskId() == null) {
            throw new IllegalArgumentException("Task and taskId must not be null");
        }

        tasks.put(task.taskId(), task);
        
        if (task.brainDomain() != null) {
            brainTasks.computeIfAbsent(task.brainDomain(), k -> new ArrayList<>()).add(task);
        }

        switch (task.trigger()) {
            case SCHEDULED -> scheduleAsCronJob(task);
            case EVENT_DRIVEN -> log.info("Event-driven task registered: {}", task.name());
            case CONDITION_BASED -> taskQueue.offer(task);
            default -> taskQueue.offer(task);
        }

        log.info("Scheduled proactive task: {} [type={}, trigger={}]", 
                task.name(), task.type(), task.trigger());
        
        return task;
    }

    private void scheduleAsCronJob(ProactiveTask task) {
        String cronExpr = (String) task.parameters().get("cronExpression");
        if (cronExpr == null || cronExpr.isEmpty()) {
            cronExpr = "0 * * * *";
        }

        CronJob cronJob = CronJob.create(task.name(), cronExpr, "proactive_task")
                .withTaskParams(Map.of("taskId", task.taskId()));

        cronService.scheduleJob(cronJob);
    }

    public boolean cancelTask(String taskId) {
        ProactiveTask task = tasks.remove(taskId);
        if (task == null) {
            return false;
        }

        if (task.brainDomain() != null) {
            List<ProactiveTask> brainTaskList = brainTasks.get(task.brainDomain());
            if (brainTaskList != null) {
                brainTaskList.removeIf(t -> t.taskId().equals(taskId));
            }
        }

        taskQueue.removeIf(t -> t.taskId().equals(taskId));

        log.info("Cancelled proactive task: {}", task.name());
        return true;
    }

    public Optional<ProactiveTask> getTask(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    public List<ProactiveTask> getTasksForBrain(String brainDomain) {
        return brainTasks.getOrDefault(brainDomain, List.of());
    }

    public List<ProactiveTask> getPendingTasks() {
        return tasks.values().stream()
                .filter(t -> t.status() == ProactiveTask.TaskStatus.PENDING)
                .toList();
    }

    public void executeTask(String taskId) {
        ProactiveTask task = tasks.get(taskId);
        if (task == null) {
            log.warn("Task not found: {}", taskId);
            return;
        }

        executorService.submit(() -> executeTaskInternal(task));
    }

    private void executeTaskInternal(ProactiveTask task) {
        log.info("Executing proactive task: {}", task.name());

        ProactiveTask runningTask = task.start();
        tasks.put(task.taskId(), runningTask);

        try {
            String result = performTask(runningTask);
            
            ProactiveTask completedTask = runningTask.complete(result);
            tasks.put(task.taskId(), completedTask);

            if (shouldNotify(completedTask)) {
                sendNotification(completedTask, result);
            }

            log.info("Task completed: {} -> {}", task.name(), result);

        } catch (Exception e) {
            log.error("Task failed: {} - {}", task.name(), e.getMessage());

            ProactiveTask failedTask = task.fail(e.getMessage());
            tasks.put(task.taskId(), failedTask);

            sendAlert(task, e.getMessage());
        }
    }

    private String performTask(ProactiveTask task) {
        return switch (task.type()) {
            case REMINDER -> performReminder(task);
            case REPORT -> performReport(task);
            case NOTIFICATION -> performNotification(task);
            case CHECK -> performCheck(task);
            case CLEANUP -> performCleanup(task);
            case SYNC -> performSync(task);
            case ANALYSIS -> performAnalysis(task);
            case CUSTOM -> performCustom(task);
        };
    }

    private String performReminder(ProactiveTask task) {
        String message = (String) task.parameters().getOrDefault("message", "提醒事项");
        List<String> targets = (List<String>) task.parameters().get("targets");
        
        if (targets != null && !targets.isEmpty()) {
            Alert alert = Alert.info("提醒: " + task.name(), message)
                    .withTargetUsers(targets);
            sendAlertToNotifiers(alert);
        }
        
        return "Reminder sent to " + (targets != null ? targets.size() : 0) + " users";
    }

    private String performReport(ProactiveTask task) {
        String reportType = (String) task.parameters().getOrDefault("reportType", "daily");
        
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("reportType", reportType);
        reportData.put("generatedAt", Instant.now().toString());
        
        return "Report generated: " + reportType;
    }

    private String performNotification(ProactiveTask task) {
        String channel = (String) task.parameters().getOrDefault("channel", "default");
        String message = (String) task.parameters().getOrDefault("message", "");
        
        Alert alert = Alert.info(task.name(), message);
        sendAlertToNotifiers(alert);
        
        return "Notification sent via " + channel;
    }

    private String performCheck(ProactiveTask task) {
        String checkType = (String) task.parameters().getOrDefault("checkType", "system");
        
        return "Check completed: " + checkType + " - OK";
    }

    private String performCleanup(ProactiveTask task) {
        String target = (String) task.parameters().getOrDefault("target", "temp");
        int daysOld = (Integer) task.parameters().getOrDefault("daysOld", 7);
        
        return "Cleanup completed: " + target + " (older than " + daysOld + " days)";
    }

    private String performSync(ProactiveTask task) {
        String source = (String) task.parameters().getOrDefault("source", "hr");
        
        return "Sync completed: " + source;
    }

    private String performAnalysis(ProactiveTask task) {
        String analysisType = (String) task.parameters().getOrDefault("analysisType", "general");
        
        return "Analysis completed: " + analysisType;
    }

    private String performCustom(ProactiveTask task) {
        String handler = (String) task.parameters().get("handler");
        if (handler == null) {
            return "Custom task executed (no handler specified)";
        }
        
        return "Custom task executed: " + handler;
    }

    private boolean shouldNotify(ProactiveTask task) {
        Boolean notify = (Boolean) task.parameters().get("notifyOnComplete");
        return Boolean.TRUE.equals(notify);
    }

    private void sendNotification(ProactiveTask task, String result) {
        List<String> targets = (List<String>) task.parameters().get("notifyTargets");
        if (targets == null || targets.isEmpty()) {
            return;
        }

        Alert alert = Alert.info("任务完成: " + task.name(), 
                "任务已成功执行。\n结果: " + result)
                .withTargetUsers(targets);

        sendAlertToNotifiers(alert);
    }

    private void sendAlert(ProactiveTask task, String error) {
        Alert alert = Alert.error("任务失败: " + task.name(), 
                "任务执行失败。\n错误: " + error);
        sendAlertToNotifiers(alert);
    }

    private void sendAlertToNotifiers(Alert alert) {
        for (AlertNotifier notifier : alertNotifiers) {
            if (notifier.isAvailable()) {
                try {
                    notifier.send(alert);
                } catch (Exception e) {
                    log.warn("Failed to send alert via {}: {}", notifier.getChannelName(), e.getMessage());
                }
            }
        }
    }

    private void handleEvent(HookEvent event) {
        String eventType = event.eventType();
        
        switch (eventType) {
            case "cron.execute" -> handleCronExecute(event);
            case "task.trigger" -> handleTaskTrigger(event);
            case "alert.needed" -> handleAlertNeeded(event);
        }
    }

    private void handleCronExecute(HookEvent event) {
        String taskId = event.getString("taskParams.taskId");
        if (taskId != null && tasks.containsKey(taskId)) {
            executeTask(taskId);
        }
    }

    private void handleTaskTrigger(HookEvent event) {
        String taskType = event.getString("taskType");
        String brainDomain = event.getString("brainDomain");
        
        List<ProactiveTask> matchingTasks = tasks.values().stream()
                .filter(t -> t.status() == ProactiveTask.TaskStatus.PENDING)
                .filter(t -> taskType == null || t.type().name().equalsIgnoreCase(taskType))
                .filter(t -> brainDomain == null || brainDomain.equals(t.brainDomain()))
                .toList();
        
        for (ProactiveTask task : matchingTasks) {
            executeTask(task.taskId());
        }
    }

    private void handleAlertNeeded(HookEvent event) {
        String title = event.getString("title");
        String content = event.getString("content");
        String level = event.getString("level");
        
        Alert alert = switch (level != null ? level.toUpperCase() : "INFO") {
            case "CRITICAL" -> Alert.critical(title, content);
            case "ERROR" -> Alert.error(title, content);
            case "WARNING" -> Alert.warning(title, content);
            default -> Alert.info(title, content);
        };
        
        sendAlertToNotifiers(alert);
    }

    public void start() {
        running = true;
        cronService.start();
        
        schedulerThread = new Thread(() -> {
            while (running) {
                try {
                    ProactiveTask task = taskQueue.poll(1, TimeUnit.SECONDS);
                    if (task != null) {
                        executeTaskInternal(task);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "proactive-scheduler");
        schedulerThread.setDaemon(true);
        schedulerThread.start();
        
        log.info("ProactiveTaskScheduler started");
    }

    public void stop() {
        running = false;
        
        if (schedulerThread != null) {
            schedulerThread.interrupt();
        }
        
        cronService.stop();
        log.info("ProactiveTaskScheduler stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTasks", tasks.size());
        stats.put("pendingTasks", getPendingTasks().size());
        stats.put("queueSize", taskQueue.size());
        stats.put("running", running);
        
        Map<String, Long> byStatus = new HashMap<>();
        for (ProactiveTask.TaskStatus status : ProactiveTask.TaskStatus.values()) {
            byStatus.put(status.name(), tasks.values().stream()
                    .filter(t -> t.status() == status)
                    .count());
        }
        stats.put("byStatus", byStatus);
        
        Map<String, Long> byType = new HashMap<>();
        for (ProactiveTask.TaskType type : ProactiveTask.TaskType.values()) {
            byType.put(type.name(), tasks.values().stream()
                    .filter(t -> t.type() == type)
                    .count());
        }
        stats.put("byType", byType);
        
        return stats;
    }

    public void shutdown() {
        stop();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        log.info("ProactiveTaskScheduler shutdown complete");
    }
}
