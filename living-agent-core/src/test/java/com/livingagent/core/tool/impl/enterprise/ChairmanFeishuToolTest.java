package com.livingagent.core.tool.impl.enterprise;

import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.Tool.ToolParams;
import com.livingagent.core.tool.ToolContext;
import com.livingagent.core.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChairmanFeishuToolTest {

    private static final String APP_ID = "cli_a920321f3b7a5cc1";
    private static final String APP_SECRET = "gmU0opRuS3Aps30BWR84ghkv7ELrkncG";

    private ChairmanFeishuTool tool;

    @BeforeEach
    void setUp() {
        tool = new ChairmanFeishuTool(APP_ID, APP_SECRET);
    }

    @Test
    @DisplayName("测试获取 Token 信息")
    void testGetTokenInfo() {
        System.out.println("========================================");
        System.out.println("测试获取 Token 信息...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of("action", "get_token_info"));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
        
        assertTrue(result.success(), "获取Token信息应该成功");
    }

    @Test
    @DisplayName("测试获取部门列表")
    void testGetDepartment() {
        System.out.println("========================================");
        System.out.println("测试获取飞书部门列表...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "get_department",
            "department_id", "0"
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        
        assertTrue(result.success(), "获取部门列表应该成功");
    }

    @Test
    @DisplayName("测试创建部门 - 技术部")
    void testCreateTechDepartment() {
        System.out.println("========================================");
        System.out.println("测试创建部门 - AI技术研发部...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_department",
            "name", "AI技术研发部",
            "parent_department_id", "0",
            "order", "1"
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
        
        if (!result.success()) {
            System.out.println("注意: 创建部门需要飞书应用有 'contact:department' 权限");
        }
    }

    @Test
    @DisplayName("测试创建部门 - 人力资源部")
    void testCreateHrDepartment() {
        System.out.println("========================================");
        System.out.println("测试创建部门 - AI人力资源部...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_department",
            "name", "AI人力资源部",
            "parent_department_id", "0",
            "order", "2"
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试创建部门 - 财务部")
    void testCreateFinanceDepartment() {
        System.out.println("========================================");
        System.out.println("测试创建部门 - AI财务部...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_department",
            "name", "AI财务部",
            "parent_department_id", "0",
            "order", "3"
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试创建部门 - 销售部")
    void testCreateSalesDepartment() {
        System.out.println("========================================");
        System.out.println("测试创建部门 - AI销售部...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_department",
            "name", "AI销售部",
            "parent_department_id", "0",
            "order", "4"
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试创建部门 - 客服部")
    void testCreateCsDepartment() {
        System.out.println("========================================");
        System.out.println("测试创建部门 - AI客服部...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_department",
            "name", "AI客服部",
            "parent_department_id", "0",
            "order", "5"
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试创建部门 - 法务部")
    void testCreateLegalDepartment() {
        System.out.println("========================================");
        System.out.println("测试创建部门 - AI法务部...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_department",
            "name", "AI法务部",
            "parent_department_id", "0",
            "order", "6"
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试创建部门 - 运营部")
    void testCreateOpsDepartment() {
        System.out.println("========================================");
        System.out.println("测试创建部门 - AI运营部...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_department",
            "name", "AI运营部",
            "parent_department_id", "0",
            "order", "7"
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试创建部门 - 行政部")
    void testCreateAdminDepartment() {
        System.out.println("========================================");
        System.out.println("测试创建部门 - AI行政部...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_department",
            "name", "AI行政部",
            "parent_department_id", "0",
            "order", "8"
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试获取审批定义列表")
    void testGetApprovalDefinitionList() {
        System.out.println("========================================");
        System.out.println("测试获取审批定义列表...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "get_approval_definition_list"
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试创建审批定义 - 请假审批")
    void testCreateLeaveApprovalDefinition() {
        System.out.println("========================================");
        System.out.println("测试创建审批定义 - 请假审批...");
        System.out.println("========================================");

        String formContent = """
            {
              "widgets": [
                {
                  "id": "leave_type",
                  "type": "select",
                  "name": "请假类型",
                  "options": ["年假", "事假", "病假", "婚假", "产假", "陪产假"]
                },
                {
                  "id": "start_time",
                  "type": "date",
                  "name": "开始时间"
                },
                {
                  "id": "end_time",
                  "type": "date",
                  "name": "结束时间"
                },
                {
                  "id": "reason",
                  "type": "textarea",
                  "name": "请假原因"
                }
              ]
            }
            """;

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_approval_definition",
            "approval_code", "leave_approval",
            "name", "请假审批",
            "description", "员工请假审批流程",
            "form_content", formContent
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试创建审批定义 - 报销审批")
    void testCreateReimbursementApprovalDefinition() {
        System.out.println("========================================");
        System.out.println("测试创建审批定义 - 报销审批...");
        System.out.println("========================================");

        String formContent = """
            {
              "widgets": [
                {
                  "id": "expense_type",
                  "type": "select",
                  "name": "费用类型",
                  "options": ["差旅费", "交通费", "餐饮费", "办公费", "其他"]
                },
                {
                  "id": "amount",
                  "type": "number",
                  "name": "报销金额"
                },
                {
                  "id": "expense_date",
                  "type": "date",
                  "name": "费用日期"
                },
                {
                  "id": "description",
                  "type": "textarea",
                  "name": "费用说明"
                },
                {
                  "id": "attachments",
                  "type": "file",
                  "name": "附件"
                }
              ]
            }
            """;

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_approval_definition",
            "approval_code", "reimbursement_approval",
            "name", "报销审批",
            "description", "费用报销审批流程",
            "form_content", formContent
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试创建审批定义 - 用章审批")
    void testCreateSealApprovalDefinition() {
        System.out.println("========================================");
        System.out.println("测试创建审批定义 - 用章审批...");
        System.out.println("========================================");

        String formContent = """
            {
              "widgets": [
                {
                  "id": "seal_type",
                  "type": "select",
                  "name": "印章类型",
                  "options": ["公章", "合同章", "财务章", "法人章"]
                },
                {
                  "id": "usage_purpose",
                  "type": "textarea",
                  "name": "用章事由"
                },
                {
                  "id": "document_name",
                  "type": "text",
                  "name": "文件名称"
                },
                {
                  "id": "attachments",
                  "type": "file",
                  "name": "附件"
                }
              ]
            }
            """;

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_approval_definition",
            "approval_code", "seal_approval",
            "name", "用章审批",
            "description", "印章使用审批流程",
            "form_content", formContent
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试创建群聊")
    void testCreateChat() {
        System.out.println("========================================");
        System.out.println("测试创建群聊 - 管理层群...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_chat",
            "name", "管理层工作群",
            "description", "公司管理层日常工作沟通群"
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试创建任务")
    void testCreateTask() {
        System.out.println("========================================");
        System.out.println("测试创建任务...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_task",
            "summary", "完成飞书集成测试",
            "description", "测试董事长飞书工具的所有功能"
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    public static void main(String[] args) {
        ChairmanFeishuToolTest test = new ChairmanFeishuToolTest();
        test.setUp();
        
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║        董事长飞书工具测试 - Living Agent Service           ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
        
        try {
            System.out.println("=== 基础功能测试 ===");
            test.testGetTokenInfo();
            System.out.println();
            test.testGetDepartment();
            System.out.println();
            
            System.out.println("=== 部门创建测试 ===");
            test.testCreateTechDepartment();
            System.out.println();
            test.testCreateHrDepartment();
            System.out.println();
            test.testCreateFinanceDepartment();
            System.out.println();
            test.testCreateSalesDepartment();
            System.out.println();
            test.testCreateCsDepartment();
            System.out.println();
            test.testCreateLegalDepartment();
            System.out.println();
            test.testCreateOpsDepartment();
            System.out.println();
            test.testCreateAdminDepartment();
            System.out.println();
            
            System.out.println("=== 审批定义测试 ===");
            test.testGetApprovalDefinitionList();
            System.out.println();
            test.testCreateLeaveApprovalDefinition();
            System.out.println();
            test.testCreateReimbursementApprovalDefinition();
            System.out.println();
            test.testCreateSealApprovalDefinition();
            System.out.println();
            
            System.out.println("=== 其他功能测试 ===");
            test.testCreateChat();
            System.out.println();
            test.testCreateTask();
            
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
