package com.livingagent.core.provider.impl;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.livingagent.core.provider.Provider;
import com.livingagent.core.provider.ProviderRegistry;

public class ProviderRegistryImpl implements ProviderRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(ProviderRegistryImpl.class);
    
    private final ConcurrentHashMap<String, Provider> providers;
    private final CopyOnWriteArrayList<Provider> providerList;
    private String defaultProviderName;
    private String asrProviderName;
    private String ttsProviderName;
    private String toolProviderName;
    
    public ProviderRegistryImpl() {
        this.providers = new ConcurrentHashMap<>();
        this.providerList = new CopyOnWriteArrayList<>();
        this.defaultProviderName = "qwen";
        this.asrProviderName = "asr";
        this.ttsProviderName = "tts";
        this.toolProviderName = "bitnet";
    }
    
    @Override
    public void register(Provider provider) {
        String name = provider.name();
        providers.put(name, provider);
        if (!providerList.contains(provider)) {
            providerList.add(provider);
        }
        log.info("Registered provider: {}", name);
    }
    
    @Override
    public void unregister(String name) {
        Provider removed = providers.remove(name);
        if (removed != null) {
            providerList.remove(removed);
            log.info("Unregistered provider: {}", name);
        }
    }
    
    @Override
    public Optional<Provider> get(String name) {
        return Optional.ofNullable(providers.get(name));
    }
    
    @Override
    public List<Provider> getAll() {
        return List.copyOf(providerList);
    }
    
    @Override
    public Provider getDefault() {
        Provider provider = providers.get(defaultProviderName);
        if (provider == null && !providerList.isEmpty()) {
            return providerList.get(0);
        }
        return provider;
    }
    
    @Override
    public Provider getAsrProvider() {
        return providers.get(asrProviderName);
    }
    
    @Override
    public Provider getTtsProvider() {
        return providers.get(ttsProviderName);
    }
    
    @Override
    public Provider getToolProvider() {
        return providers.get(toolProviderName);
    }
    
    @Override
    public boolean hasProvider(String name) {
        return providers.containsKey(name);
    }
    
    public void setDefaultProviderName(String name) {
        this.defaultProviderName = name;
    }
    
    public void setAsrProviderName(String name) {
        this.asrProviderName = name;
    }
    
    public void setTtsProviderName(String name) {
        this.ttsProviderName = name;
    }
    
    public void setToolProviderName(String name) {
        this.toolProviderName = name;
    }
    
    public void shutdown() {
        for (Provider provider : providerList) {
            try {
                if (provider instanceof AutoCloseable) {
                    ((AutoCloseable) provider).close();
                }
            } catch (Exception e) {
                log.warn("Error closing provider {}: {}", provider.name(), e.getMessage());
            }
        }
        providers.clear();
        providerList.clear();
        log.info("ProviderRegistry shutdown complete");
    }
}
