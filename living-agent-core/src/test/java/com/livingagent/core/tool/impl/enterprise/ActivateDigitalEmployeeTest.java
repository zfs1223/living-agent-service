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

class ActivateDigitalEmployeeTest {

    private static final String APP_ID = "cli_a920321f3b7a5cc1";
    private static final String APP_SECRET = "gmU0opRuS3Aps30BWR84ghkv7ELrkncG";
    
    private static final String TINGFENG_USER_ID = "ou_8f32e722d4a6ee2c67dc782fd580486e";
    private static final String TINGFENG_REAL_EMAIL = "tingfeng@smilesmartai.com";

    private ChairmanFeishuTool tool;

    @BeforeEach
    void setUp() {
        tool = new ChairmanFeishuTool(APP_ID, APP_SECRET);
    }

    @Test
    @DisplayName("Step 1: 获取听风当前信息")
    void testGetTingFengInfo() {
        System.out.println("========================================");
        System.out.println("获取听风当前信息...");
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "get_user",
            "user_id", TINGFENG_USER_ID
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("Step 2: 更新听风邮箱为真实邮箱")
    void testUpdateTingFengEmail() {
        System.out.println("========================================");
        System.out.println("更新听风邮箱...");
        System.out.println("原邮箱: tingfeng@digital-employee.local");
        System.out.println("新邮箱: " + TINGFENG_REAL_EMAIL);
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "update_user",
            "user_id", TINGFENG_USER_ID,
            "name", "听风",
            "department_id", "0",
            "employee_type", 1,
            "email", TINGFENG_REAL_EMAIL
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
    }

    @Test
    @DisplayName("Step 3: 发送激活邀请邮件")
    void testSendActivationEmail() {
        System.out.println("========================================");
        System.out.println("发送激活邀请邮件...");
        System.out.println("目标邮箱: " + TINGFENG_REAL_EMAIL);
        System.out.println("========================================");

        ToolParams params = ToolParams.of(Map.of(
            "action", "send_activation",
            "user_id", TINGFENG_USER_ID,
            "email", TINGFENG_REAL_EMAIL
        ));
        ToolContext context = ToolContext.of("test-chairman", "test-session");
        
        ToolResult result = tool.execute(params, context);
        
        System.out.println("执行结果: " + (result.success() ? "成功 ✅" : "失败 ❌"));
        System.out.println("返回数据: " + result.data());
        System.out.println("错误信息: " + result.error());
        
        if (result.success()) {
            System.out.println("\n========================================");
            System.out.println("📧 请检查邮箱: " + TINGFENG_REAL_EMAIL);
            System.out.println("   激活邮件应该已经发送，请点击邮件中的链接完成激活");
            System.out.println("========================================");
        }
    }

    public static void main(String[] args) {
        ActivateDigitalEmployeeTest test = new ActivateDigitalEmployeeTest();
        test.setUp();
        
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║     数字员工激活测试 - 听风                                 ║");
        System.out.println("║     真实邮箱: tingfeng@hengebiotech.com                     ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
        
        try {
            System.out.println("=== Step 1: 获取听风当前信息 ===");
            test.testGetTingFengInfo();
            System.out.println();
            
            System.out.println("\n=== Step 2: 更新听风邮箱 ===");
            test.testUpdateTingFengEmail();
            System.out.println();
            
            System.out.println("\n=== Step 3: 发送激活邀请 ===");
            test.testSendActivationEmail();
            
            System.out.println("\n");
            System.out.println("╔════════════════════════════════════════════════════════════╗");
            System.out.println("║                    测试完成                                ║");
            System.out.println("║     请检查邮箱完成激活流程                                 ║");
            System.out.println("╚════════════════════════════════════════════════════════════╝");
            
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
