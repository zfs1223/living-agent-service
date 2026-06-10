package com.livingagent.core.tool.impl.enterprise;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

import com.livingagent.core.tool.*;

/**
 * 飞书消息工具 — 消息发送、卡片消息、群聊管理相关 action。
 * <p>
 * Actions: send_message, send_card, create_chat, get_chat, add_chat_members, get_token_info
 */
public class FeishuMessageTool extends AbstractFeishuTool {

    private static final String NAME = "feishu_message";
    private static final String DESCRIPTION = "飞书消息与群聊管理工具，支持发送消息、卡片消息、群聊创建与管理";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "enterprise_management";

    private final ToolSchema schema;

    public FeishuMessageTool(String appId, String appSecret) {
        super(appId, appSecret, NAME);
        this.schema = ToolSchema.builder()
            .name(NAME)
            .description(DESCRIPTION)
            .parameter("action", "string",
                "操作类型: send_message(发送消息), send_card(发送卡片), " +
                "create_chat(创建群聊), get_chat(获取群聊), add_chat_members(添加群成员), get_token_info(获取令牌信息)",
                true)
            .parameter("receive_id", "string", "接收者ID (用户ID或群ID)", false)
            .parameter("receive_id_type", "string", "接收者类型: open_id, user_id, union_id, chat_id, email", false)
            .parameter("msg_type", "string", "消息类型: text, post, image, file, card", false)
            .parameter("content", "string", "消息内容", false)
            .parameter("name", "string", "群聊名称", false)
            .parameter("description", "string", "群聊描述", false)
            .parameter("user_ids", "string", "用户ID列表(逗号分隔)", false)
            .parameter("chat_id", "string", "群聊ID", false)
            .build();
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDepartment() { return DEPARTMENT; }

    @Override
    public ToolSchema getSchema() { return schema; }

    @Override
    public List<String> getCapabilities() {
        return List.of(
            "messaging",
            "card_message",
            "full_permission"
        );
    }

    @Override
    protected ToolResult doExecute(String action, ToolParams params) throws Exception {
        return switch (action) {
            case "send_message" -> sendMessage(params);
            case "send_card" -> sendCard(params);
            case "create_chat" -> createChat(params);
            case "get_chat" -> getChat(params);
            case "add_chat_members" -> addChatMembers(params);
            case "get_token_info" -> getTokenInfo();
            default -> ToolResult.failure("不支持的操作: " + action);
        };
    }

    private ToolResult getTokenInfo() {
        return ToolResult.success(Map.of(
            "appId", appId.substring(0, 4) + "****",
            "hasToken", accessToken != null,
            "expiresAt", tokenExpireTime,
            "isExpired", System.currentTimeMillis() >= tokenExpireTime
        ));
    }

    private ToolResult sendMessage(ToolParams params) throws Exception {
        String receiveId = params.getString("receive_id");
        String receiveIdType = params.getString("receive_id_type");
        if (receiveIdType == null) receiveIdType = "open_id";
        String msgType = params.getString("msg_type");
        if (msgType == null) msgType = "text";
        String content = params.getString("content");

        if (receiveId == null || content == null) {
            return ToolResult.failure("缺少必要参数: receive_id 或 content");
        }

        String url = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=" + receiveIdType;

        Map<String, Object> body = new HashMap<>();
        body.put("receive_id", receiveId);
        body.put("msg_type", msgType);

        if ("text".equals(msgType)) {
            body.put("content", objectMapper.writeValueAsString(Map.of("text", content)));
        } else {
            body.put("content", content);
        }

        var response = sendRequest(buildPostRequest(url, body));

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                return ToolResult.success(Map.of(
                    "messageId", data != null ? data.get("message_id") : null,
                    "message", "消息发送成功"
                ));
            } else {
                return ToolResult.failure("发送消息失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("发送消息失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult sendCard(ToolParams params) throws Exception {
        String receiveId = params.getString("receive_id");
        String receiveIdType = params.getString("receive_id_type");
        if (receiveIdType == null) receiveIdType = "open_id";
        String cardContent = params.getString("content");

        if (receiveId == null || cardContent == null) {
            return ToolResult.failure("缺少必要参数: receive_id 或 content");
        }

        String url = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=" + receiveIdType;

        Map<String, Object> body = new HashMap<>();
        body.put("receive_id", receiveId);
        body.put("msg_type", "interactive");
        body.put("content", cardContent);

        var response = sendRequest(buildPostRequest(url, body));

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", "卡片消息发送成功"));
            } else {
                return ToolResult.failure("发送卡片消息失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("发送卡片消息失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult createChat(ToolParams params) throws Exception {
        String name = params.getString("name");
        String description = params.getString("description");
        String userIds = params.getString("user_ids");

        if (name == null || name.isEmpty()) {
            return ToolResult.failure("缺少必要参数: name");
        }

        String url = "https://open.feishu.cn/open-apis/im/v1/chats";

        Map<String, Object> body = new HashMap<>();
        body.put("name", name);

        if (description != null && !description.isEmpty()) {
            body.put("description", description);
        }

        if (userIds != null && !userIds.isEmpty()) {
            List<String> members = new java.util.ArrayList<>();
            for (String id : userIds.split(",")) {
                members.add(id.trim());
            }
            body.put("user_id_list", members);
        }

        var response = sendRequest(buildPostRequest(url, body));

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                return ToolResult.success(Map.of(
                    "chatId", data != null ? data.get("chat_id") : null,
                    "name", name,
                    "message", "群聊创建成功"
                ));
            } else {
                return ToolResult.failure("创建群聊失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("创建群聊失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult getChat(ToolParams params) throws Exception {
        String chatId = params.getString("chat_id");

        if (chatId == null || chatId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: chat_id");
        }

        String url = "https://open.feishu.cn/open-apis/im/v1/chats/" + chatId;

        var response = sendRequest(buildGetRequest(url));

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                return ToolResult.success(data);
            } else {
                return ToolResult.failure("获取群聊信息失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("获取群聊信息失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult addChatMembers(ToolParams params) throws Exception {
        String chatId = params.getString("chat_id");
        String userIds = params.getString("user_ids");

        if (chatId == null || chatId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: chat_id");
        }

        if (userIds == null || userIds.isEmpty()) {
            return ToolResult.failure("缺少必要参数: user_ids");
        }

        String url = "https://open.feishu.cn/open-apis/im/v1/chats/" + chatId + "/members";

        List<String> members = new java.util.ArrayList<>();
        for (String id : userIds.split(",")) {
            members.add(id.trim());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("user_id_list", members);

        var response = sendRequest(buildPostRequest(url, body));

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", "成员添加成功"));
            } else {
                return ToolResult.failure("添加成员失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("添加成员失败: HTTP " + response.statusCode());
        }
    }
}
