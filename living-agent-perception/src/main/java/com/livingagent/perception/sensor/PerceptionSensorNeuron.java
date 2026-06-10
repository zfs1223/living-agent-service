package com.livingagent.perception.sensor;

import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.impl.AbstractNeuron;
import com.livingagent.core.tool.Tool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

/**
 * PerceptionSensorNeuron - WiFi 物理感知神经元
 *
 * 通过 RuView Sensing Service 获取 WiFi CSI 感知数据，
 * 包括人员存在检测、占用统计、生命体征、行为识别等。
 *
 * Renamed from SensorNeuron to PerceptionSensorNeuron to avoid conflict
 * with the system sensor neuron in com.livingagent.core.neuron.impl.SensorNeuron.
 *
 * 通道拓扑:
 *   Input:  channel://input/sensor
 *   Output: channel://perception/sensor-data  (常规感知数据)
 *           channel://perception/sensor-alert (告警事件)
 *
 * 对接 RuView API:
 *   - GET /health                  - 健康检查
 *   - GET /api/v1/sensing/latest    - 最新感知数据
 *   - GET /api/v1/vital-signs       - 生命体征
 *   - GET /api/v1/model/info        - 模型信息
 */
public class PerceptionSensorNeuron extends AbstractNeuron {

    private static final Logger log = LoggerFactory.getLogger(PerceptionSensorNeuron.class);

    public static final String ID = "neuron://perception/sensor/001";
    public static final String INPUT_CHANNEL = "channel://input/sensor";
    public static final String OUTPUT_CHANNEL = "channel://perception/sensor-data";
    public static final String ALERT_CHANNEL = "channel://perception/sensor-alert";

    private String ruviewApiBaseUrl;
    private int apiTimeoutMs = 5000;
    private int pollingIntervalSeconds = 10;
    private boolean pollingEnabled = true;

    private OkHttpClient httpClient;
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public PerceptionSensorNeuron() {
        super(
            ID,
            "SensorNeuron",
            "WiFi 物理感知神经元 - 人员检测、环境监测、生命体征",
            List.of(INPUT_CHANNEL),
            List.of(OUTPUT_CHANNEL, ALERT_CHANNEL),
            List.of()
        );
    }

    public void setRuviewApiBaseUrl(String url) {
        this.ruviewApiBaseUrl = url;
    }

    public void setApiTimeoutMs(int timeoutMs) {
        this.apiTimeoutMs = timeoutMs;
    }

    public void setPollingIntervalSeconds(int seconds) {
        this.pollingIntervalSeconds = seconds;
    }

    public void setPollingEnabled(boolean enabled) {
        this.pollingEnabled = enabled;
    }

    @Override
    protected void doStart(NeuronContext context) {
        log.info("SensorNeuron starting, RuView API at {}", ruviewApiBaseUrl);

        httpClient = new OkHttpClient.Builder()
            .connectTimeout(apiTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(apiTimeoutMs, TimeUnit.MILLISECONDS)
            .build();

        if (pollingEnabled && ruviewApiBaseUrl != null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sensor-polling");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(
                this::pollSensorData,
                0, pollingIntervalSeconds, TimeUnit.SECONDS
            );
            log.info("SensorNeuron polling enabled, interval={}s", pollingIntervalSeconds);
        } else {
            log.info("SensorNeuron polling disabled (pollingEnabled={}, apiUrl={})",
                pollingEnabled, ruviewApiBaseUrl);
        }

        running = true;
        log.info("SensorNeuron started");
    }

    @Override
    protected void doStop() {
        running = false;
        log.info("SensorNeuron stopping...");

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("SensorNeuron stopped");
    }

    @Override
    protected void doProcessMessage(ChannelMessage message) {
        log.debug("SensorNeuron processing message: {}", message.getId());

        String queryType = message.getMetadata("query_type") != null
            ? (String) message.getMetadata("query_type") : "current";

        try {
            String apiPath;
            switch (queryType) {
                case "vital-signs":
                    apiPath = "/api/v1/vital-signs";
                    break;
                case "model-info":
                    apiPath = "/api/v1/model/info";
                    break;
                default:
                    apiPath = "/api/v1/sensing/latest";
            }

            String responseBody = callRuViewApi(apiPath);

            if (responseBody != null) {
                ChannelMessage response = ChannelMessage.sensorData(
                    INPUT_CHANNEL, getId(),
                    message.getTargetChannelId() != null ? message.getTargetChannelId() : OUTPUT_CHANNEL,
                    message.getSessionId(),
                    responseBody
                );
                response.addMetadata("original_message_id", message.getId());
                response.addMetadata("query_type", queryType);
                response.addMetadata("source", "ruview-api");

                publish(OUTPUT_CHANNEL, response);
                log.debug("Published sensor data response for query: {}", queryType);
            }

        } catch (Exception e) {
            log.error("Failed to process sensor query: {}", e.getMessage());
            publishError(message, "Sensor query failed: " + e.getMessage());
        }
    }

