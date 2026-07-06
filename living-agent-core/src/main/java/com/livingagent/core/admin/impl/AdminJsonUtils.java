package com.livingagent.core.admin.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JSON 工具类（基于 Jackson）
 * <p>供 admin 包内的 AdminService 使用，避免重复创建 ObjectMapper。
 */
public final class AdminJsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AdminJsonUtils() {
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON object: " + e.getMessage(), e);
        }
    }

    public static List<Object> parseArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON array: " + e.getMessage(), e);
        }
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JSON: " + e.getMessage(), e);
        }
    }
}
