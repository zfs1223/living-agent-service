package com.livingagent.gateway.service;

import com.livingagent.core.database.entity.MessageEntity;
import com.livingagent.core.database.entity.UserContact;
import com.livingagent.core.database.repository.MessageRepository;
import com.livingagent.core.database.repository.UserContactRepository;
import com.livingagent.core.distributed.im.ImRedisService;
import com.livingagent.gateway.websocket.IMWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * IM 消息服务
 *
 * 职责:
 * - 发送消息、推送离线消息
 * - 标记已读、消息确认
 * - 更新联系人信息
 * - 撤回消息（委托给 MessageOperationService）
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository messageRepository;
    private final UserContactRepository userContactRepository;
    private final IMWebSocketHandler imWebSocketHandler;
    private final MessageOperationService messageOperationService;
    private final ImRedisService imRedisService;

    public MessageService(MessageRepository messageRepository,
                          UserContactRepository userContactRepository,
                          IMWebSocketHandler imWebSocketHandler,
                          @Lazy MessageOperationService messageOperationService,
                          ImRedisService imRedisService) {
        this.messageRepository = messageRepository;
        this.userContactRepository = userContactRepository;
        this.imWebSocketHandler = imWebSocketHandler;
        this.messageOperationService = messageOperationService;
        this.imRedisService = imRedisService;
    }

    /**
     * 发送消息请求 record
     */
    public record SendMessageRequest(
        String recipientId,
        String title,
        String content,
        String type,
        String extra,
        String replyToId
    ) {}

    /**
     * 发送消息: 创建消息 → 设置 replyToId/gapCount → save → updateContact(双方) → 推送 → 离线通知
     */
    @Transactional
    public MessageEntity sendMessage(String senderId, SendMessageRequest request) {
        String messageId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        MessageEntity message = new MessageEntity(
            messageId,
            request.recipientId(),
            senderId,
            request.type() != null ? request.type() : "TEXT",
            request.title(),
            request.content(),
            request.extra(),
            now,
            null
        );

        // 设置 IM 扩展字段
        if (request.replyToId() != null && !request.replyToId().isBlank()) {
            message.setReplyToId(request.replyToId());
            // 计算 gapCount: 引用消息与当前消息之间有多少条消息
            int gapCount = calculateGapCount(request.replyToId(), senderId, request.recipientId());
            message.setGapCount(gapCount);
        }

        MessageEntity saved = messageRepository.save(message);

        // 更新双方联系人信息
        updateContact(senderId, request.recipientId(), saved);
        updateContact(request.recipientId(), senderId, saved);

        // 构建推送载荷
        Map<String, Object> pushPayload = buildMessagePayload(saved);

        // 推送给接收者
        boolean recipientOnline = imWebSocketHandler.pushToUser(request.recipientId(), pushPayload);

        // 推送给发送者（多设备同步）
        imWebSocketHandler.pushToUser(senderId, pushPayload);

        // P90: 在途追踪 + ACK 待确认
        imRedisService.trackInflight(messageId, pushPayload);
        imRedisService.pendingAck(request.recipientId(), messageId);

        // 离线通知
        if (!recipientOnline) {
            log.info("[IM] Recipient offline, message stored for later push: recipientId={}, messageId={}",
                request.recipientId(), messageId);
        }

        log.info("[IM] Message sent: from={}, to={}, messageId={}, type={}",
            senderId, request.recipientId(), messageId, request.type());

        return saved;
    }

    /**
     * 推送离线消息: 查询未读消息 → 逐条推送
     */
    public void pushOfflineMessages(String userId) {
        List<MessageEntity> unreadMessages = messageRepository
            .findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(userId);

        if (unreadMessages.isEmpty()) return;

        log.info("[IM] Pushing {} offline messages for userId={}", unreadMessages.size(), userId);

        for (MessageEntity message : unreadMessages) {
            Map<String, Object> payload = buildMessagePayload(message);
            imWebSocketHandler.pushToUser(userId, payload);

            // P90: 在途追踪 + ACK 待确认(离线消息推送后同样需要 ACK)
            imRedisService.trackInflight(message.getMessageId(), payload);
            imRedisService.pendingAck(userId, message.getMessageId());
        }
    }

    /**
     * 标记已读: 更新 user_contacts + markAllAsReadByContact
     */
    @Transactional
    public void markAsRead(String userId, String contactId) {
        Instant now = Instant.now();

        // 标记消息已读
        int updated = messageRepository.markAsReadByContact(userId, contactId, now);

        // 更新联系人未读数
        Optional<UserContact> contactOpt = userContactRepository.findByUserIdAndContactId(userId, contactId);
        if (contactOpt.isPresent()) {
            UserContact contact = contactOpt.get();
            contact.setUnreadCount(0);
            contact.setLastReadAt(now);
            contact.setUpdatedAt(now);
            userContactRepository.save(contact);
        }

        log.info("[IM] Marked as read: userId={}, contactId={}, messagesUpdated={}", userId, contactId, updated);
    }

    /**
     * 消息确认: P90 在途追踪 + ACK 确认
     */
    public void acknowledgeMessage(String userId, String messageId) {
        // P90: 移除在途消息 + 确认 ACK
        imRedisService.acknowledgeInflight(messageId);
        imRedisService.confirmAck(userId, messageId);

        log.debug("[IM] ACK confirmed: userId={}, messageId={}", userId, messageId);
    }

    /**
     * 撤回消息: 委托给 MessageOperationService
     */
    public void recallMessage(String messageId, String operatorId) {
        messageOperationService.recallMessage(messageId, operatorId);
    }

    /**
     * 更新/创建联系人信息
     */
    @Transactional
    public void updateContact(String userId, String contactId, MessageEntity message) {
        Optional<UserContact> existingOpt = userContactRepository.findByUserIdAndContactId(userId, contactId);

        if (existingOpt.isPresent()) {
            UserContact contact = existingOpt.get();
            contact.setLastMessageId(message.getMessageId());
            contact.setLastMessageContent(truncateContent(message.getContent()));
            contact.setLastMessageTime(message.getCreatedAt());
            contact.setUpdatedAt(Instant.now());

            // 如果是消息的接收者，增加未读数
            if (userId.equals(message.getRecipientId()) && !userId.equals(message.getSenderId())) {
                contact.setUnreadCount(contact.getUnreadCount() + 1);
            }

            userContactRepository.save(contact);
        } else {
            UserContact contact = new UserContact();
            contact.setUserId(userId);
            contact.setContactId(contactId);
            contact.setContactType("PRIVATE");
            contact.setLastMessageId(message.getMessageId());
            contact.setLastMessageContent(truncateContent(message.getContent()));
            contact.setLastMessageTime(message.getCreatedAt());
            contact.setUnreadCount(userId.equals(message.getRecipientId()) ? 1 : 0);
            contact.setCreatedAt(Instant.now());
            contact.setUpdatedAt(Instant.now());
            userContactRepository.save(contact);
        }
    }

    /**
     * 检查免打扰
     */
    public boolean isMuted(String userId, String contactId) {
        return userContactRepository.findByUserIdAndContactId(userId, contactId)
            .map(UserContact::getMuted)
            .orElse(false);
    }

    // ---- 内部方法 ----

    private int calculateGapCount(String replyToId, String senderId, String recipientId) {
        try {
            // 查询引用消息之后的消息数
            Optional<MessageEntity> replyMsgOpt = messageRepository.findByMessageId(replyToId);
            if (replyMsgOpt.isEmpty()) return 0;

            Instant replyTime = replyMsgOpt.get().getCreatedAt();
            // 简化实现: 查询两个用户之间的所有消息并计数
            List<MessageEntity> messages = messageRepository.findChatMessages(recipientId, senderId);
            int count = 0;
            for (MessageEntity m : messages) {
                if (m.getCreatedAt().isAfter(replyTime)) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            log.warn("[IM] Failed to calculate gapCount: {}", e.getMessage());
            return 0;
        }
    }

    private String truncateContent(String content) {
        if (content == null) return null;
        return content.length() > 100 ? content.substring(0, 100) + "..." : content;
    }

    private Map<String, Object> buildMessagePayload(MessageEntity message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "NEW_MESSAGE");
        payload.put("messageId", message.getMessageId());
        payload.put("senderId", message.getSenderId());
        payload.put("recipientId", message.getRecipientId());
        payload.put("messageType", message.getType());
        payload.put("content", message.getContent());
        payload.put("createdAt", message.getCreatedAt().toString());
        if (message.getReplyToId() != null) {
            payload.put("replyToId", message.getReplyToId());
        }
        if (message.getGapCount() != null && message.getGapCount() > 0) {
            payload.put("gapCount", message.getGapCount());
        }
        if (message.getMetadataJson() != null) {
            payload.put("extra", message.getMetadataJson());
        }
        return payload;
    }
}
