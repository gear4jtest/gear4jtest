package io.github.gear4jtest.core.api;

/**
 * Terminal public outcome of one pipeline execution.
 *
 * <p>
 * A functional skip, a functional stop and a technical cancellation are
 * deliberately distinct from a successful completed run and from a failed run.
 * </p>
 */
public enum ExecutionOutcome {
    SUCCEEDED,
    SKIPPED,
    FAILED,
    STOPPED,
    CANCELLED
}
