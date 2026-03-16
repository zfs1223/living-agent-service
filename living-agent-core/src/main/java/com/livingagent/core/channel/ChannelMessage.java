package com.livingagent.core.channel;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class ChannelMessage {

    private final String id;
    private final String sourceChannelId;
    private final String sourceNeuronId;
    private final String targetChannelId;
    private final String sessionId;
    private final MessageType type;
    private final Object payload;
    private final Map<String, Object> metadata;
    private final Instant timestamp;
    private int priority;

    public enum MessageType {
        TEXT,
        AUDIO,
        IMAGE,
        TOOL_CALL,
        TOOL_RESULT,
        CONTROL,
        ERROR
    }

    public ChannelMessage(String sourceChannelId, String sourceNeuronId, 
                          String targetChannelId, String sessionId,
                          MessageType type, Object payload) {
        this.id = UUID.randomUUID().toString();
        this.sourceChannelId = sourceChannelId;
        this.sourceNeuronId = sourceNeuronId;
        this.targetChannelId = targetChannelId;
        this.sessionId = sessionId;
        this.type = type;
        this.payload = payload;
        this.metadata = new java.util.HashMap<>();
        this.timestamp = Instant.now();
        this.priority = 0;
    }

    public String getId() { return id; }
    public String getSourceChannelId() { return sourceChannelId; }
    public String getSourceNeuronId() { return sourceNeuronId; }
    public String getTargetChannelId() { return targetChannelId; }
    public String getSessionId() { return sessionId; }
    public MessageType getType() { return type; }
    public Object getPayload() { return payload; }
    public String getContent() { 
        return payload != null ? payload.toString() : null; 
    }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getTimestamp() { return timestamp; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        return (T) metadata.get(key);
    }

    public static ChannelMessage text(String sourceChannelId, String sourceNeuronId,
                                       String targetChannelId, String sessionId, String text) {
        return new ChannelMessage(sourceChannelId, sourceNeuronId, targetChannelId, 
                                  sessionId, MessageType.TEXT, text);
    }

    public static ChannelMessage toolCall(String sourceChannelId, String sourceNeuronId,
                                          String targetChannelId, String sessionId, Object call) {
        return new ChannelMessage(sourceChannelId, sourceNeuronId, targetChannelId,
                                  sessionId, MessageType.TOOL_CALL, call);
    }

    public static ChannelMessage error(String sourceChannelId, String sourceNeuronId,
                                        String targetChannelId, String sessionId, String error) {
        return new ChannelMessage(sourceChannelId, sourceNeuronId, targetChannelId,
                                  sessionId, MessageType.ERROR, error);
    }
}
