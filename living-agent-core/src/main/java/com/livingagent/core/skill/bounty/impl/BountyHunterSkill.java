package com.livingagent.core.skill.bounty.impl;

import com.livingagent.core.skill.Skill;
import com.livingagent.core.skill.SkillContext;
import com.livingagent.core.skill.SkillResult;
import com.livingagent.core.skill.bounty.BountyHunterService;
import com.livingagent.core.skill.bounty.BountyTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

public class BountyHunterSkill implements Skill {

    private static final Logger log = LoggerFactory.getLogger(BountyHunterSkill.class);

    private static final String SKILL_ID = "bounty-hunter";
    private static final String SKILL_NAME = "赏金猎人";
    private static final String SKILL_DESCRIPTION = "自动寻找并完成有偿任务赚取收益";
    private static final String SKILL_CATEGORY = "bounty";
    private static final String TARGET_BRAIN = "*";
    private static final String DEFAULT_CONTENT = """
# BountyHunter Skill
This skill allows agents to automatically find and accept and complete tasks 
# to earn rewards.

## Available Actions:
- find: Find available tasks
- accept: Accept a task
- submit: Submit task deliverables
- status: Check task status
- earnings: View earnings
- recommend: Get recommended tasks

## Parameters
- type: Type of task to find (e.g., "coding", "git", "jenkins")
- difficulty: Difficulty level (e.g., "easy", "medium", "hard")
- minReward: Minimum reward amount
- limit: Maximum number of results to return
    """;

    private final BountyHunterService bountyHunterService;
    private String content = DEFAULT_CONTENT;
    private String skillPath;

    public BountyHunterSkill(BountyHunterService bountyHunterService) {
        this.bountyHunterService = bountyHunterService;
    }

