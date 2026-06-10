package com.livingagent.core.tool.impl.enterprise;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import com.livingagent.core.tool.*;

/**
 * 飞书审批工具 — 审批实例与审批定义管理相关 action。
 * <p>
 * Actions: create_approval, get_approval, cancel_approval,
 *          get_approval_definition_list, create_approval_definition
 */
public class FeishuApprovalTool extends AbstractFeishuTool {

    private static final String NAME = "feishu_approval";
    private static final String DESCRIPTION = "飞书审批管理工具，支持审批实例与审批定义管理";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "enterprise_management";

    private final ToolSchema schema;

    public FeishuApprovalTool(String appId, String appSecret) {
        super(appId, appSecret, NAME);
        this.schema = ToolSchema.builder()
            .name(NAME)
            .description(DESCRIPTION)
            .parameter("action", "string",
                "操作类型: create_approval(发起审批), get_approval(查询审批), cancel_approval(取消审批), " +
                "get_approval_definition_list(获取审批定义列表), create_approval_definition(创建审批定义)",
                true)
            .parameter("approval_code", "string", "审批定义代码", false)
            .parameter("form_data", "string", "审批表单数据(JSON)", false)
            .parameter("form_content", "string", "审批表单内容(JSON)", false)
            .parameter("instance_id", "string", "审批实例ID", false)
            .parameter("name", "string", "审批定义名称", false)
            .parameter("description", "string", "描述", false)
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
            "approval_management",
            "approval_create",
            "approval_cancel",
            "full_permission"
        );
    }

    @Override
    protected ToolResult doExecute(String action, ToolParams params) throws Exception {
        return switch (action) {
            case "create_approval" -> createApproval(params);
            case "get_approval" -> getApproval(params);
            case "cancel_approval" -> cancelApproval(params);
            case "get_approval_definition_list" -> getApprovalDefinitionList(params);
            case "create_approval_definition" -> createApprovalDefinition(params);
            default -> ToolResult.failure("不支持的操作: " + action);
        };
    }

    private ToolResult createApproval(ToolParams params) throws Exception {
        String approvalCode = params.getString("approval_code");
        String formData = params.getString("form_data");

        if (approvalCode == null || approvalCode.isEmpty()) {
            return ToolResult.failure("缺少必要参数: approval_code");
        }

        String url = "https://open.feishu.cn/open-apis/approval/v4/instances";

        Map<String, Object> body = new HashMap<>();
        body.put("approval_code", approvalCode);
        if (formData != null && !formData.isEmpty()) {
            body.put("form", objectMapper.readValue(formData, Map.class));
        }

        var response = sendRequest(buildPostRequest(url, body));

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                return ToolResult.success(Map.of(
                    "instanceId", data != null ? data.get("instance_id") : null,
                    "message", "审批创建成功"
                ));
            } else {
                return ToolResult.failure("创建审批失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("创建审批失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult getApproval(ToolParams params) throws Exception {
        String instanceId = params.getString("instance_id");

        if (instanceId == null || instanceId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: instance_id");
        }

        String url = "https://open.feishu.cn/open-apis/approval/v4/instances/" + instanceId;

        var response = sendRequest(buildGetRequest(url));

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                return ToolResult.success(data);
            } else {
                return ToolResult.failure("查询审批失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("查询审批失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult cancelApproval(ToolParams params) throws Exception {
        String instanceId = params.getString("instance_id");

        if (instanceId == null || instanceId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: instance_id");
        }

        String url = "https://open.feishu.cn/open-apis/approval/v4/instances/" + instanceId + "/cancel";

        var request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
            .build();

        var response = sendRequest(request);
        return handleResponseWithMessage(response, "审批取消成功", "取消审批失败");
    }

    private ToolResult getApprovalDefinitionList(ToolParams params) throws Exception {
        Integer pageSizeInt = params.getInteger("page_size");
        int pageSize = pageSizeInt != null ? pageSizeInt : 50;

        String url = "https://open.feishu.cn/open-apis/approval/v4/approvals?page_size=" + pageSize;

        var response = sendRequest(buildGetRequest(url));

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

                List<Map<String, Object>> approvalList = new ArrayList<>();
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        Map<String, Object> approval = new HashMap<>();
                        approval.put("approvalCode", item.get("approval_code"));
                        approval.put("name", item.get("name"));
                        approval.put("description", item.get("description"));
                        approval.put("status", item.get("status"));
                        approvalList.add(approval);
                    }
                }

                return ToolResult.success(Map.of("approvals", approvalList));
            } else {
                return ToolResult.failure("获取审批定义列表失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("获取审批定义列表失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult createApprovalDefinition(ToolParams params) throws Exception {
        String approvalCode = params.getString("approval_code");
        String name = params.getString("name");
        String description = params.getString("description");
        String formContent = params.getString("form_content");

        if (approvalCode == null || approvalCode.isEmpty()) {
            return ToolResult.failure("缺少必要参数: approval_code");
        }

        if (name == null || name.isEmpty()) {
            return ToolResult.failure("缺少必要参数: name");
        }

        String url = "https://open.feishu.cn/open-apis/approval/v4/approvals";

        Map<String, Object> body = new HashMap<>();
        body.put("approval_code", approvalCode);
        body.put("name", name);

        if (description != null && !description.isEmpty()) {
            body.put("description", description);
        }

        if (formContent != null && !formContent.isEmpty()) {
            body.put("form_content", objectMapper.readValue(formContent, Map.class));
        }

        log.info("创建审批定义请求: {}", objectMapper.writeValueAsString(body));

        var response = sendRequest(buildPostRequest(url, body));

        log.info("创建审批定义响应: status={}, body={}", response.statusCode(), response.body());

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of(
                    "approvalCode", approvalCode,
                    "name", name,
                    "message", "审批定义创建成功"
                ));
            } else {
                return ToolResult.failure("创建审批定义失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("创建审批定义失败: HTTP " + response.statusCode());
        }
    }
}
