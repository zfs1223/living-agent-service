package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.TraceEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TraceEventRepository extends JpaRepository<TraceEventEntity, UUID> {

    List<TraceEventEntity> findByRequestIdOrderByTimestampAsc(String requestId);

    List<TraceEventEntity> findByStageOrderByTimestampDesc(String stage);

    List<TraceEventEntity> findByActorOrderByTimestampDesc(String actor);

    List<TraceEventEntity> findAllByOrderByTimestampDesc();

    List<TraceEventEntity> findTop1000ByOrderByTimestampDesc();

    void deleteByTimestampBefore(java.time.Instant cutoff);

    // P1-6.2: 通过 taskKey 和 executionId 交叉查询
    List<TraceEventEntity> findByTaskKeyOrderByTimestampAsc(String taskKey);

    List<TraceEventEntity> findByExecutionIdOrderByTimestampAsc(String executionId);

    // P18-A: traceId 查询（用于写入验证）
    List<TraceEventEntity> findByTraceId(String traceId);

    /**
     * P18-A: 写入验证 — 保存后立即查询确认
     */
    default TraceEventEntity saveAndVerify(TraceEventEntity entity) {
        TraceEventEntity saved = save(entity);
        if (saved.getId() == null) {
            throw new IllegalStateException("TraceEvent save verification failed: id is null");
        }
        if (!existsById(saved.getId())) {
            throw new IllegalStateException("TraceEvent save verification failed: " + saved.getId());
        }
        return saved;
    }
}
