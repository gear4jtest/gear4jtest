package io.github.gear4jtest.core.persistence;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;

public record StationLogRecord(UUID id,
                               UUID pipelineExecutionId,
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
                            UUID pipelineExecutionId,
                            String operationId,
                            UUID parentOperationId,
                            StationLogStatus status,
                            Instant startedAt,
                            Instant endedAt,
                            String errorMessage,
                            String errorHandlerMessages,
                            Map<String, Object> context,
                            String itemId) {
        this(id, pipelineExecutionId, operationId, parentOperationId, null, status, startedAt, endedAt, errorMessage,
                errorHandlerMessages, context, itemId);
    }

    public static StationLogRecord from(StationLogTrace log) {
        if (log == null) {
            throw new IllegalArgumentException("log must not be null");
        }

        Map<String, Object> copiedContext = log.getContext() == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(log.getContext()));

        return new StationLogRecord(log.getId(), log.getPipelineExecutionId(), log.getOperationId(),
                log.getParentOperationId(), log.getBranchId(), log.getStatus(), log.getStartedAt(), log.getEndedAt(),
                log.getErrorMessage(), log.getErrorHandlerMessages(), copiedContext, log.getItemId());
    }
}
