package com.livingagent.core.config;

import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.impl.*;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.impl.ChannelManagerImpl;
import com.livingagent.core.evolution.engine.DefaultEvolutionDecisionEngine;
import com.livingagent.core.evolution.engine.EvolutionDecisionEngine;
import com.livingagent.core.evolution.memory.EvolutionMemoryGraph;
import com.livingagent.core.evolution.circuitbreaker.EvolutionCircuitBreaker;
import com.livingagent.core.knowledge.KnowledgeBase;
import com.livingagent.core.knowledge.impl.KnowledgeManagerImpl;
import com.livingagent.core.knowledge.impl.SQLiteKnowledgeBase;
import com.livingagent.core.memory.Memory;
import com.livingagent.core.memory.MemoryBackend;
import com.livingagent.core.memory.impl.MemoryServiceImpl;
import com.livingagent.core.memory.impl.MemosMemoryBackend;
import com.livingagent.core.memory.impl.SQLiteMemoryBackend;
import com.livingagent.core.model.ModelManager;
import com.livingagent.core.model.impl.ModelManagerImpl;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.neuron.impl.NeuronRegistryImpl;
import com.livingagent.core.neuron.chat.ChatNeuronRouter;
import com.livingagent.core.neuron.chat.ChatNeuronConfig;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.provider.ProviderRegistry;
import com.livingagent.core.provider.impl.AsrProvider;
import com.livingagent.core.provider.impl.BitNetProvider;
import com.livingagent.core.provider.impl.ProviderRegistryImpl;
import com.livingagent.core.provider.impl.QwenProvider;
import com.livingagent.core.provider.impl.TtsProvider;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.impl.EmployeeServiceImpl;
import com.livingagent.core.employee.impl.JpaEmployeeServiceImpl;
import com.livingagent.core.employee.repository.EmployeeRepository;
import com.livingagent.core.security.PermissionService;
import com.livingagent.core.security.impl.PermissionServiceImpl;
import com.livingagent.core.security.service.EnterpriseEmployeeService;
import com.livingagent.core.database.repository.EnterpriseEmployeeRepository;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.tool.impl.BrowserAutomationTool;
import com.livingagent.core.tool.impl.BudgetManagementTool;
import com.livingagent.core.tool.impl.DockerTool;
import com.livingagent.core.tool.impl.GitHubTool;
import com.livingagent.core.tool.impl.HuggingFaceTool;
import com.livingagent.core.tool.impl.InvoiceProcessingTool;
import com.livingagent.core.tool.impl.KnowledgeGraphTool;
import com.livingagent.core.tool.impl.NotionTool;
import com.livingagent.core.tool.impl.OfficeTool;
import com.livingagent.core.tool.impl.PdfTool;
import com.livingagent.core.tool.impl.PlaywrightCrawlerTool;
import com.livingagent.core.tool.impl.ProactiveAgentTool;
import com.livingagent.core.tool.impl.RssReaderTool;
import com.livingagent.core.tool.impl.SearXNGTool;
import com.livingagent.core.tool.impl.SelfImprovingTool;
import com.livingagent.core.tool.impl.SlackTool;
import com.livingagent.core.tool.impl.SkillFinderTool;
import com.livingagent.core.tool.impl.SummarizeTool;
import com.livingagent.core.tool.impl.TavilySearchTool;
import com.livingagent.core.tool.impl.ToolRegistryImpl;
import com.livingagent.core.tool.impl.WeatherTool;
import com.livingagent.core.skill.SkillRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableAsync
@EnableScheduling
public class LivingAgentCoreConfig {

    private static final Logger log = LoggerFactory.getLogger(LivingAgentCoreConfig.class);

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

    @Value("${living-agent.knowledge.backend:sqlite}")
    private String knowledgeBackend;

    @Bean
    public NeuronRegistry neuronRegistry() {
        log.info("Initializing NeuronRegistry");
        return new NeuronRegistryImpl();
    }

    @Bean
    public BrainRegistryImpl brainRegistry() {
        log.info("Initializing BrainRegistry");
        return new BrainRegistryImpl();
    }

