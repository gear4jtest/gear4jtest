package io.github.gear4jtest.core.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.github.gear4jtest.core.persistence.StationLog;

public class DefaultStationExecutionContext implements StationExecutionContext {

    private final String operationId;
    private final StationKind kind;
    private final ExecutionContext globalContext;
    private final StationLog record;

    private final Map<Class<?>, Object> capabilities = new HashMap<>();

    public DefaultStationExecutionContext(
            String operationId,
            StationKind kind,
            ExecutionContext globalContext,
            StationLog record
    ) {
        this.operationId = operationId;
        this.kind = kind;
        this.globalContext = globalContext;
        this.record = record;
    }

    @Override
    public String getOperationId() {
        return operationId;
    }

    @Override
    public StationKind getKind() {
        return kind;
    }

    @Override
    public ExecutionContext getGlobalContext() {
        return globalContext;
    }

    @Override
    public StationLog getRecord() {
        return record;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getCapability(Class<T> type) {
        return Optional.ofNullable((T) capabilities.get(type));
    }

    /**
     * API interne pour enrichir le contexte avec des capabilities typées.
     * À utiliser depuis les OperationDefinition concrètes.
     */
    public <T> void addCapability(Class<T> type, T instance) {
        capabilities.put(type, instance);
    }
}
