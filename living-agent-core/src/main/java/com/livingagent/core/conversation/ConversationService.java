package com.livingagent.core.conversation;

import com.livingagent.core.database.entity.DepartmentConversationEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 部门对话服务接口 */
public interface ConversationService {
    Optional<DepartmentConversationEntity> getConversation(String conversationId);
    List<DepartmentConversationEntity> listConversations(String ownerUserId, String departmentCode, List<String> statuses, int limit, int offset);
    DepartmentConversationEntity createConversation(String ownerUserId, String departmentCode, String tenantId, String title);
    DepartmentConversationEntity updateConversation(String conversationId, String title, String status);
    DepartmentConversationEntity archiveConversation(String conversationId);
    DepartmentConversationEntity restoreConversation(String conversationId);
    DepartmentConversationEntity deleteConversation(String conversationId);
    void destroyConversation(String conversationId);
    void touchConversation(String conversationId);
    void updateLastMessage(String conversationId, Instant messageAt);
}