    @Bean
    public ToolRegistry toolRegistry(SkillRegistry skillRegistry,
                                     @Value("${tavily.api.key:}") String tavilyApiKey,
                                     @Value("${notion.api.key:}") String notionApiKey,
                                     @Value("${slack.bot.token:}") String slackBotToken,
                                     @Value("${summarize.api.key:}") String summarizeApiKey,
                                     @Value("${weather.qweather.key:}") String qweatherKey,
                                     @Value("${weather.openweathermap.key:}") String openweathermapKey) {
        log.info("Initializing ToolRegistry");
        ToolRegistryImpl registry = new ToolRegistryImpl();
        
        // ========== 核心工具 (每个神经元必备，完全免费) ==========
        try {
            registry.register(new PlaywrightCrawlerTool());
            log.info("Registered core tool: playwright_crawler");
        } catch (Exception e) {
            log.warn("PlaywrightCrawlerTool not available: {}. Skipping registration.", e.getMessage());
        }
        registry.register(new RssReaderTool());
        registry.register(new ProactiveAgentTool());
        registry.register(new SkillFinderTool(skillRegistry));
        log.info("Registered core tools: rss_reader, proactive_agent, find_skills");
        
        // ========== 免费搜索工具 (替代付费Tavily) ==========
        registry.register(new SearXNGTool());
        log.info("Registered free search tool: searxng");
        
        // 付费搜索工具 (可选)
        if (tavilyApiKey != null && !tavilyApiKey.isEmpty()) {
            registry.register(new TavilySearchTool(tavilyApiKey));
            log.info("Registered premium search tool: tavily_search");
        }
        
        // ========== 免费本地工具 ==========
        registry.register(new WeatherTool()
                .withQWeather(qweatherKey)
                .withOpenWeatherMap(openweathermapKey));
        log.info("Registered weather tool (free tier available)");
        
        registry.register(new GitHubTool());
        log.info("Registered github tool (requires gh CLI)");
        
        registry.register(new DockerTool());
        log.info("Registered docker tool (requires Docker CLI)");
        
        registry.register(new HuggingFaceTool());
        log.info("Registered huggingface tool (requires hf CLI)");
        
        registry.register(new PdfTool());
        log.info("Registered pdf tool (requires Python pypdf/pdfplumber)");
        
        registry.register(new OfficeTool());
        log.info("Registered office tool (requires Python python-docx/openpyxl/python-pptx)");
        
        // ========== 云服务工具 (需API Key) ==========
        if (notionApiKey != null && !notionApiKey.isEmpty()) {
            registry.register(new NotionTool(notionApiKey));
            log.info("Registered notion tool");
        } else {
            registry.register(new NotionTool());
            log.info("Registered notion tool (no API key configured)");
        }
        
        if (slackBotToken != null && !slackBotToken.isEmpty()) {
            registry.register(new SlackTool(slackBotToken));
            log.info("Registered slack tool");
        } else {
            registry.register(new SlackTool());
            log.info("Registered slack tool (no token configured)");
        }
        
        if (summarizeApiKey != null && !summarizeApiKey.isEmpty()) {
            registry.register(new SummarizeTool(summarizeApiKey));
            log.info("Registered summarize tool");
        } else {
            registry.register(new SummarizeTool());
            log.info("Registered summarize tool (no API key configured)");
        }
        
        // ========== 核心技能工具 ==========
        registry.register(new KnowledgeGraphTool());
        log.info("Registered knowledge_graph tool");
        
        registry.register(new SelfImprovingTool());
        log.info("Registered self_improving tool");
        
        // ========== 财务部工具 ==========
        registry.register(new BudgetManagementTool());
        log.info("Registered budget_management tool");
        
        registry.register(new InvoiceProcessingTool());
        log.info("Registered invoice_processing tool");
        
        // ========== 技术部工具 ==========
        registry.register(new BrowserAutomationTool());
        log.info("Registered browser_automation tool");
        
        log.info("ToolRegistry initialized with {} tools", registry.getAll().size());
        return registry;
    }

    @Bean
    public ChannelManager channelManager(NeuronRegistry neuronRegistry) {
        log.info("Initializing ChannelManager");
        ChannelManagerImpl manager = new ChannelManagerImpl();
        manager.setNeuronRegistry(neuronRegistry);
        return manager;
    }

