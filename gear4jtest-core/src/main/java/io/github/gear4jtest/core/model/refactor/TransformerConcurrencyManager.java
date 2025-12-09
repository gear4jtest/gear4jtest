package io.github.gear4jtest.core.model.refactor;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TransformerConcurrencyManager {

    private final ConcurrentMap<Object, TransformerConcurrencyGuard> guards = new ConcurrentHashMap<>();

    public TransformerConcurrencyManager() {
    }

    public TransformerConcurrencyGuard guardFor(Object transformer,
                                                TransformerConcurrencyStrategy strategy) {
        Objects.requireNonNull(transformer, "transformer must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");

        return guards.computeIfAbsent(transformer, t -> new TransformerConcurrencyGuard(strategy));
    }

    /**
     * Permet de libérer les références en fin de vie d'une AssemblyLine / classloader.
     */
    public void clear() {
        guards.clear();
    }
}
