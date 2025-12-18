package io.github.gear4jtest.core.model;

public interface ConcurrencyAwareTransformer {

    /**
     * Permet au transformer de déclarer s'il est stateful ou stateless.
     * Par défaut : AUTO => détection par réflexion.
     */
    default WorkerStatefulness statefulness() {
        return WorkerStatefulness.AUTO;
    }
}
