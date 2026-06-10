package com.livingagent.core.tool.impl.enterprise;

import java.net.URI;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import com.livingagent.core.tool.*;

/**
 * 飞书通讯录工具 — 用户管理、部门管理相关 action。
 * <p>
 * Actions: get_user, create_user, update_user, delete_user, send_activation,
 *          get_department, create_department, update_department, delete_department, upload_file
 */
public class FeishuContactTool extends AbstractFeishuTool {

    private static final String NAME = "feishu_contact";
    private static final String DESCRIPTION = "飞书通讯录管理工具，支持用户管理、部门管理、文件上传";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "enterprise_management";

    private final ToolSchema schema;

    public FeishuContactTool(String appId, String appSecret) {
        super(appId, appSecret, NAME);
        this.schema = ToolSchema.builder()
            .name(NAME)
            .description(DESCRIPTION)
            .parameter("action", "string",
                "操作类型: get_user(获取用户), create_user(创建用户), update_user(更新用户), " +
                "delete_user(删除用户), send_activation(发送激活邀请), " +
                "get_department(获取部门), create_department(创建部门), update_department(更新部门), " +
                "delete_department(删除部门), upload_file(上传文件)",
                true)
            .parameter("user_id", "string", "用户ID", false)
            .parameter("name", "string", "部门名称/用户名称", false)
            .parameter("department_id", "string", "部门ID", false)
            .parameter("parent_department_id", "string", "父部门ID", false)
            .parameter("leader_user_id", "string", "部门主管用户ID", false)
            .parameter("order", "string", "部门排序", false)
            .parameter("create_group_chat", "boolean", "是否创建部门群", false)
            .parameter("department_hrbps", "string", "部门HRBP用户ID列表", false)
            .parameter("email", "string", "邮箱", false)
            .parameter("mobile", "string", "手机号", false)
            .parameter("employee_no", "string", "工号", false)
            .parameter("employee_type", "integer", "员工类型", false)
            .parameter("gender", "integer", "性别", false)
            .parameter("file_path", "string", "文件路径", false)
            .parameter("file_type", "string", "文件类型", false)
            .parameter("file_name", "string", "文件名称", false)
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
            "user_management",
            "user_create",
            "user_update",
            "user_delete",
            "department_management",
            "department_create",
            "department_update",
            "department_delete",
            "file_upload",
            "full_permission"
        );
    }

    @Override
    protected ToolResult doExecute(String action, ToolParams params) throws Exception {
        return switch (action) {
            case "get_user" -> getUser(params);
            case "create_user" -> createUser(params);
            case "update_user" -> updateUser(params);
            case "delete_user" -> deleteUser(params);
            case "send_activation" -> sendActivationEmail(params);
            case "get_department" -> getDepartment(params);
            case "create_department" -> createDepartment(params);
            case "update_department" -> updateDepartment(params);
            case "delete_department" -> deleteDepartment(params);
            case "upload_file" -> uploadFile(params);
            default -> ToolResult.failure("不支持的操作: " + action);
        };
    }

    private ToolResult getUser(ToolParams params) throws Exception {
        String userId = params.getString("user_id");
        if (userId == null || userId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: user_id");
        }

        String url = "https://open.feishu.cn/open-apis/contact/v3/users/" + userId;
        var response = sendRequest(buildGetRequest(url));

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                Map<String, Object> user = (Map<String, Object>) data.get("user");

                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("openId", user.get("open_id"));
                userInfo.put("userId", user.get("user_id"));
                userInfo.put("name", user.get("name"));
                userInfo.put("email", user.get("email"));
                userInfo.put("mobile", user.get("mobile"));
                userInfo.put("status", user.get("status"));

                return ToolResult.success(userInfo);
            } else {
                return ToolResult.failure("获取用户信息失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("获取用户信息失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult getDepartment(ToolParams params) throws Exception {
        String departmentId = params.getString("department_id");
        if (departmentId == null || departmentId.isEmpty()) {
            departmentId = "0";
        }

        Integer pageSizeInt = params.getInteger("page_size");
        int pageSize = pageSizeInt != null ? pageSizeInt : 50;

        String url = "https://open.feishu.cn/open-apis/contact/v3/departments/" + departmentId
            + "/children?department_id_type=open_department_id&page_size=" + pageSize;

        var response = sendRequest(buildGetRequest(url));

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

                List<Map<String, Object>> departments = new ArrayList<>();
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        Map<String, Object> dept = new HashMap<>();
                        dept.put("openDepartmentId", item.get("open_department_id"));
                        dept.put("name", item.get("name"));
                        dept.put("parentDepartmentId", item.get("parent_department_id"));
                        dept.put("leaderUserId", item.get("leader_user_id"));
                        dept.put("memberCount", item.get("member_count"));
                        departments.add(dept);
                    }
                }

                return ToolResult.success(Map.of("departments", departments));
            } else {
                return ToolResult.failure("获取部门信息失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("获取部门信息失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult createDepartment(ToolParams params) throws Exception {
        String name = params.getString("name");
        String parentDepartmentId = params.getString("parent_department_id");
        String departmentId = params.getString("department_id");
        String leaderUserId = params.getString("leader_user_id");
        String order = params.getString("order");
        Boolean createGroupChat = params.getBoolean("create_group_chat");
        String departmentHrbps = params.getString("department_hrbps");

        if (name == null || name.isEmpty()) {
            return ToolResult.failure("缺少必要参数: name");
        }

        if (parentDepartmentId == null || parentDepartmentId.isEmpty()) {
            parentDepartmentId = "0";
        }

        String url = "https://open.feishu.cn/open-apis/contact/v3/departments?department_id_type=open_department_id&user_id_type=open_id";

        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("parent_department_id", parentDepartmentId);

        if (departmentId != null && !departmentId.isEmpty()) {
            body.put("department_id", departmentId);
        }
        if (leaderUserId != null && !leaderUserId.isEmpty()) {
            body.put("leader_user_id", leaderUserId);
        }
        if (order != null && !order.isEmpty()) {
            body.put("order", order);
        }
        if (createGroupChat != null) {
            body.put("create_group_chat", createGroupChat);
        }
        if (departmentHrbps != null && !departmentHrbps.isEmpty()) {
            body.put("department_hrbps", List.of(departmentHrbps.split(",")));
        }

        String requestBody = objectMapper.writeValueAsString(body);
        log.info("创建部门请求: {}", requestBody);

        var response = sendRequest(buildPostRequest(url, body));

        log.info("创建部门响应: status={}, body={}", response.statusCode(), response.body());

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                Map<String, Object> dept = (Map<String, Object>) data.get("department");
                return ToolResult.success(Map.of(
                    "departmentId", dept != null ? dept.get("open_department_id") : null,
                    "name", name,
                    "message", "部门创建成功"
                ));
            } else {
                return ToolResult.failure("创建部门失败: " + result.get("msg") + " (code: " + result.get("code") + ")");
            }
        } else {
            return ToolResult.failure("创建部门失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult updateDepartment(ToolParams params) throws Exception {
        String departmentId = params.getString("department_id");
        String name = params.getString("name");
        String leaderUserId = params.getString("leader_user_id");

        if (departmentId == null || departmentId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: department_id");
        }

        String url = "https://open.feishu.cn/open-apis/contact/v3/departments/" + departmentId;

        Map<String, Object> body = new HashMap<>();
        if (name != null && !name.isEmpty()) {
            body.put("name", name);
        }
        if (leaderUserId != null && !leaderUserId.isEmpty()) {
            body.put("leader_user_id", leaderUserId);
        }

        var response = sendRequest(buildPutRequest(url, body));
        return handleResponseWithMessage(response, "部门更新成功", "更新部门失败");
    }

    private ToolResult deleteDepartment(ToolParams params) throws Exception {
        String departmentId = params.getString("department_id");

        if (departmentId == null || departmentId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: department_id");
        }

        String url = "https://open.feishu.cn/open-apis/contact/v3/departments/" + departmentId;

        var response = sendRequest(buildDeleteRequest(url));
        return handleResponseWithMessage(response, "部门删除成功", "删除部门失败");
    }

    private ToolResult createUser(ToolParams params) throws Exception {
        String name = params.getString("name");
        String departmentId = params.getString("department_id");
        String email = params.getString("email");
        String mobile = params.getString("mobile");
        String employeeNo = params.getString("employee_no");
        Integer employeeType = params.getInteger("employee_type");
        Integer gender = params.getInteger("gender");

        if (name == null || name.isEmpty()) {
            return ToolResult.failure("缺少必要参数: name");
        }

        String url = "https://open.feishu.cn/open-apis/contact/v3/users?user_id_type=open_id&department_id_type=open_department_id";

        Map<String, Object> body = new HashMap<>();
        body.put("name", name);

        if (departmentId != null && !departmentId.isEmpty()) {
            body.put("department_ids", List.of(departmentId));
        }
        if (email != null && !email.isEmpty()) {
            body.put("email", email);
        }
        if (mobile != null && !mobile.isEmpty()) {
            body.put("mobile", mobile);
        }
        if (employeeNo != null && !employeeNo.isEmpty()) {
            body.put("employee_no", employeeNo);
        }
        body.put("employee_type", employeeType != null ? employeeType : 1);
        if (gender != null) {
            body.put("gender", gender);
        }

        String requestBody = objectMapper.writeValueAsString(body);
        log.info("创建用户请求体: {}", requestBody);

        var response = sendRequest(buildPostRequest(url, body));

        log.info("创建用户响应状态: {}, 响应体: {}", response.statusCode(), response.body());

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", "用户创建成功", "data", result.get("data")));
            } else {
                return ToolResult.failure("创建用户失败[code=" + code + "]: " + result.get("msg") + ", 响应: " + response.body());
            }
        } else {
            return ToolResult.failure("创建用户失败: HTTP " + response.statusCode() + ", 响应: " + response.body());
        }
    }

    private ToolResult updateUser(ToolParams params) throws Exception {
        String userId = params.getString("user_id");
        String name = params.getString("name");
        String departmentId = params.getString("department_id");
        String email = params.getString("email");
        String mobile = params.getString("mobile");
        Integer employeeType = params.getInteger("employee_type");

        if (userId == null || userId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: user_id");
        }

        String url = "https://open.feishu.cn/open-apis/contact/v3/users/" + userId
            + "?user_id_type=open_id&department_id_type=open_department_id";

        Map<String, Object> body = new HashMap<>();
        if (name != null && !name.isEmpty()) {
            body.put("name", name);
        }
        if (departmentId != null && !departmentId.isEmpty()) {
            body.put("department_ids", List.of(departmentId));
        }
        if (email != null && !email.isEmpty()) {
            body.put("email", email);
        }
        if (mobile != null && !mobile.isEmpty()) {
            body.put("mobile", mobile);
        }
        if (employeeType != null) {
            body.put("employee_type", employeeType);
        }

        if (body.isEmpty()) {
            return ToolResult.failure("没有需要更新的字段");
        }

        String requestBody = objectMapper.writeValueAsString(body);
        log.info("更新用户请求体: {}", requestBody);

        var response = sendRequest(buildPatchRequest(url, body));

        log.info("更新用户响应状态: {}, 响应体: {}", response.statusCode(), response.body());

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", "用户更新成功", "data", result.get("data")));
            } else {
                return ToolResult.failure("更新用户失败[code=" + code + "]: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("更新用户失败: HTTP " + response.statusCode() + ", 响应: " + response.body());
        }
    }

    private ToolResult sendActivationEmail(ToolParams params) throws Exception {
        String userId = params.getString("user_id");

        if (userId == null || userId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: user_id");
        }

        String url = "https://open.feishu.cn/open-apis/contact/v3/users/" + userId
            + "/resend_invitation?user_id_type=open_id";

        Map<String, Object> body = new HashMap<>();
        String requestBody = objectMapper.writeValueAsString(body);
        log.info("发送激活邀请请求体: {}", requestBody);

        var response = sendRequest(buildPostRequest(url, body));

        log.info("发送激活邀请响应状态: {}, 响应体: {}", response.statusCode(), response.body());

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", "激活邀请发送成功"));
            } else {
                return ToolResult.failure("发送激活邀请失败[code=" + code + "]: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("发送激活邀请失败: HTTP " + response.statusCode() + ", 响应: " + response.body());
        }
    }

    private ToolResult deleteUser(ToolParams params) throws Exception {
        String userId = params.getString("user_id");

        if (userId == null || userId.isEmpty()) {
            return ToolResult.failure("缺少必要参数: user_id");
        }

        String url = "https://open.feishu.cn/open-apis/contact/v3/users/" + userId;

        var response = sendRequest(buildDeleteRequest(url));
        return handleResponseWithMessage(response, "用户删除成功", "删除用户失败");
    }

    private ToolResult uploadFile(ToolParams params) throws Exception {
        String filePath = params.getString("file_path");
        String fileType = params.getString("file_type");
        String fileName = params.getString("file_name");

        if (filePath == null || filePath.isEmpty()) {
            return ToolResult.failure("缺少必要参数: file_path");
        }

        if (fileType == null) {
            fileType = "stream";
        }

        if (fileName == null) {
            int lastSlash = filePath.lastIndexOf("/");
            if (lastSlash == -1) {
                lastSlash = filePath.lastIndexOf("\\");
            }
            fileName = lastSlash >= 0 ? filePath.substring(lastSlash + 1) : "file";
        }

        java.io.File file = new java.io.File(filePath);
        if (!file.exists()) {
            return ToolResult.failure("文件不存在: " + filePath);
        }

        String url = "https://open.feishu.cn/open-apis/drive/v1/medias/upload_all";

        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();

        java.nio.file.Path path = file.toPath();
        byte[] fileBytes = java.nio.file.Files.readAllBytes(path);

        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file_name\"\r\n\r\n");
        sb.append(fileName).append("\r\n");
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"parent_type\"\r\n\r\n");
        sb.append("ccm_import_open").append("\r\n");
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"parent_key\"\r\n\r\n");
        sb.append("ccm_import_open").append("\r\n");
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"size\"\r\n\r\n");
        sb.append(fileBytes.length).append("\r\n");
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
          .append(fileName).append("\"\r\n");
        sb.append("Content-Type: application/octet-stream\r\n\r\n");

        byte[] headerBytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] footerBytes = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);

        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        outputStream.write(headerBytes);
        outputStream.write(fileBytes);
        outputStream.write(footerBytes);
        byte[] multipartBody = outputStream.toByteArray();

        var request = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(multipartBody))
            .build();

        var response = sendRequest(request);

        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                return ToolResult.success(Map.of(
                    "fileKey", data != null ? data.get("file_key") : null,
                    "fileName", fileName,
                    "fileSize", fileBytes.length,
                    "message", "文件上传成功"
                ));
            } else {
                return ToolResult.failure("文件上传失败: " + result.get("msg"));
            }
        } else {
            return ToolResult.failure("文件上传失败: HTTP " + response.statusCode());
        }
    }
}
