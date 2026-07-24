package io.github.gear4jtest.core.persistence;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.api.context.PayloadCloners;
import io.github.gear4jtest.core.api.trace.StationTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.security.RedactionTarget;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;

public record StationLogRecord(UUID id,
                               UUID assemblyLineExecutionId,
                               String operationId,
                               UUID parentOperationId,
                               String branchId,
                               StationLogStatus status,
                               Instant startedAt,
                               Instant endedAt,
                               String errorMessage,
                               String errorHandlerMessages,
                               Map<String, Object> context,
                               String itemId) {
    public StationLogRecord {
        id = Objects.requireNonNull(id, "id must not be null");
        assemblyLineExecutionId = Objects.requireNonNull(assemblyLineExecutionId,
                                                         "assemblyLineExecutionId must not be null");
        operationId = requireNotBlank(operationId, "operationId");
        status = Objects.requireNonNull(status, "status must not be null");
        startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        context = context == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    public StationLogRecord(UUID id,
                            UUID assemblyLineExecutionId,
                            String operationId,
                            UUID parentOperationId,
                            StationLogStatus status,
                            Instant startedAt,
                            Instant endedAt,
                            String errorMessage,
                            String errorHandlerMessages,
                            Map<String, Object> context,
                            String itemId) {
        this(id, assemblyLineExecutionId, operationId, parentOperationId, null, status, startedAt, endedAt,
                errorMessage,
                errorHandlerMessages, context, itemId);
    }

    public static StationLogRecord from(StationTrace log) {
        if (log == null) {
            throw new IllegalArgumentException("log must not be null");
        }
        Map<String, Object> copiedContext = log.getContext() == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(log.getContext()));
        return new StationLogRecord(log.getId(), log.getAssemblyLineExecutionId(), log.getOperationId(),
                log.getParentOperationId(), log.getBranchId(), log.getStatus(), log.getStartedAt(), log.getEndedAt(),
                log.getErrorMessage(), log.getErrorHandlerMessages(), copiedContext, log.getItemId());
    }

    @SuppressWarnings("unchecked")
    public StationLogRecord redactedWith(SensitiveDataRedactor redactor) {
        return redactedWith(redactor, PayloadCloners.immutableAware());
    }

    @SuppressWarnings("unchecked")
    public StationLogRecord redactedWith(SensitiveDataRedactor redactor, PayloadCloner payloadCloner) {
        SensitiveDataRedactor effective = redactor != null ? redactor : SensitiveDataRedactor.discardSensitiveValues();
        Object redactedContext = effective.redact(RedactionTarget.STATION_CONTEXT, context);
        Map<String, Object> storedContext = redactedContext instanceof Map<?, ?> map
                ? PersistenceSnapshots.capture((Map<String, Object>) map, payloadCloner) : Map.of();
        return new StationLogRecord(id, assemblyLineExecutionId, operationId, parentOperationId, branchId, status,
                startedAt, endedAt, stringValue(effective.redact(RedactionTarget.STATION_ERROR_MESSAGE, errorMessage)),
                stringValue(effective.redact(RedactionTarget.STATION_ERROR_HANDLER_MESSAGES, errorHandlerMessages)),
                storedContext, itemId);
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
