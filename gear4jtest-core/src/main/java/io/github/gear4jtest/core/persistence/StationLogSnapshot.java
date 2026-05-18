package io.github.gear4jtest.core.persistence;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record StationLogSnapshot(UUID id,
                                 UUID pipelineExecutionId,
                                 String operationId,
                                 UUID parentOperationId,
                                 String branchId,
                                 StationLog.Status status,
                                 Instant startedAt,
                                 Instant endedAt,
                                 String errorMessage,
                                 String errorHandlerMessages,
                                 Map<String, Object> context,
                                 String itemId) {
    public StationLogSnapshot(UUID id,
                              UUID pipelineExecutionId,
                              String operationId,
                              UUID parentOperationId,
                              StationLog.Status status,
                              Instant startedAt,
                              Instant endedAt,
                              String errorMessage,
                              String errorHandlerMessages,
                              Map<String, Object> context,
                              String itemId) {
        this(id, pipelineExecutionId, operationId, parentOperationId, null, status, startedAt, endedAt, errorMessage,
                errorHandlerMessages, context, itemId);
    }

    public static StationLogSnapshot from(StationLog log) {
        if (log == null) {
            throw new IllegalArgumentException("log must not be null");
        }

        Map<String, Object> copiedContext = log.getContext() == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(log.getContext()));

        return new StationLogSnapshot(log.getId(), log.getPipelineExecutionId(), log.getOperationId(),
                log.getParentOperationId(), log.getBranchId(), log.getStatus(), log.getStartedAt(), log.getEndedAt(),
                log.getErrorMessage(), log.getErrorHandlerMessages(), copiedContext, log.getItemId());
    }

    public StationLog toStationLog() {
        StationLog log = new StationLog();
        log.setId(id);
        log.setPipelineExecutionId(pipelineExecutionId);
        log.setOperationId(operationId);
        log.setParentOperationId(parentOperationId);
        log.setBranchId(branchId);
        log.setStatus(status);
        log.setStartedAt(startedAt);
        log.setEndedAt(endedAt);
        log.setErrorMessage(errorMessage);
        log.setErrorHandlerMessages(errorHandlerMessages);
        log.setContext(context == null ? null : new LinkedHashMap<>(context));
        log.setItemId(itemId);
        return log;
    }
}