    @Bean
    public Memory memory() {
        log.info("Initializing Memory with backend: {}", memoryBackend);
        
        MemoryBackend backend;
        
        if ("memos".equalsIgnoreCase(memoryBackend) && memosEnabled) {
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
    public ProviderRegistry providerRegistry() {
        log.info("Initializing ProviderRegistry");
        return new ProviderRegistryImpl();
    }

    @Bean
    public ModelManager modelManager() {
        log.info("Initializing ModelManager");
        return new ModelManagerImpl();
    }

    @Bean
    public QwenProvider qwenProvider(ModelManager modelManager, ProviderRegistry providerRegistry) {
        log.info("Initializing QwenProvider");
        QwenProvider provider = new QwenProvider(modelManager);
        providerRegistry.register(provider);
        return provider;
    }

    @Bean
    public BitNetProvider bitNetProvider(ModelManager modelManager, ProviderRegistry providerRegistry) {
        log.info("Initializing BitNetProvider");
        BitNetProvider provider = new BitNetProvider(modelManager);
        providerRegistry.register(provider);
        return provider;
    }

    @Bean
    public AsrProvider asrProvider(ModelManager modelManager, ProviderRegistry providerRegistry) {
        log.info("Initializing AsrProvider");
        AsrProvider provider = new AsrProvider(modelManager);
        providerRegistry.register(provider);
        return provider;
    }

    @Bean
    public TtsProvider ttsProvider(ModelManager modelManager, ProviderRegistry providerRegistry) {
        log.info("Initializing TtsProvider");
        TtsProvider provider = new TtsProvider(modelManager);
        providerRegistry.register(provider);
        return provider;
    }

    @Bean
    public EvolutionMemoryGraph evolutionMemoryGraph() {
        log.info("Initializing EvolutionMemoryGraph");
        return new com.livingagent.core.evolution.memory.impl.InMemoryEvolutionMemoryGraph();
    }

    @Bean
    public EvolutionCircuitBreaker evolutionCircuitBreaker(EvolutionMemoryGraph memoryGraph) {
        log.info("Initializing EvolutionCircuitBreaker");
        return new EvolutionCircuitBreaker(memoryGraph);
    }

    @Bean
    public EvolutionDecisionEngine evolutionDecisionEngine(
            EvolutionMemoryGraph memoryGraph,
            EvolutionCircuitBreaker circuitBreaker) {
        log.info("Initializing EvolutionDecisionEngine");
        return new DefaultEvolutionDecisionEngine(memoryGraph, circuitBreaker);
    }

    @Bean
    public EmployeeService employeeService(NeuronRegistry neuronRegistry, EmployeeRepository employeeRepository) {
        log.info("Initializing EmployeeService with JPA persistence");
        return new JpaEmployeeServiceImpl(employeeRepository, neuronRegistry);
    }

    @Bean
    public EnterpriseEmployeeService enterpriseEmployeeService(EnterpriseEmployeeRepository enterpriseEmployeeRepository) {
        log.info("Initializing EnterpriseEmployeeService with database persistence");
        return new EnterpriseEmployeeService(enterpriseEmployeeRepository);
    }

    @Bean
    public com.livingagent.core.security.EmployeeAuthService securityEmployeeService() {
        log.info("Initializing SecurityEmployeeService");
        return new com.livingagent.core.security.impl.EmployeeServiceImpl();
    }

    @Bean
    public PermissionService permissionService(com.livingagent.core.security.EmployeeAuthService securityEmployeeService) {
        log.info("Initializing PermissionService");
        return new PermissionServiceImpl(securityEmployeeService);
    }

    @Bean
    public com.livingagent.core.security.voiceprint.VoicePrintService voicePrintService() {
        log.info("Initializing VoicePrintService");
        return new com.livingagent.core.security.voiceprint.impl.VoicePrintServiceImpl(null);
    }

    @Bean
    public com.livingagent.core.security.auth.PhoneVerificationService phoneVerificationService() {
        log.info("Initializing PhoneVerificationService");
        return new com.livingagent.core.security.auth.PhoneVerificationService();
    }

    @Bean
    public MainBrain mainBrain(
            ToolRegistry toolRegistry,
            BrainRegistryImpl brainRegistry,
            PermissionService permissionService) {
        log.info("Initializing MainBrain");
        List<Tool> tools = new ArrayList<>(toolRegistry.getAll());
        return new MainBrain(tools, brainRegistry, permissionService);
    }

    @Bean
    public TechBrain techBrain(ToolRegistry toolRegistry) {
        log.info("Initializing TechBrain");
        List<Tool> tools = new ArrayList<>(toolRegistry.getAll());
        return new TechBrain(tools);
    }

    @Bean
    public HrBrain hrBrain(ToolRegistry toolRegistry) {
        log.info("Initializing HrBrain");
        List<Tool> tools = new ArrayList<>(toolRegistry.getAll());
        return new HrBrain(tools);
    }

    @Bean
    public FinanceBrain financeBrain(ToolRegistry toolRegistry) {
        log.info("Initializing FinanceBrain");
        List<Tool> tools = new ArrayList<>(toolRegistry.getAll());
        return new FinanceBrain(tools);
    }

    @Bean
    public SalesBrain salesBrain(ToolRegistry toolRegistry) {
        log.info("Initializing SalesBrain");
        List<Tool> tools = new ArrayList<>(toolRegistry.getAll());
        return new SalesBrain(tools);
    }

    @Bean
    public CsBrain csBrain(ToolRegistry toolRegistry) {
        log.info("Initializing CsBrain");
        List<Tool> tools = new ArrayList<>(toolRegistry.getAll());
        return new CsBrain(tools);
    }

    @Bean
    public AdminBrain adminBrain(ToolRegistry toolRegistry) {
        log.info("Initializing AdminBrain");
        List<Tool> tools = new ArrayList<>(toolRegistry.getAll());
        return new AdminBrain(tools);
    }

    @Bean
    public LegalBrain legalBrain(ToolRegistry toolRegistry) {
        log.info("Initializing LegalBrain");
        List<Tool> tools = new ArrayList<>(toolRegistry.getAll());
        return new LegalBrain(tools);
    }

    @Bean
    public OpsBrain opsBrain(ToolRegistry toolRegistry) {
        log.info("Initializing OpsBrain");
        List<Tool> tools = new ArrayList<>(toolRegistry.getAll());
        return new OpsBrain(tools);
    }

    @Bean
    public LivingAgentInitializer livingAgentInitializer(
            BrainRegistryImpl brainRegistry,
            NeuronRegistry neuronRegistry,
            ChannelManager channelManager,
            List<Brain> brains) {
        log.info("Initializing LivingAgentInitializer");
        return new LivingAgentInitializer(brainRegistry, neuronRegistry, channelManager, brains);
    }

    public static class LivingAgentInitializer {
        
        private static final Logger initLog = LoggerFactory.getLogger(LivingAgentInitializer.class);
        
        private final BrainRegistryImpl brainRegistry;
        private final NeuronRegistry neuronRegistry;
        private final ChannelManager channelManager;
        private final List<Brain> brains;

        public LivingAgentInitializer(
                BrainRegistryImpl brainRegistry,
                NeuronRegistry neuronRegistry,
                ChannelManager channelManager,
                List<Brain> brains) {
            this.brainRegistry = brainRegistry;
            this.neuronRegistry = neuronRegistry;
            this.channelManager = channelManager;
            this.brains = brains;
        }

        public void initialize() {
            initLog.info("Starting LivingAgent initialization...");

            for (Brain brain : brains) {
                brainRegistry.register(brain);
                initLog.info("Registered brain: {}", brain.getName());
            }

            brainRegistry.startAll();
            initLog.info("All brains started. Total brains: {}", brainRegistry.count());
        }

        public void shutdown() {
            initLog.info("Shutting down LivingAgent...");
            brainRegistry.stopAll();
            initLog.info("LivingAgent shutdown completed");
        }
    }

    
    @Bean
    public ChatNeuronRouter chatNeuronRouter(NeuronRegistry neuronRegistry) {
        log.info("Initializing ChatNeuronRouter");
        ChatNeuronConfig config = ChatNeuronConfig.defaultConfig();
        return new ChatNeuronRouter(neuronRegistry, config);
    }
}
