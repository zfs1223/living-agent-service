package com.livingagent.gateway.service;

import com.livingagent.core.database.entity.MessageEntity;
import com.livingagent.core.database.entity.MessageMark;
import com.livingagent.core.database.repository.MessageMarkRepository;
import com.livingagent.core.database.repository.MessageRepository;
import com.livingagent.gateway.websocket.IMWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 消息操作服务
 *
 * 职责:
 * - 撤回消息 (2分钟时限)
 * - 回复消息
 * - 标记消息 (LIKE/REPORT/IMPORTANT)
 */
@Service
public class MessageOperationService {

    private static final Logger log = LoggerFactory.getLogger(MessageOperationService.class);

    private static final Duration RECALL_TIME_LIMIT = Duration.ofMinutes(2);

    private final MessageRepository messageRepository;
    private final MessageMarkRepository messageMarkRepository;
    private final IMWebSocketHandler imWebSocketHandler;
    private final MessageService messageService;

    public MessageOperationService(MessageRepository messageRepository,
                                    MessageMarkRepository messageMarkRepository,
                                    IMWebSocketHandler imWebSocketHandler,
                                    MessageService messageService) {
        this.messageRepository = messageRepository;
        this.messageMarkRepository = messageMarkRepository;
        this.imWebSocketHandler = imWebSocketHandler;
        this.messageService = messageService;
    }

    /**
     * 回复消息请求 record
     */
    public record ReplyMessageRequest(
        String recipientId,
        String title,
        String content,
        String type,
        String extra,
        String replyToId
    ) {}

    /**
     * 撤回消息: 校验权限+2分钟时限 → 软删除(deletedAt/deletedBy) → 通知接收者 MESSAGE_RECALLED
     */
    @Transactional
    public void recallMessage(String messageId, String operatorId) {
        Optional<MessageEntity> messageOpt = messageRepository.findByMessageId(messageId);
        if (messageOpt.isEmpty()) {
            throw new IllegalArgumentException("消息不存在: " + messageId);
        }

        MessageEntity message = messageOpt.get();

        // 权限校验: 只有发送者可以撤回
        if (!message.getSenderId().equals(operatorId)) {
            throw new SecurityException("无权撤回他人消息");
        }

        // 2分钟时限校验
        Duration timeSinceSend = Duration.between(message.getCreatedAt(), Instant.now());
        if (timeSinceSend.compareTo(RECALL_TIME_LIMIT) > 0) {
            throw new IllegalStateException("消息已超过2分钟，无法撤回");
        }

        // 已撤回检查
        if (message.isRecalled()) {
            throw new IllegalStateException("消息已被撤回");
        }

        // 软删除
        Instant now = Instant.now();
        message.setDeletedAt(now);
        message.setDeletedBy(operatorId);
        messageRepository.save(message);

        // 通知接收者
        Map<String, Object> recallPayload = new LinkedHashMap<>();
        recallPayload.put("type", "MESSAGE_RECALLED");
        recallPayload.put("messageId", messageId);
        recallPayload.put("recalledBy", operatorId);
        recallPayload.put("recalledAt", now.toString());

        imWebSocketHandler.pushToUser(message.getRecipientId(), recallPayload);
        imWebSocketHandler.pushToUser(message.getSenderId(), recallPayload);

        log.info("[IM] Message recalled: messageId={}, operatorId={}", messageId, operatorId);
    }

    /**
     * 回复消息: 构造 SendMessageRequest 包含 replyToId → 调用 messageService.sendMessage
     */
    @Transactional
    public MessageEntity replyToMessage(String senderId, ReplyMessageRequest request) {
        MessageService.SendMessageRequest sendRequest = new MessageService.SendMessageRequest(
            request.recipientId(),
            request.title(),
            request.content(),
            request.type() != null ? request.type() : "TEXT",
            request.extra(),
            request.replyToId()
        );
        return messageService.sendMessage(senderId, sendRequest);
    }

    /**
     * 标记消息: 查找/创建 MessageMark → LIKE 计数检查徽章
     */
    @Transactional
    public MessageMark markMessage(String messageId, String userId, String markType) {
        // 检查是否已存在相同标记
        Optional<MessageMark> existingOpt = messageMarkRepository
            .findByMessageIdAndUserIdAndMarkType(messageId, userId, markType);

        if (existingOpt.isPresent()) {
            MessageMark existing = existingOpt.get();
            if ("ACTIVE".equals(existing.getStatus())) {
                // 取消标记
                existing.setStatus("CANCELLED");
                messageMarkRepository.save(existing);

                log.info("[IM] Mark cancelled: messageId={}, userId={}, markType={}", messageId, userId, markType);
                return existing;
            } else {
                // 重新激活
                existing.setStatus("ACTIVE");
                messageMarkRepository.save(existing);

                log.info("[IM] Mark reactivated: messageId={}, userId={}, markType={}", messageId, userId, markType);
                return existing;
            }
        }

        // 创建新标记
        MessageMark mark = new MessageMark(
            messageId, userId, markType, "ACTIVE", Instant.now()
        );
        MessageMark saved = messageMarkRepository.save(mark);

        // LIKE 计数检查徽章
        if ("LIKE".equals(markType)) {
            long likeCount = messageMarkRepository.countByMessageIdAndMarkTypeAndStatus(messageId, "LIKE", "ACTIVE");
            if (likeCount == 1) {
                // 通知发送者获得首个点赞
                Optional<MessageEntity> messageOpt = messageRepository.findByMessageId(messageId);
                if (messageOpt.isPresent()) {
                    Map<String, Object> badgePayload = new LinkedHashMap<>();
                    badgePayload.put("type", "MESSAGE_BADGE");
                    badgePayload.put("messageId", messageId);
                    badgePayload.put("badgeType", "FIRST_LIKE");
                    badgePayload.put("likeCount", likeCount);
                    imWebSocketHandler.pushToUser(messageOpt.get().getSenderId(), badgePayload);
                }
            }
        }

        log.info("[IM] Message marked: messageId={}, userId={}, markType={}", messageId, userId, markType);
        return saved;
    }
}
