package io.github.gear4jtest.core.api.trace;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.model.StationLogStatus;

/** Read-only view of a station execution trace. */
public interface StationTrace {
    UUID getId();

    UUID getAssemblyLineExecutionId();

    String getOperationId();

    UUID getParentOperationId();

    String getBranchId();

    StationLogStatus getStatus();

    Instant getStartedAt();

    Instant getEndedAt();

    String getErrorMessage();

    String getErrorHandlerMessages();

    Map<String, Object> getContext();

    List<? extends StationTrace> getSubOperations();

    Object getOutput();

    List<Throwable> getThrowables();

    String getItemId();
}
