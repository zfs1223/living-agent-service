package com.livingagent.core.brain.collaboration;

import com.livingagent.core.autonomy.CodeReviewWorkflowService;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.planner.dag.DagTask;
import com.livingagent.core.planner.dag.TaskDagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大脑层协作编排器 —— 部门大脑如何指挥数字员工执行任务。
 *
 * <p><b>层次边界</b>：本接口属于 brain 层，负责"大脑如何指挥员工"。
 * 与 {@code worker.collaboration.CollaborationService}（工人层，负责"员工之间如何协作"）职责不同：
 * <ul>
 *   <li>brain/collaboration：大脑 → 员工，单向指挥，基于 Channel 通道系统和 TaskDagService</li>
 *   <li>worker/collaboration：员工 ↔ 员工，多向协作，基于内存会话和7种协作类型</li>
 * </ul>
 * 大脑层的任务编排优先使用本接口，工人层的协作会话管理使用 CollaborationService。</p>
 */
public interface LeadOrchestrator {

    String getLeadBrainId();

    List<TeammateRole> getAvailableTeammates();

    TeammateAssignment assignTask(String taskId, String teammateNeuronId);

    TeammateAssignment assignTask(DagTask task, TeammateRole teammate);

    List<TeammateAssignment> getActiveAssignments();

    Optional<TeammateAssignment> getAssignment(String taskId);

    void completeTask(String taskId, String result);

    void failTask(String taskId, String error);

    void sendToTeammate(String teammateNeuronId, String message);

    void broadcastToTeam(String message);

    List<ChannelMessage> readFromTeam(String leadBrainId);

    // ========== 代码审查循环驱动 ==========

    /**
     * 提交代码进入审查流程：CODE_SUBMITTED → ASSIGN_REVIEWER → REVIEWING。
     *
     * <p><b>实现说明</b>：此 default 方法为接口占位，实际实现请使用 {@link com.livingagent.core.brain.collaboration.impl.TechLeadOrchestrator}。
     * 该实现类已完整实现代码审查闭环（submit→review→changes→resubmit→approve/escalate）。</p>
     *
     * @param taskId 任务ID
     * @param reviewerNeuronId 审查员工神经元ID
     * @return 更新后的审查状态
     * @throws UnsupportedOperationException 总是抛出，提示使用 TechLeadOrchestrator 实现
     */
    default CodeReviewWorkflowService.ReviewState submitForReview(String taskId, String reviewerNeuronId) {
        throw new UnsupportedOperationException(
            "submitForReview: default interface method not implemented. " +
            "Use TechLeadOrchestrator (brain/collaboration/impl/TechLeadOrchestrator.java) " +
            "which provides full code review workflow implementation.");
    }

    /**
     * 审查不通过，要求修改：REVIEWING → REVIEW_CHANGES_REQUESTED → DEVELOPER_REVISING。
     *
     * <p><b>实现说明</b>：此 default 方法为接口占位，实际实现请使用 {@link com.livingagent.core.brain.collaboration.impl.TechLeadOrchestrator}。</p>
     *
     * @param taskId 任务ID
     * @param findings 审查发现的问题列表
     * @return 更新后的审查状态
     * @throws UnsupportedOperationException 总是抛出，提示使用 TechLeadOrchestrator 实现
     */
    default CodeReviewWorkflowService.ReviewState requestChanges(String taskId, List<String> findings) {
        throw new UnsupportedOperationException(
            "requestChanges: default interface method not implemented. " +
            "Use TechLeadOrchestrator (brain/collaboration/impl/TechLeadOrchestrator.java) " +
            "which provides full code review workflow implementation.");
    }

    /**
     * 开发员工修改后重新提交：DEVELOPER_REVISING → CODE_RESUBMITTED → ASSIGN_REVIEWER。
     *
     * <p><b>实现说明</b>：此 default 方法为接口占位，实际实现请使用 {@link com.livingagent.core.brain.collaboration.impl.TechLeadOrchestrator}。</p>
     *
     * @param taskId 任务ID
     * @return 更新后的审查状态
     * @throws UnsupportedOperationException 总是抛出，提示使用 TechLeadOrchestrator 实现
     */
    default CodeReviewWorkflowService.ReviewState resubmitCode(String taskId) {
        throw new UnsupportedOperationException(
            "resubmitCode: default interface method not implemented. " +
            "Use TechLeadOrchestrator (brain/collaboration/impl/TechLeadOrchestrator.java) " +
            "which provides full code review workflow implementation.");
    }

    /**
     * 审查通过：REVIEWING → REVIEW_APPROVED → FINAL_SUMMARY。
     *
     * <p><b>实现说明</b>：此 default 方法为接口占位，实际实现请使用 {@link com.livingagent.core.brain.collaboration.impl.TechLeadOrchestrator}。</p>
     *
     * @param taskId 任务ID
     * @return 更新后的审查状态
     * @throws UnsupportedOperationException 总是抛出，提示使用 TechLeadOrchestrator 实现
     */
    default CodeReviewWorkflowService.ReviewState approveCode(String taskId) {
        throw new UnsupportedOperationException(
            "approveCode: default interface method not implemented. " +
            "Use TechLeadOrchestrator (brain/collaboration/impl/TechLeadOrchestrator.java) " +
            "which provides full code review workflow implementation.");
    }

    /**
     * 升级到人工处理：任意阶段 → ESCALATED。
     *
     * <p><b>实现说明</b>：此 default 方法为接口占位，实际实现请使用 {@link com.livingagent.core.brain.collaboration.impl.TechLeadOrchestrator}。</p>
     *
     * @param taskId 任务ID
     * @param reason 升级原因
     * @return 更新后的审查状态
     * @throws UnsupportedOperationException 总是抛出，提示使用 TechLeadOrchestrator 实现
     */
    default CodeReviewWorkflowService.ReviewState escalateReview(String taskId, String reason) {
        throw new UnsupportedOperationException(
            "escalateReview: default interface method not implemented. " +
            "Use TechLeadOrchestrator (brain/collaboration/impl/TechLeadOrchestrator.java) " +
            "which provides full code review workflow implementation.");
    }

    /**
     * 获取任务的审查状态。
     *
     * @param taskId 任务ID
     * @return 审查状态，可能为空
     */
    default Optional<CodeReviewWorkflowService.ReviewState> getReviewState(String taskId) {
        return Optional.empty();
    }
}
