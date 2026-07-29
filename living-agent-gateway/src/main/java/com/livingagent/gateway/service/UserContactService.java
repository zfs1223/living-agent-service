package com.livingagent.gateway.service;

import com.livingagent.core.database.entity.UserContact;
import com.livingagent.core.database.repository.UserContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 联系人服务
 *
 * 职责:
 * - 获取会话列表
 * - 设置免打扰/置顶/隐藏
 */
@Service
public class UserContactService {

    private static final Logger log = LoggerFactory.getLogger(UserContactService.class);

    private final UserContactRepository userContactRepository;

    public UserContactService(UserContactRepository userContactRepository) {
        this.userContactRepository = userContactRepository;
    }

    /**
     * 获取会话列表
     * @param userId 用户ID
     * @param includeHidden 是否包含隐藏会话
     */
    public List<UserContact> getContactList(String userId, boolean includeHidden) {
        if (includeHidden) {
            return userContactRepository.findByUserId(userId);
        } else {
            return userContactRepository
                .findByUserIdAndHiddenFalseOrderByPinnedDescLastMessageTimeDesc(userId);
        }
    }

    /**
     * 设置免打扰
     */
    @Transactional
    public UserContact setMuted(String userId, String contactId, boolean muted) {
        Optional<UserContact> contactOpt = userContactRepository.findByUserIdAndContactId(userId, contactId);
        if (contactOpt.isEmpty()) {
            log.warn("[IM] Contact not found for muted: userId={}, contactId={}", userId, contactId);
            return null;
        }

        UserContact contact = contactOpt.get();
        contact.setMuted(muted);
        contact.setUpdatedAt(Instant.now());
        return userContactRepository.save(contact);
    }

    /**
     * 设置置顶
     */
    @Transactional
    public UserContact setPinned(String userId, String contactId, boolean pinned) {
        Optional<UserContact> contactOpt = userContactRepository.findByUserIdAndContactId(userId, contactId);
        if (contactOpt.isEmpty()) {
            log.warn("[IM] Contact not found for pinned: userId={}, contactId={}", userId, contactId);
            return null;
        }

        UserContact contact = contactOpt.get();
        contact.setPinned(pinned);
        contact.setUpdatedAt(Instant.now());
        return userContactRepository.save(contact);
    }

    /**
     * 设置隐藏
     */
    @Transactional
    public UserContact setHidden(String userId, String contactId, boolean hidden) {
        Optional<UserContact> contactOpt = userContactRepository.findByUserIdAndContactId(userId, contactId);
        if (contactOpt.isEmpty()) {
            log.warn("[IM] Contact not found for hidden: userId={}, contactId={}", userId, contactId);
            return null;
        }

        UserContact contact = contactOpt.get();
        contact.setHidden(hidden);
        contact.setUpdatedAt(Instant.now());
        return userContactRepository.save(contact);
    }

    /**
     * 获取总未读数
     */
    public long getTotalUnreadCount(String userId) {
        return userContactRepository.countByUserIdAndUnreadCountGreaterThan(userId, 0);
    }
}
