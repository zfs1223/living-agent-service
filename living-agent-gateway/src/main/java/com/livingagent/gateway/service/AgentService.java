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
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.nativelib.AudioNative;
import com.livingagent.core.neuron.chat.ChatNeuronRouter;
import com.livingagent.core.neuron.chat.ChatNeuronRouter.RoutingResult;
import com.livingagent.core.security.AccessLevel;

@Service
public class AgentService {
    
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    
    private final ModelManager modelManager;
    private final NeuronRegistry neuronRegistry;
    private final ChannelManager channelManager;
    private final ChatNeuronRouter chatNeuronRouter;
    private final ConcurrentHashMap<String, SessionContext> activeSessions;
    private final ConcurrentHashMap<String, AudioNative.Processor> audioProcessors;
    
    public AgentService(ModelManager modelManager, NeuronRegistry neuronRegistry, 
                        ChannelManager channelManager, ChatNeuronRouter chatNeuronRouter) {
        this.modelManager = modelManager;
        this.neuronRegistry = neuronRegistry;
        this.channelManager = channelManager;
        this.chatNeuronRouter = chatNeuronRouter;
        this.activeSessions = new ConcurrentHashMap<>();
        this.audioProcessors = new ConcurrentHashMap<>();
    }
    
    public void startSession(String sessionId) {
        startSession(sessionId, null);
    }
    
    public void startSession(String sessionId, AccessLevel accessLevel) {
        startSession(sessionId, accessLevel, null);
    }
    
