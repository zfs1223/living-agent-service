package com.livingagent.core.brain.impl;

import com.livingagent.core.provider.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 大脑会话历史管理器 — 从 AbstractBrain 中提取的会话历史管理职责。
 * <p>
 * 负责：会话对话历史缓存、过期驱逐、历史注入。
 */
public class BrainSessionManager {

    private static final Logger log = LoggerFactory.getLogger(BrainSessionManager.class);

    /** 每个会话保留的最大历史消息数 */
    public static final int MAX_SESSION_HISTORY = 50;

    /** 缓存最大会话数 */
    public static final int MAX_SESSION_CACHE_SIZE = 500;

    /** 会话过期时间（毫秒），30 分钟 */
    public static final long SESSION_EXPIRY_MS = 30 * 60 * 1000L;

    /** 会话对话历史缓存：sessionId -> 消息列表（线程安全） */
    private final Map<String, List<Provider.ChatMessage>> sessionHistoryCache = new ConcurrentHashMap<>();

    /** 会话最后访问时间 */
    private final Map<String, Long> sessionLastAccessTime = new ConcurrentHashMap<>();

    private final String brainId;

    public BrainSessionManager(String brainId) {
        this.brainId = brainId;
    }

    /**
     * 获取会话的对话历史。
     */
    public List<Provider.ChatMessage> getSessionHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return new ArrayList<>();
        }
        return sessionHistoryCache.getOrDefault(sessionId, new ArrayList<>());
    }

    /**
     * 更新会话的对话历史，添加用户消息和助手响应。
     */
    public void updateSessionHistory(String sessionId, String userMessage, String assistantResponse) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        List<Provider.ChatMessage> history = sessionHistoryCache.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());

        // 添加用户消息
        history.add(Provider.ChatMessage.user(userMessage));

        // 添加助手响应
        if (assistantResponse != null && !assistantResponse.isBlank()) {
            history.add(Provider.ChatMessage.assistant(assistantResponse));
        }

        // 限制历史长度，保留最近的消息
        while (history.size() > MAX_SESSION_HISTORY) {
            history.remove(0);
        }

        // 更新最后访问时间
        sessionLastAccessTime.put(sessionId, System.currentTimeMillis());

        // 驱逐过期会话
        evictExpiredSessions();

        log.debug("Brain {} session {} history updated, size={}", brainId, sessionId, history.size());
    }

    /**
     * 驱逐过期的会话历史缓存。
     * 超过 SESSION_EXPIRY_MS 未访问的会话将被清除；
     * 若会话总数超过 MAX_SESSION_CACHE_SIZE，优先清除最旧的会话。
     */
    public void evictExpiredSessions() {
        long now = System.currentTimeMillis();

        // 清除过期会话
        sessionLastAccessTime.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > SESSION_EXPIRY_MS) {
                sessionHistoryCache.remove(entry.getKey());
                log.debug("Brain {} evicted expired session: {}", brainId, entry.getKey());
                return true;
            }
            return false;
        });

        // 若仍超过最大容量，按访问时间排序清除最旧的
        if (sessionHistoryCache.size() > MAX_SESSION_CACHE_SIZE) {
            List<String> sortedSessions = sessionLastAccessTime.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
            int toRemove = sessionHistoryCache.size() - MAX_SESSION_CACHE_SIZE;
            for (int i = 0; i < toRemove && i < sortedSessions.size(); i++) {
                String sid = sortedSessions.get(i);
                sessionHistoryCache.remove(sid);
                sessionLastAccessTime.remove(sid);
                log.debug("Brain {} evicted oldest session: {}", brainId, sid);
            }
        }
    }

    /**
     * 清除会话的对话历史。
     */
    public void clearSessionHistory(String sessionId) {
        if (sessionId != null) {
            sessionHistoryCache.remove(sessionId);
            log.debug("Brain {} session {} history cleared", brainId, sessionId);
        }
    }

    /**
     * 注入会话历史（合并去重）。
     */
    public void injectSessionHistory(String sessionId, List<Provider.ChatMessage> history) {
        if (sessionId == null || sessionId.isBlank() || history == null || history.isEmpty()) {
            return;
        }
        List<Provider.ChatMessage> existing = sessionHistoryCache.get(sessionId);
        if (existing != null && !existing.isEmpty()) {
            log.debug("Brain {} session {} already has {} cached messages, merging with {} injected messages",
                brainId, sessionId, existing.size(), history.size());
            Set<String> existingContents = new HashSet<>();
            for (Provider.ChatMessage msg : existing) {
                existingContents.add(msg.role() + ":" + msg.content());
            }
            for (Provider.ChatMessage msg : history) {
                String key = msg.role() + ":" + msg.content();
                if (!existingContents.contains(key)) {
                    existing.add(msg);
                    existingContents.add(key);
                }
            }
            while (existing.size() > MAX_SESSION_HISTORY) {
                existing.remove(0);
            }
        } else {
            List<Provider.ChatMessage> injected = new ArrayList<>(history);
            while (injected.size() > MAX_SESSION_HISTORY) {
                injected.remove(0);
            }
            sessionHistoryCache.put(sessionId, injected);
        }
        log.info("Brain {} session {} history injected {} messages, total cached={}",
            brainId, sessionId, history.size(), sessionHistoryCache.getOrDefault(sessionId, List.of()).size());
    }
}
