package com.livingagent.gateway.service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.livingagent.core.model.ModelManager;
import com.livingagent.core.model.ModelResponse;
import com.livingagent.core.model.ModelStatus;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.nativelib.AudioNative;

@Service
public class AgentService {
    
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    
    private final ModelManager modelManager;
    private final NeuronRegistry neuronRegistry;
    private final ChannelManager channelManager;
    private final ConcurrentHashMap<String, SessionContext> activeSessions;
    private final ConcurrentHashMap<String, AudioNative.Processor> audioProcessors;
    
    public AgentService(ModelManager modelManager, NeuronRegistry neuronRegistry, 
                        ChannelManager channelManager) {
        this.modelManager = modelManager;
        this.neuronRegistry = neuronRegistry;
        this.channelManager = channelManager;
        this.activeSessions = new ConcurrentHashMap<>();
        this.audioProcessors = new ConcurrentHashMap<>();
    }
    
    public void startSession(String sessionId) {
        SessionContext context = new SessionContext(sessionId);
        activeSessions.put(sessionId, context);
        
        AudioNative.Processor audioProcessor = new AudioNative.Processor(16000, 1, 960, true);
        audioProcessors.put(sessionId, audioProcessor);
        
        modelManager.createSession(sessionId)
            .thenAccept(session -> {
                context.setModelSession(session);
                log.info("Session started: {}", sessionId);
            })
            .exceptionally(e -> {
                log.error("Failed to start session: {}", sessionId, e);
                return null;
            });
    }
    
    public void endSession(String sessionId) {
        SessionContext context = activeSessions.remove(sessionId);
        if (context != null) {
            modelManager.destroySession(sessionId);
            log.info("Session ended: {}", sessionId);
        }
        
        AudioNative.Processor processor = audioProcessors.remove(sessionId);
        if (processor != null) {
            processor.close();
        }
    }
    
