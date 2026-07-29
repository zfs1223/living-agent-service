package com.livingagent.core.distributed.im;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * IM Redis 服务 — P90 在途追踪 + P91 背压控制
 *
 * 职责:
 * - P90: 消息在途追踪(60s TTL) + ACK 待确认队列(120s TTL)
 * - P91: 背压控制(滑动窗口 5条/秒) + 全局在线状态(心跳续期 35s TTL)
 *
 * 注意:
 * - RedisTemplate 使用 Jackson2JsonRedisSerializer + DefaultTyping,
 *   因此 payload 存储为 Object(Map) 而非 String, 避免 JSON 双重序列化
 * - backpressure 计数器使用 read-check-set 模式,
 *   因为 increment() 无法作用于 Jackson 序列化的 Long 值
 */
@Service
public class ImRedisService {

    private static final Logger log = LoggerFactory.getLogger(ImRedisService.class);

    private final RedisTemplate<String, Object> redisTemplate;

    // Key 前缀
    private static final String INFLIGHT_PREFIX = "im:inflight:";      // im:inflight:{messageId} → message payload (TTL 60s)
    private static final String ACK_PENDING_PREFIX = "im:ack:";         // im:ack:{userId}:{messageId} → timestamp (TTL 120s)
    private static final String BACKPRESSURE_PREFIX = "im:bp:";         // im:bp:{userId} → count (TTL 1s, 滑动窗口)
    private static final String ONLINE_PREFIX = "im:online:";           // im:online:{userId} → serverId (TTL 35s, 心跳续期)

    public ImRedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ========================
    // P90: 在途追踪
    // ========================

    /**
     * 将消息放入在途队列(TTL 60s)，等待接收者 ACK
     *
     * @param messageId 消息ID
     * @param payload   推送载荷(Map 结构)，由 Jackson 序列化存储
     */
    public void trackInflight(String messageId, Object payload) {
        redisTemplate.opsForValue().set(INFLIGHT_PREFIX + messageId, payload, Duration.ofSeconds(60));
    }

    /**
     * 接收者 ACK 后移除在途消息
     *
     * @return true 表示消息仍在途(成功确认)，false 表示已过期或不存在
     */
    public boolean acknowledgeInflight(String messageId) {
        String key = INFLIGHT_PREFIX + messageId;
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /**
     * 获取所有未 ACK 的在途消息 key 集合(用于重试扫描)
     */
    public Set<String> scanInflightMessages() {
        return redisTemplate.keys(INFLIGHT_PREFIX + "*");
    }

    /**
     * 获取在途消息的 payload
     *
     * @return 反序列化后的对象(Map)，或 null
     */
    public Object getInflightPayload(String messageId) {
        return redisTemplate.opsForValue().get(INFLIGHT_PREFIX + messageId);
    }

    // ========================
    // P90: ACK 待确认队列
    // ========================

    /**
     * 记录消息等待 ACK(TTL 120s)
     */
    public void pendingAck(String userId, String messageId) {
        String key = ACK_PENDING_PREFIX + userId + ":" + messageId;
        redisTemplate.opsForValue().set(key, Instant.now().toString(), Duration.ofSeconds(120));
    }

    /**
     * 确认 ACK，移除待确认记录
     *
     * @return true 表示成功确认，false 表示记录已过期或不存在
     */
    public boolean confirmAck(String userId, String messageId) {
        String key = ACK_PENDING_PREFIX + userId + ":" + messageId;
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /**
     * 扫描某用户所有待 ACK 消息的 key
     */
    public Set<String> scanPendingAcks(String userId) {
        return redisTemplate.keys(ACK_PENDING_PREFIX + userId + ":*");
    }

    // ========================
    // P91: 背压控制(滑动窗口)
    // ========================

    /**
     * 检查是否允许发送(5条/秒限制)
     *
     * 实现方式: read-check-set, 因为 RedisTemplate 的 Jackson2JsonRedisSerializer
     * 无法使用 Redis 原生 INCRBY 命令(JSON 序列化的值非纯数字)
     *
     * @param userId 用户ID
     * @return true 允许发送, false 超过频率限制
     */
    public boolean checkBackpressure(String userId) {
        String key = BACKPRESSURE_PREFIX + userId;
        Object count = redisTemplate.opsForValue().get(key);
        if (count == null) {
            redisTemplate.opsForValue().set(key, 1L, Duration.ofSeconds(1));
            return true;
        }
        long current;
        if (count instanceof Number) {
            current = ((Number) count).longValue();
        } else {
            // Jackson 反序列化可能返回 Integer 或 Long, 兜底解析
            current = Long.parseLong(count.toString());
        }
        if (current >= 5) {
            return false;  // 超过频率限制
        }
        redisTemplate.opsForValue().set(key, current + 1, Duration.ofSeconds(1));
        return true;
    }

    // ========================
    // P91: 在线状态
    // ========================

    /**
     * 标记用户在线(心跳续期, TTL 35s)
     *
     * @param userId   用户ID
     * @param serverId 当前服务器标识
     */
    public void markOnline(String userId, String serverId) {
        redisTemplate.opsForValue().set(ONLINE_PREFIX + userId, serverId, Duration.ofSeconds(35));
    }

    /**
     * 标记用户离线
     */
    public void markOffline(String userId) {
        redisTemplate.delete(ONLINE_PREFIX + userId);
    }

    /**
     * 检查用户是否在线(全局维度, 跨服务器)
     */
    public boolean isOnlineGlobally(String userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ONLINE_PREFIX + userId));
    }

    /**
     * 获取用户所在服务器
     *
     * @return serverId 或 null(离线)
     */
    public String getOnlineServer(String userId) {
        Object val = redisTemplate.opsForValue().get(ONLINE_PREFIX + userId);
        return val != null ? val.toString() : null;
    }
}
