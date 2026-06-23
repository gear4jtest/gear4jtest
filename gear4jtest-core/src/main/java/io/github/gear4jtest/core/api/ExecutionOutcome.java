package io.github.gear4jtest.core.api;

import java.util.Objects;

import io.github.gear4jtest.core.persistence.ExecutionStatus;

/**
 * Terminal public outcome of one pipeline execution.
 *
 * <p>
 * A functional skip, a functional stop and a technical cancellation are
 * deliberately distinct from a successful completed run and from a failed run.
 * </p>
 */
public enum ExecutionOutcome {
    SUCCEEDED(ExecutionStatus.SUCCEEDED),
    SKIPPED(ExecutionStatus.SKIPPED),
    FAILED(ExecutionStatus.FAILED),
    STOPPED(ExecutionStatus.STOPPED),
    CANCELLED(ExecutionStatus.CANCELLED);

    private final ExecutionStatus status;

    ExecutionOutcome(ExecutionStatus status) {
        this.status = status;
    }

    /**
     * Returns the corresponding terminal persistence status.
     */
    public ExecutionStatus toExecutionStatus() {
        return status;
    }

    /**
     * Converts a terminal persistence status to the public result outcome.
     *
     * @throws IllegalArgumentException when the status is active/non-terminal.
     */
    public static ExecutionOutcome fromExecutionStatus(ExecutionStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return switch (status) {
            case SUCCEEDED -> SUCCEEDED;
            case SKIPPED -> SKIPPED;
            case FAILED -> FAILED;
            case STOPPED -> STOPPED;
            case CANCELLED -> CANCELLED;
            case PENDING, INITIALIZING, RUNNING, PAUSED -> throw new IllegalArgumentException(
                    "ExecutionStatus " + status + " is not terminal and cannot be converted to ExecutionOutcome");
        };
    }
}
