package io.github.gear4jtest.core.engine.support;

public enum WorkerStatefulness {

    /**
     * Derive statefulness through runtime introspection.
     */
    AUTO,

    /**
     * The operator is explicitly stateful.
     */
    STATEFUL,

    /**
     * The operator is explicitly stateless.
     */
    STATELESS
}
