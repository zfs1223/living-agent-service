package com.livingagent.gateway.dto;

import java.util.List;

public record BrainModelResponse(
    String brainId,
    String brainName,
    String department,
    ModelInfo currentModel,
    List<AvailableModel> availableModels,
    long lastUpdated
) {
    public record ModelInfo(
        String modelId,
        String displayName,
        String provider,
        int contextLength,
        boolean hasApiKey
    ) {}

    public record AvailableModel(
        String modelId,
        String displayName,
        String provider,
        int contextLength,
        boolean cloudAvailable,
        boolean recommended,
        String bestFor
    ) {}

    public record SwitchModelRequest(
        String modelId
    ) {}

    public record ApiResponse<T>(
        boolean success,
        T data,
        String error,
        String errorDescription
    ) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, data, null, null);
        }

        public static <T> ApiResponse<T> error(String error, String description) {
            return new ApiResponse<>(false, null, error, description);
        }
    }
}
