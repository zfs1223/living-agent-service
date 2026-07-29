package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.UserContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserContactRepository extends JpaRepository<UserContact, Long> {

    Optional<UserContact> findByUserIdAndContactId(String userId, String contactId);

    List<UserContact> findByUserId(String userId);

    List<UserContact> findByUserIdAndHiddenFalseOrderByPinnedDescLastMessageTimeDesc(String userId);

    long countByUserIdAndUnreadCountGreaterThan(String userId, int unreadCount);
}
