package com.livingagent.perception.config;

import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessageQueue;
import com.livingagent.core.neuron.NeuronContext;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.neuron.NeuronState;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.tool.impl.SensorDataTool;
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
 * 感知层配置类 - 注册感知神经元和工具到 Living Agent 系统
 *
 * 变更历史（v2.0）：
 * - 禁用后台轮询，避免资源浪费和消息堆积
 * - 注册 SensorDataTool，支持按需查询传感器数据
 * - PerceptionSensorNeuron 保留为被动响应模式
 */
@Configuration
public class PerceptionConfig {

    private static final Logger log = LoggerFactory.getLogger(PerceptionConfig.class);

    @Value("${ruview.api.base-url:}")
    private String ruviewApiBaseUrl;

    @Value("${ruview.api.timeout-ms:5000}")
    private int ruviewApiTimeoutMs;

    /**
     * 注册 PerceptionSensorNeuron（被动响应模式）
     */
    @Bean
    public PerceptionSensorNeuron perceptionSensorNeuron(NeuronRegistry neuronRegistry) {
        log.info("Registering PerceptionSensorNeuron (Perception Layer - WiFi 物理感知, on-demand mode)");
        PerceptionSensorNeuron neuron = new PerceptionSensorNeuron();
        if (ruviewApiBaseUrl != null && !ruviewApiBaseUrl.isEmpty()) {
            neuron.setRuviewApiBaseUrl(ruviewApiBaseUrl);
            neuron.setApiTimeoutMs(ruviewApiTimeoutMs);
            // 注意：轮询已禁用，不再设置 pollingEnabled
            log.info("PerceptionSensorNeuron configured with RuView API (on-demand mode): {}", ruviewApiBaseUrl);
        } else {
            log.info("PerceptionSensorNeuron registered without RuView API (passive mode)");
        }
        neuronRegistry.register(neuron);
        return neuron;
    }

    /**
     * 注册 SensorDataTool（按需查询工具）
     */
    @Bean
    public SensorDataTool sensorDataTool(ToolRegistry toolRegistry) {
        log.info("Registering SensorDataTool (on-demand RuView query)");
        SensorDataTool tool = new SensorDataTool(ruviewApiBaseUrl);
        toolRegistry.register(tool);
        return tool;
    }

    @Order(3)
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        NeuronRegistry neuronRegistry = event.getApplicationContext().getBean(NeuronRegistry.class);
        ChannelManager channelManager = event.getApplicationContext().getBean(ChannelManager.class);

        // 启动 PerceptionSensorNeuron (WiFi 物理感知，被动模式)
        neuronRegistry.get("neuron://perception/sensor/001").ifPresent(n -> {
            if (n.getState() != NeuronState.RUNNING) {
                ChannelMessageQueue queue = new ChannelMessageQueue(n.getId(), 100);
                NeuronContext ctx = new NeuronContext(n.getId(), null, null, queue, null, channelManager);
                n.start(ctx);
                log.info("Post-ready: Started PerceptionSensorNeuron in on-demand mode (queue capacity=100)");
            }
        });
    }
}