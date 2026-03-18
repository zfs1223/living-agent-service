package com.livingagent.core.evolution.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DefaultSignalExtractor implements SignalExtractor {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultSignalExtractor.class);
    
    private static final Pattern ERROR_PATTERN = Pattern.compile(
        "(ERROR|Exception|Failed|failure|error|异常|失败)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern PERFORMANCE_PATTERN = Pattern.compile(
        "(slow|timeout|latency|performance|延迟|超时|慢|性能)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern OPPORTUNITY_PATTERN = Pattern.compile(
        "(could be|should be|improve|enhance|better|建议|改进|优化|可以)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern CAPABILITY_GAP_PATTERN = Pattern.compile(
        "(cannot|unable|not supported|missing|lack|无法|不支持|缺少|缺失)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final List<String> BRAIN_DOMAINS = Arrays.asList(
        "tech", "hr", "finance", "sales", "ops", "admin", "legal", "cs", "main"
    );
    
    @Override
    public List<EvolutionSignal> extractFromConversation(String conversationId) {
        log.debug("Extracting signals from conversation: {}", conversationId);
        List<EvolutionSignal> signals = new ArrayList<>();
        
        return signals;
    }
    
    public List<EvolutionSignal> extractFromConversationContent(String conversationId, String content, String brainDomain) {
        List<EvolutionSignal> signals = new ArrayList<>();
        
        if (content == null || content.isEmpty()) {
            return signals;
        }
        
        Matcher errorMatcher = ERROR_PATTERN.matcher(content);
        while (errorMatcher.find()) {
            EvolutionSignal signal = EvolutionSignal.error(
                null, "conversation", errorMatcher.group(), brainDomain
            );
            signal.addMetadata("conversationId", conversationId);
            signal.addMetadata("position", errorMatcher.start());
            signals.add(signal);
        }
        
        Matcher perfMatcher = PERFORMANCE_PATTERN.matcher(content);
        while (perfMatcher.find()) {
            EvolutionSignal signal = EvolutionSignal.performance(
                perfMatcher.group(), brainDomain, 0.6
            );
            signal.addMetadata("conversationId", conversationId);
            signals.add(signal);
        }
        
        Matcher oppMatcher = OPPORTUNITY_PATTERN.matcher(content);
        while (oppMatcher.find()) {
            EvolutionSignal signal = EvolutionSignal.opportunity(
                oppMatcher.group(), brainDomain, 0.5
            );
            signal.addMetadata("conversationId", conversationId);
            signals.add(signal);
        }
        
        Matcher gapMatcher = CAPABILITY_GAP_PATTERN.matcher(content);
        while (gapMatcher.find()) {
            EvolutionSignal signal = EvolutionSignal.capabilityGap(
                gapMatcher.group(), brainDomain
            );
            signal.addMetadata("conversationId", conversationId);
            signals.add(signal);
        }
        
        log.debug("Extracted {} signals from conversation {}", signals.size(), conversationId);
        return signals;
    }
    
    @Override
    public List<EvolutionSignal> extractFromLogs(String logContent) {
        List<EvolutionSignal> signals = new ArrayList<>();
        
        if (logContent == null || logContent.isEmpty()) {
            return signals;
        }
        
        String[] lines = logContent.split("\n");
        for (String line : lines) {
            if (line.contains("ERROR") || line.contains("Exception")) {
                String brainDomain = extractBrainDomainFromLog(line);
                EvolutionSignal signal = EvolutionSignal.error(
                    null, "log", line.trim(), brainDomain
                );
                signal.addMetadata("logLine", line);
                signals.add(signal);
            } else if (line.contains("WARN")) {
                String brainDomain = extractBrainDomainFromLog(line);
                EvolutionSignal signal = new EvolutionSignal(
                    EvolutionSignal.SignalType.DRIFT, line.trim()
                );
                signal.setBrainDomain(brainDomain);
                signal.setConfidence(0.5);
                signal.addMetadata("logLine", line);
                signals.add(signal);
            }
        }
        
        log.debug("Extracted {} signals from logs", signals.size());
        return signals;
    }
    
    @Override
    public List<EvolutionSignal> extractFromMetrics(Map<String, Object> metrics) {
        List<EvolutionSignal> signals = new ArrayList<>();
        
        if (metrics == null || metrics.isEmpty()) {
            return signals;
        }
        
        if (metrics.containsKey("errorRate")) {
            double errorRate = ((Number) metrics.get("errorRate")).doubleValue();
            if (errorRate > 0.05) {
                EvolutionSignal signal = EvolutionSignal.error(
                    null, "metrics", 
                    String.format("High error rate: %.2f%%", errorRate * 100),
                    "main"
                );
                signal.setConfidence(Math.min(errorRate * 2, 1.0));
                signals.add(signal);
            }
        }
        
        if (metrics.containsKey("avgResponseTime")) {
            double avgResponseTime = ((Number) metrics.get("avgResponseTime")).doubleValue();
            if (avgResponseTime > 5000) {
                EvolutionSignal signal = EvolutionSignal.performance(
                    String.format("Slow response time: %.0fms", avgResponseTime),
                    "main", 0.7
                );
                signals.add(signal);
            }
        }
        
        if (metrics.containsKey("toolSuccessRate")) {
            double toolSuccessRate = ((Number) metrics.get("toolSuccessRate")).doubleValue();
            if (toolSuccessRate < 0.8) {
                EvolutionSignal signal = EvolutionSignal.capabilityGap(
                    String.format("Low tool success rate: %.2f%%", toolSuccessRate * 100),
                    "tech"
                );
                signals.add(signal);
            }
        }
        
        if (metrics.containsKey("memoryUsage")) {
            double memoryUsage = ((Number) metrics.get("memoryUsage")).doubleValue();
            if (memoryUsage > 0.85) {
                EvolutionSignal signal = EvolutionSignal.performance(
                    String.format("High memory usage: %.2f%%", memoryUsage * 100),
                    "tech", 0.8
                );
                signals.add(signal);
            }
        }
        
        log.debug("Extracted {} signals from metrics", signals.size());
        return signals;
    }
    
    @Override
    public List<EvolutionSignal> extractFromUserFeedback(String feedback) {
        List<EvolutionSignal> signals = new ArrayList<>();
        
        if (feedback == null || feedback.isEmpty()) {
            return signals;
        }
        
        String lowerFeedback = feedback.toLowerCase();
        
        if (lowerFeedback.contains("不好") || lowerFeedback.contains("差") || 
            lowerFeedback.contains("bad") || lowerFeedback.contains("poor")) {
            EvolutionSignal signal = new EvolutionSignal(
                EvolutionSignal.SignalType.USER_REQUEST, feedback
            );
            signal.setCategory(EvolutionSignal.SignalCategory.REPAIR);
            signal.setConfidence(0.8);
            signals.add(signal);
        } else if (lowerFeedback.contains("希望") || lowerFeedback.contains("建议") ||
                   lowerFeedback.contains("should") || lowerFeedback.contains("wish")) {
            EvolutionSignal signal = new EvolutionSignal(
                EvolutionSignal.SignalType.USER_REQUEST, feedback
            );
            signal.setCategory(EvolutionSignal.SignalCategory.INNOVATE);
            signal.setConfidence(0.7);
            signals.add(signal);
        } else if (lowerFeedback.contains("慢") || lowerFeedback.contains("快") ||
                   lowerFeedback.contains("slow") || lowerFeedback.contains("fast")) {
            EvolutionSignal signal = new EvolutionSignal(
                EvolutionSignal.SignalType.USER_REQUEST, feedback
            );
            signal.setCategory(EvolutionSignal.SignalCategory.OPTIMIZE);
            signal.setConfidence(0.7);
            signals.add(signal);
        } else {
            EvolutionSignal signal = new EvolutionSignal(
                EvolutionSignal.SignalType.USER_REQUEST, feedback
            );
            signal.setConfidence(0.5);
            signals.add(signal);
        }
        
        log.debug("Extracted {} signals from user feedback", signals.size());
        return signals;
    }
    
    @Override
    public EvolutionSignal.SignalCategory determineCategory(EvolutionSignal signal) {
        if (signal == null || signal.getType() == null) {
            return EvolutionSignal.SignalCategory.OPTIMIZE;
        }
        
        switch (signal.getType()) {
            case ERROR:
            case CAPABILITY_GAP:
                return EvolutionSignal.SignalCategory.REPAIR;
            case PERFORMANCE:
            case DRIFT:
                return EvolutionSignal.SignalCategory.OPTIMIZE;
            case OPPORTUNITY:
            case USER_REQUEST:
            case STABILITY:
                return EvolutionSignal.SignalCategory.INNOVATE;
            default:
                return EvolutionSignal.SignalCategory.OPTIMIZE;
        }
    }
    
    @Override
    public String determineBrainDomain(EvolutionSignal signal) {
        if (signal == null) {
            return "main";
        }
        
        if (signal.getBrainDomain() != null && !signal.getBrainDomain().isEmpty()) {
            return signal.getBrainDomain();
        }
        
        String content = signal.getContent();
        if (content == null) {
            return "main";
        }
        
        String lowerContent = content.toLowerCase();
        
        if (lowerContent.contains("code") || lowerContent.contains("代码") ||
            lowerContent.contains("deploy") || lowerContent.contains("部署") ||
            lowerContent.contains("git") || lowerContent.contains("api")) {
            return "tech";
        }
        if (lowerContent.contains("employee") || lowerContent.contains("员工") ||
            lowerContent.contains("recruit") || lowerContent.contains("招聘")) {
            return "hr";
        }
        if (lowerContent.contains("finance") || lowerContent.contains("财务") ||
            lowerContent.contains("budget") || lowerContent.contains("预算")) {
            return "finance";
        }
        if (lowerContent.contains("sale") || lowerContent.contains("销售") ||
            lowerContent.contains("customer") || lowerContent.contains("客户")) {
            return "sales";
        }
        if (lowerContent.contains("operation") || lowerContent.contains("运营") ||
            lowerContent.contains("data") || lowerContent.contains("数据")) {
            return "ops";
        }
        
        return "main";
    }
    
    private String extractBrainDomainFromLog(String logLine) {
        for (String domain : BRAIN_DOMAINS) {
            if (logLine.toLowerCase().contains(domain)) {
                return domain;
            }
        }
        return "main";
    }
}
