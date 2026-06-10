package com.livingagent.core.config;

import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.impl.ChannelManagerImpl;
import com.livingagent.core.model.ModelManager;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.neuron.NeuronState;
import com.livingagent.core.neuron.impl.NeuronRegistryImpl;
import com.livingagent.core.neuron.impl.Qwen3Neuron;
import com.livingagent.core.neuron.impl.BitNetNeuron;
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
    public BitNetNeuron bitNetNeuron(ModelManager modelManager, NeuronRegistry neuronRegistry) {
        log.info("Registering BitNetNeuron (Layer 3 - 工具神经元)");
        BitNetNeuron neuron = new BitNetNeuron("neuron://tool/bitnet/001", modelManager);
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
        neuronRegistry.get("neuron://tool/bitnet/001").ifPresent(n -> {
            if (n.getState() != NeuronState.RUNNING) {
                com.livingagent.core.channel.ChannelMessageQueue queue =
                    new com.livingagent.core.channel.ChannelMessageQueue(n.getId(), 100);
                com.livingagent.core.neuron.NeuronContext ctx2 =
                    new com.livingagent.core.neuron.NeuronContext(n.getId(), null, null, queue, null, channelManager);
                n.start(ctx2);
                log.info("Post-ready: Started BitNetNeuron with queue capacity=100");
            }
        });
    }
}
