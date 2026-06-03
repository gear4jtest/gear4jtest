package io.github.gear4jtest.core.engine.support;

public enum WorkerConcurrencyStrategy {

    /**
     * Fail immediately when the operator is already being used by another
     * execution.
     */
    FAIL_FAST,

    /**
     * Block the caller until the operator is available again.
     */
    BLOCK_CALLER,

    /**
     * Do not guard operator use. Only use this for truly thread-safe or stateless
     * operators.
     */
    IGNORE
}
