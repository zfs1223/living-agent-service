package com.livingagent.core.brain.compact;

import com.livingagent.core.provider.Provider;

import java.util.List;

public interface ContextCompactor {

    CompactionResult microCompact(List<Provider.ChatMessage> messages, int keepRecent);

    CompactionResult autoCompactIfNeeded(List<Provider.ChatMessage> messages);

    String persistLargeOutput(String toolUseId, String output, int threshold);

    int estimateTokenCount(List<Provider.ChatMessage> messages);
}
