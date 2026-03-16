package com.livingagent.core.database.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.WithPayloadSelector;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.VectorsFactory.vectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class QdrantVectorService {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorService.class);

    private final QdrantClient client;
    private final String collectionPrefix;
    private final int defaultVectorSize;

    public QdrantVectorService(
            QdrantClient client,
            @Value("${qdrant.collection-prefix:living_agent_}") String collectionPrefix,
            @Value("${vector.embedding.dimension:1024}") int defaultVectorSize) {
        this.client = client;
        this.collectionPrefix = collectionPrefix;
        this.defaultVectorSize = defaultVectorSize;
    }

    public void initializeCollections() {
        createCollectionIfNotExists("knowledge", defaultVectorSize);
        createCollectionIfNotExists("employee", 192);
        createCollectionIfNotExists("experience", defaultVectorSize);
        log.info("Initialized Qdrant collections with prefix: {}", collectionPrefix);
    }

    public void createCollectionIfNotExists(String collectionName, int vectorSize) {
        String fullCollectionName = collectionPrefix + collectionName;
        
        try {
            boolean exists = client.collectionExistsAsync(fullCollectionName).get();
            
            if (!exists) {
                client.createCollectionAsync(
                        fullCollectionName,
                        Collections.VectorParams.newBuilder()
                                .setSize(vectorSize)
                                .setDistance(Collections.Distance.Cosine)
                                .build()
                ).get();
                
                log.info("Created Qdrant collection: {} (vector size: {})", fullCollectionName, vectorSize);
            } else {
                log.debug("Collection already exists: {}", fullCollectionName);
            }
            
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create collection {}: {}", fullCollectionName, e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    public void upsertVector(String collectionName, String id, float[] vector, Map<String, Object> payload) {
        String fullCollectionName = collectionPrefix + collectionName;
        
        try {
            UUID uuid = UUID.fromString(id);
            
            PointStruct.Builder pointBuilder = PointStruct.newBuilder()
                    .setId(id(uuid))
                    .setVectors(vectors(toFloatList(vector)));
            
            if (payload != null) {
                pointBuilder.putAllPayload(convertToPayloadMap(payload));
            }
            
            client.upsertAsync(fullCollectionName, List.of(pointBuilder.build())).get();
            
            log.debug("Upserted vector {} to collection {}", id, fullCollectionName);
            
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to upsert vector {}: {}", id, e.getMessage());
            Thread.currentThread().interrupt();
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for id: {}", id);
        }
    }

    public List<SearchResult> search(String collectionName, float[] queryVector, int limit, float scoreThreshold) {
        String fullCollectionName = collectionPrefix + collectionName;
        
        try {
            SearchPoints searchPoints = SearchPoints.newBuilder()
                    .setCollectionName(fullCollectionName)
                    .setLimit(limit)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                    .addAllVector(toFloatList(queryVector))
                    .build();
            
            List<ScoredPoint> results = client.searchAsync(searchPoints).get();
            
            List<SearchResult> searchResults = new ArrayList<>();
            for (ScoredPoint point : results) {
                if (point.getScore() >= scoreThreshold) {
                    SearchResult result = new SearchResult();
                    result.setId(point.getId().getUuid());
                    result.setScore(point.getScore());
                    result.setPayload(convertFromPayloadMap(point.getPayloadMap()));
                    searchResults.add(result);
                }
            }
            
            return searchResults;
            
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to search in collection {}: {}", fullCollectionName, e.getMessage());
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    public List<SearchResult> searchWithFilter(
            String collectionName, 
            float[] queryVector, 
            int limit, 
            float scoreThreshold,
            Map<String, Object> filterConditions) {
        
        String fullCollectionName = collectionPrefix + collectionName;
        
        try {
            Filter filter = buildFilter(filterConditions);
            
            SearchPoints searchPoints = SearchPoints.newBuilder()
                    .setCollectionName(fullCollectionName)
                    .setLimit(limit)
                    .setFilter(filter)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                    .addAllVector(toFloatList(queryVector))
                    .build();
            
            List<ScoredPoint> results = client.searchAsync(searchPoints).get();
            
            List<SearchResult> searchResults = new ArrayList<>();
            for (ScoredPoint point : results) {
                if (point.getScore() >= scoreThreshold) {
                    SearchResult result = new SearchResult();
                    result.setId(point.getId().getUuid());
                    result.setScore(point.getScore());
                    result.setPayload(convertFromPayloadMap(point.getPayloadMap()));
                    searchResults.add(result);
                }
            }
            
            return searchResults;
            
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to search with filter in collection {}: {}", fullCollectionName, e.getMessage());
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    public void deleteVector(String collectionName, String id) {
        String fullCollectionName = collectionPrefix + collectionName;
        
        try {
            UUID uuid = UUID.fromString(id);
            client.deleteAsync(fullCollectionName, List.of(id(uuid))).get();
            
            log.debug("Deleted vector {} from collection {}", id, fullCollectionName);
            
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to delete vector {}: {}", id, e.getMessage());
            Thread.currentThread().interrupt();
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for id: {}", id);
        }
    }

    public void deleteCollection(String collectionName) {
        String fullCollectionName = collectionPrefix + collectionName;
        
        try {
            client.deleteCollectionAsync(fullCollectionName).get();
            log.info("Deleted Qdrant collection: {}", fullCollectionName);
            
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to delete collection {}: {}", fullCollectionName, e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    private Map<String, io.qdrant.client.grpc.JsonWithInt.Value> convertToPayloadMap(Map<String, Object> payload) {
        Map<String, io.qdrant.client.grpc.JsonWithInt.Value> result = new HashMap<>();
        if (payload != null) {
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                result.put(entry.getKey(), convertToValue(entry.getValue()));
            }
        }
        return result;
    }

    private io.qdrant.client.grpc.JsonWithInt.Value convertToValue(Object value) {
        if (value == null) {
            return io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
                    .setNullValue(io.qdrant.client.grpc.JsonWithInt.NullValue.NULL_VALUE).build();
        } else if (value instanceof String) {
            return io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
                    .setStringValue((String) value).build();
        } else if (value instanceof Integer) {
            return io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
                    .setIntegerValue((Integer) value).build();
        } else if (value instanceof Long) {
            return io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
                    .setIntegerValue((Long) value).build();
        } else if (value instanceof Double) {
            return io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
                    .setDoubleValue((Double) value).build();
        } else if (value instanceof Boolean) {
            return io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
                    .setBoolValue((Boolean) value).build();
        } else if (value instanceof List) {
            return convertToListValue((List<?>) value);
        } else if (value instanceof Map) {
            return convertToMapValue((Map<?, ?>) value);
        } else {
            return io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
                    .setStringValue(value.toString()).build();
        }
    }

    private io.qdrant.client.grpc.JsonWithInt.Value convertToListValue(List<?> list) {
        List<io.qdrant.client.grpc.JsonWithInt.Value> values = new ArrayList<>();
        for (Object item : list) {
            values.add(convertToValue(item));
        }
        return io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
                .setListValue(io.qdrant.client.grpc.JsonWithInt.ListValue.newBuilder()
                        .addAllValues(values).build()).build();
    }

    private io.qdrant.client.grpc.JsonWithInt.Value convertToMapValue(Map<?, ?> map) {
        Map<String, io.qdrant.client.grpc.JsonWithInt.Value> values = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String) {
                values.put((String) entry.getKey(), convertToValue(entry.getValue()));
            }
        }
        return io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
                .setStructValue(io.qdrant.client.grpc.JsonWithInt.Struct.newBuilder()
                        .putAllFields(values).build()).build();
    }

    private Map<String, Object> convertFromPayloadMap(Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payloadMap) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, io.qdrant.client.grpc.JsonWithInt.Value> entry : payloadMap.entrySet()) {
            result.put(entry.getKey(), extractValue(entry.getValue()));
        }
        return result;
    }

    private Object extractValue(io.qdrant.client.grpc.JsonWithInt.Value value) {
        switch (value.getKindCase()) {
            case STRING_VALUE:
                return value.getStringValue();
            case INTEGER_VALUE:
                return value.getIntegerValue();
            case DOUBLE_VALUE:
                return value.getDoubleValue();
            case BOOL_VALUE:
                return value.getBoolValue();
            case NULL_VALUE:
                return null;
            case LIST_VALUE:
                List<Object> list = new ArrayList<>();
                for (io.qdrant.client.grpc.JsonWithInt.Value v : value.getListValue().getValuesList()) {
                    list.add(extractValue(v));
                }
                return list;
            case STRUCT_VALUE:
                Map<String, Object> map = new HashMap<>();
                for (Map.Entry<String, io.qdrant.client.grpc.JsonWithInt.Value> e : value.getStructValue().getFieldsMap().entrySet()) {
                    map.put(e.getKey(), extractValue(e.getValue()));
                }
                return map;
            default:
                return null;
        }
    }

    private Filter buildFilter(Map<String, Object> conditions) {
        Filter.Builder filterBuilder = Filter.newBuilder();
        
        if (conditions != null && !conditions.isEmpty()) {
            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                io.qdrant.client.grpc.Points.Match match = buildMatch(entry.getValue());
                
                io.qdrant.client.grpc.Points.Condition condition = io.qdrant.client.grpc.Points.Condition.newBuilder()
                        .setField(io.qdrant.client.grpc.Points.FieldCondition.newBuilder()
                                .setKey(entry.getKey())
                                .setMatch(match)
                                .build())
                        .build();
                filterBuilder.addMust(condition);
            }
        }
        
        return filterBuilder.build();
    }

    private io.qdrant.client.grpc.Points.Match buildMatch(Object value) {
        io.qdrant.client.grpc.Points.Match.Builder matchBuilder = io.qdrant.client.grpc.Points.Match.newBuilder();
        
        if (value == null) {
            return matchBuilder.build();
        } else if (value instanceof String) {
            matchBuilder.setKeyword((String) value);
        } else if (value instanceof Integer || value instanceof Long) {
            matchBuilder.setInteger(((Number) value).longValue());
        } else if (value instanceof Boolean) {
            matchBuilder.setBoolean((Boolean) value);
        } else {
            matchBuilder.setKeyword(value.toString());
        }
        
        return matchBuilder.build();
    }

    public static class SearchResult {
        private String id;
        private float score;
        private Map<String, Object> payload;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public float getScore() { return score; }
        public void setScore(float score) { this.score = score; }

        public Map<String, Object> getPayload() { return payload; }
        public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    }
}
