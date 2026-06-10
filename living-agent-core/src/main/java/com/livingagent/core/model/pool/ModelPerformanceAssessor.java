package com.livingagent.core.model.pool;

import java.util.List;

/**
 * 模型性能评定服务 - 由 T05（AI模型管理员）调用
 * 
 * 职责：
 * 1. 对模型池中所有已启用的模型进行实际性能测试
 * 2. 测试内容包括：响应速度、可用性、响应质量等
 * 3. 将评定结果保存到数据库（performanceScore、capabilityTags）
 * 4. 支持全量评定和单个模型评定
 */
public interface ModelPerformanceAssessor {

    /**
     * 评定结果
     */
    record AssessmentResult(
        String modelId,
        String providerId,
        String modelName,
        boolean available,
        long responseTimeMs,
        String responsePreview,
        String capabilityTags,
        int performanceScore,
        String error
    ) {}

    /**
     * 评定单个模型的实际性能
     * @param model 待评定的模型
     * @param testPrompt 测试用的 prompt（如果为 null，使用默认 prompt）
     * @return 评定结果
     */
    AssessmentResult assessModel(LlmModel model, String testPrompt);

    /**
     * 批量评定所有已启用的模型
     * @return 所有模型的评定结果列表
     */
    List<AssessmentResult> assessAllEnabledModels();

    /**
     * 批量评定指定 Provider 的所有模型
     * @param providerId Provider ID
     * @return 该 Provider 下所有模型的评定结果
     */
    List<AssessmentResult> assessProviderModels(String providerId);

    /**
     * 获取评定进度
     * @return 评定进度（已评定数/总数）
     */
    AssessmentProgress getProgress();

    /**
     * 评定进度
     */
    record AssessmentProgress(int total, int completed, int failed, boolean running) {}

    /**
     * 停止正在进行的评定
     */
    void stopAssessment();
}
