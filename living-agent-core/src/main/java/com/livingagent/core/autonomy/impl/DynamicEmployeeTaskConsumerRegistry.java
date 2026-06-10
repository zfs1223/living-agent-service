package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.EmployeeExecutionReceipt;
import com.livingagent.core.autonomy.EmployeeExecutionReceiptService;
import com.livingagent.core.autonomy.EmployeeTaskExecutionOutcome;
import com.livingagent.core.autonomy.EmployeeTaskExecutor;
import com.livingagent.core.autonomy.EmployeeWorkAssignment;
import com.livingagent.core.autonomy.TaskMetadataKeys;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.channel.ChannelSubscriber;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import com.livingagent.core.model.pool.BrainModelResolver;
import com.livingagent.core.model.pool.LlmModel;
import com.livingagent.core.model.pool.ModelPoolManager;
import com.livingagent.core.model.pool.ResolvedBrainModel;
import com.livingagent.core.provider.impl.ResolvedBrainModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicEmployeeTaskConsumerRegistry {

    private static final Logger log = LoggerFactory.getLogger(DynamicEmployeeTaskConsumerRegistry.class);

    private final ChannelManager channelManager;
    private final FixedEmployeeRegistry fixedEmployeeRegistry;
    private final EmployeeExecutionReceiptService receiptService;
    private final BrainModelResolver brainModelResolver;
    private final ModelPoolManager modelPoolManager;
    private final EmployeeTaskExecutor employeeTaskExecutor;

    private final Map<String, String> consumerIds = new ConcurrentHashMap<>();

    public DynamicEmployeeTaskConsumerRegistry(
            ChannelManager channelManager,
            FixedEmployeeRegistry fixedEmployeeRegistry,
            EmployeeExecutionReceiptService receiptService,
            BrainModelResolver brainModelResolver,
            ModelPoolManager modelPoolManager) {
        this(channelManager, fixedEmployeeRegistry, receiptService, brainModelResolver, modelPoolManager, null);
    }

    public DynamicEmployeeTaskConsumerRegistry(
            ChannelManager channelManager,
            FixedEmployeeRegistry fixedEmployeeRegistry,
            EmployeeExecutionReceiptService receiptService,
            BrainModelResolver brainModelResolver,
            ModelPoolManager modelPoolManager,
            EmployeeTaskExecutor employeeTaskExecutor) {
        this.channelManager = channelManager;
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.receiptService = receiptService;
        this.brainModelResolver = brainModelResolver;
        this.modelPoolManager = modelPoolManager;
        this.employeeTaskExecutor = employeeTaskExecutor;
    }

    public void registerAll() {
        List<FixedEmployeeRegistry.FixedEmployeeDefinition> definitions = fixedEmployeeRegistry.getAllDefinitions();
        for (FixedEmployeeRegistry.FixedEmployeeDefinition def : definitions) {
            registerConsumerFor(def);
        }
        log.info("DynamicEmployeeTaskConsumerRegistry registered {} employee task consumers", consumerIds.size());
    }

    public void registerConsumerFor(FixedEmployeeRegistry.FixedEmployeeDefinition def) {
        String neuronId = def.neuronId();
        if (neuronId == null || neuronId.isBlank()) {
            log.warn("Skip consumer registration for {}: neuronId is empty", def.code());
            return;
        }

        String channelId = "channel://employee/" + sanitize(neuronId) + "/tasks";
        String subscriberId = "employee-consumer-" + def.code();

        if (consumerIds.containsKey(def.code())) {
            log.debug("Consumer already registered for employee {} on channel {}", def.code(), channelId);
            return;
        }

        ChannelSubscriber subscriber = new ChannelSubscriber() {
            @Override
            public void onMessage(ChannelMessage message) {
                handleEmployeeTaskMessage(def, message);
            }

            @Override
            public String getSubscriberId() {
                return subscriberId;
            }
        };

        channelManager.subscribe(channelId, subscriber);
        consumerIds.put(def.code(), subscriberId);
        log.info("Registered task consumer for employee {} ({}) on channel {}", def.code(), def.name(), channelId);
    }

    public void unregisterAll() {
        for (Map.Entry<String, String> entry : consumerIds.entrySet()) {
            String code = entry.getKey();
            FixedEmployeeRegistry.FixedEmployeeDefinition def = fixedEmployeeRegistry.getDefinitionByCode(code).orElse(null);
            if (def != null) {
                String channelId = "channel://employee/" + sanitize(def.neuronId()) + "/tasks";
                channelManager.unsubscribe(channelId, entry.getValue());
                log.info("Unregistered task consumer for employee {} from channel {}", code, channelId);
            }
        }
        consumerIds.clear();
    }

    private void handleEmployeeTaskMessage(
            FixedEmployeeRegistry.FixedEmployeeDefinition def,
            ChannelMessage message) {
        String executionId = message.getMetadata().getOrDefault("execution_id", "unknown").toString();
        String dispatchId = message.getMetadata().getOrDefault("dispatch_id", "unknown").toString();
        String assignmentId = message.getMetadata().getOrDefault("assignment_id", "unknown").toString();
        String taskType = message.getMetadata().getOrDefault("task_type", "unknown").toString();
        String goal = message.getMetadata().getOrDefault("goal", "").toString();
        String executionEnvironment = message.getMetadata().getOrDefault("execution_environment", "ARTIFACT_ONLY").toString();

        log.info("Employee {} ({}) received task: executionId={}, taskType={}, goal={}, env={}",
            def.code(), def.name(), executionId, taskType, goal, executionEnvironment);

        long executionTimeoutMs = 120_000;
        try {
            EmployeeTaskExecutionOutcome outcome;
            
            if (employeeTaskExecutor != null) {
                log.info("Using ToolBackedEmployeeTaskExecutor for employee {}", def.code());
                outcome = executeWithTimeout(() -> executeWithToolExecutor(def, message, taskType, goal, executionEnvironment, executionId),
                    executionTimeoutMs, def.code(), executionId);
                
                if (outcome.status() == EmployeeTaskExecutionOutcome.ExecutionStatus.FAILED) {
                    log.warn("ToolBacked execution failed for employee {}, falling back to LLM-based execution. Reason: {}",
                        def.code(), outcome.failureReason());
                    try {
                        EmployeeTaskExecutionOutcome llmOutcome = executeWithTimeout(
                            () -> executeTaskWithOutcome(def, message),
                            executionTimeoutMs, def.code(), executionId);
                        if (llmOutcome.status() == EmployeeTaskExecutionOutcome.ExecutionStatus.COMPLETED) {
                            log.info("LLM fallback succeeded for employee {} (degraded: no file artifacts)", def.code());
                            outcome = EmployeeTaskExecutionOutcome.degraded(
                                llmOutcome.executionId(), llmOutcome.employeeCode(),
                                "[LLM降级执行] " + llmOutcome.summary(),
                                "ToolBacked执行失败，LLM降级执行无真实文件产物: " + outcome.failureReason());
                        } else {
                            log.warn("LLM fallback also failed for employee {}: {}", def.code(), llmOutcome.failureReason());
                        }
                    } catch (Exception llmEx) {
                        log.error("LLM fallback exception for employee {}: {}", def.code(), llmEx.getMessage());
                    }
                }
            } else {
                log.info("Using LLM-based execution for employee {} (no tool executor available)", def.code());
                outcome = executeWithTimeout(() -> executeTaskWithOutcome(def, message),
                    executionTimeoutMs, def.code(), executionId);
            }
            
            sendOutcomeReceipt(def, executionId, dispatchId, assignmentId, outcome, message);
        } catch (Exception e) {
            log.error("Employee {} ({}) task execution failed: {}", def.code(), def.name(), e.getMessage());
            EmployeeTaskExecutionOutcome failedOutcome = EmployeeTaskExecutionOutcome.failed(
                executionId, def.code(), e.getMessage());
            sendOutcomeReceipt(def, executionId, dispatchId, assignmentId, failedOutcome, message);
        }
    }

    /**
     * 使用真实工具执行器执行任务（阶段7）
     */
    private EmployeeTaskExecutionOutcome executeWithTimeout(
            java.util.function.Supplier<EmployeeTaskExecutionOutcome> task,
            long timeoutMs, String employeeCode, String executionId) {
        try {
            return CompletableFuture.supplyAsync(task)
                .get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Employee {} execution timed out after {}ms: executionId={}", employeeCode, timeoutMs, executionId);
            return EmployeeTaskExecutionOutcome.failed(executionId, employeeCode,
                "执行超时 (" + timeoutMs / 1000 + "秒)");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Employee {} execution failed: {}", employeeCode, cause.getMessage());
            return EmployeeTaskExecutionOutcome.failed(executionId, employeeCode, cause.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Employee {} execution interrupted: executionId={}", employeeCode, executionId);
            return EmployeeTaskExecutionOutcome.failed(executionId, employeeCode, "执行被中断");
        }
    }

    private EmployeeTaskExecutionOutcome executeWithToolExecutor(
            FixedEmployeeRegistry.FixedEmployeeDefinition def,
            ChannelMessage message,
            String taskType,
            String goal,
            String executionEnvironment,
            String executionId) {
        
        String instruction = message.getContent() != null && !message.getContent().isBlank()
            ? message.getContent() : goal;
        
        // 构建任务单
        EmployeeWorkAssignment assignment = new EmployeeWorkAssignment(
            message.getMetadata().getOrDefault("assignment_id", "unknown").toString(),
            def.department(),
            def.code(),
            def.neuronId(),
            def.name(),
            def.title(),
            instruction,
            goal,
            splitMetadataList(message.getMetadata().get("expected_deliverables")),
            def.capabilities() != null ? def.capabilities() : List.of(),  // allowedTools
            Map.of(
                "executionId", executionId,
                TaskMetadataKeys.TASK_TYPE, taskType,
                "department", message.getMetadata().getOrDefault("department", def.department()),
                "batchId", message.getMetadata().getOrDefault("batch_id", ""),
                "acceptanceCriteria", message.getMetadata().getOrDefault("acceptance_criteria", ""),
                "executionEnvironment", executionEnvironment
            )
        );
        
        // 获取员工可用工具
        List<String> availableTools = def.capabilities() != null 
            ? def.capabilities()
            : List.of();
        
        // 调用真实执行器
        EmployeeTaskExecutor.ExecutionResult execResult = employeeTaskExecutor.executeTask(
            def.code(),
            taskType,
            instruction,
            assignment,
            availableTools,
            executionEnvironment
        );
        
        if (execResult.success()) {
            // 构建产物摘要
            String artifactSummary = execResult.artifacts().stream()
                .map(a -> String.format("- %s (%s): %s", a.fileName(), a.fileType(), a.filePath()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("无产物文件");
            
            String summary = "任务执行完成（工具驱动）\n\n" +
                "员工: " + def.name() + " (" + def.code() + ")\n" +
                "任务类型: " + taskType + "\n" +
                "使用工具: " + execResult.usedTools() + "\n" +
                "产物文件:\n" + artifactSummary + "\n" +
                "执行结果: " + execResult.summary();
            
            return EmployeeTaskExecutionOutcome.completed(
                executionId, def.code(), summary,
                execResult.metadata().getOrDefault("model_provider", "tool_executor").toString(),
                execResult.metadata().getOrDefault("model_name", "tool_executor").toString()
            );
        } else {
            return EmployeeTaskExecutionOutcome.failed(
                executionId, def.code(), 
                "工具执行失败: " + execResult.errorMessage()
            );
        }
    }

    private EmployeeTaskExecutionOutcome executeTaskWithOutcome(
            FixedEmployeeRegistry.FixedEmployeeDefinition def, ChannelMessage message) {
        String executionId = message.getMetadata().getOrDefault("execution_id", "unknown").toString();
        String taskType = message.getMetadata().getOrDefault("task_type", "general").toString();
        String goal = message.getMetadata().getOrDefault("goal", "").toString();
        String instruction = message.getContent() != null && !message.getContent().isBlank()
            ? message.getContent() : goal;

        String systemPrompt = buildSystemPrompt(def, taskType, goal);
        String userMessage = instruction;

        return executeWithModelPoolAndOutcome(def, executionId, taskType, goal, systemPrompt, userMessage);
    }

    private EmployeeTaskExecutionOutcome executeWithModelPoolAndOutcome(
            FixedEmployeeRegistry.FixedEmployeeDefinition def,
            String executionId,
            String taskType, String goal,
            String systemPrompt, String userMessage) {

        ResolvedBrainModel assignedModel = brainModelResolver.resolve(def.neuronId());
        if (assignedModel != null) {
            try {
                String result = callLLM(assignedModel, systemPrompt, userMessage);
                if (result != null && !result.isBlank()) {
                    log.info("Employee {} executed task via assigned model: provider={}, model={}",
                        def.code(), assignedModel.getProviderId(), assignedModel.getModelName());
                    return EmployeeTaskExecutionOutcome.completed(
                        executionId, def.code(), result,
                        assignedModel.getProviderId(), assignedModel.getModelName());
                }
            } catch (Exception e) {
                log.warn("Employee {} assigned model call failed (provider={}, model={}): {}",
                    def.code(), assignedModel.getProviderId(), assignedModel.getModelName(), e.getMessage());
            }
        } else {
            log.warn("Employee {} has no assigned model resolved", def.code());
        }

        List<LlmModel> fallbackModels = modelPoolManager.getAllModels().stream()
            .filter(LlmModel::isEnabled)
            .filter(LlmModel::isRecommended)
            .toList();

        if (fallbackModels.isEmpty()) {
            fallbackModels = modelPoolManager.getAllModels().stream()
                .filter(LlmModel::isEnabled)
                .toList();
        }

        log.info("Employee {} attempting fallback models (count={})", def.code(), fallbackModels.size());

        for (LlmModel model : fallbackModels) {
            log.debug("Employee {} trying fallback model: provider={}, model={}",
                def.code(), model.getProviderId(), model.getModelName());
            try {
                ResolvedBrainModel fallbackResolved = brainModelResolver.resolveRaw(
                    model.getProviderId(), model.getModelName());
                if (fallbackResolved == null) {
                    log.debug("Employee {} could not resolve fallback model: provider={}, model={}",
                        def.code(), model.getProviderId(), model.getModelName());
                    continue;
                }

                String result = callLLM(fallbackResolved, systemPrompt, userMessage);
                if (result != null && !result.isBlank()) {
                    log.info("Employee {} executed task via fallback model: provider={}, model={}",
                        def.code(), fallbackResolved.getProviderId(), fallbackResolved.getModelName());
                    return EmployeeTaskExecutionOutcome.completed(
                        executionId, def.code(), result,
                        fallbackResolved.getProviderId(), fallbackResolved.getModelName());
                }
            } catch (Exception e) {
                log.warn("Employee {} fallback model {} failed: {}",
                    def.code(), model.getModelName(), e.getMessage());
            }
        }

        log.warn("Employee {} all LLM attempts failed, returning DEGRADED outcome", def.code());
        
        String degradedSummary = "任务执行降级（" + def.name() + "/" + def.departmentName()
            + "）：类型=" + taskType + ", 目标=" + goal + "\n"
            + "【重要】模型调用暂不可用，此任务尚未真实完成，需要等待模型恢复后重新处理或人工介入。";
        
        return EmployeeTaskExecutionOutcome.degraded(
            executionId, def.code(), degradedSummary, "所有模型调用失败");
    }

    private String callLLM(ResolvedBrainModel resolvedModel, String systemPrompt, String userMessage) {
        try {
            ResolvedBrainModelProvider provider = new ResolvedBrainModelProvider(resolvedModel);
            String result = provider.chatWithSystem(systemPrompt, userMessage, null, 0.7)
                .get(310, java.util.concurrent.TimeUnit.SECONDS);
            if (result != null && !result.isBlank()) {
                return result;
            }
            log.warn("callLLM: model {} returned empty result", resolvedModel.getModelName());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("callLLM interrupted for model {}", resolvedModel.getModelName());
            return null;
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("callLLM timeout for model {} after 310s", resolvedModel.getModelName());
            return null;
        } catch (Exception e) {
            log.warn("callLLM failed for model {}: {}", resolvedModel.getModelName(), e.getMessage());
            return null;
        }
    }

    private String buildSystemPrompt(FixedEmployeeRegistry.FixedEmployeeDefinition def,
                                      String taskType, String goal) {
        return """
            你是 %s（%s），隶属于 %s。
            你的职责领域：%s
            你的核心能力：%s

            现在收到一项任务：
            - 任务类型：%s
            - 任务目标：%s

            请以专业身份完成此任务，输出具体、可执行的成果。
            如果产出包含文件路径、配置参数等关键信息请明确标注。
            回复使用中文。
            """.formatted(
                def.name(),
                def.title(),
                def.departmentName(),
                String.join("、", def.roles()),
                String.join("、", def.capabilities()),
                taskType,
                goal
        );
    }

    private void sendOutcomeReceipt(
            FixedEmployeeRegistry.FixedEmployeeDefinition def,
            String executionId, String dispatchId, String assignmentId,
            EmployeeTaskExecutionOutcome outcome, ChannelMessage message) {

        String receiptChannel = message.getMetadata().getOrDefault("receipt_channel",
            "channel://department/" + def.department() + "/execution-receipts").toString();

        EmployeeExecutionReceipt receipt = outcome.toReceipt(dispatchId, assignmentId, def.neuronId());
        receiptService.recordReceipt(receipt);

        ChannelMessage receiptMessage = ChannelMessage.text(
            "channel://employee/" + sanitize(def.neuronId()),
            "autonomy://employee-task-consumer",
            receiptChannel,
            message.getMetadata().getOrDefault("session_id", "unknown").toString(),
            outcome.summary() != null ? outcome.summary() : ""
        );
        receiptMessage.addMetadata("receipt_id", receipt.receiptId());
        receiptMessage.addMetadata("execution_id", executionId);
        receiptMessage.addMetadata("dispatch_id", dispatchId);
        receiptMessage.addMetadata("assignment_id", assignmentId);
        receiptMessage.addMetadata("batch_id", message.getMetadata().getOrDefault("batch_id", ""));
        receiptMessage.addMetadata("employee_code", def.code());
        receiptMessage.addMetadata("employee_name", def.name());
        receiptMessage.addMetadata("status", receipt.status() != null ? receipt.status().getCode() : "");
        receiptMessage.addMetadata("outcome_status", outcome.status().name());
        receiptMessage.addMetadata("needs_retry", String.valueOf(outcome.needsRetry()));
        receiptMessage.addMetadata("needs_human_review", String.valueOf(outcome.needsHumanReview()));
        receiptMessage.addMetadata("confidence", String.valueOf(outcome.confidence()));
        if (outcome.modelProvider() != null) {
            receiptMessage.addMetadata("model_provider", outcome.modelProvider());
        }
        if (outcome.modelName() != null) {
            receiptMessage.addMetadata("model_name", outcome.modelName());
        }
        if (outcome.failureReason() != null) {
            receiptMessage.addMetadata("failure_reason", outcome.failureReason());
        }
        channelManager.publish(receiptChannel, receiptMessage);

        log.info("Employee {} ({}) sent receipt: executionId={}, status={}, outcomeStatus={}",
            def.code(), def.name(), executionId, receipt.status() != null ? receipt.status().getCode() : "null", outcome.status());
    }

    private List<String> splitMetadataList(Object value) {
        if (value == null) {
            return List.of();
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(text.split(","))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .toList();
    }

    private String sanitize(String value) {
        return value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
