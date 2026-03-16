package com.livingagent.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class IdUtilsTest {

    @Test
    @DisplayName("生成人类员工ID - 钉钉")
    void testGenerateHumanEmployeeId_DingTalk() {
        String employeeId = IdUtils.generateHumanEmployeeId(IdUtils.AuthProvider.DINGTALK, "123456");
        
        assertEquals("employee://human/dingtalk/123456", employeeId);
        assertTrue(IdUtils.isEmployeeId(employeeId));
        assertTrue(IdUtils.isHumanEmployeeId(employeeId));
        assertEquals(IdUtils.EmployeeType.HUMAN, IdUtils.getEmployeeType(employeeId));
    }

    @Test
    @DisplayName("生成人类员工ID - 飞书")
    void testGenerateHumanEmployeeId_Feishu() {
        String employeeId = IdUtils.generateHumanEmployeeId(IdUtils.AuthProvider.FEISHU, "abc123");
        
        assertEquals("employee://human/feishu/abc123", employeeId);
        assertTrue(IdUtils.isEmployeeId(employeeId));
        assertTrue(IdUtils.isHumanEmployeeId(employeeId));
        assertEquals(IdUtils.EmployeeType.HUMAN, IdUtils.getEmployeeType(employeeId));
    }

    @Test
    @DisplayName("生成人类员工ID - 企业微信")
    void testGenerateHumanEmployeeId_Wecom() {
        String employeeId = IdUtils.generateHumanEmployeeId(IdUtils.AuthProvider.WECOM, "user789");
        
        assertEquals("employee://human/wecom/user789", employeeId);
        assertTrue(IdUtils.isHumanEmployeeId(employeeId));
    }

    @Test
    @DisplayName("生成数字员工ID")
    void testGenerateDigitalEmployeeId() {
        String employeeId = IdUtils.generateDigitalEmployeeId("tech", "code-reviewer", "001");
        
        assertEquals("employee://digital/tech/code-reviewer/001", employeeId);
        assertTrue(IdUtils.isEmployeeId(employeeId));
        assertTrue(IdUtils.isDigitalEmployeeId(employeeId));
        assertEquals(IdUtils.EmployeeType.DIGITAL, IdUtils.getEmployeeType(employeeId));
    }

    @Test
    @DisplayName("生成神经元ID")
    void testGenerateNeuronId() {
        String neuronId = IdUtils.generateNeuronId("tech", "code-reviewer", "001");
        
        assertEquals("neuron://tech/code-reviewer/001", neuronId);
        assertTrue(IdUtils.isNeuronId(neuronId));
    }

    @Test
    @DisplayName("生成Channel ID")
    void testGenerateChannelId() {
        String channelId = IdUtils.generateChannelId("input", "text");
        
        assertEquals("channel://input/text", channelId);
        assertTrue(IdUtils.isChannelId(channelId));
    }

    @Test
    @DisplayName("员工ID转神经元ID")
    void testEmployeeToNeuronId() {
        String employeeId = "employee://digital/tech/code-reviewer/001";
        String neuronId = IdUtils.employeeToNeuronId(employeeId);
        
        assertEquals("neuron://tech/code-reviewer/001", neuronId);
    }

    @Test
    @DisplayName("神经元ID转员工ID")
    void testNeuronToEmployeeId() {
        String neuronId = "neuron://tech/code-reviewer/001";
        String employeeId = IdUtils.neuronToEmployeeId(neuronId);
        
        assertEquals("employee://digital/tech/code-reviewer/001", employeeId);
    }

    @Test
    @DisplayName("双向转换一致性")
    void testBidirectionalConversion() {
        String originalEmployeeId = "employee://digital/hr/recruiter/042";
        String neuronId = IdUtils.employeeToNeuronId(originalEmployeeId);
        String backToEmployeeId = IdUtils.neuronToEmployeeId(neuronId);
        
        assertEquals(originalEmployeeId, backToEmployeeId);
    }

    @Test
    @DisplayName("解析数字员工ID组件")
    void testParseEmployeeId_Digital() {
        String employeeId = "employee://digital/tech/code-reviewer/001";
        IdUtils.ParsedEmployeeId parsed = IdUtils.parseEmployeeId(employeeId);
        
        assertEquals(IdUtils.EmployeeType.DIGITAL, parsed.getType());
        assertEquals("tech", parsed.getDepartment());
        assertEquals("code-reviewer", parsed.getRole());
        assertEquals("001", parsed.getInstance());
    }

    @Test
    @DisplayName("解析人类员工ID组件")
    void testParseEmployeeId_Human() {
        String employeeId = "employee://human/dingtalk/123456";
        IdUtils.ParsedEmployeeId parsed = IdUtils.parseEmployeeId(employeeId);
        
        assertEquals(IdUtils.EmployeeType.HUMAN, parsed.getType());
        assertEquals(IdUtils.AuthProvider.DINGTALK, parsed.getAuthProvider());
        assertEquals("123456", parsed.getAccountId());
    }

    @Test
    @DisplayName("解析神经元ID组件")
    void testParseNeuronId() {
        String neuronId = "neuron://perception/ear/001";
        IdUtils.ParsedNeuronId parsed = IdUtils.parseNeuronId(neuronId);
        
        assertEquals("perception", parsed.getDepartment());
        assertEquals("ear", parsed.getRole());
        assertEquals("001", parsed.getInstance());
    }

    @Test
    @DisplayName("解析Channel ID组件")
    void testParseChannelId() {
        String channelId = "channel://input/text";
        IdUtils.ParsedChannelId parsed = IdUtils.parseChannelId(channelId);
        
        assertEquals("input", parsed.getScope());
        assertEquals("text", parsed.getName());
    }

    @Test
    @DisplayName("无效员工ID检测")
    void testInvalidEmployeeId() {
        assertFalse(IdUtils.isEmployeeId("invalid-id"));
        assertFalse(IdUtils.isEmployeeId("neuron://tech/code-reviewer/001"));
        assertFalse(IdUtils.isEmployeeId(null));
        assertFalse(IdUtils.isEmployeeId(""));
    }

    @Test
    @DisplayName("无效神经元ID检测")
    void testInvalidNeuronId() {
        assertFalse(IdUtils.isNeuronId("invalid-id"));
        assertFalse(IdUtils.isNeuronId("employee://digital/tech/code-reviewer/001"));
        assertFalse(IdUtils.isNeuronId(null));
        assertFalse(IdUtils.isNeuronId(""));
    }

    @Test
    @DisplayName("人类员工ID转神经元ID抛出异常")
    void testHumanEmployeeToNeuronId_ThrowsException() {
        String humanEmployeeId = "employee://human/dingtalk/123456";
        
        assertThrows(IllegalArgumentException.class, () -> {
            IdUtils.employeeToNeuronId(humanEmployeeId);
        });
    }

    @Test
    @DisplayName("获取员工类型")
    void testGetEmployeeType() {
        assertEquals(IdUtils.EmployeeType.HUMAN, 
            IdUtils.getEmployeeType("employee://human/dingtalk/123456"));
        assertEquals(IdUtils.EmployeeType.DIGITAL, 
            IdUtils.getEmployeeType("employee://digital/tech/code-reviewer/001"));
    }

    @Test
    @DisplayName("获取员工类型 - 无效ID抛出异常")
    void testGetEmployeeType_InvalidId_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            IdUtils.getEmployeeType("invalid-id");
        });
    }

    @Test
    @DisplayName("感知层神经元ID格式验证")
    void testPerceptionNeuronIds() {
        assertTrue(IdUtils.isNeuronId("neuron://perception/ear/001"));
        assertTrue(IdUtils.isNeuronId("neuron://perception/mouth/001"));
        assertTrue(IdUtils.isNeuronId("neuron://perception/text/001"));
    }

    @Test
    @DisplayName("核心层神经元ID格式验证")
    void testCoreNeuronIds() {
        assertTrue(IdUtils.isNeuronId("neuron://core/router/001"));
        assertTrue(IdUtils.isNeuronId("neuron://core/qwen3/001"));
        assertTrue(IdUtils.isNeuronId("neuron://core/bitnet/001"));
    }

    @Test
    @DisplayName("部门数字员工ID示例")
    void testDepartmentDigitalEmployeeIds() {
        String techId = IdUtils.generateDigitalEmployeeId("tech", "developer", "001");
        assertTrue(techId.startsWith("employee://digital/tech/"));
        
        String hrId = IdUtils.generateDigitalEmployeeId("hr", "recruiter", "001");
        assertTrue(hrId.startsWith("employee://digital/hr/"));
        
        String financeId = IdUtils.generateDigitalEmployeeId("finance", "analyst", "001");
        assertTrue(financeId.startsWith("employee://digital/finance/"));
    }

    @Test
    @DisplayName("ID大小写处理")
    void testCaseInsensitiveGeneration() {
        String id1 = IdUtils.generateDigitalEmployeeId("TECH", "CODE-REVIEWER", "001");
        String id2 = IdUtils.generateDigitalEmployeeId("tech", "code-reviewer", "001");
        
        assertEquals(id1, id2);
        assertTrue(id1.contains("tech"));
        assertTrue(id1.contains("code-reviewer"));
    }

    @Test
    @DisplayName("空参数校验")
    void testNullArguments() {
        assertThrows(NullPointerException.class, () -> {
            IdUtils.generateHumanEmployeeId(null, "123");
        });
        
        assertThrows(NullPointerException.class, () -> {
            IdUtils.generateHumanEmployeeId(IdUtils.AuthProvider.DINGTALK, null);
        });
        
        assertThrows(NullPointerException.class, () -> {
            IdUtils.generateDigitalEmployeeId(null, "role", "001");
        });
        
        assertThrows(NullPointerException.class, () -> {
            IdUtils.generateNeuronId("dept", null, "001");
        });
    }
}
