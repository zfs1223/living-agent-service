package com.livingagent.perception.sensor;

import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.impl.AbstractNeuron;
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
 * 设计原则（v2.0 重构）：
 * - 按需查询模式：仅在收到查询请求时才调用 API
 * - 无后台轮询：避免资源浪费和无效消息堆积
 * - 通道保留：仍可接收来自外部的查询请求
 *
 * Renamed from SensorNeuron to PerceptionSensorNeuron to avoid conflict
 * with the system sensor neuron in com.livingagent.core.neuron.impl.SensorNeuron.
 *
 * 通道拓扑:
 *   Input:  channel://input/sensor (接收查询请求)
 *   Output: channel://perception/sensor-data (响应数据，仅在有查询时发布)
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

    // 注意：轮询已禁用，以下变量保留用于向后兼容但不再使用
    @Deprecated
    private int pollingIntervalSeconds = 10;
    @Deprecated
    private boolean pollingEnabled = false;  // 默认禁用

    private OkHttpClient httpClient;
    private volatile boolean running = false;

    public PerceptionSensorNeuron() {
        super(
            ID,
            "SensorNeuron",
            "WiFi 物理感知神经元 - 人员检测、环境监测、生命体征（按需查询模式）",
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

    /**
     * @deprecated 轮询已禁用，此方法不再有效
     */
    @Deprecated
    public void setPollingIntervalSeconds(int seconds) {
        this.pollingIntervalSeconds = seconds;
        log.warn("setPollingIntervalSeconds is deprecated - polling is disabled");
    }

    /**
     * @deprecated 轮询已禁用，此方法不再有效
     */
    @Deprecated
    public void setPollingEnabled(boolean enabled) {
        this.pollingEnabled = false;  // 始终禁用
        if (enabled) {
            log.warn("Polling is deprecated and permanently disabled. Use SensorDataTool for on-demand queries.");
        }
    }

    @Override
    protected void doStart(NeuronContext context) {
        log.info("SensorNeuron starting (on-demand mode), RuView API at {}", ruviewApiBaseUrl);

        httpClient = new OkHttpClient.Builder()
            .connectTimeout(apiTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(apiTimeoutMs, TimeUnit.MILLISECONDS)
            .build();

        // 不再启动轮询线程
        // 使用 SensorDataTool 进行按需查询

        running = true;
        log.info("SensorNeuron started in on-demand query mode (polling disabled)");
    }

    @Override
    protected void doStop() {
        running = false;
        log.info("SensorNeuron stopped");
    }

    @Override
    protected void doProcessMessage(ChannelMessage message) {
        if (!running || ruviewApiBaseUrl == null) {
            log.debug("SensorNeuron not ready or no API URL configured");
            return;
        }

        log.debug("SensorNeuron processing query: {}", message.getId());

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
                response.addMetadata("mode", "on-demand");

                publish(OUTPUT_CHANNEL, response);
                log.debug("Published sensor data response for query: {}", queryType);
            }

        } catch (Exception e) {
            log.error("Failed to process sensor query: {}", e.getMessage());
            publishError(message, "Sensor query failed: " + e.getMessage());
        }
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