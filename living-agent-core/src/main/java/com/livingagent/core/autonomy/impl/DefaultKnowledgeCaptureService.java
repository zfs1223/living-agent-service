package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.KnowledgeCaptureResult;
import com.livingagent.core.autonomy.KnowledgeCaptureService;
import com.livingagent.core.autonomy.TaskMetadataKeys;
import com.livingagent.core.knowledge.KnowledgeManager;
import com.livingagent.core.knowledge.KnowledgeType;
import com.livingagent.core.knowledge.Importance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class DefaultKnowledgeCaptureService implements KnowledgeCaptureService {

    private static final Logger log = LoggerFactory.getLogger(DefaultKnowledgeCaptureService.class);

    private final KnowledgeManager knowledgeManager;

    public DefaultKnowledgeCaptureService(KnowledgeManager knowledgeManager) {
        this.knowledgeManager = knowledgeManager;
    }

    @Override
    public KnowledgeCaptureResult captureFromExecution(
            String executionId,
            String department,
            String taskType,
            String goal,
            String resultSummary,
            List<String> employeeCodes) {
        if (executionId == null || goal == null || goal.isBlank()) {
            log.debug("Knowledge capture skipped: executionId or goal is empty");
            return KnowledgeCaptureResult.skipped("executionId or goal is empty");
        }

        try {
            String key = "execution://" + department + "/" + taskType + "/" + executionId;
            Map<String, String> metadata = Map.of(
                "executionId", executionId,
                "department", department,
                TaskMetadataKeys.TASK_TYPE, taskType,
                "employeeCodes", String.join(",", employeeCodes),
                "capturedAt", Instant.now().toString()
            );

            String content = buildKnowledgeContent(taskType, goal, resultSummary, employeeCodes);

            knowledgeManager.storeDomain(key, content, KnowledgeType.EXPERIENCE, Importance.MEDIUM);

            log.info("Knowledge captured: key={}, department={}, taskType={}", key, department, taskType);
            return KnowledgeCaptureResult.success(key, "DOMAIN", department, content);
        } catch (Exception e) {
            log.warn("Knowledge capture failed: executionId={}, error={}", executionId, e.getMessage());
            return KnowledgeCaptureResult.skipped("capture failed: " + e.getMessage());
        }
    }

    private String buildKnowledgeContent(String taskType, String goal, String resultSummary, List<String> employeeCodes) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务类型: ").append(taskType).append("\n");
        sb.append("目标: ").append(goal).append("\n");
        if (resultSummary != null && !resultSummary.isBlank()) {
            sb.append("执行结果: ").append(resultSummary).append("\n");
        }
        if (employeeCodes != null && !employeeCodes.isEmpty()) {
            sb.append("参与员工: ").append(String.join(", ", employeeCodes)).append("\n");
        }
        sb.append("捕获时间: ").append(Instant.now());
        return sb.toString();
    }
}
