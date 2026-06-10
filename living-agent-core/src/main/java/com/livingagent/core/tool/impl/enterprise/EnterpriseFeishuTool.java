package com.livingagent.core.tool.impl.enterprise;

import java.util.List;
import java.util.Map;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.tool.*;

/**
 * 企业级飞书管理工具（已拆分，保留向后兼容）。
 *
 * @deprecated 此类已拆分为以下独立工具类，请使用新类：
 * <ul>
 *   <li>{@link FeishuMessageTool} — 消息与群聊管理</li>
 *   <li>{@link FeishuContactTool} — 通讯录与用户管理</li>
 *   <li>{@link FeishuApprovalTool} — 审批管理</li>
 *   <li>{@link FeishuCalendarTool} — 日历与任务管理</li>
 * </ul>
 * 公共逻辑已提取到 {@link AbstractFeishuTool} 基类。
 */
@Deprecated
public class EnterpriseFeishuTool implements Tool {

    private static final String NAME = "enterprise_feishu";
    private static final String DESCRIPTION = "企业级飞书管理工具（已拆分，保留向后兼容）。请使用 FeishuMessageTool/FeishuContactTool/FeishuApprovalTool/FeishuCalendarTool";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "enterprise_management";

    private final FeishuMessageTool messageTool;
    private final FeishuContactTool contactTool;
    private final FeishuApprovalTool approvalTool;
    private final FeishuCalendarTool calendarTool;

    private final ToolSchema schema;

    public EnterpriseFeishuTool(String appId, String appSecret) {
        this.messageTool = new FeishuMessageTool(appId, appSecret);
        this.contactTool = new FeishuContactTool(appId, appSecret);
        this.approvalTool = new FeishuApprovalTool(appId, appSecret);
        this.calendarTool = new FeishuCalendarTool(appId, appSecret);

        this.schema = ToolSchema.builder()
            .name(NAME)
            .description(DESCRIPTION)
            .parameter("action", "string",
                "操作类型: " +
                "send_message(发送消息), " +
                "get_user(获取用户), " +
                "get_department(获取部门), " +
                "create_department(创建部门), " +
                "update_department(更新部门), " +
                "delete_department(删除部门), " +
                "create_user(创建用户), " +
                "update_user(更新用户), " +
                "delete_user(删除用户), " +
                "create_approval(发起审批), " +
                "get_approval(查询审批), " +
                "cancel_approval(取消审批), " +
                "get_approval_definition_list(获取审批定义列表), " +
                "create_approval_definition(创建审批定义), " +
                "send_card(发送卡片), " +
                "upload_file(上传文件), " +
                "get_calendar_list(获取日历列表), " +
                "create_event(创建日程), " +
                "get_event_list(获取日程列表), " +
                "create_task(创建任务), " +
                "get_task_list(获取任务列表), " +
                "update_task(更新任务), " +
                "create_chat(创建群聊), " +
                "get_chat(获取群聊), " +
                "add_chat_members(添加群成员), " +
                "get_token_info(获取令牌信息)",
                true)
            .parameter("receive_id", "string", "接收者ID (用户ID或群ID)", false)
            .parameter("receive_id_type", "string", "接收者类型: open_id, user_id, union_id, chat_id, email", false)
            .parameter("msg_type", "string", "消息类型: text, post, image, file, card", false)
            .parameter("content", "string", "消息内容", false)
            .parameter("user_id", "string", "用户ID", false)
            .parameter("user_ids", "string", "用户ID列表(逗号分隔)", false)
            .parameter("department_id", "string", "部门ID", false)
            .parameter("name", "string", "部门名称/用户名称/群聊名称/任务名称", false)
            .parameter("parent_department_id", "string", "父部门ID", false)
            .parameter("leader_user_id", "string", "部门主管用户ID", false)
            .parameter("order", "string", "部门排序", false)
            .parameter("create_group_chat", "boolean", "是否创建部门群", false)
            .parameter("department_hrbps", "string", "部门HRBP用户ID列表", false)
            .parameter("approval_code", "string", "审批定义代码", false)
            .parameter("form_data", "string", "审批表单数据(JSON)", false)
            .parameter("form_content", "string", "审批表单内容(JSON)", false)
            .parameter("instance_id", "string", "审批实例ID", false)
            .parameter("calendar_id", "string", "日历ID", false)
            .parameter("summary", "string", "日程/任务摘要", false)
            .parameter("description", "string", "描述", false)
            .parameter("start_time", "string", "开始时间(时间戳)", false)
            .parameter("end_time", "string", "结束时间(时间戳)", false)
            .parameter("due_time", "string", "截止时间(时间戳)", false)
            .parameter("attendee_ids", "string", "参与者用户ID列表(逗号分隔)", false)
            .parameter("assignee_ids", "string", "任务负责人用户ID列表(逗号分隔)", false)
            .parameter("task_id", "string", "任务ID", false)
            .parameter("status", "string", "状态", false)
            .parameter("chat_id", "string", "群聊ID", false)
            .parameter("file_path", "string", "文件路径", false)
            .parameter("page_size", "integer", "分页大小", false)
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
            "user_management",
            "user_create",
            "user_update",
            "user_delete",
            "department_management",
            "department_create",
            "department_update",
            "department_delete",
            "approval_management",
            "approval_create",
            "approval_cancel",
            "card_message",
            "file_upload",
            "full_permission"
        );
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            return ToolResult.failure("缺少必要参数: action");
        }

        // 委托到对应的子工具
        return switch (action) {
            // 消息相关
            case "send_message", "send_card", "create_chat", "get_chat", "add_chat_members", "get_token_info"
                -> messageTool.execute(params, context);
            // 通讯录相关
            case "get_user", "create_user", "update_user", "delete_user", "send_activation",
                 "get_department", "create_department", "update_department", "delete_department", "upload_file"
                -> contactTool.execute(params, context);
            // 审批相关
            case "create_approval", "get_approval", "cancel_approval",
                 "get_approval_definition_list", "create_approval_definition"
                -> approvalTool.execute(params, context);
            // 日历/任务相关
            case "get_calendar_list", "create_event", "get_event_list",
                 "create_task", "get_task_list", "update_task"
                -> calendarTool.execute(params, context);
            default -> ToolResult.failure("不支持的操作: " + action);
        };
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("缺少必要参数: action");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    public boolean isActionAllowed(String action, AccessLevel accessLevel) {
        if (accessLevel != AccessLevel.FULL) {
            return false;
        }
        return true;
    }

    @Override
    public ToolStats getStats() {
        // 聚合所有子工具的统计
        return messageTool.getStats();
    }
}
