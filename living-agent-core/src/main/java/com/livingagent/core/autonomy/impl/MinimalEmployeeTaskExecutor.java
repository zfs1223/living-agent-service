package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.EmployeeExecutionReceipt;
import com.livingagent.core.autonomy.EmployeeExecutionReceiptService;
import com.livingagent.core.autonomy.ReceiptStatus;
import com.livingagent.core.autonomy.TaskMetadataKeys;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.channel.ChannelSubscriber;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class MinimalEmployeeTaskExecutor implements ChannelSubscriber {

    private static final Logger log = LoggerFactory.getLogger(MinimalEmployeeTaskExecutor.class);

    private final ChannelManager channelManager;
    private final EmployeeExecutionReceiptService receiptService;
    private final String subscriberId;
    private final String taskChannel;

    public MinimalEmployeeTaskExecutor(
            String employeeKey,
            ChannelManager channelManager,
            EmployeeExecutionReceiptService receiptService) {
        String safeKey = sanitize(employeeKey);
        this.channelManager = channelManager;
        this.receiptService = receiptService;
        this.subscriberId = "minimal-employee-executor-" + safeKey;
        this.taskChannel = "channel://employee/" + safeKey + "/tasks";
    }

    @PostConstruct
    public void start() {
        channelManager.subscribe(taskChannel, this);
        log.info("MinimalEmployeeTaskExecutor subscribed: {}", taskChannel);
    }

    @Override
    public void onMessage(ChannelMessage message) {
        String receiptChannel = String.valueOf(message.getMetadata().getOrDefault("receipt_channel", ""));
        String executionId = String.valueOf(message.getMetadata().getOrDefault("execution_id", ""));
        String dispatchId = String.valueOf(message.getMetadata().getOrDefault("dispatch_id", ""));
        String assignmentId = String.valueOf(message.getMetadata().getOrDefault("assignment_id", ""));
        String employeeCode = String.valueOf(message.getMetadata().getOrDefault("employee_code", "unknown"));
        String employeeNeuronId = String.valueOf(message.getMetadata().getOrDefault("employee_neuron_id", "unknown"));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceTaskChannel", taskChannel);
        metadata.put("requestId", String.valueOf(message.getMetadata().getOrDefault("request_id", "")));
        metadata.put(TaskMetadataKeys.TASK_TYPE, String.valueOf(message.getMetadata().getOrDefault("task_type", "")));
        metadata.put("goal", String.valueOf(message.getMetadata().getOrDefault("goal", "")));
        metadata.put("contentLength", String.valueOf(message.getContent() != null ? message.getContent().length() : 0));

        EmployeeExecutionReceipt receipt = new EmployeeExecutionReceipt(
            UUID.randomUUID().toString(),
            executionId,
            dispatchId,
            assignmentId,
            employeeCode,
            employeeNeuronId,
            ReceiptStatus.COMPLETED,
            buildSummary(message),
            Instant.now(),
            metadata
        );
        receiptService.recordReceipt(receipt);

        if (receiptChannel != null && !receiptChannel.isBlank()) {
            ChannelMessage receiptMessage = ChannelMessage.text(
                taskChannel,
                subscriberId,
                receiptChannel,
                message.getSessionId(),
                receipt.summary()
            );
            receiptMessage.addMetadata("receipt_id", receipt.receiptId());
            receiptMessage.addMetadata("execution_id", receipt.executionId());
            receiptMessage.addMetadata("dispatch_id", receipt.dispatchId());
            receiptMessage.addMetadata("assignment_id", receipt.assignmentId());
            receiptMessage.addMetadata("employee_code", receipt.employeeCode());
            receiptMessage.addMetadata("employee_neuron_id", receipt.employeeNeuronId());
            receiptMessage.addMetadata("status", receipt.status() != null ? receipt.status().getCode() : "");
            channelManager.publish(receiptChannel, receiptMessage);
        }
    }

    @Override
    public String getSubscriberId() {
        return subscriberId;
    }

    private String buildSummary(ChannelMessage message) {
        String content = message.getContent();
        if (content == null || content.isBlank()) {
            return "Task consumed and receipt published.";
        }
        return content.length() > 120
            ? content.substring(0, 120) + "..."
            : content;
    }

    private static String sanitize(String value) {
        return value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
