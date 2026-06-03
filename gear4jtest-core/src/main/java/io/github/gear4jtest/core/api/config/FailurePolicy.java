package io.github.gear4jtest.core.api.config;

/**
 * Policy applied when a child station ends with {@code FAILED}.
 */
public enum FailurePolicy {
    /** Stop orchestration immediately and propagate the failure. */
    FAIL_FAST,
    /** Ignore the failure and continue with the previous input. */
    IGNORE_AND_CONTINUE,
    /** Continue execution, collect failures, and fail the container at the end. */
    COLLECT_AND_FAIL
}
