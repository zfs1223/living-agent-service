package com.livingagent.core.brain;

import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.provider.Provider;
import com.livingagent.core.tool.Tool;

import java.util.List;

public interface Brain {

    String getId();

    String getName();

    String getDepartment();

    BrainState getState();

    void start(BrainContext context);

    void stop();

    void process(ChannelMessage message);

    /**
     * 处理消息并返回结构化输出契约（可选实现）
     * 默认实现返回 null，表示大脑仍使用 ChannelMessage 发布模式
     */
    default BrainOutputContract processWithContract(ChannelMessage message) {
        process(message);
        return null;
    }

    List<Tool> getTools();

    List<String> getSubscribedChannels();

    List<String> getPublishChannels();

    default void injectSessionHistory(String sessionId, List<Provider.ChatMessage> history) {
    }

    enum BrainState {
        INITIALIZING,
        RUNNING,
        PAUSED,
        STOPPED,
        ERROR
    }
}
