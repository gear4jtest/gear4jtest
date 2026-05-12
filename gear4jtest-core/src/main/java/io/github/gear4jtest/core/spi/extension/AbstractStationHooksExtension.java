package io.github.gear4jtest.core.spi.extension;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.runner.StationRunner;

/**
 * Convenience base class for writing station wrapper extensions as hooks.
 *
 * <p>
 * This extension participates in station execution and therefore runs inside
 * the station exception boundary. Any {@link RuntimeException} thrown here may
 * be normalized into station status.
 * </p>
 *
 * <p>
 * If you need to observe the final normalized station status, use
 * {@link StationLifecycleExtension} instead of this wrapper SPI.
 * </p>
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

    /**
     * Hook called before delegate station execution.
     */
    protected void onStart(AbstractStation<?, ?> station, StationExecutionContext stationCtx) {
    }

    /**
     * Hook called after delegate station execution returns a station log trace.
     */
    protected void onResult(AbstractStation<?, ?> station, StationExecutionContext stationCtx, StationLogTrace log) {
    }

    /**
     * Hook called when delegate station execution throws a runtime exception.
     */
    protected void onException(AbstractStation<?, ?> station,
                               StationExecutionContext stationCtx,
                               RuntimeException error) {
    }

    /**
     * Hook called after station execution attempt completion.
     */
    protected void onEnd(AbstractStation<?, ?> station, StationExecutionContext stationCtx) {
    }
}
