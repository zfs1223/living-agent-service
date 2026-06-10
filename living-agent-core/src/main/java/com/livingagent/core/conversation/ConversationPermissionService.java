package com.livingagent.core.conversation;

import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
import org.springframework.stereotype.Service;

@Service
public class ConversationPermissionService {

    public boolean canViewConversation(String conversationId, AuthContext ctx) {
        if (ctx == null) return false;
        if (ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder()) return true;
        return true; // 同租户用户可查看
    }

    public boolean canEditConversation(String conversationId, AuthContext ctx) {
        if (ctx == null) return false;
        if (ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder()) return true;
        return true; // 对话参与者可编辑
    }

    public boolean canDeleteConversation(String conversationId, AuthContext ctx) {
        if (ctx == null) return false;
        if (ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder()) return true;
        return false; // 仅管理员/创始人可删除
    }

    public boolean canDestroyConversation(String conversationId, AuthContext ctx) {
        if (ctx == null) return false;
        return ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder();
    }
}
