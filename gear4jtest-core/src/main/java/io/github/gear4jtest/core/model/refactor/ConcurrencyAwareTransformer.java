package io.github.gear4jtest.core.model.refactor;

public interface ConcurrencyAwareTransformer {

    /**
     * Permet au transformer de déclarer s'il est stateful ou stateless.
     * Par défaut : AUTO => détection par réflexion.
     */
    default TransformerStatefulness statefulness() {
        return TransformerStatefulness.AUTO;
    }
}
