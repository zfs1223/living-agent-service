package com.livingagent.core.conversation.impl;

import com.livingagent.core.conversation.ConversationService;
import com.livingagent.core.conversation.ConversationStatus;
import com.livingagent.core.database.entity.DepartmentConversationEntity;
import com.livingagent.core.database.repository.DepartmentConversationRepository;
import com.livingagent.core.util.IdUtils;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ConversationServiceImpl implements ConversationService {
    private static final Logger log = LoggerFactory.getLogger(ConversationServiceImpl.class);

    private final DepartmentConversationRepository repository;

    public ConversationServiceImpl(DepartmentConversationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<DepartmentConversationEntity> getConversation(String conversationId) {
        return repository.findByConversationId(conversationId);
    }

    @Override
    public List<DepartmentConversationEntity> listConversations(String ownerUserId, String departmentCode, List<String> statuses, int limit, int offset) {
        if (departmentCode != null && !departmentCode.isBlank()) {
            return repository.findByOwnerUserIdAndDepartmentCodeAndStatusInOrderByLastActivityAtDesc(ownerUserId, departmentCode, statuses)
                    .stream().skip(offset).limit(limit).toList();
        }
        return repository.findByOwnerUserIdAndStatusInOrderByLastActivityAtDesc(ownerUserId, statuses)
                .stream().skip(offset).limit(limit).toList();
    }

    @Override
    @Transactional
    public DepartmentConversationEntity createConversation(String ownerUserId, String departmentCode, String tenantId, String title) {
        DepartmentConversationEntity conv = new DepartmentConversationEntity();
        conv.setConversationId(IdUtils.generateConversationId());
        conv.setOwnerUserId(ownerUserId);
        conv.setDepartmentCode(departmentCode);
        conv.setTenantId(tenantId);
        conv.setTitle(title != null ? title : "新对话");
        conv.setStatus(ConversationStatus.ACTIVE.getDbValue());
        conv.setCreatedAt(Instant.now());
        conv.setLastActivityAt(Instant.now());
        conv.setRetentionPolicy("default");
        return repository.save(conv);
    }

    @Override
    @Transactional
    public DepartmentConversationEntity updateConversation(String conversationId, String title, String status) {
        return repository.findByConversationId(conversationId).map(conv -> {
            if (title != null) conv.setTitle(title);
            if (status != null) conv.setStatus(status);
            conv.setUpdatedAt(Instant.now());
            return repository.save(conv);
        }).orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
    }

    @Override
    @Transactional
    public DepartmentConversationEntity archiveConversation(String conversationId) {
        return repository.findByConversationId(conversationId).map(conv -> {
            conv.setStatus(ConversationStatus.ARCHIVED.getDbValue());
            conv.setArchivedAt(Instant.now());
            conv.setUpdatedAt(Instant.now());
            return repository.save(conv);
        }).orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
    }

    @Override
    @Transactional
    public DepartmentConversationEntity restoreConversation(String conversationId) {
        return repository.findByConversationId(conversationId).map(conv -> {
            conv.setStatus(ConversationStatus.ACTIVE.getDbValue());
            conv.setArchivedAt(null);
            conv.setUpdatedAt(Instant.now());
            return repository.save(conv);
        }).orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
    }

    @Override
    @Transactional
    public DepartmentConversationEntity deleteConversation(String conversationId) {
        return repository.findByConversationId(conversationId).map(conv -> {
            conv.setStatus(ConversationStatus.DELETED.getDbValue());
            conv.setDeletedAt(Instant.now());
            conv.setUpdatedAt(Instant.now());
            return repository.save(conv);
        }).orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
    }

    @Override
    @Transactional
    public void destroyConversation(String conversationId) {
        repository.findByConversationId(conversationId).ifPresent(conv -> {
            conv.setStatus(ConversationStatus.DELETED.getDbValue());
            conv.setDestroyedAt(Instant.now());
            conv.setUpdatedAt(Instant.now());
            repository.save(conv);
            log.info("Destroyed conversation: {}", conversationId);
        });
    }

    @Override
    @Transactional
    public void touchConversation(String conversationId) {
        repository.findByConversationId(conversationId).ifPresent(conv -> {
            conv.setLastActivityAt(Instant.now());
            repository.save(conv);
        });
    }

    @Override
    @Transactional
    public void updateLastMessage(String conversationId, Instant messageAt) {
        repository.findByConversationId(conversationId).ifPresent(conv -> {
            conv.setLastMessageAt(messageAt);
            conv.setLastActivityAt(Instant.now());
            repository.save(conv);
        });
    }
}
