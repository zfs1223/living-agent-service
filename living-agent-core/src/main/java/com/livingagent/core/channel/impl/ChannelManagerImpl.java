package com.livingagent.core.channel.impl;

import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.Channel.ChannelType;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.channel.ChannelSubscriber;
import com.livingagent.core.neuron.Neuron;
import com.livingagent.core.neuron.NeuronRegistry;
import com.livingagent.core.neuron.NeuronState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChannelManagerImpl implements ChannelManager {

    private static final Logger log = LoggerFactory.getLogger(ChannelManagerImpl.class);

    private final ConcurrentMap<String, Channel> channels = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<ChannelSubscriber>> externalSubscribers = new ConcurrentHashMap<>();
    private NeuronRegistry neuronRegistry;

    public void setNeuronRegistry(NeuronRegistry neuronRegistry) {
        this.neuronRegistry = neuronRegistry;
    }

    @Override
    public Channel create(String channelId, ChannelType type) {
        return channels.computeIfAbsent(channelId, id -> {
            Channel channel = createChannel(id, type);
            log.info("Created channel: {} (type: {})", id, type);
            return channel;
        });
    }
    
    @Override
    public Channel getOrCreateChannel(String channelId) {
        return getOrCreateChannel(channelId, ChannelType.BROADCAST);
    }
    
    @Override
    public Channel getOrCreateChannel(String channelId, ChannelType type) {
        return channels.computeIfAbsent(channelId, id -> {
            Channel channel = createChannel(id, type);
            log.info("Created channel: {} (type: {})", id, type);
            return channel;
        });
    }

    private Channel createChannel(String id, ChannelType type) {
        String name = extractName(id);
        return switch (type) {
            case BROADCAST -> new BroadcastChannel(id, name);
            case UNICAST -> new UnicastChannel(id, name);
            case ROUND_ROBIN -> new RoundRobinChannel(id, name);
            case PRIORITY -> new PriorityChannel(id, name);
        };
    }

    private String extractName(String channelId) {
        String[] parts = channelId.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : channelId;
    }

    @Override
    public void destroy(String channelId) {
        Channel channel = channels.remove(channelId);
        externalSubscribers.remove(channelId);
        if (channel != null) {
            channel.close();
            log.info("Destroyed channel: {}", channelId);
        }
    }

    @Override
    public Optional<Channel> get(String channelId) {
        return Optional.ofNullable(channels.get(channelId));
    }

    @Override
    public List<Channel> getAll() {
        return List.copyOf(channels.values());
    }

    @Override
    public boolean exists(String channelId) {
        return channels.containsKey(channelId);
    }

    @Override
    public void publish(String channelId, ChannelMessage message) {
        Channel channel = channels.get(channelId);
        if (channel != null) {
            channel.publish(message);
            // deliverToSubscribers 内部已调用 deliverToExternalSubscribers，不再重复调用
            deliverToSubscribers(channel, message);
        } else {
            List<ChannelSubscriber> subscribers = externalSubscribers.get(channelId);
            if (subscribers != null && !subscribers.isEmpty()) {
                deliverToExternalSubscribers(channelId, message);
            } else {
                log.warn("Message dropped: channel not found and no subscribers: channelId={}, contentType={}",
                    channelId, message.getContent() != null ? message.getContent().getClass().getSimpleName() : "null");
            }
        }
    }

    private void deliverToSubscribers(Channel channel, ChannelMessage message) {
        if (neuronRegistry == null) {
            log.warn("NeuronRegistry not set, skipping internal subscriber delivery");
            // 即使内部注册表不可用，仍需投递给外部订阅者
            deliverToExternalSubscribers(channel.getId(), message);
            return;
        }
        
        List<String> subscribers = channel.getSubscribers();
        java.util.concurrent.atomic.AtomicInteger internalCount = new java.util.concurrent.atomic.AtomicInteger(0);
        int externalCount = externalSubscribers.getOrDefault(channel.getId(), List.of()).size();
        
        for (String neuronId : subscribers) {
            neuronRegistry.get(neuronId).ifPresentOrElse(neuron -> {
                try {
                    if (neuron.getState() == NeuronState.RUNNING ||
                        neuron.getState() == NeuronState.PROCESSING ||
                        neuron.getState() == NeuronState.IDLE ||
                        neuron.getState() == NeuronState.ACTIVE) {
                        neuron.onMessage(message);
                        internalCount.incrementAndGet();
                    } else {
                        log.debug("Skipping internal subscriber {} in state {}", neuronId, neuron.getState());
                    }
                } catch (Exception e) {
                    log.error("Error delivering message to internal subscriber {}: {}", neuronId, e.getMessage(), e);
                }
            }, () -> {
                log.warn("Internal subscriber {} NOT FOUND in registry for channel {}", neuronId, channel.getId());
            });
        }
        
        deliverToExternalSubscribers(channel.getId(), message);
        
        log.info("Channel delivery: channel={}, internalDelivered={}, externalDelivered={}, totalSubscribers={}", 
            channel.getId(), internalCount.get(), externalCount, subscribers.size());
    }
    
    private void deliverToExternalSubscribers(String channelId, ChannelMessage message) {
        List<ChannelSubscriber> subscribers = externalSubscribers.get(channelId);
        if (subscribers != null && !subscribers.isEmpty()) {
            int delivered = 0;
            for (ChannelSubscriber subscriber : subscribers) {
                try {
                    subscriber.onMessage(message);
                    delivered++;
                } catch (Exception e) {
                    log.error("Error delivering message to external subscriber {} on channel {}: {}", 
                        subscriber.getSubscriberId(), channelId, e.getMessage());
                }
            }
            log.debug("Delivered message {} to {}/{} external subscribers on channel {}", 
                message.getId(), delivered, subscribers.size(), channelId);
        }
    }

    @Override
    public void broadcast(String pattern, ChannelMessage message) {
        channels.keySet().stream()
            .filter(id -> id.matches(pattern.replace("*", ".*")))
            .forEach(id -> publish(id, message));
    }
    
    @Override
    public void subscribe(String channelId, ChannelSubscriber subscriber) {
        externalSubscribers.computeIfAbsent(channelId, k -> new CopyOnWriteArrayList<>())
            .add(subscriber);
        log.info("External subscriber {} subscribed to channel {}", subscriber.getSubscriberId(), channelId);
    }
    
    @Override
    public void unsubscribe(String channelId, String subscriberId) {
        List<ChannelSubscriber> subscribers = externalSubscribers.get(channelId);
        if (subscribers != null) {
            subscribers.removeIf(s -> s.getSubscriberId().equals(subscriberId));
            log.info("External subscriber {} unsubscribed from channel {}", subscriberId, channelId);
        }
    }

    @Override
    public List<String> getChannelIds() {
        return List.copyOf(channels.keySet());
    }

    @Override
    public int count() {
        return channels.size();
    }

    @Override
    public ChannelHealthSummary getHealthSummary() {
        int total = channels.size();
        int active = 0;
        int empty = 0;
        int totalSubscribers = 0;
        long totalMessages = 0;

        for (Channel channel : channels.values()) {
            int subs = channel.getSubscribers().size();
            int msgs = channel.getMessageCount();
            totalSubscribers += subs;
            totalMessages += msgs;
            if (subs > 0) active++;
            if (subs == 0) empty++;
        }

        return new ChannelHealthSummary(total, active, empty, totalSubscribers, totalMessages);
    }
}
