package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

public class HuggingFaceTool implements Tool {
    private static final String NAME = "huggingface";
    private static final String DESCRIPTION = "HuggingFace Hub operations: download models, upload models, manage datasets, search models. Requires huggingface-hub CLI (pip install huggingface-hub).";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "ml";
    
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
                .parameter("action", "string", "操作类型: download, upload, search, info, list, delete, login, whoami", true)
                .parameter("repo_id", "string", "仓库ID (如: meta-llama/Llama-2-7b-hf)", false)
                .parameter("repo_type", "string", "仓库类型: model, dataset, space", false)
                .parameter("local_dir", "string", "本地目录路径", false)
                .parameter("filename", "string", "特定文件名", false)
                .parameter("revision", "string", "分支/标签/commit", false)
                .parameter("query", "string", "搜索查询", false)
                .parameter("filter", "string", "搜索过滤器 (如: text-generation)", false)
                .parameter("limit", "integer", "搜索结果数量限制", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("model_download", "model_upload", "model_search", "dataset_management");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        if (action == null) {
            return ToolResult.failure("action parameter is required");
        }

        if (!isHfCliAvailable()) {
            return ToolResult.failure("HuggingFace CLI is not installed. Install with: pip install huggingface-hub");
        }

        try {
            ToolResult result;
            switch (action.toLowerCase()) {
                case "download":
                    result = downloadModel(params);
                    break;
                case "upload":
                    result = uploadModel(params);
                    break;
                case "search":
                    result = searchModels(params);
                    break;
                case "info":
                    result = getRepoInfo(params);
                    break;
                case "list":
                    result = listFiles(params);
                    break;
                case "delete":
                    result = deleteRepo(params);
                    break;
                case "login":
                    result = login(params);
                    break;
                case "whoami":
                    result = whoami();
                    break;
                default:
                    result = ToolResult.failure("Unknown action: " + action);
            }
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("HuggingFace operation failed: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("action parameter is required");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public ToolStats getStats() {
        return stats;
    }

    private boolean isHfCliAvailable() {
        try {
            Process process = new ProcessBuilder("huggingface-cli", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            try {
                Process process = new ProcessBuilder("python", "-m", "huggingface_hub.commands.huggingface_cli", "--version").start();
                return process.waitFor() == 0;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    private ToolResult downloadModel(ToolParams params) throws Exception {
        String repoId = params.getString("repo_id");
        if (repoId == null) {
            return ToolResult.failure("repo_id parameter is required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("huggingface-cli");
        cmd.add("download");
        cmd.add(repoId);
        
        String localDir = params.getString("local_dir");
        if (localDir != null && !localDir.isBlank()) {
            cmd.add("--local-dir");
            cmd.add(localDir);
        }
        
        String repoType = params.getString("repo_type");
        if (repoType != null && !repoType.isBlank()) {
            cmd.add("--repo-type");
            cmd.add(repoType);
        }
        
        String filename = params.getString("filename");
        if (filename != null && !filename.isBlank()) {
            cmd.add("--include");
            cmd.add(filename);
        }
        
        String revision = params.getString("revision");
        if (revision != null && !revision.isBlank()) {
            cmd.add("--revision");
            cmd.add(revision);
        }

        String output = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of(
                "message", "Download completed",
                "repo_id", repoId,
                "local_dir", localDir != null ? localDir : "cache",
                "output", output
        ));
    }

    private ToolResult uploadModel(ToolParams params) throws Exception {
        String repoId = params.getString("repo_id");
        String localDir = params.getString("local_dir");
        
        if (repoId == null || localDir == null) {
            return ToolResult.failure("repo_id and local_dir parameters are required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("huggingface-cli");
        cmd.add("upload");
        cmd.add(repoId);
        cmd.add(localDir);
        
        String repoType = params.getString("repo_type");
        if (repoType != null && !repoType.isBlank()) {
            cmd.add("--repo-type");
            cmd.add(repoType);
        }

        String output = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of(
                "message", "Upload completed",
                "repo_id", repoId,
                "local_dir", localDir,
                "output", output
        ));
    }

    private ToolResult searchModels(ToolParams params) throws Exception {
        String query = params.getString("query");
        if (query == null) {
            return ToolResult.failure("query parameter is required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("huggingface-cli");
        cmd.add("search");
        cmd.add(query);
        
        String filter = params.getString("filter");
        if (filter != null && !filter.isBlank()) {
            cmd.add("--filter");
            cmd.add(filter);
        }
        
        Integer limit = params.getInteger("limit");
        int limitValue = limit != null ? limit : 10;
        cmd.add("--limit");
        cmd.add(String.valueOf(limitValue));

        String output = executeCommand(cmd.toArray(new String[0]));
        List<Map<String, String>> results = parseSearchResults(output);
        
        return ToolResult.success(Map.of(
                "query", query,
                "total", results.size(),
                "results", results
        ));
    }

    private List<Map<String, String>> parseSearchResults(String output) {
        List<Map<String, String>> results = new ArrayList<>();
        String[] lines = output.split("\n");
        
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("=")) continue;
            
            String[] parts = line.split("\\s+", 3);
            if (parts.length >= 1) {
                Map<String, String> result = new LinkedHashMap<>();
                result.put("repo_id", parts[0].trim());
                if (parts.length > 1) {
                    result.put("type", parts[1].trim());
                }
                if (parts.length > 2) {
                    result.put("description", parts[2].trim());
                }
                results.add(result);
            }
        }
        
        return results;
    }

    private ToolResult getRepoInfo(ToolParams params) throws Exception {
        String repoId = params.getString("repo_id");
        if (repoId == null) {
            return ToolResult.failure("repo_id parameter is required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("huggingface-cli");
        cmd.add("repo");
        cmd.add("info");
        cmd.add(repoId);
        
        String repoType = params.getString("repo_type");
        if (repoType != null && !repoType.isBlank()) {
            cmd.add("--repo-type");
            cmd.add(repoType);
        }

        String output = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of(
                "repo_id", repoId,
                "info", output
        ));
    }

    private ToolResult listFiles(ToolParams params) throws Exception {
        String repoId = params.getString("repo_id");
        if (repoId == null) {
            return ToolResult.failure("repo_id parameter is required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("huggingface-cli");
        cmd.add("repo");
        cmd.add("ls");
        cmd.add(repoId);
        
        String repoType = params.getString("repo_type");
        if (repoType != null && !repoType.isBlank()) {
            cmd.add("--repo-type");
            cmd.add(repoType);
        }
        
        String revision = params.getString("revision");
        if (revision != null && !revision.isBlank()) {
            cmd.add("--revision");
            cmd.add(revision);
        }

        String output = executeCommand(cmd.toArray(new String[0]));
        List<Map<String, String>> files = parseFileList(output);
        
        return ToolResult.success(Map.of(
                "repo_id", repoId,
                "files", files,
                "total", files.size()
        ));
    }

    private List<Map<String, String>> parseFileList(String output) {
        List<Map<String, String>> files = new ArrayList<>();
        String[] lines = output.split("\n");
        
        for (String line : lines) {
            if (line.isBlank()) continue;
            
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                Map<String, String> file = new LinkedHashMap<>();
                file.put("name", parts[parts.length - 1]);
                file.put("size", parts.length > 2 ? parts[parts.length - 2] : "unknown");
                files.add(file);
            }
        }
        
        return files;
    }

    private ToolResult deleteRepo(ToolParams params) throws Exception {
        String repoId = params.getString("repo_id");
        if (repoId == null) {
            return ToolResult.failure("repo_id parameter is required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("huggingface-cli");
        cmd.add("repo");
        cmd.add("delete");
        cmd.add(repoId);
        cmd.add("--yes");
        
        String repoType = params.getString("repo_type");
        if (repoType != null && !repoType.isBlank()) {
            cmd.add("--repo-type");
            cmd.add(repoType);
        }

        String output = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of(
                "message", "Repository deleted",
                "repo_id", repoId,
                "output", output
        ));
    }

    private ToolResult login(ToolParams params) throws Exception {
        String token = params.getString("token");
        if (token == null) {
            return ToolResult.failure("token parameter is required. Get your token from https://huggingface.co/settings/tokens");
        }

        String output = executeCommand("huggingface-cli", "login", "--token", token);
        return ToolResult.success(Map.of("message", "Login successful", "output", output));
    }

    private ToolResult whoami() throws Exception {
        String output = executeCommand("huggingface-cli", "whoami");
        return ToolResult.success(Map.of("user_info", output));
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