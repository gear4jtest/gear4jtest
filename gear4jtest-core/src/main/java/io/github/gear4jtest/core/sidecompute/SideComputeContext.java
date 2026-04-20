package io.github.gear4jtest.core.sidecompute;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Contexte side compute pour une exécution donnée. Stocke des CompletableFuture par clé logique. */
public final class SideComputeContext {

    private final ConcurrentMap<String, CompletableFuture<?>> futures = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> getOrCreateFuture(String key) {
        return (CompletableFuture<T>) futures.computeIfAbsent(key, __ -> new CompletableFuture<>());
    }

    public void cancelUnresolvedFutures() {
        futures.values().forEach(future -> {
            if (!future.isDone()) {
                future.completeExceptionally(
                        new CancellationException("Pipeline execution ended before side-compute completion"));
            }
        });
    }

    public void cancelPendingFutures() {
        cancelUnresolvedFutures();
    }
}
