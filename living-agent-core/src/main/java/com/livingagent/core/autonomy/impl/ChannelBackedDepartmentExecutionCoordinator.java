package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.DepartmentExecutionCoordinator;
import com.livingagent.core.autonomy.DepartmentExecutionResult;
import com.livingagent.core.autonomy.EmployeeExecutionDispatch;
import com.livingagent.core.autonomy.EmployeeWorkAssignment;
import com.livingagent.core.autonomy.PreparedAssignmentBatch;
import com.livingagent.core.autonomy.TaskMetadataKeys;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChannelBackedDepartmentExecutionCoordinator implements DepartmentExecutionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ChannelBackedDepartmentExecutionCoordinator.class);

    private static final String STATUS_DISPATCHED = "DISPATCHED";
    private static final String STATUS_WAITING_RECEIPT = "WAITING_RECEIPT";

    private final ChannelManager channelManager;
    private final com.livingagent.core.autonomy.EmployeeExecutionReceiptService receiptService;

    public ChannelBackedDepartmentExecutionCoordinator(ChannelManager channelManager) {
        this(channelManager, null);
    }

    public ChannelBackedDepartmentExecutionCoordinator(
            ChannelManager channelManager,
            com.livingagent.core.autonomy.EmployeeExecutionReceiptService receiptService) {
        this.channelManager = channelManager;
        this.receiptService = receiptService;
    }

    @Override
    public DepartmentExecutionResult coordinate(PreparedAssignmentBatch preparedAssignmentBatch) {
        if (preparedAssignmentBatch == null || preparedAssignmentBatch.assignments().isEmpty()) {
            return new DepartmentExecutionResult(
                UUID.randomUUID().toString(),
                preparedAssignmentBatch != null ? preparedAssignmentBatch.batchId() : null,
                preparedAssignmentBatch != null ? preparedAssignmentBatch.department() : null,
                null,
                "NO_ASSIGNMENT",
                List.of(),
                Map.of("reason", "prepared assignment batch is empty")
            );
        }

        String executionId = UUID.randomUUID().toString();
        String receiptChannel = receiptChannelFor(preparedAssignmentBatch);
        channelManager.getOrCreateChannel(receiptChannel);
        List<EmployeeExecutionDispatch> dispatches = new ArrayList<>();
        List<ChannelMessage> messages = new ArrayList<>();

        for (EmployeeWorkAssignment assignment : preparedAssignmentBatch.assignments()) {
            String targetChannel = targetChannelFor(assignment);
            String dispatchId = UUID.randomUUID().toString();

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("role", assignment.role());
            metadata.put("employeeName", assignment.employeeName());
            metadata.put(TaskMetadataKeys.TASK_TYPE, preparedAssignmentBatch.taskType());
            metadata.put("goal", preparedAssignmentBatch.goal());
            dispatches.add(new EmployeeExecutionDispatch(
                dispatchId,
                assignment.assignmentId(),
                assignment.employeeCode(),
                assignment.employeeNeuronId(),
                targetChannel,
                STATUS_DISPATCHED,
                Instant.now(),
                metadata
            ));

            ChannelMessage message = ChannelMessage.text(
                "channel://department/" + preparedAssignmentBatch.department() + "/execution",
                "autonomy://department-execution-coordinator",
                targetChannel,
                preparedAssignmentBatch.sessionId(),
                assignment.instruction()
            );
            message.addMetadata("execution_id", executionId);
            message.addMetadata("batch_id", preparedAssignmentBatch.batchId());
            message.addMetadata("request_id", preparedAssignmentBatch.requestId());
            message.addMetadata("dispatch_id", dispatchId);
            message.addMetadata("assignment_id", assignment.assignmentId());
            message.addMetadata("department", assignment.department());
            message.addMetadata("employee_code", assignment.employeeCode());
            message.addMetadata("employee_name", assignment.employeeName());
            message.addMetadata("employee_neuron_id", assignment.employeeNeuronId());
            message.addMetadata("role", assignment.role());
            message.addMetadata("task_type", preparedAssignmentBatch.taskType());
            message.addMetadata("goal", preparedAssignmentBatch.goal());
            message.addMetadata("expected_deliverables", String.join(",", assignment.expectedDeliverables()));
            message.addMetadata("allowed_tools", String.join(",", assignment.allowedTools()));
            message.addMetadata("receipt_channel", receiptChannel);
            messages.add(message);
        }

        Map<String, Object> resultMetadata = new LinkedHashMap<>();
        resultMetadata.put("assignmentCount", String.valueOf(preparedAssignmentBatch.assignments().size()));
        resultMetadata.put("dispatchedCount", String.valueOf(dispatches.size()));
        resultMetadata.put(TaskMetadataKeys.TASK_TYPE, preparedAssignmentBatch.taskType());
        resultMetadata.put("goal", preparedAssignmentBatch.goal());
        resultMetadata.put("receiptChannel", receiptChannel);
        resultMetadata.put("sessionId", preparedAssignmentBatch.sessionId());
        resultMetadata.put("requestId", preparedAssignmentBatch.requestId());

        DepartmentExecutionResult result = new DepartmentExecutionResult(
            executionId,
            preparedAssignmentBatch.batchId(),
            preparedAssignmentBatch.department(),
            preparedAssignmentBatch.sessionId(),
            STATUS_WAITING_RECEIPT,
            dispatches,
            resultMetadata
        );

        if (receiptService != null) {
            receiptService.registerExecution(result);
        }

        for (ChannelMessage message : messages) {
            String targetChannelId = message.getTargetChannelId();
            if (!channelManager.exists(targetChannelId)) {
                channelManager.getOrCreateChannel(targetChannelId);
                log.info("Auto-created channel before publish: {}", targetChannelId);
            }
            channelManager.publish(targetChannelId, message);
        }

        return result;
    }

    private String targetChannelFor(EmployeeWorkAssignment assignment) {
        String neuronId = assignment.employeeNeuronId();
        if (neuronId != null && !neuronId.isBlank()) {
            return "channel://employee/" + sanitize(neuronId) + "/tasks";
        }
        return "channel://employee/" + sanitize(assignment.employeeCode()) + "/tasks";
    }

    private String receiptChannelFor(PreparedAssignmentBatch preparedAssignmentBatch) {
        return "channel://department/" + sanitize(preparedAssignmentBatch.department()) + "/execution-receipts";
    }

    private String sanitize(String value) {
        return value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
