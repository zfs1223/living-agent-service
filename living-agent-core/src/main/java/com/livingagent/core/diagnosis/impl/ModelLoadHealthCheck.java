package com.livingagent.core.diagnosis.impl;

import com.livingagent.core.diagnosis.HealthCheck;
import com.livingagent.core.diagnosis.HealthStatus;
import com.livingagent.core.model.ModelClient;
import com.livingagent.core.model.ModelRequest;
import com.livingagent.core.model.ModelResponse;
import com.livingagent.core.model.ModelStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ModelLoadHealthCheck implements HealthCheck {

    private static final Logger log = LoggerFactory.getLogger(ModelLoadHealthCheck.class);

    private static final List<String> MODEL_DIRS = buildModelDirs();

    private static List<String> buildModelDirs() {
        List<String> dirs = new ArrayList<>();
        String aiModelsPath = System.getenv("AI_MODELS_PATH");
        if (aiModelsPath != null && !aiModelsPath.isBlank()) {
            dirs.add(aiModelsPath);
            dirs.add(aiModelsPath + "/Qwen3-0.6B-GGUF");
            dirs.add(aiModelsPath + "/Qwen3.5-2B-GGUF");
            dirs.add(aiModelsPath + "/sherpa-ncnn");
            dirs.add(aiModelsPath + "/MeloTTS");
        }
        dirs.add("/app/ai-models");
        dirs.add("/app/ai-models/Qwen3-0.6B-GGUF");
        dirs.add("/app/ai-models/Qwen3.5-2B-GGUF");
        dirs.add("models/Qwen3-0.6B");
        dirs.add("models/Qwen3.5-2B");
        dirs.add("models");
        dirs.add("data/models");
        return List.copyOf(dirs);
    }

    private static final List<String> MODEL_FILE_MARKERS = List.of(
        "config.json",
        "model.safetensors",
        "pytorch_model.bin",
        "tokenizer.json"
    );

    private static final long PROBE_TIMEOUT_MS = 5000;

    private final Optional<ModelClient> modelClient;

    public ModelLoadHealthCheck() {
        this.modelClient = Optional.empty();
    }

    public ModelLoadHealthCheck(ModelClient modelClient) {
        this.modelClient = Optional.ofNullable(modelClient);
    }

    @Override
    public HealthStatus check() {
        // Phase 1: 检查模型文件是否存在
        FileCheckResult fileResult = checkModelFiles();

        // Phase 2: 如果有 ModelClient，通过 status 命令验证推理可用性
        if (modelClient.isPresent()) {
            return checkWithModelClient(fileResult);
        }

        // 无 ModelClient 时仅返回文件检查结果
        if (fileResult.foundModels > 0) {
            HealthStatus hs = HealthStatus.healthy("model_load");
            hs.setMessage(fileResult.foundModels + " model directory(ies) found: " + fileResult.details);
            return hs;
        } else {
            return HealthStatus.degraded("model_load",
                "No model files found in standard directories (checked: " + String.join(", ", MODEL_DIRS) + ")");
        }
    }

    private HealthStatus checkWithModelClient(FileCheckResult fileResult) {
        try {
            ModelClient client = modelClient.get();

            if (!client.isConnected()) {
                return HealthStatus.unhealthy("model_load",
                    "ModelClient not connected (pipe unavailable)");
            }

            ModelRequest statusRequest = ModelRequest.builder().service("status").build();
            ModelResponse response = client.sendControlRequest(statusRequest)
                .get(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (response == null || !response.isSuccess()) {
                String error = response != null ? response.getError() : "null response";
                return HealthStatus.unhealthy("model_load",
                    "Model daemon status probe failed: " + error);
            }

            // 检查 model_status 中是否有模型已加载
            Object modelStatus = response.get("model_status");
            if (modelStatus instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Boolean> modelsLoaded = (java.util.Map<String, Boolean>) modelStatus;
                long loadedCount = modelsLoaded.values().stream().filter(Boolean::booleanValue).count();

                if (loadedCount > 0) {
                    HealthStatus hs = HealthStatus.healthy("model_load");
                    hs.setMessage(String.format("%d/%d models loaded, %d file dirs found",
                        loadedCount, modelsLoaded.size(), fileResult.foundModels));
                    return hs;
                } else {
                    return HealthStatus.degraded("model_load",
                        String.format("Daemon alive but 0/%d models loaded, %d file dirs found",
                            modelsLoaded.size(), fileResult.foundModels));
                }
            }

            // daemon 响应成功但无 model_status 详情
            HealthStatus hs = HealthStatus.healthy("model_load");
            hs.setMessage("Model daemon responsive, " + fileResult.foundModels + " file dirs found");
            return hs;

        } catch (java.util.concurrent.TimeoutException e) {
            return HealthStatus.unhealthy("model_load",
                "Model daemon status probe timed out (" + PROBE_TIMEOUT_MS + "ms)");
        } catch (Exception e) {
            return HealthStatus.unhealthy("model_load",
                "Model daemon status probe error: " + e.getMessage());
        }
    }

    private FileCheckResult checkModelFiles() {
        String userDir = System.getProperty("user.dir", ".");
        int foundModels = 0;
        StringBuilder details = new StringBuilder();

        for (String modelDir : MODEL_DIRS) {
            Path dirPath = Path.of(userDir, modelDir);
            if (Files.isDirectory(dirPath)) {
                long markerCount = MODEL_FILE_MARKERS.stream()
                    .filter(marker -> Files.exists(dirPath.resolve(marker)))
                    .count();
                if (markerCount > 0) {
                    foundModels++;
                    details.append(modelDir).append("(").append(markerCount).append(" files); ");
                }
            }
        }
        return new FileCheckResult(foundModels, details.toString());
    }

    private static class FileCheckResult {
        final int foundModels;
        final String details;

        FileCheckResult(int foundModels, String details) {
            this.foundModels = foundModels;
            this.details = details;
        }
    }
}
