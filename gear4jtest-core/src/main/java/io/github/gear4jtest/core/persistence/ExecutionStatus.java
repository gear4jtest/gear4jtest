package io.github.gear4jtest.core.persistence;

public enum ExecutionStatus {
    /**
     * Reserved for queued or externally scheduled executions. The core in-process
     * engine currently starts runs directly in {@link #RUNNING}.
     */
    PENDING(StatusCategory.ACTIVE),

    /**
     * Reserved for future multi-step run initialization. The core in-process engine
     * currently starts runs directly in {@link #RUNNING}.
     */
    INITIALIZING(StatusCategory.ACTIVE),

    /**
     * Execution is actively running in the current process.
     */
    RUNNING(StatusCategory.ACTIVE),

    /**
     * Reserved for future pause/resume support. The core engine does not currently
     * emit this status.
     */
    PAUSED(StatusCategory.ACTIVE),

    /**
     * Successful terminal state.
     */
    SUCCEEDED(StatusCategory.TERMINAL),

    /**
     * Terminal state for an execution that failed with an error.
     */
    FAILED(StatusCategory.TERMINAL),

    /**
     * Terminal state for an execution stopped by flow-control rules.
     */
    STOPPED(StatusCategory.TERMINAL),

    /**
     * Terminal state for an execution cancelled through cooperative cancellation.
     */
    CANCELLED(StatusCategory.TERMINAL),

    /**
     * Terminal state for an execution skipped by runtime rules.
     */
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
     * Returns whether this status represents an execution skipped by runtime rules.
     */
    public boolean isSkipped() {
        return this == SKIPPED;
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
