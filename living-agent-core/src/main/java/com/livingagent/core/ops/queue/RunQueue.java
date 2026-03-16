package com.livingagent.core.ops.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RunQueue {

    private static final Logger log = LoggerFactory.getLogger(RunQueue.class);

    private final PriorityBlockingQueue<QueueItem> priorityQueue;
    private final Map<String, QueueItem> runningItems = new ConcurrentHashMap<>();
    private final Map<String, QueueItem> completedItems = new ConcurrentHashMap<>();
    private final Map<String, List<QueueItem>> employeeQueues = new ConcurrentHashMap<>();
    
    private final AtomicInteger runningCount = new AtomicInteger(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalWaitTime = new AtomicLong(0);
    
    private final int maxConcurrent;
    private final ScheduledExecutorService scheduler;
    
    private final List<QueueListener> listeners = new CopyOnWriteArrayList<>();

    public RunQueue() {
        this.maxConcurrent = Runtime.getRuntime().availableProcessors() * 2;
        this.priorityQueue = new PriorityBlockingQueue<>(100, 
            Comparator.comparingInt(QueueItem::priority).reversed()
                .thenComparing(QueueItem::submittedAt));
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        startQueueProcessor();
    }

    public RunQueue(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
        this.priorityQueue = new PriorityBlockingQueue<>(100,
            Comparator.comparingInt(QueueItem::priority).reversed()
                .thenComparing(QueueItem::submittedAt));
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        startQueueProcessor();
    }

    public QueueItem submit(String itemId, String itemType, String description, 
                           int priority, String assignedEmployee, Map<String, Object> payload) {
        QueueItem item = new QueueItem(
            itemId,
            itemType,
            description,
            priority,
            assignedEmployee,
            payload,
            QueueStatus.PENDING,
            Instant.now(),
            null,
            null,
            null,
            0
        );
        
        if (assignedEmployee != null) {
            employeeQueues.computeIfAbsent(assignedEmployee, k -> new CopyOnWriteArrayList<>()).add(item);
        }
        
        priorityQueue.offer(item);
        
        notifyListeners(QueueEvent.submitted(item));
        log.info("Submitted item {} to queue (priority={}, employee={})", itemId, priority, assignedEmployee);
        
        return item;
    }

    public Optional<QueueItem> poll() {
        if (runningCount.get() >= maxConcurrent) {
            return Optional.empty();
        }
        
        QueueItem item = priorityQueue.poll();
        if (item == null) {
            return Optional.empty();
        }
        
        if (runningCount.incrementAndGet() > maxConcurrent) {
            runningCount.decrementAndGet();
            priorityQueue.offer(item);
            return Optional.empty();
        }
        
        Instant now = Instant.now();
        long waitTimeMs = now.toEpochMilli() - item.submittedAt().toEpochMilli();
        
        QueueItem running = new QueueItem(
            item.itemId(),
            item.itemType(),
            item.description(),
            item.priority(),
            item.assignedEmployee(),
            item.payload(),
            QueueStatus.RUNNING,
            item.submittedAt(),
            now,
            null,
            null,
            (int) waitTimeMs
        );
        
        runningItems.put(item.itemId(), running);
        totalWaitTime.addAndGet(waitTimeMs);
        
        notifyListeners(QueueEvent.started(running));
        log.info("Started item {} (waitTime={}ms)", item.itemId(), waitTimeMs);
        
        return Optional.of(running);
    }

    public QueueItem complete(String itemId, boolean success, String result, Map<String, Object> output) {
        QueueItem running = runningItems.remove(itemId);
        if (running == null) {
            throw new IllegalArgumentException("Item not found in running queue: " + itemId);
        }
        
        runningCount.decrementAndGet();
        totalProcessed.incrementAndGet();
        
        Instant now = Instant.now();
        long executionTimeMs = now.toEpochMilli() - running.startedAt().toEpochMilli();
        
        QueueItem completed = new QueueItem(
            running.itemId(),
            running.itemType(),
            running.description(),
            running.priority(),
            running.assignedEmployee(),
            running.payload(),
            success ? QueueStatus.COMPLETED : QueueStatus.FAILED,
            running.submittedAt(),
            running.startedAt(),
            now,
            result,
            running.waitTimeMs()
        );
        
        completedItems.put(itemId, completed);
        
        if (running.assignedEmployee() != null) {
            List<QueueItem> empQueue = employeeQueues.get(running.assignedEmployee());
            if (empQueue != null) {
                empQueue.removeIf(i -> i.itemId().equals(itemId));
            }
        }
        
        notifyListeners(QueueEvent.completed(completed, executionTimeMs));
        log.info("Completed item {} (success={}, executionTime={}ms)", itemId, success, executionTimeMs);
        
        return completed;
    }

    public QueueItem cancel(String itemId, String reason) {
        QueueItem pending = priorityQueue.stream()
            .filter(i -> i.itemId().equals(itemId))
            .findFirst()
            .orElse(null);
            
        if (pending != null) {
            priorityQueue.remove(pending);
            
            QueueItem cancelled = new QueueItem(
                pending.itemId(),
                pending.itemType(),
                pending.description(),
                pending.priority(),
                pending.assignedEmployee(),
                pending.payload(),
                QueueStatus.CANCELLED,
                pending.submittedAt(),
                null,
                Instant.now(),
                "Cancelled: " + reason,
                pending.waitTimeMs()
            );
            
            notifyListeners(QueueEvent.cancelled(cancelled, reason));
            log.info("Cancelled item {} - reason: {}", itemId, reason);
            return cancelled;
        }
        
        QueueItem running = runningItems.get(itemId);
        if (running != null) {
            return complete(itemId, false, "Cancelled: " + reason, Map.of());
        }
        
        throw new IllegalArgumentException("Item not found: " + itemId);
    }

    public void reprioritize(String itemId, int newPriority) {
        Optional<QueueItem> found = priorityQueue.stream()
            .filter(i -> i.itemId().equals(itemId))
            .findFirst();
            
        if (found.isPresent()) {
            QueueItem old = found.get();
            priorityQueue.remove(old);
            
            QueueItem updated = new QueueItem(
                old.itemId(),
                old.itemType(),
                old.description(),
                newPriority,
                old.assignedEmployee(),
                old.payload(),
                old.status(),
                old.submittedAt(),
                old.startedAt(),
                old.completedAt(),
                old.result(),
                old.waitTimeMs()
            );
            
            priorityQueue.offer(updated);
            log.info("Reprioritized item {} from {} to {}", itemId, old.priority(), newPriority);
        }
    }

    public List<QueueItem> getPendingItems() {
        return new ArrayList<>(priorityQueue);
    }

    public List<QueueItem> getPendingItems(int limit) {
        return priorityQueue.stream()
            .limit(limit)
            .toList();
    }

    public List<QueueItem> getRunningItems() {
        return new ArrayList<>(runningItems.values());
    }

    public List<QueueItem> getCompletedItems(int limit) {
        return completedItems.values().stream()
            .sorted(Comparator.comparing(QueueItem::completedAt).reversed())
            .limit(limit)
            .toList();
    }

    public List<QueueItem> getEmployeeQueue(String employeeId) {
        return new ArrayList<>(employeeQueues.getOrDefault(employeeId, List.of()));
    }

    public Optional<QueueItem> getItem(String itemId) {
        return Optional.ofNullable(runningItems.get(itemId))
            .or(() -> Optional.ofNullable(completedItems.get(itemId)))
            .or(() -> priorityQueue.stream().filter(i -> i.itemId().equals(itemId)).findFirst());
    }

    public int getPendingCount() {
        return priorityQueue.size();
    }

    public int getRunningCount() {
        return runningCount.get();
    }

    public int getAvailableSlots() {
        return Math.max(0, maxConcurrent - runningCount.get());
    }

    public QueueStatistics getStatistics() {
        double avgWaitTime = totalProcessed.get() > 0 
            ? (double) totalWaitTime.get() / totalProcessed.get() 
            : 0;
            
        return new QueueStatistics(
            priorityQueue.size(),
            runningCount.get(),
            completedItems.size(),
            maxConcurrent,
            totalProcessed.get(),
            avgWaitTime
        );
    }

    public void addListener(QueueListener listener) {
        listeners.add(listener);
    }

    public void removeListener(QueueListener listener) {
        listeners.remove(listener);
    }

    public void clearCompleted() {
        int cleared = completedItems.size();
        completedItems.clear();
        log.info("Cleared {} completed items from queue", cleared);
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("RunQueue shutdown complete");
    }

    private void startQueueProcessor() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                processQueue();
            } catch (Exception e) {
                log.error("Error processing queue", e);
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    private void processQueue() {
        while (runningCount.get() < maxConcurrent && !priorityQueue.isEmpty()) {
            Optional<QueueItem> item = poll();
            if (item.isEmpty()) {
                break;
            }
        }
    }

    private void notifyListeners(QueueEvent event) {
        for (QueueListener listener : listeners) {
            try {
                listener.onQueueEvent(event);
            } catch (Exception e) {
                log.error("Error notifying listener", e);
            }
        }
    }

    public record QueueItem(
        String itemId,
        String itemType,
        String description,
        int priority,
        String assignedEmployee,
        Map<String, Object> payload,
        QueueStatus status,
        Instant submittedAt,
        Instant startedAt,
        Instant completedAt,
        String result,
        int waitTimeMs
    ) {
        public long executionTimeMs() {
            if (startedAt != null && completedAt != null) {
                return completedAt.toEpochMilli() - startedAt.toEpochMilli();
            }
            return 0;
        }
        
        public long totalTimeMs() {
            if (submittedAt != null && completedAt != null) {
                return completedAt.toEpochMilli() - submittedAt.toEpochMilli();
            }
            return 0;
        }
    }

    public record QueueEvent(
        QueueEventType eventType,
        QueueItem item,
        String message,
        long durationMs
    ) {
        public static QueueEvent submitted(QueueItem item) {
            return new QueueEvent(QueueEventType.SUBMITTED, item, null, 0);
        }
        
        public static QueueEvent started(QueueItem item) {
            return new QueueEvent(QueueEventType.STARTED, item, null, 0);
        }
        
        public static QueueEvent completed(QueueItem item, long durationMs) {
            return new QueueEvent(QueueEventType.COMPLETED, item, null, durationMs);
        }
        
        public static QueueEvent cancelled(QueueItem item, String reason) {
            return new QueueEvent(QueueEventType.CANCELLED, item, reason, 0);
        }
    }

    public record QueueStatistics(
        int pendingCount,
        int runningCount,
        int completedCount,
        int maxConcurrent,
        long totalProcessed,
        double avgWaitTimeMs
    ) {
        public double utilizationPercent() {
            return maxConcurrent > 0 ? (double) runningCount / maxConcurrent * 100 : 0;
        }
    }

    public interface QueueListener {
        void onQueueEvent(QueueEvent event);
    }

    public enum QueueStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        TIMEOUT
    }

    public enum QueueEventType {
        SUBMITTED,
        STARTED,
        COMPLETED,
        FAILED,
        CANCELLED,
        TIMEOUT
    }
}
