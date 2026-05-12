package io.github.gear4jtest.core.spi.extension;

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
 * Implementations are intended for persistence, tracing, metrics, audit, etc.
 * </p>
 */
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
