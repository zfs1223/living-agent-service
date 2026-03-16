package com.livingagent.gateway.dialogue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class DialogueSession {
    
    private final String sessionId;
    private final String userId;
    private final List<DialogueMessage> messages;
    private final LocalDateTime createdAt;
    private volatile LocalDateTime lastActiveAt;
    private volatile String language;
    private volatile String templateId;
    private volatile String role;
    private final ReentrantLock lock;
    
    public DialogueSession(String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.messages = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.lastActiveAt = this.createdAt;
        this.language = "zh-CN";
        this.templateId = "friendly-chat";
        this.role = "assistant";
        this.lock = new ReentrantLock();
    }
    
    public static DialogueSession create(String userId) {
        String sessionId = UUID.randomUUID().toString();
        return new DialogueSession(sessionId, userId);
    }
    
    public void addMessage(String role, String content) {
        addMessage(role, content, null);
    }
    
    public void addMessage(String role, String content, Map<String, Object> metadata) {
        lock.lock();
        try {
            messages.add(new DialogueMessage(role, content, LocalDateTime.now(), metadata));
            lastActiveAt = LocalDateTime.now();
        } finally {
            lock.unlock();
        }
    }
    
    public void addSystemMessage(String content) {
        addMessage("system", content);
    }
    
    public void addUserMessage(String content) {
        addMessage("user", content);
    }
    
    public void addAssistantMessage(String content) {
        addMessage("assistant", content);
    }
    
    public void addToolMessage(String toolName, String result) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("toolName", toolName);
        addMessage("tool", result, metadata);
    }
    
    public void trimHistory(int maxSize) {
        lock.lock();
        try {
            while (messages.size() > maxSize) {
                if (messages.get(0).role().equals("system")) {
                    if (messages.size() > 1) {
                        messages.remove(1);
                    } else {
                        break;
                    }
                } else {
                    messages.remove(0);
                }
            }
        } finally {
            lock.unlock();
        }
    }
    
    public List<DialogueMessage> getMessages() {
        lock.lock();
        try {
            return new ArrayList<>(messages);
        } finally {
            lock.unlock();
        }
    }
    
    public List<DialogueMessage> getRecentMessages(int count) {
        lock.lock();
        try {
            int start = Math.max(0, messages.size() - count);
            return new ArrayList<>(messages.subList(start, messages.size()));
        } finally {
            lock.unlock();
        }
    }
    
    public void clearHistory() {
        lock.lock();
        try {
            messages.clear();
        } finally {
            lock.unlock();
        }
    }
    
    public void touch() {
        this.lastActiveAt = LocalDateTime.now();
    }
    
    public long getInactiveSeconds() {
        return java.time.Duration.between(lastActiveAt, LocalDateTime.now()).getSeconds();
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public String getTemplateId() {
        return templateId;
    }
    
    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public int getMessageCount() {
        lock.lock();
        try {
            return messages.size();
        } finally {
            lock.unlock();
        }
    }
}
