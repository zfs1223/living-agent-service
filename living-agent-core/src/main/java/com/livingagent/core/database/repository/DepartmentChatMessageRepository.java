package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.DepartmentChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface DepartmentChatMessageRepository extends JpaRepository<DepartmentChatMessageEntity, String> {

    List<DepartmentChatMessageEntity> findByDepartmentOrderByTimestampDesc(String department);

    List<DepartmentChatMessageEntity> findByDepartmentAndTimestampAfterOrderByTimestampAsc(String department, Instant since);

    List<DepartmentChatMessageEntity> findByDepartmentAndUserIdAndTimestampBetweenOrderByTimestampAsc(String department, String userId, Instant start, Instant end, org.springframework.data.domain.Pageable pageable);

    List<DepartmentChatMessageEntity> findByDepartmentAndUserIdOrderByTimestampAsc(String department, String userId, org.springframework.data.domain.Pageable pageable);

    List<DepartmentChatMessageEntity> findByDepartmentAndTimestampBetweenOrderByTimestampAsc(String department, Instant start, Instant end, org.springframework.data.domain.Pageable pageable);

    List<DepartmentChatMessageEntity> findByDepartmentOrderByTimestampDesc(String department, org.springframework.data.domain.Pageable pageable);

    List<DepartmentChatMessageEntity> findByConversationIdOrderByTimestampAsc(String conversationId);

    List<DepartmentChatMessageEntity> findByConversationIdAndTimestampAfterOrderByTimestampAsc(String conversationId, Instant since);

    List<DepartmentChatMessageEntity> findByConversationIdAndDeletedAtIsNullOrderByTimestampAsc(String conversationId);

    long countByDepartment(String department);

    long countByConversationId(String conversationId);

    void deleteByDepartmentAndTimestampBefore(String department, Instant before);

    @Query("SELECT DISTINCT m.department FROM DepartmentChatMessageEntity m")
    List<String> findDistinctDepartments();
}
