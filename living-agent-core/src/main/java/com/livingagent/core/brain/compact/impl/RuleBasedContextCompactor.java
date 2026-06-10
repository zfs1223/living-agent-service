package com.livingagent.core.brain.compact.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.brain.compact.CompactionResult;
import com.livingagent.core.brain.compact.ContextCompactor;
import com.livingagent.core.nativelib.CompactNative;
import com.livingagent.core.provider.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RuleBasedContextCompactor implements ContextCompactor {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedContextCompactor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int DEFAULT_CONTEXT_LIMIT = 50000;
    private static final int PREVIEW_CHARS = 2000;
    private static final int MAX_RECENT_ROLE_SUMMARIES = 3;
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
        "(?:^|\\s)([\\w./-]+\\.(?:java|rs|py|ts|js|json|yml|yaml|md|toml|xml|sql|sh))(?:\\s|$)",
        Pattern.MULTILINE
    );
    private static final Pattern PENDING_WORK_PATTERN = Pattern.compile(
        "(?i)(?:todo|next|pending|remaining|still need|follow.?up|继续|待办|下一步)",
        Pattern.MULTILINE
    );

    private final Path persistDir;
    private final int contextLimit;
    private final boolean nativeCompactEnabled;
    private volatile String lastCompactionSummary;

    public RuleBasedContextCompactor(Path persistDir, int contextLimit) {
        this(persistDir, contextLimit, false);
    }

    public RuleBasedContextCompactor(Path persistDir, int contextLimit, boolean nativeCompactEnabled) {
        this.persistDir = persistDir;
        this.contextLimit = contextLimit;
        this.nativeCompactEnabled = nativeCompactEnabled;
        try {
            Files.createDirectories(persistDir);
        } catch (IOException e) {
            log.warn("Failed to create persist directory: {}", persistDir, e);
        }
    }

    public RuleBasedContextCompactor(Path persistDir) {
        this(persistDir, DEFAULT_CONTEXT_LIMIT, false);
    }

    @Override
    public CompactionResult microCompact(List<Provider.ChatMessage> messages, int keepRecent) {
        if (messages == null || messages.size() <= keepRecent + 1) {
            return CompactionResult.noChange(messages);
        }

        List<Provider.ChatMessage> result = new ArrayList<>();
        int removedCount = 0;

        List<Integer> toolResultIndices = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Provider.ChatMessage msg = messages.get(i);
            if ("tool".equals(msg.role()) && msg.toolResults() != null) {
                toolResultIndices.add(i);
            }
        }

        Set<Integer> indicesToCompact = new HashSet<>();
        if (toolResultIndices.size() > keepRecent) {
            for (int i = 0; i < toolResultIndices.size() - keepRecent; i++) {
                indicesToCompact.add(toolResultIndices.get(i));
            }
        }

        for (int i = 0; i < messages.size(); i++) {
            Provider.ChatMessage msg = messages.get(i);
            if (indicesToCompact.contains(i) && msg.toolResults() != null) {
                List<Provider.ToolResultData> compactedResults = new ArrayList<>();
                for (Provider.ToolResultData tr : msg.toolResults()) {
                    if (tr.content().length() <= 120) {
                        compactedResults.add(tr);
                    } else {
                        compactedResults.add(new Provider.ToolResultData(
                            tr.callId(),
                            "[Earlier tool result compacted. Re-run the tool if you need full detail.]"
                        ));
                        removedCount++;
                    }
                }
                result.add(new Provider.ChatMessage("tool", null, null, compactedResults));
            } else {
                result.add(msg);
            }
        }

        if (removedCount == 0) {
            return CompactionResult.noChange(messages);
        }

        return CompactionResult.compacted(result, removedCount, 0);
    }

    @Override
    public CompactionResult autoCompactIfNeeded(List<Provider.ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return CompactionResult.noChange(messages);
        }

        int tokenCount = estimateTokenCount(messages);
        if (tokenCount < contextLimit) {
            return CompactionResult.noChange(messages);
        }

        log.info("Context size {} exceeds limit {}, triggering auto-compact", tokenCount, contextLimit);

        int preserveRecent = Math.min(4, messages.size());
        List<Provider.ChatMessage> toCompact = messages.subList(0, messages.size() - preserveRecent);
        List<Provider.ChatMessage> toKeep = messages.subList(messages.size() - preserveRecent, messages.size());

        String summary = summarizeMessages(toCompact);

        if (lastCompactionSummary != null) {
            summary = mergeCompactionSummaries(lastCompactionSummary, summary);
        }
        lastCompactionSummary = summary;

        List<Provider.ChatMessage> compacted = new ArrayList<>();
        compacted.add(Provider.ChatMessage.user(
            "This conversation was compacted. Here is a summary of earlier context:\n" + summary +
            "\n\nContinue from where we left off. No recap needed."
        ));
        compacted.add(Provider.ChatMessage.assistant("Understood. Continuing from where we left off."));
        compacted.addAll(toKeep);

        return CompactionResult.compacted(compacted, toCompact.size(), summary.length());
    }

    @Override
    public String persistLargeOutput(String toolUseId, String output, int threshold) {
        if (output.length() <= threshold) {
            return output;
        }

        try {
            String filename = toolUseId.replace("/", "_").replace("\\", "_") + "_" +
                System.currentTimeMillis() + ".txt";
            Path filePath = persistDir.resolve(filename);
            Files.writeString(filePath, output);

            String preview = output.substring(0, Math.min(PREVIEW_CHARS, output.length()));
            return "<persisted-output>\nFull output saved to: " + filePath +
                "\nPreview:\n" + preview + "\n</persisted-output>";
        } catch (IOException e) {
            log.warn("Failed to persist large output: {}", e.getMessage());
            return output.substring(0, Math.min(PREVIEW_CHARS, output.length())) +
                "\n[Output truncated due to size. Persist failed.]";
        }
    }

    @Override
    public int estimateTokenCount(List<Provider.ChatMessage> messages) {
        if (messages == null) return 0;

        if (nativeCompactEnabled) {
            try {
                String text = messages.stream()
                    .map(m -> m.content() == null ? "" : m.content())
                    .collect(Collectors.joining("\n"));
                int nativeEstimate = CompactNative.estimateTokenCount(text);
                if (nativeEstimate > 0) {
                    return nativeEstimate;
                }
            } catch (Throwable t) {
                log.debug("Native estimateTokenCount failed, fallback to Java: {}", t.getMessage());
            }
        }

        int total = 0;
        for (Provider.ChatMessage msg : messages) {
            if (msg.content() != null) {
                total += msg.content().length() / 4;
            }
            if (msg.toolCalls() != null) {
                for (Provider.ToolCallData tc : msg.toolCalls()) {
                    total += (tc.name().length() + tc.arguments().length()) / 4;
                }
            }
            if (msg.toolResults() != null) {
                for (Provider.ToolResultData tr : msg.toolResults()) {
                    total += tr.content().length() / 4;
                }
            }
        }
        return total;
    }

    String summarizeMessages(List<Provider.ChatMessage> messages) {
        if (nativeCompactEnabled) {
            try {
                List<Map<String, String>> normalized = new ArrayList<>();
                for (Provider.ChatMessage m : messages) {
                    Map<String, String> item = new HashMap<>();
                    item.put("role", m.role());
                    item.put("content", m.content() == null ? "" : m.content());
                    normalized.add(item);
                }
                String payload = MAPPER.writeValueAsString(normalized);
                String nativeSummary = CompactNative.summarizeMessagesJson(payload, 5);
                if (nativeSummary != null && !nativeSummary.isBlank()) {
                    return nativeSummary;
                }
            } catch (Throwable t) {
                log.debug("Native summarizeMessages failed, fallback to Java: {}", t.getMessage());
            }
        }

        int userCount = 0, assistantCount = 0, toolCount = 0;
        Set<String> toolNames = new LinkedHashSet<>();
        List<String> recentUserRequests = new ArrayList<>();
        List<String> pendingWorkItems = new ArrayList<>();
        Set<String> keyFiles = new LinkedHashSet<>();
        String currentWork = "";

        for (Provider.ChatMessage msg : messages) {
            switch (msg.role()) {
                case "user" -> {
                    userCount++;
                    if (msg.content() != null && !msg.content().isEmpty()) {
                        recentUserRequests.add(truncate(msg.content(), 200));
                        extractFilePaths(msg.content(), keyFiles);
                        extractPendingWork(msg.content(), pendingWorkItems);
                    }
                }
                case "assistant" -> {
                    assistantCount++;
                    if (msg.content() != null && !msg.content().isEmpty()) {
                        currentWork = truncate(msg.content(), 200);
                        extractPendingWork(msg.content(), pendingWorkItems);
                    }
                    if (msg.toolCalls() != null) {
                        for (Provider.ToolCallData tc : msg.toolCalls()) {
                            toolNames.add(tc.name());
                            if (tc.arguments() != null) {
                                extractFilePaths(tc.arguments(), keyFiles);
                            }
                        }
                    }
                }
                case "tool" -> {
                    toolCount++;
                    if (msg.toolResults() != null) {
                        for (Provider.ToolResultData tr : msg.toolResults()) {
                            extractFilePaths(tr.content(), keyFiles);
                        }
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<summary>\nConversation summary:\n");
        sb.append("- Scope: ").append(messages.size()).append(" earlier messages compacted (user=")
          .append(userCount).append(", assistant=").append(assistantCount).append(", tool=").append(toolCount).append(").\n");

        if (!toolNames.isEmpty()) {
            sb.append("- Tools mentioned: ").append(String.join(", ", toolNames)).append(".\n");
        }

        if (!recentUserRequests.isEmpty()) {
            int start = Math.max(0, recentUserRequests.size() - MAX_RECENT_ROLE_SUMMARIES);
            sb.append("- Recent user requests:\n");
            for (int i = start; i < recentUserRequests.size(); i++) {
                sb.append("  - ").append(recentUserRequests.get(i)).append("\n");
            }
        }

        if (!pendingWorkItems.isEmpty()) {
            sb.append("- Pending work:\n");
            for (String item : pendingWorkItems.stream().distinct().limit(5).toList()) {
                sb.append("  - ").append(item).append("\n");
            }
        }

        if (!keyFiles.isEmpty()) {
            sb.append("- Key files referenced: ").append(String.join(", ",
                keyFiles.stream().limit(10).toList())).append(".\n");
        }

        if (!currentWork.isEmpty()) {
            sb.append("- Current work: ").append(currentWork).append("\n");
        }

        sb.append("</summary>");
        return sb.toString();
    }

    String mergeCompactionSummaries(String oldSummary, String newSummary) {
        String oldContent = extractSummaryContent(oldSummary);
        String newContent = extractSummaryContent(newSummary);

        return "<summary>\nMerged conversation summary:\n" +
            "--- Earlier session ---\n" + oldContent + "\n" +
            "--- Recent session ---\n" + newContent + "\n" +
            "</summary>";
    }

    private String extractSummaryContent(String summary) {
        if (summary == null) return "";
        return summary.replace("<summary>", "").replace("</summary>", "").trim();
    }

    private void extractFilePaths(String text, Set<String> keyFiles) {
        if (text == null) return;
        Matcher matcher = FILE_PATH_PATTERN.matcher(text);
        while (matcher.find() && keyFiles.size() < 20) {
            keyFiles.add(matcher.group(1));
        }
    }

    private void extractPendingWork(String text, List<String> pendingWorkItems) {
        if (text == null) return;
        Matcher matcher = PENDING_WORK_PATTERN.matcher(text);
        if (matcher.find()) {
            int start = Math.max(0, matcher.start() - 20);
            int end = Math.min(text.length(), matcher.end() + 80);
            pendingWorkItems.add(text.substring(start, end).trim());
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
