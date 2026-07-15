package com.livingagent.core.tool;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 工具结构化描述，用于 LLM 工具选择和行动发现。
 * 64-A-1: 扩展 capabilities/healthCheckHint/installHint/outputSchema
 */
public record ToolSchema(
    String name,
    String description,
    Map<String, Property> properties,
    List<String> required,
    /** 64-A-1: 工具能力标签（如 ["code_review", "git_merge", "ci_trigger"]） */
    List<String> capabilities,
    /** 64-A-1: 声明式输出 schema */
    OutputSchema outputSchema,
    /** 64-A-1: 健康检查提示（如 "curl -s http://jenkins:8384/api/json"） */
    String healthCheckHint,
    /** 64-A-1: 不可用时的安装/配置指引 */
    String installHint
) {
    /** 兼容旧构造：无扩展字段 */
    public ToolSchema(String name, String description,
                      Map<String, Property> properties, List<String> required) {
        this(name, description, properties, required, List.of(), null, null, null);
    }

    /** 声明式输出 schema */
    public record OutputSchema(
        String type,
        String description,
        Map<String, Property> properties
    ) {
        public static OutputSchema json(String description, Map<String, Property> properties) {
            return new OutputSchema("json", description, properties);
        }
        public static OutputSchema text(String description) {
            return new OutputSchema("text", description, null);
        }
        public static OutputSchema markdown(String description) {
            return new OutputSchema("markdown", description, null);
        }
        public static OutputSchema html(String description) {
            return new OutputSchema("html", description, null);
        }
        public static OutputSchema binary(String description) {
            return new OutputSchema("binary", description, null);
        }
    }

    public record Property(
        String type,
        String description,
        Object defaultValue,
        List<String> enumValues
    ) {
        public static Property string(String description) {
            return new Property("string", description, null, null);
        }

        public static Property string(String description, List<String> enumValues) {
            return new Property("string", description, null, enumValues);
        }

        public static Property integer(String description) {
            return new Property("integer", description, null, null);
        }

        public static Property bool(String description) {
            return new Property("boolean", description, null, null);
        }

        public static Property object(String description) {
            return new Property("object", description, null, null);
        }

        public static Property array(String description) {
            return new Property("array", description, null, null);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private final Map<String, Property> properties = new LinkedHashMap<>();
        private final List<String> required = new java.util.ArrayList<>();
        private List<String> capabilities = List.of();
        private OutputSchema outputSchema;
        private String healthCheckHint;
        private String installHint;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder parameter(String name, String type, String description, boolean isRequired) {
            Property property = switch (type.toLowerCase()) {
                case "string" -> Property.string(description);
                case "integer", "int" -> Property.integer(description);
                case "boolean", "bool" -> Property.bool(description);
                case "object" -> Property.object(description);
                case "array" -> Property.array(description);
                default -> new Property(type, description, null, null);
            };
            properties.put(name, property);
            if (isRequired) {
                required.add(name);
            }
            return this;
        }

        public Builder property(String name, Property property) {
            properties.put(name, property);
            return this;
        }

        public Builder required(String... names) {
            required.addAll(List.of(names));
            return this;
        }

        /** 64-A-1: 设置工具能力标签 */
        public Builder capabilities(String... caps) {
            this.capabilities = List.of(caps);
            return this;
        }

        /** 64-A-1: 设置工具能力标签 */
        public Builder capabilities(List<String> caps) {
            this.capabilities = caps != null ? caps : List.of();
            return this;
        }

        /** 64-A-1: 设置声明式输出 schema */
        public Builder outputSchema(OutputSchema outputSchema) {
            this.outputSchema = outputSchema;
            return this;
        }

        /** 64-A-1: 设置健康检查提示 */
        public Builder healthCheckHint(String hint) {
            this.healthCheckHint = hint;
            return this;
        }

        /** 64-A-1: 设置安装/配置指引 */
        public Builder installHint(String hint) {
            this.installHint = hint;
            return this;
        }

        public ToolSchema build() {
            return new ToolSchema(name, description, properties, required,
                capabilities, outputSchema, healthCheckHint, installHint);
        }
    }
}
