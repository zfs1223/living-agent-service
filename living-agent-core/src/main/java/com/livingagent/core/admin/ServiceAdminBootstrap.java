package com.livingagent.core.admin;

/**
 * 服务初始化入口
 * <p>主脑（MainBrain）以管理员身份在系统启动时调用，完成外部服务的初始配置。
 * <p>不通过 ReAct 循环调用，与运行时决策流程（六步决策法）完全独立。
 * <p>关联文档：docs/core/MAINBRAIN_ADMIN_BRIDGE_PLAN.md
 */
public interface ServiceAdminBootstrap {

    /**
     * 执行全部服务的初始化配置
     * <p>幂等操作：已完成的步骤会跳过，失败的步骤会重试
     *
     * @return 初始化结果
     */
    BootstrapResult bootstrapAll();

    /**
     * 执行指定服务的初始化配置
     *
     * @param serviceType 服务类型：gitlab/openproject/jenkins/memos
     * @return 初始化结果
     */
    BootstrapResult bootstrapService(String serviceType);

    /**
     * 检查指定服务的初始化状态
     *
     * @param serviceType 服务类型
     * @return true 表示已初始化完成
     */
    boolean isServiceInitialized(String serviceType);

    /**
     * 初始化结果
     */
    record BootstrapResult(
        boolean success,
        String serviceType,
        int totalSteps,
        int successSteps,
        int skippedSteps,
        int failedSteps,
        String summary
    ) {
        public static BootstrapResult success(String serviceType, int total, int success, int skipped, String summary) {
            return new BootstrapResult(true, serviceType, total, success, skipped, 0, summary);
        }

        public static BootstrapResult partial(String serviceType, int total, int success, int skipped, int failed, String summary) {
            return new BootstrapResult(false, serviceType, total, success, skipped, failed, summary);
        }
    }
}
