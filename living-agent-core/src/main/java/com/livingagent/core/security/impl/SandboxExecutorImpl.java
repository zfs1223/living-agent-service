package com.livingagent.core.security.impl;

import com.livingagent.core.security.SandboxExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class SandboxExecutorImpl implements SandboxExecutor {

    private static final Logger log = LoggerFactory.getLogger(SandboxExecutorImpl.class);

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    @Value("${sandbox.worker.java-path:java}")
    private String javaPath;
    
    @Value("${sandbox.worker.classpath:}")
    private String workerClasspath;
    
    @Value("${sandbox.enabled:true}")
    private boolean sandboxEnabled;
    
    private final AtomicLong totalExecutions = new AtomicLong(0);
    private final AtomicLong successfulExecutions = new AtomicLong(0);
    private final AtomicLong failedExecutions = new AtomicLong(0);
    private final AtomicLong timeoutCount = new AtomicLong(0);
    private final AtomicLong memoryExceededCount = new AtomicLong(0);
    private final AtomicLong activeExecutions = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);

    @Override
    public <T> ExecutionResult<T> execute(SandboxConfig config, CallableTask<T> task) {
        totalExecutions.incrementAndGet();
        activeExecutions.incrementAndGet();
        
        long startTime = System.currentTimeMillis();
        
        try {
            String taskId = UUID.randomUUID().toString();
            Path taskFile = serializeTask(task, taskId);
            Path resultFile = getResultPath(taskId);
            
            List<String> command = buildWorkerCommand(config, taskFile, resultFile, taskId);
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(config.workingDirectory()));
            pb.environment().putAll(config.environment());
            pb.environment().put("SANDBOX_TASK_ID", taskId);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            CountDownLatch outputLatch = new CountDownLatch(1);
            
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        log.debug("[Sandbox-{}] {}", taskId, line);
                    }
                } catch (IOException e) {
                    log.error("Error reading process output: {}", e.getMessage());
                } finally {
                    outputLatch.countDown();
                }
            });
            outputThread.start();
            
            boolean completed = process.waitFor(config.timeoutMs(), TimeUnit.MILLISECONDS);
            
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                timeoutCount.incrementAndGet();
                failedExecutions.incrementAndGet();
                cleanupFiles(taskFile, resultFile);
                return ExecutionResult.timeout();
            }
            
            outputLatch.await(5, TimeUnit.SECONDS);
            
            long executionTime = System.currentTimeMillis() - startTime;
            totalExecutionTime.addAndGet(executionTime);
            
            if (process.exitValue() != 0) {
                failedExecutions.incrementAndGet();
                String errorMsg = output.toString();
                
                if (errorMsg.contains("OutOfMemoryError") || errorMsg.contains("memory limit")) {
                    memoryExceededCount.incrementAndGet();
                    cleanupFiles(taskFile, resultFile);
                    return ExecutionResult.memoryLimitExceeded();
                }
                
                cleanupFiles(taskFile, resultFile);
                return ExecutionResult.failure("Process exited with code " + process.exitValue() + ": " + errorMsg);
            }
            
            ExecutionResult<T> result = deserializeResult(resultFile, executionTime);
            
            cleanupFiles(taskFile, resultFile);
            
            if (result.success()) {
                successfulExecutions.incrementAndGet();
            } else {
                failedExecutions.incrementAndGet();
            }
            
            return result;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failedExecutions.incrementAndGet();
            return ExecutionResult.failure("Execution interrupted");
            
        } catch (Exception e) {
            failedExecutions.incrementAndGet();
            log.error("Sandbox execution error: {}", e.getMessage(), e);
            return ExecutionResult.failure(e.getMessage());
            
        } finally {
            activeExecutions.decrementAndGet();
        }
    }

    @Override
    public ExecutionResult<?> executeScript(SandboxConfig config, String script, String language) {
        log.info("Executing {} script in sandbox", language);
        
        try {
            Path tempScript = createTempScript(script, language);
            
            String[] command = buildScriptCommand(language, tempScript.toString());
            
            ExecutionResult<String> result = executeInProcess(config, command);
            
            try {
                Files.deleteIfExists(tempScript);
            } catch (IOException e) {
                log.warn("Failed to delete temp script: {}", e.getMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Script execution failed: {}", e.getMessage());
            return ExecutionResult.failure(e.getMessage());
        }
    }

    @Override
    public ExecutionResult<?> executeCommand(SandboxConfig config, String command, String[] args) {
        List<String> commandParts = new ArrayList<>();
        commandParts.add(command);
        if (args != null) {
            commandParts.addAll(Arrays.asList(args));
        }
        
        return executeInProcess(config, commandParts.toArray(new String[0]));
    }

    private ExecutionResult<String> executeInProcess(SandboxConfig config, String[] command) {
        totalExecutions.incrementAndGet();
        activeExecutions.incrementAndGet();
        
        long startTime = System.currentTimeMillis();
        
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(config.workingDirectory()));
            pb.environment().putAll(config.environment());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            boolean completed = process.waitFor(config.timeoutMs(), TimeUnit.MILLISECONDS);
            long executionTime = System.currentTimeMillis() - startTime;
            totalExecutionTime.addAndGet(executionTime);
            
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                timeoutCount.incrementAndGet();
                failedExecutions.incrementAndGet();
                return ExecutionResult.timeout();
            }
            
            if (process.exitValue() != 0) {
                failedExecutions.incrementAndGet();
                return ExecutionResult.failure("Process exited with code " + process.exitValue());
            }
            
            successfulExecutions.incrementAndGet();
            return ExecutionResult.success(output.toString(), executionTime);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failedExecutions.incrementAndGet();
            return ExecutionResult.failure("Execution interrupted");
            
        } catch (Exception e) {
            failedExecutions.incrementAndGet();
            log.error("Process execution error: {}", e.getMessage());
            return ExecutionResult.failure(e.getMessage());
            
        } finally {
            activeExecutions.decrementAndGet();
        }
    }

    @Override
    public boolean isAvailable() {
        return sandboxEnabled && !executorService.isShutdown();
    }

    @Override
    public SandboxStats getStats() {
        long total = totalExecutions.get();
        double avgTime = total > 0 ? (double) totalExecutionTime.get() / total : 0;
        
        return new SandboxStats(
            total,
            successfulExecutions.get(),
            failedExecutions.get(),
            timeoutCount.get(),
            memoryExceededCount.get(),
            avgTime,
            activeExecutions.get()
        );
    }

    @Override
    public void cleanup() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        cleanupTempFiles();
    }

    private List<String> buildWorkerCommand(SandboxConfig config, Path taskFile, Path resultFile, String taskId) {
        List<String> command = new ArrayList<>();
        command.add(javaPath);
        
        long memoryBytes = config.memoryLimitMB() * 1024 * 1024;
        command.add("-Xmx" + memoryBytes);
        command.add("-Xms" + Math.min(memoryBytes / 2, 256 * 1024 * 1024));
        
        command.add("-XX:+UseG1GC");
        command.add("-XX:MaxGCPauseMillis=100");
        
        if (!config.networkAllowed()) {
            command.add("-Djava.net.preferIPv4Stack=true");
        }
        
        String classpath = workerClasspath.isEmpty() ? 
            System.getProperty("java.class.path") : workerClasspath;
        command.add("-cp");
        command.add(classpath);
        
        command.add("com.livingagent.core.security.impl.SandboxWorker");
        command.add(taskFile.toString());
        command.add(resultFile.toString());
        command.add(taskId);
        
        return command;
    }

    private Path serializeTask(CallableTask<?> task, String taskId) throws IOException {
        Path taskFile = getTaskPath(taskId);
        
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(taskFile)))) {
            oos.writeObject(new TaskWrapper(task));
        }
        
        return taskFile;
    }

    @SuppressWarnings("unchecked")
    private <T> ExecutionResult<T> deserializeResult(Path resultFile, long executionTime) {
        if (!Files.exists(resultFile)) {
            return ExecutionResult.failure("Result file not found");
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(resultFile)))) {
            Object obj = ois.readObject();
            
            if (obj instanceof ExecutionResult<?> result) {
                return (ExecutionResult<T>) result;
            } else if (obj instanceof Throwable error) {
                return ExecutionResult.failure(error.getMessage());
            }
            
            return ExecutionResult.success((T) obj, executionTime);
            
        } catch (Exception e) {
            log.error("Failed to deserialize result: {}", e.getMessage());
            return ExecutionResult.failure("Failed to read result: " + e.getMessage());
        }
    }

    private Path getTaskPath(String taskId) {
        return Paths.get(System.getProperty("java.io.tmpdir"), "sandbox_task_" + taskId + ".ser");
    }

    private Path getResultPath(String taskId) {
        return Paths.get(System.getProperty("java.io.tmpdir"), "sandbox_result_" + taskId + ".ser");
    }

    private void cleanupFiles(Path... files) {
        for (Path file : files) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.warn("Failed to cleanup file {}: {}", file, e.getMessage());
            }
        }
    }

    private void cleanupTempFiles() {
        try {
            Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
            Files.list(tmpDir)
                .filter(p -> p.getFileName().toString().startsWith("sandbox_"))
                .filter(p -> {
                    try {
                        return Files.getLastModifiedTime(p).toMillis() < 
                            System.currentTimeMillis() - 3600000;
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        log.warn("Failed to delete temp file: {}", p);
                    }
                });
        } catch (IOException e) {
            log.warn("Failed to cleanup temp files: {}", e.getMessage());
        }
    }

    private Path createTempScript(String script, String language) throws IOException {
        String extension = switch (language.toLowerCase()) {
            case "python", "python3" -> ".py";
            case "javascript", "node", "nodejs" -> ".js";
            case "bash", "shell", "sh" -> ".sh";
            case "powershell", "ps1" -> ".ps1";
            case "ruby" -> ".rb";
            case "perl" -> ".pl";
            default -> ".txt";
        };
        
        Path tempFile = Files.createTempFile("sandbox_", extension);
        Files.writeString(tempFile, script);
        return tempFile;
    }

    private String[] buildScriptCommand(String language, String scriptPath) {
        return switch (language.toLowerCase()) {
            case "python", "python3" -> new String[]{"python3", scriptPath};
            case "javascript", "node", "nodejs" -> new String[]{"node", scriptPath};
            case "bash", "shell", "sh" -> new String[]{"bash", scriptPath};
            case "powershell", "ps1" -> new String[]{"powershell", "-File", scriptPath};
            case "ruby" -> new String[]{"ruby", scriptPath};
            case "perl" -> new String[]{"perl", scriptPath};
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    public static class TaskWrapper implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final byte[] serializedTask;
        
        @SuppressWarnings("unchecked")
        public TaskWrapper(CallableTask<?> task) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(task);
            }
            this.serializedTask = baos.toByteArray();
        }
        
        public CallableTask<?> getTask() throws IOException, ClassNotFoundException {
            try (ObjectInputStream ois = new ObjectInputStream(
                    new ByteArrayInputStream(serializedTask))) {
                return (CallableTask<?>) ois.readObject();
            }
        }
    }
}
