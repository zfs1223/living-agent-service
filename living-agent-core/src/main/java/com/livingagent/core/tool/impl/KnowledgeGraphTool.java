package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KnowledgeGraphTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphTool.class);

    private static final String NAME = "knowledge_graph";
    private static final String DESCRIPTION = "知识图谱工具，管理实体、关系和结构化记忆";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "core";

    private final ObjectMapper objectMapper;
    private final Map<String, Entity> entities = new ConcurrentHashMap<>();
    private final Map<String, Relation> relations = new ConcurrentHashMap<>();
    private ToolStats stats = ToolStats.empty(NAME);

    public KnowledgeGraphTool() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDepartment() { return DEPARTMENT; }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("action", "string", "操作类型: create_entity, create_relation, query, delete", true)
                .parameter("entity_type", "string", "实体类型: Person, Project, Task, Event, Document, Organization", false)
                .parameter("entity_name", "string", "实体名称", false)
                .parameter("entity_properties", "object", "实体属性", false)
                .parameter("relation_type", "string", "关系类型: works_for, manages, belongs_to, depends_on, participates_in", false)
                .parameter("from_entity", "string", "关系起始实体ID", false)
                .parameter("to_entity", "string", "关系目标实体ID", false)
                .parameter("query", "string", "查询语句", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("entity_management", "relation_management", "query", "inference");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        
        try {
            Object result = switch (action) {
                case "create_entity" -> createEntity(params);
                case "create_relation" -> createRelation(params);
                case "query" -> query(params);
                case "delete" -> delete(params);
                case "list_entities" -> listEntities(params);
                case "list_relations" -> listRelations(params);
                default -> throw new IllegalArgumentException("未知操作: " + action);
            };
            
            stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
            return ToolResult.success(result);
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            log.error("知识图谱操作失败: {}", e.getMessage(), e);
            return ToolResult.failure("知识图谱操作失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createEntity(ToolParams params) {
        String entityType = params.getString("entity_type");
        String entityName = params.getString("entity_name");
        Object propsObj = params.get("entity_properties");
        Map<String, Object> properties = new HashMap<>();
        if (propsObj instanceof Map) {
            properties = new HashMap<>((Map<String, Object>) propsObj);
        }
        
        String entityId = UUID.randomUUID().toString();
        Entity entity = new Entity(entityId, entityType, entityName, properties);
        entities.put(entityId, entity);
        
        return Map.of(
            "entity_id", entityId,
            "type", entityType,
            "name", entityName,
            "created", true
        );
    }

    private Map<String, Object> createRelation(ToolParams params) {
        String relationType = params.getString("relation_type");
        String fromEntity = params.getString("from_entity");
        String toEntity = params.getString("to_entity");
        
        if (!entities.containsKey(fromEntity) || !entities.containsKey(toEntity)) {
            throw new IllegalArgumentException("实体不存在");
        }
        
        String relationId = UUID.randomUUID().toString();
        Relation relation = new Relation(relationId, relationType, fromEntity, toEntity);
        relations.put(relationId, relation);
        
        return Map.of(
            "relation_id", relationId,
            "type", relationType,
            "from", fromEntity,
            "to", toEntity,
            "created", true
        );
    }

    private List<Map<String, Object>> query(ToolParams params) {
        String query = params.getString("query");
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Entity entity : entities.values()) {
            if (query != null && (entity.name().contains(query) || entity.type().contains(query))) {
                results.add(Map.of(
                    "entity_id", entity.id(),
                    "type", entity.type(),
                    "name", entity.name(),
                    "properties", entity.properties()
                ));
            }
        }
        
        return results;
    }

    private Map<String, Object> delete(ToolParams params) {
        String entityId = params.getString("entity_id");
        entities.remove(entityId);
        relations.entrySet().removeIf(e -> 
            e.getValue().from().equals(entityId) || e.getValue().to().equals(entityId)
        );
        return Map.of("deleted", true, "entity_id", entityId);
    }

    private List<Map<String, Object>> listEntities(ToolParams params) {
        String type = params.getString("entity_type");
        return entities.values().stream()
            .filter(e -> type == null || e.type().equals(type))
            .map(e -> Map.<String, Object>of(
                "entity_id", e.id(),
                "type", e.type(),
                "name", e.name()
            ))
            .toList();
    }

    private List<Map<String, Object>> listRelations(ToolParams params) {
        return relations.values().stream()
            .map(r -> Map.<String, Object>of(
                "relation_id", r.id(),
                "type", r.type(),
                "from", r.from(),
                "to", r.to()
            ))
            .toList();
    }

    @Override
    public void validate(ToolParams params) {
        if (params.getString("action") == null) {
            throw new IllegalArgumentException("action 参数不能为空");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) { return true; }

    @Override
    public boolean requiresApproval() { return false; }

    @Override
    public ToolStats getStats() { return stats; }

    private record Entity(String id, String type, String name, Map<String, Object> properties) {}
    private record Relation(String id, String type, String from, String to) {}
}
