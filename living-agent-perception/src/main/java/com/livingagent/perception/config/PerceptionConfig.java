package com.livingagent.perception.config;

import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessageQueue;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.neuron.NeuronState;
import com.livingagent.perception.sensor.PerceptionSensorNeuron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

/**
 * 感知层配置类 - 注册感知神经元到 Living Agent 系统
 *
 * 将 SensorNeuron 等 Neuron 注册到 NeuronRegistry，
 * 并在 ApplicationReadyEvent 后启动。
 */
@Configuration
public class PerceptionConfig {

    private static final Logger log = LoggerFactory.getLogger(PerceptionConfig.class);

    @Value("${ruview.api.base-url:}")
    private String ruviewApiBaseUrl;

    @Value("${ruview.api.timeout-ms:5000}")
    private int ruviewApiTimeoutMs;

    @Value("${ruview.polling.enabled:true}")
    private boolean ruviewPollingEnabled;

    @Value("${ruview.polling.interval-seconds:10}")
    private int ruviewPollingIntervalSeconds;

    @Bean
    public PerceptionSensorNeuron perceptionSensorNeuron(NeuronRegistry neuronRegistry) {
        log.info("Registering PerceptionSensorNeuron (Perception Layer - WiFi 物理感知)");
        PerceptionSensorNeuron neuron = new PerceptionSensorNeuron();
        if (ruviewApiBaseUrl != null && !ruviewApiBaseUrl.isEmpty()) {
            neuron.setRuviewApiBaseUrl(ruviewApiBaseUrl);
            neuron.setApiTimeoutMs(ruviewApiTimeoutMs);
            neuron.setPollingEnabled(ruviewPollingEnabled);
            neuron.setPollingIntervalSeconds(ruviewPollingIntervalSeconds);
            log.info("PerceptionSensorNeuron configured with RuView API: {}", ruviewApiBaseUrl);
        } else {
            neuron.setPollingEnabled(false);
            log.info("PerceptionSensorNeuron registered without RuView API (polling disabled, query-only mode)");
        }
        neuronRegistry.register(neuron);
        return neuron;
    }

    @Order(3)
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        NeuronRegistry neuronRegistry = event.getApplicationContext().getBean(NeuronRegistry.class);
        ChannelManager channelManager = event.getApplicationContext().getBean(ChannelManager.class);

        // 启动 PerceptionSensorNeuron (WiFi 物理感知)
        neuronRegistry.get("neuron://perception/sensor/001").ifPresent(n -> {
            if (n.getState() != NeuronState.RUNNING) {
                ChannelMessageQueue queue = new ChannelMessageQueue(n.getId(), 100);
                NeuronContext ctx = new NeuronContext(n.getId(), null, null, queue, null, channelManager);
                n.start(ctx);
                log.info("Post-ready: Started PerceptionSensorNeuron with queue capacity=100");
            }
        });
    }
}
