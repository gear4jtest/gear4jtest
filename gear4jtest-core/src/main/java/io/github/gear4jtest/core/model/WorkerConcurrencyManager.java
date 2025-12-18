package io.github.gear4jtest.core.model;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class WorkerConcurrencyManager {

    private final ConcurrentMap<Object, WorkerConcurrencyGuard> guards = new ConcurrentHashMap<>();

    public WorkerConcurrencyManager() {
    }

    public WorkerConcurrencyGuard guardFor(Object transformer,
                                           WorkerConcurrencyStrategy strategy) {
        Objects.requireNonNull(transformer, "transformer must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");

        return guards.computeIfAbsent(transformer, t -> new WorkerConcurrencyGuard(strategy));
    }

    /**
     * Permet de libérer les références en fin de vie d'une AssemblyLine / classloader.
     */
    public void clear() {
        guards.clear();
    }
}
