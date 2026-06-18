package io.github.gear4jtest.core.execution.trace;

import java.util.UUID;

import io.github.gear4jtest.core.internal.AbstractStationLogState;
import io.github.gear4jtest.core.model.StationLogStatus;

public class StationLogTrace extends AbstractStationLogState<StationLogTrace, StationLogStatus> {
    public static StationLogTrace start(UUID pipelineExecutionId, String operationId, UUID parentOperationId) {
        StationLogTrace record = new StationLogTrace();
        record.initializeStarted(pipelineExecutionId, operationId, parentOperationId);
        return record;
    }

    @Override
    protected StationLogStatus runningStatus() {
        return StationLogStatus.RUNNING;
    }

    @Override
    protected StationLogStatus skippedStatus() {
        return StationLogStatus.SKIPPED;
    }

    @Override
    protected StationLogStatus succeededStatus() {
        return StationLogStatus.SUCCEEDED;
    }

    @Override
    protected StationLogStatus failedStatus() {
        return StationLogStatus.FAILED;
    }

    @Override
    protected StationLogStatus stoppedStatus() {
        return StationLogStatus.STOPPED;
    }

    @Override
    protected StationLogStatus cancelledStatus() {
        return StationLogStatus.CANCELLED;
    }
}
