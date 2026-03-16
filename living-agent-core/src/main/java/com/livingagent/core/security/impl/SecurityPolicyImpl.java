package com.livingagent.core.security.impl;

import com.livingagent.core.security.AutonomyLevel;
import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Component
public class SecurityPolicyImpl implements SecurityPolicy {

    private static final Logger log = LoggerFactory.getLogger(SecurityPolicyImpl.class);

    private AutonomyLevel autonomyLevel = AutonomyLevel.SUPERVISED;
    private Path workspaceDir = Paths.get("./workspace");
    private boolean workspaceOnly = true;
    private int maxActionsPerHour = 100;
    private int maxCostPerDayCents = 1000;

    private final List<String> allowedCommands = new ArrayList<>();
    private final List<String> forbiddenPaths = new ArrayList<>();
    private final List<Path> allowedRoots = new ArrayList<>();

    private final Map<String, AtomicInteger> hourlyActionCount = new ConcurrentHashMap<>();
    private final Map<String, Integer> dailyCostCents = new ConcurrentHashMap<>();

    private static final Pattern SHELL_ESCAPE_PATTERN = Pattern.compile(
        "[`$]|\\$\\(|\\$\\{|\\|\\||&&|;|>|<|>>|<<"
    );

    private static final Set<String> HIGH_RISK_COMMANDS = Set.of(
        "rm", "sudo", "chmod", "chown", "dd", "mkfs", "fdisk",
        "curl", "wget", "nc", "netcat", "ssh", "scp", "rsync",
        "kill", "killall", "pkill", "reboot", "shutdown", "halt"
    );

    private static final Set<String> MEDIUM_RISK_COMMANDS = Set.of(
        "git", "npm", "yarn", "pip", "python", "java", "mvn",
        "docker", "kubectl", "terraform", "ansible"
    );

    public SecurityPolicyImpl() {
        initDefaultForbiddenPaths();
        initDefaultAllowedCommands();
    }

    private void initDefaultForbiddenPaths() {
        String home = System.getProperty("user.home");
        forbiddenPaths.addAll(List.of(
            "/etc", "/root", "/home", "/usr", "/bin", "/sbin",
            "/lib", "/opt", "/boot", "/dev", "/proc", "/sys", "/var", "/tmp",
            home + "/.ssh", home + "/.gnupg", home + "/.aws",
            home + "/.config", home + "/.env"
        ));
    }

    private void initDefaultAllowedCommands() {
        allowedCommands.addAll(List.of(
            "ls", "cat", "grep", "find", "echo", "pwd", "whoami",
            "git", "npm", "yarn", "mvn", "gradle"
        ));
    }

    @Override
    public AutonomyLevel getAutonomyLevel() {
        return autonomyLevel;
    }

    @Override
    public void setAutonomyLevel(AutonomyLevel level) {
        this.autonomyLevel = level;
        log.info("Autonomy level set to: {}", level);
    }

    @Override
    public Path getWorkspaceDir() {
        return workspaceDir;
    }

    public void setWorkspaceDir(Path path) {
        this.workspaceDir = path;
    }

    @Override
    public boolean isWorkspaceOnly() {
        return workspaceOnly;
    }

    public void setWorkspaceOnly(boolean workspaceOnly) {
        this.workspaceOnly = workspaceOnly;
    }

    @Override
    public List<String> getAllowedCommands() {
        return List.copyOf(allowedCommands);
    }

    public void addAllowedCommand(String command) {
        allowedCommands.add(command);
    }

    @Override
    public List<String> getForbiddenPaths() {
        return List.copyOf(forbiddenPaths);
    }

    public void addForbiddenPath(String path) {
        forbiddenPaths.add(path);
    }

    @Override
    public CommandRiskLevel validateCommandExecution(String command, boolean approved) {
        if (command == null || command.isBlank()) {
            return CommandRiskLevel.LOW;
        }

        if (containsShellEscapes(command)) {
            log.warn("Command contains shell escape sequences: {}", command);
            return CommandRiskLevel.HIGH;
        }

        String baseCommand = extractBaseCommand(command);
        
        if (HIGH_RISK_COMMANDS.contains(baseCommand)) {
            if (!approved && autonomyLevel != AutonomyLevel.FULL) {
                log.warn("High risk command blocked: {}", baseCommand);
                return CommandRiskLevel.HIGH;
            }
        }

        if (MEDIUM_RISK_COMMANDS.contains(baseCommand)) {
            if (autonomyLevel == AutonomyLevel.READ_ONLY) {
                log.warn("Medium risk command blocked in read-only mode: {}", baseCommand);
                return CommandRiskLevel.MEDIUM;
            }
        }

        return CommandRiskLevel.LOW;
    }

