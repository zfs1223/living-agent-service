package com.livingagent.core.neuron.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;

class ChatIntentClassifierTest {
    
    private ChatIntentClassifier classifier;
    
    @BeforeEach
    void setUp() {
        classifier = new ChatIntentClassifier();
    }
    
    @Test
    @DisplayName("测试问候语识别 - 早上好")
    void testGreetingMorning() {
        String input = "早上好";
        ChatIntentClassifier.ClassificationResult result = classifier.classify(input);
        
        assertEquals(ChatIntentClassifier.ChatIntent.GREETING, result.getIntent());
        assertTrue(result.getConfidence() >= 0.9);
        assertTrue(result.shouldUseChatNeuron());
    }
    
    @Test
    @DisplayName("测试问候语识别 - 你好")
    void testGreetingGeneral() {
        String input = "你好";
        ChatIntentClassifier.ClassificationResult result = classifier.classify(input);
        
        assertEquals(ChatIntentClassifier.ChatIntent.GREETING, result.getIntent());
        assertTrue(result.shouldUseChatNeuron());
    }
    
    @Test
    @DisplayName("测试问候语识别 - Hello")
    void testGreetingEnglish() {
        String input = "Hello";
        ChatIntentClassifier.ClassificationResult result = classifier.classify(input);
        
        assertEquals(ChatIntentClassifier.ChatIntent.GREETING, result.getIntent());
        assertTrue(result.shouldUseChatNeuron());
    }
    
    @Test
    @DisplayName("测试工具调用识别 - 查询天气")
    void testToolCallWeather() {
        String input = "帮我查询一下北京的天气";
        ChatIntentClassifier.ClassificationResult result = classifier.classify(input);
        
        assertEquals(ChatIntentClassifier.ChatIntent.TOOL_CALL, result.getIntent());
        assertFalse(result.shouldUseChatNeuron());
    }
    
    @Test
    @DisplayName("测试工具调用识别 - Git操作")
    void testToolCallGit() {
        String input = "帮我提交代码到git";
        ChatIntentClassifier.ClassificationResult result = classifier.classify(input);
        
        assertEquals(ChatIntentClassifier.ChatIntent.TOOL_CALL, result.getIntent());
        assertFalse(result.shouldUseChatNeuron());
    }
    
    @Test
    @DisplayName("测试复杂任务识别 - 架构设计")
    void testComplexTaskArchitecture() {
        String input = "请帮我设计一个微服务架构方案";
        ChatIntentClassifier.ClassificationResult result = classifier.classify(input);
        
        assertEquals(ChatIntentClassifier.ChatIntent.COMPLEX_TASK, result.getIntent());
        assertFalse(result.shouldUseChatNeuron());
    }
    
    @Test
    @DisplayName("测试简单问题识别")
    void testSimpleQuestion() {
        String input = "今天天气好吗？";
        ChatIntentClassifier.ClassificationResult result = classifier.classify(input);
        
        assertEquals(ChatIntentClassifier.ChatIntent.SIMPLE_QUESTION, result.getIntent());
        assertTrue(result.shouldUseChatNeuron());
    }
    
    @Test
    @DisplayName("测试闲聊识别")
    void testCasualChat() {
        String input = "你觉得怎么样";
        ChatIntentClassifier.ClassificationResult result = classifier.classify(input);
        
        assertEquals(ChatIntentClassifier.ChatIntent.CASUAL_CHAT, result.getIntent());
        assertTrue(result.shouldUseChatNeuron());
    }
    
    @Test
    @DisplayName("测试空输入")
    void testEmptyInput() {
        ChatIntentClassifier.ClassificationResult result = classifier.classify("");
        
        assertEquals(ChatIntentClassifier.ChatIntent.UNKNOWN, result.getIntent());
        assertEquals(0.0, result.getConfidence());
    }
    
    @Test
    @DisplayName("测试空值输入")
    void testNullInput() {
        ChatIntentClassifier.ClassificationResult result = classifier.classify(null);
        
        assertEquals(ChatIntentClassifier.ChatIntent.UNKNOWN, result.getIntent());
        assertEquals(0.0, result.getConfidence());
    }
    
    @Test
    @DisplayName("测试快速响应候选判断")
    void testQuickResponseCandidate() {
        assertTrue(classifier.isQuickResponseCandidate("你好"));
        assertTrue(classifier.isQuickResponseCandidate("今天怎么样"));
        assertFalse(classifier.isQuickResponseCandidate("帮我查询天气"));
        assertFalse(classifier.isQuickResponseCandidate("设计一个系统架构"));
    }
    
    @Test
    @DisplayName("测试工具调用需求判断")
    void testRequiresToolCall() {
        assertTrue(classifier.requiresToolCall("帮我搜索一下"));
        assertTrue(classifier.requiresToolCall("查询天气"));
        assertFalse(classifier.requiresToolCall("你好"));
        assertFalse(classifier.requiresToolCall("今天怎么样"));
    }
    
    @Test
    @DisplayName("测试复杂处理需求判断")
    void testRequiresComplexProcessing() {
        assertTrue(classifier.requiresComplexProcessing("帮我分析这个问题"));
        assertTrue(classifier.requiresComplexProcessing("设计一个方案"));
        assertFalse(classifier.requiresComplexProcessing("你好"));
        assertFalse(classifier.requiresComplexProcessing("查询天气"));
    }
    
    @Test
    @DisplayName("测试短输入处理")
    void testShortInput() {
        ChatIntentClassifier.ClassificationResult result = classifier.classify("在吗");
        
        assertTrue(result.shouldUseChatNeuron());
    }
    
    @Test
    @DisplayName("测试混合意图 - 工具关键词+问候")
    void testMixedIntentToolWithGreeting() {
        String input = "你好，帮我查询一下天气";
        ChatIntentClassifier.ClassificationResult result = classifier.classify(input);
        
        assertEquals(ChatIntentClassifier.ChatIntent.TOOL_CALL, result.getIntent());
    }
}
