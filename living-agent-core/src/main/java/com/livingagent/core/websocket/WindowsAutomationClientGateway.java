package com.livingagent.core.websocket;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Windows 自动化客户端网关接口
 *
 * 定义后端向桌面客户端转发 Windows 自动化操作的能力。
 * core 模块仅定义接口，具体实现由 gateway 模块提供
 * （gateway 维护 clientId → WebSocketSession 映射）。
 *
 * 详细设计：docs/WINDOWS_MCP_INTEGRATION_PLAN.md §3.2、§5.1
 */
public interface WindowsAutomationClientGateway {

    /**
     * 注册客户端连接（WebSocket 建立时调用）
     *
     * @param clientId 客户端唯一标识
     * @param sessionId WebSocket session 标识
     */
    void registerClient(String clientId, String sessionId);

    /**
     * 注册客户端 WebSocket session（供 gateway 转发操作）
     *
     * @param clientId 客户端唯一标识
     * @param session  WebSocket session 对象
     */
    void registerSession(String clientId, Object session);

    /**
     * 注销客户端 WebSocket session
     *
     * @param clientId 客户端唯一标识
     */
    void unregisterSession(String clientId);

    /**
     * 注销客户端连接（WebSocket 关闭时调用）
     *
     * @param clientId 客户端唯一标识
     */
    void unregisterClient(String clientId);

    /**
     * 检查客户端是否在线
     *
     * @param clientId 客户端唯一标识
     * @return true 表示客户端 WebSocket 连接活跃
     */
    boolean isClientOnline(String clientId);

    /**
     * 向指定客户端发送 Windows 自动化操作并等待响应
     *
     * @param clientId  目标客户端
     * @param operation 操作类型（如 click、shell、registry_get）
     * @param args      操作参数
     * @return 异步响应 Future
     */
    CompletableFuture<WinAutomationResponse> sendOperation(
        String clientId, String operation, Map<String, Object> args);

    /**
     * 向指定客户端发送技能执行请求并等待响应
     * 个人助手技能采用"大脑在服务器、双手在桌面"的双层架构——服务器只转发，桌面端本地执行
     *
     * @param clientId     目标客户端
     * @param skillId      技能ID
     * @param args         技能执行参数
     * @param workspaceDir 隔离工作区目录（个人助手使用用户数据目录）
     * @return 异步响应 Future
     */
    CompletableFuture<WinAutomationResponse> sendSkillExecute(
        String clientId, String skillId, Map<String, Object> args, String workspaceDir);

    /**
     * 处理来自客户端的响应（WebSocket 收到 WIN_AUTOMATION_RESPONSE 时调用）
     *
     * @param requestId 请求 ID（与 sendOperation 内部生成的一致）
     * @param success   是否成功
     * @param result    成功时的结果数据
     * @param error     失败时的错误信息
     */
    void handleResponse(long requestId, boolean success, Object result, String error);

    /**
     * Windows 自动化操作响应
     */
    record WinAutomationResponse(boolean success, Object result, String error) {
        public static WinAutomationResponse ok(Object result) {
            return new WinAutomationResponse(true, result, null);
        }

        public static WinAutomationResponse fail(String error) {
            return new WinAutomationResponse(false, null, error);
        }
    }
}
