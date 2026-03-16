package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

public class DockerTool implements Tool {
    private static final String NAME = "docker";
    private static final String DESCRIPTION = "Docker container management: build, run, stop, remove containers, manage images, networks, volumes. Requires Docker CLI.";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "devops";
    
    private ToolStats stats = ToolStats.empty(NAME);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public String getDepartment() {
        return DEPARTMENT;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("action", "string", "操作类型: ps, images, run, stop, rm, rmi, build, logs, exec, network, volume, compose", true)
                .parameter("container", "string", "容器名称或ID", false)
                .parameter("image", "string", "镜像名称", false)
                .parameter("tag", "string", "镜像标签", false)
                .parameter("ports", "string", "端口映射 (如: 8080:80)", false)
                .parameter("volumes", "string", "卷挂载 (如: /host:/container)", false)
                .parameter("env", "string", "环境变量 (KEY=VALUE)", false)
                .parameter("command", "string", "执行的命令", false)
                .parameter("dockerfile_path", "string", "Dockerfile路径", false)
                .parameter("compose_path", "string", "docker-compose.yml路径", false)
                .parameter("all", "boolean", "显示所有(包括停止的)", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("container-management", "image-management", "network-management", "volume-management");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        
        String action = params.getString("action");
        if (action == null) {
            return ToolResult.failure("action parameter is required");
        }

        if (!isDockerAvailable()) {
            return ToolResult.failure("Docker is not installed or not running. Please install Docker and ensure it's running.");
        }

        try {
            ToolResult result = switch (action.toLowerCase()) {
                case "ps" -> listContainers(params);
                case "images" -> listImages(params);
                case "run" -> runContainer(params);
                case "stop" -> stopContainer(params);
                case "rm" -> removeContainer(params);
                case "rmi" -> removeImage(params);
                case "build" -> buildImage(params);
                case "logs" -> getLogs(params);
                case "exec" -> execCommand(params);
                case "network" -> manageNetwork(params);
                case "volume" -> manageVolume(params);
                case "compose" -> dockerCompose(params);
                case "info" -> dockerInfo();
                default -> ToolResult.failure("Unknown action: " + action);
            };
            
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("Docker operation failed: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null) {
            throw new IllegalArgumentException("action parameter is required");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy != null && policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public ToolStats getStats() {
        return stats;
    }

    private boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private ToolResult listContainers(ToolParams params) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("ps");
        
        Boolean all = params.getBoolean("all");
        if (Boolean.TRUE.equals(all)) {
            cmd.add("-a");
        }
        
        cmd.add("--format");
        cmd.add("{{.ID}}|{{.Names}}|{{.Image}}|{{.Status}}|{{.Ports}}");

        String output = executeCommand(cmd.toArray(new String[0]));
        List<Map<String, String>> containers = parseContainerList(output);
        
        return ToolResult.success(Map.of("containers", containers, "total", containers.size()));
    }

    private List<Map<String, String>> parseContainerList(String output) {
        List<Map<String, String>> containers = new ArrayList<>();
        String[] lines = output.split("\n");
        
        for (String line : lines) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\|");
            if (parts.length >= 4) {
                Map<String, String> container = new LinkedHashMap<>();
                container.put("id", parts[0].trim());
                container.put("name", parts[1].trim());
                container.put("image", parts[2].trim());
                container.put("status", parts[3].trim());
                if (parts.length > 4) {
                    container.put("ports", parts[4].trim());
                }
                containers.add(container);
            }
        }
        
        return containers;
    }

    private ToolResult listImages(ToolParams params) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("images");
        cmd.add("--format");
        cmd.add("{{.ID}}|{{.Repository}}|{{.Tag}}|{{.Size}}|{{.CreatedAt}}");

        String output = executeCommand(cmd.toArray(new String[0]));
        List<Map<String, String>> images = parseImageList(output);
        
        return ToolResult.success(Map.of("images", images, "total", images.size()));
    }

    private List<Map<String, String>> parseImageList(String output) {
        List<Map<String, String>> images = new ArrayList<>();
        String[] lines = output.split("\n");
        
        for (String line : lines) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\|");
            if (parts.length >= 4) {
                Map<String, String> image = new LinkedHashMap<>();
                image.put("id", parts[0].trim());
                image.put("repository", parts[1].trim());
                image.put("tag", parts[2].trim());
                image.put("size", parts[3].trim());
                if (parts.length > 4) {
                    image.put("created", parts[4].trim());
                }
                images.add(image);
            }
        }
        
        return images;
    }

