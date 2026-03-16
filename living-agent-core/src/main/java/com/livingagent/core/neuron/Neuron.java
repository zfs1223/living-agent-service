package com.livingagent.core.neuron;

import com.livingagent.core.channel.Channel;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.tool.Tool;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Neuron {

    String getId();

    String getName();

    String getDescription();

    String getType();

    NeuronState getState();

    void setState(NeuronState state);

    void initialize(NeuronContext context);

    /**
     * 使用完整的上下文启动神经元。
     */
    void start(NeuronContext context);
    
    /**
     * 默认启动方法。
     */
    default void start() { }

    void stop();

    void onMessage(ChannelMessage message);

    void subscribe(Channel channel);

    void publishTo(Channel channel);

    void publish(String channelId, ChannelMessage message);

    /**
     * 返回订阅的通道 ID 列表。
     */
    List<String> getSubscribedChannels();

    /**
     * 返回发布目标通道 ID 列表。
     */
    List<String> getPublishChannels();
    
    /**
     * 返回订阅的通道对象列表。
     */
    default List<Channel> getSubscribedChannelObjects() { return List.of(); }
    
    /**
     * 返回发布目标通道对象列表。
     */
    default List<Channel> getPublishingChannels() { return List.of(); }

    List<Tool> getTools();

    Set<String> getSkills();

    void addSkill(String skillName);

    void removeSkill(String skillName);

    boolean hasSkill(String skillName);

    void autoDiscoverSkills();

    Map<String, Object> getStateData();

    void setStateData(String key, Object value);

    Object getStateData(String key);
}
