package io.github.gear4jtest.core.persistence;

public enum ExecutionStatus {
    // Active states before execution starts.
    PENDING(StatusCategory.ACTIVE), INITIALIZING(StatusCategory.ACTIVE),

    // Active states while execution is in progress.
    RUNNING(StatusCategory.ACTIVE), PAUSED(StatusCategory.ACTIVE),

    // Successful terminal state.
    SUCCEEDED(StatusCategory.TERMINAL),

    // Terminal stop and failure states.
    FAILED(StatusCategory.TERMINAL), STOPPED(StatusCategory.TERMINAL), CANCELLED(StatusCategory.TERMINAL),
    SKIPPED(StatusCategory.TERMINAL);

    private final StatusCategory category;

    ExecutionStatus(StatusCategory category) {
        this.category = category;
    }

    /**
     * Returns whether this status is terminal.
     */
    public boolean isTerminal() {
        return this.category == StatusCategory.TERMINAL;
    }

    /**
     * Returns whether this status still represents an active execution.
     */
    public boolean isActive() {
        return this.category == StatusCategory.ACTIVE;
    }

    /**
     * Returns whether this status represents a successful completion.
     */
    public boolean isSuccess() {
        return this == SUCCEEDED;
    }

    /**
     * Returns whether this status should be treated as an error for monitoring or
     * alerting.
     */
    public boolean isError() {
        return this == FAILED || this == CANCELLED;
    }

    /**
     * Returns whether this status represents a functional stop.
     */
    public boolean isStopped() {
        return this == STOPPED;
    }

    public enum StatusCategory {
        /**
         * Execution has not produced a final result yet.
         */
        ACTIVE,

        /**
         * Execution has produced a final result and should no longer change state.
         */
        TERMINAL
    }
}
