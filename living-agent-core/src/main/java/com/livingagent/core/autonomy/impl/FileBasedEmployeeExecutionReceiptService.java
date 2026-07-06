package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.livingagent.core.autonomy.DepartmentExecutionResult;
import com.livingagent.core.autonomy.EmployeeExecutionDispatch;
import com.livingagent.core.autonomy.EmployeeExecutionReceipt;
import com.livingagent.core.autonomy.EmployeeExecutionReceiptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class FileBasedEmployeeExecutionReceiptService implements EmployeeExecutionReceiptService {

    private static final Logger log = LoggerFactory.getLogger(FileBasedEmployeeExecutionReceiptService.class);

    private static final String DATA_DIR = "data/receipts";
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final Map<String, List<EmployeeExecutionReceipt>> receiptsByExecution = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> expectedDispatchIdsByExecution = new ConcurrentHashMap<>();
    private final Map<String, DepartmentExecutionResult> executionResults = new ConcurrentHashMap<>();
    private final List<EmployeeExecutionReceiptService.ReceiptListener> listeners = new CopyOnWriteArrayList<>();
    private final Path dataDir;

    public FileBasedEmployeeExecutionReceiptService() {
        this.dataDir = Paths.get(DATA_DIR).toAbsolutePath();
        initDataDir();
        loadAllFromDisk();
    }

    private void initDataDir() {
        try {
            Files.createDirectories(dataDir);
            log.info("Receipt data directory initialized: {}", dataDir);
        } catch (IOException e) {
            log.error("Failed to create receipts data directory {}: {}", dataDir, e.getMessage());
        }
    }

    private void loadAllFromDisk() {
        int loaded = 0;
        try {
            if (!Files.exists(dataDir)) {
                return;
            }
            List<Path> files = Files.list(dataDir)
                    .filter(f -> f.toString().endsWith(".json"))
                    .toList();
            for (Path file : files) {
                try {
                    if (file.getFileName().toString().endsWith("-meta.json")) {
                        DepartmentExecutionResult result = objectMapper.readValue(
                            file.toFile(), DepartmentExecutionResult.class);
                        if (result != null && result.executionId() != null) {
                            executionResults.put(result.executionId(), result);
                        }
                        continue;
                    }
                    List<EmployeeExecutionReceipt> receipts = objectMapper.readValue(
                            file.toFile(),
                            new TypeReference<List<EmployeeExecutionReceipt>>() {});
                    if (!receipts.isEmpty()) {
                        String executionId = receipts.get(0).executionId();
                        receiptsByExecution.put(executionId, new ArrayList<>(receipts));
                        loaded += receipts.size();
                    }
                } catch (IOException e) {
                    log.warn("Failed to load receipt file {}: {}", file.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list receipt files: {}", e.getMessage());
        }
        log.info("Loaded {} receipts from disk across {} executions ({} execution results restored)",
            loaded, receiptsByExecution.size(), executionResults.size());
    }

    @Override
    public void registerExecution(DepartmentExecutionResult executionResult) {
        if (executionResult == null || executionResult.executionId() == null) {
            return;
        }
        receiptsByExecution.putIfAbsent(executionResult.executionId(), new ArrayList<>());
        executionResults.put(executionResult.executionId(), executionResult);
        Set<String> dispatchIds = ConcurrentHashMap.newKeySet();
        executionResult.dispatchedAssignments().stream()
                .map(EmployeeExecutionDispatch::dispatchId)
                .forEach(dispatchIds::add);
        expectedDispatchIdsByExecution.put(executionResult.executionId(), dispatchIds);
        persistExecutionResult(executionResult);
        log.info("Registered execution {} with {} dispatched assignments", 
            executionResult.executionId(), dispatchIds.size());
    }

    @Override
    public EmployeeExecutionReceipt recordReceipt(EmployeeExecutionReceipt receipt) {
        receipt = ensureTimestamp(receipt);
        receiptsByExecution.computeIfAbsent(receipt.executionId(), key -> new ArrayList<>()).add(receipt);
        persistExecution(receipt.executionId());
        
        // 通知所有 listener
        DepartmentExecutionResult executionResult = executionResults.get(receipt.executionId());
        for (EmployeeExecutionReceiptService.ReceiptListener listener : listeners) {
            try {
                listener.onReceiptRecorded(receipt, executionResult);
            } catch (Exception e) {
                log.warn("Receipt listener failed: {}", e.getMessage());
            }
        }
        
        return receipt;
    }

    @Override
    public List<EmployeeExecutionReceipt> getReceipts(String executionId) {
        return List.copyOf(receiptsByExecution.getOrDefault(executionId, List.of()));
    }

    @Override
    public List<EmployeeExecutionReceipt> getReceiptsByDepartment(String department) {
        return receiptsByExecution.values().stream()
            .flatMap(List::stream)
            .filter(r -> {
                if (r.metadata() == null) return false;
                Object dept = r.metadata().get("department");
                return department.equals(dept);
            })
            .collect(Collectors.toList());
    }

    @Override
    public boolean isExecutionComplete(String executionId) {
        Set<String> expected = expectedDispatchIdsByExecution.get(executionId);
        if (expected == null || expected.isEmpty()) {
            return false;
        }
        long completed = receiptsByExecution.getOrDefault(executionId, List.of()).stream()
                .map(EmployeeExecutionReceipt::dispatchId)
                .distinct()
                .count();
        return completed >= expected.size();
    }

    @Override
    public void addReceiptListener(EmployeeExecutionReceiptService.ReceiptListener listener) {
        listeners.add(listener);
        log.info("Added receipt listener, total listeners: {}", listeners.size());
    }

    @Override
    public void removeReceiptListener(EmployeeExecutionReceiptService.ReceiptListener listener) {
        listeners.remove(listener);
        log.info("Removed receipt listener, total listeners: {}", listeners.size());
    }

    private void persistExecution(String executionId) {
        List<EmployeeExecutionReceipt> receipts = receiptsByExecution.get(executionId);
        if (receipts == null) {
            return;
        }
        String sanitizedExecId = executionId.replaceAll("[^a-zA-Z0-9._-]", "_");
        // 1. 主文件：按 executionId 组织（保持兼容）
        Path file = dataDir.resolve(sanitizedExecId + ".json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), List.copyOf(receipts));
            log.debug("Persisted {} receipts for execution {}", receipts.size(), executionId);
        } catch (IOException e) {
            log.error("Failed to persist receipts for execution {}: {}", executionId, e.getMessage());
        }
        // 2. 员工索引：按 employeeCode 组织（按员工查询）
        for (EmployeeExecutionReceipt receipt : receipts) {
            if (receipt.employeeCode() == null || receipt.employeeCode().isBlank()) continue;
            try {
                String sanitizedEmployee = receipt.employeeCode().replaceAll("[^a-zA-Z0-9._-]", "_");
                Path employeeDir = dataDir.resolve("by-employee").resolve(sanitizedEmployee);
                Files.createDirectories(employeeDir);
                Path employeeFile = employeeDir.resolve(sanitizedExecId + ".json");
                // 写入该员工在该 executionId 下的所有 receipt
                List<EmployeeExecutionReceipt> employeeReceipts = receipts.stream()
                    .filter(r -> sanitizedEmployee.equals(r.employeeCode() != null
                        ? r.employeeCode().replaceAll("[^a-zA-Z0-9._-]", "_")
                        : null))
                    .toList();
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(employeeFile.toFile(), employeeReceipts);
            } catch (IOException e) {
                log.warn("Failed to persist employee index for {}: {}", receipt.employeeCode(), e.getMessage());
            }
        }
    }

    private void persistExecutionResult(DepartmentExecutionResult result) {
        if (result == null || result.executionId() == null) return;
        Path file = dataDir.resolve(result.executionId().replaceAll("[^a-zA-Z0-9._-]", "_") + "-meta.json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), result);
            log.debug("Persisted execution result meta for {}", result.executionId());
        } catch (IOException e) {
            log.error("Failed to persist execution result for {}: {}", result.executionId(), e.getMessage());
        }
    }

    private EmployeeExecutionReceipt ensureTimestamp(EmployeeExecutionReceipt receipt) {
        if (receipt.receivedAt() == null) {
            return new EmployeeExecutionReceipt(
                    receipt.receiptId(),
                    receipt.executionId(),
                    receipt.dispatchId(),
                    receipt.assignmentId(),
                    receipt.employeeCode(),
                    receipt.employeeNeuronId(),
                    receipt.status(),
                    receipt.summary(),
                    java.time.Instant.now(),
                    receipt.metadata(),
                    receipt.worktreePath(),
                    receipt.diffPath()
            );
        }
        return receipt;
    }

    public void clearAll() {
        receiptsByExecution.clear();
        expectedDispatchIdsByExecution.clear();
        try {
            if (Files.exists(dataDir)) {
                List<Path> files = Files.list(dataDir)
                        .filter(f -> f.toString().endsWith(".json"))
                        .toList();
                for (Path file : files) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to clear receipt files: {}", e.getMessage());
        }
        log.info("Cleared all receipt data");
    }

    public Map<String, Object> getStats() {
        int totalExecutions = receiptsByExecution.size();
        long totalReceipts = receiptsByExecution.values().stream().mapToLong(List::size).sum();
        long completedExecutions = receiptsByExecution.keySet().stream()
                .filter(this::isExecutionComplete)
                .count();
        return Map.of(
                "totalExecutions", totalExecutions,
                "totalReceipts", totalReceipts,
                "completedExecutions", completedExecutions,
                "dataDir", dataDir.toString()
        );
    }
}
