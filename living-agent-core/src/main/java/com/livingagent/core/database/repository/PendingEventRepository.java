package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.PendingEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PendingEventRepository extends JpaRepository<PendingEventEntity, String> {

    List<PendingEventEntity> findBySessionIdAndSentFalseOrderByTimestampAsc(String sessionId);

    @Query("SELECT e FROM PendingEventEntity e WHERE e.sessionId = :sessionId AND e.sent = false ORDER BY e.timestamp ASC")
    List<PendingEventEntity> findPendingEvents(@Param("sessionId") String sessionId);

    @Modifying
    @Query("UPDATE PendingEventEntity e SET e.sent = true, e.sentAt = :sentAt WHERE e.eventId = :eventId")
    void markAsSent(@Param("eventId") String eventId, @Param("sentAt") Instant sentAt);

    @Modifying
    @Query("DELETE FROM PendingEventEntity e WHERE e.sessionId = :sessionId AND e.sent = true")
    void deleteSentEvents(@Param("sessionId") String sessionId);

    @Modifying
    @Query("DELETE FROM PendingEventEntity e WHERE e.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") String sessionId);

    int countBySessionIdAndSentFalse(String sessionId);

    /** R6: 获取指定时间戳之后的待发送事件（游标重连补发） */
    List<PendingEventEntity> findBySessionIdAndTimestampGreaterThanAndSentFalseOrderByTimestampAsc(
        String sessionId, long afterTimestamp);

    /** R6: 获取会话最新事件（用于返回游标给客户端） */
    Optional<PendingEventEntity> findTopBySessionIdOrderByTimestampDesc(String sessionId);

    @Modifying
    @Query("DELETE FROM PendingEventEntity e WHERE e.timestamp < :threshold AND e.sent = true")
    void deleteOldSentEvents(@Param("threshold") long threshold);
}
