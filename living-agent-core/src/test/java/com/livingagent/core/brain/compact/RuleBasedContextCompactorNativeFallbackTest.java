package com.livingagent.core.brain.compact;

import com.livingagent.core.brain.compact.impl.RuleBasedContextCompactor;
import com.livingagent.core.provider.Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleBasedContextCompactorNativeFallbackTest {

    @Test
    @DisplayName("Native disabled should still summarize and estimate")
    void javaFallbackWhenNativeDisabled() throws Exception {
        Path tmp = Files.createTempDirectory("compact-test-");
        try {
            RuleBasedContextCompactor compactor = new RuleBasedContextCompactor(tmp, 20, false);

            List<Provider.ChatMessage> messages = List.of(
                Provider.ChatMessage.user("请继续优化 TechBrain 的协作链路，并更新文档。"),
                Provider.ChatMessage.assistant("好的，我会先分析并给出改造步骤。"),
                Provider.ChatMessage.user("下一步请补充测试并验证。")
            );

            int tokenCount = compactor.estimateTokenCount(messages);
            assertTrue(tokenCount > 0, "token estimate should be positive");

            CompactionResult result = compactor.autoCompactIfNeeded(messages);
            assertNotNull(result);
            assertNotNull(result.messages());
        } finally {
            Files.walk(tmp)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
        }
    }

    @Test
    @DisplayName("Native enabled should fallback gracefully when library unavailable")
    void nativeEnabledFallbackWhenLibraryUnavailable() throws Exception {
        Path tmp = Files.createTempDirectory("compact-native-test-");
        try {
            RuleBasedContextCompactor compactor = new RuleBasedContextCompactor(tmp, 20, true);

            List<Provider.ChatMessage> messages = List.of(
                Provider.ChatMessage.user("请处理一个较长任务：修复工具授权，补充文档，完善测试。")
            );

            int tokenCount = compactor.estimateTokenCount(messages);
            assertTrue(tokenCount > 0, "fallback token estimate should still be positive");

            String persisted = compactor.persistLargeOutput("tool-1", "x".repeat(200), 50);
            assertNotNull(persisted);
            assertFalse(persisted.isEmpty());
        } finally {
            Files.walk(tmp)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
        }
    }
}
