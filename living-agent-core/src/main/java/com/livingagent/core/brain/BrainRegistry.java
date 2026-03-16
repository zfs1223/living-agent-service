package com.livingagent.core.brain;

import java.util.List;
import java.util.Optional;

public interface BrainRegistry {

    void register(Brain brain);

    void unregister(String brainId);

    Optional<Brain> get(String brainId);

    List<Brain> getAll();

    Optional<Brain> getByDepartment(String department);

    List<Brain> getByState(Brain.BrainState state);

    boolean exists(String brainId);

    int count();

    void startAll();

    void stopAll();
}
