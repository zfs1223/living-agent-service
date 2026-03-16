package com.livingagent.core.model.selector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class HardwareResourceMonitor {
    
    private static final Logger log = LoggerFactory.getLogger(HardwareResourceMonitor.class);
    
    private final OperatingSystemMXBean osBean;
    private final Runtime runtime;
    
    private final AtomicLong availableMemoryMB = new AtomicLong(0);
    private final AtomicReference<Double> cpuLoad = new AtomicReference<>(0.0);
    private final ScheduledExecutorService scheduler;
    
    private static final long TOTAL_MEMORY_MB = Runtime.getRuntime().maxMemory() / (1024 * 1024);
    
    public HardwareResourceMonitor() {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.runtime = Runtime.getRuntime();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hardware-resource-monitor");
            t.setDaemon(true);
            return t;
        });
        
        startMonitoring();
    }
    
    private void startMonitoring() {
        scheduler.scheduleAtFixedRate(this::updateMetrics, 0, 5, TimeUnit.SECONDS);
        log.info("Hardware resource monitor started");
    }
    
    private void updateMetrics() {
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        
        long usedMemory = totalMemory - freeMemory;
        long available = maxMemory - usedMemory;
        
        availableMemoryMB.set(available / (1024 * 1024));
        
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            cpuLoad.set(sunOsBean.getCpuLoad());
        } else {
            cpuLoad.set(0.5);
        }
    }
    
    public long getAvailableMemoryMB() {
        return availableMemoryMB.get();
    }
    
    public long getTotalMemoryMB() {
        return TOTAL_MEMORY_MB;
    }
    
    public long getUsedMemoryMB() {
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
    
    public int getAvailableProcessors() {
        return runtime.availableProcessors();
    }
    
    public double getCpuLoad() {
        double load = cpuLoad.get();
        return load < 0 ? 0.0 : load;
    }
    
    public double getMemoryUsagePercent() {
        long used = getUsedMemoryMB();
        long total = getTotalMemoryMB();
        return total > 0 ? (double) used / total : 0.0;
    }
    
    public ResourceSnapshot getSnapshot() {
        return new ResourceSnapshot(
            getAvailableMemoryMB(),
            getTotalMemoryMB(),
            getUsedMemoryMB(),
            getAvailableProcessors(),
            getCpuLoad(),
            getMemoryUsagePercent()
        );
    }
    
    public boolean hasSufficientResources(long requiredMemoryMB, int requiredProcessors, double maxCpuLoad) {
        return getAvailableMemoryMB() >= requiredMemoryMB &&
               getAvailableProcessors() >= requiredProcessors &&
               getCpuLoad() <= maxCpuLoad;
    }
    
    public void shutdown() {
        scheduler.shutdown();
        log.info("Hardware resource monitor stopped");
    }
    
    public static class ResourceSnapshot {
        public final long availableMemoryMB;
        public final long totalMemoryMB;
        public final long usedMemoryMB;
        public final int availableProcessors;
        public final double cpuLoad;
        public final double memoryUsagePercent;
        public final long timestamp;
        
        public ResourceSnapshot(long availableMemoryMB, long totalMemoryMB, long usedMemoryMB,
                               int availableProcessors, double cpuLoad, double memoryUsagePercent) {
            this.availableMemoryMB = availableMemoryMB;
            this.totalMemoryMB = totalMemoryMB;
            this.usedMemoryMB = usedMemoryMB;
            this.availableProcessors = availableProcessors;
            this.cpuLoad = cpuLoad;
            this.memoryUsagePercent = memoryUsagePercent;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return String.format(
                "ResourceSnapshot{memory=%d/%dMB (%.1f%%), cpu=%.1f%%, processors=%d}",
                usedMemoryMB, totalMemoryMB, memoryUsagePercent * 100, cpuLoad * 100, availableProcessors
            );
        }
    }
}
