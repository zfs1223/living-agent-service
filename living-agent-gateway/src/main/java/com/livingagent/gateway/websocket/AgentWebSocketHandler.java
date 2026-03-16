package com.livingagent.gateway.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.gateway.service.AgentService;

@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger log = LoggerFactory.getLogger(AgentWebSocketHandler.class);
    
    private final ObjectMapper objectMapper;
    private final AgentService agentService;
    private final Map<String, WebSocketSession> sessions;
    
    public AgentWebSocketHandler(ObjectMapper objectMapper, AgentService agentService) {
        this.objectMapper = objectMapper;
        this.agentService = agentService;
        this.sessions = new ConcurrentHashMap<>();
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("WebSocket connection established: sessionId={}, remoteAddress={}", 
            sessionId, session.getRemoteAddress());
        
        sendMessage(session, Map.of(
            "type", "connected",
            "sessionId", sessionId,
            "message", "Connection established"
        ));
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received message: sessionId={}, payload={}", session.getId(), payload);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> request = objectMapper.readValue(payload, Map.class);
            
            String type = (String) request.getOrDefault("type", "unknown");
            
            switch (type) {
                case "text" -> handleTextMessage(session, request);
                case "audio" -> handleAudioMessage(session, request);
                case "control" -> handleControlMessage(session, request);
                default -> sendError(session, "Unknown message type: " + type);
            }
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
            sendError(session, "Error processing message: " + e.getMessage());
        }
    }
    
    private void handleTextMessage(WebSocketSession session, Map<String, Object> request) {
        String text = (String) request.get("text");
        String channel = (String) request.getOrDefault("channel", "default");
        
        if (text == null || text.isEmpty()) {
            sendError(session, "Missing text content");
            return;
        }
        
        agentService.processTextAsync(session.getId(), text, channel)
            .thenAccept(response -> sendMessage(session, response))
            .exceptionally(e -> {
                log.error("Error processing text message", e);
                sendError(session, "Processing error: " + e.getMessage());
                return null;
            });
    }
    
    private void handleAudioMessage(WebSocketSession session, Map<String, Object> request) {
        String audioData = (String) request.get("audio");
        String format = (String) request.getOrDefault("format", "wav");
        
        if (audioData == null || audioData.isEmpty()) {
            sendError(session, "Missing audio data");
            return;
        }
        
        agentService.processAudioAsync(session.getId(), audioData, format)
            .thenAccept(response -> sendMessage(session, response))
            .exceptionally(e -> {
                log.error("Error processing audio message", e);
                sendError(session, "Audio processing error: " + e.getMessage());
                return null;
            });
    }
    
    private void handleControlMessage(WebSocketSession session, Map<String, Object> request) {
        String action = (String) request.get("action");
        
        switch (action) {
            case "start_session" -> {
                agentService.startSession(session.getId());
                sendMessage(session, Map.of(
                    "type", "control",
                    "action", "session_started",
                    "sessionId", session.getId()
                ));
            }
            case "end_session" -> {
                agentService.endSession(session.getId());
                sendMessage(session, Map.of(
                    "type", "control",
                    "action", "session_ended",
                    "sessionId", session.getId()
                ));
            }
            case "get_status" -> {
                Map<String, Object> status = agentService.getStatus();
                sendMessage(session, Map.of(
                    "type", "control",
                    "action", "status",
                    "data", status
                ));
            }
            default -> sendError(session, "Unknown control action: " + action);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        agentService.endSession(sessionId);
        log.info("WebSocket connection closed: sessionId={}, status={}", sessionId, status);
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error: sessionId={}", session.getId(), exception);
    }
    
    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("Error sending message: {}", e.getMessage(), e);
        }
    }
    
    private void sendError(WebSocketSession session, String error) {
        sendMessage(session, Map.of(
            "type", "error",
            "message", error
        ));
    }
    
    public void broadcastToSession(String sessionId, Map<String, Object> message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            sendMessage(session, message);
        }
    }
    
    public int getActiveSessionCount() {
        return sessions.size();
    }
}
