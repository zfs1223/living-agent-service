package com.livingagent.core.service.local;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.livingagent.core.model.local.AiModelsConfig;
import com.livingagent.core.model.local.AiModelsConfig.AsrConfig;
import com.livingagent.core.model.local.AiModelsConfig.TtsConfig;
import com.livingagent.core.model.local.AiModelsConfig.AsrProviderConfig;
import com.livingagent.core.model.local.AiModelsConfig.TtsProviderConfig;
import com.livingagent.core.model.local.AiModelsConfig.ModelConfig;
import com.livingagent.core.model.local.AiModelsConfig.LlmConfig;
import java.util.List;
import java.util.Map;

@Service
public class LocalModelService {
    
    @Autowired
    private AiModelsConfig config;
    
    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();
    
    public String generateText(String prompt) {
        return generateText(prompt, config.getLlmConfig().getDefaultModel());
    }
    
    public String generateText(String prompt, String modelName) {
        AiModelsConfig.ModelConfig modelConfig = findModelConfig(modelName);
        if (modelConfig == null) {
            throw new IllegalArgumentException("Model not found: " + modelName);
        }
        
        if (!modelConfig.isEnabled()) {
            throw new IllegalStateException("Model is disabled: " + modelName);
        }
        
        if ("local".equals(modelConfig.getType())) {
            return executeLocalLlm(prompt, modelConfig);
        } else {
            throw new IllegalArgumentException("Unsupported model type: " + modelConfig.getType());
        }
    }
    
    public String transcribeAudio(byte[] audioData) {
        return transcribeAudio(audioData, config.getAsrConfig().getDefaultProvider());
    }
    
    public String transcribeAudio(byte[] audioData, String providerName) {
        AsrProviderConfig providerConfig = findAsrProvider(providerName);
        if (providerConfig == null) {
            throw new IllegalArgumentException("ASR provider not found: " + providerName);
        }
        
        if (!providerConfig.isEnabled()) {
            throw new IllegalArgumentException("ASR provider is disabled: " + providerName);
        }
        
        return executeAsr(audioData, providerConfig);
    }
    
    public byte[] synthesizeSpeech(String text, String voice, double speed) {
        return synthesizeSpeech(text, voice, speed, config.getTtsConfig().getDefaultProvider());
    }
    
    public byte[] synthesizeSpeech(String text, String voice, double speed, String providerName) {
        TtsProviderConfig providerConfig = findTtsProvider(providerName);
        if (providerConfig == null) {
            throw new IllegalArgumentException("TTS provider not found: " + providerName);
        }
        
        if (!providerConfig.isEnabled()) {
            throw new IllegalArgumentException("TTS provider is disabled: " + providerName);
        }
        
        return executeTts(text, voice, speed, providerConfig);
    }
    
    public List<String> getAvailableModels() {
        List<String> models = new ArrayList<>();
        for (AiModelsConfig.ModelConfig config : config.getLlmConfig().getModels()) {
            if (config.isEnabled()) {
                models.add(config.getName());
            }
        }
        return models;
    }
    
    public List<String> getAvailableAsrProviders() {
        List<String> providers = new ArrayList<>();
        for (AsrProviderConfig provider : config.getAsrConfig().getProviders()) {
            if (provider.isEnabled()) {
                providers.add(provider.getName());
            }
        }
        return providers;
    }
    
    public List<String> getAvailableTtsProviders() {
        List<String> providers = new ArrayList<>();
        for (TtsProviderConfig provider : config.getTtsConfig().getProviders()) {
            if (provider.isEnabled()) {
                providers.add(provider.getName());
            }
        }
        return providers;
    }
    
    public List<String> getAvailableVoices(String providerName) {
        TtsProviderConfig providerConfig = findTtsProvider(providerName);
        if (providerConfig == null) {
            return Collections.emptyList();
        }
        
        Path voicesPath = Paths.get(providerConfig.getVoicesPath());
        if (!Files.exists(voicesPath)) {
            return Collections.emptyList();
        }
        
        List<String> voices = new ArrayList<>();
        try {
            Files.list(voicesPath)
                    .filter(p -> p.toString().endsWith(".pt"))
                    .map(p -> p.getFileName().toString().replace(".pt", ""))
                    .forEach(voices::add);
        } catch (IOException e) {
            return Collections.emptyList();
        }
        return voices;
    }
    
    AiModelsConfig.ModelConfig findModelConfig(String modelName) {
        for (AiModelsConfig.ModelConfig model : config.getLlmConfig().getModels()) {
            if (model.getName().equals(modelName)) {
                return model;
            }
        }
        return null;
    }
    
    AsrProviderConfig findAsrProvider(String providerName) {
        for (AsrProviderConfig provider : config.getAsrConfig().getProviders()) {
            if (provider.getName().equals(providerName)) {
                return provider;
            }
        }
        return null;
    }
    
    TtsProviderConfig findTtsProvider(String providerName) {
        for (TtsProviderConfig provider : config.getTtsConfig().getProviders()) {
            if (provider.getName().equals(providerName)) {
                return provider;
            }
        }
        return null;
    }
    
    private String executeLocalLlm(String prompt, AiModelsConfig.ModelConfig modelConfig) {
        String pythonScript = modelConfig.getPythonScript();
        if (pythonScript == null || pythonScript.isBlank()) {
            throw new IllegalStateException("Python script not configured for model: " + modelConfig.getName());
        }
        
        Path scriptPath = Paths.get(pythonScript);
        if (!Files.exists(scriptPath)) {
            throw new IllegalStateException("Python script not found: " + scriptPath);
        }
        
        try {
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath.toString());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(prompt);
                writer.flush();
            }
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("LLM script failed with exit code " + exitCode + ": " + output);
            }
            
            return output.toString().trim();
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute LLM script", e);
        }
    }
    
    private String executeAsr(byte[] audioData, AsrProviderConfig providerConfig) {
        String pythonScript = providerConfig.getPythonScript();
        if (pythonScript == null || pythonScript.isBlank()) {
            throw new IllegalArgumentException("Python script not configured for ASR provider: " + providerConfig.getName());
        }
        
        Path scriptPath = Paths.get(pythonScript);
        if (!Files.exists(scriptPath)) {
            throw new IllegalArgumentException("Python script not found: " + scriptPath);
        }
        
        try {
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath.toString());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(Base64.getEncoder().encodeToString(audioData));
                writer.flush();
            }
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("ASR script failed with exit code " + exitCode + ": " + output);
            }
            
            return output.toString().trim();
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute ASR script", e);
        }
    }
    
    private byte[] executeTts(String text, String voice, double speed, TtsProviderConfig providerConfig) {
        String pythonScript = providerConfig.getPythonScript();
        if (pythonScript == null || pythonScript.isBlank()) {
            throw new IllegalArgumentException("Python script not configured for TTS provider: " + providerConfig.getName());
        }
        
        Path scriptPath = Paths.get(pythonScript);
        if (!Files.exists(scriptPath)) {
            throw new IllegalArgumentException("Python script not found: " + scriptPath);
        }
        
        try {
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath.toString());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write(text);
                writer.newLine();
                writer.write(voice != null ? voice : "default");
                writer.newLine();
                writer.write(String.valueOf(speed));
                writer.flush();
            }
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (InputStream inputStream = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("TTS script failed with exit code " + exitCode);
            }
            
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute TTS script", e);
        }
    }
}
