package com.livingagent.core.service.local;

import com.livingagent.core.model.local.AiModelsConfig;
import com.livingagent.core.model.local.AiModelsConfig.TtsProviderConfig;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TtsService {
    
    private final LocalModelService localModelService;
    private final AiModelsConfig config;
    
    public TtsService(LocalModelService localModelService, AiModelsConfig config) {
        this.localModelService = localModelService;
        this.config = config;
    }
    
    public byte[] synthesize(String text) {
        return synthesize(text, config.getTtsConfig().getDefaultVoice(), config.getTtsConfig().getDefaultSpeed());
    }
    
    public byte[] synthesize(String text, String voice) {
        return synthesize(text, voice, config.getTtsConfig().getDefaultSpeed());
    }
    
    public byte[] synthesize(String text, String voice, double speed) {
        return synthesize(text, voice, speed, config.getTtsConfig().getDefaultProvider());
    }
    
    public byte[] synthesize(String text, String voice, double speed, String providerName) {
        return localModelService.synthesizeSpeech(text, voice, speed, providerName);
    }
    
    public void synthesizeToFile(String text, String outputPath) {
        byte[] audioData = synthesize(text);
        try {
            Files.write(Paths.get(outputPath), audioData);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write audio file: " + outputPath, e);
        }
    }
    
    public void synthesizeToFile(String text, String outputPath, String voice, double speed) {
        byte[] audioData = synthesize(text, voice, speed);
        try {
            Files.write(Paths.get(outputPath), audioData);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write audio file: " + outputPath, e);
        }
    }
    
    public List<String> getAvailableVoices() {
        return localModelService.getAvailableVoices(config.getTtsConfig().getDefaultProvider());
    }
    
    public List<String> getAvailableProviders() {
        return config.getTtsConfig().getProviders().stream()
                .filter(TtsProviderConfig::isEnabled)
                .map(TtsProviderConfig::getName)
                .toList();
    }
    
    public boolean isProviderAvailable(String providerName) {
        return localModelService.findTtsProvider(providerName) != null;
    }
}
