package com.livingagent.core.channel;

public interface ChannelSubscriber {
    
    void onMessage(ChannelMessage message);
    
    String getSubscriberId();
}
