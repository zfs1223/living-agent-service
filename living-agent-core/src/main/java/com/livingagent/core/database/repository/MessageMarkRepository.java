package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.MessageMark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageMarkRepository extends JpaRepository<MessageMark, Long> {

    Optional<MessageMark> findByMessageIdAndUserIdAndMarkType(String messageId, String userId, String markType);

    long countByMessageIdAndMarkTypeAndStatus(String messageId, String markType, String status);

    List<MessageMark> findByMessageIdAndStatus(String messageId, String status);
}
