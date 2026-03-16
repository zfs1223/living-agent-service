package com.livingagent.core.tool;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record ToolSchema(
    String name,
    String description,
    Map<String, Property> properties,
    List<String> required
) {
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

        public ToolSchema build() {
            return new ToolSchema(name, description, properties, required);
        }
    }
}
