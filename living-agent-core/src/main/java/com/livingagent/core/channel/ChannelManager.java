package com.livingagent.core.channel;

import java.util.List;
import java.util.Optional;

public interface ChannelManager {

    Channel create(String channelId, Channel.ChannelType type);
    
    Channel getOrCreateChannel(String channelId);
    
    Channel getOrCreateChannel(String channelId, Channel.ChannelType type);

    void destroy(String channelId);

    Optional<Channel> get(String channelId);

    List<Channel> getAll();

    boolean exists(String channelId);

    void publish(String channelId, ChannelMessage message);

    void broadcast(String pattern, ChannelMessage message);

    List<String> getChannelIds();

    int count();
}
