package io.github.gear4jtest.core.engine.core;

import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.DefaultStationExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLog;
import io.github.gear4jtest.core.persistence.StationLog.Status;

public class ScopeInitializingRunner implements StationRunner {

    private final StationRunner delegate;

    public ScopeInitializingRunner(StationRunner delegate) {
        this.delegate = delegate;
    }

    @Override
    public StationLog run(Object input, AbstractStation station, StationExecutionContext parentCtx) {
        StationLog stationLog = StationLog.start(
            parentCtx.getGlobalContext().getExecutionId(),
            station.getId(),
            parentCtx.getGlobalContext().getCurrentParentOperationId()
        );
        stationLog.setItemId(parentCtx.getGlobalContext().getCurrentItemId());
        stationLog.setStatus(Status.RUNNING);

        StationExecutionContext currentCtx = new DefaultStationExecutionContext(
            station.getId(),
            station.getKind(),
            parentCtx.getGlobalContext(),
            stationLog
        );
        parentCtx.getGlobalContext().pushParentOperationId(stationLog.getId());

        return delegate.run(input, station, currentCtx);
    }
}