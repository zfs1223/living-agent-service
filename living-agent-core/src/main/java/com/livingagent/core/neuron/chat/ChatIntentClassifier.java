package com.livingagent.core.neuron.chat;

import com.livingagent.core.autonomy.DialogueAnalyzer;
import com.livingagent.core.autonomy.DialogueDecision;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class ChatIntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(ChatIntentClassifier.class);

    private static final Set<String> GREETINGS = Set.of(
        "你好", "您好", "hi", "hello", "hey", "早上好", "下午好", "晚上好",
        "早安", "晚安", "哈喽", "嗨", "在吗", "在不在"
    );

    private static final Set<String> CASUAL_PATTERNS = Set.of(
        "怎么样", "如何", "什么意思", "为什么", "怎么", "干嘛",
        "是不是", "对不对", "好不好", "行不行", "可以吗", "能吗",
        "觉得", "认为", "感觉", "想", "希望", "知道", "了解"
    );

    private static final Set<String> TOOL_KEYWORDS = Set.of(
        "查询", "搜索", "查找", "获取", "执行", "运行", "调用",
        "创建", "删除", "修改", "更新", "发送", "接收",
        "打开", "关闭", "启动", "停止", "重启",
        "git", "docker", "部署", "构建", "测试",
        "天气", "时间", "日期", "提醒", "闹钟",
        "邮件", "消息", "通知", "报告"
    );

    private static final Set<String> COMPLEX_KEYWORDS = Set.of(
        "分析", "设计", "规划", "评估", "优化", "重构",
        "架构", "方案", "策略", "计划", "总结",
        "比较", "对比", "选择", "决策", "建议"
    );

    private static final Pattern QUESTION_PATTERN = Pattern.compile("^[？?？]+$|[吗呢吧啊呀]$");

    private final DialogueAnalyzer dialogueAnalyzer;
    private final boolean llmFirst;

    public ChatIntentClassifier() {
        this.dialogueAnalyzer = null;
        this.llmFirst = false;
    }

    public ChatIntentClassifier(DialogueAnalyzer dialogueAnalyzer) {
        this.dialogueAnalyzer = dialogueAnalyzer;
        this.llmFirst = dialogueAnalyzer != null;
    }

    public enum ChatIntent {
        GREETING,
        CASUAL_CHAT,
        SIMPLE_QUESTION,
        TOOL_CALL,
        COMPLEX_TASK,
        UNKNOWN
    }

    public static class ClassificationResult {
        private final ChatIntent intent;
        private final double confidence;
        private final String reason;
        private final String originalInput;

        public ClassificationResult(ChatIntent intent, double confidence, String reason) {
            this.intent = intent;
            this.confidence = confidence;
            this.reason = reason;
            this.originalInput = null;
        }

        public ClassificationResult(ChatIntent intent, double confidence, String reason, String originalInput) {
            this.intent = intent;
            this.confidence = confidence;
            this.reason = reason;
            this.originalInput = originalInput;
        }

        public ChatIntent getIntent() { return intent; }
        public double getConfidence() { return confidence; }
        public String getReason() { return reason; }
        public String getOriginalInput() { return originalInput; }

        public boolean shouldUseChatNeuron() {
            return intent == ChatIntent.GREETING ||
                   intent == ChatIntent.CASUAL_CHAT ||
                   intent == ChatIntent.SIMPLE_QUESTION;
        }
    }

    public ClassificationResult classify(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return new ClassificationResult(ChatIntent.UNKNOWN, 0.0, "Empty input");
        }

        if (llmFirst && dialogueAnalyzer != null) {
            try {
                DialogueDecision decision = dialogueAnalyzer.analyze(userInput, "chat", null, null);
                return mapFromDialogueDecision(decision, userInput);
            } catch (Exception e) {
                log.debug("DialogueAnalyzer failed for chat classification, using rule-based fallback: {}", e.getMessage());
            }
        }

        return classifyByRules(userInput);
    }

    private ClassificationResult mapFromDialogueDecision(DialogueDecision decision, String originalInput) {
        if (decision.kind() == DialogueDecision.MessageKind.TASK || decision.requiresTaskExecution()) {
            return new ClassificationResult(ChatIntent.COMPLEX_TASK, 0.9, "DialogueAnalyzer: task execution required", originalInput);
        }
        if (decision.kind() == DialogueDecision.MessageKind.CROSS_DEPARTMENT) {
            return new ClassificationResult(ChatIntent.COMPLEX_TASK, 0.85, "DialogueAnalyzer: cross-department", originalInput);
        }
        if (decision.kind() == DialogueDecision.MessageKind.APPROVAL) {
            return new ClassificationResult(ChatIntent.COMPLEX_TASK, 0.8, "DialogueAnalyzer: approval required", originalInput);
        }
        if (decision.kind() == DialogueDecision.MessageKind.PROJECT) {
            return new ClassificationResult(ChatIntent.COMPLEX_TASK, 0.8, "DialogueAnalyzer: project related", originalInput);
        }
        if (decision.kind() == DialogueDecision.MessageKind.KNOWLEDGE) {
            return new ClassificationResult(ChatIntent.SIMPLE_QUESTION, 0.75, "DialogueAnalyzer: knowledge query", originalInput);
        }
        if (decision.kind() == DialogueDecision.MessageKind.CHAT) {
            return new ClassificationResult(ChatIntent.CASUAL_CHAT, 0.7, "DialogueAnalyzer: chat", originalInput);
        }
        return new ClassificationResult(ChatIntent.CASUAL_CHAT, 0.6, "DialogueAnalyzer: casual", originalInput);
    }

    private ClassificationResult classifyByRules(String userInput) {
        String normalizedInput = userInput.trim().toLowerCase();

        if (isGreeting(normalizedInput)) {
            return new ClassificationResult(ChatIntent.GREETING, 0.95, "Detected greeting");
        }

        if (containsToolKeywords(normalizedInput)) {
            return new ClassificationResult(ChatIntent.TOOL_CALL, 0.8, "Contains tool keywords");
        }

        if (containsComplexKeywords(normalizedInput)) {
            return new ClassificationResult(ChatIntent.COMPLEX_TASK, 0.75, "Contains complex task keywords");
        }

        if (isSimpleQuestion(normalizedInput)) {
            return new ClassificationResult(ChatIntent.SIMPLE_QUESTION, 0.7, "Simple question pattern");
        }

        if (isCasualChat(normalizedInput)) {
            return new ClassificationResult(ChatIntent.CASUAL_CHAT, 0.65, "Casual chat pattern");
        }

        int wordCount = normalizedInput.split("\\s+").length;
        if (wordCount <= 5) {
            return new ClassificationResult(ChatIntent.SIMPLE_QUESTION, 0.6, "Short input");
        }

        return new ClassificationResult(ChatIntent.CASUAL_CHAT, 0.5, "Default to casual chat");
    }

    private boolean isGreeting(String input) {
        String cleanInput = input.replaceAll("[\\s\\p{Punct}]", "");
        return GREETINGS.stream().anyMatch(greeting ->
            cleanInput.equals(greeting.toLowerCase()) ||
            cleanInput.contains(greeting.toLowerCase())
        );
    }

    private boolean containsToolKeywords(String input) {
        return TOOL_KEYWORDS.stream().anyMatch(keyword ->
            input.contains(keyword.toLowerCase())
        );
    }

    private boolean containsComplexKeywords(String input) {
        return COMPLEX_KEYWORDS.stream().anyMatch(keyword ->
            input.contains(keyword.toLowerCase())
        );
    }

    private boolean isSimpleQuestion(String input) {
        return QUESTION_PATTERN.matcher(input).find() && input.length() < 50;
    }

    private boolean isCasualChat(String input) {
        return CASUAL_PATTERNS.stream().anyMatch(pattern ->
            input.contains(pattern.toLowerCase())
        );
    }

    public boolean isQuickResponseCandidate(String userInput) {
        ClassificationResult result = classify(userInput);
        return result.shouldUseChatNeuron() && result.getConfidence() >= 0.6;
    }

    public boolean requiresToolCall(String userInput) {
        ClassificationResult result = classify(userInput);
        return result.getIntent() == ChatIntent.TOOL_CALL;
    }

    public boolean requiresComplexProcessing(String userInput) {
        ClassificationResult result = classify(userInput);
        return result.getIntent() == ChatIntent.COMPLEX_TASK;
    }
}
