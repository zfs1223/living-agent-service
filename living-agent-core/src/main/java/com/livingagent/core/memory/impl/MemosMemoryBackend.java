package com.livingagent.core.memory.impl;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.memory.MemoryBackend;
import com.livingagent.core.memory.MemoryCategory;
import com.livingagent.core.memory.MemoryEntry;

public class MemosMemoryBackend implements MemoryBackend {
    
    private static final Logger log = LoggerFactory.getLogger(MemosMemoryBackend.class);
    
    private final String baseUrl;
    private final String defaultCubeId;
    private final String userId;
    private final int timeout;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    private boolean initialized = false;
    
    public MemosMemoryBackend(String baseUrl, String defaultCubeId, String userId, int timeout) {
        this.baseUrl = baseUrl;
        this.defaultCubeId = defaultCubeId;
        this.userId = userId;
        this.timeout = timeout;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public String name() {
        return "memos";
    }
    
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                String url = baseUrl + "/product/users";
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    initialized = true;
                    log.info("MemOS memory backend initialized: {}", baseUrl);
                    
                    registerUserIfNeeded();
                } else {
                    throw new RuntimeException("MemOS health check failed: " + response.getStatusCode());
                }
            } catch (RestClientException e) {
                log.warn("MemOS not available, will use fallback: {}", e.getMessage());
                initialized = false;
            }
        });
    }
    
    private void registerUserIfNeeded() {
        try {
            String url = baseUrl + "/product/users/register";
            
            Map<String, Object> request = new HashMap<>();
            request.put("user_id", userId);
            request.put("user_name", userId);
            request.put("mem_cube_id", defaultCubeId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            restTemplate.postForEntity(url, entity, String.class);
            log.debug("User registered in MemOS: {}", userId);
        } catch (RestClientException e) {
            log.debug("User may already exist in MemOS: {}", e.getMessage());
        }
    }
    
    @Override
    public CompletableFuture<Void> store(String key, String content, MemoryCategory category, String sessionId) {
        return CompletableFuture.runAsync(() -> {
            if (!initialized) {
                log.warn("MemOS not initialized, skipping store");
                return;
            }
            
            try {
                String url = baseUrl + "/product/add";
                
                Map<String, Object> request = new HashMap<>();
                request.put("user_id", userId);
                request.put("mem_cube_id", defaultCubeId);
                request.put("async_mode", "sync");
                
                List<Map<String, String>> messages = new ArrayList<>();
                Map<String, String> message = new HashMap<>();
                message.put("role", "user");
                message.put("content", String.format("[%s] %s: %s", category.name(), key, content));
                messages.add(message);
                request.put("messages", messages);
                
                if (sessionId != null) {
                    request.put("session_id", sessionId);
                }
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
                
                restTemplate.postForEntity(url, entity, String.class);
                log.debug("Stored memory in MemOS: key={}, category={}", key, category);
            } catch (RestClientException e) {
                log.error("Failed to store memory in MemOS: key={}", key, e);
                throw new RuntimeException("Failed to store memory in MemOS", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<List<MemoryEntry>> recall(String query, int limit, String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                log.warn("MemOS not initialized, returning empty results");
                return Collections.emptyList();
            }
            
            try {
                String url = baseUrl + "/product/search";
                
                Map<String, Object> request = new HashMap<>();
                request.put("query", query);
                request.put("user_id", userId);
                request.put("mem_cube_id", defaultCubeId);
                request.put("top_k", limit);
                
                if (sessionId != null) {
                    request.put("session_id", sessionId);
                }
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
                
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                
                return parseSearchResponse(response.getBody(), limit);
            } catch (RestClientException e) {
                log.error("Failed to recall memories from MemOS: query={}", query, e);
                return Collections.emptyList();
            }
        });
    }
    
    @Override
    public CompletableFuture<Optional<MemoryEntry>> get(String key) {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                return Optional.empty();
            }
            
            try {
                List<MemoryEntry> results = recall(key, 1, null).join();
                return results.stream().findFirst();
            } catch (Exception e) {
                log.error("Failed to get memory from MemOS: key={}", key, e);
                return Optional.empty();
            }
        });
    }
    
    @Override
    public CompletableFuture<List<MemoryEntry>> list(MemoryCategory category, String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                return Collections.emptyList();
            }
            
            try {
                String url = baseUrl + "/product/get_all";
                
                Map<String, Object> request = new HashMap<>();
                request.put("user_id", userId);
                request.put("mem_cube_ids", List.of(defaultCubeId));
                
                if (category != null) {
                    request.put("memory_type", category.name().toLowerCase());
                }
                
                if (sessionId != null) {
                    request.put("session_id", sessionId);
                }
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
                
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                
                return parseGetAllResponse(response.getBody());
            } catch (RestClientException e) {
                log.error("Failed to list memories from MemOS: category={}", category, e);
                return Collections.emptyList();
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> forget(String key) {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                return false;
            }
            
            try {
                Optional<MemoryEntry> entry = get(key).join();
                if (entry.isEmpty()) {
                    return false;
                }
                
                String memoryId = entry.get().id();
                String url = baseUrl + "/product/memories/" + memoryId;
                
                restTemplate.delete(url);
                log.debug("Forgot memory from MemOS: key={}", key);
                return true;
            } catch (RestClientException e) {
                log.error("Failed to forget memory from MemOS: key={}", key, e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Integer> count() {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                return 0;
            }
            
            try {
                List<MemoryEntry> entries = list(null, null).join();
                return entries.size();
            } catch (Exception e) {
                log.error("Failed to count memories from MemOS", e);
                return 0;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> healthCheck() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/product/users";
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                return response.getStatusCode().is2xxSuccessful();
            } catch (RestClientException e) {
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            initialized = false;
            log.info("MemOS memory backend closed");
        });
    }
    
    private List<MemoryEntry> parseSearchResponse(String responseBody, int limit) {
        List<MemoryEntry> results = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            
            if (data.isArray()) {
                int count = 0;
                for (JsonNode node : data) {
                    if (count >= limit) break;
                    
                    MemoryEntry entry = parseMemoryNode(node);
                    if (entry != null) {
                        results.add(entry);
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse MemOS search response", e);
        }
        
        return results;
    }
    
    private List<MemoryEntry> parseGetAllResponse(String responseBody) {
        List<MemoryEntry> results = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            
            if (data.isArray()) {
                for (JsonNode node : data) {
                    MemoryEntry entry = parseMemoryNode(node);
                    if (entry != null) {
                        results.add(entry);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse MemOS get_all response", e);
        }
        
        return results;
    }
    
    private MemoryEntry parseMemoryNode(JsonNode node) {
        try {
            String id = node.path("id").asText(UUID.randomUUID().toString());
            String content = node.path("content").asText("");
            double score = node.path("score").asDouble(0.0);
            long timestamp = node.path("timestamp").asLong(Instant.now().toEpochMilli());
            String sessionId = node.path("session_id").asText(null);
            
            String key = extractKeyFromContent(content);
            MemoryCategory category = extractCategoryFromContent(content);
            
            return new MemoryEntry(
                id,
                key,
                content,
                category,
                Instant.ofEpochMilli(timestamp),
                sessionId,
                score
            );
        } catch (Exception e) {
            log.warn("Failed to parse memory node: {}", e.getMessage());
            return null;
        }
    }
    
    private String extractKeyFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        
        int bracketEnd = content.indexOf("] ");
        if (bracketEnd > 0) {
            int colonIndex = content.indexOf(": ", bracketEnd);
            if (colonIndex > bracketEnd) {
                return content.substring(bracketEnd + 2, colonIndex);
            }
        }
        
        return content.substring(0, Math.min(50, content.length()));
    }
    
    private MemoryCategory extractCategoryFromContent(String content) {
        if (content == null || !content.startsWith("[")) {
            return MemoryCategory.CUSTOM;
        }
        
        int bracketEnd = content.indexOf("]");
        if (bracketEnd > 1) {
            String categoryStr = content.substring(1, bracketEnd);
            try {
                return MemoryCategory.valueOf(categoryStr);
            } catch (IllegalArgumentException e) {
                return MemoryCategory.CUSTOM;
            }
        }
        
        return MemoryCategory.CUSTOM;
    }
    
    public boolean isInitialized() {
        return initialized;
    }
}
