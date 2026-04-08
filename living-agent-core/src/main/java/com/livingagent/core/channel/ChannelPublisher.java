package com.livingagent.core.channel;

import java.util.Map;

public interface ChannelPublisher {

    void publish(String channelUri, ChannelMessage message);

    void publish(String channelUri, String type, Map<String, Object> payload);

    void broadcast(ChannelMessage message);
}
