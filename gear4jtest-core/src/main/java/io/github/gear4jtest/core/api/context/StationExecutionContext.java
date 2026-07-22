package io.github.gear4jtest.core.api.context;

import java.util.Optional;

import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.api.trace.StationTrace;

/**
 * Runtime context of a single station execution.
 *
 * <p>
 * The context exposes the station identity, station kind, global run context,
 * station trace and typed capabilities used by station-specific features.
 * </p>
 */
public interface StationExecutionContext {
    String getOperationId();

    StationKind getKind();

    ExecutionContext getGlobalContext();

    default ExecutionServices getServices() {
        return getGlobalContext().getServices();
    }

    StationTrace getRecord();

    /**
     * Returns an optional typed capability attached to this station execution.
     */
    <T> Optional<T> getCapability(Class<T> type);

    ResolvedParameters getResolvedParameters();
}
