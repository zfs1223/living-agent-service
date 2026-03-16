package com.livingagent.core.security.impl;

import com.livingagent.core.security.SandboxExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class SandboxWorker {

    private static final Logger log = LoggerFactory.getLogger(SandboxWorker.class);

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: SandboxWorker <taskFile> <resultFile> <taskId>");
            System.exit(1);
        }

        String taskFile = args[0];
        String resultFile = args[1];
        String taskId = args[2];

        log.info("SandboxWorker started - taskId: {}", taskId);

        try {
            SandboxExecutor.CallableTask<?> task = loadTask(taskFile);
            
            Thread executionThread = new Thread(() -> {
                try {
                    Object result = task.call();
                    saveResult(resultFile, result, null);
                    log.info("Task {} completed successfully", taskId);
                } catch (Throwable e) {
                    log.error("Task {} failed: {}", taskId, e.getMessage(), e);
                    saveResult(resultFile, null, e);
                }
            });
            
            executionThread.start();
            executionThread.join();
            
            System.exit(0);
            
        } catch (Throwable e) {
            log.error("SandboxWorker error: {}", e.getMessage(), e);
            saveResult(resultFile, null, e);
            System.exit(1);
        }
    }

    private static SandboxExecutor.CallableTask<?> loadTask(String taskFile) 
            throws IOException, ClassNotFoundException {
        
        Path path = Paths.get(taskFile);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Task file not found: " + taskFile);
        }

        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            Object obj = ois.readObject();
            
            if (obj instanceof SandboxExecutorImpl.TaskWrapper wrapper) {
                return wrapper.getTask();
            } else if (obj instanceof SandboxExecutor.CallableTask<?> task) {
                return task;
            } else {
                throw new IllegalArgumentException("Invalid task object type: " + obj.getClass());
            }
        }
    }

    private static void saveResult(String resultFile, Object result, Throwable error) {
        Path path = Paths.get(resultFile);
        
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            
            if (error != null) {
                oos.writeObject(error);
            } else {
                oos.writeObject(result);
            }
            
        } catch (IOException e) {
            log.error("Failed to save result: {}", e.getMessage());
            
            try {
                Files.writeString(path, "ERROR: " + e.getMessage());
            } catch (IOException ex) {
                log.error("Failed to write error message: {}", ex.getMessage());
            }
        }
    }
}
