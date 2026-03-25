package io.github.gear4jtest.core.engine.support;

import io.github.gear4jtest.core.engine.support.WorkerStatefulness;

public interface ConcurrencyAwareTransformer {

    /**
     * Permet au transformer de déclarer s'il est stateful ou stateless.
     * Par défaut : AUTO => détection par réflexion.
     */
    default WorkerStatefulness statefulness() {
        return WorkerStatefulness.AUTO;
    }
}
