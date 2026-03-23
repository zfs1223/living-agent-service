package com.livingagent.core.neuron.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

class ChatNeuronConfigTest {
    
    @Test
    @DisplayName("测试默认配置创建")
    void testDefaultConfig() {
        ChatNeuronConfig config = ChatNeuronConfig.defaultConfig();
        
        assertEquals("qwen3-0.6b", config.getModelId());
        assertEquals(512, config.getMaxTokens());
        assertEquals(0.7, config.getTemperature());
        assertEquals(0.9, config.getTopP());
        assertEquals(512, config.getContextWindowSize());
        assertEquals(5, config.getMaxHistoryTurns());
        assertEquals(5000, config.getResponseTimeoutMs());
        assertTrue(config.isEnableQuickResponse());
        assertTrue(config.isEnableIntentClassification());
        assertEquals(0.6, config.getIntentConfidenceThreshold());
    }
    
    @Test
    @DisplayName("测试快速响应配置")
    void testQuickResponseConfig() {
        ChatNeuronConfig config = ChatNeuronConfig.quickResponseConfig();
        
        assertEquals(256, config.getMaxTokens());
        assertEquals(0.8, config.getTemperature());
        assertEquals(3000, config.getResponseTimeoutMs());
        assertEquals(3, config.getMaxHistoryTurns());
    }
    
    @Test
    @DisplayName("测试系统提示词构建")
    void testBuildSystemPrompt() {
        ChatNeuronConfig config = new ChatNeuronConfig();
        String prompt = config.buildSystemPrompt();
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("前台接待"));
        assertTrue(prompt.contains("公司形象代表"));
        assertTrue(prompt.contains("专注于表达和高效回复"));
        assertTrue(prompt.contains("不处理专业业务"));
        assertTrue(prompt.contains("转接专业人员处理"));
    }
    
    @Test
    @DisplayName("测试带历史的提示词构建")
    void testBuildPromptWithContext() {
        ChatNeuronConfig config = new ChatNeuronConfig();
        
        List<Map<String, String>> history = new ArrayList<>();
        
        Map<String, String> userTurn = new HashMap<>();
        userTurn.put("role", "user");
        userTurn.put("content", "你好");
        history.add(userTurn);
        
        Map<String, String> assistantTurn = new HashMap<>();
        assistantTurn.put("role", "assistant");
        assistantTurn.put("content", "你好！有什么可以帮您的吗？");
        history.add(assistantTurn);
        
        String prompt = config.buildPromptWithContext("今天天气怎么样", history);
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("对话历史"));
        assertTrue(prompt.contains("用户：你好"));
        assertTrue(prompt.contains("助手：你好"));
        assertTrue(prompt.contains("当前问题"));
        assertTrue(prompt.contains("今天天气怎么样"));
    }
    
    @Test
    @DisplayName("测试空历史的提示词构建")
    void testBuildPromptWithEmptyHistory() {
        ChatNeuronConfig config = new ChatNeuronConfig();
        
        String prompt = config.buildPromptWithContext("你好", null);
        
        assertNotNull(prompt);
        assertFalse(prompt.contains("对话历史"));
        assertTrue(prompt.contains("用户：你好"));
    }
    
    @Test
    @DisplayName("测试历史轮次限制")
    void testHistoryTurnLimit() {
        ChatNeuronConfig config = new ChatNeuronConfig();
        config.setMaxHistoryTurns(2);
        
        List<Map<String, String>> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, String> turn = new HashMap<>();
            turn.put("role", i % 2 == 0 ? "user" : "assistant");
            turn.put("content", "消息" + i);
            history.add(turn);
        }
        
        String prompt = config.buildPromptWithContext("新问题", history);
        
        int userCount = countOccurrences(prompt, "用户：");
        int assistantCount = countOccurrences(prompt, "助手：");
        
        assertTrue(userCount <= 3);
        assertTrue(assistantCount <= 2);
    }
    
    @Test
    @DisplayName("测试配置设置器")
    void testSetters() {
        ChatNeuronConfig config = new ChatNeuronConfig();
        
        config.setModelId("qwen3.5-2b");
        assertEquals("qwen3.5-2b", config.getModelId());
        
        config.setMaxTokens(1024);
        assertEquals(1024, config.getMaxTokens());
        
        config.setTemperature(0.5);
        assertEquals(0.5, config.getTemperature());
        
        config.setTopP(0.95);
        assertEquals(0.95, config.getTopP());
        
        config.setContextWindowSize(1024);
        assertEquals(1024, config.getContextWindowSize());
        
        config.setMaxHistoryTurns(10);
        assertEquals(10, config.getMaxHistoryTurns());
        
        config.setResponseTimeoutMs(10000);
        assertEquals(10000, config.getResponseTimeoutMs());
        
        config.setEnableQuickResponse(false);
        assertFalse(config.isEnableQuickResponse());
        
        config.setEnableIntentClassification(false);
        assertFalse(config.isEnableIntentClassification());
        
        config.setIntentConfidenceThreshold(0.8);
        assertEquals(0.8, config.getIntentConfidenceThreshold());
    }
    
    @Test
    @DisplayName("测试系统提示词设置")
    void testSystemPromptsSetter() {
        ChatNeuronConfig config = new ChatNeuronConfig();
        
        List<String> prompts = new ArrayList<>();
        prompts.add("你是一个专业的客服助手");
        prompts.add("请用礼貌的语言回答");
        config.setSystemPrompts(prompts);
        
        assertEquals(2, config.getSystemPrompts().size());
        assertTrue(config.getSystemPrompts().contains("你是一个专业的客服助手"));
    }
    
    @Test
    @DisplayName("测试人格特征设置")
    void testPersonalityTraitsSetter() {
        ChatNeuronConfig config = new ChatNeuronConfig();
        
        Map<String, String> traits = new HashMap<>();
        traits.put("tone", "professional");
        traits.put("style", "detailed");
        traits.put("language", "english");
        config.setPersonalityTraits(traits);
        
        assertEquals(3, config.getPersonalityTraits().size());
        assertEquals("professional", config.getPersonalityTraits().get("tone"));
    }
    
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}