    public void startSession(String sessionId, AccessLevel accessLevel, String departmentId) {
        SessionContext context = new SessionContext(sessionId, accessLevel, departmentId);
        activeSessions.put(sessionId, context);
        
        AudioNative.Processor audioProcessor = new AudioNative.Processor(16000, 1, 960, true);
        audioProcessors.put(sessionId, audioProcessor);
        
        modelManager.createSession(sessionId)
            .thenAccept(session -> {
                context.setModelSession(session);
                log.info("Session started: {}, accessLevel={}, departmentId={}", 
                    sessionId, accessLevel, departmentId);
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
        
        AccessLevel accessLevel = context.getAccessLevel();
        String departmentId = context.getDepartmentId();
        
        Map<String, Object> routingContext = new HashMap<>();
        routingContext.put("channel", channel);
        routingContext.put("accessLevel", accessLevel);
        routingContext.put("departmentId", departmentId);
        routingContext.put("userId", context.getUserId());
        
        RoutingResult routing = chatNeuronRouter.route(sessionId, text, routingContext);
        
        if (!routing.isPermissionGranted()) {
            return CompletableFuture.completedFuture(Map.of(
                "type", "permission_denied",
                "sessionId", sessionId,
                "message", routing.getPermissionDeniedReason(),
                "requiredLevel", getRequiredLevelForIntent(routing.getIntent()),
                "currentLevel", accessLevel.name()
            ));
        }
        
        Neuron targetNeuron = routing.getNeuron();
        if (targetNeuron == null) {
            return CompletableFuture.completedFuture(Map.of(
                "type", "error",
                "message", "No available neuron for routing"
            ));
        }
        
        return processWithNeuron(sessionId, text, channel, routing, targetNeuron, context);
    }
    
    private CompletableFuture<Map<String, Object>> processWithNeuron(String sessionId, String text, 
            String channel, RoutingResult routing, Neuron neuron, SessionContext context) {
        
        List<String> inputChannels = neuron.getSubscribedChannels();
        String targetChannelId = inputChannels.isEmpty() 
            ? channel
            : inputChannels.get(0);
        
        ChannelMessage message = ChannelMessage.text(
            "channel://input/user",
            "user",
            targetChannelId,
            sessionId,
            text
        );
        
        message.addMetadata("intent", routing.getIntent());
        message.addMetadata("accessLevel", routing.getAccessLevel().name());
        message.addMetadata("originalInput", routing.getOriginalInput());
        
        channelManager.publish(targetChannelId, message);
        
        log.info("Published message to channel: {} for session: {}", targetChannelId, sessionId);
        
        return waitForResponse(sessionId, routing, neuron);
    }
    
    private CompletableFuture<Map<String, Object>> waitForResponse(String sessionId, RoutingResult routing, Neuron neuron) {
        SessionContext context = activeSessions.get(sessionId);
        
        String responseChannelId = "channel://response/" + sessionId;
        final java.util.concurrent.atomic.AtomicReference<ChannelMessage> responseRef = 
            new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        
        channelManager.subscribe(responseChannelId, new com.livingagent.core.channel.ChannelSubscriber() {
            @Override
            public void onMessage(ChannelMessage message) {
                if ("brain_response".equals(message.getMetadata().get("type"))) {
                    responseRef.set(message);
                    latch.countDown();
                }
            }
            
            @Override
            public String getSubscriberId() {
                return "response_waiter_" + sessionId;
            }
        });
        
        List<String> outputChannels = neuron.getPublishChannels();
        if (!outputChannels.isEmpty()) {
            for (String outputChannel : outputChannels) {
                channelManager.subscribe(outputChannel, new com.livingagent.core.channel.ChannelSubscriber() {
                    @Override
                    public void onMessage(ChannelMessage message) {
                        responseRef.set(message);
                        latch.countDown();
                    }
                    
                    @Override
                    public String getSubscriberId() {
                        return "output_watcher_" + sessionId;
                    }
                });
            }
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean received = latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
                
                channelManager.unsubscribe(responseChannelId, "response_waiter_" + sessionId);
                for (String outputChannel : neuron.getPublishChannels()) {
                    channelManager.unsubscribe(outputChannel, "output_watcher_" + sessionId);
                }
                
                Map<String, Object> result = new HashMap<>();
                result.put("type", "response");
                result.put("sessionId", sessionId);
                result.put("intent", routing.getIntent());
                result.put("neuron", routing.getTargetNeuron());
                result.put("accessLevel", routing.getAccessLevel().name());
                
                if (received && responseRef.get() != null) {
                    ChannelMessage response = responseRef.get();
                    String responseText = response.getContent();
                    
                    result.put("text", responseText);
                    result.put("model", response.getMetadata().getOrDefault("model", "unknown"));
                    result.put("processedBy", response.getSourceNeuronId());
                    
                    context.addHistory("user", routing.getOriginalInput());
                    context.addHistory("assistant", responseText);
                    
                    log.info("Session {} received response from {} via channel", sessionId, response.getSourceNeuronId());
                } else {
                    log.warn("Session {} timeout waiting for neuron response, falling back to ModelManager", sessionId);
                    
                    ModelResponse fallbackResponse = modelManager.processChatWithIntent(
                        sessionId, 
                        routing.getOriginalInput(), 
                        context.getHistory()
                    ).join();
                    
                    if (fallbackResponse.isSuccess()) {
                        result.put("text", fallbackResponse.getText());
                        result.put("model", fallbackResponse.getModel());
                        result.put("fallback", true);
                        
                        context.addHistory("user", routing.getOriginalInput());
                        context.addHistory("assistant", fallbackResponse.getText());
                    } else {
                        result.put("error", fallbackResponse.getError());
                    }
                }
                
                return result;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for response: {}", sessionId, e);
                return Map.of(
                    "type", "error",
                    "message", "Processing interrupted"
                );
            } catch (Exception e) {
                log.error("Error waiting for response: {}", sessionId, e);
                return Map.of(
                    "type", "error",
                    "message", "Error processing message: " + e.getMessage()
                );
            }
        });
    }
    
    private String getRequiredLevelForIntent(String intent) {
        return switch (intent) {
            case "TOOL_CALL" -> "DEPARTMENT";
            case "COMPLEX_TASK" -> "FULL";
            default -> "CHAT_ONLY";
        };
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
        private final AccessLevel accessLevel;
        private final String departmentId;
        private volatile String userId;
        private volatile Object modelSession;
        private volatile int messageCount;
        private final List<Map<String, String>> history;
        
        public SessionContext(String sessionId) {
            this(sessionId, AccessLevel.CHAT_ONLY, null);
        }
        
        public SessionContext(String sessionId, AccessLevel accessLevel, String departmentId) {
            this.sessionId = sessionId;
            this.createdAt = System.currentTimeMillis();
            this.accessLevel = accessLevel != null ? accessLevel : AccessLevel.CHAT_ONLY;
            this.departmentId = departmentId;
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
        
        public AccessLevel getAccessLevel() {
            return accessLevel;
        }
        
        public String getDepartmentId() {
            return departmentId;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public void setUserId(String userId) {
            this.userId = userId;
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
