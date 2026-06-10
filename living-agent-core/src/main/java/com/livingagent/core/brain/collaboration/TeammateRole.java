package com.livingagent.core.brain.collaboration;

public record TeammateRole(
    String name,
    String description,
    String channelId,
    String neuronId
) {
    public static TeammateRole of(String name, String description, String channelId, String neuronId) {
        return new TeammateRole(name, description, channelId, neuronId);
    }
}
