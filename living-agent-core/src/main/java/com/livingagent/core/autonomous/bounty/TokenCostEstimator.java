package com.livingagent.core.autonomous.bounty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenCostEstimator {

    private static final Logger log = LoggerFactory.getLogger(TokenCostEstimator.class);

    @Value("${cost.electricity-rate:1.0}")
    private double electricityRatePerKwh;

    @Value("${cost.local-gpu-power-watts:300}")
    private int localGpuPowerWatts;

    @Value("${cost.currency:USD}")
    private String defaultCurrency;

    private final Map<String, ProjectAccounting> projectAccounts = new ConcurrentHashMap<>();

    private static final Map<String, ModelPricing> CLOUD_PRICING = new ConcurrentHashMap<>();
    
    static {
        CLOUD_PRICING.put("gpt-4o", new ModelPricing(2.50, 10.00, "OpenAI"));
        CLOUD_PRICING.put("gpt-4.1", new ModelPricing(2.00, 8.00, "OpenAI"));
        CLOUD_PRICING.put("gpt-4-turbo", new ModelPricing(10.00, 30.00, "OpenAI"));
        CLOUD_PRICING.put("gpt-3.5-turbo", new ModelPricing(0.50, 1.50, "OpenAI"));
        
        CLOUD_PRICING.put("claude-3.5-sonnet", new ModelPricing(3.00, 15.00, "Anthropic"));
        CLOUD_PRICING.put("claude-3-opus", new ModelPricing(15.00, 75.00, "Anthropic"));
        CLOUD_PRICING.put("claude-3-haiku", new ModelPricing(0.25, 1.25, "Anthropic"));
        
        CLOUD_PRICING.put("deepseek-chat", new ModelPricing(0.14, 0.28, "DeepSeek"));
        CLOUD_PRICING.put("deepseek-reasoner", new ModelPricing(0.55, 2.19, "DeepSeek"));
        
        CLOUD_PRICING.put("qwen-turbo", new ModelPricing(0.30, 0.60, "Alibaba"));
        CLOUD_PRICING.put("qwen-plus", new ModelPricing(0.80, 2.00, "Alibaba"));
        CLOUD_PRICING.put("qwen-max", new ModelPricing(2.00, 6.00, "Alibaba"));
        
        CLOUD_PRICING.put("gemini-1.5-pro", new ModelPricing(1.25, 5.00, "Google"));
        CLOUD_PRICING.put("gemini-1.5-flash", new ModelPricing(0.075, 0.30, "Google"));
    }

    private static final Map<String, LocalModelConfig> LOCAL_MODELS = new ConcurrentHashMap<>();
    
    static {
        LOCAL_MODELS.put("qwen3.5:9b", new LocalModelConfig(9.7, 24, 300, 50));
        LOCAL_MODELS.put("qwen3.5:27b", new LocalModelConfig(27, 48, 450, 30));
        LOCAL_MODELS.put("qwen2.5:3b", new LocalModelConfig(3.1, 8, 150, 80));
        LOCAL_MODELS.put("qwen2.5:7b", new LocalModelConfig(7.6, 16, 200, 60));
        LOCAL_MODELS.put("deepseek-r1:7b", new LocalModelConfig(7.6, 16, 200, 55));
        LOCAL_MODELS.put("deepseek-r1:14b", new LocalModelConfig(14.8, 32, 350, 35));
        LOCAL_MODELS.put("llama3.2:3b", new LocalModelConfig(3.2, 8, 150, 75));
        LOCAL_MODELS.put("codellama:7b", new LocalModelConfig(7, 16, 200, 65));
    }

    public CostEstimate estimateCloudCost(String modelName, int inputTokens, int outputTokens) {
        ModelPricing pricing = CLOUD_PRICING.get(modelName);
        if (pricing == null) {
            pricing = new ModelPricing(1.0, 3.0, "Unknown");
        }
        
        double inputCost = (inputTokens / 1_000_000.0) * pricing.inputPricePerMillion;
        double outputCost = (outputTokens / 1_000_000.0) * pricing.outputPricePerMillion;
        double totalCost = inputCost + outputCost;
        
        return new CostEstimate(
            totalCost,
            inputTokens,
            outputTokens,
            modelName,
            pricing.provider,
            "cloud",
            defaultCurrency
        );
    }

    public CostEstimate estimateLocalCost(String modelName, int inputTokens, int outputTokens, 
                                          double executionTimeSeconds) {
        LocalModelConfig config = LOCAL_MODELS.get(modelName);
        if (config == null) {
            config = new LocalModelConfig(7, 16, localGpuPowerWatts, 50);
        }
        
        double powerKw = config.avgPowerWatts / 1000.0;
        double energyKwh = powerKw * (executionTimeSeconds / 3600.0);
        double electricityCost = energyKwh * electricityRatePerKwh;
        
        double timeCost = calculateTimeCost(executionTimeSeconds);
        
        double totalCost = electricityCost + timeCost;
        
        return new CostEstimate(
            totalCost,
            inputTokens,
            outputTokens,
            modelName,
            "Local",
            "local",
            defaultCurrency,
            electricityCost,
            timeCost,
            energyKwh,
            executionTimeSeconds
        );
    }

    public TaskCostEstimate estimateTaskCost(TaskProfile task) {
        int estimatedInputTokens = estimateInputTokens(task);
        int estimatedOutputTokens = estimateOutputTokens(task);
        double estimatedTimeSeconds = estimateExecutionTime(task, estimatedInputTokens, estimatedOutputTokens);
        
        double cloudCost = estimateCloudCost(task.preferredModel(), 
            estimatedInputTokens, estimatedOutputTokens).totalCost();
        
        double localCost = estimateLocalCost(task.preferredModel(),
            estimatedInputTokens, estimatedOutputTokens, estimatedTimeSeconds).totalCost();
        
        double complexityMultiplier = getComplexityMultiplier(task.complexity());
        double riskMultiplier = getRiskMultiplier(task.riskLevel());
        
        double adjustedCloudCost = cloudCost * complexityMultiplier * riskMultiplier;
        double adjustedLocalCost = localCost * complexityMultiplier * riskMultiplier;
        
        return new TaskCostEstimate(
            task.taskId(),
            adjustedCloudCost,
            adjustedLocalCost,
            estimatedInputTokens,
            estimatedOutputTokens,
            estimatedTimeSeconds,
            task.complexity(),
            task.riskLevel()
        );
    }

    public FrontendTaskCost estimateFrontendTaskCost(FrontendTaskProfile profile) {
        double baseCostPerUnit = getBaseCostPerUnit(profile.taskType());
        int estimatedUnits = profile.estimatedUnits();
        double qualityFactor = profile.qualityRequirement() / 100.0;
        double urgencyFactor = getUrgencyFactor(profile.urgency());
        
        double estimatedCost = baseCostPerUnit * estimatedUnits * qualityFactor * urgencyFactor;
        
        double completionBonus = calculateCompletionBonus(profile);
        double qualityBonus = calculateQualityBonus(profile);
        
        return new FrontendTaskCost(
            profile.taskId(),
            estimatedCost,
            completionBonus,
            qualityBonus,
            estimatedUnits,
            profile.taskType()
        );
    }

    private int estimateInputTokens(TaskProfile task) {
        int baseTokens = 500;
        
        if (task.description() != null) {
            baseTokens += task.description().length() / 4;
        }
        
        if (task.context() != null) {
            baseTokens += task.context().size() * 100;
        }
        
        return baseTokens * task.complexity();
    }

    private int estimateOutputTokens(TaskProfile task) {
        int baseTokens = 200;
        
        return switch (task.taskType().toUpperCase()) {
            case "CODE_GENERATION", "BUG_FIX" -> baseTokens * task.complexity() * 5;
            case "CODE_REVIEW" -> baseTokens * task.complexity() * 3;
            case "DATA_ANALYSIS" -> baseTokens * task.complexity() * 4;
            case "DOCUMENTATION" -> baseTokens * task.complexity() * 6;
            case "TRANSLATION" -> baseTokens * task.complexity() * 2;
            case "FEATURE" -> baseTokens * task.complexity() * 5;
            case "REFACTORING" -> baseTokens * task.complexity() * 4;
            default -> baseTokens * task.complexity();
        };
    }

    private double estimateExecutionTime(TaskProfile task, int inputTokens, int outputTokens) {
        LocalModelConfig config = LOCAL_MODELS.getOrDefault(task.preferredModel(),
            new LocalModelConfig(7, 16, localGpuPowerWatts, 50));
        
        double inputTime = inputTokens / (double) config.tokensPerSecond;
        double outputTime = outputTokens / (double) config.tokensPerSecond;
        
        return inputTime + outputTime + task.complexity() * 2;
    }

    private double calculateTimeCost(double seconds) {
        double hourlyRate = 0.50;
        return (seconds / 3600.0) * hourlyRate;
    }

    private double getComplexityMultiplier(int complexity) {
        if (complexity <= 3) return 1.0;
        if (complexity <= 5) return 1.2;
        if (complexity <= 7) return 1.5;
        if (complexity <= 9) return 2.0;
        return 3.0;
    }

    private double getRiskMultiplier(String riskLevel) {
        return switch (riskLevel.toLowerCase()) {
            case "low" -> 1.0;
            case "medium" -> 1.3;
            case "high" -> 1.8;
            case "critical" -> 2.5;
            default -> 1.5;
        };
    }

    private double getBaseCostPerUnit(String taskType) {
        return switch (taskType.toLowerCase()) {
            case "code_generation" -> 0.02;
            case "code_review" -> 0.01;
            case "data_analysis" -> 0.015;
            case "documentation" -> 0.008;
            case "translation" -> 0.005;
            case "summarization" -> 0.003;
            case "chat" -> 0.001;
            default -> 0.01;
        };
    }

    private double getUrgencyFactor(String urgency) {
        return switch (urgency.toLowerCase()) {
            case "low" -> 1.0;
            case "normal" -> 1.2;
            case "high" -> 1.5;
            case "urgent" -> 2.0;
            default -> 1.2;
        };
    }

    private double calculateCompletionBonus(FrontendTaskProfile profile) {
        double baseBonus = getBaseCostPerUnit(profile.taskType()) * profile.estimatedUnits() * 0.1;
        
        if (profile.historicalCompletionRate() > 0.9) {
            return baseBonus * 1.5;
        } else if (profile.historicalCompletionRate() > 0.7) {
            return baseBonus;
        }
        return baseBonus * 0.5;
    }

    private double calculateQualityBonus(FrontendTaskProfile profile) {
        if (profile.qualityRequirement() >= 90) {
            return getBaseCostPerUnit(profile.taskType()) * profile.estimatedUnits() * 0.2;
        }
        return 0;
    }

    public record ModelPricing(
        double inputPricePerMillion,
        double outputPricePerMillion,
        String provider
    ) {}

    public record LocalModelConfig(
        double parametersBillions,
        int vramGB,
        int avgPowerWatts,
        int tokensPerSecond
    ) {}

    public record CostEstimate(
        double totalCost,
        int inputTokens,
        int outputTokens,
        String modelName,
        String provider,
        String deploymentType,
        String currency,
        double electricityCost,
        double timeCost,
        double energyKwh,
        double executionTimeSeconds
    ) {
        public CostEstimate(double totalCost, int inputTokens, int outputTokens,
                           String modelName, String provider, String deploymentType, String currency) {
            this(totalCost, inputTokens, outputTokens, modelName, provider, deploymentType, currency, 0, 0, 0, 0);
        }
    }

    public record TaskProfile(
        String taskId,
        String taskType,
        String description,
        Map<String, Object> context,
        int complexity,
        String riskLevel,
        String preferredModel
    ) {}

    public record TaskCostEstimate(
        String taskId,
        double cloudCost,
        double localCost,
        int estimatedInputTokens,
        int estimatedOutputTokens,
        double estimatedTimeSeconds,
        int complexity,
        String riskLevel
    ) {
        public double recommendedCost() {
            return Math.min(cloudCost, localCost);
        }
        
        public String recommendedDeployment() {
            return cloudCost <= localCost ? "cloud" : "local";
        }
    }

    public record FrontendTaskProfile(
        String taskId,
        String taskType,
        int estimatedUnits,
        int qualityRequirement,
        String urgency,
        double historicalCompletionRate
    ) {}

    public record FrontendTaskCost(
        String taskId,
        double estimatedCost,
        double completionBonus,
        double qualityBonus,
        int estimatedUnits,
        String taskType
    ) {
        public double totalPotentialEarning() {
            return estimatedCost + completionBonus + qualityBonus;
        }
    }

    public record ProjectAccounting(
        String projectId,
        String projectName,
        Instant createdAt,
        double totalIncome,
        double totalCost,
        double netProfit,
        Map<String, TaskCostRecord> taskRecords,
        Map<String, Double> modelCosts,
        Map<String, Integer> taskTypeCounts
    ) {
        public double profitMargin() {
            return totalIncome > 0 ? netProfit / totalIncome : 0;
        }
        
        public boolean isProfitable() {
            return netProfit > 0;
        }
    }

    public record TaskCostRecord(
        String taskId,
        String taskType,
        Instant executedAt,
        double cost,
        double income,
        int inputTokens,
        int outputTokens,
        String modelUsed,
        String deploymentType,
        double executionTimeSeconds,
        boolean success
    ) {
        public double netValue() {
            return income - cost;
        }
    }

    public ProjectAccounting createProjectAccount(String projectId, String projectName) {
        ProjectAccounting account = new ProjectAccounting(
            projectId,
            projectName,
            Instant.now(),
            0,
            0,
            0,
            new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>()
        );
        projectAccounts.put(projectId, account);
        log.info("Created project accounting: {} ({})", projectId, projectName);
        return account;
    }

    public void recordTaskExecution(String projectId, TaskCostRecord record) {
        ProjectAccounting account = projectAccounts.get(projectId);
        if (account == null) {
            log.warn("Project account not found: {}, creating default", projectId);
            account = createProjectAccount(projectId, "Auto-created Project");
        }
        
        Map<String, TaskCostRecord> records = new ConcurrentHashMap<>(account.taskRecords());
        records.put(record.taskId(), record);
        
        Map<String, Double> modelCosts = new ConcurrentHashMap<>(account.modelCosts());
        modelCosts.merge(record.modelUsed(), record.cost(), Double::sum);
        
        Map<String, Integer> taskTypeCounts = new ConcurrentHashMap<>(account.taskTypeCounts());
        taskTypeCounts.merge(record.taskType(), 1, Integer::sum);
        
        double newTotalCost = account.totalCost() + record.cost();
        double newTotalIncome = account.totalIncome() + record.income();
        
        ProjectAccounting updated = new ProjectAccounting(
            account.projectId(),
            account.projectName(),
            account.createdAt(),
            newTotalIncome,
            newTotalCost,
            newTotalIncome - newTotalCost,
            records,
            modelCosts,
            taskTypeCounts
        );
        
        projectAccounts.put(projectId, updated);
        log.info("Recorded task {} for project {}: cost={}, income={}, net={}", 
            record.taskId(), projectId, record.cost(), record.income(), record.netValue());
    }

    public ProjectAccounting getProjectAccounting(String projectId) {
        return projectAccounts.get(projectId);
    }

    public Map<String, ProjectAccounting> getAllProjectAccounting() {
        return new HashMap<>(projectAccounts);
    }

    public ProjectSummary getProjectSummary(String projectId) {
        ProjectAccounting account = projectAccounts.get(projectId);
        if (account == null) {
            return null;
        }
        
        String mostUsedModel = account.modelCosts().entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("none");
        
        String mostCommonTaskType = account.taskTypeCounts().entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("none");
        
        double avgTaskCost = account.taskRecords().isEmpty() ? 0 :
            account.totalCost() / account.taskRecords().size();
        
        double successRate = account.taskRecords().isEmpty() ? 0 :
            account.taskRecords().values().stream()
                .filter(TaskCostRecord::success)
                .count() / (double) account.taskRecords().size();
        
        return new ProjectSummary(
            projectId,
            account.projectName(),
            account.totalIncome(),
            account.totalCost(),
            account.netProfit(),
            account.profitMargin(),
            account.taskRecords().size(),
            mostUsedModel,
            mostCommonTaskType,
            avgTaskCost,
            successRate
        );
    }

    public record ProjectSummary(
        String projectId,
        String projectName,
        double totalIncome,
        double totalCost,
        double netProfit,
        double profitMargin,
        int totalTasks,
        String mostUsedModel,
        String mostCommonTaskType,
        double avgTaskCost,
        double successRate
    ) {
        public String profitabilityStatus() {
            if (profitMargin > 0.5) return "HIGHLY_PROFITABLE";
            if (profitMargin > 0.2) return "PROFITABLE";
            if (profitMargin > 0) return "MARGINALLY_PROFITABLE";
            if (profitMargin > -0.2) return "MARGINALLY_UNPROFITABLE";
            return "UNPROFITABLE";
        }
    }
}
