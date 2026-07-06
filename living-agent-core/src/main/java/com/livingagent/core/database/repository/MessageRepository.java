package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    List<MessageEntity> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    List<MessageEntity> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(String recipientId);

    long countByRecipientIdAndReadAtIsNull(String recipientId);

    Optional<MessageEntity> findByMessageId(String messageId);

    @Modifying
    @Transactional
    @Query("UPDATE MessageEntity m SET m.readAt = :readAt WHERE m.recipientId = :recipientId AND m.readAt IS NULL")
    int markAllAsReadByRecipientId(String recipientId, Instant readAt);
}
