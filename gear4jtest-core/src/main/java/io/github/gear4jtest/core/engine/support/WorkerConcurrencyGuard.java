package io.github.gear4jtest.core.engine.support;

import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.github.gear4jtest.core.exception.ConcurrentTransformerUseException;

public final class WorkerConcurrencyGuard {
    private final Lock lock = new ReentrantLock();
    private final WorkerConcurrencyStrategy strategy;

    public WorkerConcurrencyGuard(WorkerConcurrencyStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
    }

    /**
     * Acquires the guard before the full station lifecycle starts.
     */
    public void beforeUse() {
        if (strategy == WorkerConcurrencyStrategy.IGNORE) {
            return;
        }

        switch (strategy) {
            case FAIL_FAST -> {
                boolean acquired = lock.tryLock();
                if (!acquired) {
                    throw new ConcurrentTransformerUseException("Transformer is already in use by another execution");
                }
            }
            case BLOCK_CALLER -> lock.lock();
            case IGNORE -> {
                // No guard required.
            }
        }
    }

    /**
     * Releases the guard after the full station lifecycle has completed.
     */
    public void afterUse() {
        if (strategy != WorkerConcurrencyStrategy.IGNORE) {
            lock.unlock();
        }
    }
}
