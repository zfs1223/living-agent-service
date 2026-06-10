package com.livingagent.core.model.impl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.livingagent.core.model.ModelClient;
import com.livingagent.core.model.ModelSession;
import com.livingagent.core.model.ModelResponse;
import com.livingagent.core.model.ModelRequest;
import com.livingagent.core.model.ModelStatus;

public class NamedPipeModelClient implements ModelClient {
    
    private static final Logger log = LoggerFactory.getLogger(NamedPipeModelClient.class);
    private static final int DEFAULT_TIMEOUT_MS = 60000;
    
    private final String controlRequestPipe;
    private final String controlResponsePipe;
    private final String sessionPipePrefix;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ModelSession> sessions;
    private final ConcurrentHashMap<String, CompletableFuture<ModelResponse>> pendingRequests;
    private final AtomicBoolean connected;
    private final String daemonPath;
    private final int timeoutMs;
    private final Object controlPipeLock = new Object();
    
    public NamedPipeModelClient() {
        this("/opt/dialogue-service", DEFAULT_TIMEOUT_MS, "/tmp");
    }
    
    public NamedPipeModelClient(String daemonPath, int timeoutMs) {
        this(daemonPath, timeoutMs, "/tmp");
    }
    
    public NamedPipeModelClient(String daemonPath, int timeoutMs, String pipeDir) {
        this.objectMapper = new ObjectMapper();
        this.sessions = new ConcurrentHashMap<>();
        this.pendingRequests = new ConcurrentHashMap<>();
        this.connected = new AtomicBoolean(false);
        this.daemonPath = daemonPath;
        this.timeoutMs = timeoutMs;
        this.controlRequestPipe = pipeDir + "/dialogue_daemon_control_request";
        this.controlResponsePipe = pipeDir + "/dialogue_daemon_control_response";
        this.sessionPipePrefix = pipeDir + "/dialogue_daemon";
    }
    
    public String getControlRequestPipe() {
        return controlRequestPipe;
    }
    
    public String getControlResponsePipe() {
        return controlResponsePipe;
    }
    
    @Override
    public boolean isConnected() {
        return connected.get() && Files.exists(Paths.get(controlRequestPipe));
    }
    
    @Override
    public CompletableFuture<ModelSession> createSession(String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelRequest request = ModelRequest.builder()
                    .service("create_session")
                    .param("session_id", sessionId)
                    .build();
                
                ModelResponse response = sendControlRequest(request).get(timeoutMs, TimeUnit.MILLISECONDS);
                
                if (response.isSuccess()) {
                    ModelSession session = new ModelSession(sessionId);
                    sessions.put(sessionId, session);
                    log.info("Created model session: {}", sessionId);
                    return session;
                } else {
                    throw new RuntimeException("Failed to create session: " + response.getError());
                }
            } catch (Exception e) {
                log.error("Error creating session: {}", sessionId, e);
                throw new RuntimeException("Failed to create session", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> destroySession(String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelRequest request = ModelRequest.builder()
                    .service("destroy_session")
                    .param("session_id", sessionId)
                    .build();
                
                ModelResponse response = sendControlRequest(request).get(timeoutMs, TimeUnit.MILLISECONDS);
                
                ModelSession session = sessions.remove(sessionId);
                if (session != null) {
                    session.close();
                }
                
                log.info("Destroyed model session: {}", sessionId);
                return response.isSuccess();
            } catch (Exception e) {
                log.error("Error destroying session: {}", sessionId, e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<ModelResponse> sendRequest(String sessionId, ModelRequest request) {
        ModelSession session = sessions.get(sessionId);
        if (session == null) {
            return CompletableFuture.completedFuture(
                ModelResponse.failure("Session not found: " + sessionId)
            );
        }
        
        session.touch();
        
        return CompletableFuture.supplyAsync(() -> {
            String requestPipe = "/tmp/dialogue_daemon_request_" + sessionId;
            String responsePipe = "/tmp/dialogue_daemon_response_" + sessionId;
            
            try {
                return writeToPipe(requestPipe, responsePipe, request);
            } catch (Exception e) {
                log.error("Error sending request to session {}: {}", sessionId, e.getMessage());
                return ModelResponse.failure("Communication error: " + e.getMessage());
            }
        });
    }
    
    @Override
    public CompletableFuture<ModelResponse> sendControlRequest(ModelRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return writeControlRequest(request);
            } catch (Exception e) {
                log.error("Error sending control request: {}", e.getMessage());
                return ModelResponse.failure("Control communication error: " + e.getMessage());
            }
        });
    }
    
    private ModelResponse writeControlRequest(ModelRequest request) throws IOException {
        synchronized (controlPipeLock) {
            Path requestPath = Paths.get(controlRequestPipe);
            if (!Files.exists(requestPath)) {
                throw new IOException("Request pipe not found: " + controlRequestPipe);
            }

            String requestId = request.getRequestId();
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
                request.setRequestId(requestId);
            }

            String responsePipePath = sessionPipePrefix + "_control_response_" + requestId;
            Path responsePath = Paths.get(responsePipePath);
            try {
                Files.deleteIfExists(responsePath);
                new ProcessBuilder("mkfifo", responsePipePath).start().waitFor(5, TimeUnit.SECONDS);

                Map<String, Object> params = request.getParams();
                if (params == null) {
                    params = new java.util.HashMap<>();
                    request.setParams(params);
                }
                params.put("control_response_pipe", responsePipePath);

                String jsonRequest = objectMapper.writeValueAsString(request);
                log.debug("Sending control request: {}", jsonRequest);

                try (BufferedWriter requestWriter = Files.newBufferedWriter(
                        requestPath,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.WRITE)) {
                    requestWriter.write(jsonRequest);
                    requestWriter.newLine();
                    requestWriter.flush();
                }

                String responseLine = readLineWithTimeout(responsePath, timeoutMs);

                if (responseLine == null || responseLine.isEmpty()) {
                    throw new IOException("Empty response from daemon");
                }

                log.debug("Received control response: {}", responseLine);
                JsonNode responseNode = objectMapper.readTree(responseLine);

                String responseRequestId = responseNode.path("requestId").asText("");
                if (!responseRequestId.isBlank() && !requestId.equals(responseRequestId)) {
                    throw new IOException("Mismatched response requestId: expected " + requestId + ", got " + responseRequestId);
                }

                return parseResponse(responseNode);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while creating control response pipe", e);
            } finally {
                try {
                    Files.deleteIfExists(responsePath);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String readLineWithTimeout(Path path, int timeoutMillis) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    return line;
                }
            } catch (IOException e) {
                // FIFO 暂无写端或瞬态问题，重试到超时
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting pipe response", e);
            }
        }
        throw new IOException("Timed out waiting response from daemon");
    }

