package io.github.gear4jtest.core.model.refactor;

import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class TransformerConcurrencyGuard {

    private final Lock lock = new ReentrantLock();
    private final TransformerConcurrencyStrategy strategy;

    public TransformerConcurrencyGuard(TransformerConcurrencyStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
    }

    /**
     * À appeler juste avant de démarrer le "cycle de vie" complet de l'opération
     * (pre-processors + transformer + post-processors) pour ce transformer.
     */
    public void beforeUse() {
        if (strategy == TransformerConcurrencyStrategy.IGNORE) {
            return;
        }

        switch (strategy) {
            case FAIL_FAST -> {
                boolean acquired = lock.tryLock();
                if (!acquired) {
                    throw new ConcurrentTransformerUseException(
                        "Transformer is already in use by another execution"
                    );
                }
            }
            case BLOCK_CALLER -> lock.lock();
            case IGNORE -> {
                // rien
            }
        }
    }

    /**
     * À appeler juste après la fin du cycle de vie complet de l'opération.
     */
    public void afterUse() {
        if (strategy != TransformerConcurrencyStrategy.IGNORE) {
            lock.unlock();
        }
    }
}