    private boolean containsShellEscapes(String command) {
        return SHELL_ESCAPE_PATTERN.matcher(command).find();
    }

    private String extractBaseCommand(String command) {
        String trimmed = command.trim().split("\\s+")[0];
        int lastSlash = trimmed.lastIndexOf('/');
        return lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
    }

    @Override
    public boolean isPathAllowed(String path) {
        if (path == null || path.contains("\0")) {
            return false;
        }

        if (path.contains("..")) {
            String normalized = path.replace("\\", "/");
            if (normalized.contains("../") || normalized.contains("/..")) {
                return false;
            }
        }

        String lower = path.toLowerCase();
        if (lower.contains("..%2f") || lower.contains("..%5c")) {
            return false;
        }

        if (path.startsWith("~") && !path.equals("~") && !path.startsWith("~/")) {
            return false;
        }

        Path resolved = resolvePath(path);
        return isResolvedPathAllowed(resolved);
    }

    private Path resolvePath(String path) {
        if (path.startsWith("~")) {
            String home = System.getProperty("user.home");
            path = home + path.substring(1);
        }
        return Paths.get(path).toAbsolutePath().normalize();
    }

    @Override
    public boolean isResolvedPathAllowed(Path resolved) {
        if (workspaceOnly && !resolved.startsWith(workspaceDir.toAbsolutePath())) {
            return false;
        }

        for (String forbidden : forbiddenPaths) {
            Path forbiddenPath = resolvePath(forbidden);
            if (resolved.startsWith(forbiddenPath)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void enforceToolOperation(ToolOperation operation, String operationName) {
        if (autonomyLevel == AutonomyLevel.READ_ONLY && operation == ToolOperation.ACT) {
            throw new SecurityException("Write operation not allowed in READ_ONLY mode: " + operationName);
        }
    }

    @Override
    public boolean recordAction() {
        String hourKey = Instant.now().toString().substring(0, 13);
        AtomicInteger count = hourlyActionCount.computeIfAbsent(hourKey, k -> new AtomicInteger(0));
        
        if (count.incrementAndGet() > maxActionsPerHour) {
            log.warn("Hourly action limit exceeded: {}", maxActionsPerHour);
            return false;
        }
        
        return true;
    }

    @Override
    public boolean isRateLimited() {
        String hourKey = Instant.now().toString().substring(0, 13);
        AtomicInteger count = hourlyActionCount.get(hourKey);
        return count != null && count.get() >= maxActionsPerHour;
    }

    @Override
    public int getMaxActionsPerHour() {
        return maxActionsPerHour;
    }

    public void setMaxActionsPerHour(int max) {
        this.maxActionsPerHour = max;
    }

    @Override
    public int getMaxCostPerDayCents() {
        return maxCostPerDayCents;
    }

    @Override
    public boolean isToolAllowed(String toolName) {
        if (autonomyLevel == AutonomyLevel.READ_ONLY) {
            return false;
        }
        return true;
    }

    public void setMaxCostPerDayCents(int max) {
        this.maxCostPerDayCents = max;
    }

    public boolean recordCost(int cents) {
        String dayKey = Instant.now().toString().substring(0, 10);
        int current = dailyCostCents.getOrDefault(dayKey, 0);
        
        if (current + cents > maxCostPerDayCents) {
            log.warn("Daily cost limit exceeded: {} + {} > {}", current, cents, maxCostPerDayCents);
            return false;
        }
        
        dailyCostCents.put(dayKey, current + cents);
        return true;
    }

    public void cleanupOldRecords() {
        String currentHour = Instant.now().toString().substring(0, 13);
        hourlyActionCount.keySet().removeIf(key -> !key.equals(currentHour));

        String currentDay = Instant.now().toString().substring(0, 10);
        dailyCostCents.keySet().removeIf(key -> !key.equals(currentDay));
    }
}
