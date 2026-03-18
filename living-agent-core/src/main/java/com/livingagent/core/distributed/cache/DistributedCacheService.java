package com.livingagent.core.distributed.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class DistributedCacheService {

    private static final Logger log = LoggerFactory.getLogger(DistributedCacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SESSION_PREFIX = "session:";
    private static final String KNOWLEDGE_PREFIX = "knowledge:";
    private static final String NEURON_STATE_PREFIX = "neuron:state:";
    private static final String USER_CONTEXT_PREFIX = "user:context:";
    private static final String HOT_KNOWLEDGE_PREFIX = "hot:knowledge:";

    public DistributedCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void storeSession(String sessionId, Object sessionData, Duration ttl) {
        String key = SESSION_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, sessionData, ttl);
        log.debug("Stored session: {} with TTL: {}", sessionId, ttl);
    }

    public Optional<Object> getSession(String sessionId) {
        String key = SESSION_PREFIX + sessionId;
        Object data = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(data);
    }

    public void deleteSession(String sessionId) {
        String key = SESSION_PREFIX + sessionId;
        redisTemplate.delete(key);
        log.debug("Deleted session: {}", sessionId);
    }

    public boolean hasSession(String sessionId) {
        String key = SESSION_PREFIX + sessionId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void refreshSessionTTL(String sessionId, Duration ttl) {
        String key = SESSION_PREFIX + sessionId;
        redisTemplate.expire(key, ttl);
    }

    public void cacheKnowledge(String knowledgeId, Object knowledge, Duration ttl) {
        String key = KNOWLEDGE_PREFIX + knowledgeId;
        redisTemplate.opsForValue().set(key, knowledge, ttl);
        log.debug("Cached knowledge: {}", knowledgeId);
    }

    public Optional<Object> getCachedKnowledge(String knowledgeId) {
        String key = KNOWLEDGE_PREFIX + knowledgeId;
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public void cacheHotKnowledge(String brainDomain, String key, Object value, Duration ttl) {
        String redisKey = HOT_KNOWLEDGE_PREFIX + brainDomain + ":" + key;
        redisTemplate.opsForValue().set(redisKey, value, ttl);
    }

    public Optional<Object> getHotKnowledge(String brainDomain, String key) {
        String redisKey = HOT_KNOWLEDGE_PREFIX + brainDomain + ":" + key;
        return Optional.ofNullable(redisTemplate.opsForValue().get(redisKey));
    }

    public void cacheNeuronState(String neuronId, Object state) {
        String key = NEURON_STATE_PREFIX + neuronId;
        redisTemplate.opsForValue().set(key, state);
        log.debug("Cached neuron state: {}", neuronId);
    }

    public Optional<Object> getNeuronState(String neuronId) {
        String key = NEURON_STATE_PREFIX + neuronId;
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public void updateUserContext(String userId, Object context) {
        String key = USER_CONTEXT_PREFIX + userId;
        redisTemplate.opsForValue().set(key, context, Duration.ofHours(24));
    }

    public Optional<Object> getUserContext(String userId) {
        String key = USER_CONTEXT_PREFIX + userId;
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public boolean acquireLock(String lockKey, String ownerId, Duration ttl) {
        String key = "lock:" + lockKey;
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(key, ownerId, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public boolean releaseLock(String lockKey, String ownerId) {
        String key = "lock:" + lockKey;
        Object currentOwner = redisTemplate.opsForValue().get(key);
        if (ownerId.equals(currentOwner)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }

    public boolean extendLock(String lockKey, String ownerId, Duration ttl) {
        String key = "lock:" + lockKey;
        Object currentOwner = redisTemplate.opsForValue().get(key);
        if (ownerId.equals(currentOwner)) {
            redisTemplate.expire(key, ttl);
            return true;
        }
        return false;
    }

    public long incrementCounter(String counterKey) {
        String key = "counter:" + counterKey;
        Long value = redisTemplate.opsForValue().increment(key);
        return value != null ? value : 0;
    }

    public long incrementCounter(String counterKey, Duration ttl) {
        String key = "counter:" + counterKey;
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1) {
            redisTemplate.expire(key, ttl);
        }
        return value != null ? value : 0;
    }

    public void addToSet(String setKey, String... values) {
        String key = "set:" + setKey;
        redisTemplate.opsForSet().add(key, (Object[]) values);
    }

    public Set<Object> getSetMembers(String setKey) {
        String key = "set:" + setKey;
        return redisTemplate.opsForSet().members(key);
    }

    public boolean isSetMember(String setKey, String value) {
        String key = "set:" + setKey;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
    }

    public void addToSortedSet(String setKey, String value, double score) {
        String key = "zset:" + setKey;
        redisTemplate.opsForZSet().add(key, value, score);
    }

    public Set<Object> getTopFromSortedSet(String setKey, long count) {
        String key = "zset:" + setKey;
        return redisTemplate.opsForZSet().reverseRange(key, 0, count - 1);
    }

    public void publish(String channel, Object message) {
        redisTemplate.convertAndSend(channel, message);
        log.debug("Published message to channel: {}", channel);
    }

    public void setHashField(String hashKey, String field, Object value) {
        String key = "hash:" + hashKey;
        redisTemplate.opsForHash().put(key, field, value);
    }

    public Object getHashField(String hashKey, String field) {
        String key = "hash:" + hashKey;
        return redisTemplate.opsForHash().get(key, field);
    }

    public Map<Object, Object> getHashAll(String hashKey) {
        String key = "hash:" + hashKey;
        return redisTemplate.opsForHash().entries(key);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public void deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