    @Override
    public String getId() {
        return SKILL_ID;
    }

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        return SKILL_DESCRIPTION;
    }

    @Override
    public String getCategory() {
        return SKILL_CATEGORY;
    }

    @Override
    public String getTargetBrain() {
        return TARGET_BRAIN;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String getSkillPath() {
        return skillPath;
    }

    public void setSkillPath(String skillPath) {
        this.skillPath = skillPath;
    }

    @Override
    public Map<String, Object> getMetadata() {
        return Map.of(
            "id", SKILL_ID,
            "name", SKILL_NAME,
            "description", SKILL_DESCRIPTION,
            "category", SKILL_CATEGORY,
            "targetBrain", TARGET_BRAIN
        );
    }

    @Override
    public String getMetadataSummary() {
        return String.format("Skill: %s (%s) - %s", SKILL_NAME, SKILL_ID, SKILL_DESCRIPTION);
    }

    @Override
    public List<String> getRequiredCapabilities() {
        return List.of("task-execution", "skill-matching", "reward-optimization");
    }

    @Override
    public SkillResult execute(SkillContext context) {
        String action = context.getParameter("action", "find");
        String workerId = context.getNeuronId();
        
        try {
            return switch (action.toLowerCase()) {
                case "find" -> executeFind(context, workerId);
                case "accept" -> executeAccept(context, workerId);
                case "submit" -> executeSubmit(context, workerId);
                case "status" -> executeStatus(context, workerId);
                case "earnings" -> executeEarnings(context, workerId);
                case "recommend" -> executeRecommend(context, workerId);
                default -> SkillResult.failure("Unknown action: " + action);
            };
        } catch (Exception e) {
            log.error("BountyHunter skill execution failed: {}", e.getMessage());
            return SkillResult.failure(e.getMessage());
        }
    }

    private SkillResult executeFind(SkillContext context, String workerId) {
        String typeStr = context.getParameter("type", "");
        String difficultyStr = context.getParameter("difficulty", "");
        double minReward = context.getParameter("minReward", 0.0);
        int limit = context.getParameter("limit", 10);
        
        List<BountyTask> tasks;
        
        if (!typeStr.isEmpty()) {
            BountyTask.BountyType type = BountyTask.BountyType.valueOf(typeStr.toUpperCase());
            tasks = bountyHunterService.findTasksByType(type);
        } else if (!difficultyStr.isEmpty()) {
            BountyTask.DifficultyLevel difficulty = BountyTask.DifficultyLevel.valueOf(difficultyStr.toUpperCase());
            tasks = bountyHunterService.findTasksByDifficulty(difficulty);
        } else if (minReward > 0) {
            tasks = bountyHunterService.findHighRewardTasks(minReward);
        } else {
            tasks = bountyHunterService.findAvailableTasks(workerId);
        }

        List<Map<String, Object>> taskList = tasks.stream()
            .limit(limit)
            .map(this::taskToMap)
            .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("tasks", taskList);
        result.put("total", tasks.size());

        return SkillResult.success(result);
    }

    private SkillResult executeAccept(SkillContext context, String workerId) {
        String taskId = context.getParameter("taskId", "");
        
        if (taskId.isEmpty()) {
            return SkillResult.failure("taskId is required");
        }

        boolean accepted = bountyHunterService.acceptTask(taskId, workerId);
        
        if (!accepted) {
            return SkillResult.failure("Failed to accept task: " + taskId);
        }

        BountyTask task = bountyHunterService.getTask(taskId).orElse(null);
        if (task == null) {
            return SkillResult.failure("Task not found: " + taskId);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("accepted", true);
        result.put("task", taskToMap(task));
        
        return SkillResult.success(result);
    }

    private SkillResult executeSubmit(SkillContext context, String workerId) {
        String taskId = context.getParameter("taskId", "");
        Map<String, Object> deliverables = context.getParameter("deliverables", Map.of());
        String notes = context.getParameter("notes", "");
        
        if (taskId.isEmpty()) {
            return SkillResult.failure("taskId is required");
        }

        BountyHunterService.BountySubmission submission = new BountyHunterService.BountySubmission(
            taskId,
            workerId,
            deliverables,
            notes,
            Instant.now()
        );
        
        boolean submitted = bountyHunterService.submitTask(taskId, workerId, submission);
        if (!submitted) {
            return SkillResult.failure("Failed to submit task: " + taskId);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("submitted", true);
        result.put("taskId", taskId);
        
        return SkillResult.success(result);
    }

    private SkillResult executeStatus(SkillContext context, String workerId) {
        String taskId = context.getParameter("taskId", "");
        
        if (!taskId.isEmpty()) {
            Optional<BountyTask> taskOpt = bountyHunterService.getTask(taskId);
            if (taskOpt.isPresent()) {
                return SkillResult.success(taskToMap(taskOpt.get()));
            } else {
                return SkillResult.failure("Task not found: " + taskId);
            }
        }

        List<BountyTask> activeTasks = bountyHunterService.getWorkerActiveTasks(workerId);
        List<Map<String, Object>> taskList = activeTasks.stream()
            .map(this::taskToMap)
            .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("activeTasks", taskList);
        result.put("activeCount", activeTasks.size());

        return SkillResult.success(result);
    }

    private SkillResult executeEarnings(SkillContext context, String workerId) {
        BountyHunterService.WorkerEarnings earnings = bountyHunterService.getWorkerEarnings(workerId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalEarned", earnings.totalEarned());
        result.put("pendingEarnings", earnings.pendingEarnings());
        result.put("tasksCompleted", earnings.tasksCompleted());
        result.put("tasksRejected", earnings.tasksRejected());
        result.put("averageRating", earnings.averageRating());
        result.put("successRate", earnings.successRate());

        return SkillResult.success(result);
    }

    private SkillResult executeRecommend(SkillContext context, String workerId) {
        List<String> skills = context.getParameter("skills", List.of());
        int limit = context.getParameter("limit", 5);
        
        List<BountyTask> matchingTasks = bountyHunterService.findMatchingTasks(workerId, skills);
        
        List<Map<String, Object>> recommendations = matchingTasks.stream()
            .sorted(Comparator.comparingDouble(BountyTask::getReward).reversed())
            .limit(limit)
            .map(task -> {
                Map<String, Object> rec = new HashMap<>(taskToMap(task));
                rec.put("matchScore", calculateMatchScore(task, skills));
                rec.put("recommendationReason", generateRecommendationReason(task, skills));
                return rec;
            })
            .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("recommendations", recommendations);

        return SkillResult.success(result);
    }

    private Map<String, Object> taskToMap(BountyTask task) {
        Map<String, Object> map = new HashMap<>();
        map.put("taskId", task.getTaskId());
        map.put("title", task.getTitle());
        map.put("description", task.getDescription());
        map.put("type", task.getType().name());
        map.put("status", task.getStatus().name());
        map.put("reward", task.getReward());
        map.put("currency", task.getCurrency());
        map.put("difficulty", task.getDifficulty().name());
        map.put("deadline", task.getDeadline() != null ? task.getDeadline().toString() : null);
        map.put("requiredSkills", task.getRequiredSkills());
        return map;
    }

    private double calculateMatchScore(BountyTask task, List<String> workerSkills) {
        if (workerSkills.isEmpty()) {
            return 0.5;
        }

        List<String> requiredSkills = task.getRequiredSkills();
        if (requiredSkills.isEmpty()) {
            return 0.7;
        }

        long matchingSkills = requiredSkills.stream()
            .filter(workerSkills::contains)
            .count();

        return (double) matchingSkills / requiredSkills.size();
    }

    private String generateRecommendationReason(BountyTask task, List<String> workerSkills) {
        List<String> requiredSkills = task.getRequiredSkills();
        
        if (requiredSkills.isEmpty()) {
            return "任务无特殊技能要求，适合快速完成";
        }

        List<String> matched = requiredSkills.stream()
            .filter(workerSkills::contains)
            .toList();

        if (matched.size() == requiredSkills.size()) {
            return "完全匹配您的技能，推荐优先完成";
        } else if (!matched.isEmpty()) {
            return "高奖励任务，可能需要学习新技能";
        } else {
            return "部分匹配您的技能: " + String.join(", ", matched);
        }
    }
}
