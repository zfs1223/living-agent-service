package com.livingagent.core.config;

import com.livingagent.core.knowledge.KnowledgeBase;
import com.livingagent.core.knowledge.impl.KnowledgeManagerImpl;
import com.livingagent.core.knowledge.impl.SQLiteKnowledgeBase;
import com.livingagent.core.knowledge.professional.ProfessionalKnowledgeSeeder;
import com.livingagent.core.memory.Memory;
import com.livingagent.core.memory.MemoryBackend;
import com.livingagent.core.memory.impl.MemoryServiceImpl;
import com.livingagent.core.memory.impl.MemPalaceBackend;
import com.livingagent.core.memory.impl.MemosMemoryBackend;
import com.livingagent.core.memory.impl.SQLiteMemoryBackend;
import com.livingagent.core.runtime.DataNamespaceService;
import com.livingagent.core.runtime.RuntimeEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemoryConfig {

    private static final Logger log = LoggerFactory.getLogger(MemoryConfig.class);

    @Value("${living-agent.data.path:./data}")
    private String dataPath;

    @Value("${living-agent.memory.backend:sqlite}")
    private String memoryBackend;

    @Value("${living-agent.memory.memos.enabled:false}")
    private boolean memosEnabled;

    @Value("${living-agent.memory.memos.base-url:http://memos:8381}")
    private String memosBaseUrl;

    @Value("${living-agent.memory.memos.default-cube-id:living-agent}")
    private String memosDefaultCubeId;

    @Value("${living-agent.memory.memos.user-id:living-agent-system}")
    private String memosUserId;

    @Value("${living-agent.memory.memos.timeout:30000}")
    private int memosTimeout;

    @Value("${living-agent.memory.mempalace.enabled:false}")
    private boolean mempalaceEnabled;

    @Value("${living-agent.memory.mempalace.palace-path:./data/palace}")
    private String mempalacePath;

    @Value("${living-agent.memory.mempalace.python-command:python}")
    private String mempalacePythonCommand;

    @Value("${living-agent.memory.mempalace.timeout-ms:5000}")
    private int mempalaceTimeout;

    @Value("${living-agent.knowledge.backend:sqlite}")
    private String knowledgeBackend;

    @Bean
    public Memory memory() {
        log.info("Initializing Memory with backend: {} (mempalace={}, memos={})",
                 memoryBackend, mempalaceEnabled, memosEnabled);

        MemoryBackend backend;

        if ("mempalace".equalsIgnoreCase(memoryBackend) && mempalaceEnabled) {
            log.info("Using MemPalace memory backend: path={}, python={}", mempalacePath, mempalacePythonCommand);
            MemPalaceBackend mempalaceBackend = new MemPalaceBackend(
                mempalacePath,
                mempalacePythonCommand,
                mempalaceTimeout
            );
            mempalaceBackend.initialize().join();
            backend = mempalaceBackend;
        } else if ("memos".equalsIgnoreCase(memoryBackend) && memosEnabled) {
            log.info("Using MemOS memory backend: {}", memosBaseUrl);
            MemosMemoryBackend memosBackend = new MemosMemoryBackend(
                memosBaseUrl,
                memosDefaultCubeId,
                memosUserId,
                memosTimeout
            );
            memosBackend.initialize().join();
            backend = memosBackend;
        } else {
            log.info("Using SQLite memory backend (fallback)");
            backend = new SQLiteMemoryBackend(dataPath + "/memory.db");
            backend.initialize().join();
        }

        return new MemoryServiceImpl(backend);
    }

    @Bean
    public DataNamespaceService dataNamespaceService() {
        log.info("Initializing DataNamespaceService with baseDataDir={}", dataPath);
        return new DataNamespaceService(dataPath);
    }

    @Bean
    public RuntimeEventStore runtimeEventStore(DataNamespaceService dataNamespaceService) {
        log.info("Initializing RuntimeEventStore");
        return new RuntimeEventStore(dataNamespaceService);
    }

    @Bean
    public KnowledgeBase knowledgeBase() {
        log.info("Initializing KnowledgeBase with backend: {}", knowledgeBackend);
        return new SQLiteKnowledgeBase(dataPath + "/knowledge.db", 1536);
    }

    @Bean
    public KnowledgeManagerImpl knowledgeManager(KnowledgeBase knowledgeBase) {
        log.info("Initializing KnowledgeManager");
        return new KnowledgeManagerImpl(
            knowledgeBase,
            knowledgeBase,
            knowledgeBase,
            null
        );
    }

    @Bean
    public ProfessionalKnowledgeSeeder professionalKnowledgeSeeder(KnowledgeBase knowledgeBase) {
        log.info("Initializing ProfessionalKnowledgeSeeder");
        return new ProfessionalKnowledgeSeeder(knowledgeBase);
    }
}
