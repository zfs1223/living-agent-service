package com.livingagent.core.brain;

import com.livingagent.core.channel.ChannelMessage;
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

    List<Tool> getTools();

    List<String> getSubscribedChannels();

    List<String> getPublishChannels();

    enum BrainState {
        INITIALIZING,
        RUNNING,
        PAUSED,
        STOPPED,
        ERROR
    }
}
