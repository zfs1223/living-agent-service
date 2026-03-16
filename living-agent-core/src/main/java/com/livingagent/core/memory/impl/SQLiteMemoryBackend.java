package com.livingagent.core.memory.impl;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.livingagent.core.memory.MemoryBackend;
import com.livingagent.core.memory.MemoryCategory;
import com.livingagent.core.memory.MemoryEntry;

public class SQLiteMemoryBackend implements MemoryBackend {
    
    private static final Logger log = LoggerFactory.getLogger(SQLiteMemoryBackend.class);
    
    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS memories (
            id TEXT PRIMARY KEY,
            key TEXT NOT NULL,
            content TEXT NOT NULL,
            category TEXT NOT NULL,
            session_id TEXT,
            timestamp INTEGER NOT NULL,
            score REAL,
            embedding BLOB,
            metadata TEXT
        )
        """;
    
    private static final String CREATE_INDEX_SQL = """
        CREATE INDEX IF NOT EXISTS idx_memories_key ON memories(key);
        CREATE INDEX IF NOT EXISTS idx_memories_category ON memories(category);
        CREATE INDEX IF NOT EXISTS idx_memories_session ON memories(session_id);
        CREATE INDEX IF NOT EXISTS idx_memories_timestamp ON memories(timestamp);
        """;
    
    private final String dbPath;
    private Connection connection;
    
    public SQLiteMemoryBackend() {
        this("memory.db");
    }
    
    public SQLiteMemoryBackend(String dbPath) {
        this.dbPath = dbPath;
    }
    
    @Override
    public String name() {
        return "sqlite";
    }
    
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                String url = "jdbc:sqlite:" + dbPath;
                connection = DriverManager.getConnection(url);
                
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(CREATE_TABLE_SQL);
                    for (String indexSql : CREATE_INDEX_SQL.split(";")) {
                        if (!indexSql.trim().isEmpty()) {
                            stmt.execute(indexSql.trim());
                        }
                    }
                }
                
                log.info("SQLite memory backend initialized: {}", dbPath);
            } catch (SQLException e) {
                log.error("Failed to initialize SQLite memory backend", e);
                throw new RuntimeException("Failed to initialize SQLite memory backend", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> store(String key, String content, MemoryCategory category, String sessionId) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT OR REPLACE INTO memories (id, key, content, category, session_id, timestamp)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                String id = UUID.randomUUID().toString();
                stmt.setString(1, id);
                stmt.setString(2, key);
                stmt.setString(3, content);
                stmt.setString(4, category.name());
                stmt.setString(5, sessionId);
                stmt.setLong(6, Instant.now().toEpochMilli());
                
                stmt.executeUpdate();
                log.debug("Stored memory: key={}, category={}", key, category);
            } catch (SQLException e) {
                log.error("Failed to store memory: key={}", key, e);
                throw new RuntimeException("Failed to store memory", e);
            }
        });
    }
    
    @Override
    public CompletableFuture<List<MemoryEntry>> recall(String query, int limit, String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql;
            boolean hasSession = sessionId != null && !sessionId.isEmpty();
            
            if (hasSession) {
                sql = """
                    SELECT id, key, content, category, session_id, timestamp, score
                    FROM memories
                    WHERE (session_id = ? OR session_id IS NULL)
                    AND (content LIKE ? OR key LIKE ?)
                    ORDER BY timestamp DESC
                    LIMIT ?
                    """;
            } else {
                sql = """
                    SELECT id, key, content, category, session_id, timestamp, score
                    FROM memories
                    WHERE content LIKE ? OR key LIKE ?
                    ORDER BY timestamp DESC
                    LIMIT ?
                    """;
            }
            
            List<MemoryEntry> results = new ArrayList<>();
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                int paramIndex = 1;
                
                if (hasSession) {
                    stmt.setString(paramIndex++, sessionId);
                }
                
                String searchPattern = "%" + query + "%";
                stmt.setString(paramIndex++, searchPattern);
                stmt.setString(paramIndex++, searchPattern);
                stmt.setInt(paramIndex, limit);
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    results.add(mapToEntry(rs));
                }
            } catch (SQLException e) {
                log.error("Failed to recall memories: query={}", query, e);
            }
            
            return results;
        });
    }
    
    @Override
    public CompletableFuture<Optional<MemoryEntry>> get(String key) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT id, key, content, category, session_id, timestamp, score
                FROM memories
                WHERE key = ?
                """;
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, key);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    return Optional.of(mapToEntry(rs));
                }
            } catch (SQLException e) {
                log.error("Failed to get memory: key={}", key, e);
            }
            
            return Optional.empty();
        });
    }
    
    @Override
    public CompletableFuture<List<MemoryEntry>> list(MemoryCategory category, String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sqlBuilder = new StringBuilder("""
                SELECT id, key, content, category, session_id, timestamp, score
                FROM memories
                WHERE 1=1
                """);
            
            List<Object> params = new ArrayList<>();
            
            if (category != null) {
                sqlBuilder.append(" AND category = ?");
                params.add(category.name());
            }
            
            if (sessionId != null && !sessionId.isEmpty()) {
                sqlBuilder.append(" AND (session_id = ? OR session_id IS NULL)");
                params.add(sessionId);
            }
            
            sqlBuilder.append(" ORDER BY timestamp DESC");
            
            List<MemoryEntry> results = new ArrayList<>();
            
            try (PreparedStatement stmt = connection.prepareStatement(sqlBuilder.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    results.add(mapToEntry(rs));
                }
            } catch (SQLException e) {
                log.error("Failed to list memories: category={}", category, e);
            }
            
            return results;
        });
    }
    
    @Override
    public CompletableFuture<Boolean> forget(String key) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM memories WHERE key = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, key);
                int rows = stmt.executeUpdate();
                return rows > 0;
            } catch (SQLException e) {
                log.error("Failed to forget memory: key={}", key, e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Integer> count() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM memories";
            
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                log.error("Failed to count memories", e);
            }
            
            return 0;
        });
    }
    
    @Override
    public CompletableFuture<Boolean> healthCheck() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return connection != null && !connection.isClosed();
            } catch (SQLException e) {
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            if (connection != null) {
                try {
                    connection.close();
                    log.info("SQLite memory backend closed");
                } catch (SQLException e) {
                    log.error("Failed to close SQLite connection", e);
                }
            }
        });
    }
    
    private MemoryEntry mapToEntry(ResultSet rs) throws SQLException {
        return new MemoryEntry(
            rs.getString("id"),
            rs.getString("key"),
            rs.getString("content"),
            MemoryCategory.valueOf(rs.getString("category")),
            Instant.ofEpochMilli(rs.getLong("timestamp")),
            rs.getString("session_id"),
            rs.getDouble("score")
        );
    }
}
