package com.livingagent.core.neuron.impl;

import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.neuron.*;
import com.livingagent.core.neuron.NeuronState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class SensorNeuron extends AbstractNeuron {

    private static final Logger log = LoggerFactory.getLogger(SensorNeuron.class);

    public static final String ID = "neuron://sensor/system-sensor/001";
    public static final String INPUT_CHANNEL = "channel://sensor/events";
    public static final String OUTPUT_CHANNEL = "channel://sensor/alerts";

    private final Map<String, SensorConfig> sensors = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, SensorReading> latestReadings = new ConcurrentHashMap<>();
    private final List<AlertRule> alertRules = new CopyOnWriteArrayList<>();

    public SensorNeuron(List<com.livingagent.core.tool.Tool> tools) {
        super(
            ID,
            "SensorNeuron",
            "系统传感器神经元 - 监控CPU/内存/磁盘并触发告警",
            List.of(INPUT_CHANNEL),
            List.of(OUTPUT_CHANNEL),
            tools
        );
        initializeDefaultSensors();
        initializeDefaultAlertRules();
    }

    private void initializeDefaultSensors() {
        addSensor(new SensorConfig(
            "system.cpu",
            "CPU使用率监控",
            () -> getCpuUsage(),
            60,
            "percent"
        ));
        
        addSensor(new SensorConfig(
            "system.memory",
            "内存使用率监控",
            () -> getMemoryUsage(),
            60,
            "percent"
        ));
        
        addSensor(new SensorConfig(
            "system.disk",
            "磁盘使用率监控",
            () -> getDiskUsage(),
            300,
            "percent"
        ));
        
        addSensor(new SensorConfig(
            "system.uptime",
            "系统运行时间",
            () -> getSystemUptime(),
            60,
            "seconds"
        ));
        
        addSensor(new SensorConfig(
            "neuron.count",
            "活跃神经元数量",
            () -> getActiveNeuronCount(),
            30,
            "count"
        ));
        
        addSensor(new SensorConfig(
            "channel.count",
            "活跃通道数量",
            () -> getActiveChannelCount(),
            30,
            "count"
        ));
    }

    private void initializeDefaultAlertRules() {
        addAlertRule(new AlertRule(
            "cpu_high",
            "system.cpu",
            AlertCondition.GREATER_THAN,
            80.0,
            AlertSeverity.WARNING,
            "CPU使用率过高"
        ));
        
        addAlertRule(new AlertRule(
            "cpu_critical",
            "system.cpu",
            AlertCondition.GREATER_THAN,
            95.0,
            AlertSeverity.CRITICAL,
            "CPU使用率严重过高"
        ));
        
        addAlertRule(new AlertRule(
            "memory_high",
            "system.memory",
            AlertCondition.GREATER_THAN,
            80.0,
            AlertSeverity.WARNING,
            "内存使用率过高"
        ));
        
        addAlertRule(new AlertRule(
            "memory_critical",
            "system.memory",
            AlertCondition.GREATER_THAN,
            95.0,
            AlertSeverity.CRITICAL,
            "内存使用率严重过高"
        ));
    }

    @Override
    protected void doStart(NeuronContext context) {
        log.info("SensorNeuron starting with {} sensors", sensors.size());
        
        sensors.values().forEach(sensor -> {
            ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> collectReading(sensor),
                0,
                sensor.intervalSeconds(),
                TimeUnit.SECONDS
            );
            log.debug("Scheduled sensor: {} (interval: {}s)", sensor.id(), sensor.intervalSeconds());
        });
        
        log.info("SensorNeuron started successfully");
    }

    @Override
    protected void doStop() {
        log.info("SensorNeuron stopping...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("SensorNeuron stopped");
    }

    @Override
    protected void doProcessMessage(ChannelMessage message) {
        log.debug("SensorNeuron processing message: {}", message.getId());
        
        Object payload = message.getPayload();
        if (payload instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload;
            String action = (String) data.get("action");
            
            switch (action) {
                case "get_reading" -> handleGetReading(data, message);
                case "get_all_readings" -> handleGetAllReadings(message);
                case "add_sensor" -> handleAddSensor(data, message);
                case "add_alert_rule" -> handleAddAlertRule(data, message);
                default -> log.warn("Unknown sensor action: {}", action);
            }
        }
    }

    private void collectReading(SensorConfig sensor) {
        try {
            double value = sensor.supplier().get();
            SensorReading reading = new SensorReading(
                sensor.id(),
                value,
                sensor.unit(),
                Instant.now()
            );
            
            latestReadings.put(sensor.id(), reading);
            log.debug("Collected reading: {} = {} {}", sensor.id(), value, sensor.unit());
            
            checkAlertRules(reading);
            
        } catch (Exception e) {
            log.error("Failed to collect reading for sensor {}: {}", sensor.id(), e.getMessage());
        }
    }

    private void checkAlertRules(SensorReading reading) {
        for (AlertRule rule : alertRules) {
            if (rule.sensorId().equals(reading.sensorId())) {
                boolean triggered = switch (rule.condition()) {
                    case GREATER_THAN -> reading.value() > rule.threshold();
                    case LESS_THAN -> reading.value() < rule.threshold();
                    case EQUALS -> Math.abs(reading.value() - rule.threshold()) < 0.001;
                    case NOT_EQUALS -> Math.abs(reading.value() - rule.threshold()) >= 0.001;
                };
                
                if (triggered) {
                    publishAlert(rule, reading);
                }
            }
        }
    }

    private void publishAlert(AlertRule rule, SensorReading reading) {
        ChannelMessage alert = ChannelMessage.text(
            OUTPUT_CHANNEL,
            getId(),
            "system://monitor",
            UUID.randomUUID().toString(),
            String.format("[%s] %s: %s (current: %.2f, threshold: %.2f)",
                rule.severity(),
                rule.sensorId(),
                rule.message(),
                reading.value(),
                rule.threshold()
            )
        );
        
        alert.addMetadata("type", "sensor_alert");
        alert.addMetadata("rule_id", rule.id());
        alert.addMetadata("sensor_id", reading.sensorId());
        alert.addMetadata("value", reading.value());
        alert.addMetadata("threshold", rule.threshold());
        alert.addMetadata("severity", rule.severity().name());
        alert.addMetadata("timestamp", Instant.now().toString());
        
        publish(OUTPUT_CHANNEL, alert);
        log.info("Published alert: {} - {}", rule.severity(), rule.message());
    }

    private void handleGetReading(Map<String, Object> data, ChannelMessage original) {
        String sensorId = (String) data.get("sensor_id");
        SensorReading reading = latestReadings.get(sensorId);
        
        ChannelMessage response = ChannelMessage.text(
            OUTPUT_CHANNEL,
            getId(),
            original.getSourceChannelId(),
            original.getSessionId(),
            reading != null ? reading.toString() : "Sensor not found: " + sensorId
        );
        response.addMetadata("type", "reading_response");
        response.addMetadata("sensor_id", sensorId);
        if (reading != null) {
            response.addMetadata("value", reading.value());
            response.addMetadata("unit", reading.unit());
        }
        
        publish(OUTPUT_CHANNEL, response);
    }

    private void handleGetAllReadings(ChannelMessage original) {
        Map<String, Object> allReadings = new HashMap<>();
        latestReadings.forEach((id, reading) -> {
            Map<String, Object> readingData = new HashMap<>();
            readingData.put("value", reading.value());
            readingData.put("unit", reading.unit());
            readingData.put("timestamp", reading.timestamp().toString());
            allReadings.put(id, readingData);
        });
        
        ChannelMessage response = ChannelMessage.text(
            OUTPUT_CHANNEL,
            getId(),
            original.getSourceChannelId(),
            original.getSessionId(),
            allReadings.toString()
        );
        response.addMetadata("type", "all_readings_response");
        response.addMetadata("count", allReadings.size());
        
        publish(OUTPUT_CHANNEL, response);
    }

    private void handleAddSensor(Map<String, Object> data, ChannelMessage original) {
        String sensorId = (String) data.get("sensor_id");
        String description = (String) data.get("description");
        int intervalSeconds = (Integer) data.getOrDefault("interval_seconds", 60);
        String unit = (String) data.getOrDefault("unit", "value");
        
        addSensor(new SensorConfig(
            sensorId,
            description,
            () -> 0.0,
            intervalSeconds,
            unit
        ));
        
        log.info("Added sensor: {} (interval: {}s)", sensorId, intervalSeconds);
    }

    private void handleAddAlertRule(Map<String, Object> data, ChannelMessage original) {
        String ruleId = (String) data.get("rule_id");
        String sensorId = (String) data.get("sensor_id");
        String conditionStr = (String) data.get("condition");
        double threshold = ((Number) data.get("threshold")).doubleValue();
        String severityStr = (String) data.getOrDefault("severity", "WARNING");
        String message = (String) data.get("message");
        
        AlertCondition condition = AlertCondition.valueOf(conditionStr.toUpperCase());
        AlertSeverity severity = AlertSeverity.valueOf(severityStr.toUpperCase());
        
        addAlertRule(new AlertRule(ruleId, sensorId, condition, threshold, severity, message));
        
        log.info("Added alert rule: {} for sensor {}", ruleId, sensorId);
    }

    public void addSensor(SensorConfig sensor) {
        sensors.put(sensor.id(), sensor);
    }

    public void addAlertRule(AlertRule rule) {
        alertRules.add(rule);
    }

    public Map<String, SensorReading> getLatestReadings() {
        return Collections.unmodifiableMap(latestReadings);
    }

    public List<AlertRule> getAlertRules() {
        return Collections.unmodifiableList(alertRules);
    }

    private double getCpuUsage() {
        com.sun.management.OperatingSystemMXBean osBean = 
            (com.sun.management.OperatingSystemMXBean) 
            java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        return osBean.getCpuLoad() * 100;
    }

    private double getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return (double) usedMemory / maxMemory * 100;
    }

    private double getDiskUsage() {
        try {
            java.io.File root = new java.io.File("/");
            long total = root.getTotalSpace();
            long free = root.getFreeSpace();
            return (double) (total - free) / total * 100;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double getSystemUptime() {
        long uptimeMillis = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        return uptimeMillis / 1000.0;
    }

    private double getActiveNeuronCount() {
        return 0.0;
    }

    private double getActiveChannelCount() {
        return 0.0;
    }

    protected String buildPrompt(NeuronContext context, String userInput) {
        return "Sensor neuron monitoring system status. Current readings: " + latestReadings;
    }

    public record SensorConfig(
        String id,
        String description,
        Supplier<Double> supplier,
        int intervalSeconds,
        String unit
    ) {}

    public record SensorReading(
        String sensorId,
        double value,
        String unit,
        Instant timestamp
    ) {}

    public record AlertRule(
        String id,
        String sensorId,
        AlertCondition condition,
        double threshold,
        AlertSeverity severity,
        String message
    ) {}

    public enum AlertCondition {
        GREATER_THAN,
        LESS_THAN,
        EQUALS,
        NOT_EQUALS
    }

    public enum AlertSeverity {
        INFO,
        WARNING,
        CRITICAL
    }
}
