package io.github.gear4jtest.core.engine.runner;

import io.github.gear4jtest.core.spi.runner.StationRunner;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
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
                stationLog,
                parentCtx.getSupport()
        );
        parentCtx.getGlobalContext().pushParentOperationId(stationLog.getId());

        return delegate.run(input, station, currentCtx);
    }
}