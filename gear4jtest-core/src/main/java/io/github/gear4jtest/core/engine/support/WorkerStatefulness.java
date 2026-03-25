package io.github.gear4jtest.core.engine.support;

public enum WorkerStatefulness {

    /**
     * L'état (stateful/stateless) est déduit automatiquement par introspection.
     */
    AUTO,

    /**
     * Le transformer est explicitement déclaré comme stateful.
     */
    STATEFUL,

    /**
     * Le transformer est explicitement déclaré comme stateless.
     */
    STATELESS
}
