package com.livingagent.core.channel.impl;

import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.channel.ChannelPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class ChannelPublisherImpl implements ChannelPublisher {

    private static final Logger log = LoggerFactory.getLogger(ChannelPublisherImpl.class);

    private final ChannelManager channelManager;

    @Autowired
    public ChannelPublisherImpl(ChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    @Override
    public void publish(String channelUri, ChannelMessage message) {
        try {
            channelManager.publish(channelUri, message);
            log.debug("Published message to channel: {}", channelUri);
        } catch (Exception e) {
            log.error("Failed to publish message to channel {}: {}", channelUri, e.getMessage());
        }
    }

    @Override
    public void publish(String channelUri, String type, Map<String, Object> payload) {
        try {
            ChannelMessage message = new ChannelMessage(
                channelUri,
                "channel-publisher",
                channelUri,
                "system",
                ChannelMessage.MessageType.CONTROL,
                payload
            );
            channelManager.publish(channelUri, message);
            log.debug("Published {} message to channel: {}", type, channelUri);
        } catch (Exception e) {
            log.error("Failed to publish {} message to channel {}: {}", type, channelUri, e.getMessage());
        }
    }

    @Override
    public void broadcast(ChannelMessage message) {
        try {
            channelManager.broadcast("*", message);
            log.debug("Broadcast message to all channels");
        } catch (Exception e) {
            log.error("Failed to broadcast message: {}", e.getMessage());
        }
    }
}
