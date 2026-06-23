package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.persistence.ExecutionStatus;

public enum StationLogStatus {
    RUNNING(ExecutionStatus.RUNNING),
    SKIPPED(ExecutionStatus.SKIPPED),
    SUCCEEDED(ExecutionStatus.SUCCEEDED),
    FAILED(ExecutionStatus.FAILED),
    STOPPED(ExecutionStatus.STOPPED),
    CANCELLED(ExecutionStatus.CANCELLED);

    private final ExecutionStatus executionStatus;

    StationLogStatus(ExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
    }

    public ExecutionStatus toExecutionStatus() {
        return executionStatus;
    }
}
