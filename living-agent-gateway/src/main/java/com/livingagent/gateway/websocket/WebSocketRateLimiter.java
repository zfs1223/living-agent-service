package com.livingagent.gateway.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * WebSocket 消息频率限制器
 * 防止恶意客户端高频发送消息导致 LLM 调用过载
 */
@Component
public class WebSocketRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(WebSocketRateLimiter.class);

    // 每会话每秒最多 5 条消息
    private static final double PERMITS_PER_SECOND = 5.0;
    // 每分钟最多 30 条消息（防止突发）
    private static final int MAX_BURST_PER_MINUTE = 30;

    private final Map<String, SessionRateLimiter> perSessionLimiters = new ConcurrentHashMap<>();

    /**
     * 尝试获取消息发送许可
     * @return true 如果允许发送，false 如果被限流
     */
    public boolean tryAcquire(String sessionId) {
        SessionRateLimiter limiter = perSessionLimiters.computeIfAbsent(sessionId, 
            k -> new SessionRateLimiter(sessionId));
        return limiter.tryAcquire();
    }

    /**
     * 移除会话的限流器（连接关闭时调用）
     */
    public void removeSession(String sessionId) {
        perSessionLimiters.remove(sessionId);
        log.debug("Removed rate limiter for session: {}", sessionId);
    }

    /**
     * 每会话的限流器
     */
    private static class SessionRateLimiter {
        private final String sessionId;
        private final AtomicLong lastRefillTime = new AtomicLong(System.currentTimeMillis());
        // 初始化时给予完整令牌数，允许新连接立即发送消息
        private final AtomicInteger permits = new AtomicInteger((int) PERMITS_PER_SECOND);
        private final AtomicLong minuteWindowStart = new AtomicLong(System.currentTimeMillis());
        private final AtomicInteger minuteCount = new AtomicInteger(0);

        SessionRateLimiter(String sessionId) {
            this.sessionId = sessionId;
        }

        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            
            // 检查每分钟限制
            long minuteStart = minuteWindowStart.get();
            if (now - minuteStart > 60_000) {
                // 新的一分钟，重置计数
                if (minuteWindowStart.compareAndSet(minuteStart, now)) {
                    minuteCount.set(0);
                }
            }
            
            if (minuteCount.get() >= MAX_BURST_PER_MINUTE) {
                log.warn("Rate limit exceeded (minute): sessionId={}, count={}/{}", 
                    sessionId, minuteCount.get(), MAX_BURST_PER_MINUTE);
                return false;
            }

            // 检查每秒限制（令牌桶算法）
            long lastRefill = lastRefillTime.get();
            long elapsed = now - lastRefill;
            
            if (elapsed > 1000) {
                // 超过 1 秒，补充令牌
                int newPermits = (int) (elapsed / 1000.0 * PERMITS_PER_SECOND);
                int current = permits.get();
                int maxPermits = (int) PERMITS_PER_SECOND;
                int updated = Math.min(current + newPermits, maxPermits);
                
                if (lastRefillTime.compareAndSet(lastRefill, now)) {
                    permits.set(updated);
                }
            }

            // 尝试消耗一个令牌
            int current = permits.get();
            if (current > 0) {
                if (permits.compareAndSet(current, current - 1)) {
                    minuteCount.incrementAndGet();
                    return true;
                }
            }

            log.warn("Rate limit exceeded (second): sessionId={}, permits={}/{}", 
                sessionId, current, (int) PERMITS_PER_SECOND);
            return false;
        }
    }
}
