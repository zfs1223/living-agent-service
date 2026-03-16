package com.livingagent.core.nativelib;

import java.util.Map;
import java.util.HashMap;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ChannelNative {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    static {
        NativeLibrary.isLoaded();
    }
    
    public static native long createMpscChannel(String name, int capacity);
    
    public static native void destroyChannel(long handle);
    
    public static native boolean sendMessage(long handle, String source, String messageType, String payload);
    
    public static native String receiveMessage(long handle);
    
    public static native int getChannelLength(long handle);
    
    public static native boolean isChannelEmpty(long handle);
    
    public static class MpscChannel implements AutoCloseable {
        
        private final long handle;
        private volatile boolean closed = false;
        
        public MpscChannel(String name, int capacity) {
            this.handle = createMpscChannel(name, capacity);
            if (this.handle == 0) {
                throw new RuntimeException("Failed to create channel");
            }
        }
        
        public boolean send(String source, Map<String, Object> payload) {
            checkClosed();
            try {
                String json = objectMapper.writeValueAsString(payload);
                return sendMessage(handle, source, "data", json);
            } catch (Exception e) {
                throw new RuntimeException("Failed to send message", e);
            }
        }
        
        public Map<String, Object> receive() {
            checkClosed();
            String json = receiveMessage(handle);
            if (json == null) {
                return null;
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(json, Map.class);
                return result;
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse message", e);
            }
        }
        
        public int length() {
            checkClosed();
            return getChannelLength(handle);
        }
        
        public boolean isEmpty() {
            checkClosed();
            return isChannelEmpty(handle);
        }
        
        private void checkClosed() {
            if (closed) {
                throw new IllegalStateException("Channel is closed");
            }
        }
        
        @Override
        public void close() {
            if (!closed) {
                closed = true;
                destroyChannel(handle);
            }
        }
    }
}
