package com.livingagent.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

@Service
public class DepartmentChatService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentChatService.class);

    private final UnifiedAuthService authService;
    private final ObjectMapper objectMapper;

    private final Map<String, Deque<ChatHistoryEntry>> chatHistory = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> onlineUsers = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastActivity = new ConcurrentHashMap<>();
    
    private static final int MAX_HISTORY_PER_DEPARTMENT = 100;
    private static final long OFFLINE_TIMEOUT_MS = 5 * 60 * 1000;

    public DepartmentChatService(UnifiedAuthService authService) {
        this.authService = authService;
        this.objectMapper = new ObjectMapper();
    }

    public ChatHistoryEntry saveMessage(String department, String userId, String userName, 
                                        String content, String metadata) {
        ChatHistoryEntry entry = new ChatHistoryEntry(
            UUID.randomUUID().toString(),
            department,
            userId,
            userName,
            content,
            metadata,
            Instant.now()
        );

        chatHistory.computeIfAbsent(department, k -> new ConcurrentLinkedDeque<>())
            .addLast(entry);

        Deque<ChatHistoryEntry> history = chatHistory.get(department);
        while (history.size() > MAX_HISTORY_PER_DEPARTMENT) {
            history.removeFirst();
        }

        lastActivity.put(department, Instant.now());

        log.debug("Saved chat message: dept={}, user={}, content={}", 
            department, userId, content.length() > 50 ? content.substring(0, 50) + "..." : content);

        return entry;
    }

    public List<ChatHistoryEntry> getHistory(String department, int limit) {
        Deque<ChatHistoryEntry> history = chatHistory.get(department);
        if (history == null) {
            return List.of();
        }

        return history.stream()
            .skip(Math.max(0, history.size() - limit))
            .collect(Collectors.toList());
    }

    public List<ChatHistoryEntry> getHistorySince(String department, Instant since) {
        Deque<ChatHistoryEntry> history = chatHistory.get(department);
        if (history == null) {
            return List.of();
        }

        return history.stream()
            .filter(e -> e.timestamp().isAfter(since))
            .collect(Collectors.toList());
    }

    public void userJoined(String department, String userId) {
        onlineUsers.computeIfAbsent(department, k -> ConcurrentHashMap.newKeySet())
            .add(userId);
        lastActivity.put(department, Instant.now());

        log.info("User joined department chat: dept={}, user={}", department, userId);
    }

    public void userLeft(String department, String userId) {
        Set<String> users = onlineUsers.get(department);
        if (users != null) {
            users.remove(userId);
            if (users.isEmpty()) {
                onlineUsers.remove(department);
            }
        }

        log.info("User left department chat: dept={}, user={}", department, userId);
    }

    public Set<String> getOnlineUsers(String department) {
        return new HashSet<>(onlineUsers.getOrDefault(department, Set.of()));
    }

    public int getOnlineCount(String department) {
        Set<String> users = onlineUsers.get(department);
        return users != null ? users.size() : 0;
    }

    public Map<String, Integer> getAllOnlineCounts() {
        Map<String, Integer> counts = new HashMap<>();
        onlineUsers.forEach((dept, users) -> counts.put(dept, users.size()));
        return counts;
    }

    public Instant getLastActivity(String department) {
        return lastActivity.get(department);
    }

    public void clearHistory(String department) {
        chatHistory.remove(department);
        log.info("Cleared chat history for department: {}", department);
    }

    public void cleanupOfflineUsers() {
        Instant now = Instant.now();
        onlineUsers.forEach((dept, users) -> {
            users.removeIf(userId -> {
                return false;
            });
        });
    }

    public boolean canAccessDepartment(String department, String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        
        String sessionId = authorization.substring(7);
        Optional<AuthSession> sessionOpt = authService.validateSession(sessionId);
        
        if (sessionOpt.isEmpty()) {
            return false;
        }
        
        AuthContext ctx = sessionOpt.get().authContext();
        
        if ("public".equals(department)) {
            return true;
        }
        
        if ("chairman".equals(department)) {
            return ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder();
        }
        
        if (ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder()) {
            return true;
        }
        
        if (ctx.getAccessLevel() == AccessLevel.CHAT_ONLY) {
            return false;
        }
        
        String userDept = ctx.getDepartment();
        return userDept != null && userDept.equalsIgnoreCase(department);
    }

    public DepartmentChatSummary getSummary(String department) {
        Deque<ChatHistoryEntry> history = chatHistory.get(department);
        Set<String> users = onlineUsers.get(department);
        Instant activity = lastActivity.get(department);

        return new DepartmentChatSummary(
            department,
            history != null ? history.size() : 0,
            users != null ? users.size() : 0,
            activity
        );
    }

    public List<DepartmentChatSummary> getAllSummaries() {
        Set<String> departments = new HashSet<>();
        departments.addAll(chatHistory.keySet());
        departments.addAll(onlineUsers.keySet());

        return departments.stream()
            .map(this::getSummary)
            .collect(Collectors.toList());
    }

    public record ChatHistoryEntry(
        String messageId,
        String department,
        String userId,
        String userName,
        String content,
        String metadata,
        Instant timestamp
    ) {
        public String toJson() {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                return "{}";
            }
        }
    }

    public record DepartmentChatSummary(
        String department,
        int messageCount,
        int onlineCount,
        Instant lastActivity
    ) {}
}
