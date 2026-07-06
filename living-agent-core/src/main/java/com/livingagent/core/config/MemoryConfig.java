package com.livingagent.core.config;

import com.livingagent.core.knowledge.KnowledgeBase;
import com.livingagent.core.knowledge.impl.KnowledgeManagerImpl;
import com.livingagent.core.knowledge.impl.SQLiteKnowledgeBase;
import com.livingagent.core.knowledge.professional.ArchitectureKnowledgeSeeder;
import com.livingagent.core.knowledge.professional.ProfessionalKnowledgeSeeder;
import com.livingagent.core.knowledge.professional.SourceTreeIndexer;
import com.livingagent.core.evolution.escalation.EscalationNotificationService;
import com.livingagent.core.evolution.codemapper.ErrorCodeMapper;
import com.livingagent.core.evolution.codebase.CodebaseAccessConfig;
import com.livingagent.core.evolution.codebase.CodebaseAccessService;
import com.livingagent.core.evolution.patch.PatchProposalService;
import com.livingagent.core.evolution.patch.PatchApplicationService;
import com.livingagent.core.memory.Memory;
import com.livingagent.core.memory.MemoryBackend;
import com.livingagent.core.memory.impl.MemoryServiceImpl;
import com.livingagent.core.memory.impl.MemPalaceBackend;
import com.livingagent.core.memory.impl.MemosMemoryBackend;
import com.livingagent.core.memory.impl.SQLiteMemoryBackend;
import com.livingagent.core.runtime.DataNamespaceService;
import com.livingagent.core.runtime.EvolutionNamespaceService;
import com.livingagent.core.runtime.RuntimeEventStore;
import com.livingagent.core.database.repository.RuntimeEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
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

    @Value("${living-agent.living-dir:./.living}")
    private String livingDir;

    @Value("${living-agent.architecture-knowledge.enabled:true}")
    private boolean architectureKnowledgeEnabled;

    @Value("${living-agent.architecture-knowledge.docs-path:./docs}")
    private String docsPath;

    @Value("${living-agent.architecture-knowledge.documents-path:./documents}")
    private String documentsPath;

    @Value("${living-agent.architecture-knowledge.chunk-size:2000}")
    private int architectureChunkSize;

    @Value("${living-agent.professional-knowledge.enabled:true}")
    private boolean professionalKnowledgeEnabled;

    @Value("${living-agent.professional-knowledge.agency-agents-path:./data/agency-agents}")
    private String agencyAgentsPath;

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
    public RuntimeEventStore runtimeEventStore(RuntimeEventRepository eventRepository,
                                                DataNamespaceService dataNamespaceService) {
        log.info("Initializing RuntimeEventStore with DB persistence");
        return new RuntimeEventStore(eventRepository, dataNamespaceService);
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

    @Bean
    public ArchitectureKnowledgeSeeder architectureKnowledgeSeeder(KnowledgeBase knowledgeBase) {
        log.info("Initializing ArchitectureKnowledgeSeeder with chunkSize={}", architectureChunkSize);
        return new ArchitectureKnowledgeSeeder(knowledgeBase, architectureChunkSize);
    }

    @Bean
    public SourceTreeIndexer sourceTreeIndexer() {
        log.info("Initializing SourceTreeIndexer");
        return new SourceTreeIndexer();
    }

    @Bean
    public EvolutionNamespaceService evolutionNamespaceService() {
        log.info("Initializing EvolutionNamespaceService with baseLivingDir={}", livingDir);
        return new EvolutionNamespaceService(livingDir);
    }

    @Bean
    public EscalationNotificationService escalationNotificationService(EvolutionNamespaceService ens) {
        log.info("Initializing EscalationNotificationService");
        return new EscalationNotificationService(ens);
    }

    @Bean
    public ErrorCodeMapper errorCodeMapper() {
        log.info("Initializing ErrorCodeMapper");
        return new ErrorCodeMapper();
    }

    @Bean
    public CodebaseAccessConfig codebaseAccessConfig() {
        CodebaseAccessConfig config = new CodebaseAccessConfig();
        config.setProjectRoot(".");
        config.getMountPoints().put("docs", docsPath);
        config.getMountPoints().put("documents", documentsPath);
        return config;
    }

    @Bean
    public CodebaseAccessService codebaseAccessService(EvolutionNamespaceService ens, CodebaseAccessConfig config) {
        log.info("Initializing CodebaseAccessService");
        return new CodebaseAccessService(ens, config);
    }

    @Bean
    public PatchProposalService patchProposalService(EvolutionNamespaceService ens) {
        log.info("Initializing PatchProposalService");
        return new PatchProposalService(ens);
    }

    @Bean
    public PatchApplicationService patchApplicationService(
            EvolutionNamespaceService ens,
            PatchProposalService pps,
            EscalationNotificationService ens2) {
        log.info("Initializing PatchApplicationService");
        return new PatchApplicationService(ens, pps, ens2);
    }

    /**
     * 启动时执行知识播种（通过 ApplicationRunner 确保所有 Bean 已就绪）
     * 1. 专业知识播种（ProfessionalKnowledgeSeeder）
     * 2. 架构文档知识播种（ArchitectureKnowledgeSeeder）
     * 3. 源码结构索引生成（SourceTreeIndexer）
     */
    @Bean
    public ApplicationRunner knowledgeSeedingRunner(
            KnowledgeBase knowledgeBase,
            ProfessionalKnowledgeSeeder professionalSeeder,
            ArchitectureKnowledgeSeeder architectureSeeder,
            SourceTreeIndexer indexer,
            EvolutionNamespaceService evolutionNamespaceService) {
        return args -> {
            log.info("=== 开始知识播种 ===");

            // 1. 专业知识播种
            if (professionalKnowledgeEnabled) {
                try {
                    int count = professionalSeeder.seedFromDirectory(java.nio.file.Paths.get(agencyAgentsPath));
                    log.info("专业知识播种完成: {} 条", count);
                } catch (Exception e) {
                    log.warn("专业知识播种失败（非致命）: {}", e.getMessage());
                }
            }

            // 2. 架构文档知识播种
            if (architectureKnowledgeEnabled) {
                try {
                    int docsCount = architectureSeeder.seedFromDocsDirectory(java.nio.file.Paths.get(docsPath));
                    int documentsCount = architectureSeeder.seedFromDocumentsDirectory(java.nio.file.Paths.get(documentsPath));
                    log.info("架构文档知识播种完成: docs={} 条, documents={} 条", docsCount, documentsCount);
                } catch (Exception e) {
                    log.warn("架构文档知识播种失败（非致命）: {}", e.getMessage());
                }
            }

            // 3. 源码结构索引生成
            try {
                java.nio.file.Path outputPath = java.nio.file.Paths.get(evolutionNamespaceService.getCodebaseSourceTreePath());
                int modules = indexer.generateIndex(java.nio.file.Paths.get("."), outputPath);
                log.info("源码结构索引生成完成: {} 个模块", modules);
            } catch (Exception e) {
                log.warn("源码结构索引生成失败（非致命）: {}", e.getMessage());
            }

            log.info("=== 知识播种结束 ===");
        };
    }
}
