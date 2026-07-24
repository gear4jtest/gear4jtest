package io.github.gear4jtest.core.persistence;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.api.context.PayloadCloners;
import io.github.gear4jtest.core.api.trace.RunTrace;
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
    public AssemblyRunRecord {
        id = Objects.requireNonNull(id, "id must not be null");
        assemblyLineId = requireNotBlank(assemblyLineId, "assemblyLineId");
        context = context == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(context));
        status = Objects.requireNonNull(status, "status must not be null");
    }

    public static AssemblyRunRecord from(RunTrace trace) {
        return from(trace, SensitiveDataRedactor.discardSensitiveValues());
    }

    public static AssemblyRunRecord from(RunTrace trace, SensitiveDataRedactor redactor) {
        return from(trace, redactor, PayloadCloners.immutableAware());
    }

    @SuppressWarnings("unchecked")
    public static AssemblyRunRecord from(RunTrace trace,
                                         SensitiveDataRedactor redactor,
                                         PayloadCloner payloadCloner) {
        if (trace == null) {
            throw new IllegalArgumentException("trace must not be null");
        }
        SensitiveDataRedactor effective = redactor != null ? redactor : SensitiveDataRedactor.discardSensitiveValues();
        Map<String, Object> context = trace.getContext() == null ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(trace.getContext()));
        Object redactedContext = effective.redact(RedactionTarget.RUN_CONTEXT, context);
        Map<String, Object> storedContext = redactedContext instanceof Map<?, ?> map
                ? PersistenceSnapshots.capture((Map<String, Object>) map, payloadCloner) : Map.of();
        return new AssemblyRunRecord(trace.getId(), trace.getAssemblyLineId(), storedContext,
                PersistenceSnapshots.capture(effective.redact(RedactionTarget.RUN_INPUT, trace.getInputParams()),
                                             payloadCloner),
                PersistenceSnapshots.capture(effective.redact(RedactionTarget.RUN_RESULT, trace.getResult()),
                                             payloadCloner),
                trace.getStatus(), trace.getStartTime(),
                trace.getEndTime(), stringValue(effective.redact(RedactionTarget.RUN_ERROR_MESSAGE,
                                                                 trace.getErrorMessage())),
                trace.getParentExecutionId(), trace.getRootExecutionId(),
                trace.getParentStationLogId());
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private static String requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
