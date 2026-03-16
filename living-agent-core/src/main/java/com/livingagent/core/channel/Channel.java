package com.livingagent.core.channel;

import java.util.List;

public interface Channel {

    String getId();

    String getName();

    ChannelType getType();

    void publish(ChannelMessage message);

    void subscribe(String neuronId);

    void unsubscribe(String neuronId);

    List<String> getSubscribers();

    int getMessageCount();

    void clear();

    void close();

    enum ChannelType {
        BROADCAST,
        UNICAST,
        ROUND_ROBIN,
        PRIORITY
    }
}
