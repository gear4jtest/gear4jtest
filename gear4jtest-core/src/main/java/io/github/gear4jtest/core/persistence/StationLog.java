package io.github.gear4jtest.core.persistence;

import java.util.UUID;

import io.github.gear4jtest.core.internal.AbstractStationLogState;

public class StationLog extends AbstractStationLogState<StationLog, StationLog.Status> {
    public static StationLog start(UUID pipelineExecutionId, String operationId, UUID parentOperationId) {
        StationLog stationLog = new StationLog();
        stationLog.initializeStarted(pipelineExecutionId, operationId, parentOperationId);
        return stationLog;
    }

    @Override
    protected Status runningStatus() {
        return Status.RUNNING;
    }

    @Override
    protected Status skippedStatus() {
        return Status.SKIPPED;
    }

    @Override
    protected Status succeededStatus() {
        return Status.SUCCEEDED;
    }

    @Override
    protected Status failedStatus() {
        return Status.FAILED;
    }

    @Override
    protected Status stoppedStatus() {
        return Status.STOPPED;
    }

    @Override
    protected Status cancelledStatus() {
        return Status.CANCELLED;
    }

    public enum Status {
        RUNNING, SKIPPED, SUCCEEDED, FAILED, STOPPED, CANCELLED
    }
}
