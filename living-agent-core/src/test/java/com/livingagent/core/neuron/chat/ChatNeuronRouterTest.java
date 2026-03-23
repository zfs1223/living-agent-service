package com.livingagent.core.neuron.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import static org.mockito.Mockito.*;

import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronRegistry;

class ChatNeuronRouterTest {
    
    private NeuronRegistry neuronRegistry;
    private ChatNeuronRouter router;
    private Neuron mockChatNeuron;
    private Neuron mockToolNeuron;
    private Neuron mockMainBrain;
    
    @BeforeEach
    void setUp() {
        neuronRegistry = mock(NeuronRegistry.class);
        mockChatNeuron = mock(Neuron.class);
        mockToolNeuron = mock(Neuron.class);
        mockMainBrain = mock(Neuron.class);
        
        when(mockChatNeuron.getId()).thenReturn("neuron://chat/qwen3/001");
        when(mockToolNeuron.getId()).thenReturn("neuron://tool/bitnet/001");
        when(mockMainBrain.getId()).thenReturn("neuron://main/brain/001");
        
        when(neuronRegistry.get("neuron://chat/qwen3/001")).thenReturn(Optional.of(mockChatNeuron));
        when(neuronRegistry.get("neuron://tool/bitnet/001")).thenReturn(Optional.of(mockToolNeuron));
        when(neuronRegistry.get("neuron://main/brain/001")).thenReturn(Optional.of(mockMainBrain));
        
        router = new ChatNeuronRouter(neuronRegistry);
        router.initialize();
    }
    
    @Nested
    @DisplayName("路由测试")
    class RoutingTests {
        
        @Test
        @DisplayName("问候语应路由到闲聊神经元")
        void testGreetingRouting() {
            ChatNeuronRouter.RoutingResult result = router.route("session-1", "你好", null);
            
            assertEquals("neuron://chat/qwen3/001", result.getTargetNeuron());
            assertEquals("GREETING", result.getIntent());
            assertTrue(result.isQuickResponse());
            assertTrue(result.shouldRoute());
        }
        
        @Test
        @DisplayName("工具调用应路由到工具神经元")
        void testToolCallRouting() {
            ChatNeuronRouter.RoutingResult result = router.route("session-1", "查询天气", null);
            
            assertEquals("neuron://tool/bitnet/001", result.getTargetNeuron());
            assertEquals("TOOL_CALL", result.getIntent());
            assertTrue(result.requiresToolNeuron());
            assertFalse(result.shouldUseChatNeuron());
        }
        
        @Test
        @DisplayName("复杂任务应路由到主大脑")
        void testComplexTaskRouting() {
            ChatNeuronRouter.RoutingResult result = router.route("session-1", "设计一个系统架构", null);
            
            assertEquals("neuron://main/brain/001", result.getTargetNeuron());
            assertEquals("COMPLEX_TASK", result.getIntent());
            assertTrue(result.requiresMainBrain());
        }
        
        @Test
        @DisplayName("简单问题应路由到闲聊神经元")
        void testSimpleQuestionRouting() {
            ChatNeuronRouter.RoutingResult result = router.route("session-1", "今天怎么样？", null);
            
            assertEquals("neuron://chat/qwen3/001", result.getTargetNeuron());
            assertTrue(result.isQuickResponse());
        }
        
        @Test
        @DisplayName("闲聊应路由到闲聊神经元")
        void testCasualChatRouting() {
            ChatNeuronRouter.RoutingResult result = router.route("session-1", "你觉得怎么样", null);
            
            assertEquals("neuron://chat/qwen3/001", result.getTargetNeuron());
            assertTrue(result.isQuickResponse());
        }
    }
    
    @Nested
    @DisplayName("统计测试")
    class StatsTests {
        
        @Test
        @DisplayName("路由统计应正确记录")
        void testRoutingStats() {
            router.route("session-1", "你好", null);
            router.route("session-1", "查询天气", null);
            router.route("session-1", "设计架构", null);
            
            ChatNeuronRouter.RoutingStats stats = router.getStats("session-1");
            
            assertNotNull(stats);
            assertEquals(3, stats.getTotalRoutings());
            assertTrue(stats.getAvgLatencyMs() >= 0);
        }
        
