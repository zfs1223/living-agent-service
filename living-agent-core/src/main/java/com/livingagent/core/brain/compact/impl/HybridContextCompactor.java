package com.livingagent.core.brain.compact.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.compact.CompactionResult;
import com.livingagent.core.brain.compact.ContextCompactor;
import com.livingagent.core.brain.impl.MainBrain;
import com.livingagent.core.provider.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HybridContextCompactor implements ContextCompactor {

    private static final Logger log = LoggerFactory.getLogger(HybridContextCompactor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String COMPACT_SYSTEM_PROMPT = """
        你是一个上下文压缩器，负责将多轮对话历史压缩为简洁的摘要。
        
        要求：
        1. 保留所有关键决策、结论和待办事项
        2. 保留涉及的工具调用和结果
        3. 保留用户的核心需求和约束
        4. 移除重复、寒暄和无关内容
        5. 摘要应能让后续对话无缝继续
        
        输出压缩后的摘要文本，不要输出JSON。
        """;

    private final BrainRegistry brainRegistry;
    private final RuleBasedContextCompactor ruleBasedCompactor;

    public HybridContextCompactor(BrainRegistry brainRegistry, Path persistDir, int contextLimit) {
        this.brainRegistry = brainRegistry;
        this.ruleBasedCompactor = new RuleBasedContextCompactor(persistDir, contextLimit);
    }

    public HybridContextCompactor(BrainRegistry brainRegistry, Path persistDir, int contextLimit, boolean nativeCompactEnabled) {
        this.brainRegistry = brainRegistry;
        this.ruleBasedCompactor = new RuleBasedContextCompactor(persistDir, contextLimit, nativeCompactEnabled);
    }

    public HybridContextCompactor(BrainRegistry brainRegistry, Path persistDir) {
        this(brainRegistry, persistDir, 50000);
    }

    @Override
    public CompactionResult microCompact(List<Provider.ChatMessage> messages, int keepRecent) {
        CompactionResult ruleResult = ruleBasedCompactor.microCompact(messages, keepRecent);

        if (!ruleResult.compacted() || ruleResult.removedCount() < 3) {
            return ruleResult;
        }

        MainBrain mainBrain = brainRegistry.get(MainBrain.ID)
            .filter(b -> b instanceof MainBrain)
            .map(b -> (MainBrain) b)
            .orElse(null);

        if (mainBrain == null) {
            return ruleResult;
        }

        try {
            List<Provider.ChatMessage> toCompress = messages.subList(0, Math.max(0, messages.size() - keepRecent));
            if (toCompress.isEmpty()) {
                return ruleResult;
            }

            StringBuilder contextBuilder = new StringBuilder();
            for (Provider.ChatMessage msg : toCompress) {
                contextBuilder.append("[").append(msg.role()).append("]: ")
                    .append(msg.content()).append("\n\n");
            }

            String llmSummary = mainBrain.callLlm(COMPACT_SYSTEM_PROMPT, contextBuilder.toString());
            if (llmSummary == null || llmSummary.isBlank()) {
                return ruleResult;
            }

            List<Provider.ChatMessage> compacted = new ArrayList<>();
            compacted.add(Provider.ChatMessage.system("以下是之前对话的压缩摘要：\n" + llmSummary));
            compacted.addAll(messages.subList(Math.max(0, messages.size() - keepRecent), messages.size()));

            log.debug("LLM semantic compaction: {} messages -> {} messages, summary length={}",
                messages.size(), compacted.size(), llmSummary.length());

            return CompactionResult.compacted(compacted, messages.size() - compacted.size(), llmSummary.length());

        } catch (Exception e) {
            log.warn("LLM semantic compaction failed: {}, using rule-based result", e.getMessage());
            return ruleResult;
        }
    }

    @Override
    public CompactionResult autoCompactIfNeeded(List<Provider.ChatMessage> messages) {
        return ruleBasedCompactor.autoCompactIfNeeded(messages);
    }

    @Override
    public String persistLargeOutput(String toolUseId, String output, int threshold) {
        return ruleBasedCompactor.persistLargeOutput(toolUseId, output, threshold);
    }

    @Override
    public int estimateTokenCount(List<Provider.ChatMessage> messages) {
        return ruleBasedCompactor.estimateTokenCount(messages);
    }
}
