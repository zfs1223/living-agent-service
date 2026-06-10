package com.livingagent.core.brain.compact;

import com.livingagent.core.provider.Provider;

import java.util.List;

public record CompactionResult(
    boolean compacted,
    List<Provider.ChatMessage> messages,
    int removedCount,
    int summaryLength
) {
    public static CompactionResult noChange(List<Provider.ChatMessage> messages) {
        return new CompactionResult(false, messages, 0, 0);
    }

    public static CompactionResult compacted(List<Provider.ChatMessage> messages, int removedCount, int summaryLength) {
        return new CompactionResult(true, messages, removedCount, summaryLength);
    }
}