        @Test
        @DisplayName("聚合统计应正确计算")
        void testAggregatedStats() {
            router.route("session-1", "你好", null);
            router.route("session-1", "查询天气", null);
            router.route("session-2", "设计架构", null);
            
            Map<String, Object> stats = router.getAggregatedStats();
            
            assertEquals(3, stats.get("totalRoutings"));
            assertEquals(2, stats.get("activeSessions"));
            
            @SuppressWarnings("unchecked")
            Map<String, Integer> intentDist = (Map<String, Integer>) stats.get("intentDistribution");
            assertTrue(intentDist.containsKey("GREETING"));
            assertTrue(intentDist.containsKey("TOOL_CALL"));
            assertTrue(intentDist.containsKey("COMPLEX_TASK"));
        }
        
        @Test
        @DisplayName("清除会话统计")
        void testClearSessionStats() {
            router.route("session-1", "你好", null);
            
            router.clearSessionStats("session-1");
            
            assertNull(router.getStats("session-1"));
        }
    }
    
    @Nested
    @DisplayName("便捷方法测试")
    class ConvenienceMethodTests {
        
        @Test
        @DisplayName("shouldUseChatNeuron 应正确判断")
        void testShouldUseChatNeuron() {
            assertTrue(router.shouldUseChatNeuron("你好"));
            assertTrue(router.shouldUseChatNeuron("今天怎么样"));
            assertFalse(router.shouldUseChatNeuron("查询天气"));
            assertFalse(router.shouldUseChatNeuron("设计架构"));
        }
        
        @Test
        @DisplayName("requiresToolCall 应正确判断")
        void testRequiresToolCall() {
            assertTrue(router.requiresToolCall("查询天气"));
            assertTrue(router.requiresToolCall("帮我搜索"));
            assertFalse(router.requiresToolCall("你好"));
            assertFalse(router.requiresToolCall("今天怎么样"));
        }
        
        @Test
        @DisplayName("requiresComplexProcessing 应正确判断")
        void testRequiresComplexProcessing() {
            assertTrue(router.requiresComplexProcessing("设计架构"));
            assertTrue(router.requiresComplexProcessing("分析这个问题"));
            assertFalse(router.requiresComplexProcessing("你好"));
            assertFalse(router.requiresComplexProcessing("查询天气"));
        }
    }
    
    @Nested
    @DisplayName("神经元设置测试")
    class NeuronSetterTests {
        
        @Test
        @DisplayName("手动设置神经元")
        void testSetNeurons() {
            Neuron newChatNeuron = mock(Neuron.class);
            when(newChatNeuron.getId()).thenReturn("neuron://chat/new/001");
            
            router.setChatNeuron(newChatNeuron);
            
            ChatNeuronRouter.RoutingResult result = router.route("session-1", "你好", null);
            assertEquals("neuron://chat/new/001", result.getTargetNeuron());
        }
    }
    
    @Nested
    @DisplayName("异步路由测试")
    class AsyncRoutingTests {
        
        @Test
        @DisplayName("异步路由应返回正确结果")
        void testAsyncRouting() throws Exception {
            ChatNeuronRouter.RoutingResult result = router.routeAsync("session-1", "你好", null).get();
            
            assertEquals("neuron://chat/qwen3/001", result.getTargetNeuron());
            assertEquals("GREETING", result.getIntent());
        }
    }
    
    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("空输入应返回未知意图")
        void testEmptyInput() {
            ChatNeuronRouter.RoutingResult result = router.route("session-1", "", null);
            
            assertNotNull(result);
            assertEquals("UNKNOWN", result.getIntent());
        }
        
        @Test
        @DisplayName("null输入应返回未知意图")
        void testNullInput() {
            ChatNeuronRouter.RoutingResult result = router.route("session-1", null, null);
            
            assertNotNull(result);
            assertEquals("UNKNOWN", result.getIntent());
        }
        
        @Test
        @DisplayName("带上下文的路由")
        void testRoutingWithContext() {
            Map<String, Object> context = new HashMap<>();
            context.put("userId", "user-123");
            context.put("previousIntent", "GREETING");
            
            ChatNeuronRouter.RoutingResult result = router.route("session-1", "你好", context);
            
            assertNotNull(result);
            assertEquals("GREETING", result.getIntent());
        }
    }
}
