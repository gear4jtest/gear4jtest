package io.github.gear4jtest.core.spi.extension;

import io.github.gear4jtest.core.api.annotation.Spi;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLogRecord;

/**
 * Passive station lifecycle hooks.
 *
 * <p>
 * These hooks are observers of station execution. They are invoked by the
 * engine:
 * </p>
 * <ul>
 * <li>once the station scope has been initialized, before delegate
 * execution,</li>
 * <li>once the station completion has been normalized.</li>
 * </ul>
 *
 * <p>
 * Implementations are intended for persistence, tracing, metrics, audit, etc. A
 * {@link LifecycleFailureMode#BEST_EFFORT} hook failure is logged and ignored.
 * A {@link LifecycleFailureMode#CRITICAL} hook failure is recorded by turning
 * an otherwise running/successful/skipped station log into a failed station
 * log. It is then interpreted by the existing parent flow policy, just like any
 * other failed child station. It is not thrown directly to the API consumer by
 * this wrapper.
 * </p>
 */
@Spi
public interface StationLifecycleExtension extends RuntimeExtension {
    default LifecycleFailureMode failureMode() {
        return LifecycleFailureMode.BEST_EFFORT;
    }

    default void onStationStarted(ExecutionContext runCtx,
                                  StationExecutionContext stationCtx,
                                  StationLogRecord snapshot) {
        // no-op
    }

    default void onStationCompleted(ExecutionContext runCtx,
                                    StationExecutionContext stationCtx,
                                    StationLogRecord snapshot) {
        // no-op
    }
}
