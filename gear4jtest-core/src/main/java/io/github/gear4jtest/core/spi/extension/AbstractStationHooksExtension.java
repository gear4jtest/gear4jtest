package io.github.gear4jtest.core.spi.extension;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.runner.StationRunner;

/**
 * Hook wrapper extension.
 *
 * <p>This extension participates in station execution and therefore runs inside the station
 * exception boundary. Any RuntimeException thrown here may be normalized into station status.
 *
 * <p>If you need to observe the final normalized station status, use {@link StationLifecycleExtension}
 * instead of this wrapper SPI.
 */
public abstract class AbstractStationHooksExtension implements StationWrapperExtension {

    @Override
    public final StationRunner wrapStationRunner(StationRunner delegate, ExecutionContext ctx) {
        return (input, station, parentCtx) -> {
            onStart(station, parentCtx);

            try {
                StationLogTrace log = delegate.run(input, station, parentCtx);
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

    protected void onResult(Object station, Object stationCtx, StationLogTrace log) {
    }

    protected void onException(Object station, Object stationCtx, RuntimeException error) {
    }

    protected void onEnd(Object station, Object stationCtx) {
    }
}