    public CompletableFuture<Map<String, Object>> processTextAsync(String sessionId, String text, String channel) {
        SessionContext context = activeSessions.get(sessionId);
        if (context == null) {
            return CompletableFuture.completedFuture(Map.of(
                "type", "error",
                "message", "Session not found"
            ));
        }
        
        context.incrementMessageCount();
        
        return modelManager.generateText(sessionId, text, null)
            .thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("type", "response");
                result.put("sessionId", sessionId);
                result.put("channel", channel);
                
                if (response.isSuccess()) {
                    result.put("text", response.getText());
                    result.put("model", response.getModel());
                } else {
                    result.put("error", response.getError());
                }
                
                return result;
            });
    }
    
    public CompletableFuture<Map<String, Object>> processAudioAsync(String sessionId, String audioData, String format) {
        SessionContext context = activeSessions.get(sessionId);
        if (context == null) {
            return CompletableFuture.completedFuture(Map.of(
                "type", "error",
                "message", "Session not found"
            ));
        }
        
        context.incrementMessageCount();
        
        return modelManager.recognizeSpeech(sessionId, audioData, "sherpa")
            .thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("type", "transcription");
                result.put("sessionId", sessionId);
                
                if (response.isSuccess()) {
                    result.put("text", response.getText());
                    result.put("model", response.getModel());
                } else {
                    result.put("error", response.getError());
                }
                
                return result;
            });
    }
    
    public CompletableFuture<Map<String, Object>> processAudioFullChain(String sessionId, String base64OpusData) {
        SessionContext context = activeSessions.get(sessionId);
        AudioNative.Processor audioProcessor = audioProcessors.get(sessionId);
        
        if (context == null) {
            return CompletableFuture.completedFuture(Map.of(
                "type", "error",
                "message", "Session not found"
            ));
        }
        
        if (audioProcessor == null) {
            return CompletableFuture.completedFuture(Map.of(
                "type", "error",
                "message", "Audio processor not initialized"
            ));
        }
        
        long startTime = System.currentTimeMillis();
        context.incrementMessageCount();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] opusBytes = Base64.getDecoder().decode(base64OpusData);
                
                byte[] pcmBytes = audioProcessor.decodeOpus(opusBytes);
                if (pcmBytes == null || pcmBytes.length == 0) {
                    return Map.of("type", "error", "message", "Opus decode failed");
                }
                
                Path tempAudioFile = savePcmToWav(pcmBytes, 16000, 1);
                
                ModelResponse asrResponse = modelManager.recognizeSpeech(sessionId, tempAudioFile.toString(), "sherpa").join();
                if (!asrResponse.isSuccess()) {
                    return Map.of("type", "error", "message", "ASR failed: " + asrResponse.getError());
                }
                
                String recognizedText = asrResponse.getText();
                log.info("[{}] ASR: {}", sessionId, recognizedText);
                
                List<Map<String, String>> history = context.getHistory();
                
                ModelResponse chatResponse = modelManager.processChatWithIntent(sessionId, recognizedText, history).join();
                if (!chatResponse.isSuccess()) {
                    return Map.of("type", "error", "message", "Chat failed: " + chatResponse.getError());
                }
                
                String responseText = chatResponse.getText();
                String intent = (String) chatResponse.getData().getOrDefault("intent", "unknown");
                String model = chatResponse.getModel();
                
                log.info("[{}] Chat ({}) intent={}: {}", sessionId, model, intent, responseText);
                
                context.addHistory("user", recognizedText);
                context.addHistory("assistant", responseText);
                
                if (responseText == null || responseText.isEmpty()) {
                    return Map.of("type", "error", "message", "Empty response from LLM");
                }
                
                ModelResponse ttsResponse = modelManager.synthesizeSpeechRaw(sessionId, responseText, "zh", 1.0).join();
                if (!ttsResponse.isSuccess()) {
                    return Map.of("type", "error", "message", "TTS failed: " + ttsResponse.getError());
                }
                
                byte[] ttsPcmBytes = extractPcmFromResponse(ttsResponse);
                if (ttsPcmBytes == null || ttsPcmBytes.length == 0) {
                    return Map.of("type", "error", "message", "TTS produced no audio");
                }
                
                List<byte[]> opusPackets = encodePcmToOpus(audioProcessor, ttsPcmBytes);
                
                String responseBase64 = combineOpusPacketsToBase64(opusPackets);
                
                long latency = System.currentTimeMillis() - startTime;
                
                Map<String, Object> result = new HashMap<>();
                result.put("type", "audio_response");
                result.put("sessionId", sessionId);
                result.put("text", recognizedText);
                result.put("response", responseText);
                result.put("audio", responseBase64);
                result.put("model", model);
                result.put("intent", intent);
                result.put("latency_ms", latency);
                
                Files.deleteIfExists(tempAudioFile);
                
                return result;
                
            } catch (Exception e) {
                log.error("[{}] Full chain processing error", sessionId, e);
                return Map.of("type", "error", "message", "Processing error: " + e.getMessage());
            }
        });
    }
    
    private Path savePcmToWav(byte[] pcmData, int sampleRate, int channels) throws Exception {
        Path tempFile = Files.createTempFile("audio_input_", ".wav");
        
        try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
            int dataSize = pcmData.length;
            int fileSize = 36 + dataSize;
            
            fos.write("RIFF".getBytes());
            fos.write(intToLittleEndian(fileSize));
            fos.write("WAVE".getBytes());
            fos.write("fmt ".getBytes());
            fos.write(intToLittleEndian(16));
            fos.write(shortToLittleEndian((short) 1));
            fos.write(shortToLittleEndian((short) channels));
            fos.write(intToLittleEndian(sampleRate));
            fos.write(intToLittleEndian(sampleRate * channels * 2));
            fos.write(shortToLittleEndian((short) (channels * 2)));
            fos.write(shortToLittleEndian((short) 16));
            fos.write("data".getBytes());
            fos.write(intToLittleEndian(dataSize));
            fos.write(pcmData);
        }
        
        return tempFile;
    }
    
    private byte[] intToLittleEndian(int value) {
        return new byte[] {
            (byte) (value & 0xFF),
            (byte) ((value >> 8) & 0xFF),
            (byte) ((value >> 16) & 0xFF),
            (byte) ((value >> 24) & 0xFF)
        };
    }
    
    private byte[] shortToLittleEndian(short value) {
        return new byte[] {
            (byte) (value & 0xFF),
            (byte) ((value >> 8) & 0xFF)
        };
    }
    
    private byte[] extractPcmFromResponse(ModelResponse response) {
        Object audioData = response.getData().get("audio_data");
        if (audioData instanceof List) {
            @SuppressWarnings("unchecked")
            List<Number> samples = (List<Number>) audioData;
            byte[] pcmBytes = new byte[samples.size() * 2];
            for (int i = 0; i < samples.size(); i++) {
                short sample = samples.get(i).shortValue();
                pcmBytes[i * 2] = (byte) (sample & 0xFF);
                pcmBytes[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
            }
            return pcmBytes;
        }
        return null;
    }
    
    private List<byte[]> encodePcmToOpus(AudioNative.Processor processor, byte[] pcmData) {
        List<byte[]> packets = new ArrayList<>();
        int frameSize = 960 * 2;
        
        for (int i = 0; i + frameSize <= pcmData.length; i += frameSize) {
            byte[] frame = new byte[frameSize];
            System.arraycopy(pcmData, i, frame, 0, frameSize);
            
            byte[] opusPacket = processor.encodePcm(frame);
            if (opusPacket != null && opusPacket.length > 0) {
                packets.add(opusPacket);
            }
        }
        
        return packets;
    }
    
    private String combineOpusPacketsToBase64(List<byte[]> packets) {
        int totalLength = 0;
        for (byte[] packet : packets) {
            totalLength += 4 + packet.length;
        }
        
        byte[] combined = new byte[totalLength];
        int offset = 0;
        for (byte[] packet : packets) {
            combined[offset++] = (byte) ((packet.length >> 24) & 0xFF);
            combined[offset++] = (byte) ((packet.length >> 16) & 0xFF);
            combined[offset++] = (byte) ((packet.length >> 8) & 0xFF);
            combined[offset++] = (byte) (packet.length & 0xFF);
            System.arraycopy(packet, 0, combined, offset, packet.length);
            offset += packet.length;
        }
        
        return Base64.getEncoder().encodeToString(combined);
    }
    
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("activeSessions", activeSessions.size());
        status.put("neurons", neuronRegistry.getAll().size());
        status.put("channels", channelManager.getAll().size());
        
        try {
            ModelStatus modelStatus = modelManager.getStatus().join();
            status.put("modelsLoaded", modelStatus.getLoadedCount());
            status.put("modelsTotal", modelStatus.getTotalModels());
            status.put("asrAvailable", modelStatus.isAsrAvailable());
            status.put("llmAvailable", modelStatus.isLlmAvailable());
            status.put("ttsAvailable", modelStatus.isTtsAvailable());
        } catch (Exception e) {
            status.put("modelStatusError", e.getMessage());
        }
        
        return status;
    }
    
    public boolean isSessionActive(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }
    
    private static class SessionContext {
        private final String sessionId;
        private final long createdAt;
        private volatile Object modelSession;
        private volatile int messageCount;
        private final List<Map<String, String>> history;
        
        public SessionContext(String sessionId) {
            this.sessionId = sessionId;
            this.createdAt = System.currentTimeMillis();
            this.messageCount = 0;
            this.history = new ArrayList<>();
        }
        
        public void setModelSession(Object modelSession) {
            this.modelSession = modelSession;
        }
        
        public Object getModelSession() {
            return modelSession;
        }
        
        public void incrementMessageCount() {
            this.messageCount++;
        }
        
        public int getMessageCount() {
            return messageCount;
        }
        
        public List<Map<String, String>> getHistory() {
            return new ArrayList<>(history);
        }
        
        public void addHistory(String role, String content) {
            Map<String, String> turn = new HashMap<>();
            turn.put("role", role);
            turn.put("content", content);
            history.add(turn);
            
            if (history.size() > 10) {
                history.remove(0);
            }
        }
    }
}
