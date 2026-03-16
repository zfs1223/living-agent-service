package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SelfImprovingTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(SelfImprovingTool.class);

    private static final String NAME = "self_improving";
    private static final String DESCRIPTION = "自我改进工具，从错误中学习并持续优化";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "core";

    private final ObjectMapper objectMapper;
    private final Map<String, LearningRecord> learnings = new ConcurrentHashMap<>();
    private final Map<String, ErrorRecord> errors = new ConcurrentHashMap<>();
    private ToolStats stats = ToolStats.empty(NAME);

    public SelfImprovingTool() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDepartment() { return DEPARTMENT; }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("action", "string", "操作类型: record_error, record_correction, record_success, get_solution, get_learnings", true)
                .parameter("error_type", "string", "错误类型", false)
                .parameter("error_message", "string", "错误信息", false)
                .parameter("context", "object", "错误上下文", false)
                .parameter("original_output", "string", "原始输出", false)
                .parameter("corrected_output", "string", "纠正后的输出", false)
                .parameter("task_type", "string", "任务类型", false)
                .parameter("solution", "string", "解决方案", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("error_capture", "correction_learning", "solution_lookup", "pattern_optimization");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        
        try {
            Object result = switch (action) {
                case "record_error" -> recordError(params);
                case "record_correction" -> recordCorrection(params);
                case "record_success" -> recordSuccess(params);
                case "get_solution" -> getSolution(params);
                case "get_learnings" -> getLearnings(params);
                case "get_errors" -> getErrors(params);
                default -> throw new IllegalArgumentException("未知操作: " + action);
            };
            
            stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
            return ToolResult.success(result);
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            log.error("自我改进操作失败: {}", e.getMessage(), e);
            return ToolResult.failure("自我改进操作失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> recordError(ToolParams params) {
        String errorType = params.getString("error_type");
        String errorMessage = params.getString("error_message");
        Object ctxObj = params.get("context");
        Map<String, Object> errorContext = new HashMap<>();
        if (ctxObj instanceof Map) {
            errorContext = new HashMap<>((Map<String, Object>) ctxObj);
        }
        
        String errorId = UUID.randomUUID().toString();
        ErrorRecord record = new ErrorRecord(
            errorId, errorType, errorMessage, errorContext, 
            System.currentTimeMillis(), null, false
        );
        errors.put(errorId, record);
        
        Optional<LearningRecord> solution = findSolution(errorType, errorMessage);
        
        return Map.of(
            "error_id", errorId,
            "recorded", true,
            "has_solution", solution.isPresent(),
            "solution", solution.map(LearningRecord::solution).orElse(null)
        );
    }

    private Map<String, Object> recordCorrection(ToolParams params) {
        String originalOutput = params.getString("original_output");
        String correctedOutput = params.getString("corrected_output");
        String taskType = params.getString("task_type");
        
        String learningId = UUID.randomUUID().toString();
        LearningRecord learning = new LearningRecord(
            learningId, "correction", taskType, 
            originalOutput, correctedOutput, 
            System.currentTimeMillis(), 0, 1.0
        );
        learnings.put(learningId, learning);
        
        return Map.of(
            "learning_id", learningId,
            "recorded", true,
            "type", "correction"
        );
    }

    private Map<String, Object> recordSuccess(ToolParams params) {
        String taskType = params.getString("task_type");
        String approach = params.getString("approach");
        
        String learningId = UUID.randomUUID().toString();
        LearningRecord learning = new LearningRecord(
            learningId, "success", taskType,
            approach, approach,
            System.currentTimeMillis(), 0, 1.0
        );
        learnings.put(learningId, learning);
        
        return Map.of(
            "learning_id", learningId,
            "recorded", true,
            "type", "success"
        );
    }

    private Map<String, Object> getSolution(ToolParams params) {
        String errorType = params.getString("error_type");
        String errorMessage = params.getString("error_message");
        
        Optional<LearningRecord> solution = findSolution(errorType, errorMessage);
        
        if (solution.isPresent()) {
            LearningRecord record = solution.get();
            return Map.of(
                "found", true,
                "solution", record.solution(),
                "confidence", record.confidence(),
                "applied_count", record.appliedCount()
            );
        }
        
        return Map.of("found", false);
    }

    private List<Map<String, Object>> getLearnings(ToolParams params) {
        String type = params.getString("type");
        
        return learnings.values().stream()
            .filter(l -> type == null || l.type().equals(type))
            .sorted((a, b) -> Long.compare(b.timestamp(), a.timestamp()))
            .limit(20)
            .map(l -> Map.<String, Object>of(
                "learning_id", l.id(),
                "type", l.type(),
                "task_type", l.taskType(),
                "confidence", l.confidence(),
                "applied_count", l.appliedCount()
            ))
            .toList();
    }

    private List<Map<String, Object>> getErrors(ToolParams params) {
        String errorType = params.getString("error_type");
        
        return errors.values().stream()
            .filter(e -> errorType == null || e.errorType().equals(errorType))
            .sorted((a, b) -> Long.compare(b.timestamp(), a.timestamp()))
            .limit(20)
            .map(e -> Map.<String, Object>of(
                "error_id", e.id(),
                "error_type", e.errorType(),
                "error_message", e.errorMessage(),
                "resolved", e.resolved()
            ))
            .toList();
    }

    private Optional<LearningRecord> findSolution(String errorType, String errorMessage) {
        return learnings.values().stream()
            .filter(l -> l.type().equals("correction"))
            .filter(l -> errorMessage != null && errorMessage.contains(l.original()))
            .max(Comparator.comparingDouble(LearningRecord::confidence));
    }

    @Override
    public void validate(ToolParams params) {
        if (params.getString("action") == null) {
            throw new IllegalArgumentException("action 参数不能为空");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) { return true; }

    @Override
    public boolean requiresApproval() { return false; }

    @Override
    public ToolStats getStats() { return stats; }

    private record ErrorRecord(
        String id, String errorType, String errorMessage, 
        Map<String, Object> context, long timestamp, 
        String resolution, boolean resolved
    ) {}

    private record LearningRecord(
        String id, String type, String taskType,
        String original, String solution,
        long timestamp, int appliedCount, double confidence
    ) {}
}
