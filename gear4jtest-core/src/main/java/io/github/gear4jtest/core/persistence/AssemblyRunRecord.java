package io.github.gear4jtest.core.persistence;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;

public record AssemblyRunRecord(UUID id,
                                String pipelineId,
                                Map<String, Object> context,
                                Object inputParams,
                                Object result,
                                ExecutionStatus status,
                                Instant startTime,
                                Instant endTime,
                                String errorMessage,
                                UUID parentExecutionId,
                                UUID rootExecutionId,
                                UUID parentStationLogId) {
    public static AssemblyRunRecord from(AssemblyRunTrace trace) {
        if (trace == null) {
            throw new IllegalArgumentException("trace must not be null");
        }

        return new AssemblyRunRecord(trace.getId(), trace.getPipelineId(),
                trace.getContext() == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(trace.getContext())),
                trace.getInputParams(), trace.getResult(), trace.getStatus(), trace.getStartTime(), trace.getEndTime(),
                trace.getErrorMessage(), trace.getParentExecutionId(), trace.getRootExecutionId(),
                trace.getParentStationLogId());
    }

    public AssemblyRunTrace toTrace() {
        AssemblyRunTrace trace = new AssemblyRunTrace();
        trace.setId(id);
        trace.setPipelineId(pipelineId);
        trace.setContext(context == null ? Map.of() : new LinkedHashMap<>(context));
        trace.setInputParams(inputParams);
        trace.setResult(result);
        trace.setStatus(status);
        trace.setStartTime(startTime);
        trace.setEndTime(endTime);
        trace.setErrorMessage(errorMessage);
        trace.setParentExecutionId(parentExecutionId);
        trace.setRootExecutionId(rootExecutionId);
        trace.setParentStationLogId(parentStationLogId);
        return trace;
    }
}
