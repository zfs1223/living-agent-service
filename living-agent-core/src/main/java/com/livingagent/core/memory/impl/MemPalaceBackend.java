package com.livingagent.core.memory.impl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.memory.MemoryBackend;
import com.livingagent.core.memory.MemoryCategory;
import com.livingagent.core.memory.MemoryEntry;

public class MemPalaceBackend implements MemoryBackend {

    private static final Logger log = LoggerFactory.getLogger(MemPalaceBackend.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicInteger REQ_ID = new AtomicInteger(0);

    private static final String HEADER_CONTENT_LENGTH = "content-length";

    private final String palacePath;
    private final String pythonCommand;
    private final int timeoutMs;

    private volatile boolean initialized = false;
    private Process mcpProcess;
    private OutputStream processStdin;
    private PushbackInputStream processStdout;
    private Thread readerThread;
    private final Map<Integer, CompletableFuture<JsonNode>> pendingRequests = new java.util.concurrent.ConcurrentHashMap<>();

    public MemPalaceBackend(String palacePath, String pythonCommand, int timeoutMs) {
        this.palacePath = palacePath;
        this.pythonCommand = pythonCommand;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String name() {
        return "mempalace";
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Initializing MemPalace backend: path={}, python={}", palacePath, pythonCommand);

                startMcpProcess();
                sendMcpInitialize();

                boolean healthy = healthCheck().join();
                if (healthy) {
                    initialized = true;
                    log.info("MemPalace memory backend initialized successfully");
                } else {
                    log.error("MemPalace health check failed after initialization");
                    initialized = false;
                }
            } catch (Exception e) {
                log.error("Failed to initialize MemPalace backend", e);
                initialized = false;
            }
        });
    }

    private void startMcpProcess() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
            pythonCommand, "-m", "mempalace.mcp_server"
        );

        pb.environment().put("MEMPALACE_PALACE_PATH", palacePath);
        pb.redirectErrorStream(false);

        mcpProcess = pb.start();

        processStdin = mcpProcess.getOutputStream();
        processStdout = new PushbackInputStream(mcpProcess.getInputStream(), 8192);

        readerThread = new Thread(this::readResponses, "mempalace-mcp-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        Thread stderrThread = new Thread(this::readStderr, "mempalace-mcp-stderr");
        stderrThread.setDaemon(true);
        stderrThread.start();

        log.info("MemPalace MCP process started (pid={})", mcpProcess.pid());
    }

    private void sendMcpInitialize() throws IOException {
        Map<String, Object> initRequest = new LinkedHashMap<>();
        initRequest.put("jsonrpc", "2.0");
        initRequest.put("id", REQ_ID.incrementAndGet());
        initRequest.put("method", "initialize");
        initRequest.put("params", Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities", Map.of(),
            "clientInfo", Map.of("name", "living-agent-service", "version", "1.0.0")
        ));

        sendJsonRpcMessage(initRequest);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Map<String, Object> initializedNotif = new LinkedHashMap<>();
        initializedNotif.put("jsonrpc", "2.0");
        initializedNotif.put("method", "notifications/initialized");

        sendJsonRpcMessage(initializedNotif);
    }

    private void sendJsonRpcMessage(Map<String, Object> payload) throws IOException {
        String json = MAPPER.writeValueAsString(payload);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String headers = "Content-Length: " + body.length + "\r\n\r\n";

        synchronized (processStdin) {
            processStdin.write(headers.getBytes(StandardCharsets.US_ASCII));
            processStdin.write(body);
            processStdin.flush();
        }
    }

    private void readResponses() {
        try {
            while (true) {
                JsonNode response = readNextMessage();
                if (response == null) {
                    break;
                }

                JsonNode idNode = response.get("id");
                if (idNode != null && idNode.isInt()) {
                    int reqId = idNode.asInt();
                    CompletableFuture<JsonNode> future = pendingRequests.remove(reqId);
                    if (future != null) {
                        future.complete(response.get("result"));
                    }
                }
            }
        } catch (Exception e) {
            if (initialized) {
                log.error("MCP reader thread error", e);
            }
        } finally {
            initialized = false;
        }
    }

    private JsonNode readNextMessage() throws IOException {
        int first = skipIgnorableBytes(processStdout);
        if (first == -1) {
            return null;
        }

        // 兼容旧行分隔 JSON（历史实现）
        if (first == '{' || first == '[') {
            String lineJson = readJsonLine(first);
            if (lineJson == null || lineJson.isBlank()) {
                return null;
            }
            try {
                return MAPPER.readTree(lineJson);
            } catch (Exception e) {
                log.warn("Failed to parse line-delimited MCP response: {}", lineJson, e);
                return null;
            }
        }

        processStdout.unread(first);
        Map<String, String> headers = readHeaders(processStdout);
        if (headers.isEmpty()) {
            return null;
        }

        int contentLength = parseContentLength(headers);
        if (contentLength <= 0) {
            log.warn("Invalid Content-Length in MCP response headers: {}", headers);
            return null;
        }

        byte[] body = readExactBytes(processStdout, contentLength);
        if (body == null) {
            return null;
        }

        String json = new String(body, StandardCharsets.UTF_8);
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to parse framed MCP response: {}", json, e);
            return null;
        }
    }

    private int skipIgnorableBytes(InputStream in) throws IOException {
        int b;
        while ((b = in.read()) != -1) {
            if (!Character.isWhitespace((char) b)) {
                return b;
            }
        }
        return -1;
    }

    private String readJsonLine(int firstByte) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(firstByte);

        int b;
        while ((b = processStdout.read()) != -1) {
            if (b == '\n') {
                break;
            }
            bos.write(b);
        }

        if (b == -1 && bos.size() == 0) {
            return null;
        }

        String raw = bos.toString(StandardCharsets.UTF_8);
        return raw.trim();
    }

    private Map<String, String> readHeaders(InputStream in) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();

        while (true) {
            String line = readAsciiLine(in);
            if (line == null) {
                return Map.of();
            }

            if (line.isBlank()) {
                break;
            }

            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }

            String key = line.substring(0, idx).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(idx + 1).trim();
            headers.put(key, value);
        }

        return headers;
    }

    private String readAsciiLine(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                bos.write(b);
            }
        }

        if (b == -1 && bos.size() == 0) {
            return null;
        }

        return bos.toString(StandardCharsets.US_ASCII);
    }

    private int parseContentLength(Map<String, String> headers) {
        String raw = headers.get(HEADER_CONTENT_LENGTH);
        if (raw == null) {
            return -1;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private byte[] readExactBytes(InputStream in, int length) throws IOException {
        byte[] body = new byte[length];
        int offset = 0;

        while (offset < length) {
            int read = in.read(body, offset, length - offset);
            if (read == -1) {
                return null;
            }
            offset += read;
        }

        return body;
    }

    private void readStderr() {
        try (BufferedReader errReader = new BufferedReader(new InputStreamReader(mcpProcess.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = errReader.readLine()) != null) {
                log.debug("[MemPalace MCP] {}", line);
            }
        } catch (IOException e) {
            // ignore
        }
    }

    private CompletableFuture<JsonNode> callMcpTool(String toolName, Map<String, Object> arguments) {
        if (!initialized && !"mempalace_status".equals(toolName)) {
            return CompletableFuture.failedFuture(new IllegalStateException("MemPalace not initialized"));
        }

        int reqId = REQ_ID.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(reqId, future);

        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", reqId);
            request.put("method", "tools/call");
            request.put("params", Map.of(
                "name", toolName,
                "arguments", arguments
            ));

            sendJsonRpcMessage(request);
        } catch (IOException e) {
            pendingRequests.remove(reqId);
            future.completeExceptionally(e);
        }

        return future.orTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public CompletableFuture<Void> store(String key, String content, MemoryCategory category, String sessionId) {
        return CompletableFuture.runAsync(() -> {
            if (!initialized) {
                log.warn("MemPalace not initialized, skipping store");
                return;
            }

            try {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("wing", determineWing(sessionId));
                params.put("room", mapCategoryToRoom(category));
                params.put("content", content);
                params.put("added_by", "living-agent");
                params.put("source_file", key);

                callMcpTool("mempalace_add_drawer", params).join();
                log.debug("Stored memory in MemPalace: key={}, category={}", key, category);
            } catch (Exception e) {
                log.error("Failed to store memory in MemPalace: key={}", key, e);
            }
        });
    }

    @Override
    public CompletableFuture<List<MemoryEntry>> recall(String query, int limit, String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                return Collections.emptyList();
            }

            try {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("query", query);
                params.put("limit", limit);

                if (sessionId != null) {
                    params.put("wing", determineWing(sessionId));
                }

                JsonNode result = callMcpTool("mempalace_search", params).join();
                return parseSearchResult(result, limit);
            } catch (Exception e) {
                log.error("Failed to recall memories from MemPalace: query={}", query, e);
                return Collections.emptyList();
            }
        });
    }

    @Override
    public CompletableFuture<Optional<MemoryEntry>> get(String key) {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                return Optional.empty();
            }
            try {
                List<MemoryEntry> results = recall(key, 1, null).join();
                return results.stream().findFirst();
            } catch (Exception e) {
                log.error("Failed to get memory from MemPalace: key={}", key, e);
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<List<MemoryEntry>> list(MemoryCategory category, String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                return Collections.emptyList();
            }

            try {
                Map<String, Object> params = new LinkedHashMap<>();
                if (sessionId != null) {
                    params.put("wing", determineWing(sessionId));
                }

                JsonNode result = callMcpTool("mempalace_list_rooms", params).join();
                return parseListResult(result, category);
            } catch (Exception e) {
                log.error("Failed to list memories from MemPalace: category={}", category, e);
                return Collections.emptyList();
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> forget(String key) {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                return false;
            }
            try {
                Optional<MemoryEntry> entry = get(key).join();
                if (entry.isEmpty()) {
                    return false;
                }

                Map<String, Object> params = Map.of("drawer_id", entry.get().id());
                callMcpTool("mempalace_delete_drawer", params).join();
                log.debug("Forgot memory from MemPalace: key={}", key);
                return true;
            } catch (Exception e) {
                log.error("Failed to forget memory from MemPalace: key={}", key, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Integer> count() {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                return 0;
            }
            try {
                JsonNode result = callMcpTool("mempalace_status", Map.of()).join();
                return extractCountFromStatus(result);
            } catch (Exception e) {
                log.error("Failed to count memories in MemPalace", e);
                return 0;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> healthCheck() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (mcpProcess == null || !mcpProcess.isAlive()) {
                    return false;
                }
                JsonNode result = callMcpTool("mempalace_status", Map.of()).join();
                return result != null && result.has("total_drawers");
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            initialized = false;

            try {
                if (processStdin != null) {
                    processStdin.close();
                }
            } catch (IOException e) { /* ignore */ }

            try {
                if (processStdout != null) {
                    processStdout.close();
                }
            } catch (IOException e) { /* ignore */ }

            if (mcpProcess != null && mcpProcess.isAlive()) {
                mcpProcess.destroyForcibly();
                log.info("MemPalace MCP process forcibly destroyed");
            }

            if (readerThread != null && readerThread.isAlive()) {
                readerThread.interrupt();
            }

            pendingRequests.clear();
            log.info("MemPalace memory backend closed");
        });
    }

    @jakarta.annotation.PreDestroy
    public void destroy() {
        close().join();
    }

    private List<MemoryEntry> parseSearchResult(JsonNode result, int limit) {
        if (result == null) return Collections.emptyList();

        List<MemoryEntry> entries = new ArrayList<>();

        JsonNode contentArray = result.get("content");
        if (contentArray != null && contentArray.isArray()) {
            for (JsonNode contentItem : contentArray) {
                String text = contentItem.get("text").asText();
                try {
                    JsonNode searchData = MAPPER.readTree(text);
                    JsonNode results = searchData.get("results");
                    if (results != null && results.isArray()) {
                        for (JsonNode item : results) {
                            entries.add(parseMemoryEntry(item));
                            if (entries.size() >= limit) break;
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse search result item", e);
                }
            }
        }

        return entries;
    }

    private List<MemoryEntry> parseListResult(JsonNode result, MemoryCategory category) {
        if (result == null) return Collections.emptyList();
        return new ArrayList<>();
    }

    private MemoryEntry parseMemoryEntry(JsonNode item) {
        String id = item.has("id") ? item.get("id").asText() : UUID.randomUUID().toString();
        String content = item.has("document") ? item.get("document").asText() : "";
        double score = item.has("distance") ? 1.0 - item.get("distance").asDouble() : 1.0;

        JsonNode meta = item.get("metadata");
        String key = (meta != null && meta.has("source_file")) ? meta.get("source_file").asText() : id;
        String categoryStr = (meta != null && meta.has("room")) ? meta.get("room").asText() : "CUSTOM";
        String timestampStr = (meta != null && meta.has("filed_at")) ? meta.get("filed_at").asText() : Instant.now().toString();
        String sessionId = (meta != null && meta.has("wing")) ? meta.get("wing").asText() : null;

        MemoryCategory cat;
        try {
            cat = MemoryCategory.valueOf(categoryStr.toUpperCase());
        } catch (Exception e) {
            cat = MemoryCategory.CUSTOM;
        }

        Instant timestamp;
        try {
            timestamp = Instant.parse(timestampStr);
        } catch (Exception e) {
            timestamp = Instant.now();
        }

        return new MemoryEntry(id, key, content, cat, timestamp, sessionId, score);
    }

    private int extractCountFromStatus(JsonNode result) {
        if (result == null) return 0;

        JsonNode contentArray = result.get("content");
        if (contentArray != null && contentArray.isArray() && !contentArray.isEmpty()) {
            String text = contentArray.get(0).get("text").asText();
            try {
                JsonNode statusData = MAPPER.readTree(text);
                if (statusData.has("total_drawers")) {
                    return statusData.get("total_drawers").asInt();
                }
            } catch (Exception e) {
                log.debug("Failed to parse status result", e);
            }
        }
        return 0;
    }

    private String determineWing(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return "wing_general";
        }
        if (sessionId.contains("tech") || sessionId.contains("department/tech")) {
            return "wing_tech";
        } else if (sessionId.contains("hr") || sessionId.contains("department/hr")) {
            return "wing_hr";
        } else if (sessionId.contains("finance") || sessionId.contains("department/finance")) {
            return "wing_finance";
        }
        return "wing_general";
    }

    private String mapCategoryToRoom(MemoryCategory category) {
        if (category == null) {
            return "hall_events";
        }
        return switch (category) {
            case CORE -> "hall_facts";
            case DAILY -> "hall_preferences";
            case CONVERSATION -> "hall_knowledge";
            case CUSTOM -> "hall_facts";
            default -> "hall_events";
        };
    }

    public boolean isInitialized() {
        return initialized;
    }
}
