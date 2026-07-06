package com.livingagent.gateway.service;

import com.livingagent.core.database.entity.TaskEntity;
import com.livingagent.core.database.repository.TaskRepository;
import com.livingagent.gateway.controller.TaskController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class TaskWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(TaskWorkflowService.class);
    private final TaskRepository taskRepository;

    public TaskWorkflowService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public Map<String, Object> summarizeReview(TaskController.TaskReviewResult review) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", review.taskId());
        payload.put("employeeId", review.employeeId());
        payload.put("reviewerId", review.reviewerId());
        payload.put("approved", review.approved());
        payload.put("rewardGranted", review.rewardGranted());
        payload.put("qualityScore", review.qualityScore());
        payload.put("comment", review.comment());
        payload.put("reviewedAt", review.reviewedAt());

        Optional<TaskEntity> taskOpt = taskRepository.findByTaskId(review.taskId());
        if (taskOpt.isPresent()) {
            TaskEntity task = taskOpt.get();
            task.setReviewerId(review.reviewerId());
            task.setReviewConclusion(buildConclusion(review));
            task.setReviewedAt(Instant.now());
            task.setUpdatedAt(Instant.now());
            if (review.approved()) {
                task.setStatus("completed");
                task.setCompletedAt(Instant.now());
            } else {
                task.setStatus("review_failed");
            }
            taskRepository.save(task);
            log.info("Task {} review persisted: approved={}, qualityScore={}, reviewer={}",
                    review.taskId(), review.approved(), review.qualityScore(), review.reviewerId());
        } else {
            log.warn("Task {} not found for review persistence", review.taskId());
        }

        return payload;
    }

    private String buildConclusion(TaskController.TaskReviewResult review) {
        StringBuilder sb = new StringBuilder();
        sb.append(review.approved() ? "APPROVED" : "REJECTED");
        if (review.comment() != null && !review.comment().isBlank()) {
            sb.append(": ").append(review.comment());
        }
        sb.append(" [quality=").append(review.qualityScore()).append(", reward=").append(review.rewardGranted()).append("]");
        return sb.toString();
    }
}
