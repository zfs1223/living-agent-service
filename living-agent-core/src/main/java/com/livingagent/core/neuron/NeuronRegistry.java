package com.livingagent.core.neuron;

import java.util.List;
import java.util.Optional;

public interface NeuronRegistry {

    void register(Neuron neuron);

    void unregister(String neuronId);

    Optional<Neuron> get(String neuronId);

    List<Neuron> getAll();

    List<Neuron> getByChannel(String channelId);

    List<Neuron> getByState(NeuronState state);

    boolean exists(String neuronId);

    int count();

    void startAll();

    void stopAll();

    void healthCheck();
}
