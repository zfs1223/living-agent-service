package com.livingagent.gateway.controller;

import java.util.Map;
import java.util.HashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.livingagent.gateway.service.AgentService;

@RestController
@RequestMapping("/api/v1")
public class AgentController {
    
    private final AgentService agentService;
    
    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(health);
    }
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(agentService.getStatus());
    }
    
    @PostMapping("/session/{sessionId}/start")
    public ResponseEntity<Map<String, Object>> startSession(@PathVariable String sessionId) {
        agentService.startSession(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("status", "started");
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/session/{sessionId}/end")
    public ResponseEntity<Map<String, Object>> endSession(@PathVariable String sessionId) {
        agentService.endSession(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("status", "ended");
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/session/{sessionId}/status")
    public ResponseEntity<Map<String, Object>> getSessionStatus(@PathVariable String sessionId) {
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("active", agentService.isSessionActive(sessionId));
        return ResponseEntity.ok(result);
    }
}
