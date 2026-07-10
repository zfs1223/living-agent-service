package com.livingagent.core.conversation.feedback;

import com.livingagent.core.knowledge.KnowledgeManager;
import com.livingagent.core.knowledge.KnowledgeType;
import com.livingagent.core.knowledge.Importance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 闭环46-P46-B: 对话归档服务
 * 对话归档时自动沉淀经验到知识库
 */
public class ConversationArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ConversationArchiveService.class);

    private final KnowledgeManager knowledgeManager;

    public ConversationArchiveService(KnowledgeManager knowledgeManager) {
        this.knowledgeManager = knowledgeManager;
    }

    public void archiveAndExtractKnowledge(String conversationId, String summary, String department) {
        log.info("[闭环46] 对话归档+知识沉淀: id={}, dept={}", conversationId, department);
        try {
            if (knowledgeManager != null && summary != null && !summary.isBlank()) {
                knowledgeManager.storeDomain(
                    "conversation-archive://" + conversationId,
                    summary,
                    KnowledgeType.EXPERIENCE,
                    Importance.MEDIUM
                );
            }
        } catch (Exception e) {
            log.warn("[闭环46] 知识沉淀失败: id={}, error={}", conversationId, e.getMessage());
        }
    }
}
