package com.livingagent.core.config;

import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.impl.ChannelManagerImpl;
import com.livingagent.core.model.ModelManager;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.neuron.NeuronState;
import com.livingagent.core.neuron.impl.NeuronRegistryImpl;
import com.livingagent.core.neuron.impl.Qwen3Neuron;
import com.livingagent.core.neuron.impl.ToolNeuron;
import com.livingagent.core.neuron.impl.NeuronCoordinator;
import com.livingagent.core.neuron.chat.ChatNeuronRouter;
import com.livingagent.core.neuron.chat.ChatNeuronConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

@Configuration
public class ChannelConfig {

    private static final Logger log = LoggerFactory.getLogger(ChannelConfig.class);

    @Bean
    public NeuronRegistry neuronRegistry(@org.springframework.context.annotation.Lazy ChannelManager channelManager) {
        log.info("Initializing NeuronRegistry");
        NeuronRegistryImpl registry = new NeuronRegistryImpl();
        registry.setChannelManager(channelManager);
        return registry;
    }

    @Bean
    public ChannelManager channelManager(NeuronRegistry neuronRegistry) {
        log.info("Initializing ChannelManager");
        ChannelManagerImpl manager = new ChannelManagerImpl();
        manager.setNeuronRegistry(neuronRegistry);
        return manager;
    }

    @Bean
    public NeuronCoordinator neuronCoordinator(NeuronRegistry neuronRegistry, ChannelManager channelManager) {
        log.info("Initializing NeuronCoordinator (会话级神经元协调器)");
        return new NeuronCoordinator(neuronRegistry, channelManager);
    }

    @Bean
    public ChatNeuronRouter chatNeuronRouter(NeuronRegistry neuronRegistry) {
        log.info("Creating ChatNeuronRouter");
        ChatNeuronConfig config = ChatNeuronConfig.defaultConfig();
        return new ChatNeuronRouter(neuronRegistry, config);
    }

    @Bean
    public Qwen3Neuron qwen3Neuron(ModelManager modelManager, NeuronRegistry neuronRegistry) {
        log.info("Registering Qwen3Neuron (Layer 2 - 闲聊神经元)");
        Qwen3Neuron neuron = new Qwen3Neuron("neuron://chat/qwen3/001", modelManager);
        neuronRegistry.register(neuron);
        return neuron;
    }

    @Bean
    public ToolNeuron toolNeuron(ModelManager modelManager, NeuronRegistry neuronRegistry) {
        log.info("Registering ToolNeuron (Layer 3 - 工具神经元, B-1-12)");
        ToolNeuron neuron = new ToolNeuron("neuron://tool/qwen35/001", modelManager);
        // 注册公共工具（所有访客可用，无需认证）
        // 注意：公共工具与公司内部工具(gitlab/jenkins等)是独立的，不能混合
        // 公共工具的实际执行在Python model_daemon.py中完成
        // Java端注册仅用于记录和未来内部工具路由的边界区分
        neuron.registerTools(java.util.List.of(
            "weather_query", "time_query", "calculator", "translation", "encyclopedia"
        ));
        log.info("ToolNeuron registered 5 public tools (weather_query, time_query, calculator, translation, encyclopedia)");
        neuronRegistry.register(neuron);
        return neuron;
    }

    @Order(2)
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        log.info("Application ready - initializing ChatNeuronRouter with registered neurons");
        ApplicationContext ctx = event.getApplicationContext();
        ChatNeuronRouter router = ctx.getBean(ChatNeuronRouter.class);
        router.initialize();
        log.info("ChatNeuronRouter initialization completed after ApplicationReadyEvent");

        NeuronRegistry neuronRegistry = ctx.getBean(NeuronRegistry.class);
        ChannelManager channelManager = ctx.getBean(ChannelManager.class);
        neuronRegistry.get("neuron://chat/qwen3/001").ifPresent(n -> {
            if (n.getState() != NeuronState.RUNNING) {
                com.livingagent.core.channel.ChannelMessageQueue queue =
                    new com.livingagent.core.channel.ChannelMessageQueue(n.getId(), 100);
                com.livingagent.core.neuron.NeuronContext ctx2 =
                    new com.livingagent.core.neuron.NeuronContext(n.getId(), null, null, queue, null, channelManager);
                n.start(ctx2);
                log.info("Post-ready: Started Qwen3Neuron with queue capacity=100");
            }
        });
        neuronRegistry.get("neuron://tool/qwen35/001").ifPresent(n -> {
            if (n.getState() != NeuronState.RUNNING) {
                com.livingagent.core.channel.ChannelMessageQueue queue =
                    new com.livingagent.core.channel.ChannelMessageQueue(n.getId(), 100);
                com.livingagent.core.neuron.NeuronContext ctx2 =
                    new com.livingagent.core.neuron.NeuronContext(n.getId(), null, null, queue, null, channelManager);
                n.start(ctx2);
                log.info("Post-ready: Started ToolNeuron with queue capacity=100");
            }
        });
    }
}
