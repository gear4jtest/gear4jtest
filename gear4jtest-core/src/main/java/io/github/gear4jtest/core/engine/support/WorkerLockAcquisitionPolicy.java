package io.github.gear4jtest.core.engine.support;

/**
 * Defines what Gear4J does when a protected worker instance is already being
 * invoked by another thread.
 */
public enum WorkerLockAcquisitionPolicy {

    /**
     * Wait until the worker lock becomes available, up to the configured lock wait
     * timeout.
     *
     * <p>
     * This is the safest default: non-thread-safe singleton workers are protected,
     * and concurrent pipeline executions naturally apply bounded backpressure
     * instead of corrupting worker state.
     * </p>
     */
    BLOCK_CALLER,

    /**
     * Fail immediately if the worker lock is already held.
     *
     * <p>
     * This is useful in tests or environments where hidden backpressure is
     * undesirable and concurrent use of a stateful worker should be surfaced as a
     * configuration/design error.
     * </p>
     */
    FAIL_FAST
}
