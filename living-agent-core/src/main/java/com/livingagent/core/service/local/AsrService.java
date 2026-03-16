package com.livingagent.core.service.local;

import com.livingagent.core.model.local.AiModelsConfig;
import com.livingagent.core.model.local.AiModelsConfig.AsrProviderConfig;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AsrService {
    
    private final LocalModelService localModelService;
    private final AiModelsConfig config;
    
    public AsrService(LocalModelService localModelService, AiModelsConfig config) {
        this.localModelService = localModelService;
        this.config = config;
    }
    
    public String transcribe(byte[] audioData) {
        return transcribe(audioData, config.getAsrConfig().getDefaultProvider());
    }
    
    public String transcribe(byte[] audioData, String providerName) {
        return localModelService.transcribeAudio(audioData, providerName);
    }
    
    public String transcribeFile(String filePath) {
        try {
            byte[] audioData = Files.readAllBytes(Paths.get(filePath));
            return transcribe(audioData);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read audio file: " + filePath, e);
        }
    }
    
    public boolean isProviderAvailable(String providerName) {
        return localModelService.findAsrProvider(providerName) != null;
    }
    
    public List<String> getAvailableProviders() {
        return config.getAsrConfig().getProviders().stream()
                .filter(AsrProviderConfig::isEnabled)
                .map(AsrProviderConfig::getName)
                .toList();
    }
}
