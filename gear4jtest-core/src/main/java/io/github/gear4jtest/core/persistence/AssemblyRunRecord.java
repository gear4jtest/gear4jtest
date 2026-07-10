package io.github.gear4jtest.core.persistence;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.spi.security.RedactionTarget;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;

public record AssemblyRunRecord(UUID id,
                                String assemblyLineId,
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
        return from(trace, SensitiveDataRedactor.discardSensitiveValues());
    }

    @SuppressWarnings("unchecked")
    public static AssemblyRunRecord from(AssemblyRunTrace trace, SensitiveDataRedactor redactor) {
        if (trace == null) {
            throw new IllegalArgumentException("trace must not be null");
        }
        SensitiveDataRedactor effective = redactor != null ? redactor : SensitiveDataRedactor.discardSensitiveValues();
        Map<String, Object> context = trace.getContext() == null ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(trace.getContext()));
        Object redactedContext = effective.redact(RedactionTarget.RUN_CONTEXT, context);
        Map<String, Object> storedContext = redactedContext instanceof Map<?, ?> map
                ? Map.copyOf((Map<String, Object>) map) : Map.of();
        return new AssemblyRunRecord(trace.getId(), trace.getAssemblyLineId(), storedContext,
                effective.redact(RedactionTarget.RUN_INPUT, trace.getInputParams()),
                effective.redact(RedactionTarget.RUN_RESULT, trace.getResult()), trace.getStatus(),
                trace.getStartTime(),
                trace.getEndTime(), stringValue(effective.redact(RedactionTarget.RUN_ERROR_MESSAGE,
                                                                 trace.getErrorMessage())),
                trace.getParentExecutionId(), trace.getRootExecutionId(),
                trace.getParentStationLogId());
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    public AssemblyRunTrace toTrace() {
        AssemblyRunTrace trace = new AssemblyRunTrace();
        trace.setId(id);
        trace.setAssemblyLineId(assemblyLineId);
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
