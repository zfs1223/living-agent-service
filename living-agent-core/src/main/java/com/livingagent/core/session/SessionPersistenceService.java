package com.livingagent.core.session;

import com.livingagent.core.session.ConnectionContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 会话持久化服务接口 */
public interface SessionPersistenceService {
    /** 保存会话上下文 */
    void saveSession(String sessionId, ConnectionContext context);
    /** 获取会话上下文 */
    Optional<ConnectionContext> getSession(String sessionId);
    /** 删除会话上下文 */
    void deleteSession(String sessionId);
    /** 获取用户最近的会话列表 */
    List<String> getRecentSessionsByUser(String userId, int limit);
    /** 获取租户下活跃的会话数 */
    int countActiveSessionsByTenant(String tenantId);
    /** 清理过期会话 */
    void cleanupExpiredSessions(long maxIdleMs);
}
