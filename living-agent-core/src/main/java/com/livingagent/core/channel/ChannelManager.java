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
    
    void subscribe(String channelId, ChannelSubscriber subscriber);
    
    void unsubscribe(String channelId, String subscriberId);

    List<String> getChannelIds();

    int count();

    /**
     * P11-B: 获取通道健康摘要（用于监控闭环）
     */
    ChannelHealthSummary getHealthSummary();

    /**
     * P11-B: 通道健康摘要
     */
    record ChannelHealthSummary(
        int totalChannels,
        int activeChannels,
        int emptyChannels,
        int totalSubscribers,
        long totalMessages
    ) {}
}
