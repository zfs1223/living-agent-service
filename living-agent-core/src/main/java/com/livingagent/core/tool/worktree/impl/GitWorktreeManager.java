package com.livingagent.core.tool.worktree.impl;

import com.livingagent.core.planner.dag.DagTaskStatus;
import com.livingagent.core.planner.dag.TaskDagService;
import com.livingagent.core.tool.worktree.WorktreeEntry;
import com.livingagent.core.tool.worktree.WorktreeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GitWorktreeManager implements WorktreeManager {

    private static final Logger log = LoggerFactory.getLogger(GitWorktreeManager.class);

    private final Path repoRoot;
    private final Path worktreesDir;
    private final TaskDagService taskDagService;
    private final Map<String, WorktreeEntry> index = new ConcurrentHashMap<>();

    public GitWorktreeManager(Path repoRoot, TaskDagService taskDagService) {
        this.repoRoot = repoRoot;
        this.worktreesDir = repoRoot.resolve(".worktrees");
        this.taskDagService = taskDagService;
        try {
            Files.createDirectories(worktreesDir);
        } catch (IOException e) {
            log.warn("Failed to create worktrees directory: {}", worktreesDir, e);
        }
    }

    @Override
    public WorktreeEntry create(String name, String taskId, String baseRef) {
        if (index.containsKey(name)) {
            throw new IllegalStateException("Worktree already exists: " + name);
        }

        String branch = "wt/" + name;
        Path worktreePath = worktreesDir.resolve(name);

        try {
            runGit("worktree", "add", "-b", branch, worktreePath.toString(),
                baseRef != null ? baseRef : "HEAD");

            WorktreeEntry entry = WorktreeEntry.create(name, worktreePath.toString(), branch, taskId);
            index.put(name, entry);

            if (taskId != null && taskDagService != null) {
                taskDagService.bindWorktree(taskId, name);
            }

            log.info("Created worktree: {} at {} (branch={}, task={})", name, worktreePath, branch, taskId);
            return entry;
        } catch (Exception e) {
            log.error("Failed to create worktree: {}", name, e);
            throw new RuntimeException("Failed to create worktree: " + name, e);
        }
    }

    @Override
    public Optional<WorktreeEntry> get(String name) {
        return Optional.ofNullable(index.get(name));
    }

    @Override
    public List<WorktreeEntry> getAll() {
        return new ArrayList<>(index.values());
    }

    @Override
    public List<WorktreeEntry> getActive() {
        return index.values().stream()
            .filter(e -> e.status() == WorktreeEntry.WorktreeStatus.ACTIVE)
            .toList();
    }

    @Override
    public WorktreeEntry bindTask(String name, String taskId) {
        WorktreeEntry entry = index.get(name);
        if (entry == null) {
            throw new IllegalArgumentException("Worktree not found: " + name);
        }
        WorktreeEntry updated = new WorktreeEntry(name, entry.path(), entry.branch(),
            taskId, entry.status(), entry.createdAt());
        index.put(name, updated);

        if (taskDagService != null) {
            taskDagService.bindWorktree(taskId, name);
        }
        return updated;
    }

    @Override
    public ExecutionResult run(String name, String command) {
        WorktreeEntry entry = index.get(name);
        if (entry == null) {
            return ExecutionResult.fail("Worktree not found: " + name, -1);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command)
                .directory(Path.of(entry.path()).toFile())
                .redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            return exitCode == 0 ? ExecutionResult.ok(output) : ExecutionResult.fail(output, exitCode);
        } catch (Exception e) {
            return ExecutionResult.fail("Execution failed: " + e.getMessage(), -1);
        }
    }

    @Override
    public CloseoutResult closeout(String name, CloseoutAction action, String reason, boolean completeTask) {
        WorktreeEntry entry = index.get(name);
        if (entry == null) {
            return new CloseoutResult(false, "Worktree not found: " + name, action);
        }

        try {
            if (action == CloseoutAction.REMOVE) {
                runGit("worktree", "remove", entry.path(), "--force");
                runGit("branch", "-d", entry.branch());
                index.put(name, entry.withStatus(WorktreeEntry.WorktreeStatus.REMOVED));
                log.info("Removed worktree: {} ({})", name, reason);
            } else {
                index.put(name, entry.withStatus(WorktreeEntry.WorktreeStatus.KEPT));
                log.info("Kept worktree: {} ({})", name, reason);
            }

            if (completeTask && entry.taskId() != null && taskDagService != null) {
                taskDagService.updateTaskStatus(entry.taskId(), DagTaskStatus.COMPLETED);
            }

            return new CloseoutResult(true, "Worktree " + name + " " + action.name().toLowerCase(), action);
        } catch (Exception e) {
            log.error("Failed to closeout worktree: {}", name, e);
            return new CloseoutResult(false, "Closeout failed: " + e.getMessage(), action);
        }
    }

    @Override
    public boolean exists(String name) {
        return index.containsKey(name);
    }

    @Override
    public void cleanAll() {
        for (WorktreeEntry entry : new ArrayList<>(index.values())) {
            if (entry.status() == WorktreeEntry.WorktreeStatus.ACTIVE) {
                try {
                    runGit("worktree", "remove", entry.path(), "--force");
                } catch (Exception e) {
                    log.warn("Failed to clean worktree: {}", entry.name(), e);
                }
            }
        }
        index.clear();
    }

    private String runGit(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(command)
            .directory(repoRoot.toFile())
            .redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Git command failed: " + String.join(" ", command) + "\n" + output);
        }
        return output;
    }
}
