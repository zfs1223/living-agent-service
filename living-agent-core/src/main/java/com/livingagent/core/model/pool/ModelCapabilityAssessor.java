package com.livingagent.core.model.pool;

import java.util.List;
import java.util.Map;

/**
 * 模型能力评定服务
 * 
 * 职责：
 * 1. 服务启动时自动评定所有已知模型的能力标签和性能评分
 * 2. 新增模型时自动评定其适合的任务类型和员工职责
 * 3. 为 BrainModelResolver 提供模型选择的能力依据
 */
public interface ModelCapabilityAssessor {

    /**
     * 评定单个模型的能力
     * @param model 待评定的模型
     * @return 评定后的模型（包含 capabilityTags, performanceScore, parameterSize）
     */
    LlmModel assessModel(LlmModel model);

    /**
     * 批量评定模型能力
     * @param models 待评定的模型列表
     */
    void assessModels(List<LlmModel> models);

    /**
     * 根据任务类型和员工职责筛选最合适的模型
     * @param taskType 任务类型（如 web_development, data_analysis, code_review 等）
     * @param employeeRole 员工角色（如 frontend_engineer, data_analyst, code_reviewer 等）
     * @param availableModels 可用模型列表
     * @return 最合适的模型，如果没有合适的则返回 null
     */
    LlmModel selectBestModelForTask(String taskType, String employeeRole, List<LlmModel> availableModels);

    /**
     * 获取模型能力标签与任务类型的映射关系
     * @return 映射表
     */
    Map<String, List<String>> getCapabilityTaskMapping();

    /**
     * 重新评定所有已启用的模型
     */
    void reassessAllEnabledModels();
}
