package com.livingagent.core.config;

import com.livingagent.core.autonomy.CodeReviewWorkflowService;
import com.livingagent.core.autonomy.ExecutionReceiptReviewer;
import com.livingagent.core.autonomy.impl.CodeArtifactMetadataBinder;
import com.livingagent.core.autonomy.impl.JpaCodeReviewWorkflowService;
import com.livingagent.core.autonomy.impl.JpaEmployeeExecutionReceiptService;
import com.livingagent.core.database.repository.ArtifactRecordRepository;
import com.livingagent.core.database.repository.CodeReviewStateRepository;
import com.livingagent.core.database.repository.EmployeeExecutionReceiptRepository;
import com.livingagent.core.database.repository.EnterpriseEmployeeRepository;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.impl.JpaEmployeeServiceImpl;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.security.ApprovalManager;
import com.livingagent.core.security.PermissionService;
import com.livingagent.core.security.impl.PermissionServiceImpl;
import com.livingagent.core.security.service.EnterpriseEmployeeService;
import com.livingagent.core.database.repository.AccessAuditLogRepository;
import com.livingagent.core.database.repository.DepartmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 核心配置类（已拆分）。
 * <p>
 * 原 54 个 @Bean 已按功能域拆分到以下配置类：
 * <ul>
 *   <li>{@link BrainConfig} — 大脑相关 Bean（MainBrain、TechBrain 等）</li>
 *   <li>{@link ToolConfig} — 工具相关 Bean（各种 Tool 实现）</li>
 *   <li>{@link ProviderConfig} — 模型 Provider 相关 Bean</li>
 *   <li>{@link MemoryConfig} — 记忆/知识库相关 Bean</li>
 *   <li>{@link ChannelConfig} — 通道/神经元相关 Bean</li>
 * </ul>
 * 本类仅保留 Employee/Security/Autonomy 等尚未归入上述分类的 Bean。
 */
@Configuration
@EnableAsync
@EnableScheduling
@Import({
    BrainConfig.class,
    ToolConfig.class,
    ProviderConfig.class,
    MemoryConfig.class,
    ChannelConfig.class
})
public class LivingAgentCoreConfig {

    private static final Logger log = LoggerFactory.getLogger(LivingAgentCoreConfig.class);

    @Bean
    public EmployeeService employeeService(NeuronRegistry neuronRegistry,
                                            EnterpriseEmployeeRepository enterpriseEmployeeRepository,
                                            DepartmentRepository departmentRepository) {
        log.info("Initializing EmployeeService with EnterpriseEmployeeEntity persistence");
        return new JpaEmployeeServiceImpl(enterpriseEmployeeRepository, departmentRepository, neuronRegistry);
    }

    @Bean
    public EnterpriseEmployeeService enterpriseEmployeeService(EnterpriseEmployeeRepository enterpriseEmployeeRepository) {
        log.info("Initializing EnterpriseEmployeeService with database persistence");
        return new EnterpriseEmployeeService(enterpriseEmployeeRepository);
    }

    @Bean
    public com.livingagent.core.security.EmployeeAuthService securityEmployeeService() {
        log.info("Initializing SecurityEmployeeService");
        return new com.livingagent.core.security.impl.AuthEmployeeServiceImpl();
    }

    @Bean
    public PermissionService permissionService(com.livingagent.core.security.EmployeeAuthService securityEmployeeService,
                                                com.livingagent.core.security.service.EnterpriseEmployeeService enterpriseEmployeeService,
                                                com.livingagent.core.database.repository.AccessAuditLogRepository auditLogRepository) {
        log.info("Initializing PermissionService");
        return new PermissionServiceImpl(securityEmployeeService, enterpriseEmployeeService, auditLogRepository);
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
    public com.livingagent.core.autonomy.ArtifactRecordService artifactRecordService(ArtifactRecordRepository artifactRecordRepository) {
        log.info("Initializing JpaArtifactRecordService");
        return new com.livingagent.core.autonomy.impl.JpaArtifactRecordService(artifactRecordRepository);
    }

    @Bean
    public CodeReviewWorkflowService codeReviewWorkflowService(CodeReviewStateRepository codeReviewStateRepository,
                                                                com.livingagent.core.autonomy.ArtifactRecordService artifactRecordService) {
        log.info("Initializing JpaCodeReviewWorkflowService");
        return new JpaCodeReviewWorkflowService(codeReviewStateRepository, artifactRecordService);
    }

    @Bean
    public CodeArtifactMetadataBinder codeArtifactMetadataBinder(com.livingagent.core.autonomy.ArtifactRecordService artifactRecordService,
                                                                 CodeReviewWorkflowService codeReviewWorkflowService) {
        log.info("Initializing CodeArtifactMetadataBinder");
        return new CodeArtifactMetadataBinder(artifactRecordService, codeReviewWorkflowService);
    }

    @Bean
    public com.livingagent.core.autonomy.EmployeeExecutionReceiptService employeeExecutionReceiptService(EmployeeExecutionReceiptRepository employeeExecutionReceiptRepository,
                                                                                                        CodeReviewWorkflowService codeReviewWorkflowService,
                                                                                                        CodeArtifactMetadataBinder codeArtifactMetadataBinder,
                                                                                                        ObjectProvider<ExecutionReceiptReviewer> executionReceiptReviewerProvider) {
        log.info("Initializing JpaEmployeeExecutionReceiptService");
        ExecutionReceiptReviewer reviewer = executionReceiptReviewerProvider.getIfAvailable();
        if (reviewer != null) {
            log.info("ExecutionReceiptReviewer available, auto-review enabled");
        } else {
            log.warn("ExecutionReceiptReviewer not available, review workflow will stay in REVIEWING until manually advanced");
        }
        return new JpaEmployeeExecutionReceiptService(employeeExecutionReceiptRepository, codeReviewWorkflowService, codeArtifactMetadataBinder, reviewer);
    }
}
