package io.github.gear4jtest.core.spi.extension;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import io.github.gear4jtest.core.persistence.StationLog;

public abstract class AbstractStationHooksExtension implements StationWrapperExtension {

    @Override
    public final StationRunner wrapStationRunner(StationRunner delegate, ExecutionContext ctx) {
        return (input, station, parentCtx) -> {
            onStart(station, parentCtx);

            try {
                StationLog log = delegate.run(input, station, parentCtx);
                onResult(station, parentCtx, log);
                return log;
            } catch (RuntimeException e) {
                onException(station, parentCtx, e);
                throw e;
            } finally {
                onEnd(station, parentCtx);
            }
        };
    }

    protected void onStart(Object station, Object stationCtx) {
    }

    protected void onResult(Object station, Object stationCtx, StationLog log) {
    }

    protected void onException(Object station, Object stationCtx, RuntimeException error) {
    }

    protected void onEnd(Object station, Object stationCtx) {
    }
}