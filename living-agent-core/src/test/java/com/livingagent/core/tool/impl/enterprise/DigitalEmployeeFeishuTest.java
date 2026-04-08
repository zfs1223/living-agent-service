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

class DigitalEmployeeFeishuTest {

    private static final String APP_ID = "cli_a920321f3b7a5cc1";
    private static final String APP_SECRET = "gmU0opRuS3Aps30BWR84ghkv7ELrkncG";

    private ChairmanFeishuTool tool;

    @BeforeEach
    void setUp() {
        tool = new ChairmanFeishuTool(APP_ID, APP_SECRET);
    }

    @Test
    @DisplayName("测试创建数字员工 - 听风（董事长办公室）- 使用虚拟联系方式")
    void testCreateDigitalEmployeeTingFeng() {
        System.out.println("========================================");
        System.out.println("测试创建数字员工 - 听风...");
        System.out.println("含义: 听风者，感知全局");
        System.out.println("部门: 董事长办公室");
        System.out.println("手机号: +8613800138001 (虚拟)");
        System.out.println("邮箱: tingfeng@digital-employee.local (虚拟)");
        System.out.println("employee_type: 1 (正式员工)");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_user",
            "name", "听风",
            "department_id", "0",
            "mobile", "+8613800138001",
            "email", "tingfeng@digital-employee.local",
            "employee_no", "DE-001",
            "employee_type", 1,
            "gender", 1
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
        
        if (!result.success()) {
            System.out.println("\n注意: 创建用户需要飞书应用有以下权限:");
            System.out.println("  - contact:user (创建用户)");
            System.out.println("  - contact:user:readonly (读取用户)");
            System.out.println("\n请在飞书开放平台为应用配置相应权限");
        }
    }

    @Test
    @DisplayName("测试创建数字员工 - 听雨（人力资源部）")
    void testCreateDigitalEmployeeTingYu() {
        System.out.println("========================================");
        System.out.println("测试创建数字员工 - 听雨...");
        System.out.println("含义: 听雨声，细腻关怀");
        System.out.println("部门: 人力资源部");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_user",
            "name", "听雨",
            "department_id", "od-ae927fcdea823fa33c10d74c1f6568eb",
            "mobile", "+8613800138002",
            "email", "tingyu@digital-employee.local",
            "employee_no", "DE-002",
            "employee_type", 1,
            "gender", 2
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试创建数字员工 - 听云（技术部）")
    void testCreateDigitalEmployeeTingYun() {
        System.out.println("========================================");
        System.out.println("测试创建数字员工 - 听云...");
        System.out.println("含义: 听云端，技术洞察");
        System.out.println("部门: 技术部");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_user",
            "name", "听云",
            "department_id", "od-c390d448156a0072bc07f47e64466277",
            "mobile", "+8613800138003",
            "email", "tingyun@digital-employee.local",
            "employee_no", "DE-003",
            "employee_type", 1,
            "gender", 1
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试创建数字员工 - 听涛（财务部）")
    void testCreateDigitalEmployeeTingTao() {
        System.out.println("========================================");
        System.out.println("测试创建数字员工 - 听涛...");
        System.out.println("含义: 听波涛，把握趋势");
        System.out.println("部门: 财务部");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_user",
            "name", "听涛",
            "department_id", "0",
            "mobile", "+8613800138004",
            "email", "tingtao@digital-employee.local",
            "employee_no", "DE-004",
            "employee_type", 1,
            "gender", 1
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试创建数字员工 - 听语（客服部）")
    void testCreateDigitalEmployeeTingYu2() {
        System.out.println("========================================");
        System.out.println("测试创建数字员工 - 听语...");
        System.out.println("含义: 听心声，服务客户");
        System.out.println("部门: 客服部");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_user",
            "name", "听语",
            "department_id", "0",
            "mobile", "+8613800138005",
            "email", "tingyu2@digital-employee.local",
            "employee_no", "DE-005",
            "employee_type", 1,
            "gender", 2
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("测试创建数字员工 - 听墨（行政部）")
    void testCreateDigitalEmployeeTingMo() {
        System.out.println("========================================");
        System.out.println("测试创建数字员工 - 听墨...");
        System.out.println("含义: 听笔墨，文思敏捷");
        System.out.println("部门: 行政部");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "create_user",
            "name", "听墨",
            "department_id", "0",
            "mobile", "+8613800138006",
            "email", "tingmo@digital-employee.local",
            "employee_no", "DE-006",
            "employee_type", 1,
            "gender", 2
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("获取已创建的部门列表")
    void testGetDepartmentList() {
        System.out.println("========================================");
        System.out.println("获取飞书部门列表...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "get_department",
            "department_id", "0"
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
    }

    public static void main(String[] args) {
        DigitalEmployeeFeishuTest test = new DigitalEmployeeFeishuTest();
        test.setUp();
        
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     数字员工创建测试 - Living Agent Service                ║");
        System.out.println("║     数字员工命名: 听姓系列                                 ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
        
        try {
            System.out.println("=== Step 1: 获取部门列表 ===");
            test.testGetDepartmentList();
            System.out.println();
            
            System.out.println("=== Step 2: 创建数字员工 ===");
            System.out.println("\n--- 听风 (董事长办公室) ---");
            test.testCreateDigitalEmployeeTingFeng();
            System.out.println();
            
            System.out.println("\n--- 听雨 (人力资源部) ---");
            test.testCreateDigitalEmployeeTingYu();
            System.out.println();
            
            System.out.println("\n--- 听云 (技术部) ---");
            test.testCreateDigitalEmployeeTingYun();
            System.out.println();
            
            System.out.println("\n--- 听涛 (财务部) ---");
            test.testCreateDigitalEmployeeTingTao();
            System.out.println();
            
            System.out.println("\n--- 听语 (客服部) ---");
            test.testCreateDigitalEmployeeTingYu2();
            System.out.println();
            
            System.out.println("\n--- 听墨 (行政部) ---");
            test.testCreateDigitalEmployeeTingMo();
            
            System.out.println("\n");
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║                    测试完成                                ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝");
            
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
