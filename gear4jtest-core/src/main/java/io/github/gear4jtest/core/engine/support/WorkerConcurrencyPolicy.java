package io.github.gear4jtest.core.engine.support;

/**
 * Defines where Gear4J protects stateful worker instances from concurrent use.
 *
 * <p>
 * The behavior when a protected worker is already in use is configured
 * separately through {@link WorkerLockAcquisitionPolicy}.
 * </p>
 */
public enum WorkerConcurrencyPolicy {

    /**
     * Protect each stateful worker instance with a process-wide lock.
     *
     * <p>
     * This is the safest default when worker instances may be singletons and not
     * thread-safe. The same worker instance shared by several
     * {@code AssemblyLineEngine} instances is still protected.
     * </p>
     */
    LOCK_PER_WORKER_INSTANCE,

    /**
     * Protect each stateful worker instance only inside one
     * {@code AssemblyLineEngine}.
     *
     * <p>
     * This mode does not protect a worker instance shared by several engines.
     * </p>
     */
    ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE,

    /**
     * Do not lock worker invocations.
     *
     * <p>
     * Use only when workers are stateless, thread-safe, or created with an
     * execution scope that makes concurrent access impossible.
     * </p>
     */
    ALLOW_PARALLEL_INVOCATIONS
}
