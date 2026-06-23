package io.github.gear4jtest.core.persistence;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.execution.trace.StationLogTrace;
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

    public static StationLogRecord from(StationLogTrace log) {
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
        SensitiveDataRedactor effective = redactor != null ? redactor : SensitiveDataRedactor.none();
        Object redactedContext = effective.redact(RedactionTarget.STATION_CONTEXT, context);
        Map<String, Object> storedContext = redactedContext instanceof Map<?, ?> map
                ? Collections.unmodifiableMap(new LinkedHashMap<>((Map<String, Object>) map)) : Map.of();
        return new StationLogRecord(id, assemblyLineExecutionId, operationId, parentOperationId, branchId, status,
                startedAt, endedAt, stringValue(effective.redact(RedactionTarget.STATION_ERROR_MESSAGE, errorMessage)),
                stringValue(effective.redact(RedactionTarget.STATION_ERROR_HANDLER_MESSAGES, errorHandlerMessages)),
                storedContext, itemId);
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }
}
