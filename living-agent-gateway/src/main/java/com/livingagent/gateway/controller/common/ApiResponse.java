package com.livingagent.gateway.controller.common;

/**
 * 统一API响应格式
 * 所有Controller应使用此类包装响应数据
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String error,
        String errorDescription
) {
    /**
     * 创建成功响应
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    /**
     * 创建成功响应（无数据）
     */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null, null);
    }

    /**
     * 创建错误响应
     */
    public static <T> ApiResponse<T> err(String error, String description) {
        return new ApiResponse<>(false, null, error, description);
    }

    /**
     * 创建错误响应（带数据）
     */
    public static <T> ApiResponse<T> err(String error, String description, T data) {
        return new ApiResponse<>(false, data, error, description);
    }
}
