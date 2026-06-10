package com.livingagent.core.config;

import com.livingagent.core.model.ModelManager;
import com.livingagent.core.model.impl.ModelManagerImpl;
import com.livingagent.core.provider.ProviderRegistry;
import com.livingagent.core.provider.impl.AsrProvider;
import com.livingagent.core.provider.impl.BitNetProvider;
import com.livingagent.core.provider.impl.OllamaProvider;
import com.livingagent.core.provider.impl.ProviderRegistryImpl;
import com.livingagent.core.provider.impl.QwenProvider;
import com.livingagent.core.provider.impl.TtsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(ProviderConfig.class);

    @Bean
    public ProviderRegistry providerRegistry() {
        log.info("Initializing ProviderRegistry");
        return new ProviderRegistryImpl();
    }

    @Bean
    public ModelManager modelManager() {
        log.info("Initializing ModelManager");
        return new ModelManagerImpl();
    }

    @Bean
    public QwenProvider qwenProvider(ModelManager modelManager, ProviderRegistry providerRegistry) {
        log.info("Initializing QwenProvider");
        QwenProvider provider = new QwenProvider(modelManager);
        providerRegistry.register(provider);
        return provider;
    }

    @Bean
    public BitNetProvider bitNetProvider(ModelManager modelManager, ProviderRegistry providerRegistry) {
        log.info("Initializing BitNetProvider");
        BitNetProvider provider = new BitNetProvider(modelManager);
        providerRegistry.register(provider);
        return provider;
    }

    @Bean
    public AsrProvider asrProvider(ModelManager modelManager, ProviderRegistry providerRegistry) {
        log.info("Initializing AsrProvider");
        AsrProvider provider = new AsrProvider(modelManager);
        providerRegistry.register(provider);
        return provider;
    }

    @Bean
    public TtsProvider ttsProvider(ModelManager modelManager, ProviderRegistry providerRegistry) {
        log.info("Initializing TtsProvider");
        TtsProvider provider = new TtsProvider(modelManager);
        providerRegistry.register(provider);
        return provider;
    }

    @Bean
    public OllamaProvider registerOllamaProvider(ProviderRegistry providerRegistry, OllamaProvider ollamaProvider) {
        log.info("Registering OllamaProvider to ProviderRegistry");
        providerRegistry.register(ollamaProvider);
        if (providerRegistry instanceof ProviderRegistryImpl impl) {
            impl.setDefaultProviderName("ollama");
            impl.setToolProviderName("ollama");
            log.info("Set default provider to ollama, tool provider to ollama");
        }
        return ollamaProvider;
    }
}
