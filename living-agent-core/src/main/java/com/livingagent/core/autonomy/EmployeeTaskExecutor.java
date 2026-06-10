package com.livingagent.core.autonomy;

import java.util.List;
import java.util.Map;

/**
 * 员工任务执行器接口
 * 用于阶段7：从"LLM文本执行"推进到"按任务类型调用真实工具"
 */
public interface EmployeeTaskExecutor {

    /**
     * 执行结果
     */
    record ExecutionResult(
        /** 执行是否成功 */
        boolean success,
        /** 执行状态：COMPLETED / FAILED / PARTIAL */
        String status,
        /** 执行总结 */
        String summary,
        /** 生成的产物文件列表 */
        List<ArtifactFile> artifacts,
        /** 使用的工具列表 */
        List<String> usedTools,
        /** 执行元数据 */
        Map<String, Object> metadata,
        /** 错误信息（如果失败） */
        String errorMessage
    ) {}

    /**
     * 产物文件
     */
    record ArtifactFile(
        /** 文件名 */
        String fileName,
        /** 文件路径 */
        String filePath,
        /** 文件类型：html/css/js/markdown/report */
        String fileType,
        /** 文件内容 */
        String content,
        /** 文件大小（字节） */
        long sizeBytes
    ) {}

    /**
     * 执行员工任务
     * 
     * @param employeeCode 员工代码
     * @param taskType 任务类型：web_prototype / web_development / software_development / document_generation / data_analysis / legal_review / finance_workflow
     * @taskDescription 任务描述
     * @assignmentTask 任务单
     * @availableTools 可用工具列表
     * @executionEnvironment 执行环境：DOCKER_SANDBOX / LOCAL_RESTRICTED / ARTIFACT_ONLY / HUMAN_REVIEW_REQUIRED
     * @return 执行结果
     */
    ExecutionResult executeTask(
        String employeeCode,
        String taskType,
        String taskDescription,
        EmployeeWorkAssignment assignmentTask,
        List<String> availableTools,
        String executionEnvironment
    );
}