    private ModelResponse writeToPipe(String requestPipePath, String responsePipePath, ModelRequest request) 
            throws IOException, TimeoutException {
        
        Path requestPath = Paths.get(requestPipePath);
        Path responsePath = Paths.get(responsePipePath);
        
        if (!Files.exists(requestPath)) {
            throw new IOException("Request pipe not found: " + requestPipePath);
        }
        if (!Files.exists(responsePath)) {
            throw new IOException("Response pipe not found: " + responsePipePath);
        }
        
        String jsonRequest = objectMapper.writeValueAsString(request);
        log.debug("Sending request: {}", jsonRequest);
        
        // 关键修复：先写请求，再打开响应管道读取，避免FIFO双端打开顺序导致阻塞
        try (BufferedWriter requestWriter = Files.newBufferedWriter(
                requestPath,
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.WRITE)) {
            requestWriter.write(jsonRequest);
            requestWriter.newLine();
            requestWriter.flush();
        }

        String responseLine = readLineWithTimeout(responsePath, timeoutMs);

        if (responseLine == null || responseLine.isEmpty()) {
            throw new IOException("Empty response from daemon");
        }

        log.debug("Received response: {}", responseLine);

        JsonNode responseNode = objectMapper.readTree(responseLine);
        return parseResponse(responseNode);
    }

    private ModelResponse parseResponse(JsonNode responseNode) {
        ModelResponse response = new ModelResponse();
        response.setSuccess(responseNode.path("success").asBoolean(false));

        if (responseNode.has("error")) {
            response.setError(responseNode.get("error").asText());
        }

        if (responseNode.has("model")) {
            response.setModel(responseNode.get("model").asText());
        }

        JsonNode dataNode = responseNode.path("data");
        if (dataNode.isObject()) {
            response.setData(objectMapper.convertValue(dataNode,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
        } else {
            Map<String, Object> data = new java.util.HashMap<>();
            responseNode.fields().forEachRemaining(entry -> {
                if (!entry.getKey().equals("success") &&
                    !entry.getKey().equals("error") &&
                    !entry.getKey().equals("model")) {
                    data.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
                }
            });
            response.setData(data);
        }

        return response;
    }
    
    @Override
    public CompletableFuture<ModelStatus> getStatus() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModelRequest request = ModelRequest.builder()
                    .service("status")
                    .build();
                
                ModelResponse response = sendControlRequest(request).get(timeoutMs, TimeUnit.MILLISECONDS);
                
                if (response.isSuccess()) {
                    ModelStatus status = new ModelStatus();
                    
                    Object modelStatus = response.get("model_status");
                    if (modelStatus instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Boolean> modelsLoaded = (Map<String, Boolean>) modelStatus;
                        status.setModelsLoaded(modelsLoaded);
                    }
                    
                    Object sessionCount = response.get("session_count");
                    if (sessionCount instanceof Number) {
                        status.setSessionCount(((Number) sessionCount).intValue());
                    }
                    
                    Object sessions = response.get("sessions");
                    if (sessions instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> sessionList = (List<String>) sessions;
                        status.setSessions(sessionList);
                    }
                    
                    connected.set(true);
                    return status;
                } else {
                    connected.set(false);
                    throw new RuntimeException("Failed to get status: " + response.getError());
                }
            } catch (Exception e) {
                log.error("Error getting status", e);
                connected.set(false);
                return new ModelStatus();
            }
        });
    }
    
    @Override
    public void close() {
        for (String sessionId : sessions.keySet()) {
            try {
                destroySession(sessionId).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Error closing session {} during shutdown: {}", sessionId, e.getMessage());
            }
        }
        connected.set(false);
        log.info("ModelClient closed");
    }
    
    @Override
    public String getDaemonPath() {
        return daemonPath;
    }
}