    private ToolResult runContainer(ToolParams params) throws Exception {
        String image = params.getString("image");
        if (image == null) {
            return ToolResult.failure("image parameter is required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("-d");
        
        String container = params.getString("container");
        if (container != null && !container.isBlank()) {
            cmd.add("--name");
            cmd.add(container);
        }
        
        String ports = params.getString("ports");
        if (ports != null && !ports.isBlank()) {
            String[] portMappings = ports.split(",");
            for (String port : portMappings) {
                cmd.add("-p");
                cmd.add(port.trim());
            }
        }
        
        String volumes = params.getString("volumes");
        if (volumes != null && !volumes.isBlank()) {
            String[] volumeMappings = volumes.split(",");
            for (String volume : volumeMappings) {
                cmd.add("-v");
                cmd.add(volume.trim());
            }
        }
        
        String env = params.getString("env");
        if (env != null && !env.isBlank()) {
            String[] envVars = env.split(",");
            for (String envVar : envVars) {
                cmd.add("-e");
                cmd.add(envVar.trim());
            }
        }
        
        cmd.add(image);
        
        String command = params.getString("command");
        if (command != null && !command.isBlank()) {
            cmd.add(command);
        }

        String output = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("message", "Container started", "container_id", output.trim()));
    }

    private ToolResult stopContainer(ToolParams params) throws Exception {
        String container = params.getString("container");
        if (container == null) {
            return ToolResult.failure("container parameter is required");
        }

        String output = executeCommand("docker", "stop", container);
        return ToolResult.success(Map.of("message", "Container stopped", "container", container));
    }

    private ToolResult removeContainer(ToolParams params) throws Exception {
        String container = params.getString("container");
        if (container == null) {
            return ToolResult.failure("container parameter is required");
        }

        String output = executeCommand("docker", "rm", "-f", container);
        return ToolResult.success(Map.of("message", "Container removed", "container", container));
    }

    private ToolResult removeImage(ToolParams params) throws Exception {
        String image = params.getString("image");
        if (image == null) {
            return ToolResult.failure("image parameter is required");
        }

        String output = executeCommand("docker", "rmi", "-f", image);
        return ToolResult.success(Map.of("message", "Image removed", "image", image));
    }

    private ToolResult buildImage(ToolParams params) throws Exception {
        String dockerfilePath = params.getString("dockerfile_path");
        String tag = params.getString("tag");
        
        if (dockerfilePath == null || tag == null) {
            return ToolResult.failure("dockerfile_path and tag parameters are required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("build");
        cmd.add("-t");
        cmd.add(tag);
        cmd.add("-f");
        cmd.add(dockerfilePath);
        cmd.add(".");

        String output = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("message", "Image built successfully", "tag", tag, "output", output));
    }

    private ToolResult getLogs(ToolParams params) throws Exception {
        String container = params.getString("container");
        if (container == null) {
            return ToolResult.failure("container parameter is required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("logs");
        cmd.add("--tail");
        cmd.add("100");
        cmd.add(container);

        String output = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("logs", output, "container", container));
    }

    private ToolResult execCommand(ToolParams params) throws Exception {
        String container = params.getString("container");
        String command = params.getString("command");
        
        if (container == null || command == null) {
            return ToolResult.failure("container and command parameters are required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("exec");
        cmd.add(container);
        cmd.add("sh");
        cmd.add("-c");
        cmd.add(command);

        String output = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("output", output, "container", container, "command", command));
    }

    private ToolResult manageNetwork(ToolParams params) throws Exception {
        String command = params.getString("command");
        if (command == null) {
            String output = executeCommand("docker", "network", "ls");
            return ToolResult.success(Map.of("networks", output));
        }

        String output = executeCommand("docker", "network", command);
        return ToolResult.success(Map.of("output", output));
    }

    private ToolResult manageVolume(ToolParams params) throws Exception {
        String command = params.getString("command");
        if (command == null) {
            String output = executeCommand("docker", "volume", "ls");
            return ToolResult.success(Map.of("volumes", output));
        }

        String output = executeCommand("docker", "volume", command);
        return ToolResult.success(Map.of("output", output));
    }

    private ToolResult dockerCompose(ToolParams params) throws Exception {
        String command = params.getString("command");
        String composePath = params.getString("compose_path");
        
        if (command == null) {
            return ToolResult.failure("command parameter is required (up, down, ps, logs)");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("compose");
        
        if (composePath != null && !composePath.isBlank()) {
            cmd.add("-f");
            cmd.add(composePath);
        }
        
        cmd.add(command);

        String output = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("output", output, "command", command));
    }

    private ToolResult dockerInfo() throws Exception {
        String output = executeCommand("docker", "info");
        return ToolResult.success(Map.of("info", output));
    }

    private String executeCommand(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode + ": " + output);
        }
        
        return output.toString();
    }
}