    /**
     * 定时轮询 RuView API 获取感知数据
     */
    private void pollSensorData() {
        if (!running || ruviewApiBaseUrl == null) return;

        try {
            String responseBody = callRuViewApi("/api/v1/sensing/latest");

            if (responseBody != null) {
                ChannelMessage sensorMessage = ChannelMessage.sensorData(
                    INPUT_CHANNEL, getId(),
                    OUTPUT_CHANNEL,
                    "system-polling",
                    responseBody
                );
                sensorMessage.addMetadata("source", "ruview-polling");
                sensorMessage.addMetadata("timestamp", Instant.now().toString());

                publish(OUTPUT_CHANNEL, sensorMessage);

                checkAlertConditions(responseBody);
            }
        } catch (Exception e) {
            log.error("RuView polling failed: {}", e.getMessage());
        }
    }

    /**
     * 检查告警条件
     */
    private void checkAlertConditions(String responseBody) {
        String lower = responseBody.toLowerCase();

        // 检测跌倒
        if (lower.contains("falling") || lower.contains("fall_detected")) {
            publishAlert("FALL_DETECTED", responseBody);
        }

        // 检测异常闯入 (非工作时间)
        if (lower.contains("intrusion") || lower.contains("unauthorized")) {
            publishAlert("INTRUSION", responseBody);
        }
    }

    /**
     * 发布告警消息
     */
    private void publishAlert(String alertType, String detail) {
        ChannelMessage alertMessage = ChannelMessage.sensorData(
            INPUT_CHANNEL, getId(),
            ALERT_CHANNEL,
            "system-alert",
            alertType + ": " + detail
        );
        alertMessage.addMetadata("alert_type", alertType);
        alertMessage.addMetadata("timestamp", Instant.now().toString());
        alertMessage.setPriority(10); // 高优先级

        publish(ALERT_CHANNEL, alertMessage);
        log.warn("Sensor alert published: {}", alertType);
    }

    /**
     * 调用 RuView REST API
     */
    private long lastRuViewWarnTime = 0;
    private static final long RUVIEW_WARN_INTERVAL_MS = 300_000; // 5 minutes

    private String callRuViewApi(String path) {
        if (ruviewApiBaseUrl == null) {
            log.debug("RuView API URL not configured, skipping call");
            return null;
        }

        try {
            String url = ruviewApiBaseUrl + path;
            Request request = new Request.Builder()
                .url(url)
                .header("Host", "localhost:3000")
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().string();
                } else {
                    long now = System.currentTimeMillis();
                    if (now - lastRuViewWarnTime > RUVIEW_WARN_INTERVAL_MS) {
                        log.warn("RuView API returned status {}: {} (suppressing for 5min)", response.code(), url);
                        lastRuViewWarnTime = now;
                    } else {
                        log.debug("RuView API returned status {}: {}", response.code(), url);
                    }
                    return null;
                }
            }
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            if (now - lastRuViewWarnTime > RUVIEW_WARN_INTERVAL_MS) {
                log.warn("RuView API call failed for {}: {} (suppressing for 5min)", path, e.getMessage());
                lastRuViewWarnTime = now;
            } else {
                log.debug("RuView API call failed for {}: {}", path, e.getMessage());
            }
            return null;
        }
    }

    /**
     * 发布错误消息
     */
    private void publishError(ChannelMessage original, String error) {
        ChannelMessage errorMessage = ChannelMessage.error(
            OUTPUT_CHANNEL,
            getId(),
            original != null && original.getTargetChannelId() != null
                ? original.getTargetChannelId() : OUTPUT_CHANNEL,
            original != null ? original.getSessionId() : "system-error",
            error
        );
        publish(OUTPUT_CHANNEL, errorMessage);
    }
}
