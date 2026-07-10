package io.github.gear4jtest.core.sidecompute;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Run-scoped registry of side-compute futures keyed by logical side-compute
 * name.
 */
public final class SideComputeContext {
    private final ConcurrentMap<String, CompletableFuture<?>> futures = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicBoolean> startedComputations = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> getOrCreateFuture(String key) {
        return (CompletableFuture<T>) futures.computeIfAbsent(key, __ -> new CompletableFuture<>());
    }

    boolean tryStart(String key) {
        CompletableFuture<?> future = getOrCreateFuture(key);
        if (future.isDone()) {
            return false;
        }

        AtomicBoolean started = startedComputations.computeIfAbsent(key, __ -> new AtomicBoolean());
        if (!started.compareAndSet(false, true)) {
            return false;
        }

        if (future.isDone()) {
            started.set(false);
            return false;
        }
        return true;
    }

    public void cancelUnresolvedFutures() {
        futures.values().forEach(future -> {
            if (!future.isDone()) {
                future.completeExceptionally(new CancellationException(
                        "AssemblyLine execution ended before side-compute completion"));
            }
        });
    }

    public void cancelPendingFutures() {
        cancelUnresolvedFutures();
    }
}
