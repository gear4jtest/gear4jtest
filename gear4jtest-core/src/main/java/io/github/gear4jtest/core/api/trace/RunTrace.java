package io.github.gear4jtest.core.api.trace;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.persistence.ExecutionStatus;

/**
 * Read-only view of an assembly-line run trace.
 *
 * <p>
 * The runtime owns the mutable trace implementation. Consumers should treat
 * values returned by this interface as observations and must not rely on an
 * implementation type.
 * </p>
 */
public interface RunTrace {
    UUID getId();

    String getAssemblyLineId();

    Map<String, Object> getContext();

    Object getInputParams();

    Object getResult();

    ExecutionStatus getStatus();

    Instant getStartTime();

    Instant getEndTime();

    String getErrorMessage();

    Exception getError();

    UUID getParentExecutionId();

    UUID getRootExecutionId();

    UUID getParentStationLogId();
}
