package io.github.gear4jtest.core.engine.support;

public interface ConcurrencyAwareTransformer {
    /**
     * Lets an operator declare whether it is stateful or stateless.
     *
     * <p>
     * {@link WorkerStatefulness#AUTO} delegates the decision to runtime
     * introspection.
     * </p>
     */
    default WorkerStatefulness statefulness() {
        return WorkerStatefulness.AUTO;
    }
}
