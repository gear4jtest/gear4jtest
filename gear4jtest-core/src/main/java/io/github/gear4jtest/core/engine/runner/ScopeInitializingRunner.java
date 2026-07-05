package io.github.gear4jtest.core.engine.runner;

import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.engine.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.engine.context.EngineStationContexts;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class ScopeInitializingRunner implements StationRunner {
    private final StationRunner delegate;

    public ScopeInitializingRunner(StationRunner delegate) {
        this.delegate = delegate;
    }

    @Override
    public StationLogTrace run(Object input, AbstractStation<?, ?> station, StationExecutionContext parentCtx) {
        StationLogTrace stationLog = StationLogTrace.start(parentCtx.getGlobalContext().getExecutionId(),
                                                           station.getId(),
                                                           parentCtx.getGlobalContext().getCurrentParentOperationId());
        stationLog.setItemId(parentCtx.getGlobalContext().getCurrentItemId());
        stationLog.setBranchId(parentCtx.getGlobalContext().getCurrentBranchId());
        stationLog.setStatus(StationLogStatus.RUNNING);

        StationExecutionContext currentCtx = new DefaultStationExecutionContext(station.getId(), station.getKind(),
                parentCtx.getGlobalContext(), stationLog, EngineStationContexts.support(parentCtx));
        try (var ignored = parentCtx.getGlobalContext().enterParentOperation(stationLog.getId())) {
            return delegate.run(input, station, currentCtx);
        }
    }
}
