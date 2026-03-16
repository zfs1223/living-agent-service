package com.livingagent.core.provider;

import java.util.List;
import java.util.Optional;

public interface ProviderRegistry {
    
    void register(Provider provider);
    
    void unregister(String name);
    
    Optional<Provider> get(String name);
    
    List<Provider> getAll();
    
    Provider getDefault();
    
    Provider getAsrProvider();
    
    Provider getTtsProvider();
    
    Provider getToolProvider();
    
    boolean hasProvider(String name);
}
