package com.livingagent.gateway.service;

import com.livingagent.gateway.controller.TaskController;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TaskWorkflowService {

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
        return payload;
    }
}
